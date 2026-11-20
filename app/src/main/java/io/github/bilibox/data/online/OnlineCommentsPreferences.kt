package io.github.bilibox.data.online

import android.content.Context
import android.content.SharedPreferences

internal class OnlineCommentsPreferences(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("online_comments", Context.MODE_PRIVATE)
    )

    fun load(): Boolean = preferences.getBoolean("enabled", true)

    fun save(enabled: Boolean) {
        preferences.edit().putBoolean("enabled", enabled).apply()
    }
}
