package io.github.bilibox.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.bilibox.core.ui.theme.BiliBoxTheme
import io.github.bilibox.data.localvideo.LocalVideoQuickFilter

@Preview(widthDp = 412, heightDp = 800)
@Composable
internal fun LibraryHeaderControlsPreview() {
    val accent = Color(0xFF29B6F6)
    BiliBoxTheme(darkTheme = false, accent = accent, dynamicColor = false) {
        Column(Modifier.fillMaxSize().background(Color(0xFFEFF1F5))) {
            HeaderExample("文件夹", dark = false, accent = accent, folder = true)
            HeaderExample("根目录", dark = false, accent = accent, folder = false)
            HeaderExample("深色文件夹", dark = true, accent = accent, folder = true)
        }
    }
}

@Composable
private fun HeaderExample(label: String, dark: Boolean, accent: Color, folder: Boolean) {
    var inFolder by remember { mutableStateOf(folder) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<LocalVideoQuickFilter?>(null) }
    var settingsOpened by remember { mutableStateOf(false) }
    Text(if (settingsOpened) "$label · 已打开设置" else label, Modifier.padding(16.dp), color = Color(0xFF555C64))
    CompositionLocalProvider(LocalLibraryPalette provides libraryPalette(dark, accent)) {
        LibraryTopBar(
            inFolder = inFolder,
            searchOpen = searchOpen,
            query = query,
            breadcrumbs = if (inFolder) listOf("MiShare") else emptyList(),
            filter = filter,
            onBack = { inFolder = false },
            onSearchOpen = { searchOpen = true },
            onSearchClose = {
                searchOpen = false
                query = ""
            },
            onQueryChange = { query = it },
            onSettings = { settingsOpened = true },
            onRoot = { inFolder = false },
            onBreadcrumb = {},
            onClearFilter = { filter = null },
            onSelectFilter = { filter = it },
        )
    }
}
