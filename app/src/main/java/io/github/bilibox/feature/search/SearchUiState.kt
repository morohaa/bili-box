package io.github.bilibox.feature.search

import io.github.bilibox.core.model.SearchOrder
import io.github.bilibox.core.model.VideoSummary

internal sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(
        val items: List<VideoSummary>,
        val page: Int,
        val hasMore: Boolean,
        val order: SearchOrder,
        val total: Int = 0,
    ) : SearchUiState
    data class Error(val message: String, val page: Int = 1, val order: SearchOrder = SearchOrder.RELEVANCE) : SearchUiState
}
