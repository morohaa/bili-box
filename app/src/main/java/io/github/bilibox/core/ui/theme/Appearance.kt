package io.github.bilibox.core.ui.theme

import androidx.compose.ui.graphics.Color

/** 外观模式。跟随系统时由调用方用 [resolveDark] 解析成实际明暗。 */
internal enum class AppearanceMode(val label: String) {
    LIGHT("浅色"),
    DARK("深色"),
    AUTO("自动"),
}

/**
 * 首页背景样式，「主题外观」页的底部弹层直接映射到这个枚举，顺序即展示顺序。
 *
 * [CUSTOM] 使用用户自己上传的图片，图片缺失时退回 [GRADIENT] 的渐变光晕。
 */
internal enum class HomeBackground(val label: String) {
    GRADIENT("渐变"),
    SOLID("纯色"),
    CUSTOM("自定义"),
}

/**
 * 一套可选的外观主题。
 *
 * [base] 是强调色，用于按钮、选中态与首页背景光晕；[start] / [end] 只服务于
 * 主题圆形预览，保留设计稿里的渐变层次。
 */
internal data class ThemeOption(
    val id: String,
    val label: String,
    val name: String,
    val base: Color,
    val start: Color,
    val end: Color,
    val custom: Boolean = false,
)

internal const val DefaultThemeId = "pink"

/** 12 套预设主题，顺序与设计稿的 4 列 3 行网格一致。 */
internal val ThemeCatalog: List<ThemeOption> = listOf(
    ThemeOption("rose", "玫瑰红", "玫瑰红 · 热烈", Color(0xFFF43F5E), Color(0xFFFB7185), Color(0xFFFDA4AF)),
    ThemeOption("pink", "粉电视", "粉电视 · 经典", Color(0xFFFB7299), Color(0xFFFB7299), Color(0xFFFFA7C0)),
    ThemeOption("sakura", "樱花粉", "樱花粉 · 浪漫", Color(0xFFFF85A2), Color(0xFFFF9CB6), Color(0xFFFFD1DC)),
    ThemeOption("orange", "落日橙", "落日橙 · 暖阳", Color(0xFFFF8A3D), Color(0xFFFF8A3D), Color(0xFFFFC46B)),
    ThemeOption("deep", "深海蓝", "深海蓝 · 深邃", Color(0xFF1E40AF), Color(0xFF2563EB), Color(0xFF60A5FA)),
    ThemeOption("blue", "星空蓝", "星空蓝 · 静谧", Color(0xFF0284C7), Color(0xFF0EA5E9), Color(0xFF7DD3FC)),
    ThemeOption("sky", "青空蓝", "青空蓝 · 旷野", Color(0xFF38BDF8), Color(0xFF7DD3FC), Color(0xFFBAE6FD)),
    ThemeOption("aurora", "极光紫", "极光紫 · 梦幻", Color(0xFFA78BFA), Color(0xFFC4B5FD), Color(0xFFEDE9FE)),
    ThemeOption("green", "抹茶绿", "抹茶绿 · 清新", Color(0xFF4FBF6A), Color(0xFF4FBF6A), Color(0xFFA6E36B)),
    ThemeOption("mint", "薄荷绿", "薄荷绿 · 清凉", Color(0xFF2DD4BF), Color(0xFF5EEAD4), Color(0xFF99F6E4)),
    ThemeOption("night", "暗夜灰", "暗夜灰 · 极简", Color(0xFF6D71A6), Color(0xFF2B2D42), Color(0xFF4A4E69)),
    ThemeOption("custom", "自定义", "自定义 · 专属", Color(0xFFFB7299), Color(0xFFFB7299), Color(0xFFFFA7C0), custom = true),
)

/** 主题偏好：主题色、明暗模式与首页背景。 */
internal data class AppearanceSettings(
    val themeId: String = DefaultThemeId,
    val mode: AppearanceMode = AppearanceMode.LIGHT,
    val background: HomeBackground = HomeBackground.GRADIENT,
) {
    val theme: ThemeOption get() = themeById(themeId)
}

internal fun themeById(id: String): ThemeOption = ThemeCatalog.firstOrNull { it.id == id } ?: ThemeCatalog.first { it.id == DefaultThemeId }

internal fun AppearanceMode.resolveDark(systemDark: Boolean): Boolean = when (this) {
    AppearanceMode.LIGHT -> false
    AppearanceMode.DARK -> true
    AppearanceMode.AUTO -> systemDark
}
