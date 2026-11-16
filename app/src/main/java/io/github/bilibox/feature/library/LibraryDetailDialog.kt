package io.github.bilibox.feature.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon

/** A compact header and fixed actions keep long file details and editors usable on small windows. */
@Composable
internal fun LibraryDetailDialog(
    title: String,
    subtitle: String,
    icon: AppIcon,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalLibraryPalette.current
    val maxHeight = minOf(LocalConfiguration.current.screenHeightDp.dp * .8f, 680.dp)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().heightIn(max = maxHeight),
                shape = RoundedCornerShape(28.dp),
                color = palette.card,
                border = BorderStroke(1.dp, palette.line.copy(alpha = .7f)),
            ) {
                Column {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(40.dp).background(palette.accentSoft, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                            AppGlyphIcon(icon, palette.accent, Modifier.size(21.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(title, color = palette.ink, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
                            Text(subtitle, color = palette.ink2, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                    }
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        content = content,
                    )
                    HorizontalDivider(color = palette.line.copy(alpha = .7f))
                    Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), content = actions)
                }
            }
        }
    }
}
