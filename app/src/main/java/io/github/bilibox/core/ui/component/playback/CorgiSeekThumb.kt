package io.github.bilibox.core.ui.component.playback

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

private val CorgiFur = Color(0xFFF59E0B)
private val CorgiEars = Color(0xFFD97706)
private val CorgiCream = Color(0xFFFFFBEB)
private val CorgiInk = Color(0xFF18181B)
private val CorgiTongue = Color(0xFFFB7185)
private val FaceCenter = Offset(21f, 16.6f)
private val TailPivot = Offset(9.5f, 18.5f)

private val Tail = Path().apply {
    moveTo(TailPivot.x, TailPivot.y)
    cubicTo(4.8f, 16.5f, 4.2f, 12.2f, 7.2f, 11.8f)
    cubicTo(9.8f, 11.4f, 10.8f, 15.5f, 9.8f, 18.5f)
    close()
}

private val Ears = Path().apply {
    moveTo(13f, 11.5f)
    lineTo(11.5f, 4.5f)
    lineTo(17.5f, 11.5f)
    close()
    moveTo(22.2f, 11.5f)
    lineTo(25.5f, 4.8f)
    lineTo(26.5f, 11.5f)
    close()
}

private val Muzzle = Path().apply {
    moveTo(16f, 13.2f)
    cubicTo(18f, 11.2f, 22f, 11.2f, 24f, 13.2f)
    lineTo(26.5f, 18.5f)
    cubicTo(26.5f, 22f, 23.5f, 24f, 19.5f, 24f)
    lineTo(13.5f, 24f)
    cubicTo(11f, 24f, 9.5f, 22.5f, 9.5f, 20.5f)
    lineTo(16.5f, 20.5f)
    close()
}

private val Tongue = Path().apply {
    moveTo(20.5f, 19.8f)
    quadraticTo(21.5f, 22.2f, 22.6f, 19.8f)
    close()
}

@Composable
internal fun CorgiSeekThumb(modifier: Modifier, animate: Boolean) {
    val tailAngle = if (animate) {
        rememberInfiniteTransition(label = "柯基摇尾巴").animateFloat(
            initialValue = -22f,
            targetValue = 22f,
            animationSpec = infiniteRepeatable(tween(110, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "尾巴角度",
        )
    } else {
        null
    }

    Canvas(modifier) {
        val scale = minOf(size.width / 32f, size.height / 26f)
        translate(center.x - FaceCenter.x * scale, center.y - FaceCenter.y * scale) {
            scale(scale, pivot = Offset.Zero) {
                scale(.7f, pivot = TailPivot) {
                    rotate(tailAngle?.value ?: 0f, pivot = TailPivot) {
                        drawPath(Tail, CorgiFur)
                        drawPath(Tail, CorgiCream, style = Stroke(1f, join = StrokeJoin.Round))
                    }
                }
                drawPath(Ears, CorgiEars)
                drawRoundRect(CorgiFur, Offset(9f, 11f), Size(18f, 13f), CornerRadius(6.5f))
                drawPath(Muzzle, CorgiCream)
                drawCircle(CorgiInk, 1.35f, Offset(18.2f, 15.2f))
                drawCircle(CorgiInk, 1.35f, Offset(23.8f, 15.2f))
                drawOval(CorgiInk, Offset(19.9f, 16.9f), Size(3.2f, 2.2f))
                drawPath(Tongue, CorgiTongue)
            }
        }
    }
}
