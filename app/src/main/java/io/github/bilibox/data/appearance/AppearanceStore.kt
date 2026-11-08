package io.github.bilibox.data.appearance

import android.content.Context
import io.github.bilibox.core.ui.theme.AppearanceMode
import io.github.bilibox.core.ui.theme.AppearanceSettings
import io.github.bilibox.core.ui.theme.DefaultThemeId
import io.github.bilibox.core.ui.theme.HomeBackground
import io.github.bilibox.core.ui.theme.ThemeCatalog
import io.github.bilibox.core.ui.theme.WallpaperBlurMax
import io.github.bilibox.core.ui.theme.WallpaperDarknessMax

/** 「主题外观」偏好；沿用旧版的 `appearance` SharedPreferences，并兼容 `dark_mode`。 */
internal class AppearanceStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("appearance", Context.MODE_PRIVATE)

    fun load(): AppearanceSettings {
        val themeId = preferences.getString("theme_id", null)
            ?.takeIf { id -> ThemeCatalog.any { it.id == id } }
            ?: DefaultThemeId
        val mode = preferences.getString("appearance_mode", null)
            ?.let { raw -> AppearanceMode.entries.firstOrNull { it.name == raw } }
            ?: if (preferences.getBoolean("dark_mode", false)) AppearanceMode.DARK else AppearanceMode.LIGHT
        // 旧版存过 HOME（纯光晕），现在由 GRADIENT 承担，读不出来时一并退回渐变。
        val background = preferences.getString("home_background", null)
            ?.let { raw -> HomeBackground.entries.firstOrNull { it.name == raw } }
            ?: HomeBackground.GRADIENT
        // 旧版本没有这两项：读不到时按 0 处理，保持原来的纯图片效果。
        val darkness = preferences.getInt("background_darkness", 0)
            .coerceIn(0, WallpaperDarknessMax)
        val blur = preferences.getInt("background_blur", 0)
            .coerceIn(0, WallpaperBlurMax)
        return AppearanceSettings(
            themeId = themeId,
            mode = mode,
            background = background,
            backgroundDarkness = darkness,
            backgroundBlur = blur,
        )
    }

    fun save(settings: AppearanceSettings) {
        preferences.edit()
            .putString("theme_id", settings.themeId)
            .putString("appearance_mode", settings.mode.name)
            .putString("home_background", settings.background.name)
            .putInt("background_darkness", settings.backgroundDarkness.coerceIn(0, WallpaperDarknessMax))
            .putInt("background_blur", settings.backgroundBlur.coerceIn(0, WallpaperBlurMax))
            .remove("dark_mode")
            .apply()
    }
}
