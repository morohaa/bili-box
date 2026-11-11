package io.github.bilibox.core.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import io.github.bilibox.R

/** 存储 ID 独立于展示文案，旧版或未知值回退到系统默认字体。 */
internal enum class AppFont(val storageId: String, val label: String, val description: String) {
    DEFAULT("default", "默认字体", "跟随系统，清晰自然"),
    NOTO_SERIF("noto_serif", "思源宋体", "经典衬线，沉静雅致"),
    WENKAI("wenkai", "霞鹜文楷", "手写韵味，轻松阅读"),
    ;

    companion object {
        fun fromStorageId(id: String?): AppFont = entries.firstOrNull { it.storageId == id } ?: DEFAULT
    }
}

private val NotoSerifFamily = FontFamily(Font(R.font.noto_serif_sc_regular))
private val WenKaiFamily = FontFamily(Font(R.font.lxgw_wenkai_lite_regular))

internal val AppFont.fontFamily: FontFamily
    get() = when (this) {
        AppFont.DEFAULT -> FontFamily.Default
        AppFont.NOTO_SERIF -> NotoSerifFamily
        AppFont.WENKAI -> WenKaiFamily
    }

/** 供自绘文字和 BasicTextField 使用；普通 Text 由 Material Typography 继承。 */
internal val LocalAppFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }
