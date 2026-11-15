package io.github.bilibox.app

import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import io.github.bilibox.core.ui.theme.AppearanceSettings
import io.github.bilibox.core.ui.theme.BackgroundPage
import io.github.bilibox.core.ui.theme.BiliBoxTheme
import io.github.bilibox.data.appearance.BackgroundImageStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AutoHideNavigationTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var state: BiliBoxAppState
    private var appearance by mutableStateOf(AppearanceSettings())

    @Before
    fun setUp() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val scope = rememberCoroutineScope()
            val homeScroll = rememberScrollState()
            val profileScroll = rememberScrollState()
            state = remember {
                BiliBoxAppState(null, scope, homeScroll, profileScroll).apply { selectTab(AppTab.PROFILE.ordinal) }
            }
            DisposableEffect(state) { onDispose { state.dispose() } }
            val context = LocalContext.current
            val stores = remember { BackgroundPage.entries.associateWith { BackgroundImageStore(context, it) } }
            BiliBoxTheme(darkTheme = false, dynamicColor = false) {
                AppNavigation(
                    appState = state,
                    darkMode = false,
                    appearance = appearance,
                    backgroundImages = emptyMap(),
                    backgroundStores = stores,
                    onBackgroundImageChange = { _, _ -> },
                    onAppearanceChange = { appearance = it },
                    onToggleDarkMode = {},
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
    }

    @Test
    fun idleNavigationHidesAndBottomTouchRestoresItWithoutSelectingATab() {
        compose.mainClock.advanceTimeBy(2_000)
        navigation().assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_500)
        navigation().assertDoesNotExist()

        compose.onNodeWithTag("app-bottom-navigation-reveal").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(500)
        navigation().assertIsDisplayed()
        compose.onNodeWithTag("app-bottom-navigation-reveal").assertDoesNotExist()
        compose.runOnIdle { assertEquals(AppTab.PROFILE, state.selectedTab) }

        compose.onNodeWithText("视频").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(AppTab.LIBRARY, state.selectedTab) }
        navigation().assertIsDisplayed()
        compose.mainClock.advanceTimeBy(3_500)
        navigation().assertDoesNotExist()
    }

    @Test
    fun contentTouchRestartsIdleCountdownWithoutBlockingTheTouch() {
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithTag("app-page-transition").performTouchInput { click(Offset(8f, height / 2f)) }
        compose.mainClock.advanceTimeBy(2_000)
        navigation().assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_500)
        navigation().assertDoesNotExist()
    }

    @Test
    fun longTouchKeepsNavigationVisibleUntilThreeSecondsAfterRelease() {
        compose.onNodeWithTag("app-page-transition").performTouchInput { down(Offset(8f, height / 2f)) }
        compose.mainClock.advanceTimeBy(4_000)
        navigation().assertIsDisplayed()
        compose.onNodeWithTag("app-page-transition").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(2_000)
        navigation().assertIsDisplayed()
        compose.mainClock.advanceTimeBy(1_500)
        navigation().assertDoesNotExist()
    }

    @Test
    fun touchingContentAfterHidingDoesNotRestoreNavigation() {
        compose.mainClock.advanceTimeBy(3_500)
        compose.onNodeWithTag("app-page-transition").performTouchInput { click(Offset(8f, height / 2f)) }
        compose.mainClock.advanceTimeBy(500)
        navigation().assertDoesNotExist()
        compose.onNodeWithTag("app-bottom-navigation-reveal").assertIsDisplayed()
    }

    @Test
    fun secondaryPagesHaveNoRevealTargetAndReturningRestartsTheTimer() {
        compose.mainClock.advanceTimeBy(3_500)
        compose.runOnIdle { state.openAppearance() }
        compose.mainClock.advanceTimeBy(3_500)
        navigation().assertDoesNotExist()
        compose.onNodeWithTag("app-bottom-navigation-reveal").assertDoesNotExist()

        compose.runOnIdle { state.closeAppearance() }
        compose.mainClock.advanceTimeBy(500)
        navigation().assertIsDisplayed()
        compose.mainClock.advanceTimeBy(3_500)
        navigation().assertDoesNotExist()
    }

    private fun navigation() = compose.onNodeWithTag("app-bottom-navigation")

    @Test
    fun appearanceSwitchKeepsNavigationVisibleAndTurningItOffRestoresAutoHide() {
        repeat(2) { index ->
            // Let scrolling finish while settings are open; freeze time again for the idle countdown.
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { state.openAppearance() }
            navigation().assertDoesNotExist()
            compose.onNodeWithTag("app-bottom-navigation-reveal").assertDoesNotExist()
            compose.onNodeWithText("固定底部导航栏").performScrollTo().performClick()
            compose.runOnIdle { assertEquals(index == 0, appearance.fixedBottomNavigation) }
            compose.mainClock.autoAdvance = false
            compose.onNodeWithContentDescription("返回").performClick()
            compose.mainClock.advanceTimeBy(500)
            navigation().assertIsDisplayed()
            compose.mainClock.advanceTimeBy(5_000)
            if (index == 0) {
                navigation().assertIsDisplayed()
                compose.onNodeWithTag("app-bottom-navigation-reveal").assertDoesNotExist()
            } else {
                navigation().assertDoesNotExist()
                compose.onNodeWithTag("app-bottom-navigation-reveal").assertIsDisplayed()
            }
        }
    }
}
