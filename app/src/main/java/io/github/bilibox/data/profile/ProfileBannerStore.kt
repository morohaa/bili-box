package io.github.bilibox.data.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import io.github.bilibox.core.ui.theme.WallpaperBlurMax
import io.github.bilibox.core.ui.theme.WallpaperDarknessMax
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

// 头图预览固定 16:9，落盘时也按这个比例裁切，避免每次读取再算一遍。
private const val BannerWidth = 1600
private const val BannerHeight = 900

// 相册原图先按最长边限制解码尺寸，既保留裁切余量，又不会把整张原图读进内存。
private const val DecodeMaxEdge = 2400

/** 头图的暗化与模糊，范围与壁纸编辑器共用。 */
internal data class BannerAdjustments(
    val darkness: Int = 0,
    val blur: Int = 0,
) {
    fun clamped(): BannerAdjustments = BannerAdjustments(
        darkness = darkness.coerceIn(0, WallpaperDarknessMax),
        blur = blur.coerceIn(0, WallpaperBlurMax),
    )
}

/**
 * 「我的」页自定义头图的本地存储。
 *
 * 图片会按 16:9 裁切后以 JPEG 写入应用私有目录，不依赖相册 URI 的临时读权限；
 * 相册只负责提供一次源图，应用重启后仍能直接展示。
 * 暗化 / 模糊与图片分开保存在 SharedPreferences，恢复默认头图时可以一并清零。
 */
internal class ProfileBannerStore(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "profile")
    private val bannerFile = File(directory, "banner.jpg")
    private val tempFile = File(directory, "banner.tmp")
    private val preferences = appContext.getSharedPreferences("profile_banner", Context.MODE_PRIVATE)

    /** 读取已保存的头图；界面层再转换成 Compose 位图。 */
    fun load(): Bitmap? = runCatching {
        if (bannerFile.isFile) BitmapFactory.decodeFile(bannerFile.absolutePath) else null
    }.getOrNull()

    /** 读取相册原图供裁剪预览；[Bitmap] 归调用方持有。 */
    fun loadSource(uri: Uri): Bitmap? = runCatching { decode(uri) }.getOrNull()

    /**
     * 按裁剪界面给出的源图区域生成 16:9 头图并落盘。
     *
     * 返回的 [Bitmap] 归调用方持有；源图不会被回收。
     */
    fun saveCrop(source: Bitmap, crop: BannerCrop): Bitmap? {
        if (source.width <= 0 || source.height <= 0) return null
        val left = crop.left.coerceIn(0f, (source.width - 1).toFloat())
        val top = crop.top.coerceIn(0f, (source.height - 1).toFloat())
        val right = (crop.left + crop.width).coerceIn(left + 1f, source.width.toFloat())
        val bottom = (crop.top + crop.height).coerceIn(top + 1f, source.height.toFloat())

        val output = Bitmap.createBitmap(BannerWidth, BannerHeight, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val srcRect = Rect(left.roundToInt(), top.roundToInt(), right.roundToInt(), bottom.roundToInt())
        Canvas(output).drawBitmap(
            source,
            srcRect,
            RectF(0f, 0f, BannerWidth.toFloat(), BannerHeight.toFloat()),
            paint,
        )
        return try {
            write(output)
            output
        } catch (_: Exception) {
            output.recycle()
            null
        }
    }

    fun clear() {
        bannerFile.delete()
        tempFile.delete()
    }

    fun loadAdjustments(): BannerAdjustments = BannerAdjustments(
        darkness = preferences.getInt("darkness", 0),
        blur = preferences.getInt("blur", 0),
    ).clamped()

    fun saveAdjustments(adjustments: BannerAdjustments) {
        val value = adjustments.clamped()
        preferences.edit()
            .putInt("darkness", value.darkness)
            .putInt("blur", value.blur)
            .apply()
    }

    private fun write(bitmap: Bitmap) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("无法创建头图目录")
        FileOutputStream(tempFile).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output)) {
                throw IOException("头图编码失败")
            }
        }
        if (!tempFile.renameTo(bannerFile)) {
            bannerFile.delete()
            if (!tempFile.renameTo(bannerFile)) throw IOException("头图保存失败")
        }
    }

    private fun decode(uri: Uri): Bitmap? {
        val source = ImageDecoder.createSource(appContext.contentResolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // Canvas 裁切需要软件位图；部分设备默认返回硬件位图。
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width
            val height = info.size.height
            val longest = max(width, height)
            if (longest > DecodeMaxEdge) {
                val scale = DecodeMaxEdge.toFloat() / longest
                decoder.setTargetSize(
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1),
                )
            }
        }
    }
}
