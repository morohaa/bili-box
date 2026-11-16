package io.github.bilibox.data.localvideo

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
internal data class LocalVideoBookmark(
    val id: String,
    val positionMs: Long,
    val note: String = "",
)

internal class LocalVideoBookmarkStore(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("local_video_bookmarks", Context.MODE_PRIVATE)
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun load(videoKey: String): List<LocalVideoBookmark> = runCatching {
        json.decodeFromString<List<LocalVideoBookmark>>(preferences.getString(videoKey, null) ?: "[]")
            .filter { it.id.isNotBlank() && it.positionMs >= 0L }
            .distinctBy { it.id }
            .sortedBy { it.positionMs }
    }.getOrDefault(emptyList())

    fun save(videoKey: String, bookmarks: List<LocalVideoBookmark>) {
        preferences.edit().apply {
            if (bookmarks.isEmpty()) remove(videoKey) else putString(videoKey, json.encodeToString(bookmarks))
        }.apply()
    }
}
