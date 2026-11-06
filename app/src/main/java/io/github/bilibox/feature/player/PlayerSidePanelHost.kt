package io.github.bilibox.feature.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/*
 * 横屏侧栏的统一宿主。
 *
 * 遮罩作为独立图层，打开时与侧栏同一帧直接出现，不参与侧栏的滑入动画；
 * 关闭时遮罩单独淡出，侧栏继续按原有节奏滑出。
 */

private const val SidePanelScrimAlpha = .30f

@Composable
internal fun PlayerSidePanelHost(
    visible: Boolean,
    onDismiss: () -> Unit,
    scrimDescription: String,
    content: @Composable () -> Unit,
) {
    val scrimAlpha = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        if (visible) {
            scrimAlpha.snapTo(SidePanelScrimAlpha)
        } else {
            scrimAlpha.animateTo(0f, animationSpec = tween(140))
        }
    }
    if (visible || scrimAlpha.value > 0f) {
        val interaction = remember { MutableInteractionSource() }
        Box(
            Modifier.fillMaxSize()
                .background(
                    Color.Black.copy(
                        alpha = if (visible) SidePanelScrimAlpha else scrimAlpha.value
                    )
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onDismiss,
                )
                .semantics { contentDescription = scrimDescription }
        )
    }
    PlayerDrawerVisibility(visible = visible) { content() }
}
