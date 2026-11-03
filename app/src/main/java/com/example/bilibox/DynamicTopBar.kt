package com.example.bilibox

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DynamicPink = Color(0xFFEF7196)
private val DynamicInk = Color(0xFF24313D)
private val DynamicMuted = Color(0xFF82909D)

/**
 * 动态页固定顶栏的四个内容：头像框、搜索框、下载、历史。
 * 除搜索外，其余按钮目前只绘制图标。
 */
@Composable
internal fun RowScope.DynamicTopBarContent(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    darkMode: Boolean = false,
) {
    DynamicAvatar()
    Spacer(Modifier.width(10.dp))
    DynamicSearchBox(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = onSearch,
        darkMode = darkMode,
        modifier = Modifier.weight(1f),
    )
    Spacer(Modifier.width(6.dp))
    TopBarGlyph(TopBarAction.DOWNLOAD, "下载", darkMode)
    Spacer(Modifier.width(2.dp))
    TopBarGlyph(TopBarAction.HISTORY, "历史", darkMode)
}

@Composable
private fun DynamicAvatar() {
    Box(
        Modifier.size(38.dp)
            .clip(CircleShape)
            .background(Color(0xFFFCE7EE))
            .border(1.dp, Color(0xFFEBC8D4), CircleShape)
            .semantics { contentDescription = "头像" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val unit = size.minDimension / 24f
            val tint = Color(0xFFD97A98)
            val stroke = Stroke(1.7f * unit, cap = StrokeCap.Round)
            drawCircle(tint, 4.2f * unit, Offset(12f * unit, 8f * unit), style = stroke)
            drawPath(
                Path().apply {
                    moveTo(4.5f * unit, 21f * unit)
                    cubicTo(4.5f * unit, 14f * unit, 19.5f * unit, 14f * unit, 19.5f * unit, 21f * unit)
                },
                tint,
                style = stroke,
            )
        }
    }
}

@Composable
private fun DynamicSearchBox(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    darkMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val submit = {
        focusManager.clearFocus()
        onSearch()
    }
    Row(
        modifier.height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (darkMode) Color(0xFF292D33) else Color.White)
            .border(1.dp, if (darkMode) Color(0xFF3B4048) else Color(0xFFE4E9EF), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(20.dp)
                .clickable(role = Role.Button, onClick = submit)
                .semantics { contentDescription = "搜索" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(17.dp)) {
                val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(DynamicPink, radius = size.minDimension * .31f, center = Offset(size.width * .42f, size.height * .42f), style = stroke)
                drawLine(DynamicPink, Offset(size.width * .64f, size.height * .64f), Offset(size.width * .9f, size.height * .9f), stroke.width, StrokeCap.Round)
            }
        }
        Spacer(Modifier.width(7.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(color = if (darkMode) Color(0xFFE7E9ED) else DynamicInk, fontSize = 13.5.sp),
            cursorBrush = SolidColor(DynamicPink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("搜索 B 站视频", color = if (darkMode) Color(0xFF9BA3AE) else DynamicMuted, fontSize = 13.sp, maxLines = 1)
                    }
                    inner()
                }
            },
        )
    }
}

/** 仅绘制图标，点击行为留待后续接入。 */
@Composable
private fun TopBarGlyph(icon: TopBarAction, description: String, darkMode: Boolean) {
    Box(
        Modifier.size(40.dp).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(23.dp)) {
            val tint = if (darkMode) Color(0xFFB4BDC8) else Color(0xFF4C5B68)
            val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
            when (icon) {
                TopBarAction.DOWNLOAD -> {
                    drawLine(tint, Offset(size.width * .5f, size.height * .12f), Offset(size.width * .5f, size.height * .62f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(size.width * .31f, size.height * .43f), Offset(size.width * .5f, size.height * .62f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(size.width * .69f, size.height * .43f), Offset(size.width * .5f, size.height * .62f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(size.width * .2f, size.height * .78f), Offset(size.width * .8f, size.height * .78f), stroke.width, StrokeCap.Round)
                }
                TopBarAction.HISTORY -> {
                    drawArc(tint, 45f, 300f, false, topLeft = Offset(size.width * .14f, size.height * .14f), size = androidx.compose.ui.geometry.Size(size.width * .72f, size.height * .72f), style = stroke)
                    drawLine(tint, Offset(size.width * .5f, size.height * .28f), Offset(size.width * .5f, size.height * .5f), stroke.width, StrokeCap.Round)
                    drawLine(tint, Offset(size.width * .5f, size.height * .5f), Offset(size.width * .67f, size.height * .58f), stroke.width, StrokeCap.Round)
                }
            }
        }
    }
}

private enum class TopBarAction { DOWNLOAD, HISTORY }
