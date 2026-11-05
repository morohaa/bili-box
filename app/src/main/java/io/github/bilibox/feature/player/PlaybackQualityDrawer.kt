package io.github.bilibox.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.theme.OnlinePink

@Composable
internal fun PlaybackQualityDrawer(
    options: List<PlayerQualityOption>,
    selectedQuality: Int?,
    onDismiss: () -> Unit,
    onQualitySelected: (Int) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color.Transparent)
                .clickable(onClick = onDismiss)
                .semantics { contentDescription = "关闭清晰度选择" }
        )
        Column(
            Modifier.align(Alignment.CenterEnd)
                .width(360.dp).fillMaxHeight()
                .padding(end = 8.dp, top = 8.dp, bottom = 8.dp)
                .navigationBarsPadding()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xF214161E))
                .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "清晰度与音质",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "切换后保持当前播放进度",
                        color = Color.White.copy(alpha = .45f),
                        fontSize = 10.5.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                        .semantics { contentDescription = "收起清晰度选择" }
                ) {
                    Text("×", color = Color.White.copy(alpha = .72f), fontSize = 22.sp)
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(bottom = 8.dp),
                color = Color.White.copy(alpha = .08f)
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                options.forEach { option ->
                    val selected = option.qn == selectedQuality
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
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
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                            if (option.description.isNotBlank()) {
                                Text(
                                    option.description,
                                    color = Color.White.copy(alpha = .45f),
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
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
}
