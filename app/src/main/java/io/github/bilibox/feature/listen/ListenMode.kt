package io.github.bilibox.feature.listen

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import io.github.bilibox.core.playback.ListenPlaybackBridge
import io.github.bilibox.core.playback.ListenQueue
import io.github.bilibox.core.playback.buildListenMetadata
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
                /* handleAudioFocus = */
                true
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
        drawPath(
            Path().apply {
                moveTo(4f * u, 15f * u)
                lineTo(4f * u, 12f * u)
                arcTo(Rect(4f * u, 4f * u, 20f * u, 20f * u), 180f, 180f, false)
                lineTo(20f * u, 15f * u)
            },
            tint,
            style = stroke
        )
        listOf(3f, 16.5f).forEach { x ->
            drawRoundRect(
                tint,
                Offset(x * u, 14f * u),
                Size(4.5f * u, 7f * u),
                CornerRadius(1.6f * u),
                style = stroke
            )
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
            Image(
                cover.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f)))
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ListenVisualizer(playing, Modifier.width(72.dp).height(22.dp), bars = 9)
            Spacer(Modifier.height(8.dp))
            Text(
                if (playing) "正在听视频 · 画面已关闭" else "听视频已暂停",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
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
        Text(
            text,
            color = if (filled) OnlineText else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
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
                    } else {
                        0f
                    }
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
            drawRoundRect(
                brush,
                Offset(k * w + w * .22f, (size.height - h) / 2f),
                Size(bw, h),
                CornerRadius(bw / 2f)
            )
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
                if (progress >= .999f) {
                    drawContent()
                } else {
                    val radius = hypot(max(origin.x, size.width - origin.x), max(origin.y, size.height - origin.y)) * progress
                    clipPath(
                        Path().apply {
                            addOval(Rect(origin, radius))
                        }
                    ) { this@drawWithContent.drawContent() }
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
                        Box(
                            Modifier.size(6.dp).clip(CircleShape)
                                .background(if (playing) Color(0xFF7CF29A) else Color.White.copy(alpha = .4f))
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (info.online) "听视频 · 仅加载音频流" else "听视频 · 画面已停止解码",
                            color = Color.White.copy(alpha = .9f),
                            fontSize = 12.sp
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

            Text(
                title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 24.sp
            )
            if (info.subtitle.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    info.subtitle,
                    color = Color.White.copy(alpha = .65f),
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
                    onValueChange = {
                        scrubbing = true
                        scrubValue = it
                    },
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
                            drawLine(
                                Color.White.copy(alpha = .25f),
                                Offset(0f, y),
                                Offset(size.width, y),
                                size.height,
                                StrokeCap.Round
                            )
                            if (played > 0f) {
                                drawLine(
                                    OnlinePink,
                                    Offset(0f, y),
                                    Offset(size.width * played, y),
                                    size.height,
                                    StrokeCap.Round
                                )
                            }
                        }
                    }
                )
            }
            Row(Modifier.fillMaxWidth()) {
                Text(
                    formatPlaybackTime(if (scrubbing) scrubValue.toLong() else positionMs),
                    color = Color.White.copy(alpha = .65f),
                    fontSize = 11.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    formatPlaybackTime(durationMs, unknownWhenZero = true),
                    color = Color.White.copy(alpha = .65f),
                    fontSize = 11.sp
                )
            }

            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ListenRoundButton("上一个", {
                    if (hasPrevious) {
                        onPrevious()
                    } else {
                        Toast.makeText(context, "已经是第一个了", Toast.LENGTH_SHORT).show()
                    }
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
                    if (hasNext) {
                        onNext()
                    } else {
                        Toast.makeText(context, "已经是最后一个了", Toast.LENGTH_SHORT).show()
                    }
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
