package io.github.bilibox.feature.library

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.text.format.Formatter
import android.util.LruCache
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.AppTopBar
import io.github.bilibox.core.ui.theme.Ink
import io.github.bilibox.core.ui.theme.Muted
import io.github.bilibox.core.ui.theme.Pink
import io.github.bilibox.core.util.formatVideoDuration
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.data.localvideo.LocalVideoFolder
import io.github.bilibox.data.localvideo.LocalVideoLayout
import io.github.bilibox.data.localvideo.LocalVideoLibraryView
import io.github.bilibox.data.localvideo.LocalVideoPlayback
import io.github.bilibox.data.localvideo.LocalVideoPlaybackState
import io.github.bilibox.data.localvideo.LocalVideoQuickFilter
import io.github.bilibox.data.localvideo.LocalVideoScanResult
import io.github.bilibox.data.localvideo.LocalVideoScanState
import io.github.bilibox.data.localvideo.LocalVideoSettings
import io.github.bilibox.data.localvideo.LocalVideoSettingsStore
import io.github.bilibox.data.localvideo.LocalVideoSort
import io.github.bilibox.data.localvideo.LocalVideoViewMode
import io.github.bilibox.data.localvideo.PlaybackProgressStore
import io.github.bilibox.data.localvideo.buildLocalVideoLibrary
import io.github.bilibox.data.localvideo.localVideoFolderLabel
import io.github.bilibox.data.localvideo.localVideoIsVisible
import io.github.bilibox.data.localvideo.localVideoMatchesQuickFilter
import io.github.bilibox.data.localvideo.queryLocalVideoLibrary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val LibraryBg = Color(0xFFF4F6FA)
internal val LibrarySoftPink = Color(0xFFFFF1F5)
internal val LibrarySecondary = Color(0xFF61666D)
private val LibraryLine = Color(0xFFE9EDF2)
private val LibraryCard = Color(0xFFFFFFFF)

@Composable
internal fun LibraryPage(
    refreshToken: Int = 0,
    darkMode: Boolean = false,
    initialFilter: LocalVideoQuickFilter? = null,
    onToggleDarkMode: () -> Unit = {},
    onVideoSelected: (LocalVideo, List<LocalVideo>) -> Unit,
) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    val settingsStore = remember(context) { LocalVideoSettingsStore(context) }
    val progressStore = remember(context) { PlaybackProgressStore(context) }

    var settings by remember { mutableStateOf(settingsStore.load()) }
    var draftSettings by remember { mutableStateOf<LocalVideoSettings?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var scanState by remember {
        mutableStateOf<LocalVideoScanState>(
            if (hasLocalVideoPermission(context)) LocalVideoScanState.Loading
            else LocalVideoScanState.NeedsPermission
        )
    }
    var scanRequest by remember { mutableIntStateOf(0) }
    var nav by remember { mutableStateOf<List<String>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    // 从“我的”页的离线缓存 / 历史记录进入时，直接落到对应筛选。
    var quickFilter by remember { mutableStateOf<LocalVideoQuickFilter?>(initialFilter) }
    var infoVideo by remember { mutableStateOf<LocalVideo?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }
    var lastToastMessage by remember { mutableStateOf("") }

    LaunchedEffect(scanRequest) {
        if (!hasLocalVideoPermission(context)) {
            scanState = LocalVideoScanState.NeedsPermission
            return@LaunchedEffect
        }
        scanState = LocalVideoScanState.Loading
        scanState = try {
            val result = withContext<LocalVideoScanResult>(Dispatchers.IO) { queryLocalVideoLibrary(context) }
            LocalVideoScanState.Ready(result.videos, result.nomediaFolders)
        } catch (error: Exception) {
            LocalVideoScanState.Error(error.message ?: "扫描本地视频失败")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) scanRequest++ else scanState = LocalVideoScanState.NeedsPermission
    }

    val ready = scanState as? LocalVideoScanState.Ready
    val videos = ready?.videos ?: emptyList()
    val nomediaFolders = ready?.nomediaFolders ?: emptySet()
    val playback = remember(videos, refreshToken) {
        videos.associate { video ->
            video.key to progressStore.playback(video.key, video.durationMs)
        }
    }

    val effectiveSettings = draftSettings ?: settings
    val effectiveNav = if (draftSettings != null && draftSettings?.mode != settings.mode) emptyList() else nav
    val view = remember(
        videos,
        playback,
        effectiveSettings,
        effectiveNav,
        query,
        quickFilter,
        nomediaFolders,
    ) {
        buildLocalVideoLibrary(
            videos = videos,
            playback = playback,
            settings = effectiveSettings,
            nav = effectiveNav,
            query = query,
            quickFilter = quickFilter,
            nomediaFolders = nomediaFolders,
        )
    }

    val visibleVideos = remember(videos, effectiveSettings, nomediaFolders) {
        videos.filter { localVideoIsVisible(it, effectiveSettings, nomediaFolders) }
    }
    val resume = remember(videos, playback) {
        videos.asSequence()
            .mapNotNull { video ->
                val state = playback[video.key]?.state as? LocalVideoPlaybackState.Watching
                    ?: return@mapNotNull null
                val playedAt = playback[video.key]?.lastPlayedAtMs ?: 0L
                ResumeTarget(video, state, playedAt)
            }
            .maxByOrNull { it.playedAtMs }
    }

    BackHandler(enabled = searchOpen || query.isNotEmpty() || quickFilter != null || nav.isNotEmpty()) {
        when {
            searchOpen -> {
                searchOpen = false
                query = ""
            }
            quickFilter != null -> quickFilter = null
            nav.isNotEmpty() -> nav = nav.dropLast(1)
        }
    }

    LaunchedEffect(toastMessage) {
        val shown = toastMessage ?: return@LaunchedEffect
        lastToastMessage = shown
        delay(2200)
        if (toastMessage == shown) toastMessage = null
    }

    fun toast(message: String) {
        toastMessage = message
    }

    fun selectFilter(filter: LocalVideoQuickFilter) {
        if (quickFilter == filter) {
            quickFilter = null
            return
        }
        val candidates = if (filter == LocalVideoQuickFilter.PRIVATE) videos else visibleVideos
        val count = candidates.count { localVideoMatchesQuickFilter(it, playback, filter) }
        if (count == 0) {
            toast(emptyFilterMessage(filter))
            return
        }
        quickFilter = filter
        searchOpen = false
        query = ""
        nav = emptyList()
    }

    fun play(video: LocalVideo) {
        onVideoSelected(video, view.playlist.ifEmpty { listOf(video) })
    }

    fun share(video: LocalVideo) {
        runCatching {
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = video.mimeType.ifBlank { "video/*" }
                        putExtra(Intent.EXTRA_STREAM, video.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "分享视频"
                )
            )
        }.onFailure { toast("无法分享这个视频") }
    }

    fun applyDraft(next: LocalVideoSettings) {
        if (next.mode != settings.mode) {
            nav = emptyList()
            quickFilter = null
        }
        settings = next
        settingsStore.save(next)
        draftSettings = null
        settingsOpen = false
    }

    val pageBackground = if (darkMode) Color(0xFF17191D) else LibraryBg
    Box(Modifier.fillMaxSize().background(pageBackground)) {
        Column(Modifier.fillMaxSize()) {
            // 视频页顶栏直接承载本地视频标题和操作按钮；品牌顶栏仅保留给其他页面。
            AppTopBar(darkMode = darkMode, onToggleDarkMode = onToggleDarkMode) {
                LibraryHeader(
                    searchOpen = searchOpen,
                    query = query,
                    onSearchOpen = { searchOpen = true },
                    onSearchClose = {
                        searchOpen = false
                        query = ""
                        keyboard?.hide()
                    },
                    onQueryChange = { value ->
                        query = value
                        if (value.isNotBlank()) quickFilter = null
                    },
                    onSettings = {
                        draftSettings = settings
                        settingsOpen = true
                    },
                    focusSearcher = searchOpen,
                )
            }

            if (scanState is LocalVideoScanState.Ready) {
                QuickFilterRow(
                    selected = quickFilter,
                    onSelect = ::selectFilter,
                )
                SortBar(
                    view = view,
                    settings = effectiveSettings,
                    nav = effectiveNav,
                    query = query,
                    quickFilter = quickFilter,
                    onNavigateTo = { index -> nav = nav.take(index + 1) },
                    onRoot = { nav = emptyList() },
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (val state = scanState) {
                    LocalVideoScanState.NeedsPermission -> PermissionState(
                        onRequest = { permissionLauncher.launch(localVideoPermissions()) },
                        onRecheck = { scanRequest++ },
                    )
                    LocalVideoScanState.Loading -> LoadingState()
                    is LocalVideoScanState.Error -> ErrorState(
                        message = state.message,
                        onRetry = { scanRequest++ },
                    )
                    is LocalVideoScanState.Ready -> {
                        if (view.folders.isEmpty() && view.videos.isEmpty()) {
                            EmptyState(
                                title = emptyTitle(query, quickFilter, nav),
                                message = emptyMessage(query, quickFilter, nav, settings),
                                action = when {
                                    videos.isNotEmpty() -> null
                                    hasLocalVideoPermission(context) -> ({ scanRequest++ })
                                    else -> ({ permissionLauncher.launch(localVideoPermissions()) })
                                },
                                actionLabel = when {
                                    videos.isNotEmpty() -> null
                                    hasLocalVideoPermission(context) -> "重新扫描"
                                    else -> "允许访问并扫描"
                                },
                            )
                        } else {
                            val bottom = if (resume != null) 206.dp else 150.dp
                            if (effectiveSettings.layout == LocalVideoLayout.GRID) {
                                VideoGrid(
                                    view = view,
                                    settings = effectiveSettings,
                                    playback = playback,
                                    bottomPadding = bottom,
                                    onFolder = { nav = nav + it.path },
                                    onVideo = ::play,
                                    onShare = ::share,
                                    onInfo = { infoVideo = it },
                                )
                            } else {
                                VideoList(
                                    view = view,
                                    settings = effectiveSettings,
                                    playback = playback,
                                    bottomPadding = bottom,
                                    onFolder = { nav = nav + it.path },
                                    onVideo = ::play,
                                    onShare = ::share,
                                    onInfo = { infoVideo = it },
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = toastMessage != null,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding()
                .padding(start = 38.dp, end = 38.dp, bottom = if (resume != null) 174.dp else 118.dp),
        ) {
            Box(
                Modifier.widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xE618191C))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(
                    lastToastMessage,
                    color = Color.White,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (resume != null && !settingsOpen) {
            val target = resume
            ContinueWatchingBar(
                video = target.video,
                playback = target.playback,
                settings = settings,
                onClick = {
                    val playlist = if (view.playlist.any { it.key == target.video.key }) {
                        view.playlist
                    } else {
                        videos.sortedByDescending { it.dateModifiedMs }
                    }
                    onVideoSelected(target.video, playlist)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 14.dp, end = 14.dp, bottom = 96.dp),
            )
        }

        if (settingsOpen) {
            LocalVideoSettingsSheet(
                applied = settings,
                draft = draftSettings ?: settings,
                sample = resume?.video ?: videos.firstOrNull(),
                samplePlayback = (resume?.video ?: videos.firstOrNull())?.let { video ->
                    playback[video.key] ?: LocalVideoPlayback(LocalVideoPlaybackState.Unplayed)
                } ?: LocalVideoPlayback(LocalVideoPlaybackState.Unplayed),
                onDraftChange = { draftSettings = it },
                onReset = { draftSettings = LocalVideoSettings() },
                onDismiss = {
                    settingsOpen = false
                    draftSettings = null
                },
                onApply = { applyDraft(draftSettings ?: settings) },
                onRescan = {
                    val next = draftSettings ?: settings
                    applyDraft(next)
                    scanRequest++
                },
            )
        }

        infoVideo?.let { video ->
            VideoInfoDialog(
                video = video,
                playback = playback[video.key] ?: LocalVideoPlayback(LocalVideoPlaybackState.Unplayed),
                onDismiss = { infoVideo = null },
            )
        }
    }
}

private data class ResumeTarget(
    val video: LocalVideo,
    val playback: LocalVideoPlaybackState.Watching,
    val playedAtMs: Long,
)

@Composable
private fun LibraryHeader(
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
private fun HeaderIconButton(icon: LocalIcon, description: String, onClick: () -> Unit) {
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
private fun QuickFilterRow(
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

private fun quickFilterIcon(filter: LocalVideoQuickFilter): LocalIcon = when (filter) {
    LocalVideoQuickFilter.RECENT -> LocalIcon.CLOCK
    LocalVideoQuickFilter.BILI_CACHE -> LocalIcon.DOWNLOAD
    LocalVideoQuickFilter.PRIVATE -> LocalIcon.LOCK
    LocalVideoQuickFilter.UNFINISHED -> LocalIcon.STATUS
    LocalVideoQuickFilter.FINISHED -> LocalIcon.CHECK
}

@Composable
private fun SortBar(
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
private fun CrumbCount(text: String) {
    Text(text, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun CrumbLink(text: String, onClick: () -> Unit) {
    Text(
        text,
        color = Pink,
        fontSize = 11.5.sp,
        maxLines = 1,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 2.dp),
    )
}

@Composable
private fun VideoList(
    view: LocalVideoLibraryView,
    settings: LocalVideoSettings,
    playback: Map<String, LocalVideoPlayback>,
    bottomPadding: Dp,
    onFolder: (LocalVideoFolder) -> Unit,
    onVideo: (LocalVideo) -> Unit,
    onShare: (LocalVideo) -> Unit,
    onInfo: (LocalVideo) -> Unit,
) {
    val state = rememberLazyListState()
    ResetScroll(state, settings.layout, settings.sort, settings.ascending, view.currentPath, view.searching, view.activeFilter, view.videos.size)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(view.folders, key = { "folder:${it.path}" }) { folder ->
            FolderRow(folder, settings, onClick = { onFolder(folder) })
        }
        items(view.videos, key = { "video:${it.key}" }) { video ->
            VideoRow(
                video = video,
                settings = settings,
                playback = playback[video.key] ?: LocalVideoPlayback(LocalVideoPlaybackState.Unplayed),
                onClick = { onVideo(video) },
                onShare = { onShare(video) },
                onInfo = { onInfo(video) },
            )
        }
    }
}

@Composable
private fun VideoGrid(
    view: LocalVideoLibraryView,
    settings: LocalVideoSettings,
    playback: Map<String, LocalVideoPlayback>,
    bottomPadding: Dp,
    onFolder: (LocalVideoFolder) -> Unit,
    onVideo: (LocalVideo) -> Unit,
    onShare: (LocalVideo) -> Unit,
    onInfo: (LocalVideo) -> Unit,
) {
    val state = rememberLazyGridState()
    ResetScroll(state, settings.layout, settings.sort, settings.ascending, view.currentPath, view.searching, view.activeFilter, view.videos.size)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 2.dp, bottom = bottomPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(view.folders, key = { "folder:${it.path}" }) { folder ->
            FolderCard(folder, settings, onClick = { onFolder(folder) })
        }
        items(view.videos, key = { "video:${it.key}" }) { video ->
            VideoCard(
                video = video,
                settings = settings,
                playback = playback[video.key] ?: LocalVideoPlayback(LocalVideoPlaybackState.Unplayed),
                onClick = { onVideo(video) },
                onShare = { onShare(video) },
                onInfo = { onInfo(video) },
            )
        }
    }
}

@Composable
private fun ResetScroll(
    state: LazyListState,
    layout: LocalVideoLayout,
    sort: LocalVideoSort,
    ascending: Boolean,
    path: String?,
    searching: Boolean,
    filter: LocalVideoQuickFilter?,
    count: Int,
) {
    LaunchedEffect(layout, sort, ascending, path, searching, filter, count) {
        if (layout == LocalVideoLayout.LIST) runCatching { state.scrollToItem(0) }
    }
}

@Composable
private fun ResetScroll(
    state: LazyGridState,
    layout: LocalVideoLayout,
    sort: LocalVideoSort,
    ascending: Boolean,
    path: String?,
    searching: Boolean,
    filter: LocalVideoQuickFilter?,
    count: Int,
) {
    LaunchedEffect(layout, sort, ascending, path, searching, filter, count) {
        if (layout == LocalVideoLayout.GRID) runCatching { state.scrollToItem(0) }
    }
}

@Composable
private fun FolderRow(
    folder: LocalVideoFolder,
    settings: LocalVideoSettings,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FolderIcon(folder)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                folder.name,
                color = Ink,
                fontSize = 14.5.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            MetaText(folderMetaParts(folder, settings))
            if (settings.fields.path) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "内部存储 / ${folder.path}",
                    color = Muted,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        LocalGlyphIcon(LocalIcon.CHEVRON_RIGHT, Muted.copy(alpha = .62f), Modifier.size(17.dp), 1.8f)
    }
}

@Composable
private fun FolderIcon(folder: LocalVideoFolder) {
    // Leave room above the tile so the unread badge can sit fully outside it.
    Box(Modifier.size(54.dp)) {
        Box(
            Modifier.size(46.dp).align(Alignment.BottomStart)
                .clip(RoundedCornerShape(14.dp))
                .background(LibrarySoftPink),
            contentAlignment = Alignment.Center,
        ) {
            LocalGlyphIcon(LocalIcon.FOLDER, Pink, Modifier.size(24.dp), 1.7f)
        }
        if (folder.unplayedCount > 0) {
            Box(
                Modifier.align(Alignment.TopEnd)
                    .offset(x = (-3).dp, y = 5.dp)
                    .widthIn(min = 18.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(LibraryBg)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Pink)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${folder.unplayedCount}",
                    color = Color.White,
                    fontSize = 9.5.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun VideoRow(
    video: LocalVideo,
    settings: LocalVideoSettings,
    playback: LocalVideoPlayback,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onInfo: () -> Unit,
    compact: Boolean = false,
) {
    val watched = playback.state == LocalVideoPlaybackState.Watched
    val thumbWidth = if (compact) 96.dp else 122.dp
    val thumbHeight = if (compact) 54.dp else 69.dp
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(if (compact) 10.dp else 14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 6.dp else 0.dp, vertical = if (compact) 4.dp else 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (settings.fields.thumbnail) {
            VideoThumbnail(
                video = video,
                settings = settings,
                playback = playback,
                modifier = Modifier.width(thumbWidth).height(thumbHeight),
            )
        } else {
            Box(
                Modifier.size(if (compact) 38.dp else 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(LibrarySoftPink),
                contentAlignment = Alignment.Center,
            ) {
                LocalGlyphIcon(LocalIcon.FILE, Pink, Modifier.size(if (compact) 20.dp else 23.dp), 1.7f)
            }
        }
        Spacer(Modifier.width(if (compact) 9.dp else 11.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    video.title,
                    color = if (watched) Muted else Ink,
                    fontSize = if (compact) 13.sp else 14.5.sp,
                    lineHeight = if (compact) 17.sp else 19.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (settings.fields.extension) ExtensionBadge(video.extension)
            }
            val meta = videoMetaParts(video, playback, settings)
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                MetaText(meta, maxLines = if (compact) 1 else 2)
            }
            if (settings.fields.path) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "内部存储 / ${video.relativeDirectory.ifBlank { "根目录" }} / ${video.name}",
                    color = Muted,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!compact) {
            Spacer(Modifier.width(4.dp))
            VideoMoreMenu(onPlay = onClick, onShare = onShare, onInfo = onInfo)
        }
    }
}

@Composable
private fun VideoMoreMenu(
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onInfo: () -> Unit,
    onThumbnail: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(28.dp)
                .clip(CircleShape)
                .background(if (onThumbnail) Color.Black.copy(alpha = .3f) else Color.Transparent)
                .semantics { contentDescription = "更多操作" }
                .clickable { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            LocalGlyphIcon(LocalIcon.MORE, if (onThumbnail) Color.White else Muted, Modifier.size(16.dp), 1.8f)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LocalGlyphIcon(LocalIcon.PLAY, Pink, Modifier.size(15.dp), 1.8f)
                        Spacer(Modifier.width(9.dp))
                        Text("播放", color = Ink, fontSize = 13.sp)
                    }
                },
                onClick = {
                    expanded = false
                    onPlay()
                },
            )
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LocalGlyphIcon(LocalIcon.SHARE, LibrarySecondary, Modifier.size(15.dp), 1.7f)
                        Spacer(Modifier.width(9.dp))
                        Text("分享", color = Ink, fontSize = 13.sp)
                    }
                },
                onClick = {
                    expanded = false
                    onShare()
                },
            )
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LocalGlyphIcon(LocalIcon.INFO, LibrarySecondary, Modifier.size(15.dp), 1.7f)
                        Spacer(Modifier.width(9.dp))
                        Text("文件信息", color = Ink, fontSize = 13.sp)
                    }
                },
                onClick = {
                    expanded = false
                    onInfo()
                },
            )
        }
    }
}

@Composable
private fun VideoThumbnail(
    video: LocalVideo,
    settings: LocalVideoSettings,
    playback: LocalVideoPlayback,
    modifier: Modifier = Modifier,
    showStatus: Boolean = true,
) {
    val bitmap = rememberVideoThumbnail(video)
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(coverBrush(video.title))) {
        bitmap?.let { frame ->
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Color.White.copy(alpha = .07f),
                radius = size.minDimension * .52f,
                center = Offset(size.width * .78f, size.height * .28f),
            )
            drawCircle(
                Color.Black.copy(alpha = .05f),
                radius = size.minDimension * .46f,
                center = Offset(size.width * .12f, size.height * .9f),
            )
        }
        if (showStatus) {
            when (playback.state) {
                LocalVideoPlaybackState.Unplayed -> ThumbBadge("NEW", Pink, Modifier.align(Alignment.TopStart))
                LocalVideoPlaybackState.Watched -> ThumbBadge("已看完", Color(0xFF4B5560), Modifier.align(Alignment.TopStart))
                is LocalVideoPlaybackState.Watching -> Unit
            }
        }
        if (settings.fields.duration && settings.durationOnThumbnail && video.durationMs > 0L) {
            Text(
                formatVideoDuration(video.durationMs),
                color = Color.White,
                fontSize = 9.5.sp,
                lineHeight = 12.sp,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = if (playback.state is LocalVideoPlaybackState.Watching) 7.dp else 4.dp)
                    .background(Color.Black.copy(alpha = .55f), RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        if (playback.state is LocalVideoPlaybackState.Watching) {
            val fraction = playback.state.fraction
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp)
                    .background(Color.White.copy(alpha = .38f)),
            ) {
                Box(
                    Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .background(Pink),
                )
            }
        }
    }
}

@Composable
private fun ThumbBadge(text: String, background: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        color = Color.White,
        fontSize = 9.sp,
        lineHeight = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(4.dp)
            .background(background, RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
private fun ExtensionBadge(extension: String) {
    if (extension.isBlank()) return
    Text(
        extension.uppercase(),
        color = Muted,
        fontSize = 8.5.sp,
        lineHeight = 12.sp,
        modifier = Modifier.padding(start = 5.dp, top = 2.dp)
            .border(1.dp, LibraryLine, RoundedCornerShape(3.dp))
            .padding(horizontal = 3.dp, vertical = 1.dp),
    )
}

private data class MetaPart(val text: String, val highlight: Boolean = false)

@Composable
private fun MetaText(parts: List<MetaPart>, maxLines: Int = 2) {
    if (parts.isEmpty()) return
    val text = buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            if (index > 0) {
                withStyle(SpanStyle(color = Muted.copy(alpha = .65f))) { append("  ·  ") }
            }
            withStyle(SpanStyle(color = if (part.highlight) Pink else Muted)) { append(part.text) }
        }
    }
    Text(text, fontSize = 10.5.sp, lineHeight = 15.sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun VideoCard(
    video: LocalVideo,
    settings: LocalVideoSettings,
    playback: LocalVideoPlayback,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onInfo: () -> Unit,
) {
    val watched = playback.state == LocalVideoPlaybackState.Watched
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LibraryCard)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
            if (settings.fields.thumbnail) {
                VideoThumbnail(video, settings, playback, Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize().background(coverBrush(video.title)), contentAlignment = Alignment.Center) {
                    LocalGlyphIcon(LocalIcon.FILE, Color.White.copy(alpha = .9f), Modifier.size(28.dp), 1.5f)
                }
            }
            Box(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                VideoMoreMenu(onPlay = onClick, onShare = onShare, onInfo = onInfo, onThumbnail = true)
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 9.dp, end = 9.dp, top = 7.dp, bottom = 9.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    video.title,
                    color = if (watched) Muted else Ink,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).heightIn(min = 34.dp),
                )
                if (settings.fields.extension) ExtensionBadge(video.extension)
            }
            val meta = videoMetaParts(video, playback, settings)
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                MetaText(meta, maxLines = 1)
            }
            if (settings.fields.path) {
                Spacer(Modifier.height(3.dp))
                Text(
                    "内部存储 / ${video.relativeDirectory.ifBlank { "根目录" }}",
                    color = Muted,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FolderCard(
    folder: LocalVideoFolder,
    settings: LocalVideoSettings,
    onClick: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(LibraryCard)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f)) {
            if (settings.fields.thumbnail) {
                FolderCover(folder)
            } else {
                Box(
                    Modifier.fillMaxSize().background(coverBrush(folder.path)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = .82f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        LocalGlyphIcon(LocalIcon.FOLDER, Pink, Modifier.size(26.dp), 1.7f)
                    }
                }
            }
            if (folder.unplayedCount > 0) {
                ThumbBadge("${folder.unplayedCount} 新", Pink, Modifier.align(Alignment.TopStart))
            }
            Row(
                Modifier.align(Alignment.BottomEnd).padding(5.dp)
                    .background(Color.Black.copy(alpha = .52f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LocalGlyphIcon(LocalIcon.FOLDER, Color.White, Modifier.size(11.dp), 2f)
                Spacer(Modifier.width(3.dp))
                Text("${folder.count}", color = Color.White, fontSize = 9.5.sp)
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 9.dp, end = 9.dp, top = 7.dp, bottom = 9.dp)) {
            Text(
                folder.name,
                color = Ink,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            MetaText(folderMetaParts(folder, settings, includeDuration = false), maxLines = 1)
            if (settings.fields.path) {
                Spacer(Modifier.height(3.dp))
                Text(
                    "内部存储 / ${folder.path}",
                    color = Muted,
                    fontSize = 9.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FolderCover(folder: LocalVideoFolder) {
    val cover = folder.cover
    val bitmap = cover?.let { rememberVideoThumbnail(it) }
    Box(Modifier.fillMaxSize().background(coverBrush(folder.path))) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = .32f)),
                        startY = size.height * .4f,
                    )
                )
            }
        } else {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    Color.White.copy(alpha = .1f),
                    radius = size.minDimension * .5f,
                    center = Offset(size.width * .76f, size.height * .3f),
                )
            }
            Box(
                Modifier.align(Alignment.Center).size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = .82f)),
                contentAlignment = Alignment.Center,
            ) {
                LocalGlyphIcon(LocalIcon.FOLDER, Pink, Modifier.size(26.dp), 1.7f)
            }
        }
    }
}

@Composable
private fun ContinueWatchingBar(
    video: LocalVideo,
    playback: LocalVideoPlaybackState.Watching,
    settings: LocalVideoSettings,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(60.dp)
            .shadow(16.dp, RoundedCornerShape(30.dp), ambientColor = Color(0xFF5D7288).copy(alpha = .22f), spotColor = Color(0xFF5D7288).copy(alpha = .22f))
            .clip(RoundedCornerShape(30.dp))
            .background(Color.White.copy(alpha = .94f))
            .border(1.dp, Color.White, RoundedCornerShape(30.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(68.dp).height(48.dp).clip(RoundedCornerShape(24.dp)),
        ) {
            VideoThumbnail(video, settings, LocalVideoPlayback(playback), Modifier.fillMaxSize(), showStatus = false)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                video.title,
                color = Ink,
                fontSize = 12.5.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "继续观看 · ${formatVideoDuration(playback.positionMs)} / ${formatVideoDuration(video.durationMs)}",
                color = Muted,
                fontSize = 10.sp,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(40.dp)
                .shadow(8.dp, CircleShape, spotColor = Pink.copy(alpha = .4f))
                .clip(CircleShape)
                .background(Pink),
            contentAlignment = Alignment.Center,
        ) {
            LocalGlyphIcon(LocalIcon.PLAY, Color.White, Modifier.size(18.dp), 1.8f)
        }
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun PermissionState(onRequest: () -> Unit, onRecheck: () -> Unit) {
    LibraryMessage(
        icon = LocalIcon.FOLDER,
        title = "允许访问本地视频",
        message = "授权后，BiliBox 会读取设备中的视频文件，并按文件夹整理展示。",
        actionLabel = "允许访问并扫描",
        onAction = onRequest,
        secondaryLabel = "已授权？重新检查",
        onSecondary = onRecheck,
    )
}

@Composable
private fun LoadingState() {
    Column(
        Modifier.fillMaxWidth().padding(top = 88.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = Pink, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(14.dp))
        Text("正在扫描本地视频…", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    LibraryMessage(
        icon = LocalIcon.INFO,
        title = "扫描失败",
        message = message,
        actionLabel = "重试",
        onAction = onRetry,
    )
}

@Composable
private fun EmptyState(
    title: String,
    message: String,
    action: (() -> Unit)? = null,
    actionLabel: String? = null,
) {
    LibraryMessage(
        icon = LocalIcon.FOLDER,
        title = title,
        message = message,
        actionLabel = actionLabel,
        onAction = action,
    )
}

@Composable
private fun LibraryMessage(
    icon: LocalIcon,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(76.dp).clip(RoundedCornerShape(24.dp)).background(LibrarySoftPink),
            contentAlignment = Alignment.Center,
        ) {
            LocalGlyphIcon(icon, Pink, Modifier.size(34.dp), 1.5f)
        }
        Spacer(Modifier.height(18.dp))
        Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Muted, fontSize = 12.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.height(42.dp).clip(RoundedCornerShape(21.dp)).background(Pink)
                    .clickable(onClick = onAction).padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(actionLabel, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            }
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onSecondary) {
                Text(secondaryLabel, color = Pink, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun VideoInfoDialog(
    video: LocalVideo,
    playback: LocalVideoPlayback,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val formatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭", color = Pink) }
        },
        title = { Text("文件信息", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                InfoLine("文件名", video.name)
                InfoLine("路径", "内部存储 / ${video.relativeDirectory.ifBlank { "根目录" }} / ${video.name}")
                InfoLine("大小", Formatter.formatFileSize(context, video.sizeBytes))
                InfoLine(
                    "时长",
                    video.durationMs.takeIf { it > 0L }?.let(::formatVideoDuration) ?: "未知",
                )
                InfoLine(
                    "分辨率",
                    if (video.width > 0 && video.height > 0) "${video.width} × ${video.height} · ${resolutionLabel(video.width, video.height)}" else "未知",
                )
                InfoLine("帧率", if (video.frameRate > 0f) frameRateLabel(video.frameRate) else "未知")
                InfoLine("修改时间", formatFullDate(video.dateModifiedMs, formatter))
                InfoLine("类型", video.mimeType.ifBlank { video.extension.uppercase().ifBlank { "未知" } })
                InfoLine(
                    "播放状态",
                    when (val state = playback.state) {
                        LocalVideoPlaybackState.Unplayed -> "未播放"
                        is LocalVideoPlaybackState.Watching -> "看到 ${formatVideoDuration(state.positionMs)}"
                        LocalVideoPlaybackState.Watched -> "已看完"
                    },
                )
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, color = Muted, fontSize = 10.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = Ink, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

private fun emptyFilterMessage(filter: LocalVideoQuickFilter): String = when (filter) {
    LocalVideoQuickFilter.RECENT -> "最近 7 天没有播放记录"
    LocalVideoQuickFilter.BILI_CACHE -> "未发现 B 站缓存视频。系统通常不开放其他应用的私有缓存目录。"
    LocalVideoQuickFilter.PRIVATE -> "没有找到隐藏目录中的视频"
    LocalVideoQuickFilter.UNFINISHED -> "没有正在观看的视频"
    LocalVideoQuickFilter.FINISHED -> "还没有看完的视频"
}

private fun emptyTitle(
    query: String,
    filter: LocalVideoQuickFilter?,
    nav: List<String>,
): String = when {
    query.isNotBlank() -> "没有找到相关视频"
    filter != null -> "没有符合条件的视频"
    nav.isNotEmpty() -> "这个文件夹里没有视频"
    else -> "还没有本地视频"
}

private fun emptyMessage(
    query: String,
    filter: LocalVideoQuickFilter?,
    nav: List<String>,
    settings: LocalVideoSettings,
): String = when {
    query.isNotBlank() -> "换一个关键词试试，或清空搜索查看全部视频。"
    filter != null -> emptyFilterMessage(filter)
    nav.isNotEmpty() -> "换个文件夹看看，或者在设置里切换视图模式。"
    settings.showHidden -> "把视频保存到手机后，回到这一页会自动出现。"
    else -> "把视频保存到手机后，回到这一页会自动出现。也可以在设置中打开隐藏文件显示。"
}

private fun videoMetaParts(
    video: LocalVideo,
    playback: LocalVideoPlayback,
    settings: LocalVideoSettings,
): List<MetaPart> {
    val fields = settings.fields
    val parts = mutableListOf<MetaPart>()
    val durationOnThumb = fields.thumbnail && settings.durationOnThumbnail
    val state = playback.state
    if (state == LocalVideoPlaybackState.Unplayed && !fields.thumbnail) {
        parts += MetaPart("NEW", highlight = true)
    }
    if (fields.duration && !durationOnThumb && video.durationMs > 0L) {
        parts += MetaPart(formatVideoDuration(video.durationMs))
    }
    if (fields.played) {
        when (state) {
            is LocalVideoPlaybackState.Watching -> parts += MetaPart("看到 ${formatVideoDuration(state.positionMs)}", highlight = true)
            LocalVideoPlaybackState.Watched -> parts += MetaPart(
                if (playback.lastPlayedAtMs > 0L) "看过 ${formatRelativeDate(playback.lastPlayedAtMs)}" else "已看完"
            )
            LocalVideoPlaybackState.Unplayed -> Unit
        }
    }
    if (fields.resolution && video.width > 0 && video.height > 0) {
        parts += MetaPart(resolutionLabel(video.width, video.height))
    }
    if (fields.frameRate && video.frameRate > 0f) {
        parts += MetaPart(frameRateLabel(video.frameRate))
    }
    if (fields.size) {
        parts += MetaPart(formatBytes(video.sizeBytes))
    }
    if (fields.date) {
        parts += MetaPart(formatRelativeDate(video.dateModifiedMs))
    }
    return parts
}

private fun folderMetaParts(
    folder: LocalVideoFolder,
    settings: LocalVideoSettings,
    includeDuration: Boolean = true,
): List<MetaPart> {
    val parts = mutableListOf(MetaPart("${folder.count} 个视频"))
    if (settings.fields.size) parts += MetaPart(formatBytes(folder.totalSizeBytes))
    if (settings.fields.date) parts += MetaPart(formatRelativeDate(folder.newestDateMs))
    if (includeDuration && settings.fields.duration && folder.totalDurationMs > 0L) {
        parts += MetaPart("共 ${formatVideoDuration(folder.totalDurationMs)}")
    }
    if (settings.fields.resolution && folder.maxWidth > 0 && folder.maxHeight > 0) {
        parts += MetaPart(resolutionLabel(folder.maxWidth, folder.maxHeight))
    }
    return parts
}

internal fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val gb = bytes / 1_000_000_000.0
    if (gb >= 1.0) return "%.2f GB".format(gb)
    val mb = bytes / 1_000_000.0
    if (mb >= 1.0) return "%.0f MB".format(mb)
    return "%.0f KB".format(bytes / 1_000.0)
}

internal fun resolutionLabel(width: Int, height: Int): String {
    val shortSide = minOf(width, height)
    return when {
        shortSide >= 2160 -> "4K"
        shortSide >= 1440 -> "2K"
        shortSide >= 1080 -> "1080P"
        shortSide >= 720 -> "720P"
        shortSide >= 480 -> "480P"
        else -> "${shortSide}P"
    }
}

internal fun frameRateLabel(frameRate: Float): String {
    val rounded = frameRate.roundToInt()
    val text = if (kotlin.math.abs(frameRate - rounded) < .05f) "$rounded" else {
        "%.2f".format(frameRate).trimEnd('0').trimEnd('.')
    }
    return "$text fps"
}

internal fun formatRelativeDate(milliseconds: Long): String {
    if (milliseconds <= 0L) return "未知"
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(milliseconds).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    val days = ChronoUnit.DAYS.between(date, today)
    return when {
        days <= 0L -> "今天"
        days == 1L -> "昨天"
        days < 7L -> "$days 天前"
        date.year == today.year -> "${date.monthValue}月${date.dayOfMonth}日"
        else -> "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
    }
}

private fun formatFullDate(milliseconds: Long, formatter: DateTimeFormatter): String =
    if (milliseconds <= 0L) "未知"
    else Instant.ofEpochMilli(milliseconds).atZone(ZoneId.systemDefault()).format(formatter)

internal fun coverBrush(seed: String): Brush {
    val hash = seed.hashCode()
    val hue = ((hash ushr 8) % 360 + 360) % 360
    val secondHue = (hue + 42 + ((hash ushr 3) % 70)) % 360
    return Brush.linearGradient(
        listOf(
            Color.hsl(hue.toFloat(), .46f, .44f),
            Color.hsl(secondHue.toFloat(), .52f, .62f),
        )
    )
}

private val thumbnailCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
}

@Composable
internal fun rememberVideoThumbnail(video: LocalVideo): Bitmap? {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = thumbnailCache.get(video.key), video.key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { loadVideoThumbnail(context, video.uri, video.key) }
        }
    }
    return bitmap
}

internal fun loadVideoThumbnail(context: Context, uri: Uri, key: String): Bitmap? {
    thumbnailCache.get(key)?.let { return it }
    val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        runCatching { context.contentResolver.loadThumbnail(uri, Size(480, 270), null) }.getOrNull()
    } else {
        null
    } ?: run {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.getScaledFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 480, 270)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
    if (frame != null) thumbnailCache.put(key, frame)
    return frame
}

private fun hasLocalVideoPermission(context: Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_VIDEO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) return true
    return Build.VERSION.SDK_INT >= 34 && context.checkSelfPermission(
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    ) == PackageManager.PERMISSION_GRANTED
}

private fun localVideoPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}
