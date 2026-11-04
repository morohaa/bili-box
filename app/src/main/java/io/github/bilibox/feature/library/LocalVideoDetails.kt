package io.github.bilibox.feature.library

import androidx.compose.ui.graphics.Color
import io.github.bilibox.data.localvideo.LocalVideo

/*
 * 本地视频播放页下半部分的详情设计。
 *
 * 参考「B 站本地播放器」原型：来源卡、可展开标题、图标元信息、五个快捷操作、
 * 续播条，以及同文件夹队列；第二个标签页直接展示完整的媒体信息。
 */

internal val DetailBlue = Color(0xFF00AEEC)
internal val DetailBlueSoft = Color(0xFFE8F7FD)
internal val DetailLine = Color(0xFFE9EAEC)
internal val DetailCard = Color(0xFFF6F7F8)
internal val DetailSecondary = Color(0xFF61666D)
internal val DetailOrange = Color(0xFFFF8C00)
internal val DetailOrangeSoft = Color(0xFFFFF7E8)
internal val DetailPinkSoft = Color(0xFFFFF1F5)

internal fun localVideoFolderPath(video: LocalVideo): String {
    val folder = video.relativeDirectory.split('/')
        .filter { it.isNotBlank() }
        .joinToString(" / ")
        .ifBlank { "根目录" }
    return "内部存储 / $folder"
}

internal fun localVideoFullPath(video: LocalVideo): String =
    "${localVideoFolderPath(video)} / ${video.name}"
