package io.github.bilibox.feature.player

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SeekPreviewSessionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun disabledPreviewDoesNotPrewarmAndTurningOffReleasesSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = createSeekPreviewFixture(context)
        val opens = AtomicInteger()
        val factory = DataSource.Factory {
            val file = FileDataSource()
            object : DataSource by file {
                override fun open(dataSpec: DataSpec): Long {
                    opens.incrementAndGet()
                    return file.open(dataSpec.withUri(Uri.fromFile(fixture)))
                }
            }
        }
        val source = PlayerPreviewSource(MediaItem.fromUri("https://preview.test/video.mp4"), factory)
        var enabled by mutableStateOf(false)
        var preview: PlayerSeekPreviewController? = null
        compose.setContent {
            val main = remember { ExoPlayer.Builder(context).build() }
            DisposableEffect(main) { onDispose { main.release() } }
            preview = rememberPlayerSeekPreview(main, source, enabled)
        }
        compose.runOnIdle {
            assertNull(preview)
            assertEquals(0, opens.get())
            enabled = true
        }
        compose.waitUntil(5_000) { opens.get() > 0 }
        var previous: PlayerSeekPreviewController? = null
        compose.runOnIdle {
            previous = preview
            assertTrue(previous!!.networkPlayer != null)
            enabled = false
        }
        compose.runOnIdle {
            assertNull(preview)
            assertNull(previous!!.networkPlayer)
            enabled = true
        }
        compose.runOnIdle {
            assertTrue(preview != null)
            assertNotSame(previous, preview)
        }
    }

    @Test
    fun localCacheDisplaysImmediatelyAndOldResultsCannotOverwriteLatestTarget() {
        val source = PlayerPreviewSource(MediaItem.fromUri("file:///preview.mp4"))
        val preview = PlayerSeekPreviewController(source)
        val first = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888)
        val second = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888)
        preview.request(1_000L)
        preview.accept(1_000L, first)
        preview.request(2_000L)
        assertSame("等待新图时保留已显示的画面", first, preview.frame)
        preview.accept(2_000L, second)
        preview.request(1_000L)
        assertSame("缓存命中不需要等待后台解码", first, preview.frame)
        preview.accept(2_000L, second)
        assertSame("过时结果不得覆盖最新目标的缓存图", first, preview.frame)
        assertEquals(1_000L, preview.requests.tryReceive().getOrThrow())
    }

    @Test
    fun networkSessionWarmsBeforeDragAndSurvivesPopupReopen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = createSeekPreviewFixture(context)
        // 走远程预览的代码路径，但用本地测试流避免网络造成计时抖动。
        val factory = DataSource.Factory {
            val file = FileDataSource()
            object : DataSource by file {
                override fun open(dataSpec: DataSpec): Long = file.open(dataSpec.withUri(Uri.fromFile(fixture)))
            }
        }
        val source = PlayerPreviewSource(MediaItem.fromUri("https://preview.test/video.mp4"), factory)
        var visible by mutableStateOf(false)
        var preview: PlayerSeekPreviewController? = null
        compose.setContent {
            val main = remember { ExoPlayer.Builder(context).build() }
            DisposableEffect(main) { onDispose { main.release() } }
            val session = rememberPlayerSeekPreview(main, source)!!
            preview = session
            Box(Modifier.padding(top = 120.dp)) {
                if (visible) PlayerSeekPreview(session, 20_000L, Modifier.size(136.dp, 98.5.dp))
            }
        }
        compose.waitUntil(5_000) {
            var ready = false
            compose.runOnIdle { ready = preview?.networkPlayer?.playbackState == Player.STATE_READY }
            ready
        }
        var originalPlayer: ExoPlayer? = null
        compose.runOnIdle {
            originalPlayer = preview!!.networkPlayer
            preview!!.request(20_000L)
            visible = true
        }
        compose.waitUntil(2_000) {
            var rendered = false
            compose.runOnIdle { rendered = preview!!.networkRendered }
            rendered
        }
        compose.onNodeWithText("加载中…").assertDoesNotExist()
        compose.runOnIdle {
            preview!!.request(10_000L)
            assertTrue("切换目标时保持已有帧可见", preview!!.networkRendered)
            visible = false
        }
        compose.onNodeWithTag("seek-preview").assertDoesNotExist()
        compose.runOnIdle {
            assertSame(originalPlayer, preview!!.networkPlayer)
            assertTrue(!originalPlayer!!.playWhenReady && originalPlayer!!.volume == 0f)
            visible = true
        }
        compose.onNodeWithTag("seek-preview").assertExists()
        compose.onNodeWithText("加载中…").assertDoesNotExist()
    }
}
