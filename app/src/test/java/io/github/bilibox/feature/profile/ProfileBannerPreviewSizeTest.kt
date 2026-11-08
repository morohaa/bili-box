package io.github.bilibox.feature.profile

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 头图预览宽高：竖屏铺满宽度，横屏或矮窗口按 16:9 让位于高度。
 */
class ProfileBannerPreviewSizeTest {
    @Test
    fun previewUsesFullWidthInPortrait() {
        assertEquals(360f, profileBannerPreviewWidth(360.dp, 520.dp).value, 0.01f)
        assertEquals(393f, profileBannerPreviewWidth(393.dp, 700.dp).value, 0.01f)
    }

    @Test
    fun previewStopsAtSixteenByNineInLandscape() {
        // 800dp 宽、270dp 高的横屏区域：270 × 16 / 9 = 480dp。
        assertEquals(480f, profileBannerPreviewWidth(800.dp, 270.dp).value, 0.01f)
    }
}
