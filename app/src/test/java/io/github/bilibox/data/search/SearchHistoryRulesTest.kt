package io.github.bilibox.data.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHistoryRulesTest {
    @Test
    fun `new keyword goes first and keeps older entries`() {
        val merged = SearchHistoryRules.merge(listOf("线性代数", "Kotlin 协程"), "番茄炒蛋")
        assertEquals(listOf("番茄炒蛋", "线性代数", "Kotlin 协程"), merged)
    }

    @Test
    fun `duplicate keyword is moved to the front without repeating`() {
        val merged = SearchHistoryRules.merge(listOf("a", "b", "A"), "a")
        assertEquals(listOf("a", "b"), merged)
    }

    @Test
    fun `keyword is trimmed before matching`() {
        val merged = SearchHistoryRules.merge(listOf("雨中漫步 4K"), "  雨中漫步 4K  ")
        assertEquals(listOf("雨中漫步 4K"), merged)
    }

    @Test
    fun `blank keyword leaves history untouched`() {
        val history = listOf("a", "b")
        assertEquals(history, SearchHistoryRules.merge(history, "   "))
    }

    @Test
    fun `history is capped at the limit`() {
        val history = (1..SearchHistoryRules.LIMIT).map { "item-$it" }
        val merged = SearchHistoryRules.merge(history, "new")
        assertEquals(SearchHistoryRules.LIMIT, merged.size)
        assertEquals("new", merged.first())
        assertEquals("item-9", merged.last())
    }
}