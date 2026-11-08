package io.github.bilibox.data.appearance

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

// 首页背景最长边限制在 2160px：足够覆盖 2K 屏，又不会让每次启动都解码超大图。
private const val MaxEdge = 2160
private const val JpegQuality = 92

/**
 * 「首页背景 → 自定义」的本地图片存储。
 *
 * 相册只负责提供一次源图，选中的图片会被缩放到 [MaxEdge] 以内并以 JPEG 写入
 * 应用私有目录，之后不再依赖相册 URI 的临时读权限，应用重启后仍能直接展示。
 */
internal class HomeBackgroundImageStore(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "appearance")
    private val imageFile = File(directory, "home_background.jpg")
    private val tempFile = File(directory, "home_background.tmp")

    /** 读取已保存的首页背景；界面层再转换成 Compose 位图。 */
    fun load(): Bitmap? = runCatching {
        if (imageFile.isFile) BitmapFactory.decodeFile(imageFile.absolutePath) else null
    }.getOrNull()

    /** 读取相册原图供预览；[Bitmap] 归调用方持有。 */
    fun loadSource(uri: Uri): Bitmap? = runCatching { decode(uri) }.getOrNull()

    /**
     * 把预览里确认的图片落盘，之后的启动都读这一份。
     *
     * 传入的 [bitmap] 不会被回收；缩放产生的临时位图由本方法负责回收。
     */
    fun save(bitmap: Bitmap): Boolean = runCatching {
        if (bitmap.width <= 0 || bitmap.height <= 0) error("空图片")
        val scaled = bitmap.scaleToFit()
        try {
            write(scaled)
            true
        } finally {
            if (scaled !== bitmap) scaled.recycle()
        }
    }.getOrDefault(false)

    fun clear() {
        imageFile.delete()
        tempFile.delete()
    }

    private fun Bitmap.scaleToFit(): Bitmap {
        val longest = max(width, height)
        if (longest <= MaxEdge) return this
        val scale = MaxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            this,
            (width * scale).roundToInt().coerceAtLeast(1),
            (height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    private fun write(bitmap: Bitmap) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建背景目录")
        FileOutputStream(tempFile).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JpegQuality, output)) {
                throw IOException("背景编码失败")
            }
        }
        if (!tempFile.renameTo(imageFile)) {
            imageFile.delete()
            if (!tempFile.renameTo(imageFile)) throw IOException("背景保存失败")
        }
    }

    private fun decode(uri: Uri): Bitmap? {
        val source = ImageDecoder.createSource(appContext.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // JPEG 压缩需要软件位图；部分设备默认返回硬件位图。
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width
            val height = info.size.height
            val longest = max(width, height)
            if (longest > MaxEdge) {
                val scale = MaxEdge.toFloat() / longest
                decoder.setTargetSize(
                    (width * scale).roundToInt().coerceAtLeast(1),
                    (height * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }
    }
}
