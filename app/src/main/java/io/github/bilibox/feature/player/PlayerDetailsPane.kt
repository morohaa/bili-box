package io.github.bilibox.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.playback.SpeedPanel
import io.github.bilibox.core.ui.component.playback.SpeedPanelMode
import io.github.bilibox.core.ui.theme.Ink
import io.github.bilibox.core.ui.theme.Muted
import io.github.bilibox.core.ui.theme.Pink
import io.github.bilibox.data.player.LightsOffMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerDetailsPane(
    modifier: Modifier = Modifier,
    detailsBackground: Color,
    showDetailTabs: Boolean,
    detailTabs: List<String>,
    detailsTab: Int,
    onDetailsTabChange: (Int) -> Unit,
    sourcePill: Boolean,
    sourceLabel: String,
    danmakuEnabled: Boolean,
    onOpenDanmakuComposer: (() -> Unit)?,
    onToggleDanmaku: (() -> Unit)?,
    lightsOff: Boolean,
    lightsOffMode: LightsOffMode,
    onToggleLights: (() -> Unit)?,
    onSelectLightsOffMode: (LightsOffMode) -> Unit,
    playbackError: String?,
    onRetry: () -> Unit,
    commentMessage: String,
    duration: Long,
    positionMs: Long,
    speed: Float,
    screenshotEnabled: Boolean,
    sleepTimerActive: Boolean,
    onOpenSpeedPanel: () -> Unit,
    onOpenPlaybackModeSheet: () -> Unit,
    onOpenSleepTimerSheet: () -> Unit,
    onTakeScreenshot: () -> Unit,
    onSeekTo: (Long) -> Unit,
    details: @Composable (Long) -> Unit,
    extraDetailPane: (@Composable (Long) -> Unit)?,
    episodeOverlay: (@Composable () -> Unit)?,
    episodeOverlayVisible: Boolean,
    speedMenuExpanded: Boolean,
    onDismissSpeedSheet: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    playbackModeExpanded: Boolean,
    playbackModeSheet: (@Composable (onDismiss: () -> Unit) -> Unit)?,
    onDismissPlaybackMode: () -> Unit,
) {
    val overlayVisible = episodeOverlayVisible && episodeOverlay != null
    val activePane = if (showDetailTabs) detailsTab else 0
    val detailScroll = rememberScrollState()
    LaunchedEffect(activePane) { detailScroll.scrollTo(0) }
    val blurRadius by animateDpAsState(
        targetValue = if (lightsOff) lightsOffMode.contentBlurRadius() else 0.dp,
        animationSpec = tween(220),
        label = "关灯模糊半径",
    )
    Box(
        modifier.fillMaxWidth().background(detailsBackground)
            .navigationBarsPadding().clipToBounds()
    ) {
        Column(
            Modifier.fillMaxSize()
                .verticalScroll(detailScroll, enabled = !lightsOff)
                .then(if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier)
        ) {
            if (showDetailTabs) {
                PlayerDetailBar(
                    tabs = detailTabs,
                    selectedTab = detailsTab,
                    onSelectTab = onDetailsTabChange,
                    sourcePill = sourcePill,
                    sourceLabel = sourceLabel,
                    danmakuEnabled = danmakuEnabled,
                    onOpenDanmakuComposer = onOpenDanmakuComposer,
                    onToggleDanmaku = onToggleDanmaku,
                    lightsOffMode = lightsOffMode,
                    onToggleLights = onToggleLights,
                    onSelectLightsOffMode = onSelectLightsOffMode,
                )
                HorizontalDivider(color = if (detailsBackground == Color.White) Color(0xFFE3E5E7) else Color.White.copy(alpha = .08f), thickness = 1.dp)
            }

            val detailActions = PlayerDetailActions(
                speed = speed,
                positionMs = positionMs,
                screenshotEnabled = screenshotEnabled,
                openSpeedPanel = onOpenSpeedPanel,
                openPlaybackModeSheet = onOpenPlaybackModeSheet,
                openSleepTimerSheet = onOpenSleepTimerSheet,
                sleepTimerActive = sleepTimerActive,
                takeScreenshot = onTakeScreenshot,
                seekTo = onSeekTo,
            )
            CompositionLocalProvider(LocalPlayerDetailActions provides detailActions) {
                AnimatedContent(
                    targetState = activePane,
                    transitionSpec = {
                        val forward = targetState > initialState
                        val enter = fadeIn(tween(180)) +
                            slideInHorizontally(
                                animationSpec = tween(220, easing = FastOutSlowInEasing),
                                initialOffsetX = { width -> if (forward) width / 8 else -width / 8 },
                            )
                        val exit = fadeOut(tween(120)) +
                            slideOutHorizontally(
                                animationSpec = tween(160, easing = FastOutSlowInEasing),
                                targetOffsetX = { width -> if (forward) -width / 8 else width / 8 },
                            )
                        enter togetherWith exit
                    },
                    label = "简介与媒体信息切换",
                ) { tab ->
                    when {
                        tab == 0 -> Column(Modifier.fillMaxWidth()) {
                            details(duration)
                            playbackError?.let { message ->
                                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
                                    Text("播放失败：$message", color = Color(0xFFB42318), fontSize = 12.sp)
                                    TextButton(onClick = onRetry) { Text("重试", color = Pink) }
                                }
                            }
                        }

                        extraDetailPane != null -> extraDetailPane(duration)

                        else -> {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp)) {
                                Text(detailTabs.getOrNull(1) ?: "评论", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                Text(commentMessage, color = Muted, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        val overlay = episodeOverlay
        if (overlay != null) {
            PlayerDetailsOverlay(visible = overlayVisible) { overlay() }
        }

        // Keep the portrait sheet inside the details pane so the video
        // stays bright and the dimmed details remain visible behind it.
        PlayerDrawerVisibility(visible = speedMenuExpanded, fromBottom = true) {
            SpeedPanel(
                speed = speed,
                mode = SpeedPanelMode.Sheet,
                onDismiss = onDismissSpeedSheet,
                onSpeedChange = onSpeedChange
            )
        }

        val modeSheet = playbackModeSheet
        if (modeSheet != null) {
            PlayerDrawerVisibility(visible = playbackModeExpanded, fromBottom = true) {
                modeSheet { onDismissPlaybackMode() }
            }
        }

        val exitLightsOff = onToggleLights
        if (lightsOff && exitLightsOff != null) {
            PlayerLightsOffOverlay(
                mode = lightsOffMode,
                onExit = exitLightsOff,
                onSelectMode = onSelectLightsOffMode,
            )
        }
    }
}
