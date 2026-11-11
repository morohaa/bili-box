package io.github.bilibox.feature.online

import io.github.bilibox.data.bili.Page
import io.github.bilibox.data.online.CachedMediaFile
import io.github.bilibox.data.online.OnlineCacheDownloader
import io.github.bilibox.data.online.OnlineCacheIndex
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineCachePersistenceTest {
    @Test
    fun completedDownloadCommitsDisplayMetadataEvenWhenPlaybackScreenCloses() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val saved = CompletableDeferred<OnlineCacheIndex>()
        val downloader = object : OnlineCacheDownloader {
            override suspend fun loadIndex(bvid: String) = OnlineCacheIndex(bvid)

            override suspend fun saveIndex(index: OnlineCacheIndex) {
                started.complete(Unit)
                finish.await()
                saved.complete(index)
            }

            override suspend fun cacheVideo(
                bvid: String,
                cid: Long,
                qn: Int,
                page: Int,
                part: String,
                videoTitle: String,
                onProgress: (Float) -> Unit,
            ) = CachedMediaFile("content://video/1", "课程 P1 起步.mp4", qn, "720P", 512L)

            override suspend fun cacheAudio(
                bvid: String,
                cid: Long,
                audioId: Int,
                page: Int,
                part: String,
                videoTitle: String,
                onProgress: (Float) -> Unit,
            ): CachedMediaFile = error("Unexpected audio task")
        }
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val controller = OnlineCacheController("BV", "课程", downloader, scope, author = "老师", coverUrl = "cover")
        controller.syncEpisodes(listOf(Page(1, 1, "起步", 18), Page(2, 2, "进阶", 20)), 1, "课程")
        controller.onEpisodeClick(1)
        withTimeout(5_000) { started.await() }
        controller.dispose()
        scope.cancel()
        finish.complete(Unit)
        val index = withTimeout(5_000) { saved.await() }
        assertEquals("课程", index.title)
        assertEquals("老师", index.author)
        assertEquals("cover", index.coverUrl)
        assertEquals(2, index.totalParts)
        assertEquals(18_000L, index.episodes.single().durationMs)
        assertEquals("content://video/1", index.episodes.single().video?.uri)
    }
}
