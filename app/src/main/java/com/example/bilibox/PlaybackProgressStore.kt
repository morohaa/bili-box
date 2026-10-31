package com.example.bilibox

import android.content.Context
import android.content.SharedPreferences

internal class PlaybackProgressStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("local_playback_progress", Context.MODE_PRIVATE)
    )

    fun load(videoKey: String, durationMs: Long): Long =
        validPosition(preferences.getLong(videoKey, 0L), durationMs)

    fun save(videoKey: String, positionMs: Long, durationMs: Long, completed: Boolean) {
        val position = if (completed) 0L else validPosition(positionMs, durationMs)
        preferences.edit().apply {
            if (position == 0L) remove(videoKey) else putLong(videoKey, position)
        }.apply()
    }

    private fun validPosition(positionMs: Long, durationMs: Long): Long = when {
        positionMs <= 0L -> 0L
        durationMs > 0L && positionMs >= durationMs -> 0L
        else -> positionMs
    }
}
