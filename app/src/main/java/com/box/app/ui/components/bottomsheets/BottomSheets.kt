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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.box.app.data.backend.BoxApi
import com.box.app.data.backend.ShellExecutor
import com.box.app.data.backend.HomeMetricsApi
import com.box.app.data.model.HomeMetricsState
import com.box.app.data.model.SubscriptionItem
import com.box.app.ui.theme.appColors
import com.box.app.utils.ThemeManager
import com.box.app.utils.UiScaleManager
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.window.WindowBottomSheet

val LocalSheetBackdrop = staticCompositionLocalOf<com.kyant.backdrop.Backdrop?> { null }

// BottomSheet 模糊状态：通过计数器支持多 sheet 叠加
class SheetBlurState {
    var count by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    val isActive: Boolean get() = count > 0
    fun increment() { count++ }
    fun decrement() { count = (count - 1).coerceAtLeast(0) }
}

val LocalSheetBlurState = staticCompositionLocalOf<SheetBlurState?> { null }

/** 在 BottomSheet 组合期间自动注册/注销模糊计数 */
@Composable
fun SheetBlurEffect() {
    val state = LocalSheetBlurState.current ?: return
    androidx.compose.runtime.DisposableEffect(Unit) {
        state.increment()
        onDispose { state.decrement() }
    }
}

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

@Composable
fun AppModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedRectangle(22.dp),
    containerColor: Color = appColors().card,
    scrimColor: Color = Color.Black.copy(alpha = 0.55f),
    tonalElevation: Dp = 0.dp,
    dragHandle: (@Composable (() -> Unit))? = null,
    content: @Composable ColumnScope.() -> Unit
) {

    val blurEnabled = ThemeManager.shouldUseBlurEffects()

    // 注册模糊计数
    if (blurEnabled) SheetBlurEffect()

    val baseDensity = LocalDensity.current
    val uiScale by UiScaleManager.uiScale.collectAsState()
    val scaledDensity = Density(
        density = baseDensity.density * uiScale,
        fontScale = baseDensity.fontScale * uiScale
    )

    WindowBottomSheet(
        show = true,
        modifier = modifier,
        backgroundColor = containerColor,
        enableWindowDim = scrimColor.alpha > 0f,
        cornerRadius = 22.dp,
        outsideMargin = DpSize(16.dp, 16.dp),
        insideMargin = DpSize(0.dp, 0.dp),
        dragHandleColor = Color.Transparent,
        onDismissRequest = onDismissRequest
    ) {
        CompositionLocalProvider(LocalDensity provides scaledDensity) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SystemBottomSheet() {
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
    val scrollState = rememberScrollState()
    val viewParent = LocalView.current.parent

    val isDark = ThemeManager.shouldUseDarkTheme()
    val downColor = if (isDark) Color(0xFF79C6FF) else Color(0xFF1472B6)
    val upColor = if (isDark) Color(0xFF7DE3B5) else Color(0xFF0E8A63)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 0.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

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
            // 实时速度卡片
            top.yukonga.miuix.kmp.basic.Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 18.dp,
                insideMargin = PaddingValues(16.dp),
                colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
                    color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    top.yukonga.miuix.kmp.basic.Text(
                        text = stringResource(R.string.bottomsheet_net_speed_title),
                        style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.Medium,
                        color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        NetSpeedStatCard(
                            icon = Icons.Filled.ArrowDownward,
                            label = stringResource(R.string.bottomsheet_net_speed_down),
                            value = metrics.netDown,
                            tint = downColor,
                            modifier = Modifier.weight(1f)
                        )
                        NetSpeedStatCard(
                            icon = Icons.Filled.ArrowUpward,
                            label = stringResource(R.string.bottomsheet_net_speed_up),
                            value = metrics.netUp,
                            tint = upColor,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 动画图表
                    AnimatedSpeedChart(
                        downSeries = metrics.netDownHistory,
                        upSeries = metrics.netUpHistory,
                        downColor = downColor,
                        upColor = upColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                }
            }

            // 最快连接详情
            if (metrics.useClashApiForNetSpeed) {
                top.yukonga.miuix.kmp.basic.Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    insideMargin = PaddingValues(14.dp),
                    colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
                        color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    NetSpeedConnectionDetail(
                        icon = Icons.Filled.ArrowDownward,
                        iconTint = downColor,
                        title = stringResource(R.string.bottomsheet_net_speed_fastest_download),
                        speed = metrics.netFastestDownSpeed,
                        host = metrics.netFastestDownHost,
                        chains = metrics.netFastestDownChains
                    )
                }

                top.yukonga.miuix.kmp.basic.Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    insideMargin = PaddingValues(14.dp),
                    colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
                        color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    NetSpeedConnectionDetail(
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

@Composable
private fun NetSpeedStatCard(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val bg = tint.copy(alpha = if (ThemeManager.shouldUseDarkTheme()) 0.12f else 0.08f)

    Box(
        modifier = modifier
            .clip(RoundedRectangle(14.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            top.yukonga.miuix.kmp.basic.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                top.yukonga.miuix.kmp.basic.Text(
                    text = label,
                    style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.footnote2,
                    color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceSecondary
                )
                top.yukonga.miuix.kmp.basic.Text(
                    text = value,
                    style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                    color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun NetSpeedConnectionDetail(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    speed: String,
    host: String,
    chains: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            top.yukonga.miuix.kmp.basic.Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            top.yukonga.miuix.kmp.basic.Text(
                text = title,
                style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body2,
                color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.weight(1f)
            )
            top.yukonga.miuix.kmp.basic.Text(
                text = speed,
                style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
                color = iconTint
            )
        }
        if (host.isNotBlank() && host != "-") {
            top.yukonga.miuix.kmp.basic.Text(
                text = host,
                style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.footnote1,
                color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.padding(start = 24.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (chains.isNotBlank() && chains != "-") {
            top.yukonga.miuix.kmp.basic.Text(
                text = chains,
                style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.footnote1,
                color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.padding(start = 24.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 带动画的速度图表，使用 spring 物理插值实现自然过渡。
 * 渲染完全在 Canvas 内完成，每个数据点独立使用 Animatable 驱动，
 * 配合渐变填充和平滑曲线实现流畅视觉效果。
 */
@Composable
private fun AnimatedSpeedChart(
    downSeries: List<Float>,
    upSeries: List<Float>,
    downColor: Color,
    upColor: Color,
    modifier: Modifier = Modifier
) {
    val maxPoints = 30
    val downPadded = remember(downSeries) {
        if (downSeries.size >= maxPoints) downSeries.takeLast(maxPoints)
        else List(maxPoints - downSeries.size) { 0f } + downSeries
    }
    val upPadded = remember(upSeries) {
        if (upSeries.size >= maxPoints) upSeries.takeLast(maxPoints)
        else List(maxPoints - upSeries.size) { 0f } + upSeries
    }

    val allMax = remember(downPadded, upPadded) {
        (downPadded + upPadded).maxOrNull()?.coerceAtLeast(1f) ?: 1f
    }

    val downAnimValues = remember { List(maxPoints) { Animatable(0f) } }
    val upAnimValues = remember { List(maxPoints) { Animatable(0f) } }

    LaunchedEffect(downPadded, allMax) {
        downPadded.forEachIndexed { i, v ->
            val target = (v / allMax).coerceIn(0f, 1f)
            launch {
                downAnimValues[i].animateTo(
                    targetValue = target,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 220f)
                )
            }
        }
    }
    LaunchedEffect(upPadded, allMax) {
        upPadded.forEachIndexed { i, v ->
            val target = (v / allMax).coerceIn(0f, 1f)
            launch {
                upAnimValues[i].animateTo(
                    targetValue = target,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 220f)
                )
            }
        }
    }

    val downGradient = listOf(downColor.copy(alpha = 0.25f), downColor.copy(alpha = 0f))
    val upGradient = listOf(upColor.copy(alpha = 0.15f), upColor.copy(alpha = 0f))
    val gridColor = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.outline.copy(alpha = 0.12f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val n = maxPoints
        val padTop = 4f
        val chartH = h - padTop

        // 网格线 (3 条水平虚线)
        for (row in 1..3) {
            val gy = padTop + chartH * (row / 4f)
            drawLine(
                color = gridColor,
                start = Offset(0f, gy),
                end = Offset(w, gy),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(
                    intervals = floatArrayOf(6f, 4f)
                )
            )
        }

        fun buildPath(animValues: List<Animatable<Float, *>>): Path {
            val p = Path()
            if (n < 2) return p
            fun pt(i: Int): Offset {
                val x = (i.toFloat() / (n - 1)) * w
                val y = padTop + chartH * (1f - animValues[i].value)
                return Offset(x, y)
            }
            val first = pt(0)
            p.moveTo(first.x, first.y)
            for (i in 1 until n) {
                val prev = pt(i - 1)
                val cur = pt(i)
                val cx = (prev.x + cur.x) / 2f
                p.cubicTo(cx, prev.y, cx, cur.y, cur.x, cur.y)
            }
            return p
        }

        fun buildFillPath(animValues: List<Animatable<Float, *>>): Path {
            val stroke = buildPath(animValues)
            val fill = Path()
            fill.addPath(stroke)
            val lastX = ((n - 1).toFloat() / (n - 1)) * w
            fill.lineTo(lastX, h)
            fill.lineTo(0f, h)
            fill.close()
            return fill
        }

        // 绘制渐变填充
        val downFill = buildFillPath(downAnimValues)
        drawPath(
            path = downFill,
            brush = Brush.verticalGradient(
                colors = downGradient,
                startY = 0f,
                endY = h
            )
        )
        val upFill = buildFillPath(upAnimValues)
        drawPath(
            path = upFill,
            brush = Brush.verticalGradient(
                colors = upGradient,
                startY = 0f,
                endY = h
            )
        )

        // 绘制曲线
        val upStroke = buildPath(upAnimValues)
        drawPath(
            path = upStroke,
            color = upColor,
            style = Stroke(
                width = 2.5f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
        val downStroke = buildPath(downAnimValues)
        drawPath(
            path = downStroke,
            color = downColor,
            style = Stroke(
                width = 2.5f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // 最新数据点标记
        if (n >= 2) {
            val downLast = downAnimValues.last().value
            val upLast = upAnimValues.last().value
            if (downLast > 0.005f) {
                val y = padTop + chartH * (1f - downLast)
                drawCircle(color = downColor, radius = 3.5f, center = Offset(w, y))
                drawCircle(color = Color.White, radius = 1.5f, center = Offset(w, y))
            }
            if (upLast > 0.005f) {
                val y = padTop + chartH * (1f - upLast)
                drawCircle(color = upColor, radius = 3.5f, center = Offset(w, y))
                drawCircle(color = Color.White, radius = 1.5f, center = Offset(w, y))
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
    val isDark = ThemeManager.shouldUseDarkTheme()
    val normalColor = if (isDark) Color(0xFF79B8FF) else Color(0xFF1472B6)
    val warnColor = if (isDark) Color(0xFFFFCC80) else Color(0xFFE67E22)
    val criticalColor = if (isDark) Color(0xFFFF8A80) else Color(0xFFC62828)
    val trackColor = if (isDark) Color(0xFF2A2A2A) else Color(0xFFE8E8E8)

    val viewParent = LocalView.current.parent
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        if (!isClashApiEnabled) {
            top.yukonga.miuix.kmp.basic.TextButton(
                text = stringResource(R.string.bottomsheet_subscription_go_tools),
                onClick = onOpenToolsSubscription,
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp,
                colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.textButtonColorsPrimary()
            )
            Spacer(modifier = Modifier.height(8.dp))
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
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (items.isEmpty()) {
                top.yukonga.miuix.kmp.basic.Text(
                    text = stringResource(R.string.bottomsheet_subscription_none),
                    style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body2,
                    color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                items.forEach { item ->
                    val used = item.uploadBytes + item.downloadBytes
                    val remain = (item.totalBytes - used).max(java.math.BigInteger.ZERO)
                    val progress = if (item.totalBytes > java.math.BigInteger.ZERO) {
                        used.toBigDecimal().divide(item.totalBytes.toBigDecimal(), 6, java.math.RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
                    } else { 0f }
                    val remainRatio = if (item.totalBytes > java.math.BigInteger.ZERO) remain.toBigDecimal().divide(item.totalBytes.toBigDecimal(), 6, java.math.RoundingMode.HALF_UP).toDouble() else 1.0
                    val daysLeft = subscriptionDaysUntilExpiry(item.expiryDate)

                    val statusColor = when {
                        (daysLeft != null && daysLeft < 0) || remainRatio <= 0.05 -> criticalColor
                        (daysLeft != null && daysLeft <= 3) || remainRatio <= 0.10 -> criticalColor
                        (daysLeft != null && daysLeft <= 7) || remainRatio <= 0.20 -> warnColor
                        else -> normalColor
                    }

                    SubItemCard(
                        item = item,
                        used = used,
                        remain = remain,
                        progress = progress,
                        statusColor = statusColor,
                        trackColor = trackColor,
                        isDark = isDark,
                        onRefresh = { onRefresh(item.url) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SubItemCard(
    item: SubscriptionItem,
    used: java.math.BigInteger,
    remain: java.math.BigInteger,
    progress: Float,
    statusColor: Color,
    trackColor: Color,
    isDark: Boolean,
    onRefresh: () -> Unit
) {
    top.yukonga.miuix.kmp.basic.Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(0.dp),
        colors = top.yukonga.miuix.kmp.basic.CardDefaults.defaultColors(
            color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 标题行 + 刷新
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧状态竖条 + 名称
                Box(
                    modifier = Modifier
                        .size(width = 3.dp, height = 18.dp)
                        .clip(RoundedRectangle(999.dp))
                        .background(statusColor)
                )
                top.yukonga.miuix.kmp.basic.Text(
                    text = item.name,
                    style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                    color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                )
                top.yukonga.miuix.kmp.basic.IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(40.dp),
                    enabled = !item.loading,
                    cornerRadius = 12.dp,
                    backgroundColor = Color.Transparent
                ) {
                    top.yukonga.miuix.kmp.basic.Icon(
                        imageVector = Icons.Filled.Autorenew,
                        contentDescription = null,
                        tint = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceSecondary
                            .copy(alpha = if (item.loading) 0.35f else 1f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // 到期 / 更新时间
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                top.yukonga.miuix.kmp.basic.Text(
                    text = stringResource(R.string.bottomsheet_subscription_expires, subscriptionFormatExpiryWithDays(item.expiryDate)),
                    style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.footnote2,
                    color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceSecondary
                )
                top.yukonga.miuix.kmp.basic.Text(
                    text = stringResource(R.string.bottomsheet_subscription_updated, subscriptionFormatTimeHm(item.lastUpdatedAtMs)),
                    style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.footnote2,
                    color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 流量三列
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SubStatCell(
                    label = stringResource(R.string.bottomsheet_subscription_upload),
                    value = HomeMetricsApi.formatBytes(item.uploadBytes),
                    modifier = Modifier.weight(1f)
                )
                SubStatCell(
                    label = stringResource(R.string.bottomsheet_subscription_download),
                    value = HomeMetricsApi.formatBytes(item.downloadBytes),
                    modifier = Modifier.weight(1f)
                )
                SubStatCell(
                    label = stringResource(R.string.bottomsheet_subscription_remaining),
                    value = HomeMetricsApi.formatBytes(remain),
                    valueColor = statusColor,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 进度条 + 已用/总量
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                top.yukonga.miuix.kmp.basic.LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedRectangle(4.dp)),
                    colors = top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = statusColor,
                        backgroundColor = trackColor
                    )
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    top.yukonga.miuix.kmp.basic.Text(
                        text = stringResource(R.string.bottomsheet_subscription_used, HomeMetricsApi.formatBytes(used)),
                        style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.footnote2,
                        color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    top.yukonga.miuix.kmp.basic.Text(
                        text = stringResource(R.string.bottomsheet_subscription_total, HomeMetricsApi.formatBytes(item.totalBytes)),
                        style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.footnote2,
                        color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun SubStatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurface
) {
    Column(
        modifier = modifier
            .clip(RoundedRectangle(12.dp))
            .background(top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        top.yukonga.miuix.kmp.basic.Text(
            text = value,
            style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.body1,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        top.yukonga.miuix.kmp.basic.Text(
            text = label,
            style = top.yukonga.miuix.kmp.theme.MiuixTheme.textStyles.footnote2,
            color = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.onSurfaceSecondary
        )
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
                    style = MiuixTheme.textStyles.footnote1,
                    color = c.textSecondary
                )
                Text(
                    text = value,
                    style = MiuixTheme.textStyles.title4,
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
                style = MiuixTheme.textStyles.footnote1,
                color = c.textSecondary,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = speed,
                style = MiuixTheme.textStyles.button,
                fontWeight = FontWeight.SemiBold,
                color = iconTint
            )
        }

        Text(
            text = host,
            style = MiuixTheme.textStyles.footnote1,
            color = c.textSecondary,
            modifier = Modifier.padding(start = 22.dp)
        )

        Text(
            text = chains,
            style = MiuixTheme.textStyles.footnote1,
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
                style = MiuixTheme.textStyles.title4,
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
                style = MiuixTheme.textStyles.title4,
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
            style = MiuixTheme.textStyles.body2,
            color = c.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 170.dp)
        )
        
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
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
                style = MiuixTheme.textStyles.title2,
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
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.SemiBold,
                        color = c.textPrimary
                    )
                    Text(
                        text = updateResult.currentVersion,
                        style = MiuixTheme.textStyles.body1,
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
                            style = MiuixTheme.textStyles.body2,
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
                            style = MiuixTheme.textStyles.title1
                        )
                        Text(
                            text = if (mode == UpdateSheetMode.APP) {
                                stringResource(R.string.bottomsheet_app_latest_version_title)
                            } else {
                                stringResource(R.string.bottomsheet_latest_version_title)
                            },
                            style = MiuixTheme.textStyles.title4,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textPrimary
                        )
                        Text(
                            text = stringResource(R.string.bottomsheet_no_updates_available),
                            style = MiuixTheme.textStyles.body2,
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
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(
                        R.string.bottomsheet_release_version,
                        release.name.ifBlank { release.tag }
                    ),
                    style = MiuixTheme.textStyles.body2,
                    color = c.textPrimary
                )

                if (release.publishedAt.isNotBlank()) {
                    val formattedDate = formatPublishDate(release.publishedAt)
                    Text(
                        text = stringResource(R.string.bottomsheet_release_date, formattedDate),
                        style = MiuixTheme.textStyles.footnote1,
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
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.Medium,
                        color = c.textPrimary
                    )

                    Text(
                        text = formattedBody,
                        style = MiuixTheme.textStyles.footnote1,
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
                        style = MiuixTheme.textStyles.footnote1
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
                        style = MiuixTheme.textStyles.footnote1
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
                    style = MiuixTheme.textStyles.title4,
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
                            style = MiuixTheme.textStyles.footnote2,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.bottomsheet_release_version, versionText),
                    style = MiuixTheme.textStyles.body2,
                    color = c.textPrimary
                )

                if (release.commitSha.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.bottomsheet_release_commit, release.commitSha),
                        style = MiuixTheme.textStyles.footnote1,
                        color = c.textSecondary
                    )
                }

                if (release.publishedAt.isNotBlank()) {
                    val formattedDate = formatPublishDate(release.publishedAt)
                    Text(
                        text = stringResource(R.string.bottomsheet_release_date, formattedDate),
                        style = MiuixTheme.textStyles.footnote1,
                        color = c.textSecondary
                    )
                }

                if (showReleaseType) {
                    Text(
                        text = stringResource(
                            R.string.bottomsheet_release_type,
                            if (release.isPrerelease) stringResource(R.string.bottomsheet_release_type_prerelease) else stringResource(R.string.bottomsheet_release_type_stable)
                        ),
                        style = MiuixTheme.textStyles.footnote1,
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
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.Medium,
                        color = c.textPrimary
                    )

                    Text(
                        text = formattedBody,
                        style = MiuixTheme.textStyles.footnote1,
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
                        style = MiuixTheme.textStyles.body2
                    )
                    Text(
                        text = stringResource(R.string.bottomsheet_release_prerelease_warning),
                        style = MiuixTheme.textStyles.footnote1,
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
                        style = MiuixTheme.textStyles.footnote1
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
                        style = MiuixTheme.textStyles.footnote1
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
                style = MiuixTheme.textStyles.body2,
                color = c.textSecondary
            )
        }
        
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
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

