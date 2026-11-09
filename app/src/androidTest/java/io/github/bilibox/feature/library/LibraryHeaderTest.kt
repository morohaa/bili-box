package io.github.bilibox.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.bilibox.core.ui.theme.BiliBoxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LibraryHeaderTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun rootSearchRightEdgeStaysFixedDuringPressAndTransitions() = verifySearchEdge(inFolder = false)

    @Test
    fun folderSearchRightEdgeStaysFixedWhileBackButtonDisappears() = verifySearchEdge(inFolder = true)

    private fun verifySearchEdge(inFolder: Boolean) {
        compose.setContent {
            val accent = Color(0xFF29B6F6)
            var searchOpen by remember { mutableStateOf(false) }
            BiliBoxTheme(darkTheme = false, accent = accent, dynamicColor = false) {
                CompositionLocalProvider(LocalLibraryPalette provides libraryPalette(false, accent)) {
                    Box(Modifier.width(320.dp)) {
                        LibraryTopBar(
                            inFolder = inFolder,
                            searchOpen = searchOpen,
                            query = "",
                            breadcrumbs = if (inFolder) listOf("MiShare") else emptyList(),
                            filter = null,
                            onBack = {},
                            onSearchOpen = { searchOpen = true },
                            onSearchClose = { searchOpen = false },
                            onQueryChange = {},
                            onSettings = {},
                            onRoot = {},
                            onBreadcrumb = {},
                            onClearFilter = {},
                            onSelectFilter = {},
                        )
                    }
                }
            }
        }
        val search = compose.onNodeWithTag("library-search-field")
        val right = search.fetchSemanticsNode().boundsInRoot.right
        compose.mainClock.autoAdvance = false
        search.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(120)
        assertEquals("Pressing must not scale the search field", right, search.fetchSemanticsNode().boundsInRoot.right, 1f)
        search.performTouchInput { up() }
        assertRightEdgeDuringTransition(right)
        compose.onNodeWithText("取消").performClick()
        assertRightEdgeDuringTransition(right)
        compose.onNodeWithContentDescription("显示与排序设置").assertExists()
        if (inFolder) compose.onNodeWithContentDescription("返回上一级").assertExists()
        compose.mainClock.autoAdvance = true
    }

    private fun assertRightEdgeDuringTransition(expected: Float) {
        repeat(24) { frame ->
            compose.mainClock.advanceTimeByFrame()
            assertEquals(
                "Search right edge moved at frame $frame",
                expected,
                compose.onNodeWithTag("library-search-field").fetchSemanticsNode().boundsInRoot.right,
                1f,
            )
        }
    }
}
