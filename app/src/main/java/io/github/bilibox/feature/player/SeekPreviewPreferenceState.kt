package io.github.bilibox.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.bilibox.data.player.SeekPreviewPreferences

@Stable
internal class SeekPreviewPreferenceState(private val store: SeekPreviewPreferences) {
    var enabled by mutableStateOf(store.load())
        private set

    fun update(value: Boolean) {
        enabled = value
        store.save(value)
    }
}

@Composable
internal fun rememberSeekPreviewPreference(): SeekPreviewPreferenceState {
    val context = LocalContext.current.applicationContext
    return remember(context) { SeekPreviewPreferenceState(SeekPreviewPreferences(context)) }
}
