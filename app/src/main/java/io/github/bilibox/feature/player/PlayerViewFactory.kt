package io.github.bilibox.feature.player

import android.content.Context
import android.content.ContextWrapper
import android.view.LayoutInflater
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.*
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import io.github.bilibox.R

internal tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun createTexturePlayerView(context: Context, player: ExoPlayer): PlayerView = (LayoutInflater.from(context).inflate(R.layout.video_player_view, null) as PlayerView).apply {
    this.player = player
    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
    keepScreenOn = true
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun createCompatiblePlayer(context: Context): ExoPlayer {
    val renderersFactory = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
    return ExoPlayer.Builder(context, renderersFactory).build()
}
