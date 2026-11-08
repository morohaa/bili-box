package io.github.bilibox.core.ui.theme

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale

/** 自定义背景图上的蒙层：保证首页文字在任何图片上都可读。 */
private const val CustomScrimLight = 0.30f
private const val CustomScrimDark = 0.44f

/** 自定义背景图与首页文字之间的固定可读性蒙层；用户可在壁纸编辑器里再叠一层暗化。 */
internal fun homeBackgroundScrim(darkMode: Boolean): Color = if (darkMode) Color.Black.copy(alpha = CustomScrimDark) else Color.White.copy(alpha = CustomScrimLight)

/**
 * 首页背景：把「主题外观」里选择的首页背景画在页面底色之上。
 *
 * 纯色不追加任何内容；渐变是角落光晕；自定义把用户上传的图片铺满整页，
 * 并叠上可读性蒙层与用户在壁纸编辑器里选定的暗化 / 模糊。
 * 光晕使用当前强调色，透明度按明暗模式调整，避免浅色下发灰。
 */
@Composable
internal fun AppearanceBackdrop(
    style: HomeBackground,
    accent: Color,
    darkMode: Boolean,
    image: ImageBitmap? = null,
    blur: Int = 0,
    darkness: Int = 0,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        when (style) {
            HomeBackground.SOLID -> Unit

            HomeBackground.CUSTOM -> {
                if (image != null) {
                    CustomBackgroundImage(image, blur)
                    Box(Modifier.fillMaxSize().background(homeBackgroundScrim(darkMode)))
                    val alpha = darkness.coerceIn(0, 100) / 100f
                    if (alpha > 0f) {
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = alpha)))
                    }
                } else {
                    // 图片还没选好或读取失败时退回渐变，页面不会突然变成纯色。
                    GradientGlowBackground(accent, darkMode)
                }
            }

            HomeBackground.GRADIENT -> GradientGlowBackground(accent, darkMode)
        }
    }
}

@Composable
private fun CustomBackgroundImage(image: ImageBitmap, blur: Int) {
    val radius = blur.coerceIn(0, WallpaperBlurMax)
    // 滑杆标的是 px，RenderEffect 也按像素计算；用 dp 在 3x 屏上会明显过糊。
    val blurPx = radius.toFloat()
    val canBlur = Build.VERSION.SDK_INT >= 31
    Image(
        bitmap = image,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().graphicsLayer {
            if (canBlur && blurPx > 0f) {
                renderEffect = BlurEffect(blurPx, blurPx, TileMode.Clamp)
                // 模糊会把边缘向外扩散，略微放大盖住透明边。
                val scale = 1f + radius / 200f
                scaleX = scale
                scaleY = scale
            }
        },
    )
}

@Composable
private fun GradientGlowBackground(accent: Color, darkMode: Boolean) {
    Box(Modifier.fillMaxSize().drawBehind { drawGradientGlow(accent, darkMode) })
}

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
