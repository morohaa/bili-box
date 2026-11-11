package io.github.bilibox.data.online

import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineCacheRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = OnlineCacheRepository(context)
    private val bvid = "BV-offline-test-${UUID.randomUUID()}"
    private val uris = mutableListOf<Uri>()

    @After
    fun cleanUp() {
        uris.forEach { runCatching { context.contentResolver.delete(it, null, null) } }
        File(context.filesDir, "online_cache/$bvid.json").delete()
    }

    @Test
    fun completedMultiPartCacheLoadsOfflineAndDeletesOnlySelectedTrack() = runBlocking {
        val video = publish(false, "第一讲.mp4")
        val audio = publish(true, "第一讲.m4a")
        val second = publish(false, "第二讲.mp4")
        val unrelated = publish(false, "未登记的本地视频.mp4")
        repository.saveIndex(
            OnlineCacheIndex(
                bvid = bvid,
                title = "离线课程",
                author = "测试作者",
                totalParts = 2,
                episodes = listOf(
                    OnlineCacheEpisode(1, 1, "第一讲", video = video, audio = audio, durationMs = 90_000),
                    OnlineCacheEpisode(2, 2, "第二讲", video = second, durationMs = 80_000),
                ),
            )
        )
        val group = repository.loadLibrary().groups.single { it.bvid == bvid }
        assertEquals("离线课程", group.title)
        assertEquals(listOf(1, 1, 2), group.entries.map { it.page })
        assertTrue(group.isCollection)
        assertEquals(90_000L, group.entries.first().media.durationMs)
        assertFalse(group.entries.any { it.id == unrelated.uri })

        val audioEntry = group.entries.single { it.audioOnly }
        assertEquals(0, repository.delete(listOf(audioEntry)))
        val reloaded = OnlineCacheRepository(context).loadLibrary().groups.single { it.bvid == bvid }
        assertEquals(2, reloaded.entries.size)
        assertEquals(video.uri, reloaded.entries.first().id)
        assertEquals(0, repository.delete(reloaded.entries))
        assertFalse(repository.loadLibrary().groups.any { it.bvid == bvid })
        context.contentResolver.query(Uri.parse(unrelated.uri), null, null, null, null)!!.use { assertTrue(it.moveToFirst()) }
    }

    @Test
    fun externalDeletionPrunesOneTrackAndPersistsPruning() = runBlocking {
        val video = publish(false, "外部删除.mp4")
        val audio = publish(true, "保留音频.m4a")
        repository.saveIndex(OnlineCacheIndex(bvid, listOf(OnlineCacheEpisode(1, video = video, audio = audio))))
        context.contentResolver.delete(Uri.parse(video.uri), null, null)
        val loaded = repository.loadIndex(bvid)
        assertEquals(null, loaded.episodes.single().video)
        assertEquals(audio, loaded.episodes.single().audio)
        val persisted = File(context.filesDir, "online_cache/$bvid.json").readText()
        assertFalse(persisted.contains(video.uri))
        assertTrue(persisted.contains(audio.uri))
    }

    private fun publish(audio: Boolean, name: String): OnlineCacheFile {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "${bvid}_$name")
            put(MediaStore.MediaColumns.MIME_TYPE, if (audio) "audio/mp4" else "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (audio) "Music/BiliBox" else "Movies/BiliBox")
        }
        val collection = if (audio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val uri = context.contentResolver.insert(collection, values)!!
        uris += uri
        context.contentResolver.openOutputStream(uri)!!.use { it.write(ByteArray(128)) }
        return OnlineCacheFile(uri.toString(), name, sizeBytes = 128, cachedAt = System.currentTimeMillis())
    }
}
