package io.github.bilibox.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.data.localvideo.LocalVideoFolder
import io.github.bilibox.data.localvideo.LocalVideoLibraryView
import io.github.bilibox.data.localvideo.LocalVideoQuickFilter
import io.github.bilibox.data.localvideo.LocalVideoSettings
import io.github.bilibox.data.localvideo.LocalVideoSort
import io.github.bilibox.data.localvideo.LocalVideoViewMode
import io.github.bilibox.data.localvideo.localVideoFolderLabel

/**
 * 本地视频页头部。
 *
 * 不再用整块彩色标题栏，而是「大标题 + 白色搜索框 + 克制的筛选胶囊」：
 * 品牌色只出现在顶部光晕、选中态和少量图标上。进入文件夹后，胶囊行换成
 * 路径面包屑并出现返回键；搜索展开时其余行淡出，头部保持紧凑。
 */
@Composable
internal fun LibraryTopBar(
    inFolder: Boolean,
    searchOpen: Boolean,
    query: String,
    breadcrumbs: List<String>,
    filter: LocalVideoQuickFilter?,
    onBack: () -> Unit,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSettings: () -> Unit,
    onRoot: () -> Unit,
    onBreadcrumb: (Int) -> Unit,
    onClearFilter: () -> Unit,
    onSelectFilter: (LocalVideoQuickFilter) -> Unit,
) {
    val palette = LocalLibraryPalette.current
    Column(
        Modifier.fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(46.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "本地视频",
                color = palette.ink,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-.6).sp,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            HeaderGlyphButton(AppIcon.SEARCH, "搜索本地视频", palette, onSearchOpen)
            HeaderGlyphButton(AppIcon.TUNE, "显示与排序设置", palette, onSettings)
        }

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AnimatedVisibility(
                visible = inFolder && !searchOpen,
                enter = fadeIn(tween(160)) + expandHorizontally(tween(180)),
                exit = fadeOut(tween(120)) + shrinkHorizontally(tween(150)),
            ) {
                Box(
                    Modifier.size(34.dp).clip(CircleShape)
                        .semantics { contentDescription = "返回上一级" }
                        .pressFeedback(onClick = onBack, shape = CircleShape, scaleTo = .9f),
                    contentAlignment = Alignment.Center,
                ) {
                    AppGlyphIcon(AppIcon.BACK, palette.ink2, Modifier.size(21.dp), 2.2f)
                }
            }
            LibrarySearchField(
                searchOpen = searchOpen,
                query = query,
                palette = palette,
                onOpen = onSearchOpen,
                onQueryChange = onQueryChange,
                modifier = Modifier.weight(1f),
            )
            AnimatedVisibility(
                visible = searchOpen,
                enter = fadeIn(tween(160)) + expandHorizontally(tween(180)),
                exit = fadeOut(tween(120)) + shrinkHorizontally(tween(150)),
            ) {
                Text(
                    "取消",
                    color = palette.accent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .pressFeedback(onClick = onSearchClose)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = !searchOpen,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(100)),
        ) {
            if (inFolder) {
                BreadcrumbBar(palette, breadcrumbs, onRoot, onBreadcrumb)
            } else {
                FilterChips(palette, filter, onClear = onClearFilter, onSelect = onSelectFilter)
            }
        }
    }
}

@Composable
private fun HeaderGlyphButton(
    icon: AppIcon,
    description: String,
    palette: LibraryPalette,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(40.dp)
            .clip(CircleShape)
            .semantics { contentDescription = description }
            .pressFeedback(onClick = onClick, shape = CircleShape, scaleTo = .9f),
        contentAlignment = Alignment.Center,
    ) {
        AppGlyphIcon(icon, palette.ink2, Modifier.size(20.dp), 1.9f)
    }
}

@Composable
private fun LibrarySearchField(
    searchOpen: Boolean,
    query: String,
    palette: LibraryPalette,
    onOpen: () -> Unit,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(searchOpen) {
        if (searchOpen) runCatching { focusRequester.requestFocus() }
    }
    val background by animateColorAsState(
        if (searchOpen) palette.card else palette.card.copy(alpha = .92f),
        animationSpec = tween(180),
        label = "search background",
    )

    Row(
        modifier.height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .then(if (searchOpen) Modifier else Modifier.pressFeedback(onClick = onOpen, shape = RoundedCornerShape(14.dp)))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyphIcon(AppIcon.SEARCH, palette.ink3, Modifier.size(16.dp), 2.1f)
        Spacer(Modifier.width(8.dp))
        if (searchOpen) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                singleLine = true,
                textStyle = TextStyle(color = palette.ink, fontSize = 14.5.sp),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text("搜索视频或文件夹", color = palette.ink3, fontSize = 14.5.sp)
                        }
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                Box(
                    Modifier.size(22.dp).clip(CircleShape)
                        .semantics { contentDescription = "清空搜索" }
                        .clickable { onQueryChange("") },
                    contentAlignment = Alignment.Center,
                ) {
                    AppGlyphIcon(AppIcon.CLOSE, palette.ink3, Modifier.size(12.dp), 2.3f)
                }
            }
        } else {
            Text(
                "搜索视频或文件夹",
                color = palette.ink3,
                fontSize = 14.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FilterChips(
    palette: LibraryPalette,
    selected: LocalVideoQuickFilter?,
    onClear: () -> Unit,
    onSelect: (LocalVideoQuickFilter) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        HeaderChip(palette, "全部", selected == null, onClear)
        LocalVideoQuickFilter.entries.forEach { filter ->
            HeaderChip(palette, filter.label, selected == filter) { onSelect(filter) }
        }
    }
}

@Composable
private fun HeaderChip(
    palette: LibraryPalette,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        if (active) palette.accentSoft else Color.Transparent,
        animationSpec = tween(200),
        label = "chip background",
    )
    val content by animateColorAsState(
        if (active) palette.accent else palette.ink2,
        animationSpec = tween(200),
        label = "chip content",
    )
    Box(
        Modifier.height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .pressFeedback(onClick = onClick, shape = RoundedCornerShape(16.dp), scaleTo = .95f)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = content,
            fontSize = 13.5.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun BreadcrumbBar(
    palette: LibraryPalette,
    breadcrumbs: List<String>,
    onRoot: () -> Unit,
    onBreadcrumb: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(3.dp).height(13.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(palette.accent),
        )
        Spacer(Modifier.width(7.dp))
        AppGlyphIcon(AppIcon.FOLDER, palette.ink3, Modifier.size(15.dp), 1.9f)
        Spacer(Modifier.width(6.dp))
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "根目录",
                color = palette.ink2,
                fontSize = 14.sp,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .pressFeedback(onClick = onRoot, shape = RoundedCornerShape(8.dp), scaleTo = .96f)
                    .padding(vertical = 3.dp, horizontal = 2.dp),
            )
            breadcrumbs.forEachIndexed { index, path ->
                Spacer(Modifier.width(2.dp))
                AppGlyphIcon(
                    AppIcon.CHEVRON_RIGHT,
                    palette.ink3.copy(alpha = .7f),
                    Modifier.size(11.dp),
                    2.4f,
                )
                Spacer(Modifier.width(2.dp))
                val isLast = index == breadcrumbs.lastIndex
                Text(
                    localVideoFolderLabel(path),
                    color = if (isLast) palette.ink else palette.ink2,
                    fontSize = 14.sp,
                    fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = 150.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (isLast) {
                                Modifier
                            } else {
                                Modifier.pressFeedback(
                                    onClick = { onBreadcrumb(index) },
                                    shape = RoundedCornerShape(8.dp),
                                    scaleTo = .96f,
                                )
                            }
                        )
                        .padding(vertical = 3.dp, horizontal = 2.dp),
                )
            }
        }
    }
}

/**
 * 列表上方的概览行：左边是当前目录的数量统计，右边的排序胶囊点开设置弹窗。
 */
@Composable
internal fun LibrarySummaryRow(
    view: LocalVideoLibraryView,
    settings: LocalVideoSettings,
    nav: List<String>,
    query: String,
    quickFilter: LocalVideoQuickFilter?,
    onOpenSettings: () -> Unit,
) {
    val palette = LocalLibraryPalette.current
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            librarySummaryText(view, settings, nav, query, quickFilter),
            color = palette.ink3,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        SortChip(palette = palette, sort = settings.sort, ascending = settings.ascending, onClick = onOpenSettings)
    }
}

internal fun librarySummaryText(
    view: LocalVideoLibraryView,
    settings: LocalVideoSettings,
    nav: List<String>,
    query: String,
    quickFilter: LocalVideoQuickFilter?,
): String = when {
    query.isNotBlank() -> "搜索到 ${view.videos.size} 个视频"
    quickFilter != null -> "${quickFilter.label} · ${view.videos.size} 个视频"
    nav.isNotEmpty() -> "${view.videos.size} 个视频"
    settings.mode == LocalVideoViewMode.FILES -> "全部视频 · ${view.videos.size}"
    else -> "${view.folders.size} 个文件夹 · ${view.videos.size} 个视频"
}

@Composable
private fun SortChip(
    palette: LibraryPalette,
    sort: LocalVideoSort,
    ascending: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier.height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.card.copy(alpha = .92f))
            .pressFeedback(onClick = onClick, shape = RoundedCornerShape(14.dp), scaleTo = .95f)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyphIcon(AppIcon.SORT, palette.ink3, Modifier.size(12.dp), 2f)
        Spacer(Modifier.width(4.dp))
        Text(
            "${sort.label} ${if (ascending) "↑" else "↓"}",
            color = palette.ink2,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** 文件夹行/卡片共用的路径提示，只在「路径」字段开启时补一行。 */
@Composable
internal fun FolderPathHint(folder: LocalVideoFolder) {
    val palette = LocalLibraryPalette.current
    Text(
        "内部存储 / ${folder.path}",
        color = palette.ink3,
        fontSize = 10.5.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** 展开/收起图标的旋转角度。 */
@Composable
internal fun chevronRotation(expanded: Boolean): Float {
    val rotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        animationSpec = tween(220),
        label = "chevron rotation",
    )
    return rotation
}
