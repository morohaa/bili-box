package io.github.bilibox.data.online

import io.github.bilibox.data.bili.BiliQn
import kotlinx.serialization.Serializable

/**
 * Persistent metadata for BiliBox's own offline cache.
 *
 * The media files themselves live in MediaStore (videos under Movies/BiliBox,
 * standalone audio under Music/BiliBox). This index only remembers which
 * episode owns which track, so the cache sheet can restore its state after the
 * app restarts without scanning the whole media library.
 */
@Serializable
internal data class OnlineCacheIndex(
    val bvid: String = "",
    val episodes: List<OnlineCacheEpisode> = emptyList(),
)

@Serializable
internal data class OnlineCacheEpisode(
    val cid: Long = 0L,
    val page: Int = 1,
    val part: String = "",
    val video: OnlineCacheFile? = null,
    val audio: OnlineCacheFile? = null,
)

@Serializable
internal data class OnlineCacheFile(
    val uri: String = "",
    val displayName: String = "",
    val qualityId: Int = 0,
    val qualityLabel: String = "",
    val sizeBytes: Long = 0L,
    val cachedAt: Long = 0L,
)

/** A successfully published MediaStore item returned by the downloader. */
internal data class CachedMediaFile(
    val uri: String,
    val displayName: String,
    val qualityId: Int,
    val qualityLabel: String,
    val sizeBytes: Long,
)

/**
 * Download contract for the cache controller. Keeping this as an interface
 * makes the queue/state machine testable with a fake downloader; the production
 * implementation is [OnlineVideoCacheStore].
 */
internal interface OnlineCacheDownloader {
    suspend fun loadIndex(bvid: String): OnlineCacheIndex

    suspend fun saveIndex(index: OnlineCacheIndex)

    suspend fun cacheVideo(
        bvid: String,
        cid: Long,
        qn: Int,
        page: Int,
        part: String,
        videoTitle: String,
        onProgress: (Float) -> Unit,
    ): CachedMediaFile

    suspend fun cacheAudio(
        bvid: String,
        cid: Long,
        audioId: Int,
        page: Int,
        part: String,
        videoTitle: String,
        onProgress: (Float) -> Unit,
    ): CachedMediaFile
}

internal fun onlineCacheVideoQualityLabel(qn: Int): String = when (qn) {
    BiliQn.P1080 -> "1080P 超清"
    BiliQn.P720 -> "720P 高清"
    BiliQn.P480 -> "480P 标清"
    BiliQn.P360 -> "360P 流畅"
    74 -> "720P 60帧"
    116 -> "1080P 60帧"
    112 -> "1080P+ 超清"
    else -> "${qn}P"
}

internal fun onlineCacheAudioQualityLabel(audioId: Int): String = when (audioId) {
    30280 -> "192K 高品质"
    30232 -> "132K 标准"
    30216 -> "64K 流畅"
    30250 -> "杜比全景声"
    30251 -> "Hi-Res 无损"
    else -> "音频 $audioId"
}

/**
 * Builds the display name shown in the system video/music library. It stays
 * readable (title + part) instead of exposing the bvid or a timestamp; if two
 * files collide MediaStore appends its own suffix.
 */
internal fun onlineCacheDisplayName(
    title: String,
    page: Int,
    part: String,
    extension: String,
): String {
    val readable = listOf(title, "P$page", part)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(" ")
    val safe = readable.map { char ->
        if (char.isLetterOrDigit() || char in " -_().+") char else '_'
    }.joinToString("").trim().take(90).ifBlank { "BiliBox_P$page" }
    return "$safe.$extension"
}
