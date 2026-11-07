package io.github.bilibox.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.model.SearchDuration
import io.github.bilibox.core.model.SearchOrder
import io.github.bilibox.core.model.SearchTimeRange
import io.github.bilibox.core.model.VideoSummary
import io.github.bilibox.core.ui.theme.BrandSpringEasing
import io.github.bilibox.core.ui.theme.DynamicColors
import io.github.bilibox.core.ui.theme.HomeBackground
import io.github.bilibox.core.ui.theme.LocalAccentColor
import io.github.bilibox.core.ui.theme.appearanceBackdrop
import io.github.bilibox.core.ui.theme.dynamicColors
import io.github.bilibox.data.search.SearchHistoryStore
import io.github.bilibox.feature.search.SearchControlsBar
import io.github.bilibox.feature.search.SearchError
import io.github.bilibox.feature.search.SearchLoading
import io.github.bilibox.feature.search.SearchResults
import io.github.bilibox.feature.search.SearchUiState
import kotlinx.coroutines.delay

/**
 * 首页容器：主界面是居中的 bilibox 标志与胶囊搜索框；点按搜索框后标志收起，
 * 搜索行移到状态栏下方并让出返回与「搜索」按钮，下方展示最近搜索。
 *
 * 提交后沿用既有搜索结果渲染（[SearchResults]），顶栏样式与参考稿保持一致。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeScreen(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onRetry: (Int, SearchOrder) -> Unit,
    onVideoClick: (VideoSummary) -> Unit,
    onPageChange: (Int) -> Unit,
    searchState: SearchUiState,
    order: SearchOrder,
    onOrderChange: (SearchOrder) -> Unit,
    duration: SearchDuration?,
    timeRange: SearchTimeRange?,
    onFiltersChange: (SearchDuration?, SearchTimeRange?) -> Unit,
    gridLayout: Boolean,
    onToggleLayout: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    scrollState: ScrollState,
    darkMode: Boolean = false,
    backgroundStyle: HomeBackground = HomeBackground.HOME,
) {
    val accent = LocalAccentColor.current
    val colors = dynamicColors(darkMode, accent)
    val context = LocalContext.current
    val historyStore = remember(context) { SearchHistoryStore(context.applicationContext) }
    var history by remember(historyStore) { mutableStateOf(historyStore.load()) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    // 首页 = 未聚焦、没有输入、也没有结果；其余情况都算搜索态。
    val expanded = focused || query.isNotEmpty() || searchState != SearchUiState.Idle
    LaunchedEffect(expanded) { onExpandedChange(expanded) }

    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = 520, easing = BrandSpringEasing),
        label = "home search expansion",
    )
    // 回弹曲线会短暂越过 1，尺寸类插值需要夹紧，避免出现负高度。
    val travel = progress.coerceIn(0f, 1f)
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(durationMillis = 700, easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)))
    }

    val submit: (String) -> Unit = { raw ->
        val keyword = raw.trim()
        if (keyword.isNotEmpty()) {
            history = historyStore.add(keyword)
            if (keyword != query) onQueryChange(keyword)
            focusManager.clearFocus()
            onSearch()
        }
    }

    Box(Modifier.fillMaxSize().background(colors.background).appearanceBackdrop(backgroundStyle, accent, darkMode)) {
        BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            // 参考稿把 logo 顶边放在屏高一半 - 100；这里再上移 40dp，搜索框正好落在视觉中心。
            val homeTop = (maxHeight * 0.5f - 120.dp).coerceAtLeast(16.dp)
            val topSpace = lerp(16.dp, homeTop, 1f - travel)
            val logoHeight = 48.dp * (1f - travel)

            Column(Modifier.fillMaxSize()) {
                Spacer(Modifier.height(topSpace))
                Box(
                    Modifier.fillMaxWidth().height(logoHeight).clipToBounds(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    BrandLogo(
                        colors = colors,
                        darkMode = darkMode,
                        modifier = Modifier.graphicsLayer {
                            alpha = ((1f - travel) * entrance.value).coerceIn(0f, 1f)
                            translationY = (-40.dp.toPx() * travel) + (14.dp.toPx() * (1f - entrance.value))
                            val scale = (1f - 0.15f * travel) * (0.985f + 0.015f * entrance.value)
                            scaleX = scale
                            scaleY = scale
                            rotationZ = -2f * travel
                        },
                    )
                }
                Spacer(Modifier.height(16.dp * (1f - travel)))
                SearchBarRow(
                    query = query,
                    onQueryChange = onQueryChange,
                    onSubmit = { submit(query) },
                    onBack = {
                        focusManager.clearFocus()
                        onQueryChange("")
                    },
                    onRequestFocus = { focusRequester.requestFocus() },
                    travel = travel,
                    focused = focused,
                    colors = colors,
                    darkMode = darkMode,
                    focusRequester = focusRequester,
                    interactionSource = interactionSource,
                )
                Spacer(Modifier.height(10.dp))

                if (searchState != SearchUiState.Idle) {
                    Column(Modifier.fillMaxWidth().weight(1f)) {
                        SearchControlsBar(
                            order = order,
                            onOrderChange = onOrderChange,
                            duration = duration,
                            timeRange = timeRange,
                            onFiltersChange = onFiltersChange,
                            gridLayout = gridLayout,
                            onToggleLayout = onToggleLayout,
                            colors = colors,
                        )
                        Column(
                            Modifier.fillMaxWidth().weight(1f)
                                .verticalScroll(scrollState)
                                // 结果区比顶栏（16dp）更贴边，让视频卡片更宽、更满。
                                .padding(horizontal = 10.dp)
                        ) {
                            when (val state = searchState) {
                                SearchUiState.Idle -> Unit

                                SearchUiState.Loading -> SearchLoading(darkMode)

                                is SearchUiState.Error -> SearchError(state.message, darkMode) {
                                    onRetry(state.page, state.order)
                                }

                                is SearchUiState.Success -> SearchResults(
                                    state = state,
                                    darkMode = darkMode,
                                    gridLayout = gridLayout,
                                    onVideoClick = onVideoClick,
                                    onPageChange = onPageChange,
                                )
                            }
                            Spacer(Modifier.height(28.dp))
                        }
                    }
                } else {
                    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollState)) {
                        AnimatedVisibility(
                            visible = expanded,
                            enter = fadeIn(tween(260)) +
                                slideInVertically(tween(300, easing = BrandSpringEasing)) { -it / 8 },
                            exit = fadeOut(tween(120)),
                        ) {
                            SearchHistorySection(
                                history = history,
                                colors = colors,
                                onSelect = { submit(it) },
                                onClear = {
                                    historyStore.clear()
                                    history = emptyList()
                                },
                            )
                        }
                        Spacer(Modifier.height(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBarRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    onRequestFocus: () -> Unit,
    travel: Float,
    focused: Boolean,
    colors: DynamicColors,
    darkMode: Boolean,
    focusRequester: FocusRequester,
    interactionSource: MutableInteractionSource,
) {
    val focusManager = LocalFocusManager.current
    val backWidth = 32.dp * travel
    val goWidth = 62.dp * travel
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(backWidth).height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .clickable(enabled = travel > .5f, role = Role.Button, onClick = onBack)
                .semantics { contentDescription = "返回" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(24.dp).graphicsLayer { alpha = travel }) {
                val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val path = Path().apply {
                    moveTo(size.width * .625f, size.height * .25f)
                    lineTo(size.width * .375f, size.height * .5f)
                    lineTo(size.width * .625f, size.height * .75f)
                }
                drawPath(path, colors.ink2, style = stroke)
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.weight(1f).height(44.dp)
                .graphicsLayer {
                    shadowElevation = if (travel < .5f) 12.dp.toPx() else 4.dp.toPx()
                    shape = RoundedCornerShape(22.dp)
                    ambientShadowColor = if (darkMode) Color.Black.copy(alpha = .5f) else colors.pink.copy(alpha = .45f)
                    spotShadowColor = if (darkMode) Color.Black.copy(alpha = .5f) else colors.pink.copy(alpha = .45f)
                }
                .clip(RoundedCornerShape(22.dp))
                .background(colors.card)
                .border(1.5.dp, if (focused) colors.pink else Color.Transparent, RoundedCornerShape(22.dp))
                .padding(start = 16.dp, end = if (query.isNotEmpty()) 8.dp else 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(20.dp)
                        .clickable(role = Role.Button, onClick = onRequestFocus)
                        .semantics { contentDescription = "搜索" },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(20.dp)) {
                        val unit = size.minDimension / 24f
                        val stroke = Stroke(width = 2.4f * unit, cap = StrokeCap.Round)
                        drawCircle(colors.pink, 7f * unit, Offset(11f * unit, 11f * unit), style = stroke)
                        drawLine(
                            colors.pink,
                            Offset(16.5f * unit, 16.5f * unit),
                            Offset(20f * unit, 20f * unit),
                            stroke.width,
                            StrokeCap.Round,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = TextStyle(color = colors.ink, fontSize = 15.sp),
                    cursorBrush = SolidColor(colors.pink),
                    interactionSource = interactionSource,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        focusManager.clearFocus()
                        onSubmit()
                    }),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    "输入关键词、BV 号或粘贴链接",
                                    color = colors.ink3,
                                    fontSize = 15.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                if (query.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier.size(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button, onClick = {
                                onQueryChange("")
                                onRequestFocus()
                            })
                            .semantics { contentDescription = "清空搜索词" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.size(18.dp)) {
                            drawCircle(colors.ink3.copy(alpha = .22f))
                            val stroke = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(
                                colors.ink3,
                                Offset(size.width * .34f, size.height * .34f),
                                Offset(size.width * .66f, size.height * .66f),
                                stroke.width,
                                StrokeCap.Round,
                            )
                            drawLine(
                                colors.ink3,
                                Offset(size.width * .66f, size.height * .34f),
                                Offset(size.width * .34f, size.height * .66f),
                                stroke.width,
                                StrokeCap.Round,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.width(goWidth).height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(colors.pink)
                .clickable(enabled = travel > .5f, role = Role.Button, onClick = onSubmit)
                .semantics { contentDescription = "搜索" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "搜索",
                color = colors.onPink,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.graphicsLayer { alpha = travel },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchHistorySection(
    history: List<String>,
    colors: DynamicColors,
    onSelect: (String) -> Unit,
    onClear: () -> Unit,
) {
    var clearing by remember { mutableStateOf(false) }
    LaunchedEffect(clearing) {
        if (!clearing) return@LaunchedEffect
        delay(300L + history.size * 20L)
        onClear()
        clearing = false
    }
    Column(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("最近搜过", color = colors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                "清空",
                color = colors.ink3,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = history.isNotEmpty() && !clearing, role = Role.Button) {
                        clearing = true
                    }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .semantics { contentDescription = "清空搜索历史" },
            )
        }
        Spacer(Modifier.height(12.dp))
        if (history.isEmpty()) {
            Text(
                "暂无搜索历史",
                color = colors.ink3,
                fontSize = 13.sp,
                modifier = Modifier.padding(4.dp),
            )
        } else {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                history.forEachIndexed { index, entry ->
                    HistoryChip(
                        text = entry,
                        index = index,
                        clearing = clearing,
                        colors = colors,
                        onClick = { onSelect(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryChip(
    text: String,
    index: Int,
    clearing: Boolean,
    colors: DynamicColors,
    onClick: () -> Unit,
) {
    val drop = remember { Animatable(0f) }
    LaunchedEffect(clearing) {
        if (clearing) {
            delay((index * 20).toLong())
            drop.animateTo(1f, tween(durationMillis = 300, easing = BrandSpringEasing))
        }
    }
    Box(
        Modifier.graphicsLayer {
            alpha = 1f - drop.value
            translationY = 10.dp.toPx() * drop.value
            val scale = 1f - 0.15f * drop.value
            scaleX = scale
            scaleY = scale
        }
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(colors.chip)
            .border(1.dp, colors.chipBorder, RoundedCornerShape(17.dp))
            .clickable(enabled = !clearing, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = colors.ink,
            fontSize = 14.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * bilibox 字标：粉色的 bili + 渐变 b + 3D 纸箱 + 玫瑰金 x。
 *
 * 直接用 [androidx.compose.ui.text.TextMeasurer] 排版并按基线对齐立方体，
 * 避免 Compose Row 无法把 Canvas 与文字按基线对齐的问题。
 */
@Composable
private fun BrandLogo(colors: DynamicColors, darkMode: Boolean, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val logoSize = 48.dp
    val fontSize = with(density) { logoSize.toSp() }
    val letterSpacing = (-0.045f * fontSize.value).sp
    val pink = colors.brandPink
    val gold = colors.roseGold

    val baseStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = fontSize,
        letterSpacing = letterSpacing,
        lineHeight = fontSize,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    val bridgeStyle = baseStyle.copy(brush = Brush.linearGradient(listOf(pink, gold)))
    val layouts = remember(measurer, darkMode, fontSize) {
        LogoLayouts(
            bili = measurer.measure(AnnotatedString("bili"), baseStyle.copy(color = pink)),
            bridge = measurer.measure(AnnotatedString("b"), bridgeStyle),
            cross = measurer.measure(AnnotatedString("x"), baseStyle.copy(color = gold)),
        )
    }

    val cubeSize = with(density) { 29.76.dp.toPx() } // 0.62em
    val cubeMargin = with(density) { 3.84.dp.toPx() } // 0.08em
    val totalPx = layouts.bili.size.width + layouts.bridge.size.width +
        layouts.cross.size.width + cubeSize + cubeMargin * 2
    val totalWidth: Dp = with(density) { totalPx.toDp() }

    val topLight = if (darkMode) Color(0xFFFFF5F0) else Color(0xFFFFF0E6)
    val topDark = if (darkMode) Color(0xFFFFE2D1) else Color(0xFFFFD4C2)
    val leftLight = if (darkMode) Color(0xFFFFD4BA) else Color(0xFFF2B999)
    val leftDark = if (darkMode) Color(0xFFFFCBA4) else Color(0xFFE5A87B)
    val rightLight = if (darkMode) Color(0xFFF2B48F) else Color(0xFFD9976B)
    val rightDark = if (darkMode) Color(0xFFE59B72) else Color(0xFFC77E56)

    Canvas(modifier.width(totalWidth).height(logoSize)) {
        // 显式把基线放在字标盒内 94% 处，而不是用 layout.firstBaseline：
        // 系统字体放大后行高会变化，按 layout 基线绘制会把字底顶到盒底并被裁掉；
        // 留 3dp 余量给圆形笔画的下沿过冲，同时保持字标与搜索框之间的设计间距。
        val baseline = size.height * 0.94f
        var x = 0f
        drawText(layouts.bili, topLeft = Offset(x, baseline - layouts.bili.firstBaseline))
        x += layouts.bili.size.width
        drawText(layouts.bridge, topLeft = Offset(x, baseline - layouts.bridge.firstBaseline))
        x += layouts.bridge.size.width + cubeMargin
        drawCube(
            left = x,
            top = baseline - cubeSize,
            cube = cubeSize,
            topBrush = Brush.linearGradient(
                listOf(topLight, topDark),
                start = Offset(x + 0.05f * cubeSize, baseline - 0.97f * cubeSize),
                end = Offset(x + 0.95f * cubeSize, baseline - 0.5f * cubeSize),
            ),
            leftBrush = Brush.linearGradient(
                listOf(leftLight, leftDark),
                start = Offset(x + 0.05f * cubeSize, baseline - 0.74f * cubeSize),
                end = Offset(x + 0.37f * cubeSize, baseline),
            ),
            rightBrush = Brush.linearGradient(
                listOf(rightLight, rightDark),
                start = Offset(x + 0.5f * cubeSize, baseline - 0.74f * cubeSize),
                end = Offset(x + 0.82f * cubeSize, baseline),
            ),
        )
        x += cubeSize + cubeMargin
        drawText(layouts.cross, topLeft = Offset(x, baseline - layouts.cross.firstBaseline))
    }
}

private class LogoLayouts(
    val bili: androidx.compose.ui.text.TextLayoutResult,
    val bridge: androidx.compose.ui.text.TextLayoutResult,
    val cross: androidx.compose.ui.text.TextLayoutResult,
)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCube(
    left: Float,
    top: Float,
    cube: Float,
    topBrush: Brush,
    leftBrush: Brush,
    rightBrush: Brush,
) {
    fun point(x: Float, y: Float) = Offset(left + x / 100f * cube, top + y / 100f * cube)
    val topFace = Path().apply {
        moveTo(point(50f, 3f).x, point(50f, 3f).y)
        lineTo(point(95f, 26.5f).x, point(95f, 26.5f).y)
        lineTo(point(50f, 50f).x, point(50f, 50f).y)
        lineTo(point(5f, 26.5f).x, point(5f, 26.5f).y)
        close()
    }
    val leftFace = Path().apply {
        moveTo(point(5f, 26.5f).x, point(5f, 26.5f).y)
        lineTo(point(50f, 50f).x, point(50f, 50f).y)
        lineTo(point(50f, 97f).x, point(50f, 97f).y)
        lineTo(point(5f, 73.5f).x, point(5f, 73.5f).y)
        close()
    }
    val rightFace = Path().apply {
        moveTo(point(95f, 26.5f).x, point(95f, 26.5f).y)
        lineTo(point(50f, 50f).x, point(50f, 50f).y)
        lineTo(point(50f, 97f).x, point(50f, 97f).y)
        lineTo(point(95f, 73.5f).x, point(95f, 73.5f).y)
        close()
    }
    drawPath(topFace, topBrush)
    drawPath(leftFace, leftBrush)
    drawPath(rightFace, rightBrush)
}
