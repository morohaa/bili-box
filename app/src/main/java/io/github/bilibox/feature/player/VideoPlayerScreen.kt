package io.github.bilibox.feature.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.github.bilibox.core.playback.ListenQueue
import io.github.bilibox.core.playback.PlaybackSleepTimer
import io.github.bilibox.core.playback.SleepTimerMode
import io.github.bilibox.core.ui.component.playback.SleepTimerSheet
import io.github.bilibox.core.ui.component.playback.SpeedPanel
import io.github.bilibox.core.ui.component.playback.SpeedPanelMode
import io.github.bilibox.core.ui.theme.Pink
import io.github.bilibox.data.danmaku.Danmaku
import io.github.bilibox.data.danmaku.DanmakuSettings
import io.github.bilibox.data.player.LightsOffMode
import io.github.bilibox.data.player.LightsOffPreferences
import io.github.bilibox.feature.danmaku.DanmakuAreaGuide
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** A selectable video stream quality shown in the fullscreen quality drawer. */

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    // 播放设置卡片的偏好：在线视频由外层持有自动连播，本地队列持有循环模式。
    autoPlayNext: Boolean? = null,
    onAutoPlayNextChange: ((Boolean) -> Unit)? = null,
    loopMode: PlayerLoopMode? = null,
    onLoopModeChange: ((PlayerLoopMode) -> Unit)? = null,
    cacheLabel: String? = null,
    onCache: (() -> Unit)? = null,
    // 缓存面板与播放设置卡片共用视频下沿作为顶边；可见状态由在线视频页持有，
    // 这样返回键、权限申请和播放器内的弹层关闭顺序都由同一处控制。
    cacheSheetVisible: Boolean = false,
    onCacheSheetVisibleChange: (Boolean) -> Unit = {},
    cacheSheet: (@Composable (visible: Boolean, topInset: Dp, onDismiss: () -> Unit) -> Unit)? = null,
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
    var settingsSheetOpen by remember(player) { mutableStateOf(false) }
    var localAutoPlayNext by rememberSaveable(player) { mutableStateOf(false) }
    var localLoopMode by rememberSaveable(player) { mutableStateOf(PlayerLoopMode.NONE) }
    var subtitleEnabled by rememberSaveable(player) { mutableStateOf(true) }
    var mirrorEnabled by rememberSaveable(player) { mutableStateOf(false) }
    val density = LocalDensity.current
    // 卡片顶边要贴在视频下沿，用视频 Box 在根布局里的底边定位。
    var videoBottom by remember { mutableStateOf(0.dp) }
    var episodesExpanded by remember(player) { mutableStateOf(false) }
    var qualityMenuExpanded by remember(player) { mutableStateOf(false) }
    var playbackState by remember(player) { mutableIntStateOf(player.playbackState) }
    var detailsTab by remember(player) { mutableIntStateOf(0) }
    val lightsOffPreferences = remember(context) { LightsOffPreferences(context) }
    var lightsOff by remember(player) { mutableStateOf(false) }
    var lightsOffMode by remember(player) { mutableStateOf(lightsOffPreferences.load()) }
    val selectLightsOffMode: (LightsOffMode) -> Unit = { mode ->
        lightsOffMode = mode
        lightsOffPreferences.save(mode)
        lightsOff = true
    }
    LaunchedEffect(fullScreen) { if (fullScreen) lightsOff = false }
    var playbackModeExpanded by remember(player) { mutableStateOf(false) }
    var sleepTimerOpen by remember(player) { mutableStateOf(false) }
    var danmakuSettingsOpen by remember(player) { mutableStateOf(false) }
    var danmakuComposerOpen by remember(player) { mutableStateOf(false) }
    var danmakuSending by remember(player) { mutableStateOf(false) }
    var danmakuSendError by remember(player) { mutableStateOf<String?>(null) }
    var controlsVisible by remember(player) { mutableStateOf(true) }
    var screenLocked by remember(player) { mutableStateOf(false) }
    // 竖屏全屏：保持竖屏方向，画面按原比例居中，不放大也不裁切。
    var verticalFullScreen by remember(player) { mutableStateOf(false) }
    var orientationBeforeLock by remember(player) { mutableStateOf<Int?>(null) }
    var playerView by remember(player) { mutableStateOf<PlayerView?>(null) }
    var hasVideoFrame by remember(player) { mutableStateOf(false) }
    val screenScope = rememberCoroutineScope()
    val gestures = rememberPlayerGestureController(
        player = player,
        activity = activity,
        audioManager = audioManager,
        screenLocked = screenLocked,
        onSpeedChange = { speed = it },
    )
    val screenshot = rememberPlayerScreenshotController(playerView, hasVideoFrame, screenLocked, playbackError)
    // 从详情操作栏或底栏进入同一个发送弹层；发送前先把弹幕打开，新弹幕才看得见。
    val openDanmakuComposer: () -> Unit = {
        if (onSendDanmaku == null) {
            Toast.makeText(context, "登录后才能发送弹幕", Toast.LENGTH_SHORT).show()
        } else {
            if (!danmakuSettings.enabled) {
                onDanmakuSettingsChange(danmakuSettings.copy(enabled = true))
            }
            danmakuSendError = null
            danmakuComposerOpen = true
        }
    }

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
        settingsSheetOpen = false
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
    val listenVisible = rememberListenVisible(listenReveal)
    PlayerSystemBarsEffects(
        activity = activity,
        fullScreen = fullScreen,
        verticalFullScreen = verticalFullScreen,
        controlsVisible = controlsVisible,
        screenLocked = screenLocked,
        listenVisible = listenVisible,
        detailsBackground = detailsBackground,
        onResetOverlays = {
            speedMenuExpanded = false
            settingsSheetOpen = false
            playbackModeExpanded = false
            sleepTimerOpen = false
            episodesExpanded = false
            qualityMenuExpanded = false
            danmakuSettingsOpen = false
        },
        onVerticalFullScreenChange = { verticalFullScreen = it },
        onScreenLockedChange = { screenLocked = it },
    )

    fun toggleOrientation() {
        controlsVisible = true
        verticalFullScreen = false
        activity?.requestedOrientation = if (fullScreen) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    fun toggleVerticalFullScreen() {
        controlsVisible = true
        verticalFullScreen = !verticalFullScreen
    }

    fun navigateBack() {
        if (verticalFullScreen) {
            verticalFullScreen = false
        } else if (fullScreen) {
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

    val takeScreenshot = rememberPlayerScreenshotAction(screenshot)

    PlayerPlaybackStateObserver(
        player = player,
        onFirstFrame = { hasVideoFrame = true },
        // A queue can switch files on the same player; wait for the new first frame.
        onMediaItemTransition = { hasVideoFrame = false },
        onPlayWhenReadyChange = { playWhenReady = it },
        onPlaybackStateChange = {
            playbackState = it
            if (it == Player.STATE_IDLE) hasVideoFrame = false
        },
    )
    LaunchedEffect(player, subtitleEnabled) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitleEnabled)
            .build()
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
    val speedBoostActiveState = rememberUpdatedState(gestures.speedBoostActive)
    DisposableEffect(player) {
        onDispose {
            if (speedBoostActiveState.value) {
                gestures.restoreSpeedBoostOnDispose()
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
        settingsSheetOpen = false
        playbackModeExpanded = false
        sleepTimerOpen = false
        episodesExpanded = false
        qualityMenuExpanded = false
        onCacheSheetVisibleChange(false)
        duration = defaultDurationMs
        position = player.currentPosition.coerceAtLeast(0L)
    }
    LaunchedEffect(controlsVisible, isPlaying, screenLocked, scrubbing, speedMenuExpanded, settingsSheetOpen, playbackModeExpanded, sleepTimerOpen, episodesExpanded, qualityMenuExpanded, cacheSheetVisible, verticalFullScreen) {
        if (controlsVisible && (isPlaying || screenLocked) && !scrubbing &&
            !speedMenuExpanded && !settingsSheetOpen && !playbackModeExpanded && !sleepTimerOpen &&
            !episodesExpanded && !qualityMenuExpanded && !cacheSheetVisible
        ) {
            delay(4_000)
            controlsVisible = false
        }
    }
    LaunchedEffect(gestures.feedback, gestures.verticalGestureActive) {
        if (gestures.feedback != null && !gestures.verticalGestureActive) {
            delay(900)
            gestures.clearFeedback()
        }
    }
    BackHandler(enabled = !listenPageOpen) {
        when {
            screenLocked -> controlsVisible = true
            cacheSheetVisible -> onCacheSheetVisibleChange(false)
            danmakuComposerOpen -> if (!danmakuSending) danmakuComposerOpen = false
            danmakuSettingsOpen -> danmakuSettingsOpen = false
            episodeOverlayDismiss != null -> episodeOverlayDismiss()
            qualityMenuExpanded -> qualityMenuExpanded = false
            episodesExpanded -> episodesExpanded = false
            playbackModeExpanded -> playbackModeExpanded = false
            sleepTimerOpen -> sleepTimerOpen = false
            settingsSheetOpen -> settingsSheetOpen = false
            speedMenuExpanded -> speedMenuExpanded = false
            verticalFullScreen -> verticalFullScreen = false
            else -> navigateBack()
        }
    }

    // The speed panel takes the whole stage: hide the chrome behind it so the
    // runner and the ruler are the only things competing for attention.
    val showChrome = controlsVisible && !screenLocked && !(fullScreen && speedMenuExpanded)
    val verticalDanmakuBar = verticalFullScreen && danmakuSupported
    val sleepTimerRemainingMs = when (sleepTimer.mode) {
        SleepTimerMode.TIME -> (sleepTimer.endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)

        SleepTimerMode.VIDEOS ->
            if (sleepTimer.videosLeft <= 1) (duration - position).coerceAtLeast(0L) else 0L

        SleepTimerMode.OFF -> 0L
    }
    val settingsQualityLabel = qualityOptions.firstOrNull { it.qn == selectedQuality }?.label
        ?: qualityOptions.maxByOrNull { it.qn }?.label
        ?: "自动"
    val settingsAutoPlayNext = autoPlayNext ?: localAutoPlayNext
    val settingsLoopMode = loopMode ?: localLoopMode
    Box(Modifier.fillMaxSize().onGloballyPositioned { screenRootOffset = it.positionInRoot() }) {
        Column(
            Modifier.fillMaxSize().background(Color.Black)
                .then(
                    when {
                        fullScreen -> Modifier

                        // 状态栏随控件隐藏，但顶部安全区高度不变，画面不会上下跳动。
                        verticalFullScreen -> Modifier.windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)

                        else -> Modifier.statusBarsPadding()
                    }
                )
        ) {
            Box(
                (if (fullScreen || verticalFullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                    .background(Color.Black)
                    .onGloballyPositioned { coordinates ->
                        videoBottom = with(density) {
                            (coordinates.positionInRoot().y + coordinates.size.height).toDp()
                        }
                    },
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
                    modifier = Modifier.fillMaxSize().then(
                        // 竖屏全屏画面整体下移，这里额外上移，让视频落在更舒适的观看区域。
                        if (verticalFullScreen) Modifier.offset(y = (-88).dp) else Modifier
                    ).then(
                        if (mirrorEnabled) Modifier.graphicsLayer { scaleX = -1f } else Modifier
                    )
                )
                PlayerTouchSurface(
                    locked = screenLocked,
                    onToggleControls = { controlsVisible = !controlsVisible },
                    onTogglePlayback = { togglePlayback() },
                    verticalGesturesEnabled = !screenLocked,
                    onVerticalGestureStart = { gestures.startVerticalGesture(it) },
                    onVerticalSwipe = { side, delta -> gestures.adjustVerticalGesture(side, delta) },
                    onVerticalGestureEnd = { gestures.endVerticalGesture() },
                    speedBoostEnabled = !screenLocked && !speedMenuExpanded &&
                        !playbackModeExpanded && !sleepTimerOpen && playbackError == null,
                    onSpeedBoostStart = { gestures.startSpeedBoost() },
                    onSpeedBoostEnd = { gestures.endSpeedBoost() },
                    modifier = Modifier.fillMaxSize().testTag("player-touch-surface")
                )
                if (danmakuSupported && !listening && !listenPageOpen) {
                    DanmakuOverlay(player, danmakuItems, danmakuSettings, Modifier.fillMaxSize())
                    DanmakuAreaGuide(danmakuSettings.areaFraction, Modifier.fillMaxSize())
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
                            .padding(
                                bottom = when {
                                    fullScreen -> 150.dp
                                    verticalDanmakuBar -> 124.dp
                                    else -> 72.dp
                                }
                            ),
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
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .height(
                                when {
                                    fullScreen -> 150.dp
                                    verticalDanmakuBar -> 116.dp
                                    else -> 64.dp
                                }
                            )
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
                            verticalFullScreen = verticalFullScreen,
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
                            onDanmakuSend = openDanmakuComposer,
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
                            onToggleVerticalFullScreen = { toggleVerticalFullScreen() },
                        ),
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .then(
                                if (fullScreen) {
                                    Modifier
                                        .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 6.dp)
                                } else if (verticalDanmakuBar) {
                                    Modifier.padding(start = 6.dp, end = 6.dp, bottom = 12.dp)
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
                        settingsOpen = settingsSheetOpen,
                        onBack = { navigateBack() },
                        onListenClick = { enterListen() },
                        onListenPositioned = { listenOrigin = it },
                        onMoreClick = {
                            // 竖屏全屏时先回到普通竖屏，等视频重新排好版再展开卡片。
                            if (verticalFullScreen) {
                                verticalFullScreen = false
                                screenScope.launch {
                                    withFrameNanos { }
                                    settingsSheetOpen = true
                                }
                            } else {
                                settingsSheetOpen = true
                            }
                        },
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                }
                if (!showChrome && !fullScreen && !verticalFullScreen && duration > 0L) {
                    MiniPlaybackProgress(
                        position = position,
                        duration = duration,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    )
                }
                // 控件隐藏后的沉浸播放保留一个变淡的全屏入口，不必先唤出底栏。
                // 位置对齐底栏的横屏按钮，控件显隐时图标不会跳动。
                if (!showChrome && !fullScreen && !verticalFullScreen && !listening) {
                    MiniFullscreenButton(
                        onClick = { toggleOrientation() },
                        modifier = Modifier.align(Alignment.BottomEnd)
                            .padding(end = 6.dp),
                    )
                }
                // Locking the screen must keep the unlock affordance reachable, so
                // this follows the raw chrome state rather than showChrome.
                if (fullScreen && controlsVisible && !speedMenuExpanded) {
                    PlayerSideTools(
                        locked = screenLocked,
                        screenshotEnabled = hasVideoFrame && playbackError == null && !screenshot.saving,
                        onScreenshot = takeScreenshot,
                        onToggleLock = { toggleScreenLock() },
                        modifier = Modifier.align(Alignment.CenterEnd).displayCutoutPadding()
                            .navigationBarsPadding().padding(end = 10.dp).offset(y = (-36).dp)
                    )
                }
                gestures.feedback?.let { feedback ->
                    PlayerGestureFeedback(
                        side = feedback.side,
                        level = feedback.level,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp)
                    )
                }
                PlayerSpeedBoostIndicator(
                    active = gestures.speedBoostActive,
                    speed = gestures.speedBoostSpeed,
                    // Keep the feedback near the top edge: centering it covered the
                    // part of the frame the user was watching while holding.
                    modifier = Modifier.align(Alignment.TopCenter)
                        .padding(top = if (fullScreen) 56.dp else 52.dp)
                )
                val episodeContent = episodes
                if (fullScreen && !screenLocked && episodeContent != null) {
                    PlayerSidePanelHost(
                        visible = episodesExpanded,
                        onDismiss = {
                            episodesExpanded = false
                            controlsVisible = true
                        },
                        scrimDescription = "关闭选集",
                    ) {
                        episodeContent.invoke {
                            episodesExpanded = false
                            controlsVisible = true
                        }
                    }
                }
                if (fullScreen && !screenLocked) {
                    PlayerSidePanelHost(
                        visible = speedMenuExpanded,
                        onDismiss = { speedMenuExpanded = false },
                        scrimDescription = "关闭倍速面板",
                    ) {
                        SpeedPanel(
                            speed = speed,
                            mode = SpeedPanelMode.Drawer,
                            onDismiss = { speedMenuExpanded = false },
                            onSpeedChange = {
                                player.setPlaybackSpeed(it)
                                speed = it
                            },
                            remainingMs = (duration - position).coerceAtLeast(0L),
                        )
                    }
                    PlayerSidePanelHost(
                        visible = qualityMenuExpanded,
                        onDismiss = { qualityMenuExpanded = false },
                        scrimDescription = "关闭清晰度选择",
                    ) {
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
                val previewFrame = screenshot.previewFrame
                if (previewFrame != null) {
                    ScreenshotPreviewCard(
                        frame = previewFrame,
                        visible = screenshot.previewVisible,
                        modifier = Modifier.align(Alignment.BottomStart)
                            .padding(
                                start = 10.dp,
                                bottom = when {
                                    fullScreen -> 156.dp
                                    verticalDanmakuBar -> 100.dp
                                    else -> 48.dp
                                }
                            ),
                    )
                }
                if (screenshot.flash.value > .001f) {
                    Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = screenshot.flash.value)))
                }
            }

            if (!fullScreen && !verticalFullScreen) {
                PlayerDetailsPane(
                    modifier = Modifier.weight(1f),
                    detailsBackground = detailsBackground,
                    showDetailTabs = showDetailTabs,
                    detailTabs = detailTabs,
                    detailsTab = detailsTab,
                    onDetailsTabChange = { detailsTab = it },
                    sourcePill = sourcePill,
                    sourceLabel = sourceLabel,
                    danmakuEnabled = danmakuSettings.enabled,
                    onOpenDanmakuComposer = if (danmakuSupported) openDanmakuComposer else null,
                    onToggleDanmaku = if (danmakuSupported) {
                        {
                            onDanmakuSettingsChange(
                                danmakuSettings.copy(enabled = !danmakuSettings.enabled)
                            )
                        }
                    } else {
                        null
                    },
                    lightsOff = lightsOff,
                    lightsOffMode = lightsOffMode,
                    onToggleLights = { lightsOff = !lightsOff },
                    onSelectLightsOffMode = selectLightsOffMode,
                    playbackError = playbackError,
                    onRetry = onRetry,
                    commentMessage = commentMessage,
                    duration = duration,
                    positionMs = if (scrubbing) scrubPosition.toLong() else position,
                    speed = speed,
                    screenshotEnabled = hasVideoFrame && playbackError == null && !screenshot.saving,
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
                    onTakeScreenshot = takeScreenshot,
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
        // 播放设置卡片从视频下沿铺到屏幕底部，作为兄弟节点才能盖住详情区。
        if (!fullScreen && !verticalFullScreen) {
            PlayerPlaybackSettingsHost(
                visible = settingsSheetOpen,
                topInset = videoBottom,
                state = PlayerSettingsState(
                    speed = speed,
                    autoPlayNext = settingsAutoPlayNext,
                    loopMode = settingsLoopMode,
                    backgroundAudio = listening,
                    qualityLabel = settingsQualityLabel,
                    subtitleEnabled = subtitleEnabled,
                    mirrorEnabled = mirrorEnabled,
                    cacheLabel = cacheLabel.orEmpty(),
                ),
                qualityAvailable = qualityOptions.isNotEmpty(),
                danmakuAvailable = danmakuSupported,
                moreSettingsAvailable = playbackModeSheet != null,
                onDismiss = { settingsSheetOpen = false },
                onSpeed = { speedMenuExpanded = true },
                onSleepTimer = { sleepTimerOpen = true },
                onQuality = { qualityMenuExpanded = true },
                onDanmakuSettings = { danmakuSettingsOpen = true },
                onMoreSettings = { playbackModeExpanded = true },
                onBackgroundAudioChange = { if (it) enterListen() else restoreVideo() },
                onAutoPlayNextChange = { onAutoPlayNextChange?.invoke(it) ?: run { localAutoPlayNext = it } },
                onLoopModeChange = { onLoopModeChange?.invoke(it) ?: run { localLoopMode = it } },
                onSubtitleChange = { subtitleEnabled = it },
                onMirrorChange = { mirrorEnabled = it },
                onCache = onCache,
                onUnavailable = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
            )
        }
        // 缓存面板也贴在视频下沿；播放器只量 topInset，避免反向依赖在线视频包。
        if (!fullScreen && !verticalFullScreen) {
            cacheSheet?.invoke(cacheSheetVisible, videoBottom) {
                onCacheSheetVisibleChange(false)
            }
        }
        // 旋转进入全屏时关闭下沿面板，避免面板停留在已不存在的视频下沿。
        LaunchedEffect(fullScreen, verticalFullScreen) {
            if (fullScreen || verticalFullScreen) {
                onCacheSheetVisibleChange(false)
                sleepTimerOpen = false
            }
        }
        // 竖屏的画质选择使用底部弹层；横屏仍沿用侧边抽屉。
        if (!fullScreen && !verticalFullScreen && qualityMenuExpanded && qualityOptions.isNotEmpty()) {
            PlayerQualitySheet(
                options = qualityOptions,
                selectedQuality = selectedQuality,
                onDismiss = { qualityMenuExpanded = false },
                onQualitySelected = { qn ->
                    onQualitySelected?.invoke(qn)
                    qualityMenuExpanded = false
                },
            )
        }
        // The timer outlives the sheet. The card starts at the video's bottom
        // edge so the picture stays visible while picking a duration.
        if (!fullScreen) {
            PlayerDrawerVisibility(visible = sleepTimerOpen, fromBottom = true) {
                SleepTimerSheet(
                    timer = sleepTimer,
                    remainingVideos = remainingVideos,
                    currentRemainingMs = (duration - position).coerceAtLeast(0L),
                    durationMs = duration,
                    topInset = videoBottom,
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
                            screenScope.launch {
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
        if (danmakuSupported) {
            DanmakuSettingsSheet(
                visible = danmakuSettingsOpen,
                settings = danmakuSettings,
                topInset = if (fullScreen || verticalFullScreen) null else videoBottom,
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
