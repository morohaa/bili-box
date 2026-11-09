package io.github.bilibox.feature.favorites

import io.github.bilibox.data.bili.BiliException
import io.github.bilibox.data.favorites.FavoriteFolder
import io.github.bilibox.data.favorites.FavoriteFolderPage
import io.github.bilibox.data.favorites.FavoriteOrder
import io.github.bilibox.data.favorites.FavoriteVideo
import io.github.bilibox.data.favorites.FavoriteVideoPage
import io.github.bilibox.data.favorites.FavoritesSource
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class FavoritesStateHolderTest {
    private val folder = FavoriteFolder(8, "默认收藏夹", "", 3, false)

    @Test
    fun `folder paging deduplicates and failed refresh retries page one`() = runBlocking {
        val source = FakeSource()
        val holder = FavoritesStateHolder(source, this)
        holder.start()
        yield()
        holder.loadMore()
        yield()
        assertEquals(listOf(8L, 9L), holder.state.folders.map { it.id })
        source.fail = IOException("private details")
        holder.refresh()
        yield()
        assertEquals(2, holder.state.folders.size)
        assertFalse(holder.state.error.orEmpty().contains("private details"))
        source.fail = null
        holder.retry()
        yield()
        assertEquals(1, source.folderPages.last())
        assertEquals(listOf(folder), holder.state.folders)
        holder.dispose()
    }

    @Test
    fun `changing filter cancels old page and append failure retries same page`() = runBlocking {
        val source = FakeSource()
        val holder = FavoritesStateHolder(source, this)
        holder.start()
        yield()
        holder.openFolder(folder)
        yield()
        assertEquals(listOf(1L), holder.state.videos.map { it.id })
        source.fail = IOException()
        holder.loadMore()
        yield()
        assertEquals(listOf(1L), holder.state.videos.map { it.id })
        source.fail = null
        holder.retry()
        yield()
        assertEquals(listOf(1L, 2L), holder.state.videos.map { it.id })
        assertEquals(listOf(1, 2, 2), source.videoRequests.map { it.first })

        val oldRequest = CompletableDeferred<Unit>()
        source.wait = oldRequest
        holder.filter("旧搜索")
        yield()
        source.wait = null
        holder.filter("新搜索", FavoriteOrder.PUBLISHED)
        yield()
        oldRequest.complete(Unit)
        yield()
        assertEquals("新搜索", holder.state.keyword)
        assertEquals("新搜索", holder.state.videos.single().title)
        assertEquals(FavoriteOrder.PUBLISHED, source.videoRequests.last().third)
        assertEquals(1, holder.state.page)
        holder.closeFolder()
        assertEquals(listOf(folder), holder.state.folders)
        assertFalse(holder.state.loading)
        assertTrue(holder.state.hasMore)
        holder.dispose()
    }

    @Test
    fun `expired login requires reauthentication and does not expose stale videos`() = runBlocking {
        val source = FakeSource()
        val holder = FavoritesStateHolder(source, this)
        holder.openFolder(folder)
        yield()
        source.fail = BiliException(-101, "expired")
        holder.refresh()
        yield()
        assertTrue(holder.state.needsLogin)
        assertTrue(holder.state.videos.isEmpty())
        assertTrue(holder.state.error.orEmpty().contains("重新登录"))
        holder.dispose()
    }

    private inner class FakeSource : FavoritesSource {
        var fail: Exception? = null
        var wait: CompletableDeferred<Unit>? = null
        val folderPages = mutableListOf<Int>()
        val videoRequests = mutableListOf<Triple<Int, String, FavoriteOrder>>()
        override suspend fun accountId(): Long = 123L
        override suspend fun folders(mid: Long, page: Int): FavoriteFolderPage {
            folderPages += page
            fail?.let { throw it }
            return FavoriteFolderPage(if (page == 1) listOf(folder) else listOf(folder, folder.copy(id = 9)), page == 1)
        }
        override suspend fun videos(folderId: Long, page: Int, keyword: String, order: FavoriteOrder): FavoriteVideoPage {
            videoRequests += Triple(page, keyword, order)
            wait?.await()
            fail?.let { throw it }
            return FavoriteVideoPage(listOf(FavoriteVideo(page.toLong(), keyword, "", "UP", "1:00", 0, null)), page < 2, 2)
        }
    }
}
