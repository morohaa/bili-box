package io.github.bilibox.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Hand-drawn line icons that share the 24dp grid used elsewhere in BiliBox. */
internal enum class AppIcon {
    SEARCH,
    TUNE,
    LIST,
    GRID,
    FOLDER,
    FILE,
    SORT,
    CLOCK,
    DOWNLOAD,
    LOCK,
    PLAY,
    CHECK,
    CHEVRON_RIGHT,
    CHEVRON_DOWN,
    ARROW_UP,
    ARROW_DOWN,
    MORE,
    MORE_HORIZONTAL,
    FULLSCREEN,
    REFRESH,
    CLOSE,
    SHARE,
    INFO,
    CALENDAR,
    FRAME,
    SIZE,
    FILM,
    PIN,
    DOCUMENT,
    TIMER,
    STATUS,
    PLAYED,
    TITLE,
    BACK,
    SPEED,
    LOOP,
    CAMERA,
    STAR,
    MOON,
    COPY,
    HISTORY,
    TV,
    DANMAKU,
    BULB,
    LOOP_ONE,
    WAVEFORM,
    SHUFFLE,
    ROTATE_LEFT,
    IMAGE,
    FOLDER_ALL,
}

@Composable
internal fun AppGlyphIcon(
    icon: AppIcon,
    tint: Color,
    modifier: Modifier = Modifier.size(22.dp),
    strokeWidth: Float = 1.8f,
    fill: Boolean = false,
) {
    Canvas(modifier) { drawAppGlyph(icon, tint, strokeWidth, fill) }
}

internal fun DrawScope.drawAppGlyph(
    icon: AppIcon,
    tint: Color,
    strokeWidth: Float = 1.8f,
    fill: Boolean = false,
) {
    val unit = size.minDimension / 24f
    scale(unit, unit, pivot = Offset.Zero) {
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun path(data: String) {
            drawPath(PathParser().parsePathString(data).toPath(), tint, style = stroke)
        }
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) {
            drawLine(tint, Offset(x1, y1), Offset(x2, y2), strokeWidth, StrokeCap.Round)
        }
        fun circle(cx: Float, cy: Float, radius: Float, filled: Boolean = false) {
            drawCircle(tint, radius, Offset(cx, cy), style = if (filled) androidx.compose.ui.graphics.drawscope.Fill else stroke)
        }
        when (icon) {
            AppIcon.SEARCH -> {
                circle(11f, 11f, 6.5f)
                line(15.7f, 15.7f, 20f, 20f)
            }

            AppIcon.TUNE -> {
                line(4f, 7f, 11f, 7f)
                line(18f, 7f, 20f, 7f)
                circle(14f, 7f, 2f)
                line(4f, 17f, 6f, 17f)
                line(12f, 17f, 20f, 17f)
                circle(10f, 17f, 2f)
            }

            AppIcon.LIST -> {
                drawRoundRect(tint, Offset(4f, 5f), Size(16f, 5f), CornerRadius(1.5f), style = stroke)
                drawRoundRect(tint, Offset(4f, 14f), Size(16f, 5f), CornerRadius(1.5f), style = stroke)
            }

            AppIcon.GRID -> {
                listOf(4f to 4f, 13f to 4f, 4f to 13f, 13f to 13f).forEach { (x, y) ->
                    drawRoundRect(tint, Offset(x, y), Size(7f, 7f), CornerRadius(1.5f), style = stroke)
                }
            }

            AppIcon.FOLDER -> path(
                "M3 7.5A1.5 1.5 0 0 1 4.5 6H9l2 2h8.5A1.5 1.5 0 0 1 21 9.5v8A1.5 1.5 0 0 1 19.5 19h-15A1.5 1.5 0 0 1 3 17.5z"
            )

            AppIcon.FILE -> {
                path("M7 3h7l5 5v12a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z")
                path("M14 3v5h5")
                path("M10.5 12v5l4-2.5z")
            }

            AppIcon.SORT -> path("M7 4v16M4 17l3 3 3-3M17 20V4M14 7l3-3 3 3")

            AppIcon.CLOCK -> {
                circle(12f, 12f, 8f)
                path("M12 8v4l3 2")
            }

            AppIcon.DOWNLOAD -> path("M12 4v11M7 10l5 5 5-5M5 20h14")

            AppIcon.LOCK -> {
                drawRoundRect(tint, Offset(5f, 10f), Size(14f, 10f), CornerRadius(2f), style = stroke)
                path("M8 10V7a4 4 0 0 1 8 0v3")
            }

            AppIcon.PLAY -> drawPath(
                PathParser().parsePathString("M8 5.5v13l11-6.5z").toPath(),
                tint,
                style = androidx.compose.ui.graphics.drawscope.Fill,
            )

            AppIcon.CHECK -> path("M5 12.5l4.5 4.5L19 7.5")

            AppIcon.CHEVRON_RIGHT -> path("M9 6l6 6-6 6")

            AppIcon.CHEVRON_DOWN -> path("M6 9l6 6 6-6")

            AppIcon.ARROW_UP -> path("M12 19V5M6 11l6-6 6 6")

            AppIcon.ARROW_DOWN -> path("M12 5v14M6 13l6 6 6-6")

            AppIcon.MORE -> {
                circle(12f, 5f, 1.3f, filled = true)
                circle(12f, 12f, 1.3f, filled = true)
                circle(12f, 19f, 1.3f, filled = true)
            }

            AppIcon.MORE_HORIZONTAL -> {
                circle(5f, 12f, 1.6f, filled = true)
                circle(12f, 12f, 1.6f, filled = true)
                circle(19f, 12f, 1.6f, filled = true)
            }

            AppIcon.FULLSCREEN -> {
                path("M8 3H5a2 2 0 0 0-2 2v3")
                path("M21 8V5a2 2 0 0 0-2-2h-3")
                path("M3 16v3a2 2 0 0 0 2 2h3")
                path("M16 21h3a2 2 0 0 0 2-2v-3")
            }

            AppIcon.REFRESH -> {
                path("M20 12a8 8 0 1 1-2.34-5.66")
                path("M20 4v4h-4")
            }

            AppIcon.CLOSE -> path("M6 6l12 12M18 6L6 18")

            AppIcon.SHARE -> {
                circle(18f, 5f, 2.5f)
                circle(6f, 12f, 2.5f)
                circle(18f, 19f, 2.5f)
                line(8.2f, 10.8f, 15.8f, 6.2f)
                line(8.2f, 13.2f, 15.8f, 17.8f)
            }

            AppIcon.INFO -> {
                circle(12f, 12f, 8f)
                line(12f, 11f, 12f, 16f)
                circle(12f, 8f, 1.1f, filled = true)
            }

            AppIcon.CALENDAR -> {
                drawRoundRect(tint, Offset(4f, 5f), Size(16f, 15f), CornerRadius(2f), style = stroke)
                line(4f, 10f, 20f, 10f)
                line(8f, 3f, 8f, 7f)
                line(16f, 3f, 16f, 7f)
            }

            AppIcon.FRAME -> {
                drawRoundRect(tint, Offset(3f, 5f), Size(18f, 14f), CornerRadius(2f), style = stroke)
                path("M7 9v6M7 12h3.5M10.5 9v6M13.5 9v6h1.5a3 3 0 0 0 0-6z")
            }

            AppIcon.SIZE -> {
                path("M7 3h7l4 4v13a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z")
                path("M9 7v3M12 7v3M15 8v2M9 15h6")
            }

            AppIcon.FILM -> {
                drawRoundRect(tint, Offset(3f, 5f), Size(18f, 14f), CornerRadius(2f), style = stroke)
                line(8f, 5f, 8f, 19f)
                line(16f, 5f, 16f, 19f)
                line(3f, 10f, 8f, 10f)
                line(3f, 14f, 8f, 14f)
                line(16f, 10f, 21f, 10f)
                line(16f, 14f, 21f, 14f)
            }

            AppIcon.PIN -> path(
                "M7 3h7l4 4v6M6 4v16a1 1 0 0 0 1 1h5M18 15a3 3 0 0 0-3 3c0 2 3 4 3 4s3-2 3-4a3 3 0 0 0-3-3z"
            )

            AppIcon.DOCUMENT -> {
                path("M7 3h7l5 5v12a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z")
                path("M14 3v5h5")
                path("M9 13h6M9 16h4")
            }

            AppIcon.TIMER -> {
                drawRoundRect(tint, Offset(3f, 4f), Size(18f, 12f), CornerRadius(2f), style = stroke)
                path("M10 7.5v5l4-2.5z")
                line(3f, 20f, 21f, 20f)
                circle(8f, 20f, 1.4f)
            }

            AppIcon.STATUS -> {
                drawCircle(
                    tint,
                    8f,
                    Offset(12f, 12f),
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5f, 2.5f), 0f),
                    ),
                )
                path("M10 9v6l5-3z")
            }

            AppIcon.PLAYED -> {
                path("M20 12a8 8 0 1 0-8 8")
                path("M12 7v5l3 2")
                path("M16 16v5l4.5-2.5z")
            }

            AppIcon.TITLE -> {
                path("M3.5 17L8 6l4.5 11M5.2 13.5h5.6")
                path("M18.5 6v12M15.8 15.3l2.7 2.7 2.7-2.7")
            }

            AppIcon.BACK -> path("M15 5l-7 7 7 7")

            AppIcon.SPEED -> {
                path("M4.5 16a8 8 0 1 1 15 0")
                line(12f, 15.5f, 16.3f, 11.2f)
                circle(12f, 15.5f, 1.3f, filled = true)
            }

            AppIcon.LOOP -> {
                path("M17 3l3 3-3 3")
                path("M4 11V9a3 3 0 0 1 3-3h13")
                path("M7 21l-3-3 3-3")
                path("M20 13v2a3 3 0 0 1-3 3H4")
            }

            AppIcon.CAMERA -> {
                path("M4 8.5A1.5 1.5 0 0 1 5.5 7h2l1.5-2h6L16.5 7h2A1.5 1.5 0 0 1 20 8.5v9a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 17.5z")
                circle(12f, 13f, 3.4f)
            }

            AppIcon.STAR -> drawPath(
                PathParser().parsePathString(
                    "M12 3.6l2.62 5.32 5.88.86-4.25 4.14 1 5.85L12 16.99l-5.25 2.78 1-5.85L3.5 9.78l5.88-.86z"
                ).toPath(),
                tint,
                style = if (fill) androidx.compose.ui.graphics.drawscope.Fill else stroke,
            )

            AppIcon.MOON -> drawPath(
                PathParser().parsePathString(
                    "M20.4 14.3A8.6 8.6 0 0 1 9.7 3.6a8.6 8.6 0 1 0 10.7 10.7z"
                ).toPath(),
                tint,
                style = if (fill) androidx.compose.ui.graphics.drawscope.Fill else stroke,
            )

            AppIcon.COPY -> {
                drawRoundRect(tint, Offset(8f, 8f), Size(12f, 12f), CornerRadius(2f), style = stroke)
                path("M16 8V5a1 1 0 0 0-1-1H5a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h3")
            }

            AppIcon.HISTORY -> {
                path("M4 12a8 8 0 1 0 8-8 8 8 0 0 0-6 2.7L4 9")
                path("M4 4v5h5M12 8v4l3 2")
            }

            AppIcon.TV -> {
                drawRoundRect(tint, Offset(3f, 5f), Size(18f, 13f), CornerRadius(2.5f), style = stroke)
                line(8f, 20f, 16f, 20f)
                line(12f, 18f, 12f, 20f)
            }

            AppIcon.DANMAKU -> {
                drawRoundRect(tint, Offset(3f, 5f), Size(18f, 13f), CornerRadius(2f), style = stroke)
                line(8f, 9f, 16f, 9f)
                line(9f, 12f, 15f, 12f)
                line(10f, 15f, 14f, 15f)
            }

            AppIcon.BULB -> {
                path("M9 18h6M10 21h4")
                path("M12 2a7 7 0 0 0-4.9 11.9c.6.6 1 1.5 1 2.4V17h7.8v-.7c0-.9.4-1.8 1-2.4A7 7 0 0 0 12 2z")
            }

            AppIcon.LOOP_ONE -> {
                path("M17 3l3 3-3 3")
                path("M4 11V9a3 3 0 0 1 3-3h13")
                path("M7 21l-3-3 3-3")
                path("M20 13v2a3 3 0 0 1-3 3H4")
                path("M11.4 10.3l1.1-.6v4.6")
            }

            AppIcon.WAVEFORM -> path("M4 12h3l2-6 4 12 2-6h5")

            AppIcon.SHUFFLE -> {
                path("M16 4h4v4")
                path("M4 20L20 4")
                path("M20 16v4h-4")
                path("M14.5 14.5L20 20")
                path("M4 4l5 5")
            }

            AppIcon.ROTATE_LEFT -> {
                path("M4 4v6h6")
                path("M4.6 10a8 8 0 1 1 .7 5.4")
            }

            AppIcon.IMAGE -> {
                drawRoundRect(tint, Offset(3f, 5f), Size(18f, 14f), CornerRadius(2f), style = stroke)
                circle(8.5f, 9.8f, 1.3f, filled = true)
                path("M4.5 17l5-5 4 4 2.5-2.5L20 17")
            }

            AppIcon.FOLDER_ALL -> {
                path("M3 7.5A1.5 1.5 0 0 1 4.5 6H9l2 2h8.5A1.5 1.5 0 0 1 21 9.5v8A1.5 1.5 0 0 1 19.5 19h-15A1.5 1.5 0 0 1 3 17.5z")
                line(7f, 12.5f, 17f, 12.5f)
                line(7f, 15.6f, 13.5f, 15.6f)
            }
        }
    }
}
