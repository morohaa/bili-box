package io.github.bilibox.feature.online

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.component.EqualizerBars
import io.github.bilibox.core.ui.theme.OnlineCardGray
import io.github.bilibox.core.ui.theme.OnlineMuted
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.ui.theme.OnlinePinkSoft
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val CacheSheetSurface = Color(0xFFFFFFFF)
private val CacheHeaderSurface = Color(0xFFFFFFFF)
private val CacheItemSurface = OnlineCardGray
private val CacheItemPressed = Color(0xFFEDEFF2)
private val CacheSecondaryBorder = Color(0xFFE6E9ED)
private val CacheInk = Color(0xFF18191C)
private val CacheSecondary = Color(0xFF5F6266)
private val CacheMuted = Color(0xFF9CA1A8)
private val CacheLine = Color(0xFFEFF1F4)
private val CacheGreen = Color(0xFF52C41A)
private val CacheRed = Color(0xFFFF4D4F)
private val CacheGrabber = Color(0xFFD5D8DD)
private val CacheQualitySurface = Color(0xFFF2F3F5)
private val CacheSwitchTrack = Color(0xFFF0F1F3)

@Composable
internal fun OnlineCacheSheetHost(
    visible: Boolean,
    topInset: Dp,
    onDismiss: () -> Unit,
    controller: OnlineCacheController,
    playingPage: Int,
    onViewCache: () -> Unit,
) {
    val scrimInteraction = remember { MutableInteractionSource() }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(180)),
        exit = fadeOut(tween(150)),
    ) {
        Box(
            Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = .42f))
                .clickable(
                    interactionSource = scrimInteraction,
                    indication = null,
                    onClick = onDismiss,
                )
                .semantics { contentDescription = "关闭缓存面板" }
        )
    }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize().padding(top = topInset),
        enter = slideInVertically(
            animationSpec = tween(320, easing = CubicBezierEasing(.22f, .8f, .25f, 1f)),
            initialOffsetY = { it },
        ),
        exit = slideOutVertically(
            animationSpec = tween(240, easing = FastOutSlowInEasing),
            targetOffsetY = { it },
        ),
    ) {
        OnlineCacheSheetPanel(
            controller = controller,
            playingPage = playingPage,
            onDismiss = onDismiss,
            onViewCache = onViewCache,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun OnlineCacheSheetPanel(
    controller: OnlineCacheController,
    playingPage: Int,
    onDismiss: () -> Unit,
    onViewCache: () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dragThreshold = with(density) { 78.dp.toPx() }
    val listState = rememberLazyListState()
    val maxPage = controller.episodes.maxOfOrNull { it.page } ?: 1
    val episodeSlotWidth = when {
        maxPage >= 100 -> 34.dp
        maxPage >= 10 -> 30.dp
        else -> 28.dp
    }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var qualityMenuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    val context = LocalContext.current

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) qualityMenuOpen = false
    }

    fun settleDrag() {
        if (dragOffset > dragThreshold) {
            onDismiss()
        } else {
            scope.launch {
                animate(dragOffset, 0f) { value, _ -> dragOffset = value }
            }
        }
    }

    Column(
        Modifier.fillMaxSize()
            .offset { androidx.compose.ui.unit.IntOffset(0, dragOffset.roundToInt()) }
            .shadow(16.dp, shape)
            .clip(shape)
            .background(CacheSheetSurface)
            // Consume taps on the empty sheet surface so they do not reach the scrim.
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Box(
            Modifier.fillMaxWidth().height(28.dp)
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            dragOffset = (dragOffset + amount).coerceAtLeast(0f)
                        },
                        onDragEnd = { settleDrag() },
                        onDragCancel = { settleDrag() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.width(40.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CacheGrabber)
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                stickyHeader(key = "cache-header") {
                    CacheHeader(
                        controller = controller,
                        qualityMenuOpen = qualityMenuOpen,
                        onQualityMenuOpenChange = { qualityMenuOpen = it },
                        onModeChange = {
                            qualityMenuOpen = false
                            controller.selectMode(it)
                        },
                    )
                }

                if (controller.episodes.isEmpty()) {
                    item(key = "cache-empty") {
                        Box(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("正在加载分集…", color = CacheMuted, fontSize = 13.sp)
                        }
                    }
                } else {
                    items(controller.episodes, key = { it.cid }) { episode ->
                        CacheEpisodeRow(
                            episode = episode,
                            playing = episode.page == playingPage,
                            episodeSlotWidth = episodeSlotWidth,
                            onClick = { controller.onEpisodeClick(episode.cid) },
                        )
                    }
                }

                item(key = "cache-bottom-space") { Spacer(Modifier.height(4.dp)) }
            }
        }

        CacheFooter(
            controller = controller,
            onViewCache = {
                when {
                    controller.hasActiveWork -> {
                        Toast.makeText(context, "还有缓存任务进行中，完成后再查看", Toast.LENGTH_SHORT).show()
                    }

                    controller.cachedVideoCount == 0 && controller.cachedAudioCount == 0 -> {
                        Toast.makeText(context, "还没有缓存内容", Toast.LENGTH_SHORT).show()
                    }

                    controller.cachedVideoCount == 0 -> {
                        Toast.makeText(context, "音频缓存已保存到 Music/BiliBox", Toast.LENGTH_SHORT).show()
                    }

                    else -> onViewCache()
                }
            },
        )
    }
}

@Composable
private fun CacheHeader(
    controller: OnlineCacheController,
    qualityMenuOpen: Boolean,
    onQualityMenuOpenChange: (Boolean) -> Unit,
    onModeChange: (OnlineCacheKind) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .background(CacheHeaderSurface)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CacheQualityTrigger(
                controller = controller,
                expanded = qualityMenuOpen,
                onExpandedChange = onQualityMenuOpenChange,
            )
            CacheModeSwitch(controller.mode, onModeChange)
        }
        HorizontalDivider(color = CacheLine.copy(alpha = .7f), thickness = 1.dp)
    }
}

@Composable
private fun CacheQualityTrigger(
    controller: OnlineCacheController,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "缓存画质箭头",
    )

    Box {
        Row(
            Modifier.height(38.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(CacheQualitySurface)
                .clickable(role = Role.Button) { onExpandedChange(!expanded) }
                .padding(horizontal = 11.dp)
                .semantics {
                    contentDescription = if (controller.mode == OnlineCacheKind.VIDEO) {
                        "缓存画质：${controller.currentQuality.label}"
                    } else {
                        "缓存音质：${controller.currentQuality.label}"
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (controller.mode == OnlineCacheKind.VIDEO) "画质" else "音质",
                color = Color(0xFF8A8E95),
                fontSize = 13.sp,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                controller.currentQuality.label,
                color = Color(0xFF3A3D42),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(4.dp))
            AppGlyphIcon(
                icon = AppIcon.CHEVRON_DOWN,
                tint = Color(0xFF8E9299),
                modifier = Modifier.size(14.dp).rotate(arrowRotation),
                strokeWidth = 2f,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.width(184.dp),
            offset = DpOffset(0.dp, 8.dp),
            shape = RoundedCornerShape(12.dp),
            containerColor = Color.White,
            tonalElevation = 0.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, Color(0xFFF0F0F0)),
        ) {
            Column(Modifier.padding(horizontal = 5.dp, vertical = 4.dp)) {
                controller.currentQualities.forEach { quality ->
                    val selected = quality.id == controller.currentQuality.id
                    Row(
                        Modifier.fillMaxWidth().height(42.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(role = Role.Button) {
                                if (controller.mode == OnlineCacheKind.VIDEO) {
                                    controller.selectVideoQuality(quality)
                                } else {
                                    controller.selectAudioQuality(quality)
                                }
                                onExpandedChange(false)
                            }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            quality.label,
                            color = if (selected) OnlinePink else Color(0xFF3E4146),
                            fontSize = 13.5.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            AppGlyphIcon(
                                icon = AppIcon.CHECK,
                                tint = OnlinePink,
                                modifier = Modifier.size(15.dp),
                                strokeWidth = 2.2f,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CacheModeSwitch(
    mode: OnlineCacheKind,
    onChange: (OnlineCacheKind) -> Unit,
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (mode == OnlineCacheKind.AUDIO) 53.dp else 3.dp,
        animationSpec = tween(280, easing = CubicBezierEasing(.22f, .8f, .25f, 1f)),
        label = "缓存类型滑块",
    )
    Box(
        Modifier.width(106.dp).height(34.dp)
            .clip(CircleShape)
            .background(CacheSwitchTrack)
    ) {
        Box(
            Modifier.offset(x = thumbOffset, y = 3.dp)
                .width(50.dp).height(28.dp)
                .shadow(1.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
        )
        Row(Modifier.padding(3.dp).selectableGroup()) {
            CacheModeButton(
                label = "视频",
                selected = mode == OnlineCacheKind.VIDEO,
                onClick = { onChange(OnlineCacheKind.VIDEO) },
            )
            CacheModeButton(
                label = "音频",
                selected = mode == OnlineCacheKind.AUDIO,
                onClick = { onChange(OnlineCacheKind.AUDIO) },
            )
        }
    }
}

@Composable
private fun CacheModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier.width(50.dp).height(28.dp)
            .clip(CircleShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) OnlinePink else Color(0xFF7B7F86),
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun CacheEpisodeRow(
    episode: OnlineCacheEpisodeUi,
    playing: Boolean,
    episodeSlotWidth: Dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(12.dp)
    val surface by animateColorAsState(
        targetValue = when {
            playing && pressed -> OnlinePinkSoft.copy(alpha = .18f)
            playing -> OnlinePinkSoft
            pressed -> CacheItemPressed
            else -> CacheItemSurface
        },
        animationSpec = tween(160),
        label = "缓存卡片背景",
    )
    val outline by animateColorAsState(
        targetValue = if (playing) OnlinePink else Color.Transparent,
        animationSpec = tween(160),
        label = "缓存卡片描边",
    )

    Row(
        Modifier.fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(shape)
            .background(surface)
            .border(1.dp, outline, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "P${episode.page} ${episode.part}，${episode.stateDescription(playing)}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(episodeSlotWidth), contentAlignment = Alignment.CenterStart) {
            if (playing) {
                EqualizerBars()
            } else {
                Text(
                    "P${episode.page}",
                    color = OnlineMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            episode.part,
            color = if (playing) OnlinePink else CacheInk,
            fontSize = 13.sp,
            fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(28.dp).clearAndSetSemantics { }) {
            CacheStatusIcon(
                badge = episode.badge(),
                cardColor = surface,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun CacheStatusIcon(
    badge: OnlineCacheBadge,
    cardColor: Color,
    modifier: Modifier = Modifier,
) {
    when (badge) {
        OnlineCacheBadge.None -> Unit

        OnlineCacheBadge.Queued -> QueuedBadge()

        is OnlineCacheBadge.Downloading -> DownloadingBadge(badge.progress, badge.supplement, modifier)

        OnlineCacheBadge.Failed -> FailedBadge(modifier)

        OnlineCacheBadge.Video -> VideoDoneBadge(modifier)

        OnlineCacheBadge.Audio -> AudioDoneBadge(modifier)

        OnlineCacheBadge.Both -> Box(modifier.size(28.dp)) {
            VideoDoneBadge(Modifier.align(Alignment.Center))
            AudioDoneBadge(
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = 1.dp, y = 1.dp),
                size = 14.dp,
                background = OnlinePink,
                borderColor = cardColor,
            )
        }
    }
}

@Composable
private fun QueuedBadge() {
    val transition = rememberInfiniteTransition(label = "缓存排队")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "缓存排队旋转",
    )
    Canvas(Modifier.size(20.dp).rotate(angle)) {
        val stroke = 2.dp.toPx()
        drawCircle(
            color = Color(0xFFCCCCCC),
            radius = (size.minDimension - stroke) / 2f,
            style = Stroke(
                width = stroke,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f),
            ),
        )
    }
}

@Composable
private fun DownloadingBadge(
    progress: Float,
    supplement: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (supplement) OnlinePink else CacheGreen
    val animatedProgress by animateFloatAsState(
        targetValue = if (progress >= 0f) progress.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(120, easing = LinearEasing),
        label = "缓存下载进度",
    )
    val transition = rememberInfiniteTransition(label = "缓存下载旋转")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "缓存下载旋转角度",
    )

    Canvas(modifier.size(28.dp).then(if (progress < 0f) Modifier.rotate(spin) else Modifier)) {
        val stroke = 2.5.dp.toPx()
        drawCircle(
            color = Color(0xFFE4E5E8),
            radius = (size.minDimension - stroke) / 2f,
            style = Stroke(width = stroke),
        )
        drawArc(
            color = color,
            startAngle = -90f,
            sweepAngle = if (progress < 0f) 92f else 360f * animatedProgress,
            useCenter = false,
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

@Composable
private fun FailedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier.size(20.dp).clip(CircleShape).background(CacheRed),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "!",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 13.sp,
        )
    }
}

@Composable
private fun VideoDoneBadge(modifier: Modifier = Modifier) {
    Canvas(modifier.size(20.dp)) {
        drawCircle(CacheGreen)
        drawCheck(Color.White)
    }
}

@Composable
private fun AudioDoneBadge(
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    background: Color = CacheGreen,
    borderColor: Color? = null,
) {
    Box(
        modifier.size(size)
            .then(if (borderColor != null) Modifier.border(1.5.dp, borderColor, CircleShape) else Modifier)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            drawMusicNote(Color.White)
        }
    }
}

private fun DrawScope.drawCheck(color: Color) {
    val unit = size.minDimension / 24f
    val path = Path().apply {
        moveTo(7f * unit, 12f * unit)
        lineTo(10.5f * unit, 15.5f * unit)
        lineTo(17f * unit, 9f * unit)
    }
    drawPath(
        path = path,
        color = color,
        style = Stroke(width = 2.5f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private val CacheMusicNote = PathParser().parsePathString(
    "M659.000889 7.395556c-12.515556 17.521778-80.099556 47.559111-137.671111 60.074666-72.590222 15.018667-110.136889 27.534222-147.683556 47.559111C323.584 145.066667 296.049778 182.613333 301.056 217.656889c2.503111 7.509333 62.577778 95.118222 132.664889 195.242667 72.590222 100.124444 132.664889 185.230222 135.168 187.733333 2.503111 5.006222-7.509333 5.006222-42.552889 2.503111-110.136889-7.509333-210.261333 50.062222-255.317333 140.174222-12.515556 27.534222-15.018667 42.552889-15.018667 75.093334 0 75.093333 40.049778 135.168 115.143111 170.211555 30.037333 12.515556 40.049778 15.018667 102.627556 15.018667 67.584 0 70.087111 0 115.143111-22.528 120.149333-57.571556 170.211556-170.211556 120.149333-272.839111-10.012444-22.528-72.590222-130.161778-140.174222-240.298667L446.236444 265.216h20.024889c80.099556-5.006222 135.168-30.037333 172.714667-72.590222 32.540444-37.546667 42.552889-75.093333 40.049778-135.168-2.503111-55.068444-7.509333-67.584-20.024889-50.062222z"
).toPath()

private fun DrawScope.drawMusicNote(color: Color) {
    // Same transform as the reference SVG: the 1024-unit note is scaled into
    // a 24-unit circle with a 4.5-unit inset, then centered by the parent Box.
    val unit = size.minDimension / 24f
    withTransform({
        translate(4.5f * unit, 4.5f * unit)
        scale(0.015f * unit, 0.015f * unit, pivot = Offset.Zero)
    }) {
        drawPath(CacheMusicNote, color)
    }
}

@Composable
private fun CacheFooter(
    controller: OnlineCacheController,
    onViewCache: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        CachePillButton(
            label = "缓存全部",
            primary = true,
            modifier = Modifier.weight(1f),
            onClick = { controller.cacheAll() },
        )
        CachePillButton(
            label = "查看缓存",
            primary = false,
            modifier = Modifier.weight(1f),
            onClick = onViewCache,
        )
    }
}

@Composable
private fun CachePillButton(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier.height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(if (primary) OnlinePink else Color.White)
            .then(
                if (primary) {
                    Modifier
                } else {
                    Modifier.border(1.dp, CacheSecondaryBorder, RoundedCornerShape(23.dp))
                }
            )
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (primary) Color.White else Color(0xFF222222),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
