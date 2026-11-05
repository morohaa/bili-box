package io.github.bilibox.feature.player

import androidx.compose.runtime.staticCompositionLocalOf

internal data class PlayerQualityOption(
    val qn: Int,
    val label: String,
    val description: String = "",
    val badge: String? = null,
)

/**
 * Live player hooks for the portrait details pane. The pane can reuse the
 * player's own speed sheet, screenshot pipeline and seek logic instead of
 * keeping a second copy of that state.
 */
internal class PlayerDetailActions(
    val speed: Float,
    val positionMs: Long,
    val screenshotEnabled: Boolean,
    val openSpeedPanel: () -> Unit,
    val openPlaybackModeSheet: () -> Unit,
    val openSleepTimerSheet: () -> Unit,
    val sleepTimerActive: Boolean,
    val takeScreenshot: () -> Unit,
    val seekTo: (Long) -> Unit,
)

internal val LocalPlayerDetailActions = staticCompositionLocalOf<PlayerDetailActions?> { null }
