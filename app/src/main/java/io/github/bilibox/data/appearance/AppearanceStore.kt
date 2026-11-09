package io.github.bilibox.data.appearance

import android.content.Context
import android.content.SharedPreferences
import io.github.bilibox.core.ui.theme.AppearanceMode
import io.github.bilibox.core.ui.theme.AppearanceSettings
import io.github.bilibox.core.ui.theme.BackgroundPage
import io.github.bilibox.core.ui.theme.DefaultThemeId
import io.github.bilibox.core.ui.theme.HomeBackground
import io.github.bilibox.core.ui.theme.PageBackgroundSettings
import io.github.bilibox.core.ui.theme.ThemeCatalog
import io.github.bilibox.core.ui.theme.WallpaperBlurMax
import io.github.bilibox.core.ui.theme.WallpaperDarknessMax

/** 「主题外观」偏好；沿用旧版的 `appearance` SharedPreferences，并兼容 `dark_mode`。 */
internal class AppearanceStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(context.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE))

    fun load(): AppearanceSettings {
        val themeId = preferences.getString("theme_id", null)
            ?.takeIf { id -> ThemeCatalog.any { it.id == id } }
            ?: DefaultThemeId
        val mode = preferences.getString("appearance_mode", null)
            ?.let { raw -> AppearanceMode.entries.firstOrNull { it.name == raw } }
            ?: if (preferences.getBoolean("dark_mode", false)) AppearanceMode.DARK else AppearanceMode.LIGHT
        return AppearanceSettings(
            themeId = themeId,
            mode = mode,
        ).let { settings ->
            BackgroundPage.entries.fold(settings) { result, page ->
                result.withBackground(page, loadBackground(page))
            }
        }
    }

    private fun darknessKey(page: BackgroundPage): String = if (page == BackgroundPage.HOME) "background_darkness" else "${page.storageKey}_darkness"

    private fun blurKey(page: BackgroundPage): String = if (page == BackgroundPage.HOME) "background_blur" else "${page.storageKey}_blur"

    private fun loadBackground(page: BackgroundPage): PageBackgroundSettings = PageBackgroundSettings(
        // 旧版 HOME 样式和未知值统一退回渐变。
        style = preferences.getString(page.storageKey, null)
            ?.let { raw -> HomeBackground.entries.firstOrNull { it.name == raw } }
            ?: HomeBackground.GRADIENT,
        darkness = preferences.getInt(darknessKey(page), 0).coerceIn(0, WallpaperDarknessMax),
        blur = preferences.getInt(blurKey(page), 0).coerceIn(0, WallpaperBlurMax),
    )

    fun save(settings: AppearanceSettings) {
        val editor = preferences.edit()
            .putString("theme_id", settings.themeId)
            .putString("appearance_mode", settings.mode.name)
            .remove("dark_mode")
        BackgroundPage.entries.forEach { page ->
            val background = settings.backgroundFor(page)
            editor.putString(page.storageKey, background.style.name)
                .putInt(darknessKey(page), background.darkness.coerceIn(0, WallpaperDarknessMax))
                .putInt(blurKey(page), background.blur.coerceIn(0, WallpaperBlurMax))
        }
        editor.apply()
    }
}
