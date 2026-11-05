package io.github.bilibox.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.bilibox.core.ui.theme.OnlinePink

@Composable
internal fun EqualizerBars() {
    Row(
        Modifier.width(20.dp).height(16.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val heights = listOf(8.dp, 13.dp, 10.dp)
        heights.forEach { height -> Box(Modifier.width(3.dp).height(height).clip(RoundedCornerShape(2.dp)).background(OnlinePink)) }
    }
}
