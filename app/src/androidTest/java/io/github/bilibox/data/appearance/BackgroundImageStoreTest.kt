package io.github.bilibox.data.appearance

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import io.github.bilibox.core.ui.theme.BackgroundPage
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundImageStoreTest {
    @Test
    fun savingAndClearingImagesDoesNotAffectOtherPages() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(base.cacheDir, "background-test-${System.nanoTime()}")
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir(): File = directory
        }
        val images = listOf(Color.RED, Color.GREEN, Color.BLUE).map { color ->
            Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        }
        try {
            BackgroundPage.entries.forEachIndexed { index, page ->
                assertTrue(BackgroundImageStore(context, page).save(images[index]))
            }
            BackgroundPage.entries.forEachIndexed { index, page ->
                val loaded = BackgroundImageStore(context, page).load()
                assertNotNull(loaded)
                val pixel = loaded!!.getPixel(8, 8)
                val expected = images[index].getPixel(8, 8)
                assertEquals(Color.red(expected).toDouble(), Color.red(pixel).toDouble(), 3.0)
                assertEquals(Color.green(expected).toDouble(), Color.green(pixel).toDouble(), 3.0)
                assertEquals(Color.blue(expected).toDouble(), Color.blue(pixel).toDouble(), 3.0)
                loaded.recycle()
            }
            BackgroundImageStore(context, BackgroundPage.VIDEO).clear()
            assertNull(BackgroundImageStore(context, BackgroundPage.VIDEO).load())
            listOf(BackgroundPage.HOME, BackgroundPage.PROFILE).forEach { page ->
                val loaded = BackgroundImageStore(context, page).load()
                assertNotNull(loaded)
                loaded?.recycle()
            }
        } finally {
            images.forEach { it.recycle() }
            directory.deleteRecursively()
        }
    }
}
