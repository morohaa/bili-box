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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlin.math.abs
import kotlin.math.roundToInt


private data class PlayerGestureFeedbackState(
    val side: PlayerGestureSide,
    val level: Float
)

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
    var liked by remember(video.uri) { mutableStateOf(false) }
    var saved by remember(video.uri) { mutableStateOf(false) }
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

    DisposableEffect(player, activity) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                val detail = (error.cause?.message ?: error.message)?.takeIf { it.isNotBlank() }
                playbackError = "${error.errorCodeName}${detail?.let { "：$it" }.orEmpty()}"
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_READY) playbackError = null
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
    VideoPlayerScreen(
        player = player,
        title = video.name,
        defaultDurationMs = video.durationMs,
        playbackError = playbackError,
        sourceLabel = "本地视频",
        commentMessage = "本地视频暂时没有评论",
        onBack = onBack,
        onRetry = {
            playbackError = null
            val retryPosition = player.currentPosition.coerceAtLeast(0L)
            player.stop()
            player.setMediaItem(androidx.media3.common.MediaItem.fromUri(video.uri), retryPosition)
            player.prepare()
            player.playWhenReady = true
        },
        episodes = { dismiss ->
            LocalVideoEpisodes(playlist, video, onDismiss = dismiss, onVideoSelected = {
                dismiss()
                onVideoSelected(it)
            })
        },
        details = { duration ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text(video.name, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "本地视频 · ${Formatter.formatFileSize(context, video.sizeBytes)} · ${formatVideoDuration(duration)}",
                    color = Muted,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    VideoAction(0, if (liked) "已点赞" else "点赞", liked) { liked = !liked }
                    VideoAction(1, "投币", false, enabled = false) { }
                    VideoAction(2, if (saved) "已收藏" else "收藏", saved) { saved = !saved }
                    VideoAction(3, "分享", false) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "video/*"
                            putExtra(Intent.EXTRA_STREAM, video.uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "分享视频"))
                    }
                }
                HorizontalDivider(Modifier.padding(top = 14.dp), color = Color(0xFFE9EAEC), thickness = 1.dp)
                Spacer(Modifier.height(14.dp))
                Text("视频信息", color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("${video.name}\n${Formatter.formatFileSize(context, video.sizeBytes)}", color = Muted, fontSize = 12.sp, lineHeight = 19.sp)
            }
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
    episodeOverlayDismiss: (() -> Unit)? = null,
    detailsBackground: Color = Color.White,
    showDetailTabs: Boolean = true,
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
    var playbackState by remember(player) { mutableIntStateOf(player.playbackState) }
    var detailsTab by remember(player) { mutableIntStateOf(0) }
    var controlsVisible by remember(player) { mutableStateOf(true) }
    var screenLocked by remember(player) { mutableStateOf(false) }
    var orientationBeforeLock by remember(player) { mutableStateOf<Int?>(null) }
    var playerView by remember(player) { mutableStateOf<PlayerView?>(null) }
    var hasVideoFrame by remember(player) { mutableStateOf(false) }
    var savingFrame by remember { mutableStateOf(false) }
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
        episodesExpanded = false
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
        savingFrame = true
        captureScope.launch {
            try {
                withContext(Dispatchers.IO) { saveVideoFrame(context, frame) }
                Toast.makeText(context, "已保存到相册 Pictures/BiliBox", Toast.LENGTH_SHORT).show()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Toast.makeText(context, "截帧保存失败，请检查存储空间或权限后重试", Toast.LENGTH_SHORT).show()
            } finally {
                frame.recycle()
                savingFrame = false
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
    LaunchedEffect(controlsVisible, isPlaying, screenLocked, scrubbing, speedMenuExpanded, episodesExpanded) {
        if (controlsVisible && (isPlaying || screenLocked) && !scrubbing && !speedMenuExpanded && !episodesExpanded) {
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
    BackHandler {
        when {
            screenLocked -> controlsVisible = true
            episodeOverlayDismiss != null -> episodeOverlayDismiss()
            episodesExpanded -> episodesExpanded = false
            speedMenuExpanded -> speedMenuExpanded = false
            else -> navigateBack()
        }
    }

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
            if (controlsVisible && !screenLocked) {
                Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(56.dp)
                    .background(Brush.verticalGradient(listOf(
                        Color.Black.copy(alpha = .75f), Color.Black.copy(alpha = .3f), Color.Transparent
                    ))))
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(if (fullScreen) 128.dp else 64.dp)
                    .background(Brush.verticalGradient(listOf(
                        Color.Transparent, Color.Black.copy(alpha = .4f), Color.Black.copy(alpha = .85f)
                    ))))
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .then(if (fullScreen) Modifier.padding(top = 12.dp)
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
                        Row(
                            Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            seekBar(Modifier.fillMaxWidth())
                        }
                        Row(
                            Modifier.fillMaxWidth().height(48.dp)
                                .padding(start = 18.dp, end = 36.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlaybackTimeLabel(
                                position = if (scrubbing) scrubPosition.toLong() else position,
                                duration = duration,
                                modifier = Modifier.padding(start = 9.dp, end = 10.dp)
                            )
                            Box(
                                modifier = Modifier.size(48.dp).semantics {
                                    contentDescription = if (showPause) "暂停" else "播放"
                                }.clickable(role = Role.Button, onClick = { togglePlayback() }),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                PlaybackIcon(showPause, Modifier.padding(start = 4.dp).size(26.dp))
                            }
                            Spacer(Modifier.weight(1f))
                            if (episodes != null) {
                                TextButton(
                                    onClick = { episodesExpanded = true },
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Text("选集", color = Color.White, fontSize = 12.sp)
                                }
                            }
                            Box {
                                TextButton(
                                    onClick = { speedMenuExpanded = true },
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Text(
                                        if (speed == 1f) "倍速" else "${speed}x",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        modifier = Modifier.semantics { contentDescription = "倍速" }
                                    )
                                }
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
            if (controlsVisible && !screenLocked) Row(
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
                Box {
                    if (!fullScreen) {
                        IconButton(onClick = { speedMenuExpanded = true }, modifier = Modifier.size(40.dp)
                            .semantics { contentDescription = "更多播放设置" }) {
                            Canvas(Modifier.size(20.dp)) {
                                listOf(.22f, .5f, .78f).forEach { x ->
                                    drawCircle(Color.White, size.width * 1.75f / 24f,
                                        Offset(size.width * x, size.height * .5f))
                                }
                            }
                        }
                        PlaybackSpeedMenu(speedMenuExpanded, speed, dark = false,
                            onDismiss = { speedMenuExpanded = false }, onSpeedSelected = {
                                player.setPlaybackSpeed(it)
                                speed = it
                                speedMenuExpanded = false
                            })
                    }
                }
            }
            if (fullScreen && controlsVisible) {
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
                    PlaybackSpeedDrawer(
                        speed = speed,
                        onDismiss = { speedMenuExpanded = false },
                        onSpeedSelected = {
                            player.setPlaybackSpeed(it)
                            speed = it
                            speedMenuExpanded = false
                        },
                        onSpeedChanged = {
                            player.setPlaybackSpeed(it)
                            speed = it
                        },
                        sideDrawer = true
                    )
                }
            }
        }

        if (!fullScreen) {
            val detailOverlay = episodeOverlay
            Column(
                Modifier.weight(1f).fillMaxWidth().background(detailsBackground)
                    .then(if (detailOverlay == null) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .navigationBarsPadding()
            ) {
                if (detailOverlay != null) {
                    detailOverlay.invoke()
                } else {
                    if (showDetailTabs) {
                        Row(
                            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("简介", "评论").forEachIndexed { index, label ->
                                Column(
                                    Modifier.fillMaxHeight().clickable { detailsTab = index }.padding(horizontal = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(label, color = if (detailsTab == index) Pink else Muted, fontSize = 15.sp,
                                        fontWeight = if (detailsTab == index) FontWeight.SemiBold else FontWeight.Normal)
                                    Spacer(Modifier.height(5.dp))
                                    Box(Modifier.width(30.dp).height(2.dp).background(if (detailsTab == index) Pink else Color.Transparent, CircleShape))
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            Text(sourceLabel, color = Muted, fontSize = 12.sp)
                        }
                        HorizontalDivider(color = if (detailsBackground == Color.White) Color(0xFFE9EAEC) else Color.White.copy(alpha = .08f), thickness = 1.dp)
                    }

                    if (!showDetailTabs || detailsTab == 0) {
                        details(duration)
                        playbackError?.let { message ->
                            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                                Text("播放失败：$message", color = Color(0xFFB42318), fontSize = 12.sp)
                                TextButton(onClick = onRetry) { Text("重试", color = Pink) }
                            }
                        }
                    } else {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp)) {
                            Text("评论", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Text(commentMessage, color = Muted, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
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
private fun PlaybackSpeedMenu(
    expanded: Boolean,
    speed: Float,
    dark: Boolean,
    onDismiss: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss,
        containerColor = if (dark) Color(0xFF242424) else Color.White) {
        playbackSpeedOptions.forEach { option ->
            DropdownMenuItem(
                text = { Text("倍速 ${option}x", color = when {
                    speed == option -> Pink
                    dark -> Color.White
                    else -> Ink
                }) },
                onClick = { onSpeedSelected(option) }
            )
        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackSpeedDrawer(
    speed: Float,
    onDismiss: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onSpeedChanged: (Float) -> Unit,
    sideDrawer: Boolean = true,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color.Transparent)
                .clickable(onClick = onDismiss)
                .semantics { contentDescription = "关闭倍速选择" }
        )
        Column(
            Modifier.align(if (sideDrawer) Alignment.CenterEnd else Alignment.BottomCenter)
                .then(
                    if (sideDrawer) {
                        Modifier.width(360.dp).fillMaxHeight()
                            .padding(end = 8.dp, top = 8.dp, bottom = 8.dp)
                            .navigationBarsPadding()
                    } else {
                        Modifier.widthIn(max = 540.dp).fillMaxWidth()
                            .padding(start = 14.dp, top = 0.dp, end = 14.dp, bottom = 72.dp)
                            .navigationBarsPadding()
                    }
                )
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xD614161E))
                .border(
                    1.dp,
                    Color.White.copy(alpha = .08f),
                    RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.Top
        ) {
            val shownSpeed = speed.coerceIn(.25f, 3f)
            val speedText = String.format(Locale.US, "%.2f", shownSpeed)
            val snapAnchors = listOf(.5f, 1f, 1.25f, 1.5f, 2f, 3f)
            fun quantizeSpeed(raw: Float): Float {
                val stepped = (raw * 20f).roundToInt().coerceIn(5, 60) / 20f
                return snapAnchors.firstOrNull { abs(it - stepped) <= .02f } ?: stepped
            }
            val animationTransition = rememberInfiniteTransition(label = "倍速播放动效")
            val animationProgress by animationTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "倍速播放动效进度"
            )

            // The first row mirrors the HTML panel: readout on the left and a compact
            // animated fast-forward mark on the right.
            Row(
                Modifier.fillMaxWidth().height(82.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    Modifier.background(Color.White.copy(alpha = .05f), RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = .1f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(speedText, color = Pink, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        Text("x", color = Pink, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 3.dp, bottom = 5.dp))
                    }
                }
                Box(Modifier.size(148.dp).height(58.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val centerY = size.height / 2f
                        repeat(3) { index ->
                            val phase = (animationProgress + index * .24f) % 1f
                            val x = size.width * .18f + phase * size.width * .72f
                            val alpha = (1f - phase).coerceIn(.12f, .9f)
                            val path = Path().apply {
                                moveTo(x - 9.dp.toPx(), centerY - 13.dp.toPx())
                                lineTo(x + 8.dp.toPx(), centerY)
                                lineTo(x - 9.dp.toPx(), centerY + 13.dp.toPx())
                                close()
                            }
                            drawPath(path, Pink.copy(alpha = alpha))
                        }
                        drawLine(
                            Color.White.copy(alpha = .28f),
                            Offset(size.width * .08f, centerY + 20.dp.toPx()),
                            Offset(size.width * .92f, centerY + 20.dp.toPx()),
                            strokeWidth = 1.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    Text("倍速播放", color = Color.White.copy(alpha = .62f), fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.BottomCenter))
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp)
                        .semantics { contentDescription = "收起倍速选择" }
                ) {
                    Canvas(Modifier.size(16.dp)) {
                        val stroke = Stroke(2.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(
                            Color.White.copy(alpha = .72f),
                            Offset(2.dp.toPx(), 2.dp.toPx()),
                            Offset(size.width - 2.dp.toPx(), size.height - 2.dp.toPx()),
                            stroke.width,
                            StrokeCap.Round
                        )
                        drawLine(
                            Color.White.copy(alpha = .72f),
                            Offset(size.width - 2.dp.toPx(), 2.dp.toPx()),
                            Offset(2.dp.toPx(), size.height - 2.dp.toPx()),
                            stroke.width,
                            StrokeCap.Round
                        )
                    }
                }
            }
            // Progress track with the two precise 0.05x step controls on either side.
            Row(
                Modifier.fillMaxWidth().height(56.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                    Box(
                        Modifier.height(40.dp).width(62.dp).clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = .08f))
                            .border(1.dp, Color.White.copy(alpha = .1f), RoundedCornerShape(12.dp))
                            .clickable {
                                onSpeedChanged(quantizeSpeed(shownSpeed - .05f))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("-0.05", color = Color.White, fontSize = 13.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                    Slider(
                        value = shownSpeed,
                        onValueChange = { value ->
                            onSpeedChanged(quantizeSpeed(value))
                        },
                        valueRange = .25f..3f,
                        steps = 54,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Transparent,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent,
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        ),
                        thumb = {
                            Box(
                                Modifier.size(34.dp)
                                    .background(Pink, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(Modifier.size(24.dp).background(Color.White, CircleShape)) {
                                    Box(Modifier.size(8.dp).background(Pink, CircleShape))
                                }
                            }
                        },
                        track = { sliderState ->
                            Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                                val trackHeight = 10.dp.toPx()
                                val centerY = size.height / 2f
                                val progress = ((sliderState.value - .25f) / 2.75f).coerceIn(0f, 1f)
                                drawRoundRect(
                                    color = Color.White.copy(alpha = .18f),
                                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                                    size = androidx.compose.ui.geometry.Size(size.width, trackHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f)
                                )
                                drawRoundRect(
                                    color = Pink,
                                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                                    size = androidx.compose.ui.geometry.Size(size.width * progress, trackHeight),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackHeight / 2f)
                                )
                                listOf(.5f, 1f, 1.25f, 1.5f, 2f, 3f).forEach { anchor ->
                                    val x = size.width * ((anchor - .25f) / 2.75f)
                                    drawCircle(
                                        color = if (anchor <= sliderState.value) Color.White.copy(alpha = .9f)
                                        else Color.White.copy(alpha = .34f),
                                        radius = 4.dp.toPx(),
                                        center = Offset(x, centerY)
                                    )
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                            .semantics { contentDescription = "自定义倍速 0.25x 到 3.0x" }
                    )
                    Box(
                        Modifier.height(40.dp).width(62.dp).clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = .08f))
                            .border(1.dp, Color.White.copy(alpha = .1f), RoundedCornerShape(12.dp))
                            .clickable {
                                onSpeedChanged(quantizeSpeed(shownSpeed + .05f))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+0.05", color = Color.White, fontSize = 13.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 0.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width(62.dp))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("0.25x", "1.0x", "2.0x", "3.0x").forEach { label ->
                            Text(label, color = Color.White.copy(alpha = .48f), fontSize = 10.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                    Spacer(Modifier.width(62.dp))
            }
            HorizontalDivider(
                modifier = Modifier.padding(bottom = 10.dp),
                color = Color.White.copy(alpha = .1f)
            )
            // Keep the common presets together on one final row.
            Row(
                Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    playbackSpeedQuickOptions.forEach { option ->
                        val selected = speed == option
                        Box(
                            Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) Pink.copy(alpha = .2f)
                                    else Color.White.copy(alpha = .05f)
                                )
                                .border(
                                    1.dp,
                                    if (selected) Pink else Color.White.copy(alpha = .1f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable(role = Role.Button) { onSpeedSelected(option) }
                                .semantics {
                                    contentDescription = "选择倍速 ${option}x"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (option == 1f) "1.0x" else "${option}x",
                                color = if (selected) Pink else Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
            }
        }
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
