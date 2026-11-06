package io.github.bilibox.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.playback.PlayerSidePanel
import io.github.bilibox.core.ui.theme.OnlinePink

@Composable
internal fun PlaybackQualityDrawer(
    options: List<PlayerQualityOption>,
    selectedQuality: Int?,
    onDismiss: () -> Unit,
    onQualitySelected: (Int) -> Unit,
) {
    PlayerSidePanel(
        title = "画质",
        onDismiss = onDismiss,
        closeDescription = "收起清晰度选择",
    ) {
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val selected = option.qn == selectedQuality
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) {
                                OnlinePink.copy(alpha = .16f)
                            } else {
                                Color.White.copy(alpha = .05f)
                            }
                        )
                        .border(
                            1.dp,
                            if (selected) OnlinePink.copy(alpha = .75f) else Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onQualitySelected(option.qn) }
                        )
                        .semantics { contentDescription = "选择清晰度 ${option.label}" }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            option.label,
                            color = if (selected) OnlinePink else Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        option.badge?.let { badge ->
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier.clip(RoundedCornerShape(4.dp))
                                    .background(OnlinePink.copy(alpha = .22f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    badge,
                                    color = OnlinePink,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    if (selected) {
                        Box(
                            Modifier.clip(RoundedCornerShape(20.dp))
                                .background(OnlinePink)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "播放中",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
