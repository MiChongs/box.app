package com.box.app.utils

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.appcompat.app.AppCompatDelegate
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

    fun setBlurEffectsEnabled(context: Context, enabled: Boolean) {
        val finalEnabled = enabled && supportsBlurEffects()
        _blurEffectsEnabled.value = finalEnabled
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_BLUR_EFFECTS_ENABLED, finalEnabled).apply()
    }

    fun supportsBlurEffects(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun isBlurEffectsEnabled(): Boolean = supportsBlurEffects() && _blurEffectsEnabled.value

    private fun applyAppCompatNightMode(mode: ThemeMode) {
        val nightMode = when (mode) {
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(nightMode)
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
