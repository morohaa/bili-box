package com.example.bilibox.bili

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

class BiliDanmakuPayloadTest {
    @Test
    fun decodesPlainZlibAndRawDeflatePayloads() {
        val xml = """<i><d p="1,1,25,16777215,0,0,u,1,10">压缩弹幕</d></i>"""
        val bytes = xml.toByteArray(Charsets.UTF_8)

        assertEquals(xml, decodeDanmakuPayload(bytes))
        assertEquals(xml, decodeDanmakuPayload(deflate(bytes, nowrap = true)))
        assertEquals(xml, decodeDanmakuPayload(deflate(bytes, nowrap = false)))
    }

    private fun deflate(bytes: ByteArray, nowrap: Boolean): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, nowrap)
        val output = ByteArrayOutputStream()
        try {
            deflater.setInput(bytes)
            deflater.finish()
            val buffer = ByteArray(1024)
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                output.write(buffer, 0, count)
            }
        } finally {
            deflater.end()
        }
        return output.toByteArray()
    }
}
