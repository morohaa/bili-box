package io.github.bilibox.data.player

import android.content.Context

/** 记住上次选择的关灯模式，下次进入视频时继续沿用。 */
internal class LightsOffPreferences(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): LightsOffMode {
        val name = preferences.getString(KEY_MODE, null) ?: return LightsOffMode.BLACK
        return LightsOffMode.entries.firstOrNull { it.name == name } ?: LightsOffMode.BLACK
    }

    fun save(mode: LightsOffMode) {
        preferences.edit().putString(KEY_MODE, mode.name).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "player_lights_off"
        const val KEY_MODE = "mode"
    }
}
