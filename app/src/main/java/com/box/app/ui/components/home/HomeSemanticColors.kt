package com.box.app.ui.components.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.box.app.utils.ThemeManager
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class HomeSemanticColors(
    val accent: Color,
    val container: Color,
    val onContainer: Color
)

// ── Monet 辅助：从主题色派生语义色 ──────────────────────────────────────────

/**
 * 基于 [tint] 和当前 surface 色生成容器背景 + 内容色。
 * [ratio] 控制 tint 混入 surface 的比例，值越大容器色越接近 tint。
 */
@Composable
private fun monetSemanticColors(
    tint: Color,
    dark: Boolean,
    ratio: Float = if (ThemeManager.shouldUseDarkTheme()) 0.16f else 0.10f
): HomeSemanticColors {
    val surface = MiuixTheme.colorScheme.surface
    val container = lerp(surface, tint, ratio)
    val onContainer = if (dark) {
        lerp(tint, Color.White, 0.35f)
    } else {
        lerp(tint, Color.Black, 0.30f)
    }
    return HomeSemanticColors(
        accent = tint,
        container = container,
        onContainer = onContainer
    )
}

/** 是否启用了 Monet 动态取色 */
@Composable
private fun isMonetActive(): Boolean {
    val enabled by ThemeManager.monetEnabled.collectAsState()
    return enabled
}

// ── 语义色函数 ──────────────────────────────────────────────────────────────

/** 运行正常 — Monet: primary 系列；非 Monet: 绿 */
@Composable
internal fun homeSuccessColors(): HomeSemanticColors {
    val dark = ThemeManager.shouldUseDarkTheme()
    if (isMonetActive()) {
        return monetSemanticColors(MiuixTheme.colorScheme.primary, dark)
    }
    return HomeSemanticColors(
        accent      = if (dark) Color(0xFF66BB6A) else Color(0xFF2E7D32),
        container   = if (dark) Color(0xFF1B2E1C) else Color(0xFFE8F5E9),
        onContainer = if (dark) Color(0xFFA5D6A7) else Color(0xFF1B5E20)
    )
}

/** 信息 — Monet: secondary 系列；非 Monet: 蓝 */
@Composable
internal fun homeInfoColors(): HomeSemanticColors {
    val dark = ThemeManager.shouldUseDarkTheme()
    if (isMonetActive()) {
        // secondary 偏移色调，用于区分 success
        val secondary = lerp(
            MiuixTheme.colorScheme.primary,
            if (dark) Color(0xFF90CAF9) else Color(0xFF1565C0),
            0.5f
        )
        return monetSemanticColors(secondary, dark)
    }
    return HomeSemanticColors(
        accent      = if (dark) Color(0xFF42A5F5) else Color(0xFF1565C0),
        container   = if (dark) Color(0xFF162A3E) else Color(0xFFE3F2FD),
        onContainer = if (dark) Color(0xFF90CAF9) else Color(0xFF0D47A1)
    )
}

/** 警告 / 过渡 — Monet: tertiary 偏暖；非 Monet: 琥珀 */
@Composable
internal fun homeWarningColors(): HomeSemanticColors {
    val dark = ThemeManager.shouldUseDarkTheme()
    if (isMonetActive()) {
        val tertiary = lerp(
            MiuixTheme.colorScheme.primary,
            if (dark) Color(0xFFFFCC80) else Color(0xFFE65100),
            0.6f
        )
        return monetSemanticColors(tertiary, dark)
    }
    return HomeSemanticColors(
        accent      = if (dark) Color(0xFFFFA726) else Color(0xFFE65100),
        container   = if (dark) Color(0xFF2E2210) else Color(0xFFFFF3E0),
        onContainer = if (dark) Color(0xFFFFCC80) else Color(0xFFBF360C)
    )
}

/** 危险 / 不可用 — Monet: error 偏移；非 Monet: 红 */
@Composable
internal fun homeDangerColors(): HomeSemanticColors {
    val dark = ThemeManager.shouldUseDarkTheme()
    if (isMonetActive()) {
        val error = lerp(
            MiuixTheme.colorScheme.primary,
            if (dark) Color(0xFFEF9A9A) else Color(0xFFC62828),
            0.65f
        )
        return monetSemanticColors(error, dark)
    }
    return HomeSemanticColors(
        accent      = if (dark) Color(0xFFEF5350) else Color(0xFFC62828),
        container   = if (dark) Color(0xFF2E1415) else Color(0xFFFFEBEE),
        onContainer = if (dark) Color(0xFFEF9A9A) else Color(0xFFB71C1C)
    )
}

/** 停止 / 中性 — Monet: onSurfaceSecondary 系列；非 Monet: 蓝灰 */
@Composable
internal fun homeNeutralColors(): HomeSemanticColors {
    val dark = ThemeManager.shouldUseDarkTheme()
    if (isMonetActive()) {
        return monetSemanticColors(MiuixTheme.colorScheme.onSurfaceSecondary, dark, ratio = 0.10f)
    }
    return HomeSemanticColors(
        accent      = if (dark) Color(0xFF78909C) else Color(0xFF546E7A),
        container   = if (dark) Color(0xFF1E272C) else Color(0xFFECEFF1),
        onContainer = if (dark) Color(0xFFB0BEC5) else Color(0xFF37474F)
    )
}
