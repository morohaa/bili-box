package io.github.bilibox.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** 设计稿里的回弹曲线 cubic-bezier(.34, 1.56, .64, 1)，用于搜索态展开/收起。 */
internal val BrandSpringEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/** 夜间模式正文色：比纯白柔和，避免在 #121314 上显得刺眼。 */
internal val DarkInkSoft = Color(0xFFC9CED6)

/** 动态页与底部玻璃导航共用的配色，色值取自参考稿。 */
internal data class DynamicColors(
    val background: Color,
    val card: Color,
    val chip: Color,
    val chipBorder: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val pink: Color,
    val pinkSoft: Color,
    val onPink: Color,
    val brandPink: Color,
    val roseGold: Color,
    val navIdle: Color,
    val navGlassTop: Color,
    val navGlassBottom: Color,
    val navEdge: Color,
    val navShadow: Color,
)

internal fun dynamicColors(darkMode: Boolean, accent: Color = OnlinePinkBrand): DynamicColors {
    val background = if (darkMode) Color(0xFF121314) else Color(0xFFF6F7F8)
    return if (darkMode) {
        DynamicColors(
            background = background,
            card = Color(0xFF1D1F21),
            chip = Color(0xFF26292E),
            chipBorder = Color(0xFF3B3F45),
            ink = DarkInkSoft,
            ink2 = Color(0xFFA2A7AE),
            ink3 = Color(0xFF6D7278),
            line = Color(0xFF2C2F33),
            pink = accent,
            pinkSoft = lerp(background, accent, 0.18f),
            onPink = Color.White,
            brandPink = accent,
            roseGold = Color(0xFFFFCBA4),
            navIdle = Color(0xFFA2A7AE),
            navGlassTop = Color(0xFF202225).copy(alpha = .52f),
            navGlassBottom = Color(0xFF1A1C1E).copy(alpha = .62f),
            navEdge = Color.White.copy(alpha = .08f),
            navShadow = Color.Black.copy(alpha = .5f),
        )
    } else {
        DynamicColors(
            background = background,
            card = Color(0xFFFFFFFF),
            chip = Color(0xFFFFFFFF),
            chipBorder = Color(0xFFE0E4E9),
            ink = Color(0xFF18191C),
            ink2 = Color(0xFF61666D),
            ink3 = Color(0xFF9499A0),
            line = Color(0xFFE3E5E7),
            pink = accent,
            pinkSoft = lerp(background, accent, 0.12f),
            onPink = Color.White,
            brandPink = accent,
            roseGold = Color(0xFFE5A87B),
            navIdle = Color(0xFF61666D),
            navGlassTop = Color.White.copy(alpha = .48f),
            navGlassBottom = Color(0xFFF7F8FA).copy(alpha = .34f),
            navEdge = Color.White.copy(alpha = .6f),
            navShadow = Color(0xFF18191C).copy(alpha = .16f),
        )
    }
}
