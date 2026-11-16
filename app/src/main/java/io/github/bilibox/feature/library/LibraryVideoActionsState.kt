package io.github.bilibox.feature.library

import android.Manifest
import android.app.Activity
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.data.localvideo.LocalVideoFileResult
import io.github.bilibox.data.localvideo.LocalVideoFileStore
import io.github.bilibox.data.localvideo.LocalVideoPlaybackState
import io.github.bilibox.data.localvideo.PlaybackProgressStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
internal class LibraryVideoActionsState(
    private val files: LocalVideoFileStore,
    private val progress: PlaybackProgressStore,
    private val scope: CoroutineScope,
    private val onFileChanged: (LocalVideo, String?) -> Unit,
    private val notify: (String) -> Unit,
) {
    var renaming by mutableStateOf<LocalVideo?>(null)
        private set
    var deleting by mutableStateOf<LocalVideo?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var version by mutableIntStateOf(0)
        private set
    var authorization by mutableStateOf<IntentSender?>(null)
        private set
    var storagePermission by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var pending: FileAction? = null

    fun openRename(video: LocalVideo) {
        if (busy) return
        error = null
        renaming = video
    }

    fun openDelete(video: LocalVideo) {
        if (busy) return
        error = null
        deleting = video
    }

    fun dismiss() {
        if (busy) return
        renaming = null
        deleting = null
        error = null
    }

    fun toggleWatched(video: LocalVideo) {
        val watched = progress.playback(video.key, video.durationMs).state == LocalVideoPlaybackState.Watched
        if (watched) {
            progress.markUnplayed(video.key)
        } else {
            progress.save(video.key, 0L, video.durationMs, completed = true)
        }
        version++
        notify(if (watched) "已设为未播放" else "已设为已播完")
    }

    fun rename(title: String) {
        val video = renaming ?: return
        if (!busy) perform(FileAction(video, title))
    }

    fun delete() {
        val video = deleting ?: return
        if (!busy) perform(FileAction(video))
    }

    fun authorizationResult(granted: Boolean) {
        authorization = null
        storagePermission = false
        val action = pending ?: return
        pending = null
        if (granted) {
            perform(action, authorized = true)
        } else {
            busy = false
            error = "已取消，文件未修改"
        }
    }

    private fun perform(action: FileAction, authorized: Boolean = false) {
        busy = true
        error = null
        scope.launch {
            try {
                val result = if (action.title == null) files.delete(action.video) else files.rename(action.video, action.title)
                when (result) {
                    is LocalVideoFileResult.Done -> {
                        onFileChanged(action.video, result.name)
                        version++
                        renaming = null
                        deleting = null
                        busy = false
                        notify(if (action.title == null) "已删除视频" else "已重命名")
                    }

                    else -> {
                        check(!authorized) { "未获得文件修改权限，请重试" }
                        pending = action
                        when (result) {
                            is LocalVideoFileResult.Consent -> authorization = result.sender
                            LocalVideoFileResult.StoragePermission -> storagePermission = true
                            else -> Unit
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                busy = false
                error = if (failure is java.io.IOException || failure is IllegalStateException) failure.message else "无法修改这个视频，请检查文件访问权限"
            }
        }
    }

    private data class FileAction(val video: LocalVideo, val title: String? = null)
}

@Composable
internal fun rememberLibraryVideoActions(
    onFileChanged: (LocalVideo, String?) -> Unit,
    notify: (String) -> Unit,
): LibraryVideoActionsState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val changed by rememberUpdatedState(onFileChanged)
    val message by rememberUpdatedState(notify)
    val state = remember(context, scope) {
        LibraryVideoActionsState(LocalVideoFileStore(context), PlaybackProgressStore(context), scope, { video, name -> changed(video, name) }, { message(it) })
    }
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        state.authorizationResult(it.resultCode == Activity.RESULT_OK)
    }
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), state::authorizationResult)
    LaunchedEffect(state.authorization) {
        state.authorization?.let { sender ->
            try {
                consentLauncher.launch(IntentSenderRequest.Builder(sender).build())
            } catch (_: Exception) {
                state.authorizationResult(false)
            }
        }
    }
    LaunchedEffect(state.storagePermission) {
        if (state.storagePermission) storageLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
    return state
}
