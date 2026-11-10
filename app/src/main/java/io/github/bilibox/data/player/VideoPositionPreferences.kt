package io.github.bilibox.data.player

import android.content.Context
import android.content.SharedPreferences

/** 本地和在线视频共用竖屏全屏的位置微调，下次播放时恢复。 */
internal class VideoPositionPreferences(private val preferences: SharedPreferences) {
    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences("player_video_position", Context.MODE_PRIVATE)
    )

    fun load(): Int = preferences.getInt("adjustment_dp", 0).coerceIn(-MAX_ADJUSTMENT_DP, MAX_ADJUSTMENT_DP)

    fun save(adjustmentDp: Int) {
        preferences.edit()
            .putInt("adjustment_dp", adjustmentDp.coerceIn(-MAX_ADJUSTMENT_DP, MAX_ADJUSTMENT_DP))
            .apply()
    }

    companion object {
        const val MAX_ADJUSTMENT_DP = 160
    }
}
