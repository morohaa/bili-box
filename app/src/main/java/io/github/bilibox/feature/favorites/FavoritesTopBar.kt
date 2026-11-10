package io.github.bilibox.feature.favorites

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.component.AppGlyphIcon
import io.github.bilibox.core.ui.component.AppIcon
import io.github.bilibox.core.ui.theme.LocalAccentColor
import io.github.bilibox.data.favorites.FavoriteOrder

@Composable
internal fun FavoritesTopBar(state: FavoritesUiState, onBack: () -> Unit, onFilter: (String, FavoriteOrder) -> Unit) {
    var searchOpen by rememberSaveable(state.folder?.id) { mutableStateOf(false) }
    var keyword by rememberSaveable(state.folder?.id, state.keyword) { mutableStateOf(state.keyword) }
    val colors = MaterialTheme.colorScheme
    val accent = LocalAccentColor.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val canSearch = state.folder != null && !state.needsLogin
    val expanded = searchOpen && canSearch
    val reveal by animateFloatAsState(if (expanded) 1f else 0f, tween(320, easing = FastOutSlowInEasing), label = "favoritesSearchReveal")
    val closeSearch: () -> Unit = {
        searchOpen = false
        keyword = state.keyword
        focus.clearFocus()
        keyboard?.hide()
    }
    val submitSearch: () -> Unit = {
        focus.clearFocus()
        keyboard?.hide()
        onFilter(keyword, state.order)
    }
    BackHandler(enabled = expanded, onBack = closeSearch)
    LaunchedEffect(expanded) {
        if (expanded) {
            withFrameNanos { }
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { if (expanded) closeSearch() else onBack() }, modifier = Modifier.semantics { contentDescription = "返回" }) {
            AppGlyphIcon(AppIcon.BACK, colors.onBackground)
        }
        BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            val searchWidth by animateDpAsState(if (expanded) maxWidth else 48.dp, tween(320, easing = FastOutSlowInEasing), label = "favoritesSearchWidth")
            val titleAlpha by animateFloatAsState(if (expanded) 0f else 1f, tween(160, delayMillis = if (expanded) 0 else 120), label = "favoritesTitleAlpha")
            Column(
                Modifier.fillMaxWidth().padding(start = 6.dp, end = if (canSearch) 54.dp else 6.dp).alpha(titleAlpha)
                    .then(if (expanded) Modifier.clearAndSetSemantics { } else Modifier),
            ) {
                Text(state.folder?.title ?: "我的收藏", color = colors.onBackground, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (state.folder == null) "我创建的收藏夹" else "${state.total} 个收藏 · ${if (state.folder.isPublic) "公开" else "私密"}",
                    color = colors.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (canSearch) {
                Box(
                    Modifier.width(searchWidth).height(48.dp).clip(CircleShape)
                        .background(accent.copy(alpha = .08f * reveal))
                        .border(1.dp, accent.copy(alpha = .22f * reveal), CircleShape),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (expanded || reveal > 0f) {
                        Row(Modifier.fillMaxSize().padding(end = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            BasicTextField(
                                value = keyword,
                                onValueChange = { keyword = it },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(color = colors.onBackground, fontSize = 14.sp),
                                cursorBrush = SolidColor(accent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { submitSearch() }),
                                modifier = Modifier.weight(1f).padding(start = 16.dp).alpha(reveal).focusRequester(focusRequester)
                                    .semantics { contentDescription = "搜索此收藏夹" },
                                decorationBox = { input ->
                                    Box {
                                        if (keyword.isEmpty()) Text("搜索此收藏夹", color = colors.onSurfaceVariant, fontSize = 14.sp, maxLines = 1)
                                        input()
                                    }
                                },
                            )
                            if (keyword.isNotEmpty()) {
                                IconButton(onClick = {
                                    keyword = ""
                                    if (state.keyword.isNotEmpty()) onFilter("", state.order)
                                    focusRequester.requestFocus()
                                }, enabled = expanded, modifier = Modifier.size(36.dp).alpha(reveal).semantics { contentDescription = "清除搜索" }) {
                                    AppGlyphIcon(AppIcon.CLOSE, colors.onSurfaceVariant, Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    IconButton(
                        onClick = { if (expanded) submitSearch() else searchOpen = true },
                        modifier = Modifier.size(48.dp).align(Alignment.CenterEnd).semantics {
                            contentDescription = "搜索收藏"
                            stateDescription = if (state.keyword.isBlank()) "未筛选" else "当前搜索：${state.keyword}"
                        },
                    ) {
                        AppGlyphIcon(AppIcon.SEARCH, accent)
                    }
                }
            }
        }
        if (canSearch) FavoritesSortAction(state, onFilter)
    }
}

@Composable
private fun FavoritesSortAction(state: FavoritesUiState, onFilter: (String, FavoriteOrder) -> Unit) {
    var orderOpen by remember(state.folder?.id) { mutableStateOf(false) }
    val accent = LocalAccentColor.current
    Box {
        IconButton(
            onClick = { orderOpen = true },
            modifier = Modifier.semantics {
                contentDescription = "收藏排序"
                stateDescription = orderLabel(state.order)
            }
        ) {
            AppGlyphIcon(AppIcon.SORT, accent)
        }
        DropdownMenu(expanded = orderOpen, onDismissRequest = { orderOpen = false }) {
            FavoriteOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(orderLabel(order)) },
                    trailingIcon = { if (order == state.order) AppGlyphIcon(AppIcon.CHECK, accent) },
                    onClick = {
                        orderOpen = false
                        onFilter(state.keyword, order)
                    },
                )
            }
        }
    }
}

private fun orderLabel(order: FavoriteOrder): String = when (order) {
    FavoriteOrder.SAVED -> "最近收藏"
    FavoriteOrder.PUBLISHED -> "最新发布"
    FavoriteOrder.MOST_PLAYED -> "最多播放"
}
