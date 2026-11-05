package io.github.bilibox.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.theme.Ink
import io.github.bilibox.core.ui.theme.OnlineMuted
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.util.formatSpeedBadge

private val MoreCard = Color.White
private val MoreLine = Color(0xFFE3E5E7)
private val MoreLabel = Color(0xFF18191C)

/** 播放器右上角的更多入口：倍速、定时关闭与截图都收进同一个菜单。 */
@Composable
internal fun PlayerMoreButton(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    speed: Float,
    sleepTimerActive: Boolean,
    screenshotEnabled: Boolean,
    onSpeedClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onScreenshotClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        IconButton(
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier.size(40.dp).semantics { contentDescription = "更多" },
        ) {
            MoreDotsIcon(Modifier.size(20.dp), Color.White.copy(alpha = .92f))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.width(190.dp),
            offset = DpOffset((-150).dp, 4.dp),
            shape = RoundedCornerShape(14.dp),
            containerColor = MoreCard,
            tonalElevation = 0.dp,
            shadowElevation = 14.dp,
            border = BorderStroke(1.dp, MoreLine),
        ) {
            Column(Modifier.padding(horizontal = 5.dp, vertical = 5.dp)) {
                MoreMenuRow(
                    icon = AppIcon.SPEED,
                    label = "倍速",
                    value = formatSpeedBadge(speed),
                    onDismiss = { onExpandedChange(false) },
                    onClick = onSpeedClick,
                )
                MoreMenuRow(
                    icon = AppIcon.TIMER,
                    label = "定时关闭",
                    value = if (sleepTimerActive) "已开启" else "",
                    valueColor = if (sleepTimerActive) OnlinePink else OnlineMuted,
                    onDismiss = { onExpandedChange(false) },
                    onClick = onSleepTimerClick,
                )
                MoreMenuRow(
                    icon = AppIcon.CAMERA,
                    label = "截图",
                    enabled = screenshotEnabled,
                    onDismiss = { onExpandedChange(false) },
                    onClick = onScreenshotClick,
                )
            }
        }
    }
}

@Composable
private fun MoreMenuRow(
    icon: AppIcon,
    label: String,
    value: String = "",
    valueColor: Color = OnlineMuted,
    enabled: Boolean = true,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
) {
    val color = if (enabled) MoreLabel else MoreLabel.copy(alpha = .35f)
    Row(
        Modifier.fillMaxWidth().height(42.dp)
            .clip(RoundedCornerShape(9.dp))
            .clickable(enabled = enabled, role = Role.Button) {
                onDismiss()
                onClick()
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyphIcon(icon, if (enabled) Ink else Ink.copy(alpha = .35f), Modifier.size(15.dp), 1.8f)
        Spacer(Modifier.width(9.dp))
        Text(label, color = color, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
        if (value.isNotBlank()) {
            Text(
                value,
                color = if (enabled) valueColor else valueColor.copy(alpha = .35f),
                fontSize = 11.5.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MoreDotsIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier) {
        val radius = size.minDimension * .075f
        listOf(.25f, .5f, .75f).forEach { fraction ->
            drawCircle(tint, radius, Offset(size.width / 2f, size.height * fraction))
        }
    }
}
