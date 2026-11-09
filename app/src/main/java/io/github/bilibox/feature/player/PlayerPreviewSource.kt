package io.github.bilibox.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource

/** 预览独立读取视频流；在线视频带上与主播放器相同的请求头。 */
internal data class PlayerPreviewSource(
    val mediaItem: MediaItem,
    val dataSourceFactory: DataSource.Factory? = null,
)

@Composable
internal fun rememberPlayerPreviewSource(player: Player, override: PlayerPreviewSource?): PlayerPreviewSource? {
    var mediaItem by remember(player) { mutableStateOf(player.currentMediaItem) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                mediaItem = item
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    return remember(mediaItem, override) { override ?: mediaItem?.let { PlayerPreviewSource(it) } }
}
