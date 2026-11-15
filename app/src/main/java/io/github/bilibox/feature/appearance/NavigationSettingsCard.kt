package io.github.bilibox.feature.appearance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun NavigationSettingsCard(
    fixed: Boolean,
    accent: Color,
    palette: AppearancePalette,
    onFixedChange: (Boolean) -> Unit,
) {
    AppearanceCard(palette) {
        Row(
            Modifier.fillMaxWidth()
                .toggleable(value = fixed, role = Role.Switch, onValueChange = onFixedChange)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("固定底部导航栏", color = palette.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (fixed) "导航栏保持显示" else "闲置 3 秒后隐藏，触碰底部显示",
                    color = palette.ink3,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            }
            Switch(
                checked = fixed,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accent,
                    uncheckedThumbColor = palette.ink3,
                    uncheckedTrackColor = palette.line,
                    uncheckedBorderColor = palette.ink3.copy(alpha = .35f),
                ),
            )
        }
    }
}
