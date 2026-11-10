package io.github.bilibox.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerVideoOffsetTest {
    @Test
    fun adjustmentOnlyAffectsVerticalFullScreenAndKeepsExistingDefaults() {
        assertEquals(0, playerVideoOffsetDp(false, true, 80))
        assertEquals(0, playerVideoOffsetDp(false, false, -80))
        assertEquals(-32, playerVideoOffsetDp(true, true, 0))
        assertEquals(-88, playerVideoOffsetDp(true, false, 0))
        assertEquals(48, playerVideoOffsetDp(true, true, 80))
        assertEquals(-168, playerVideoOffsetDp(true, false, -80))
    }
}
