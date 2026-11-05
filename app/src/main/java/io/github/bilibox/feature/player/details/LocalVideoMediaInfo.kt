package io.github.bilibox.feature.player.details

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.theme.Ink
import io.github.bilibox.core.ui.theme.Muted
import io.github.bilibox.core.util.formatVideoDuration
import io.github.bilibox.core.util.frameRateLabel
import io.github.bilibox.core.util.resolutionLabel
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.feature.player.LocalPlayerDetailActions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** The 媒体信息 tab: file, stream and playback facts in compact tables. */
@Composable
internal fun LocalVideoMediaInfo(
    video: LocalVideo,
    durationMs: Long,
) {
    val context = LocalContext.current
    val actions = LocalPlayerDetailActions.current
    val positionMs = actions?.positionMs ?: 0L
    val fraction = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val path = localVideoFullPath(video)
    val sizeText = if (video.sizeBytes > 0L) {
        "${Formatter.formatFileSize(context, video.sizeBytes)}（${String.format(Locale.US, "%,d", video.sizeBytes)} 字节）"
    } else {
        "未知"
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 22.dp)) {
        MediaInfoSection(
            title = "文件",
            icon = AppIcon.FILE,
            rows = listOf(
                "文件名" to video.name,
                "路径" to path,
                "大小" to sizeText,
                "修改时间" to formatDetailDate(video.dateModifiedMs),
                "添加时间" to formatDetailDate(video.dateAddedMs),
                "容器" to video.extension.uppercase().ifBlank { "未知" },
            ),
        )
        MediaInfoSection(
            title = "视频",
            icon = AppIcon.FRAME,
            rows = listOf(
                "分辨率" to if (video.width > 0 && video.height > 0) {
                    "${video.width} × ${video.height}（${resolutionLabel(video.width, video.height)}）"
                } else {
                    "未知"
                },
                "帧率" to if (video.frameRate > 0f) frameRateLabel(video.frameRate) else "未知",
                "类型" to video.mimeType.ifBlank { "未知" },
            ),
        )
        MediaInfoSection(
            title = "播放",
            icon = AppIcon.HISTORY,
            rows = listOf(
                "进度" to if (durationMs > 0L) {
                    "${formatVideoDuration(positionMs)} / ${formatVideoDuration(durationMs)}" +
                        "（${(fraction * 100).roundToInt()}%）"
                } else {
                    "未知"
                },
                "状态" to when {
                    fraction >= .995f -> "已看完"
                    positionMs > 0L -> "观看中"
                    else -> "未开始"
                },
                "文件夹" to video.relativeDirectory.ifBlank { "根目录" },
            ),
        )
        Box(
            Modifier.fillMaxWidth().padding(top = 18.dp).height(42.dp)
                .clip(RoundedCornerShape(21.dp)).background(DetailCard)
                .clickable(role = Role.Button) {
                    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    if (manager != null) {
                        manager.setPrimaryClip(ClipData.newPlainText("视频路径", path))
                        Toast.makeText(context, "路径已复制", Toast.LENGTH_SHORT).show()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppGlyphIcon(AppIcon.COPY, DetailSecondary, Modifier.size(14.dp), 1.8f)
                Spacer(Modifier.width(6.dp))
                Text("复制文件路径", color = DetailSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun MediaInfoSection(
    title: String,
    icon: AppIcon,
    rows: List<Pair<String, String>>,
) {
    val visible = rows.filter { it.second.isNotBlank() }
    if (visible.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyphIcon(icon, Muted, Modifier.size(14.dp), 1.8f)
        Spacer(Modifier.width(5.dp))
        Text(title, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(DetailCard)) {
        visible.forEachIndexed { index, row ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(row.first, color = Muted, fontSize = 11.5.sp, modifier = Modifier.width(64.dp))
                Text(
                    row.second,
                    color = Ink,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f),
                )
            }
            if (index != visible.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 12.dp),
                    color = Color(0xFFEDEEF0),
                    thickness = 1.dp,
                )
            }
        }
    }
}

private fun formatDetailDate(milliseconds: Long): String {
    if (milliseconds <= 0L) return "未知"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(milliseconds))
}
