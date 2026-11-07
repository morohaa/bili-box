package io.github.bilibox.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

internal val Ink = Color(0xFF24313D)

/** 当前主题的强调色；「主题外观」切换后自动替换。 */
internal val Pink: Color
    @Composable @ReadOnlyComposable
    get() = LocalAccentColor.current
internal val Muted = Color(0xFF82909D)
internal val OnlineSurface = Color.White
internal val OnlineText = Color(0xFF18191C)
internal val OnlineMuted = Color(0xFF9499A0)
internal val OnlineSecondary = Color(0xFF61666D)

/** 固定的 B 站品牌粉，供不能读取组合状态的默认参数使用。 */
internal val OnlinePinkBrand = Color(0xFFFB7299)

/** 当前主题的强调色（B 站品牌粉位）；界面代码直接使用即可。 */
internal val OnlinePink: Color
    @Composable @ReadOnlyComposable
    get() = LocalAccentColor.current

/** 选中态浅底，跟随主题强调色。 */
internal val OnlinePinkSoft: Color
    @Composable @ReadOnlyComposable
    get() = LocalAccentColor.current.copy(alpha = .12f)
internal val OnlineCardGray = Color(0xFFF6F7F8)
internal val OnlineStroke = Color(0xFFE5E7EB)
internal val OnlineDivider = Color(0xFFF1F2F3)

/** 当前外观主题的强调色；由 `BiliBoxTheme` 注入，未设置时回落到品牌粉。 */
internal val LocalAccentColor = staticCompositionLocalOf { OnlinePinkBrand }
