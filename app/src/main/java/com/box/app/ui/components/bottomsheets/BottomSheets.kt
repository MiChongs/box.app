package com.box.app.ui.components.bottomsheets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

import androidx.activity.BackEventCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.collectAsState

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.box.app.data.backend.BoxApi
import com.box.app.data.backend.ProcSampler
import com.box.app.data.backend.ShellExecutor
import com.box.app.data.backend.HomeMetricsApi
import com.box.app.data.model.HomeMetricsState
import com.box.app.data.model.SubscriptionItem
import com.box.app.ui.components.home.SpeedSparkline
import com.box.app.ui.theme.appColors
import com.box.app.utils.ThemeManager
import com.box.app.utils.UiScaleManager
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

val LocalSheetBackdrop = staticCompositionLocalOf<com.kyant.backdrop.Backdrop?> { null }

data class BoxServiceInfo(
    val pid: String = "-",
    val memoryRssMb: Long = 0L,
    val memoryPssMb: Long = 0L,
    val memoryUssMb: Long = 0L,
    val status: String = "Unknown",
    val coreVersion: String = "-",
    val totalMemoryMb: Long = 0L,
    val androidVersion: String = "-",
    val kernelVersion: String = "-",
    val kernelHasIpset: String = "-",
    val ipsetBinaryAvailable: String = "-",
    val cpuAffinity: String = "-",
    val currentCpu: String = "-"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    modifier: Modifier = Modifier,
    shape: Shape = RoundedRectangle(22.dp),
    containerColor: Color = appColors().card,
    scrimColor: Color = Color.Black.copy(alpha = 0.55f),
    tonalElevation: Dp = 0.dp,
    dragHandle: (@Composable (() -> Unit))? = null,
    content: @Composable ColumnScope.() -> Unit
) {

    val backdrop = LocalSheetBackdrop.current
    val supportsLiquidGlass = ThemeManager.shouldUseBlurEffects()
    val effectiveBackdrop = if (supportsLiquidGlass) backdrop else null
    val baseDensity = LocalDensity.current
    val uiScale by UiScaleManager.uiScale.collectAsState()
    val scaledDensity = Density(
        density = baseDensity.density * uiScale,
        fontScale = baseDensity.fontScale * uiScale
    )
    val sheetOuterPadding = 16.dp

    var sheetHeightPx by remember { mutableIntStateOf(0) }
    val dismissProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var predictiveInProgress by remember { mutableStateOf(false) }

    val animatedScrimColor = scrimColor.copy(
        alpha = (scrimColor.alpha * (1f - dismissProgress.value)).coerceIn(0f, 1f)
    )

    val sheetDismissModifier = Modifier
        .onSizeChanged { sheetHeightPx = it.height }
        .graphicsLayer {
            if (sheetHeightPx > 0) {
                translationY = dismissProgress.value * sheetHeightPx.toFloat()
            }
        }

    if (effectiveBackdrop == null) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            modifier = modifier,
            shape = shape,
            properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
            containerColor = Color.Transparent,
            scrimColor = animatedScrimColor,
            tonalElevation = tonalElevation,
            contentWindowInsets = { WindowInsets.navigationBars },
            dragHandle = null,
            content = {
                BackHandler(enabled = !predictiveInProgress) {
                    scope.launch {
                        dismissProgress.stop()
                        dismissProgress.animateTo(1f, animationSpec = tween(120))
                        onDismissRequest()
                    }
                }

                PredictiveBackHandler(enabled = true) { backEvents: kotlinx.coroutines.flow.Flow<BackEventCompat> ->
                    predictiveInProgress = true
                    dismissProgress.stop()
                    val startProgress = dismissProgress.value
                    try {
                        backEvents.collect { ev ->
                            val p = kotlin.math.max(startProgress, ev.progress.coerceIn(0f, 1f))
                            dismissProgress.snapTo(p)
                        }

                        dismissProgress.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(durationMillis = 120)
                        )
                        onDismissRequest()
                    } catch (_: CancellationException) {
                        dismissProgress.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 180)
                        )
                    } finally {
                        predictiveInProgress = false
                    }
                }

                CompositionLocalProvider(LocalDensity provides scaledDensity) {
                    Column(
                        modifier = Modifier
                            .then(sheetDismissModifier)
                            .fillMaxWidth()
                            .padding(
                                start = sheetOuterPadding,
                                end = sheetOuterPadding,
                                bottom = sheetOuterPadding
                            )
                            .clip(shape)
                            .background(containerColor)
                    ) {
                        if (dragHandle != null) {
                            dragHandle()
                        }
                        content()
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        )
        return
    }

    val isDark = ThemeManager.shouldUseDarkTheme()
    val translucent by ThemeManager.liquidGlassTranslucent.collectAsState()
    val blurDp by ThemeManager.liquidGlassBlurDp.collectAsState()
    val lensStrength by ThemeManager.liquidGlassLensStrength.collectAsState()
    val bottomSheetBlur by ThemeManager.bottomSheetBlur.collectAsState()
    val bottomSheetBackdrop = rememberLayerBackdrop()
    val useBackdropBlur = supportsLiquidGlass && bottomSheetBlur
    var backdropTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(useBackdropBlur) {
        if (!useBackdropBlur) return@LaunchedEffect
        while (isActive) {
            delay(180)
            backdropTick++
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        modifier = modifier,
        shape = shape,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = false),
        containerColor = Color.Transparent,
        scrimColor = animatedScrimColor,
        tonalElevation = tonalElevation,
        contentWindowInsets = { WindowInsets.navigationBars },
        dragHandle = null
    ) {
        BackHandler(enabled = !predictiveInProgress) {
            scope.launch {
                dismissProgress.stop()
                dismissProgress.animateTo(1f, animationSpec = tween(120))
                onDismissRequest()
            }
        }

        PredictiveBackHandler(enabled = true) { backEvents: kotlinx.coroutines.flow.Flow<BackEventCompat> ->
            predictiveInProgress = true
            dismissProgress.stop()
            val startProgress = dismissProgress.value
            try {
                backEvents.collect { ev ->
                    val p = kotlin.math.max(startProgress, ev.progress.coerceIn(0f, 1f))
                    dismissProgress.snapTo(p)
                }

                dismissProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 120)
                )
                onDismissRequest()
            } catch (_: CancellationException) {
                dismissProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 180)
                )
            } finally {
                predictiveInProgress = false
            }
        }

        CompositionLocalProvider(LocalDensity provides scaledDensity) {
            Column(
                modifier = Modifier
                    .then(sheetDismissModifier)
                    .fillMaxWidth()
                    .padding(
                        start = sheetOuterPadding,
                        end = sheetOuterPadding,
                        bottom = sheetOuterPadding
                    )
                    .clip(shape)
                    .then(
                        if (useBackdropBlur) {
                            Modifier.drawBackdrop(
                                backdrop = effectiveBackdrop,
                                exportedBackdrop = bottomSheetBackdrop,
                                shape = { shape },
                                effects = {
                                    if (translucent) {
                                        vibrancy()
                                    }
                                    blur(blurDp.dp.toPx())
                                    if (translucent) {
                                        val s = lensStrength.coerceIn(0f, 2f)
                                        lens((12f * s).dp.toPx(), (24f * s).dp.toPx(), isDark)
                                    }
                                },
                                onDrawSurface = {
                                    drawRect(containerColor.copy(alpha = if (isDark) 0.26f else 0.34f))
                                }
                            )
                                // Keep sheet backdrop sampling live while host content updates.
                                .drawWithContent {
                                    backdropTick
                                    drawContent()
                                }
                        } else {
                            Modifier.background(containerColor)
                        }
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    if (dragHandle != null) {
                        dragHandle()
                    }
                    content()
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemBottomSheet(sheetState: SheetState) {
    val c = appColors()
    val scrollState = rememberScrollState()
    val viewParent = LocalView.current.parent
    
    var serviceInfo by remember { mutableStateOf(BoxServiceInfo()) }
    var isLoading by remember { mutableStateOf(true) }
    
    // Refresh service info
    LaunchedEffect(Unit) {
        // Initial load
        serviceInfo = loadBoxServiceInfo()
        isLoading = false
        
        // Periodic updates every 3 seconds
        while (isActive) {
            delay(3000)
            serviceInfo = loadBoxServiceInfo()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 0.dp)
    ) {
        // Handle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clearAndSetSemantics { }
                    .size(width = 28.dp, height = 3.dp)
                    .clip(Capsule())
                    .background(c.divider.copy(alpha = 0.42f))
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInteropFilter { ev ->
                    when (ev.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN,
                        android.view.MotionEvent.ACTION_MOVE -> viewParent?.requestDisallowInterceptTouchEvent(true)
                        android.view.MotionEvent.ACTION_UP,
                        android.view.MotionEvent.ACTION_CANCEL -> viewParent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = c.textSecondary
                    )
                }
            } else {
                // Service Details
                ServiceDetailsCard(serviceInfo)
                
                // System Environment
                SystemEnvironmentCard(serviceInfo)
            }
        }
    }
}

@Composable
fun NetSpeedBottomSheet(metrics: HomeMetricsState) {
    val c = appColors()
    val scrollState = rememberScrollState()
    val viewParent = LocalView.current.parent

    val isDark = ThemeManager.shouldUseDarkTheme()
    val downColor = if (isDark) Color(0xFF79C6FF) else Color(0xFF1E6EA8)
    val upColor = if (isDark) Color(0xFF7DE3B5) else Color(0xFF12936A)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clearAndSetSemantics { }
                    .size(width = 28.dp, height = 3.dp)
                    .clip(Capsule())
                    .background(c.divider.copy(alpha = 0.42f))
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInteropFilter { ev ->
                    when (ev.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN,
                        android.view.MotionEvent.ACTION_MOVE -> viewParent?.requestDisallowInterceptTouchEvent(true)
                        android.view.MotionEvent.ACTION_UP,
                        android.view.MotionEvent.ACTION_CANCEL -> viewParent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedRectangle(18.dp))
                    .background(c.cardAlt.copy(alpha = if (isDark) 0.58f else 0.72f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.bottomsheet_net_speed_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = c.textPrimary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SpeedStatPill(
                            icon = Icons.Filled.ArrowDownward,
                            label = stringResource(R.string.bottomsheet_net_speed_down),
                            value = metrics.netDown,
                            tint = downColor,
                            modifier = Modifier.weight(1f)
                        )
                        SpeedStatPill(
                            icon = Icons.Filled.ArrowUpward,
                            label = stringResource(R.string.bottomsheet_net_speed_up),
                            value = metrics.netUp,
                            tint = upColor,
                            modifier = Modifier.weight(1f)
                        )
                    }
            
            Spacer(modifier = Modifier.height(12.dp))

                    SpeedSparkline(
                        downSeries = metrics.netDownHistory,
                        upSeries = metrics.netUpHistory,
                        downColor = downColor,
                        upColor = upColor,
                        strokeWidth = 3.5f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(132.dp)
                    )
                }
            }

            if (metrics.useClashApiForNetSpeed) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedRectangle(18.dp))
                        .background(c.cardAlt.copy(alpha = if (isDark) 0.58f else 0.72f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.bottomsheet_net_speed_details),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textPrimary
                        )

                        FastestConnectionGroup(
                            icon = Icons.Filled.ArrowDownward,
                            iconTint = downColor,
                            title = stringResource(R.string.bottomsheet_net_speed_fastest_download),
                            speed = metrics.netFastestDownSpeed,
                            host = metrics.netFastestDownHost,
                            chains = metrics.netFastestDownChains
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(c.divider.copy(alpha = 0.55f))
                        )

                        FastestConnectionGroup(
                            icon = Icons.Filled.ArrowUpward,
                            iconTint = upColor,
                            title = stringResource(R.string.bottomsheet_net_speed_fastest_upload),
                            speed = metrics.netFastestUpSpeed,
                            host = metrics.netFastestUpHost,
                            chains = metrics.netFastestUpChains
                        )
                    }
                }
            }

        }
    }
}

@Composable
fun SubscriptionBottomSheet(
    items: List<SubscriptionItem>,
    onRefresh: (String) -> Unit,
    isClashApiEnabled: Boolean,
    onOpenToolsSubscription: () -> Unit
) {
    val c = appColors()
    val isDark = ThemeManager.shouldUseDarkTheme()
    val normalColor = if (isDark) Color(0xFF58A6FF) else Color(0xFF0969DA)
    val warnColor = if (isDark) Color(0xFFD29922) else Color(0xFF9A6700)
    val criticalColor = if (isDark) Color(0xFFFF7B72) else Color(0xFFCF222E)

    val viewParent = LocalView.current.parent
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clearAndSetSemantics { }
                    .size(width = 28.dp, height = 3.dp)
                    .clip(Capsule())
                    .background(c.divider.copy(alpha = 0.42f))
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (!isClashApiEnabled) {
            OutlinedButton(
                onClick = onOpenToolsSubscription,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedRectangle(12.dp),
                border = BorderStroke(1.dp, c.divider.copy(alpha = if (isDark) 0.24f else 0.32f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = c.textPrimary
                )
            ) {
                Text(
                    text = stringResource(R.string.bottomsheet_subscription_go_tools),
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInteropFilter { ev ->
                    when (ev.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN,
                        android.view.MotionEvent.ACTION_MOVE -> viewParent?.requestDisallowInterceptTouchEvent(true)
                        android.view.MotionEvent.ACTION_UP,
                        android.view.MotionEvent.ACTION_CANCEL -> viewParent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
                .verticalScroll(scrollState)
        ) {
            if (items.isEmpty()) {
                Text(
                    text = stringResource(R.string.bottomsheet_subscription_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary
                )
            } else {
                items.forEachIndexed { idx, item ->
                    val used = item.uploadBytes + item.downloadBytes
                    val remain = (item.totalBytes - used).coerceAtLeast(0L)
                    val progress = if (item.totalBytes > 0L) {
                        (used.toDouble() / item.totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    val remainRatio = if (item.totalBytes > 0L) (remain.toDouble() / item.totalBytes.toDouble()) else 1.0
                    val daysLeft = subscriptionDaysUntilExpiry(item.expiryDate)

                    val statusColor = when {
                        (daysLeft != null && daysLeft < 0) || remainRatio <= 0.05 -> criticalColor
                        (daysLeft != null && daysLeft <= 3) || remainRatio <= 0.10 -> criticalColor
                        (daysLeft != null && daysLeft <= 7) || remainRatio <= 0.20 -> warnColor
                        else -> normalColor
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedRectangle(18.dp))
                            .background(c.cardAlt.copy(alpha = if (isDark) 0.58f else 0.72f))
                    ) {
                        Column(
                            modifier = Modifier.padding(
                                start = 14.dp,
                                end = 14.dp,
                                top = 0.dp,
                                bottom = 14.dp
                            )
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = c.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(
                                    onClick = { onRefresh(item.url) },
                                    enabled = !item.loading,
                                    modifier = Modifier.alpha(if (item.loading) 0.45f else 1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Autorenew,
                                        contentDescription = null,
                                        tint = c.textSecondary
                                    )
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.bottomsheet_subscription_expires, subscriptionFormatExpiryWithDays(item.expiryDate)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = stringResource(R.string.bottomsheet_subscription_updated, subscriptionFormatTimeHm(item.lastUpdatedAtMs)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = HomeMetricsApi.formatBytes(item.uploadBytes),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = c.textPrimary
                                    )
                                    Text(text = stringResource(R.string.bottomsheet_subscription_upload), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                                }
                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = HomeMetricsApi.formatBytes(item.downloadBytes),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = c.textPrimary
                                    )
                                    Text(text = stringResource(R.string.bottomsheet_subscription_download), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                                }
                                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = HomeMetricsApi.formatBytes(remain),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = c.textPrimary
                                    )
                                    Text(text = stringResource(R.string.bottomsheet_subscription_remaining), style = MaterialTheme.typography.bodySmall, color = c.textSecondary)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = stringResource(R.string.bottomsheet_subscription_used, HomeMetricsApi.formatBytes(used)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textSecondary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = stringResource(R.string.bottomsheet_subscription_total, HomeMetricsApi.formatBytes(item.totalBytes)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textSecondary
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedRectangle(6.dp)),
                                color = statusColor,
                                trackColor = c.divider.copy(alpha = if (isDark) 0.14f else 0.20f)
                            )
                        }
                    }
                    
                    if (idx != items.lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun subscriptionFormatTimeHm(ms: Long): String {
    if (ms <= 0L) return "-"

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)

    val updatedDate = runCatching {
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    }.getOrNull() ?: return "-"

    val timePart = runCatching {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
    }.getOrNull() ?: return "-"

    return when {
        updatedDate == today -> timePart
        updatedDate == today.minusDays(1) -> stringResource(R.string.common_yesterday_time, timePart)
        updatedDate.year == today.year -> runCatching {
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
        }.getOrNull() ?: timePart
        else -> runCatching {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
        }.getOrNull() ?: timePart
    }
}

private fun subscriptionFormatExpiryWithDays(expiryDate: String): String {
    val d = expiryDate.trim()
    if (d.isBlank() || d == "-") return "-"
    return try {
        val expiry = LocalDate.parse(d)
        val today = LocalDate.now()
        val days = ChronoUnit.DAYS.between(today, expiry).toInt()
        when {
            days < 0 -> "$d (expired)"
            else -> "$d (+${days}d)"
        }
    } catch (_: Exception) {
        d
    }
}

private fun subscriptionDaysUntilExpiry(expiryDate: String): Int? {
    val d = expiryDate.trim()
    if (d.isBlank() || d == "-") return null
    return try {
        val expiry = LocalDate.parse(d)
        val today = LocalDate.now()
        ChronoUnit.DAYS.between(today, expiry).toInt()
    } catch (_: Exception) {
        null
    }
}

@Composable
fun SpeedStatPill(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val c = appColors()
    val isDark = ThemeManager.shouldUseDarkTheme()
    val bg = tint.copy(alpha = if (isDark) 0.16f else 0.10f)

    Box(
        modifier = modifier
            .clip(RoundedRectangle(16.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
            
            Spacer(modifier = Modifier.size(12.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textSecondary
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = c.textPrimary
                )
            }
        }
    }
}

@Composable
fun FastestConnectionGroup(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    speed: String,
    host: String,
    chains: String
) {
    val c = appColors()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(14.dp)
            )

            Spacer(modifier = Modifier.size(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = speed,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = iconTint
            )
        }

        Text(
            text = host,
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
            modifier = Modifier.padding(start = 22.dp)
        )

        Text(
            text = chains,
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
            modifier = Modifier.padding(start = 22.dp)
        )
    }
}

@Composable
private fun ServiceDetailsCard(serviceInfo: BoxServiceInfo) {
    val c = appColors()
    val isDark = ThemeManager.shouldUseDarkTheme()
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedRectangle(18.dp))
            .background(c.cardAlt.copy(alpha = if (isDark) 0.58f else 0.72f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.bottomsheet_service_details_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary
            )
            
            ServiceInfoRow(
                icon = Icons.Filled.Computer,
                label = stringResource(R.string.bottomsheet_service_details_pid),
                value = serviceInfo.pid
            )
            
            ServiceInfoRow(
                icon = Icons.Filled.Memory,
                label = stringResource(R.string.bottomsheet_service_details_memory),
                value = when {
                    serviceInfo.memoryRssMb > 0 && serviceInfo.memoryPssMb > 0 && serviceInfo.memoryUssMb > 0 ->
                        "RSS ${serviceInfo.memoryRssMb}MB / PSS ${serviceInfo.memoryPssMb}MB / USS ${serviceInfo.memoryUssMb}MB"
                    serviceInfo.memoryRssMb > 0 && serviceInfo.memoryPssMb > 0 -> "RSS ${serviceInfo.memoryRssMb}MB / PSS ${serviceInfo.memoryPssMb}MB"
                    serviceInfo.memoryRssMb > 0 -> "RSS ${serviceInfo.memoryRssMb}MB"
                    serviceInfo.memoryPssMb > 0 -> "PSS ${serviceInfo.memoryPssMb}MB"
                    serviceInfo.memoryUssMb > 0 -> "USS ${serviceInfo.memoryUssMb}MB"
                    else -> "-"
                }
            )
            
            if (serviceInfo.coreVersion != "-" && serviceInfo.coreVersion.isNotBlank()) {
                ServiceInfoRow(
                    icon = Icons.Filled.Info,
                    label = stringResource(R.string.bottomsheet_service_details_core_version),
                    value = serviceInfo.coreVersion
                )
            }
            
            // CPU Affinity and Current Core with different icons
            if (serviceInfo.cpuAffinity != "-") {
                ServiceInfoRow(
                    icon = Icons.Filled.Tune,
                    label = stringResource(R.string.bottomsheet_service_details_cpu_affinity),
                    value = serviceInfo.cpuAffinity
                )
            }
            
            if (serviceInfo.currentCpu != "-") {
                ServiceInfoRow(
                    icon = Icons.Filled.CenterFocusStrong,
                    label = stringResource(R.string.bottomsheet_service_details_current_cpu),
                    value = "Core ${serviceInfo.currentCpu}"
                )
            }
        }
    }
}

@Composable
private fun SystemEnvironmentCard(serviceInfo: BoxServiceInfo) {
    val c = appColors()
    val isDark = ThemeManager.shouldUseDarkTheme()
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedRectangle(18.dp))
            .background(c.cardAlt.copy(alpha = if (isDark) 0.58f else 0.72f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.bottomsheet_system_environment_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary
            )
            
            ServiceInfoRow(
                icon = Icons.Filled.Android,
                label = stringResource(R.string.bottomsheet_system_environment_android),
                value = serviceInfo.androidVersion
            )
            
            ServiceInfoRow(
                icon = Icons.Filled.Computer,
                label = stringResource(R.string.bottomsheet_system_environment_kernel),
                value = serviceInfo.kernelVersion
            )
            
            ServiceInfoRow(
                icon = Icons.Filled.Code,
                label = stringResource(R.string.bottomsheet_system_environment_ipset),
                value = run {
                    val hasKernel = serviceInfo.kernelHasIpset.equals("yes", ignoreCase = true)
                    val hasBin = serviceInfo.ipsetBinaryAvailable.equals("yes", ignoreCase = true)
                    when {
                        !hasKernel -> stringResource(R.string.bottomsheet_system_environment_ipset_status_not_supported)
                        hasBin -> stringResource(R.string.bottomsheet_system_environment_ipset_status_available)
                        else -> stringResource(R.string.bottomsheet_system_environment_ipset_status_missing_binary)
                    }
                }
            )
            
            if (serviceInfo.totalMemoryMb > 0) {
                ServiceInfoRow(
                    icon = Icons.Filled.Memory,
                    label = stringResource(R.string.bottomsheet_system_environment_total_memory),
                    value = "${serviceInfo.totalMemoryMb}MB"
                )
            }
        }
    }
}

@Composable
private fun ServiceInfoRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    val c = appColors()
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = c.textSecondary,
            modifier = Modifier.size(20.dp)
        )
        
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 170.dp)
        )
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = c.textPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .wrapContentWidth(Alignment.End)
        )
    }
}

private suspend fun loadBoxServiceInfo(): BoxServiceInfo {
    var pid = "-"
    var memoryRssMb = 0L
    var memoryPssMb = 0L
    var memoryUssMb = 0L
    var status = "Unknown"
    var coreName = "-"
    var coreVersion = "-"
    var totalMemoryMb = 0L
    var androidVersion = "-"
    var kernelVersion = "-"
    var kernelHasIpset = "-"
    var ipsetBinaryAvailable = "-"
    var cpuAffinity = "-"
    var currentCpu = "-"
    
    // Get Box service PID and status
    val pidResult = BoxApi.getPid()
    val pidValue = pidResult.stdout.trim().takeIf { it.isNotBlank() && it.all(Char::isDigit) }
    
    if (pidValue != null) {
        pid = pidValue
        status = "Running"
        
        // Read current core name from settings.ini (bin_name)
        runCatching {
            val coreRes = ShellExecutor.execute("grep -m1 '^bin_name=' /data/adb/box/settings.ini 2>/dev/null | head -n 1")
            val raw = coreRes.stdout.trim().substringAfter("=", "").trim().trim('"')
            coreName = raw.ifBlank { "-" }
        }

        // Resolve core binary and read version
        if (coreName != "-") {
            val cmd = """
                sh -c '
                p=""
                if command -v "$coreName" >/dev/null 2>&1; then p="$coreName"; fi
                if [ -z "${'$'}p" ] && [ -x "/data/adb/box/$coreName/$coreName" ]; then p="/data/adb/box/$coreName/$coreName"; fi
                if [ -z "${'$'}p" ] && [ -x "/data/adb/box/bin/$coreName" ]; then p="/data/adb/box/bin/$coreName"; fi
                if [ -z "${'$'}p" ]; then exit 0; fi

                v=""
                v="${'$'}(${'$'}p --version 2>/dev/null | head -n 1)"
                [ -z "${'$'}v" ] && v="${'$'}(${'$'}p -v 2>/dev/null | head -n 1)"
                [ -z "${'$'}v" ] && v="${'$'}(${'$'}p version 2>/dev/null | head -n 1)"
                echo "${'$'}v"
                '
            """.trimIndent()
            val vRes = ShellExecutor.execute(cmd)
            coreVersion = vRes.stdout.trim().ifBlank { "-" }
        }
        
        // Get process memory usage (RSS + USS)
        val sep = "\n---SEP---\n"
        val memCmd = """
            grep '^VmRSS:' /proc/$pidValue/status 2>/dev/null | head -n 1
            echo '$sep'
            cat /proc/$pidValue/smaps_rollup 2>/dev/null | grep -E '^(Pss|Private_Clean|Private_Dirty):' || true
        """.trimIndent()
        val memRes = ShellExecutor.execute(memCmd)
        if (memRes.exitCode == 0 && memRes.stdout.isNotBlank()) {
            val memParts = memRes.stdout.split(sep)
            val rssLine = memParts.getOrNull(0)?.trim().orEmpty()
            val ussLines = memParts.getOrNull(1)?.trim().orEmpty()

            val rssKb = rssLine.split(Regex("\\s+")).getOrNull(1)?.toLongOrNull()
            if (rssKb != null) {
                memoryRssMb = rssKb / 1024
            }

            if (ussLines.isNotBlank()) {
                val pssKb = Regex("^Pss:\\s+(\\d+)\\s+kB$", RegexOption.MULTILINE)
                    .find(ussLines)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toLongOrNull()
                if (pssKb != null && pssKb > 0L) {
                    memoryPssMb = pssKb / 1024
                }

                val privateCleanKb = Regex("^Private_Clean:\\s+(\\d+)\\s+kB$", RegexOption.MULTILINE)
                    .find(ussLines)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toLongOrNull()
                val privateDirtyKb = Regex("^Private_Dirty:\\s+(\\d+)\\s+kB$", RegexOption.MULTILINE)
                    .find(ussLines)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toLongOrNull()
                val totalPrivateKb = (privateCleanKb ?: 0L) + (privateDirtyKb ?: 0L)
                if (totalPrivateKb > 0L) {
                    memoryUssMb = totalPrivateKb / 1024
                }
            }
        }
        
        // Get CPU affinity (which cores the process is allowed to run on)
        val affinityResult = ShellExecutor.execute("taskset -p $pidValue 2>/dev/null")
        if (affinityResult.exitCode == 0) {
            // Parse taskset output like "pid 1234's current affinity mask: f"
            val affinityLine = affinityResult.stdout.trim()
            val maskMatch = Regex("affinity mask: ([0-9a-fA-F]+)").find(affinityLine)
            if (maskMatch != null) {
                val mask = maskMatch.groupValues[1]
                try {
                    val maskInt = mask.toLong(16)
                    val cores = mutableListOf<Int>()
                    for (i in 0 until 32) { // Check up to 32 cores
                        if ((maskInt and (1L shl i)) != 0L) {
                            cores.add(i)
                        }
                    }
                    cpuAffinity = if (cores.isNotEmpty()) {
                        cores.joinToString(",")
                    } else {
                        "0x$mask"
                    }
                } catch (e: Exception) {
                    cpuAffinity = "0x$mask"
                }
            }
        }
        
        // Get current CPU core (which core the process is currently running on)
        val statResult = ShellExecutor.execute("cat /proc/$pidValue/stat 2>/dev/null")
        if (statResult.exitCode == 0) {
            val parts = statResult.stdout.split(Regex("\\s+"))
            // The processor field is at index 38 (0-based) in /proc/pid/stat
            if (parts.size > 38) {
                val processorId = parts[38].toIntOrNull()
                if (processorId != null && processorId >= 0) {
                    currentCpu = processorId.toString()
                }
            }
        }
        
        // Alternative method to get current CPU using ps command
        if (currentCpu == "-") {
            val psResult = ShellExecutor.execute("ps -o pid,psr -p $pidValue 2>/dev/null | tail -n 1")
            if (psResult.exitCode == 0) {
                val psLine = psResult.stdout.trim()
                val parts = psLine.split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val processorId = parts[1].toIntOrNull()
                    if (processorId != null && processorId >= 0) {
                        currentCpu = processorId.toString()
                    }
                }
            }
        }
    } else {
        status = "Stopped"
    }
    
    // Get total system memory
    val memInfoResult = ShellExecutor.execute("grep '^MemTotal:' /proc/meminfo")
    if (memInfoResult.exitCode == 0) {
        val kb = memInfoResult.stdout.split(Regex("\\s+")).getOrNull(1)?.toLongOrNull()
        if (kb != null) {
            totalMemoryMb = kb / 1024
        }
    }
    
    // Get Android version
    val androidResult = ShellExecutor.execute("getprop ro.build.version.release")
    if (androidResult.exitCode == 0) {
        androidVersion = androidResult.stdout.trim()
    }
    
    // Get kernel version
    val kernelResult = ShellExecutor.execute("uname -r")
    if (kernelResult.exitCode == 0) {
        kernelVersion = kernelResult.stdout.trim()
    }

    val ipsetKernelRes = ShellExecutor.execute(
        "sh -c '" +
            "if [ -r /proc/config.gz ]; then " +
            "  if ( (command -v zcat >/dev/null 2>&1 && zcat /proc/config.gz 2>/dev/null) || (command -v gzip >/dev/null 2>&1 && gzip -dc /proc/config.gz 2>/dev/null) ) | grep -q \"^CONFIG_IP_SET=[ym]$\"; then " +
            "    echo yes; " +
            "  else " +
            "    echo no; " +
            "  fi; " +
            "else " +
            "  echo no; " +
            "fi'"
    )
    if (ipsetKernelRes.exitCode == 0) {
        kernelHasIpset = ipsetKernelRes.stdout.trim().ifBlank { kernelHasIpset }
    }

    val ipsetBinRes = ShellExecutor.execute("command -v ipset >/dev/null 2>&1 && echo yes || echo no")
    if (ipsetBinRes.exitCode == 0) {
        ipsetBinaryAvailable = ipsetBinRes.stdout.trim().ifBlank { ipsetBinaryAvailable }
    }
    
    return BoxServiceInfo(
        pid = pid,
        memoryRssMb = memoryRssMb,
        memoryPssMb = memoryPssMb,
        memoryUssMb = memoryUssMb,
        status = status,
        coreVersion = coreVersion,
        totalMemoryMb = totalMemoryMb,
        androidVersion = androidVersion,
        kernelVersion = kernelVersion,
        kernelHasIpset = kernelHasIpset,
        ipsetBinaryAvailable = ipsetBinaryAvailable,
        cpuAffinity = cpuAffinity,
        currentCpu = currentCpu
    )
}

private enum class UpdateDownloadUiState { Idle, Downloading, Success, Failed }
enum class UpdateSheetMode { MODULE, APP }

@Composable
fun UpdateBottomSheet(
    updateResult: com.box.app.data.model.UpdateCheckResult,
    mode: UpdateSheetMode = UpdateSheetMode.MODULE,
    onDismiss: () -> Unit = {},
    onDownload: suspend (com.box.app.data.model.ReleaseInfo, (Int?) -> Unit) -> Boolean = { _, _ -> false },
    onOpenInBrowser: (String) -> Unit = {}
) {
    val c = appColors()
    val isDark = ThemeManager.shouldUseDarkTheme()
    val scrollState = rememberScrollState()
    val viewParent = LocalView.current.parent
    val scope = rememberCoroutineScope()
    var activeDownloadTag by remember { mutableStateOf<String?>(null) }
    var activeDownloadState by remember { mutableStateOf(UpdateDownloadUiState.Idle) }
    var activeDownloadProgressPercent by remember { mutableStateOf<Int?>(null) }

    fun launchDownload(release: com.box.app.data.model.ReleaseInfo) {
        if (release.downloadUrl.isBlank()) return
        if (activeDownloadState == UpdateDownloadUiState.Downloading) return

        activeDownloadTag = release.tag
        activeDownloadState = UpdateDownloadUiState.Downloading
        activeDownloadProgressPercent = 0

        scope.launch {
            val ok = runCatching {
                onDownload(release) { percent ->
                    scope.launch {
                        if (activeDownloadTag == release.tag && activeDownloadState == UpdateDownloadUiState.Downloading) {
                            activeDownloadProgressPercent = percent?.coerceIn(0, 100)
                        }
                    }
                }
            }.getOrDefault(false)
            activeDownloadState = if (ok) UpdateDownloadUiState.Success else UpdateDownloadUiState.Failed
            delay(1500)
            if (activeDownloadTag == release.tag) {
                activeDownloadTag = null
                activeDownloadState = UpdateDownloadUiState.Idle
                activeDownloadProgressPercent = null
            }
        }
    }

    fun currentDownloadStateFor(release: com.box.app.data.model.ReleaseInfo): UpdateDownloadUiState {
        return if (activeDownloadTag == release.tag) activeDownloadState else UpdateDownloadUiState.Idle
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clearAndSetSemantics { }
                    .size(width = 28.dp, height = 3.dp)
                    .clip(Capsule())
                    .background(c.divider.copy(alpha = 0.42f))
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInteropFilter { ev ->
                    when (ev.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN,
                        android.view.MotionEvent.ACTION_MOVE -> viewParent?.requestDisallowInterceptTouchEvent(true)
                        android.view.MotionEvent.ACTION_UP,
                        android.view.MotionEvent.ACTION_CANCEL -> viewParent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = when {
                    !updateResult.hasUpdate -> stringResource(R.string.bottomsheet_no_updates_available)
                    mode == UpdateSheetMode.APP -> stringResource(R.string.bottomsheet_app_update_available)
                    else -> stringResource(R.string.bottomsheet_update_available)
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = c.textPrimary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedRectangle(18.dp))
                    .background(c.cardAlt.copy(alpha = if (isDark) 0.58f else 0.72f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.bottomsheet_current_version),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = c.textPrimary
                    )
                    Text(
                        text = updateResult.currentVersion,
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.textSecondary
                    )
                }
            }

            val appRelease = if (mode == UpdateSheetMode.APP) {
                updateResult.recommendedRelease
                    ?: updateResult.stableRelease
                    ?: updateResult.prereleaseRelease
            } else {
                null
            }

            if (mode == UpdateSheetMode.APP) {
                if (appRelease != null) {
                    UpdateAppReleaseCard(
                        release = appRelease,
                        noAssetText = stringResource(R.string.bottomsheet_no_app_asset),
                        isDownloading = activeDownloadTag == appRelease.tag && activeDownloadState == UpdateDownloadUiState.Downloading,
                        downloadProgressPercent = if (activeDownloadTag == appRelease.tag) activeDownloadProgressPercent else null,
                        downloadState = currentDownloadStateFor(appRelease),
                        onDownload = { launchDownload(appRelease) },
                        onOpenInBrowser = { onOpenInBrowser(appRelease.url) }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedRectangle(18.dp))
                            .background(c.cardAlt.copy(alpha = if (isDark) 0.58f else 0.72f))
                    ) {
                        Text(
                            text = stringResource(R.string.bottomsheet_no_app_asset),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textSecondary,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                updateResult.stableRelease?.let { release ->
                    UpdateReleaseCard(
                        title = if (updateResult.recommendedRelease == release) {
                            stringResource(R.string.bottomsheet_release_stable_recommended)
                        } else {
                            stringResource(R.string.bottomsheet_release_stable)
                        },
                        release = release,
                        isRecommended = updateResult.recommendedRelease == release,
                        showRecommendedBadge = true,
                        showReleaseType = true,
                        showPrereleaseWarning = true,
                        versionText = release.tag,
                        downloadButtonText = stringResource(R.string.bottomsheet_download_module),
                        noAssetText = stringResource(R.string.bottomsheet_no_module_asset),
                        isDownloading = activeDownloadTag == release.tag && activeDownloadState == UpdateDownloadUiState.Downloading,
                        downloadProgressPercent = if (activeDownloadTag == release.tag) activeDownloadProgressPercent else null,
                        downloadState = currentDownloadStateFor(release),
                        onDownload = { launchDownload(release) },
                        onOpenInBrowser = { onOpenInBrowser(release.url) }
                    )
                }

                updateResult.prereleaseRelease?.let { release ->
                    UpdateReleaseCard(
                        title = if (updateResult.recommendedRelease == release) {
                            stringResource(R.string.bottomsheet_release_prerelease_recommended)
                        } else {
                            stringResource(R.string.bottomsheet_release_prerelease)
                        },
                        release = release,
                        isRecommended = updateResult.recommendedRelease == release,
                        showRecommendedBadge = true,
                        showReleaseType = true,
                        showPrereleaseWarning = true,
                        versionText = release.tag,
                        downloadButtonText = stringResource(R.string.bottomsheet_download_module),
                        noAssetText = stringResource(R.string.bottomsheet_no_module_asset),
                        isDownloading = activeDownloadTag == release.tag && activeDownloadState == UpdateDownloadUiState.Downloading,
                        downloadProgressPercent = if (activeDownloadTag == release.tag) activeDownloadProgressPercent else null,
                        downloadState = currentDownloadStateFor(release),
                        onDownload = { launchDownload(release) },
                        onOpenInBrowser = { onOpenInBrowser(release.url) }
                    )
                }
            }

            val noModuleRelease = updateResult.stableRelease == null &&
                updateResult.prereleaseRelease == null &&
                updateResult.recommendedRelease == null
            val shouldShowLatestCard = if (mode == UpdateSheetMode.APP) {
                !updateResult.hasUpdate
            } else {
                !updateResult.hasUpdate && noModuleRelease
            }

            if (shouldShowLatestCard) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedRectangle(18.dp))
                        .background(c.cardAlt.copy(alpha = if (isDark) 0.58f else 0.72f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "🎉",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = if (mode == UpdateSheetMode.APP) {
                                stringResource(R.string.bottomsheet_app_latest_version_title)
                            } else {
                                stringResource(R.string.bottomsheet_latest_version_title)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textPrimary
                        )
                        Text(
                            text = stringResource(R.string.bottomsheet_no_updates_available),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateAppReleaseCard(
    release: com.box.app.data.model.ReleaseInfo,
    noAssetText: String,
    isDownloading: Boolean,
    downloadProgressPercent: Int?,
    downloadState: UpdateDownloadUiState,
    onDownload: () -> Unit,
    onOpenInBrowser: () -> Unit
) {
    val c = appColors()
    val isDark = ThemeManager.shouldUseDarkTheme()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedRectangle(18.dp))
            .background(c.cardAlt.copy(alpha = if (isDark) 0.55f else 0.75f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.bottomsheet_app_release_latest),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(
                        R.string.bottomsheet_release_version,
                        release.name.ifBlank { release.tag }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textPrimary
                )

                if (release.publishedAt.isNotBlank()) {
                    val formattedDate = formatPublishDate(release.publishedAt)
                    Text(
                        text = stringResource(R.string.bottomsheet_release_date, formattedDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary
                    )
                }
            }

            if (release.body.isNotBlank()) {
                var notesExpanded by remember(release.tag) { mutableStateOf(false) }
                val formattedBody = formatReleaseBody(release.body)
                val canExpand = formattedBody.length > 280 || formattedBody.count { it == '\n' } > 7

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.bottomsheet_release_notes),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = c.textPrimary
                    )

                    Text(
                        text = formattedBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        maxLines = if (notesExpanded) Int.MAX_VALUE else 10,
                        overflow = if (notesExpanded) TextOverflow.Clip else TextOverflow.Ellipsis
                    )

                    if (canExpand) {
                        TextButton(
                            onClick = { notesExpanded = !notesExpanded }
                        ) {
                            Text(
                                text = if (notesExpanded) {
                                    stringResource(R.string.bottomsheet_show_less)
                                } else {
                                    stringResource(R.string.bottomsheet_show_more)
                                }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onOpenInBrowser,
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedRectangle(12.dp),
                    border = BorderStroke(1.dp, c.divider.copy(alpha = if (isDark) 0.24f else 0.32f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = c.textPrimary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.bottomsheet_view_in_browser),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                androidx.compose.material3.Button(
                    onClick = onDownload,
                    enabled = release.downloadUrl.isNotBlank() && !isDownloading,
                    modifier = Modifier.weight(1f),
                    shape = RoundedRectangle(12.dp)
                ) {
                    Text(
                        text = when {
                            !release.downloadUrl.isNotBlank() -> noAssetText
                            downloadState == UpdateDownloadUiState.Success -> stringResource(R.string.bottomsheet_download_success)
                            downloadState == UpdateDownloadUiState.Failed -> stringResource(R.string.bottomsheet_download_failed)
                            isDownloading -> {
                                val downloading = stringResource(R.string.bottomsheet_downloading)
                                val pct = downloadProgressPercent
                                if (pct != null && pct in 0..100) "$downloading $pct%" else downloading
                            }
                            else -> stringResource(R.string.bottomsheet_download_app)
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateReleaseCard(
    title: String,
    release: com.box.app.data.model.ReleaseInfo,
    isRecommended: Boolean,
    showRecommendedBadge: Boolean,
    showReleaseType: Boolean,
    showPrereleaseWarning: Boolean,
    versionText: String,
    downloadButtonText: String,
    noAssetText: String,
    isDownloading: Boolean,
    downloadProgressPercent: Int?,
    downloadState: UpdateDownloadUiState,
    onDownload: () -> Unit,
    onOpenInBrowser: () -> Unit
) {
    val c = appColors()
    val isDark = ThemeManager.shouldUseDarkTheme()

    val baseColor = if (isRecommended) {
        if (isDark) Color(0xFF1F2937) else Color(0xFFF0F9FF)
    } else {
        c.cardAlt
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedRectangle(18.dp))
            .background(baseColor.copy(alpha = if (isDark) 0.55f else 0.75f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = c.textPrimary
                )

                if (showRecommendedBadge && isRecommended) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isDark) Color(0xFF059669) else Color(0xFF10B981),
                                shape = RoundedRectangle(12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.bottomsheet_release_recommended),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.bottomsheet_release_version, versionText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textPrimary
                )

                if (release.commitSha.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.bottomsheet_release_commit, release.commitSha),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary
                    )
                }

                if (release.publishedAt.isNotBlank()) {
                    val formattedDate = formatPublishDate(release.publishedAt)
                    Text(
                        text = stringResource(R.string.bottomsheet_release_date, formattedDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary
                    )
                }

                if (showReleaseType) {
                    Text(
                        text = stringResource(
                            R.string.bottomsheet_release_type,
                            if (release.isPrerelease) stringResource(R.string.bottomsheet_release_type_prerelease) else stringResource(R.string.bottomsheet_release_type_stable)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary
                    )
                }
            }

            if (release.body.isNotBlank()) {
                var notesExpanded by remember(title, release.tag) { mutableStateOf(false) }
                val formattedBody = formatReleaseBody(release.body)
                val canExpand = formattedBody.length > 280 || formattedBody.count { it == '\n' } > 7

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.bottomsheet_release_notes),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = c.textPrimary
                    )

                    Text(
                        text = formattedBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        maxLines = if (notesExpanded) Int.MAX_VALUE else 10,
                        overflow = if (notesExpanded) TextOverflow.Clip else TextOverflow.Ellipsis
                    )

                    if (canExpand) {
                        TextButton(
                            onClick = { notesExpanded = !notesExpanded }
                        ) {
                            Text(
                                text = if (notesExpanded) {
                                    stringResource(R.string.bottomsheet_show_less)
                                } else {
                                    stringResource(R.string.bottomsheet_show_more)
                                }
                            )
                        }
                    }
                }
            }

            if (showPrereleaseWarning && release.isPrerelease) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (isDark) Color(0xFF92400E).copy(alpha = 0.2f) else Color(0xFFFEF3C7),
                            shape = RoundedRectangle(8.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "⚠️",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = stringResource(R.string.bottomsheet_release_prerelease_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) Color(0xFFFBBF24) else Color(0xFF92400E)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onOpenInBrowser,
                    enabled = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedRectangle(12.dp),
                    border = BorderStroke(1.dp, c.divider.copy(alpha = if (isDark) 0.24f else 0.32f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = c.textPrimary
                    )
                ) {
                    Text(
                        text = stringResource(R.string.bottomsheet_view_in_browser),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                androidx.compose.material3.Button(
                    onClick = onDownload,
                    enabled = release.downloadUrl.isNotBlank() && !isDownloading,
                    modifier = Modifier.weight(1f),
                    shape = RoundedRectangle(12.dp)
                ) {
                    Text(
                        text = when {
                            !release.downloadUrl.isNotBlank() -> noAssetText
                            downloadState == UpdateDownloadUiState.Success -> stringResource(R.string.bottomsheet_download_success)
                            downloadState == UpdateDownloadUiState.Failed -> stringResource(R.string.bottomsheet_download_failed)
                            isDownloading -> {
                                val downloading = stringResource(R.string.bottomsheet_downloading)
                                val pct = downloadProgressPercent
                                if (pct != null && pct in 0..100) "$downloading $pct%" else downloading
                            }
                            else -> downloadButtonText
                        },
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

private fun formatPublishDate(publishedAt: String): String {
    return try {
        // 绠€鍗曠殑鏃ユ湡鏍煎紡鍖栵紝鍙互鏍规嵁闇€瑕佹敼杩?
        publishedAt.substringBefore("T")
    } catch (e: Exception) {
        publishedAt
    }
}

private fun formatReleaseBody(body: String): String {
    // 绠€鍗曠殑Markdown鏍煎紡鍖?
    return body
        .replace("# ", "")
        .replace("## ", "")
        .replace("### ", "")
        .replace("**", "")
        .replace("*", "")
        .trim()
}

@Composable
fun AboutBottomSheet(
    appVersion: String,
    moduleVersion: String,
    appIcon: Any? = null,
    onModuleClick: () -> Unit = {},
    onChannelClick: () -> Unit = {},
    onAppVersionClick: (() -> Unit)? = null,
    onModuleVersionClick: () -> Unit = {}
) {
    val c = appColors()
    val isDark = ThemeManager.shouldUseDarkTheme()
    val scrollState = rememberScrollState()
    val viewParent = LocalView.current.parent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 0.dp)
    ) {
        // Handle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clearAndSetSemantics { }
                    .size(width = 28.dp, height = 3.dp)
                    .clip(Capsule())
                    .background(c.divider.copy(alpha = 0.42f))
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInteropFilter { ev ->
                    when (ev.actionMasked) {
                        android.view.MotionEvent.ACTION_DOWN,
                        android.view.MotionEvent.ACTION_MOVE -> viewParent?.requestDisallowInterceptTouchEvent(true)
                        android.view.MotionEvent.ACTION_UP,
                        android.view.MotionEvent.ACTION_CANCEL -> viewParent?.requestDisallowInterceptTouchEvent(false)
                    }
                    false
                }
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // App Icon and Title Section
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // App Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedRectangle(18.dp))
                        .background(c.cardAlt),
                    contentAlignment = Alignment.Center
                ) {
                    if (appIcon is Painter) {
                        androidx.compose.foundation.Image(
                            painter = appIcon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedRectangle(14.dp))
                        )
                    } else {
                        // Fallback icon
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedRectangle(14.dp))
                                .background(Color(0xFF2DA44E).copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.bottomsheet_about_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2DA44E)
                            )
                        }
                    }
                }

                // App Name and Version
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.bottomsheet_about_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = c.textPrimary
                    )
                    Text(
                        text = appVersion,
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.textSecondary,
                        modifier = if (onAppVersionClick != null) {
                            Modifier
                                .clip(RoundedRectangle(8.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onAppVersionClick
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        } else {
                            Modifier
                        }
                    )
                }
            }

            // Divider
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.3f)
                    .height(1.dp)
                    .padding(vertical = 2.dp)
                    .background(c.divider.copy(alpha = 0.3f))
            )

            // Version Information Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedRectangle(18.dp))
                    .background(c.cardAlt.copy(alpha = if (isDark) 0.58f else 0.72f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AboutInfoRow(
                        icon = Icons.Filled.Info,
                        label = stringResource(R.string.bottomsheet_about_module),
                        value = stringResource(R.string.bottomsheet_about_value_github),
                        onClick = onModuleClick
                    )
                    
                    AboutInfoRow(
                        icon = Icons.Filled.Code,
                        label = stringResource(R.string.bottomsheet_about_channel),
                        value = stringResource(R.string.bottomsheet_about_value_telegram),
                        onClick = onChannelClick
                    )
                    
                    AboutInfoRow(
                        icon = Icons.Filled.Computer,
                        label = stringResource(R.string.bottomsheet_about_module_version),
                        value = moduleVersion,
                        onClick = onModuleVersionClick
                    )
                    
                    AboutInfoRow(
                        icon = Icons.Filled.Person,
                        label = stringResource(R.string.bottomsheet_about_author),
                        value = stringResource(R.string.bottomsheet_about_value_author)
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutInfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    val c = appColors()
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedRectangle(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null // 绉婚櫎姘存尝绾圭壒鏁?
                        ) { onClick() }
                        .padding(vertical = 2.dp)
                } else {
                    Modifier.padding(vertical = 2.dp)
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = c.textSecondary,
                modifier = Modifier.size(20.dp)
            )
            
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary
            )
        }
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (onClick != null) {
                // 鍙偣鍑婚」鐩娇鐢╝ccent棰滆壊
                Color(0xFF2DA44E)
            } else {
                c.textPrimary
            },
            textAlign = TextAlign.End
        )
    }
}

