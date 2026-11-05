package io.github.bilibox.data.bili

import io.github.bilibox.core.model.SearchDuration
import io.github.bilibox.core.model.SearchOrder
import io.github.bilibox.core.model.SearchTimeRange
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoSearchParametersTest {
    @Test
    fun `unfiltered request preserves paging and omits optional parameters`() {
        val params = buildVideoSearchParameters("  Kotlin  ", 2, 20, SearchOrder.RELEVANCE, null, null)

        assertEquals(
            mapOf(
                "search_type" to "video",
                "keyword" to "Kotlin",
                "page" to "2",
                "pagesize" to "20",
                "order" to "totalrank",
                "web_location" to "1430654",
            ),
            params,
        )
    }

    @Test
    fun `sort options retain the web API values`() {
        val orders = listOf(
            SearchOrder.RELEVANCE,
            SearchOrder.NEWEST,
            SearchOrder.MOST_PLAYED,
            SearchOrder.MOST_DANMAKU,
            SearchOrder.MOST_SAVED,
        )
        val values = orders.map {
            buildVideoSearchParameters("Kotlin", 1, 20, it, null, null)["order"]
        }

        assertEquals(listOf("totalrank", "pubdate", "click", "dm", "stow"), values)
    }

    @Test
    fun `duration options retain the web API values`() {
        val durations = listOf(
            SearchDuration.UNDER_TEN,
            SearchDuration.TEN_TO_THIRTY,
            SearchDuration.THIRTY_TO_SIXTY,
            SearchDuration.OVER_SIXTY,
        )
        val values = durations.map {
            buildVideoSearchParameters("Kotlin", 1, 20, SearchOrder.RELEVANCE, it, null)["duration"]
        }

        assertEquals(listOf("1", "2", "3", "4"), values)
    }

    @Test
    fun `combined filters send a complete time window in seconds`() {
        val nowSeconds = 1_800_000_000L
        val ranges = listOf(
            SearchTimeRange.LAST_WEEK to 604_800L,
            SearchTimeRange.LAST_MONTH to 2_592_000L,
            SearchTimeRange.LAST_YEAR to 31_536_000L,
        )
        ranges.forEach { (range, windowSeconds) ->
            val params = buildVideoSearchParameters(
                "Kotlin",
                3,
                50,
                SearchOrder.NEWEST,
                SearchDuration.TEN_TO_THIRTY,
                range,
                nowSeconds,
            )

            assertEquals("pubdate", params["order"])
            assertEquals("2", params["duration"])
            assertEquals((nowSeconds - windowSeconds).toString(), params["pubtime_begin_s"])
            assertEquals(nowSeconds.toString(), params["pubtime_end_s"])
        }
    }
}
