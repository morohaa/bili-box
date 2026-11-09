package io.github.bilibox.feature.appearance

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bilibox.core.ui.theme.AppearanceSettings
import io.github.bilibox.core.ui.theme.BackgroundPage
import io.github.bilibox.core.ui.theme.BiliBoxTheme
import io.github.bilibox.core.ui.theme.HomeBackground
import io.github.bilibox.core.ui.theme.PageBackgroundSettings
import io.github.bilibox.data.appearance.BackgroundImageStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppearanceScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun eachBackgroundRowUpdatesOnlyItsOwnPage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val stores = BackgroundPage.entries.associateWith { BackgroundImageStore(context, it) }
        var settings by mutableStateOf(AppearanceSettings())
        compose.setContent {
            BiliBoxTheme {
                AppearanceScreen(
                    appearance = settings,
                    darkMode = false,
                    backgroundImages = emptyMap(),
                    backgroundStores = stores,
                    onBackgroundImageChange = { _, _ -> },
                    onBack = {},
                    onChange = { settings = it },
                )
            }
        }
        listOf("视频背景", "我的背景", "首页背景").forEachIndexed { index, label ->
            compose.onNodeWithText(label).performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.onNodeWithText("渐变").assertExists()
            compose.onNode(hasText("自定义") and !hasContentDescription("自定义主题")).assertExists()
            compose.onNodeWithText("纯色").performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.runOnIdle {
                assertEquals(HomeBackground.SOLID, settings.videoBackground.style)
                assertEquals(if (index >= 1) HomeBackground.SOLID else HomeBackground.GRADIENT, settings.profileBackground.style)
                assertEquals(if (index >= 2) HomeBackground.SOLID else HomeBackground.GRADIENT, settings.background)
            }
        }
        compose.onNodeWithText("视频背景").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.onNodeWithText("渐变").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.runOnIdle {
            assertEquals(HomeBackground.GRADIENT, settings.videoBackground.style)
            assertEquals(HomeBackground.SOLID, settings.profileBackground.style)
            assertEquals(HomeBackground.SOLID, settings.background)
        }
    }

    @Test
    fun customWallpaperUsesSelectedPagesImageAndAdjustments() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(base.cacheDir, "appearance-ui-test-${System.nanoTime()}")
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
        }
        val stores = BackgroundPage.entries.associateWith { BackgroundImageStore(context, it) }
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val image = bitmap.asImageBitmap()
        val original = AppearanceSettings(
            background = HomeBackground.SOLID,
            videoBackground = PageBackgroundSettings(HomeBackground.CUSTOM, 17, 4),
            profileBackground = PageBackgroundSettings(HomeBackground.SOLID, 38, 11),
        )
        var settings by mutableStateOf(original)
        var changedPage: BackgroundPage? = null
        compose.setContent {
            BiliBoxTheme {
                AppearanceScreen(
                    appearance = settings,
                    darkMode = false,
                    backgroundImages = mapOf(BackgroundPage.PROFILE to image),
                    backgroundStores = stores,
                    onBackgroundImageChange = { page, _ -> changedPage = page },
                    onBack = {},
                    onChange = { settings = it },
                )
            }
        }
        try {
            compose.onNodeWithText("我的背景").performScrollTo().performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.onNode(hasText("自定义") and !hasContentDescription("自定义主题")).performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.onNodeWithText("壁纸预览").assertExists()
            compose.onNodeWithText("应用").performSemanticsAction(SemanticsActions.OnClick) { it() }
            compose.waitUntil(5_000) { changedPage != null }
            compose.runOnIdle {
                assertEquals(BackgroundPage.PROFILE, changedPage)
                assertEquals(original.background, settings.background)
                assertEquals(original.videoBackground, settings.videoBackground)
                assertEquals(PageBackgroundSettings(HomeBackground.CUSTOM, 38, 11), settings.profileBackground)
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
