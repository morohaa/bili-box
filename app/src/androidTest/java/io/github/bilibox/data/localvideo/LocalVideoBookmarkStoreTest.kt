package io.github.bilibox.data.localvideo

import android.content.Context
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalVideoBookmarkStoreTest {
    private lateinit var preferences: SharedPreferences
    private lateinit var store: LocalVideoBookmarkStore

    @Before
    fun setUp() {
        preferences = InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("bookmark_store_test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        store = LocalVideoBookmarkStore(preferences)
    }

    @After
    fun tearDown() {
        preferences.edit().clear().commit()
    }

    @Test
    fun reopeningRestoresSortedBookmarksAndNotesForEachFileIndependently() {
        val first = LocalVideoBookmark("first", 5_000L, "重点讲解")
        val second = LocalVideoBookmark("second", 20_000L)
        store.save("video-a|100", listOf(second, first))
        store.save("video-b|200", listOf(LocalVideoBookmark("other", 0L, "开头")))
        val reopened = LocalVideoBookmarkStore(preferences)
        assertEquals(listOf(first, second), reopened.load("video-a|100"))
        assertEquals("开头", reopened.load("video-b|200").single().note)
        assertTrue(reopened.load("video-a|999").isEmpty())
        store.save("video-a|100", emptyList())
        assertTrue(reopened.load("video-a|100").isEmpty())
        assertEquals(1, reopened.load("video-b|200").size)
    }

    @Test
    fun corruptOrInvalidEntriesDoNotBreakPlaybackAndUnknownFieldsAreIgnored() {
        preferences.edit().putString("corrupt", "broken-json")
            .putString("valid", """[{"id":"a","positionMs":0,"future":true},{"id":"b","positionMs":-10},{"id":"","positionMs":1},{"id":"a","positionMs":1000}]""")
            .apply()
        assertTrue(store.load("corrupt").isEmpty())
        assertEquals(listOf(LocalVideoBookmark("a", 0)), store.load("valid"))
    }
}
