package io.github.bilibox.data.favorites

import androidx.test.platform.app.InstrumentationRegistry
import io.github.bilibox.data.auth.BiliSessionStore
import io.github.bilibox.data.bili.BiliClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test

/** 有登录会话的开发设备上读取真实账号；不输出账号信息或 Cookie，不修改收藏。 */
class BiliFavoritesAccountTest {
    @Test
    fun readsCurrentAccountFoldersAndVideos() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val session = BiliSessionStore.load(context)
        assumeNotNull(session)
        val source = BiliFavorites(BiliClient().also { it.cookieHeader = requireNotNull(session).cookie })
        val mid = source.accountId()
        assertTrue(mid > 0)
        val folders = source.folders(mid, 1)
        assertTrue(folders.items.all { it.id > 0 })
        folders.items.firstOrNull { it.count > 0 }?.let { folder ->
            val videos = source.videos(folder.id, 1, "", FavoriteOrder.SAVED)
            assertTrue("A nonempty folder should expose its saved resources", videos.items.isNotEmpty())
            assertTrue(videos.items.all { it.video != null || it.unavailableReason != null })
        }
    }
}
