package io.github.bilibox.feature.player

import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekPreviewPositionTest {
    @Test
    fun previewStaysAboveBarAndInsidePortraitAndLandscapeWindows() {
        listOf(IntSize(412, 917), IntSize(917, 412)).forEach { window ->
            val anchor = IntRect(18, window.height - 80, window.width - 18, window.height - 56)
            listOf(0f, .5f, 1f).forEach { fraction ->
                val offset = SeekPreviewPositionProvider(fraction, 8, 9)
                    .calculatePosition(anchor, window, LayoutDirection.Ltr, IntSize(136, 99))
                assertTrue(offset.x >= 0 && offset.x + 136 <= window.width)
                assertEquals(anchor.top - 8, offset.y + 99)
            }
        }
    }

    @Test
    fun verySmallWindowStillProducesNonNegativeCoordinates() {
        val offset = SeekPreviewPositionProvider(1f, 8, 9)
            .calculatePosition(IntRect(0, 0, 80, 24), IntSize(80, 50), LayoutDirection.Ltr, IntSize(136, 99))
        assertEquals(0, offset.x)
        assertEquals(0, offset.y)
    }
}
