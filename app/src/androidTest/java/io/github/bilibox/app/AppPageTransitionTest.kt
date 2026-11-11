package io.github.bilibox.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppPageTransitionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var transition: AppPageTransitionState

    @Test
    fun forwardAndBackKeepContentAcrossEveryFrameWithoutOverlappingPageLifetimes() {
        var page by mutableIntStateOf(0)
        var livePages = 0
        var maximumLivePages = 0
        var changes = 0
        val instances = mutableMapOf<Int, Any>()
        compose.setContent {
            transition = rememberAppPageTransitionState()
            AppPageTransition(transition, Color.White) {
                androidx.compose.runtime.key(page) {
                    val id = remember { Any() }
                    DisposableEffect(page) {
                        instances[page] = id
                        livePages++
                        maximumLivePages = maxOf(maximumLivePages, livePages)
                        onDispose { livePages-- }
                    }
                    Box(
                        Modifier.fillMaxSize().background(if (page == 0) Color.Red else Color.Blue)
                            .testTag("page-$page")
                            .clickable {
                                val navigate = {
                                    changes++
                                    page = 1 - page
                                }
                                if (page == 0) transition.forward(navigate) else transition.back(navigate)
                            },
                    ) { Text("页面 $page") }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        repeat(2) { step ->
            compose.onNodeWithTag("page-$step").performClick()
            compose.waitUntil(5_000) {
                compose.mainClock.advanceTimeByFrame()
                changes == step + 1
            }
            compose.runOnIdle {
                assertTrue(transition.running)
                assertTrue(if (step == 0) transition.offsetDp > 0f else transition.offsetDp < 0f)
                assertEquals(1, livePages)
            }
            compose.onNodeWithText("页面 $step").assertDoesNotExist()
            compose.onNodeWithText("页面 ${1 - step}").assertExists()
            repeat(10) {
                compose.mainClock.advanceTimeByFrame()
                val pixels = compose.onNodeWithTag("app-page-transition").captureToImage().toPixelMap()
                // The page colors have no green; any white background leak adds green.
                listOf(1, pixels.width / 2, pixels.width - 2).forEach { x ->
                    assertTrue("切换中不能露出白底", pixels[x, pixels.height / 2].green < .03f)
                    assertEquals(1f, pixels[x, pixels.height / 2].alpha, .01f)
                }
            }
            compose.mainClock.advanceTimeBy(400)
            compose.runOnIdle {
                assertFalse(transition.running)
                assertEquals(1f, transition.progress.value, 0f)
                assertEquals(0f, transition.offsetDp, 0f)
            }
        }
        compose.runOnIdle {
            assertEquals(1, maximumLivePages)
            assertEquals(2, instances.size)
        }
    }

    @Test
    fun repeatedTapsAndNavigationRequestsCannotOpenMultiplePages() {
        var changes = 0
        compose.setContent {
            transition = rememberAppPageTransitionState()
            AppPageTransition(transition, Color.White) {
                Box(
                    Modifier.fillMaxSize().testTag("navigate").clickable {
                        transition.forward { changes++ }
                    },
                )
            }
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("navigate").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("navigate").performTouchInput { click(center) }
        compose.runOnIdle { transition.forward { changes++ } }
        compose.waitUntil(5_000) {
            compose.mainClock.advanceTimeBy(500)
            !transition.running
        }
        compose.runOnIdle {
            assertEquals(1, changes)
            assertFalse(transition.running)
        }
    }

    @Test
    fun unavailableSnapshotStillNavigatesWithoutLeavingContentHidden() {
        var changes = 0
        compose.setContent {
            transition = rememberAppPageTransitionState()
            AppPageTransition(transition, Color.White) { Text("页面内容") }
        }
        compose.runOnIdle {
            transition.captureFrame = { error("Unavailable frame") }
            transition.forward { changes++ }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, changes)
            assertFalse(transition.running)
            assertEquals(null, transition.previousFrame)
        }
        compose.onNodeWithText("页面内容").assertExists()
    }

    @Test
    fun tabsUseTheirOrderForDirectionAndReselectStillRunsItsAction() {
        var changes = 0
        compose.setContent {
            transition = rememberAppPageTransitionState()
            AppPageTransition(transition, Color.White) { Text("标签页") }
        }
        compose.mainClock.autoAdvance = false
        val routes = listOf(AppTab.HOME to AppTab.LIBRARY, AppTab.PROFILE to AppTab.HOME)
        routes.forEach { (from, to) ->
            val expectedChanges = changes + 1
            compose.runOnIdle { transition.tab(from, to) { changes++ } }
            compose.waitUntil(5_000) {
                compose.mainClock.advanceTimeByFrame()
                changes == expectedChanges
            }
            compose.mainClock.advanceTimeBy(80)
            compose.runOnIdle {
                assertTrue(if (to.ordinal > from.ordinal) transition.offsetDp > 0f else transition.offsetDp < 0f)
                assertTrue(kotlin.math.abs(transition.offsetDp) <= 18f)
            }
            compose.mainClock.advanceTimeBy(400)
        }
        compose.runOnIdle {
            transition.tab(AppTab.LIBRARY, AppTab.LIBRARY) { changes++ }
            assertEquals(3, changes)
            assertFalse(transition.running)
        }
    }
}
