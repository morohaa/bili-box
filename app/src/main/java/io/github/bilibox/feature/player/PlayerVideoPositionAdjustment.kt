package io.github.bilibox.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.data.player.VideoPositionPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerVideoPositionAdjustment(state: PlayerVideoPositionState, verticalFullScreen: Boolean) {
    LaunchedEffect(verticalFullScreen) {
        if (!verticalFullScreen && state.adjusting) state.finish()
    }
    if (!verticalFullScreen || !state.adjusting) return
    BackHandler { state.finish() }
    DisposableEffect(state) { onDispose { state.finish() } }
    val accent = OnlinePink
    Box(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp)
                .widthIn(max = 480.dp).fillMaxWidth().testTag("video-position-adjustment"),
            shape = RoundedCornerShape(18.dp),
            color = Color(0xF21B1B1E),
            contentColor = Color.White,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("竖屏全屏画面位置", fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    TextButton(onClick = state::finish) { Text("完成", color = OnlinePink) }
                }
                Text("拖动滑块上下移动画面，调整会自动保存", color = Color.White.copy(alpha = .65f), fontSize = 12.sp)
                Slider(
                    value = state.adjustmentDp.toFloat(),
                    onValueChange = state::update,
                    onValueChangeFinished = state::save,
                    valueRange = -VideoPositionPreferences.MAX_ADJUSTMENT_DP.toFloat()..VideoPositionPreferences.MAX_ADJUSTMENT_DP.toFloat(),
                    modifier = Modifier.fillMaxWidth().testTag("video-position-slider")
                        .semantics { contentDescription = "竖屏全屏画面位置" },
                    thumb = {
                        Box(
                            Modifier.size(18.dp)
                                .shadow(3.dp, CircleShape)
                                .background(Color.White, CircleShape)
                        )
                    },
                    track = { sliderState ->
                        val limit = VideoPositionPreferences.MAX_ADJUSTMENT_DP.toFloat()
                        val fraction = ((sliderState.value + limit) / (2f * limit)).coerceIn(0f, 1f)
                        Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                            val start = Offset(0f, center.y)
                            drawLine(
                                Color.White.copy(alpha = .22f),
                                start,
                                Offset(size.width, center.y),
                                strokeWidth = size.height,
                                cap = StrokeCap.Round,
                            )
                            if (fraction > 0f) {
                                drawLine(
                                    accent,
                                    start,
                                    Offset(size.width * fraction, center.y),
                                    strokeWidth = size.height,
                                    cap = StrokeCap.Round,
                                )
                            }
                        }
                    },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("向上", fontSize = 12.sp, color = Color.White.copy(alpha = .65f))
                    TextButton(onClick = state::reset) { Text("恢复默认", color = Color.White) }
                    Text("向下", fontSize = 12.sp, color = Color.White.copy(alpha = .65f))
                }
            }
        }
    }
}
