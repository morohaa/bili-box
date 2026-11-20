package io.github.bilibox.feature.online

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.bilibox.data.online.OnlineCommentsPreferences

@Stable
internal class OnlineCommentsPreferenceState(private val store: OnlineCommentsPreferences) {
    var enabled by mutableStateOf(store.load())
        private set

    fun update(value: Boolean) {
        enabled = value
        store.save(value)
    }
}

@Composable
internal fun rememberOnlineCommentsPreference(): OnlineCommentsPreferenceState {
    val context = LocalContext.current.applicationContext
    return remember(context) { OnlineCommentsPreferenceState(OnlineCommentsPreferences(context)) }
}
