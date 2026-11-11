package io.github.bilibox.app

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bilibox.core.ui.theme.AppearanceSettings
import io.github.bilibox.core.ui.theme.BackgroundPage
import io.github.bilibox.core.ui.theme.BiliBoxTheme
import io.github.bilibox.data.appearance.BackgroundImageStore
import io.github.bilibox.data.online.OnlineCacheEpisode
import io.github.bilibox.data.online.OnlineCacheFile
import io.github.bilibox.data.online.OnlineCacheIndex
import io.github.bilibox.data.online.OnlineCacheRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class OfflineCacheNavigationTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val bvid = "BV-back-test-${UUID.randomUUID()}"
    private var uri: Uri? = null

    @Before
    fun seedCachedSeries() = runBlocking {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$bvid.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/BiliBox")
        }
        val media = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)!!
        uri = media
        context.contentResolver.openOutputStream(media)!!.use { it.write(ByteArray(128)) }
        OnlineCacheRepository(context).saveIndex(
            OnlineCacheIndex(
                bvid = bvid,
                title = "返回测试课程",
                totalParts = 2,
                episodes = listOf(OnlineCacheEpisode(1, 1, "返回测试分集", video = OnlineCacheFile(media.toString(), sizeBytes = 128))),
            )
        )
    }

    @After
    fun cleanUp() {
        uri?.let { context.contentResolver.delete(it, null, null) }
        File(context.filesDir, "online_cache/$bvid.json").delete()
    }

    @Test
    fun phoneBackReturnsFromEpisodesToCacheListThenProfileWithTransitions() {
        lateinit var state: BiliBoxAppState
        compose.setContent {
            val scope = rememberCoroutineScope()
            val homeScroll = rememberScrollState()
            val profileScroll = rememberScrollState()
            state = remember {
                BiliBoxAppState(null, scope, homeScroll, profileScroll).apply { selectTab(AppTab.PROFILE.ordinal) }
            }
            DisposableEffect(state) { onDispose { state.dispose() } }
            val appContext = LocalContext.current
            val stores = remember { BackgroundPage.entries.associateWith { BackgroundImageStore(appContext, it) } }
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
        compose.onNodeWithText("离线缓存").performClick()
        finishTransition()
        compose.waitUntil(5_000) {
            compose.mainClock.advanceTimeBy(16)
            compose.onAllNodesWithText("返回测试课程").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("返回测试课程").performClick()
        finishTransition()
        compose.onNodeWithText("P1  返回测试分集").assertIsDisplayed()
        compose.onNodeWithText("返回测试课程").assertDoesNotExist()
        compose.onNodeWithText("已缓存分集").assertDoesNotExist()

        pressBack()
        finishTransition()
        compose.onNodeWithText("返回测试课程").assertIsDisplayed()
        compose.onNodeWithText("P1  返回测试分集").assertDoesNotExist()
        compose.runOnIdle { assertTrue(state.offlineCacheOpen) }

        pressBack()
        finishTransition()
        compose.runOnIdle { assertFalse(state.offlineCacheOpen) }
        compose.onNodeWithText("离线缓存").assertIsDisplayed()
    }

    private fun finishTransition() {
        compose.waitUntil(5_000) {
            compose.mainClock.advanceTimeBy(350)
            compose.onNodeWithTag("app-page-transition").fetchSemanticsNode()
                .config[SemanticsProperties.StateDescription] == "页面就绪"
        }
    }
}
