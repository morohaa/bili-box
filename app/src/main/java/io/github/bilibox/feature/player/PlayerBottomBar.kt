package io.github.bilibox.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import io.github.bilibox.data.danmaku.DanmakuSettings
import io.github.bilibox.feature.danmaku.DanmakuControlBar

/** 播放器底栏需要展示的播放状态。 */
internal data class PlayerBottomBarState(
    val fullScreen: Boolean,
    val position: Long,
    val duration: Long,
    val bufferedPosition: Long,
    val scrubbing: Boolean,
    val scrubPosition: Float,
    val playWhenReady: Boolean,
    val playbackState: Int,
    val danmakuSupported: Boolean,
    val danmakuSettings: DanmakuSettings,
    val danmakuLoading: Boolean,
    val danmakuError: String?,
    val canSendDanmaku: Boolean,
    val episodesAvailable: Boolean,
    val speed: Float,
    val qualityOptions: List<PlayerQualityOption>,
    val selectedQuality: Int?,
    val verticalFullScreen: Boolean,
)

/** 底栏操作回调，避免把播放器的全部状态继续作为参数传进来。 */
internal class PlayerBottomBarActions(
    val onSeekStart: (Float) -> Unit,
    val onSeekFinished: () -> Unit,
    val onTogglePlayback: () -> Unit,
    val onNextEpisode: () -> Unit,
    val onDanmakuToggle: () -> Unit,
    val onDanmakuSettings: () -> Unit,
    val onDanmakuSend: () -> Unit,
    val onEpisodes: () -> Unit,
    val onSpeed: () -> Unit,
    val onQuality: () -> Unit,
    val onToggleOrientation: () -> Unit,
    val onToggleVerticalFullScreen: () -> Unit,
)

@Composable
internal fun PlayerBottomBar(
    state: PlayerBottomBarState,
    actions: PlayerBottomBarActions,
    modifier: Modifier = Modifier,
) {
    val showPause = state.playWhenReady && state.playbackState != Player.STATE_ENDED
    val seekBar: @Composable (Modifier) -> Unit = { seekModifier ->
        VideoSeekBar(
            position = if (state.scrubbing) state.scrubPosition else state.position.toFloat(),
            duration = state.duration,
            bufferedPosition = state.bufferedPosition,
            onSeek = actions.onSeekStart,
            onSeekFinished = actions.onSeekFinished,
            modifier = seekModifier,
        )
    }

    Column(modifier) {
        if (state.fullScreen) {
            val currentQualityLabel = state.qualityOptions
                .firstOrNull { it.qn == state.selectedQuality }?.label
                ?: state.qualityOptions.maxByOrNull { it.qn }?.label.orEmpty()
            Row(
                Modifier.fillMaxWidth().height(26.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                seekBar(Modifier.fillMaxWidth())
            }
            Row(
                Modifier.fillMaxWidth().height(46.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerBarIconButton(
                    contentDescription = if (showPause) "暂停" else "播放",
                    onClick = actions.onTogglePlayback,
                ) {
                    PlaybackIcon(showPause, Modifier.size(22.dp))
                }
                PlayerBarIconButton(
                    contentDescription = "下一集",
                    onClick = actions.onNextEpisode,
                ) {
                    NextEpisodeIcon(Modifier.size(18.dp))
                }
                PlaybackTimeLabel(
                    position = if (state.scrubbing) state.scrubPosition.toLong() else state.position,
                    duration = state.duration,
                    modifier = Modifier.padding(start = 6.dp, end = 4.dp),
                )
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (state.danmakuSupported) {
                        DanmakuControlBar(
                            settings = state.danmakuSettings,
                            loading = state.danmakuLoading,
                            error = state.danmakuError,
                            canSend = state.canSendDanmaku,
                            onToggle = actions.onDanmakuToggle,
                            onOpenSettings = actions.onDanmakuSettings,
                            onSend = actions.onDanmakuSend,
                            modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                if (state.episodesAvailable) {
                    PlayerBarAction("选集", contentDescription = "选集", onClick = actions.onEpisodes)
                }
                SpeedBarChip(speed = state.speed, onClick = actions.onSpeed)
                if (state.qualityOptions.isNotEmpty()) {
                    PlayerQualityChip(label = currentQualityLabel, onClick = actions.onQuality)
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().height(40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = actions.onTogglePlayback,
                    modifier = Modifier.size(40.dp).semantics {
                        contentDescription = if (showPause) "暂停" else "播放"
                    },
                ) {
                    PlaybackIcon(showPause, Modifier.size(18.dp))
                }
                seekBar(Modifier.weight(1f))
                PlaybackTimeLabel(
                    position = if (state.scrubbing) state.scrubPosition.toLong() else state.position,
                    duration = state.duration,
                    modifier = Modifier.padding(start = 10.dp),
                )
                Spacer(Modifier.width(8.dp))
                PlayerVerticalScreenChip(
                    active = state.verticalFullScreen,
                    onClick = actions.onToggleVerticalFullScreen,
                )
                IconButton(
                    onClick = actions.onToggleOrientation,
                    modifier = Modifier.size(40.dp).semantics { contentDescription = "切换横屏" },
                ) {
                    FullscreenIcon(fullScreen = false, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
