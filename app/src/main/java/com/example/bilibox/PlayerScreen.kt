package com.example.bilibox

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.Formatter
import android.util.Size
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.bilibox.ui.theme.BiliBoxTheme
import com.example.bilibox.bili.BiliClient
import com.example.bilibox.bili.Page
import com.example.bilibox.bili.BiliPlayback
import com.example.bilibox.bili.BiliQn
import com.example.bilibox.bili.VideoInfo
import com.example.bilibox.bili.VideoSearchItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


private data class PlayerGestureFeedbackState(
    val side: PlayerGestureSide,
    val level: Float
)

/** A selectable video stream quality shown in the fullscreen quality drawer. */
internal data class PlayerQualityOption(
    val qn: Int,
    val label: String,
    val description: String = "",
    val badge: String? = null,
)

/**
 * Live player hooks for the portrait details pane. The pane can reuse the
 * player's own speed sheet, screenshot pipeline and seek logic instead of
 * keeping a second copy of that state.
 */
internal class PlayerDetailActions(
    val speed: Float,
    val positionMs: Long,
    val screenshotEnabled: Boolean,
    val openSpeedPanel: () -> Unit,
    val openPlaybackModeSheet: () -> Unit,
    val takeScreenshot: () -> Unit,
    val seekTo: (Long) -> Unit,
)

internal val LocalPlayerDetailActions = staticCompositionLocalOf<PlayerDetailActions?> { null }

private fun readScreenBrightness(activity: ComponentActivity): Float {
    val override = activity.window.attributes.screenBrightness
    if (override >= 0f) return override.coerceIn(0f, 1f)
    val systemBrightness = Settings.System.getInt(
        activity.contentResolver,
        Settings.System.SCREEN_BRIGHTNESS,
        128
    )
    return systemBrightness.coerceIn(0, 255) / 255f
}

@Composable
internal fun LocalVideoPlayer(
    video: LocalVideo,
    playlist: List<LocalVideo>,
    onVideoSelected: (LocalVideo) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val progressStore = remember(context) { PlaybackProgressStore(context) }
    // File size also distinguishes a replacement file that reuses a MediaStore URI.
    val progressKey = remember(video.uri, video.sizeBytes) { "${video.uri}|${video.sizeBytes}" }
    val resumePosition = remember(progressKey) { progressStore.load(progressKey, video.durationMs) }
    val player = remember(video.uri) { createCompatiblePlayer(context) }
    var playbackError by remember(player) { mutableStateOf<String?>(null) }
    var playbackState by remember(player) { mutableIntStateOf(Player.STATE_IDLE) }
    var saved by remember(video.uri) { mutableStateOf(false) }
    // Survives a switch to the next folder item so 连播 keeps working.
    var queueMode by rememberSaveable { mutableStateOf(LocalQueueMode.LIST_LOOP) }
    var resumeDismissed by remember(video.uri) { mutableStateOf(false) }
    val queueModeState = rememberUpdatedState(queueMode)
    val playlistState = rememberUpdatedState(playlist)
    val videoState = rememberUpdatedState(video)
    val selectState = rememberUpdatedState(onVideoSelected)

    fun savePlaybackProgress() {
        // Ignore the empty/idle player during setup or a failed preparation.
        if (player.mediaItemCount == 0 || player.playbackState == Player.STATE_IDLE) return
        progressStore.save(
            progressKey,
            player.currentPosition,
            player.duration.takeIf { it > 0L } ?: video.durationMs,
            completed = player.playbackState == Player.STATE_ENDED
        )
    }

    fun switchTo(next: LocalVideo) {
        savePlaybackProgress()
        onVideoSelected(next)
    }

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

    DisposableEffect(player, activity) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val detail = (error.cause?.message ?: error.message)?.takeIf { it.isNotBlank() }
                playbackError = "${error.errorCodeName}${detail?.let { "：$it" }.orEmpty()}"
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_READY) playbackError = null
                if (state == Player.STATE_ENDED) {
                    val list = playlistState.value
                    val current = videoState.value
                    when (queueModeState.value) {
                        LocalQueueMode.LIST_LOOP -> {
                            val index = list.indexOfFirst { it.uri == current.uri }
                            val next = list.getOrNull(index + 1) ?: list.firstOrNull()
                            if (next != null && next.uri != current.uri) {
                                savePlaybackProgress()
                                selectState.value(next)
                            } else {
                                player.seekTo(0L)
                                player.playWhenReady = true
                            }
                        }
                        LocalQueueMode.SHUFFLE -> {
                            val next = list.filter { it.uri != current.uri }.randomOrNull()
                            if (next != null) {
                                savePlaybackProgress()
                                selectState.value(next)
                            } else {
                                player.seekTo(0L)
                                player.playWhenReady = true
                            }
                        }
                        else -> Unit
                    }
                }
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_POSITION_DISCONTINUITY)) {
                    savePlaybackProgress()
                }
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                savePlaybackProgress()
            }
        }
        player.addListener(listener)
        activity?.lifecycle?.addObserver(lifecycleObserver)
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(video.uri), resumePosition)
        player.prepare()
        player.playWhenReady = true
        onDispose {
            savePlaybackProgress()
            activity?.lifecycle?.removeObserver(lifecycleObserver)
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            delay(5_000)
            savePlaybackProgress()
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
    LaunchedEffect(player, queueMode) {
        player.repeatMode = if (queueMode == LocalQueueMode.SINGLE_LOOP) Player.REPEAT_MODE_ONE
        else Player.REPEAT_MODE_OFF
    }
    val currentIndex = playlist.indexOfFirst { it.uri == video.uri }
    val nextVideo = if (currentIndex >= 0) playlist.getOrNull(currentIndex + 1) else null
    // Folder queue progress is read once per opened video; the current item's
    // bar keeps following the live player position instead.
    val playbackByKey = remember(playlist, video.uri) {
        playlist.associate { item -> item.key to progressStore.playback(item.key, item.durationMs) }
    }
    VideoPlayerScreen(
        player = player,
        title = video.name,
        defaultDurationMs = video.durationMs,
        playbackError = playbackError,
        sourceLabel = "本地视频 ${playlist.size}",
        commentMessage = "本地视频暂时没有评论",
        detailTabs = listOf("简介", "媒体信息"),
        sourcePill = true,
        // Persist the exact exit position before the library recomposes its status list.
        onBack = {
            savePlaybackProgress()
            onBack()
        },
        onRetry = {
            playbackError = null
            val retryPosition = player.currentPosition.coerceAtLeast(0L)
            player.stop()
            player.setMediaItem(androidx.media3.common.MediaItem.fromUri(video.uri), retryPosition)
            player.prepare()
            player.playWhenReady = true
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
        details = { duration ->
            LocalVideoIntro(
                video = video,
                playlist = playlist,
                durationMs = duration,
                resumeMs = if (resumeDismissed) 0L else resumePosition,
                saved = saved,
                queueMode = queueMode,
                playbackByKey = playbackByKey,
                onToggleSaved = { saved = !saved },
                onShare = { shareVideo() },
                onSelectVideo = { switchTo(it) },
                onResumeDismiss = { resumeDismissed = true },
            )
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoPlayerScreen(
    player: ExoPlayer,
    title: String,
    defaultDurationMs: Long = 0L,
    playbackError: String? = null,
    loadingMessage: String? = null,
    sourceLabel: String,
    commentMessage: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    episodes: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    episodeOverlay: (@Composable () -> Unit)? = null,
    episodeOverlayVisible: Boolean = episodeOverlay != null,
    episodeOverlayDismiss: (() -> Unit)? = null,
    onNextEpisode: (() -> Unit)? = null,
    qualityOptions: List<PlayerQualityOption> = emptyList(),
    selectedQuality: Int? = null,
    onQualitySelected: ((Int) -> Unit)? = null,
    detailsBackground: Color = Color.White,
    showDetailTabs: Boolean = true,
    detailTabs: List<String> = listOf("简介", "评论"),
    sourcePill: Boolean = false,
    extraDetailPane: (@Composable (duration: Long) -> Unit)? = null,
    playbackModeSheet: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    details: @Composable (duration: Long) -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val fullScreen = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var position by remember(player) { mutableLongStateOf(player.currentPosition.coerceAtLeast(0L)) }
    var bufferedPosition by remember(player) { mutableLongStateOf(player.bufferedPosition.coerceAtLeast(0L)) }
    var duration by remember(player) { mutableLongStateOf(defaultDurationMs) }
    var isPlaying by remember(player) { mutableStateOf(player.isPlaying) }
    var playWhenReady by remember(player) { mutableStateOf(player.playWhenReady) }
    var scrubbing by remember(player) { mutableStateOf(false) }
    var scrubPosition by remember(player) { mutableFloatStateOf(0f) }
    var speed by remember(player) { mutableFloatStateOf(1f) }
    var speedMenuExpanded by remember(player) { mutableStateOf(false) }
    var episodesExpanded by remember(player) { mutableStateOf(false) }
    var qualityMenuExpanded by remember(player) { mutableStateOf(false) }
    var playbackState by remember(player) { mutableIntStateOf(player.playbackState) }
    var detailsTab by remember(player) { mutableIntStateOf(0) }
    var playbackModeExpanded by remember(player) { mutableStateOf(false) }
    var controlsVisible by remember(player) { mutableStateOf(true) }
    var screenLocked by remember(player) { mutableStateOf(false) }
    var orientationBeforeLock by remember(player) { mutableStateOf<Int?>(null) }
    var playerView by remember(player) { mutableStateOf<PlayerView?>(null) }
    var hasVideoFrame by remember(player) { mutableStateOf(false) }
    var savingFrame by remember { mutableStateOf(false) }
    val screenshotFlash = remember { Animatable(0f) }
    var screenshotPreview by remember { mutableStateOf<Bitmap?>(null) }
    var screenshotPreviewVisible by remember { mutableStateOf(false) }
    var screenBrightness by remember(activity) {
        mutableFloatStateOf(activity?.let(::readScreenBrightness) ?: .5f)
    }
    var volumeGestureRemainder by remember(player) { mutableFloatStateOf(0f) }
    var gestureFeedback by remember(player) { mutableStateOf<PlayerGestureFeedbackState?>(null) }
    var verticalGestureActive by remember(player) { mutableStateOf(false) }
    val captureScope = rememberCoroutineScope()

    val originalWindowBrightness = remember(activity) { activity?.window?.attributes?.screenBrightness }
    DisposableEffect(activity) {
        val originalOrientation = activity?.requestedOrientation
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        val lightStatusBars = controller?.isAppearanceLightStatusBars
        val lightNavigationBars = controller?.isAppearanceLightNavigationBars
        val barsBehavior = controller?.systemBarsBehavior
        onDispose {
            if (originalOrientation != null) activity?.requestedOrientation = originalOrientation
            if (originalWindowBrightness != null) activity?.window?.let { window ->
                val attributes = window.attributes
                attributes.screenBrightness = originalWindowBrightness
                window.attributes = attributes
            }
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (lightStatusBars != null) controller?.isAppearanceLightStatusBars = lightStatusBars
            if (lightNavigationBars != null) controller?.isAppearanceLightNavigationBars = lightNavigationBars
            if (barsBehavior != null) controller?.systemBarsBehavior = barsBehavior
        }
    }
    LaunchedEffect(activity, fullScreen) {
        speedMenuExpanded = false
        playbackModeExpanded = false
        episodesExpanded = false
        qualityMenuExpanded = false
        if (!fullScreen) screenLocked = false
        activity?.let {
            WindowCompat.getInsetsController(it.window, it.window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = !fullScreen && detailsBackground == Color.White
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (fullScreen) hide(WindowInsetsCompat.Type.systemBars())
                else show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    fun toggleOrientation() {
        controlsVisible = true
        activity?.requestedOrientation = if (fullScreen) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    fun navigateBack() {
        if (fullScreen) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else onBack()
    }

    fun togglePlayback() {
        if (screenLocked) return
        if (player.playbackState == Player.STATE_ENDED) {
            player.seekTo(0L)
            player.playWhenReady = true
        } else {
            player.playWhenReady = !player.playWhenReady
        }
        controlsVisible = true
    }

    fun toggleScreenLock() {
        screenLocked = !screenLocked
        controlsVisible = true
        speedMenuExpanded = false
        episodesExpanded = false
        qualityMenuExpanded = false
        if (screenLocked) {
            orientationBeforeLock = activity?.requestedOrientation
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        } else {
            activity?.requestedOrientation = orientationBeforeLock ?: ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            orientationBeforeLock = null
        }
    }

    fun updateGestureFeedback(side: PlayerGestureSide) {
        if (side == PlayerGestureSide.LEFT) {
            val level = screenBrightness.coerceIn(0f, 1f)
            gestureFeedback = PlayerGestureFeedbackState(side, level)
        } else {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            gestureFeedback = PlayerGestureFeedbackState(side, volume.toFloat() / maxVolume)
        }
    }

    fun startVerticalGesture(side: PlayerGestureSide) {
        verticalGestureActive = true
        if (side == PlayerGestureSide.LEFT) {
            screenBrightness = activity?.let(::readScreenBrightness) ?: screenBrightness
        } else {
            volumeGestureRemainder = 0f
        }
        updateGestureFeedback(side)
    }

    fun adjustFromVerticalGesture(side: PlayerGestureSide, deltaFraction: Float) {
        if (side == PlayerGestureSide.LEFT) {
            val newBrightness = (screenBrightness - deltaFraction).coerceIn(.02f, 1f)
            screenBrightness = newBrightness
            activity?.window?.let { setPlayerWindowBrightness(it, newBrightness) }
        } else {
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            volumeGestureRemainder += -deltaFraction * maxVolume
            val volumeSteps = volumeGestureRemainder.toInt()
            if (volumeSteps != 0) {
                val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    (current + volumeSteps).coerceIn(0, maxVolume),
                    0
                )
                volumeGestureRemainder -= volumeSteps
            }
        }
        updateGestureFeedback(side)
    }

    fun takeVideoScreenshot() {
        if (savingFrame || screenLocked || !hasVideoFrame || playbackError != null) return
        val frame = try {
            playerView?.let(::captureVideoFrame)
        } catch (_: Exception) { null }
        if (frame == null) {
            Toast.makeText(context, "暂时无法截取画面，请等待视频显示后重试", Toast.LENGTH_SHORT).show()
            return
        }
        // Shutter flash over the video, then the captured frame slides in as a
        // confirmation card instead of a plain toast.
        val flash = captureScope.launch {
            screenshotFlash.snapTo(.82f)
            screenshotFlash.animateTo(0f, animationSpec = tween(360, easing = LinearEasing))
        }
        savingFrame = true
        captureScope.launch {
            var saved = false
            try {
                withContext(Dispatchers.IO) { saveVideoFrame(context, frame) }
                saved = true
                // Let the shutter flash finish first, then reveal the frame card.
                flash.join()
                screenshotPreview = frame
                // Compose the card once while hidden so its scale-in transition runs.
                withFrameNanos { }
                screenshotPreviewVisible = true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Toast.makeText(context, "截帧保存失败，请检查存储空间或权限后重试", Toast.LENGTH_SHORT).show()
            } finally {
                savingFrame = false
                if (!saved) frame.recycle()
            }
        }
    }

    val screenshotPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takeVideoScreenshot()
        else Toast.makeText(context, "需要存储权限才能保存视频画面", Toast.LENGTH_SHORT).show()
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() { hasVideoFrame = true }
            override fun onPlayWhenReadyChanged(ready: Boolean, reason: Int) { playWhenReady = ready }
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_IDLE) hasVideoFrame = false
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }
    LaunchedEffect(player) {
        while (true) {
            duration = player.duration.takeIf { it > 0 } ?: defaultDurationMs
            if (!scrubbing) position = player.currentPosition.coerceAtLeast(0L)
            bufferedPosition = player.bufferedPosition.coerceAtLeast(0L)
            isPlaying = player.isPlaying
            speed = player.playbackParameters.speed
            delay(250)
        }
    }
    LaunchedEffect(controlsVisible, isPlaying, screenLocked, scrubbing, speedMenuExpanded, playbackModeExpanded, episodesExpanded, qualityMenuExpanded) {
        if (controlsVisible && (isPlaying || screenLocked) && !scrubbing &&
            !speedMenuExpanded && !playbackModeExpanded && !episodesExpanded && !qualityMenuExpanded) {
            delay(4_000)
            controlsVisible = false
        }
    }
    LaunchedEffect(gestureFeedback, verticalGestureActive) {
        if (gestureFeedback != null && !verticalGestureActive) {
            delay(900)
            gestureFeedback = null
        }
    }
    LaunchedEffect(screenshotPreview) {
        val frame = screenshotPreview ?: return@LaunchedEffect
        delay(2_200)
        screenshotPreviewVisible = false
        delay(260)
        if (screenshotPreview === frame) {
            screenshotPreview = null
            frame.recycle()
        }
    }
    BackHandler {
        when {
            screenLocked -> controlsVisible = true
            episodeOverlayDismiss != null -> episodeOverlayDismiss()
            qualityMenuExpanded -> qualityMenuExpanded = false
            episodesExpanded -> episodesExpanded = false
            playbackModeExpanded -> playbackModeExpanded = false
            speedMenuExpanded -> speedMenuExpanded = false
            else -> navigateBack()
        }
    }

    // The speed panel takes the whole stage: hide the chrome behind it so the
    // runner and the ruler are the only things competing for attention.
    val showChrome = controlsVisible && !screenLocked && !(fullScreen && speedMenuExpanded)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Color.Black)
            .then(if (fullScreen) Modifier else Modifier.statusBarsPadding())) {
        Box(
            (if (fullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { viewContext -> createTexturePlayerView(viewContext, player).also { playerView = it } },
                update = { it.player = player; playerView = it },
                modifier = Modifier.fillMaxSize()
            )
            PlayerTouchSurface(
                locked = screenLocked,
                onToggleControls = { controlsVisible = !controlsVisible },
                onTogglePlayback = { togglePlayback() },
                verticalGesturesEnabled = !screenLocked,
                onVerticalGestureStart = { startVerticalGesture(it) },
                onVerticalSwipe = { side, delta -> adjustFromVerticalGesture(side, delta) },
                onVerticalGestureEnd = { verticalGestureActive = false },
                modifier = Modifier.fillMaxSize()
            )
            if (playbackError != null) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("暂时无法播放", color = Color.White, fontSize = 14.sp)
                    TextButton(onClick = onRetry) { Text("重试", color = Pink) }
                }
            } else if (loadingMessage != null || playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE) {
                BiliLoadingIndicator(
                    message = loadingMessage ?: if (playbackState == Player.STATE_BUFFERING) "正在缓冲…" else "等待视频加载"
                )
            }
            if (showChrome) {
                Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(56.dp)
                    .background(Brush.verticalGradient(listOf(
                        Color.Black.copy(alpha = .75f), Color.Black.copy(alpha = .3f), Color.Transparent
                    ))))
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(if (fullScreen) 150.dp else 64.dp)
                    .background(Brush.verticalGradient(listOf(
                        Color.Transparent, Color.Black.copy(alpha = .4f), Color.Black.copy(alpha = .88f)
                    ))))
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .then(if (fullScreen) Modifier
                            .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 6.dp)
                            else Modifier.padding(horizontal = 6.dp))
                ) {
                    val seekBar: @Composable (Modifier) -> Unit = { modifier ->
                        VideoSeekBar(
                            position = if (scrubbing) scrubPosition else position.toFloat(),
                            duration = duration,
                            bufferedPosition = bufferedPosition,
                            onSeek = { scrubbing = true; scrubPosition = it },
                            onSeekFinished = {
                                player.seekTo(scrubPosition.toLong())
                                position = scrubPosition.toLong()
                                scrubbing = false
                            },
                            modifier = modifier
                        )
                    }
                    val showPause = playWhenReady && playbackState != Player.STATE_ENDED
                    if (fullScreen) {
                        val currentQualityLabel = qualityOptions
                            .firstOrNull { it.qn == selectedQuality }?.label
                            ?: qualityOptions.maxByOrNull { it.qn }?.label.orEmpty()
                        Row(
                            Modifier.fillMaxWidth().height(26.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            seekBar(Modifier.fillMaxWidth())
                        }
                        Row(
                            Modifier.fillMaxWidth().height(46.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: play/pause, next part and the tabular timestamp.
                            PlayerBarIconButton(
                                contentDescription = if (showPause) "暂停" else "播放",
                                onClick = { togglePlayback() }
                            ) {
                                PlaybackIcon(showPause, Modifier.size(22.dp))
                            }
                            // Keep the next-episode icon in the bar even for
                            // single-part videos, where tapping explains why it
                            // has nowhere to go.
                            PlayerBarIconButton(
                                contentDescription = "下一集",
                                onClick = {
                                    val next = onNextEpisode
                                    if (next != null) next()
                                    else Toast.makeText(context, "已经是最后一集", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                NextEpisodeIcon(Modifier.size(18.dp))
                            }
                            PlaybackTimeLabel(
                                position = if (scrubbing) scrubPosition.toLong() else position,
                                duration = duration,
                                modifier = Modifier.padding(start = 6.dp, end = 4.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            // Center: danmaku hub placeholder, capped so the
                            // send field does not stretch across the whole bar.
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                DanmakuPlaceholderBar(
                                    onPlaceholderClick = {
                                        Toast.makeText(context, "弹幕功能正在开发中，敬请期待", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth()
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            // Right: episodes, speed and quality. The fullscreen
                            // exit is intentionally left to the top-left back arrow.
                            if (episodes != null) {
                                PlayerBarAction("选集", contentDescription = "选集") {
                                    speedMenuExpanded = false
                                    qualityMenuExpanded = false
                                    episodesExpanded = true
                                }
                            }
                            SpeedBarChip(speed = speed) {
                                episodesExpanded = false
                                qualityMenuExpanded = false
                                speedMenuExpanded = true
                            }
                            if (qualityOptions.isNotEmpty()) {
                                PlayerQualityChip(
                                    label = currentQualityLabel,
                                    onClick = {
                                        episodesExpanded = false
                                        speedMenuExpanded = false
                                        qualityMenuExpanded = true
                                    }
                                )
                            }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { togglePlayback() },
                                modifier = Modifier.size(40.dp).semantics {
                                    contentDescription = if (showPause) "暂停" else "播放"
                                }
                            ) {
                                PlaybackIcon(showPause, Modifier.size(18.dp))
                            }
                            seekBar(Modifier.weight(1f))
                            PlaybackTimeLabel(
                                position = if (scrubbing) scrubPosition.toLong() else position,
                                duration = duration,
                                modifier = Modifier.padding(start = 10.dp)
                            )
                            IconButton(
                                onClick = { toggleOrientation() },
                                modifier = Modifier.size(40.dp).semantics { contentDescription = "切换横屏" }
                            ) {
                                FullscreenIcon(fullScreen = false, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            if (showChrome) Row(
                Modifier.align(Alignment.TopStart).fillMaxWidth()
                    .then(if (fullScreen) Modifier.displayCutoutPadding() else Modifier)
                    .height(if (fullScreen) 48.dp else 44.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { navigateBack() }, modifier = Modifier.size(if (fullScreen) 48.dp else 40.dp)
                    .semantics { contentDescription = "返回" }) {
                    Canvas(Modifier.size(if (fullScreen) 24.dp else 20.dp)) {
                        drawPath(Path().apply {
                            moveTo(size.width * .63f, size.height * .15f)
                            lineTo(size.width * .28f, size.height * .5f)
                            lineTo(size.width * .63f, size.height * .85f)
                        }, Color.White, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                Text(if (fullScreen) title else "", color = Color.White, fontSize = 14.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (!fullScreen) {
                    SpeedBarChip(speed = speed) { speedMenuExpanded = true }
                }
            }
            // Locking the screen must keep the unlock affordance reachable, so
            // this follows the raw chrome state rather than showChrome.
            if (fullScreen && controlsVisible && !speedMenuExpanded) {
                PlayerSideTools(
                    locked = screenLocked,
                    screenshotEnabled = hasVideoFrame && playbackError == null && !savingFrame,
                    onScreenshot = {
                        if (Build.VERSION.SDK_INT == 28 && context.checkSelfPermission(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            ) != PackageManager.PERMISSION_GRANTED) {
                            screenshotPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else takeVideoScreenshot()
                    },
                    onToggleLock = { toggleScreenLock() },
                    modifier = Modifier.align(Alignment.CenterEnd).displayCutoutPadding()
                        .navigationBarsPadding().padding(end = 10.dp).offset(y = (-36).dp)
                )
            }
            gestureFeedback?.let { feedback ->
                PlayerGestureFeedback(
                    side = feedback.side,
                    level = feedback.level,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            val episodeContent = episodes
            if (fullScreen && !screenLocked && episodeContent != null) {
                PlayerDrawerVisibility(visible = episodesExpanded) {
                    episodeContent.invoke {
                        episodesExpanded = false
                        controlsVisible = true
                    }
                }
            }
            if (fullScreen && !screenLocked) {
                PlayerDrawerVisibility(visible = speedMenuExpanded) {
                    SpeedPanel(
                        speed = speed,
                        mode = SpeedPanelMode.Drawer,
                        onDismiss = { speedMenuExpanded = false },
                        onSpeedChange = {
                            player.setPlaybackSpeed(it)
                            speed = it
                        }
                    )
                }
                PlayerDrawerVisibility(visible = qualityMenuExpanded) {
                    PlaybackQualityDrawer(
                        options = qualityOptions,
                        selectedQuality = selectedQuality,
                        onDismiss = { qualityMenuExpanded = false },
                        onQualitySelected = { qn ->
                            onQualitySelected?.invoke(qn)
                            qualityMenuExpanded = false
                        },
                    )
                }
            }

            // Save confirmation: a scaled thumbnail of the captured frame sits
            // above the control bar, mirroring the reference player.
            val previewFrame = screenshotPreview
            if (previewFrame != null) {
                ScreenshotPreviewCard(
                    frame = previewFrame,
                    visible = screenshotPreviewVisible,
                    modifier = Modifier.align(Alignment.BottomStart)
                        .padding(start = 10.dp, bottom = if (fullScreen) 156.dp else 48.dp),
                )
            }
            if (screenshotFlash.value > .001f) {
                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = screenshotFlash.value)))
            }
        }

        if (!fullScreen) {
            val overlayVisible = episodeOverlayVisible && episodeOverlay != null
            val activePane = if (showDetailTabs) detailsTab else 0
            val detailScroll = rememberScrollState()
            LaunchedEffect(activePane) { detailScroll.scrollTo(0) }
            Box(
                Modifier.weight(1f).fillMaxWidth().background(detailsBackground)
                    .navigationBarsPadding().clipToBounds()
            ) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(detailScroll)
                ) {
                    if (showDetailTabs) {
                        var tabCenters by remember { mutableStateOf<Map<Int, Float>>(emptyMap()) }
                        val density = LocalDensity.current
                        val indicatorWidth = 28.dp
                        val indicatorWidthPx = with(density) { indicatorWidth.toPx() }
                        val activeCenter = tabCenters[detailsTab]
                        Box(Modifier.fillMaxWidth().height(44.dp)) {
                            Row(
                                Modifier.fillMaxSize().padding(start = 4.dp, end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                detailTabs.forEachIndexed { index, label ->
                                    val selected = detailsTab == index
                                    val textColor by animateColorAsState(
                                        targetValue = if (selected) OnlinePink else Color(0xFF61666D),
                                        animationSpec = tween(180),
                                        label = "详情标签颜色",
                                    )
                                    Box(
                                        Modifier.fillMaxHeight()
                                            .clickable { detailsTab = index }
                                            .onGloballyPositioned { coordinates ->
                                                val center = coordinates.positionInParent().x +
                                                    coordinates.size.width / 2f
                                                if (tabCenters[index] != center) {
                                                    tabCenters = tabCenters + (index to center)
                                                }
                                            }
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            label,
                                            color = textColor,
                                            fontSize = 15.sp,
                                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                        )
                                    }
                                }
                                Spacer(Modifier.weight(1f))
                                if (sourcePill) {
                                    Row(
                                        Modifier.height(32.dp).clip(RoundedCornerShape(16.dp))
                                            .background(Color(0xFFF6F7F8))
                                            .border(1.dp, Color(0xFFE3E5E7), RoundedCornerShape(16.dp))
                                            .padding(start = 12.dp, end = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            sourceLabel,
                                            color = Color(0xFF9499A0),
                                            fontSize = 11.5.sp,
                                            maxLines = 1,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Box(
                                            Modifier.size(26.dp).clip(CircleShape).background(Color.White)
                                                .border(1.dp, Color(0xFFE3E5E7), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            LocalGlyphIcon(LocalIcon.TV, OnlinePink, Modifier.size(14.dp), 1.7f)
                                        }
                                    }
                                } else {
                                    Text(sourceLabel, color = Muted, fontSize = 12.sp)
                                }
                            }
                            if (activeCenter != null) {
                                val indicatorX by animateDpAsState(
                                    targetValue = with(density) {
                                        ((activeCenter - indicatorWidthPx / 2f).coerceAtLeast(0f)).toDp()
                                    },
                                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                                    label = "详情标签下划线",
                                )
                                Box(
                                    Modifier.align(Alignment.BottomStart)
                                        .offset(x = indicatorX)
                                        .width(indicatorWidth).height(3.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(OnlinePink)
                                )
                            }
                        }
                        HorizontalDivider(color = if (detailsBackground == Color.White) Color(0xFFE3E5E7) else Color.White.copy(alpha = .08f), thickness = 1.dp)
                    }

                    val detailActions = PlayerDetailActions(
                        speed = speed,
                        positionMs = if (scrubbing) scrubPosition.toLong() else position,
                        screenshotEnabled = hasVideoFrame && playbackError == null && !savingFrame,
                        openSpeedPanel = {
                            playbackModeExpanded = false
                            speedMenuExpanded = true
                        },
                        openPlaybackModeSheet = {
                            speedMenuExpanded = false
                            playbackModeExpanded = true
                        },
                        takeScreenshot = { takeVideoScreenshot() },
                        seekTo = { target ->
                            player.seekTo(target.coerceAtLeast(0L))
                            player.playWhenReady = true
                            controlsVisible = true
                        },
                    )
                    CompositionLocalProvider(LocalPlayerDetailActions provides detailActions) {
                        AnimatedContent(
                            targetState = activePane,
                            transitionSpec = {
                                val forward = targetState > initialState
                                val enter = fadeIn(tween(180)) +
                                    slideInHorizontally(
                                        animationSpec = tween(220, easing = FastOutSlowInEasing),
                                        initialOffsetX = { width -> if (forward) width / 8 else -width / 8 },
                                    )
                                val exit = fadeOut(tween(120)) +
                                    slideOutHorizontally(
                                        animationSpec = tween(160, easing = FastOutSlowInEasing),
                                        targetOffsetX = { width -> if (forward) -width / 8 else width / 8 },
                                    )
                                enter togetherWith exit
                            },
                            label = "简介与媒体信息切换",
                        ) { tab ->
                            when {
                                tab == 0 -> Column(Modifier.fillMaxWidth()) {
                                    details(duration)
                                    playbackError?.let { message ->
                                        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                                            Text("播放失败：$message", color = Color(0xFFB42318), fontSize = 12.sp)
                                            TextButton(onClick = onRetry) { Text("重试", color = Pink) }
                                        }
                                    }
                                }
                                extraDetailPane != null -> extraDetailPane(duration)
                                else -> {
                                    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp)) {
                                        Text(detailTabs.getOrNull(1) ?: "评论", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.height(8.dp))
                                        Text(commentMessage, color = Muted, fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                val overlay = episodeOverlay
                if (overlay != null) {
                    PlayerDetailsOverlay(visible = overlayVisible) { overlay() }
                }

                // Keep the portrait sheet inside the details pane so the video
                // stays bright and the dimmed details remain visible behind it.
                PlayerDrawerVisibility(visible = speedMenuExpanded, fromBottom = true) {
                    SpeedPanel(
                        speed = speed,
                        mode = SpeedPanelMode.Sheet,
                        onDismiss = { speedMenuExpanded = false },
                        onSpeedChange = {
                            player.setPlaybackSpeed(it)
                            speed = it
                        }
                    )
                }

                val modeSheet = playbackModeSheet
                if (modeSheet != null) {
                    PlayerDrawerVisibility(visible = playbackModeExpanded, fromBottom = true) {
                        modeSheet { playbackModeExpanded = false }
                    }
                }
            }
        }

    }
}
}

@Composable
private fun ScreenshotPreviewCard(
    frame: Bitmap,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) +
            scaleIn(
                initialScale = .6f,
                transformOrigin = TransformOrigin(0f, 1f),
                animationSpec = tween(240, easing = FastOutSlowInEasing),
            ),
        exit = fadeOut(tween(160)) +
            scaleOut(
                targetScale = .92f,
                transformOrigin = TransformOrigin(0f, 1f),
                animationSpec = tween(180, easing = FastOutSlowInEasing),
            ),
        modifier = modifier,
    ) {
        Column(
            Modifier.width(96.dp)
                .shadow(10.dp, RoundedCornerShape(7.dp))
                .background(Color.White, RoundedCornerShape(7.dp))
                .padding(2.dp)
                .clip(RoundedCornerShape(5.dp)),
        ) {
            Box {
                Image(
                    bitmap = frame.asImageBitmap(),
                    contentDescription = "已保存截图预览",
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Color.Black.copy(alpha = .55f))
                        .padding(vertical = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("已保存到相册", color = Color.White, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PlayerDetailsOverlay(visible: Boolean, overlay: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(300, easing = CubicBezierEasing(.22f, 1f, .36f, 1f))
        ) + fadeIn(animationSpec = tween(180)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(240, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(140)),
    ) {
        overlay()
    }
}

@Composable
private fun BiliLoadingIndicator(message: String) {
    val transition = rememberInfiniteTransition(label = "播放器缓冲")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = CubicBezierEasing(.45f, .05f, .55f, .95f))),
        label = "粉色缓冲环旋转"
    )
    val televisionOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = -3f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "小电视浮动"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(44.dp).rotate(rotation)) {
                val u = size.width / 50f
                val diameter = androidx.compose.ui.geometry.Size(40f * u, 40f * u)
                drawCircle(Color.White.copy(alpha = .12f), radius = 20f * u,
                    style = Stroke(3.2f * u))
                drawArc(OnlinePink, startAngle = -90f, sweepAngle = 244f, useCenter = false,
                    topLeft = Offset(5f * u, 5f * u), size = diameter,
                    style = Stroke(3.2f * u, cap = StrokeCap.Round))
            }
            Canvas(Modifier.offset(y = televisionOffset.dp).size(20.dp)) {
                val u = size.width / 24f
                val tint = Color.White.copy(alpha = .9f)
                val stroke = Stroke(1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawPath(Path().apply {
                    moveTo(8f * u, 3f * u); lineTo(11f * u, 6f * u); lineTo(14f * u, 3f * u)
                }, tint, style = stroke)
                drawRoundRect(tint, Offset(4f * u, 6f * u),
                    androidx.compose.ui.geometry.Size(16f * u, 13f * u),
                    androidx.compose.ui.geometry.CornerRadius(3.5f * u), style = stroke)
                listOf(9f, 15f).forEach { x ->
                    drawLine(tint, Offset(x * u, 11f * u), Offset(x * u, 13f * u), 1.8f * u, StrokeCap.Round)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(message, color = Color.White.copy(alpha = .9f), fontSize = 12.sp,
            lineHeight = 17.sp, letterSpacing = .3.sp)
    }
}

@Composable
private fun PlaybackTimeLabel(
    position: Long,
    duration: Long,
    modifier: Modifier = Modifier,
    fullScreen: Boolean = false,
) {
    Text(
        "${formatPlaybackTime(position)} / ${formatPlaybackTime(duration, unknownWhenZero = true)}",
        modifier = modifier,
        color = Color.White.copy(alpha = .9f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        maxLines = 1
    )
}

@Composable
private fun PlayerBarIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable BoxScope.() -> Unit,
) {
    val description = contentDescription
    Box(
        modifier.size(32.dp).clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = description },
        contentAlignment = Alignment.Center,
        content = icon
    )
}

@Composable
private fun PlayerBarAction(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier.height(32.dp).clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = .92f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun PlayerQualityChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier.height(30.dp).clip(RoundedCornerShape(8.dp))
            .background(OnlinePink.copy(alpha = .18f))
            .border(1.dp, OnlinePink.copy(alpha = .55f), RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "画质 ${label.ifBlank { "选择" }}" }
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label.ifBlank { "画质" },
            color = OnlinePink,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun DanmakuPlaceholderBar(
    onPlaceholderClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Master switch pill from the reference bar, kept as a visible placeholder.
        Row(
            Modifier.height(30.dp).clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = .12f))
                .clickable(role = Role.Button, onClick = onPlaceholderClick)
                .semantics { contentDescription = "弹幕开关（开发中）" }
                .padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("弹", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(3.dp))
            Box(
                Modifier.size(13.dp).background(OnlinePink, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                    lineHeight = 8.sp)
            }
        }
        // Danmaku settings pill (弹 + gear).
        Row(
            Modifier.height(30.dp).clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = .12f))
                .clickable(role = Role.Button, onClick = onPlaceholderClick)
                .semantics { contentDescription = "弹幕设置（开发中）" }
                .padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("弹", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(3.dp))
            Canvas(Modifier.size(13.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension / 2f
                drawCircle(OnlinePink, radius * .42f, center,
                    style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                repeat(8) { index ->
                    val angle = Math.toRadians(index * 45.0)
                    val cos = kotlin.math.cos(angle).toFloat()
                    val sin = kotlin.math.sin(angle).toFloat()
                    drawLine(
                        OnlinePink,
                        Offset(center.x + cos * radius * .56f, center.y + sin * radius * .56f),
                        Offset(center.x + cos * radius * .9f, center.y + sin * radius * .9f),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                drawCircle(OnlinePink, radius * .12f, center)
            }
        }
        // Frosted input pill, matching the "发个友善的弹幕…" entry.
        Row(
            Modifier.weight(1f).height(30.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = .14f))
                .border(1.dp, Color.White.copy(alpha = .08f), CircleShape)
                .clickable(role = Role.Button, onClick = onPlaceholderClick)
                .semantics { contentDescription = "发送弹幕（开发中）" }
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "发个友善的弹幕见证当下…",
                color = Color.White.copy(alpha = .68f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                "发送",
                color = OnlinePink,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun NextEpisodeIcon(modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier) {
        val u = size.width / 24f
        drawPath(Path().apply {
            moveTo(5f * u, 5.5f * u)
            lineTo(15f * u, 12f * u)
            lineTo(5f * u, 18.5f * u)
            close()
        }, Color.White)
        drawRoundRect(
            Color.White,
            Offset(17.5f * u, 5f * u),
            androidx.compose.ui.geometry.Size(2.5f * u, 14f * u),
            androidx.compose.ui.geometry.CornerRadius(1f * u)
        )
    }
}

@Composable
private fun PlaybackQualityDrawer(
    options: List<PlayerQualityOption>,
    selectedQuality: Int?,
    onDismiss: () -> Unit,
    onQualitySelected: (Int) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color.Transparent)
                .clickable(onClick = onDismiss)
                .semantics { contentDescription = "关闭清晰度选择" }
        )
        Column(
            Modifier.align(Alignment.CenterEnd)
                .width(360.dp).fillMaxHeight()
                .padding(end = 8.dp, top = 8.dp, bottom = 8.dp)
                .navigationBarsPadding()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xF214161E))
                .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("清晰度与音质", color = Color.White, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold)
                    Text("切换后保持当前播放进度", color = Color.White.copy(alpha = .45f),
                        fontSize = 10.5.sp, modifier = Modifier.padding(top = 2.dp))
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                        .semantics { contentDescription = "收起清晰度选择" }
                ) {
                    Text("×", color = Color.White.copy(alpha = .72f), fontSize = 22.sp)
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(bottom = 8.dp),
                color = Color.White.copy(alpha = .08f)
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                options.forEach { option ->
                    val selected = option.qn == selectedQuality
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) OnlinePink.copy(alpha = .16f)
                                else Color.White.copy(alpha = .05f)
                            )
                            .border(
                                1.dp,
                                if (selected) OnlinePink.copy(alpha = .75f) else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onQualitySelected(option.qn) }
                            )
                            .semantics { contentDescription = "选择清晰度 ${option.label}" }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    option.label,
                                    color = if (selected) OnlinePink else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                option.badge?.let { badge ->
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        Modifier.clip(RoundedCornerShape(4.dp))
                                            .background(OnlinePink.copy(alpha = .22f))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(badge, color = OnlinePink, fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            if (option.description.isNotBlank()) {
                                Text(
                                    option.description,
                                    color = Color.White.copy(alpha = .45f),
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                            }
                        }
                        if (selected) {
                            Box(
                                Modifier.clip(RoundedCornerShape(20.dp))
                                    .background(OnlinePink)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("播放中", color = Color.White, fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoSeekBar(
    position: Float,
    duration: Long,
    bufferedPosition: Long,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep the visual layout compact; Compose still expands the touch target.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Slider(
            value = position.coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
            onValueChange = onSeek,
            onValueChangeFinished = onSeekFinished,
            valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
            enabled = duration > 0,
            modifier = modifier.height(24.dp).semantics { contentDescription = "播放进度" },
            thumb = { TelevisionSeekThumb() },
            track = { sliderState ->
                Canvas(Modifier.fillMaxWidth().height(2.5.dp)) {
                    val played = (sliderState.value / duration.coerceAtLeast(1L)).coerceIn(0f, 1f)
                    val buffered = (bufferedPosition.toFloat() / duration.coerceAtLeast(1L)).coerceIn(0f, 1f)
                    val start = Offset(0f, center.y)
                    drawLine(Color.White.copy(alpha = .3f), start, Offset(size.width, center.y),
                        size.height, StrokeCap.Round)
                    if (buffered > 0f) drawLine(Color.White.copy(alpha = .45f), start,
                        Offset(size.width * buffered, center.y), size.height, StrokeCap.Round)
                    if (played > 0f) drawLine(OnlinePink, start,
                        Offset(size.width * played, center.y), size.height, StrokeCap.Round)
                }
            }
        )
    }
}

@Composable
private fun PlayerDrawerVisibility(
    visible: Boolean,
    fromBottom: Boolean = false,
    content: @Composable () -> Unit,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = if (fromBottom) {
            fadeIn(tween(180)) + slideInVertically(
                initialOffsetY = { it }, animationSpec = tween(260, easing = FastOutSlowInEasing)
            )
        } else {
            fadeIn(tween(180)) + slideInHorizontally(
                initialOffsetX = { it }, animationSpec = tween(260, easing = FastOutSlowInEasing)
            )
        },
        exit = if (fromBottom) {
            fadeOut(tween(140)) + slideOutVertically(
                targetOffsetY = { it }, animationSpec = tween(220, easing = FastOutSlowInEasing)
            )
        } else {
            fadeOut(tween(140)) + slideOutHorizontally(
                targetOffsetX = { it }, animationSpec = tween(220, easing = FastOutSlowInEasing)
            )
        }
    ) {
        content()
    }
}

@Composable
private fun LocalVideoEpisodes(
    playlist: List<LocalVideo>,
    currentVideo: LocalVideo,
    onDismiss: () -> Unit,
    onVideoSelected: (LocalVideo) -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = playlist.indexOfFirst { it.uri == currentVideo.uri }.coerceAtLeast(0)
    )
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f))
            .clickable(onClick = onDismiss).semantics { contentDescription = "关闭选集" })
        Column(
            Modifier.align(Alignment.CenterEnd).width(360.dp).fillMaxHeight()
                .background(Color(0xF2121318))
                .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .navigationBarsPadding()
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp).height(56.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("分集(${playlist.size}P)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = "收起选集" }) {
                    Text("×", color = Color.White.copy(alpha = .72f), fontSize = 24.sp)
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = .08f))
            LazyColumn(Modifier.weight(1f).selectableGroup(), state = listState,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(playlist, key = { _, item -> item.uri.toString() }) { index, item ->
                    LocalEpisodeRow(index, item, item.uri == currentVideo.uri) { onVideoSelected(item) }
                }
            }
        }
    }
}

@Composable
private fun LocalEpisodeRow(index: Int, item: LocalVideo, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
            .background(if (selected) Pink.copy(alpha = .18f) else Color.White.copy(alpha = .05f))
            .border(1.dp, if (selected) Pink.copy(alpha = .8f) else Color.Transparent, RoundedCornerShape(11.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) EqualizerBars()
        Text(
            "P${index + 1} ${item.name}${if (selected) " (当前播放)" else ""}",
            color = if (selected) Pink else Color.White,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = if (selected) 4.dp else 0.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            formatVideoDuration(item.durationMs),
            color = if (selected) Pink else Color.White.copy(alpha = .45f),
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun PlaybackIcon(playing: Boolean, modifier: Modifier = Modifier.size(26.dp)) {
    Canvas(modifier) {
        val u = size.width / 24f
        if (playing) {
            listOf(6f, 14.2f).forEach { x ->
                drawRoundRect(Color.White, Offset(x * u, 5f * u),
                    androidx.compose.ui.geometry.Size(3.8f * u, 14f * u),
                    androidx.compose.ui.geometry.CornerRadius(1.2f * u))
            }
        } else {
            drawPath(Path().apply {
                moveTo(6.5f * u, 4.8f * u)
                cubicTo(5.7f * u, 4.3f * u, 4.7f * u, 4.9f * u, 4.7f * u, 5.8f * u)
                lineTo(4.7f * u, 18.2f * u)
                cubicTo(4.7f * u, 19.1f * u, 5.7f * u, 19.7f * u, 6.5f * u, 19.2f * u)
                lineTo(17.1f * u, 13f * u)
                cubicTo(17.9f * u, 12.6f * u, 17.9f * u, 11.4f * u, 17.1f * u, 11f * u)
                close()
            }, Color.White)
        }
    }
}

@Composable
private fun TelevisionSeekThumb() {
    Canvas(Modifier.size(width = 18.dp, height = 16.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawLine(OnlinePink, Offset(7.5f*u, 1.5f*u), Offset(10.5f*u, 4.5f*u), 2f*u, StrokeCap.Round)
        drawLine(OnlinePink, Offset(16.5f*u, 1.5f*u), Offset(13.5f*u, 4.5f*u), 2f*u, StrokeCap.Round)
        drawRoundRect(Color.White, Offset(2.5f*u, 4f*u), androidx.compose.ui.geometry.Size(19f*u, 14f*u),
            androidx.compose.ui.geometry.CornerRadius(4.5f*u))
        drawRoundRect(OnlinePink, Offset(2.5f*u, 4f*u), androidx.compose.ui.geometry.Size(19f*u, 14f*u),
            androidx.compose.ui.geometry.CornerRadius(4.5f*u), style = stroke)
        listOf(8.5f, 15.5f).forEach { x ->
            drawLine(OnlineText, Offset(x*u, 10f*u), Offset(x*u, 11.8f*u), 2f*u, StrokeCap.Round)
        }
    }
}

@Composable
private fun FullscreenIcon(fullScreen: Boolean, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val u = size.width / 24f
        val corners = Path().apply {
            if (fullScreen) {
                moveTo(2f*u, 8f*u); lineTo(8f*u, 8f*u); lineTo(8f*u, 2f*u)
                moveTo(16f*u, 2f*u); lineTo(16f*u, 8f*u); lineTo(22f*u, 8f*u)
                moveTo(22f*u, 16f*u); lineTo(16f*u, 16f*u); lineTo(16f*u, 22f*u)
                moveTo(8f*u, 22f*u); lineTo(8f*u, 16f*u); lineTo(2f*u, 16f*u)
            } else {
                moveTo(4f*u, 9f*u); lineTo(4f*u, 5f*u)
                quadraticTo(4f*u, 4f*u, 5f*u, 4f*u); lineTo(9f*u, 4f*u)
                moveTo(20f*u, 9f*u); lineTo(20f*u, 5f*u)
                quadraticTo(20f*u, 4f*u, 19f*u, 4f*u); lineTo(15f*u, 4f*u)
                moveTo(4f*u, 15f*u); lineTo(4f*u, 19f*u)
                quadraticTo(4f*u, 20f*u, 5f*u, 20f*u); lineTo(9f*u, 20f*u)
                moveTo(20f*u, 15f*u); lineTo(20f*u, 19f*u)
                quadraticTo(20f*u, 20f*u, 19f*u, 20f*u); lineTo(15f*u, 20f*u)
            }
        }
        drawPath(corners, Color.White.copy(alpha = .9f), style = Stroke(2.1f*u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}


private fun createTexturePlayerView(context: Context, player: ExoPlayer): PlayerView =
    (LayoutInflater.from(context).inflate(R.layout.video_player_view, null) as PlayerView).apply {
        this.player = player
        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
        keepScreenOn = true
    }


@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun createCompatiblePlayer(context: Context): ExoPlayer {
    val renderersFactory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
    return ExoPlayer.Builder(context, renderersFactory).build()
}

internal fun formatPlaybackTime(positionMs: Long, unknownWhenZero: Boolean = false): String {
    if (unknownWhenZero && positionMs <= 0L) return "--:--"
    val totalSeconds = (positionMs.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
