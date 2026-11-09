package io.github.bilibox.feature.player.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import io.github.bilibox.core.ui.theme.playbackColor
import io.github.bilibox.data.localvideo.LocalVideo

/*
 * 本地视频播放页下半部分的详情设计。
 *
 * 参考「B 站本地播放器」原型：来源卡、可展开标题、图标元信息、五个快捷操作、
 * 续播条，以及同文件夹队列；第二个标签页直接展示完整的媒体信息。
 */

internal val DetailBlue = Color(0xFF00AEEC)
internal val DetailBlueSoft: Color
    @Composable @ReadOnlyComposable
    get() = playbackColor(Color(0xFFE8F7FD), Color(0xFF163E50))
internal val DetailLine: Color
    @Composable @ReadOnlyComposable
    get() = playbackColor(Color(0xFFE9EAEC), Color(0xFF363B43))
internal val DetailCard: Color
    @Composable @ReadOnlyComposable
    get() = playbackColor(Color(0xFFF6F7F8), Color(0xFF30343C))
internal val DetailSecondary: Color
    @Composable @ReadOnlyComposable
    get() = playbackColor(Color(0xFF61666D), Color(0xFFB6BDC7))
internal val DetailOrange = Color(0xFFFF8C00)
internal val DetailOrangeSoft: Color
    @Composable @ReadOnlyComposable
    get() = playbackColor(Color(0xFFFFF7E8), Color(0xFF443524))
internal val DetailPinkSoft: Color
    @Composable @ReadOnlyComposable
    get() = playbackColor(Color(0xFFFFF1F5), Color(0xFF48323E))

internal fun localVideoFolderPath(video: LocalVideo): String {
    val folder = video.relativeDirectory.split('/')
        .filter { it.isNotBlank() }
        .joinToString(" / ")
        .ifBlank { "根目录" }
    return "内部存储 / $folder"
}

internal fun localVideoFullPath(video: LocalVideo): String = "${localVideoFolderPath(video)} / ${video.name}"
