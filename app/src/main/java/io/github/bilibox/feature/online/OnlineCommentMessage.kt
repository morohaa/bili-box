package io.github.bilibox.feature.online

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.theme.PlaybackInk
import io.github.bilibox.core.util.RemoteImageCache
import io.github.bilibox.data.bili.VideoComment
import kotlinx.coroutines.launch

private val CommentEmoteToken = Regex("\\[[^\\[\\]\\r\\n]+\\]")

@Composable
internal fun CommentMessage(
    comment: VideoComment,
    inThread: Boolean,
    maxLines: Int,
    overflow: TextOverflow,
    onTextLayout: (TextLayoutResult) -> Unit,
    imageCache: RemoteImageCache = RemoteImageCache.shared,
) {
    val message = comment.message.ifBlank { "（无文字内容）" }
    val emotes = remember(message, comment.emotes) {
        if (comment.emotes.isEmpty()) {
            emptyMap()
        } else {
            CommentEmoteToken.findAll(message).map { it.value }.distinct().mapNotNull { token ->
                val emote = comment.emotes[token] ?: return@mapNotNull null
                val url = normalizeImageUrl(emote.url).takeIf { it.startsWith("https://") } ?: return@mapNotNull null
                token to emote.copy(url = url)
            }.toMap()
        }
    }
    val text = remember(message, emotes) {
        buildAnnotatedString {
            var start = 0
            if (emotes.isNotEmpty()) {
                for (match in CommentEmoteToken.findAll(message).filter { it.value in emotes }) {
                    append(message, start, match.range.first)
                    appendInlineContent(match.value, match.value)
                    start = match.range.last + 1
                }
            }
            append(message, start, message.length)
        }
    }
    val urls = remember(emotes) { emotes.values.map { it.url }.toSet() }
    val images = remember(urls, imageCache) {
        mutableStateMapOf(*urls.mapNotNull { url -> imageCache.cached(url)?.let { url to it.asImageBitmap() } }.toTypedArray())
    }
    LaunchedEffect(urls, imageCache) {
        for (url in urls) {
            if (url !in images) launch { imageCache.load(url)?.let { images[url] = it.asImageBitmap() } }
        }
    }
    val inlineContent = buildMap {
        for ((token, emote) in emotes) {
            val image = images[emote.url] ?: continue
            val size = if (emote.meta?.size == 2) 2.em else 1.25.em
            val content = InlineTextContent(Placeholder(size, size, PlaceholderVerticalAlign.TextCenter)) {
                Image(image, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
            put(token, content)
        }
    }
    Text(
        text,
        color = PlaybackInk,
        fontSize = if (inThread) 14.sp else 15.sp,
        lineHeight = if (inThread) 22.sp else 23.sp,
        maxLines = maxLines,
        overflow = overflow,
        inlineContent = inlineContent,
        onTextLayout = onTextLayout,
    )
}
