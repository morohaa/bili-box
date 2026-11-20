package io.github.bilibox.feature.online

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.bilibox.core.model.VideoSummary
import io.github.bilibox.core.playback.PlaybackMode
import io.github.bilibox.core.ui.theme.BiliBoxTheme
import io.github.bilibox.data.bili.Stat
import io.github.bilibox.data.bili.VideoInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnlineVideoDescriptionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun titleAndDescriptionAnimateTheirHeightInBothDirections() {
        val title = "这是一个用于验证展开与收起动画的长视频标题".repeat(4)
        val description = "第一行简介\n第二行简介\n第三行简介"
        compose.setContent {
            BiliBoxTheme(darkTheme = false, dynamicColor = false) {
                OnlineVideoDetails(
                    video = VideoSummary(title, "测试 UP", "100", 0, 0, "1:23", "BV-fixture", ""),
                    info = VideoInfo(desc = description, stat = Stat(like = 101)),
                    creatorCard = null,
                    context = LocalContext.current,
                    selectedPage = 1,
                    playbackMode = PlaybackMode.STOP_AFTER,
                    onPlaybackModeChange = {},
                    onPageSelected = {},
                    onOpenEpisodes = {},
                )
            }
        }

        val duration = compose.onNodeWithText("1:23")
        val like = compose.onNodeWithContentDescription("点赞 101")
        val about = compose.onNodeWithText(description)
        val copy = compose.onNodeWithContentDescription("复制 BV 号 BV-fixture")
        fun metadataTop() = duration.getUnclippedBoundsInRoot().top.value
        fun descriptionGap() = like.getUnclippedBoundsInRoot().top.value - metadataTop()

        val collapsedTop = metadataTop()
        val collapsedGap = descriptionGap()
        about.assertDoesNotExist()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithContentDescription("展开简介").performClick()
            compose.mainClock.advanceTimeBy(96)
            val expandingTop = metadataTop()
            val expandingGap = descriptionGap()
            compose.mainClock.advanceTimeBy(320)
            val expandedTop = metadataTop()
            val expandedGap = descriptionGap()
            assertTrue(collapsedTop < expandingTop && expandingTop < expandedTop)
            assertTrue(collapsedGap < expandingGap && expandingGap < expandedGap)
            about.assertIsDisplayed()
            copy.assertIsDisplayed()

            compose.onNodeWithContentDescription("收起简介").performClick()
            compose.mainClock.advanceTimeBy(96)
            val shrinkingTop = metadataTop()
            val shrinkingGap = descriptionGap()
            assertTrue(collapsedTop < shrinkingTop && shrinkingTop < expandedTop)
            assertTrue(collapsedGap < shrinkingGap && shrinkingGap < expandedGap)
            compose.mainClock.advanceTimeBy(320)
            assertEquals(collapsedTop, metadataTop(), 0.5f)
            assertEquals(collapsedGap, descriptionGap(), 0.5f)
            about.assertDoesNotExist()
            copy.assertDoesNotExist()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }
}
