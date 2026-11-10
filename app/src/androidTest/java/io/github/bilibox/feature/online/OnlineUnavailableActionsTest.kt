package io.github.bilibox.feature.online

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.bilibox.core.model.VideoSummary
import io.github.bilibox.core.ui.theme.BiliBoxTheme
import io.github.bilibox.data.bili.Stat
import io.github.bilibox.data.bili.VideoInfo
import org.junit.Rule
import org.junit.Test

class OnlineUnavailableActionsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun unsupportedActionsKeepServerCountsAndNeverPretendToFollow() {
        compose.setContent {
            BiliBoxTheme(darkTheme = false, dynamicColor = false) {
                OnlineVideoDetails(
                    video = VideoSummary("测试视频", "测试 UP", "100", 0, 0, "1:00", "BV-fixture", ""),
                    info = VideoInfo(stat = Stat(like = 101, coin = 202, favorite = 303)),
                    creatorCard = null,
                    context = LocalContext.current,
                    selectedPage = 1,
                    autoPlayNext = false,
                    onAutoPlayNextChange = {},
                    onPageSelected = {},
                    onOpenEpisodes = {},
                )
            }
        }

        for (description in listOf("关注 UP 主", "点赞 101", "投币 202", "收藏 303")) {
            val action = compose.onNodeWithContentDescription(description)
            action.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "当前版本暂不支持"))
            repeat(2) { action.performClick() }
            // The same server count and unavailable state survive repeated taps.
            action.assertExists()
        }
        compose.onNodeWithText("已关注").assertDoesNotExist()
        compose.onNodeWithContentDescription("取消关注").assertDoesNotExist()
    }
}
