package io.github.bilibox.data.player

import android.content.Context
import android.content.SharedPreferences

/** 标记白点默认显示，横竖屏及后续打开的视频共用同一偏好。 */
internal class BookmarkDisplayPreferences(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("player_bookmark_display", Context.MODE_PRIVATE)
    )

    fun load(): Boolean = preferences.getBoolean("enabled", true)

    fun save(enabled: Boolean) {
        preferences.edit().putBoolean("enabled", enabled).apply()
    }
}
