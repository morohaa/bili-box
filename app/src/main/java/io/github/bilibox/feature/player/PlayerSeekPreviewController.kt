package io.github.bilibox.feature.player

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import io.github.bilibox.data.localvideo.LocalPreviewFrames
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

/** 跟随播放源而非预览弹窗存活：预热一次、复用解码器，并保留上一张可见缩略图。 */
@Stable
internal class PlayerSeekPreviewController(val source: PlayerPreviewSource) {
    val local = source.mediaItem.localConfiguration?.uri?.scheme in setOf(null, "file", "content", "android.resource")
    var frame by mutableStateOf<Bitmap?>(null)
        private set
    var framePositionMs by mutableLongStateOf(-1L)
        private set
    var requestedPosition by mutableLongStateOf(0L)
        private set
    var failed by mutableStateOf(false)
    var networkRendered by mutableStateOf(false)
    var networkPlayer by mutableStateOf<ExoPlayer?>(null)
    val requests = Channel<Long>(Channel.CONFLATED)
    private val cache = object : LruCache<Long, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount
    }

    fun request(positionMs: Long) {
        val bucket = positionMs.coerceAtLeast(0L) / 250L * 250L
        requestedPosition = bucket
        failed = false
        if (local) {
            val cached = cache.get(bucket)
            if (cached != null) {
                frame = cached
                framePositionMs = bucket
            }
            // 即使缓存命中也替换队列中的旧目标，后台不会继续处理已过时的位置。
            requests.trySend(bucket)
        }
    }

    fun cached(positionMs: Long): Bitmap? = cache.get(positionMs)

    fun accept(positionMs: Long, bitmap: Bitmap?) {
        if (bitmap != null) cache.put(positionMs, bitmap)
        if (positionMs != requestedPosition) return
        failed = bitmap == null
        if (bitmap != null) {
            frame = bitmap
            framePositionMs = positionMs
        }
    }

    fun retainNetworkFrame(bitmap: Bitmap?) {
        if (bitmap != null) frame = bitmap
        networkRendered = false
    }
}

@Composable
internal fun rememberPlayerSeekPreview(player: Player, override: PlayerPreviewSource?, enabled: Boolean = true): PlayerSeekPreviewController? {
    if (!enabled) return null
    val source = rememberPlayerPreviewSource(player, override) ?: return null
    val controller = remember(source) { PlayerSeekPreviewController(source) }
    val context = LocalContext.current.applicationContext
    if (controller.local) {
        LaunchedEffect(controller) {
            controller.request(player.currentPosition)
            try {
                withContext(Dispatchers.IO) {
                    LocalPreviewFrames(context, source.mediaItem.localConfiguration!!.uri).use { reader ->
                        for (target in controller.requests) {
                            val cached = withContext(Dispatchers.Main) { controller.cached(target) }
                            val bitmap = cached ?: runCatching { reader.frame(target) }.getOrNull()
                            withContext(Dispatchers.Main) { controller.accept(target, bitmap) }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                controller.failed = true
            }
        }
    } else {
        NetworkSeekPreviewSession(controller, player.currentPosition)
    }
    return controller
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun NetworkSeekPreviewSession(controller: PlayerSeekPreviewController, startPositionMs: Long) {
    val context = LocalContext.current
    val player = remember(controller) {
        val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
            .setMediaCodecSelector { mime, secure, tunneling ->
                MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling).sortedBy { !it.softwareOnly }
            }
        ExoPlayer.Builder(context, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(controller.source.dataSourceFactory ?: DefaultDataSource.Factory(context)))
            .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(250, 1_000, 100, 100).build())
            .build().apply {
                volume = 0f
                playWhenReady = false
                trackSelectionParameters = trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
                setSeekParameters(SeekParameters.CLOSEST_SYNC)
            }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                controller.networkRendered = true
            }
            override fun onPlayerError(error: PlaybackException) {
                controller.failed = true
            }
        }
        controller.networkPlayer = player
        controller.request(startPositionMs)
        player.addListener(listener)
        player.setMediaItem(controller.source.mediaItem, startPositionMs)
        player.prepare()
        onDispose {
            controller.networkPlayer = null
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player) {
        snapshotFlow { controller.requestedPosition }.conflate().collect { target ->
            if (player.currentPosition != target) player.seekTo(target)
            delay(50)
        }
    }
}
