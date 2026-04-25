package com.box.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.box.app.utils.SystemBarMode
import com.box.app.utils.ThemeManager
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeColorSpec
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.theme.ThemePaletteStyle
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColorScheme

data class AppColors(
    val pageBg: Color,
    val card: Color,
    val cardAlt: Color,
    val divider: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

@Composable
fun appColors(): AppColors {
    val scheme = MiuixTheme.colorScheme
    return AppColors(
        pageBg = scheme.background,
        card = scheme.surfaceContainer,
        cardAlt = scheme.surfaceContainerHigh,
        divider = scheme.dividerLine,
        textPrimary = scheme.onSurface,
        textSecondary = scheme.onSurfaceSecondary
    )
}

@Composable
fun AppTheme(
    content: @Composable () -> Unit
) {
    val themeMode by ThemeManager.themeMode.collectAsState()
    val isDark = ThemeManager.shouldUseDarkTheme()
    val systemBarSettings by ThemeManager.systemBarSettings.collectAsState()

    val trueBlack by ThemeManager.trueBlack.collectAsState()
    val monetEnabled by ThemeManager.monetEnabled.collectAsState()
    val keyColor by ThemeManager.keyColor.collectAsState()
    val paletteStyleName by ThemeManager.paletteStyle.collectAsState()
    val colorSpecName by ThemeManager.colorSpec.collectAsState()

    val miuixPaletteStyle = remember(paletteStyleName) {
        try { ThemePaletteStyle.valueOf(paletteStyleName) } catch (_: Exception) { ThemePaletteStyle.TonalSpot }
    }
    val miuixColorSpec = remember(colorSpecName) {
        if (colorSpecName == "SPEC_2025") ThemeColorSpec.Spec2025 else ThemeColorSpec.Spec2021
    }

    val colorSchemeMode = when {
        monetEnabled -> when (themeMode) {
            com.box.app.utils.ThemeMode.SYSTEM -> ColorSchemeMode.MonetSystem
            com.box.app.utils.ThemeMode.LIGHT -> ColorSchemeMode.MonetLight
            com.box.app.utils.ThemeMode.DARK -> ColorSchemeMode.MonetDark
        }
        else -> when (themeMode) {
            com.box.app.utils.ThemeMode.LIGHT -> ColorSchemeMode.Light
            com.box.app.utils.ThemeMode.DARK -> ColorSchemeMode.Dark
            com.box.app.utils.ThemeMode.SYSTEM -> ColorSchemeMode.System
        }
    }

    val lightColors = remember { miuixLightColorScheme() }
    val darkColors = remember(trueBlack) {
        if (trueBlack) {
            miuixDarkColorScheme().copy(
                background = Color.Black,
                surface = Color.Black
            )
        } else {
            miuixDarkColorScheme()
        }
    }

    val controller = remember(
        colorSchemeMode, isDark, lightColors, darkColors, trueBlack,
        monetEnabled, keyColor, miuixPaletteStyle, miuixColorSpec
    ) {
        if (monetEnabled) {
            ThemeController(
                colorSchemeMode = colorSchemeMode,
                keyColor = if (keyColor == 0) null else Color(keyColor),
                isDark = isDark,
                paletteStyle = miuixPaletteStyle,
                colorSpec = miuixColorSpec
            )
        } else {
            ThemeController(
                colorSchemeMode = colorSchemeMode,
                lightColors = lightColors,
                darkColors = darkColors,
                isDark = isDark
            )
        }
    }

    top.yukonga.miuix.kmp.theme.MiuixTheme(controller = controller) {
        val c = appColors()
        val view = LocalView.current

        SideEffect {
            val activity = view.context.findActivity() ?: return@SideEffect
            val window = activity.window

            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            @Suppress("DEPRECATION")
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
            @Suppress("DEPRECATION")
            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION)

            WindowCompat.setDecorFitsSystemWindows(window, false)

            val statusBarColor = when (systemBarSettings.statusBar) {
                SystemBarMode.TRANSPARENT -> android.graphics.Color.TRANSPARENT
                SystemBarMode.OPAQUE -> c.pageBg.toArgb()
            }

            val navigationBarColor = when (systemBarSettings.navigationBar) {
                SystemBarMode.TRANSPARENT -> android.graphics.Color.TRANSPARENT
                SystemBarMode.OPAQUE -> c.pageBg.toArgb()
            }

            @Suppress("DEPRECATION")
            window.statusBarColor = statusBarColor
            @Suppress("DEPRECATION")
            window.navigationBarColor = navigationBarColor

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                window.isStatusBarContrastEnforced = false
                @Suppress("DEPRECATION")
                window.isNavigationBarContrastEnforced = false
            }

            WindowInsetsControllerCompat(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
        }

        val contentModifier = Modifier
            .then(
                if (systemBarSettings.statusBar == SystemBarMode.OPAQUE) {
                    Modifier.windowInsetsPadding(WindowInsets.statusBars)
                } else {
                    Modifier
                }
            )
            .then(
                if (systemBarSettings.navigationBar == SystemBarMode.OPAQUE) {
                    Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                } else {
                    Modifier
                }
            )

        MiuixScaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { paddingValues ->
            Box(
                modifier = contentModifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                content()
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Composable
fun appAccentColor(): Color = MiuixTheme.colorScheme.primary

@Composable
fun appErrorColor(): Color = MiuixTheme.colorScheme.error

@Composable
fun isDark() = ThemeManager.shouldUseDarkTheme()
