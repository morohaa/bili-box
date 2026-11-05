package io.github.bilibox.data.bili

import io.github.bilibox.core.model.SearchDuration
import io.github.bilibox.core.model.SearchOrder
import io.github.bilibox.core.model.SearchTimeRange

/** 将业务筛选选项转换为 B 站 Web 搜索接口参数。 */
internal fun buildVideoSearchParameters(
    keyword: String,
    page: Int,
    pageSize: Int,
    order: SearchOrder,
    duration: SearchDuration?,
    timeRange: SearchTimeRange?,
    nowSeconds: Long = System.currentTimeMillis() / 1000L,
): Map<String, String> = buildMap {
    put("search_type", "video")
    put("keyword", keyword.trim())
    put("page", page.toString())
    put("pagesize", pageSize.toString())
    put(
        "order",
        when (order) {
            SearchOrder.RELEVANCE -> "totalrank"
            SearchOrder.NEWEST -> "pubdate"
            SearchOrder.MOST_PLAYED -> "click"
            SearchOrder.MOST_DANMAKU -> "dm"
            SearchOrder.MOST_SAVED -> "stow"
        },
    )
    put("web_location", "1430654")
    if (duration != null) {
        put(
            "duration",
            when (duration) {
                SearchDuration.UNDER_TEN -> "1"
                SearchDuration.TEN_TO_THIRTY -> "2"
                SearchDuration.THIRTY_TO_SIXTY -> "3"
                SearchDuration.OVER_SIXTY -> "4"
            },
        )
    }
    if (timeRange != null) {
        val days = when (timeRange) {
            SearchTimeRange.LAST_WEEK -> 7
            SearchTimeRange.LAST_MONTH -> 30
            SearchTimeRange.LAST_YEAR -> 365
        }
        put("pubtime_begin_s", (nowSeconds - days * 86_400L).toString())
        put("pubtime_end_s", nowSeconds.toString())
    }
}
