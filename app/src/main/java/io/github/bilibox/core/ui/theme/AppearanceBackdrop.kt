package io.github.bilibox.core.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 首页背景光晕：把「主题外观」里选择的首页背景画在页面底色之上。
 *
 * 纯色不追加任何内容；首页背景是两枚角落光斑，渐变则换成更大、更靠边的两枚。
 * 光晕使用当前强调色，透明度按明暗模式调整，避免浅色下发灰。
 */
internal fun Modifier.appearanceBackdrop(
    style: HomeBackground,
    accent: Color,
    darkMode: Boolean,
): Modifier = drawBehind {
    if (style == HomeBackground.SOLID) return@drawBehind
    val alpha = if (darkMode) 0.15f else 0.08f
    val tint = accent.copy(alpha = alpha)
    if (style == HomeBackground.GRADIENT) {
        drawRect(
            Brush.radialGradient(
                colors = listOf(tint.copy(alpha = alpha * 1.6f), Color.Transparent),
                center = Offset(size.width, 0f),
                radius = size.width * 0.85f,
            )
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(tint, Color.Transparent),
                center = Offset(0f, size.height),
                radius = size.width * 0.85f,
            )
        )
    } else {
        drawRect(
            Brush.radialGradient(
                colors = listOf(tint, Color.Transparent),
                center = Offset(size.width * 0.1f, size.height * 0.2f),
                radius = size.maxDimension * 0.6f,
            )
        )
        drawRect(
            Brush.radialGradient(
                colors = listOf(tint, Color.Transparent),
                center = Offset(size.width * 0.9f, size.height * 0.8f),
                radius = size.maxDimension * 0.6f,
            )
        )
    }
}

/** 供 `fillMaxSize()` 的页面根布局使用；单独保留以免调用处多 import。 */
internal fun Modifier.fillMaxSizeWithBackdrop(
    style: HomeBackground,
    accent: Color,
    darkMode: Boolean,
): Modifier = fillMaxSize().appearanceBackdrop(style, accent, darkMode)
