package io.github.bilibox.core.ui.component.playback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import io.github.bilibox.core.ui.theme.OnlineText
import io.github.bilibox.core.ui.theme.SeekThumbStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal val SeekThumbWidth = 18.dp

@Composable
internal fun SeekThumb(style: SeekThumbStyle, accent: Color, modifier: Modifier = Modifier) {
    val thumbModifier = modifier.size(width = SeekThumbWidth, height = 16.dp)
    if (style == SeekThumbStyle.TELEVISION) {
        TelevisionSeekThumb(accent, thumbModifier)
        return
    }
    Canvas(thumbModifier) {
        val outline = Stroke(1.35.dp.toPx(), join = StrokeJoin.Round)
        if (style == SeekThumbStyle.DOT) {
            val radius = 5.dp.toPx()
            drawCircle(Color.White, radius)
            drawCircle(accent, radius, style = outline)
        } else {
            val star = style == SeekThumbStyle.STAR
            val pointCount = if (star) 10 else 4
            val outerRadius = (if (star) 7.dp else 6.dp).toPx()
            val path = Path().apply {
                repeat(pointCount) { index ->
                    val angle = -PI / 2.0 + 2.0 * PI * index / pointCount
                    val radius = if (star && index % 2 == 1) outerRadius * .48f else outerRadius
                    val pointX = center.x + cos(angle).toFloat() * radius
                    val pointY = center.y + sin(angle).toFloat() * radius
                    if (index == 0) moveTo(pointX, pointY) else lineTo(pointX, pointY)
                }
                close()
            }
            drawPath(path, Color.White)
            drawPath(path, accent, style = outline)
        }
    }
}

@Composable
private fun TelevisionSeekThumb(accent: Color, modifier: Modifier) {
    Canvas(modifier) {
        val scale = size.width / 24f
        val stroke = Stroke(1.8f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val bodyStart = Offset(2.5f * scale, 4f * scale)
        val bodySize = Size(19f * scale, 14f * scale)
        val bodyCorners = CornerRadius(4.5f * scale)
        drawLine(accent, Offset(7.5f * scale, 1.5f * scale), Offset(10.5f * scale, 4.5f * scale), 2f * scale, StrokeCap.Round)
        drawLine(accent, Offset(16.5f * scale, 1.5f * scale), Offset(13.5f * scale, 4.5f * scale), 2f * scale, StrokeCap.Round)
        drawRoundRect(Color.White, bodyStart, bodySize, bodyCorners)
        drawRoundRect(accent, bodyStart, bodySize, bodyCorners, style = stroke)
        listOf(8.5f, 15.5f).forEach { eye ->
            drawLine(OnlineText, Offset(eye * scale, 10f * scale), Offset(eye * scale, 11.8f * scale), 2f * scale, StrokeCap.Round)
        }
    }
}
