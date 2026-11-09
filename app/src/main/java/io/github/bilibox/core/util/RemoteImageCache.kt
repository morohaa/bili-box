package io.github.bilibox.core.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request

/** 通用远程图片缓存；加载跟随缓存生命周期，列表项离开组合不会中断其他消费者。 */
internal class RemoteImageCache(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .build(),
    maxBytes: Int = 32 * 1024 * 1024,
) {
    private val memory = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = mutableMapOf<String, Deferred<Bitmap?>>()
    private val lock = Any()
    private val permits = Semaphore(4)

    fun cached(url: String): Bitmap? = memory.get(url)

    suspend fun load(url: String): Bitmap? {
        if (url.isBlank()) return null
        val request = synchronized(lock) {
            cached(url)?.let { return it }
            requests[url] ?: scope.async(start = CoroutineStart.LAZY) {
                permits.withPermit {
                    val bitmap = download(url)
                    val context = currentCoroutineContext()
                    synchronized(lock) {
                        context.ensureActive()
                        if (bitmap != null) memory.put(url, bitmap)
                    }
                    bitmap
                }
            }.also { task ->
                requests[url] = task
                task.invokeOnCompletion {
                    synchronized(lock) {
                        if (requests[url] === task) requests.remove(url)
                    }
                }
            }
        }
        request.start()
        return request.await()
    }

    fun clear() {
        synchronized(lock) {
            // 先移除再取消，避免已完成任务的回调在遍历期间修改集合。
            val pending = requests.values.toList()
            requests.clear()
            pending.forEach { it.cancel() }
            memory.evictAll()
        }
    }

    private fun download(url: String): Bitmap? {
        return try {
            val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
                var sample = 1
                while (bounds.outWidth.toLong() > MAX_EDGE.toLong() * sample || bounds.outHeight.toLong() > MAX_EDGE.toLong() * sample) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    companion object {
        private const val MAX_EDGE = 640
        val shared = RemoteImageCache()
    }
}
