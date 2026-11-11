package io.github.bilibox.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.model.VideoSummary
import io.github.bilibox.core.ui.theme.BiliBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VideoListCardTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun onlineAndCustomContentShareCardDimensionsAndClickBehavior() {
        var onlineClicks = 0
        var offlineClicks = 0
        compose.setContent {
            BiliBoxTheme(dynamicColor = false) {
                Column(Modifier.width(340.dp).verticalScroll(rememberScrollState())) {
                    VideoListCard(
                        video = VideoSummary("在线视频", "作者", "100", 20, 0, "01:40", "BV-test", ""),
                        darkMode = false,
                        modifier = Modifier.testTag("online-card"),
                        onClick = { onlineClicks++ },
                    )
                    VideoListCardLayout(
                        title = "离线视频",
                        author = "作者",
                        darkMode = false,
                        cover = { VideoCoverPlaceholder(Modifier) },
                        metadata = { Text("3 MB · 已缓存", fontSize = 11.sp, lineHeight = 14.3.sp) },
                        modifier = Modifier.testTag("offline-card"),
                        onClick = { offlineClicks++ },
                    )
                }
            }
        }
        val online = compose.onNodeWithTag("online-card").fetchSemanticsNode().boundsInRoot
        val offline = compose.onNodeWithTag("offline-card").fetchSemanticsNode().boundsInRoot
        assertEquals(online.width, offline.width, .1f)
        assertEquals(online.height, offline.height, .1f)
        compose.onNodeWithText("100").assertIsDisplayed()
        compose.onNodeWithText("1:40").assertIsDisplayed()
        compose.onNodeWithTag("online-card").performClick()
        compose.onNodeWithTag("offline-card").performClick()
        compose.runOnIdle {
            assertEquals(1, onlineClicks)
            assertEquals(1, offlineClicks)
        }
    }
}
