package io.github.bilibox.data.danmaku

/**
 * 按 [DanmakuSettings.blockedTypes] 过滤弹幕，对应设置面板里的「画面防挡」。
 *
 * 语义和 B 站网页端保持一致：
 * - 顶部 / 底部 / 滚动：只看弹幕自身的模式；
 * - 彩色：白色以外的弹幕（B 站 XML 用 `0xFFFFFF` 表示默认白色）；
 * - 重复：同一段文字在整个时间轴上只保留最早的一条。
 *
 * 过滤在轨道排布之前完成，被屏蔽的弹幕不会占用轨道，因此屏蔽后画面不会留下空行。
 */
internal object DanmakuFilter {
    /** 默认白色，B 站把没设过颜色的弹幕都写成这个值。 */
    private const val DEFAULT_COLOR = 0xFFFFFF

    fun apply(items: List<Danmaku>, settings: DanmakuSettings): List<Danmaku> {
        if (items.isEmpty() || settings.blockedTypes.isEmpty()) return items
        val seen = if (settings.isBlocked(DanmakuBlockType.REPEAT)) HashSet<String>() else null
        return items.filter { item ->
            if (isBlockedByMode(item, settings)) return@filter false
            if (settings.isBlocked(DanmakuBlockType.COLORFUL) && item.color != DEFAULT_COLOR) {
                return@filter false
            }
            // 输入已经按时间升序排好，因此第一条就是最早出现的那条。
            if (seen != null && !seen.add(item.text)) return@filter false
            true
        }
    }

    private fun isBlockedByMode(item: Danmaku, settings: DanmakuSettings): Boolean = when (item.mode) {
        Danmaku.Mode.TOP -> settings.isBlocked(DanmakuBlockType.TOP)
        Danmaku.Mode.BOTTOM -> settings.isBlocked(DanmakuBlockType.BOTTOM)
        Danmaku.Mode.SCROLL -> settings.isBlocked(DanmakuBlockType.SCROLL)
    }
}
