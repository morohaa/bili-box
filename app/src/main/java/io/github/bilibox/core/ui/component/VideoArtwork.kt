package io.github.bilibox.core.ui.component

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun coverBrush(seed: String): Brush {
    val hash = seed.hashCode()
    val hue = ((hash ushr 8) % 360 + 360) % 360
    val secondHue = (hue + 42 + ((hash ushr 3) % 70)) % 360
    return Brush.linearGradient(
        listOf(
            Color.hsl(hue.toFloat(), .46f, .44f),
            Color.hsl(secondHue.toFloat(), .52f, .62f),
        )
    )
}

/** The caller supplies cached state and loading; artwork UI does not own a data store. */
@Composable
internal fun rememberVideoThumbnail(
    key: String,
    cachedThumbnail: Bitmap?,
    loadThumbnail: suspend (Context) -> Bitmap?,
): Bitmap? {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = cachedThumbnail, key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { loadThumbnail(context) }
        }
    }
    return bitmap
}
