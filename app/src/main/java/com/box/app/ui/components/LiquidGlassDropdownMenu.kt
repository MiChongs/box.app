package com.box.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.box.app.ui.theme.appColors
import com.box.app.utils.ThemeManager
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle

@Composable
fun LiquidGlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorBoundsInWindow: IntRect?,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalLiquidBackdrop.current,
    offset: DpOffset = DpOffset(0.dp, 8.dp),
    shape: Shape = RoundedRectangle(16.dp),
    contentPadding: PaddingValues = PaddingValues(8.dp),
    containerColor: Color = appColors().cardAlt,
    content: @Composable ColumnScope.() -> Unit
) {
    if (anchorBoundsInWindow == null) return

    val density = LocalDensity.current
    val offsetXpx = with(density) { offset.x.roundToPx() }
    val offsetYpx = with(density) { offset.y.roundToPx() }
    val c = appColors()
    val isDark = ThemeManager.shouldUseDarkTheme()
    val translucent by ThemeManager.liquidGlassTranslucent.collectAsState()
    val blurDp by ThemeManager.liquidGlassBlurDp.collectAsState()
    val lensStrength by ThemeManager.liquidGlassLensStrength.collectAsState()
    val blurEffectsEnabled = ThemeManager.shouldUseBlurEffects()
    val supportsLiquidGlass = blurEffectsEnabled && backdrop != null
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.Black.copy(alpha = 0.07f)
    }

    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    var overlayPosInWindow by remember { mutableStateOf(IntOffset.Zero) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                val p = coords.positionInWindow()
                overlayPosInWindow = IntOffset(p.x.roundToInt(), p.y.roundToInt())
            }
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(animationSpec = tween(180, delayMillis = 30)) +
                scaleIn(
                    initialScale = 0.94f,
                    animationSpec = tween(220)
                ) +
                slideInVertically(
                    animationSpec = tween(220),
                    initialOffsetY = { fullHeight -> -fullHeight / 10 }
                ),
            exit = fadeOut(animationSpec = tween(140)) +
                scaleOut(
                    targetScale = 0.94f,
                    animationSpec = tween(160)
                ) +
                slideOutVertically(
                    animationSpec = tween(160),
                    targetOffsetY = { fullHeight -> -fullHeight / 12 }
                )
        ) {
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val maxHeightPx = with(density) { maxHeight.roundToPx() }

        val desiredX = (anchorBoundsInWindow.right - overlayPosInWindow.x) - menuSize.width + offsetXpx
        val desiredY = (anchorBoundsInWindow.bottom - overlayPosInWindow.y) + offsetYpx

        val x = desiredX.coerceIn(0, (maxWidthPx - menuSize.width).coerceAtLeast(0))
        val y = desiredY.coerceIn(0, (maxHeightPx - menuSize.height).coerceAtLeast(0))

        val interactionSource = remember { MutableInteractionSource() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onDismissRequest
                )
        )

        Column(
            modifier = modifier
                .offset { IntOffset(x, y) }
                .onSizeChanged { menuSize = it }
                .width(IntrinsicSize.Max)
                .widthIn(max = maxWidth)
                .clip(shape)
                .then(
                    if (supportsLiquidGlass) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { shape },
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
                                drawRect(containerColor.copy(alpha = if (isDark) 0.26f else 0.34f))
                            }
                        )
                    } else {
                        Modifier.background(containerColor)
                    }
                )
                .then(
                    if (blurEffectsEnabled) {
                        Modifier
                    } else {
                        Modifier.border(1.dp, borderColor, shape)
                    }
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { }
                )
                .padding(contentPadding),
            content = content
        )
        }
    }
}
