package io.github.bilibox.feature.profile

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 裁剪视口到源图坐标的映射：中心裁剪、拖动边界与缩放后再映射。
 */
class BannerCropMathTest {
    @Test
    fun sixteenByNineSourceFillsTheViewport() {
        val crop = bannerCropRect(
            sourceWidth = 2400,
            sourceHeight = 1350,
            viewportWidth = 1000f,
            viewportHeight = 562.5f,
            zoom = 1f,
            offsetX = 0f,
            offsetY = 0f,
        )
        assertEquals(0f, crop.left, 0.01f)
        assertEquals(0f, crop.top, 0.01f)
        assertEquals(2400f, crop.width, 0.01f)
        assertEquals(1350f, crop.height, 0.01f)
    }

    @Test
    fun wideSourceIsCroppedAtTheCenter() {
        val crop = bannerCropRect(
            sourceWidth = 2400,
            sourceHeight = 1000,
            viewportWidth = 1000f,
            viewportHeight = 562.5f,
            zoom = 1f,
            offsetX = 0f,
            offsetY = 0f,
        )
        assertEquals(311.11f, crop.left, 0.05f)
        assertEquals(0f, crop.top, 0.05f)
        assertEquals(1777.78f, crop.width, 0.05f)
        assertEquals(1000f, crop.height, 0.05f)
    }

    @Test
    fun portraitSourceLeavesEqualTopAndBottomCrop() {
        val crop = bannerCropRect(
            sourceWidth = 1000,
            sourceHeight = 2400,
            viewportWidth = 1000f,
            viewportHeight = 562.5f,
            zoom = 1f,
            offsetX = 0f,
            offsetY = 0f,
        )
        assertEquals(0f, crop.left, 0.05f)
        assertEquals(918.75f, crop.top, 0.05f)
        assertEquals(1000f, crop.width, 0.05f)
        assertEquals(562.5f, crop.height, 0.05f)
    }

    @Test
    fun draggingPastTheEdgeClampsToTheSource() {
        val crop = bannerCropRect(
            sourceWidth = 2400,
            sourceHeight = 1000,
            viewportWidth = 1000f,
            viewportHeight = 562.5f,
            zoom = 1f,
            offsetX = 1000f,
            offsetY = 1000f,
        )
        assertEquals(0f, crop.left, 0.05f)
        assertEquals(0f, crop.top, 0.05f)
        assertEquals(1777.78f, crop.width, 0.05f)
        assertEquals(1000f, crop.height, 0.05f)
    }

    @Test
    fun zoomingInKeepsTheViewportCentered() {
        val crop = bannerCropRect(
            sourceWidth = 2400,
            sourceHeight = 1000,
            viewportWidth = 1000f,
            viewportHeight = 562.5f,
            zoom = 2f,
            offsetX = 0f,
            offsetY = 0f,
        )
        assertEquals(755.56f, crop.left, 0.05f)
        assertEquals(250f, crop.top, 0.05f)
        assertEquals(888.89f, crop.width, 0.05f)
        assertEquals(500f, crop.height, 0.05f)
    }

    @Test
    fun offsetIsClampedToTheCurrentZoomRange() {
        val (x, y) = clampBannerOffset(
            offsetX = 1000f,
            offsetY = 1000f,
            zoom = 1f,
            sourceWidth = 2400,
            sourceHeight = 1000,
            viewportWidth = 1000f,
            viewportHeight = 562.5f,
        )
        assertEquals(175f, x, 0.01f)
        assertEquals(0f, y, 0.01f)
    }
}
