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
        compose.onNodeWithText("我的").performClick()
        compose.mainClock.advanceTimeBy(48)
        assertTransitioning()
        finishTransition()
        compose.runOnIdle {
            assertEquals(AppTab.PROFILE, state.selectedTab)
            state.updateQuery("Kotlin")
        }

        compose.onNodeWithText("主题外观").performClick()
        compose.mainClock.advanceTimeBy(48)
        assertTransitioning()
        finishTransition()
        compose.runOnIdle { assertTrue(state.appearanceOpen) }
        compose.onNodeWithContentDescription("返回").performClick()
        finishTransition()
        compose.runOnIdle {
            assertFalse(state.appearanceOpen)
            assertEquals(AppTab.PROFILE, state.selectedTab)
        }

        compose.onNodeWithContentDescription("预览头图").performClick()
        finishTransition()
        compose.runOnIdle { assertTrue(state.bannerPreviewOpen) }
        pressBack()
        finishTransition()
        compose.runOnIdle { assertFalse(state.bannerPreviewOpen) }

        compose.onNodeWithText("视频").performClick()
        finishTransition()
        compose.onNodeWithTag("library-search-field").assertExists()
        compose.onNodeWithText("首页").performClick()
        finishTransition()
        compose.runOnIdle {
            assertEquals(AppTab.HOME, state.selectedTab)
            assertEquals("Kotlin", state.searchQuery)
        }
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
