package io.github.bilibox.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.util.formatSpeedBadge
import kotlin.math.abs

/** Speed entry point used by the player chrome. */
@Composable
internal fun SpeedBarChip(
    speed: Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val active = abs(speed - 1f) > 0.001f
    Row(
        modifier.height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (active) OnlinePink.copy(alpha = .16f) else Color.White.copy(alpha = .10f)
            )
            .border(
                1.dp,
                if (active) OnlinePink.copy(alpha = .55f) else Color.White.copy(alpha = .10f),
                RoundedCornerShape(8.dp)
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "倍速",
            color = if (active) OnlinePink else Color.White.copy(alpha = .92f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
        if (active) {
            Spacer(Modifier.width(4.dp))
            Text(
                formatSpeedBadge(speed),
                color = OnlinePink,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
