package io.github.bilibox.feature.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.playback.SeekThumb
import io.github.bilibox.core.ui.theme.SeekThumbStyle

@Composable
internal fun SeekThumbSettingsCard(
    selected: SeekThumbStyle,
    accent: Color,
    palette: AppearancePalette,
    onSelect: (SeekThumbStyle) -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    AppearanceCard(palette) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("进度条滑块样式", color = palette.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Row(
                Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SeekThumbStyle.entries.forEach { style ->
                    val active = style == selected
                    Column(
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp).clip(shape)
                            .background(if (active) accent.copy(alpha = .12f) else Color.Transparent)
                            .border(1.dp, if (active) accent else palette.ink3.copy(alpha = .3f), shape)
                            .selectable(selected = active, role = Role.RadioButton, onClick = { onSelect(style) })
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    ) {
                        SeekThumb(style, accent)
                        Text(
                            style.label,
                            color = if (active) palette.ink else palette.ink2,
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}
