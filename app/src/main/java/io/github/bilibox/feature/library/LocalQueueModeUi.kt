package io.github.bilibox.feature.library

import io.github.bilibox.core.playback.LocalQueueMode
import io.github.bilibox.core.ui.component.AppIcon

internal val LocalQueueMode.label: String
    get() = when (this) {
        LocalQueueMode.LIST_LOOP -> "列表循环"
        LocalQueueMode.SINGLE_LOOP -> "单个循环"
        LocalQueueMode.STOP_AFTER -> "播完暂停"
        LocalQueueMode.SHUFFLE -> "随机播放"
    }

internal val LocalQueueMode.description: String
    get() = when (this) {
        LocalQueueMode.LIST_LOOP -> "播完自动下一个，列表结束后从头继续"
        LocalQueueMode.SINGLE_LOOP -> "循环播放当前视频"
        LocalQueueMode.STOP_AFTER -> "播放完当前视频后停止"
        LocalQueueMode.SHUFFLE -> "随机播放同文件夹里的下一个视频"
    }

internal val LocalQueueMode.icon: AppIcon
    get() = when (this) {
        LocalQueueMode.LIST_LOOP -> AppIcon.LOOP
        LocalQueueMode.SINGLE_LOOP -> AppIcon.LOOP_ONE
        LocalQueueMode.STOP_AFTER -> AppIcon.WAVEFORM
        LocalQueueMode.SHUFFLE -> AppIcon.SHUFFLE
    }
