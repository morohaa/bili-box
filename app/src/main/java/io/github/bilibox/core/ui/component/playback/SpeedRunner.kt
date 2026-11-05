package io.github.bilibox.core.ui.component.playback

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
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
import io.github.bilibox.core.ui.theme.OnlinePink
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal fun DrawScope.drawSpeedRunner(
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
            val lengthPx = (
                length + 16f * streak +
                    sin(phase * 3f + phaseOffset) * 3f * streak
                ) * unit * (0.55f + streak)
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
