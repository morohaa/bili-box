package io.github.bilibox.feature.online

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.github.bilibox.core.model.PlaybackState
import io.github.bilibox.core.model.SearchVideo
import io.github.bilibox.core.ui.theme.OnlinePink
import io.github.bilibox.core.ui.theme.OnlineSurface
import io.github.bilibox.data.auth.BiliSession
import io.github.bilibox.data.auth.hasLoginCookies
import io.github.bilibox.data.bili.BiliClient
import io.github.bilibox.data.bili.BiliPlayback
import io.github.bilibox.data.bili.BiliQn
import io.github.bilibox.data.bili.Dolby
import io.github.bilibox.data.bili.Page
import io.github.bilibox.data.bili.PlayData
import io.github.bilibox.data.bili.UserCardData
import io.github.bilibox.data.bili.VideoInfo
import io.github.bilibox.data.danmaku.Danmaku
import io.github.bilibox.data.danmaku.DanmakuParser
import io.github.bilibox.data.danmaku.DanmakuPreferences
import io.github.bilibox.data.danmaku.DanmakuSettings
import io.github.bilibox.feature.listen.ListenInfo
import io.github.bilibox.feature.listen.ListenPlaybackBridge
import io.github.bilibox.feature.listen.ListenQueue
import io.github.bilibox.feature.player.PlayerQualityOption
import io.github.bilibox.feature.player.VideoPlayerScreen
import io.github.bilibox.feature.player.formatPlaybackTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun BiliVideoDetail(video: SearchVideo, session: BiliSession?, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val client = remember(video.bvid, session?.cookie) {
        BiliClient().also { it.cookieHeader = session?.cookie }
    }
    val exoPlayer = remember(video.bvid, session?.cookie) {
        val dataSourceFactory = DefaultDataSource.Factory(context, BiliPlayback.dataSourceFactory(session?.cookie, video.bvid))
        val renderersFactory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
    }
    var playbackState by remember(video.bvid, session?.cookie) { mutableStateOf<PlaybackState>(PlaybackState.Loading) }
    var playbackData by remember(video.bvid, session?.cookie) { mutableStateOf<PlayData?>(null) }
    var playbackCid by remember(video.bvid, session?.cookie) { mutableLongStateOf(0L) }
    var playbackCandidateIndex by remember(video.bvid, session?.cookie) { mutableIntStateOf(0) }
    var playbackCandidateCount by remember(video.bvid, session?.cookie) { mutableIntStateOf(1) }
    // Candidate lists depend on the qn they were built with; host rotation must reuse it.
    var playbackCandidateQn by remember(video.bvid, session?.cookie) { mutableIntStateOf(BiliQn.P1080) }
    var playbackRefreshTried by remember(video.bvid, session?.cookie) { mutableStateOf(false) }
    var dashFallbackTried by remember(video.bvid, session?.cookie) { mutableStateOf(false) }
    var durlFallbackTried by remember(video.bvid, session?.cookie) { mutableStateOf(false) }
    var html5FallbackTried by remember(video.bvid, session?.cookie) { mutableStateOf(false) }
    var videoInfo by remember(video.bvid, session?.cookie) { mutableStateOf<VideoInfo?>(null) }
    var selectedPage by rememberSaveable(video.bvid, session?.cookie) { mutableIntStateOf(1) }
    var selectedQn by rememberSaveable(video.bvid, session?.cookie) { mutableIntStateOf(BiliQn.P1080) }
    // The quality the server actually started, which can be lower than the requested one.
    var actualQn by rememberSaveable(video.bvid, session?.cookie) { mutableIntStateOf(0) }
    // Selecting the same quality again must still be able to retry a clamped stream.
    var qualityRequestToken by remember(video.bvid, session?.cookie) { mutableIntStateOf(0) }
    var qualityResumeMs by remember(video.bvid, session?.cookie) { mutableLongStateOf(0L) }
    var qualityResumePlaying by remember(video.bvid, session?.cookie) { mutableStateOf(true) }
    var episodesSheetVisible by rememberSaveable(video.bvid, session?.cookie) { mutableStateOf(false) }
    var autoPlayNext by rememberSaveable(video.bvid, session?.cookie) { mutableStateOf(true) }
    var episodesInGridView by rememberSaveable(video.bvid, session?.cookie) { mutableStateOf(false) }
    var creatorCard by remember(video.bvid, session?.cookie) { mutableStateOf<UserCardData?>(null) }
    var cacheStatus by remember(video.bvid, selectedPage, session?.cookie) { mutableStateOf(OnlineVideoCacheStatus.IDLE) }
    // Cache entries belong to one quality, so do not reuse 1080P urls after a quality change.
    val episodePlayCache = remember(video.bvid, session?.cookie, selectedQn) { EpisodePlayCache() }
    var retryToken by remember(video.bvid) { mutableIntStateOf(0) }
    // 弹幕偏好跨视频保留；每条分 P 的弹幕列表按 cid 单独加载。
    val danmakuPreferences = remember(context) { DanmakuPreferences(context) }
    var danmakuSettings by remember { mutableStateOf(danmakuPreferences.load()) }
    var danmakuItems by remember(video.bvid) { mutableStateOf<List<Danmaku>>(emptyList()) }
    var danmakuLoading by remember(video.bvid) { mutableStateOf(false) }
    var danmakuError by remember(video.bvid) { mutableStateOf<String?>(null) }
    LaunchedEffect(playbackCid) {
        val cid = playbackCid
        if (cid <= 0L) return@LaunchedEffect
        danmakuItems = emptyList()
        danmakuError = null
        danmakuLoading = true
        try {
            danmakuItems = DanmakuParser.parse(client.danmakuXml(cid))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            danmakuError = error.message ?: "弹幕加载失败"
        } finally {
            danmakuLoading = false
        }
    }
    /** 保存用户刚调整的弹幕偏好，让下一个分 P / 下一个视频继续沿用。 */
    val updateDanmakuSettings: (DanmakuSettings) -> Unit = { updated ->
        danmakuSettings = updated
        danmakuPreferences.save(updated)
    }
    /** 登录后才允许发送；成功后立即插入本地时间轴。 */
    val sendDanmaku: (suspend (String, Long) -> Result<Unit>)? =
        if (session != null && hasLoginCookies(session.cookie)) {
            { message, progressMs ->
                try {
                    client.sendDanmaku(
                        bvid = video.bvid,
                        cid = playbackCid,
                        message = message,
                        progressMs = progressMs,
                    )
                    danmakuItems = (danmakuItems + Danmaku(
                        timeMs = progressMs.coerceAtLeast(0L),
                        mode = Danmaku.Mode.SCROLL,
                        fontSize = 25,
                        color = 0xFFFFFF,
                        text = message,
                    )).sortedBy { it.timeMs }
                    Result.success(Unit)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    Result.failure(error)
                }
            }
        } else {
            null
        }
    /** Attaches fresh playurl data, resuming where the failed stream stopped. */
    fun attachPlayData(data: PlayData, qn: Int, resumeMs: Long = exoPlayer.currentPosition) {
        playbackData = data
        playbackCandidateIndex = 0
        playbackCandidateQn = qn
        playbackCandidateCount = BiliPlayback.candidateCount(data, preferredQn = qn)
        BiliPlayback.attach(exoPlayer, data, preferredQn = qn, cookie = session?.cookie, bvid = video.bvid, startPositionMs = resumeMs)
    }
    /** Moves to the next CDN host pair of the current playurl response. */
    fun rotateCandidate(): Boolean {
        val data = playbackData ?: return false
        val nextIndex = playbackCandidateIndex + 1
        if (nextIndex >= playbackCandidateCount) return false
        val resumeMs = exoPlayer.currentPosition
        playbackCandidateIndex = nextIndex
        playbackState = PlaybackState.Loading
        BiliPlayback.attach(
            exoPlayer, data, preferredQn = playbackCandidateQn, candidateIndex = nextIndex,
            cookie = session?.cookie, bvid = video.bvid, startPositionMs = resumeMs,
        )
        return true
    }
    suspend fun tryHtml5Fallback(): String? {
        html5FallbackTried = true
        return try {
            val fallback = client.playUrlHtml5(video.bvid, playbackCid, qn = BiliQn.P1080)
            attachPlayData(fallback, BiliQn.P1080)
            null
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            error.message ?: "HTML5 单文件流也不可用"
        }
    }

    /**
     * Shared failure path for player errors and startup timeouts. A timeout skips
     * candidate rotation and the 1080P refresh so it can fall back quickly.
     */
    fun handlePlaybackFailure(error: PlaybackException?, skipCandidateRotation: Boolean = false) {
        if (!skipCandidateRotation && rotateCandidate()) return
        // Signed CDN URLs are short-lived and a CDN can reject one
        // host while another URL returned by a fresh playurl request
        // is still usable. Refresh DASH once before falling back to
        // the older single-file format.
        if (!playbackRefreshTried && playbackCid > 0) {
            playbackRefreshTried = true
            playbackState = PlaybackState.Loading
            scope.launch {
                try {
                    val refreshed = client.playUrl(
                        bvid = video.bvid,
                        cid = playbackCid,
                        qn = BiliQn.P1080,
                        fnval = BiliClient.FNVAL_DASH_ALL,
                    )
                    attachPlayData(refreshed, BiliQn.P1080)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // The DURL fallback below provides the next
                    // compatibility path and its error is user-facing.
                    playbackState = PlaybackState.Loading
                    durlFallbackTried = true
                    try {
                        val fallback = client.playUrl(
                            bvid = video.bvid,
                            cid = playbackCid,
                            qn = BiliQn.P720,
                            fnval = BiliClient.FNVAL_DURL,
                        )
                        attachPlayData(fallback, BiliQn.P720)
                    } catch (error2: CancellationException) {
                        throw error2
                    } catch (error2: Exception) {
                        val html5Error = tryHtml5Fallback()
                        if (html5Error != null) {
                            playbackState = PlaybackState.Error("播放器无法读取视频流：${error2.message ?: "单文件流也不可用"}；HTML5 回退失败：$html5Error")
                        }
                    }
                }
            }
            return
        }
        if (!dashFallbackTried && !durlFallbackTried && playbackCid > 0) {
            dashFallbackTried = true
            playbackState = PlaybackState.Loading
            scope.launch {
                try {
                    val fallback = client.playUrl(
                        bvid = video.bvid,
                        cid = playbackCid,
                        qn = BiliQn.P720,
                        fnval = BiliClient.FNVAL_DASH_ALL,
                    )
                    durlFallbackTried = fallback.dash == null
                    attachPlayData(fallback, BiliQn.P720)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    durlFallbackTried = true
                    try {
                        val fallback = client.playUrl(
                            bvid = video.bvid,
                            cid = playbackCid,
                            qn = BiliQn.P720,
                            fnval = BiliClient.FNVAL_DURL,
                        )
                        attachPlayData(fallback, BiliQn.P720)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        val html5Error = tryHtml5Fallback()
                        if (html5Error != null) {
                            playbackState = PlaybackState.Error("播放器无法读取视频流：${error.message ?: "低清晰度视频流也不可用"}；HTML5 回退失败：$html5Error")
                        }
                    }
                }
            }
            return
        }
        // DASH uses separate audio/video URLs. If every CDN pair is rejected,
        // request the older single-file stream format as a compatibility fallback.
        if (!durlFallbackTried && playbackCid > 0) {
            durlFallbackTried = true
            playbackState = PlaybackState.Loading
            scope.launch {
                try {
                    val fallback = client.playUrl(
                        bvid = video.bvid,
                        cid = playbackCid,
                        qn = BiliQn.P720,
                        fnval = BiliClient.FNVAL_DURL,
                    )
                    attachPlayData(fallback, BiliQn.P720)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    val html5Error = tryHtml5Fallback()
                    if (html5Error != null) {
                        playbackState = PlaybackState.Error("播放器无法读取视频流：${error.message ?: "单文件流也不可用"}；HTML5 回退失败：$html5Error")
                    }
                }
            }
            return
        }
        if (!html5FallbackTried && playbackCid > 0) {
            playbackState = PlaybackState.Loading
            scope.launch {
                val fallbackError = tryHtml5Fallback()
                if (fallbackError != null) {
                    playbackState = PlaybackState.Error("播放器无法读取视频流：$fallbackError")
                }
            }
            return
        }
        val httpStatus = generateSequence(error as Throwable?) { it.cause }
            .mapNotNull { (it as? HttpDataSource.InvalidResponseCodeException)?.responseCode }
            .firstOrNull()
        val detail = error?.cause?.message?.takeIf { it.isNotBlank() }
        val message = when {
            error == null -> "视频源响应超时，请重试"
            httpStatus == 403 ||
                (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS && detail?.contains("403") == true) ->
                "CDN 返回 HTTP 403，当前网络出口可能被 B 站拒绝"
            else -> "播放器无法读取视频流：${error.errorCodeName}${detail?.let { "（$it）" }.orEmpty()}"
        }
        playbackState = PlaybackState.Error(message)
    }
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    playbackState = PlaybackState.Ready
                }
            }

            override fun onPlayerError(error: PlaybackException) = handlePlaybackFailure(error)
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }
    // A CDN node can accept the connection and then trickle or stall without
    // ever raising an error, leaving the player on "正在获取视频流". If startup
    // makes no meaningful progress for a while, move to the next backup host;
    // a hard deadline also stops a slow trickle from looking like progress.
    val loadStartedAt = remember(playbackData) { SystemClock.elapsedRealtime() }
    LaunchedEffect(playbackData, playbackCandidateIndex) {
        if (playbackData == null) return@LaunchedEffect
        val candidateStartedAt = SystemClock.elapsedRealtime()
        var lastBuffered = -1L
        var lastProgressAt = candidateStartedAt
        while (playbackState == PlaybackState.Loading) {
            delay(500)
            val now = SystemClock.elapsedRealtime()
            val buffered = exoPlayer.bufferedPosition
            if (buffered > lastBuffered) {
                lastBuffered = buffered
                lastProgressAt = now
            }
            if (now - loadStartedAt >= STARTUP_DEADLINE_MS) {
                // Do not retry the same quality again; jump to the 720P/DURL/
                // HTML5 fallbacks so this load cannot hang indefinitely.
                playbackRefreshTried = true
                handlePlaybackFailure(null, skipCandidateRotation = true)
                return@LaunchedEffect
            }
            if (now - candidateStartedAt >= STARTUP_STALL_TIMEOUT_MS &&
                now - lastProgressAt >= STARTUP_NO_PROGRESS_MS
            ) {
                if (!rotateCandidate()) {
                    playbackRefreshTried = true
                    handlePlaybackFailure(null, skipCandidateRotation = true)
                }
                return@LaunchedEffect
            }
        }
    }
    // Part loading runs in its own scope instead of the effect: when listening in
    // the background, Compose stops recomposing, so the end-of-part listener and the
    // notification "next" start the next part directly through [startLoad].
    val loadScope = remember(video.bvid, session?.cookie) { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    var loadJob by remember(video.bvid, session?.cookie) { mutableStateOf<Job?>(null) }
    var lastRequestedLoad by remember(video.bvid, session?.cookie) { mutableStateOf<List<Any?>?>(null) }
    DisposableEffect(loadScope) { onDispose { loadScope.cancel() } }
    suspend fun loadPlayback() {
        val resumeAtMs = qualityResumeMs
        val resumePlaying = qualityResumePlaying
        qualityResumeMs = 0L
        qualityResumePlaying = true
        playbackState = PlaybackState.Loading
        playbackRefreshTried = false
        dashFallbackTried = false
        durlFallbackTried = false
        html5FallbackTried = false
        playbackData = null
        playbackCid = 0L
        exoPlayer.stop()
        try {
            // View + creator data do not change between parts; only playurl
            // needs a fresh request when switching episodes.
            val info = videoInfo ?: run {
                client.warmUp()
                client.videoInfo(video.bvid).also { loaded ->
                    videoInfo = loaded
                    if (loaded.owner.mid > 0) {
                        scope.launch {
                            creatorCard = runCatching { client.userCard(loaded.owner.mid) }.getOrNull()
                        }
                    }
                }
            }
            val page = info.pages.firstOrNull { it.page == selectedPage }
                ?: info.pages.firstOrNull()?.also { selectedPage = it.page }
            val cid = page?.cid ?: info.cid
            if (cid <= 0) throw IllegalStateException("B 站没有返回视频分 P 信息")
            val resolvedBvid = info.bvid.ifBlank { video.bvid }
            val play = episodePlayCache.take(cid)
                ?: client.playUrl(resolvedBvid, cid, qn = selectedQn)
            playbackCid = cid
            attachPlayData(play, selectedQn, resumeMs = resumeAtMs)
            if (!resumePlaying) exoPlayer.playWhenReady = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            playbackState = PlaybackState.Error(error.message ?: "无法获取视频播放地址")
        }
    }
    fun startLoad(key: List<Any?>) {
        lastRequestedLoad = key
        loadJob?.cancel()
        loadJob = loadScope.launch { loadPlayback() }
    }
    LaunchedEffect(video.bvid, session?.cookie, retryToken, selectedPage, selectedQn, qualityRequestToken) {
        val key = listOf(retryToken, selectedPage, selectedQn, qualityRequestToken)
        // The background queue may already have started exactly this part.
        if (key != lastRequestedLoad) startLoad(key)
    }
    fun listenSubtitle(page: Int): String = listOfNotNull(
        videoInfo?.owner?.name?.takeIf { it.isNotBlank() } ?: video.author.takeIf { it.isNotBlank() },
        videoInfo?.pages?.takeIf { it.size > 1 }?.firstOrNull { it.page == page }
            ?.let { "P${it.page} ${it.part}".trim() },
    ).joinToString(" · ")
    /** Switches part without waiting for recomposition (background-safe). */
    fun advanceToPart(page: Int) {
        selectedPage = page
        startLoad(listOf(retryToken, page, selectedQn, qualityRequestToken))
        ListenPlaybackBridge.updateMetadata(exoPlayer, video.title, listenSubtitle(page))
    }
    val latestAdvance by rememberUpdatedState(::advanceToPart)
    // Fallback paths can lower the stream too, so derive the badge from every
    // successful playurl response instead of only the primary request.
    LaunchedEffect(playbackData) {
        playbackData?.quality?.takeIf { it > 0 }?.let { actualQn = it }
    }
    fun beginCacheDownload() {
        if (cacheStatus == OnlineVideoCacheStatus.DOWNLOADING) return
        if (cacheStatus == OnlineVideoCacheStatus.SAVED) {
            Toast.makeText(context, "当前分 P 已缓存", Toast.LENGTH_SHORT).show()
            return
        }
        val cid = playbackCid
        if (cid <= 0L) {
            Toast.makeText(context, "播放地址尚未准备好，请稍后重试", Toast.LENGTH_SHORT).show()
            return
        }
        val cacheTitle = videoInfo?.title?.takeIf { it.isNotBlank() } ?: video.title
        val updateCacheStatus: (OnlineVideoCacheStatus) -> Unit = { cacheStatus = it }
        startOnlineVideoCache(
            scope = scope,
            context = context,
            client = client,
            bvid = video.bvid,
            cid = cid,
            qn = selectedQn,
            title = cacheTitle,
            cookie = session?.cookie,
            onStatus = updateCacheStatus,
        )
    }
    val cachePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) beginCacheDownload()
        else Toast.makeText(context, "需要存储权限才能缓存视频", Toast.LENGTH_SHORT).show()
    }
    val cacheLabel = when (cacheStatus) {
        OnlineVideoCacheStatus.DOWNLOADING -> "缓存中"
        OnlineVideoCacheStatus.SAVED -> "已缓存"
        OnlineVideoCacheStatus.IDLE -> ""
    }
    val cacheSelected = cacheStatus != OnlineVideoCacheStatus.IDLE
    val onCache: () -> Unit = {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && context.checkSelfPermission(
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            cachePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            beginCacheDownload()
        }
    }
    val episodePages = videoInfo?.pages?.takeIf { it.size > 1 }
    val latestEpisodePages by rememberUpdatedState(episodePages)
    val latestAutoPlayNext by rememberUpdatedState(autoPlayNext)
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state != Player.STATE_ENDED || !latestAutoPlayNext) return
                val parts = latestEpisodePages ?: return
                // Read the live page, not a composition snapshot: in the background
                // several parts can finish before the UI recomposes once.
                val index = parts.indexOfFirst { it.page == selectedPage }
                if (index in 0 until parts.lastIndex) {
                    latestAdvance(parts[index + 1].page)
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }
    LaunchedEffect(exoPlayer, selectedPage, autoPlayNext, episodePages, selectedQn) {
        if (!autoPlayNext) return@LaunchedEffect
        val parts = episodePages ?: return@LaunchedEffect
        val currentIndex = parts.indexOfFirst { it.page == selectedPage }
        val nextPart = parts.getOrNull(currentIndex + 1) ?: return@LaunchedEffect
        while (true) {
            delay(1_000)
            val duration = exoPlayer.duration
            val position = exoPlayer.currentPosition
            if (duration <= 0L || position <= 0L) continue
            if (duration - position > NEXT_PART_PREFETCH_AHEAD_MS) continue
            if (episodePlayCache.containsFresh(nextPart.cid)) break
            try {
                val play = client.playUrl(
                    bvid = video.bvid,
                    cid = nextPart.cid,
                    qn = selectedQn,
                )
                episodePlayCache.put(nextPart.cid, play)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // The regular fetch when the current part ends is the fallback.
            }
            break
        }
    }
    val episodePicker: (@Composable (onDismiss: () -> Unit) -> Unit)? =
        if (episodePages == null) null else { onDismiss ->
            OnlineVideoEpisodes(episodePages, selectedPage, onDismiss) { selectedPage = it.page }
        }
    val episodeOverlayVisible = LocalConfiguration.current.orientation
        .let { it != Configuration.ORIENTATION_LANDSCAPE } &&
        episodesSheetVisible && episodePages != null
    val overlayPages = episodePages
    val episodeOverlayContent: (@Composable () -> Unit)? = if (overlayPages != null) {
        {
            OnlineEpisodesBottomSheet(
                pages = overlayPages,
                selectedPage = selectedPage,
                autoPlayNext = autoPlayNext,
                onAutoPlayNextChange = { autoPlayNext = it },
                gridView = episodesInGridView,
                onGridViewChange = { episodesInGridView = it },
                onDismiss = { episodesSheetVisible = false },
                onPageSelected = { selectedPage = it }
            )
        }
    } else null
    val qualityOptions = remember(playbackData, actualQn) { buildQualityOptions(playbackData, actualQn) }
    // Show what is actually playing; fall back to the requested value while loading.
    val effectiveQuality = actualQn.takeIf { qn -> qn > 0 && qualityOptions.any { it.qn == qn } }
        ?: qualityOptions.firstOrNull { it.qn == selectedQn }?.qn
        ?: qualityOptions.maxByOrNull { it.qn }?.qn
    val nextPage = videoInfo?.pages?.let { pages ->
        val currentIndex = pages.indexOfFirst { it.page == selectedPage }
        if (currentIndex >= 0) pages.getOrNull(currentIndex + 1) else null
    }
    val listenQueue = remember(exoPlayer) {
        object : ListenQueue {
            private fun next() = latestEpisodePages?.let { parts ->
                val index = parts.indexOfFirst { it.page == selectedPage }
                if (index >= 0) parts.getOrNull(index + 1) else null
            }
            override val hasNext: Boolean get() = next() != null
            override fun skipToNext() { next()?.let { latestAdvance(it.page) } }
        }
    }
    VideoPlayerScreen(
        player = exoPlayer,
        title = video.title,
        playbackError = (playbackState as? PlaybackState.Error)?.message,
        loadingMessage = if (playbackState == PlaybackState.Loading) {
            // 区分“请求播放地址”和“连接视频源”，等待时能看出卡在哪一步。
            if (playbackData == null) "正在获取播放地址…" else "正在连接视频源…"
        } else null,
        sourceLabel = "哔哩哔哩",
        commentMessage = "暂不支持查看在线评论",
        onBack = onBack,
        onRetry = { retryToken++ },
        onNextEpisode = nextPage?.let { page -> { advanceToPart(page.page) } },
        qualityOptions = qualityOptions,
        selectedQuality = effectiveQuality,
        onQualitySelected = { qn ->
            if (qn == actualQn) {
                // Already streaming at this quality; just keep the requested value in sync.
                selectedQn = qn
            } else {
                qualityResumeMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                qualityResumePlaying = exoPlayer.playWhenReady
                selectedQn = qn
                // The selected qn may already match while the server clamped the
                // stream, so use a token to force a fresh playurl request.
                qualityRequestToken++
            }
        },
        danmakuSupported = true,
        danmakuItems = danmakuItems,
        danmakuSettings = danmakuSettings,
        danmakuLoading = danmakuLoading,
        danmakuError = danmakuError,
        onDanmakuSettingsChange = updateDanmakuSettings,
        onSendDanmaku = sendDanmaku,
        episodes = episodePicker,
        episodeOverlay = episodeOverlayContent,
        episodeOverlayVisible = episodeOverlayVisible,
        episodeOverlayDismiss = if (episodeOverlayVisible) {
            { episodesSheetVisible = false }
        } else null,
        detailsBackground = OnlineSurface,
        showDetailTabs = false,
        listenInfo = ListenInfo(
            subtitle = listenSubtitle(selectedPage),
            online = true,
            coverUrl = videoInfo?.pic?.takeIf { it.isNotBlank() }?.let(::normalizeImageUrl) ?: video.coverUrl,
        ),
        listenQueue = listenQueue,
        remainingVideos = episodePages?.let { parts ->
            val index = parts.indexOfFirst { it.page == selectedPage }
            if (index >= 0) (parts.size - index).coerceAtLeast(1) else 1
        } ?: 1,
        details = { _ ->
            Box(Modifier.fillMaxWidth()) {
                OnlineVideoDetails(
                    video = video,
                    info = videoInfo,
                    creatorCard = creatorCard,
                    context = context,
                    selectedPage = selectedPage,
                    autoPlayNext = autoPlayNext,
                    onAutoPlayNextChange = { autoPlayNext = it },
                    onPageSelected = { selectedPage = it },
                    onOpenEpisodes = { episodesSheetVisible = true },
                    cacheLabel = cacheLabel,
                    cacheSelected = cacheSelected,
                    onCache = onCache,
                )
            }
        }
    )
}

@Composable
internal fun OnlineVideoEpisodes(
    pages: List<Page>,
    selectedPage: Int,
    onDismiss: () -> Unit,
    onPageSelected: (Page) -> Unit,
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = pages.indexOfFirst { it.page == selectedPage }.coerceAtLeast(0)
    )
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f))
            .clickable(onClick = onDismiss).semantics { contentDescription = "关闭选集" })
        Column(
            Modifier.align(Alignment.CenterEnd).width(360.dp).fillMaxHeight()
                .background(Color(0xF2121318))
                .border(1.dp, Color.White.copy(alpha = .08f), RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .navigationBarsPadding()
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp).height(56.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("分集(${pages.size}P)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = "收起选集" }) {
                    Text("×", color = Color.White.copy(alpha = .72f), fontSize = 24.sp)
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = .08f))
            LazyColumn(Modifier.weight(1f).selectableGroup(), state = listState,
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(pages, key = { _, page -> page.cid }) { _, page ->
                    OnlineEpisodeRow(page, page.page == selectedPage) { onPageSelected(page); onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun OnlineEpisodeRow(page: Page, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
            .background(if (selected) OnlinePink.copy(alpha = .18f) else Color.White.copy(alpha = .05f))
            .border(1.dp, if (selected) OnlinePink else Color.Transparent, RoundedCornerShape(11.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) EqualizerBars()
        Text(
            "P${page.page} ${page.part.ifBlank { "第 ${page.page} 部分" }}${if (selected) " (当前播放)" else ""}",
            color = if (selected) OnlinePink else Color.White,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = if (selected) 4.dp else 0.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            formatPlaybackTime(page.duration * 1_000L),
            color = if (selected) OnlinePink else Color.White.copy(alpha = .45f),
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

@Composable
internal fun EqualizerBars() {
    Row(Modifier.width(20.dp).height(16.dp), verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        val heights = listOf(8.dp, 13.dp, 10.dp)
        heights.forEach { height -> Box(Modifier.width(3.dp).height(height).clip(RoundedCornerShape(2.dp)).background(OnlinePink)) }
    }
}

/**
 * Maps the playurl `accept_quality` list to the compact labels used by the
 * fullscreen quality drawer. Anonymous playback is reliable up to 1080P; the
 * 4K/8K/Dolby tiers need account privileges and are intentionally hidden for
 * now instead of offering an option that cannot start.
 */
private fun buildQualityOptions(play: PlayData?, actualQn: Int): List<PlayerQualityOption> {
    val accepted = (play?.acceptQuality.orEmpty() + listOf(actualQn))
        .filter { it in BiliQn.P360..BiliQn.P1080 }
        .distinct()
    val qns = accepted.ifEmpty {
        listOf(BiliQn.P1080, BiliQn.P720, BiliQn.P480, BiliQn.P360)
    }.sortedDescending()
    return qns.map { qn ->
        when (qn) {
            74 -> PlayerQualityOption(qn, "720P 60帧", "高帧率")
            BiliQn.P1080 -> PlayerQualityOption(qn, "1080P", "高清 · 推荐")
            BiliQn.P720 -> PlayerQualityOption(qn, "720P", "高清 · 省流")
            BiliQn.P480 -> PlayerQualityOption(qn, "480P", "清晰")
            BiliQn.P360 -> PlayerQualityOption(qn, "360P", "流畅")
            else -> PlayerQualityOption(qn, "${qn}P", "")
        }
    }
}
