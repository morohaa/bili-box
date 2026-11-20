package io.github.bilibox.core.ui.component.playback

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

private val PuffPink = Color(0xFFFF85A8)
private val PuffEyePositions = listOf(13.2f, 18.8f)
private val HamsterPaw = Color(0xFFFED7AA)

private val PuffSmile = Path().apply {
    moveTo(14.8f, 18.2f)
    quadraticTo(16f, 19.6f, 17.2f, 18.2f)
}

private val HamsterBelly = Path().apply {
    moveTo(10.5f, 14.5f)
    cubicTo(12f, 11.5f, 20f, 11.5f, 21.5f, 14.5f)
    cubicTo(23.5f, 16.5f, 24.2f, 22.5f, 20.5f, 25.5f)
    cubicTo(18f, 27f, 14f, 27f, 11.5f, 25.5f)
    cubicTo(7.8f, 22.5f, 8.5f, 16.5f, 10.5f, 14.5f)
    close()
}

private val SunflowerSeed = Path().apply {
    moveTo(16f, 18.2f)
    cubicTo(18.2f, 18.2f, 19.2f, 21.5f, 16f, 24.8f)
    cubicTo(12.8f, 21.5f, 13.8f, 18.2f, 16f, 18.2f)
    close()
}

@Composable
internal fun PinkPuffSeekThumb(modifier: Modifier, animate: Boolean) {
    SteppingSeekThumb(modifier, animate, faceCenterY = 16.5f, stepHeight = 2.4f, halfCycleMillis = 170, easing = FastOutSlowInEasing) { left, right ->
        drawCenteredOval(Color(0xFFE11D48), Offset(11.5f, 25.5f + left), 4.2f, 2.4f)
        drawCenteredOval(Color(0xFFBE123C), Offset(20.5f, 25.5f + right), 4.2f, 2.4f)
        val leftArm = Offset(5.2f, 16.5f)
        val rightArm = Offset(26.8f, 16.5f)
        rotate(-20f, pivot = leftArm) { drawCenteredOval(PuffPink, leftArm, 2.6f, 2.1f) }
        rotate(20f, pivot = rightArm) { drawCenteredOval(PuffPink, rightArm, 2.6f, 2.1f) }
        val body = Offset(16f, 16.5f)
        drawCircle(PuffPink, 10.5f, body)
        drawCircle(Color(0xFFFB7299), 10.5f, body, style = Stroke(1.2f))
        for (eye in PuffEyePositions) {
            drawCenteredOval(Color(0xFF1E1B4B), Offset(eye, 14.5f), 1.5f, 2.8f)
            drawCenteredOval(Color.White, Offset(eye, 13.4f), .7f, 1.1f)
        }
        val blush = Color(0xFFF43F5E).copy(alpha = .65f)
        drawCenteredOval(blush, Offset(9.8f, 17.5f), 2f, 1.1f)
        drawCenteredOval(blush, Offset(22.2f, 17.5f), 2f, 1.1f)
        drawPath(PuffSmile, Color(0xFF9F1239), style = Stroke(1.3f, cap = StrokeCap.Round))
    }
}

@Composable
internal fun HamsterSeekThumb(modifier: Modifier, animate: Boolean) {
    SteppingSeekThumb(modifier, animate, faceCenterY = 15.5f, stepHeight = 2f, halfCycleMillis = 100, easing = LinearEasing) { left, right ->
        val ear = Color(0xFFD97706)
        drawCircle(ear, 3.4f, Offset(9.5f, 9.5f))
        drawCircle(ear, 3.4f, Offset(22.5f, 9.5f))
        drawCenteredOval(Color(0xFFF59E0B), Offset(16f, 17.5f), 10.8f, 9.8f)
        drawPath(HamsterBelly, Color(0xFFFFFBEB))
        val cheek = Color(0xFFFDA4AF)
        drawCenteredOval(cheek, Offset(9.6f, 18.2f), 2.5f, 1.9f)
        drawCenteredOval(cheek, Offset(22.4f, 18.2f), 2.5f, 1.9f)
        val eye = Color(0xFF18181B)
        drawCircle(eye, 1.45f, Offset(12.5f, 14.5f))
        drawCircle(eye, 1.45f, Offset(19.5f, 14.5f))
        drawCenteredOval(Color(0xFFFB7185), Offset(16f, 16.5f), 1.2f, .85f)
        drawPath(SunflowerSeed, Color(0xFF27272A))
        drawPath(SunflowerSeed, Color(0xFFE4E4E7), style = Stroke(.9f))
        drawCenteredOval(HamsterPaw, Offset(12.6f, 21.5f + left), 2f, 1.5f)
        drawCenteredOval(HamsterPaw, Offset(19.4f, 21.5f + right), 2f, 1.5f)
    }
}

@Composable
private fun SteppingSeekThumb(
    modifier: Modifier,
    animate: Boolean,
    faceCenterY: Float,
    stepHeight: Float,
    halfCycleMillis: Int,
    easing: Easing,
    draw: DrawScope.(left: Float, right: Float) -> Unit,
) {
    val step = if (animate) {
        rememberInfiniteTransition(label = "交替抬脚").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(halfCycleMillis, easing = easing), RepeatMode.Reverse),
            label = "抬脚进度",
        )
    } else {
        null
    }

    Canvas(modifier) {
        val progress = step?.value
        val left = if (progress == null) 0f else -stepHeight * progress
        val right = if (progress == null) 0f else -stepHeight * (1f - progress)
        val scale = minOf(size.width / 28f, size.height / 25f)
        translate(center.x - 16f * scale, center.y - faceCenterY * scale) {
            scale(scale, pivot = Offset.Zero) { draw(left, right) }
        }
    }
}

private fun DrawScope.drawCenteredOval(color: Color, center: Offset, radiusX: Float, radiusY: Float) {
    drawOval(color, center - Offset(radiusX, radiusY), Size(radiusX * 2f, radiusY * 2f))
}
