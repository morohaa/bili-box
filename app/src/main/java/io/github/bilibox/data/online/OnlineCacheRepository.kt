package io.github.bilibox.data.online

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import io.github.bilibox.data.localvideo.LocalVideo
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 下载器与离线页面共用索引锁；这里只读取本应用登记的缓存。 */
internal class OnlineCacheRepository(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val directory = File(appContext.filesDir, "online_cache")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    suspend fun loadIndex(bvid: String): OnlineCacheIndex = withContext(Dispatchers.IO) {
        mutex.withLock { readIndex(bvid) }
    }

    suspend fun saveIndex(index: OnlineCacheIndex) = withContext(Dispatchers.IO) {
        mutex.withLock { writeIndex(mergeCacheIndices(readIndex(index.bvid), index)) }
    }

    suspend fun loadLibrary(): OfflineCacheLibrary = withContext(Dispatchers.IO) {
        mutex.withLock {
            val files = directory.listFiles { file -> file.extension == "json" }.orEmpty()
            val groups = files.mapNotNull { file ->
                val index = readIndex(file.nameWithoutExtension)
                val title = index.offlineTitle()
                val entries = index.episodes.sortedBy { it.page }.flatMap { episode ->
                    listOfNotNull(
                        episode.video?.let { entry(index, episode, it, false) },
                        episode.audio?.let { entry(index, episode, it, true) },
                    )
                }
                if (entries.isEmpty()) null else OfflineCacheGroup(index.bvid, title, index.author, index.totalParts, entries)
            }.sortedByDescending { it.latestCacheMs }
            OfflineCacheLibrary(groups, StatFs(Environment.getExternalStorageDirectory().path).availableBytes)
        }
    }

    /** 返回未能删除的文件数。删除失败的轨道仍保留在索引中，可重试。 */
    suspend fun delete(entries: List<OfflineCacheEntry>): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            var failures = 0
            entries.groupBy { it.bvid }.forEach { (bvid, selected) ->
                val index = readIndex(bvid)
                val removed = selected.mapNotNull { entry ->
                    val deleted = runCatching {
                        resolver.delete(entry.media.uri, null, null) > 0 || !isMediaAlive(entry.id)
                    }.getOrDefault(false)
                    if (deleted) {
                        entry.id
                    } else {
                        failures++
                        null
                    }
                }.toSet()
                val episodes = index.episodes.mapNotNull { episode ->
                    val video = episode.video?.takeUnless { it.uri in removed }
                    val audio = episode.audio?.takeUnless { it.uri in removed }
                    if (video == null && audio == null) null else episode.copy(video = video, audio = audio)
                }
                writeIndex(index.copy(episodes = episodes))
            }
            failures
        }
    }

    private fun readIndex(bvid: String): OnlineCacheIndex {
        val file = indexFile(bvid)
        if (!file.isFile) return OnlineCacheIndex(bvid)
        val parsed = runCatching { json.decodeFromString<OnlineCacheIndex>(file.readText()) }
            .getOrNull() ?: return OnlineCacheIndex(bvid)
        val episodes = parsed.episodes.mapNotNull { episode ->
            val video = episode.video?.takeIf { isMediaAlive(it.uri) }
            val audio = episode.audio?.takeIf { isMediaAlive(it.uri) }
            if (video == null && audio == null) null else episode.copy(video = video, audio = audio)
        }
        val index = parsed.copy(bvid = bvid, episodes = episodes)
        if (episodes != parsed.episodes) writeIndex(index)
        return index
    }

    private fun entry(index: OnlineCacheIndex, episode: OnlineCacheEpisode, file: OnlineCacheFile, audio: Boolean): OfflineCacheEntry {
        var duration = episode.durationMs
        var size = file.sizeBytes
        var width = 0
        var height = 0
        resolver.query(Uri.parse(file.uri), null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                fun number(column: String): Long = cursor.getColumnIndex(column).takeIf { it >= 0 }?.let { cursor.getLong(it) } ?: 0L
                duration = number(MediaStore.MediaColumns.DURATION).takeIf { it > 0 } ?: duration
                size = number(MediaStore.MediaColumns.SIZE).takeIf { it > 0 } ?: size
                width = number(MediaStore.MediaColumns.WIDTH).toInt()
                height = number(MediaStore.MediaColumns.HEIGHT).toInt()
            }
        }
        val title = episode.part.ifBlank { index.offlineTitle() }
        return OfflineCacheEntry(
            bvid = index.bvid,
            cid = episode.cid,
            page = episode.page,
            title = title,
            audioOnly = audio,
            quality = file.qualityLabel,
            media = LocalVideo(
                uri = Uri.parse(file.uri),
                name = "$title.${if (audio) "m4a" else "mp4"}",
                durationMs = duration,
                sizeBytes = size,
                relativePath = if (audio) "Music/BiliBox" else "Movies/BiliBox",
                bucketName = "BiliBox",
                dateAddedMs = file.cachedAt,
                width = width,
                height = height,
                mimeType = if (audio) "audio/mp4" else "video/mp4",
            ),
        )
    }

    private fun isMediaAlive(uri: String): Boolean {
        if (uri.isBlank()) return false
        return resolver.query(Uri.parse(uri), arrayOf(MediaStore.MediaColumns._ID), null, null, null)
            ?.use { it.moveToFirst() } == true
    }

    private fun writeIndex(index: OnlineCacheIndex) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建缓存索引目录")
        val target = indexFile(index.bvid)
        val temp = File(directory, "${target.name}.tmp")
        temp.writeText(json.encodeToString(index))
        if (!temp.renameTo(target)) throw IOException("无法保存缓存索引")
    }

    private fun indexFile(bvid: String): File {
        val safe = bvid.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '_' }
            .joinToString("").ifBlank { "unknown" }
        return File(directory, "$safe.json")
    }

    private companion object {
        val mutex = Mutex()
    }
}
