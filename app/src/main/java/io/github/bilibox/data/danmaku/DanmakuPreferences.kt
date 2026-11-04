package io.github.bilibox.data.danmaku

import android.content.Context

/** 弹幕显示区域，对应设置面板里的「全屏 / 3/4 屏 / 半屏」。 */
internal enum class DanmakuArea(val label: String, val fraction: Float) {
    FULL("全屏", 1f),
    THREE_QUARTERS("3/4 屏", .75f),
    HALF("半屏", .5f),
}

/**
 * 弹幕显示偏好。默认值就是 B 站网页端的观感，用户调整后按设备保存。
 * [speedScale] 小于 1 时滚动更慢，大于 1 时更快。
 */
internal data class DanmakuSettings(
    val enabled: Boolean = true,
    val opacity: Float = 1f,
    val fontScale: Float = 1f,
    val area: DanmakuArea = DanmakuArea.FULL,
    val speedScale: Float = 1f,
) {
    fun sanitized(): DanmakuSettings = copy(
        opacity = opacity.coerceIn(.2f, 1f),
        fontScale = fontScale.coerceIn(.7f, 1.5f),
        speedScale = speedScale.coerceIn(.5f, 1.8f),
    )
}

internal class DanmakuPreferences(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): DanmakuSettings {
        val area = preferences.getString(KEY_AREA, null)
            ?.let { name -> DanmakuArea.entries.firstOrNull { it.name == name } }
            ?: DanmakuArea.FULL
        return DanmakuSettings(
            enabled = preferences.getBoolean(KEY_ENABLED, true),
            opacity = preferences.getFloat(KEY_OPACITY, 1f),
            fontScale = preferences.getFloat(KEY_FONT_SCALE, 1f),
            area = area,
            speedScale = preferences.getFloat(KEY_SPEED_SCALE, 1f),
        ).sanitized()
    }

    fun save(settings: DanmakuSettings) {
        val safe = settings.sanitized()
        preferences.edit()
            .putBoolean(KEY_ENABLED, safe.enabled)
            .putFloat(KEY_OPACITY, safe.opacity)
            .putFloat(KEY_FONT_SCALE, safe.fontScale)
            .putString(KEY_AREA, safe.area.name)
            .putFloat(KEY_SPEED_SCALE, safe.speedScale)
            .apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "danmaku_prefs"
        const val KEY_ENABLED = "enabled"
        const val KEY_OPACITY = "opacity"
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_AREA = "area"
        const val KEY_SPEED_SCALE = "speed_scale"
    }
}
