package io.github.bilibox.feature.danmaku

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser

/**
 * 弹幕开关与设置图标，基于 48 视图框的「弹幕屏 + 对勾 / 禁行 / 齿轮」素材。
 * 角标会从图标中真正挖空，因此半透明胶囊和视频画面上都不会出现底色补丁。
 */
private const val DANMAKU_ICON_VIEWPORT = 48f

private const val ANTENNA_PATH = "m17 10.5-3.2-4M27.7 10.5l3.2-4"
private const val SCREEN_ON_PATH = "M25.6 37H14a7 7 0 0 1-7-7V18a7 7 0 0 1 7-7h17a7 7 0 0 1 7 7v8"
private const val SCREEN_OFF_PATH = "M23.9 37H14a7 7 0 0 1-7-7V18a7 7 0 0 1 7-7h17a7 7 0 0 1 7 7v6.1"

private const val GLYPH_PATH =
    "M13.2 16.8h5.5v5.4h-5l-.6 4.7h5.6l-.6 5.6c-.1.8-.6 1.2-1.4 1.2h-2.2M24 15.7l1.3 2M31.2 15.7l-1.3 2" +
        "M23.2 20h9v8.4h-9zM23.4 24.2H32M27.7 20v14M21.9 31.5h11.6"

private const val CHECK_PATH = "m27.5 35.8 5.1 5.1 10.6-11.5"
private const val OFF_SLASH_PATH = "m30.4 29.4 9.2 9.2"

private const val GEAR_PATH =
    "M33.7 25.8h3.4l.6 2.2 1.6.9 2.2-.6 1.7 2.9-1.6 1.6v1.9l1.6 1.6-1.7 2.9-2.2-.6-1.6.9-.6 2.2h-3.4" +
        "l-.6-2.2-1.6-.9-2.2.6-1.7-2.9 1.6-1.6v-1.9l-1.6-1.6 1.7-2.9 2.2.6 1.6-.9.6-2.2Zm1.7 5a3.05 3.05 0 1 0 0 6.1 3.05 3.05 0 0 0 0-6.1Z"

private fun parseIconPath(data: String, fillRule: PathFillType = PathFillType.NonZero): Path = PathParser().parsePathString(data).toPath().apply {
    fillType = fillRule
}

@Composable
internal fun DanmakuStateIcon(
    enabled: Boolean,
    tint: Color,
    checkColor: Color,
    modifier: Modifier = Modifier,
) {
    val check = remember { parseIconPath(CHECK_PATH) }
    val offSlash = remember { parseIconPath(OFF_SLASH_PATH) }
    DanmakuIcon(
        screenData = if (enabled) SCREEN_ON_PATH else SCREEN_OFF_PATH,
        tint = tint,
        modifier = modifier,
        cutoutBadge = !enabled,
    ) {
        if (enabled) {
            drawPath(check, checkColor, style = Stroke(3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        } else {
            drawCircle(tint, radius = 6.7f, center = Offset(35f, 34f), style = Stroke(2.8f))
            drawPath(offSlash, tint, style = Stroke(2.8f, cap = StrokeCap.Round))
        }
    }
}

@Composable
internal fun DanmakuSettingsIcon(
    tint: Color,
    gearColor: Color,
    modifier: Modifier = Modifier,
) {
    val gear = remember { parseIconPath(GEAR_PATH, PathFillType.EvenOdd) }
    DanmakuIcon(
        screenData = SCREEN_OFF_PATH,
        tint = tint,
        modifier = modifier,
        cutoutBadge = true,
    ) {
        drawPath(gear, gearColor)
    }
}

@Composable
private fun DanmakuIcon(
    screenData: String,
    tint: Color,
    modifier: Modifier,
    cutoutBadge: Boolean,
    badge: DrawScope.() -> Unit,
) {
    val antenna = remember { parseIconPath(ANTENNA_PATH) }
    val screen = remember(screenData) { parseIconPath(screenData) }
    val glyph = remember { parseIconPath(GLYPH_PATH) }
    Canvas(modifier) {
        val side = size.minDimension
        if (side <= 0f) return@Canvas
        val factor = side / DANMAKU_ICON_VIEWPORT
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), Paint())
            translate((size.width - side) / 2f, (size.height - side) / 2f) {
                scale(factor, pivot = Offset.Zero) {
                    val outline = Stroke(3.3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    val detail = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    drawPath(antenna, tint, style = outline)
                    drawPath(screen, tint, style = outline)
                    translate(0f, -1.2f) {
                        drawPath(glyph, tint, style = detail)
                    }
                    if (cutoutBadge) {
                        drawCircle(Color.Transparent, radius = 9f, center = Offset(35f, 34f), blendMode = BlendMode.Clear)
                    }
                    badge()
                }
            }
            canvas.restore()
        }
    }
}
