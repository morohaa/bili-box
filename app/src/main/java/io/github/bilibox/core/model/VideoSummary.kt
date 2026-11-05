package io.github.bilibox.core.model

internal data class VideoSummary(
    val title: String,
    val author: String,
    val playCount: String,
    val commentCount: Long,
    val publishTime: Long,
    val duration: String,
    val bvid: String,
    val coverUrl: String
)
