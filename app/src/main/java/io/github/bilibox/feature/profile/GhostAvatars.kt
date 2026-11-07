package io.github.bilibox.feature.profile

import io.github.bilibox.R
import kotlin.random.Random

/**
 * 未登录时的默认头像候选。
 *
 * 只用一张「B」字太单调，这里备了 24 只幽灵插画：每张自带渐变底色，
 * 圆形容器裁切后就是一枚完整头像。
 *
 * 抽取时机由应用根组合决定（见 AppNavigation）：一次运行内只抽一次，
 * 切页、退后台、转屏都沿用同一只，完全退出后重新启动才会换。
 */
internal val GhostAvatarDrawables: List<Int> = listOf(
    R.drawable.avatar_ghost_01,
    R.drawable.avatar_ghost_02,
    R.drawable.avatar_ghost_03,
    R.drawable.avatar_ghost_04,
    R.drawable.avatar_ghost_05,
    R.drawable.avatar_ghost_06,
    R.drawable.avatar_ghost_07,
    R.drawable.avatar_ghost_08,
    R.drawable.avatar_ghost_09,
    R.drawable.avatar_ghost_10,
    R.drawable.avatar_ghost_11,
    R.drawable.avatar_ghost_12,
    R.drawable.avatar_ghost_13,
    R.drawable.avatar_ghost_14,
    R.drawable.avatar_ghost_15,
    R.drawable.avatar_ghost_16,
    R.drawable.avatar_ghost_17,
    R.drawable.avatar_ghost_18,
    R.drawable.avatar_ghost_19,
    R.drawable.avatar_ghost_20,
    R.drawable.avatar_ghost_21,
    R.drawable.avatar_ghost_22,
    R.drawable.avatar_ghost_23,
    R.drawable.avatar_ghost_24,
)

/** 随机抽一只幽灵头像；只应在应用启动时调用一次。[random] 供测试固定结果。 */
internal fun randomGhostAvatar(random: Random = Random.Default): Int = GhostAvatarDrawables[random.nextInt(GhostAvatarDrawables.size)]
