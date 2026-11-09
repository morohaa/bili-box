package io.github.bilibox.data.bili

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BiliPreviewTest {
    @Test
    fun previewUsesSmallCompatibleVideoAndReliableCdn() {
        val data = PlayData(
            dash = Dash(
                video = listOf(
                    DashVideo(id = 80, baseUrl = "https://upos-a.bilivideo.com/high", codecs = "avc1"),
                    DashVideo(id = 16, baseUrl = "https://upos-a.bilivideo.com/av1", codecs = "av01"),
                    DashVideo(
                        id = 32,
                        baseUrl = "https://bad.mcdn.bilivideo.cn/low",
                        backupUrl = listOf("https://upos-a.bilivideo.com/low"),
                        codecs = "avc1",
                    ),
                )
            )
        )
        assertEquals("https://upos-a.bilivideo.com/low", BiliPlayback.previewVideoUrl(data))
    }

    @Test
    fun missingVideoHasNoPreviewAndLegacyUrlIsSupported() {
        assertNull(BiliPlayback.previewVideoUrl(PlayData()))
        val data = PlayData(dash = Dash(video = listOf(DashVideo(id = 16, baseUrlLegacy = "https://upos-a.bilivideo.com/legacy"))))
        assertEquals("https://upos-a.bilivideo.com/legacy", BiliPlayback.previewVideoUrl(data))
    }
}
