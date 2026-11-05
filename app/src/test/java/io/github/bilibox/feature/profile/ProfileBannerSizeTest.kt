package io.github.bilibox.feature.profile

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 头图高度规则：屏宽比例优先，再受绝对上限和可用高度限制。
 */
class ProfileBannerSizeTest {
    @Test
    fun bannerHeightFollowsScreenWidthRatio() {
        // 360dp 宽、793dp 高的常规竖屏：360 / 1.7 ≈ 211.76dp。
        assertEquals(211.765f, profileBannerHeight(360.dp, 793.dp).value, 0.01f)
        assertEquals(231.176f, profileBannerHeight(393.dp, 852.dp).value, 0.01f)
    }

    @Test
    fun bannerHeightStopsAtAbsoluteCap() {
        assertEquals(320f, profileBannerHeight(900.dp, 1200.dp).value, 0.01f)
    }

    @Test
    fun bannerHeightLeavesRoomOnShortViewports() {
        // 手机横屏：792dp 宽、360dp 高时按 42% 的可用高度封顶。
        assertEquals(151.2f, profileBannerHeight(792.dp, 360.dp).value, 0.01f)
    }
}
