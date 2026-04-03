package com.box.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.box.app.ui.theme.appColors
import com.box.app.utils.ThemeManager
import com.kyant.shapes.Capsule

val LocalLiquidBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalLiquidBackdrop.current,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    contentPaddingHorizontal: Dp = 14.dp,
    contentPaddingVertical: Dp = 10.dp,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDark = ThemeManager.shouldUseDarkTheme()
    val c = appColors()
    val translucent by ThemeManager.liquidGlassTranslucent.collectAsState()
    val blurDp by ThemeManager.liquidGlassBlurDp.collectAsState()
    val lensStrength by ThemeManager.liquidGlassLensStrength.collectAsState()
    val blurEffectsEnabled = ThemeManager.shouldUseBlurEffects()
    val pillShape = Capsule()
    val supportsLiquidGlass = blurEffectsEnabled && backdrop != null
    val minSurfaceAlpha = if (isDark) 0.78f else 0.86f
    val normalizedSurfaceColor = if (surfaceColor.isSpecified && surfaceColor.alpha < minSurfaceAlpha) {
        surfaceColor.copy(alpha = minSurfaceAlpha)
    } else {
        surfaceColor
    }
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.Black.copy(alpha = 0.07f)
    }
    val fallbackSurfaceColor = when {
        normalizedSurfaceColor.isSpecified -> normalizedSurfaceColor
        tint.isSpecified -> tint.copy(alpha = 1f)
        else -> c.card.copy(alpha = if (isDark) 0.94f else 0.96f)
    }
    val surfaceModifier = if (supportsLiquidGlass) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { pillShape },
            effects = {
                if (translucent) {
                    vibrancy()
                }
                blur(blurDp.dp.toPx())
                if (translucent) {
                    val s = lensStrength.coerceIn(0f, 2f)
                    lens((12f * s).dp.toPx(), (24f * s).dp.toPx())
                }
            },
            onDrawSurface = {
                if (tint.isSpecified) {
                    drawRect(tint, blendMode = BlendMode.Hue)
                    drawRect(tint.copy(alpha = 0.86f))
                }
                if (normalizedSurfaceColor.isSpecified) {
                    drawRect(normalizedSurfaceColor)
                } else if (!tint.isSpecified) {
                    drawRect(if (isDark) Color.Black.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.16f))
                }
            }
        )
    } else {
        Modifier
            .clip(pillShape)
            .background(fallbackSurfaceColor)
    }

    Row(
        modifier = modifier
            .then(surfaceModifier)
            .then(
                if (blurEffectsEnabled) {
                    Modifier
                } else {
                    Modifier.border(1.dp, borderColor, pillShape)
                }
            )
            .clickable(
                interactionSource = interactionSource,
                indication = if (enabled) null else LocalIndication.current,
                role = Role.Button,
                enabled = enabled,
                onClick = onClick
            )
            .padding(horizontal = contentPaddingHorizontal, vertical = contentPaddingVertical),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
fun LiquidGlassTextFieldPill(
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalLiquidBackdrop.current,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    content: @Composable () -> Unit
) {
    val isDark = ThemeManager.shouldUseDarkTheme()
    val c = appColors()
    val translucent by ThemeManager.liquidGlassTranslucent.collectAsState()
    val blurDp by ThemeManager.liquidGlassBlurDp.collectAsState()
    val lensStrength by ThemeManager.liquidGlassLensStrength.collectAsState()
    val blurEffectsEnabled = ThemeManager.shouldUseBlurEffects()
    val pillShape = Capsule()
    val supportsLiquidGlass = blurEffectsEnabled && backdrop != null
    val minSurfaceAlpha = if (isDark) 0.48f else 0.56f
    val normalizedSurfaceColor = if (surfaceColor.isSpecified && surfaceColor.alpha < minSurfaceAlpha) {
        surfaceColor.copy(alpha = minSurfaceAlpha)
    } else {
        surfaceColor
    }
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.Black.copy(alpha = 0.07f)
    }
    val fallbackSurfaceColor = when {
        normalizedSurfaceColor.isSpecified -> normalizedSurfaceColor
        tint.isSpecified -> tint.copy(alpha = 1f)
        else -> c.card.copy(alpha = if (isDark) 0.94f else 0.96f)
    }
    val surfaceModifier = if (supportsLiquidGlass) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { pillShape },
            effects = {
                if (translucent) {
                    vibrancy()
                }
                blur(blurDp.dp.toPx())
                if (translucent) {
                    val s = lensStrength.coerceIn(0f, 2f)
                    lens((12f * s).dp.toPx(), (24f * s).dp.toPx())
                }
            },
            onDrawSurface = {
                if (tint.isSpecified) {
                    drawRect(tint, blendMode = BlendMode.Hue)
                    drawRect(tint.copy(alpha = 0.86f))
                }
                if (normalizedSurfaceColor.isSpecified) {
                    drawRect(normalizedSurfaceColor)
                } else if (!tint.isSpecified) {
                    drawRect(if (isDark) Color.Black.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.16f))
                }
            }
        )
    } else {
        Modifier.background(fallbackSurfaceColor)
    }

    Box(
        modifier = modifier
            .clip(pillShape)
            .then(surfaceModifier)
            .then(
                if (blurEffectsEnabled) {
                    Modifier
                } else {
                    Modifier.border(1.dp, borderColor, pillShape)
                }
            )
    ) {
        content()
    }
}

@Composable
fun LiquidGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalLiquidBackdrop.current,
    enabled: Boolean = true,
    size: Dp = 38.dp,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    content: @Composable RowScope.() -> Unit
) {
    LiquidGlassButton(
        onClick = onClick,
        modifier = modifier.size(size),
        backdrop = backdrop,
        enabled = enabled,
        tint = tint,
        surfaceColor = surfaceColor,
        contentPaddingHorizontal = 10.dp,
        contentPaddingVertical = 0.dp,
        content = content
    )
}
