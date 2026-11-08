package io.github.bilibox.data.profile

/**
 * 源图像素坐标系中的裁切区域，宽高固定为 16:9。
 *
 * 由裁剪界面按用户拖动 / 缩放后的可见区域计算，再交给 [ProfileBannerStore] 落盘。
 */
internal data class BannerCrop(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)
