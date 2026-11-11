package io.github.bilibox.feature.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.testTag
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
import io.github.bilibox.data.localvideo.LocalVideoQuickFilter
import io.github.bilibox.data.localvideo.localVideoFolderLabel

private val HeaderControlHeight = 48.dp
private val SearchFieldHeight = 36.dp
private val HeaderControlShape = RoundedCornerShape(24.dp)

/**
 * 本地视频页头部：渐变底 + 顶部搜索框 + 筛选胶囊。
 *
 * 搜索框放在最上层，页面不再有单独的大标题；根目录显示快捷筛选，
 * 进入文件夹后换成路径面包屑。搜索展开时只保留搜索框。
 */
@Composable
internal fun LibraryTopBar(
    inFolder: Boolean,
    searchOpen: Boolean,
    query: String,
    breadcrumbs: List<String>,
    filter: LocalVideoQuickFilter?,
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
    val style = remember(palette) { libraryHeaderStyle(palette) }
    Column(
        Modifier.fillMaxWidth()
            .background(style.brush)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(HeaderControlHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LibrarySearchField(
                style = style,
                searchOpen = searchOpen,
                query = query,
                onOpen = onSearchOpen,
                onQueryChange = onQueryChange,
                modifier = Modifier.weight(1f),
            )
            // 固定右侧占位，避免「设置」退出与「取消」进入时反复挤压搜索框。
            AnimatedContent(
                targetState = searchOpen,
                modifier = Modifier.size(HeaderControlHeight),
                transitionSpec = { fadeIn(tween(140)).togetherWith(fadeOut(tween(90))) },
                contentAlignment = Alignment.Center,
                label = "header action",
            ) {
                if (it) {
                    Box(
                        Modifier.fillMaxSize().clip(HeaderControlShape)
                            .pressFeedback(onClick = onSearchClose, shape = HeaderControlShape, scaleTo = .96f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("取消", color = style.content, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                } else {
                    HeaderIconButton(style, AppIcon.TUNE, "显示与排序设置", onSettings)
                }
            }
        }

        AnimatedVisibility(
            visible = !searchOpen,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(100)),
        ) {
            if (inFolder) {
                BreadcrumbBar(style, breadcrumbs, onRoot, onBreadcrumb)
            } else {
                FilterChips(style, filter, onClear = onClearFilter, onSelect = onSelectFilter)
            }
        }
    }
}

@Composable
private fun HeaderIconButton(style: LibraryHeaderStyle, icon: AppIcon, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(HeaderControlHeight)
            .clip(HeaderControlShape)
            .semantics { contentDescription = description }
            .pressFeedback(onClick = onClick, shape = HeaderControlShape, scaleTo = .94f),
        contentAlignment = Alignment.Center,
    ) {
        AppGlyphIcon(icon, style.content, Modifier.size(20.dp), 2f)
    }
}

@Composable
private fun LibrarySearchField(
    style: LibraryHeaderStyle,
    searchOpen: Boolean,
    query: String,
    onOpen: () -> Unit,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalLibraryPalette.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(searchOpen) {
        if (searchOpen) runCatching { focusRequester.requestFocus() }
    }
    Row(
        modifier.height(SearchFieldHeight)
            .testTag("library-search-field")
            .clip(HeaderControlShape)
            .background(style.searchBackground)
            .then(if (searchOpen) Modifier else Modifier.pressFeedback(onClick = onOpen, shape = HeaderControlShape, scaleTo = 1f))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppGlyphIcon(AppIcon.SEARCH, style.searchHint, Modifier.size(16.dp), 2.1f)
        Spacer(Modifier.width(8.dp))
        if (searchOpen) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f).focusRequester(focusRequester),
                singleLine = true,
                textStyle = TextStyle(color = style.searchText, fontSize = 14.5.sp),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                "搜索视频或文件夹",
                                color = style.searchHint,
                                fontSize = 14.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
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
                    AppGlyphIcon(AppIcon.CLOSE, style.searchHint, Modifier.size(12.dp), 2.3f)
                }
            }
        } else {
            Text(
                "搜索视频或文件夹",
                color = style.searchHint,
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
    style: LibraryHeaderStyle,
    selected: LocalVideoQuickFilter?,
    onClear: () -> Unit,
    onSelect: (LocalVideoQuickFilter) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        HeaderChip(style, "全部", selected == null, onClear)
        LocalVideoQuickFilter.entries.forEach { filter ->
            HeaderChip(style, filter.label, selected == filter) { onSelect(filter) }
        }
    }
}

@Composable
private fun HeaderChip(
    style: LibraryHeaderStyle,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        if (active) style.chipActiveBackground else Color.Transparent,
        animationSpec = tween(200),
        label = "chip background",
    )
    val content by animateColorAsState(
        if (active) style.chipActiveContent else style.chipIdleContent,
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
    style: LibraryHeaderStyle,
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
                .background(style.accentBar),
        )
        Spacer(Modifier.width(7.dp))
        AppGlyphIcon(AppIcon.FOLDER, style.breadcrumbSoft, Modifier.size(15.dp), 1.9f)
        Spacer(Modifier.width(6.dp))
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "根目录",
                color = style.contentSoft,
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
                    style.breadcrumbSoft,
                    Modifier.size(11.dp),
                    2.4f,
                )
                Spacer(Modifier.width(2.dp))
                val isLast = index == breadcrumbs.lastIndex
                Text(
                    localVideoFolderLabel(path),
                    color = if (isLast) style.content else style.contentSoft,
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
