package io.github.bilibox.feature.favorites

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
        FavoritesTopBar(state, onBack, onFilter)
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
private fun FavoritesMessage(message: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AppGlyphIcon(AppIcon.STAR, LocalAccentColor.current, Modifier.size(40.dp))
        Text(message, Modifier.padding(top = 16.dp, bottom = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}
