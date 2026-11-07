package io.github.bilibox.feature.profile

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 未登录默认头像候选集：每一款都要是独立可用的 drawable。
 */
class GhostAvatarsTest {
    @Test
    fun everyGhostAvatarIsDistinctAndValid() {
        assertEquals(24, GhostAvatarDrawables.size)
        assertEquals(GhostAvatarDrawables.size, GhostAvatarDrawables.toSet().size)
        assertTrue(GhostAvatarDrawables.all { it != 0 })
    }

    @Test
    fun randomGhostAvatarAlwaysPicksFromTheSet() {
        val random = Random(20261108)
        repeat(200) {
            assertTrue(randomGhostAvatar(random) in GhostAvatarDrawables)
        }
    }
}
