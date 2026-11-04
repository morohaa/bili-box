package io.github.bilibox.core.model

import io.github.bilibox.data.bili.BiliClient

internal data class SearchVideo(
    val title: String,
    val author: String,
    val playCount: String,
    val commentCount: Long,
    val publishTime: Long,
    val duration: String,
    val bvid: String,
    val coverUrl: String
)

internal sealed interface SearchState {
    data object Idle : SearchState
    data object Loading : SearchState
    data class Success(
        val items: List<SearchVideo>,
        val page: Int,
        val hasMore: Boolean,
        val order: SearchOrder,
        val total: Int = 0,
    ) : SearchState
    data class Error(val message: String, val page: Int = 1, val order: SearchOrder = SearchOrder.RELEVANCE) : SearchState
}

internal enum class SearchOrder(val apiValue: String, val label: String) {
    RELEVANCE(BiliClient.SEARCH_ORDER_TOTALRANK, "综合排序"),
    NEWEST(BiliClient.SEARCH_ORDER_PUBDATE, "最新发布"),
    MOST_PLAYED(BiliClient.SEARCH_ORDER_CLICK, "最多播放"),
    MOST_DANMAKU(BiliClient.SEARCH_ORDER_DM, "最多弹幕"),
    MOST_SAVED(BiliClient.SEARCH_ORDER_STOW, "最多收藏"),
}

/** B 站搜索接口按视频时长筛选，取值与 Web 端「筛选」面板一致。 */
internal enum class SearchDuration(val apiValue: String, val label: String) {
    UNDER_TEN("1", "10 分钟以内"),
    TEN_TO_THIRTY("2", "10–30 分钟"),
    THIRTY_TO_SIXTY("3", "30–60 分钟"),
    OVER_SIXTY("4", "60 分钟以上"),
}

/** 发布时间筛选，换算成接口需要的起止时间戳。 */
internal enum class SearchTimeRange(val days: Int, val label: String) {
    LAST_WEEK(7, "最近一周"),
    LAST_MONTH(30, "最近一个月"),
    LAST_YEAR(365, "最近一年"),
}

internal sealed interface PlaybackState {
    data object Loading : PlaybackState
    data object Ready : PlaybackState
    data class Error(val message: String) : PlaybackState
}
