package io.github.bilibox.data.bili

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.net.URI

@androidx.annotation.OptIn(UnstableApi::class)
object BiliPlayback {
    const val CDN_CONNECT_TIMEOUT_MS = 6_000
    const val CDN_READ_TIMEOUT_MS = 8_000

    // Bilibili's upos/edge CDN hosts reject the mobile Chrome UA on some
    // signed URLs with HTTP 403. PiliPlus uses a desktop UA for media requests;
    // the same UA keeps the official mirrors usable here.
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    fun dataSourceFactory(cookie: String? = null, bvid: String? = null): DataSource.Factory {
        val referer = bvid?.takeIf { it.isNotBlank() }
            ?.let { "https://www.bilibili.com/video/$it" }
            ?: "https://www.bilibili.com/"
        return DefaultHttpDataSource.Factory()
            .setUserAgent(UA)
            .setDefaultRequestProperties(
                buildMap {
                    put("Referer", referer)
                    put("Origin", "https://www.bilibili.com")
                    put("Accept", "*/*")
                    cookie?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
                }
            )
            .setAllowCrossProtocolRedirects(true)
            // A CDN node that cannot connect or send data within a few seconds is
            // almost always dead; failing fast lets the next backup URL take over.
            .setConnectTimeoutMs(CDN_CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(CDN_READ_TIMEOUT_MS)
    }

    /**
     * ExoPlayer's default policy retries one URL with growing delays and resets
     * the count whenever a few bytes arrive, so a flaky CDN host can keep the
     * player buffering for minutes. We have backup URLs, so retry once quickly
     * and then surface the error to switch hosts.
     */
    private val fastFailoverPolicy = object : DefaultLoadErrorHandlingPolicy(1) {
        override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
            val error = loadErrorInfo.exception
            if (error is HttpDataSource.InvalidResponseCodeException) return C.TIME_UNSET
            return if (loadErrorInfo.errorCount <= 1) 500L else C.TIME_UNSET
        }
    }

    fun candidateCount(data: PlayData, preferredQn: Int = BiliQn.P1080): Int = candidates(data, preferredQn).size

    /** 预览优先取最低清晰度的 AVC 视频轨，不加载音频或影响主画质。 */
    fun previewVideoUrl(data: PlayData): String? {
        val videos = data.dash?.video.orEmpty().filter { it.url.isNotBlank() }
        val compatible = videos.filter { it.codecs.startsWith("avc") }.ifEmpty { videos }
        val video = compatible.minWithOrNull(compareBy({ it.id }, { it.bandwidth }))
        val urls = video?.let { listOf(it.url) + it.backups }
            ?: data.durl.firstOrNull()?.let { listOf(it.url) + it.backupUrl.orEmpty() }.orEmpty()
        return BiliCdn.prioritize(urls).firstOrNull()
    }

    fun buildMediaSource(
        data: PlayData,
        preferredQn: Int = BiliQn.P1080,
        candidateIndex: Int = 0,
        cookie: String? = null,
        bvid: String? = null,
    ): MediaSource {
        val factory = ProgressiveMediaSource.Factory(dataSourceFactory(cookie, bvid))
            .setLoadErrorHandlingPolicy(fastFailoverPolicy)
        val candidate = candidates(data, preferredQn).getOrNull(candidateIndex)
            ?: throw BiliException(-1, "playurl 没有返回任何可播放地址")
        if (candidate.audioUrl == null) return factory.createMediaSource(MediaItem.fromUri(candidate.videoUrl))
        val videoSource = factory.createMediaSource(
            MediaItem.Builder().setUri(candidate.videoUrl).setMimeType(MimeTypes.VIDEO_MP4).build()
        )
        val audioSource = factory.createMediaSource(
            MediaItem.Builder().setUri(candidate.audioUrl).setMimeType(MimeTypes.AUDIO_MP4).build()
        )
        return MergingMediaSource(videoSource, audioSource)
    }

    fun attach(
        player: ExoPlayer,
        data: PlayData,
        preferredQn: Int = BiliQn.P1080,
        candidateIndex: Int = 0,
        cookie: String? = null,
        bvid: String? = null,
        startPositionMs: Long = 0L,
    ) {
        val source = buildMediaSource(data, preferredQn, candidateIndex, cookie, bvid)
        // Switching to a backup host mid-video must not restart from 0.
        if (startPositionMs > 0L) player.setMediaSource(source, startPositionMs) else player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
    }

    private data class Candidate(val videoUrl: String, val audioUrl: String?)

    private fun candidates(data: PlayData, preferredQn: Int): List<Candidate> {
        data.dash?.let { dash ->
            val video = pickVideo(dash, preferredQn)
            if (video != null) {
                val videoUrls = BiliCdn.prioritize(listOf(video.url) + video.backups)
                val audio = pickAudio(dash)
                if (audio == null) return videoUrls.map { Candidate(it, null) }
                val audioUrls = BiliCdn.prioritize(listOf(audio.url) + audio.backups)
                // A video backup and an audio backup do not always use the same CDN
                // ordering. Try every pair so one rejected host does not block playback,
                // starting with the pairs whose hosts are both most reliable.
                return videoUrls.withIndex().flatMap { (vi, videoUrl) ->
                    audioUrls.withIndex().map { (ai, audioUrl) -> Triple(vi + ai, vi, Candidate(videoUrl, audioUrl)) }
                }.sortedWith(compareBy({ it.first }, { it.second }))
                    .map { it.third }
                    .distinctBy { it.videoUrl to it.audioUrl }
            }
        }
        return BiliCdn.prioritize(data.durl.flatMap { segment -> listOf(segment.url) + segment.backupUrl.orEmpty() })
            .map { Candidate(it, null) }
    }

    private fun pickVideo(dash: Dash, preferredQn: Int): DashVideo? {
        val pool = dash.video.filter { it.id <= preferredQn }.ifEmpty { dash.video }
        val bestId = pool.maxOfOrNull { it.id } ?: return null
        return dash.video.filter { it.id == bestId && it.url.isNotEmpty() }.minByOrNull { codecRank(it.codecs) }
    }

    private fun pickAudio(dash: Dash): DashAudio? = dash.audio.filter { it.url.isNotEmpty() }.maxByOrNull { it.id }
        ?: dash.dolby?.audio?.maxByOrNull { it.bandwidth }
        ?: dash.flac?.audio?.takeIf { it.url.isNotEmpty() }

    private fun codecRank(codecs: String): Int = when {
        codecs.startsWith("avc") -> 0
        codecs.startsWith("hev") -> 1
        codecs.startsWith("av01") -> 2
        else -> 3
    }
}

/**
 * Orders Bilibili CDN urls by how reliably they start. playurl often returns a
 * PCDN/P2P node (mcdn.bilivideo.cn, szbdyd.com, raw IP with a port) as the
 * primary url; those frequently accept the connection and then stall, which is
 * what kept playback on "正在获取视频流". Official upos mirrors go first.
 */
object BiliCdn {
    fun prioritize(urls: List<String>): List<String> = urls.filter { it.isNotBlank() }.distinct().withIndex()
        .sortedWith(compareBy({ rank(it.value) }, { it.index }))
        .map { it.value }

    /** Lower is better: 0 official upos mirror, 1 other bilivideo CDN, 2 PCDN/P2P. */
    fun rank(url: String): Int {
        val uri = runCatching { URI(url) }.getOrNull() ?: return 2
        val host = uri.host?.lowercase() ?: return 2
        val isIp = host.all { it.isDigit() || it == '.' } || host.contains(':')
        val nonStandardPort = uri.port != -1 && uri.port != 80 && uri.port != 443
        return when {
            isIp || nonStandardPort -> 2
            host.endsWith(".mcdn.bilivideo.cn") || host.endsWith(".szbdyd.com") -> 2
            host.startsWith("upos-") -> 0
            else -> 1
        }
    }
}

object BiliQn {
    const val P8K = 127
    const val P4K = 120
    const val P1080_60 = 116
    const val P1080_PLUS = 112
    const val P1080 = 80
    const val P720 = 64
    const val P480 = 32
    const val P360 = 16
}
