package io.github.bilibox.feature.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlin.math.abs
import kotlinx.coroutines.withTimeoutOrNull

@Composable
internal fun PlayerTouchSurface(
    locked: Boolean,
    onToggleControls: () -> Unit,
    onTogglePlayback: () -> Unit,
    verticalGesturesEnabled: Boolean = true,
    onVerticalGestureStart: (PlayerGestureSide) -> Unit = {},
    onVerticalSwipe: (PlayerGestureSide, Float) -> Unit = { _, _ -> },
    onVerticalGestureEnd: () -> Unit = {},
    speedBoostEnabled: Boolean = true,
    onSpeedBoostStart: () -> Unit = {},
    onSpeedBoostEnd: () -> Unit = {},
    horizontalGesturesEnabled: Boolean = false,
    onHorizontalGestureStart: () -> Unit = {},
    onHorizontalSwipe: (Float) -> Unit = {},
    onHorizontalGestureEnd: () -> Unit = {},
    onHorizontalGestureCancel: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val toggleControls by rememberUpdatedState(onToggleControls)
    val togglePlayback by rememberUpdatedState(onTogglePlayback)
    val startVerticalGesture by rememberUpdatedState(onVerticalGestureStart)
    val verticalSwipe by rememberUpdatedState(onVerticalSwipe)
    val endVerticalGesture by rememberUpdatedState(onVerticalGestureEnd)
    val startSpeedBoost by rememberUpdatedState(onSpeedBoostStart)
    val endSpeedBoost by rememberUpdatedState(onSpeedBoostEnd)
    val haptics = LocalHapticFeedback.current
    val startHorizontalGesture by rememberUpdatedState(onHorizontalGestureStart)
    val horizontalSwipe by rememberUpdatedState(onHorizontalSwipe)
    val endHorizontalGesture by rememberUpdatedState(onHorizontalGestureEnd)
    val cancelHorizontalGesture by rememberUpdatedState(onHorizontalGestureCancel)
    // Lock the axis after touch slop so diagonal movement cannot adjust both
    // playback position and brightness / volume during the same gesture.
    val dragModifier = Modifier.pointerInput(locked, verticalGesturesEnabled, horizontalGesturesEnabled) {
        if (locked || (!verticalGesturesEnabled && !horizontalGesturesEnabled)) return@pointerInput
        var gestureSide = PlayerGestureSide.LEFT
        var horizontal: Boolean? = null
        fun cancelDrag() {
            if (horizontal == true) cancelHorizontalGesture()
            if (horizontal == false) endVerticalGesture()
            horizontal = null
        }
        try {
            detectDragGestures(
                onDragStart = { position ->
                    gestureSide = if (position.x < size.width / 2f) PlayerGestureSide.LEFT else PlayerGestureSide.RIGHT
                },
                onDrag = { change, amount ->
                    if (horizontal == null) {
                        horizontal = abs(amount.x) > abs(amount.y)
                        if (horizontal == true && horizontalGesturesEnabled) startHorizontalGesture()
                        if (horizontal == false && verticalGesturesEnabled) startVerticalGesture(gestureSide)
                    }
                    change.consume()
                    if (horizontal == true && horizontalGesturesEnabled && size.width > 0) {
                        horizontalSwipe(amount.x / size.width)
                    } else if (horizontal == false && verticalGesturesEnabled && size.height > 0) {
                        verticalSwipe(gestureSide, amount.y / size.height)
                    }
                },
                onDragEnd = {
                    if (horizontal == true && horizontalGesturesEnabled) endHorizontalGesture()
                    if (horizontal == false && verticalGesturesEnabled) endVerticalGesture()
                    horizontal = null
                },
                onDragCancel = { cancelDrag() },
            )
        } finally {
            cancelDrag()
        }
    }
    // Tap / double tap / long press share one detector: a long press must be able
    // to own the gesture and swallow its release, otherwise letting go would also
    // toggle the chrome. detectTapGestures cannot report the release of a long
    // press, hence the hand-rolled gesture below.
    val tapAndLongPressModifier = Modifier.pointerInput(locked, speedBoostEnabled) {
        awaitEachGesture {
            // Do not steal taps that a button or the seek bar already consumed.
            awaitFirstDown(requireUnconsumed = true)
            var cancelledBeforeLongPress = false
            val upBeforeLongPress = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                waitForUpOrCancellation().also { if (it == null) cancelledBeforeLongPress = true }
            }
            when {
                upBeforeLongPress != null -> {
                    // A short press: keep the previous single / double tap behaviour.
                    val doubleTap = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                        awaitFirstDown(requireUnconsumed = true)
                        waitForUpOrCancellation() != null
                    }
                    if (doubleTap == true) {
                        if (locked) toggleControls() else togglePlayback()
                    } else {
                        toggleControls()
                    }
                }

                cancelledBeforeLongPress -> Unit

                else -> {
                    // The finger stayed down: play at 2x until it is lifted.
                    if (!locked && speedBoostEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        startSpeedBoost()
                        try {
                            // Own the rest of the gesture so no tap fires on release
                            // and a later swipe cannot also change the volume.
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                                if (event.changes.all { !it.pressed }) break
                            }
                        } finally {
                            endSpeedBoost()
                        }
                    }
                }
            }
        }
    }
    Box(modifier.then(dragModifier).then(tapAndLongPressModifier))
}
