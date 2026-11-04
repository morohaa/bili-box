package io.github.bilibox.data.localvideo

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.text.Collator
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

internal data class LocalVideoSettings(
    val mode: LocalVideoViewMode = LocalVideoViewMode.ALL_FOLDERS,
    val layout: LocalVideoLayout = LocalVideoLayout.LIST,
    val sort: LocalVideoSort = LocalVideoSort.TITLE,
    val ascending: Boolean = true,
    val fields: LocalVideoDisplayFields = LocalVideoDisplayFields(),
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

internal class LocalVideoSettingsStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("local_video_display", Context.MODE_PRIVATE)

    fun load(): LocalVideoSettings = LocalVideoSettings(
        mode = preferences.enumValue("mode", LocalVideoViewMode.ALL_FOLDERS),
        layout = preferences.enumValue("layout", LocalVideoLayout.LIST),
        sort = preferences.enumValue("sort", LocalVideoSort.TITLE),
        ascending = preferences.getBoolean("ascending", true),
        fields = LocalVideoDisplayFields(
            thumbnail = preferences.getBoolean("field_thumbnail", true),
            duration = preferences.getBoolean("field_duration", true),
            extension = preferences.getBoolean("field_extension", true),
            played = preferences.getBoolean("field_played", false),
            resolution = preferences.getBoolean("field_resolution", false),
            frameRate = preferences.getBoolean("field_frame_rate", false),
            path = preferences.getBoolean("field_path", false),
            size = preferences.getBoolean("field_size", true),
            date = preferences.getBoolean("field_date", true),
        ),
        durationOnThumbnail = preferences.getBoolean("duration_on_thumbnail", true),
        showHidden = preferences.getBoolean("show_hidden", false),
        recognizeNomedia = preferences.getBoolean("recognize_nomedia", true),
    )

    fun save(settings: LocalVideoSettings) {
        preferences.edit()
            .putString("mode", settings.mode.name)
            .putString("layout", settings.layout.name)
            .putString("sort", settings.sort.name)
            .putBoolean("ascending", settings.ascending)
            .putBoolean("field_thumbnail", settings.fields.thumbnail)
            .putBoolean("field_duration", settings.fields.duration)
            .putBoolean("field_extension", settings.fields.extension)
            .putBoolean("field_played", settings.fields.played)
            .putBoolean("field_resolution", settings.fields.resolution)
            .putBoolean("field_frame_rate", settings.fields.frameRate)
            .putBoolean("field_path", settings.fields.path)
            .putBoolean("field_size", settings.fields.size)
            .putBoolean("field_date", settings.fields.date)
            .putBoolean("duration_on_thumbnail", settings.durationOnThumbnail)
            .putBoolean("show_hidden", settings.showHidden)
            .putBoolean("recognize_nomedia", settings.recognizeNomedia)
            .apply()
    }
}

private inline fun <reified T : Enum<T>> android.content.SharedPreferences.enumValue(
    key: String,
    fallback: T,
): T {
    val raw = getString(key, null) ?: return fallback
    return runCatching { enumValueOf<T>(raw) }.getOrDefault(fallback)
}

/** Reads every video the system media store exposes, plus best-effort folder markers. */
internal fun queryLocalVideoLibrary(context: Context): LocalVideoScanResult {
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }
    val projection = buildList {
        add(MediaStore.Video.Media._ID)
        add(MediaStore.Video.Media.DISPLAY_NAME)
        add(MediaStore.Video.Media.DURATION)
        add(MediaStore.Video.Media.SIZE)
        add(MediaStore.Video.Media.DATE_ADDED)
        add(MediaStore.Video.Media.DATE_MODIFIED)
        add(MediaStore.Video.Media.WIDTH)
        add(MediaStore.Video.Media.HEIGHT)
        add(MediaStore.Video.Media.MIME_TYPE)
        add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(MediaStore.Video.Media.RELATIVE_PATH)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(MediaStore.Video.Media.CAPTURE_FRAMERATE)
        }
    }.toTypedArray()

    val videos = runCatching {
        readLocalVideos(context, collection, projection)
    }.getOrElse {
        // Some OEM media providers do not expose every optional column. Retry with the
        // universal projection and derive the folder from the legacy DATA column.
        @Suppress("DEPRECATION")
        val fallback = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.DATE_ADDED)
            add(MediaStore.Video.Media.DATE_MODIFIED)
            add(MediaStore.Video.Media.WIDTH)
            add(MediaStore.Video.Media.HEIGHT)
            add(MediaStore.Video.Media.MIME_TYPE)
            add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            @Suppress("DEPRECATION")
            add(MediaStore.Video.Media.DATA)
        }.toTypedArray()
        readLocalVideos(context, collection, fallback)
    }

    return LocalVideoScanResult(videos, detectNomediaFolders(videos))
}

private fun readLocalVideos(
    context: Context,
    collection: Uri,
    projection: Array<String>,
): List<LocalVideo> {
    val videos = mutableListOf<LocalVideo>()
    context.contentResolver.query(
        collection,
        projection,
        null,
        null,
        "${MediaStore.Video.Media.DATE_MODIFIED} DESC",
    )?.use { cursor ->
        val idIndex = cursor.getColumnIndex(MediaStore.Video.Media._ID)
        val nameIndex = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
        val durationIndex = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
        val sizeIndex = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
        val addedIndex = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
        val modifiedIndex = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
        val widthIndex = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
        val heightIndex = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
        val mimeIndex = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
        val bucketIndex = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
        val relativeIndex = cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
        val frameRateIndex = cursor.getColumnIndex(MediaStore.Video.Media.CAPTURE_FRAMERATE)
        @Suppress("DEPRECATION")
        val dataIndex = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
        val externalRoot = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')

        while (cursor.moveToNext()) {
            val id = cursor.longOrNull(idIndex) ?: continue
            val name = cursor.stringOrNull(nameIndex).orEmpty().ifBlank { "未命名视频" }
            val bucketName = cursor.stringOrNull(bucketIndex).orEmpty()
            val relativePath = when {
                relativeIndex >= 0 -> cursor.stringOrNull(relativeIndex).orEmpty()
                dataIndex >= 0 -> cursor.stringOrNull(dataIndex)
                    ?.takeIf { it.startsWith(externalRoot) }
                    ?.removePrefix(externalRoot)
                    ?.trimStart('/')
                    ?.substringBeforeLast('/', "")
                    .orEmpty()
                else -> bucketName
            }.trim().trim('/')
            videos += LocalVideo(
                uri = ContentUris.withAppendedId(collection, id),
                name = name,
                durationMs = cursor.longOrNull(durationIndex)?.coerceAtLeast(0L) ?: 0L,
                sizeBytes = cursor.longOrNull(sizeIndex)?.coerceAtLeast(0L) ?: 0L,
                relativePath = relativePath,
                bucketName = bucketName.ifBlank { relativePath.substringAfterLast('/') },
                dateAddedMs = (cursor.longOrNull(addedIndex) ?: 0L) * 1_000L,
                dateModifiedMs = (cursor.longOrNull(modifiedIndex) ?: 0L) * 1_000L,
                width = cursor.longOrNull(widthIndex)?.toInt() ?: 0,
                height = cursor.longOrNull(heightIndex)?.toInt() ?: 0,
                frameRate = if (frameRateIndex >= 0) cursor.floatOrNull(frameRateIndex) ?: 0f else 0f,
                mimeType = cursor.stringOrNull(mimeIndex).orEmpty(),
            )
        }
    }
    return videos
}

private fun Cursor.longOrNull(index: Int): Long? =
    if (index < 0 || isNull(index)) null else getLong(index)

private fun Cursor.floatOrNull(index: Int): Float? =
    if (index < 0 || isNull(index)) null else getFloat(index)

private fun Cursor.stringOrNull(index: Int): String? =
    if (index < 0 || isNull(index)) null else getString(index)

private fun detectNomediaFolders(videos: List<LocalVideo>): Set<String> {
    val root = Environment.getExternalStorageDirectory()
    return videos.asSequence()
        .map { it.relativeDirectory }
        .filter { it.isNotBlank() }
        .distinct()
        .filter { path ->
            runCatching { File(File(root, path), ".nomedia").exists() }.getOrDefault(false)
        }
        .toSet()
}

internal fun localVideoFolderLabel(path: String): String {
    val name = path.substringAfterLast('/')
    return when {
        path.contains("tv.danmaku.bili", ignoreCase = true) -> "哔哩哔哩缓存"
        name.equals("Camera", ignoreCase = true) -> "相机"
        name.equals("DCIM", ignoreCase = true) -> "相机"
        name.equals("Movies", ignoreCase = true) -> "电影"
        name.equals("Download", ignoreCase = true) -> "下载"
        name.equals("Pictures", ignoreCase = true) -> "图片"
        name.equals("WeChat", ignoreCase = true) -> "微信"
        name.contains("Telegram", ignoreCase = true) -> "Telegram 视频"
        name.isBlank() -> "内部存储"
        else -> name
    }
}

internal fun buildLocalVideoLibrary(
    videos: List<LocalVideo>,
    playback: Map<String, LocalVideoPlayback>,
    settings: LocalVideoSettings,
    nav: List<String>,
    query: String,
    quickFilter: LocalVideoQuickFilter?,
    nomediaFolders: Set<String>,
    nowMs: Long = System.currentTimeMillis(),
): LocalVideoLibraryView {
    val keyword = query.trim()
    val allowHidden = quickFilter == LocalVideoQuickFilter.PRIVATE
    val visible = videos.filter { video ->
        localVideoIsVisible(video, settings, nomediaFolders, allowHidden)
    }
    val filtered = visible.filter { video ->
        val matchesQuery = keyword.isEmpty() ||
            video.title.contains(keyword, ignoreCase = true) ||
            video.relativeDirectory.contains(keyword, ignoreCase = true)
        val matchesFilter = quickFilter == null ||
            localVideoMatchesQuickFilter(video, playback, quickFilter, nowMs)
        matchesQuery && matchesFilter
    }
    val here = nav.lastOrNull()
    val searching = keyword.isNotEmpty()
    val flat = searching || quickFilter != null || settings.mode == LocalVideoViewMode.FILES

    var files: List<LocalVideo>
    var folderGroups: List<Pair<String, List<LocalVideo>>>

    if (flat) {
        files = filtered.sortedWith(videoComparator(settings.sort, settings.ascending, playback))
        folderGroups = emptyList()
    } else if (settings.mode == LocalVideoViewMode.ALL_FOLDERS) {
        if (here == null) {
            files = filtered.filter { it.relativeDirectory.isBlank() }
                .sortedWith(videoComparator(settings.sort, settings.ascending, playback))
            folderGroups = filtered.filter { it.relativeDirectory.isNotBlank() }
                .groupBy { it.relativeDirectory }
                .toList()
        } else {
            files = filtered.filter { it.relativeDirectory == here }
                .sortedWith(videoComparator(settings.sort, settings.ascending, playback))
            folderGroups = emptyList()
        }
    } else {
        val prefix = if (here == null) "" else "$here/"
        files = filtered.filter { it.relativeDirectory == here.orEmpty() }
            .sortedWith(videoComparator(settings.sort, settings.ascending, playback))
        folderGroups = filtered.asSequence()
            .filter { it.relativeDirectory != here.orEmpty() }
            .filter { here == null || it.relativeDirectory.startsWith(prefix) }
            .groupBy { video ->
                val rest = video.relativeDirectory.removePrefix(prefix)
                prefix + rest.substringBefore('/')
            }
            .toList()
    }

    val folders = folderGroups
        .map { (path, folderVideos) -> aggregateFolder(path, folderVideos, playback) }
        .sortedWith(folderComparator(settings.sort, settings.ascending))

    val sortedFiltered = filtered.sortedWith(videoComparator(settings.sort, settings.ascending, playback))
    val playlist = files.ifEmpty { sortedFiltered }

    return LocalVideoLibraryView(
        folders = folders,
        videos = files,
        playlist = playlist,
        currentPath = here,
        searching = searching,
        activeFilter = quickFilter,
    )
}

private fun aggregateFolder(
    path: String,
    videos: List<LocalVideo>,
    playback: Map<String, LocalVideoPlayback>,
): LocalVideoFolder {
    val cover = videos.maxByOrNull { it.dateModifiedMs } ?: videos.firstOrNull()
    return LocalVideoFolder(
        path = path,
        name = localVideoFolderLabel(path),
        videos = videos,
        cover = cover,
        count = videos.size,
        unplayedCount = videos.count { playback[it.key]?.state == LocalVideoPlaybackState.Unplayed },
        newestDateMs = videos.maxOfOrNull { it.dateModifiedMs } ?: 0L,
        newestPlayedAtMs = videos.maxOfOrNull { playback[it.key]?.lastPlayedAtMs ?: 0L } ?: 0L,
        totalDurationMs = videos.sumOf { it.durationMs },
        totalSizeBytes = videos.sumOf { it.sizeBytes },
        maxWidth = videos.maxOfOrNull { it.width } ?: 0,
        maxHeight = videos.maxOfOrNull { it.height } ?: 0,
        maxFrameRate = videos.maxOfOrNull { it.frameRate } ?: 0f,
    )
}

internal fun localVideoIsVisible(
    video: LocalVideo,
    settings: LocalVideoSettings,
    nomediaFolders: Set<String>,
    allowHidden: Boolean = false,
): Boolean =
    (allowHidden || settings.showHidden || !video.isHiddenPath) &&
        !(settings.recognizeNomedia && video.relativeDirectory in nomediaFolders)

internal fun localVideoMatchesQuickFilter(
    video: LocalVideo,
    playback: Map<String, LocalVideoPlayback>,
    filter: LocalVideoQuickFilter,
    nowMs: Long = System.currentTimeMillis(),
): Boolean = when (filter) {
    LocalVideoQuickFilter.RECENT -> {
        val playedAt = playback[video.key]?.lastPlayedAtMs ?: 0L
        playedAt > 0L && nowMs - playedAt <= 7L * 24 * 60 * 60 * 1_000L
    }
    LocalVideoQuickFilter.BILI_CACHE -> video.isBiliCache
    LocalVideoQuickFilter.PRIVATE -> video.isHiddenPath
    LocalVideoQuickFilter.UNFINISHED -> playback[video.key]?.state is LocalVideoPlaybackState.Watching
    LocalVideoQuickFilter.FINISHED -> playback[video.key]?.state is LocalVideoPlaybackState.Watched
}

private val videoCollator: Collator = Collator.getInstance(Locale.CHINA).apply {
    strength = Collator.PRIMARY
}

private fun videoComparator(
    sort: LocalVideoSort,
    ascending: Boolean,
    playback: Map<String, LocalVideoPlayback>,
): Comparator<LocalVideo> = Comparator { left, right ->
    val result = when (sort) {
        LocalVideoSort.TITLE -> videoCollator.compare(left.title, right.title)
        LocalVideoSort.DATE -> left.dateModifiedMs.compareTo(right.dateModifiedMs)
        LocalVideoSort.PLAYED -> lastPlayed(left, playback).compareTo(lastPlayed(right, playback))
        LocalVideoSort.STATUS -> statusRank(left, playback).compareTo(statusRank(right, playback))
        LocalVideoSort.DURATION -> left.durationMs.compareTo(right.durationMs)
        LocalVideoSort.SIZE -> left.sizeBytes.compareTo(right.sizeBytes)
        LocalVideoSort.RESOLUTION -> left.pixelCount.compareTo(right.pixelCount)
        LocalVideoSort.PATH -> videoCollator.compare(
            "${left.relativeDirectory}/${left.title}",
            "${right.relativeDirectory}/${right.title}",
        )
        LocalVideoSort.FPS -> left.frameRate.compareTo(right.frameRate)
        LocalVideoSort.TYPE -> videoCollator.compare(left.extension, right.extension)
    }
    val tied = if (result != 0) result else videoCollator.compare(left.title, right.title)
    if (ascending) tied else -tied
}

private fun folderComparator(
    sort: LocalVideoSort,
    ascending: Boolean,
): Comparator<LocalVideoFolder> = Comparator { left, right ->
    val result = when (sort) {
        LocalVideoSort.TITLE -> videoCollator.compare(left.name, right.name)
        LocalVideoSort.DATE -> left.newestDateMs.compareTo(right.newestDateMs)
        LocalVideoSort.PLAYED -> left.newestPlayedAtMs.compareTo(right.newestPlayedAtMs)
        LocalVideoSort.STATUS -> right.unplayedCount.compareTo(left.unplayedCount)
        LocalVideoSort.DURATION -> left.totalDurationMs.compareTo(right.totalDurationMs)
        LocalVideoSort.SIZE -> left.totalSizeBytes.compareTo(right.totalSizeBytes)
        LocalVideoSort.RESOLUTION -> (left.maxWidth.toLong() * left.maxHeight)
            .compareTo(right.maxWidth.toLong() * right.maxHeight)
        LocalVideoSort.PATH -> videoCollator.compare(left.path, right.path)
        LocalVideoSort.FPS -> left.maxFrameRate.compareTo(right.maxFrameRate)
        LocalVideoSort.TYPE -> videoCollator.compare(left.name, right.name)
    }
    val tied = if (result != 0) result else videoCollator.compare(left.name, right.name)
    if (ascending) tied else -tied
}

private fun lastPlayed(video: LocalVideo, playback: Map<String, LocalVideoPlayback>): Long =
    playback[video.key]?.lastPlayedAtMs ?: 0L

private fun statusRank(video: LocalVideo, playback: Map<String, LocalVideoPlayback>): Int =
    when (playback[video.key]?.state) {
        LocalVideoPlaybackState.Unplayed, null -> 0
        is LocalVideoPlaybackState.Watching -> 1
        LocalVideoPlaybackState.Watched -> 2
    }
