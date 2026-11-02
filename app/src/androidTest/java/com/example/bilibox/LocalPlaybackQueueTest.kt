package com.example.bilibox

import android.content.Context
import android.net.Uri
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalPlaybackQueueTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val preferences = context.getSharedPreferences("local_queue_test", Context.MODE_PRIVATE)
    private lateinit var queue: LocalPlaybackQueue

    private fun video(name: String) = LocalVideo(
        uri = Uri.parse("content://media/external/video/media/${name.hashCode()}"),
        name = name,
        durationMs = 6_000L,
        sizeBytes = 1_000L,
    )

    private val a = video("a.mp4")
    private val b = video("b.mp4")
    private val c = video("c.mp4")

    @Before
    fun setUp() {
        preferences.edit().clear().commit()
        instrumentation.runOnMainSync {
            queue = LocalPlaybackQueue(context, ExoPlayer.Builder(context).build(), PlaybackProgressStore(preferences), a)
            queue.playlist = listOf(a, b, c)
        }
    }

    @After
    fun tearDown() {
        instrumentation.runOnMainSync { queue.release() }
        preferences.edit().clear().commit()
    }

    @Test
    fun skipToNextSwitchesTheSamePlayerWithoutTheUi() {
        val changes = mutableListOf<LocalVideo>()
        instrumentation.runOnMainSync {
            val player = queue.player
            queue.onCurrentChanged = { changes += it }
            queue.open(a)
            assertTrue(queue.hasNext)
            queue.skipToNext()
            assertSame(player, queue.player)
            assertEquals(b.uri, queue.current.uri)
            assertEquals(b.uri, player.currentMediaItem?.localConfiguration?.uri)
            queue.skipToNext()
            assertEquals(c.uri, queue.current.uri)
            // Folder order ends at the last file; only the end-of-file loop wraps.
            assertFalse(queue.hasNext)
            queue.skipToNext()
            assertEquals(c.uri, queue.current.uri)
        }
        assertEquals(listOf(b, c), changes)
    }

    @Test
    fun openIsANoOpWhenTheQueueIsAlreadyOnThatFile() {
        instrumentation.runOnMainSync {
            queue.open(a)
            queue.skipToNext()
            val item = queue.player.currentMediaItem
            // The UI catching up after a background switch must not restart b.
            queue.open(b)
            assertSame(item, queue.player.currentMediaItem)
        }
    }

    @Test
    fun singleLoopModeSetsRepeatOne() {
        instrumentation.runOnMainSync {
            queue.mode = LocalQueueMode.SINGLE_LOOP
            assertEquals(androidx.media3.common.Player.REPEAT_MODE_ONE, queue.player.repeatMode)
            queue.mode = LocalQueueMode.LIST_LOOP
            assertEquals(androidx.media3.common.Player.REPEAT_MODE_OFF, queue.player.repeatMode)
        }
    }
}
