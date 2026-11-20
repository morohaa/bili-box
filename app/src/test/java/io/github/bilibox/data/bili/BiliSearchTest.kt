package io.github.bilibox.data.bili

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BiliSearchTest {
    @Test
    fun `anonymous search omits cookies on both attempts without clearing the session`() = runBlocking {
        val requests = mutableListOf<Request>()
        var searchAttempts = 0
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val body = when (request.url.encodedPath) {
                "/x/web-interface/nav" ->
                    """{"code":0,"data":{"wbi_img":{
                    "img_url":"https://example.com/0123456789abcdef0123456789abcdef.png",
                    "sub_url":"https://example.com/abcdef0123456789abcdef0123456789.png"
                }}}"""

                "/x/frontend/finger/spi" -> """{"code":0,"data":{"b_3":"fixture-fingerprint"}}"""

                "/x/web-interface/wbi/search/type" -> if (++searchAttempts == 1) {
                    """{"code":-403}"""
                } else {
                    """{"code":0,"data":{"result":[{"bvid":"BV-fixture","mid":42,"upic":"//i.example/face.jpg"}]}}"""
                }

                else -> error("Unexpected request path: ${request.url.encodedPath}")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody()).build()
        }.build()
        val client = BiliClient(http).apply { cookieHeader = "SESSDATA=fixture; bili_jct=fixture" }

        val result = client.searchVideos("Kotlin", page = 2, forceAnonymous = true)

        assertEquals("BV-fixture", result.items.single().bvid)
        assertEquals(42L, result.items.single().authorId)
        assertEquals("//i.example/face.jpg", result.items.single().authorAvatarUrl)
        assertEquals(2, searchAttempts)
        assertFalse(requests.any { it.url.encodedPath.endsWith("/spi") })
        requests.forEach { assertNull(it.header("Cookie")) }
        requests.filter { it.url.encodedPath.endsWith("/search/type") }.forEach {
            assertEquals("Kotlin", it.url.queryParameter("keyword"))
            assertEquals("2", it.url.queryParameter("page"))
        }

        client.searchVideos("Kotlin")

        assertEquals(3, searchAttempts)
        assertEquals("SESSDATA=fixture; bili_jct=fixture; buvid3=fixture-fingerprint", requests.last().header("Cookie"))
    }
}
