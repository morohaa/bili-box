package io.github.bilibox.data.bili

import org.junit.Assert.assertEquals
import org.junit.Test

class WbiSignerTest {
    private val img = "7cd084941338484aae1ad9425b84077c"
    private val sub = "4932caff0ff746eab6f01bf08b70ac45"

    @Test
    fun `mixin key matches known vector`() {
        assertEquals("ea1db124af3c7062474693fa704f4ff8", WbiSigner.mixinKey(img, sub))
    }

    @Test
    fun `extracts key from image url`() {
        assertEquals(img, WbiSigner.extractKey("https://i0.hdslb.com/bfs/wbi/$img.png"))
    }

    @Test
    fun `signature matches known vector`() {
        val signed = WbiSigner.sign(
            params = mapOf(
                "bvid" to "BV1GJ411x7h7",
                "cid" to "137649199",
                "qn" to "127",
                "fnval" to "4048",
                "fnver" to "0",
                "fourk" to "1",
            ),
            mixinKey = WbiSigner.mixinKey(img, sub),
            wts = 1_700_000_000L,
        )
        assertEquals(
            "bvid=BV1GJ411x7h7&cid=137649199&fnval=4048&fnver=0&fourk=1&qn=127" +
                "&wts=1700000000&w_rid=2cbb9ff3f8e13e2106af90c229a73c57",
            signed,
        )
    }
}