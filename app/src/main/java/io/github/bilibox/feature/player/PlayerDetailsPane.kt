package io.github.bilibox.feature.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.theme.Ink
import io.github.bilibox.core.ui.theme.Muted
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.ui.theme.Pink
import io.github.bilibox.feature.library.LocalGlyphIcon
import io.github.bilibox.feature.library.LocalIcon

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
    Box(
        modifier.fillMaxWidth().background(detailsBackground)
            .navigationBarsPadding().clipToBounds()
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(detailScroll)
        ) {
            if (showDetailTabs) {
                var tabCenters by remember { mutableStateOf<Map<Int, Float>>(emptyMap()) }
                val density = LocalDensity.current
                val indicatorWidth = 28.dp
                val indicatorWidthPx = with(density) { indicatorWidth.toPx() }
                val activeCenter = tabCenters[detailsTab]
                Box(Modifier.fillMaxWidth().height(44.dp)) {
                    Row(
                        Modifier.fillMaxSize().padding(start = 4.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        detailTabs.forEachIndexed { index, label ->
                            val selected = detailsTab == index
                            val textColor by animateColorAsState(
                                targetValue = if (selected) OnlinePink else Color(0xFF61666D),
                                animationSpec = tween(180),
                                label = "详情标签颜色",
                            )
                            Box(
                                Modifier.fillMaxHeight()
                                    .clickable { onDetailsTabChange(index) }
                                    .onGloballyPositioned { coordinates ->
                                        val center = coordinates.positionInParent().x +
                                            coordinates.size.width / 2f
                                        if (tabCenters[index] != center) {
                                            tabCenters = tabCenters + (index to center)
                                        }
                                    }
                                    .padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    color = textColor,
                                    fontSize = 15.sp,
                                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                )
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        if (sourcePill) {
                            Row(
                                Modifier.height(32.dp).clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFF6F7F8))
                                    .border(1.dp, Color(0xFFE3E5E7), RoundedCornerShape(16.dp))
                                    .padding(start = 12.dp, end = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    sourceLabel,
                                    color = Color(0xFF9499A0),
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    Modifier.size(26.dp).clip(CircleShape).background(Color.White)
                                        .border(1.dp, Color(0xFFE3E5E7), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    LocalGlyphIcon(LocalIcon.TV, OnlinePink, Modifier.size(14.dp), 1.7f)
                                }
                            }
                        } else {
                            Text(sourceLabel, color = Muted, fontSize = 12.sp)
                        }
                    }
                    if (activeCenter != null) {
                        val indicatorX by animateDpAsState(
                            targetValue = with(density) {
                                ((activeCenter - indicatorWidthPx / 2f).coerceAtLeast(0f)).toDp()
                            },
                            animationSpec = tween(220, easing = FastOutSlowInEasing),
                            label = "详情标签下划线",
                        )
                        Box(
                            Modifier.align(Alignment.BottomStart)
                                .offset(x = indicatorX)
                                .width(indicatorWidth).height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(OnlinePink)
                        )
                    }
                }
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
    }
}
