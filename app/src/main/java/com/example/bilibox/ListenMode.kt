package com.example.bilibox

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * What the 听视频 page shows for the current item. Online videos pass the
 * cover URL, local files pass the extracted thumbnail.
 */
internal data class ListenInfo(
    val subtitle: String,
    val online: Boolean,
    val coverUrl: String? = null,
    val coverBitmap: Bitmap? = null,
)

/** Sleep timer shared by the listen page and the collapsed state. */
@Stable
internal class ListenSleepTimer {
    /** elapsedRealtime deadline, or 0 when no countdown is running. */
    var endAt by mutableLongStateOf(0L)
    var minutes by mutableIntStateOf(0)
    var afterCurrent by mutableStateOf(false)
    val active: Boolean get() = endAt > 0L || afterCurrent

    fun start(minutes: Int) {
        afterCurrent = false
        this.minutes = minutes
        endAt = SystemClock.elapsedRealtime() + minutes * 60_000L
    }

    fun stopAfterCurrent() {
        endAt = 0L
        minutes = 0
        afterCurrent = true
    }

    fun cancel() {
        endAt = 0L
        minutes = 0
        afterCurrent = false
    }
}

private val ListenFadeMs = 30_000L

/**
 * Applies the audio-only state to [player]: the video renderer is disabled so
 * frames are neither downloaded (DASH) nor decoded, the player takes audio
 * focus, pauses on headphone unplug, holds a wake lock, and is exposed to the
 * background media session.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun ListenPlaybackEffect(
    player: ExoPlayer,
    listening: Boolean,
    title: String,
    subtitle: String,
    cover: Bitmap?,
) {
    val context = LocalContext.current
    DisposableEffect(player, listening) {
        if (listening) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                .build()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            player.setHandleAudioBecomingNoisy(true)
            player.setWakeMode(C.WAKE_MODE_NETWORK)
        }
        onDispose {
            if (listening) {
                ListenPlaybackBridge.detach(context, player)
                // No-ops if the player was already released with its screen.
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                    .build()
                player.setAudioAttributes(AudioAttributes.DEFAULT, false)
                player.setHandleAudioBecomingNoisy(false)
                player.setWakeMode(C.WAKE_MODE_NONE)
            }
        }
    }
    // Encode the notification artwork off the main thread; re-attaching only
    // refreshes the session player, the service keeps running.
    LaunchedEffect(player, listening, title, subtitle, cover) {
        if (!listening) return@LaunchedEffect
        ListenPlaybackBridge.attach(context, player, buildListenMetadata(title, subtitle, null))
        if (cover != null) {
            val bytes = withContext(Dispatchers.Default) {
                runCatching {
                    ByteArrayOutputStream().use { out ->
                        cover.compress(Bitmap.CompressFormat.JPEG, 85, out)
                        out.toByteArray()
                    }
                }.getOrNull()
            }
            if (bytes != null) {
                ListenPlaybackBridge.attach(context, player, buildListenMetadata(title, subtitle, bytes))
            }
        }
    }
}

/** Countdown / "播完当前" handling. Fades the volume over the last 30 seconds. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun ListenSleepTimerEffect(player: ExoPlayer, timer: ListenSleepTimer) {
    val context = LocalContext.current
    LaunchedEffect(player, timer.endAt) {
        val endAt = timer.endAt
        if (endAt <= 0L) return@LaunchedEffect
        try {
            while (isActive) {
                val left = endAt - SystemClock.elapsedRealtime()
                if (left <= 0L) {
                    player.playWhenReady = false
                    timer.cancel()
                    Toast.makeText(context, "定时关闭：已暂停播放", Toast.LENGTH_SHORT).show()
                    break
                }
                player.volume = if (left < ListenFadeMs) (left.toFloat() / ListenFadeMs).coerceIn(0f, 1f) else 1f
                delay(if (left < ListenFadeMs) 200L else 1_000L)
            }
        } finally {
            player.volume = 1f
        }
    }
    DisposableEffect(player, timer.afterCurrent) {
        if (!timer.afterCurrent) return@DisposableEffect onDispose { }
        // ExoPlayer pauses right before leaving the item, so 连播 / 自动下一P
        // never get a chance to start the next file.
        player.pauseAtEndOfMediaItems = true
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
                    timer.cancel()
                    Toast.makeText(context, "定时关闭：当前视频已播完", Toast.LENGTH_SHORT).show()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.pauseAtEndOfMediaItems = false
        }
    }
}

private val listenCoverCache = android.util.LruCache<String, Bitmap>(4)

/** Loads the online cover once per URL; the bitmap feeds both the page and the notification. */
@Composable
internal fun rememberListenCover(info: ListenInfo?, enabled: Boolean): Bitmap? {
    val direct = info?.coverBitmap
    val url = info?.coverUrl?.takeIf { it.isNotBlank() }
    val loaded by produceState<Bitmap?>(initialValue = url?.let { listenCoverCache.get(it) }, url, enabled) {
        // initialValue only applies to the first key; refresh it when the URL changes.
        value = url?.let { listenCoverCache.get(it) }
        if (!enabled || url == null || value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                try {
                    connection.inputStream.use(BitmapFactory::decodeStream)
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }?.also { listenCoverCache.put(url, it) }
    }
    return direct ?: loaded
}

// ---------------------------------------------------------------- 顶栏按钮

/** Headphone glyph for the player top bar (听视频 entry). */
@Composable
internal fun HeadphoneIcon(tint: Color, modifier: Modifier = Modifier.size(20.dp)) {
    Canvas(modifier) {
        val u = size.width / 24f
        val stroke = Stroke(1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(Path().apply {
            moveTo(4f * u, 15f * u)
            lineTo(4f * u, 12f * u)
            arcTo(Rect(4f * u, 4f * u, 20f * u, 20f * u), 180f, 180f, false)
            lineTo(20f * u, 15f * u)
        }, tint, style = stroke)
        listOf(3f, 16.5f).forEach { x ->
            drawRoundRect(tint, Offset(x * u, 14f * u), Size(4.5f * u, 7f * u),
                CornerRadius(1.6f * u), style = stroke)
        }
    }
}

/** The 40dp top-bar button. Pink with a soft pink disc while listening. */
@Composable
internal fun ListenTopBarButton(listening: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier.size(40.dp).clip(CircleShape)
            .background(if (listening) OnlinePink.copy(alpha = .22f) else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (listening) "打开听视频" else "听视频" },
        contentAlignment = Alignment.Center
    ) {
        HeadphoneIcon(if (listening) Color(0xFFFFB3CA) else Color.White)
    }
}

// ---------------------------------------------------------------- 收起后的视频区

/**
 * Shown over the 16:9 video box while listening with the full page collapsed.
 * Only the two chips take touches, so the player chrome underneath keeps working.
 */
@Composable
internal fun CollapsedListenOverlay(
    cover: Bitmap?,
    playing: Boolean,
    onExpand: () -> Unit,
    onRestoreVideo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Color(0xFF15161B))) {
        if (cover != null) {
            Image(cover.asImageBitmap(), contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)))
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ListenVisualizer(playing, Modifier.width(72.dp).height(22.dp), bars = 9)
            Spacer(Modifier.height(8.dp))
            Text(if (playing) "正在听视频 · 画面已关闭" else "听视频已暂停", color = Color.White,
                fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ListenChip("展开", onExpand, filled = false)
                ListenChip("恢复画面", onRestoreVideo, filled = true)
            }
        }
    }
}

@Composable
private fun ListenChip(text: String, onClick: () -> Unit, filled: Boolean) {
    Box(
        Modifier.height(30.dp).clip(RoundedCornerShape(15.dp))
            .background(if (filled) Color.White.copy(alpha = .92f) else Color.White.copy(alpha = .16f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (filled) OnlineText else Color.White, fontSize = 12.sp,
            fontWeight = FontWeight.Medium)
    }
}

// ---------------------------------------------------------------- 频谱

/**
 * Decorative level bars. Real FFT would need the Visualizer API and the
 * RECORD_AUDIO permission, so the bars are animated and settle when paused.
 */
@Composable
internal fun ListenVisualizer(playing: Boolean, modifier: Modifier = Modifier, bars: Int = 36) {
    val levels = remember(bars) { FloatArray(bars) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing, bars) {
        while (isActive) {
            var moving = false
            withFrameMillis { now ->
                val t = now / 1000f
                for (k in 0 until bars) {
                    val target = if (playing) {
                        val wave = abs(sin(t * 2.1f + k * .45f)) * abs(kotlin.math.cos(t * 1.3f + k * .17f))
                        val noise = ((now / 90 + k * 7919) % 97) / 97f * .25f
                        val edge = 1f - abs(k - bars / 2f) / bars * .9f
                        ((.25f + .45f * wave + noise) * edge).coerceIn(0f, 1f)
                    } else 0f
                    levels[k] += (target - levels[k]) * .18f
                    if (abs(levels[k] - target) > .004f) moving = true
                }
                frame = now
            }
            if (!playing && !moving) break
        }
    }
    Canvas(modifier) {
        // Reading the frame counter makes the canvas redraw on every animation frame.
        if (frame < 0L) return@Canvas
        val w = size.width / bars
        val brush = Brush.verticalGradient(listOf(OnlinePink, Color.White.copy(alpha = .92f), OnlinePink))
        for (k in 0 until bars) {
            val h = max(size.height * .08f, levels[k] * size.height)
            val bw = w * .56f
            drawRoundRect(brush, Offset(k * w + w * .22f, (size.height - h) / 2f), Size(bw, h),
                CornerRadius(bw / 2f))
        }
    }
}

// ---------------------------------------------------------------- 听视频页

/**
 * Full-screen 听视频 page drawn above the player. [reveal] returns 0..1 and
 * drives a circular clip expanding from [origin] (the headphone button); it is
 * only read while drawing, so the animation does not recompose the page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ListenScreen(
    reveal: () -> Float,
    origin: Offset,
    open: Boolean,
    title: String,
    info: ListenInfo,
    cover: Bitmap?,
    playing: Boolean,
    buffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    speed: Float,
    timer: ListenSleepTimer,
    hasNext: Boolean,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onNext: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onCollapse: () -> Unit,
    onRestoreVideo: () -> Unit,
) {
    val context = LocalContext.current
    var speedOpen by remember { mutableStateOf(false) }
    var timerOpen by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }

    // The page is laid out for portrait only.
    val activity = remember(context) { context.findListenActivity() }
    DisposableEffect(activity) {
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            if (previousOrientation != null) activity?.requestedOrientation = previousOrientation
        }
    }
    BackHandler(enabled = open) {
        when {
            timerOpen -> timerOpen = false
            speedOpen -> speedOpen = false
            else -> onCollapse()
        }
    }

    Box(
        Modifier.fillMaxSize()
            .drawWithContent {
                val progress = reveal()
                if (progress >= .999f) drawContent() else {
                    val radius = hypot(max(origin.x, size.width - origin.x), max(origin.y, size.height - origin.y)) * progress
                    clipPath(Path().apply {
                        addOval(Rect(origin, radius))
                    }) { this@drawWithContent.drawContent() }
                }
            }
            // Swallow touches so nothing reaches the player underneath.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        ListenBackdrop(cover)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            // Header: collapse, status pill, sleep timer.
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                ListenRoundButton("收起", onCollapse) { ChevronDownGlyph(Color.White) }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .14f))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape)
                            .background(if (playing) Color(0xFF7CF29A) else Color.White.copy(alpha = .4f)))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (info.online) "听视频 · 仅加载音频流" else "听视频 · 画面已停止解码",
                            color = Color.White.copy(alpha = .9f), fontSize = 12.sp
                        )
                    }
                }
                ListenRoundButton("定时关闭", { timerOpen = true }) {
                    TimerGlyph(if (timer.active) Color(0xFFFFB3CA) else Color.White)
                }
            }

            Spacer(Modifier.weight(.6f))
            ListenCoverCard(cover, playing, info.online, onRestoreVideo)
            Spacer(Modifier.height(18.dp))
            ListenVisualizer(playing, Modifier.fillMaxWidth().height(40.dp))
            Spacer(Modifier.weight(1f))

            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 24.sp)
            if (info.subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(info.subtitle, color = Color.White.copy(alpha = .65f), fontSize = 12.5.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (timer.active) {
                Spacer(Modifier.height(6.dp))
                SleepTimerLabel(timer, Modifier)
            }

            Spacer(Modifier.height(14.dp))
            val sliderMax = durationMs.coerceAtLeast(1L).toFloat()
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Slider(
                    value = (if (scrubbing) scrubValue else positionMs.toFloat()).coerceIn(0f, sliderMax),
                    onValueChange = { scrubbing = true; scrubValue = it },
                    onValueChangeFinished = {
                        onSeek(scrubValue.toLong())
                        scrubbing = false
                    },
                    valueRange = 0f..sliderMax,
                    enabled = durationMs > 0,
                    modifier = Modifier.fillMaxWidth().height(24.dp).semantics { contentDescription = "播放进度" },
                    thumb = {
                        Box(Modifier.size(14.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(Color.White))
                    },
                    track = { state ->
                        Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                            val played = (state.value / sliderMax).coerceIn(0f, 1f)
                            val y = center.y
                            drawLine(Color.White.copy(alpha = .25f), Offset(0f, y), Offset(size.width, y),
                                size.height, StrokeCap.Round)
                            if (played > 0f) drawLine(OnlinePink, Offset(0f, y), Offset(size.width * played, y),
                                size.height, StrokeCap.Round)
                        }
                    }
                )
            }
            Row(Modifier.fillMaxWidth()) {
                Text(formatPlaybackTime(if (scrubbing) scrubValue.toLong() else positionMs),
                    color = Color.White.copy(alpha = .65f), fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text(formatPlaybackTime(durationMs, unknownWhenZero = true),
                    color = Color.White.copy(alpha = .65f), fontSize = 11.sp)
            }

            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(width = 52.dp, height = 44.dp).clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button) { speedOpen = true }
                        .semantics { contentDescription = "倍速" },
                    contentAlignment = Alignment.Center
                ) {
                    val active = abs(speed - 1f) > .001f
                    Text(if (active) formatSpeedBadge(speed) else "倍速",
                        color = if (active) Color(0xFFFFB3CA) else Color.White,
                        fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                ListenRoundButton("后退 15 秒", { onSeek((positionMs - 15_000L).coerceAtLeast(0L)) }, size = 48.dp) {
                    SkipGlyph(forward = false)
                }
                ListenPlayButton(playing, buffering, onTogglePlay)
                ListenRoundButton("前进 15 秒", {
                    onSeek(if (durationMs > 0) (positionMs + 15_000L).coerceAtMost(durationMs) else positionMs + 15_000L)
                }, size = 48.dp) {
                    SkipGlyph(forward = true)
                }
                ListenRoundButton("下一个", {
                    if (hasNext) onNext()
                    else Toast.makeText(context, "已经是最后一个了", Toast.LENGTH_SHORT).show()
                }, size = 48.dp) {
                    NextGlyph(Color.White.copy(alpha = if (hasNext) 1f else .35f))
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "锁屏或切到后台会继续播放，可在通知栏控制",
                color = Color.White.copy(alpha = .4f), fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(12.dp))
        }

        AnimatedVisibility(
            visible = speedOpen,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(180)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it },
        ) {
            SpeedPanel(
                speed = speed,
                mode = SpeedPanelMode.Sheet,
                onDismiss = { speedOpen = false },
                onSpeedChange = onSpeedChange,
            )
        }
        AnimatedVisibility(
            visible = timerOpen,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(180)) + slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it },
            exit = fadeOut(tween(140)) + slideOutVertically(tween(220, easing = FastOutSlowInEasing)) { it },
        ) {
            SleepTimerSheet(timer, onDismiss = { timerOpen = false })
        }
    }
}

@Composable
private fun ListenBackdrop(cover: Bitmap?) {
    // A tiny copy stretched with bilinear filtering gives a cheap blur on every API level.
    val tiny = remember(cover) {
        cover?.let { runCatching { Bitmap.createScaledBitmap(it, 12, 8, true) }.getOrNull() }
    }
    Box(Modifier.fillMaxSize().background(Color(0xFF15161B))) {
        if (tiny != null) {
            Image(tiny.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop, filterQuality = FilterQuality.Low)
        } else {
            Box(Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xFF6B2D47), Color(0xFF15161B)))))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color(0x590A0A0E), Color(0xBF0A0A0E), Color(0xE60A0A0E)
        ))))
    }
}

@Composable
private fun ListenCoverCard(cover: Bitmap?, playing: Boolean, online: Boolean, onRestoreVideo: () -> Unit) {
    val scale by animateFloatAsState(if (playing) 1f else .92f, spring(dampingRatio = .6f, stiffness = 300f),
        label = "听视频封面缩放")
    Box(
        Modifier.fillMaxWidth().aspectRatio(16f / 9f).scale(scale)
            .shadow(20.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF2A2B33))
    ) {
        if (cover != null) {
            Image(cover.asImageBitmap(), contentDescription = "视频封面", modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop)
        } else {
            HeadphoneIcon(Color.White.copy(alpha = .35f), Modifier.align(Alignment.Center).size(48.dp))
        }
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(
            listOf(Color.White.copy(alpha = .18f), Color.Transparent),
            start = Offset.Zero, end = Offset(600f, 600f)
        )))
        Text(
            if (online) "省流量 · 不加载画面" else "省电 · 已停止解码画面",
            color = Color.White, fontSize = 10.5.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                .clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = .45f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
        Row(
            Modifier.align(Alignment.BottomEnd).padding(10.dp)
                .clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .92f))
                .clickable(role = Role.Button, onClick = onRestoreVideo)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("回到画面", color = OnlineText, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ListenRoundButton(
    description: String,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    icon: @Composable BoxScope.() -> Unit,
) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
        content = icon
    )
}

@Composable
private fun ListenPlayButton(playing: Boolean, buffering: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(66.dp).shadow(14.dp, CircleShape, spotColor = OnlinePink)
            .clip(CircleShape).background(Color.White)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (playing) "暂停" else "播放" },
        contentAlignment = Alignment.Center
    ) {
        if (buffering) {
            CircularProgressIndicator(color = OnlinePink, strokeWidth = 2.dp, modifier = Modifier.size(62.dp))
        }
        Canvas(Modifier.size(28.dp)) {
            val u = size.width / 24f
            if (playing) {
                listOf(6f, 14.2f).forEach { x ->
                    drawRoundRect(OnlineText, Offset(x * u, 5f * u), Size(3.8f * u, 14f * u), CornerRadius(1.2f * u))
                }
            } else {
                drawPath(Path().apply {
                    moveTo(8f * u, 5f * u); lineTo(19.5f * u, 12f * u); lineTo(8f * u, 19f * u); close()
                }, OnlineText)
            }
        }
    }
}

@Composable
private fun ChevronDownGlyph(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        drawPath(Path().apply { moveTo(6f * u, 9f * u); lineTo(12f * u, 15f * u); lineTo(18f * u, 9f * u) },
            tint, style = Stroke(2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun TimerGlyph(tint: Color) {
    Canvas(Modifier.size(21.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawCircle(tint, 7.5f * u, Offset(12f * u, 13.5f * u), style = stroke)
        drawPath(Path().apply { moveTo(12f * u, 9.5f * u); lineTo(12f * u, 13.5f * u); lineTo(14.5f * u, 15.5f * u) },
            tint, style = stroke)
        drawLine(tint, Offset(9.5f * u, 2.5f * u), Offset(14.5f * u, 2.5f * u), 1.9f * u, StrokeCap.Round)
    }
}

/** Circular arrow with "15" inside, mirrored for rewind. */
@Composable
private fun SkipGlyph(forward: Boolean) {
    Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val u = size.width / 24f
            val stroke = Stroke(1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val start = if (forward) -60f else -120f
            val sweep = if (forward) 300f else -300f
            drawArc(Color.White, start, sweep, false, Offset(3f * u, 3f * u), Size(18f * u, 18f * u), style = stroke)
            val tipX = if (forward) 16.5f else 7.5f
            val dir = if (forward) 1f else -1f
            drawPath(Path().apply {
                moveTo((tipX - 2.6f * dir) * u, 1.4f * u)
                lineTo((tipX + .4f * dir) * u, 4.2f * u)
                lineTo((tipX - 3f * dir) * u, 6.4f * u)
            }, Color.White, style = stroke)
        }
        Text("15", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun NextGlyph(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        drawPath(Path().apply { moveTo(6f * u, 5.5f * u); lineTo(16f * u, 12f * u); lineTo(6f * u, 18.5f * u); close() }, tint)
        drawRoundRect(tint, Offset(17f * u, 5.5f * u), Size(2.4f * u, 13f * u), CornerRadius(1f * u))
    }
}

@Composable
private fun SleepTimerLabel(timer: ListenSleepTimer, modifier: Modifier) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(timer.endAt) {
        while (isActive && timer.endAt > 0L) {
            now = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }
    val text = when {
        timer.afterCurrent -> "定时关闭：播完当前视频后暂停"
        timer.endAt > 0L -> "定时关闭：剩余 ${formatPlaybackTime((timer.endAt - now).coerceAtLeast(0L))}"
        else -> ""
    }
    Text(text, color = Color(0xFFFFB3CA), fontSize = 12.sp, modifier = modifier)
}

private val SleepTimerChoices = listOf(-1 to "播完当前", 15 to "15 分钟", 30 to "30 分钟",
    45 to "45 分钟", 60 to "60 分钟", 90 to "90 分钟")

@Composable
private fun SleepTimerSheet(timer: ListenSleepTimer, onDismiss: () -> Unit) {
    val scrim = remember { MutableInteractionSource() }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .46f))
            .clickable(interactionSource = scrim, indication = null, onClick = onDismiss)
            .semantics { contentDescription = "关闭定时关闭" })
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Color.White)
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding()
                .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 18.dp)
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp)).background(Color(0xFFE3E5E7)))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("定时关闭", color = OnlineText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                if (timer.active) SleepTimerLabel(timer, Modifier)
                else Text("未开启", color = Color(0xFF9499A0), fontSize = 12.sp)
            }
            Spacer(Modifier.height(14.dp))
            SleepTimerChoices.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (value, label) ->
                        val selected = if (value < 0) timer.afterCurrent else timer.endAt > 0L && timer.minutes == value
                        Box(
                            Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp))
                                .background(if (selected) OnlinePinkSoft else Color(0xFFF1F2F3))
                                .border(1.dp, if (selected) OnlinePink else Color.Transparent, RoundedCornerShape(20.dp))
                                .clickable(role = Role.Button) {
                                    if (value < 0) timer.stopAfterCurrent() else timer.start(value)
                                    onDismiss()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (selected) OnlinePink else OnlineText, fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (timer.active) {
                Box(
                    Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(21.dp))
                        .clickable(role = Role.Button) { timer.cancel(); onDismiss() },
                    contentAlignment = Alignment.Center
                ) { Text("关闭定时", color = Color(0xFF61666D), fontSize = 13.sp) }
            }
            Text("倒计时结束前 30 秒音量会逐渐降低", color = Color(0xFF9499A0), fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private tailrec fun Context.findListenActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findListenActivity()
    else -> null
}
