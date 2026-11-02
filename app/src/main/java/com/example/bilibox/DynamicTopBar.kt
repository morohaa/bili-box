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

/**
 * 动态页固定顶栏的四个内容：头像框、搜索框、下载、历史。
 * 除搜索外，其余按钮目前只绘制图标。
 */
@Composable
internal fun RowScope.DynamicTopBarContent(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    DynamicAvatar()
    Spacer(Modifier.width(10.dp))
    DynamicSearchBox(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = onSearch,
        modifier = Modifier.weight(1f),
    )
    Spacer(Modifier.width(6.dp))
    TopBarGlyph(LocalIcon.DOWNLOAD, "下载")
    Spacer(Modifier.width(2.dp))
    TopBarGlyph(LocalIcon.HISTORY, "历史")
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
            .background(Color.White)
            .border(1.dp, Color(0xFFE4E9EF), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(20.dp)
                .clickable(role = Role.Button, onClick = submit)
                .semantics { contentDescription = "搜索" },
            contentAlignment = Alignment.Center,
        ) {
            LocalGlyphIcon(LocalIcon.SEARCH, Pink, Modifier.size(17.dp), 2f)
        }
        Spacer(Modifier.width(7.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = TextStyle(color = Ink, fontSize = 13.5.sp),
            cursorBrush = SolidColor(Pink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text("搜索 B 站视频", color = Muted, fontSize = 13.sp, maxLines = 1)
                    }
                    inner()
                }
            },
        )
    }
}

/** 仅绘制图标，点击行为留待后续接入。 */
@Composable
private fun TopBarGlyph(icon: LocalIcon, description: String) {
    Box(
        Modifier.size(40.dp).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        LocalGlyphIcon(icon, Color(0xFF4C5B68), Modifier.size(23.dp), 1.8f)
    }
}
