package io.github.bilibox.feature.library

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.bilibox.core.ui.theme.BiliBoxTheme
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.data.localvideo.LocalVideoDisplayFields
import io.github.bilibox.data.localvideo.LocalVideoLibraryView
import io.github.bilibox.data.localvideo.LocalVideoPlayback
import io.github.bilibox.data.localvideo.LocalVideoPlaybackState
import io.github.bilibox.data.localvideo.LocalVideoSettings

@Preview(widthDp = 412, heightDp = 600)
@Composable
internal fun VideoStatesListPreview() = VideoStatesPreview()

@Preview(widthDp = 412, heightDp = 600)
@Composable
internal fun VideoStatesDarkPreview() = VideoStatesPreview(dark = true)

@Preview(widthDp = 412, heightDp = 600)
@Composable
internal fun VideoStatesGridPreview() = VideoStatesPreview(grid = true)

@Preview(widthDp = 412, heightDp = 600)
@Composable
internal fun VideoStatesWithoutCoverPreview() = VideoStatesPreview(thumbnail = false)

@Preview(widthDp = 412, heightDp = 900)
@Composable
internal fun VideoFieldsSettingsPreview() {
    val accent = Color(0xFFFB7299)
    val applied = LocalVideoSettings(
        fields = LocalVideoDisplayFields(
            thumbnail = false,
            duration = false,
            extension = false,
            size = false,
            date = false,
            path = true,
            frameRate = true,
        ),
    )
    var draft by remember { mutableStateOf(applied) }
    BiliBoxTheme(darkTheme = false, accent = accent, dynamicColor = false) {
        CompositionLocalProvider(LocalLibraryPalette provides libraryPalette(false, accent)) {
            LocalVideoSettingsDialog(
                applied = applied,
                draft = draft,
                sampleVideo = LocalVideo(
                    uri = Uri.parse("content://io.github.bilibox.preview/video/settings"),
                    name = "海边的日落.mp4",
                    durationMs = 134_000L,
                    sizeBytes = 8_388_608L,
                    relativePath = "Movies/旅行",
                    frameRate = 60f,
                ),
                samplePlayback = LocalVideoPlayback(LocalVideoPlaybackState.Watching(52_000L, 52f / 134f)),
                sampleFolder = null,
                onDraftChange = { draft = it },
                onReset = { draft = applied },
                onDismiss = {},
                onApply = {},
                onRescan = {},
            )
        }
    }
}

/** 独立样例覆盖三种进度，不读取或写入用户的播放记录。 */
@Composable
private fun VideoStatesPreview(dark: Boolean = false, grid: Boolean = false, thumbnail: Boolean = true) {
    val accent = Color(0xFFFB7299)
    val palette = remember(dark) { libraryPalette(dark, accent) }
    val videos = remember {
        val now = System.currentTimeMillis()
        listOf("海边的日落与慢镜头", "那些值得反复回味的片段", "在城市的清晨，慢慢醒来").mapIndexed { index, title ->
            LocalVideo(
                uri = Uri.parse("content://io.github.bilibox.preview/video/$index"),
                name = "$title.mp4",
                durationMs = 134_000L,
                sizeBytes = 8_388_608L,
                dateModifiedMs = now - 3 * 86_400_000L,
            )
        }
    }
    val playback = remember(videos) {
        videos.zip(
            listOf(
                LocalVideoPlaybackState.Unplayed,
                LocalVideoPlaybackState.Watching(positionMs = 52_000L, fraction = 52f / 134f),
                LocalVideoPlaybackState.Watched,
            ),
        ).associate { (video, state) -> video.key to LocalVideoPlayback(state) }
    }
    val settings = LocalVideoSettings(
        fields = LocalVideoDisplayFields(thumbnail = thumbnail, extension = false),
    )
    val view = LocalVideoLibraryView(
        folders = emptyList(),
        videos = videos,
        playlist = videos,
        currentPath = null,
        searching = false,
        activeFilter = null,
    )
    BiliBoxTheme(darkTheme = dark, accent = accent, dynamicColor = false) {
        CompositionLocalProvider(LocalLibraryPalette provides palette) {
            Box(Modifier.fillMaxSize().background(palette.background)) {
                if (grid) {
                    VideoGrid(view, settings, playback, 24.dp, {}, {}, {}, {})
                } else {
                    VideoList(view, settings, playback, 24.dp, {}, {}, {}, {})
                }
            }
        }
    }
}
