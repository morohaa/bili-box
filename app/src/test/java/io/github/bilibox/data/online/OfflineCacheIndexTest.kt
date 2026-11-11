package io.github.bilibox.data.online

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OfflineCacheIndexTest {
    @Test
    fun oldIndexRetainsEpisodesAndDerivesReadableTitle() {
        val old = """{"bvid":"BV-old","episodes":[{"cid":123,"page":2,"part":"第二讲","video":{"uri":"content://video/1","displayName":"课程 P2 第二讲.mp4","sizeBytes":400}}]}"""
        val index = Json.decodeFromString<OnlineCacheIndex>(old)
        assertEquals("课程", index.offlineTitle())
        assertEquals(1, index.totalParts)
        assertEquals(0L, index.episodes.single().durationMs)
        assertEquals(index, Json.decodeFromString<OnlineCacheIndex>(Json.encodeToString(index)))
    }

    @Test
    fun oldAudioIndexDerivesTitleWithoutAudioSuffix() {
        val index = OnlineCacheIndex("BV", listOf(OnlineCacheEpisode(page = 3, audio = OnlineCacheFile(displayName = "课程 音频 P3 第三讲.m4a"))))
        assertEquals("课程", index.offlineTitle())
        assertEquals("BV", OnlineCacheIndex("BV").offlineTitle())
    }

    @Test
    fun outOfOrderSnapshotsRetainAllPartsAndNewestTrack() {
        val newer = OnlineCacheIndex(
            bvid = "BV",
            title = "课程",
            author = "老师",
            totalParts = 3,
            episodes = listOf(
                OnlineCacheEpisode(cid = 1, video = OnlineCacheFile(uri = "new", cachedAt = 20), durationMs = 8000),
                OnlineCacheEpisode(cid = 2, page = 2, audio = OnlineCacheFile(uri = "audio", cachedAt = 30)),
            ),
        )
        val older = OnlineCacheIndex("BV", listOf(OnlineCacheEpisode(cid = 1, video = OnlineCacheFile(uri = "old", cachedAt = 10))))
        val merged = mergeCacheIndices(newer, older)
        assertEquals("课程", merged.title)
        assertEquals("老师", merged.author)
        assertEquals(3, merged.totalParts)
        assertEquals(listOf(1L, 2L), merged.episodes.map { it.cid })
        assertEquals("new", merged.episodes[0].video?.uri)
        assertEquals(8000L, merged.episodes[0].durationMs)
        assertEquals("audio", merged.episodes[1].audio?.uri)
        assertNull(merged.episodes[1].video)
    }
}
