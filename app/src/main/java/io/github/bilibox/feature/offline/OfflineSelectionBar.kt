package io.github.bilibox.feature.offline

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.theme.LocalAccentColor

@Composable
internal fun OfflineSelectionBar(
    selectedCount: Int,
    allSelected: Boolean,
    canSelect: Boolean,
    deleting: Boolean,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val accent = LocalAccentColor.current
    val selectionState = when {
        allSelected -> ToggleableState.On
        selectedCount > 0 -> ToggleableState.Indeterminate
        else -> ToggleableState.Off
    }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(24.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .35f)),
        shadowElevation = 6.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                    .triStateToggleable(state = selectionState, enabled = canSelect, role = Role.Checkbox, onClick = onSelectAll)
                    .padding(horizontal = 4.dp, vertical = 6.dp).heightIn(min = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OfflineSelectionIndicator(selectionState, canSelect)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(if (allSelected) "取消全选" else "全选", color = colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("已选 $selectedCount 项", color = colors.onSurfaceVariant, fontSize = 11.sp, lineHeight = 14.sp)
                }
            }
            Button(
                onClick = onDelete,
                enabled = selectedCount > 0 && !deleting,
                modifier = Modifier.heightIn(min = 46.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color.White,
                    disabledContainerColor = colors.onSurface.copy(alpha = .07f),
                    disabledContentColor = colors.onSurface.copy(alpha = .38f),
                ),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
            ) {
                Text(if (deleting) "正在删除…" else "删除缓存", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
internal fun OfflineSelectionCheckbox(
    checked: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    description: String,
    modifier: Modifier = Modifier,
) {
    val state = if (checked) ToggleableState.On else ToggleableState.Off
    Box(
        modifier.clip(RoundedCornerShape(12.dp))
            .triStateToggleable(state = state, enabled = enabled, role = Role.Checkbox, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        OfflineSelectionIndicator(state, enabled)
    }
}

@Composable
private fun OfflineSelectionIndicator(state: ToggleableState, enabled: Boolean) {
    val colors = MaterialTheme.colorScheme
    val selected = state != ToggleableState.Off
    val accent = LocalAccentColor.current
    val fill by animateColorAsState(if (selected) accent.copy(alpha = if (enabled) 1f else .4f) else Color.Transparent, label = "selectionFill")
    val outline by animateColorAsState(
        if (selected) fill else colors.onSurfaceVariant.copy(alpha = if (enabled) .55f else .2f),
        label = "selectionOutline",
    )
    Box(
        Modifier.size(22.dp).background(fill, CircleShape).border(1.5.dp, outline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            ToggleableState.On -> AppGlyphIcon(AppIcon.CHECK, Color.White, Modifier.size(15.dp), strokeWidth = 2.6f)
            ToggleableState.Indeterminate -> Box(Modifier.size(width = 10.dp, height = 2.dp).background(Color.White, CircleShape))
            ToggleableState.Off -> Unit
        }
    }
}
