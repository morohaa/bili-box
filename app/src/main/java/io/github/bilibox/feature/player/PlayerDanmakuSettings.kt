package io.github.bilibox.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import io.github.bilibox.data.danmaku.DanmakuSettings
import io.github.bilibox.feature.danmaku.DanmakuSettingsDrawer
import io.github.bilibox.feature.danmaku.DanmakuSettingsSheet

@Composable
internal fun PlayerDanmakuSettings(
    visible: Boolean,
    fullScreen: Boolean,
    settings: DanmakuSettings,
    topInset: Dp?,
    onDismiss: () -> Unit,
    onSettingsChange: (DanmakuSettings) -> Unit,
) {
    if (fullScreen) {
        PlayerSidePanelHost(
            visible = visible,
            onDismiss = onDismiss,
            scrimDescription = "关闭弹幕设置",
        ) {
            DanmakuSettingsDrawer(settings, onDismiss, onSettingsChange)
        }
    } else {
        DanmakuSettingsSheet(visible, settings, topInset, onDismiss, onSettingsChange)
    }
}
