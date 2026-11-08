package io.github.bilibox.feature.profile

import io.github.bilibox.data.profile.BannerCrop
import kotlin.math.max

/** 裁剪预览的基础缩放：保证图片在缩放为 1 时仍完整覆盖 16:9 视口。 */
internal fun bannerCoverScale(
    sourceWidth: Int,
    sourceHeight: Int,
    viewportWidth: Float,
    viewportHeight: Float,
): Float = max(viewportWidth / sourceWidth.coerceAtLeast(1), viewportHeight / sourceHeight.coerceAtLeast(1))

/**
 * 把「缩放 + 位移」的裁剪视口映射回源图像素坐标。
 *
 * 位移方向与裁剪界面一致：正值表示图片向右 / 向下移动，可见区域因此向左 / 向上。
 * 结果会限制在源图范围内，避免浮点误差导致裁剪越界。
 */
internal fun bannerCropRect(
    sourceWidth: Int,
    sourceHeight: Int,
    viewportWidth: Float,
    viewportHeight: Float,
    zoom: Float,
    offsetX: Float,
    offsetY: Float,
): BannerCrop {
    if (sourceWidth <= 0 || sourceHeight <= 0 || viewportWidth <= 0f || viewportHeight <= 0f) {
        return BannerCrop(
            left = 0f,
            top = 0f,
            width = sourceWidth.coerceAtLeast(1).toFloat(),
            height = sourceHeight.coerceAtLeast(1).toFloat(),
        )
    }
    val scale = bannerCoverScale(sourceWidth, sourceHeight, viewportWidth, viewportHeight) * zoom
    val displayWidth = sourceWidth * scale
    val displayHeight = sourceHeight * scale
    val maxOffsetX = ((displayWidth - viewportWidth) / 2f).coerceAtLeast(0f)
    val maxOffsetY = ((displayHeight - viewportHeight) / 2f).coerceAtLeast(0f)
    val dx = offsetX.coerceIn(-maxOffsetX, maxOffsetX)
    val dy = offsetY.coerceIn(-maxOffsetY, maxOffsetY)

    val width = (viewportWidth / scale).coerceAtMost(sourceWidth.toFloat())
    val height = (viewportHeight / scale).coerceAtMost(sourceHeight.toFloat())
    val left = (((displayWidth - viewportWidth) / 2f - dx) / scale)
        .coerceIn(0f, (sourceWidth - width).coerceAtLeast(0f))
    val top = (((displayHeight - viewportHeight) / 2f - dy) / scale)
        .coerceIn(0f, (sourceHeight - height).coerceAtLeast(0f))
    return BannerCrop(left = left, top = top, width = width, height = height)
}

/** 手势更新时把位移限制在当前缩放对应的可移动范围内。 */
internal fun clampBannerOffset(
    offsetX: Float,
    offsetY: Float,
    zoom: Float,
    sourceWidth: Int,
    sourceHeight: Int,
    viewportWidth: Float,
    viewportHeight: Float,
): Pair<Float, Float> {
    val scale = bannerCoverScale(sourceWidth, sourceHeight, viewportWidth, viewportHeight) * zoom
    val maxOffsetX = ((sourceWidth * scale - viewportWidth) / 2f).coerceAtLeast(0f)
    val maxOffsetY = ((sourceHeight * scale - viewportHeight) / 2f).coerceAtLeast(0f)
    return offsetX.coerceIn(-maxOffsetX, maxOffsetX) to offsetY.coerceIn(-maxOffsetY, maxOffsetY)
}
