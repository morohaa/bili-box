package io.github.bilibox.feature.search

import android.content.Context
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bilibox.app.BiliBoxAppState
import io.github.bilibox.core.model.SearchDuration
import io.github.bilibox.core.model.SearchTimeRange
import io.github.bilibox.core.ui.theme.dynamicColors
import io.github.bilibox.data.auth.BiliSession
import io.github.bilibox.data.search.SearchPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AnonymousSearchTest {
    @get:Rule val compose = createComposeRule()

    private val storage = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("anonymous_search_test", Context.MODE_PRIVATE)
    private val session = BiliSession("account", "", "SESSDATA=fixture")
    private val scope = CoroutineScope(Job().apply { cancel() })

    @Before
    fun setUp() {
        storage.edit().clear().commit()
    }

    @After
    fun tearDown() {
        storage.edit().clear().commit()
    }

    @Test
    fun anonymousModeAppliesExplicitlyAndSurvivesResetAndRecreation() {
        val state = newState()
        state.updateSearchFilters(SearchDuration.TEN_TO_THIRTY, SearchTimeRange.LAST_MONTH)
        compose.setContent {
            SearchControlsBar(
                order = state.searchOrder,
                onOrderChange = {},
                duration = state.searchDuration,
                timeRange = state.searchTimeRange,
                anonymousSearch = state.anonymousSearch,
                onFiltersChange = state::updateSearchFilters,
                gridLayout = false,
                onToggleLayout = {},
                colors = dynamicColors(darkMode = false),
            )
        }

        compose.onNodeWithContentDescription("筛选，已启用").performClick()
        compose.onNodeWithText("匿名搜索").performScrollTo().assertIsOff().performClick()
        compose.onNodeWithContentDescription("关闭筛选").performClick()
        compose.onNodeWithContentDescription("匿名搜索已开启").assertDoesNotExist()
        assertFalse(newState().anonymousSearch)

        compose.onNodeWithContentDescription("筛选，已启用").performClick()
        compose.onNodeWithText("匿名搜索").performScrollTo().assertIsOff().performClick()
        compose.onNodeWithText("应用筛选").performClick()
        compose.onNodeWithContentDescription("匿名搜索已开启").assertIsDisplayed()

        compose.onNodeWithContentDescription("筛选，已启用").performClick()
        compose.onNodeWithText("重置").performClick()
        compose.onNodeWithText("匿名搜索").performScrollTo().assertIsOn()
        compose.onNodeWithText("应用筛选").performClick()
        compose.onNodeWithContentDescription("匿名搜索已开启").assertIsDisplayed()
        compose.runOnIdle {
            assertNull(state.searchDuration)
            assertNull(state.searchTimeRange)
            assertEquals(session, state.session)
            assertTrue(newState().anonymousSearch)
        }

        compose.onNodeWithContentDescription("筛选").performClick()
        compose.onNodeWithText("匿名搜索").performScrollTo().performClick()
        compose.onNodeWithText("应用筛选").performClick()
        compose.onNodeWithContentDescription("匿名搜索已开启").assertDoesNotExist()
        assertFalse(newState().anonymousSearch)
    }

    private fun newState() = BiliBoxAppState(session, scope, ScrollState(0), ScrollState(0), SearchPreferences(storage))
}
