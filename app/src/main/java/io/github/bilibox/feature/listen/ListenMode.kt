package io.github.bilibox.feature.listen

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.ui.theme.OnlineText
import io.github.bilibox.core.ui.theme.Pink
import io.github.bilibox.feature.player.PlaybackSleepTimer
import io.github.bilibox.feature.player.SleepTimerLabel
import io.github.bilibox.feature.player.SleepTimerMode
import io.github.bilibox.feature.player.SleepTimerSheet
import io.github.bilibox.feature.player.SpeedPanel
import io.github.bilibox.feature.player.SpeedPanelMode
import io.github.bilibox.feature.player.VideoPlayerScreen
import io.github.bilibox.feature.player.formatPlaybackTime
import io.github.bilibox.feature.player.formatSpeedBadge
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

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

/**
 * Queue controls on the 听视频 page (上一个/下一个/播放模式/选集). Kept in one
 * object so VideoPlayerScreen does not grow more parameters: that composable is
 * already so large that extra parameters pushed it past ART's verifier.
 * [mode]: 0 列表循环, 1 单曲循环, 2 随机播放, 3 播完暂停.
 */
internal class ListenControls(
    val mode: Int = 0,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
    val onPrevious: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onCycleMode: () -> Unit = {},
    val onOpenQueue: () -> Unit = {},
)

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
    queue: ListenQueue? = null,
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
    LaunchedEffect(player, listening, title, subtitle, cover, queue) {
        if (!listening) return@LaunchedEffect
        ListenPlaybackBridge.attach(context, player, buildListenMetadata(title, subtitle, null), queue)
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
                ListenPlaybackBridge.attach(context, player, buildListenMetadata(title, subtitle, bytes), queue)
            }
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
    // 帧动画只在真正出声时跑：缓冲中 playWhenReady 已为 true，但画面不该一直重绘。
    visualizing: Boolean = playing,
    buffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    speed: Float,
    timer: PlaybackSleepTimer,
    mode: Int = 0,
    remainingVideos: Int = 1,
    hasPrevious: Boolean = false,
    hasNext: Boolean,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit = {},
    onNext: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onCycleMode: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
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
                Spacer(Modifier.width(40.dp))
            }

            Spacer(Modifier.weight(.6f))
            ListenCoverCard(cover, visualizing, info.online, onRestoreVideo)
            Spacer(Modifier.height(18.dp))
            ListenVisualizer(visualizing, Modifier.fillMaxWidth().height(40.dp))
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

            Spacer(Modifier.height(6.dp))
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

            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ListenRoundButton("上一个", {
                    if (hasPrevious) onPrevious()
                    else Toast.makeText(context, "已经是第一个了", Toast.LENGTH_SHORT).show()
                }, size = 48.dp) {
                    PrevGlyph(Color.White.copy(alpha = if (hasPrevious) 1f else .35f))
                }
                ListenRoundButton("后退 10 秒", { onSeek((positionMs - 10_000L).coerceAtLeast(0L)) }, size = 48.dp) {
                    SkipGlyph(forward = false, seconds = 10)
                }
                ListenPlayButton(playing, buffering, onTogglePlay)
                ListenRoundButton("前进 10 秒", {
                    onSeek(if (durationMs > 0) (positionMs + 10_000L).coerceAtMost(durationMs) else positionMs + 10_000L)
                }, size = 48.dp) {
                    SkipGlyph(forward = true, seconds = 10)
                }
                ListenRoundButton("下一个", {
                    if (hasNext) onNext()
                    else Toast.makeText(context, "已经是最后一个了", Toast.LENGTH_SHORT).show()
                }, size = 48.dp) {
                    NextGlyph(Color.White.copy(alpha = if (hasNext) 1f else .35f))
                }
            }

            Spacer(Modifier.height(10.dp))

            // 底部工具栏
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolButton(
                    icon = { SpeedGlyph(it) },
                    label = if (abs(speed - 1f) > .001f) formatSpeedBadge(speed) else "倍速",
                    active = abs(speed - 1f) > .001f,
                    onClick = { speedOpen = true }
                )
                ToolButton(
                    icon = { TimerGlyph(it) },
                    label = when {
                        timer.mode == SleepTimerMode.TIME && timer.endAt > 0 -> {
                            val remainingMs = (timer.endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                            formatTimerBadge(remainingMs)
                        }
                        timer.mode == SleepTimerMode.VIDEOS -> "${timer.videosLeft}个后"
                        else -> "定时关闭"
                    },
                    active = timer.active,
                    onClick = { timerOpen = true }
                )
                ToolButton(
                    icon = {
                        when (mode) {
                            0 -> LoopListGlyph(it)
                            1 -> LoopOneGlyph(it)
                            2 -> ShuffleGlyph(it)
                            else -> StopAfterGlyph(it)
                        }
                    },
                    label = when (mode) {
                        0 -> "列表循环"
                        1 -> "单曲循环"
                        2 -> "随机播放"
                        else -> "播完暂停"
                    },
                    active = false,
                    onClick = { onCycleMode() }
                )
                ToolButton(
                    icon = { QueueGlyph(it) },
                    label = "选集",
                    active = false,
                    onClick = { onOpenQueue() }
                )
            }

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
            SleepTimerSheet(
                timer = timer,
                remainingVideos = remainingVideos,
                currentRemainingMs = (durationMs - positionMs).coerceAtLeast(0L),
                durationMs = durationMs,
                onDismiss = { timerOpen = false },
            )
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
        Row(
            Modifier.align(Alignment.BottomEnd).padding(10.dp)
                .clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = .92f))
                .clickable(role = Role.Button, onClick = onRestoreVideo)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            VideoScreenIcon(OnlineText)
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
private fun VideoScreenIcon(tint: Color) {
    Canvas(Modifier.size(13.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // 外框矩形 - rect x="3" y="5" width="18" height="13" rx="2.5"
        drawRoundRect(
            tint,
            topLeft = Offset(3f * u, 5f * u),
            size = Size(18f * u, 13f * u),
            cornerRadius = CornerRadius(2.5f * u),
            style = stroke
        )

        // 底座线 - path d="M8 21h8"
        drawLine(
            tint,
            start = Offset(8f * u, 21f * u),
            end = Offset(16f * u, 21f * u),
            strokeWidth = 1.9f * u,
            cap = StrokeCap.Round
        )

        // 播放三角形（填充）- path d="M10.5 9.2v4.6l4-2.3z"
        drawPath(Path().apply {
            moveTo(10.5f * u, 9.2f * u)
            relativeLineTo(0f, 4.6f * u)
            relativeLineTo(4f * u, -2.3f * u)
            close()
        }, tint)
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

/** Circular arrow with seconds inside, mirrored for rewind. */
@Composable
private fun SkipGlyph(forward: Boolean, seconds: Int = 15) {
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
        Text("$seconds", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PrevGlyph(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        // 三角形向左 M18 6.2v11.6a.8.8 0 0 1-1.2.7L8.5 12.7a.8.8 0 0 1 0-1.4l8.3-5.8a.8.8 0 0 1 1.2.7z
        drawPath(Path().apply {
            moveTo(18f * u, 6.2f * u)
            lineTo(18f * u, 17.8f * u)
            cubicTo(18f * u, 18.2f * u, 17.6f * u, 18.4f * u, 17.3f * u, 18.2f * u)
            lineTo(8.5f * u, 12.7f * u)
            cubicTo(8.2f * u, 12.5f * u, 8.2f * u, 12.1f * u, 8.5f * u, 11.9f * u)
            lineTo(17.3f * u, 6.1f * u)
            cubicTo(17.6f * u, 5.9f * u, 18f * u, 6.1f * u, 18f * u, 6.4f * u)
            close()
        }, tint)
        // 左侧竖条 rect x="5" y="5.5" width="2.4" height="13" rx="1"
        drawRoundRect(tint, Offset(5f * u, 5.5f * u), Size(2.4f * u, 13f * u), CornerRadius(1f * u))
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
private fun ToolButton(
    icon: @Composable (Color) -> Unit,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
            icon(if (active) Color(0xFFFFB3CA) else Color.White.copy(alpha = .8f))
        }
        Text(
            label,
            color = if (active) Color(0xFFFFB3CA) else Color.White.copy(alpha = .8f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SpeedGlyph(tint: Color) {
    Canvas(Modifier.size(22.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // path d="M4.5 18a8.5 8.5 0 1 1 15 0"
        drawArc(tint, 180f, 180f, false,
            topLeft = Offset(4.5f * u, 9.5f * u),
            size = Size(15f * u, 17f * u),
            style = stroke)
        // path d="M12 14l4-4.5"
        drawLine(tint, Offset(12f * u, 14f * u), Offset(16f * u, 9.5f * u), 1.9f * u, StrokeCap.Round)
        // circle cx="12" cy="14" r="1.3"
        drawCircle(tint, 1.3f * u, Offset(12f * u, 14f * u))
    }
}

@Composable
private fun LoopListGlyph(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // M17 2l4 4-4 4
        drawPath(Path().apply {
            moveTo(17f * u, 2f * u); lineTo(21f * u, 6f * u); lineTo(17f * u, 10f * u)
        }, tint, style = stroke)
        // M3 11V9a3 3 0 0 1 3-3h15
        drawPath(Path().apply {
            moveTo(3f * u, 11f * u); lineTo(3f * u, 9f * u)
            arcTo(Rect(3f * u, 6f * u, 9f * u, 12f * u), 180f, 90f, false)
            lineTo(21f * u, 6f * u)
        }, tint, style = stroke)
        // M7 22l-4-4 4-4
        drawPath(Path().apply {
            moveTo(7f * u, 22f * u); lineTo(3f * u, 18f * u); lineTo(7f * u, 14f * u)
        }, tint, style = stroke)
        // M21 13v2a3 3 0 0 1-3 3H3
        drawPath(Path().apply {
            moveTo(21f * u, 13f * u); lineTo(21f * u, 15f * u)
            arcTo(Rect(15f * u, 12f * u, 21f * u, 18f * u), 0f, 90f, false)
            lineTo(3f * u, 18f * u)
        }, tint, style = stroke)
    }
}

@Composable
private fun LoopOneGlyph(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(Path().apply {
            moveTo(17f * u, 2f * u); lineTo(21f * u, 6f * u); lineTo(17f * u, 10f * u)
        }, tint, style = stroke)
        drawPath(Path().apply {
            moveTo(3f * u, 11f * u); lineTo(3f * u, 9f * u)
            arcTo(Rect(3f * u, 6f * u, 9f * u, 12f * u), 180f, 90f, false)
            lineTo(21f * u, 6f * u)
        }, tint, style = stroke)
        drawPath(Path().apply {
            moveTo(7f * u, 22f * u); lineTo(3f * u, 18f * u); lineTo(7f * u, 14f * u)
        }, tint, style = stroke)
        drawPath(Path().apply {
            moveTo(21f * u, 13f * u); lineTo(21f * u, 15f * u)
            arcTo(Rect(15f * u, 12f * u, 21f * u, 18f * u), 0f, 90f, false)
            lineTo(3f * u, 18f * u)
        }, tint, style = stroke)
        // "1" 数字 - M11 10.5l1.5-1v5
        val numStroke = Stroke(1.7f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(Path().apply {
            moveTo(11f * u, 10.5f * u); lineTo(12.5f * u, 9.5f * u); lineTo(12.5f * u, 14.5f * u)
        }, tint, style = numStroke)
    }
}

@Composable
private fun ShuffleGlyph(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // M16 3h5v5
        drawPath(Path().apply {
            moveTo(16f * u, 3f * u); lineTo(21f * u, 3f * u); lineTo(21f * u, 8f * u)
        }, tint, style = stroke)
        // M4 20L21 3
        drawLine(tint, Offset(4f * u, 20f * u), Offset(21f * u, 3f * u), 1.9f * u, StrokeCap.Round)
        // M21 16v5h-5
        drawPath(Path().apply {
            moveTo(21f * u, 16f * u); lineTo(21f * u, 21f * u); lineTo(16f * u, 21f * u)
        }, tint, style = stroke)
        // M15 15l6 6
        drawLine(tint, Offset(15f * u, 15f * u), Offset(21f * u, 21f * u), 1.9f * u, StrokeCap.Round)
        // M4 4l5 5
        drawLine(tint, Offset(4f * u, 4f * u), Offset(9f * u, 9f * u), 1.9f * u, StrokeCap.Round)
    }
}

@Composable
private fun StopAfterGlyph(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        // 暂停符号 - 两个竖条
        drawRoundRect(tint, Offset(7f * u, 5f * u), Size(3f * u, 14f * u), CornerRadius(1f * u))
        drawRoundRect(tint, Offset(14f * u, 5f * u), Size(3f * u, 14f * u), CornerRadius(1f * u))
    }
}

@Composable
private fun QueueGlyph(tint: Color) {
    Canvas(Modifier.size(24.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(1.9f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        // M4 6h11 M4 12h11 M4 18h7
        drawLine(tint, Offset(4f * u, 6f * u), Offset(15f * u, 6f * u), 1.9f * u, StrokeCap.Round)
        drawLine(tint, Offset(4f * u, 12f * u), Offset(15f * u, 12f * u), 1.9f * u, StrokeCap.Round)
        drawLine(tint, Offset(4f * u, 18f * u), Offset(11f * u, 18f * u), 1.9f * u, StrokeCap.Round)
        // M18 14v6
        drawLine(tint, Offset(18f * u, 14f * u), Offset(18f * u, 20f * u), 1.9f * u, StrokeCap.Round)
        // circle cx="16.5" cy="19.5" r="1.8"
        drawCircle(tint, 1.8f * u, Offset(16.5f * u, 19.5f * u), style = stroke)
        // M18 14l3 1
        drawLine(tint, Offset(18f * u, 14f * u), Offset(21f * u, 15f * u), 1.9f * u, StrokeCap.Round)
    }
}

private fun formatTimerBadge(remainingMs: Long): String {
    val mins = (remainingMs / 60_000).toInt()
    return if (mins > 0) "${mins}分钟" else "不到1分钟"
}

private tailrec fun Context.findListenActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findListenActivity()
    else -> null
}
