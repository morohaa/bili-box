package io.github.bilibox.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.util.formatPlaybackTime

/**
 * The last-minute banner over the video: "再看 10 分钟" extends, closing keeps
 * the timer running so it still stops playback on time.
 */
@Composable
internal fun SleepTimerWarning(
    remainingMs: Long,
    onExtend: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xCC141418))
            .semantics { contentDescription = "定时关闭提醒" }
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyphIcon(AppIcon.MOON, Color(0xFFFFB4C8), Modifier.size(14.dp), 1.8f)
        Spacer(Modifier.width(6.dp))
        Text(formatPlaybackTime(remainingMs), color = Color(0xFFFFB4C8), fontSize = 12.sp)
        Text(" 后暂停", color = Color.White.copy(alpha = .9f), fontSize = 12.sp)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.height(26.dp).clip(RoundedCornerShape(13.dp)).background(OnlinePink)
                .clickable(role = Role.Button, onClick = onExtend)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("再看 10 分钟", color = Color.White, fontSize = 12.sp)
        }
        Box(
            Modifier.size(26.dp).clip(CircleShape)
                .clickable(role = Role.Button, onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AppGlyphIcon(AppIcon.CLOSE, Color.White.copy(alpha = .7f), Modifier.size(13.dp), 2.2f)
        }
    }
}
