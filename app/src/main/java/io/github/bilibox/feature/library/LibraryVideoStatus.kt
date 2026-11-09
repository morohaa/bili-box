package io.github.bilibox.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.data.localvideo.LocalVideoPlayback
import io.github.bilibox.data.localvideo.LocalVideoPlaybackState
import io.github.bilibox.data.localvideo.LocalVideoSettings
import io.github.bilibox.data.localvideo.cachedVideoFrameRate
import io.github.bilibox.data.localvideo.loadVideoFrameRate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 状态与文件信息共用一行，窄卡片和大字体下自然换行，不占用封面。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun VideoInfoLine(
    video: LocalVideo,
    playback: LocalVideoPlayback,
    settings: LocalVideoSettings,
    metadataMaxLines: Int = 2,
    pathMaxLines: Int = 1,
    modifier: Modifier = Modifier,
) {
    val displayVideo = if (settings.fields.frameRate) video.copy(frameRate = rememberVideoFrameRate(video)) else video
    val meta = videoMetaParts(displayVideo, playback, settings)
    val fields = settings.fields
    if (!fields.status && meta.isEmpty() && !fields.path) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (fields.status || meta.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (fields.status) VideoPlaybackStatus(playback.state, Modifier.align(Alignment.CenterVertically))
                if (meta.isNotEmpty()) {
                    MetaText(
                        meta,
                        fontSize = 11.sp,
                        maxLines = metadataMaxLines,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
            }
        }
        if (fields.path) VideoPathHint(video, maxLines = pathMaxLines)
    }
}

@Composable
private fun rememberVideoFrameRate(video: LocalVideo): Float {
    if (video.frameRate.isFinite() && video.frameRate > 0f) return video.frameRate
    val context = LocalContext.current
    val frameRate by produceState(
        initialValue = cachedVideoFrameRate(video.key) ?: 0f,
        key1 = video.key,
    ) {
        value = withContext(Dispatchers.IO) { loadVideoFrameRate(context, video) }
    }
    return frameRate
}

@Composable
private fun VideoPathHint(video: LocalVideo, maxLines: Int) {
    val palette = LocalLibraryPalette.current
    Text(
        "内部存储 / ${video.relativeDirectory.ifBlank { "根目录" }} / ${video.name}",
        color = palette.ink3,
        fontSize = 10.5.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 三态使用相同的轻量标签，只有播放中的状态使用主题色强调。 */
@Composable
internal fun VideoPlaybackStatus(
    state: LocalVideoPlaybackState,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLibraryPalette.current
    val label: String
    val icon: AppIcon
    val content: Color
    val background: Color
    when (state) {
        LocalVideoPlaybackState.Unplayed -> {
            label = "未播放"
            icon = AppIcon.PLAY
            content = palette.ink2
            background = palette.line.copy(alpha = .5f)
        }

        is LocalVideoPlaybackState.Watching -> {
            label = "播放中"
            icon = AppIcon.HISTORY
            content = lerp(palette.accent, palette.ink, if (palette.dark) .12f else .25f)
            background = palette.accentSoft
        }

        LocalVideoPlaybackState.Watched -> {
            label = "已播完"
            icon = AppIcon.CHECK
            content = palette.ink2
            background = palette.line.copy(alpha = .3f)
        }
    }
    Row(
        modifier.background(background, RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        AppGlyphIcon(icon, content, Modifier.size(11.dp), 2f)
        Text(
            label,
            color = content,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
