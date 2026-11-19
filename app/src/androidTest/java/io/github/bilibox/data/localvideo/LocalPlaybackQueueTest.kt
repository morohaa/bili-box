package io.github.bilibox.data.localvideo

import android.content.Context
import android.net.Uri
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bilibox.core.playback.PlaybackMode
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
            queue.mode = PlaybackMode.SINGLE_LOOP
            assertEquals(androidx.media3.common.Player.REPEAT_MODE_ONE, queue.player.repeatMode)
            queue.mode = PlaybackMode.LIST_LOOP
            assertEquals(androidx.media3.common.Player.REPEAT_MODE_OFF, queue.player.repeatMode)
        }
    }

    @Test
    fun sequentialPlaybackStopsAtTheEndWhileListLoopWraps() {
        val directory = File(context.cacheDir, "queue-eof-test").apply { mkdirs() }
        try {
            val first = silentClip(directory, "first")
            val last = silentClip(directory, "last")
            val stopped = CountDownLatch(1)
            val finished = CountDownLatch(1)
            val wrapped = CountDownLatch(1)
            val changes = mutableListOf<LocalVideo>()
            instrumentation.runOnMainSync {
                queue.playlist = listOf(first, last)
                queue.mode = PlaybackMode.STOP_AFTER
                queue.onCurrentChanged = { changes += it }
                queue.player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state != Player.STATE_ENDED || queue.player.playbackState != Player.STATE_ENDED) return
                        if (queue.current == first) stopped.countDown()
                        if (queue.current == last) finished.countDown()
                    }
                })
                queue.open(first)
            }
            assertTrue("Stop after playback should not advance to the next clip", stopped.await(10, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                assertTrue(changes.isEmpty())
                queue.mode = PlaybackMode.SEQUENTIAL
                queue.player.seekTo(0L)
                queue.player.play()
            }
            assertTrue("Sequential playback should reach the end of the last clip", finished.await(10, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                assertEquals(listOf(last), changes)
                assertEquals(Player.STATE_ENDED, queue.player.playbackState)
                queue.mode = PlaybackMode.LIST_LOOP
                queue.onCurrentChanged = { video ->
                    if (video == first) {
                        queue.player.pause()
                        wrapped.countDown()
                    }
                }
                queue.player.seekTo(0L)
                queue.player.play()
            }
            assertTrue("List loop should wrap from the last clip to the first", wrapped.await(10, TimeUnit.SECONDS))
        } finally {
            instrumentation.runOnMainSync { queue.player.stop() }
            directory.deleteRecursively()
        }
    }

    private fun silentClip(directory: File, name: String): LocalVideo {
        val sampleRate = 8_000
        val audioBytes = sampleRate
        val wave = ByteBuffer.allocate(44 + audioBytes).order(ByteOrder.LITTLE_ENDIAN)
        wave.put("RIFF".toByteArray()).putInt(36 + audioBytes).put("WAVEfmt ".toByteArray())
            .putInt(16).putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2)
            .putShort(2).putShort(16).put("data".toByteArray()).putInt(audioBytes)
        val file = File(directory, "$name.wav").apply { writeBytes(wave.array()) }
        return LocalVideo(uri = Uri.fromFile(file), name = file.name, durationMs = 500L, sizeBytes = file.length())
    }
}
