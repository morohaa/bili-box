package io.github.bilibox.feature.library

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import android.util.LruCache
import android.util.Size
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.theme.Ink
import io.github.bilibox.core.ui.theme.Muted
import io.github.bilibox.core.ui.theme.Pink
import io.github.bilibox.core.util.formatVideoDuration
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.data.localvideo.LocalVideoFolder
import io.github.bilibox.data.localvideo.LocalVideoPlayback
import io.github.bilibox.data.localvideo.LocalVideoPlaybackState
import io.github.bilibox.data.localvideo.LocalVideoQuickFilter
import io.github.bilibox.data.localvideo.LocalVideoSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun PermissionState(onRequest: () -> Unit, onRecheck: () -> Unit) {
    LibraryMessage(
        icon = LocalIcon.FOLDER,
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
    Column(
        Modifier.fillMaxWidth().padding(top = 88.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Pink, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(14.dp))
        Text("正在扫描本地视频…", color = Muted, fontSize = 12.sp)
    }
}

@Composable
internal fun ErrorState(message: String, onRetry: () -> Unit) {
    LibraryMessage(
        icon = LocalIcon.INFO,
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
        icon = LocalIcon.FOLDER,
        title = title,
        message = message,
        actionLabel = actionLabel,
        onAction = action,
    )
}

@Composable
internal fun LibraryMessage(
    icon: LocalIcon,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(76.dp).clip(RoundedCornerShape(24.dp)).background(LibrarySoftPink),
            contentAlignment = Alignment.Center,
        ) {
            LocalGlyphIcon(icon, Pink, Modifier.size(34.dp), 1.5f)
        }
        Spacer(Modifier.height(18.dp))
        Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Muted, fontSize = 12.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.height(42.dp).clip(RoundedCornerShape(21.dp)).background(Pink)
                    .clickable(onClick = onAction).padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(actionLabel, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onSecondary) {
                Text(secondaryLabel, color = Pink, fontSize = 12.sp)
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
    val context = LocalContext.current
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = Pink) }
        },
        title = { Text("文件信息", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
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
                    if (video.width > 0 && video.height > 0) "${video.width} × ${video.height} · ${resolutionLabel(video.width, video.height)}" else "未知",
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
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
    )
}

@Composable
internal fun InfoLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = Muted, fontSize = 10.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = Ink, fontSize = 12.sp, lineHeight = 17.sp)
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
    return parts
}

internal fun folderMetaParts(
    folder: LocalVideoFolder,
    settings: LocalVideoSettings,
    includeDuration: Boolean = true,
): List<MetaPart> {
    val parts = mutableListOf(MetaPart("${folder.count} 个视频"))
    if (settings.fields.size) parts += MetaPart(formatBytes(folder.totalSizeBytes))
    if (settings.fields.date) parts += MetaPart(formatRelativeDate(folder.newestDateMs))
    if (includeDuration && settings.fields.duration && folder.totalDurationMs > 0L) {
        parts += MetaPart("共 ${formatVideoDuration(folder.totalDurationMs)}")
    }
    if (settings.fields.resolution && folder.maxWidth > 0 && folder.maxHeight > 0) {
        parts += MetaPart(resolutionLabel(folder.maxWidth, folder.maxHeight))
    }
    return parts
}

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val gb = bytes / 1_000_000_000.0
    if (gb >= 1.0) return "%.2f GB".format(gb)
    val mb = bytes / 1_000_000.0
    if (mb >= 1.0) return "%.0f MB".format(mb)
    return "%.0f KB".format(bytes / 1_000.0)
}

internal fun resolutionLabel(width: Int, height: Int): String {
    val shortSide = minOf(width, height)
    return when {
        shortSide >= 2160 -> "4K"
        shortSide >= 1440 -> "2K"
        shortSide >= 1080 -> "1080P"
        shortSide >= 720 -> "720P"
        shortSide >= 480 -> "480P"
        else -> "${shortSide}P"
    }
}

internal fun frameRateLabel(frameRate: Float): String {
    val rounded = frameRate.roundToInt()
    val text = if (kotlin.math.abs(frameRate - rounded) < .05f) {
        "$rounded"
    } else {
        "%.2f".format(frameRate).trimEnd('0').trimEnd('.')
    }
    return "$text fps"
}

internal fun formatRelativeDate(milliseconds: Long): String {
    if (milliseconds <= 0L) return "未知"
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(milliseconds).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val days = ChronoUnit.DAYS.between(date, today)
    return when {
        days <= 0L -> "今天"
        days == 1L -> "昨天"
        days < 7L -> "$days 天前"
        date.year == today.year -> "${date.monthValue}月${date.dayOfMonth}日"
        else -> "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    }
}

internal fun formatFullDate(milliseconds: Long, formatter: DateTimeFormatter): String = if (milliseconds <= 0L) {
    "未知"
} else {
    Instant.ofEpochMilli(milliseconds).atZone(ZoneId.systemDefault()).format(formatter)
}

internal fun coverBrush(seed: String): Brush {
    val hash = seed.hashCode()
    val hue = ((hash ushr 8) % 360 + 360) % 360
    val secondHue = (hue + 42 + ((hash ushr 3) % 70)) % 360
    return Brush.linearGradient(
        listOf(
            Color.hsl(hue.toFloat(), .46f, .44f),
            Color.hsl(secondHue.toFloat(), .52f, .62f),
        )
    )
}

internal val thumbnailCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

@Composable
internal fun rememberVideoThumbnail(video: LocalVideo): Bitmap? {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = thumbnailCache.get(video.key), video.key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { loadVideoThumbnail(context, video.uri, video.key) }
        }
    }
    return bitmap
}

internal fun loadVideoThumbnail(context: Context, uri: Uri, key: String): Bitmap? {
    thumbnailCache.get(key)?.let { return it }
    val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching { context.contentResolver.loadThumbnail(uri, Size(480, 270), null) }.getOrNull()
    } else {
        null
    } ?: run {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.getScaledFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 480, 270)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
    if (frame != null) thumbnailCache.put(key, frame)
    return frame
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
