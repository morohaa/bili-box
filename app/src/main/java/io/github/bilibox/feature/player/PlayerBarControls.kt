package io.github.bilibox.feature.player

import android.util.Size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.testTag
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

/**
 * 控件隐藏后贴在视频底边的迷你进度条，只绘制已播放部分。
 * 不绘制轨道，避免在黑色画面上出现一条多余的灰线。
 */
@Composable
internal fun MiniPlaybackProgress(
    position: Long,
    duration: Long,
    modifier: Modifier = Modifier,
) {
    val fraction = if (duration > 0L) {
        (position.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }
    val accent = OnlinePink
    Canvas(modifier.height(2.dp)) {
        if (fraction <= 0f) return@Canvas
        drawLine(
            color = accent,
            start = Offset(0f, center.y),
            end = Offset(size.width * fraction, center.y),
            strokeWidth = size.height,
            cap = StrokeCap.Butt
        )
    }
}

/**
 * 控件隐藏后仍留在视频右下角的全屏入口：图标调淡，避免和画面抢注意力，
 * 位置和底栏的横屏按钮对齐，点一下就进入横屏全屏。
 */
@Composable
internal fun MiniFullscreenButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.size(40.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag("mini-fullscreen")
            .semantics { contentDescription = "切换横屏" },
        contentAlignment = Alignment.Center,
    ) {
        FullscreenIcon(
            modifier = Modifier.size(16.dp),
            tint = Color.White.copy(alpha = .7f),
        )
    }
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
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

/** 竖屏全屏开关：半透明白底小胶囊，文字随状态在“竖屏 / 退出竖屏”之间切换。 */
@Composable
internal fun PlayerVerticalScreenChip(
    active: Boolean,
    onClick: () -> Unit,
) {
    val label = if (active) "退出竖屏" else "竖屏"
    // Keep the visual capsule close to the reference; the 32dp box keeps it easy to tap.
    Box(
        Modifier.height(32.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = .2f))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                color = Color.White,
                fontSize = 10.sp,
                // Material3 的 24sp 默认行高会把字挤到胶囊底部，按 1.2 倍字号收紧。
                lineHeight = 12.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun PlayerQualityChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier.height(30.dp).clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "画质 ${label.ifBlank { "选择" }}" }
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label.ifBlank { "画质" },
            color = Color.White,
            fontSize = 13.sp,
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
