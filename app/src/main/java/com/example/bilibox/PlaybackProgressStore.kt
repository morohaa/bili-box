package com.example.bilibox

import android.content.Context
import android.content.SharedPreferences

internal sealed interface LocalVideoPlaybackState {
    data object Unplayed : LocalVideoPlaybackState
    data class Watching(val positionMs: Long, val fraction: Float) : LocalVideoPlaybackState
    data object Watched : LocalVideoPlaybackState
}

internal data class LocalVideoPlayback(
    val state: LocalVideoPlaybackState,
    val lastPlayedAtMs: Long = 0L,
)

internal class PlaybackProgressStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("local_playback_progress", Context.MODE_PRIVATE)
    )

    fun load(videoKey: String, durationMs: Long): Long =
        validPosition(preferences.getLong(videoKey, 0L), durationMs)

    fun save(videoKey: String, positionMs: Long, durationMs: Long, completed: Boolean) {
        val position = if (completed) 0L else validPosition(positionMs, durationMs)
        val played = completed || position > 0L
        preferences.edit().apply {
            if (position == 0L) remove(videoKey) else putLong(videoKey, position)
            if (completed) putBoolean(completedKey(videoKey), true)
            else if (position > 0L) remove(completedKey(videoKey))
            if (played) putLong(lastPlayedKey(videoKey), System.currentTimeMillis())
        }.apply()
    }

    /** Full display state for the local-video browser. */
    fun playback(videoKey: String, durationMs: Long): LocalVideoPlayback {
        val position = load(videoKey, durationMs)
        val completed = preferences.getBoolean(completedKey(videoKey), false)
        val lastPlayedAt = preferences.getLong(lastPlayedKey(videoKey), 0L)
        val state = when {
            position > 0L -> LocalVideoPlaybackState.Watching(
                positionMs = position,
                fraction = if (durationMs > 0L) (position.toFloat() / durationMs).coerceIn(0f, 1f) else 0f,
            )
            completed -> LocalVideoPlaybackState.Watched
            else -> LocalVideoPlaybackState.Unplayed
        }
        return LocalVideoPlayback(state, lastPlayedAt)
    }

    private fun completedKey(videoKey: String): String = "$videoKey#completed"

    private fun lastPlayedKey(videoKey: String): String = "$videoKey#played-at"

    private fun validPosition(positionMs: Long, durationMs: Long): Long = when {
        positionMs <= 0L -> 0L
        durationMs > 0L && positionMs >= durationMs -> 0L
        else -> positionMs
    }
}
