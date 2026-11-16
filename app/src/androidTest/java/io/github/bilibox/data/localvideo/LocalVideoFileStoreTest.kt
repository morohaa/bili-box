package io.github.bilibox.data.localvideo

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bilibox.feature.player.createSeekPreviewFixture
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class LocalVideoFileStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private val store = LocalVideoFileStore(context)
    private val uris = mutableListOf<Uri>()
    private val folder = "Movies/BiliBoxFileTest-${UUID.randomUUID()}"

    @After
    fun cleanUp() {
        uris.forEach { runCatching { resolver.delete(it, null, null) } }
    }

    @Test
    fun renameKeepsContentsUriExtensionAndPlaybackProgress() = runBlocking {
        val video = publish("原名.MP4")
        val before = resolver.openInputStream(video.uri)!!.use { it.readBytes() }
        val preferences = context.getSharedPreferences("file_actions_progress_test", 0)
        val progress = PlaybackProgressStore(preferences)
        try {
            progress.save(video.key, 8_000, video.durationMs, false)
            assertEquals(LocalVideoFileResult.Done("【P1】新名字.MP4"), store.rename(video, " 【P1】新名字 "))
            resolver.query(video.uri, arrayOf(MediaStore.Video.Media.DISPLAY_NAME), null, null, null)!!.use {
                assertTrue(it.moveToFirst())
                assertEquals("【P1】新名字.MP4", it.getString(0))
            }
            assertArrayEquals(before, resolver.openInputStream(video.uri)!!.use { it.readBytes() })
            assertEquals(8_000L, progress.load(video.copy(name = "【P1】新名字.MP4").key, video.durationMs))
        } finally {
            preferences.edit().clear().commit()
        }
    }

    @Test
    fun duplicateNameDoesNotOverwriteEitherVideo() = runBlocking {
        val first = publish("第一集.mp4")
        val second = publish("第二集.mp4")
        val failure = runCatching { store.rename(first, "第二集") }.exceptionOrNull()
        assertTrue(failure is IOException)
        assertEquals("这个文件夹中已有同名视频", failure?.message)
        for (video in listOf(first, second)) {
            resolver.query(video.uri, arrayOf(MediaStore.Video.Media.DISPLAY_NAME), null, null, null)!!.use {
                assertTrue(it.moveToFirst())
                assertEquals(video.name, it.getString(0))
            }
        }
    }

    @Test
    fun deleteRemovesOnlyTheSelectedFileAndCanFinishAfterSystemDeletion() = runBlocking {
        val first = publish("删除.mp4")
        val second = publish("保留.mp4")
        assertEquals(LocalVideoFileResult.Done(), store.delete(first))
        assertEquals(LocalVideoFileResult.Done(), store.delete(first))
        resolver.query(first.uri, null, null, null, null)!!.use { assertFalse(it.moveToFirst()) }
        resolver.openInputStream(second.uri)!!.use { assertTrue(it.read() >= 0) }
    }

    @Test
    fun invalidNamesAreRejectedBeforeTouchingTheFile() = runBlocking {
        val video = publish("原名.mp4")
        for (name in listOf("", "..", "../新名", "目录/视频", "视频?", "视频.", "课".repeat(90))) {
            assertNotNull(localVideoNameError(name, "mp4"))
            assertTrue(runCatching { store.rename(video, name) }.exceptionOrNull() is IOException)
        }
        assertNull(localVideoNameError("【P1】课程第一讲", "mp4"))
    }

    private fun publish(name: String): LocalVideo {
        val fixture = createSeekPreviewFixture(context)
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, folder)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)!!
        uris += uri
        resolver.openOutputStream(uri)!!.use { output -> fixture.inputStream().use { it.copyTo(output) } }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
        return LocalVideo(uri, name, 30_000, fixture.length(), relativePath = folder)
    }
}
