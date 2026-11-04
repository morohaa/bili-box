package io.github.bilibox.core.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.TextureView
import androidx.media3.ui.PlayerView
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Read only the fitted video surface, excluding the player chrome and window. Call on the UI thread. */
internal fun captureVideoFrame(playerView: PlayerView): Bitmap? {
    val surface = playerView.videoSurfaceView as? TextureView ?: return null
    if (!surface.isAvailable || surface.width <= 0 || surface.height <= 0) return null
    return surface.bitmap
}

/** Publish the frame to the gallery only after its contents have been written successfully. */
internal fun saveVideoFrame(context: Context, bitmap: Bitmap): android.net.Uri {
    val resolver = context.contentResolver
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
    val name = "BiliBox_${timestamp}_${UUID.randomUUID().toString().take(8)}.png"
    var legacyFile: File? = null
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.WIDTH, bitmap.width)
        put(MediaStore.Images.Media.HEIGHT, bitmap.height)
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/BiliBox")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        } else {
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "BiliBox")
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建截帧目录")
            legacyFile = File(directory, name)
            @Suppress("DEPRECATION")
            put(MediaStore.Images.Media.DATA, legacyFile.absolutePath)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: throw IOException("无法创建截帧文件")
    try {
        val output = resolver.openOutputStream(uri) ?: throw IOException("无法写入截帧文件")
        output.use {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException("截帧编码失败")
        }
        if (Build.VERSION.SDK_INT >= 29) {
            val published = resolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                },
                null,
                null
            )
            if (published != 1) throw IOException("无法保存截帧")
        }
        return uri
    } catch (error: Exception) {
        runCatching { resolver.delete(uri, null, null) }
        legacyFile?.delete()
        throw error
    }
}
