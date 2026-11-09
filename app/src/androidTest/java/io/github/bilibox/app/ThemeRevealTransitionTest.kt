package io.github.bilibox.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.bilibox.feature.profile.ThemeToggle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ThemeRevealTransitionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun bothThemesSpreadFromButtonAndIgnoreRepeatedClicks() {
        var dark by mutableStateOf(false)
        var toggles = 0
        compose.setContent {
            // 根容器有偏移时，按钮坐标仍须正确转换。
            Box(Modifier.padding(20.dp).size(240.dp, 360.dp).testTag("reveal")) {
                ThemeRevealTransition(onToggle = {
                    dark = !dark
                    toggles++
                }) { toggle ->
                    Box(Modifier.fillMaxSize().background(if (dark) Color.Black else Color.White)) {
                        ThemeToggle(dark, toggle, Modifier.align(Alignment.TopEnd))
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        repeat(2) { direction ->
            val before = if (direction == 0) Color.White else Color.Black
            val after = if (direction == 0) Color.Black else Color.White
            compose.onNodeWithContentDescription(if (direction == 0) "切换深色模式" else "切换浅色模式").performClick()
            compose.waitUntil(5_000) {
                compose.mainClock.advanceTimeByFrame()
                toggles == direction + 1
            }
            compose.mainClock.advanceTimeBy(160)
            val image = compose.onNodeWithTag("reveal").captureToImage().toPixelMap()
            // 按钮附近已变色，远端仍是旧主题，排除整屏淡入或瞬间切换。
            assertEquals(after, image[(image.width * .8f).toInt(), (image.height * .2f).toInt()])
            assertEquals(before, image[4, image.height - 5])
            compose.onNodeWithContentDescription(if (direction == 0) "切换浅色模式" else "切换深色模式").performClick()
            compose.mainClock.advanceTimeBy(700)
            compose.runOnIdle { assertEquals(direction + 1, toggles) }
            val finished = compose.onNodeWithTag("reveal").captureToImage().toPixelMap()
            assertEquals(after, finished[4, finished.height - 5])
        }
    }
}
