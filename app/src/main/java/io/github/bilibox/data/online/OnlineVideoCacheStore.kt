package io.github.bilibox.data.online

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import io.github.bilibox.data.bili.BiliClient
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Downloads an MP4-compatible stream and publishes it in the device's video library. */
internal class OnlineVideoCacheStore(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    suspend fun cache(
        client: BiliClient,
        bvid: String,
        cid: Long,
        qn: Int,
        title: String,
        cookie: String?,
    ) = withContext(Dispatchers.IO) {
        val playData = client.playUrlHtml5(bvid, cid, qn)
        val segments = playData.durl.sortedBy { it.order }
        if (segments.isEmpty()) throw IllegalStateException("没有可缓存的 MP4 视频流")

        val safeTitle = title.trim().map { char ->
            if (char.isLetterOrDigit() || char in " -_().") char else '_'
        }.joinToString("").trim().take(80).ifBlank { bvid }
        val displayName = "${safeTitle}_${bvid}_${System.currentTimeMillis()}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/BiliBox")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val directory = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "BiliBox",
                )
                if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建缓存目录")
                put(MediaStore.Video.Media.DATA, File(directory, displayName).absolutePath)
            }
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("无法创建缓存文件")
        try {
            val output = resolver.openOutputStream(uri, "w") ?: throw IOException("无法写入缓存文件")
            output.use { destination ->
                for (segment in segments) {
                    val connection = (URL(segment.url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10_000
                        readTimeout = 20_000
                        setRequestProperty(
                            "User-Agent",
                            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                        )
                        setRequestProperty("Referer", "https://www.bilibili.com/video/$bvid")
                        setRequestProperty("Origin", "https://www.bilibili.com")
                        setRequestProperty("Accept", "*/*")
                        cookie?.takeIf { it.isNotBlank() }?.let { setRequestProperty("Cookie", it) }
                    }
                    try {
                        val responseCode = connection.responseCode
                        if (responseCode !in 200..299) throw IOException("视频源返回 HTTP $responseCode")
                        connection.inputStream.use { it.copyTo(destination) }
                    } finally {
                        connection.disconnect()
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val published = resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                    null,
                    null,
                )
                if (published != 1) throw IOException("无法发布缓存视频")
            }
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }
}
