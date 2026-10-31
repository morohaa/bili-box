package com.example.bilibox.bili

import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource

@androidx.annotation.OptIn(UnstableApi::class)
object BiliPlayback {
    private const val UA = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    fun dataSourceFactory(cookie: String? = null): DataSource.Factory = DefaultHttpDataSource.Factory()
        .setUserAgent(UA)
        .setDefaultRequestProperties(buildMap {
            put("Referer", "https://www.bilibili.com/")
            put("Origin", "https://www.bilibili.com")
            put("Accept", "*/*")
            cookie?.takeIf { it.isNotBlank() }?.let { put("Cookie", it) }
        })
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(10_000)
        .setReadTimeoutMs(20_000)

    fun candidateCount(data: PlayData, preferredQn: Int = BiliQn.P1080): Int = candidates(data, preferredQn).size

    fun buildMediaSource(data: PlayData, preferredQn: Int = BiliQn.P1080, candidateIndex: Int = 0, cookie: String? = null): MediaSource {
        val factory = ProgressiveMediaSource.Factory(dataSourceFactory(cookie))
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

    fun attach(player: ExoPlayer, data: PlayData, preferredQn: Int = BiliQn.P1080, candidateIndex: Int = 0, cookie: String? = null) {
        player.setMediaSource(buildMediaSource(data, preferredQn, candidateIndex, cookie))
        player.prepare()
        player.playWhenReady = true
    }

    private data class Candidate(val videoUrl: String, val audioUrl: String?)

    private fun candidates(data: PlayData, preferredQn: Int): List<Candidate> {
        data.dash?.let { dash ->
            val video = pickVideo(dash, preferredQn)
            if (video != null) {
                val videoUrls = listOf(video.url) + video.backups
                val audio = pickAudio(dash)
                if (audio == null) return videoUrls.filter { it.isNotBlank() }.map { Candidate(it, null) }
                val audioUrls = listOf(audio.url) + audio.backups
                // A video backup and an audio backup do not always use the same CDN
                // ordering. Try every pair so one rejected host does not block playback.
                return videoUrls.filter { it.isNotBlank() }.flatMap { videoUrl ->
                    audioUrls.filter { it.isNotBlank() }.map { audioUrl -> Candidate(videoUrl, audioUrl) }
                }.distinctBy { it.videoUrl to it.audioUrl }
            }
        }
        return data.durl.flatMap { segment -> listOf(segment.url) + segment.backupUrl.orEmpty() }
            .filter { it.isNotBlank() }
            .distinct()
            .map { Candidate(it, null) }
    }

    private fun pickVideo(dash: Dash, preferredQn: Int): DashVideo? {
        val pool = dash.video.filter { it.id <= preferredQn }.ifEmpty { dash.video }
        val bestId = pool.maxOfOrNull { it.id } ?: return null
        return dash.video.filter { it.id == bestId && it.url.isNotEmpty() }.minByOrNull { codecRank(it.codecs) }
    }

    private fun pickAudio(dash: Dash): DashAudio? =
        dash.audio.filter { it.url.isNotEmpty() }.maxByOrNull { it.id }
            ?: dash.dolby?.audio?.maxByOrNull { it.bandwidth }
            ?: dash.flac?.audio?.takeIf { it.url.isNotEmpty() }

    private fun codecRank(codecs: String): Int = when {
        codecs.startsWith("avc") -> 0
        codecs.startsWith("hev") -> 1
        codecs.startsWith("av01") -> 2
        else -> 3
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
