package io.github.bilibox.data.localvideo

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.util.LruCache

private val frameRateCache = LruCache<String, Float>(256)

internal fun cachedVideoFrameRate(videoKey: String): Float? = frameRateCache.get(videoKey)

/** 按需补读媒体库遗漏的帧率；调用方在 IO 线程执行，未知结果也缓存。 */
internal fun loadVideoFrameRate(context: Context, video: LocalVideo): Float {
    frameRateCache.get(video.key)?.let { return it }
    val rate = readTrackFrameRate(context, video) ?: readCaptureFrameRate(context, video) ?: 0f
    frameRateCache.put(video.key, rate)
    return rate
}

private fun readTrackFrameRate(context: Context, video: LocalVideo): Float? = runCatching {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(context, video.uri, null)
        (0 until extractor.trackCount).firstNotNullOfOrNull { index ->
            val format = extractor.getTrackFormat(index)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") != true ||
                !format.containsKey(MediaFormat.KEY_FRAME_RATE)
            ) {
                null
            } else {
                runCatching { format.getFloat(MediaFormat.KEY_FRAME_RATE) }
                    .getOrElse { format.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }
                    .takeIf { it.isFinite() && it > 0f }
            }
        }
    } finally {
        extractor.release()
    }
}.getOrNull()

private fun readCaptureFrameRate(context: Context, video: LocalVideo): Float? = runCatching {
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, video.uri)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
            ?.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }
    } finally {
        retriever.release()
    }
}.getOrNull()
