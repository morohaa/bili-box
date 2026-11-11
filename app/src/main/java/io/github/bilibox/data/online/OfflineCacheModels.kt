package io.github.bilibox.data.online

import io.github.bilibox.data.localvideo.LocalVideo

internal data class OfflineCacheEntry(
    val bvid: String,
    val cid: Long,
    val page: Int,
    val title: String,
    val audioOnly: Boolean,
    val quality: String,
    val media: LocalVideo,
) {
    val id: String get() = media.uri.toString()
}

internal data class OfflineCacheGroup(
    val bvid: String,
    val title: String,
    val author: String,
    val totalParts: Int,
    val entries: List<OfflineCacheEntry>,
) {
    val sizeBytes: Long get() = entries.sumOf { it.media.sizeBytes }
    val latestCacheMs: Long get() = entries.maxOfOrNull { it.media.dateAddedMs } ?: 0L
    val isCollection: Boolean get() = totalParts > 1 || entries.size > 1 || entries.any { it.page > 1 }
}

internal data class OfflineCacheLibrary(
    val groups: List<OfflineCacheGroup> = emptyList(),
    val freeBytes: Long = 0L,
) {
    val sizeBytes: Long get() = groups.sumOf { it.sizeBytes }
}

/** 旧索引没有标题，用下载文件的 P 标记前缀回填，列表始终可离线读取。 */
internal fun OnlineCacheIndex.offlineTitle(): String = title.ifBlank {
    val episode = episodes.firstOrNull()
    val name = (episode?.video ?: episode?.audio)?.displayName.orEmpty().substringBeforeLast('.')
    val marker = " P${episode?.page ?: 1}"
    name.substringBefore(marker).removeSuffix(" 音频").ifBlank { bvid }
}

/** 保存快照可能来自并行完成的下载，按轨道时间合并，避免后写的旧快照丢掉另一分 P。 */
internal fun mergeCacheIndices(old: OnlineCacheIndex, incoming: OnlineCacheIndex): OnlineCacheIndex {
    val episodes = (old.episodes + incoming.episodes).groupBy { it.cid }.map { (_, versions) ->
        val latest = versions.last()
        latest.copy(
            video = versions.mapNotNull { it.video }.maxByOrNull { it.cachedAt },
            audio = versions.mapNotNull { it.audio }.maxByOrNull { it.cachedAt },
            durationMs = versions.maxOf { it.durationMs },
        )
    }.sortedBy { it.page }
    return incoming.copy(
        title = incoming.title.ifBlank { old.title },
        author = incoming.author.ifBlank { old.author },
        coverUrl = incoming.coverUrl.ifBlank { old.coverUrl },
        totalParts = maxOf(old.totalParts, incoming.totalParts),
        episodes = episodes,
    )
}
