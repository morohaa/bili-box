package io.github.bilibox.feature.library

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.AppTopBar
import io.github.bilibox.core.ui.theme.LocalAccentColor
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.data.localvideo.LocalVideoLayout
import io.github.bilibox.data.localvideo.LocalVideoPlayback
import io.github.bilibox.data.localvideo.LocalVideoPlaybackState
import io.github.bilibox.data.localvideo.LocalVideoQuickFilter
import io.github.bilibox.data.localvideo.LocalVideoScanResult
import io.github.bilibox.data.localvideo.LocalVideoScanState
import io.github.bilibox.data.localvideo.LocalVideoSettings
import io.github.bilibox.data.localvideo.LocalVideoSettingsStore
import io.github.bilibox.data.localvideo.PlaybackProgressStore
import io.github.bilibox.data.localvideo.buildLocalVideoLibrary
import io.github.bilibox.data.localvideo.localVideoIsVisible
import io.github.bilibox.data.localvideo.localVideoMatchesQuickFilter
import io.github.bilibox.data.localvideo.queryLocalVideoLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal val LibraryBg = Color(0xFFF4F6FA)

/** 选中态浅底，跟随主题强调色。 */
internal val LibrarySoftPink: Color
    @Composable @ReadOnlyComposable
    get() = LocalAccentColor.current.copy(alpha = .10f)
internal val LibrarySecondary = Color(0xFF61666D)
internal val LibraryLine = Color(0xFFE9EDF2)
internal val LibraryCard = Color(0xFFFFFFFF)

@Composable
internal fun LibraryScreen(
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
            if (hasLocalVideoPermission(context)) {
                LocalVideoScanState.Loading
            } else {
                LocalVideoScanState.NeedsPermission
            }
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
