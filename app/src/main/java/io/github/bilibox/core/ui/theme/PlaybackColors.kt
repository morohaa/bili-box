package io.github.bilibox.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** 播放详情沿用浅色设计，深色配色跟随应用主题（包括手动选择的外观）。 */
@Composable
@ReadOnlyComposable
internal fun playbackColor(light: Color, dark: Color): Color = if (MaterialTheme.colorScheme.background.luminance() < .5f) dark else light

internal val PlaybackSurface: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surface
internal val PlaybackText: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurface
internal val PlaybackInk: Color
    @Composable @ReadOnlyComposable
    get() = playbackColor(Ink, PlaybackText)
internal val PlaybackMuted: Color
    @Composable @ReadOnlyComposable
    get() = playbackColor(OnlineMuted, Color(0xFF9CA3AD))
internal val PlaybackSecondary: Color
    @Composable @ReadOnlyComposable
    get() = playbackColor(OnlineSecondary, Color(0xFFB6BDC7))
internal val PlaybackCard: Color
    @Composable @ReadOnlyComposable
    get() = playbackColor(OnlineCardGray, Color(0xFF30343C))
internal val PlaybackStroke: Color
    @Composable @ReadOnlyComposable
    get() = playbackColor(OnlineStroke, Color(0xFF454B55))
internal val PlaybackDivider: Color
    @Composable @ReadOnlyComposable
    get() = playbackColor(OnlineDivider, Color(0xFF363B43))
