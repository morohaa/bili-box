package io.github.bilibox.feature.online

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.bilibox.data.bili.BiliQn
import io.github.bilibox.data.bili.Page
import io.github.bilibox.data.online.CachedMediaFile
import io.github.bilibox.data.online.OnlineCacheDownloader
import io.github.bilibox.data.online.OnlineCacheEpisode
import io.github.bilibox.data.online.OnlineCacheFile
import io.github.bilibox.data.online.OnlineCacheIndex
import io.github.bilibox.data.online.onlineCacheAudioQualityLabel
import io.github.bilibox.data.online.onlineCacheVideoQualityLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the cache sheet's queue and state machine.
 *
 * At most two tracks download at once. Queued tasks keep the quality that was
 * selected when they were added. Closing the sheet does not stop a download;
 * leaving the video (or calling [dispose]) cancels running jobs and the store
 * deletes their pending MediaStore rows.
 */
internal class OnlineCacheController(
    private val bvid: String,
    private val videoTitle: String,
    private val downloader: OnlineCacheDownloader,
    private val scope: CoroutineScope,
    private val notify: (String) -> Unit = {},
    private val author: String = "",
    private val coverUrl: String = "",
) {
    private var pageDurations = emptyMap<Long, Long>()
    private var totalParts = 1

    var episodes by mutableStateOf<List<OnlineCacheEpisodeUi>>(emptyList())
        private set
    var mode by mutableStateOf(OnlineCacheKind.VIDEO)
        private set
    var videoQualities by mutableStateOf(defaultVideoQualities())
        private set
    var videoQuality by mutableStateOf(videoQualities.first())
        private set
    val audioQualities = DEFAULT_AUDIO_QUALITIES
    var audioQuality by mutableStateOf(audioQualities.first())
        private set

    private val pendingTasks = ArrayDeque<OnlineCacheTask>()
    private val running = mutableMapOf<OnlineCacheKey, Job>()
    private var cachedEpisodes: Map<Long, OnlineCacheEpisode> = emptyMap()
    private var videoQualityTouched = false
    private var disposed = false

    val currentQualities: List<OnlineCacheQuality>
        get() = if (mode == OnlineCacheKind.VIDEO) videoQualities else audioQualities

    val currentQuality: OnlineCacheQuality
        get() = if (mode == OnlineCacheKind.VIDEO) videoQuality else audioQuality

    val hasActiveWork: Boolean
        get() = running.isNotEmpty() || pendingTasks.isNotEmpty()

    val cachedVideoCount: Int
        get() = episodes.count { it.video is OnlineCacheTrackState.Done }

    val cachedAudioCount: Int
        get() = episodes.count { it.audio is OnlineCacheTrackState.Done }

    init {
        loadCachedIndex()
    }

    fun selectMode(value: OnlineCacheKind) {
        if (disposed || mode == value) return
        mode = value
    }

    fun selectVideoQuality(quality: OnlineCacheQuality) {
        if (disposed) return
        videoQuality = quality
        videoQualityTouched = true
    }

    fun selectAudioQuality(quality: OnlineCacheQuality) {
        if (disposed) return
        audioQuality = quality
    }

    /**
     * Replaces/merges the visible episode rows when the video's page list or
     * the active cid changes, preserving every in-flight or completed state.
     */
    fun syncEpisodes(pages: List<Page>, fallbackCid: Long, fallbackTitle: String) {
        if (disposed) return
        pageDurations = pages.associate { it.cid to it.duration * 1000L }
        totalParts = pages.size.coerceAtLeast(1)
        val seeds = pages.map { OnlineCacheSeed(it.cid, it.page, it.part) }.ifEmpty {
            if (fallbackCid > 0L) listOf(OnlineCacheSeed(fallbackCid, 1, fallbackTitle)) else emptyList()
        }
        if (seeds.isEmpty()) {
            if (episodes.isNotEmpty()) episodes = emptyList()
            return
        }

        val previous = episodes.associateBy { it.cid }
        val next = seeds.map { seed ->
            val old = previous[seed.cid]
            val cached = cachedEpisodes[seed.cid]
            val part = seed.part.ifBlank {
                if (seeds.size == 1) fallbackTitle else "第 ${seed.page} 部分"
            }
            OnlineCacheEpisodeUi(
                cid = seed.cid,
                page = seed.page,
                part = part,
                video = old?.video ?: if (cached?.video != null) OnlineCacheTrackState.Done else OnlineCacheTrackState.None,
                audio = old?.audio ?: if (cached?.audio != null) OnlineCacheTrackState.Done else OnlineCacheTrackState.None,
            )
        }
        if (next != episodes) episodes = next
    }

    /**
     * Refreshes the video qualities available from the player. Until the user
     * picks one manually, a player quality change also becomes the cache
     * default.
     */
    fun updateVideoQualities(qns: List<Int>, selectedQn: Int) {
        if (disposed) return
        val ids = qns.filter { it > 0 }.distinct().sortedDescending().ifEmpty {
            DEFAULT_VIDEO_QUALITIES.map { it.id }
        }
        val qualities = ids.map { OnlineCacheQuality(it, onlineCacheVideoQualityLabel(it)) }
        videoQualities = qualities

        val current = qualities.firstOrNull { it.id == videoQuality.id }
        when {
            current != null && videoQualityTouched -> videoQuality = current

            current != null -> videoQuality = qualities.firstOrNull { it.id == selectedQn } ?: current

            else -> {
                videoQuality = qualities.firstOrNull { it.id == selectedQn } ?: qualities.first()
                videoQualityTouched = false
            }
        }
    }

    fun onEpisodeClick(cid: Long) {
        if (disposed) return
        val episode = episodes.firstOrNull { it.cid == cid } ?: return

        // A row can only own one active task. Tapping a downloading/queued row
        // cancels it regardless of which toggle is currently selected, matching
        // the reference sheet's "tap again to stop" behaviour.
        val downloadingKind = when {
            episode.video is OnlineCacheTrackState.Downloading -> OnlineCacheKind.VIDEO
            episode.audio is OnlineCacheTrackState.Downloading -> OnlineCacheKind.AUDIO
            else -> null
        }
        if (downloadingKind != null) {
            cancel(cid, downloadingKind)
            return
        }
        val queuedKind = when {
            episode.video is OnlineCacheTrackState.Queued -> OnlineCacheKind.VIDEO
            episode.audio is OnlineCacheTrackState.Queued -> OnlineCacheKind.AUDIO
            else -> null
        }
        if (queuedKind != null) {
            cancelQueued(cid, queuedKind)
            return
        }

        val failedKind = when {
            episode.video is OnlineCacheTrackState.Failed -> OnlineCacheKind.VIDEO
            episode.audio is OnlineCacheTrackState.Failed -> OnlineCacheKind.AUDIO
            else -> null
        }
        // Pressing the red "!" should retry the failed track even when the
        // current toggle points at a track that is already complete.
        val target = if (failedKind != null && episode.track(mode) is OnlineCacheTrackState.Done) {
            failedKind
        } else {
            mode
        }

        when (val state = episode.track(target)) {
            OnlineCacheTrackState.None, OnlineCacheTrackState.Failed -> enqueue(cid, target)

            OnlineCacheTrackState.Done -> {
                when {
                    episode.video is OnlineCacheTrackState.Done &&
                        episode.audio is OnlineCacheTrackState.Done ->
                        notify("P${episode.page} 的视频和音频都已缓存")

                    target == OnlineCacheKind.VIDEO ->
                        notify("P${episode.page} 的视频已缓存，可切换音频补全")

                    else ->
                        notify("P${episode.page} 的音频已缓存，可切换视频补全")
                }
            }

            is OnlineCacheTrackState.Downloading, OnlineCacheTrackState.Queued -> Unit
        }
    }

    fun cacheAll() {
        if (disposed) return
        val kind = mode
        if (episodes.isEmpty()) {
            notify("分集信息加载中，请稍后再试")
            return
        }
        var added = 0
        episodes.toList().forEach { episode ->
            if (enqueue(episode.cid, kind)) added += 1
        }
        if (added == 0) {
            notify("当前${if (kind == OnlineCacheKind.VIDEO) "视频" else "音频"}已全部缓存或正在缓存")
        } else {
            notify("已添加 $added 个${if (kind == OnlineCacheKind.VIDEO) "视频" else "音频"}缓存任务")
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        pendingTasks.clear()
        running.values.forEach { it.cancel() }
        running.clear()
    }

    private fun loadCachedIndex() {
        scope.launch {
            val loaded = runCatching { downloader.loadIndex(bvid) }.getOrNull() ?: return@launch
            if (disposed) return@launch
            // Keep anything recorded locally while the disk read was in flight.
            cachedEpisodes = loaded.episodes.associateBy { it.cid } + cachedEpisodes
            applyCachedIndex()
        }
    }

    private fun applyCachedIndex() {
        if (disposed || cachedEpisodes.isEmpty()) return
        val next = episodes.map { episode ->
            val cached = cachedEpisodes[episode.cid] ?: return@map episode
            episode.copy(
                video = if (episode.video is OnlineCacheTrackState.None && cached.video != null) {
                    OnlineCacheTrackState.Done
                } else {
                    episode.video
                },
                audio = if (episode.audio is OnlineCacheTrackState.None && cached.audio != null) {
                    OnlineCacheTrackState.Done
                } else {
                    episode.audio
                },
            )
        }
        if (next != episodes) episodes = next
    }

    private fun enqueue(cid: Long, kind: OnlineCacheKind): Boolean {
        val episode = episodes.firstOrNull { it.cid == cid } ?: return false
        val state = episode.track(kind)
        if (state is OnlineCacheTrackState.Done ||
            state is OnlineCacheTrackState.Queued ||
            state is OnlineCacheTrackState.Downloading
        ) {
            return false
        }
        val otherKind = if (kind == OnlineCacheKind.VIDEO) OnlineCacheKind.AUDIO else OnlineCacheKind.VIDEO
        val otherState = episode.track(otherKind)
        // "缓存全部" can be pressed for both toggles. Keep one task per row so
        // the badge state and the two-item concurrency limit stay meaningful.
        if (otherState is OnlineCacheTrackState.Queued || otherState is OnlineCacheTrackState.Downloading) {
            return false
        }

        val quality = if (kind == OnlineCacheKind.VIDEO) videoQuality else audioQuality
        updateTrack(cid, kind, OnlineCacheTrackState.Queued)
        pendingTasks.addLast(
            OnlineCacheTask(
                cid = cid,
                kind = kind,
                qualityId = quality.id,
                page = episode.page,
                part = episode.part,
            )
        )
        pump()
        return true
    }

    private fun pump() {
        if (disposed) return
        while (running.size < MAX_CONCURRENT && pendingTasks.isNotEmpty()) {
            val task = pendingTasks.removeFirst()
            val episode = episodes.firstOrNull { it.cid == task.cid } ?: continue
            if (episode.track(task.kind) is OnlineCacheTrackState.Queued) launch(task)
        }
    }

    private fun launch(task: OnlineCacheTask) {
        val key = OnlineCacheKey(task.cid, task.kind)
        updateTrack(task.cid, task.kind, OnlineCacheTrackState.Downloading(-1f))

        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val file = when (task.kind) {
                    OnlineCacheKind.VIDEO -> downloader.cacheVideo(
                        bvid = bvid,
                        cid = task.cid,
                        qn = task.qualityId,
                        page = task.page,
                        part = task.part,
                        videoTitle = videoTitle,
                        onProgress = { progress -> onProgress(task, progress) },
                    )

                    OnlineCacheKind.AUDIO -> downloader.cacheAudio(
                        bvid = bvid,
                        cid = task.cid,
                        audioId = task.qualityId,
                        page = task.page,
                        part = task.part,
                        videoTitle = videoTitle,
                        onProgress = { progress -> onProgress(task, progress) },
                    )
                }
                record(task, file)
                if (disposed) return@launch
                updateTrack(task.cid, task.kind, OnlineCacheTrackState.Done)
                notify(
                    "P${task.page} ${if (task.kind == OnlineCacheKind.VIDEO) "视频" else "音频"}缓存完成" +
                        file.qualityLabel.takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty()
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!disposed) {
                    updateTrack(task.cid, task.kind, OnlineCacheTrackState.Failed)
                    notify("P${task.page} 缓存失败：${error.message ?: "请稍后重试"}")
                }
            } finally {
                if (running.remove(key) != null && !disposed) pump()
            }
        }
        running[key] = job
        job.start()
    }

    private fun cancel(cid: Long, kind: OnlineCacheKind) {
        val key = OnlineCacheKey(cid, kind)
        running.remove(key)?.cancel()
        updateTrack(cid, kind, OnlineCacheTrackState.None)
        pump()
    }

    private fun cancelQueued(cid: Long, kind: OnlineCacheKind) {
        pendingTasks.removeAll { it.cid == cid && it.kind == kind }
        updateTrack(cid, kind, OnlineCacheTrackState.None)
        pump()
    }

    private fun onProgress(task: OnlineCacheTask, progress: Float) {
        if (disposed) return
        scope.launch {
            val episode = episodes.firstOrNull { it.cid == task.cid } ?: return@launch
            if (episode.track(task.kind) !is OnlineCacheTrackState.Downloading) return@launch
            updateTrack(
                task.cid,
                task.kind,
                OnlineCacheTrackState.Downloading(
                    if (progress < 0f) -1f else progress.coerceIn(0f, 1f)
                ),
            )
        }
    }

    private fun updateTrack(cid: Long, kind: OnlineCacheKind, state: OnlineCacheTrackState) {
        if (disposed) return
        val index = episodes.indexOfFirst { it.cid == cid }
        if (index < 0) return
        val episode = episodes[index]
        if (episode.track(kind) == state) return
        episodes = episodes.toMutableList().also { list ->
            list[index] = episode.copyTrack(kind, state)
        }
    }

    private suspend fun record(task: OnlineCacheTask, file: CachedMediaFile) {
        val previous = cachedEpisodes[task.cid] ?: OnlineCacheEpisode(
            cid = task.cid,
            page = task.page,
            part = task.part,
        )
        val cached = OnlineCacheFile(
            uri = file.uri,
            displayName = file.displayName,
            qualityId = file.qualityId,
            qualityLabel = file.qualityLabel,
            sizeBytes = file.sizeBytes,
            cachedAt = System.currentTimeMillis(),
        )
        val updated = when (task.kind) {
            OnlineCacheKind.VIDEO -> previous.copy(page = task.page, part = task.part, video = cached)
            OnlineCacheKind.AUDIO -> previous.copy(page = task.page, part = task.part, audio = cached)
        }
        cachedEpisodes = cachedEpisodes + (task.cid to updated.copy(durationMs = pageDurations[task.cid] ?: previous.durationMs))
        val snapshot = OnlineCacheIndex(
            bvid = bvid,
            title = videoTitle,
            author = author,
            coverUrl = coverUrl,
            totalParts = totalParts,
            episodes = cachedEpisodes.values.sortedBy { it.page },
        )
        // 完成文件的索引写入不随页面离开而取消。
        withContext(NonCancellable) { downloader.saveIndex(snapshot) }
    }

    private data class OnlineCacheSeed(
        val cid: Long,
        val page: Int,
        val part: String,
    )

    private data class OnlineCacheTask(
        val cid: Long,
        val kind: OnlineCacheKind,
        val qualityId: Int,
        val page: Int,
        val part: String,
    )

    private data class OnlineCacheKey(
        val cid: Long,
        val kind: OnlineCacheKind,
    )

    private companion object {
        const val MAX_CONCURRENT = 2

        val DEFAULT_VIDEO_QUALITIES = listOf(
            OnlineCacheQuality(BiliQn.P1080, onlineCacheVideoQualityLabel(BiliQn.P1080)),
            OnlineCacheQuality(BiliQn.P720, onlineCacheVideoQualityLabel(BiliQn.P720)),
            OnlineCacheQuality(BiliQn.P480, onlineCacheVideoQualityLabel(BiliQn.P480)),
            OnlineCacheQuality(BiliQn.P360, onlineCacheVideoQualityLabel(BiliQn.P360)),
        )

        val DEFAULT_AUDIO_QUALITIES = listOf(
            OnlineCacheQuality(30280, onlineCacheAudioQualityLabel(30280)),
            OnlineCacheQuality(30232, onlineCacheAudioQualityLabel(30232)),
            OnlineCacheQuality(30216, onlineCacheAudioQualityLabel(30216)),
        )

        fun defaultVideoQualities(): List<OnlineCacheQuality> = DEFAULT_VIDEO_QUALITIES
    }
}
