package com.box.app.ui.screens

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.box.app.R
import com.box.app.ui.theme.keyColorOptions
import com.box.app.utils.MapleFontManager
import com.box.app.utils.ThemeManager
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.miuixShape
import com.box.app.ui.theme.appColors
import com.box.app.utils.UiScaleManager
import kotlin.math.roundToInt

@Composable
fun ThemeSettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val c = appColors()

    // ── 状态 ──
    val themeMode by ThemeManager.themeMode.collectAsState()
    val isDark = ThemeManager.shouldUseDarkTheme()
    val trueBlack by ThemeManager.trueBlack.collectAsState()
    val monetEnabled by ThemeManager.monetEnabled.collectAsState()
    val keyColor by ThemeManager.keyColor.collectAsState()
    val paletteStyleName by ThemeManager.paletteStyle.collectAsState()
    val colorSpecName by ThemeManager.colorSpec.collectAsState()
    val blurEffectsSupported = ThemeManager.supportsBlurEffects()
    val blurEffectsActive by ThemeManager.blurEffectsEnabled.collectAsState()
    val liquidGlassNavBarEnabled by ThemeManager.liquidGlassNavBar.collectAsState()
    val bottomSheetBlurEnabled by ThemeManager.bottomSheetBlur.collectAsState()
    val mapleFontEnabled by ThemeManager.mapleFontLogs.collectAsState()
    val fontState by MapleFontManager.state.collectAsState()
    val hyperXNavEnabled by ThemeManager.hyperXNavTransitions.collectAsState()
    val liquidGlassBlurDp by ThemeManager.liquidGlassBlurDp.collectAsState()
    val liquidGlassLensStrength by ThemeManager.liquidGlassLensStrength.collectAsState()
    val liquidGlassTranslucent by ThemeManager.liquidGlassTranslucent.collectAsState()

    val blurSummary = if (blurEffectsSupported)
        stringResource(R.string.settings_blur_effects_subtitle)
    else
        stringResource(R.string.settings_blur_effects_unsupported_subtitle)

    val mapleSummary = when {
        fontState is MapleFontManager.FontState.Downloading -> stringResource(R.string.settings_maple_font_logs_downloading)
        fontState is MapleFontManager.FontState.Error -> stringResource(R.string.settings_maple_font_logs_failed)
        mapleFontEnabled && fontState is MapleFontManager.FontState.Ready -> stringResource(R.string.settings_maple_font_logs_ready)
        else -> stringResource(R.string.settings_maple_font_logs_subtitle)
    }

    // UI Scale
    val currentUiScalePercent by UiScaleManager.uiScalePercent.collectAsState()
    var pendingUiScalePercent by remember(currentUiScalePercent) { mutableFloatStateOf(currentUiScalePercent.toFloat()) }
    var showTuningSection by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.settings_theme_appearance),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = 12.dp),
            contentPadding = innerPadding,
        ) {
            // ── 主题预览卡片 ──
            item {
                Spacer(modifier = Modifier.height(16.dp))
                ThemePreviewCard(
                    keyColor = keyColor,
                    isDark = isDark,
                    monetEnabled = monetEnabled,
                    liquidGlassNavBar = liquidGlassNavBarEnabled,
                )
                Spacer(modifier = Modifier.height(40.dp))
            }

            // ── 主题模式 TabRow ──
            item {
                val themeItems = listOf(
                    stringResource(R.string.settings_theme_follow_system),
                    stringResource(R.string.settings_theme_light),
                    stringResource(R.string.settings_theme_dark),
                )
                TabRow(
                    tabs = themeItems,
                    selectedTabIndex = when (themeMode) {
                        com.box.app.utils.ThemeMode.SYSTEM -> 0
                        com.box.app.utils.ThemeMode.LIGHT -> 1
                        com.box.app.utils.ThemeMode.DARK -> 2
                    },
                    onTabSelected = { index ->
                        val mode = when (index) {
                            1 -> com.box.app.utils.ThemeMode.LIGHT
                            2 -> com.box.app.utils.ThemeMode.DARK
                            else -> com.box.app.utils.ThemeMode.SYSTEM
                        }
                        ThemeManager.setThemeMode(context, mode)
                    },
                    height = 48.dp,
                )
            }

            // ── Monet 动态取色 ──
            item {
                Card(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_monet),
                        summary = stringResource(R.string.settings_monet_subtitle),
                        checked = monetEnabled,
                        onCheckedChange = { ThemeManager.setMonetEnabled(context, it) }
                    )

                    AnimatedVisibility(visible = monetEnabled) {
                        Column {
                            val colorItems = listOf(
                                stringResource(R.string.settings_key_color_default),
                                stringResource(R.string.color_red),
                                stringResource(R.string.color_pink),
                                stringResource(R.string.color_purple),
                                stringResource(R.string.color_deep_purple),
                                stringResource(R.string.color_indigo),
                                stringResource(R.string.color_blue),
                                stringResource(R.string.color_cyan),
                                stringResource(R.string.color_teal),
                                stringResource(R.string.color_green),
                                stringResource(R.string.color_yellow),
                                stringResource(R.string.color_amber),
                                stringResource(R.string.color_orange),
                                stringResource(R.string.color_brown),
                                stringResource(R.string.color_blue_grey),
                                stringResource(R.string.color_sakura),
                            )
                            val colorValues = listOf(0) + keyColorOptions
                            OverlayDropdownPreference(
                                title = stringResource(R.string.settings_key_color),
                                items = colorItems,
                                selectedIndex = colorValues.indexOf(keyColor).takeIf { it >= 0 } ?: 0,
                                onSelectedIndexChange = { index ->
                                    ThemeManager.setKeyColor(context, colorValues[index])
                                }
                            )

                            AnimatedVisibility(visible = keyColor != 0) {
                                Column {
                                    val styles = PaletteStyle.entries
                                    OverlayDropdownPreference(
                                        title = stringResource(R.string.settings_color_style),
                                        items = styles.map { it.name },
                                        selectedIndex = styles.indexOfFirst { it.name == paletteStyleName }.coerceAtLeast(0),
                                        onSelectedIndexChange = { index ->
                                            ThemeManager.setPaletteStyle(context, styles[index].name)
                                        }
                                    )

                                    val specs = ColorSpec.SpecVersion.entries
                                    OverlayDropdownPreference(
                                        title = stringResource(R.string.settings_color_spec),
                                        items = specs.map { it.name },
                                        selectedIndex = specs.indexOfFirst { it.name == colorSpecName }.coerceAtLeast(0),
                                        onSelectedIndexChange = { index ->
                                            ThemeManager.setColorSpec(context, specs[index].name)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 视觉效果 ──
            item {
                Card(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_true_black),
                        summary = stringResource(R.string.settings_true_black_subtitle),
                        checked = trueBlack,
                        onCheckedChange = { ThemeManager.setTrueBlack(context, it) }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_blur_effects),
                        summary = blurSummary,
                        checked = blurEffectsActive,
                        onCheckedChange = { ThemeManager.setBlurEffectsEnabled(context, it) },
                        enabled = blurEffectsSupported,
                    )
                    AnimatedVisibility(visible = blurEffectsActive) {
                        Column {
                            SwitchPreference(
                                title = stringResource(R.string.settings_liquid_glass_nav_bar),
                                summary = stringResource(R.string.settings_liquid_glass_nav_bar_subtitle),
                                checked = liquidGlassNavBarEnabled,
                                onCheckedChange = { ThemeManager.setLiquidGlassNavBar(context, it) },
                            )
                            SwitchPreference(
                                title = stringResource(R.string.settings_bottom_sheet_blur),
                                summary = stringResource(R.string.settings_bottom_sheet_blur_subtitle),
                                checked = bottomSheetBlurEnabled,
                                onCheckedChange = { ThemeManager.setBottomSheetBlur(context, it) },
                            )
                            SwitchPreference(
                                title = stringResource(R.string.settings_liquid_glass_translucent),
                                summary = stringResource(R.string.settings_liquid_glass_translucent_subtitle),
                                checked = liquidGlassTranslucent,
                                onCheckedChange = { ThemeManager.setLiquidGlassTranslucent(context, it) },
                            )
                        }
                    }
                }
            }

            // ── 微调滑块 ──
            item {
                Card(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                ) {
                    AnimatedVisibility(visible = blurEffectsActive && liquidGlassTranslucent) {
                        ArrowPreference(
                            title = stringResource(R.string.settings_liquid_glass_lens_strength),
                            summary = stringResource(R.string.settings_liquid_glass_lens_strength_subtitle),
                            onClick = {},
                            holdDownState = true,
                            bottomAction = {
                                Slider(
                                    value = liquidGlassLensStrength,
                                    onValueChange = { ThemeManager.setLiquidGlassLensStrength(context, it) },
                                    valueRange = 0f..2f,
                                )
                            }
                        )
                    }
                    AnimatedVisibility(visible = blurEffectsActive) {
                        ArrowPreference(
                            title = stringResource(R.string.settings_liquid_glass_blur_strength),
                            summary = stringResource(R.string.settings_liquid_glass_blur_strength_subtitle),
                            onClick = {},
                            holdDownState = true,
                            bottomAction = {
                                Slider(
                                    value = liquidGlassBlurDp,
                                    onValueChange = { ThemeManager.setLiquidGlassBlurDp(context, it) },
                                    valueRange = 0f..20f,
                                )
                            }
                        )
                    }
                    ArrowPreference(
                        title = stringResource(R.string.settings_ui_scale, pendingUiScalePercent.roundToInt()),
                        summary = stringResource(R.string.settings_ui_scale_subtitle),
                        onClick = {},
                        holdDownState = true,
                        bottomAction = {
                            Slider(
                                value = pendingUiScalePercent,
                                onValueChange = { pendingUiScalePercent = it.coerceIn(80f, 120f) },
                                onValueChangeFinished = {
                                    UiScaleManager.setUiScalePercent(context, pendingUiScalePercent.roundToInt())
                                },
                                valueRange = 80f..120f,
                            )
                        }
                    )
                }
            }

            // ── 其他 ──
            item {
                Card(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth(),
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.settings_maple_font_logs),
                        summary = mapleSummary,
                        checked = mapleFontEnabled,
                        enabled = fontState !is MapleFontManager.FontState.Downloading,
                        onCheckedChange = { enabled ->
                            ThemeManager.setMapleFontLogs(context, enabled)
                            if (enabled && !MapleFontManager.isCached(context)) {
                                scope.launch { MapleFontManager.downloadAndInstall(context) }
                            } else if (enabled) {
                                MapleFontManager.loadCachedFont(context)
                            }
                        }
                    )
                    SwitchPreference(
                        title = stringResource(R.string.settings_hyperx_nav_transitions),
                        summary = stringResource(R.string.settings_hyperx_nav_transitions_subtitle),
                        checked = hyperXNavEnabled,
                        onCheckedChange = { ThemeManager.setHyperXNavTransitions(context, it) }
                    )
                }
            }

            // ── 底部留白 ──
            item {
                Spacer(
                    Modifier.height(
                        com.box.app.ui.components.systemNavBarBottomPadding() + 16.dp
                    )
                )
            }
        }
    }
}

// ── 主题预览卡片 ──

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
private fun ThemePreviewCard(
    keyColor: Int,
    isDark: Boolean,
    monetEnabled: Boolean,
    liquidGlassNavBar: Boolean,
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.toFloat()
    val screenHeight = configuration.screenHeightDp.toFloat()
    val screenRatio = screenWidth / screenHeight

    val seedColor = if (keyColor == 0) MiuixTheme.colorScheme.primary else Color(keyColor)
    val paletteStyleName by ThemeManager.paletteStyle.collectAsState()
    val effectiveStyle = if (keyColor == 0) PaletteStyle.TonalSpot else {
        try { PaletteStyle.valueOf(paletteStyleName) } catch (_: Exception) { PaletteStyle.TonalSpot }
    }
    val dynamicCs = rememberDynamicColorScheme(
        seedColor = seedColor,
        isDark = isDark,
        style = effectiveStyle,
    )

    val cs = MiuixTheme.colorScheme
    val bgColor = if (monetEnabled) dynamicCs.background else cs.surface
    val textColor = if (monetEnabled) dynamicCs.onSurface else cs.onBackground
    val accentCardColor = when {
        monetEnabled -> dynamicCs.secondaryContainer
        isDark -> Color(0xFF1A3825)
        else -> Color(0xFFDFFAE4)
    }
    val cardColor = if (monetEnabled) dynamicCs.surfaceContainerHighest else cs.surfaceVariant
    val navBarColor = if (monetEnabled) dynamicCs.surfaceContainer else cs.surface
    val iconColor = if (monetEnabled) dynamicCs.primary else cs.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .aspectRatio(screenRatio)
                .clip(miuixShape(20.dp))
                .background(bgColor)
                .border(1.dp, cs.outline, miuixShape(20.dp))
        ) {
            Column {
                // 顶栏
                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .fillMaxWidth()
                        .padding(start = 12.dp, top = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontSize = 12.sp,
                        color = textColor
                    )
                }

                // 卡片区
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(65.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .background(accentCardColor)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(cardColor)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(cardColor)
                        )
                    }
                }

                // 内容区
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.8f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(cardColor)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(.1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(cardColor)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(.1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(cardColor)
                    )
                }
            }

            // 导航栏
            if (liquidGlassNavBar) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(navBarColor.copy(alpha = 0.5f))
                            .border(0.5.dp, textColor.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .size(13.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (it == 0) iconColor else textColor.copy(alpha = 0.5f))
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(textColor.copy(alpha = 0.1f))
                    )
                    Row(
                        modifier = Modifier
                            .height(36.dp)
                            .fillMaxWidth()
                            .background(navBarColor)
                            .padding(top = 2.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(3) {
                            Box(
                                modifier = Modifier
                                    .size(15.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        if (it == 0) cs.onSurfaceContainer
                                        else cs.onSurfaceContainer.copy(alpha = 0.5f)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}
