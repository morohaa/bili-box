package io.github.bilibox.feature.online

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow
import io.github.bilibox.core.util.RemoteImageCache
import io.github.bilibox.data.bili.VideoComment
import io.github.bilibox.data.bili.VideoCommentEmote
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnlineCommentMessageTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun rendersRepeatedEmotesInlineAndKeepsUnknownOrUnavailableTokensAsText() {
        val successUrl = "https://images.test/emote.png"
        val failedUrl = "https://images.test/missing.png"
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
        val cache = RemoteImageCache(
            OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                val code = if (request.url.toString() == successUrl) 200 else 404
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("Test")
                    .body(bytes.toResponseBody()).build()
            }.build(),
        )
        val message = "前[笑][笑]后[普通][缺失]"
        val comment = VideoComment(
            id = 1L,
            author = "测试用户",
            avatarUrl = "",
            message = message,
            timestampSeconds = 0L,
            likes = 0L,
            replyCount = 0,
            emotes = mapOf("[笑]" to VideoCommentEmote("//images.test/emote.png"), "[缺失]" to VideoCommentEmote(failedUrl)),
        )
        val layout = AtomicReference<TextLayoutResult>()
        try {
            compose.setContent {
                CommentMessage(comment, false, Int.MAX_VALUE, TextOverflow.Clip, layout::set, imageCache = cache)
            }
            compose.waitUntil(5000) { layout.get()?.placeholderRects?.size == 2 }
            val result = layout.get()
            assertEquals(message, result.layoutInput.text.text)
            val pixels = compose.onNodeWithText(message).captureToImage().toPixelMap()
            for (placeholder in result.placeholderRects.filterNotNull()) {
                assertEquals(Color.Red, pixels[placeholder.center.x.toInt(), placeholder.center.y.toInt()])
            }
            assertEquals(2, result.placeholderRects.filterNotNull().size)
        } finally {
            cache.clear()
        }
    }
}
