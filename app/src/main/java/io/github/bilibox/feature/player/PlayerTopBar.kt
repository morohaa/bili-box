package io.github.bilibox.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.feature.listen.ListenTopBarButton

@Composable
internal fun PlayerTopBar(
    fullScreen: Boolean,
    title: String,
    listening: Boolean,
    showListenButton: Boolean,
    speed: Float,
    onBack: () -> Unit,
    onListenClick: () -> Unit,
    onListenPositioned: (Offset) -> Unit,
    onSpeedClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth()
            .then(if (fullScreen) Modifier.displayCutoutPadding() else Modifier)
            .height(if (fullScreen) 48.dp else 44.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(if (fullScreen) 48.dp else 40.dp)
                .semantics { contentDescription = "返回" }
        ) {
            Canvas(Modifier.size(if (fullScreen) 24.dp else 20.dp)) {
                drawPath(
                    Path().apply {
                        moveTo(size.width * .63f, size.height * .15f)
                        lineTo(size.width * .28f, size.height * .5f)
                        lineTo(size.width * .63f, size.height * .85f)
                    },
                    Color.White,
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
        Text(
            if (fullScreen) title else "",
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!fullScreen && showListenButton) {
            ListenTopBarButton(
                listening = listening,
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    val p = coordinates.positionInRoot()
                    onListenPositioned(
                        Offset(
                            p.x + coordinates.size.width / 2f,
                            p.y + coordinates.size.height / 2f
                        )
                    )
                }
            ) { onListenClick() }
            Spacer(Modifier.width(4.dp))
        }
        if (!fullScreen) {
            SpeedBarChip(speed = speed, onClick = onSpeedClick)
        }
    }
}
