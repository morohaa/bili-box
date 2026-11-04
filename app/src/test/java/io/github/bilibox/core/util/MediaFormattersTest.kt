package io.github.bilibox.core.util

import org.junit.Assert.*
import org.junit.Test

/**
 * 搜索时长格式化等纯函数的回归测试。
 */
class MediaFormattersTest {
    @Test
    fun searchDurationConvertsUnboundedMinutesToHours() {
        assertEquals("75:11:45", formatSearchDuration("4511:45"))
        assertEquals("15:08", formatSearchDuration("15:8"))
        assertEquals("--:--", formatSearchDuration("--:--"))
    }
}
