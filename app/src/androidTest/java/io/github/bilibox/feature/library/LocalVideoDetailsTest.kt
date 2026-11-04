package io.github.bilibox.feature.library

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.exoplayer.ExoPlayer
import io.github.bilibox.data.localvideo.LocalVideo
import io.github.bilibox.feature.player.LocalPlayerDetailActions
import io.github.bilibox.feature.player.PlaybackSleepTimer
import io.github.bilibox.feature.player.PlayerDetailActions
import io.github.bilibox.feature.player.SleepTimerMode
import io.github.bilibox.feature.player.SleepTimerSheet
import io.github.bilibox.feature.player.VideoPlayerScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LocalVideoDetailsTest {
    @get:Rule val compose = createComposeRule()

    private fun video(name: String): LocalVideo = LocalVideo(
        uri = Uri.parse("content://media/external/video/media/${name.hashCode()}"),
        name = name,
        durationMs = 6_000L,
        sizeBytes = 1_200_000L,
        relativePath = "Movies/666",
        bucketName = "666",
        dateModifiedMs = 1_700_000_000_000L,
        width = 1920,
        height = 1080,
    )

    @Test
    fun introShowsSourceActionsAndSwitchesFolderVideos() {
        val first = video("第一堂课.mp4")
        val second = video("第二堂课.mp4")
        var speedOpened = 0
        var timerOpened = 0
        var playbackSheetOpened = 0
        var selected: LocalVideo? = null
        compose.setContent {
            CompositionLocalProvider(
                LocalPlayerDetailActions provides PlayerDetailActions(
                    speed = 1.5f,
                    positionMs = 5_000L,
                    screenshotEnabled = true,
                    openSpeedPanel = { speedOpened++ },
                    openPlaybackModeSheet = { playbackSheetOpened++ },
                    openSleepTimerSheet = { timerOpened++ },
                    sleepTimerActive = false,
                    takeScreenshot = {},
                    seekTo = {},
                )
            ) {
                LocalVideoIntro(
                    video = first,
                    playlist = listOf(first, second),
                    durationMs = 6_000L,
                    resumeMs = 0L,
                    queueMode = LocalQueueMode.STOP_AFTER,
                    playbackByKey = emptyMap(),
                    onShare = {},
                    onSelectVideo = { selected = it },
                    onResumeDismiss = {},
                )
            }
        }

        compose.onNodeWithText("本机文件").assertExists()
        compose.onNodeWithText("1.5x").assertExists()
        compose.onNodeWithText("播完暂停").assertExists()
        compose.onNodeWithText("同文件夹").assertExists()
        compose.onNodeWithText("播放中").assertExists()
        compose.onNodeWithText("第二堂课").performClick()
        compose.runOnIdle { assertEquals(second.uri, selected?.uri) }
        compose.onNodeWithText("1.5x").performClick()
        compose.runOnIdle { assertEquals(1, speedOpened) }
        compose.onNodeWithText("定时关闭").performClick()
        compose.runOnIdle { assertEquals(1, timerOpened) }
        compose.onNodeWithText("播完暂停").performClick()
        compose.runOnIdle { assertEquals(1, playbackSheetOpened) }
    }

    @Test
    fun sleepTimerSheetShowsPresetsContentAndAppliesChoice() {
        lateinit var timer: PlaybackSleepTimer
        compose.setContent {
            timer = remember { PlaybackSleepTimer() }
            SleepTimerSheet(
                timer = timer,
                remainingVideos = 4,
                currentRemainingMs = 300_000L,
                durationMs = 600_000L,
                onDismiss = {},
            )
        }
        compose.onNodeWithText("定时关闭").assertExists()
        compose.onNodeWithText("未开启").assertExists()
        compose.onNodeWithText("按时长").assertExists()
        compose.onNodeWithText("按内容").assertExists()
        compose.onNodeWithText("播完当前视频").assertExists()
        compose.onNodeWithText("播完多个视频").assertExists()
        compose.onNodeWithText("音量渐弱").assertExists()
        compose.onNodeWithText("结束前提醒").assertExists()
        compose.onNodeWithContentDescription("30 分钟定时").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(SleepTimerMode.TIME, timer.mode)
            assertEquals(30, timer.minutes)
        }
    }

    @Test
    fun playbackSheetShowsFourModesAndSelects() {
        var picked: LocalQueueMode? = null
        compose.setContent {
            PlaybackModeSheet(
                selected = LocalQueueMode.LIST_LOOP,
                onDismiss = {},
                onSelect = { picked = it },
            )
        }
        compose.onNodeWithText("播放顺序").assertExists()
        compose.onNodeWithText("列表循环").assertExists()
        compose.onNodeWithText("单个循环").assertExists()
        compose.onNodeWithText("播完暂停").assertExists()
        compose.onNodeWithText("随机播放").performClick()
        compose.runOnIdle { assertEquals(LocalQueueMode.SHUFFLE, picked) }
    }

    @Test
    fun mediaInfoTabListsFileFactsAndCustomPane() {
        val item = video("250228 第三堂课-场景角度切换.mp4")
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                val context = LocalContext.current
                val player = remember { ExoPlayer.Builder(context).build() }
                DisposableEffect(player) { onDispose { player.release() } }
                VideoPlayerScreen(
                    player = player,
                    title = item.name,
                    defaultDurationMs = item.durationMs,
                    sourceLabel = "本地视频",
                    commentMessage = "",
                    detailTabs = listOf("简介", "媒体信息"),
                    sourcePill = true,
                    extraDetailPane = { duration ->
                        LocalVideoMediaInfo(video = item, durationMs = duration)
                    },
                    onBack = {},
                    onRetry = {},
                    details = { Text("简介内容") },
                )
            }
        }
        compose.onNodeWithText("简介内容").assertExists()
        compose.onNodeWithText("媒体信息").performClick()
        // Let the tab underline/content transition settle before asserting the swap.
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithText("简介内容").assertDoesNotExist()
        compose.onNodeWithText("文件名").assertExists()
        compose.onNodeWithText(item.name).assertExists()
        compose.onNodeWithText("内部存储 / Movies / 666 / ${item.name}").assertExists()
        compose.onNodeWithText("1920 × 1080（1080P）").assertExists()
        compose.onNodeWithText("复制文件路径").assertExists()
    }
}