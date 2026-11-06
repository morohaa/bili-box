package io.github.bilibox.feature.player

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import io.github.bilibox.core.playback.LocalQueueMode
import io.github.bilibox.core.ui.component.rememberVideoThumbnail
import io.github.bilibox.data.localvideo.LocalPlaybackQueue
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.data.localvideo.PlaybackProgressStore
import io.github.bilibox.data.localvideo.cachedVideoThumbnail
import io.github.bilibox.data.localvideo.loadVideoThumbnail
import io.github.bilibox.data.localvideo.localListenSubtitle
import io.github.bilibox.feature.listen.ListenControls
import io.github.bilibox.feature.listen.ListenInfo
import io.github.bilibox.feature.player.details.LocalVideoIntro
import io.github.bilibox.feature.player.details.LocalVideoMediaInfo
import io.github.bilibox.feature.player.details.PlaybackModeSheet
import kotlinx.coroutines.delay

@Composable
internal fun LocalVideoScreen(
    video: LocalVideo,
    playlist: List<LocalVideo>,
    onVideoSelected: (LocalVideo) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val progressStore = remember(context) { PlaybackProgressStore(context) }
    // One player for the whole folder session: the queue switches files on it
    // directly, so 连播 also works while the screen is locked (no recomposition).
    val queue = remember { LocalPlaybackQueue(context, createCompatiblePlayer(context), progressStore, video) }
    val player = queue.player
    var playbackError by remember(player) { mutableStateOf<String?>(null) }
    var playbackState by remember(player) { mutableIntStateOf(Player.STATE_IDLE) }
    // Survives a switch to the next folder item so 连播 keeps working.
    var queueMode by rememberSaveable { mutableStateOf(LocalQueueMode.LIST_LOOP) }
    var resumeDismissed by remember(video.uri) { mutableStateOf(false) }
    SideEffect {
        queue.playlist = playlist
        queue.mode = queueMode
        queue.onCurrentChanged = onVideoSelected
    }

    fun savePlaybackProgress() = queue.save()

    fun switchTo(next: LocalVideo) = queue.moveTo(next)

    fun shareVideo() {
        runCatching {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = video.mimeType.ifBlank { "video/*" }
                        putExtra(Intent.EXTRA_STREAM, video.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "分享视频"
                )
            )
        }.onFailure { Toast.makeText(context, "无法分享这个视频", Toast.LENGTH_SHORT).show() }
    }

    DisposableEffect(queue, activity) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val detail = (error.cause?.message ?: error.message)?.takeIf { it.isNotBlank() }
                playbackError = "${error.errorCodeName}${detail?.let { "：$it" }.orEmpty()}"
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_READY) playbackError = null
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                queue.save()
            }
        }
        player.addListener(listener)
        activity?.lifecycle?.addObserver(lifecycleObserver)
        onDispose {
            activity?.lifecycle?.removeObserver(lifecycleObserver)
            player.removeListener(listener)
            queue.release()
        }
    }
    // Opens the first file, and files picked while the UI was paused are already
    // current, so this is a no-op when the queue got there first.
    LaunchedEffect(queue, video.uri) {
        playbackError = null
        queue.open(video)
    }
    // The queue remembers where it actually opened the file; the store may
    // already hold a later position if playback ran on in the background.
    val storedResume = remember(video.key) { progressStore.load(video.key, video.durationMs) }
    val resumePosition = if (queue.current.uri == video.uri) queue.startPositionMs else storedResume
    LaunchedEffect(queue) {
        while (true) {
            delay(5_000)
            queue.save()
        }
    }
    LaunchedEffect(player, playbackState) {
        if (playbackState == Player.STATE_BUFFERING) {
            delay(12_000)
            if (player.playbackState == Player.STATE_BUFFERING && playbackError == null) {
                playbackError = "加载超时，请检查文件是否可读取或编码是否受支持"
            }
        }
    }
    val currentIndex = playlist.indexOfFirst { it.uri == video.uri }
    val nextVideo = if (currentIndex >= 0) playlist.getOrNull(currentIndex + 1) else null
    val previousVideo = if (currentIndex > 0) playlist.getOrNull(currentIndex - 1) else null
    // Folder queue progress is read once per opened video; the current item's
    // bar keeps following the live player position instead.
    val playbackByKey = remember(playlist, video.uri) {
        playlist.associate { item -> item.key to progressStore.playback(item.key, item.durationMs) }
    }
    val listenThumbnail = rememberVideoThumbnail(video.key, cachedVideoThumbnail(video.key)) { context ->
        loadVideoThumbnail(context, video.uri, video.key)
    }
    VideoPlayerScreen(
        player = player,
        title = video.name,
        defaultDurationMs = video.durationMs,
        playbackError = playbackError,
        sourceLabel = "本地视频 ${playlist.size}",
        remainingVideos = (playlist.size - currentIndex).coerceAtLeast(1),
        commentMessage = "本地视频暂时没有评论",
        detailTabs = listOf("简介", "媒体信息"),
        sourcePill = true,
        listenInfo = ListenInfo(
            subtitle = localListenSubtitle(video),
            online = false,
            coverBitmap = listenThumbnail,
        ),
        listenQueue = queue,
        listenControls = ListenControls(
            mode = when (queueMode) {
                LocalQueueMode.LIST_LOOP -> 0
                LocalQueueMode.SINGLE_LOOP -> 1
                LocalQueueMode.SHUFFLE -> 2
                LocalQueueMode.STOP_AFTER -> 3
            },
            hasPrevious = previousVideo != null,
            hasNext = nextVideo != null,
            onPrevious = { previousVideo?.let { switchTo(it) } },
            onNext = { nextVideo?.let { switchTo(it) } },
            onCycleMode = {
                queueMode = when (queueMode) {
                    LocalQueueMode.LIST_LOOP -> LocalQueueMode.SINGLE_LOOP
                    LocalQueueMode.SINGLE_LOOP -> LocalQueueMode.SHUFFLE
                    LocalQueueMode.SHUFFLE -> LocalQueueMode.STOP_AFTER
                    LocalQueueMode.STOP_AFTER -> LocalQueueMode.LIST_LOOP
                }
            },
        ),
        mediaKey = video.uri,
        // Persist the exact exit position before the library recomposes its status list.
        onBack = {
            savePlaybackProgress()
            onBack()
        },
        onRetry = {
            playbackError = null
            queue.retry()
        },
        onNextEpisode = nextVideo?.let { next -> { switchTo(next) } },
        episodes = { dismiss ->
            LocalVideoEpisodes(playlist, video, onDismiss = dismiss, onVideoSelected = {
                dismiss()
                switchTo(it)
            })
        },
        extraDetailPane = { duration ->
            LocalVideoMediaInfo(video = video, durationMs = duration)
        },
        playbackModeSheet = { dismiss ->
            PlaybackModeSheet(
                selected = queueMode,
                onDismiss = dismiss,
                onSelect = { mode ->
                    queueMode = mode
                    dismiss()
                },
            )
        },
        autoPlayNext = queueMode != LocalQueueMode.STOP_AFTER,
        onAutoPlayNextChange = { enabled ->
            queueMode = if (enabled) LocalQueueMode.LIST_LOOP else LocalQueueMode.STOP_AFTER
        },
        loopMode = when (queueMode) {
            LocalQueueMode.SINGLE_LOOP -> PlayerLoopMode.SINGLE_LOOP
            LocalQueueMode.STOP_AFTER -> PlayerLoopMode.NONE
            LocalQueueMode.LIST_LOOP, LocalQueueMode.SHUFFLE -> PlayerLoopMode.LIST_LOOP
        },
        onLoopModeChange = { mode ->
            queueMode = when (mode) {
                PlayerLoopMode.SINGLE_LOOP -> LocalQueueMode.SINGLE_LOOP
                PlayerLoopMode.LIST_LOOP -> LocalQueueMode.LIST_LOOP
                PlayerLoopMode.NONE -> LocalQueueMode.STOP_AFTER
            }
        },
        details = { duration ->
            LocalVideoIntro(
                video = video,
                playlist = playlist,
                durationMs = duration,
                resumeMs = if (resumeDismissed) 0L else resumePosition,
                queueMode = queueMode,
                playbackByKey = playbackByKey,
                onShare = { shareVideo() },
                onSelectVideo = { switchTo(it) },
                onResumeDismiss = { resumeDismissed = true },
            )
        }
    )
}
