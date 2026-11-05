package io.github.bilibox.core.model

/** 搜索业务选项；显示文案和服务端参数由各自所属层映射。 */
internal enum class SearchOrder {
    RELEVANCE,
    NEWEST,
    MOST_PLAYED,
    MOST_DANMAKU,
    MOST_SAVED,
}

internal enum class SearchDuration {
    UNDER_TEN,
    TEN_TO_THIRTY,
    THIRTY_TO_SIXTY,
    OVER_SIXTY,
}

internal enum class SearchTimeRange {
    LAST_WEEK,
    LAST_MONTH,
    LAST_YEAR,
}
