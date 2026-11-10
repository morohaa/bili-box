package io.github.bilibox.feature.player

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerSwipeGestureTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun videoSurfacePreviewsThenSeeksOnReleaseAndPreservesPausedState() {
        lateinit var player: ExoPlayer
        var seeks = 0
        compose.setContent {
            val context = LocalContext.current
            player = remember {
                val renderers = DefaultRenderersFactory(context).setMediaCodecSelector { mime, secure, tunneling ->
                    MediaCodecSelector.DEFAULT.getDecoderInfos(mime, secure, tunneling).sortedBy { !it.softwareOnly }
                }
                ExoPlayer.Builder(context, renderers).build().apply {
                    setMediaItem(MediaItem.fromUri(createSeekPreviewFixture(context).toURI().toString()))
                    prepare()
                    addListener(object : Player.Listener {
                        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                            if (reason == Player.DISCONTINUITY_REASON_SEEK) seeks++
                        }
                    })
                }
            }
            DisposableEffect(player) { onDispose { player.release() } }
            VideoPlayerScreen(
                player = player,
                title = "横滑测试",
                defaultDurationMs = 30_000L,
                sourceLabel = "本地视频",
                commentMessage = "测试",
                onBack = {},
                onRetry = {},
                details = { Text("视频详情") },
            )
        }
        compose.waitUntil(15_000) { compose.runOnIdle { player.playbackState == Player.STATE_READY } }
        compose.waitForIdle()
        val surface = compose.onNodeWithTag("player-touch-surface")
        surface.performTouchInput {
            down(Offset(width * .25f, center.y))
            moveTo(Offset(width * .65f, center.y), 200)
        }
        compose.onNodeWithTag("player-swipe-seek-feedback").assertExists()
        compose.runOnIdle { assertEquals(0, seeks) }
        surface.performTouchInput { up() }
        compose.onNodeWithTag("player-swipe-seek-feedback").assertDoesNotExist()
        val forwardPosition = compose.runOnIdle {
            assertEquals(1, seeks)
            assertTrue(!player.playWhenReady)
            player.currentPosition.also { assertTrue(it > 5_000L) }
        }
        surface.performTouchInput {
            swipe(Offset(width * .65f, center.y), Offset(width * .4f, center.y))
        }
        compose.runOnIdle {
            assertEquals(2, seeks)
            assertTrue(player.currentPosition < forwardPosition)
            assertTrue(!player.playWhenReady)
        }
        surface.performTouchInput {
            down(Offset(width * .25f, center.y))
            moveTo(Offset(width * .65f, center.y), 200)
            cancel()
        }
        compose.runOnIdle { assertEquals(2, seeks) }
    }

    @Test
    fun horizontalSwipesSeekInBothDirectionsWithoutChangingVolumeOrTogglingControls() {
        val deltas = mutableListOf<Float>()
        var starts = 0
        var finishes = 0
        var verticalStarts = 0
        var toggles = 0
        compose.setContent {
            PlayerTouchSurface(
                locked = false,
                onToggleControls = { toggles++ },
                onTogglePlayback = { toggles++ },
                onVerticalGestureStart = { verticalStarts++ },
                horizontalGesturesEnabled = true,
                onHorizontalGestureStart = { starts++ },
                onHorizontalSwipe = { deltas.add(it) },
                onHorizontalGestureEnd = { finishes++ },
                modifier = Modifier.size(320.dp, 180.dp).testTag("touch"),
            )
        }
        compose.onNodeWithTag("touch").performTouchInput {
            swipe(Offset(width * .2f, center.y), Offset(width * .8f, center.y + 5f))
        }
        compose.runOnIdle {
            assertTrue(deltas.sum() > 0f)
            deltas.clear()
        }
        compose.onNodeWithTag("touch").performTouchInput {
            swipe(Offset(width * .8f, center.y), Offset(width * .2f, center.y - 5f))
        }
        compose.runOnIdle {
            assertTrue(deltas.sum() < 0f)
            assertEquals(2, starts)
            assertEquals(2, finishes)
            assertEquals(0, verticalStarts)
            assertEquals(0, toggles)
        }
    }

    @Test
    fun lockingDuringSwipeCancelsPreviewAndBlocksFurtherSwipes() {
        var locked by mutableStateOf(false)
        var starts = 0
        var finishes = 0
        var cancellations = 0
        compose.setContent {
            PlayerTouchSurface(
                locked = locked,
                onToggleControls = {},
                onTogglePlayback = {},
                horizontalGesturesEnabled = true,
                onHorizontalGestureStart = { starts++ },
                onHorizontalGestureEnd = { finishes++ },
                onHorizontalGestureCancel = { cancellations++ },
                modifier = Modifier.size(320.dp, 180.dp).testTag("touch"),
            )
        }
        compose.onNodeWithTag("touch").performTouchInput {
            down(Offset(width * .2f, center.y))
            moveTo(Offset(width * .6f, center.y))
        }
        compose.runOnIdle { locked = true }
        compose.onNodeWithTag("touch").performTouchInput { up() }
        compose.onNodeWithTag("touch").performTouchInput {
            swipe(Offset(width * .2f, center.y), Offset(width * .8f, center.y))
        }
        compose.runOnIdle {
            assertEquals(1, starts)
            assertEquals(1, cancellations)
            assertEquals(0, finishes)
        }
    }

    @Test
    fun cancelledSwipeNeverCommitsSeek() {
        var finishes = 0
        var cancellations = 0
        compose.setContent {
            PlayerTouchSurface(
                locked = false,
                onToggleControls = {},
                onTogglePlayback = {},
                horizontalGesturesEnabled = true,
                onHorizontalGestureEnd = { finishes++ },
                onHorizontalGestureCancel = { cancellations++ },
                modifier = Modifier.size(320.dp, 180.dp).testTag("touch"),
            )
        }
        compose.onNodeWithTag("touch").performTouchInput {
            down(Offset(width * .2f, center.y))
            moveTo(Offset(width * .6f, center.y))
            cancel()
        }
        compose.runOnIdle {
            assertEquals(0, finishes)
            assertEquals(1, cancellations)
        }
    }

    @Test
    fun longPressOwnsSubsequentMovementUntilRelease() {
        var boosts = 0
        var releases = 0
        var seeks = 0
        var toggles = 0
        compose.setContent {
            PlayerTouchSurface(
                locked = false,
                onToggleControls = { toggles++ },
                onTogglePlayback = { toggles++ },
                horizontalGesturesEnabled = true,
                onHorizontalGestureStart = { seeks++ },
                onSpeedBoostStart = { boosts++ },
                onSpeedBoostEnd = { releases++ },
                modifier = Modifier.size(320.dp, 180.dp).testTag("touch"),
            )
        }
        compose.onNodeWithTag("touch").performTouchInput {
            down(center)
            advanceEventTime(700)
            moveTo(Offset(width * .8f, center.y))
            up()
        }
        compose.runOnIdle {
            assertEquals(1, boosts)
            assertEquals(1, releases)
            assertEquals(0, seeks)
            assertEquals(0, toggles)
        }
    }
}
