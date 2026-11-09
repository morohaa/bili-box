package io.github.bilibox.data.favorites

import io.github.bilibox.core.model.VideoSummary

internal data class FavoriteFolder(
    val id: Long,
    val title: String,
    val coverUrl: String,
    val count: Int,
    val isPublic: Boolean,
)

internal data class FavoriteFolderPage(val items: List<FavoriteFolder>, val hasMore: Boolean)

internal data class FavoriteVideo(
    val id: Long,
    val title: String,
    val coverUrl: String,
    val author: String,
    val duration: String,
    val savedAt: Long,
    val video: VideoSummary?,
    val unavailableReason: String? = null,
)

internal data class FavoriteVideoPage(val items: List<FavoriteVideo>, val hasMore: Boolean, val total: Int)

internal enum class FavoriteOrder(val parameter: String) {
    SAVED("mtime"),
    PUBLISHED("pubtime"),
    MOST_PLAYED("view"),
}

internal interface FavoritesSource {
    suspend fun accountId(): Long
    suspend fun folders(mid: Long, page: Int): FavoriteFolderPage
    suspend fun videos(folderId: Long, page: Int, keyword: String, order: FavoriteOrder): FavoriteVideoPage
}
