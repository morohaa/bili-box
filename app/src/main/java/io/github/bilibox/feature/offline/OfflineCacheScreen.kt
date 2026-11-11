package io.github.bilibox.feature.offline

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.theme.LocalAccentColor
import io.github.bilibox.core.util.formatBytes
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.data.localvideo.LocalVideoPlayback
import io.github.bilibox.data.localvideo.PlaybackProgressStore
import io.github.bilibox.data.online.OfflineCacheEntry
import io.github.bilibox.data.online.OfflineCacheLibrary
import io.github.bilibox.data.online.OnlineCacheRepository

@Composable
internal fun OfflineCacheScreen(
    refreshToken: Int,
    onBack: () -> Unit,
    onPlay: (LocalVideo, List<LocalVideo>) -> Unit,
    onForward: (() -> Unit) -> Unit,
    onBackward: (() -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember(context, scope) { OfflineCacheState(OnlineCacheRepository(context), PlaybackProgressStore(context), scope) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, state) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) state.refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(refreshToken) { state.refresh() }
    OfflineCacheContent(
        library = state.library,
        playback = state.playback,
        loading = state.loading,
        deleting = state.deleting,
        error = state.error,
        onRetry = state::refresh,
        onDelete = state::delete,
        onBack = onBack,
        onPlay = onPlay,
        onForward = onForward,
        onBackward = onBackward,
    )
}

@Composable
internal fun OfflineCacheContent(
    library: OfflineCacheLibrary,
    playback: Map<String, LocalVideoPlayback>,
    loading: Boolean = false,
    deleting: Boolean = false,
    error: String? = null,
    onRetry: () -> Unit = {},
    onDelete: (List<OfflineCacheEntry>) -> Unit,
    onBack: () -> Unit,
    onPlay: (LocalVideo, List<LocalVideo>) -> Unit,
    onForward: (() -> Unit) -> Unit,
    onBackward: (() -> Unit) -> Unit,
) {
    var groupId by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var managing by rememberSaveable(groupId) { mutableStateOf(false) }
    var selected by remember(groupId, managing) { mutableStateOf(emptySet<String>()) }
    var pendingDelete by remember { mutableStateOf<List<OfflineCacheEntry>>(emptyList()) }
    val rootScroll = rememberLazyListState()
    val detailScroll = key(groupId) { rememberLazyListState() }
    val group = library.groups.firstOrNull { it.bvid == groupId }
    // 最后一集被删除后保留详情空态；返回仍回到缓存首页。
    val detail = groupId != null
    val groups = library.groups.filter {
        it.title.contains(query, true) || it.author.contains(query, true) || it.entries.any { entry -> entry.title.contains(query, true) }
    }
    val entries = group?.entries.orEmpty().filter { it.title.contains(query, true) || group?.title.orEmpty().contains(query, true) }
    val visibleEntries = if (detail) entries else groups.flatMap { it.entries }
    val visibleIds = visibleEntries.map { it.id }.toSet()
    val selectedEntries = visibleEntries.filter { it.id in selected }
    val accent = LocalAccentColor.current
    val colors = MaterialTheme.colorScheme

    fun back() {
        when {
            pendingDelete.isNotEmpty() -> pendingDelete = emptyList()

            managing -> managing = false

            searching -> {
                searching = false
                query = ""
            }

            detail -> onBackward {
                groupId = null
                query = ""
            }

            else -> onBack()
        }
    }
    fun play(entry: OfflineCacheEntry) {
        val source = library.groups.firstOrNull { it.bvid == entry.bvid }?.entries.orEmpty()
        val playlist = source.filter { it.audioOnly == entry.audioOnly }.map { it.media }
        onPlay(entry.media, playlist)
    }
    fun toggle(ids: Set<String>) {
        selected = if (selected.containsAll(ids)) selected - ids else selected + ids
    }
    // 使用 lambda 更新状态捕获，避免局部函数引用相等导致系统返回保留旧层级。
    BackHandler { back() }
    Column(Modifier.fillMaxSize().background(colors.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OfflineIconButton(AppIcon.BACK, "返回", { back() })
            Spacer(Modifier.weight(1f))
            if (!managing) {
                OfflineIconButton(AppIcon.SEARCH, "搜索缓存", {
                    searching = !searching
                    if (!searching) query = ""
                })
            }
            TextButton(onClick = { managing = !managing }, enabled = !deleting) { Text(if (managing) "完成" else "管理", color = accent) }
        }
        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索视频、分集或 UP 主") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { AppGlyphIcon(AppIcon.SEARCH, colors.onSurfaceVariant) },
                trailingIcon = { if (query.isNotEmpty()) OfflineIconButton(AppIcon.CLOSE, "清空搜索", { query = "" }) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp),
            )
        }
        if (managing) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { toggle(visibleIds) }, enabled = visibleIds.isNotEmpty() && !deleting) {
                    Text(if (visibleIds.isNotEmpty() && selected.containsAll(visibleIds)) "取消全选" else "全选", color = accent)
                }
            }
        }
        if (error != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(error, Modifier.weight(1f), color = colors.error, fontSize = 13.sp)
                TextButton(onClick = onRetry, enabled = !deleting) { Text("重试") }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = accent)
            } else if (visibleEntries.isEmpty()) {
                Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AppGlyphIcon(AppIcon.DOWNLOAD, accent.copy(alpha = .65f), Modifier.size(48.dp))
                    Spacer(Modifier.height(18.dp))
                    Text(if (query.isNotBlank()) "没有找到相关缓存" else "暂无离线缓存", color = colors.onBackground, fontSize = 17.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(if (query.isNotBlank()) "换个关键词试试" else "在视频播放页下载后，就能在这里离线观看", color = colors.onSurfaceVariant, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    state = if (detail) detailScroll else rootScroll,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (detail) {
                        items(entries, key = { it.id }) { entry ->
                            OfflineEpisodeRow(
                                entry = entry,
                                author = group?.author.orEmpty(),
                                playback = playback[entry.id],
                                managing = managing,
                                selected = entry.id in selected,
                                enabled = !deleting,
                                onClick = { if (managing) toggle(setOf(entry.id)) else play(entry) },
                                onDelete = { pendingDelete = listOf(entry) },
                            )
                        }
                    } else {
                        items(groups, key = { it.bvid }) { item ->
                            OfflineGroupRow(
                                group = item,
                                playback = playback[item.entries.first().id],
                                managing = managing,
                                selected = selected.containsAll(item.entries.map { it.id }),
                                enabled = !deleting,
                                onClick = {
                                    when {
                                        managing -> toggle(item.entries.map { it.id }.toSet())

                                        item.isCollection -> onForward {
                                            groupId = item.bvid
                                            searching = false
                                            query = ""
                                        }

                                        else -> play(item.entries.first())
                                    }
                                },
                                onDelete = { pendingDelete = item.entries },
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = .45f))
        if (managing) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("已选 ${selectedEntries.size} 项", Modifier.weight(1f), color = colors.onSurfaceVariant, fontSize = 13.sp)
                Button(onClick = { pendingDelete = selectedEntries }, enabled = selectedEntries.isNotEmpty() && !deleting) {
                    Text(if (deleting) "正在删除…" else "删除缓存")
                }
            }
        } else {
            Text(
                "已使用 ${formatBytes(library.sizeBytes)}  /  剩余 ${formatBytes(library.freeBytes)}",
                Modifier.align(Alignment.CenterHorizontally).padding(18.dp),
                color = colors.onSurfaceVariant,
                fontSize = 12.sp,
            )
        }
    }
    if (pendingDelete.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingDelete = emptyList() },
            title = { Text("删除离线缓存？") },
            text = { Text("将删除 ${pendingDelete.size} 个缓存文件，释放 ${formatBytes(pendingDelete.sumOf { it.media.sizeBytes })}。删除后需要重新下载。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(pendingDelete)
                    pendingDelete = emptyList()
                    selected = emptySet()
                }) { Text("删除", color = colors.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = emptyList() }) { Text("取消") } },
        )
    }
}

@Composable
internal fun OfflineIconButton(icon: AppIcon, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        AppGlyphIcon(icon, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
