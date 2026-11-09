package io.github.bilibox.feature.favorites

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.model.VideoSummary
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.theme.LocalAccentColor
import io.github.bilibox.data.favorites.FavoriteFolder
import io.github.bilibox.data.favorites.FavoriteOrder

@Composable
internal fun FavoritesScreen(
    holder: FavoritesStateHolder?,
    folderListState: LazyListState,
    videoListState: LazyListState,
    onBack: () -> Unit,
    onVideoClick: (VideoSummary) -> Unit,
    darkMode: Boolean = false,
) {
    val state = holder?.state ?: FavoritesUiState(needsLogin = true, error = "请先登录 B 站账号，查看你的收藏夹与收藏视频")
    LaunchedEffect(holder) { holder?.start() }
    val back: () -> Unit = { if (state.folder != null) holder?.closeFolder() else onBack() }
    BackHandler(onBack = back)
    // 每次换夹、搜索或排序回到列表顶部；从播放页返回保留原位置。
    LaunchedEffect(state.folder?.id, state.keyword, state.order) {
        if (state.page == 0) videoListState.scrollToItem(0)
    }
    FavoritesContent(
        state = state,
        listState = if (state.folder == null) folderListState else videoListState,
        onBack = back,
        onRefresh = { holder?.refresh() },
        onRetry = { holder?.retry() },
        onLoadMore = { holder?.loadMore() },
        onFolderClick = { holder?.openFolder(it) },
        onVideoClick = onVideoClick,
        onFilter = { keyword, order -> holder?.filter(keyword, order) },
        onLogin = onBack,
        darkMode = darkMode,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FavoritesContent(
    state: FavoritesUiState,
    listState: LazyListState = rememberLazyListState(),
    onBack: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onRetry: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onFolderClick: (FavoriteFolder) -> Unit = {},
    onVideoClick: (VideoSummary) -> Unit = {},
    onFilter: (String, FavoriteOrder) -> Unit = { _, _ -> },
    onLogin: () -> Unit = {},
    darkMode: Boolean = false,
) {
    val accent = LocalAccentColor.current
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "返回" }) { AppGlyphIcon(AppIcon.BACK, colors.onBackground) }
            Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                Text(state.folder?.title ?: "我的收藏", color = colors.onBackground, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (state.folder == null) "我创建的收藏夹" else "${state.total} 个收藏 · ${if (state.folder.isPublic) "公开" else "私密"}",
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
            IconButton(onClick = onRefresh, enabled = !state.loading && !state.needsLogin, modifier = Modifier.semantics { contentDescription = "刷新收藏" }) {
                AppGlyphIcon(AppIcon.REFRESH, accent)
            }
        }
        if (state.folder != null && !state.needsLogin) {
            FavoritesFilters(state, onFilter)
        }
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = .4f))
        PullToRefreshBox(
            isRefreshing = state.loading,
            onRefresh = { if (!state.loading && !state.needsLogin) onRefresh() },
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (state.needsLogin) {
                    item { FavoritesMessage(state.error.orEmpty(), "返回登录", onLogin) }
                } else {
                    if (state.folder == null) {
                        items(state.folders, key = { "folder-${it.id}" }) { folder ->
                            FavoriteFolderCard(folder, onClick = { onFolderClick(folder) })
                        }
                    } else {
                        items(state.videos, key = { "video-${it.id}" }) { video ->
                            FavoriteVideoCard(video, darkMode = darkMode, onClick = { video.video?.let(onVideoClick) })
                        }
                    }
                    when {
                        state.error != null -> item { FavoritesMessage(state.error, "重试", onRetry) }

                        state.loading -> item {
                            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = accent, modifier = Modifier.size(28.dp))
                            }
                        }

                        state.folder == null && state.folders.isEmpty() -> item {
                            FavoritesMessage("还没有收藏夹\n在 B 站创建收藏夹后，下拉刷新即可同步", "刷新", onRefresh)
                        }

                        state.folder != null && state.videos.isEmpty() -> item {
                            FavoritesMessage(if (state.keyword.isBlank()) "这个收藏夹还没有视频" else "没有找到相关收藏视频", null, {})
                        }

                        state.hasMore -> item {
                            TextButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) { Text("加载更多", color = accent) }
                        }

                        else -> item {
                            Text("已经到底了", color = colors.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(12.dp), textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesFilters(state: FavoritesUiState, onFilter: (String, FavoriteOrder) -> Unit) {
    var keyword by rememberSaveable(state.folder?.id, state.keyword) { mutableStateOf(state.keyword) }
    var orderOpen by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it },
            label = { Text("搜索此收藏夹") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focus.clearFocus()
                onFilter(keyword, state.order)
            }),
            trailingIcon = {
                TextButton(onClick = {
                    focus.clearFocus()
                    onFilter(keyword, state.order)
                }) { Text("搜索") }
            },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (state.keyword.isNotEmpty()) {
                TextButton(onClick = { onFilter("", state.order) }) { Text("清除搜索") }
            }
            Spacer(Modifier.weight(1f))
            Box {
                TextButton(onClick = { orderOpen = true }) {
                    Text(orderLabel(state.order))
                    AppGlyphIcon(AppIcon.CHEVRON_DOWN, LocalAccentColor.current, Modifier.padding(start = 4.dp).size(16.dp))
                }
                DropdownMenu(expanded = orderOpen, onDismissRequest = { orderOpen = false }) {
                    FavoriteOrder.entries.forEach { order ->
                        DropdownMenuItem(text = { Text(orderLabel(order)) }, onClick = {
                            orderOpen = false
                            onFilter(state.keyword, order)
                        })
                    }
                }
            }
        }
    }
}

private fun orderLabel(order: FavoriteOrder): String = when (order) {
    FavoriteOrder.SAVED -> "最近收藏"
    FavoriteOrder.PUBLISHED -> "最新发布"
    FavoriteOrder.MOST_PLAYED -> "最多播放"
}

@Composable
private fun FavoritesMessage(message: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AppGlyphIcon(AppIcon.STAR, LocalAccentColor.current, Modifier.size(40.dp))
        Text(message, Modifier.padding(top = 16.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}
