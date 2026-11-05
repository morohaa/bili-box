package io.github.bilibox.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.bilibox.core.ui.theme.Muted

@Composable
internal fun VideoMetaIcon(kind: String, modifier: Modifier = Modifier, tint: Color = Muted) {
    Canvas(modifier) {
        val strokeWidth = size.minDimension * .065f
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val left = size.width * .14f
        val right = size.width * .86f
        val top = size.height * .14f
        val bottom = size.height * .86f
        when (kind) {
            "play" -> {
                drawRoundRect(
                    tint,
                    Offset(left, top),
                    androidx.compose.ui.geometry.Size(right - left, bottom - top),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * .18f),
                    style = stroke
                )
                val path = Path().apply {
                    moveTo(size.width * .43f, size.height * .31f)
                    lineTo(size.width * .67f, size.height * .5f)
                    lineTo(size.width * .43f, size.height * .69f)
                    close()
                }
                drawPath(path, tint)
            }

            "comment" -> {
                drawRoundRect(
                    tint,
                    Offset(left, top),
                    androidx.compose.ui.geometry.Size(right - left, bottom - top),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * .16f),
                    style = stroke
                )
                drawCircle(tint, size.minDimension * .055f, Offset(size.width * .31f, size.height * .4f))
                drawLine(tint, Offset(size.width * .43f, size.height * .4f), Offset(size.width * .7f, size.height * .4f), strokeWidth)
                drawCircle(tint, size.minDimension * .055f, Offset(size.width * .31f, size.height * .62f))
                drawLine(tint, Offset(size.width * .43f, size.height * .62f), Offset(size.width * .62f, size.height * .62f), strokeWidth)
            }

            "author" -> {
                drawCircle(
                    tint,
                    size.minDimension * .155f,
                    Offset(size.width * .5f, size.height * .32f),
                    style = stroke
                )
                val shoulders = Path().apply {
                    moveTo(size.width * .18f, size.height * .87f)
                    cubicTo(
                        size.width * .18f,
                        size.height * .61f,
                        size.width * .35f,
                        size.height * .52f,
                        size.width * .5f,
                        size.height * .52f
                    )
                    cubicTo(
                        size.width * .65f,
                        size.height * .52f,
                        size.width * .82f,
                        size.height * .61f,
                        size.width * .82f,
                        size.height * .87f
                    )
                }
                drawPath(shoulders, tint, style = stroke)
            }

            "date" -> {
                drawRoundRect(
                    tint,
                    Offset(left, top + size.height * .08f),
                    androidx.compose.ui.geometry.Size(right - left, bottom - top - size.height * .08f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * .12f),
                    style = stroke
                )
                drawLine(tint, Offset(left, size.height * .38f), Offset(right, size.height * .38f), strokeWidth)
                drawLine(tint, Offset(size.width * .34f, size.height * .08f), Offset(size.width * .34f, size.height * .3f), strokeWidth)
                drawLine(tint, Offset(size.width * .66f, size.height * .08f), Offset(size.width * .66f, size.height * .3f), strokeWidth)
            }
        }
    }
}
