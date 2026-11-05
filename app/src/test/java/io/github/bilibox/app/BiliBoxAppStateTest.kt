package io.github.bilibox.app

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.saveable.SaverScope
import io.github.bilibox.core.model.SearchDuration
import io.github.bilibox.core.model.SearchOrder
import io.github.bilibox.core.model.SearchTimeRange
import io.github.bilibox.core.model.VideoSummary
import io.github.bilibox.data.auth.BiliSession
import io.github.bilibox.data.localvideo.LocalVideoQuickFilter
import io.github.bilibox.feature.search.SearchUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BiliBoxAppStateTest {
    // 这些测试只验证同步状态转换与保存恢复，取消作用域避免启动网络请求。
    private val scope = CoroutineScope(Job().apply { cancel() })
    private val saverScope = object : SaverScope {
        override fun canBeSaved(value: Any): Boolean = true
    }

    @Test
    fun `navigation and search preferences survive restoration and disposal`() {
        val state = newState()
        state.selectTab(AppTab.PROFILE.ordinal)
        state.updateSearchFilters(SearchDuration.TEN_TO_THIRTY, SearchTimeRange.LAST_MONTH)
        state.updateQuery("Kotlin")
        state.runSearch(order = SearchOrder.NEWEST)
        state.searchGrid = true
        state.homeExpanded = true
        state.dispose()

        val restored = restore(state)

        assertEquals(AppTab.PROFILE, restored.selectedTab)
        assertEquals("Kotlin", restored.searchQuery)
        assertEquals(SearchOrder.NEWEST, restored.searchOrder)
        assertEquals(SearchDuration.TEN_TO_THIRTY, restored.searchDuration)
        assertEquals(SearchTimeRange.LAST_MONTH, restored.searchTimeRange)
        assertTrue(restored.searchGrid)
        assertTrue(restored.homeExpanded)
        assertEquals(SearchUiState.Idle, restored.searchState)
    }

    @Test
    fun `restoration reloads session and drops transient navigation and search work`() {
        val oldSession = BiliSession("old account", "", "test-old-cookie")
        val loadedSession = BiliSession("loaded account", "", "test-loaded-cookie")
        val state = newState(oldSession)
        state.openLibrary(LocalVideoQuickFilter.UNFINISHED)
        state.openOnlineVideo(VideoSummary("Video", "Author", "0", 0L, 0L, "1:00", "BV-test", ""))
        state.updateQuery("Kotlin")
        state.runSearch()
        assertEquals(SearchUiState.Loading, state.searchState)

        val restored = restore(state, loadedSession)

        assertEquals(AppTab.LIBRARY, restored.selectedTab)
        assertEquals(loadedSession, restored.session)
        assertEquals("Kotlin", restored.searchQuery)
        assertNull(restored.libraryFilter)
        assertNull(restored.selectedVideo)
        assertNull(restored.selectedLocalVideo)
        assertTrue(restored.localPlaylist.isEmpty())
        assertEquals(SearchUiState.Idle, restored.searchState)
        assertNull(restored.searchDuration)
        assertNull(restored.searchTimeRange)
        assertFalse(restored.searchGrid)
        assertFalse(restored.homeExpanded)
    }

    @Test
    fun `changing session cancels previous search while preserving navigation and query`() {
        val state = newState(BiliSession("old account", "", "test-old-cookie"))
        state.openLibrary(LocalVideoQuickFilter.RECENT)
        state.updateQuery("Kotlin")
        state.runSearch(order = SearchOrder.MOST_PLAYED)

        state.updateSession(null)

        assertNull(state.session)
        assertEquals(SearchUiState.Idle, state.searchState)
        assertEquals(AppTab.LIBRARY, state.selectedTab)
        assertEquals(LocalVideoQuickFilter.RECENT, state.libraryFilter)
        assertEquals("Kotlin", state.searchQuery)
        assertEquals(SearchOrder.MOST_PLAYED, state.searchOrder)
    }

    private fun newState(session: BiliSession? = null) = BiliBoxAppState(session, scope, ScrollState(0), ScrollState(0))

    private fun restore(state: BiliBoxAppState, session: BiliSession? = null): BiliBoxAppState {
        val saver = BiliBoxAppState.saver(session, scope, ScrollState(0), ScrollState(0))
        val saved = with(saver) { saverScope.save(state) }
        return requireNotNull(saver.restore(requireNotNull(saved)))
    }
}
