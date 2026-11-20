package io.github.bilibox.feature.online

import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.media3.exoplayer.ExoPlayer
import io.github.bilibox.core.ui.theme.BiliBoxTheme
import io.github.bilibox.data.bili.BiliException
import io.github.bilibox.data.bili.VideoComment
import io.github.bilibox.data.bili.VideoCommentOrder
import io.github.bilibox.data.bili.VideoCommentPage
import io.github.bilibox.feature.player.VideoPlayerScreen
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OnlineVideoCommentsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun readsCommentsAndRepliesAndRetriesOnlyTheFailedPage() {
        val requests = mutableListOf<Pair<VideoCommentOrder, String>>()
        val threadRequests = mutableListOf<Pair<Long, Int>>()
        val sheetReplies = CompletableDeferred<VideoCommentPage<Int>>()
        val root = comment(10L, "第一条热门评论", replyCount = 12).copy(
            authorId = 1L,
            authorLevel = 6,
            pinned = true,
            previewReply = comment(20L, "第一条回复预览").copy(authorLevel = 0),
        )
        val newest = comment(12L, "最新的一条测试评论", replyCount = 2)
        setPlayerContent(
            loadComments = { order, offset ->
                requests += order to offset
                when {
                    order == VideoCommentOrder.NEWEST -> VideoCommentPage(listOf(newest), null)
                    offset.isEmpty() -> VideoCommentPage(listOf(root), "next")
                    requests.count { it.second == "next" } == 1 -> throw BiliException(-412, "受限")
                    else -> VideoCommentPage(listOf(root, comment(11L, "下一页评论")), null)
                }
            },
            loadReplies = { rootId, page ->
                threadRequests += rootId to page
                if (rootId == root.id) {
                    sheetReplies.await()
                } else {
                    VideoCommentPage(listOf(comment(20L, "页内回复").copy(authorLevel = 3)), null)
                }
            },
        )

        compose.onNodeWithText("视频简介").assertIsDisplayed()
        compose.runOnIdle { assertEquals(emptyList<Pair<VideoCommentOrder, String>>(), requests) }
        compose.onNodeWithText("评论 24").performClick()
        compose.onNodeWithText("热门评论").assertIsDisplayed()
        compose.onNodeWithText("按热度").assertIsDisplayed()
        compose.onNodeWithText("共 24 条评论").assertDoesNotExist()
        compose.onNodeWithText("第一条热门评论").assertIsDisplayed()
        compose.onNodeWithText("展开全文").assertDoesNotExist()
        compose.onNodeWithText("置顶").assertIsDisplayed()
        compose.onNodeWithText("UP").assertIsDisplayed()
        compose.onNodeWithContentDescription("LV6").assertIsDisplayed()
        compose.onNodeWithContentDescription("LV0", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("第一条回复预览", substring = true).assertDoesNotExist()
        compose.onNodeWithText("查看 12 条回复").performClick()
        compose.onNodeWithText("评论详情").assertIsDisplayed()
        compose.onNodeWithText("全部回复（12）").assertIsDisplayed()
        val replies = List(12) { index -> comment(20L + index, "抽屉回复 ${index + 1}").copy(authorLevel = if (index == 0) 4 else null) }
        compose.runOnIdle { sheetReplies.complete(VideoCommentPage(replies, null)) }
        compose.onNodeWithText("抽屉回复 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("LV4").assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭评论详情").performClick()
        compose.onNodeWithText("加载更多").performClick()
        compose.onNodeWithText("哔哩哔哩暂时限制了评论请求，请稍后再试").assertIsDisplayed()
        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithText("下一页评论").assertIsDisplayed()
        compose.onNodeWithText("按热度").performClick()
        compose.onNodeWithText("按时间").assertIsDisplayed()
        compose.onNodeWithText("最新评论").assertIsDisplayed()
        compose.onNodeWithText("最新的一条测试评论").assertIsDisplayed()
        compose.onNodeWithText("第一条热门评论").assertDoesNotExist()
        compose.onNodeWithText("页内回复").assertDoesNotExist()
        compose.onNodeWithText("查看 2 条回复").performClick()
        compose.onNodeWithText("页内回复").assertIsDisplayed()
        compose.onNodeWithContentDescription("LV3").assertIsDisplayed()
        compose.onNodeWithText("评论详情").assertDoesNotExist()
        compose.onNodeWithText("收起回复").performClick()
        compose.onNodeWithText("页内回复").assertDoesNotExist()
        compose.onNodeWithText("查看 2 条回复").assertIsDisplayed()
        compose.onNodeWithText("按时间").performClick()
        compose.onNodeWithText("按热度").assertIsDisplayed()
        compose.onNodeWithText("热门评论").assertIsDisplayed()
        compose.onNodeWithText("第一条热门评论").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(listOf(10L to 1, 12L to 1), threadRequests)
            assertEquals(
                listOf(
                    VideoCommentOrder.HOT to "",
                    VideoCommentOrder.HOT to "next",
                    VideoCommentOrder.HOT to "next",
                    VideoCommentOrder.NEWEST to "",
                    VideoCommentOrder.HOT to "",
                ),
                requests,
            )
        }
    }

    @Test
    fun longCommentStartsCollapsedAndCanBeExpandedAndCollapsedAgain() {
        val message = (1..7).joinToString("\n") { "评论第 $it 行" }
        setPlayerContent(loadComments = { _, _ -> VideoCommentPage(listOf(comment(1L, message)), null) })
        compose.onNodeWithText("评论 24").performClick()

        fun messageLayout(): TextLayoutResult {
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(message).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            return results.single()
        }

        assertEquals(5, messageLayout().lineCount)
        assertTrue(messageLayout().isLineEllipsized(4))
        compose.onNodeWithText("展开全文").performClick()
        assertEquals(7, messageLayout().lineCount)
        assertFalse(messageLayout().hasVisualOverflow)
        compose.onNodeWithText("收起全文").performClick()
        assertEquals(5, messageLayout().lineCount)
        assertTrue(messageLayout().isLineEllipsized(4))
    }

    @Test
    fun orderBarScrollsAwayWhileDetailTabsStayVisible() {
        val comments = List(24) { index -> comment(index + 1L, "测试评论 ${index + 1}") }
        setPlayerContent(loadComments = { _, _ -> VideoCommentPage(comments, null) })

        compose.onNodeWithText("评论 24").performClick()
        compose.onNodeWithText("按热度").assertIsDisplayed()
        compose.onNodeWithText("热门评论").assertIsDisplayed()

        val list = compose.onNode(hasScrollToIndexAction())
        list.performScrollToIndex(10)
        compose.onNodeWithText("按热度").assertIsNotDisplayed()
        compose.onNodeWithText("热门评论").assertIsNotDisplayed()
        compose.onNodeWithText("评论 24").assertIsDisplayed()

        list.performScrollToIndex(0)
        compose.onNodeWithText("按热度").assertIsDisplayed()
        compose.onNodeWithText("热门评论").assertIsDisplayed()
    }

    private fun setPlayerContent(
        loadComments: suspend (VideoCommentOrder, String) -> VideoCommentPage<String>,
        loadReplies: suspend (Long, Int) -> VideoCommentPage<Int> = { _, _ -> VideoCommentPage(emptyList(), null) },
    ) {
        compose.setContent {
            val context = LocalContext.current
            val player = remember { ExoPlayer.Builder(context).build() }
            DisposableEffect(player) { onDispose { player.release() } }
            BiliBoxTheme(darkTheme = false, dynamicColor = false) {
                VideoPlayerScreen(
                    player = player,
                    title = "测试视频",
                    sourceLabel = "哔哩哔哩",
                    detailTabs = listOf("简介", "评论 24"),
                    commentMessage = "",
                    extraDetailPane = onlineVideoCommentsPane(
                        videoKey = 42L,
                        canLoad = true,
                        awaitingVideoInfo = false,
                        onRetryVideoInfo = {},
                        ownerId = 1L,
                        loadComments = loadComments,
                        loadReplies = loadReplies,
                    ),
                    onBack = {},
                    onRetry = {},
                    details = { Text("视频简介") },
                )
            }
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
