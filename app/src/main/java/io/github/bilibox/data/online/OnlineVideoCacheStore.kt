package io.github.bilibox.data.online

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import io.github.bilibox.data.bili.BiliCdn
import io.github.bilibox.data.bili.BiliClient
import io.github.bilibox.data.bili.BiliQn
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Downloads video and audio tracks for the cache sheet.
 *
 * Video mode uses Bilibili's HTML5 single-file MP4 (the same stream the old
 * one-tap cache used) so the result is immediately playable from the system
 * video library. Audio mode downloads a DASH audio track to Music/BiliBox.
 * Progress is reported continuously and cancellation deletes the pending
 * MediaStore row.
 */
internal class OnlineVideoCacheStore(
    context: Context,
    private val client: BiliClient,
    private val cookie: String?,
) : OnlineCacheDownloader {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val indices = OnlineCacheRepository(appContext)

    override suspend fun loadIndex(bvid: String): OnlineCacheIndex = indices.loadIndex(bvid)

    override suspend fun saveIndex(index: OnlineCacheIndex) = indices.saveIndex(index)

    override suspend fun cacheVideo(
        bvid: String,
        cid: Long,
        qn: Int,
        page: Int,
        part: String,
        videoTitle: String,
        onProgress: (Float) -> Unit,
    ): CachedMediaFile = withContext(Dispatchers.IO) {
        val playData = client.playUrlHtml5(bvid, cid, qn)
        val segments = playData.durl.sortedBy { it.order }
        if (segments.isEmpty()) throw IllegalStateException("没有可缓存的 MP4 视频流")

        val displayName = onlineCacheDisplayName(videoTitle, page, part, "mp4")
        val totalHint = segments.sumOf { it.size.coerceAtLeast(0L) }.takeIf { it > 0L } ?: -1L
        val uri = insertVideo(displayName)
        try {
            var copied = 0L
            resolver.openOutputStream(uri, "w")?.use { output ->
                if (totalHint > 0L) onProgress(0f) else onProgress(-1f)
                segments.forEach { segment ->
                    val urls = BiliCdn.prioritize(listOf(segment.url) + segment.backupUrl.orEmpty())
                    copied += copyTo(
                        output = output,
                        urls = urls,
                        bvid = bvid,
                        copiedBefore = copied,
                        totalHint = totalHint,
                        useContentLength = segments.size == 1,
                        onProgress = onProgress,
                    )
                }
            } ?: throw IOException("无法写入缓存文件")
            if (totalHint > 0L) onProgress(1f)
            publish(uri)

            val actualQn = playData.quality.takeIf { it > 0 } ?: qn
            CachedMediaFile(
                uri = uri.toString(),
                displayName = displayName,
                qualityId = actualQn,
                qualityLabel = onlineCacheVideoQualityLabel(actualQn),
                sizeBytes = copied,
            )
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    override suspend fun cacheAudio(
        bvid: String,
        cid: Long,
        audioId: Int,
        page: Int,
        part: String,
        videoTitle: String,
        onProgress: (Float) -> Unit,
    ): CachedMediaFile = withContext(Dispatchers.IO) {
        val playData = client.playUrl(
            bvid = bvid,
            cid = cid,
            qn = BiliQn.P1080,
            fnval = BiliClient.FNVAL_DASH_ALL,
        )
        val dash = playData.dash ?: throw IllegalStateException("该视频没有独立音频流")
        val candidates = dash.audio.filter { it.url.isNotEmpty() }
        if (candidates.isEmpty()) throw IllegalStateException("该视频没有可缓存的音频流")

        // The requested id may be missing for some videos. Use the nearest
        // available bitrate rather than failing the whole task.
        val audio = candidates.firstOrNull { it.id == audioId }
            ?: candidates.minByOrNull { kotlin.math.abs(it.id - audioId) }
            ?: throw IllegalStateException("该视频没有可缓存的音频流")

        val displayName = onlineCacheDisplayName("$videoTitle 音频", page, part, "m4a")
        val totalHint = if (dash.duration > 0 && audio.bandwidth > 0L) {
            dash.duration.toLong() * audio.bandwidth / 8L
        } else {
            -1L
        }
        val uri = insertAudio(displayName)
        try {
            var copied = 0L
            resolver.openOutputStream(uri, "w")?.use { output ->
                if (totalHint > 0L) onProgress(0f) else onProgress(-1f)
                copied = copyTo(
                    output = output,
                    urls = BiliCdn.prioritize(listOf(audio.url) + audio.backups),
                    bvid = bvid,
                    copiedBefore = 0L,
                    totalHint = totalHint,
                    useContentLength = true,
                    onProgress = onProgress,
                )
            } ?: throw IOException("无法写入缓存文件")
            onProgress(1f)
            publish(uri)

            CachedMediaFile(
                uri = uri.toString(),
                displayName = displayName,
                qualityId = audio.id,
                qualityLabel = onlineCacheAudioQualityLabel(audio.id),
                sizeBytes = copied,
            )
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    private suspend fun copyTo(
        output: OutputStream,
        urls: List<String>,
        bvid: String,
        copiedBefore: Long,
        totalHint: Long,
        useContentLength: Boolean,
        onProgress: (Float) -> Unit,
    ): Long {
        val connection = openFirstAvailable(urls, bvid)
        val coroutineContext = currentCoroutineContext()
        try {
            val contentLength = connection.contentLengthLong.takeIf { it > 0L }
            val total = when {
                totalHint > 0L -> totalHint
                useContentLength && contentLength != null -> contentLength + copiedBefore
                else -> -1L
            }

            var read = 0L
            var lastReportAt = 0L
            connection.inputStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    // copyTo is not cancellation-cooperative by itself; checking
                    // each buffer lets "cancel download" stop promptly and let
                    // the caller delete the pending MediaStore row.
                    coroutineContext.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    read += count

                    val now = SystemClock.elapsedRealtime()
                    if (total > 0L && (now - lastReportAt >= PROGRESS_INTERVAL_MS || copiedBefore + read >= total)) {
                        onProgress(((copiedBefore + read).toFloat() / total).coerceIn(0f, 1f))
                        lastReportAt = now
                    }
                }
            }
            if (total > 0L) {
                onProgress(((copiedBefore + read).toFloat() / total).coerceIn(0f, 1f))
            } else {
                onProgress(-1f)
            }
            return read
        } finally {
            connection.disconnect()
        }
    }

    private fun openFirstAvailable(urls: List<String>, bvid: String): HttpURLConnection {
        var lastError: Exception? = null
        for (url in urls.distinct()) {
            if (url.isBlank()) continue
            val connection = try {
                URL(url).openConnection() as? HttpURLConnection
            } catch (error: Exception) {
                lastError = error
                null
            } ?: continue

            try {
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.instanceFollowRedirects = true
                connection.useCaches = false
                connection.setRequestProperty("User-Agent", MEDIA_UA)
                connection.setRequestProperty("Referer", "https://www.bilibili.com/video/$bvid")
                connection.setRequestProperty("Origin", "https://www.bilibili.com")
                connection.setRequestProperty("Accept", "*/*")
                cookie?.takeIf { it.isNotBlank() }?.let { connection.setRequestProperty("Cookie", it) }
                val code = connection.responseCode
                if (code in 200..299) return connection
                lastError = IOException("媒体源返回 HTTP $code")
            } catch (error: Exception) {
                lastError = error
            }
            runCatching { connection.disconnect() }
        }
        throw lastError ?: IOException("没有可用的缓存地址")
    }

    private fun insertVideo(displayName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "$VIDEO_RELATIVE_DIR/")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val directory = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    VIDEO_RELATIVE_DIR.substringAfterLast('/'),
                )
                if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建缓存目录")
                @Suppress("DEPRECATION")
                put(MediaStore.Video.Media.DATA, File(directory, displayName).absolutePath)
            }
        }
        return resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("无法创建视频缓存文件")
    }

    private fun insertAudio(displayName: String): Uri {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "$AUDIO_RELATIVE_DIR/")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val directory = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    AUDIO_RELATIVE_DIR.substringAfterLast('/'),
                )
                if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建缓存目录")
                @Suppress("DEPRECATION")
                put(MediaStore.Audio.Media.DATA, File(directory, displayName).absolutePath)
            }
        }
        return resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("无法创建音频缓存文件")
    }

    private fun publish(uri: Uri) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val published = resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null,
        )
        if (published != 1) throw IOException("无法发布缓存文件")
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 80L
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 20_000
        const val VIDEO_RELATIVE_DIR = "Movies/BiliBox"
        const val AUDIO_RELATIVE_DIR = "Music/BiliBox"
        const val MEDIA_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
