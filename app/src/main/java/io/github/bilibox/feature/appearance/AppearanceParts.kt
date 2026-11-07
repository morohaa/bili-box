package io.github.bilibox.feature.appearance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.theme.AppearanceMode
import io.github.bilibox.core.ui.theme.ThemeOption

/** 「主题外观」页的明暗色板，取色自设计稿。 */
internal data class AppearancePalette(
    val background: Color,
    val card: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val cardBorder: Color,
    val shadow: Color,
)

internal fun appearancePalette(dark: Boolean): AppearancePalette = if (dark) {
    AppearancePalette(
        background = Color(0xFF121317),
        card = Color(0xFF1C1E24),
        ink = Color(0xFFEDEEF0),
        ink2 = Color(0xFFA9AEB6),
        ink3 = Color(0xFF7C828B),
        line = Color(0xFF25272D),
        cardBorder = Color(0x0FFFFFFF),
        shadow = Color.Black.copy(alpha = .7f),
    )
} else {
    AppearancePalette(
        background = Color(0xFFF6F7F8),
        card = Color(0xFFFFFFFF),
        ink = Color(0xFF18191C),
        ink2 = Color(0xFF61666D),
        ink3 = Color(0xFF9499A0),
        line = Color(0xFFF1F2F4),
        cardBorder = Color(0x0A18191C),
        shadow = Color(0xFF18191C).copy(alpha = .16f),
    )
}

/** 主题圆形预览的渐变；自定义项使用设计稿里的彩色扫掠。 */
internal fun themeBrush(option: ThemeOption): Brush = if (option.custom) {
    Brush.sweepGradient(
        listOf(
            Color(0xFFFF9A9E),
            Color(0xFFFECFEF),
            Color(0xFFA1C4FD),
            Color(0xFFC2E9FB),
            Color(0xFFFF9A9E),
        )
    )
} else {
    Brush.linearGradient(listOf(option.start, option.end))
}

/** 主题网格里的一枚圆形色块，选中时带强调色描边与浅色外环。 */
@Composable
internal fun ThemeTile(
    option: ThemeOption,
    selected: Boolean,
    accent: Color,
    palette: AppearancePalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics { contentDescription = "${option.label}主题" }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(54.dp)
                .drawBehind {
                    if (selected) {
                        // 选中环画在布局之外，和设计稿一样不把网格撑高。
                        drawCircle(accent.copy(alpha = .14f), radius = size.minDimension / 2 + 6.dp.toPx())
                        drawCircle(
                            accent,
                            radius = size.minDimension / 2 + 2.5.dp.toPx(),
                            style = Stroke(2.5.dp.toPx()),
                        )
                    }
                }
                .clip(CircleShape)
                .background(themeBrush(option)),
            contentAlignment = Alignment.Center,
        ) {
            if (option.custom) {
                PaletteGlyph(Color.White, Modifier.size(22.dp))
            } else {
                TvGlyph(Color.White, Modifier.size(25.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = option.label,
            color = if (selected) accent else palette.ink3,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 「浅色 / 深色 / 自动」分段控制器。 */
@Composable
internal fun AppearanceSegmented(
    mode: AppearanceMode,
    accent: Color,
    palette: AppearancePalette,
    onSelect: (AppearanceMode) -> Unit,
) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(palette.line).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        AppearanceMode.entries.forEach { item ->
            val selected = item == mode
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .then(
                        if (selected) {
                            Modifier.shadow(1.dp, RoundedCornerShape(6.dp), spotColor = palette.shadow)
                        } else {
                            Modifier
                        }
                    )
                    .background(if (selected) selectedSegmentColor(palette) else Color.Transparent)
                    .clickable { onSelect(item) }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.label,
                    color = if (selected) palette.ink else palette.ink3,
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

private fun selectedSegmentColor(palette: AppearancePalette): Color = if (palette.background.luminance() < .5f) Color(0xFF3A3D44) else Color.White

/** 说明性卡片：两行设置共用同一张底。 */
@Composable
internal fun AppearanceCard(
    palette: AppearancePalette,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 14.dp)
            .shadow(4.dp, RoundedCornerShape(16.dp), spotColor = palette.shadow)
            .clip(RoundedCornerShape(16.dp))
            .background(palette.card)
            .border(1.dp, palette.cardBorder, RoundedCornerShape(16.dp)),
        content = content,
    )
}

@Composable
internal fun SettingRow(
    label: String,
    palette: AppearancePalette,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = palette.ink, fontSize = 14.sp, lineHeight = 18.sp, modifier = Modifier.weight(1f))
        trailing()
    }
}

// ----------------------------------------------------------------- glyph ---

@Composable
internal fun TvGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        val stroke = Stroke(width = 2.1f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawLine(tint, Offset(5f * unit, 3.2f * unit), Offset(8.4f * unit, 6.7f * unit), stroke.width, StrokeCap.Round)
        drawLine(tint, Offset(19f * unit, 3.2f * unit), Offset(15.6f * unit, 6.7f * unit), stroke.width, StrokeCap.Round)
        drawRoundRect(
            color = tint,
            topLeft = Offset(1.5f * unit, 6.7f * unit),
            size = Size(21f * unit, 15.3f * unit),
            cornerRadius = CornerRadius(4.4f * unit, 4.4f * unit),
            style = stroke,
        )
        drawCircle(tint, radius = 1.35f * unit, center = Offset(7.6f * unit, 13.6f * unit))
        drawCircle(tint, radius = 1.35f * unit, center = Offset(16.4f * unit, 13.6f * unit))
    }
}

@Composable
internal fun PaletteGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        val stroke = Stroke(width = 1.9f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawCircle(tint, radius = 9.2f * unit, center = Offset(12f * unit, 12f * unit), style = stroke)
        drawCircle(tint, radius = 1.25f * unit, center = Offset(8.4f * unit, 9.4f * unit))
        drawCircle(tint, radius = 1.25f * unit, center = Offset(15.6f * unit, 9.4f * unit))
        drawCircle(tint, radius = 1.25f * unit, center = Offset(12f * unit, 14.6f * unit))
    }
}

@Composable
internal fun BackChevron(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 20f
        val stroke = 2.1f * unit
        drawLine(tint, Offset(12.6f * unit, 3.6f * unit), Offset(6.4f * unit, 10f * unit), stroke, StrokeCap.Round)
        drawLine(tint, Offset(6.4f * unit, 10f * unit), Offset(12.6f * unit, 16.4f * unit), stroke, StrokeCap.Round)
    }
}

@Composable
internal fun RightChevron(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 14f
        val stroke = 1.7f * unit
        drawLine(tint, Offset(5.6f * unit, 3.6f * unit), Offset(10.4f * unit, 7f * unit), stroke, StrokeCap.Round)
        drawLine(tint, Offset(10.4f * unit, 7f * unit), Offset(5.6f * unit, 10.4f * unit), stroke, StrokeCap.Round)
    }
}

@Composable
internal fun MoreDots(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val gap = 3.6.dp.toPx()
        for (index in 0..2) {
            drawCircle(tint, radius = 1.7.dp.toPx(), center = Offset(size.width / 2 + (index - 1) * gap, size.height / 2))
        }
    }
}

@Composable
internal fun TickGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val unit = size.minDimension / 24f
        val stroke = 2.6f * unit
        drawLine(tint, Offset(4.6f * unit, 12.6f * unit), Offset(9.8f * unit, 17.8f * unit), stroke, StrokeCap.Round)
        drawLine(tint, Offset(9.8f * unit, 17.8f * unit), Offset(19.6f * unit, 6.8f * unit), stroke, StrokeCap.Round)
    }
}
