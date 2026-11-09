package io.github.bilibox.core.ui.component

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import io.github.bilibox.core.util.RemoteImageCache
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RemoteCoverTest {
    @get:Rule val compose = createComposeRule()
    private val caches = mutableListOf<RemoteImageCache>()

    @After
    fun clearCaches() {
        caches.forEach { it.clear() }
    }

    @Test
    fun scrollingAwayAndBackUsesCachedBitmapWithoutAnotherRequest() {
        val calls = ConcurrentHashMap<String, AtomicInteger>()
        val cache = cache(imageBytes()) { url -> calls.computeIfAbsent(url) { AtomicInteger() }.incrementAndGet() }
        val first = "https://images.test/0"
        compose.setContent {
            LazyColumn(Modifier.height(160.dp).testTag("covers")) {
                items((0..60).toList(), key = { it }) { index ->
                    RemoteCover(
                        url = "https://images.test/$index",
                        modifier = Modifier.fillMaxWidth().height(100.dp).testTag("cover-$index"),
                        imageCache = cache,
                        placeholder = { Box(Modifier.fillMaxSize().testTag("placeholder-$index")) },
                    )
                }
            }
        }
        compose.waitUntil(5000) { cache.cached(first) != null }
        compose.onNodeWithTag("covers").performScrollToIndex(40)
        compose.onNodeWithTag("cover-0").assertDoesNotExist()
        compose.onNodeWithTag("covers").performScrollToIndex(0)
        compose.onNodeWithTag("cover-0").assertExists()
        compose.onNodeWithTag("placeholder-0").assertDoesNotExist()
        assertEquals(1, calls.getValue(first).get())
    }

    @Test
    fun duplicateConsumersShareRequestAndLeavingCompositionDoesNotDiscardResult() {
        val started = AtomicInteger()
        val release = CountDownLatch(1)
        val cache = cache(imageBytes()) {
            started.incrementAndGet()
            check(release.await(5, TimeUnit.SECONDS))
        }
        val url = "https://images.test/shared"
        var mounted by mutableStateOf(true)
        compose.setContent {
            Column {
                if (mounted) {
                    repeat(2) {
                        RemoteCover(url, Modifier.fillMaxWidth().height(100.dp), imageCache = cache)
                    }
                }
            }
        }
        try {
            compose.waitUntil(5000) { started.get() > 0 }
            compose.runOnIdle { mounted = false }
            compose.waitForIdle()
        } finally {
            release.countDown()
        }
        compose.waitUntil(5000) { cache.cached(url) != null }
        compose.runOnIdle { mounted = true }
        compose.waitForIdle()
        assertEquals(1, started.get())
    }

    @Test
    fun largeImagesAreSampledAndLeastRecentlyUsedImagesAreEvicted() = runBlocking<Unit> {
        val calls = AtomicInteger()
        val cache = cache(imageBytes(1536, 1024), maxBytes = 512 * 1024) { calls.incrementAndGet() }
        val first = "https://images.test/first"
        val bitmap = requireNotNull(cache.load(first))
        assertTrue(bitmap.width <= 640 && bitmap.height <= 640)
        assertTrue(bitmap.allocationByteCount <= 512 * 1024)
        assertNotNull(cache.load("https://images.test/second"))
        assertNull(cache.cached(first))
        assertNotNull(cache.load(first))
        assertEquals(3, calls.get())
        cache.clear()
        assertNull(cache.cached(first))
    }

    private fun cache(bytes: ByteArray, maxBytes: Int = 32 * 1024 * 1024, beforeResponse: (String) -> Unit): RemoteImageCache {
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            beforeResponse(chain.request().url.toString())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(bytes.toResponseBody()).build()
        }.build()
        return RemoteImageCache(http, maxBytes).also { caches += it }
    }

    private fun imageBytes(width: Int = 96, height: Int = 64): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        return try {
            ByteArrayOutputStream().use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
