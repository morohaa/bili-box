package io.github.bilibox.data.bili

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BiliCommentsTest {
    @Test
    fun requestsRealCommentEndpointsWithVideoIdCursorAndThreadId() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = client(requests) { request ->
            when (request.url.encodedPath) {
                "/x/web-interface/nav" -> """{"code":0,"data":{"wbi_img":{"img_url":"https://example.com/7cd084941338484aae1ad9425b84077c.png","sub_url":"https://example.com/4932caff0ff746eab6f01bf08b70ac45.png"}}}"""

                "/x/v2/reply/wbi/main" -> """
                    {"code":0,"data":{
                      "cursor":{"is_end":false,"pagination_reply":{"next_offset":"{\"page\":2}"}},
                      "top_replies":[{"rpid_str":"9007199254740995","like":12,"rcount":3,"member":{"uname":"作者","mid":"42","level_info":{"current_level":6}},"content":{"message":"置顶内容"},
                        "replies":[{"rpid":0},{"rpid":7,"member":{"uname":"回复者","mid":11,"level_info":{"current_level":0}},"content":{"message":"真实回复预览"}}]}],
                      "replies":[{"rpid":9007199254740995},{"rpid":2,"member":{"level_info":{}},"content":{"message":"普通评论"}}]
                    }}
                """.trimIndent()

                "/x/v2/reply/reply" -> """{"code":0,"data":{"page":{"size":20,"count":42},"replies":[{"rpid":3,"dialog_str":"9007199254740993","member":{"level_info":{"current_level":4}},"content":{"message":"楼中楼回复"}}]}}"""

                else -> error("Unexpected request: ${request.url.encodedPath}")
            }
        }
        val page = client.videoComments(aid = 42L, offset = "", order = VideoCommentOrder.HOT)
        val nextOffset = checkNotNull(page.nextKey)
        assertEquals(listOf(9007199254740995L, 2L), page.comments.map(VideoComment::id))
        assertTrue(page.comments.first().pinned)
        assertEquals("置顶内容", page.comments.first().message)
        assertEquals(3, page.comments.first().replyCount)
        assertEquals(12L, page.comments.first().likes)
        assertEquals(42L, page.comments.first().authorId)
        assertEquals(6, page.comments.first().authorLevel)
        val preview = checkNotNull(page.comments.first().previewReply)
        assertEquals(7L, preview.id)
        assertEquals(11L, preview.authorId)
        assertEquals(0, preview.authorLevel)
        assertEquals("真实回复预览", preview.message)
        assertNull(page.comments[1].previewReply)
        assertNull(page.comments[1].authorLevel)
        assertEquals("{\"page\":2}", nextOffset)

        val next = client.videoComments(42L, nextOffset, VideoCommentOrder.NEWEST)
        assertTrue(next.comments.none(VideoComment::pinned))
        val thread = client.videoCommentReplies(42L, page.comments.first().id, 2)
        assertEquals("楼中楼回复", thread.comments.single().message)
        assertEquals(4, thread.comments.single().authorLevel)
        assertEquals(9007199254740993L, thread.comments.single().dialogId)
        assertEquals(3, thread.nextKey)

        assertEquals(4, requests.size)
        assertTrue(requests.all { it.method == "GET" })
        val first = requests[1].url
        assertEquals("1", first.queryParameter("type"))
        assertEquals("42", first.queryParameter("oid"))
        assertEquals("3", first.queryParameter("mode"))
        assertEquals("""{"offset":""}""", first.queryParameter("pagination_str"))
        assertNotNull(first.queryParameter("w_rid"))
        val nextRequest = requests[2].url
        assertEquals("2", nextRequest.queryParameter("mode"))
        assertEquals("""{"offset":"{\"page\":2}"}""", nextRequest.queryParameter("pagination_str"))
        val threadRequest = requests[3].url
        assertEquals("42", threadRequest.queryParameter("oid"))
        assertEquals("9007199254740995", threadRequest.queryParameter("root"))
        assertEquals("2", threadRequest.queryParameter("pn"))
    }

    @Test
    fun followsDialogFloorCursorAndStopsAtTheLastFloor() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = client(requests) { request ->
            assertEquals("/x/v2/reply/dialog/cursor", request.url.encodedPath)
            assertEquals("1", request.url.queryParameter("type"))
            assertEquals("42", request.url.queryParameter("oid"))
            assertEquals("31", request.url.queryParameter("root"))
            assertEquals("9007199254740993", request.url.queryParameter("dialog"))
            when (request.url.queryParameter("min_floor")) {
                "0" -> """{"code":0,"data":{"cursor":{"next":100,"is_begin":true},"dialog":{"max_floor":243},"replies":[{"rpid":11,"dialog":9007199254740993}]}}"""
                "100" -> """{"code":0,"data":{"cursor":{"next":244},"dialog":{"max_floor":243},"replies":[{"rpid":12}]}}"""
                else -> error("Unexpected dialog cursor: ${request.url}")
            }
        }
        val first = client.videoCommentDialog(42L, 31L, 9007199254740993L, 0L)
        assertEquals(9007199254740993L, first.comments.single().dialogId)
        val last = client.videoCommentDialog(42L, 31L, 9007199254740993L, checkNotNull(first.nextKey))
        assertEquals(12L, last.comments.single().id)
        assertNull(last.nextKey)
        assertEquals(2, requests.size)
    }

    @Test
    fun emptyAndFinalPagesDoNotOfferAnotherRequest() {
        val top = Json.decodeFromString<VideoCommentListPayload>(
            """{"cursor":{"is_end":true,"pagination_reply":{"next_offset":"unused"}},"replies":null}""",
        ).toPage(firstPage = true)
        val thread = Json.decodeFromString<VideoCommentRepliesPayload>(
            """{"page":{"size":20,"count":21},"replies":[{"rpid":5}]}""",
        ).toPage(requestedPage = 2)
        val dialog = Json.decodeFromString<VideoCommentDialogPayload>(
            """{"cursor":{"is_end":true},"replies":[]}""",
        ).toPage(minFloor = 244L)
        assertTrue(top.comments.isEmpty())
        assertNull(top.nextKey)
        assertNull(thread.nextKey)
        assertTrue(dialog.comments.isEmpty())
        assertNull(dialog.nextKey)
    }

    private fun client(requests: MutableList<Request>, respond: (Request) -> String): BiliClient = BiliClient(
        OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(respond(request).toResponseBody()).build()
        }.build(),
    )
}
