package io.github.bilibox.feature.library

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.util.formatFullDate
import io.github.bilibox.core.util.formatVideoDuration
import io.github.bilibox.core.util.frameRateLabel
import io.github.bilibox.core.util.resolutionLabel
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.data.localvideo.LocalVideoPlayback
import io.github.bilibox.data.localvideo.LocalVideoPlaybackState
import java.time.format.DateTimeFormatter

@Composable
internal fun VideoInfoDialog(video: LocalVideo, playback: LocalVideoPlayback, onDismiss: () -> Unit) {
    val palette = LocalLibraryPalette.current
    val context = LocalContext.current
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }
    LibraryDetailDialog(
        title = "文件信息",
        subtitle = "本地视频 · ${video.extension.uppercase().ifBlank { "未知格式" }}",
        icon = AppIcon.FILE,
        onDismiss = onDismiss,
        actions = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = palette.chip, contentColor = palette.ink),
            ) { Text("关闭", fontWeight = FontWeight.Medium) }
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            InfoSectionLabel("文件名")
            SelectionContainer {
                Text(video.name, color = palette.ink, fontSize = 15.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoMetric("大小", Formatter.formatFileSize(context, video.sizeBytes), AppIcon.SIZE, Modifier.weight(1f))
            InfoMetric("时长", video.durationMs.takeIf { it > 0L }?.let(::formatVideoDuration) ?: "未知", AppIcon.CLOCK, Modifier.weight(1f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            InfoSectionLabel("媒体参数")
            Column(Modifier.fillMaxWidth().background(palette.chip, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp)) {
                InfoDetailRow("分辨率", if (video.width > 0 && video.height > 0) "${video.width} × ${video.height} · ${resolutionLabel(video.width, video.height)}" else "未知")
                HorizontalDivider(color = palette.line)
                InfoDetailRow("帧率", if (video.frameRate > 0f) frameRateLabel(video.frameRate) else "未知")
                HorizontalDivider(color = palette.line)
                InfoDetailRow("类型", video.mimeType.ifBlank { video.extension.uppercase().ifBlank { "未知" } })
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            InfoSectionLabel("存储位置")
            Row(
                Modifier.fillMaxWidth().background(palette.chip, RoundedCornerShape(16.dp)).padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppGlyphIcon(AppIcon.FOLDER, palette.ink2, Modifier.padding(top = 2.dp).size(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("所在文件夹", color = palette.ink2, fontSize = 11.sp)
                    SelectionContainer {
                        Text(video.relativeDirectory.ifBlank { "根目录" }, color = palette.ink, fontSize = 13.sp, lineHeight = 20.sp)
                    }
                }
            }
        }
        Column {
            InfoDetailRow("修改时间", formatFullDate(video.dateModifiedMs, formatter))
            InfoDetailRow(
                "播放状态",
                when (val state = playback.state) {
                    LocalVideoPlaybackState.Unplayed -> "未播放"
                    is LocalVideoPlaybackState.Watching -> "播放中 · ${formatVideoDuration(state.positionMs)}"
                    LocalVideoPlaybackState.Watched -> "已播完"
                },
            )
        }
    }
}

@Composable
private fun InfoSectionLabel(label: String) {
    Text(label, color = LocalLibraryPalette.current.ink2, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun InfoMetric(label: String, value: String, icon: AppIcon, modifier: Modifier) {
    val palette = LocalLibraryPalette.current
    Column(modifier.background(palette.chip, RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AppGlyphIcon(icon, palette.ink2, Modifier.size(14.dp))
            Text(label, color = palette.ink2, fontSize = 11.sp)
        }
        Text(value, color = palette.ink, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoDetailRow(label: String, value: String) {
    val palette = LocalLibraryPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, color = palette.ink2, fontSize = 12.sp, lineHeight = 19.sp, modifier = Modifier.width(60.dp))
        SelectionContainer(Modifier.weight(1f)) {
            Text(value, color = palette.ink, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}
