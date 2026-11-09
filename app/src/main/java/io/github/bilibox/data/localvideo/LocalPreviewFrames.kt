package io.github.bilibox.data.localvideo

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.Closeable

/** 由单个后台协程持有；复用文件索引，按缩略图尺寸读取附近关键帧。 */
internal class LocalPreviewFrames(context: Context, uri: Uri) : Closeable {
    private val retriever = MediaMetadataRetriever()

    init {
        try {
            retriever.setDataSource(context, uri)
        } catch (error: Exception) {
            retriever.release()
            throw error
        }
    }

    fun frame(positionMs: Long): Bitmap? = retriever.getScaledFrameAtTime(
        positionMs.coerceAtLeast(0L) * 1_000L,
        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
        320,
        180,
    )

    override fun close() {
        retriever.release()
    }
}
