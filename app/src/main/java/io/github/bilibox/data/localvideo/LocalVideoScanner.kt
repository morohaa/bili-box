package io.github.bilibox.data.localvideo

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/** Reads every video the system media store exposes, plus best-effort folder markers. */
internal fun queryLocalVideoLibrary(context: Context): LocalVideoScanResult {
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }
    val projection = buildList {
        add(MediaStore.Video.Media._ID)
        add(MediaStore.Video.Media.DISPLAY_NAME)
        add(MediaStore.Video.Media.DURATION)
        add(MediaStore.Video.Media.SIZE)
        add(MediaStore.Video.Media.DATE_ADDED)
        add(MediaStore.Video.Media.DATE_MODIFIED)
        add(MediaStore.Video.Media.WIDTH)
        add(MediaStore.Video.Media.HEIGHT)
        add(MediaStore.Video.Media.MIME_TYPE)
        add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            add(MediaStore.Video.Media.RELATIVE_PATH)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(MediaStore.Video.Media.CAPTURE_FRAMERATE)
        }
    }.toTypedArray()

    val videos = runCatching {
        readLocalVideos(context, collection, projection)
    }.getOrElse {
        // Some OEM media providers do not expose every optional column. Retry with the
        // universal projection and derive the folder from the legacy DATA column.
        @Suppress("DEPRECATION")
        val fallback = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.DATE_ADDED)
            add(MediaStore.Video.Media.DATE_MODIFIED)
            add(MediaStore.Video.Media.WIDTH)
            add(MediaStore.Video.Media.HEIGHT)
            add(MediaStore.Video.Media.MIME_TYPE)
            add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            @Suppress("DEPRECATION")
            add(MediaStore.Video.Media.DATA)
        }.toTypedArray()
        readLocalVideos(context, collection, fallback)
    }

    return LocalVideoScanResult(videos, detectNomediaFolders(videos))
}

private fun readLocalVideos(
    context: Context,
    collection: Uri,
    projection: Array<String>,
): List<LocalVideo> {
    val videos = mutableListOf<LocalVideo>()
    context.contentResolver.query(
        collection,
        projection,
        null,
        null,
        "${MediaStore.Video.Media.DATE_MODIFIED} DESC",
    )?.use { cursor ->
        val idIndex = cursor.getColumnIndex(MediaStore.Video.Media._ID)
        val nameIndex = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
        val durationIndex = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
        val sizeIndex = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
        val addedIndex = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
        val modifiedIndex = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
        val widthIndex = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
        val heightIndex = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
        val mimeIndex = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
        val bucketIndex = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
        val relativeIndex = cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
        val frameRateIndex = cursor.getColumnIndex(MediaStore.Video.Media.CAPTURE_FRAMERATE)
        @Suppress("DEPRECATION")
        val dataIndex = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
        val externalRoot = Environment.getExternalStorageDirectory().absolutePath.trimEnd('/')

        while (cursor.moveToNext()) {
            val id = cursor.longOrNull(idIndex) ?: continue
            val name = cursor.stringOrNull(nameIndex).orEmpty().ifBlank { "未命名视频" }
            val bucketName = cursor.stringOrNull(bucketIndex).orEmpty()
            val relativePath = when {
                relativeIndex >= 0 -> cursor.stringOrNull(relativeIndex).orEmpty()
                dataIndex >= 0 -> cursor.stringOrNull(dataIndex)
                    ?.takeIf { it.startsWith(externalRoot) }
                    ?.removePrefix(externalRoot)
                    ?.trimStart('/')
                    ?.substringBeforeLast('/', "")
                    .orEmpty()
                else -> bucketName
            }.trim().trim('/')
            videos += LocalVideo(
                uri = ContentUris.withAppendedId(collection, id),
                name = name,
                durationMs = cursor.longOrNull(durationIndex)?.coerceAtLeast(0L) ?: 0L,
                sizeBytes = cursor.longOrNull(sizeIndex)?.coerceAtLeast(0L) ?: 0L,
                relativePath = relativePath,
                bucketName = bucketName.ifBlank { relativePath.substringAfterLast('/') },
                dateAddedMs = (cursor.longOrNull(addedIndex) ?: 0L) * 1_000L,
                dateModifiedMs = (cursor.longOrNull(modifiedIndex) ?: 0L) * 1_000L,
                width = cursor.longOrNull(widthIndex)?.toInt() ?: 0,
                height = cursor.longOrNull(heightIndex)?.toInt() ?: 0,
                frameRate = if (frameRateIndex >= 0) cursor.floatOrNull(frameRateIndex) ?: 0f else 0f,
                mimeType = cursor.stringOrNull(mimeIndex).orEmpty(),
            )
        }
    }
    return videos
}

private fun Cursor.longOrNull(index: Int): Long? =
    if (index < 0 || isNull(index)) null else getLong(index)

private fun Cursor.floatOrNull(index: Int): Float? =
    if (index < 0 || isNull(index)) null else getFloat(index)

private fun Cursor.stringOrNull(index: Int): String? =
    if (index < 0 || isNull(index)) null else getString(index)

private fun detectNomediaFolders(videos: List<LocalVideo>): Set<String> {
    val root = Environment.getExternalStorageDirectory()
    return videos.asSequence()
        .map { it.relativeDirectory }
        .filter { it.isNotBlank() }
        .distinct()
        .filter { path ->
            runCatching { File(File(root, path), ".nomedia").exists() }.getOrDefault(false)
        }
        .toSet()
}
