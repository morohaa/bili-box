package io.github.bilibox.feature.player

import android.util.Size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.util.formatPlaybackTime

@Composable
internal fun PlaybackTimeLabel(
    position: Long,
    duration: Long,
    modifier: Modifier = Modifier,
    fullScreen: Boolean = false,
) {
    Text(
        "${formatPlaybackTime(position)} / ${formatPlaybackTime(duration, unknownWhenZero = true)}",
        modifier = modifier,
        color = Color.White.copy(alpha = .9f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        maxLines = 1
    )
}

@Composable
internal fun PlayerBarIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable BoxScope.() -> Unit,
) {
    val description = contentDescription
    Box(
        modifier.size(32.dp).clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = description },
        contentAlignment = Alignment.Center,
        content = icon
    )
}

@Composable
internal fun PlayerBarAction(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier.height(32.dp).clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = .92f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
internal fun PlayerQualityChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier.height(30.dp).clip(RoundedCornerShape(8.dp))
            .background(OnlinePink.copy(alpha = .18f))
            .border(1.dp, OnlinePink.copy(alpha = .55f), RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "画质 ${label.ifBlank { "选择" }}" }
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label.ifBlank { "画质" },
            color = OnlinePink,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
internal fun NextEpisodeIcon(modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier) {
        val u = size.width / 24f
        drawPath(
            Path().apply {
                moveTo(5f * u, 5.5f * u)
                lineTo(15f * u, 12f * u)
                lineTo(5f * u, 18.5f * u)
                close()
            },
            Color.White
        )
        drawRoundRect(
            Color.White,
            Offset(17.5f * u, 5f * u),
            androidx.compose.ui.geometry.Size(2.5f * u, 14f * u),
            androidx.compose.ui.geometry.CornerRadius(1f * u)
        )
    }
}
