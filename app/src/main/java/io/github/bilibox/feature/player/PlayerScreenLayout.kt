package io.github.bilibox.feature.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

@Composable
internal fun rememberVerticalFullScreenProgress(fullScreen: Boolean, verticalFullScreen: Boolean): State<Float> = animateFloatAsState(
    targetValue = if (!fullScreen && verticalFullScreen) 1f else 0f,
    animationSpec = tween(360, easing = FastOutSlowInEasing),
    label = "竖屏全屏展开",
)

/** Resize the existing video surface; keep details at their normal height as they slide away. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerScreenLayout(
    fullScreen: Boolean,
    verticalFullScreen: Boolean,
    progress: State<Float>,
    content: @Composable (detailsVisible: Boolean) -> Unit,
) {
    val showDetails by remember(fullScreen, verticalFullScreen, progress) {
        derivedStateOf { !fullScreen && (!verticalFullScreen || progress.value < 1f) }
    }
    Layout(
        modifier = Modifier.fillMaxSize().background(Color.Black)
            // Keep the top edge fixed even when the fullscreen controls hide the status bar.
            .then(if (fullScreen) Modifier else Modifier.windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility))
            .clipToBounds(),
        content = { content(showDetails) },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val compactHeight = (width * 9f / 16f).roundToInt().coerceAtMost(height)
        val videoHeight = if (fullScreen) {
            height
        } else {
            (compactHeight + (height - compactHeight) * progress.value).roundToInt()
        }
        val videoPlaceable = measurables[0].measure(Constraints.fixed(width, videoHeight))
        // A fixed details height avoids reflowing text throughout the transition.
        val detailsPlaceable = measurables.getOrNull(1)?.measure(Constraints.fixed(width, height - compactHeight))
        layout(width, height) {
            videoPlaceable.placeRelative(0, 0)
            detailsPlaceable?.placeRelative(0, videoHeight)
        }
    }
}
