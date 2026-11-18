package io.github.bilibox.feature.online

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.bilibox.data.bili.BiliException
import io.github.bilibox.data.bili.VideoComment
import io.github.bilibox.data.bili.VideoCommentPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class OnlineCommentPager<Key : Any>(
    firstKey: Key,
    private val scope: CoroutineScope,
    private val fetch: suspend (Key) -> VideoCommentPage<Key>,
) {
    var comments by mutableStateOf(emptyList<VideoComment>())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var nextKey by mutableStateOf<Key?>(firstKey)
    val hasMore: Boolean get() = nextKey != null
    private val completedKeys = mutableSetOf<Key>()
    private var started by mutableStateOf(false)
    val awaitingInitialLoad: Boolean get() = !started
    private var request: Job? = null

    fun loadInitial() {
        if (!started) loadMore()
    }

    fun loadMore() {
        val pageKey = nextKey ?: return
        if (isLoading) return
        started = true
        isLoading = true
        error = null
        request = scope.launch {
            try {
                val page = fetch(pageKey)
                completedKeys += pageKey
                comments = (comments + page.comments).distinctBy(VideoComment::id)
                nextKey = page.nextKey?.takeUnless(completedKeys::contains)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val apiError = failure as? BiliException
                error = when (apiError?.code) {
                    -101 -> "请先登录哔哩哔哩后查看评论"
                    -352, -412, 412, 429 -> "哔哩哔哩暂时限制了评论请求，请稍后再试"
                    else -> apiError?.takeIf { it.code < 0 }?.message ?: "评论加载失败，请稍后重试"
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun cancel() {
        request?.cancel()
    }
}
