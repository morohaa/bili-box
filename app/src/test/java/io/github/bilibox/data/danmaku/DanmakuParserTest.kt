package io.github.bilibox.data.danmaku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DanmakuParserTest {
    @Test
    fun parsesModesColorsEntitiesAndSkipsUnsupportedEntries() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <i>
              <d p="1.234,1,25,16777215,0,0,user,11,10">滚动 &amp; 普通</d>
              <d p="2.5,4,25,255,0,0,user,12,10">底部 &lt;固定&gt;</d>
              <d p="3.75,5,30,65280,0,0,user,13,10">顶部 &#39;固定&#39;</d>
              <d p="4,6,25,16777215,0,0,user,14,10">逆向弹幕</d>
              <d p="5,7,25,16777215,0,0,user,15,10">高级弹幕</d>
              <d p="bad,1,25,16777215,0,0,user,16,10">坏时间</d>
              <d p="6,1,25,16777215,0,0,user,17,10">   </d>
            </i>
        """.trimIndent()

        val parsed = DanmakuParser.parse(xml)

        assertEquals(3, parsed.size)
        assertEquals(1_234L, parsed[0].timeMs)
        assertEquals(Danmaku.Mode.SCROLL, parsed[0].mode)
        assertEquals(0xFFFFFF, parsed[0].color)
        assertEquals("滚动 & 普通", parsed[0].text)
        assertEquals(11L, parsed[0].id)
        assertEquals(Danmaku.Mode.BOTTOM, parsed[1].mode)
        assertEquals("底部 <固定>", parsed[1].text)
        assertEquals(Danmaku.Mode.TOP, parsed[2].mode)
        assertEquals("顶部 '固定'", parsed[2].text)
        assertEquals(30, parsed[2].fontSize)
    }

    @Test
    fun clampsLimitsAndHandlesEmptyXml() {
        val xml = (1..10).joinToString("") { index ->
            """<d p="$index,1,25,16777215,0,0,u,$index,10">第 $index 条</d>"""
        }
        assertEquals(3, DanmakuParser.parse(xml, limit = 3).size)
        assertTrue(DanmakuParser.parse("<i></i>").isEmpty())
        assertTrue(DanmakuParser.parse("").isEmpty())
    }
}