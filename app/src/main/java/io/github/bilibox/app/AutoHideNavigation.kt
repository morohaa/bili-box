package io.github.bilibox.app

import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.motionEventSpy
import androidx.compose.ui.platform.LocalAccessibilityManager
import kotlinx.coroutines.delay

@Stable
internal class AutoHideNavigationState {
    var visible by mutableStateOf(true)
        private set
    var pointerDown by mutableStateOf(false)
        private set
    var interactionVersion by mutableIntStateOf(0)
        private set

    fun show() {
        visible = true
        interactionVersion++
    }

    fun updatePointerDown(pressed: Boolean) {
        if (pointerDown != pressed) {
            pointerDown = pressed
            interactionVersion++
        }
    }

    fun hide() {
        visible = false
    }
}

@Composable
internal fun rememberAutoHideNavigationState(enabled: Boolean, resetKey: Any?): AutoHideNavigationState {
    val state = remember(enabled, resetKey) { AutoHideNavigationState() }
    val timeout = LocalAccessibilityManager.current?.calculateRecommendedTimeoutMillis(
        originalTimeoutMillis = 3_000L,
        containsControls = true,
    ) ?: 3_000L
    LaunchedEffect(state, enabled, state.visible, state.pointerDown, state.interactionVersion, timeout) {
        if (enabled && state.visible && !state.pointerDown) {
            delay(timeout)
            state.hide()
        }
    }
    return state
}

/** Observe touches before children consume them, without interfering with clicks or scrolling. */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.navigationActivity(state: AutoHideNavigationState, enabled: Boolean): Modifier = if (!enabled) {
    this
} else {
    motionEventSpy { event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> state.updatePointerDown(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> state.updatePointerDown(false)
        }
    }
}
