package io.github.bilibox.data.localvideo

import android.net.Uri
import java.util.Locale

/** A video discovered in the system media library. */
internal data class LocalVideo(
    val uri: Uri,
    val name: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val relativePath: String = "",
    val bucketName: String = "",
    val dateAddedMs: Long = 0L,
    val dateModifiedMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 0f,
    val mimeType: String = "",
) {
    /** Stable key shared with [PlaybackProgressStore]. */
    val key: String get() = "$uri|$sizeBytes"

    /** Folder that directly contains this video, e.g. "Movies/动漫". */
    val relativeDirectory: String
        get() = relativePath.trim().trim('/')

    /** Name without its extension, used as the display title. */
    val title: String
        get() {
            val dot = name.lastIndexOf('.')
            return if (dot > 0) name.substring(0, dot) else name
        }

    val extension: String
        get() = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

    val isHiddenPath: Boolean
        get() = relativeDirectory.split('/').any { it.startsWith(".") } || bucketName.startsWith(".")

    val isBiliCache: Boolean
        get() {
            val path = relativePath.lowercase(Locale.ROOT)
            val bucket = bucketName.lowercase(Locale.ROOT)
            val segments = path.split('/')
            return path.contains("tv.danmaku.bili") ||
                path.contains("bilibili") ||
                path.contains("bilibox") ||
                segments.any { it == "bili" } ||
                bucket.contains("bilibili") ||
                bucket.contains("bilibox") ||
                bucket == "bili"
        }

    val pixelCount: Long get() = width.toLong() * height.toLong()
}

internal enum class LocalVideoViewMode(val label: String) {
    ALL_FOLDERS("所有文件夹"),
    FILES("文件"),
    FOLDERS("文件夹"),
}

internal enum class LocalVideoLayout(val label: String) {
    LIST("列表"),
    GRID("网格"),
}

internal enum class LocalVideoSort(
    val label: String,
    val ascendingLabel: String,
    val descendingLabel: String,
    val defaultAscending: Boolean,
) {
    TITLE("标题", "A → Z", "Z → A", true),
    DATE("日期", "最旧", "最新", false),
    PLAYED("播放时间", "最早", "最近", false),
    STATUS("状态", "未看优先", "看完优先", true),
    DURATION("长度", "最短", "最长", false),
    SIZE("大小", "最小", "最大", false),
    RESOLUTION("分辨率", "最低", "最高", false),
    PATH("路径", "A → Z", "Z → A", true),
    FPS("帧率", "最低", "最高", false),
    TYPE("类型", "A → Z", "Z → A", true);

    fun directionLabel(ascending: Boolean): String = if (ascending) ascendingLabel else descendingLabel
}

internal data class LocalVideoDisplayFields(
    val thumbnail: Boolean = true,
    val duration: Boolean = true,
    val extension: Boolean = true,
    val played: Boolean = false,
    val resolution: Boolean = false,
    val frameRate: Boolean = false,
    val path: Boolean = false,
    val size: Boolean = true,
    val date: Boolean = true,
) {
    fun isEnabled(field: LocalVideoField): Boolean = when (field) {
        LocalVideoField.THUMBNAIL -> thumbnail
        LocalVideoField.DURATION -> duration
        LocalVideoField.EXTENSION -> extension
        LocalVideoField.PLAYED -> played
        LocalVideoField.RESOLUTION -> resolution
        LocalVideoField.FRAME_RATE -> frameRate
        LocalVideoField.PATH -> path
        LocalVideoField.SIZE -> size
        LocalVideoField.DATE -> date
    }

    fun with(field: LocalVideoField, enabled: Boolean): LocalVideoDisplayFields = when (field) {
        LocalVideoField.THUMBNAIL -> copy(thumbnail = enabled)
        LocalVideoField.DURATION -> copy(duration = enabled)
        LocalVideoField.EXTENSION -> copy(extension = enabled)
        LocalVideoField.PLAYED -> copy(played = enabled)
        LocalVideoField.RESOLUTION -> copy(resolution = enabled)
        LocalVideoField.FRAME_RATE -> copy(frameRate = enabled)
        LocalVideoField.PATH -> copy(path = enabled)
        LocalVideoField.SIZE -> copy(size = enabled)
        LocalVideoField.DATE -> copy(date = enabled)
    }

    /** 已开启的字段数量，用于设置面板的分组角标。 */
    val enabledCount: Int get() = LocalVideoField.entries.count(::isEnabled)
}

internal enum class LocalVideoField(val label: String) {
    THUMBNAIL("缩略图"),
    DURATION("长度"),
    EXTENSION("扩展名"),
    PLAYED("播放进度"),
    RESOLUTION("分辨率"),
    FRAME_RATE("帧率"),
    PATH("路径"),
    SIZE("大小"),
    DATE("日期"),
}

/** 文件夹卡片上可独立开关的信息位，与视频字段分开设置。 */
internal data class LocalVideoFolderFields(
    val size: Boolean = true,
    val date: Boolean = true,
    val duration: Boolean = true,
    val status: Boolean = true,
    val count: Boolean = true,
    val path: Boolean = false,
) {
    fun isEnabled(field: LocalVideoFolderField): Boolean = when (field) {
        LocalVideoFolderField.COUNT -> count
        LocalVideoFolderField.DURATION -> duration
        LocalVideoFolderField.SIZE -> size
        LocalVideoFolderField.DATE -> date
        LocalVideoFolderField.STATUS -> status
        LocalVideoFolderField.PATH -> path
    }

    fun with(field: LocalVideoFolderField, enabled: Boolean): LocalVideoFolderFields = when (field) {
        LocalVideoFolderField.COUNT -> copy(count = enabled)
        LocalVideoFolderField.DURATION -> copy(duration = enabled)
        LocalVideoFolderField.SIZE -> copy(size = enabled)
        LocalVideoFolderField.DATE -> copy(date = enabled)
        LocalVideoFolderField.STATUS -> copy(status = enabled)
        LocalVideoFolderField.PATH -> copy(path = enabled)
    }

    /** 已开启的字段数量，用于设置面板的分组角标。 */
    val enabledCount: Int get() = LocalVideoFolderField.entries.count(::isEnabled)
}

internal enum class LocalVideoFolderField(val label: String) {
    COUNT("视频数量"),
    DURATION("总时长"),
    SIZE("文件大小"),
    DATE("添加日期"),
    STATUS("播放状态"),
    PATH("文件夹路径"),
}

internal data class LocalVideoSettings(
    val mode: LocalVideoViewMode = LocalVideoViewMode.ALL_FOLDERS,
    val layout: LocalVideoLayout = LocalVideoLayout.LIST,
    val sort: LocalVideoSort = LocalVideoSort.TITLE,
    val ascending: Boolean = true,
    val fields: LocalVideoDisplayFields = LocalVideoDisplayFields(),
    val folderFields: LocalVideoFolderFields = LocalVideoFolderFields(),
    val durationOnThumbnail: Boolean = true,
    val showHidden: Boolean = false,
    val recognizeNomedia: Boolean = true,
)

internal enum class LocalVideoQuickFilter(val label: String) {
    RECENT("最近播放"),
    BILI_CACHE("B站缓存"),
    PRIVATE("私密空间"),
    UNFINISHED("未看完"),
    FINISHED("已看完"),
}

internal sealed interface LocalVideoScanState {
    data object NeedsPermission : LocalVideoScanState
    data object Loading : LocalVideoScanState
    data class Ready(val videos: List<LocalVideo>, val nomediaFolders: Set<String>) : LocalVideoScanState
    data class Error(val message: String) : LocalVideoScanState
}

internal data class LocalVideoScanResult(
    val videos: List<LocalVideo>,
    val nomediaFolders: Set<String>,
)

internal data class LocalVideoFolder(
    val path: String,
    val name: String,
    val videos: List<LocalVideo>,
    val cover: LocalVideo?,
    val count: Int,
    val unplayedCount: Int,
    val newestDateMs: Long,
    val newestPlayedAtMs: Long,
    val totalDurationMs: Long,
    val totalSizeBytes: Long,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxFrameRate: Float,
)

internal data class LocalVideoLibraryView(
    val folders: List<LocalVideoFolder>,
    val videos: List<LocalVideo>,
    val playlist: List<LocalVideo>,
    val currentPath: String?,
    val searching: Boolean,
    val activeFilter: LocalVideoQuickFilter?,
)
