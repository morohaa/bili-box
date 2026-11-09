package io.github.bilibox.data.player

import android.content.Context
import android.content.SharedPreferences

/** 进度条预览在本地 / 在线视频及横竖屏之间共用，并在下次播放时恢复。 */
internal class SeekPreviewPreferences(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("player_seek_preview", Context.MODE_PRIVATE)
    )

    fun load(): Boolean = preferences.getBoolean("enabled", true)

    fun save(enabled: Boolean) {
        preferences.edit().putBoolean("enabled", enabled).apply()
    }
}
