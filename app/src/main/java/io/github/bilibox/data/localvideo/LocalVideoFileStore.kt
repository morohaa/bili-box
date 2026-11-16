package io.github.bilibox.data.localvideo

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal sealed interface LocalVideoFileResult {
    data class Done(val name: String? = null) : LocalVideoFileResult
    data class Consent(val sender: IntentSender) : LocalVideoFileResult
    data object StoragePermission : LocalVideoFileResult
}

/** Mutates one MediaStore item, asking Android for access only when needed. */
internal class LocalVideoFileStore(context: Context) {
    private val context = context.applicationContext
    private val resolver = this.context.contentResolver

    suspend fun rename(video: LocalVideo, title: String): LocalVideoFileResult = withContext(Dispatchers.IO) {
        localVideoNameError(title, video.extension)?.let { throw IOException(it) }
        val name = localVideoRenamedFileName(video, title)
        val target = video.copy(uri = resolveUri(video) ?: throw IOException("视频已不存在，请刷新列表"))
        mutate(target, deleting = false) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = ContentUris.removeId(target.uri)
                resolver.query(
                    collection,
                    arrayOf(MediaStore.Video.Media._ID),
                    "${MediaStore.Video.Media.RELATIVE_PATH} = ? AND ${MediaStore.Video.Media.DISPLAY_NAME} = ?",
                    arrayOf("${video.relativeDirectory}/", name),
                    null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        if (cursor.getLong(0) != ContentUris.parseId(target.uri)) throw IOException("这个文件夹中已有同名视频")
                    }
                }
                val updated = resolver.update(
                    target.uri,
                    ContentValues().apply { put(MediaStore.Video.Media.DISPLAY_NAME, name) },
                    null,
                    null,
                )
                if (updated != 1) throw IOException("视频已不存在，请刷新列表")
            } else {
                renameLegacy(target, name)
            }
            // Providers can normalize a name; use the actual value in the browser.
            val actual = resolver.query(target.uri, arrayOf(MediaStore.Video.Media.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            } ?: name
            LocalVideoFileResult.Done(actual)
        }
    }

    suspend fun delete(video: LocalVideo): LocalVideoFileResult = withContext(Dispatchers.IO) {
        val uri = resolveUri(video) ?: return@withContext LocalVideoFileResult.Done()
        mutate(video.copy(uri = uri), deleting = true) {
            val removed = resolver.delete(uri, null, null)
            // On Android 11+, approving createDeleteRequest already removes the item.
            val remains = if (removed > 0) {
                false
            } else {
                resolver.query(
                    uri,
                    arrayOf(MediaStore.Video.Media._ID),
                    null,
                    null,
                    null,
                )?.use { it.moveToFirst() } == true
            }
            if (remains) throw IOException("未能删除视频，请重试")
            LocalVideoFileResult.Done()
        }
    }

    /** Consent requests require a concrete volume, rather than the scanner's aggregate external URI. */
    private fun resolveUri(video: LocalVideo): Uri? {
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.VOLUME_NAME)
        } else {
            arrayOf(MediaStore.Video.Media._ID)
        }
        return resolver.query(video.uri, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val volume = cursor.getString(1)
                ContentUris.withAppendedId(MediaStore.Video.Media.getContentUri(volume), cursor.getLong(0))
            } else {
                video.uri
            }
        }
    }

    private inline fun mutate(video: LocalVideo, deleting: Boolean, action: () -> LocalVideoFileResult): LocalVideoFileResult {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            return LocalVideoFileResult.StoragePermission
        }
        return try {
            action()
        } catch (error: SecurityException) {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    val request = if (deleting) MediaStore.createDeleteRequest(resolver, listOf(video.uri)) else MediaStore.createWriteRequest(resolver, listOf(video.uri))
                    LocalVideoFileResult.Consent(request.intentSender)
                }

                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && error is RecoverableSecurityException ->
                    LocalVideoFileResult.Consent(error.userAction.actionIntent.intentSender)

                else -> throw error
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun renameLegacy(video: LocalVideo, name: String) {
        val path = resolver.query(video.uri, arrayOf(MediaStore.Video.Media.DATA), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: throw IOException("视频已不存在，请刷新列表")
        val source = File(path)
        val target = File(source.parentFile, name)
        if (source == target) return
        if (target.exists()) throw IOException("这个文件夹中已有同名文件")
        if (!source.renameTo(target)) throw IOException("无法重命名这个视频")
        try {
            val updated = resolver.update(
                video.uri,
                ContentValues().apply {
                    put(MediaStore.Video.Media.DATA, target.absolutePath)
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                },
                null,
                null,
            )
            if (updated != 1) throw IOException("无法更新文件信息")
        } catch (error: Exception) {
            target.renameTo(source)
            throw error
        }
    }
}

internal fun localVideoRenamedFileName(video: LocalVideo, title: String): String {
    val suffix = video.name.substringAfterLast('.', "").takeIf { video.name.lastIndexOf('.') > 0 }.orEmpty()
    return title.trim() + if (suffix.isBlank()) "" else ".$suffix"
}

internal fun localVideoNameError(title: String, extension: String): String? {
    val trimmed = title.trim()
    return when {
        trimmed.isBlank() || trimmed.all { it == '.' } -> "请输入文件名"
        trimmed.any { it < ' ' || it == '\u007f' || it in "\"*/:<>?\\|" } -> "文件名不能包含 / \\ : * ? \" < > | 等字符"
        trimmed.endsWith('.') -> "文件名不能以句点结尾"
        (trimmed + if (extension.isBlank()) "" else ".$extension").toByteArray(Charsets.UTF_8).size > 255 -> "文件名太长，请缩短后重试"
        else -> null
    }
}
