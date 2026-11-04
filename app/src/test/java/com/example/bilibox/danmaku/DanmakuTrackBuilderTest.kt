package com.example.bilibox.danmaku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuTrackBuilderTest {
    @Test
    fun denseScrollingDanmakuGetsSeparateLanesAndKeepsChronologicalOrder() {
        val items = listOf(
            danmaku(0, "第一条很长的弹幕"),
            danmaku(100, "第二条"),
            danmaku(200, "第三条"),
        )

        val tracks = build(items, width = 1_000f, areaHeight = 200f, laneHeight = 40f)

        assertEquals(3, tracks.size)
        assertEquals(3, tracks.map { it.lane }.distinct().size)
        assertTrue(tracks[0].startMs <= tracks[1].startMs)
        assertTrue(tracks[1].startMs <= tracks[2].startMs)
        tracks.forEach { assertTrue(it.endMs > it.startMs) }
    }

    @Test
    fun scrollingLaneCanBeReusedOnlyAfterPreviousTailClearsTheSpawnPoint() {
        val items = listOf(danmaku(0, "短"), danmaku(10, "紧随其后"))
        val tracks = build(items, width = 1_000f, areaHeight = 40f, laneHeight = 40f)

        // Only one lane exists, so the second item is dropped instead of overlapping.
        assertEquals(1, tracks.size)
        assertEquals(0, tracks[0].lane)
    }

    @Test
    fun topAndBottomCommentsUseIndependentLanesAndFourSecondSlots() {
        val items = listOf(
            danmaku(0, "顶部一", mode = Danmaku.Mode.TOP),
            danmaku(100, "底部一", mode = Danmaku.Mode.BOTTOM),
            danmaku(2_000, "顶部二", mode = Danmaku.Mode.TOP),
            danmaku(4_500, "顶部三", mode = Danmaku.Mode.TOP),
        )

        val tracks = build(items, width = 1_000f, areaHeight = 120f, laneHeight = 40f)

        val top = tracks.filter { it.item.mode == Danmaku.Mode.TOP }
        val bottom = tracks.filter { it.item.mode == Danmaku.Mode.BOTTOM }
        assertEquals(3, top.size)
        assertEquals(1, bottom.size)
        assertEquals(0, bottom.single().lane)
        // First two top comments overlap in time, so they take separate lanes.
        assertEquals(setOf(0, 1), top.take(2).map { it.lane }.toSet())
        // The 4.5s one starts after the 4s slot of the 0s comment, so lane 0 is free again.
        assertEquals(0, top.last().lane)
        assertEquals(DanmakuTrackBuilder.FIXED_DURATION_MS, top.last().endMs - top.last().startMs)
    }

    @Test
    fun areaLimitsLaneCountAndEmptyInputIsIgnored() {
        val items = (0..5).map { danmaku(it * 10L, "弹幕$it") }
        val tracks = build(items, width = 1_000f, areaHeight = 80f, laneHeight = 40f)

        assertEquals(2, tracks.map { it.lane }.distinct().size)
        assertTrue(build(emptyList(), 1_000f, 200f, 40f).isEmpty())
        assertTrue(build(items, 0f, 200f, 40f).isEmpty())
    }

    private fun danmaku(
        timeMs: Long,
        text: String,
        mode: Danmaku.Mode = Danmaku.Mode.SCROLL,
    ) = Danmaku(
        timeMs = timeMs,
        mode = mode,
        fontSize = 25,
        color = 0xFFFFFF,
        text = text,
    )

    private fun build(
        items: List<Danmaku>,
        width: Float,
        areaHeight: Float,
        laneHeight: Float,
    ): List<DanmakuTrack> = DanmakuTrackBuilder.build(
        items = items,
        viewportWidthPx = width,
        areaHeightPx = areaHeight,
        laneHeightPx = laneHeight,
        density = 1f,
        textWidthPx = { it.text.length * 24f },
    )
}
