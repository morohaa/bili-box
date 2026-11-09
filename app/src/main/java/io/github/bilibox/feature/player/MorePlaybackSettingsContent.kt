package io.github.bilibox.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon

@Composable
internal fun MorePlaybackSettingsContent(
    previewEnabled: Boolean,
    onPreviewChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onQueueSettings: (() -> Unit)?,
) {
    Column(Modifier.fillMaxWidth().testTag("more-playback-settings")) {
        Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack, Modifier.size(40.dp).semantics { contentDescription = "返回播放设置" }) {
                AppGlyphIcon(AppIcon.BACK, SettingsInk, Modifier.size(20.dp), 2f)
            }
            Text("更多播放设置", color = SettingsInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SettingsCardSurface)) {
            ToggleSettingRow(
                label = "进度条画面预览",
                checked = previewEnabled,
                icon = PlayerSettingsIcon.PLAYBACK_SETTINGS,
                onCheckedChange = onPreviewChange,
            )
            Text(
                "拖动进度条时显示目标画面，视频继续播放",
                color = SettingsSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
            )
            if (onQueueSettings != null) {
                SettingsDivider()
                ActionSettingRow("播放顺序", PlayerSettingsIcon.LOOP, onClick = onQueueSettings)
            }
        }
    }
}
