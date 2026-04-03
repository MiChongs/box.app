package com.box.app.ui.components

import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.onGloballyPositioned
import com.box.app.ui.theme.appColors
import com.box.app.ui.theme.appAccentColor
import com.box.app.utils.ThemeManager
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.box.app.ui.utils.DampedDragAnimation
import com.box.app.ui.utils.InteractiveHighlight
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings

@Composable
fun FloatingPillNavBar(
    mainPagerState: MainPagerState,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val c = appColors()
    val pillShape = Capsule()

    val isDark = ThemeManager.shouldUseDarkTheme()
    val translucent by ThemeManager.liquidGlassTranslucent.collectAsState()
    val blurDp by ThemeManager.liquidGlassBlurDp.collectAsState()
    val lensStrength by ThemeManager.liquidGlassLensStrength.collectAsState()
    val blurEffectsEnabled = ThemeManager.shouldUseBlurEffects()
    val supportsLiquidGlass = blurEffectsEnabled

    val accentColor = appAccentColor()
    val containerColor = c.card.copy(alpha = if (isDark) 0.32f else 0.28f)
    val solidContainerColor = c.card
    val solidIndicatorColor = if (isDark) {
        Color(0xFF8F98A8).copy(alpha = 0.32f)
    } else {
        Color(0xFF9EA8B8).copy(alpha = 0.24f)
    }
    val navBorderColor = if (isDark) {
        Color.White.copy(alpha = 0.14f)
    } else {
        Color.Black.copy(alpha = 0.07f)
    }

    val tabsBackdrop = rememberLayerBackdrop()

    val tabsCount = 3
    val fixedTabWidth = 92.dp
    val fixedNavWidth = fixedTabWidth * tabsCount + 8.dp

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    var tabWidthPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var totalWidthPx by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

    val blurRadiusPx = with(density) { blurDp.dp.toPx() }
    val lensRadiusPx = with(density) { 16f.dp.toPx() }
    val indicatorLensRadiusXPx = with(density) { 6f.dp.toPx() }
    val indicatorLensRadiusYPx = with(density) { 8f.dp.toPx() }

    val offsetAnimation = remember { Animatable(0f) }
    val panelOffset by remember(density) {
        derivedStateOf {
            if (totalWidthPx == 0f) 0f else {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                with(density) { 4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }
    }

    var isDragging by remember { androidx.compose.runtime.mutableStateOf(false) }
    var dragVisualIndex by remember { mutableIntStateOf(mainPagerState.selectedPage) }
    val isPagerBusy = mainPagerState.pagerState.isScrollInProgress
    val isTabClickEnabled = !isDragging
    val isDragEnabled = supportsLiquidGlass && !isPagerBusy

    val dampedDragAnimation = remember(animationScope, tabsCount, density, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = mainPagerState.selectedPage.toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            onDragStarted = {},
            onDragStopped = {
                isDragging = false
                val pagerBusyNow = mainPagerState.isNavigating || mainPagerState.pagerState.isScrollInProgress
                val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                animateToValue(targetIndex.toFloat())
                dragVisualIndex = targetIndex
                animationScope.launch {
                    offsetAnimation.animateTo(
                        0f,
                        spring(1f, 300f, 0.5f)
                    )
                }
                if (pagerBusyNow) return@DampedDragAnimation
                mainPagerState.animateToPage(targetIndex)
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0f) {
                    isDragging = true
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    dragVisualIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            }
        )
    }

    LaunchedEffect(mainPagerState.selectedPage) {
        if (isDragging) return@LaunchedEffect
        dampedDragAnimation.animateToValue(mainPagerState.selectedPage.toFloat())
        dragVisualIndex = mainPagerState.selectedPage
    }

    LaunchedEffect(isPagerBusy) {
        if (isPagerBusy) {
            if (isDragging) {
                isDragging = false
            }
            dampedDragAnimation.release()
            animationScope.launch {
                offsetAnimation.animateTo(
                    0f,
                    spring(1f, 300f, 0.5f)
                )
            }
        }
    }

    val visualSelectedIndex = if (isDragging) dragVisualIndex else mainPagerState.selectedPage

    val interactiveHighlight = remember(animationScope, tabWidthPx) {
        InteractiveHighlight(
            animationScope = animationScope,
            position = { size, _ ->
                Offset(
                    if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset
                    else size.width - (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset,
                    size.height / 2f
                )
            }
        )
    }

    Box(
        modifier = modifier.width(fixedNavWidth),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            Modifier
                .onGloballyPositioned { coords ->
                    totalWidthPx = coords.size.width.toFloat()
                    val contentWidthPx = totalWidthPx - with(density) { 8f.dp.toPx() }
                    tabWidthPx = if (tabsCount > 0) contentWidthPx / tabsCount else 0f
                }
                .graphicsLayer { translationX = panelOffset }
                .fillMaxWidth()
                .then(
                    if (supportsLiquidGlass) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { pillShape },
                            effects = {
                                if (translucent) {
                                    vibrancy()
                                }
                                blur(blurRadiusPx)
                                if (translucent) {
                                    val s = lensStrength.coerceIn(0f, 2f)
                                    lens((12f * s).dp.toPx(), (24f * s).dp.toPx())
                                }
                            },
                            layerBlock = {
                                val progress = dampedDragAnimation.pressProgress
                                val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                                scaleX = scale
                                scaleY = scale
                            },
                            onDrawSurface = {
                                drawRect(containerColor)
                                drawRect(
                                    if (isDark) Color.White.copy(alpha = 0.04f)
                                    else Color.Black.copy(alpha = 0.02f)
                                )
                            }
                        )
                    } else {
                        Modifier
                            .clip(pillShape)
                            .background(solidContainerColor)
                            .border(1.dp, navBorderColor, pillShape)
                    }
                )
                .then(if (supportsLiquidGlass) interactiveHighlight.modifier else Modifier)
                .then(if (isDragEnabled) interactiveHighlight.gestureModifier else Modifier)
                .height(64f.dp)
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabItem(
                index = 0,
                selectedIndex = visualSelectedIndex,
                icon = Icons.Filled.Home,
                label = stringResource(MainTab.Home.labelResId),
                selectedColor = accentColor,
                enabled = isTabClickEnabled,
                onClick = {
                    if (isTabClickEnabled) mainPagerState.animateToPage(0)
                }
            )
            TabItem(
                index = 1,
                selectedIndex = visualSelectedIndex,
                icon = Icons.Filled.Build,
                label = stringResource(MainTab.Tools.labelResId),
                selectedColor = accentColor,
                enabled = isTabClickEnabled,
                onClick = {
                    if (isTabClickEnabled) mainPagerState.animateToPage(1)
                }
            )
            TabItem(
                index = 2,
                selectedIndex = visualSelectedIndex,
                icon = Icons.Filled.Settings,
                label = stringResource(MainTab.Settings.labelResId),
                selectedColor = accentColor,
                enabled = isTabClickEnabled,
                onClick = {
                    if (isTabClickEnabled) mainPagerState.animateToPage(2)
                }
            )
        }

        if (supportsLiquidGlass) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .fillMaxWidth()
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { pillShape },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            val s = lensStrength.coerceIn(0f, 2f)
                            if (translucent) {
                                vibrancy()
                            }
                            blur(blurRadiusPx)
                            if (translucent) {
                                lens(
                                    lensRadiusPx * s * progress,
                                    lensRadiusPx * s * progress
                                )
                            }
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        layerBlock = {
                            val progress = dampedDragAnimation.pressProgress
                            val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                            scaleX = scale
                            scaleY = scale
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(56f.dp)
                    .padding(horizontal = 4f.dp)
                    .drawWithContent {
                        val paint = Paint().apply {
                            colorFilter = ColorFilter.tint(accentColor)
                        }
                        drawIntoCanvas { canvas ->
                            canvas.saveLayer(Rect(Offset.Zero, size), paint)
                            drawContent()
                            canvas.restore()
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                TabItem(
                    index = 0,
                    selectedIndex = visualSelectedIndex,
                    icon = Icons.Filled.Home,
                    label = stringResource(MainTab.Home.labelResId),
                    selectedColor = accentColor,
                    enabled = isTabClickEnabled,
                    onClick = {
                        if (isTabClickEnabled) mainPagerState.animateToPage(0)
                    },
                    isOverlay = true
                )
                TabItem(
                    index = 1,
                    selectedIndex = visualSelectedIndex,
                    icon = Icons.Filled.Build,
                    label = stringResource(MainTab.Tools.labelResId),
                    selectedColor = accentColor,
                    enabled = isTabClickEnabled,
                    onClick = {
                        if (isTabClickEnabled) mainPagerState.animateToPage(1)
                    },
                    isOverlay = true
                )
                TabItem(
                    index = 2,
                    selectedIndex = visualSelectedIndex,
                    icon = Icons.Filled.Settings,
                    label = stringResource(MainTab.Settings.labelResId),
                    selectedColor = accentColor,
                    enabled = isTabClickEnabled,
                    onClick = {
                        if (isTabClickEnabled) mainPagerState.animateToPage(2)
                    },
                    isOverlay = true
                )
            }
        }

        if (tabWidthPx > 0f) {
            Box(
                Modifier
                    .padding(horizontal = 4f.dp)
                    .graphicsLayer {
                        val contentWidth = totalWidthPx - with(density) { 8f.dp.toPx() }
                        val singleTabWidth = contentWidth / tabsCount
                        val progressOffset = dampedDragAnimation.value * singleTabWidth

                        translationX = if (isLtr) {
                            progressOffset + panelOffset
                        } else {
                            -progressOffset + panelOffset
                        }
                    }
                    .then(if (isDragEnabled) interactiveHighlight.gestureModifier else Modifier)
                    .then(if (isDragEnabled) dampedDragAnimation.modifier else Modifier)
                    .then(
                        if (supportsLiquidGlass) {
                            Modifier.drawBackdrop(
                                backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                                shape = { pillShape },
                                effects = {
                                    val progress = dampedDragAnimation.pressProgress
                                    val s = lensStrength.coerceIn(0f, 2f)
                                    if (translucent) {
                                        lens(
                                            indicatorLensRadiusXPx * s * progress,
                                            indicatorLensRadiusYPx * s * progress,
                                            chromaticAberration = true
                                        )
                                    }
                                },
                                highlight = {
                                    val progress = dampedDragAnimation.pressProgress
                                    Highlight.Default.copy(alpha = progress)
                                },
                                shadow = {
                                    val progress = dampedDragAnimation.pressProgress
                                    Shadow(alpha = progress)
                                },
                                innerShadow = {
                                    val progress = dampedDragAnimation.pressProgress
                                    InnerShadow(
                                        radius = 8f.dp * progress,
                                        alpha = progress
                                    )
                                },
                                layerBlock = {
                                    scaleX = dampedDragAnimation.scaleX
                                    scaleY = dampedDragAnimation.scaleY
                                    val velocity = dampedDragAnimation.velocity / 10f
                                    scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                    scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                                },
                                onDrawSurface = {
                                    val progress = dampedDragAnimation.pressProgress
                                    drawRect(
                                        if (!isDark) Color.Black.copy(0.1f)
                                        else Color.White.copy(0.1f),
                                        alpha = 1f - progress
                                    )
                                    drawRect(Color.Black.copy(alpha = 0.03f * progress))
                                }
                            )
                        } else {
                            Modifier
                                .clip(pillShape)
                                .background(solidIndicatorColor)
                        }
                    )
                    .height(56f.dp)
                    .width(with(density) { ((totalWidthPx - 8f.dp.toPx()) / tabsCount).toDp() })
            )
        }
    }
}

@Composable
private fun RowScope.TabItem(
    index: Int,
    selectedIndex: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selectedColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    isOverlay: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val selected = index == selectedIndex
    val isDark = ThemeManager.shouldUseDarkTheme()
    val base = if (isDark) Color.White else Color.Black
    val fg = when {
        isOverlay -> Color.Unspecified
        selected -> selectedColor
        else -> base.copy(alpha = 0.82f)
    }

    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier
                .size(20.dp)
        )
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
