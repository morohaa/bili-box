package io.github.bilibox.feature.favorites

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import io.github.bilibox.core.model.VideoSummary
import io.github.bilibox.core.ui.theme.BiliBoxTheme
import io.github.bilibox.data.favorites.FavoriteFolder
import io.github.bilibox.data.favorites.FavoriteFolderPage
import io.github.bilibox.data.favorites.FavoriteOrder
import io.github.bilibox.data.favorites.FavoriteVideo
import io.github.bilibox.data.favorites.FavoriteVideoPage
import io.github.bilibox.data.favorites.FavoritesSource
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FavoritesScreenTest {
    @get:Rule val compose = createComposeRule()
    private val folder = FavoriteFolder(8, "默认收藏夹", "", 2, false)
    private val video = VideoSummary("收藏的视频", "测试 UP", "123", 1, 1_793_966_400L, "2:00", "BV-fixture", "")

    @Test
    fun openFolderSearchSortPlayAndReturn() {
        val source = FixtureSource()
        var selected: VideoSummary? = null
        var closed = false
        compose.setContent {
            val scope = rememberCoroutineScope()
            val holder = remember { FavoritesStateHolder(source, scope) }
            BiliBoxTheme(darkTheme = false, dynamicColor = false) {
                FavoritesScreen(holder, rememberLazyListState(), rememberLazyListState(), onBack = { closed = true }, onVideoClick = { selected = it })
            }
        }
        compose.onNodeWithText("默认收藏夹").performClick()
        compose.onNodeWithText("2 个收藏 · 私密").assertExists()
        compose.onNodeWithContentDescription("刷新收藏").assertDoesNotExist()
        compose.onNodeWithText("搜索此收藏夹").assertDoesNotExist()
        compose.onNodeWithText("最近收藏").assertDoesNotExist()
        compose.onNodeWithText("2026-11-06").assertExists()
        compose.onNodeWithText("收藏于 2026-11-08").assertExists()
        compose.onNodeWithText("123").assertExists()
        compose.onNodeWithText("收藏的视频").performClick()
        compose.runOnIdle { assertEquals(video, selected) }
        compose.onNodeWithText("视频已失效或无权访问").assertExists()
        compose.onNodeWithText("失效视频").assertIsNotEnabled()
        compose.onNodeWithContentDescription("搜索收藏").performClick()
        val input = compose.onNodeWithContentDescription("搜索此收藏夹")
        input.performTextInput("Kotlin")
        val inputBounds = input.fetchSemanticsNode().boundsInRoot
        val searchBounds = compose.onNodeWithContentDescription("搜索收藏").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            assertEquals(true, inputBounds.right <= searchBounds.left)
            assertEquals(inputBounds.center.y, searchBounds.center.y, 1f)
        }
        input.performImeAction()
        compose.runOnIdle { assertEquals("Kotlin", source.keyword) }
        input.assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        input.assertDoesNotExist()
        compose.onNodeWithText("2 个收藏 · 私密").assertExists()
        compose.onNodeWithContentDescription("收藏排序").performClick()
        compose.onNodeWithText("最新发布").performClick()
        compose.runOnIdle { assertEquals(FavoriteOrder.PUBLISHED, source.order) }
        compose.onNodeWithContentDescription("搜索收藏").performClick()
        input.performTextReplacement("未提交的搜索")
        compose.onNodeWithContentDescription("返回").performClick()
        compose.runOnIdle { assertEquals("Kotlin", source.keyword) }
        compose.onNodeWithContentDescription("搜索收藏").performClick()
        compose.onNodeWithText("Kotlin").assertExists()
        compose.onNodeWithContentDescription("清除搜索").performClick()
        compose.runOnIdle { assertEquals("", source.keyword) }
        compose.runOnIdle { assertEquals(FavoriteOrder.PUBLISHED, source.order) }
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("2 个收藏 · 私密").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.onNodeWithText("我创建的收藏夹").assertExists()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.runOnIdle { assertEquals(true, closed) }
    }

    @Test
    fun signedOutPageOffersLogin() {
        var returned = false
        compose.setContent {
            BiliBoxTheme(darkTheme = true, dynamicColor = false) {
                FavoritesScreen(null, rememberLazyListState(), rememberLazyListState(), { returned = true }, {})
            }
        }
        compose.onNodeWithText("返回登录").performClick()
        compose.runOnIdle { assertEquals(true, returned) }
    }

    private inner class FixtureSource : FavoritesSource {
        var keyword = ""
        var order = FavoriteOrder.SAVED
        override suspend fun accountId(): Long = 123
        override suspend fun folders(mid: Long, page: Int) = FavoriteFolderPage(listOf(folder), false)
        override suspend fun videos(folderId: Long, page: Int, keyword: String, order: FavoriteOrder): FavoriteVideoPage {
            this.keyword = keyword
            this.order = order
            return FavoriteVideoPage(
                listOf(
                    FavoriteVideo(1, video.title, "", video.author, "2:00", 1_794_139_200L, video),
                    FavoriteVideo(2, "失效视频", "", "未知 UP 主", "0:00", 0, null, "视频已失效或无权访问"),
                ),
                false,
                2,
            )
        }
    }
}
