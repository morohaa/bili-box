package io.github.bilibox.feature.player

import android.Manifest
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.github.bilibox.core.playback.ListenQueue
import io.github.bilibox.core.playback.PlaybackSleepTimer
import io.github.bilibox.core.playback.PlaybackSpeedBoost
import io.github.bilibox.core.playback.SleepTimerMode
import io.github.bilibox.core.playback.longPressPlaybackSpeed
import io.github.bilibox.core.ui.component.playback.SleepTimerSheet
import io.github.bilibox.core.ui.component.playback.SpeedPanel
import io.github.bilibox.core.ui.component.playback.SpeedPanelMode
import io.github.bilibox.core.ui.theme.Pink
import io.github.bilibox.core.util.captureVideoFrame
import io.github.bilibox.core.util.saveVideoFrame
import io.github.bilibox.data.danmaku.Danmaku
import io.github.bilibox.data.danmaku.DanmakuSettings
import io.github.bilibox.feature.danmaku.DanmakuComposerDialog
import io.github.bilibox.feature.danmaku.DanmakuOverlay
import io.github.bilibox.feature.danmaku.DanmakuSettingsSheet
import io.github.bilibox.feature.listen.CollapsedListenOverlay
import io.github.bilibox.feature.listen.ListenControls
import io.github.bilibox.feature.listen.ListenInfo
import io.github.bilibox.feature.listen.ListenPlaybackEffect
import io.github.bilibox.feature.listen.ListenScreen
import io.github.bilibox.feature.listen.rememberListenCover
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PlayerGestureFeedbackState(
    val side: PlayerGestureSide,
    val level: Float
)

/** A selectable video stream quality shown in the fullscreen quality drawer. */

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
    // 只有在线视频提供弹幕；本地视频保持原来的播放器布局。
    danmakuSupported: Boolean = false,
    danmakuItems: List<Danmaku> = emptyList(),
    danmakuSettings: DanmakuSettings = DanmakuSettings(),
    danmakuLoading: Boolean = false,
    danmakuError: String? = null,
    onDanmakuSettingsChange: (DanmakuSettings) -> Unit = {},
    onSendDanmaku: (suspend (text: String, progressMs: Long) -> Result<Unit>)? = null,
    detailsBackground: Color = Color.White,
    showDetailTabs: Boolean = true,
    detailTabs: List<String> = listOf("简介", "评论"),
    sourcePill: Boolean = false,
    extraDetailPane: (@Composable (duration: Long) -> Unit)? = null,
    playbackModeSheet: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
    listenInfo: ListenInfo? = null,
    listenQueue: ListenQueue? = null,
    // One object instead of separate params: see ListenControls.
    listenControls: ListenControls? = null,
    // Files left in the folder / part list, used by the 按内容 timer options.
    remainingVideos: Int = 1,
    // Changes when the same player switches to another file; per-file UI resets.
    mediaKey: Any? = null,
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
    var sleepTimerOpen by remember(player) { mutableStateOf(false) }
    var danmakuSettingsOpen by remember(player) { mutableStateOf(false) }
    var danmakuComposerOpen by remember(player) { mutableStateOf(false) }
    var danmakuSending by remember(player) { mutableStateOf(false) }
    var danmakuSendError by remember(player) { mutableStateOf<String?>(null) }
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
    // 长按画面: hold at 2x, restore the user's own speed on release.
    var speedBoostActive by remember(player) { mutableStateOf(false) }
    var speedBoostSpeed by remember(player) { mutableFloatStateOf(PlaybackSpeedBoost) }
    var speedBeforeBoost by remember(player) { mutableFloatStateOf(1f) }
    val captureScope = rememberCoroutineScope()

    // 听视频: not keyed by player, so it survives switching to the next local file.
    var listening by rememberSaveable { mutableStateOf(false) }
    var listenPageOpen by rememberSaveable { mutableStateOf(false) }
    val listenReveal = remember { Animatable(0f) }
    var listenOrigin by remember { mutableStateOf(Offset.Zero) }
    var screenRootOffset by remember { mutableStateOf(Offset.Zero) }
    val sleepTimer = remember { PlaybackSleepTimer() }
    val listenCover = rememberListenCover(listenInfo, enabled = listening)
    ListenPlaybackEffect(player, listening, title, listenInfo?.subtitle.orEmpty(), listenCover, listenQueue)
    SleepTimerEffect(player, sleepTimer)
    LaunchedEffect(listenPageOpen) {
        listenReveal.animateTo(
            if (listenPageOpen) 1f else 0f,
            tween(if (listenPageOpen) 520 else 420, easing = CubicBezierEasing(.6f, 0f, .2f, 1f))
        )
    }
    fun enterListen() {
        if (!listening) {
            listening = true
            player.playWhenReady = true
            Toast.makeText(context, "已切换为听视频，画面已关闭", Toast.LENGTH_SHORT).show()
        }
        speedMenuExpanded = false
        playbackModeExpanded = false
        listenPageOpen = true
    }
    fun restoreVideo() {
        listening = false
        listenPageOpen = false
        controlsVisible = true
    }

    val originalWindowBrightness = remember(activity) { activity?.window?.attributes?.screenBrightness }
    DisposableEffect(activity) {
        val originalOrientation = activity?.requestedOrientation
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        val lightStatusBars = controller?.isAppearanceLightStatusBars
        val lightNavigationBars = controller?.isAppearanceLightNavigationBars
        val barsBehavior = controller?.systemBarsBehavior
        onDispose {
            if (originalOrientation != null) activity?.requestedOrientation = originalOrientation
            if (originalWindowBrightness != null) {
                activity?.window?.let { window ->
                    val attributes = window.attributes
                    attributes.screenBrightness = originalWindowBrightness
                    window.attributes = attributes
                }
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
        sleepTimerOpen = false
        episodesExpanded = false
        qualityMenuExpanded = false
        danmakuSettingsOpen = false
        if (!fullScreen) screenLocked = false
        activity?.let {
            WindowCompat.getInsetsController(it.window, it.window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = !fullScreen && detailsBackground == Color.White
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (fullScreen) {
                    hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }

    // Runs after the effect above, so the dark 听视频 page keeps light nav icons
    // even when opening it rotates the screen back to portrait.
    val listenVisible by remember { derivedStateOf { listenReveal.value > 0f } }
    LaunchedEffect(activity, fullScreen, listenVisible) {
        activity?.let {
            WindowCompat.getInsetsController(it.window, it.window.decorView).isAppearanceLightNavigationBars =
                !listenVisible && !fullScreen && detailsBackground == Color.White
        }
    }

    fun toggleOrientation() {
        controlsVisible = true
        activity?.requestedOrientation = if (fullScreen) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    fun navigateBack() {
        if (fullScreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            onBack()
        }
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
        // A held 2x gesture owns the finger; do not also drag brightness/volume.
        if (speedBoostActive) return
        verticalGestureActive = true
        if (side == PlayerGestureSide.LEFT) {
            screenBrightness = activity?.let(::readScreenBrightness) ?: screenBrightness
        } else {
            volumeGestureRemainder = 0f
        }
        updateGestureFeedback(side)
    }

    fun adjustFromVerticalGesture(side: PlayerGestureSide, deltaFraction: Float) {
        if (speedBoostActive) return
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

    fun startSpeedBoost() {
        if (screenLocked || speedBoostActive) return
        val current = player.playbackParameters.speed
        val target = longPressPlaybackSpeed(current)
        speedBeforeBoost = current
        speedBoostSpeed = target
        speedBoostActive = true
        gestureFeedback = null
        player.setPlaybackSpeed(target)
        speed = target
    }

    fun endSpeedBoost() {
        if (!speedBoostActive) return
        speedBoostActive = false
        // The screen may be going away mid-hold and the player already released.
        runCatching { player.setPlaybackSpeed(speedBeforeBoost) }
        speed = speedBeforeBoost
    }

    fun takeVideoScreenshot() {
        if (savingFrame || screenLocked || !hasVideoFrame || playbackError != null) return
        val frame = try {
            playerView?.let(::captureVideoFrame)
        } catch (_: Exception) {
            null
        }
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
        if (granted) {
            takeVideoScreenshot()
        } else {
            Toast.makeText(context, "需要存储权限才能保存视频画面", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                hasVideoFrame = true
            }

            // A queue can switch files on the same player; wait for the new first frame.
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                hasVideoFrame = false
            }
            override fun onPlayWhenReadyChanged(ready: Boolean, reason: Int) {
                playWhenReady = ready
            }
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
    val latestDefaultDuration by rememberUpdatedState(defaultDurationMs)
    LaunchedEffect(player) {
        while (true) {
            duration = player.duration.takeIf { it > 0 } ?: latestDefaultDuration
            if (!scrubbing) position = player.currentPosition.coerceAtLeast(0L)
            bufferedPosition = player.bufferedPosition.coerceAtLeast(0L)
            isPlaying = player.isPlaying
            speed = player.playbackParameters.speed
            delay(250)
        }
    }
    // The local queue keeps one player across files, so reset what a fresh player
    // used to reset. Playback speed deliberately carries over to the next file.
    var lastMediaKey by remember(player) { mutableStateOf(mediaKey) }
    // Leaving the screen mid-hold must not leave a shared player stuck at 2x.
    val speedBoostActiveState = rememberUpdatedState(speedBoostActive)
    val speedBeforeBoostState = rememberUpdatedState(speedBeforeBoost)
    DisposableEffect(player) {
        onDispose {
            if (speedBoostActiveState.value) {
                runCatching { player.setPlaybackSpeed(speedBeforeBoostState.value) }
            }
        }
    }
    LaunchedEffect(player, mediaKey) {
        if (mediaKey == lastMediaKey) return@LaunchedEffect
        lastMediaKey = mediaKey
        detailsTab = 0
        controlsVisible = true
        scrubbing = false
        speedMenuExpanded = false
        playbackModeExpanded = false
        sleepTimerOpen = false
        episodesExpanded = false
        qualityMenuExpanded = false
        duration = defaultDurationMs
        position = player.currentPosition.coerceAtLeast(0L)
    }
    LaunchedEffect(controlsVisible, isPlaying, screenLocked, scrubbing, speedMenuExpanded, playbackModeExpanded, sleepTimerOpen, episodesExpanded, qualityMenuExpanded) {
        if (controlsVisible && (isPlaying || screenLocked) && !scrubbing &&
            !speedMenuExpanded && !playbackModeExpanded && !sleepTimerOpen && !episodesExpanded && !qualityMenuExpanded
        ) {
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
    BackHandler(enabled = !listenPageOpen) {
        when {
            screenLocked -> controlsVisible = true
            danmakuComposerOpen -> if (!danmakuSending) danmakuComposerOpen = false
            danmakuSettingsOpen -> danmakuSettingsOpen = false
            episodeOverlayDismiss != null -> episodeOverlayDismiss()
            qualityMenuExpanded -> qualityMenuExpanded = false
            episodesExpanded -> episodesExpanded = false
            playbackModeExpanded -> playbackModeExpanded = false
            sleepTimerOpen -> sleepTimerOpen = false
            speedMenuExpanded -> speedMenuExpanded = false
            else -> navigateBack()
        }
    }

    // The speed panel takes the whole stage: hide the chrome behind it so the
    // runner and the ruler are the only things competing for attention.
    val showChrome = controlsVisible && !screenLocked && !(fullScreen && speedMenuExpanded)
    val sleepTimerRemainingMs = when (sleepTimer.mode) {
        SleepTimerMode.TIME -> (sleepTimer.endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

        SleepTimerMode.VIDEOS ->
            if (sleepTimer.videosLeft <= 1) (duration - position).coerceAtLeast(0L) else 0L

        SleepTimerMode.OFF -> 0L
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { screenRootOffset = it.positionInRoot() }) {
        Column(
            Modifier.fillMaxSize().background(Color.Black)
                .then(if (fullScreen) Modifier else Modifier.statusBarsPadding())
        ) {
            Box(
                (if (fullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { viewContext -> createTexturePlayerView(viewContext, player).also { playerView = it } },
                    update = {
                        it.player = player
                        // Let the screen sleep while only audio is playing.
                        it.keepScreenOn = !listening
                        playerView = it
                    },
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
                    speedBoostEnabled = !screenLocked && !speedMenuExpanded &&
                        !playbackModeExpanded && !sleepTimerOpen && playbackError == null,
                    onSpeedBoostStart = { startSpeedBoost() },
                    onSpeedBoostEnd = { endSpeedBoost() },
                    modifier = Modifier.fillMaxSize()
                )
                if (danmakuSupported && !listening && !listenPageOpen) {
                    DanmakuOverlay(
                        player = player,
                        items = danmakuItems,
                        settings = danmakuSettings,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (listening && !listenPageOpen) {
                    CollapsedListenOverlay(
                        cover = listenCover,
                        playing = isPlaying,
                        onExpand = { enterListen() },
                        onRestoreVideo = { restoreVideo() },
                    )
                }
                if (playbackError != null) {
                    Column(
                        Modifier.padding(horizontal = 24.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("暂时无法播放", color = Color.White, fontSize = 14.sp)
                        TextButton(onClick = onRetry) { Text("重试", color = Pink) }
                    }
                } else if (loadingMessage != null || playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE) {
                    BiliLoadingIndicator(
                        message = loadingMessage ?: if (playbackState == Player.STATE_BUFFERING) "正在缓冲…" else "等待视频加载"
                    )
                }
                if (sleepTimer.warning && sleepTimer.active) {
                    SleepTimerWarning(
                        remainingMs = sleepTimerRemainingMs,
                        onExtend = { sleepTimer.extend(10, sleepTimerRemainingMs) },
                        onDismiss = { sleepTimer.dismissWarning() },
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .padding(bottom = if (fullScreen) 150.dp else 72.dp),
                    )
                }
                if (showChrome) {
                    Box(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(56.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = .75f),
                                        Color.Black.copy(alpha = .3f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(if (fullScreen) 150.dp else 64.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = .4f),
                                        Color.Black.copy(alpha = .88f)
                                    )
                                )
                            )
                    )
                    PlayerBottomBar(
                        state = PlayerBottomBarState(
                            fullScreen = fullScreen,
                            position = position,
                            duration = duration,
                            bufferedPosition = bufferedPosition,
                            scrubbing = scrubbing,
                            scrubPosition = scrubPosition,
                            playWhenReady = playWhenReady,
                            playbackState = playbackState,
                            danmakuSupported = danmakuSupported,
                            danmakuSettings = danmakuSettings,
                            danmakuLoading = danmakuLoading,
                            danmakuError = danmakuError,
                            canSendDanmaku = onSendDanmaku != null,
                            episodesAvailable = episodes != null,
                            speed = speed,
                            qualityOptions = qualityOptions,
                            selectedQuality = selectedQuality,
                        ),
                        actions = PlayerBottomBarActions(
                            onSeekStart = {
                                scrubbing = true
                                scrubPosition = it
                            },
                            onSeekFinished = {
                                player.seekTo(scrubPosition.toLong())
                                position = scrubPosition.toLong()
                                scrubbing = false
                            },
                            onTogglePlayback = { togglePlayback() },
                            onNextEpisode = {
                                val next = onNextEpisode
                                if (next != null) {
                                    next()
                                } else {
                                    Toast.makeText(context, "已经是最后一集", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onDanmakuToggle = {
                                onDanmakuSettingsChange(
                                    danmakuSettings.copy(enabled = !danmakuSettings.enabled)
                                )
                            },
                            onDanmakuSettings = { danmakuSettingsOpen = true },
                            onDanmakuSend = {
                                if (onSendDanmaku == null) {
                                    Toast.makeText(context, "登录后才能发送弹幕", Toast.LENGTH_SHORT).show()
                                } else {
                                    danmakuSendError = null
                                    danmakuComposerOpen = true
                                }
                            },
                            onEpisodes = {
                                speedMenuExpanded = false
                                qualityMenuExpanded = false
                                episodesExpanded = true
                            },
                            onSpeed = {
                                episodesExpanded = false
                                qualityMenuExpanded = false
                                speedMenuExpanded = true
                            },
                            onQuality = {
                                episodesExpanded = false
                                speedMenuExpanded = false
                                qualityMenuExpanded = true
                            },
                            onToggleOrientation = { toggleOrientation() },
                        ),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .then(
                                if (fullScreen) {
                                    Modifier
                                        .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 6.dp)
                                } else {
                                    Modifier.padding(horizontal = 6.dp)
                                }
                            ),
                    )
                }
                if (showChrome) {
                    PlayerTopBar(
                        fullScreen = fullScreen,
                        title = title,
                        listening = listening,
                        showListenButton = listenInfo != null,
                        speed = speed,
                        onBack = { navigateBack() },
                        onListenClick = { enterListen() },
                        onListenPositioned = { listenOrigin = it },
                        onSpeedClick = { speedMenuExpanded = true },
                        modifier = Modifier.align(Alignment.TopStart),
                    )
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
                                ) != PackageManager.PERMISSION_GRANTED
                            ) {
                                screenshotPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {
                                takeVideoScreenshot()
                            }
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
                PlayerSpeedBoostIndicator(
                    active = speedBoostActive,
                    speed = speedBoostSpeed,
                    // Keep the feedback near the top edge: centering it covered the
                    // part of the frame the user was watching while holding.
                    modifier = Modifier.align(Alignment.TopCenter)
                        .padding(top = if (fullScreen) 56.dp else 52.dp)
                )
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
                PlayerDetailsPane(
                    modifier = Modifier.weight(1f),
                    detailsBackground = detailsBackground,
                    showDetailTabs = showDetailTabs,
                    detailTabs = detailTabs,
                    detailsTab = detailsTab,
                    onDetailsTabChange = { detailsTab = it },
                    sourcePill = sourcePill,
                    sourceLabel = sourceLabel,
                    playbackError = playbackError,
                    onRetry = onRetry,
                    commentMessage = commentMessage,
                    duration = duration,
                    positionMs = if (scrubbing) scrubPosition.toLong() else position,
                    speed = speed,
                    screenshotEnabled = hasVideoFrame && playbackError == null && !savingFrame,
                    sleepTimerActive = sleepTimer.active,
                    onOpenSpeedPanel = {
                        playbackModeExpanded = false
                        speedMenuExpanded = true
                    },
                    onOpenPlaybackModeSheet = {
                        speedMenuExpanded = false
                        playbackModeExpanded = true
                    },
                    onOpenSleepTimerSheet = {
                        speedMenuExpanded = false
                        playbackModeExpanded = false
                        sleepTimerOpen = true
                    },
                    onTakeScreenshot = { takeVideoScreenshot() },
                    onSeekTo = { target ->
                        player.seekTo(target.coerceAtLeast(0L))
                        player.playWhenReady = true
                        controlsVisible = true
                    },
                    details = details,
                    extraDetailPane = extraDetailPane,
                    episodeOverlay = episodeOverlay,
                    episodeOverlayVisible = episodeOverlayVisible,
                    speedMenuExpanded = speedMenuExpanded,
                    onDismissSpeedSheet = { speedMenuExpanded = false },
                    onSpeedChange = {
                        player.setPlaybackSpeed(it)
                        speed = it
                    },
                    playbackModeExpanded = playbackModeExpanded,
                    playbackModeSheet = playbackModeSheet,
                    onDismissPlaybackMode = { playbackModeExpanded = false },
                )
            }
        }
        // The timer lives on the player, so it keeps counting while the sheet is
        // closed and the queue switches files. It is a sibling of the player
        // column so the panel covers the video too, like the reference design.
        if (!fullScreen) {
            PlayerDrawerVisibility(visible = sleepTimerOpen, fromBottom = true) {
                SleepTimerSheet(
                    timer = sleepTimer,
                    remainingVideos = remainingVideos,
                    currentRemainingMs = (duration - position).coerceAtLeast(0L),
                    durationMs = duration,
                    onDismiss = { sleepTimerOpen = false },
                )
            }
        }
        // 弹幕输入与设置放在播放器外层：弹层要盖住视频和详情，而不是被视频 Box 裁掉。
        if (danmakuSupported && danmakuComposerOpen) {
            val send = onSendDanmaku
            if (send != null) {
                DanmakuComposerDialog(
                    sending = danmakuSending,
                    error = danmakuSendError,
                    onDismiss = { if (!danmakuSending) danmakuComposerOpen = false },
                    onSend = { text ->
                        if (!danmakuSending) {
                            danmakuSending = true
                            danmakuSendError = null
                            captureScope.launch {
                                try {
                                    send(text, player.currentPosition.coerceAtLeast(0L))
                                    danmakuComposerOpen = false
                                    Toast.makeText(context, "弹幕已发送", Toast.LENGTH_SHORT).show()
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    danmakuSendError = error.message ?: "弹幕发送失败"
                                } finally {
                                    danmakuSending = false
                                }
                            }
                        }
                    },
                )
            }
        }
        if (danmakuSupported && danmakuSettingsOpen) {
            DanmakuSettingsSheet(
                settings = danmakuSettings,
                onDismiss = { danmakuSettingsOpen = false },
                onSettingsChange = onDanmakuSettingsChange,
            )
        }
        // 听视频 page: stays composed while its reveal animation is running.
        val info = listenInfo
        if (info != null && listenVisible) {
            ListenScreen(
                reveal = { listenReveal.value },
                origin = listenOrigin - screenRootOffset,
                open = listenPageOpen,
                title = title,
                info = info,
                cover = listenCover,
                playing = playWhenReady && playbackState != Player.STATE_ENDED,
                visualizing = isPlaying,
                buffering = playbackState == Player.STATE_BUFFERING,
                positionMs = position,
                durationMs = duration,
                speed = speed,
                timer = sleepTimer,
                mode = listenControls?.mode ?: 0,
                remainingVideos = remainingVideos,
                hasPrevious = listenControls?.hasPrevious ?: false,
                // Online videos pass no controls; their 下一个 is the next P as before.
                hasNext = listenControls?.hasNext ?: (onNextEpisode != null),
                onTogglePlay = {
                    if (player.playbackState == Player.STATE_ENDED) {
                        player.seekTo(0L)
                        player.playWhenReady = true
                    } else {
                        player.playWhenReady = !player.playWhenReady
                    }
                },
                onSeek = { target ->
                    player.seekTo(target.coerceAtLeast(0L))
                    position = target.coerceAtLeast(0L)
                },
                onPrevious = listenControls?.onPrevious ?: {},
                onNext = { (listenControls?.onNext ?: onNextEpisode)?.invoke() },
                onSpeedChange = {
                    player.setPlaybackSpeed(it)
                    speed = it
                },
                onCycleMode = listenControls?.onCycleMode ?: {},
                onOpenQueue = listenControls?.onOpenQueue ?: {},
                onCollapse = { listenPageOpen = false },
                onRestoreVideo = { restoreVideo() },
            )
        }
    }
}
