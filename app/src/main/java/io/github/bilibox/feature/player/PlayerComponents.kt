package io.github.bilibox.feature.player

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.util.Size
import android.view.LayoutInflater
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.github.bilibox.R
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.ui.theme.OnlineText
import io.github.bilibox.core.ui.theme.Pink
import io.github.bilibox.core.util.formatVideoDuration
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.feature.online.EqualizerBars

@Composable
internal fun ScreenshotPreviewCard(
    frame: Bitmap,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) +
            scaleIn(
                initialScale = .6f,
                transformOrigin = TransformOrigin(0f, 1f),
                animationSpec = tween(240, easing = FastOutSlowInEasing),
            ),
        exit = fadeOut(tween(160)) +
            scaleOut(
                targetScale = .92f,
                transformOrigin = TransformOrigin(0f, 1f),
                animationSpec = tween(180, easing = FastOutSlowInEasing),
            ),
        modifier = modifier,
    ) {
        Column(
            Modifier.width(96.dp)
                .shadow(10.dp, RoundedCornerShape(7.dp))
                .background(Color.White, RoundedCornerShape(7.dp))
                .padding(2.dp)
                .clip(RoundedCornerShape(5.dp)),
        ) {
            Box {
                Image(
                    bitmap = frame.asImageBitmap(),
                    contentDescription = "已保存截图预览",
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Color.Black.copy(alpha = .55f))
                        .padding(vertical = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("已保存到相册", color = Color.White, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun PlayerDetailsOverlay(visible: Boolean, overlay: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(300, easing = CubicBezierEasing(.22f, 1f, .36f, 1f))
        ) + fadeIn(animationSpec = tween(180)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(240, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(140)),
    ) {
        overlay()
    }
}

@Composable
internal fun BiliLoadingIndicator(message: String) {
    val transition = rememberInfiniteTransition(label = "播放器缓冲")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = CubicBezierEasing(.45f, .05f, .55f, .95f))),
        label = "粉色缓冲环旋转"
    )
    val televisionOffset by transition.animateFloat(
        initialValue = 0f,
        targetValue = -3f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "小电视浮动"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(44.dp).rotate(rotation)) {
                val u = size.width / 50f
                val diameter = androidx.compose.ui.geometry.Size(40f * u, 40f * u)
                drawCircle(Color.White.copy(alpha = .12f), radius = 20f * u,
                    style = Stroke(3.2f * u))
                drawArc(OnlinePink, startAngle = -90f, sweepAngle = 244f, useCenter = false,
                    topLeft = Offset(5f * u, 5f * u), size = diameter,
                    style = Stroke(3.2f * u, cap = StrokeCap.Round))
            }
            Canvas(Modifier.offset(y = televisionOffset.dp).size(20.dp)) {
                val u = size.width / 24f
                val tint = Color.White.copy(alpha = .9f)
                val stroke = Stroke(1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
                drawPath(Path().apply {
                    moveTo(8f * u, 3f * u); lineTo(11f * u, 6f * u); lineTo(14f * u, 3f * u)
                }, tint, style = stroke)
                drawRoundRect(tint, Offset(4f * u, 6f * u),
                    androidx.compose.ui.geometry.Size(16f * u, 13f * u),
                    androidx.compose.ui.geometry.CornerRadius(3.5f * u), style = stroke)
                listOf(9f, 15f).forEach { x ->
                    drawLine(tint, Offset(x * u, 11f * u), Offset(x * u, 13f * u), 1.8f * u, StrokeCap.Round)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(message, color = Color.White.copy(alpha = .9f), fontSize = 12.sp,
            lineHeight = 17.sp, letterSpacing = .3.sp)
    }
}

@Composable
internal fun PlaybackTimeLabel(
    position: Long,
    duration: Long,
    modifier: Modifier = Modifier,
    fullScreen: Boolean = false,
) {
    Text(
        "${formatPlaybackTime(position)} / ${formatPlaybackTime(duration, unknownWhenZero = true)}",
        modifier = modifier,
        color = Color.White.copy(alpha = .9f),
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        maxLines = 1
    )
}

@Composable
internal fun PlayerBarIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable BoxScope.() -> Unit,
) {
    val description = contentDescription
    Box(
        modifier.size(32.dp).clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = description },
        contentAlignment = Alignment.Center,
        content = icon
    )
}

@Composable
internal fun PlayerBarAction(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier.height(32.dp).clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = Color.White.copy(alpha = .92f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
internal fun PlayerQualityChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier.height(30.dp).clip(RoundedCornerShape(8.dp))
            .background(OnlinePink.copy(alpha = .18f))
            .border(1.dp, OnlinePink.copy(alpha = .55f), RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "画质 ${label.ifBlank { "选择" }}" }
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label.ifBlank { "画质" },
            color = OnlinePink,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
internal fun NextEpisodeIcon(modifier: Modifier = Modifier.size(18.dp)) {
    Canvas(modifier) {
        val u = size.width / 24f
        drawPath(Path().apply {
            moveTo(5f * u, 5.5f * u)
            lineTo(15f * u, 12f * u)
            lineTo(5f * u, 18.5f * u)
            close()
        }, Color.White)
        drawRoundRect(
            Color.White,
            Offset(17.5f * u, 5f * u),
            androidx.compose.ui.geometry.Size(2.5f * u, 14f * u),
            androidx.compose.ui.geometry.CornerRadius(1f * u)
        )
    }
}

@Composable
internal fun PlaybackQualityDrawer(
    options: List<PlayerQualityOption>,
    selectedQuality: Int?,
    onDismiss: () -> Unit,
    onQualitySelected: (Int) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(Color.Transparent)
                .clickable(onClick = onDismiss)
                .semantics { contentDescription = "关闭清晰度选择" }
        )
        Column(
            Modifier.align(Alignment.CenterEnd)
                .width(360.dp).fillMaxHeight()
                .padding(end = 8.dp, top = 8.dp, bottom = 8.dp)
                .navigationBarsPadding()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xF214161E))
                .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("清晰度与音质", color = Color.White, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold)
                    Text("切换后保持当前播放进度", color = Color.White.copy(alpha = .45f),
                        fontSize = 10.5.sp, modifier = Modifier.padding(top = 2.dp))
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                        .semantics { contentDescription = "收起清晰度选择" }
                ) {
                    Text("×", color = Color.White.copy(alpha = .72f), fontSize = 22.sp)
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(bottom = 8.dp),
                color = Color.White.copy(alpha = .08f)
            )
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                options.forEach { option ->
                    val selected = option.qn == selectedQuality
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) OnlinePink.copy(alpha = .16f)
                                else Color.White.copy(alpha = .05f)
                            )
                            .border(
                                1.dp,
                                if (selected) OnlinePink.copy(alpha = .75f) else Color.Transparent,
                                RoundedCornerShape(12.dp)
                            )
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onQualitySelected(option.qn) }
                            )
                            .semantics { contentDescription = "选择清晰度 ${option.label}" }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    option.label,
                                    color = if (selected) OnlinePink else Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                option.badge?.let { badge ->
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        Modifier.clip(RoundedCornerShape(4.dp))
                                            .background(OnlinePink.copy(alpha = .22f))
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text(badge, color = OnlinePink, fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            if (option.description.isNotBlank()) {
                                Text(
                                    option.description,
                                    color = Color.White.copy(alpha = .45f),
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                            }
                        }
                        if (selected) {
                            Box(
                                Modifier.clip(RoundedCornerShape(20.dp))
                                    .background(OnlinePink)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("播放中", color = Color.White, fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VideoSeekBar(
    position: Float,
    duration: Long,
    bufferedPosition: Long,
    onSeek: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keep the visual layout compact; Compose still expands the touch target.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Slider(
            value = position.coerceIn(0f, duration.coerceAtLeast(1L).toFloat()),
            onValueChange = onSeek,
            onValueChangeFinished = onSeekFinished,
            valueRange = 0f..duration.coerceAtLeast(1L).toFloat(),
            enabled = duration > 0,
            modifier = modifier.height(24.dp).semantics { contentDescription = "播放进度" },
            thumb = { TelevisionSeekThumb() },
            track = { sliderState ->
                Canvas(Modifier.fillMaxWidth().height(2.5.dp)) {
                    val played = (sliderState.value / duration.coerceAtLeast(1L)).coerceIn(0f, 1f)
                    val buffered = (bufferedPosition.toFloat() / duration.coerceAtLeast(1L)).coerceIn(0f, 1f)
                    val start = Offset(0f, center.y)
                    drawLine(Color.White.copy(alpha = .3f), start, Offset(size.width, center.y),
                        size.height, StrokeCap.Round)
                    if (buffered > 0f) drawLine(Color.White.copy(alpha = .45f), start,
                        Offset(size.width * buffered, center.y), size.height, StrokeCap.Round)
                    if (played > 0f) drawLine(OnlinePink, start,
                        Offset(size.width * played, center.y), size.height, StrokeCap.Round)
                }
            }
        )
    }
}

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
                initialOffsetY = { it }, animationSpec = tween(260, easing = FastOutSlowInEasing)
            )
        } else {
            fadeIn(tween(180)) + slideInHorizontally(
                initialOffsetX = { it }, animationSpec = tween(260, easing = FastOutSlowInEasing)
            )
        },
        exit = if (fromBottom) {
            fadeOut(tween(140)) + slideOutVertically(
                targetOffsetY = { it }, animationSpec = tween(220, easing = FastOutSlowInEasing)
            )
        } else {
            fadeOut(tween(140)) + slideOutHorizontally(
                targetOffsetX = { it }, animationSpec = tween(220, easing = FastOutSlowInEasing)
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
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f))
            .clickable(onClick = onDismiss).semantics { contentDescription = "关闭选集" })
        Column(
            Modifier.align(Alignment.CenterEnd).width(360.dp).fillMaxHeight()
                .background(Color(0xF2121318))
                .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .navigationBarsPadding()
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp).height(56.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("分集(${playlist.size}P)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = "收起选集" }) {
                    Text("×", color = Color.White.copy(alpha = .72f), fontSize = 24.sp)
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = .08f))
            LazyColumn(Modifier.weight(1f).selectableGroup(), state = listState,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

@Composable
internal fun PlaybackIcon(playing: Boolean, modifier: Modifier = Modifier.size(26.dp)) {
    Canvas(modifier) {
        val u = size.width / 24f
        if (playing) {
            listOf(6f, 14.2f).forEach { x ->
                drawRoundRect(Color.White, Offset(x * u, 5f * u),
                    androidx.compose.ui.geometry.Size(3.8f * u, 14f * u),
                    androidx.compose.ui.geometry.CornerRadius(1.2f * u))
            }
        } else {
            drawPath(Path().apply {
                moveTo(6.5f * u, 4.8f * u)
                cubicTo(5.7f * u, 4.3f * u, 4.7f * u, 4.9f * u, 4.7f * u, 5.8f * u)
                lineTo(4.7f * u, 18.2f * u)
                cubicTo(4.7f * u, 19.1f * u, 5.7f * u, 19.7f * u, 6.5f * u, 19.2f * u)
                lineTo(17.1f * u, 13f * u)
                cubicTo(17.9f * u, 12.6f * u, 17.9f * u, 11.4f * u, 17.1f * u, 11f * u)
                close()
            }, Color.White)
        }
    }
}

@Composable
internal fun TelevisionSeekThumb() {
    Canvas(Modifier.size(width = 18.dp, height = 16.dp)) {
        val u = size.width / 24f
        val stroke = Stroke(1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawLine(OnlinePink, Offset(7.5f*u, 1.5f*u), Offset(10.5f*u, 4.5f*u), 2f*u, StrokeCap.Round)
        drawLine(OnlinePink, Offset(16.5f*u, 1.5f*u), Offset(13.5f*u, 4.5f*u), 2f*u, StrokeCap.Round)
        drawRoundRect(Color.White, Offset(2.5f*u, 4f*u), androidx.compose.ui.geometry.Size(19f*u, 14f*u),
            androidx.compose.ui.geometry.CornerRadius(4.5f*u))
        drawRoundRect(OnlinePink, Offset(2.5f*u, 4f*u), androidx.compose.ui.geometry.Size(19f*u, 14f*u),
            androidx.compose.ui.geometry.CornerRadius(4.5f*u), style = stroke)
        listOf(8.5f, 15.5f).forEach { x ->
            drawLine(OnlineText, Offset(x*u, 10f*u), Offset(x*u, 11.8f*u), 2f*u, StrokeCap.Round)
        }
    }
}

@Composable
internal fun FullscreenIcon(fullScreen: Boolean, modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier) {
        val u = size.width / 24f
        val corners = Path().apply {
            if (fullScreen) {
                moveTo(2f*u, 8f*u); lineTo(8f*u, 8f*u); lineTo(8f*u, 2f*u)
                moveTo(16f*u, 2f*u); lineTo(16f*u, 8f*u); lineTo(22f*u, 8f*u)
                moveTo(22f*u, 16f*u); lineTo(16f*u, 16f*u); lineTo(16f*u, 22f*u)
                moveTo(8f*u, 22f*u); lineTo(8f*u, 16f*u); lineTo(2f*u, 16f*u)
            } else {
                moveTo(4f*u, 9f*u); lineTo(4f*u, 5f*u)
                quadraticTo(4f*u, 4f*u, 5f*u, 4f*u); lineTo(9f*u, 4f*u)
                moveTo(20f*u, 9f*u); lineTo(20f*u, 5f*u)
                quadraticTo(20f*u, 4f*u, 19f*u, 4f*u); lineTo(15f*u, 4f*u)
                moveTo(4f*u, 15f*u); lineTo(4f*u, 19f*u)
                quadraticTo(4f*u, 20f*u, 5f*u, 20f*u); lineTo(9f*u, 20f*u)
                moveTo(20f*u, 15f*u); lineTo(20f*u, 19f*u)
                quadraticTo(20f*u, 20f*u, 19f*u, 20f*u); lineTo(15f*u, 20f*u)
            }
        }
        drawPath(corners, Color.White.copy(alpha = .9f), style = Stroke(2.1f*u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

internal tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}


internal fun createTexturePlayerView(context: Context, player: ExoPlayer): PlayerView =
    (LayoutInflater.from(context).inflate(R.layout.video_player_view, null) as PlayerView).apply {
        this.player = player
        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
        keepScreenOn = true
    }


@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun createCompatiblePlayer(context: Context): ExoPlayer {
    val renderersFactory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
    return ExoPlayer.Builder(context, renderersFactory).build()
}

internal fun formatPlaybackTime(positionMs: Long, unknownWhenZero: Boolean = false): String {
    if (unknownWhenZero && positionMs <= 0L) return "--:--"
    val totalSeconds = (positionMs.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
