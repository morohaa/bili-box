package com.example.bilibox

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * 播放倍速面板。
 *
 * 设计参考了「小电视在刻度轨道上奔跑」的方案：连续的 0.05x 刻度、蓝粉渐变的进度轨、
 * 跑动节奏与当前倍速绑定的小电视，以及一句随速度变化的心情文案。
 */

internal enum class SpeedPanelMode { Drawer, Sheet }

internal const val PlaybackSpeedMin = 0.5f
internal const val PlaybackSpeedMax = 3f
internal const val PlaybackSpeedStep = 0.05f
internal val PlaybackSpeedPresets = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f)

/** Temporary speed used while the video area is held down. */
internal const val PlaybackSpeedBoost = 2f

/**
 * 长按只会加速：当前倍速低于 2x 时临时切到 2x，已经更快时保持用户的选择，
 * 避免“长按反而慢下来”。
 */
internal fun longPressPlaybackSpeed(current: Float): Float = maxOf(current, PlaybackSpeedBoost)

private val SpeedBlue = Color(0xFF00AEEC)
private val SpeedInk = Color(0xFF18191C)
private val SpeedPinkDeep = Color(0xFFE8547F)
private val SpeedPinkDark = Color(0xFFC9416B)
private val SpeedScreen = Color(0xFFFDFCFB)

private const val SpeedTickCount = 50

internal fun quantizePlaybackSpeed(value: Float): Float {
    val stepped = (value / PlaybackSpeedStep).roundToInt() * PlaybackSpeedStep
    return stepped.coerceIn(PlaybackSpeedMin, PlaybackSpeedMax)
}

internal fun formatSpeedReadout(value: Float): String =
    String.format(Locale.US, "%.2f", value)

/** Compact label for pills and the player bar: 1x / 1.5x / 0.75x. */
internal fun formatSpeedBadge(value: Float): String {
    val text = String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    return "${text}x"
}

internal fun speedMood(value: Float): String = when {
    value < 0.6f -> "慢慢挪"
    value < 0.9f -> "溜达中"
    value < 1.1f -> "散步中"
    value < 1.4f -> "小跑中"
    value < 1.8f -> "快跑中"
    value < 2.5f -> "冲刺！"
    else -> "起飞！！"
}

/** Speed entry point used by the player chrome. */
@Composable
internal fun SpeedBarChip(
    speed: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val active = abs(speed - 1f) > 0.001f
    Row(
        modifier.height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (active) OnlinePink.copy(alpha = .16f) else Color.White.copy(alpha = .10f)
            )
            .border(
                1.dp,
                if (active) OnlinePink.copy(alpha = .55f) else Color.White.copy(alpha = .10f),
                RoundedCornerShape(8.dp)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "倍速",
            color = if (active) OnlinePink else Color.White.copy(alpha = .92f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
        if (active) {
            Spacer(Modifier.width(4.dp))
            Text(
                formatSpeedBadge(speed),
                color = OnlinePink,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun SpeedPanel(
    speed: Float,
    mode: SpeedPanelMode,
    onDismiss: () -> Unit,
    onSpeedChange: (Float) -> Unit,
) {
    val current = quantizePlaybackSpeed(speed)
    val haptics = LocalHapticFeedback.current
    var lastEmitted by remember { mutableFloatStateOf(current) }
    LaunchedEffect(current) {
        if (abs(current - lastEmitted) > 0.0005f) lastEmitted = current
    }

    fun emit(value: Float) {
        val next = quantizePlaybackSpeed(value)
        if (abs(next - lastEmitted) < 0.0005f) return
        lastEmitted = next
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onSpeedChange(next)
    }

    // The drawer is edge-to-edge: no outer margins, square corners on the
    // screen edges. Only the modal sheet keeps its rounded top.
    val shape = if (mode == SpeedPanelMode.Sheet) {
        RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    } else {
        RoundedCornerShape(0.dp)
    }
    val panelBrush = Brush.verticalGradient(
        listOf(Color(0xF91C1E29), Color(0xFC0C0D13))
    )
    val scrimSource = remember { MutableInteractionSource() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelWidth = (maxWidth * 0.50f).coerceIn(340.dp, 440.dp)
        // Short landscape windows (and split-screen) get a tighter rhythm so
        // the header and the presets always fit without scrolling.
        val compact = maxHeight < 340.dp
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = if (mode == SpeedPanelMode.Sheet) .46f else .30f))
                .clickable(
                    interactionSource = scrimSource,
                    indication = null,
                    onClick = onDismiss
                )
                .semantics { contentDescription = "关闭倍速面板" }
        )
        val placement = when (mode) {
            SpeedPanelMode.Drawer -> Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(panelWidth)
            SpeedPanelMode.Sheet -> Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        }
        Column(
            placement
                .clip(shape)
                .background(panelBrush)
                .then(
                    if (mode == SpeedPanelMode.Drawer) {
                        // A hairline instead of a full border, so the panel
                        // separates from the video without drawing a seam
                        // against the top / right / bottom screen edges.
                        Modifier.drawBehind {
                            drawLine(
                                color = Color.White.copy(alpha = .10f),
                                start = Offset.Zero,
                                end = Offset(0f, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    } else Modifier
                )
                .pointerInput(Unit) { detectTapGestures { } },
            verticalArrangement = Arrangement.Center
        ) {
            if (mode == SpeedPanelMode.Sheet) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally)
                        .padding(top = 9.dp)
                        .width(38.dp)
                        .height(4.dp)
                        .background(Color.White.copy(alpha = .18f), CircleShape)
                )
            }
            SpeedPanelContent(
                speed = current,
                compact = compact,
                onDismiss = onDismiss,
                onScrub = { fraction ->
                    emit(PlaybackSpeedMin + fraction * (PlaybackSpeedMax - PlaybackSpeedMin))
                },
                onStep = { direction -> emit(current + direction * PlaybackSpeedStep) },
                onPreset = { value ->
                    emit(value)
                    // A modal sheet hands the stage back to the video once the
                    // user has made a choice; the side drawer is a live control
                    // and stays open so the runner can be watched.
                    if (mode == SpeedPanelMode.Sheet) onDismiss()
                },
            )
        }
    }
}

@Composable
private fun SpeedPanelContent(
    speed: Float,
    compact: Boolean,
    onDismiss: () -> Unit,
    onScrub: (Float) -> Unit,
    onStep: (Int) -> Unit,
    onPreset: (Float) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .navigationBarsPadding()
            .padding(vertical = if (compact) 12.dp else 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SpeedMoodChip(speedMood(speed))
            Spacer(Modifier.weight(1f))
            SpeedCloseButton(onDismiss)
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatSpeedReadout(speed),
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp,
                    style = TextStyle(fontFeatureSettings = "tnum")
                )
                Text(
                    "x",
                    color = Color.White.copy(alpha = .48f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            SpeedStepButton(
                symbol = "−",
                description = "减慢 0.05 倍速",
                enabled = speed > PlaybackSpeedMin + 0.001f,
                onStep = { onStep(-1) }
            )
            Spacer(Modifier.width(8.dp))
            SpeedStepButton(
                symbol = "+",
                description = "加快 0.05 倍速",
                enabled = speed < PlaybackSpeedMax - 0.001f,
                onStep = { onStep(1) }
            )
        }
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            SpeedRail(
                speed = speed,
                trackHeight = if (compact) 112.dp else 132.dp,
                onScrubFraction = onScrub
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
            SpeedPresetRow(speed = speed, onPreset = onPreset)
        }
        if (!compact) {
            Spacer(Modifier.height(10.dp))
            Text(
                "长按画面 · 临时 ${formatSpeedBadge(PlaybackSpeedBoost)} 播放",
                color = Color.White.copy(alpha = .38f),
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
private fun SpeedMoodChip(mood: String) {
    Row(
        Modifier.clip(CircleShape)
            .background(OnlinePink.copy(alpha = .13f))
            .border(1.dp, OnlinePink.copy(alpha = .22f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(4.dp).background(OnlinePink, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(
            mood,
            color = OnlinePink,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun SpeedCloseButton(onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "收起倍速面板" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(12.dp)) {
            val stroke = 1.7.dp.toPx()
            val color = Color.White.copy(alpha = .66f)
            drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun SpeedStepButton(
    symbol: String,
    description: String,
    enabled: Boolean,
    onStep: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val step by rememberUpdatedState(onStep)
    var repeated by remember { mutableStateOf(false) }
    LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) return@LaunchedEffect
        repeated = false
        delay(380)
        while (true) {
            repeated = true
            step()
            delay(70)
        }
    }
    Box(
        Modifier.size(34.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) .08f else .035f))
            .border(
                1.dp,
                Color.White.copy(alpha = if (enabled) .16f else .05f),
                CircleShape
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = { if (!repeated) step() }
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Text(
            symbol,
            color = Color.White.copy(alpha = if (enabled) .92f else .28f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 16.sp
        )
    }
}

@Composable
private fun SpeedPresetRow(
    speed: Float,
    onPreset: (Float) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        PlaybackSpeedPresets.forEach { option ->
            val selected = abs(speed - option) < 0.001f
            Box(
                Modifier.weight(1f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) OnlinePink.copy(alpha = .20f)
                        else Color.White.copy(alpha = .06f)
                    )
                    .border(
                        1.dp,
                        if (selected) OnlinePink else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .selectable(
                        selected = selected,
                        role = Role.RadioButton,
                        onClick = { onPreset(option) }
                    )
                    .semantics {
                        contentDescription = "选择倍速 ${formatSpeedBadge(option)}"
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    formatSpeedBadge(option),
                    color = if (selected) OnlinePink else Color.White.copy(alpha = .74f),
                    fontSize = 10.5.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

private val RulerLabels = listOf("0.5", "1.0", "1.5", "2.0", "2.5", "3.0")

@Composable
private fun SpeedRail(
    speed: Float,
    trackHeight: Dp,
    onScrubFraction: (Float) -> Unit,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = remember {
        TextStyle(
            fontSize = 10.sp,
            color = Color.White.copy(alpha = .40f),
            fontFeatureSettings = "tnum"
        )
    }
    val labels = remember(measurer) {
        RulerLabels.map { measurer.measure(text = it, style = labelStyle) }
    }
    val phase = remember { mutableFloatStateOf(PI.toFloat() / 2f) }
    val blink = remember { mutableFloatStateOf(0f) }
    val latestSpeed by rememberUpdatedState(speed)

    // One shared clock drives the leg cycle and the little idle breathing.
    // withInfiniteAnimationFrameNanos keeps this out of the way of tests and
    // accessibility tools that ask for animations to settle.
    LaunchedEffect(Unit) {
        var previous = 0L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (previous == 0L) previous = now
                val dt = ((now - previous) / 1_000_000_000f).coerceIn(0f, 0.05f)
                previous = now
                val cadence = 1.05f + latestSpeed * 0.95f
                phase.floatValue = (phase.floatValue + dt * 2f * PI.toFloat() * cadence) %
                    (2f * PI.toFloat())
            }
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2_200L, 5_200L))
            blink.floatValue = 1f
            delay(110)
            blink.floatValue = 0f
        }
    }

    val progress = ((speed - PlaybackSpeedMin) / (PlaybackSpeedMax - PlaybackSpeedMin))
        .coerceIn(0f, 1f)
    val railInset = 30.dp
    val density = LocalDensity.current
    val railInsetPx = with(density) { railInset.toPx() }
    val scrub by rememberUpdatedState(onScrubFraction)

    Box(
        Modifier.fillMaxWidth()
            .height(trackHeight)
            .semantics {
                contentDescription = "播放速度"
                stateDescription = "${formatSpeedReadout(speed)} 倍速"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = speed,
                    range = PlaybackSpeedMin..PlaybackSpeedMax,
                    steps = SpeedTickCount - 1
                )
                setProgress { target ->
                    val clamped = target.coerceIn(PlaybackSpeedMin, PlaybackSpeedMax)
                    onScrubFraction(
                        (clamped - PlaybackSpeedMin) / (PlaybackSpeedMax - PlaybackSpeedMin)
                    )
                    true
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val railY = size.height - 36.dp.toPx()
            val startX = railInsetPx
            val endX = size.width - railInsetPx
            val dotX = startX + (endX - startX) * progress
            val railHeight = 4.dp.toPx()

            // Soft light under the runner, so the rail reads as a stage.
            val glowRadius = 86.dp.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(OnlinePink.copy(alpha = .17f), Color.Transparent),
                    center = Offset(dotX, railY - 14.dp.toPx()),
                    radius = glowRadius
                ),
                radius = glowRadius,
                center = Offset(dotX, railY - 14.dp.toPx())
            )

            drawRoundRect(
                color = Color.White.copy(alpha = .15f),
                topLeft = Offset(startX, railY - railHeight / 2f),
                size = Size(endX - startX, railHeight),
                cornerRadius = CornerRadius(railHeight / 2f)
            )
            if (progress > 0.002f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(SpeedBlue, OnlinePink),
                        startX = startX,
                        endX = dotX
                    ),
                    topLeft = Offset(startX, railY - railHeight / 2f),
                    size = Size((dotX - startX).coerceAtLeast(railHeight), railHeight),
                    cornerRadius = CornerRadius(railHeight / 2f)
                )
            }

            for (step in 0..SpeedTickCount) {
                val x = startX + (endX - startX) * step / SpeedTickCount
                val height = when {
                    step % 10 == 0 -> 9.dp.toPx()
                    step % 5 == 0 -> 6.dp.toPx()
                    else -> 3.5.dp.toPx()
                }
                val alpha = when {
                    step % 10 == 0 -> .52f
                    step % 5 == 0 -> .30f
                    else -> .16f
                }
                drawLine(
                    color = Color.White.copy(alpha = alpha),
                    start = Offset(x, railY + 7.dp.toPx()),
                    end = Offset(x, railY + 7.dp.toPx() + height),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            labels.forEachIndexed { index, layout ->
                val x = startX + (endX - startX) * index / (labels.size - 1).toFloat()
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(
                        x - layout.size.width / 2f,
                        railY + 21.dp.toPx()
                    )
                )
            }

            // Playhead marker.
            drawCircle(OnlinePink.copy(alpha = .30f), 7.dp.toPx(), Offset(dotX, railY))
            drawCircle(Color.White, 3.dp.toPx(), Offset(dotX, railY))

            drawSpeedRunner(
                centerX = dotX,
                footY = railY - 7.dp.toPx(),
                unit = 60.dp.toPx() / 100f,
                speed = speed,
                phase = phase.floatValue,
                blink = blink.floatValue
            )
        }
        Box(
            Modifier.fillMaxSize().pointerInput(railInsetPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    fun emitAt(positionX: Float) {
                        val usable = (size.width - railInsetPx * 2f).coerceAtLeast(1f)
                        scrub(((positionX - railInsetPx) / usable).coerceIn(0f, 1f))
                    }
                    emitAt(down.position.x)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        emitAt(change.position.x)
                    }
                }
            }
        )
    }
}

private fun DrawScope.drawSpeedRunner(
    centerX: Float,
    footY: Float,
    unit: Float,
    speed: Float,
    phase: Float,
    blink: Float,
) {
    val motion = speed
    val gait = (motion * 2f).coerceIn(0f, 1f)
    val originX = centerX - 50f * unit
    val originY = footY - 92.5f * unit
    fun x(value: Float) = originX + value * unit
    fun y(value: Float) = originY + value * unit

    val cosPhase = cos(phase)
    val sinPhase = sin(phase)
    val cosSq = cosPhase * cosPhase
    val sinSq = sinPhase * sinPhase
    val bob = min(8f, motion * 3.2f) * gait * cosSq
    val squash = min(1f, motion / 1.6f) * gait
    val scaleX = 1f + .06f * squash * sinSq - .03f * squash * cosSq
    val scaleY = 1f - .08f * squash * sinSq + .05f * squash * cosSq
    val lean = min(15f, max(0f, (motion - .7f) * 5.5f)) + sinPhase * 1.2f
    // Deliberately exaggerated: at a glance the little television must read as
    // running, even though the character is only ~60dp wide.
    val stride = min(17f, 4f + motion * 5.5f) * gait
    val lift = min(14f, 2f + motion * 4.5f) * gait
    val leanRad = lean * PI.toFloat() / 180f

    // Speed streaks behind the little television.
    val streak = ((motion - 1.2f) / 1.8f).coerceIn(0f, 1f)
    if (streak > .01f) {
        val bodyLeft = x(18f)
        listOf(
            Triple(38f, 13f, 0f),
            Triple(55f, 21f, 1.3f),
            Triple(71f, 9f, 2.4f)
        ).forEach { (yUnit, length, phaseOffset) ->
            val lengthPx = (length + 16f * streak +
                sin(phase * 3f + phaseOffset) * 3f * streak) * unit * (0.55f + streak)
            if (lengthPx > 1f) {
                val yPx = y(yUnit) - bob * unit * .5f
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = .14f + .46f * streak)
                        ),
                        startX = bodyLeft - lengthPx,
                        endX = bodyLeft
                    ),
                    start = Offset(bodyLeft - lengthPx, yPx),
                    end = Offset(bodyLeft, yPx),
                    strokeWidth = 1.8f * unit,
                    cap = StrokeCap.Round
                )
            }
        }
    }

    val shadowRadius = (20f - bob * 1.3f).coerceAtLeast(6f)
    drawOval(
        color = Color.Black.copy(alpha = (.40f * (1f - bob * .06f)).coerceIn(.12f, .40f)),
        topLeft = Offset(x(50f - shadowRadius), y(96.5f) - 2.6f * unit),
        size = Size(shadowRadius * 2f * unit, 5.2f * unit)
    )

    // Dust puffs behind the feet make the gait readable at a glance.
    val puffStrength = ((motion - 0.8f) / 1.7f).coerceIn(0f, 1f)
    if (puffStrength > 0.01f) {
        repeat(3) { index ->
            val t = (phase / (2f * PI.toFloat()) + index / 3f) % 1f
            drawCircle(
                color = Color.White.copy(alpha = (1f - t) * 0.20f * puffStrength),
                radius = (1.2f + t * 2.2f) * unit,
                center = Offset(
                    x = x(50f) - 18f * unit - t * 18f * unit,
                    y = y(94.5f) - sin(t * PI.toFloat()) * 3f * unit
                )
            )
        }
    }

    fun drawLeg(dx: Float, theta: Float, color: Color) {
        val cs = cos(leanRad)
        val sn = sin(leanRad)
        val hipX = 50f + dx * scaleX * cs + sn
        val hipY = 76f + dx * scaleX * sn - cs - bob
        val footX = 50f + dx + 2f + lean * .25f - stride * cos(theta)
        val air = max(0f, sin(theta))
        val footY = 92.5f - lift * air
        drawLine(
            color = color,
            start = Offset(x(hipX), y(hipY)),
            end = Offset(x(footX - 1.5f), y(footY - 1f)),
            strokeWidth = 5.5f * unit,
            cap = StrokeCap.Round
        )
        withTransform({ rotate(-air * 16f * gait, Offset(x(footX), y(footY))) }) {
            drawOval(
                color = color,
                topLeft = Offset(x(footX - 6f), y(footY) - 3.6f * unit),
                size = Size(12f * unit, 7.2f * unit)
            )
        }
    }

    // Back leg sits behind the body.
    drawLeg(-9f, phase + PI.toFloat(), SpeedPinkDark)

    withTransform({
        translate(0f, -bob * unit)
        rotate(lean, Offset(x(50f), y(76f)))
        translate(x(50f), y(76f))
        scale(scaleX, scaleY, Offset.Zero)
        translate(-x(50f), -y(76f))
    }) {
        // Antennae.
        val antennaSwing = sin(phase - .6f) * (3f + motion * 3.5f) * gait + lean * .5f
        withTransform({ rotate(-antennaSwing, Offset(x(50f), y(28f))) }) {
            val antennaStroke = Stroke(3.4f * unit, cap = StrokeCap.Round)
            drawLine(
                Color.White,
                Offset(x(41f), y(28f)),
                Offset(x(33f), y(13f)),
                antennaStroke.width,
                StrokeCap.Round
            )
            drawLine(
                Color.White,
                Offset(x(59f), y(28f)),
                Offset(x(67f), y(13f)),
                antennaStroke.width,
                StrokeCap.Round
            )
            drawCircle(Color.White, 3f * unit, Offset(x(33f), y(13f)))
            drawCircle(Color.White, 3f * unit, Offset(x(67f), y(13f)))
        }

        drawRoundRect(
            color = OnlinePink,
            topLeft = Offset(x(18f), y(26f)),
            size = Size(64f * unit, 50f * unit),
            cornerRadius = CornerRadius(16f * unit)
        )
        drawRoundRect(
            color = SpeedScreen,
            topLeft = Offset(x(25f), y(33f)),
            size = Size(50f * unit, 35f * unit),
            cornerRadius = CornerRadius(10f * unit)
        )

        // Blush.
        drawOval(
            color = OnlinePink.copy(alpha = .35f),
            topLeft = Offset(x(32.5f) - 3.6f * unit, y(57f) - 2.2f * unit),
            size = Size(7.2f * unit, 4.4f * unit)
        )
        drawOval(
            color = OnlinePink.copy(alpha = .35f),
            topLeft = Offset(x(67.5f) - 3.6f * unit, y(57f) - 2.2f * unit),
            size = Size(7.2f * unit, 4.4f * unit)
        )

        val face = when {
            speed < 1.3f -> 0
            speed < 2.05f -> 1
            else -> 2
        }
        when (face) {
            0 -> {
                val eyeHeight = if (blink > .5f) 3.8f * .14f else 3.8f
                listOf(41f, 59f).forEach { eyeX ->
                    drawOval(
                        color = SpeedInk,
                        topLeft = Offset(x(eyeX) - 3.4f * unit, y(47f) - eyeHeight * unit),
                        size = Size(6.8f * unit, eyeHeight * 2f * unit)
                    )
                }
            }
            1 -> {
                val stroke = Stroke(3f * unit, cap = StrokeCap.Round)
                drawPath(
                    Path().apply {
                        moveTo(x(36.5f), y(49f))
                        quadraticTo(x(41f), y(42.5f), x(45.5f), y(49f))
                    },
                    SpeedInk,
                    style = stroke
                )
                drawPath(
                    Path().apply {
                        moveTo(x(54.5f), y(49f))
                        quadraticTo(x(59f), y(42.5f), x(63.5f), y(49f))
                    },
                    SpeedInk,
                    style = stroke
                )
            }
            else -> {
                val stroke = Stroke(3.2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawPath(
                    Path().apply {
                        moveTo(x(37f), y(43.5f))
                        lineTo(x(44f), y(47f))
                        lineTo(x(37f), y(50.5f))
                    },
                    SpeedInk,
                    style = stroke
                )
                drawPath(
                    Path().apply {
                        moveTo(x(63f), y(43.5f))
                        lineTo(x(56f), y(47f))
                        lineTo(x(63f), y(50.5f))
                    },
                    SpeedInk,
                    style = stroke
                )
            }
        }

        if (speed > 1.6f) {
            val openHeight = 2.4f + abs(sinPhase) * 1.4f
            drawOval(
                color = SpeedInk,
                topLeft = Offset(x(50f) - 3.6f * unit, y(57.5f) - openHeight * unit),
                size = Size(7.2f * unit, openHeight * 2f * unit)
            )
        } else {
            drawPath(
                Path().apply {
                    moveTo(x(46.5f), y(56f))
                    quadraticTo(x(50f), y(59.5f), x(53.5f), y(56f))
                },
                SpeedInk,
                style = Stroke(2.6f * unit, cap = StrokeCap.Round)
            )
        }

        val sweat = ((motion - 2.2f) * 1.6f).coerceIn(0f, 1f)
        if (sweat > .01f) {
            withTransform({ translate(0f, sinPhase * 1.5f * unit) }) {
                drawPath(
                    Path().apply {
                        moveTo(x(80f), y(22f))
                        quadraticTo(x(84.5f), y(28.5f), x(80f), y(31.5f))
                        quadraticTo(x(75.5f), y(28.5f), x(80f), y(22f))
                        close()
                    },
                    SpeedBlue.copy(alpha = sweat)
                )
            }
        }
    }

    // Front leg crosses in front of the body.
    drawLeg(9f, phase, SpeedPinkDeep)
}
