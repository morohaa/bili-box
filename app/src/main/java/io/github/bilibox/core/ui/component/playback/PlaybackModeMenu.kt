package io.github.bilibox.core.ui.component.playback

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.bilibox.core.playback.PlaybackMode
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.ui.theme.PlaybackInk

@Composable
internal fun PlaybackModeMenu(
    mode: PlaybackMode,
    onModeChange: (PlaybackMode) -> Unit,
    anchor: @Composable (open: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        anchor { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            PlaybackMode.standard.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label, color = if (mode == option) OnlinePink else PlaybackInk) },
                    modifier = Modifier.semantics { selected = mode == option },
                    onClick = {
                        onModeChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
