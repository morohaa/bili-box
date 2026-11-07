package io.github.bilibox.feature.appearance

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bilibox.core.ui.theme.AppearanceSettings
import io.github.bilibox.core.ui.theme.HomeBackground
import io.github.bilibox.core.ui.theme.ThemeCatalog
import io.github.bilibox.core.ui.theme.ThemeOption
import io.github.bilibox.core.ui.theme.appearanceBackdrop
import kotlinx.coroutines.delay

// 设计稿：大图 244px、网格 4 列、底部胶囊按钮 48px。
private val HeroHeight = 244.dp
private val ApplyBarGap = 150.dp
private val SheetEasing = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

/**
 * 「主题外观」页：大图预览 + 12 套主题 + 明暗模式与首页背景设置。
 *
 * 所有设置都是即时生效并持久化的：点选主题立刻刷新本页预览与全局强调色，
 * 底部「立即应用」只做一次确认提示（与设计稿的 Toast 行为一致）。
 */
@Composable
internal fun AppearanceScreen(
    appearance: AppearanceSettings,
    darkMode: Boolean,
    onBack: () -> Unit,
    onChange: (AppearanceSettings) -> Unit,
) {
    val palette = remember(darkMode) { appearancePalette(darkMode) }
    val theme = appearance.theme
    val accent = theme.base

    var sheetOpen by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var toastText by remember { mutableStateOf("") }
    var toastVisible by remember { mutableStateOf(false) }
    var toastTick by remember { mutableIntStateOf(0) }

    fun showToast(text: String) {
        toastText = text
        toastVisible = true
        toastTick++
    }

    LaunchedEffect(toastTick) {
        if (toastTick == 0) return@LaunchedEffect
        delay(1800)
        toastVisible = false
    }

    BackHandler {
        if (sheetOpen) sheetOpen = false else onBack()
    }

    Box(
        Modifier.fillMaxSize()
            .background(palette.background)
            .appearanceBackdrop(appearance.background, accent, darkMode)
    ) {
        Column(Modifier.fillMaxSize()) {
            AppearanceTopBar(
                palette = palette,
                moreOpen = moreOpen,
                onMoreOpenChange = { moreOpen = it },
                onBack = onBack,
                onReset = {
                    onChange(AppearanceSettings())
                    showToast("已恢复默认外观")
                },
            )
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
            ) {
                ThemeHero(theme, darkMode, palette)
                ThemeGrid(
                    selectedId = theme.id,
                    accent = accent,
                    palette = palette,
                    onSelect = { option ->
                        if (option.custom) {
                            showToast("自定义配色：调色板尚未接入")
                        } else {
                            onChange(appearance.copy(themeId = option.id))
                        }
                    },
                )
                AppearanceCard(palette) {
                    SettingRow("外观模式", palette) {
                        AppearanceSegmented(appearance.mode, accent, palette) { mode ->
                            onChange(appearance.copy(mode = mode))
                        }
                    }
                    HorizontalDivider(color = palette.line)
                    SettingRow("首页背景", palette, onClick = { sheetOpen = true }) {
                        RightChevron(palette.ink3, Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.height(ApplyBarGap))
            }
        }

        ApplyBar(accent, palette, onApply = { showToast("已应用「${theme.name}」") })

        BackgroundSheetOverlay(
            open = sheetOpen,
            selected = appearance.background,
            accent = accent,
            palette = palette,
            onSelect = { background ->
                onChange(appearance.copy(background = background))
                sheetOpen = false
            },
            onDismiss = { sheetOpen = false },
        )

        AnimatedVisibility(
            visible = toastVisible,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 112.dp),
            enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 2 },
            exit = fadeOut(tween(200)),
        ) {
            Box(
                Modifier.clip(CircleShape).background(Color(0xE61A1B1F))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(toastText, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ------------------------------------------------------------------ top ---

@Composable
private fun AppearanceTopBar(
    palette: AppearancePalette,
    moreOpen: Boolean,
    onMoreOpenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().height(48.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack)
                .semantics { contentDescription = "返回" },
            contentAlignment = Alignment.Center,
        ) {
            BackChevron(palette.ink, Modifier.size(20.dp))
        }
        Spacer(Modifier.width(2.dp))
        Text(
            "主题外观",
            color = palette.ink,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Box {
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable { onMoreOpenChange(true) }
                    .semantics { contentDescription = "更多" },
                contentAlignment = Alignment.Center,
            ) {
                MoreDots(palette.ink2, Modifier.size(20.dp))
            }
            DropdownMenu(
                expanded = moreOpen,
                onDismissRequest = { onMoreOpenChange(false) },
                containerColor = palette.card,
            ) {
                DropdownMenuItem(
                    text = { Text("恢复默认外观", color = palette.ink, fontSize = 14.sp) },
                    onClick = {
                        onMoreOpenChange(false)
                        onReset()
                    },
                )
            }
        }
    }
}

// ----------------------------------------------------------------- hero ---

@Composable
private fun ThemeHero(theme: ThemeOption, darkMode: Boolean, palette: AppearancePalette) {
    val anchor = if (darkMode) Color(0xFF121317) else Color.White
    val gradient = Brush.linearGradient(
        0f to lerp(theme.base, anchor, if (darkMode) .87f else .90f),
        .48f to lerp(theme.base, anchor, if (darkMode) .79f else .82f),
        1f to lerp(theme.base, anchor, if (darkMode) .70f else .74f),
    )
    Box(Modifier.fillMaxWidth().height(HeroHeight).clipToBounds().background(gradient)) {
        Box(
            Modifier.size(240.dp).offset(x = (-90).dp, y = (-100).dp)
                .background(if (darkMode) Color.White.copy(alpha = .06f) else Color.White.copy(alpha = .62f), CircleShape)
        )
        Box(
            Modifier.size(200.dp).align(Alignment.BottomEnd).offset(x = 70.dp, y = 90.dp)
                .background(theme.base.copy(alpha = if (darkMode) .20f else .13f), CircleShape)
        )
        Box(Modifier.align(Alignment.Center).size(120.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                drawCircle(
                    Brush.radialGradient(listOf(theme.base.copy(alpha = .45f), Color.Transparent)),
                    radius = size.minDimension / 2,
                )
            }
            TvGlyph(theme.base, Modifier.size(85.dp))
        }
        Text(
            text = theme.name,
            color = palette.ink,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.3).sp,
            modifier = Modifier.align(Alignment.BottomStart).offset(x = 20.dp, y = (-24).dp),
        )
    }
}

// ----------------------------------------------------------------- grid ---

@Composable
private fun ThemeGrid(
    selectedId: String,
    accent: Color,
    palette: AppearancePalette,
    onSelect: (ThemeOption) -> Unit,
) {
    AppearanceCard(palette) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 20.dp).selectableGroup()) {
            ThemeCatalog.chunked(4).forEachIndexed { rowIndex, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { option ->
                        ThemeTile(
                            option = option,
                            selected = option.id == selectedId,
                            accent = accent,
                            palette = palette,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(option) },
                        )
                    }
                }
                if (rowIndex < ThemeCatalog.size / 4 - 1) Spacer(Modifier.height(20.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- bottom ---

@Composable
private fun BoxScope.ApplyBar(accent: Color, palette: AppearancePalette, onApply: () -> Unit) {
    Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    .55f to palette.background,
                    1f to palette.background,
                )
            )
            .padding(start = 16.dp, end = 16.dp, top = 20.dp)
            .navigationBarsPadding()
            .padding(bottom = 26.dp),
    ) {
        ApplyButton(accent, onApply)
    }
}

@Composable
private fun ApplyButton(accent: Color, onApply: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .98f else 1f, tween(140), label = "apply press")
    Box(
        Modifier.fillMaxWidth().height(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(
                12.dp,
                CircleShape,
                ambientColor = accent.copy(alpha = .35f),
                spotColor = accent.copy(alpha = .55f),
            )
            .clip(CircleShape)
            .background(Brush.horizontalGradient(listOf(accent, lerp(accent, Color.White, .26f))))
            .clickable(interactionSource = interaction, indication = null, onClick = onApply),
        contentAlignment = Alignment.Center,
    ) {
        Text("立即应用", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = .3.sp)
    }
}

// ----------------------------------------------------------------- sheet ---

@Composable
private fun BoxScope.BackgroundSheetOverlay(
    open: Boolean,
    selected: HomeBackground,
    accent: Color,
    palette: AppearancePalette,
    onSelect: (HomeBackground) -> Unit,
    onDismiss: () -> Unit,
) {
    val scrimInteraction = remember { MutableInteractionSource() }
    AnimatedVisibility(visible = open, enter = fadeIn(tween(260)), exit = fadeOut(tween(200))) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = .36f))
                .clickable(interactionSource = scrimInteraction, indication = null, onClick = onDismiss)
        )
    }
    AnimatedVisibility(
        visible = open,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(tween(340, easing = SheetEasing)) { it },
        exit = slideOutVertically(tween(280, easing = SheetEasing)) { it },
    ) {
        BackgroundSheet(selected, accent, palette, onSelect, onDismiss)
    }
}

@Composable
private fun BackgroundSheet(
    selected: HomeBackground,
    accent: Color,
    palette: AppearancePalette,
    onSelect: (HomeBackground) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(palette.card)
            .padding(top = 6.dp)
            .navigationBarsPadding()
            .padding(bottom = 26.dp),
    ) {
        Box(
            Modifier.width(38.dp).height(4.dp).align(Alignment.CenterHorizontally)
                .clip(CircleShape).background(palette.line)
        )
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("背景样式", color = palette.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                "完成",
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDismiss)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
        HomeBackground.entries.forEachIndexed { index, option ->
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(option) }.padding(horizontal = 22.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = option.label,
                    color = palette.ink,
                    fontSize = 15.sp,
                    fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                )
                if (option == selected) TickGlyph(accent, Modifier.size(19.dp))
            }
            if (index < HomeBackground.entries.lastIndex) {
                HorizontalDivider(Modifier.padding(start = 22.dp), color = palette.line)
            }
        }
    }
}
