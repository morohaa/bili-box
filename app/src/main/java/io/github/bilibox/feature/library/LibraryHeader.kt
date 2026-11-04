package io.github.bilibox.feature.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import io.github.bilibox.core.ui.theme.Ink
import io.github.bilibox.core.ui.theme.Muted
import io.github.bilibox.core.ui.theme.Pink
import io.github.bilibox.data.localvideo.LocalVideoLibraryView
import io.github.bilibox.data.localvideo.LocalVideoQuickFilter
import io.github.bilibox.data.localvideo.LocalVideoSettings
import io.github.bilibox.data.localvideo.LocalVideoViewMode
import io.github.bilibox.data.localvideo.localVideoFolderLabel

@Composable
internal fun LibraryHeader(
    searchOpen: Boolean,
    query: String,
    onSearchOpen: () -> Unit,
    onSearchClose: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSettings: () -> Unit,
    focusSearcher: Boolean,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(focusSearcher) {
        if (focusSearcher) runCatching { focusRequester.requestFocus() }
    }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searchOpen) {
            Row(
                Modifier.weight(1f).height(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(LibraryCard)
                    .border(1.dp, LibraryLine, RoundedCornerShape(21.dp))
                    .padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LocalGlyphIcon(LocalIcon.SEARCH, Pink, Modifier.size(17.dp), 2f)
                Spacer(Modifier.width(8.dp))
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    singleLine = true,
                    textStyle = TextStyle(color = Ink, fontSize = 14.sp),
                    cursorBrush = SolidColor(Pink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    decorationBox = { inner ->
                        Box {
                            if (query.isEmpty()) {
                                Text("搜索本地视频", color = Muted, fontSize = 13.5.sp)
                            }
                            inner()
                        }
                    },
                )
                if (query.isNotEmpty()) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape)
                            .semantics { contentDescription = "清空搜索" }
                            .clickable { onQueryChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        LocalGlyphIcon(LocalIcon.CLOSE, Muted, Modifier.size(13.dp), 2f)
                    }
                }
            }
            Text(
                "取消",
                color = Pink,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onSearchClose).padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            )
        } else {
            Text(
                "本地视频",
                color = Ink,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-.5).sp,
                modifier = Modifier.weight(1f),
            )
            HeaderIconButton(LocalIcon.SEARCH, "搜索本地视频", onSearchOpen)
            Spacer(Modifier.width(8.dp))
            HeaderIconButton(LocalIcon.TUNE, "显示设置", onSettings)
        }
    }
}

@Composable
internal fun HeaderIconButton(icon: LocalIcon, description: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp)
            .shadow(8.dp, CircleShape, ambientColor = Color(0xFF8A9AAF).copy(alpha = .18f), spotColor = Color(0xFF8A9AAF).copy(alpha = .18f))
            .clip(CircleShape)
            .background(LibraryCard)
            .semantics { contentDescription = description }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        LocalGlyphIcon(icon, Ink, Modifier.size(20.dp), 1.8f)
    }
}

@Composable
internal fun QuickFilterRow(
    selected: LocalVideoQuickFilter?,
    onSelect: (LocalVideoQuickFilter) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LocalVideoQuickFilter.entries.forEach { filter ->
            val active = selected == filter
            val background by animateColorAsState(
                if (active) LibrarySoftPink else LibraryCard,
                animationSpec = tween(180),
                label = "quick filter background",
            )
            val contentColor by animateColorAsState(
                if (active) Pink else LibrarySecondary,
                animationSpec = tween(180),
                label = "quick filter content",
            )
            Row(
                Modifier.height(31.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(background)
                    .border(1.dp, if (active) Pink.copy(alpha = .28f) else LibraryLine, RoundedCornerShape(16.dp))
                    .clickable { onSelect(filter) }
                    .padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LocalGlyphIcon(quickFilterIcon(filter), contentColor, Modifier.size(14.dp), 2f)
                Spacer(Modifier.width(5.dp))
                Text(
                    filter.label,
                    color = contentColor,
                    fontSize = 11.5.sp,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
    Spacer(Modifier.height(6.dp))
}

internal fun quickFilterIcon(filter: LocalVideoQuickFilter): LocalIcon = when (filter) {
    LocalVideoQuickFilter.RECENT -> LocalIcon.CLOCK
    LocalVideoQuickFilter.BILI_CACHE -> LocalIcon.DOWNLOAD
    LocalVideoQuickFilter.PRIVATE -> LocalIcon.LOCK
    LocalVideoQuickFilter.UNFINISHED -> LocalIcon.STATUS
    LocalVideoQuickFilter.FINISHED -> LocalIcon.CHECK
}

@Composable
internal fun SortBar(
    view: LocalVideoLibraryView,
    settings: LocalVideoSettings,
    nav: List<String>,
    query: String,
    quickFilter: LocalVideoQuickFilter?,
    onNavigateTo: (Int) -> Unit,
    onRoot: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            query.isNotBlank() -> CrumbCount("搜索到 ${view.videos.size} 个视频")

            quickFilter != null -> CrumbCount("${quickFilter.label} · ${view.videos.size} 个视频")

            nav.isEmpty() -> CrumbCount(
                if (settings.mode == LocalVideoViewMode.FILES) {
                    "全部视频 · ${view.videos.size}"
                } else {
                    "${view.folders.size} 个文件夹 · ${view.videos.size} 个视频"
                }
            )

            else -> {
                CrumbLink("根目录", onRoot)
                nav.forEachIndexed { index, path ->
                    Spacer(Modifier.width(2.dp))
                    LocalGlyphIcon(LocalIcon.CHEVRON_RIGHT, Muted.copy(alpha = .7f), Modifier.size(11.dp), 2f)
                    Spacer(Modifier.width(2.dp))
                    val isLast = index == nav.lastIndex
                    val label = localVideoFolderLabel(path)
                    if (isLast) {
                        Text(
                            label,
                            color = Ink,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 150.dp),
                        )
                    } else {
                        CrumbLink(label) { onNavigateTo(index) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CrumbCount(text: String) {
    Text(text, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
internal fun CrumbLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Pink,
        fontSize = 11.5.sp,
        maxLines = 1,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 2.dp),
    )
}
