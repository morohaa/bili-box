package io.github.bilibox.feature.profile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ------------------------------------------------------------------ grid ---
internal class MyAction(
    val title: String,
    val tint: Color,
    val glyph: String,
    val badge: Int? = null,
    val onClick: () -> Unit,
)

private const val ICON_OFFLINE = "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4 M7 10l5 5 5-5 M12 15V3"
private const val ICON_HISTORY = "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8 M3 3v5h5 M12 7v5 M12 12l3 3"
private const val ICON_STAR = "M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26Z"
private const val ICON_WATCH_LATER = "M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0Z M10 8L16 12L10 16Z"
private const val ICON_LOGIN_WAYS = "M4 7.5a3.5 3.5 0 1 0 7 0a3.5 3.5 0 1 0-7 0Z M10 10l10 10 M16 16l2-2 M18 18l2-2"
private const val ICON_SESSION = "M7 11h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2Z M8 11V7a4 4 0 0 1 8 0v4 M11 16a1 1 0 1 0 2 0a1 1 0 1 0-2 0Z"
private const val ICON_THEME = "M12 21a9 9 0 1 1 9-9c0 2-1.5 3.5-3.5 3.5h-2a2 2 0 0 0-1.5 3.3c.4.5.6 1 .6 1.7 0 1.5-1.2 2.5-2.6 2.5z M6.5 10.5a1 1 0 1 0 2 0a1 1 0 1 0-2 0Z M11 7.5a1 1 0 1 0 2 0a1 1 0 1 0-2 0Z M15.5 10.5a1 1 0 1 0 2 0a1 1 0 1 0-2 0Z"
private const val ICON_ABOUT = "M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0Z M12 16v-4 M12 8h.01"
private const val ICON_LOCAL_VIDEO = "M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z M10 11l6 3-6 3Z"
private const val ICON_CLEAR_CACHE = "M3 6h18 M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6 M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2 M10 11v6 M14 11v6"
private const val ICON_HELP = "M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3 M12 17h.01"
private const val ICON_UPDATE = "M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8 M21 3v5h-5 M12 7v5l3 3"

@Composable
internal fun ActionGrid(
    darkMode: Boolean,
    background: Color,
    onOfflineCache: () -> Unit,
    onHistory: () -> Unit,
    onLocalVideos: () -> Unit,
    onLoginMethods: () -> Unit,
    onSession: () -> Unit,
    onTheme: () -> Unit,
    onAbout: () -> Unit,
    onClearCache: () -> Unit,
    onHelp: () -> Unit,
    onUpdate: () -> Unit,
    onFavorites: () -> Unit,
    onWatchLater: () -> Unit,
) {
    val rows = listOf(
        listOf(
            MyAction("离线缓存", MyBlue, ICON_OFFLINE, onClick = onOfflineCache),
            MyAction("历史记录", MyBlue, ICON_HISTORY, onClick = onHistory),
            MyAction("我的收藏", MyBlue, ICON_STAR, onClick = onFavorites),
            MyAction("稍后再看", MyBlue, ICON_WATCH_LATER, onClick = onWatchLater),
        ),
        listOf(
            MyAction("登录方式", MyRose, ICON_LOGIN_WAYS, onClick = onLoginMethods),
            MyAction("会话保存", MyRose, ICON_SESSION, onClick = onSession),
            MyAction("主题外观", MyRose, ICON_THEME, onClick = onTheme),
            MyAction("关于 bilibox", MyRose, ICON_ABOUT, onClick = onAbout),
        ),
        listOf(
            MyAction("本地视频", MyBlue, ICON_LOCAL_VIDEO, onClick = onLocalVideos),
            MyAction("清除缓存", MyBlue, ICON_CLEAR_CACHE, onClick = onClearCache),
            MyAction("帮助与反馈", MyBlue, ICON_HELP, onClick = onHelp),
            MyAction("检查更新", MyBlue, ICON_UPDATE, onClick = onUpdate),
        ),
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 10.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { action ->
                    ActionItem(action, darkMode, background, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun ActionItem(
    action: MyAction,
    darkMode: Boolean,
    background: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.clip(RoundedCornerShape(12.dp))
            // 这些是纯触摸入口：不参与焦点，避免弹层开关时被 bring-into-view 带着滚一屏。
            .focusProperties { canFocus = false }
            .clickable(onClick = action.onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) {
            ActionGlyph(action.glyph, action.tint, Modifier.size(26.dp))
            action.badge?.let { count ->
                Box(
                    Modifier.align(Alignment.TopEnd).offset(x = 11.dp, y = (-7).dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MyBadgeRed)
                        .border(1.5.dp, background, RoundedCornerShape(10.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .defaultMinSize(minWidth = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$count",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 10.sp,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = action.title,
            color = if (darkMode) MyLabelDark else MyLabelLight,
            fontSize = 13.sp,
            lineHeight = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun ActionGlyph(pathData: String, tint: Color, modifier: Modifier = Modifier) {
    val path = remember(pathData) { PathParser().parsePathString(pathData).toPath() }
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        scale(unit, unit, pivot = Offset.Zero) {
            drawPath(
                path,
                color = tint,
                style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

// ----------------------------------------------------------------- login ---
