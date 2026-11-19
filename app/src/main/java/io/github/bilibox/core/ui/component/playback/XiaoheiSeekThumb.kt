package io.github.bilibox.core.ui.component.playback

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate

private val Fur = Color(0xFF191A1D)
private val InnerEar = Color(0xFFE3D8A1)
private val EyeRim = Color(0xFFFFF9F2)
private val Pupil = Color(0xFF141316)
private val NoseColor = Color(0xFF9DB9B0)
private val EyeCenters = listOf(10f, 26f)

private val Head = Path().apply {
    moveTo(1f, 4.5f)
    quadraticTo(5f, 3.3f, 12f, 5.8f)
    quadraticTo(18f, 4f, 24f, 5.8f)
    quadraticTo(31f, 3.3f, 35f, 4.5f)
    lineTo(34.5f, 15f)
    cubicTo(35f, 23.5f, 28.7f, 28.6f, 18f, 28.6f)
    cubicTo(7.3f, 28.6f, 1f, 23.5f, 1.5f, 15f)
    close()
}

private val Ears = Path().apply {
    moveTo(1.5f, 5.5f)
    quadraticTo(4f, 8f, 6.2f, 12.2f)
    lineTo(2f, 15.8f)
    close()
    moveTo(34.5f, 5.5f)
    quadraticTo(32f, 8f, 29.8f, 12.2f)
    lineTo(34f, 15.8f)
    close()
}

private val Nose = Path().apply {
    moveTo(15.7f, 24.1f)
    quadraticTo(18f, 23.4f, 20.3f, 24.1f)
    quadraticTo(19.6f, 26.8f, 18f, 27.6f)
    quadraticTo(16.4f, 26.8f, 15.7f, 24.1f)
    close()
}

@Composable
internal fun XiaoheiSeekThumb(modifier: Modifier) {
    Canvas(modifier) {
        val scale = minOf(size.width / 36f, size.height / 32f)
        translate((size.width - 36f * scale) / 2f, (size.height - 32f * scale) / 2f) {
            scale(scale, pivot = Offset.Zero) {
                drawPath(Head, Fur)
                drawPath(Ears, InnerEar)
                EyeCenters.forEach { eyeX ->
                    drawOval(EyeRim, Offset(eyeX - 7f, 9.5f), Size(14f, 15f))
                    drawOval(Pupil, Offset(eyeX - 4.8f, 12f), Size(9.6f, 10f))
                    drawCircle(EyeRim, 1.15f, Offset(eyeX + 1.4f, 14.3f))
                }
                drawPath(Nose, NoseColor)
            }
        }
    }
}
