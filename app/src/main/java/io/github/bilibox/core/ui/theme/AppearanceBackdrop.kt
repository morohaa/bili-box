package io.github.bilibox.core.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.roundToInt

/** 自定义背景图上的蒙层：保证首页文字在任何图片上都可读。 */
private const val CustomScrimLight = 0.30f
private const val CustomScrimDark = 0.44f

/**
 * 首页背景：把「主题外观」里选择的首页背景画在页面底色之上。
 *
 * 纯色不追加任何内容；渐变是角落光晕；自定义把用户上传的图片铺满整页，
 * 并叠一层与明暗模式匹配的蒙层，避免文字压在花哨图片上看不清。
 * 光晕使用当前强调色，透明度按明暗模式调整，避免浅色下发灰。
 */
internal fun Modifier.appearanceBackdrop(
    style: HomeBackground,
    accent: Color,
    darkMode: Boolean,
    image: ImageBitmap? = null,
): Modifier = drawBehind {
    when (style) {
        HomeBackground.SOLID -> Unit

        HomeBackground.CUSTOM -> {
            if (image != null) {
                drawCoverImage(image)
                drawRect(
                    if (darkMode) {
                        Color.Black.copy(alpha = CustomScrimDark)
                    } else {
                        Color.White.copy(alpha = CustomScrimLight)
                    }
                )
            } else {
                // 图片还没选好或读取失败时退回渐变，页面不会突然变成纯色。
                drawGradientGlow(accent, darkMode)
            }
        }

        HomeBackground.GRADIENT -> drawGradientGlow(accent, darkMode)
    }
}

/** 供 `fillMaxSize()` 的页面根布局使用；单独保留以免调用处多 import。 */
internal fun Modifier.fillMaxSizeWithBackdrop(
    style: HomeBackground,
    accent: Color,
    darkMode: Boolean,
    image: ImageBitmap? = null,
): Modifier = fillMaxSize().appearanceBackdrop(style, accent, darkMode, image)

private fun DrawScope.drawGradientGlow(accent: Color, darkMode: Boolean) {
    val alpha = if (darkMode) 0.15f else 0.08f
    val tint = accent.copy(alpha = alpha)
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
}

/** 按 `ContentScale.Crop` 的规则铺满整页：等比放大到覆盖，再居中裁掉溢出部分。 */
private fun DrawScope.drawCoverImage(image: ImageBitmap) {
    if (image.width <= 0 || image.height <= 0) return
    val scale = max(size.width / image.width, size.height / image.height)
    val dstWidth = image.width * scale
    val dstHeight = image.height * scale
    drawImage(
        image = image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(
            ((size.width - dstWidth) / 2f).roundToInt(),
            ((size.height - dstHeight) / 2f).roundToInt(),
        ),
        dstSize = IntSize(dstWidth.roundToInt(), dstHeight.roundToInt()),
        filterQuality = FilterQuality.Medium,
    )
}
