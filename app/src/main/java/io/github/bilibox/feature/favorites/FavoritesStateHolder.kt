package io.github.bilibox.feature.favorites

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.bilibox.data.bili.BiliException
import io.github.bilibox.data.favorites.FavoriteFolder
import io.github.bilibox.data.favorites.FavoriteOrder
import io.github.bilibox.data.favorites.FavoriteVideo
import io.github.bilibox.data.favorites.FavoritesSource
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class FavoritesUiState(
    val folders: List<FavoriteFolder> = emptyList(),
    val folder: FavoriteFolder? = null,
    val videos: List<FavoriteVideo> = emptyList(),
    val loading: Boolean = false,
    val initialized: Boolean = false,
    val hasMore: Boolean = false,
    val page: Int = 0,
    val total: Int = 0,
    val keyword: String = "",
    val order: FavoriteOrder = FavoriteOrder.SAVED,
    val error: String? = null,
    val needsLogin: Boolean = false,
)

/** 请求取消、分页与筛选属于收藏页；切换播放覆盖层时保留浏览结果。 */
internal class FavoritesStateHolder(private val source: FavoritesSource, private val scope: CoroutineScope) {
    var state by mutableStateOf(FavoritesUiState())
        private set
    private var job: Job? = null
    private var mid: Long? = null
    private var lastRequest: (() -> Unit)? = null

    fun start() {
        if (!state.initialized && !state.loading) refresh()
    }

    fun refresh() {
        if (state.folder != null) {
            loadVideos(reset = true)
        } else {
            loadFolders(reset = true)
        }
    }

    fun openFolder(folder: FavoriteFolder) {
        job?.cancel()
        state = state.copy(folder = folder, videos = emptyList(), keyword = "", order = FavoriteOrder.SAVED, page = 0, total = folder.count)
        loadVideos(reset = true)
    }

    fun closeFolder() {
        job?.cancel()
        state = state.copy(folder = null, videos = emptyList(), loading = false, error = null, needsLogin = false, keyword = "", page = folderPage, hasMore = moreFolders)
    }

    fun filter(keyword: String = state.keyword, order: FavoriteOrder = state.order) {
        if (state.folder == null) return
        state = state.copy(keyword = keyword.trim(), order = order)
        loadVideos(reset = true)
    }

    fun loadMore() {
        if (state.loading || !state.hasMore || state.needsLogin) return
        if (state.folder == null) loadFolders(reset = false) else loadVideos(reset = false)
    }

    fun retry() {
        if (state.needsLogin) return
        lastRequest?.invoke()
    }

    fun dispose() {
        job?.cancel()
    }

    private var folderPage = 0
    private var moreFolders = false

    private fun loadFolders(reset: Boolean) {
        lastRequest = { loadFolders(reset) }
        job?.cancel()
        val page = if (reset) 1 else folderPage + 1
        state = state.copy(loading = true, error = null, needsLogin = false)
        job = scope.launch {
            try {
                val account = if (reset || mid == null) source.accountId().also { mid = it } else requireNotNull(mid)
                val result = source.folders(account, page)
                folderPage = page
                moreFolders = result.hasMore
                state = state.copy(
                    folders = ((if (reset) emptyList() else state.folders) + result.items).distinctBy { it.id },
                    loading = false,
                    initialized = true,
                    page = page,
                    hasMore = result.hasMore,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                fail(error)
            }
        }
    }

    private fun loadVideos(reset: Boolean) {
        val folder = state.folder ?: return
        lastRequest = { loadVideos(reset) }
        job?.cancel()
        val page = if (reset) 1 else state.page + 1
        val keyword = state.keyword
        val order = state.order
        state = state.copy(loading = true, error = null, needsLogin = false, videos = if (reset) emptyList() else state.videos, page = if (reset) 0 else state.page)
        job = scope.launch {
            try {
                val result = source.videos(folder.id, page, keyword, order)
                state = state.copy(
                    videos = ((if (reset) emptyList() else state.videos) + result.items).distinctBy { it.id },
                    loading = false,
                    page = page,
                    hasMore = result.hasMore,
                    total = result.total,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                fail(error)
            }
        }
    }

    private fun fail(error: Exception) {
        val login = error is BiliException && error.code in setOf(-101, -111)
        val message = when {
            login -> "登录已失效，请返回「我的」重新登录"

            error is IOException -> "网络连接失败，请检查网络后重试"

            error is BiliException -> when (error.code) {
                -403, 403, 72010027 -> "此收藏夹已删除、设为私密或当前账号无权访问"
                -352, -412, 412, 429 -> "B 站暂时限制了请求，请稍后重试"
                else -> "收藏加载失败，请稍后重试（${error.code}）"
            }

            else -> "收藏数据暂时无法读取，请稍后重试"
        }
        state = state.copy(loading = false, initialized = true, error = message, needsLogin = login)
    }
}
