package io.github.bilibox.data.profile

import org.junit.Assert.assertEquals
import org.junit.Test

/** 头图调节值与壁纸编辑器共用上限：负值归零，超出按 80% / 20px 截断。 */
class BannerAdjustmentsTest {
    @Test
    fun `defaults keep the original header untouched`() {
        val adjustments = BannerAdjustments()

        assertEquals(0, adjustments.darkness)
        assertEquals(0, adjustments.blur)
        assertEquals(adjustments, adjustments.clamped())
    }

    @Test
    fun `clamps darkness and blur into the editor range`() {
        assertEquals(BannerAdjustments(0, 0), BannerAdjustments(-12, -3).clamped())
        assertEquals(BannerAdjustments(80, 20), BannerAdjustments(120, 42).clamped())
    }
}
