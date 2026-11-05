package io.github.bilibox.feature.search

import io.github.bilibox.core.model.SearchDuration
import io.github.bilibox.core.model.SearchOrder
import io.github.bilibox.core.model.SearchTimeRange

internal val SearchOrder.label: String
    get() = when (this) {
        SearchOrder.RELEVANCE -> "综合排序"
        SearchOrder.NEWEST -> "最新发布"
        SearchOrder.MOST_PLAYED -> "最多播放"
        SearchOrder.MOST_DANMAKU -> "最多弹幕"
        SearchOrder.MOST_SAVED -> "最多收藏"
    }

internal val SearchDuration.label: String
    get() = when (this) {
        SearchDuration.UNDER_TEN -> "10 分钟以内"
        SearchDuration.TEN_TO_THIRTY -> "10–30 分钟"
        SearchDuration.THIRTY_TO_SIXTY -> "30–60 分钟"
        SearchDuration.OVER_SIXTY -> "60 分钟以上"
    }

internal val SearchTimeRange.label: String
    get() = when (this) {
        SearchTimeRange.LAST_WEEK -> "最近一周"
        SearchTimeRange.LAST_MONTH -> "最近一个月"
        SearchTimeRange.LAST_YEAR -> "最近一年"
    }
