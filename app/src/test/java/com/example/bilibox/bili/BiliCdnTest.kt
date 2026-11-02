package com.example.bilibox.bili

import org.junit.Assert.assertEquals
import org.junit.Test

class BiliCdnTest {
    private val pcdnIp = "https://123.6.8.9:4483/upgcxcode/v.m4s"
    private val mcdn = "https://xy1x2x3x4xy.mcdn.bilivideo.cn:8082/v1/resource/v.m4s"
    private val szbdyd = "https://cn-hk-eq-01-07.szbdyd.com/upgcxcode/v.m4s"
    private val upos = "https://upos-sz-mirrorali.bilivideo.com/upgcxcode/v.m4s"
    private val upos2 = "https://upos-sz-mirrorcos.bilivideo.com/upgcxcode/v.m4s"
    private val regional = "https://cn-gdfs-ct-01-12.bilivideo.com/upgcxcode/v.m4s"

    @Test
    fun ranksOfficialMirrorsBeforeRegionalAndPcdn() {
        assertEquals(0, BiliCdn.rank(upos))
        assertEquals(1, BiliCdn.rank(regional))
        assertEquals(2, BiliCdn.rank(mcdn))
        assertEquals(2, BiliCdn.rank(szbdyd))
        assertEquals(2, BiliCdn.rank(pcdnIp))
        assertEquals(2, BiliCdn.rank("not a url"))
    }

    @Test
    fun prioritizeKeepsOriginalOrderWithinTheSameRank() {
        val ordered = BiliCdn.prioritize(listOf(mcdn, regional, upos2, pcdnIp, upos))
        assertEquals(listOf(upos2, upos, regional, mcdn, pcdnIp), ordered)
    }

    @Test
    fun prioritizeDropsBlankAndDuplicateUrls() {
        assertEquals(listOf(upos, mcdn), BiliCdn.prioritize(listOf(mcdn, "", upos, mcdn, " ")))
    }
}
