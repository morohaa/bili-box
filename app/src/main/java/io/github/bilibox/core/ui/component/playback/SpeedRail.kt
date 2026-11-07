package io.github.bilibox.core.ui.component.playback

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.playback.PlaybackSpeedMax
import io.github.bilibox.core.playback.PlaybackSpeedMin
import io.github.bilibox.core.ui.theme.OnlinePink
import kotlin.math.PI
import kotlin.random.Random
import kotlinx.coroutines.delay

internal val RulerLabels = listOf("0.5", "1.0", "1.5", "2.0", "2.5", "3.0")

@Composable
internal fun SpeedRail(
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
        val accent = OnlinePink
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
                    colors = listOf(accent.copy(alpha = .17f), Color.Transparent),
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
                        colors = listOf(SpeedBlue, accent),
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
            drawCircle(accent.copy(alpha = .30f), 7.dp.toPx(), Offset(dotX, railY))
            drawCircle(Color.White, 3.dp.toPx(), Offset(dotX, railY))

            drawSpeedRunner(
                centerX = dotX,
                footY = railY - 7.dp.toPx(),
                unit = 60.dp.toPx() / 100f,
                speed = speed,
                phase = phase.floatValue,
                blink = blink.floatValue,
                pink = accent
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
