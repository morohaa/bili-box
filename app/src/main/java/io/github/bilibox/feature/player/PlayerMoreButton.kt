package io.github.bilibox.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** 播放器右上角的更多入口：打开从画面下沿展开的播放设置卡片。 */
@Composable
internal fun PlayerMoreButton(
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(40.dp).semantics {
                contentDescription = if (expanded) "收起播放设置" else "更多"
            },
        ) {
            MoreDotsIcon(Modifier.size(20.dp), Color.White.copy(alpha = .92f))
        }
    }
}

@Composable
private fun MoreDotsIcon(modifier: Modifier = Modifier, tint: Color) {
    Canvas(modifier) {
        val radius = size.minDimension * .075f
        listOf(.25f, .5f, .75f).forEach { fraction ->
            drawCircle(tint, radius, Offset(size.width / 2f, size.height * fraction))
        }
    }
}
