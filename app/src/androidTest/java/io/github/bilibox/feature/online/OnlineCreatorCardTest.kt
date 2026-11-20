package io.github.bilibox.feature.online

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import io.github.bilibox.core.model.VideoSummary
import io.github.bilibox.core.playback.PlaybackMode
import io.github.bilibox.core.ui.theme.BiliBoxTheme
import io.github.bilibox.data.bili.BiliClient
import io.github.bilibox.data.bili.Owner
import io.github.bilibox.data.bili.VideoInfo
import java.util.concurrent.ConcurrentLinkedQueue
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnlineCreatorCardTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun usesSummaryAuthorBeforeVideoDetailsAndKeepsTheResultWhenConfirmed() {
        val requests = ConcurrentLinkedQueue<Long>()
        val details = mutableStateOf<VideoInfo?>(null)
        val client = client(requests)
        val video = VideoSummary("测试视频", "搜索结果 UP", "100", 0, 0, "1:00", "BV-fixture", "", authorId = 42)

        compose.setContent {
            val info = details.value
            BiliBoxTheme(darkTheme = false, dynamicColor = false) {
                OnlineVideoDetails(
                    video = video,
                    info = info,
                    creatorCard = rememberOnlineCreatorCard(client, video.authorId, info?.owner?.mid),
                    context = LocalContext.current,
                    selectedPage = 1,
                    playbackMode = PlaybackMode.STOP_AFTER,
                    onPlaybackModeChange = {},
                    onPageSelected = {},
                    onOpenEpisodes = {},
                )
            }
        }

        waitForText("42 粉丝")
        compose.onNodeWithText("7 视频").assertIsDisplayed()
        compose.runOnIdle { details.value = VideoInfo(owner = Owner(mid = 42, name = "详情 UP")) }
        compose.onNodeWithText("详情 UP").assertIsDisplayed()
        compose.onNodeWithText("42 粉丝").assertIsDisplayed()
        assertEquals(listOf(42L), requests.toList())

        compose.runOnIdle { details.value = VideoInfo(owner = Owner(mid = 43, name = "更正后的 UP")) }
        waitForText("43 粉丝")
        compose.onNodeWithText("42 粉丝").assertDoesNotExist()
        assertEquals(listOf(42L, 43L), requests.toList())
    }

    private fun waitForText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun client(requests: ConcurrentLinkedQueue<Long>): BiliClient = BiliClient(
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = when (request.url.encodedPath) {
                "/x/web-interface/card" -> {
                    val mid = checkNotNull(request.url.queryParameter("mid")).toLong()
                    requests.add(mid)
                    """{"code":0,"data":{"card":{"mid":$mid,"fans":$mid},"archive_count":7}}"""
                }

                else -> error("Unexpected request path: ${request.url.encodedPath}")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody()).build()
        }.build(),
    )
}
