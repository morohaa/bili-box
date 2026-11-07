package io.github.bilibox.feature.online

import io.github.bilibox.data.bili.BiliQn
import io.github.bilibox.data.online.OnlineCacheEpisode
import io.github.bilibox.data.online.OnlineCacheFile
import io.github.bilibox.data.online.OnlineCacheIndex
import io.github.bilibox.data.online.onlineCacheAudioQualityLabel
import io.github.bilibox.data.online.onlineCacheDisplayName
import io.github.bilibox.data.online.onlineCacheVideoQualityLabel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineCacheStateTest {
    private fun episode(
        video: OnlineCacheTrackState = OnlineCacheTrackState.None,
        audio: OnlineCacheTrackState = OnlineCacheTrackState.None,
    ) = OnlineCacheEpisodeUi(
        cid = 1L,
        page = 1,
        part = "第1章",
        video = video,
        audio = audio,
    )

    @Test
    fun badgePrefersActiveTaskThenFailureThenCompletedTracks() {
        assertEquals(OnlineCacheBadge.None, episode().badge())
        assertEquals(
            OnlineCacheBadge.Video,
            episode(video = OnlineCacheTrackState.Done).badge(),
        )
        assertEquals(
            OnlineCacheBadge.Audio,
            episode(audio = OnlineCacheTrackState.Done).badge(),
        )
        assertEquals(
            OnlineCacheBadge.Both,
            episode(
                video = OnlineCacheTrackState.Done,
                audio = OnlineCacheTrackState.Done,
            ).badge(),
        )

        assertEquals(
            OnlineCacheBadge.Queued,
            episode(
                video = OnlineCacheTrackState.Queued,
                audio = OnlineCacheTrackState.Done,
            ).badge(),
        )
        assertEquals(
            OnlineCacheBadge.Failed,
            episode(
                video = OnlineCacheTrackState.Failed,
                audio = OnlineCacheTrackState.Done,
            ).badge(),
        )
        assertEquals(
            OnlineCacheBadge.Downloading(progress = .4f, supplement = true),
            episode(
                video = OnlineCacheTrackState.Downloading(.4f),
                audio = OnlineCacheTrackState.Done,
            ).badge(),
        )
        assertEquals(
            OnlineCacheBadge.Downloading(progress = .4f, supplement = false),
            episode(video = OnlineCacheTrackState.Downloading(.4f)).badge(),
        )
    }

    @Test
    fun playingStateIsExposedInTheAccessibilityDescription() {
        val description = episode(video = OnlineCacheTrackState.Done)
            .stateDescription(playing = true)
        assertTrue(description.startsWith("正在播放，"))
        assertTrue(description.contains("视频已缓存"))
    }

    @Test
    fun qualityLabelsCoverTheCacheSheetRows() {
        assertEquals("1080P 超清", onlineCacheVideoQualityLabel(BiliQn.P1080))
        assertEquals("720P 高清", onlineCacheVideoQualityLabel(BiliQn.P720))
        assertEquals("480P 标清", onlineCacheVideoQualityLabel(BiliQn.P480))
        assertEquals("360P 流畅", onlineCacheVideoQualityLabel(BiliQn.P360))
        assertEquals("192K 高品质", onlineCacheAudioQualityLabel(30280))
        assertEquals("132K 标准", onlineCacheAudioQualityLabel(30232))
        assertEquals("64K 流畅", onlineCacheAudioQualityLabel(30216))
    }

    @Test
    fun displayNameStaysReadableAndHasTheExpectedExtension() {
        val name = onlineCacheDisplayName(
            title = "Java 入门 / 01",
            page = 3,
            part = "打开 CMD?",
            extension = "mp4",
        )
        assertTrue(name.endsWith(".mp4"))
        assertTrue(name.contains("Java 入门 _ 01"))
        assertTrue(name.contains("P3"))
        assertTrue(name.contains("打开 CMD_"))
    }

    @Test
    fun indexRoundTripsBothTracks() {
        val index = OnlineCacheIndex(
            bvid = "BV1test",
            episodes = listOf(
                OnlineCacheEpisode(
                    cid = 7L,
                    page = 2,
                    part = "第二集",
                    video = OnlineCacheFile(uri = "content://video/1", displayName = "v.mp4"),
                    audio = OnlineCacheFile(uri = "content://audio/1", displayName = "a.m4a"),
                ),
            ),
        )
        val json = Json { encodeDefaults = true }
        val decoded = json.decodeFromString<OnlineCacheIndex>(json.encodeToString(index))
        assertEquals(index, decoded)
    }
}
