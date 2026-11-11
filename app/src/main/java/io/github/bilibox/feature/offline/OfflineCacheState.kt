package io.github.bilibox.feature.offline

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.bilibox.data.localvideo.LocalVideoPlayback
import io.github.bilibox.data.localvideo.PlaybackProgressStore
import io.github.bilibox.data.online.OfflineCacheEntry
import io.github.bilibox.data.online.OfflineCacheLibrary
import io.github.bilibox.data.online.OnlineCacheRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class OfflineCacheState(
    private val repository: OnlineCacheRepository,
    private val progress: PlaybackProgressStore,
    private val scope: CoroutineScope,
) {
    var library by mutableStateOf(OfflineCacheLibrary())
        private set
    var playback by mutableStateOf<Map<String, LocalVideoPlayback>>(emptyMap())
        private set
    var loading by mutableStateOf(true)
        private set
    var deleting by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var refreshJob: Job? = null

    fun refresh() {
        if (deleting) return
        refreshJob?.cancel()
        refreshJob = scope.launch { reload() }
    }

    private suspend fun reload() {
        try {
            error = null
            val loaded = repository.loadLibrary()
            val positions = withContext(Dispatchers.IO) {
                loaded.groups.flatMap { it.entries }.associate { it.id to progress.playback(it.media.key, it.media.durationMs) }
            }
            library = loaded
            playback = positions
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = "无法读取离线缓存，请重试"
        } finally {
            loading = false
        }
    }

    fun delete(entries: List<OfflineCacheEntry>) {
        if (deleting || entries.isEmpty()) return
        refreshJob?.cancel()
        deleting = true
        scope.launch {
            // 文件删除和索引更新必须一起完成，即使页面在此时离开。
            withContext(NonCancellable) {
                val result = runCatching { repository.delete(entries) }
                reload()
                error = when {
                    result.isFailure -> "删除未完成，请重试"
                    result.getOrDefault(0) > 0 -> "部分缓存无法删除，请重试"
                    else -> error
                }
                deleting = false
            }
        }
    }
}
