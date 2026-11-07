package io.github.bilibox.data.appearance

import android.content.Context
import io.github.bilibox.core.ui.theme.AppearanceMode
import io.github.bilibox.core.ui.theme.AppearanceSettings
import io.github.bilibox.core.ui.theme.DefaultThemeId
import io.github.bilibox.core.ui.theme.HomeBackground
import io.github.bilibox.core.ui.theme.ThemeCatalog

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
        val background = preferences.getString("home_background", null)
            ?.let { raw -> HomeBackground.entries.firstOrNull { it.name == raw } }
            ?: HomeBackground.HOME
        return AppearanceSettings(themeId = themeId, mode = mode, background = background)
    }

    fun save(settings: AppearanceSettings) {
        preferences.edit()
            .putString("theme_id", settings.themeId)
            .putString("appearance_mode", settings.mode.name)
            .putString("home_background", settings.background.name)
            .remove("dark_mode")
            .apply()
    }
}
