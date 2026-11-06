package io.github.bilibox.core.ui.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.bilibox.core.ui.theme.OnlinePink

@Composable
internal fun EqualizerBars() {
    val transition = rememberInfiniteTransition(label = "音律条跳动")
    val scales = listOf(
        transition.animateFloat(
            initialValue = .35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(430, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "音律条 1",
        ),
        transition.animateFloat(
            initialValue = .55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(560, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "音律条 2",
        ),
        transition.animateFloat(
            initialValue = .42f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(490, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "音律条 3",
        ),
    )
    Row(
        Modifier.width(20.dp).height(16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val heights = listOf(8.dp, 13.dp, 10.dp)
        heights.forEachIndexed { index, height ->
            val scale by scales[index]
            Box(
                Modifier.width(3.dp).height(height)
                    .graphicsLayer {
                        scaleY = scale
                        transformOrigin = TransformOrigin(.5f, 1f)
                    }
                    .clip(RoundedCornerShape(2.dp))
                    .background(OnlinePink)
            )
        }
    }
}
