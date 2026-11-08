package io.github.bilibox.data.localvideo

import java.text.Collator
import java.util.Locale

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

/**
 * 设置弹窗的文件夹预览样本：取条目最多的文件夹，进入某个目录后也能看到示例。
 */
internal fun localVideoSampleFolder(
    videos: List<LocalVideo>,
    playback: Map<String, LocalVideoPlayback>,
): LocalVideoFolder? = videos.asSequence()
    .filter { it.relativeDirectory.isNotBlank() }
    .groupBy { it.relativeDirectory }
    .maxByOrNull { (_, group) -> group.size }
    ?.let { (path, group) -> aggregateFolder(path, group, playback) }

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
): Boolean = (allowHidden || settings.showHidden || !video.isHiddenPath) &&
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

private fun lastPlayed(video: LocalVideo, playback: Map<String, LocalVideoPlayback>): Long = playback[video.key]?.lastPlayedAtMs ?: 0L

private fun statusRank(video: LocalVideo, playback: Map<String, LocalVideoPlayback>): Int = when (playback[video.key]?.state) {
    LocalVideoPlaybackState.Unplayed, null -> 0
    is LocalVideoPlaybackState.Watching -> 1
    LocalVideoPlaybackState.Watched -> 2
}
