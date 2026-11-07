package io.github.bilibox.data.danmaku

import android.content.Context

/**
 * 可按类型屏蔽的弹幕种类，对应设置面板「画面防挡 · 按弹幕类型屏蔽」里的一排图标。
 *
 * [label] 用于面板上的文字，顺序即面板展示顺序。
 */
internal enum class DanmakuBlockType(val label: String) {
    TOP("顶部"),
    BOTTOM("底部"),
    SCROLL("滚动"),
    COLORFUL("彩色"),
    REPEAT("重复"),
}

/**
 * 弹幕显示偏好。默认值就是 B 站网页端的观感，用户调整后按设备保存。
 *
 * [areaFraction] 是弹幕可占用的画面高度比例，1f 表示整个画面；
 * [speedScale] 小于 1 时滚动更慢，大于 1 时更快；
 * [blockedTypes] 里出现的类型会在绘制前被过滤掉。
 */
internal data class DanmakuSettings(
    val enabled: Boolean = true,
    val opacity: Float = 1f,
    val fontScale: Float = 1f,
    val areaFraction: Float = 1f,
    val speedScale: Float = 1f,
    val blockedTypes: Set<DanmakuBlockType> = emptySet(),
) {
    fun sanitized(): DanmakuSettings = copy(
        opacity = opacity.coerceIn(MIN_OPACITY, 1f),
        fontScale = fontScale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE),
        areaFraction = areaFraction.coerceIn(MIN_AREA_FRACTION, 1f),
        speedScale = speedScale.coerceIn(MIN_SPEED_SCALE, MAX_SPEED_SCALE),
        blockedTypes = blockedTypes.toSet(),
    )

    fun isBlocked(type: DanmakuBlockType): Boolean = type in blockedTypes

    fun withBlocked(type: DanmakuBlockType, blocked: Boolean): DanmakuSettings = copy(
        blockedTypes = if (blocked) blockedTypes + type else blockedTypes - type,
    )

    companion object {
        const val MIN_OPACITY = .2f
        const val MIN_FONT_SCALE = .5f
        const val MAX_FONT_SCALE = 2f
        const val MIN_AREA_FRACTION = .05f
        const val MIN_SPEED_SCALE = .5f
        const val MAX_SPEED_SCALE = 1.8f
    }
}

internal class DanmakuPreferences(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): DanmakuSettings = DanmakuSettings(
        enabled = preferences.getBoolean(KEY_ENABLED, true),
        opacity = preferences.getFloat(KEY_OPACITY, 1f),
        fontScale = preferences.getFloat(KEY_FONT_SCALE, 1f),
        areaFraction = loadAreaFraction(),
        speedScale = preferences.getFloat(KEY_SPEED_SCALE, 1f),
        blockedTypes = loadBlockedTypes(),
    ).sanitized()

    fun save(settings: DanmakuSettings) {
        val safe = settings.sanitized()
        preferences.edit()
            .putBoolean(KEY_ENABLED, safe.enabled)
            .putFloat(KEY_OPACITY, safe.opacity)
            .putFloat(KEY_FONT_SCALE, safe.fontScale)
            .putFloat(KEY_AREA_FRACTION, safe.areaFraction)
            .putFloat(KEY_SPEED_SCALE, safe.speedScale)
            .putStringSet(KEY_BLOCKED_TYPES, safe.blockedTypes.map { it.name }.toSet())
            // 旧版本按三档枚举保存，迁移后不再写回，避免下次启动又读到旧值。
            .remove(LEGACY_KEY_AREA)
            .apply()
    }

    /**
     * 显示区域优先读连续比例；老版本只存过 `FULL / THREE_QUARTERS / HALF`
     * 三档枚举，这里做一次兜底映射，用户升级后不会突然变成全屏。
     */
    private fun loadAreaFraction(): Float {
        if (preferences.contains(KEY_AREA_FRACTION)) {
            return preferences.getFloat(KEY_AREA_FRACTION, 1f)
        }
        return when (preferences.getString(LEGACY_KEY_AREA, null)) {
            LEGACY_THREE_QUARTERS -> .75f
            LEGACY_HALF -> .5f
            else -> 1f
        }
    }

    private fun loadBlockedTypes(): Set<DanmakuBlockType> {
        val stored = preferences.getStringSet(KEY_BLOCKED_TYPES, null) ?: return emptySet()
        return stored.mapNotNullTo(mutableSetOf()) { name ->
            DanmakuBlockType.entries.firstOrNull { it.name == name }
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "danmaku_prefs"
        const val KEY_ENABLED = "enabled"
        const val KEY_OPACITY = "opacity"
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_AREA_FRACTION = "area_fraction"
        const val KEY_SPEED_SCALE = "speed_scale"
        const val KEY_BLOCKED_TYPES = "blocked_types"

        /** 1.0 之前的三档显示区域，只读不写。 */
        const val LEGACY_KEY_AREA = "area"
        const val LEGACY_THREE_QUARTERS = "THREE_QUARTERS"
        const val LEGACY_HALF = "HALF"
    }
}
