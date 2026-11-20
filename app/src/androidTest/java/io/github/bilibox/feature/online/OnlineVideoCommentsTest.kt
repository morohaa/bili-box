package io.github.bilibox.feature.online

import android.content.ClipboardManager
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.TextLayoutResult
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
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
                    VideoCommentPage(listOf(comment(20L, "详情回复").copy(authorLevel = 3)), null)
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
        for (label in listOf("置顶", "UP")) {
            val badge = compose.onNodeWithText(label).assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue((badge.bottom - badge.top).value < 18f)
            assertTrue((badge.right - badge.left).value < 29f)
        }
        compose.onNodeWithContentDescription("LV6").assertIsDisplayed()
        compose.onNodeWithContentDescription("LV0", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("第一条回复预览", substring = true).assertDoesNotExist()
        compose.onNodeWithText("查看 12 条回复").performClick()
        compose.onNodeWithText("评论详情").assertIsDisplayed()
        compose.onNodeWithText("相关回复共12条").assertIsDisplayed()
        val replies = List(12) { index ->
            val reply = comment(20L + index, "抽屉回复 ${index + 1}")
            if (index == 0) reply.copy(authorLevel = 4, likes = 20L) else reply
        }
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
        compose.onNodeWithText("详情回复").assertDoesNotExist()
        compose.onNodeWithText("查看 2 条回复").performClick()
        compose.onNodeWithText("评论详情").assertIsDisplayed()
        compose.onNodeWithText("相关回复共2条").assertIsDisplayed()
        compose.onNodeWithText("详情回复").assertIsDisplayed()
        compose.onNodeWithContentDescription("LV3").assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭评论详情").performClick()
        compose.onNodeWithTag("comment-replies-panel").assertDoesNotExist()
        compose.onNodeWithText("详情回复").assertDoesNotExist()
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
    fun dialogShowsOnlyItsRepliesInTimeOrderAndBackRestoresTheReplyList() {
        val anchor = comment(11L, "对话第一条").copy(timestampSeconds = 100L, dialogId = 11L)
        val selected = comment(12L, "点开的那条回复").copy(timestampSeconds = 200L, dialogId = 11L)
        val unrelated = List(12) { index ->
            val id = 100L + index
            comment(id, "无对话的回复 ${index + 1}").copy(
                timestampSeconds = if (index < 6) 1_000L - index else 1L,
                dialogId = id,
            )
        }
        val root = comment(10L, "有一组对话的评论", replyCount = 14)
        val requests = mutableListOf<Triple<Long, Long, Long>>()
        setPlayerContent(
            loadComments = { _, _ -> VideoCommentPage(listOf(root), null) },
            loadReplies = { _, _ -> VideoCommentPage(unrelated + listOf(anchor, selected), null) },
            loadDialog = { rootId, dialogId, minFloor ->
                requests += Triple(rootId, dialogId, minFloor)
                if (minFloor == 0L) VideoCommentPage(listOf(selected), 30L) else VideoCommentPage(listOf(anchor, selected), null)
            },
        )
        compose.onNodeWithText("评论 24").performClick()
        compose.onNodeWithText("查看 14 条回复").performClick()
        compose.onNodeWithText("默认排序").performClick()
        compose.onNodeWithText("按热度").performClick()
        compose.onNodeWithText("查看对话").assertDoesNotExist()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(8)
        val reply = compose.onNodeWithText(selected.message)
        val originalTop = reply.getUnclippedBoundsInRoot().top.value
        compose.onAllNodesWithText("查看对话")[0].performClick()

        val title = compose.onNodeWithText("对话列表").assertIsDisplayed()
        val close = compose.onNodeWithContentDescription("关闭对话列表").assertIsDisplayed()
        val header = compose.onNodeWithTag("comment-dialog-header").getUnclippedBoundsInRoot()
        assertEquals(header.left.value + 12f, title.getUnclippedBoundsInRoot().left.value, 0.5f)
        assertTrue(header.right.value - close.getUnclippedBoundsInRoot().right.value in 0f..12f)
        compose.onNodeWithText(root.message).assertDoesNotExist()
        compose.onNodeWithText("无对话的回复", substring = true).assertDoesNotExist()
        compose.onNode(hasText(selected.message) and hasAnyAncestor(isSelected())).assertIsDisplayed()
        compose.onNodeWithTag("comment-dialog-list").performScrollToIndex(0)
        val firstTop = compose.onNodeWithText(anchor.message).getUnclippedBoundsInRoot().top.value
        assertTrue(firstTop < reply.getUnclippedBoundsInRoot().top.value)
        compose.runOnIdle { assertEquals(listOf(Triple(10L, 11L, 0L), Triple(10L, 11L, 30L)), requests) }

        pressBack()
        compose.onNodeWithText("评论详情").assertIsDisplayed()
        assertEquals(originalTop, reply.getUnclippedBoundsInRoot().top.value, 0.5f)
        compose.onAllNodesWithText("查看对话")[0].performClick()
        close.performClick()
        assertEquals(originalTop, reply.getUnclippedBoundsInRoot().top.value, 0.5f)
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNodeWithText("按时间").assertIsDisplayed()
    }

    @Test
    fun dialogActionSharesMetadataRowAndPanelSlidesInAndOut() {
        val root = comment(10L, "有对话的评论", replyCount = 1).copy(
            timestampSeconds = 1_600_000_000L,
            likes = 32L,
            ipLocation = "广东",
        )
        val reply = comment(12L, "有对话的回复").copy(
            timestampSeconds = 1_700_000_000L,
            likes = 5L,
            dialogId = 11L,
            ipLocation = "浙江",
        )
        setPlayerContent(
            loadComments = { _, _ -> VideoCommentPage(listOf(root), null) },
            loadReplies = { _, _ -> VideoCommentPage(listOf(reply), null) },
            loadDialog = { _, _, _ -> VideoCommentPage(listOf(reply), null) },
        )
        compose.onNodeWithText("评论 24").performClick()
        val rootLocation = compose.onNodeWithText("广东").assertIsDisplayed().getUnclippedBoundsInRoot()
        val rootLikes = compose.onNodeWithContentDescription("32 个赞").assertIsDisplayed().getUnclippedBoundsInRoot()
        val details = compose.onNodeWithTag("playerDetails").getUnclippedBoundsInRoot()
        assertEquals(16f, (details.right - rootLikes.right).value, 0.5f)
        assertTrue(rootLocation.right < rootLikes.left)
        assertTrue(rootLikes.top < rootLocation.bottom && rootLocation.top < rootLikes.bottom)
        compose.onNodeWithText("浙江").assertDoesNotExist()
        compose.onNodeWithText("查看 1 条回复").performClick()

        val action = compose.onNodeWithText("查看对话")
        val actionBounds = action.getUnclippedBoundsInRoot()
        val timeBounds = compose.onNodeWithText(formatPublishDateTime(reply.timestampSeconds)).getUnclippedBoundsInRoot()
        val location = compose.onNodeWithText("浙江").assertIsDisplayed()
        val locationBounds = location.getUnclippedBoundsInRoot()
        val likesBounds = compose.onNodeWithContentDescription("5 个赞").getUnclippedBoundsInRoot()
        assertEquals(timeBounds.top.value, actionBounds.top.value, 0.5f)
        assertEquals(timeBounds.top.value, locationBounds.top.value, 0.5f)
        assertTrue(timeBounds.right < locationBounds.left && locationBounds.right < actionBounds.left)
        assertTrue(actionBounds.right < likesBounds.left)
        assertTrue(likesBounds.top < actionBounds.bottom && actionBounds.top < likesBounds.bottom)

        val panelTop = compose.onNodeWithTag("comment-replies-panel").getUnclippedBoundsInRoot().top.value
        val dialog = compose.onNodeWithTag("comment-dialog-panel")
        compose.mainClock.autoAdvance = false
        try {
            action.performClick()
            compose.mainClock.advanceTimeBy(96)
            assertTrue(dialog.getUnclippedBoundsInRoot().top.value > panelTop)
            compose.mainClock.advanceTimeBy(384)
            assertEquals(panelTop, dialog.getUnclippedBoundsInRoot().top.value, 0.5f)
            location.assertIsDisplayed()

            compose.onNodeWithContentDescription("关闭对话列表").performClick()
            compose.mainClock.advanceTimeBy(96)
            assertTrue(dialog.getUnclippedBoundsInRoot().top.value > panelTop)
            compose.mainClock.advanceTimeBy(384)
            dialog.assertDoesNotExist()
            action.assertIsDisplayed()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun repliesPanelStartsBelowVideoAndSortsAcrossPages() {
        val root = comment(10L, "有多页回复的评论", replyCount = 80)
        val newest = comment(20L, "时间最新的回复").copy(timestampSeconds = 1_800_000_000L)
        val hottest = comment(30L, "点赞最多的回复").copy(timestampSeconds = 1_700_000_000L, likes = 100L)
        val pages = mutableListOf<Int>()
        val failSecondPage = CompletableDeferred<Unit>()
        setPlayerContent(
            loadComments = { _, _ -> VideoCommentPage(listOf(root), null) },
            loadReplies = { _, page ->
                pages += page
                when {
                    page == 1 -> VideoCommentPage(listOf(newest), 2)

                    pages.count { it == 2 } == 1 -> {
                        failSecondPage.await()
                        throw BiliException(-412, "受限")
                    }

                    else -> VideoCommentPage(listOf(hottest), null)
                }
            },
        )
        compose.onNodeWithText("评论 24").performClick()
        compose.onNodeWithText("查看 80 条回复").performClick()

        val panel = compose.onNodeWithTag("comment-replies-panel")
        val video = compose.onNodeWithTag("player-touch-surface")
        assertEquals(video.getUnclippedBoundsInRoot().bottom.value, panel.getUnclippedBoundsInRoot().top.value, 0.5f)
        compose.onNodeWithText("相关回复共80条").assertIsDisplayed()
        compose.onNodeWithText(newest.message).assertIsDisplayed()
        compose.onNodeWithText("正在加载更多回复").assertIsDisplayed()
        compose.runOnIdle { failSecondPage.complete(Unit) }
        compose.onNodeWithText(newest.message).assertIsDisplayed()
        compose.onNodeWithText("重试").performClick()

        fun replyTop(reply: VideoComment): Float = compose.onNodeWithText(reply.message).getUnclippedBoundsInRoot().top.value

        assertTrue(replyTop(newest) < replyTop(hottest))
        compose.onNodeWithText("默认排序").performClick()
        assertTrue(replyTop(hottest) < replyTop(newest))
        compose.onNodeWithText("按热度").performClick()
        compose.onNodeWithText("按时间").assertIsDisplayed()
        assertTrue(replyTop(newest) < replyTop(hottest))
        compose.onNodeWithText("按时间").performClick()
        assertTrue(replyTop(hottest) < replyTop(newest))
        compose.runOnIdle { assertEquals(listOf(1, 2, 2), pages) }

        pressBack()
        panel.assertDoesNotExist()
        compose.onNodeWithText("查看 80 条回复").assertIsDisplayed()
    }

    @Test
    fun laterPagesAppendWithoutChangingTheOrderUntilTheNextSortClick() {
        val root = comment(1L, "有多页回复的评论", replyCount = 22)
        val firstPage = List(20) { index -> comment(index + 2L, "回复 ${index + 1}").copy(likes = 20L - index) }
        val secondPage = comment(22L, "第二页回复").copy(likes = 50L)
        val hottest = comment(23L, "最后一页的热门回复").copy(likes = 100L)
        val pendingSecondPage = CompletableDeferred<Unit>()
        val pendingLastPage = CompletableDeferred<Unit>()
        setPlayerContent(
            loadComments = { _, _ -> VideoCommentPage(listOf(root), null) },
            loadReplies = { _, page ->
                when (page) {
                    1 -> VideoCommentPage(firstPage, 2)

                    2 -> {
                        pendingSecondPage.await()
                        VideoCommentPage(listOf(secondPage), 3)
                    }

                    else -> {
                        pendingLastPage.await()
                        VideoCommentPage(listOf(hottest), null)
                    }
                }
            },
        )
        compose.onNodeWithText("评论 24").performClick()
        compose.onNodeWithText("查看 22 条回复").performClick()
        val list = compose.onNode(hasScrollToIndexAction())
        val anchor = compose.onNodeWithText(firstPage[8].message)
        list.performScrollToIndex(10)
        val anchorTop = anchor.assertIsDisplayed().getUnclippedBoundsInRoot().top.value

        compose.runOnIdle { pendingSecondPage.complete(Unit) }
        assertEquals(anchorTop, anchor.getUnclippedBoundsInRoot().top.value, 0.5f)
        list.performScrollToIndex(22)
        compose.onNodeWithText(secondPage.message).assertIsDisplayed()
        list.performScrollToIndex(1)
        compose.onNodeWithText("默认排序").performClick()
        val second = compose.onNodeWithText(secondPage.message).assertIsDisplayed()
        val first = compose.onNodeWithText(firstPage.first().message).assertIsDisplayed()
        assertTrue(second.getUnclippedBoundsInRoot().top < first.getUnclippedBoundsInRoot().top)
        list.performScrollToIndex(11)
        assertEquals(anchorTop, anchor.getUnclippedBoundsInRoot().top.value, 0.5f)

        compose.runOnIdle { pendingLastPage.complete(Unit) }
        assertEquals(anchorTop, anchor.getUnclippedBoundsInRoot().top.value, 0.5f)
        list.performScrollToIndex(23)
        compose.onNodeWithText(hottest.message).assertIsDisplayed()
        list.performScrollToIndex(1)
        assertTrue(second.getUnclippedBoundsInRoot().top < first.getUnclippedBoundsInRoot().top)
        compose.onNodeWithText(hottest.message).assertIsNotDisplayed()
        compose.onNodeWithText("按热度").performClick()
        val hottestTop = compose.onNodeWithText(hottest.message).getUnclippedBoundsInRoot().top.value
        val secondPageTop = compose.onNodeWithText(secondPage.message).getUnclippedBoundsInRoot().top.value
        assertTrue(hottestTop < secondPageTop)
    }

    @Test
    fun repliesPanelMatchesDetailBarHeightAndSlidesInAndOut() {
        val root = comment(10L, "有回复的评论", replyCount = 1)
        setPlayerContent(
            loadComments = { _, _ -> VideoCommentPage(listOf(root), null) },
            loadReplies = { _, _ -> VideoCommentPage(listOf(comment(11L, "相关回复")), null) },
        )
        compose.onNodeWithText("评论 24").performClick()
        val detailBar = compose.onNodeWithTag("player-detail-bar").getUnclippedBoundsInRoot()
        val detailBarHeight = (detailBar.bottom - detailBar.top).value
        val videoBottom = compose.onNodeWithTag("player-touch-surface").getUnclippedBoundsInRoot().bottom.value
        val panel = compose.onNodeWithTag("comment-replies-panel")
        val panelHeader = compose.onNodeWithTag("comment-replies-header")

        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithText("查看 1 条回复").performClick()
            compose.mainClock.advanceTimeBy(96)
            assertTrue(panel.getUnclippedBoundsInRoot().top.value > videoBottom)
            compose.mainClock.advanceTimeBy(384)
            assertEquals(videoBottom, panel.getUnclippedBoundsInRoot().top.value, 0.5f)
            val headerBounds = panelHeader.getUnclippedBoundsInRoot()
            assertEquals(detailBarHeight, (headerBounds.bottom - headerBounds.top).value, 0.5f)

            compose.onNodeWithContentDescription("关闭评论详情").performClick()
            compose.mainClock.advanceTimeBy(96)
            assertTrue(panel.getUnclippedBoundsInRoot().top.value > videoBottom)
            compose.mainClock.advanceTimeBy(384)
            panel.assertDoesNotExist()
            compose.onNodeWithText("查看 1 条回复").assertIsDisplayed()
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test
    fun longOriginalCommentAndRepliesShareOneScrollUnderFixedTitle() {
        val message = (1..35).joinToString("\n") { "原评论第 $it 行" }
        val root = comment(1L, message, replyCount = 8)
        val replies = List(8) { index -> comment(index + 2L, "相关回复 ${index + 1}") }
        setPlayerContent(
            loadComments = { _, _ -> VideoCommentPage(listOf(root), null) },
            loadReplies = { _, _ -> VideoCommentPage(replies, null) },
        )
        compose.onNodeWithText("评论 24").performClick()
        compose.onNodeWithText("查看 8 条回复").performClick()

        val title = compose.onNodeWithText("评论详情")
        val initialTitleTop = title.getUnclippedBoundsInRoot().top.value
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(message).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(35, layouts.single().lineCount)
        assertFalse(layouts.single().hasVisualOverflow)
        compose.onNodeWithText("相关回复共8条").assertIsNotDisplayed()

        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(1)
        compose.onNodeWithText(message).assertIsNotDisplayed()
        compose.onNodeWithText("相关回复共8条").assertIsDisplayed()
        compose.onNodeWithText("默认排序").assertIsDisplayed()
        assertEquals(initialTitleTop, title.getUnclippedBoundsInRoot().top.value, 0.5f)
    }

    @Test
    fun longCommentCanBeCopiedInFullWhileCollapsedAndStillToggles() {
        val message = (1..7).joinToString("\n") { "评论第 $it 行" } + "[滑稽]"
        setPlayerContent(loadComments = { _, _ -> VideoCommentPage(listOf(comment(1L, message)), null) })
        compose.onNodeWithText("评论 24").performClick()

        fun messageLayout(): TextLayoutResult {
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(message).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            return results.single()
        }

        assertEquals(5, messageLayout().lineCount)
        assertTrue(messageLayout().isLineEllipsized(4))
        compose.onNodeWithText(message).performTouchInput { longClick() }
        compose.runOnIdle {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val clipboard = context.getSystemService(ClipboardManager::class.java)
            assertEquals(message, clipboard?.primaryClip?.getItemAt(0)?.text?.toString())
        }
        compose.onNodeWithText("展开全文").performClick()
        assertEquals(7, messageLayout().lineCount)
        assertFalse(messageLayout().hasVisualOverflow)
        compose.onNodeWithText("收起全文").performClick()
        assertEquals(5, messageLayout().lineCount)
        assertTrue(messageLayout().isLineEllipsized(4))
    }

    @Test
    fun detailTabsFollowSwipesAndRememberCommentScroll() {
        val comments = List(24) { index -> comment(index + 1L, "测试评论 ${index + 1}") }
        var requests = 0
        setPlayerContent(loadComments = { _, _ ->
            requests++
            VideoCommentPage(comments, null)
        })

        val pager = compose.onNodeWithTag("player-detail-pager")
        val intro = compose.onNodeWithText("简介").assertIsSelected()
        val commentsTab = compose.onNodeWithText("评论 24")
        compose.runOnIdle { assertEquals(0, requests) }
        pager.performTouchInput { swipeLeft(startX = width * .8f, endX = width * .2f) }
        commentsTab.assertIsSelected()
        compose.onNodeWithText(comments.first().message).assertIsDisplayed()
        compose.onNodeWithTag("player-detail-list").performScrollToIndex(10)
        val anchor = compose.onNodeWithText(comments[9].message).assertIsDisplayed()
        val anchorTop = anchor.getUnclippedBoundsInRoot().top.value

        pager.performTouchInput { swipeRight(startX = width * .2f, endX = width * .8f) }
        intro.assertIsSelected()
        compose.onNodeWithText("视频简介").assertIsDisplayed()
        commentsTab.performClick().assertIsSelected()
        assertEquals(anchorTop, anchor.assertIsDisplayed().getUnclippedBoundsInRoot().top.value, 0.5f)
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test
    fun tappingCommentTabSlidesContentWithoutMovingTheTabBar() {
        val first = comment(1L, "测试评论")
        setPlayerContent(loadComments = { _, _ -> VideoCommentPage(listOf(first), null) })

        val introContent = compose.onNodeWithText("视频简介").assertIsDisplayed()
        val originalLeft = introContent.getUnclippedBoundsInRoot().left.value
        val bar = compose.onNodeWithTag("player-detail-bar")
        val originalBar = bar.getUnclippedBoundsInRoot()
        val commentsTab = compose.onNodeWithText("评论 24")
        compose.mainClock.autoAdvance = false
        try {
            commentsTab.performClick()
            compose.mainClock.advanceTimeBy(64)
            assertTrue(introContent.getUnclippedBoundsInRoot().left.value < originalLeft)
            assertEquals(originalBar, bar.getUnclippedBoundsInRoot())
        } finally {
            compose.mainClock.autoAdvance = true
        }
        commentsTab.assertIsSelected()
        compose.onNodeWithText(first.message).assertIsDisplayed()
    }

    @Test
    fun orderBarScrollsAwayWhileDetailTabsStayVisible() {
        val comments = List(24) { index -> comment(index + 1L, "测试评论 ${index + 1}") }
        setPlayerContent(loadComments = { _, _ -> VideoCommentPage(comments, null) })

        compose.onNodeWithText("评论 24").performClick()
        compose.onNodeWithText("按热度").assertIsDisplayed()
        compose.onNodeWithText("热门评论").assertIsDisplayed()

        val list = compose.onNodeWithTag("player-detail-list")
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
        loadDialog: suspend (Long, Long, Long) -> VideoCommentPage<Long> = { _, _, _ -> VideoCommentPage(emptyList(), null) },
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
                        loadDialog = loadDialog,
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
