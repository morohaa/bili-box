package io.github.bilibox.data.favorites

import io.github.bilibox.data.bili.BiliClient
import io.github.bilibox.data.bili.BiliException
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class BiliFavoritesTest {
    @Test
    fun `account and paged private folders use authenticated request`() = runBlocking {
        val requests = mutableListOf<Request>()
        val source = source(requests) { request ->
            if (request.url.encodedPath.endsWith("nav")) {
                """{"code":0,"data":{"isLogin":true,"mid":123}}"""
            } else {
                """{"code":0,"data":{"has_more":true,"list":[{"id":8,"title":"私密收藏","attr":0,"media_count":35,"cover":"//i.example/cover"}]}}"""
            }
        }
        assertEquals(123L, source.accountId())
        val page = source.folders(123, 2)
        assertTrue(page.hasMore)
        assertFalse(page.items.single().isPublic)
        assertEquals("https://i.example/cover", page.items.single().coverUrl)
        assertEquals("123", requests.last().url.queryParameter("up_mid"))
        assertEquals("2", requests.last().url.queryParameter("pn"))
        assertEquals("50", requests.last().url.queryParameter("ps"))
        requests.forEach {
            assertEquals("SESSDATA=fixture; bili_jct=fixture", it.header("Cookie"))
            assertEquals("https://www.bilibili.com/", it.header("Referer"))
        }
    }

    @Test
    fun `videos preserve invalid entries and encode server search and order`() = runBlocking {
        val requests = mutableListOf<Request>()
        val source = source(requests) {
            """{"code":0,"data":{"has_more":1,"info":{"media_count":24},"medias":[
                {"id":1,"type":2,"attr":0,"title":"A & B","bvid":"BV123","duration":3661,"cover":"http://i.example/a","upper":{"name":"UP"},"cnt_info":{"play":123,"danmaku":7},"fav_time":100,"pubtime":90},
                {"id":2,"type":2,"attr":9,"title":"失效视频","bvid":"BV456"},
                {"id":3,"type":12,"title":"音频"},
                {"id":4,"type":2,"title":"无 BV"}
            ]}}"""
        }
        val page = source.videos(8, 3, " 音乐 & Kotlin ", FavoriteOrder.PUBLISHED)
        assertTrue(page.hasMore)
        assertEquals(24, page.total)
        assertEquals(4, page.items.size)
        val video = requireNotNull(page.items.first().video)
        assertEquals("A & B", video.title)
        assertEquals("BV123", video.bvid)
        assertEquals("1:01:01", video.duration)
        assertEquals(7L, video.commentCount)
        assertEquals("123", video.playCount)
        assertEquals("https://i.example/a", video.coverUrl)
        page.items.drop(1).forEach {
            assertNull(it.video)
            assertNotNull(it.unavailableReason)
        }
        val url = requests.single().url
        assertEquals("8", url.queryParameter("media_id"))
        assertEquals("3", url.queryParameter("pn"))
        assertEquals("音乐 & Kotlin", url.queryParameter("keyword"))
        assertEquals("pubtime", url.queryParameter("order"))
        assertEquals("0", url.queryParameter("type"))
    }

    @Test
    fun `null media and folder lists represent empty collections`() = runBlocking {
        val source = source(mutableListOf()) { """{"code":0,"data":{"list":null,"medias":null,"has_more":false}}""" }
        assertTrue(source.folders(123, 1).items.isEmpty())
        assertTrue(source.videos(8, 1, "", FavoriteOrder.SAVED).items.isEmpty())
    }

    @Test
    fun `expired login and denied folders do not become successful empty collections`() = runBlocking {
        val expired = source(mutableListOf()) { """{"code":-101,"message":"账号未登录"}""" }
        assertEquals(-101, failureCode { expired.accountId() })
        val anonymous = source(mutableListOf()) { """{"code":0,"data":{"isLogin":false,"mid":0}}""" }
        assertEquals(-101, failureCode { anonymous.accountId() })
        val denied = source(mutableListOf()) { """{"code":72010027,"message":"收藏夹不存在"}""" }
        assertEquals(72010027, failureCode { denied.videos(8, 1, "", FavoriteOrder.SAVED) })
    }

    private suspend fun failureCode(block: suspend () -> Any): Int {
        try {
            block()
            error("Expected BiliException")
        } catch (error: BiliException) {
            return error.code
        }
    }

    private fun source(requests: MutableList<Request>, response: (Request) -> String): BiliFavorites {
        val http = OkHttpClient.Builder().addInterceptor(
            Interceptor { chain ->
                val request = chain.request()
                requests += request
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(response(request).toResponseBody()).build()
            }
        ).build()
        return BiliFavorites(BiliClient(http).also { it.cookieHeader = "SESSDATA=fixture; bili_jct=fixture" })
    }
}
