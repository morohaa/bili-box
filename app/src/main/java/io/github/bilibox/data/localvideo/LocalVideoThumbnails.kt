package io.github.bilibox.data.localvideo

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size

private val thumbnailCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

internal fun cachedVideoThumbnail(key: String): Bitmap? = thumbnailCache.get(key)

internal fun loadVideoThumbnail(context: Context, uri: Uri, key: String): Bitmap? {
    thumbnailCache.get(key)?.let { return it }
    val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching { context.contentResolver.loadThumbnail(uri, Size(480, 270), null) }.getOrNull()
    } else {
        null
    } ?: run {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.getScaledFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 480, 270)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
    if (frame != null) thumbnailCache.put(key, frame)
    return frame
}
