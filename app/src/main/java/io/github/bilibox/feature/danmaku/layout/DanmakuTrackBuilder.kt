package io.github.bilibox.feature.danmaku.layout

import io.github.bilibox.data.danmaku.Danmaku

/**
 * 一条弹幕在时间轴上的排布结果。
 *
 * [lane] 从显示区域顶部开始计数；滚动弹幕和底部固定弹幕各自维护轨道，绘制时
 * 再按模式换算成具体坐标。所有时间都是相对视频起点的毫秒数。
 */
internal data class DanmakuTrack(
    val item: Danmaku,
    val lane: Int,
    val startMs: Long,
    val endMs: Long,
    val widthPx: Float,
    val speedPxPerSecond: Float,
) {
    fun isVisibleAt(positionMs: Long): Boolean = positionMs >= startMs && positionMs < endMs

    /** 滚动弹幕在 [positionMs] 时的左边缘；固定弹幕不使用这个值。 */
    fun leftEdgeAt(positionMs: Long, viewportWidthPx: Float): Float = viewportWidthPx - (positionMs - startMs) / 1_000f * speedPxPerSecond
}

/**
 * 为当前视频窗口分配弹幕轨道。
 *
 * 滚动弹幕按恒定像素速度移动，而不是固定总时长，因此长弹幕不会追尾短弹幕。
 * 新弹幕只有在上一条的尾部完全进入屏幕后才复用同一轨道；轨道都忙时直接丢弃，
 * 避免密集弹幕叠在一起。顶部/底部弹幕各用 4 秒的时间槽，和网页端一致。
 */
internal object DanmakuTrackBuilder {
    const val SCROLL_SPEED_DP_PER_SECOND = 90f
    const val FIXED_DURATION_MS = 4_000L
    private const val LANE_GAP_DP = 24f
    private const val MIN_LANE_COUNT = 1
    private const val MAX_LANE_COUNT = 24

    fun build(
        items: List<Danmaku>,
        viewportWidthPx: Float,
        areaHeightPx: Float,
        laneHeightPx: Float,
        density: Float,
        speedScale: Float = 1f,
        textWidthPx: (Danmaku) -> Float,
    ): List<DanmakuTrack> {
        if (items.isEmpty() || viewportWidthPx <= 0f || areaHeightPx <= 0f || laneHeightPx <= 0f) {
            return emptyList()
        }
        val laneCount = (areaHeightPx / laneHeightPx).toInt()
            .coerceIn(MIN_LANE_COUNT, MAX_LANE_COUNT)
        val speedPxPerSecond = SCROLL_SPEED_DP_PER_SECOND * density * speedScale.coerceIn(.5f, 1.8f)
        val gapPx = LANE_GAP_DP * density
        val scrollFreeAt = LongArray(laneCount) { Long.MIN_VALUE }
        val topFreeAt = LongArray(laneCount) { Long.MIN_VALUE }
        val bottomFreeAt = LongArray(laneCount) { Long.MIN_VALUE }
        val result = ArrayList<DanmakuTrack>(items.size)

        items.sortedBy { it.timeMs }.forEach { item ->
            val widthPx = textWidthPx(item).coerceAtLeast(1f)
            when (item.mode) {
                Danmaku.Mode.SCROLL -> {
                    val lane = scrollFreeAt.indexOfFirst { freeAt -> freeAt <= item.timeMs }
                    if (lane < 0) return@forEach
                    val durationMs = (((viewportWidthPx + widthPx) / speedPxPerSecond) * 1_000f).toLong()
                    // 上一条的右边缘离开起点后，新弹幕才允许进入同一轨道。
                    val clearMs = (((widthPx + gapPx) / speedPxPerSecond) * 1_000f).toLong()
                    scrollFreeAt[lane] = item.timeMs + clearMs
                    result += DanmakuTrack(
                        item = item,
                        lane = lane,
                        startMs = item.timeMs,
                        endMs = item.timeMs + durationMs,
                        widthPx = widthPx,
                        speedPxPerSecond = speedPxPerSecond,
                    )
                }

                Danmaku.Mode.TOP -> {
                    val lane = topFreeAt.indexOfFirst { freeAt -> freeAt <= item.timeMs }
                    if (lane < 0) return@forEach
                    topFreeAt[lane] = item.timeMs + FIXED_DURATION_MS
                    result += DanmakuTrack(
                        item = item,
                        lane = lane,
                        startMs = item.timeMs,
                        endMs = item.timeMs + FIXED_DURATION_MS,
                        widthPx = widthPx,
                        speedPxPerSecond = 0f,
                    )
                }

                Danmaku.Mode.BOTTOM -> {
                    val lane = bottomFreeAt.indexOfFirst { freeAt -> freeAt <= item.timeMs }
                    if (lane < 0) return@forEach
                    bottomFreeAt[lane] = item.timeMs + FIXED_DURATION_MS
                    result += DanmakuTrack(
                        item = item,
                        lane = lane,
                        startMs = item.timeMs,
                        endMs = item.timeMs + FIXED_DURATION_MS,
                        widthPx = widthPx,
                        speedPxPerSecond = 0f,
                    )
                }
            }
        }
        return result.sortedBy { it.startMs }
    }
}
