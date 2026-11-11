package io.github.bilibox.feature.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.theme.AppFont
import io.github.bilibox.core.ui.theme.fontFamily

@Composable
internal fun FontSettingsCard(
    selected: AppFont,
    accent: Color,
    palette: AppearancePalette,
    onSelect: (AppFont) -> Unit,
) {
    AppearanceCard(palette) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text("应用字体", color = palette.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("点选即时生效，下次打开仍会保留", color = palette.ink3, fontSize = 12.sp)
        }
        Column(Modifier.selectableGroup()) {
            AppFont.entries.forEach { font ->
                HorizontalDivider(color = palette.line)
                val isSelected = selected == font
                Row(
                    Modifier.fillMaxWidth()
                        .background(if (isSelected) accent.copy(alpha = .06f) else Color.Transparent)
                        .selectable(selected = isSelected, role = Role.RadioButton, onClick = { onSelect(font) })
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(42.dp).background(accent.copy(alpha = .1f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("字", fontFamily = font.fontFamily, color = accent, fontSize = 24.sp)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(font.label, color = palette.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFamily = font.fontFamily)
                        Text(font.description, color = palette.ink3, fontSize = 12.sp)
                        Text("山海有相逢 · BiliBox 123", color = palette.ink, fontSize = 14.sp, fontFamily = font.fontFamily)
                    }
                    RadioButton(
                        selected = isSelected,
                        onClick = null,
                        colors = RadioButtonDefaults.colors(selectedColor = accent, unselectedColor = palette.ink3),
                    )
                }
            }
        }
    }
}
