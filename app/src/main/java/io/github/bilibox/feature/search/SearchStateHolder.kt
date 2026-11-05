package io.github.bilibox.feature.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.bilibox.core.model.SearchDuration
import io.github.bilibox.core.model.SearchOrder
import io.github.bilibox.core.model.SearchTimeRange
import io.github.bilibox.data.bili.BiliClient
import io.github.bilibox.data.search.toVideoSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Owns search requests, cancellation, paging results and UI state for the search feature. */
internal class SearchStateHolder(
    private val client: BiliClient,
    private val scope: CoroutineScope,
) {
    var state by mutableStateOf<SearchUiState>(SearchUiState.Idle)
        private set

    private var searchJob: Job? = null

    fun search(
        query: String,
        page: Int,
        order: SearchOrder,
        duration: SearchDuration?,
        timeRange: SearchTimeRange?,
    ) {
        val keyword = query.trim()
        if (keyword.isEmpty()) return

        searchJob?.cancel()
        state = SearchUiState.Loading
        searchJob = scope.launch {
            state = try {
                val result = client.searchVideos(
                    keyword = keyword,
                    page = page,
                    order = order,
                    duration = duration,
                    timeRange = timeRange,
                )
                SearchUiState.Success(
                    items = result.items.map(::toVideoSummary),
                    page = result.page,
                    hasMore = result.hasMore,
                    order = order,
                    total = result.total,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                SearchUiState.Error(error.message ?: "搜索失败，请稍后重试", page, order)
            }
        }
    }

    fun reset() {
        searchJob?.cancel()
        searchJob = null
        state = SearchUiState.Idle
    }
}
