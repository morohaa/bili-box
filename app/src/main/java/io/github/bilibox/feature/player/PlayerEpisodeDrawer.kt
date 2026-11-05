package io.github.bilibox.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.EqualizerBars
import io.github.bilibox.core.ui.theme.Pink
import io.github.bilibox.core.util.formatVideoDuration
import io.github.bilibox.data.localvideo.LocalVideo

@Composable
internal fun PlayerDrawerVisibility(
    visible: Boolean,
    fromBottom: Boolean = false,
    content: @Composable () -> Unit,
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = if (fromBottom) {
            fadeIn(tween(180)) + slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(260, easing = FastOutSlowInEasing)
            )
        } else {
            fadeIn(tween(180)) + slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(260, easing = FastOutSlowInEasing)
            )
        },
        exit = if (fromBottom) {
            fadeOut(tween(140)) + slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(220, easing = FastOutSlowInEasing)
            )
        } else {
            fadeOut(tween(140)) + slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(220, easing = FastOutSlowInEasing)
            )
        }
    ) {
        content()
    }
}

@Composable
internal fun LocalVideoEpisodes(
    playlist: List<LocalVideo>,
    currentVideo: LocalVideo,
    onDismiss: () -> Unit,
    onVideoSelected: (LocalVideo) -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = playlist.indexOfFirst { it.uri == currentVideo.uri }.coerceAtLeast(0)
    )
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f))
                .clickable(onClick = onDismiss).semantics { contentDescription = "关闭选集" }
        )
        Column(
            Modifier.align(Alignment.CenterEnd).width(360.dp).fillMaxHeight()
                .background(Color(0xF2121318))
                .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .navigationBarsPadding()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp).height(56.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("分集(${playlist.size}P)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = "收起选集" }) {
                    Text("×", color = Color.White.copy(alpha = .72f), fontSize = 24.sp)
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = .08f))
            LazyColumn(
                Modifier.weight(1f).selectableGroup(),
                state = listState,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(playlist, key = { _, item -> item.uri.toString() }) { index, item ->
                    LocalEpisodeRow(index, item, item.uri == currentVideo.uri) { onVideoSelected(item) }
                }
            }
        }
    }
}

@Composable
internal fun LocalEpisodeRow(index: Int, item: LocalVideo, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
            .background(if (selected) Pink.copy(alpha = .18f) else Color.White.copy(alpha = .05f))
            .border(1.dp, if (selected) Pink.copy(alpha = .8f) else Color.Transparent, RoundedCornerShape(11.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) EqualizerBars()
        Text(
            "P${index + 1} ${item.name}${if (selected) " (当前播放)" else ""}",
            color = if (selected) Pink else Color.White,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = if (selected) 4.dp else 0.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            formatVideoDuration(item.durationMs),
            color = if (selected) Pink else Color.White.copy(alpha = .45f),
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}
