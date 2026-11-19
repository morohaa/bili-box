package io.github.bilibox.feature.player.details

import io.github.bilibox.core.playback.PlaybackMode
import io.github.bilibox.core.ui.component.AppIcon

internal val PlaybackMode.description: String
    get() = when (this) {
        PlaybackMode.LIST_LOOP -> "播完自动下一个，列表结束后从头继续"
        PlaybackMode.SINGLE_LOOP -> "循环播放当前视频"
        PlaybackMode.STOP_AFTER -> "播放完当前视频后停止"
        PlaybackMode.SEQUENTIAL -> "按顺序播放，播完最后一个视频后停止"
        PlaybackMode.SHUFFLE -> "随机播放同文件夹里的下一个视频"
    }

internal val PlaybackMode.icon: AppIcon
    get() = when (this) {
        PlaybackMode.LIST_LOOP -> AppIcon.LOOP
        PlaybackMode.SINGLE_LOOP -> AppIcon.LOOP_ONE
        PlaybackMode.STOP_AFTER -> AppIcon.WAVEFORM
        PlaybackMode.SEQUENTIAL -> AppIcon.PLAY
        PlaybackMode.SHUFFLE -> AppIcon.SHUFFLE
    }
