package io.github.bilibox.feature.online

internal enum class OnlineCacheKind { VIDEO, AUDIO }

/** Per-track state. The two tracks are independent; [OnlineCacheEpisodeUi.badge] derives the row icon. */
internal sealed interface OnlineCacheTrackState {
    data object None : OnlineCacheTrackState
    data object Queued : OnlineCacheTrackState

    /** [progress] is 0f..1f, or -1f when the server did not report a size. */
    data class Downloading(val progress: Float) : OnlineCacheTrackState
    data object Failed : OnlineCacheTrackState
    data object Done : OnlineCacheTrackState
}

internal data class OnlineCacheEpisodeUi(
    val cid: Long,
    val page: Int,
    val part: String,
    val video: OnlineCacheTrackState = OnlineCacheTrackState.None,
    val audio: OnlineCacheTrackState = OnlineCacheTrackState.None,
)

/** The single icon/state shown at the right edge of a cache row. */
internal sealed interface OnlineCacheBadge {
    data object None : OnlineCacheBadge
    data object Queued : OnlineCacheBadge

    data class Downloading(
        val progress: Float,
        val supplement: Boolean,
    ) : OnlineCacheBadge

    data object Failed : OnlineCacheBadge
    data object Video : OnlineCacheBadge
    data object Audio : OnlineCacheBadge
    data object Both : OnlineCacheBadge
}

internal data class OnlineCacheQuality(
    val id: Int,
    val label: String,
)

internal fun OnlineCacheEpisodeUi.track(kind: OnlineCacheKind): OnlineCacheTrackState = if (kind == OnlineCacheKind.VIDEO) video else audio

internal fun OnlineCacheEpisodeUi.copyTrack(
    kind: OnlineCacheKind,
    state: OnlineCacheTrackState,
): OnlineCacheEpisodeUi = if (kind == OnlineCacheKind.VIDEO) {
    copy(video = state)
} else {
    copy(audio = state)
}

/**
 * Mirrors the reference state machine: an active task owns the icon, then the
 * failed state, then the completed tracks. A supplementary download is pink
 * because the other track is already cached.
 */
internal fun OnlineCacheEpisodeUi.badge(): OnlineCacheBadge {
    val downloadingVideo = video as? OnlineCacheTrackState.Downloading
    val downloadingAudio = audio as? OnlineCacheTrackState.Downloading
    if (downloadingVideo != null) {
        return OnlineCacheBadge.Downloading(
            progress = downloadingVideo.progress,
            supplement = audio is OnlineCacheTrackState.Done,
        )
    }
    if (downloadingAudio != null) {
        return OnlineCacheBadge.Downloading(
            progress = downloadingAudio.progress,
            supplement = video is OnlineCacheTrackState.Done,
        )
    }
    if (video is OnlineCacheTrackState.Queued || audio is OnlineCacheTrackState.Queued) {
        return OnlineCacheBadge.Queued
    }
    if (video is OnlineCacheTrackState.Failed || audio is OnlineCacheTrackState.Failed) {
        return OnlineCacheBadge.Failed
    }
    return when {
        video is OnlineCacheTrackState.Done && audio is OnlineCacheTrackState.Done -> OnlineCacheBadge.Both
        video is OnlineCacheTrackState.Done -> OnlineCacheBadge.Video
        audio is OnlineCacheTrackState.Done -> OnlineCacheBadge.Audio
        else -> OnlineCacheBadge.None
    }
}

internal fun OnlineCacheEpisodeUi.stateDescription(playing: Boolean): String {
    val prefix = if (playing) "正在播放，" else ""
    val state = when {
        video is OnlineCacheTrackState.Downloading || audio is OnlineCacheTrackState.Downloading ->
            if (video is OnlineCacheTrackState.Downloading) "视频缓存中，点按取消" else "音频缓存中，点按取消"

        video is OnlineCacheTrackState.Queued || audio is OnlineCacheTrackState.Queued ->
            "等待缓存，点按取消"

        video is OnlineCacheTrackState.Failed || audio is OnlineCacheTrackState.Failed ->
            "缓存失败，点按重试"

        video is OnlineCacheTrackState.Done && audio is OnlineCacheTrackState.Done ->
            "视频和音频已缓存"

        video is OnlineCacheTrackState.Done -> "视频已缓存"

        audio is OnlineCacheTrackState.Done -> "音频已缓存"

        else -> "未缓存，点按缓存"
    }
    return "$prefix$state"
}
