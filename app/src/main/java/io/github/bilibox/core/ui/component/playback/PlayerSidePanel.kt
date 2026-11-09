package io.github.bilibox.core.ui.component.playback

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * 横屏播放器统一的侧边面板。
 *
 * 选集、倍速、清晰度和弹幕设置共用同一套尺寸、表面、标题栏与关闭按钮，只有面板内部的
 * 具体内容不同。参考 docs 中的 player-panels.html：右侧贴边、380dp 宽、
 * 48dp 标题栏、16dp 左右与 24dp 底部留白；标题与内容之间用 1dp 分割线区分，不放顶部留白。
 */

internal val PlayerSidePanelWidth = 380.dp
internal val PlayerSidePanelSurface = Color(0xF7191A20)
internal val PlayerSidePanelLine = Color.White.copy(alpha = .09f)
internal val PlayerSidePanelTitle = Color(0xFFF6F6F8)
internal val PlayerSidePanelMuted = Color(0xFF92939C)

@Composable
internal fun PlayerSidePanel(
    title: String,
    onDismiss: () -> Unit,
    closeDescription: String,
    modifier: Modifier = Modifier,
    titleBadge: String? = null,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Match the reference's min(380px, 100%) so split-screen windows stay usable.
        val panelWidth = minOf(maxWidth, PlayerSidePanelWidth)
        Column(
            Modifier.align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(panelWidth)
                .shadow(elevation = 18.dp, shape = RectangleShape, clip = false)
                .background(PlayerSidePanelSurface)
                .drawBehind {
                    drawLine(
                        color = PlayerSidePanelLine,
                        start = Offset.Zero,
                        end = Offset(0f, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding()
        ) {
            PlayerSidePanelHeader(
                title = title,
                badge = titleBadge,
                onDismiss = onDismiss,
                closeDescription = closeDescription,
            )
            HorizontalDivider(color = PlayerSidePanelLine, thickness = 1.dp)
            Column(
                Modifier.fillMaxWidth()
                    .weight(1f)
                    .padding(contentPadding),
                content = content,
            )
        }
    }
}

@Composable
internal fun PlayerSidePanelHeader(
    title: String,
    badge: String?,
    onDismiss: () -> Unit,
    closeDescription: String,
) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = buildAnnotatedString {
                append(title)
                if (!badge.isNullOrBlank()) {
                    withStyle(
                        SpanStyle(
                            color = PlayerSidePanelMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                        )
                    ) {
                        append(" ")
                        append(badge)
                    }
                }
            },
            color = PlayerSidePanelTitle,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        PlayerSidePanelCloseButton(onDismiss, closeDescription)
    }
}

@Composable
internal fun PlayerSidePanelCloseButton(
    onDismiss: () -> Unit,
    closeDescription: String,
) {
    Box(
        Modifier.size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onDismiss)
            .semantics { contentDescription = closeDescription },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(12.dp)) {
            val stroke = 1.7.dp.toPx()
            val color = Color(0xFFAAAAB2)
            drawLine(color, Offset(0f, 0f), Offset(size.width, size.height), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width, 0f), Offset(0f, size.height), stroke, StrokeCap.Round)
        }
    }
}
