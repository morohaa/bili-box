package io.github.bilibox.feature.online

import io.github.bilibox.data.bili.BiliClient
import io.github.bilibox.data.bili.PlayData
import io.github.bilibox.data.bili.VideoInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Inputs for one requested episode/quality load. */
internal data class OnlinePlaybackLoadRequest(
    val key: List<Any?>,
    val page: Int,
    val quality: Int,
    val resumeAtMs: Long,
    val resumePlaying: Boolean,
)

/** UI callbacks used to publish the result without coupling the loader to Compose or ExoPlayer. */
internal data class OnlinePlaybackLoadCallbacks(
    val onStart: () -> Unit,
    val onVideoInfo: (VideoInfo) -> Unit,
    val onPageResolved: (Int) -> Unit,
    val onLoaded: (cid: Long, data: PlayData, quality: Int, resumeAtMs: Long, resumePlaying: Boolean) -> Unit,
    val onError: (String) -> Unit,
)

/** Owns online detail/play-url requests, request cancellation and episode result coordination. */
internal class OnlinePlaybackLoadCoordinator(
    private val client: BiliClient,
    private val bvid: String,
    private val scope: CoroutineScope,
) {
    private var videoInfo: VideoInfo? = null
    private var loadJob: Job? = null
    private var lastRequestedKey: List<Any?>? = null

    fun hasRequested(key: List<Any?>): Boolean = lastRequestedKey == key

    fun load(
        request: OnlinePlaybackLoadRequest,
        cache: EpisodePlayCache,
        callbacks: OnlinePlaybackLoadCallbacks,
    ) {
        lastRequestedKey = request.key
        loadJob?.cancel()
        loadJob = scope.launch {
            callbacks.onStart()
            try {
                val info = videoInfo ?: run {
                    client.warmUp()
                    client.videoInfo(bvid).also {
                        videoInfo = it
                        callbacks.onVideoInfo(it)
                    }
                }
                val page = info.pages.firstOrNull { it.page == request.page }
                    ?: info.pages.firstOrNull()?.also { callbacks.onPageResolved(it.page) }
                val cid = page?.cid ?: info.cid
                if (cid <= 0L) throw IllegalStateException("B 站没有返回视频分 P 信息")

                val resolvedBvid = info.bvid.ifBlank { bvid }
                val playData = cache.take(cid)
                    ?: client.playUrl(resolvedBvid, cid, qn = request.quality)
                callbacks.onLoaded(cid, playData, request.quality, request.resumeAtMs, request.resumePlaying)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                callbacks.onError(error.message ?: "无法获取视频播放地址")
            }
        }
    }

    fun cancel() {
        loadJob?.cancel()
        loadJob = null
    }
}
