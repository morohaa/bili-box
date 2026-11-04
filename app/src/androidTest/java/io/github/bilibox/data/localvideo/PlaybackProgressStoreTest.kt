package io.github.bilibox.data.localvideo

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackProgressStoreTest {
    private lateinit var preferences: SharedPreferences
    private lateinit var store: PlaybackProgressStore

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        preferences = context.getSharedPreferences("playback_progress_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        store = PlaybackProgressStore(preferences)
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun reopeningRestoresEachVideoIndependently() {
        store.save("video-a|100", 75_000L, 300_000L, completed = false)
        store.save("video-b|200", 125_000L, 600_000L, completed = false)

        val reopenedStore = PlaybackProgressStore(preferences)
        assertEquals(75_000L, reopenedStore.load("video-a|100", 300_000L))
        assertEquals(125_000L, reopenedStore.load("video-b|200", 600_000L))
        assertEquals(0L, reopenedStore.load("video-a|999", 300_000L))
        assertEquals(0L, reopenedStore.load("unplayed", 300_000L))
    }

    @Test
    fun completionOrSeekingToStartClearsResumePosition() {
        store.save("video", 75_000L, 300_000L, completed = false)
        store.save("video", 300_000L, 300_000L, completed = true)
        assertEquals(0L, store.load("video", 300_000L))

        store.save("video", 75_000L, 300_000L, completed = false)
        store.save("video", 0L, 300_000L, completed = false)
        assertEquals(0L, store.load("video", 300_000L))
    }

    @Test
    fun unknownDurationPreservesProgressButInvalidPositionsDoNotResume() {
        store.save("video", 75_000L, 0L, completed = false)
        assertEquals(75_000L, store.load("video", 0L))
        assertEquals(0L, store.load("video", 60_000L))

        store.save("video", -1L, 300_000L, completed = false)
        assertEquals(0L, store.load("video", 300_000L))
        store.save("video", 300_000L, 300_000L, completed = false)
        assertEquals(0L, store.load("video", 300_000L))
    }
}