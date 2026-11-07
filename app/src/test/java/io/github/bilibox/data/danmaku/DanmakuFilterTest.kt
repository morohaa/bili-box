package io.github.bilibox.data.danmaku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuFilterTest {
    @Test
    fun defaultSettingsKeepEverythingIncludingDuplicatesAndColors() {
        val items = listOf(
            danmaku(0, "顶部", Danmaku.Mode.TOP),
            danmaku(100, "滚动"),
            danmaku(200, "滚动"),
            danmaku(300, "彩色", color = 0xFF6699),
        )

        assertEquals(items, DanmakuFilter.apply(items, DanmakuSettings()))
    }

    @Test
    fun eachModeIsBlockedOnlyByItsOwnType() {
        val items = listOf(
            danmaku(0, "顶部", Danmaku.Mode.TOP),
            danmaku(1, "底部", Danmaku.Mode.BOTTOM),
            danmaku(2, "滚动", Danmaku.Mode.SCROLL),
        )

        val topBlocked = DanmakuFilter.apply(items, DanmakuSettings(blockedTypes = setOf(DanmakuBlockType.TOP)))
        assertEquals(listOf("底部", "滚动"), topBlocked.map { it.text })

        val bottomBlocked = DanmakuFilter.apply(items, DanmakuSettings(blockedTypes = setOf(DanmakuBlockType.BOTTOM)))
        assertEquals(listOf("顶部", "滚动"), bottomBlocked.map { it.text })

        val scrollBlocked = DanmakuFilter.apply(items, DanmakuSettings(blockedTypes = setOf(DanmakuBlockType.SCROLL)))
        assertEquals(listOf("顶部", "底部"), scrollBlocked.map { it.text })
    }

    @Test
    fun colorfulBlocksEverythingExceptDefaultWhite() {
        val items = listOf(
            danmaku(0, "白色", color = 0xFFFFFF),
            danmaku(1, "红色", color = 0xFF0000),
            danmaku(2, "黑色", color = 0x000000),
        )

        val filtered = DanmakuFilter.apply(items, DanmakuSettings(blockedTypes = setOf(DanmakuBlockType.COLORFUL)))

        assertEquals(listOf("白色"), filtered.map { it.text })
    }

    @Test
    fun repeatKeepsOnlyTheEarliestCommentWithSameText() {
        val items = listOf(
            danmaku(0, "前方高能"),
            danmaku(500, "别的弹幕"),
            danmaku(1_000, "前方高能"),
            danmaku(1_500, "前方高能"),
        )

        val filtered = DanmakuFilter.apply(items, DanmakuSettings(blockedTypes = setOf(DanmakuBlockType.REPEAT)))

        assertEquals(listOf("前方高能", "别的弹幕"), filtered.map { it.text })
        assertEquals(0L, filtered.first().timeMs)
    }

    @Test
    fun combinedTypesFilterInASinglePass() {
        val items = listOf(
            danmaku(0, "顶部", Danmaku.Mode.TOP),
            danmaku(1, "滚动一"),
            danmaku(2, "滚动一", color = 0xFF0000),
            danmaku(3, "滚动二"),
        )

        val filtered = DanmakuFilter.apply(
            items,
            DanmakuSettings(
                blockedTypes = setOf(DanmakuBlockType.TOP, DanmakuBlockType.COLORFUL, DanmakuBlockType.REPEAT),
            ),
        )

        assertEquals(listOf("滚动一", "滚动二"), filtered.map { it.text })
    }

    @Test
    fun emptyInputAndEmptyBlockSetReturnInputAsIs() {
        val items = listOf(danmaku(0, "弹幕"))

        assertTrue(DanmakuFilter.apply(emptyList(), DanmakuSettings()).isEmpty())
        assertEquals(items, DanmakuFilter.apply(items, DanmakuSettings(blockedTypes = emptySet())))
    }

    private fun danmaku(
        timeMs: Long,
        text: String,
        mode: Danmaku.Mode = Danmaku.Mode.SCROLL,
        color: Int = 0xFFFFFF,
    ) = Danmaku(
        timeMs = timeMs,
        mode = mode,
        fontSize = 25,
        color = color,
        text = text,
    )
}
