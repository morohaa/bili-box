package io.github.bilibox.feature.player

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.Player
import io.github.bilibox.core.util.formatPlaybackTime
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.data.localvideo.LocalVideoBookmark
import io.github.bilibox.data.localvideo.LocalVideoBookmarkStore
import java.util.UUID

@Stable
internal class LocalVideoBookmarkState(
    private val player: Player,
    private val video: LocalVideo,
    private val store: LocalVideoBookmarkStore,
    private val notify: (String) -> Unit,
) {
    var items by mutableStateOf(store.load(video.key))
        private set
    var sheetOpen by mutableStateOf(false)
    var editing by mutableStateOf<LocalVideoBookmark?>(null)
    var deleting by mutableStateOf<LocalVideoBookmark?>(null)
    var canAdd by mutableStateOf(false)
        private set

    fun refresh() {
        canAdd = currentVideo() && player.isCurrentMediaItemSeekable &&
            (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_ENDED)
    }

    fun add() {
        refresh()
        if (!canAdd) return
        val position = player.currentPosition.coerceAtLeast(0L)
        if (items.any { it.positionMs / 1000 == position / 1000 }) {
            notify("该时间点已有标记")
            return
        }
        save(items + LocalVideoBookmark(UUID.randomUUID().toString(), position))
        notify("已添加标记 ${formatPlaybackTime(position)}")
    }

    fun rename(bookmark: LocalVideoBookmark, note: String) {
        save(items.map { if (it.id == bookmark.id) it.copy(note = note.trim().take(200)) else it })
        editing = null
    }

    fun delete(bookmark: LocalVideoBookmark) {
        save(items.filterNot { it.id == bookmark.id })
        deleting = null
    }

    fun jump(bookmark: LocalVideoBookmark) {
        refresh()
        if (!canAdd) {
            notify("视频就绪后可跳转到标记")
            return
        }
        val duration = player.duration.takeIf { it > 0L } ?: video.durationMs
        val target = if (duration > 0L) bookmark.positionMs.coerceIn(0L, duration - 1L) else bookmark.positionMs
        player.seekTo(target)
        sheetOpen = false
    }

    private fun currentVideo(): Boolean = player.currentMediaItem?.localConfiguration?.uri == video.uri

    private fun save(updated: List<LocalVideoBookmark>) {
        val sorted = updated.sortedBy { it.positionMs }
        store.save(video.key, sorted)
        items = sorted
    }
}

@Composable
internal fun rememberLocalVideoBookmarks(player: Player, video: LocalVideo): LocalVideoBookmarkState {
    val context = LocalContext.current
    val store = remember(context) { LocalVideoBookmarkStore(context) }
    val state = remember(player, video.key) {
        LocalVideoBookmarkState(player, video, store) { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }
    DisposableEffect(player, state) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = state.refresh()
        }
        player.addListener(listener)
        state.refresh()
        onDispose { player.removeListener(listener) }
    }
    return state
}
