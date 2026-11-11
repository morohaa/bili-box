package io.github.bilibox.app

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import io.github.bilibox.core.ui.theme.AppearanceSettings
import io.github.bilibox.core.ui.theme.BackgroundPage
import io.github.bilibox.core.ui.theme.BiliBoxTheme
import io.github.bilibox.data.appearance.BackgroundImageStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppNavigationTransitionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun tabsAppearanceAndBannerAnimateBothWaysAndPreserveSearchQuery() {
        lateinit var state: BiliBoxAppState
        compose.setContent {
            val scope = rememberCoroutineScope()
            val homeScroll = rememberScrollState()
            val profileScroll = rememberScrollState()
            state = remember { BiliBoxAppState(null, scope, homeScroll, profileScroll) }
            DisposableEffect(state) { onDispose { state.dispose() } }
            val context = LocalContext.current
            val stores = remember { BackgroundPage.entries.associateWith { BackgroundImageStore(context, it) } }
            BiliBoxTheme(darkTheme = false, dynamicColor = false) {
                AppNavigation(
                    appState = state,
                    darkMode = false,
                    appearance = AppearanceSettings(),
                    backgroundImages = emptyMap(),
                    backgroundStores = stores,
                    onBackgroundImageChange = { _, _ -> },
                    onAppearanceChange = {},
                    onToggleDarkMode = {},
                )
            }
        }
        compose.mainClock.autoAdvance = false
        switchTabWithStationaryNavigation("我的")
        compose.runOnIdle {
            assertEquals(AppTab.PROFILE, state.selectedTab)
            state.updateQuery("Kotlin")
        }

        compose.onNodeWithText("主题外观").performClick()
        compose.mainClock.advanceTimeBy(48)
        assertTransitioning()
        finishTransition()
        compose.runOnIdle { assertTrue(state.appearanceOpen) }
        compose.onNodeWithTag("app-bottom-navigation").assertDoesNotExist()
        compose.onNodeWithContentDescription("返回").performClick()
        finishTransition()
        compose.runOnIdle {
            assertFalse(state.appearanceOpen)
            assertEquals(AppTab.PROFILE, state.selectedTab)
        }

        compose.onNodeWithContentDescription("预览头图").performClick()
        finishTransition()
        compose.runOnIdle { assertTrue(state.bannerPreviewOpen) }
        compose.onNodeWithTag("app-bottom-navigation").assertDoesNotExist()
        pressBack()
        finishTransition()
        compose.runOnIdle { assertFalse(state.bannerPreviewOpen) }

        compose.onNodeWithText("离线缓存").performClick()
        finishTransition()
        compose.runOnIdle { assertTrue(state.offlineCacheOpen) }
        compose.onNodeWithTag("app-bottom-navigation").assertDoesNotExist()
        compose.onNodeWithText("已缓存视频").assertDoesNotExist()
        compose.onNodeWithContentDescription("搜索缓存").assertExists()
        pressBack()
        finishTransition()
        compose.runOnIdle {
            assertFalse(state.offlineCacheOpen)
            assertEquals(AppTab.PROFILE, state.selectedTab)
        }

        switchTabWithStationaryNavigation("视频")
        compose.onNodeWithTag("library-search-field").assertExists()
        // Returning to an existing search intentionally hides the navigation bar.
        compose.onNodeWithText("首页").performClick()
        finishTransition()
        compose.runOnIdle {
            assertEquals(AppTab.HOME, state.selectedTab)
            assertEquals("Kotlin", state.searchQuery)
        }
    }

    private fun switchTabWithStationaryNavigation(title: String) {
        val bounds = compose.onNodeWithTag("app-bottom-navigation").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithText(title).performClick()
        compose.mainClock.advanceTimeBy(48)
        assertTransitioning()
        repeat(8) {
            compose.mainClock.advanceTimeByFrame()
            assertEquals("页面切换期间底部导航应保持原位", bounds, compose.onNodeWithTag("app-bottom-navigation").fetchSemanticsNode().boundsInRoot)
        }
        finishTransition()
    }

    private fun assertTransitioning() {
        compose.onNodeWithTag("app-page-transition")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "页面切换中"))
    }

    private fun finishTransition() {
        compose.waitUntil(5_000) {
            compose.mainClock.advanceTimeBy(350)
            compose.onNodeWithTag("app-page-transition").fetchSemanticsNode()
                .config[SemanticsProperties.StateDescription] == "页面就绪"
        }
        compose.onNodeWithTag("app-page-transition")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "页面就绪"))
    }
}
