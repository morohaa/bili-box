package io.github.bilibox.data.search

import android.content.Context
import org.json.JSONArray

/**
 * 最近搜索的合并规则。
 *
 * 提交的搜索词去掉首尾空白后置于最前，重复项（忽略大小写）只保留最新一条，
 * 最多保留 [LIMIT] 条。抽成纯函数便于单元测试，存储实现见 [SearchHistoryStore]。
 */
internal object SearchHistoryRules {
    const val LIMIT = 10

    fun merge(history: List<String>, keyword: String): List<String> {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return history
        return buildList {
            add(trimmed)
            history.forEach { entry ->
                if (!entry.equals(trimmed, ignoreCase = true) && entry.isNotBlank()) add(entry)
            }
        }.take(LIMIT)
    }
}

/** 动态页搜索历史的本地存储，跨启动保留最近 [SearchHistoryRules.LIMIT] 条。 */
internal class SearchHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences("search_history", Context.MODE_PRIVATE)

    fun load(): List<String> {
        val raw = preferences.getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val entry = array.optString(index).trim()
                    if (entry.isNotEmpty()) add(entry)
                }
            }
        }.getOrDefault(emptyList())
    }

    /** 记录一次提交，返回更新后的历史列表。 */
    fun add(keyword: String): List<String> {
        val next = SearchHistoryRules.merge(load(), keyword)
        save(next)
        return next
    }

    fun clear() {
        preferences.edit().remove(KEY_HISTORY).apply()
    }

    private fun save(history: List<String>) {
        val array = JSONArray()
        history.forEach(array::put)
        preferences.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    private companion object {
        const val KEY_HISTORY = "recent_queries"
    }
}
