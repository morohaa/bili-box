package io.github.bilibox.feature.online

import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.exoplayer.ExoPlayer
import io.github.bilibox.core.ui.theme.BiliBoxTheme
import io.github.bilibox.data.bili.BiliException
import io.github.bilibox.data.bili.VideoComment
import io.github.bilibox.data.bili.VideoCommentOrder
import io.github.bilibox.data.bili.VideoCommentPage
import io.github.bilibox.feature.player.VideoPlayerScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnlineVideoCommentsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun readsCommentsAndRepliesAndRetriesOnlyTheFailedPage() {
        val requests = mutableListOf<Pair<VideoCommentOrder, String>>()
        val threadRequests = mutableListOf<Pair<Long, Int>>()
        val root = comment(10L, "第一条热门评论", replyCount = 2)
        compose.setContent {
            val context = LocalContext.current
            val player = remember { ExoPlayer.Builder(context).build() }
            DisposableEffect(player) { onDispose { player.release() } }
            BiliBoxTheme(darkTheme = false, dynamicColor = false) {
                VideoPlayerScreen(
                    player = player,
                    title = "测试视频",
                    sourceLabel = "哔哩哔哩",
                    commentMessage = "",
                    extraDetailPane = onlineVideoCommentsPane(
                        videoKey = 42L,
                        canLoad = true,
                        awaitingVideoInfo = false,
                        onRetryVideoInfo = {},
                        loadComments = { order, offset ->
                            requests += order to offset
                            when {
                                order == VideoCommentOrder.NEWEST -> VideoCommentPage(listOf(comment(12L, "最新评论")), null)
                                offset.isEmpty() -> VideoCommentPage(listOf(root), "next")
                                requests.count { it.second == "next" } == 1 -> throw BiliException(-412, "受限")
                                else -> VideoCommentPage(listOf(root, comment(11L, "下一页评论")), null)
                            }
                        },
                        loadReplies = { rootId, page ->
                            threadRequests += rootId to page
                            VideoCommentPage(listOf(comment(20L, "楼中楼详情")), null)
                        },
                    ),
                    onBack = {},
                    onRetry = {},
                    details = { Text("视频简介") },
                )
            }
        }

        compose.onNodeWithText("视频简介").assertIsDisplayed()
        compose.runOnIdle { assertEquals(emptyList<Pair<VideoCommentOrder, String>>(), requests) }
        compose.onNodeWithText("评论").performClick()
        compose.onNodeWithText("第一条热门评论").assertIsDisplayed()
        compose.onNodeWithText("查看 2 条回复").performClick()
        compose.onNodeWithText("楼中楼详情").assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("加载更多").performClick()
        compose.onNodeWithText("哔哩哔哩暂时限制了评论请求，请稍后再试").assertIsDisplayed()
        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithText("下一页评论").assertIsDisplayed()
        compose.onNodeWithText("最新").performClick()
        compose.onNodeWithText("最新评论").assertIsDisplayed()
        compose.onNodeWithText("第一条热门评论").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(10L to 1), threadRequests)
            assertEquals(
                listOf(VideoCommentOrder.HOT to "", VideoCommentOrder.HOT to "next", VideoCommentOrder.HOT to "next", VideoCommentOrder.NEWEST to ""),
                requests,
            )
        }
    }

    private fun comment(id: Long, message: String, replyCount: Int = 0): VideoComment = VideoComment(
        id = id,
        author = "测试用户",
        avatarUrl = "",
        message = message,
        timestampSeconds = 0L,
        likes = 0L,
        replyCount = replyCount,
    )
}
