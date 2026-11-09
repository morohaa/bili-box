package io.github.bilibox.feature.library

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.util.formatBytes
import io.github.bilibox.core.util.formatFullDate
import io.github.bilibox.core.util.formatRelativeDate
import io.github.bilibox.core.util.formatVideoDuration
import io.github.bilibox.core.util.frameRateLabel
import io.github.bilibox.core.util.resolutionLabel
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.data.localvideo.LocalVideoFolder
import io.github.bilibox.data.localvideo.LocalVideoPlayback
import io.github.bilibox.data.localvideo.LocalVideoPlaybackState
import io.github.bilibox.data.localvideo.LocalVideoQuickFilter
import io.github.bilibox.data.localvideo.LocalVideoSettings
import java.time.format.DateTimeFormatter

@Composable
internal fun PermissionState(onRequest: () -> Unit, onRecheck: () -> Unit) {
    LibraryMessage(
        icon = AppIcon.FOLDER,
        title = "允许访问本地视频",
        message = "授权后，BiliBox 会读取设备中的视频文件，并按文件夹整理展示。",
        actionLabel = "允许访问并扫描",
        onAction = onRequest,
        secondaryLabel = "已授权？重新检查",
        onSecondary = onRecheck,
    )
}

@Composable
internal fun LoadingState() {
    val palette = LocalLibraryPalette.current
    Column(
        Modifier.fillMaxWidth().padding(top = 88.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            color = palette.accent,
            strokeWidth = 2.dp,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text("正在扫描本地视频…", color = palette.ink3, fontSize = 12.sp)
    }
}

@Composable
internal fun ErrorState(message: String, onRetry: () -> Unit) {
    LibraryMessage(
        icon = AppIcon.INFO,
        title = "扫描失败",
        message = message,
        actionLabel = "重试",
        onAction = onRetry,
    )
}

@Composable
internal fun EmptyState(
    title: String,
    message: String,
    action: (() -> Unit)? = null,
    actionLabel: String? = null,
) {
    LibraryMessage(
        icon = AppIcon.FOLDER,
        title = title,
        message = message,
        actionLabel = actionLabel,
        onAction = action,
    )
}

@Composable
internal fun LibraryMessage(
    icon: AppIcon,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val palette = LocalLibraryPalette.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(76.dp).clip(RoundedCornerShape(24.dp)).background(palette.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            AppGlyphIcon(icon, palette.accent, Modifier.size(34.dp), 1.5f)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            title,
            color = palette.ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            color = palette.ink3,
            fontSize = 12.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.height(42.dp).clip(RoundedCornerShape(21.dp)).background(palette.accent)
                    .clickable(onClick = onAction).padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    actionLabel,
                    color = palette.onAccent,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onSecondary) {
                Text(secondaryLabel, color = palette.accent, fontSize = 12.sp)
            }
        }
    }
}

@Composable
internal fun VideoInfoDialog(
    video: LocalVideo,
    playback: LocalVideoPlayback,
    onDismiss: () -> Unit,
) {
    val palette = LocalLibraryPalette.current
    val context = LocalContext.current
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = palette.accent) }
        },
        title = {
            Text("文件信息", color = palette.ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                InfoLine("文件名", video.name)
                InfoLine("路径", "内部存储 / ${video.relativeDirectory.ifBlank { "根目录" }} / ${video.name}")
                InfoLine("大小", Formatter.formatFileSize(context, video.sizeBytes))
                InfoLine(
                    "时长",
                    video.durationMs.takeIf { it > 0L }?.let(::formatVideoDuration) ?: "未知",
                )
                InfoLine(
                    "分辨率",
                    if (video.width > 0 && video.height > 0) {
                        "${video.width} × ${video.height} · ${resolutionLabel(video.width, video.height)}"
                    } else {
                        "未知"
                    },
                )
                InfoLine("帧率", if (video.frameRate > 0f) frameRateLabel(video.frameRate) else "未知")
                InfoLine("修改时间", formatFullDate(video.dateModifiedMs, formatter))
                InfoLine("类型", video.mimeType.ifBlank { video.extension.uppercase().ifBlank { "未知" } })
                InfoLine(
                    "播放状态",
                    when (val state = playback.state) {
                        LocalVideoPlaybackState.Unplayed -> "未播放"
                        is LocalVideoPlaybackState.Watching -> "看到 ${formatVideoDuration(state.positionMs)}"
                        LocalVideoPlaybackState.Watched -> "已看完"
                    },
                )
            }
        },
        containerColor = palette.card,
        shape = RoundedCornerShape(20.dp),
    )
}

@Composable
internal fun InfoLine(label: String, value: String) {
    val palette = LocalLibraryPalette.current
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = palette.ink3, fontSize = 10.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = palette.ink, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

internal fun emptyFilterMessage(filter: LocalVideoQuickFilter): String = when (filter) {
    LocalVideoQuickFilter.RECENT -> "最近 7 天没有播放记录"
    LocalVideoQuickFilter.BILI_CACHE -> "未发现 B 站缓存视频。系统通常不开放其他应用的私有缓存目录。"
    LocalVideoQuickFilter.PRIVATE -> "没有找到隐藏目录中的视频"
    LocalVideoQuickFilter.UNFINISHED -> "没有正在观看的视频"
    LocalVideoQuickFilter.FINISHED -> "还没有看完的视频"
}

internal fun emptyTitle(
    query: String,
    filter: LocalVideoQuickFilter?,
    nav: List<String>,
): String = when {
    query.isNotBlank() -> "没有找到相关视频"
    filter != null -> "没有符合条件的视频"
    nav.isNotEmpty() -> "这个文件夹里没有视频"
    else -> "还没有本地视频"
}

internal fun emptyMessage(
    query: String,
    filter: LocalVideoQuickFilter?,
    nav: List<String>,
    settings: LocalVideoSettings,
): String = when {
    query.isNotBlank() -> "换一个关键词试试，或清空搜索查看全部视频。"
    filter != null -> emptyFilterMessage(filter)
    nav.isNotEmpty() -> "换个文件夹看看，或者在设置里切换视图模式。"
    settings.showHidden -> "把视频保存到手机后，回到这一页会自动出现。"
    else -> "把视频保存到手机后，回到这一页会自动出现。也可以在设置中打开隐藏文件显示。"
}

internal fun videoMetaParts(
    video: LocalVideo,
    playback: LocalVideoPlayback,
    settings: LocalVideoSettings,
): List<MetaPart> {
    val fields = settings.fields
    val parts = mutableListOf<MetaPart>()
    val durationOnThumb = fields.thumbnail && settings.durationOnThumbnail
    val state = playback.state
    if (state == LocalVideoPlaybackState.Unplayed && !fields.thumbnail) {
        parts += MetaPart("NEW", highlight = true)
    }
    if (fields.duration && !durationOnThumb && video.durationMs > 0L) {
        parts += MetaPart(formatVideoDuration(video.durationMs))
    }
    if (fields.played) {
        when (state) {
            is LocalVideoPlaybackState.Watching -> parts += MetaPart("看到 ${formatVideoDuration(state.positionMs)}", highlight = true)

            LocalVideoPlaybackState.Watched -> parts += MetaPart(
                if (playback.lastPlayedAtMs > 0L) "看过 ${formatRelativeDate(playback.lastPlayedAtMs)}" else "已看完"
            )

            LocalVideoPlaybackState.Unplayed -> Unit
        }
    }
    if (fields.resolution && video.width > 0 && video.height > 0) {
        parts += MetaPart(resolutionLabel(video.width, video.height))
    }
    if (fields.frameRate && video.frameRate > 0f) {
        parts += MetaPart(frameRateLabel(video.frameRate))
    }
    if (fields.size) {
        parts += MetaPart(formatBytes(video.sizeBytes))
    }
    if (fields.date) {
        parts += MetaPart(formatRelativeDate(video.dateModifiedMs))
    }
    if (fields.extension && video.extension.isNotBlank()) {
        parts += MetaPart(video.extension.uppercase())
    }
    return parts
}

/** 文件大小与日期作为次要信息，两个字段都关闭时跳过整行。 */
internal fun folderMetaParts(
    folder: LocalVideoFolder,
    settings: LocalVideoSettings,
): List<MetaPart> {
    val fields = settings.folderFields
    val parts = mutableListOf<MetaPart>()
    if (fields.size) parts += MetaPart(formatBytes(folder.totalSizeBytes))
    if (fields.date) parts += MetaPart(formatRelativeDate(folder.newestDateMs))
    return parts
}

/** 数量与合计时长是文件夹的主要信息。 */
internal fun folderFootParts(
    folder: LocalVideoFolder,
    settings: LocalVideoSettings,
): List<MetaPart> {
    val parts = mutableListOf<MetaPart>()
    if (settings.folderFields.count) parts += MetaPart("${folder.count} 个视频")
    if (settings.folderFields.duration && folder.totalDurationMs > 0L) {
        parts += MetaPart(formatVideoDuration(folder.totalDurationMs))
    }
    return parts
}

internal fun hasLocalVideoPermission(context: Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) return true
    return Build.VERSION.SDK_INT >= 34 && context.checkSelfPermission(
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    ) == PackageManager.PERMISSION_GRANTED
}

internal fun localVideoPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )

    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)

    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}
