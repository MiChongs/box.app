package com.box.app.utils

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import android.app.UiModeManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

enum class SystemBarMode {
    TRANSPARENT,
    OPAQUE
}

data class SystemBarSettings(
    val statusBar: SystemBarMode,
    val navigationBar: SystemBarMode
)

object ThemeManager {
    private const val PREFS_NAME = "theme_settings"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_TRUE_BLACK = "true_black"
    private const val KEY_STATUS_BAR_MODE = "status_bar_mode"
    private const val KEY_NAVIGATION_BAR_MODE = "navigation_bar_mode"
    private const val KEY_LIQUID_GLASS_TRANSLUCENT = "liquid_glass_translucent"
    private const val KEY_LIQUID_GLASS_BLUR_DP = "liquid_glass_blur_dp"
    private const val KEY_LIQUID_GLASS_LENS_STRENGTH = "liquid_glass_lens_strength"
    private const val KEY_BOTTOM_SHEET_BLUR = "bottom_sheet_blur"
    private const val KEY_BLUR_EFFECTS_ENABLED = "blur_effects_enabled"
    private const val KEY_LIQUID_GLASS_NAV_BAR = "liquid_glass_nav_bar"
    private const val KEY_MAPLE_FONT_LOGS = "maple_font_logs"
    private const val KEY_MAPLE_FONT_EDITOR = "maple_font_editor"
    private const val KEY_HYPERX_NAV_TRANSITIONS = "hyperx_nav_transitions"
    private const val KEY_MONET_ENABLED = "monet_enabled"
    private const val KEY_KEY_COLOR = "key_color"
    private const val KEY_PALETTE_STYLE = "palette_style"
    private const val KEY_COLOR_SPEC = "color_spec"

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _trueBlack = MutableStateFlow(false)
    val trueBlack: StateFlow<Boolean> = _trueBlack.asStateFlow()

    private val _systemBarSettings = MutableStateFlow(
        SystemBarSettings(
            statusBar = SystemBarMode.TRANSPARENT,
            navigationBar = SystemBarMode.TRANSPARENT
        )
    )
    val systemBarSettings: StateFlow<SystemBarSettings> = _systemBarSettings.asStateFlow()

    private val _liquidGlassTranslucent = MutableStateFlow(true)
    val liquidGlassTranslucent: StateFlow<Boolean> = _liquidGlassTranslucent.asStateFlow()

    private val _liquidGlassBlurDp = MutableStateFlow(6f)
    val liquidGlassBlurDp: StateFlow<Float> = _liquidGlassBlurDp.asStateFlow()

    private val _liquidGlassLensStrength = MutableStateFlow(1f)
    val liquidGlassLensStrength: StateFlow<Float> = _liquidGlassLensStrength.asStateFlow()

    private val _bottomSheetBlur = MutableStateFlow(true)
    val bottomSheetBlur: StateFlow<Boolean> = _bottomSheetBlur.asStateFlow()

    private val _blurEffectsEnabled = MutableStateFlow(false)
    val blurEffectsEnabled: StateFlow<Boolean> = _blurEffectsEnabled.asStateFlow()

    private val _liquidGlassNavBar = MutableStateFlow(false)
    val liquidGlassNavBar: StateFlow<Boolean> = _liquidGlassNavBar.asStateFlow()

    private val _mapleFontLogs = MutableStateFlow(false)
    val mapleFontLogs: StateFlow<Boolean> = _mapleFontLogs.asStateFlow()

    private val _mapleFontEditor = MutableStateFlow(false)
    val mapleFontEditor: StateFlow<Boolean> = _mapleFontEditor.asStateFlow()

    private val _hyperXNavTransitions = MutableStateFlow(false)
    val hyperXNavTransitions: StateFlow<Boolean> = _hyperXNavTransitions.asStateFlow()

    private val _monetEnabled = MutableStateFlow(false)
    val monetEnabled: StateFlow<Boolean> = _monetEnabled.asStateFlow()

    private val _keyColor = MutableStateFlow(0)
    val keyColor: StateFlow<Int> = _keyColor.asStateFlow()

    private val _paletteStyle = MutableStateFlow("TonalSpot")
    val paletteStyle: StateFlow<String> = _paletteStyle.asStateFlow()

    private val _colorSpec = MutableStateFlow("Default")
    val colorSpec: StateFlow<String> = _colorSpec.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedMode = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        val mode = ThemeMode.valueOf(savedMode ?: ThemeMode.SYSTEM.name)
        _themeMode.value = mode
        _trueBlack.value = prefs.getBoolean(KEY_TRUE_BLACK, false)
        _liquidGlassTranslucent.value = prefs.getBoolean(KEY_LIQUID_GLASS_TRANSLUCENT, true)
        _liquidGlassBlurDp.value = prefs.getFloat(KEY_LIQUID_GLASS_BLUR_DP, 6f)
        _liquidGlassLensStrength.value = prefs.getFloat(KEY_LIQUID_GLASS_LENS_STRENGTH, 1f)
        _bottomSheetBlur.value = prefs.getBoolean(KEY_BOTTOM_SHEET_BLUR, true)
        _blurEffectsEnabled.value = prefs.getBoolean(KEY_BLUR_EFFECTS_ENABLED, false) && supportsBlurEffects()
        _liquidGlassNavBar.value = prefs.getBoolean(KEY_LIQUID_GLASS_NAV_BAR, false) && supportsBlurEffects()
        _mapleFontLogs.value = prefs.getBoolean(KEY_MAPLE_FONT_LOGS, false)
        _mapleFontEditor.value = prefs.getBoolean(KEY_MAPLE_FONT_EDITOR, false)
        _hyperXNavTransitions.value = prefs.getBoolean(KEY_HYPERX_NAV_TRANSITIONS, false)
        _monetEnabled.value = prefs.getBoolean(KEY_MONET_ENABLED, false)
        _keyColor.value = prefs.getInt(KEY_KEY_COLOR, 0)
        _paletteStyle.value = prefs.getString(KEY_PALETTE_STYLE, "TonalSpot") ?: "TonalSpot"
        _colorSpec.value = prefs.getString(KEY_COLOR_SPEC, "Default") ?: "Default"
        if (_mapleFontLogs.value || _mapleFontEditor.value) {
            MapleFontManager.loadCachedFont(context)
        }
        if (!supportsBlurEffects() && prefs.contains(KEY_BLUR_EFFECTS_ENABLED)) {
            prefs.edit().putBoolean(KEY_BLUR_EFFECTS_ENABLED, false).apply()
        }
        
        // Load system bar settings
        val statusBarMode = prefs.getString(KEY_STATUS_BAR_MODE, SystemBarMode.TRANSPARENT.name)
        val navigationBarMode = prefs.getString(KEY_NAVIGATION_BAR_MODE, SystemBarMode.TRANSPARENT.name)
        _systemBarSettings.value = SystemBarSettings(
            statusBar = SystemBarMode.valueOf(statusBarMode ?: SystemBarMode.TRANSPARENT.name),
            navigationBar = SystemBarMode.valueOf(navigationBarMode ?: SystemBarMode.TRANSPARENT.name)
        )
        
        applyAppCompatNightMode(mode)
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        _themeMode.value = mode
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        applyAppCompatNightMode(mode)
    }

    fun setTrueBlack(context: Context, enabled: Boolean) {
        _trueBlack.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_TRUE_BLACK, enabled).apply()
    }

    fun setSystemBarSettings(context: Context, settings: SystemBarSettings) {
        _systemBarSettings.value = settings
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_STATUS_BAR_MODE, settings.statusBar.name)
            .putString(KEY_NAVIGATION_BAR_MODE, settings.navigationBar.name)
            .apply()
    }

    fun setLiquidGlassTranslucent(context: Context, enabled: Boolean) {
        _liquidGlassTranslucent.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LIQUID_GLASS_TRANSLUCENT, enabled).apply()
    }

    fun setLiquidGlassBlurDp(context: Context, blurDp: Float) {
        val v = blurDp.coerceIn(0f, 20f)
        _liquidGlassBlurDp.value = v
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_LIQUID_GLASS_BLUR_DP, v).apply()
    }

    fun setLiquidGlassLensStrength(context: Context, strength: Float) {
        val v = strength.coerceIn(0f, 2f)
        _liquidGlassLensStrength.value = v
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_LIQUID_GLASS_LENS_STRENGTH, v).apply()
    }

    fun setBottomSheetBlur(context: Context, enabled: Boolean) {
        _bottomSheetBlur.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_BOTTOM_SHEET_BLUR, enabled).apply()
    }

    fun setLiquidGlassNavBar(context: Context, enabled: Boolean) {
        val finalEnabled = enabled && supportsBlurEffects()
        _liquidGlassNavBar.value = finalEnabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LIQUID_GLASS_NAV_BAR, finalEnabled).apply()
    }

    fun setBlurEffectsEnabled(context: Context, enabled: Boolean) {
        val finalEnabled = enabled && supportsBlurEffects()
        _blurEffectsEnabled.value = finalEnabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_BLUR_EFFECTS_ENABLED, finalEnabled).apply()
    }

    fun setMapleFontLogs(context: Context, enabled: Boolean) {
        _mapleFontLogs.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_MAPLE_FONT_LOGS, enabled).apply()
        // 关闭时仅切换状态，保留缓存文件，避免重复下载
    }

    fun setMapleFontEditor(context: Context, enabled: Boolean) {
        _mapleFontEditor.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_MAPLE_FONT_EDITOR, enabled).apply()
    }

    fun setHyperXNavTransitions(context: Context, enabled: Boolean) {
        _hyperXNavTransitions.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_HYPERX_NAV_TRANSITIONS, enabled).apply()
    }

    fun setMonetEnabled(context: Context, enabled: Boolean) {
        _monetEnabled.value = enabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_MONET_ENABLED, enabled).apply()
    }

    fun setKeyColor(context: Context, color: Int) {
        _keyColor.value = color
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_KEY_COLOR, color).apply()
    }

    fun setPaletteStyle(context: Context, style: String) {
        _paletteStyle.value = style
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PALETTE_STYLE, style).apply()
    }

    fun setColorSpec(context: Context, spec: String) {
        _colorSpec.value = spec
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_COLOR_SPEC, spec).apply()
    }

    fun supportsBlurEffects(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun isBlurEffectsEnabled(): Boolean = supportsBlurEffects() && _blurEffectsEnabled.value

    private fun applyAppCompatNightMode(mode: ThemeMode) {
        // Compose 项目通过 MiuixTheme controller 控制深浅色，此处无需 AppCompatDelegate
    }

    @Composable
    fun shouldUseDarkTheme(): Boolean {
        val mode by themeMode.collectAsState()
        val systemInDark = isSystemInDarkTheme()
        return when (mode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> systemInDark
        }
    }

    @Composable
    fun shouldUseBlurEffects(): Boolean {
        val enabled by blurEffectsEnabled.collectAsState()
        return enabled && supportsBlurEffects()
    }
}
