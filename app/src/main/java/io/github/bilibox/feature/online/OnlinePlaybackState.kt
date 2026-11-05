package io.github.bilibox.feature.online

internal sealed interface OnlinePlaybackState {
    data object Loading : OnlinePlaybackState
    data object Ready : OnlinePlaybackState
    data class Error(val message: String) : OnlinePlaybackState
}
