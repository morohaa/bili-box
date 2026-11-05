package io.github.bilibox.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import io.github.bilibox.core.ui.theme.Muted

// 播放量、UP 主与弹幕数共用同一套 1024 视图框图标；
// 外框与内部符号分开绘制，避免 even-odd 规则把框内符号挖空。
private const val ICON_VIEWPORT = 1024f

private const val SCREEN_FRAME_PATH =
    "M800 128H224C134.4 128 64 198.4 64 288v448c0 89.6 70.4 160 160 160h576c89.6 0 160-70.4 160-160V288c0-89.6-70.4-160-160-160z " +
        "m96 608c0 54.4-41.6 96-96 96H224c-54.4 0-96-41.6-96-96V288c0-54.4 41.6-96 96-96h576c54.4 0 96 41.6 96 96v448z"

private const val PLAY_GLYPH_PATH =
    "M684.8 483.2l-256-112c-22.4-9.6-44.8 6.4-44.8 28.8v224c0 22.4 22.4 38.4 44.8 28.8l256-112c25.6-9.6 25.6-48 0-57.6z"

private const val AUTHOR_GLYPH_PATH =
    "M419.2 544c0 51.2-3.2 108.8-83.2 108.8S252.8 595.2 252.8 544v-217.6H192v243.2c0 96 51.2 140.8 140.8 140.8 " +
        "89.6 0 147.2-48 147.2-144v-240h-60.8V544zM710.4 326.4h-156.8V704h60.8v-147.2h96c102.4 0 121.6-67.2 121.6-115.2 " +
        "0-44.8-19.2-115.2-121.6-115.2z m-3.2 179.2h-92.8V384h92.8c32 0 60.8 12.8 60.8 60.8 0 44.8-32 60.8-60.8 60.8z"

private const val DANMAKU_GLYPH_PATH =
    "M240 384h64v64h-64zM368 384h384v64h-384zM432 576h352v64h-352zM304 576h64v64h-64z"

private fun glyphPathData(kind: String): String? = when (kind) {
    "play" -> PLAY_GLYPH_PATH
    "author" -> AUTHOR_GLYPH_PATH
    "comment" -> DANMAKU_GLYPH_PATH
    else -> null
}

private fun parseIconPath(data: String): Path = PathParser().parsePathString(data).toPath().apply {
    fillType = PathFillType.EvenOdd
}

@Composable
internal fun VideoMetaIcon(kind: String, modifier: Modifier = Modifier, tint: Color = Muted) {
    val frame = remember { parseIconPath(SCREEN_FRAME_PATH) }
    val glyph = remember(kind) { glyphPathData(kind)?.let(::parseIconPath) }
    Canvas(modifier) {
        val symbol = glyph ?: return@Canvas
        val side = size.minDimension
        if (side <= 0f) return@Canvas
        val factor = side / ICON_VIEWPORT
        translate((size.width - side) / 2f, (size.height - side) / 2f) {
            scale(factor, pivot = Offset.Zero) {
                drawPath(frame, tint)
                drawPath(symbol, tint)
            }
        }
    }
}
