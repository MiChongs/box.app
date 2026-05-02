package com.box.app.ui.components.home

import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import coil3.compose.AsyncImage
import com.box.app.BuildConfig
import com.box.app.data.backend.BoxApi
import com.box.app.data.backend.ShellExecutor
import com.box.app.data.model.HomeServiceState
import com.box.app.data.model.ServiceStatus
import com.box.app.R
import com.box.app.ui.components.ErrorToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.util.lerp
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import top.yukonga.miuix.kmp.shapes.SmoothRoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.SwitchPreference
import com.box.app.ui.components.bottomsheets.SheetBlurEffect
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class HomeCardModel(
    val title: String,
    val value: String,
    val subtitle: String,
    val kind: HomeMetricKind,
    val accent: Color,
    val badgeText: String? = null,
    val progress: Float? = null,
    val isActive: Boolean = true,
    val onClick: (() -> Unit)? = null,
    val cornerActionIcon: ImageVector? = null,
    val onCornerAction: (() -> Unit)? = null,
    val sparkDown: List<Float>? = null,
    val sparkUp: List<Float>? = null
)

enum class HomeMetricKind {
    Service,
    Ip,
    Speed,
    Latency,
    Subscription,
    System
}

@Composable
fun HomeHeader(
    onEdit: (() -> Unit)? = null,
    scrollBehavior: ScrollBehavior? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.home_header_title),
            style = MiuixTheme.textStyles.title1,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (onEdit != null) {
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(36.dp),
                backgroundColor = Color.Transparent,
                cornerRadius = 12.dp
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.home_header_edit),
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun HomeHeroCard(
    serviceState: HomeServiceState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReload: () -> Unit,
    onOpenBaseProxyConfig: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    val env = serviceState.env

    val status = serviceState.status
    val isRunning = status is ServiceStatus.Running
    val isStopped = status is ServiceStatus.Stopped
    val isBusy = !(isRunning || isStopped)

    val statusEditable = !isRunning

    val showUnavailable = env.checked && !env.isReady
    val isEnvReady = env.isReady

    val statusText = when (status) {
        is ServiceStatus.Running -> stringResource(R.string.home_service_status_running)
        is ServiceStatus.Stopped -> if (showUnavailable) stringResource(R.string.home_service_status_unavailable) else stringResource(R.string.home_service_status_stopped)
        is ServiceStatus.Starting -> stringResource(R.string.home_service_status_starting)
        is ServiceStatus.Stopping -> stringResource(R.string.home_service_status_stopping)
        is ServiceStatus.Restarting -> stringResource(R.string.home_service_status_restarting)
        is ServiceStatus.Checking -> stringResource(R.string.home_service_status_checking)
    }

    val statusColors = when {
        isRunning -> homeSuccessColors()
        showUnavailable -> homeDangerColors()
        isBusy -> homeWarningColors()
        else -> homeNeutralColors()
    }
    val statusAccent = statusColors.accent

    var showCoreSheet by remember { mutableStateOf(false) }
    var showModeSheet by remember { mutableStateOf(false) }
    var showIpv6Sheet by remember { mutableStateOf(false) }
    var switchCoreVersions by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    var coreText by remember { mutableStateOf(serviceState.coreDisplayName) }
    var modeText by remember { mutableStateOf(serviceState.networkMode) }
    var ipv6Text by remember { mutableStateOf(serviceState.ipv6Text) }
    LaunchedEffect(serviceState.coreDisplayName, serviceState.networkMode, serviceState.ipv6Text, isRunning) {
        if (isRunning) {
            coreText = serviceState.coreDisplayName
            modeText = serviceState.networkMode
            ipv6Text = serviceState.ipv6Text
        }
    }

    fun parseSetting(settings: String, key: String): String? {
        val regex = Regex("^${key}=\"?(.*?)\"?$", setOf(RegexOption.MULTILINE))
        return regex.find(settings)?.groupValues?.getOrNull(1)
    }

    fun coreDisplayName(binName: String?, xclashOption: String?): String? {
        val bin = binName?.trim().orEmpty()
        if (bin.isBlank()) return null
        if (BuildConfig.FLAVOR == "bfr" && bin == "clash") {
            val opt = xclashOption?.trim().orEmpty()
            if (opt.isNotBlank()) return "clash-$opt"
        }
        return bin
    }

    // 打开切换核心 Sheet 时异步检测版本
    LaunchedEffect(showCoreSheet) {
        if (showCoreSheet) {
            switchCoreVersions = emptyMap()
            withContext(Dispatchers.IO) {
                val versions = mutableMapOf<String, String>()
                val bins = listOf("mihomo", "sing-box", "xray", "v2fly", "hysteria")
                for (bin in bins) {
                    runCatching {
                        // 同时执行 -v 和 version，合并输出由解析器提取版本号
                        val cmd = """for p in "/data/adb/box/bin/$bin" "/data/adb/box/$bin/$bin"; do [ -x "${'$'}p" ] || continue; v=${'$'}( { "${'$'}p" -v 2>/dev/null; "${'$'}p" version 2>/dev/null; } | head -n20 ); [ -n "${'$'}v" ] && echo "${'$'}v" && exit 0; done"""
                        val res = ShellExecutor.execute(cmd)
                        val ver = res.stdout.trim()
                        if (ver.isNotBlank()) {
                            // 三级版本解析（避免误匹配时间戳）
                            val clean =
                                Regex("""(?i)version[=:\s]+"?v?(\d+\.\d+(?:\.\d+)?(?:-[\w.]+)?)"?""").find(ver)?.let { "v${it.groupValues[1]}" }
                                    ?: Regex("""\bv(\d+\.\d+(?:\.\d+)?(?:-[\w.]+)?)""").find(ver)?.let { "v${it.groupValues[1]}" }
                                    ?: Regex("""(?i)(?:mihomo|sing-box|xray|v2ray|v2fly|hysteria)\s+(?:meta\s+)?v?(\d+\.\d+\.\d+(?:-[\w.]+)?)""").find(ver)?.let { "v${it.groupValues[1]}" }
                            if (clean != null) versions[bin] = clean
                        }
                    }
                }
                switchCoreVersions = versions
            }
        }
    }

    suspend fun reloadSettingsLocal() {
        val settings = BoxApi.getSettings()
        if (settings.isBlank()) return
        val bin = parseSetting(settings, "bin_name")
        val opt = parseSetting(settings, "xclash_option")
        coreText = coreDisplayName(bin, opt)?.takeIf { it.isNotBlank() } ?: coreText
        modeText = parseSetting(settings, "network_mode")?.takeIf { it.isNotBlank() } ?: modeText
        val ipv6Enabled = when (parseSetting(settings, "ipv6")?.toBooleanStrictOrNull()) {
            true -> true
            false -> false
            else -> null
        }
        if (ipv6Enabled != null) {
            ipv6Text = if (ipv6Enabled) "true" else "false"
        }
    }

    LaunchedEffect(isRunning) {
        if (!isRunning) {
            reloadSettingsLocal()
        }
    }

    suspend fun writeSetting(key: String, value: String) {
        val file = "/data/adb/box/settings.ini"
        val escaped = value.replace("\"", "\\\"")
        val cmd = "if [ -f '$file' ]; then " +
            "if grep -q '^${key}=' '$file'; then " +
            "sed -i 's/^${key}=.*/${key}=\\\"${escaped}\\\"/' '$file'; " +
            "else echo '${key}=\\\"${escaped}\\\"' >> '$file'; fi; " +
            "else echo '${key}=\\\"${escaped}\\\"' > '$file'; fi"
        ShellExecutor.execute(cmd)
    }

    suspend fun removeSetting(key: String) {
        val file = "/data/adb/box/settings.ini"
        val cmd = "if [ -f '$file' ]; then sed -i '/^${key}=/d' '$file'; fi"
        ShellExecutor.execute(cmd)
    }

    suspend fun writeBoolSetting(key: String, enabled: Boolean) {
        val file = "/data/adb/box/settings.ini"
        val v = if (enabled) "true" else "false"
        val cmd = "if [ -f '$file' ]; then " +
            "if grep -q '^${key}=' '$file'; then " +
            "sed -i 's/^${key}=.*/${key}=\\\"${v}\\\"/' '$file'; " +
            "else echo '${key}=\\\"${v}\\\"' >> '$file'; fi; " +
            "else echo '${key}=\\\"${v}\\\"' > '$file'; fi"
        ShellExecutor.execute(cmd)
    }

    if (showCoreSheet) {
        data class CoreChoice(
            val title: String,
            val binName: String,
            val xclashOption: String? = null
        )

        val coreOptions = if (BuildConfig.FLAVOR == "bfr") {
            listOf(
                CoreChoice(title = "clash-mihomo", binName = "clash", xclashOption = "mihomo"),
                CoreChoice(title = "clash-premium", binName = "clash", xclashOption = "premium"),
                CoreChoice(title = "sing-box", binName = "sing-box"),
                CoreChoice(title = "xray", binName = "xray"),
                CoreChoice(title = "v2fly", binName = "v2fly"),
                CoreChoice(title = "hysteria", binName = "hysteria")
            )
        } else {
            listOf(
                CoreChoice(title = "mihomo", binName = "mihomo"),
                CoreChoice(title = "sing-box", binName = "sing-box"),
                CoreChoice(title = "xray", binName = "xray"),
                CoreChoice(title = "v2fly", binName = "v2fly"),
                CoreChoice(title = "hysteria", binName = "hysteria")
            )
        }
        val sheetNavBarPadding = WindowInsets.navigationBars
            .asPaddingValues().calculateBottomPadding()

        val coreSheetBlur = com.box.app.utils.ThemeManager.shouldUseBlurEffects()
        if (coreSheetBlur) SheetBlurEffect()
        WindowBottomSheet(
            show = showCoreSheet,
            title = stringResource(R.string.home_sheet_core_title),
            onDismissRequest = { showCoreSheet = false },
            backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
            dragHandleColor = Color.Transparent
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val coreIconUrl = { name: String ->
                    when (name) {
                        "mihomo", "clash", "clash-mihomo", "clash-premium" ->
                            "https://cdn.jsdelivr.net/gh/MetaCubeX/mihomo@Alpha/docs/logo.png"
                        "sing-box" ->
                            "https://cdn.jsdelivr.net/gh/SagerNet/sing-box-for-android@main/app/src/main/ic_launcher-playstore.png"
                        "xray" ->
                            "https://avatars.githubusercontent.com/u/71564206?s=128&v=4"
                        "v2fly", "v2ray" ->
                            "https://cdn.jsdelivr.net/gh/v2fly/v2fly-github-io@master/docs/.vuepress/public/readme-logo.png"
                        "hysteria" ->
                            "https://cdn.jsdelivr.net/gh/apernet/hysteria@master/media-kit/png/symbol%201@2x.png"
                        else -> null
                    }
                }

                HomeSheetSummary(text = stringResource(R.string.home_sheet_core_subtitle))

                // HyperOS3 风格：独立圆角卡片 + 选中态动画
                val switchScheme = MiuixTheme.colorScheme
                coreOptions.forEachIndexed { index, item ->
                    val isCurrent = item.title == coreText
                    val iconUrl = coreIconUrl(item.binName) ?: coreIconUrl(item.title)

                    val bgColor by animateColorAsState(
                        if (isCurrent) switchScheme.primary.copy(alpha = 0.12f)
                        else switchScheme.surfaceContainerHigh,
                        animationSpec = tween(280), label = "sw_bg_$index"
                    )
                    val borderColor by animateColorAsState(
                        if (isCurrent) switchScheme.primary.copy(alpha = 0.28f)
                        else Color.Transparent,
                        animationSpec = tween(280), label = "sw_bd_$index"
                    )
                    val titleColor by animateColorAsState(
                        if (isCurrent) switchScheme.primary else switchScheme.onSurface,
                        animationSpec = tween(280), label = "sw_tt_$index"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SmoothRoundedCornerShape(16.dp))
                            .border(1.dp, borderColor, SmoothRoundedCornerShape(16.dp))
                            .background(bgColor)
                            .clickable {
                                if (!isCurrent) {
                                    showCoreSheet = false
                                    coreText = item.title
                                    scope.launch {
                                        writeSetting("bin_name", item.binName)
                                        val opt = item.xclashOption
                                        if (!opt.isNullOrBlank()) {
                                            writeSetting("xclash_option", opt)
                                        }
                                        reloadSettingsLocal()
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            if (iconUrl != null) {
                                AsyncImage(
                                    model = iconUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MiuixTheme.textStyles.body1,
                                    fontWeight = FontWeight.Medium,
                                    color = titleColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val ver = switchCoreVersions[item.binName]
                                if (ver != null) {
                                    Text(
                                        text = ver,
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = switchScheme.onSurfaceSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = switchScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(sheetNavBarPadding))
            }
        }
    }

    if (showModeSheet) {
        val sheetNavBarPadding = WindowInsets.navigationBars
            .asPaddingValues().calculateBottomPadding()
        val modeOptions = listOf("tun", "tproxy", "redirect", "mixed", "enhance")
        val modeSheetBlur = com.box.app.utils.ThemeManager.shouldUseBlurEffects()
        if (modeSheetBlur) SheetBlurEffect()
        WindowBottomSheet(
            show = showModeSheet,
            title = stringResource(R.string.home_sheet_network_mode_title),
            onDismissRequest = { showModeSheet = false },
            backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
            dragHandleColor = Color.Transparent
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HomeSheetSummary(text = stringResource(R.string.home_sheet_network_mode_subtitle))
                modeOptions.forEach { item ->
                    val isCurrent = item == modeText
                    HomeSheetOptionItem(
                        title = item,
                        selected = isCurrent,
                        onClick = {
                            if (!isCurrent) {
                                showModeSheet = false
                                modeText = item
                                scope.launch {
                                    writeSetting("network_mode", item)
                                    reloadSettingsLocal()
                                }
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(sheetNavBarPadding))
            }
        }
    }

    if (showIpv6Sheet) {
        val sheetNavBarPadding = WindowInsets.navigationBars
            .asPaddingValues().calculateBottomPadding()
        val checked = ipv6Text.equals("true", ignoreCase = true)
        val ipv6SheetBlur = com.box.app.utils.ThemeManager.shouldUseBlurEffects()
        if (ipv6SheetBlur) SheetBlurEffect()
        WindowBottomSheet(
            show = showIpv6Sheet,
            title = stringResource(R.string.home_sheet_ipv6_title),
            onDismissRequest = { showIpv6Sheet = false },
            backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
            dragHandleColor = Color.Transparent
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HomeSheetSummary(text = stringResource(R.string.home_sheet_ipv6_subtitle))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    insideMargin = PaddingValues(0.dp),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainerHigh)
                ) {
                    SwitchPreference(
                        checked = checked,
                        title = stringResource(R.string.home_ipv6_label),
                        summary = if (checked) stringResource(R.string.common_on) else stringResource(R.string.common_off),
                        onCheckedChange = { next ->
                            ipv6Text = if (next) "true" else "false"
                            scope.launch {
                                writeBoolSetting("ipv6", next)
                                reloadSettingsLocal()
                            }
                        }
                    )
                }
                Spacer(modifier = Modifier.height(sheetNavBarPadding))
            }
        }
    }

    var toastMessage by remember { mutableStateOf<String?>(null) }
    ErrorToast(
        message = toastMessage,
        onConsumed = { toastMessage = null }
    )

    val canReloadConfig = remember(isRunning, isEnvReady, coreText) {
        isRunning && isEnvReady && (coreText.equals("mihomo", ignoreCase = true) || coreText.equals("sing-box", ignoreCase = true))
    }

    val reloadConfigSuccessText = stringResource(R.string.home_reload_config_success)
    val reloadConfigFailedText = stringResource(R.string.home_reload_config_failed)
    val statusSummary = when {
        isRunning -> serviceState.uptimeText
        showUnavailable -> when {
            !env.hasRoot -> stringResource(R.string.home_env_title_root_required)
            !env.hasModule -> stringResource(R.string.home_env_title_module_missing)
            !env.hasScripts -> stringResource(R.string.home_env_body_scripts_missing)
            else -> stringResource(R.string.home_environment_not_ready)
        }
        isBusy -> stringResource(R.string.home_please_wait)
        else -> stringResource(R.string.home_tap_start_to_enable)
    }
    // ── 颜色动画（tween 固定时长，避免 spring 持续计算） ──
    val colorTween = tween<Color>(durationMillis = 500, easing = FastOutSlowInEasing)
    val animatedStatusColor by animateColorAsState(
        targetValue = statusAccent,
        animationSpec = colorTween,
        label = "hero_accent"
    )
    val animatedContainerColor by animateColorAsState(
        targetValue = statusColors.container,
        animationSpec = colorTween,
        label = "hero_bg"
    )
    val animatedOnContainerColor by animateColorAsState(
        targetValue = statusColors.onContainer,
        animationSpec = colorTween,
        label = "hero_fg"
    )

    // ── 状态标题行 ──
    val heroTitle = statusText
    val heroSubtitle = statusSummary
    // 运行中时显示核心名称
    val heroCoreLabel = if (isRunning) {
        coreText.ifBlank { "-" }.replaceFirstChar { it.uppercase() }
    } else null

    // ── 状态图标 path morphing（无闪烁） ──
    val targetIconState = when {
        isRunning -> 0       // check
        showUnavailable -> 1 // error
        isBusy -> 2          // refresh
        else -> 3            // pause
    }
    // morphFrom / morphTo 在动画开始前一次性锁定，动画期间不随 recomposition 变化
    var morphFrom by remember { mutableIntStateOf(targetIconState) }
    var morphTo by remember { mutableIntStateOf(targetIconState) }
    val morphProgress = remember { Animatable(1f) }
    LaunchedEffect(targetIconState) {
        if (targetIconState != morphTo) {
            // 在启动新动画前，将 from 设为当前视觉状态（上次动画的终点）
            morphFrom = morphTo
            morphTo = targetIconState
            morphProgress.snapTo(0f)
            morphProgress.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
        }
    }
    // busy 旋转：仅 busy 时驱动帧，退出时平滑归零（避免角度跳变）
    val rotationAnim = remember { Animatable(0f) }
    LaunchedEffect(isBusy) {
        if (isBusy) {
            while (true) {
                rotationAnim.animateTo(
                    targetValue = rotationAnim.value + 360f,
                    animationSpec = tween(durationMillis = 1200, easing = LinearEasing)
                )
            }
        } else {
            // 平滑旋转到最近的 360° 整数倍（即原位），而不是瞬间归零
            val current = rotationAnim.value % 360f
            if (current > 1f) {
                rotationAnim.animateTo(
                    targetValue = rotationAnim.value + (360f - current),
                    animationSpec = tween(durationMillis = ((360f - current) / 360f * 600f).toInt(), easing = FastOutSlowInEasing)
                )
            }
            rotationAnim.snapTo(0f)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── KernelSU 风格：左状态卡 + 右双信息卡 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 左侧：状态卡片（带大图标装饰） ──
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                cornerRadius = 22.dp,
                insideMargin = PaddingValues(0.dp),
                colors = CardDefaults.defaultColors(color = animatedContainerColor),
                // 仅在非运行状态可点：进入基础代理配置二级页面
                // 运行中时禁用点击，避免误触改动正在生效的核心/模式/IPv6 等
                onClick = { if (statusEditable) onOpenBaseProxyConfig() },
                showIndication = statusEditable,
                pressFeedbackType = PressFeedbackType.Sink
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 底层：大装饰图标（Canvas 绘制 + 硬件旋转）
                    val iconColor = animatedStatusColor.copy(alpha = 0.8f)
                    val morphP = morphProgress.value
                    val fromPaths = heroIconPaths(morphFrom)
                    val toPaths = heroIconPaths(morphTo)
                    val isFromRefresh = morphFrom == 2
                    val isToRefresh = morphTo == 2
                    Canvas(
                        modifier = Modifier
                            .size(150.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 30.dp, y = 35.dp)
                            .graphicsLayer { rotationZ = rotationAnim.value }
                    ) {
                        val strokeW = size.minDimension * 0.055f
                        val r = size.minDimension * 0.44f
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val center = Offset(cx, cy)
                        val sr = r * 0.92f
                        val symbolStroke = Stroke(
                            width = strokeW * 1.1f,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )

                        // ── 外圈圆环 ──
                        drawCircle(
                            color = iconColor,
                            radius = r,
                            center = center,
                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                        )

                        // ── 内部符号 ──
                        // refresh 用 drawArc 绘制精确圆弧，其他用贝塞尔路径
                        // 过渡时两层叠加，alpha 交叉淡入淡出

                        val drawBezierSymbol: (Array<FloatArray>, Float) -> Unit = { paths, alpha ->
                            val c = iconColor.copy(alpha = iconColor.alpha * alpha)
                            // 检测相邻段是否共享端点（如 ✓ 的两段），
                            // 共享则合并为一条 Path 避免 StrokeCap.Round 叠加产生亮斑
                            val p0 = paths[0]; val p1 = paths[1]
                            val continuous = p0[4] == p1[0] && p0[5] == p1[1]
                            if (continuous) {
                                val path = Path().apply {
                                    moveTo(cx + p0[0] * sr, cy + p0[1] * sr)
                                    quadraticBezierTo(
                                        cx + p0[2] * sr, cy + p0[3] * sr,
                                        cx + p0[4] * sr, cy + p0[5] * sr
                                    )
                                    quadraticBezierTo(
                                        cx + p1[2] * sr, cy + p1[3] * sr,
                                        cx + p1[4] * sr, cy + p1[5] * sr
                                    )
                                }
                                drawPath(path, color = c, style = symbolStroke)
                            } else {
                                for (s in 0..1) {
                                    val pts = paths[s]
                                    val path = Path().apply {
                                        moveTo(cx + pts[0] * sr, cy + pts[1] * sr)
                                        quadraticBezierTo(
                                            cx + pts[2] * sr, cy + pts[3] * sr,
                                            cx + pts[4] * sr, cy + pts[5] * sr
                                        )
                                    }
                                    drawPath(path, color = c, style = symbolStroke)
                                }
                            }
                        }

                        val drawRefreshSymbol: (Float) -> Unit = { alpha ->
                            val c = iconColor.copy(alpha = iconColor.alpha * alpha)
                            val arcR = sr * 0.42f
                            val arcRect = androidx.compose.ui.geometry.Rect(
                                cx - arcR, cy - arcR, cx + arcR, cy + arcR
                            )
                            val arcStroke = Stroke(width = strokeW * 1.1f, cap = StrokeCap.Round)
                            // 上半弧：从 200° 扫 140°
                            drawArc(c, 200f, 140f, false, arcRect.topLeft, arcRect.size, style = arcStroke)
                            // 下半弧：从 20° 扫 140°
                            drawArc(c, 20f, 140f, false, arcRect.topLeft, arcRect.size, style = arcStroke)
                        }

                        // smoothstep 交叉淡入：避免线性 alpha 中间态亮度塌陷
                        val fadeIn = morphP * morphP * (3f - 2f * morphP)   // 0→1 smooth
                        val fadeOut = 1f - fadeIn                            // 1→0 smooth

                        when {
                            // 两端都不是 refresh：纯贝塞尔顶点插值，无需交叉淡入
                            !isFromRefresh && !isToRefresh -> {
                                val merged = Array(2) { s ->
                                    val fp = fromPaths[s]; val tp = toPaths[s]
                                    floatArrayOf(
                                        lerp(fp[0], tp[0], morphP), lerp(fp[1], tp[1], morphP),
                                        lerp(fp[2], tp[2], morphP), lerp(fp[3], tp[3], morphP),
                                        lerp(fp[4], tp[4], morphP), lerp(fp[5], tp[5], morphP)
                                    )
                                }
                                drawBezierSymbol(merged, 1f)
                            }
                            // 从 refresh 过渡到其他：refresh 淡出 + 贝塞尔淡入
                            isFromRefresh && !isToRefresh -> {
                                drawRefreshSymbol(fadeOut)
                                drawBezierSymbol(toPaths, fadeIn)
                            }
                            // 从其他过渡到 refresh：贝塞尔淡出 + refresh 淡入
                            !isFromRefresh && isToRefresh -> {
                                drawBezierSymbol(fromPaths, fadeOut)
                                drawRefreshSymbol(fadeIn)
                            }
                            // refresh → refresh（无变化）
                            else -> {
                                drawRefreshSymbol(1f)
                            }
                        }
                    }
                    // 上层：文字（限制宽度避免被右下角装饰图标遮挡）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = heroTitle,
                            style = MiuixTheme.textStyles.title2,
                            fontWeight = FontWeight.Bold,
                            color = animatedOnContainerColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = heroSubtitle,
                            style = MiuixTheme.textStyles.body2,
                            color = animatedOnContainerColor.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (heroCoreLabel != null) {
                            Text(
                                text = heroCoreLabel,
                                style = MiuixTheme.textStyles.body2,
                                color = animatedOnContainerColor.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // ── 右侧：双信息卡（模式 + 核心） ──
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 模式卡片
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    cornerRadius = 18.dp,
                    insideMargin = PaddingValues(0.dp),
                    colors = CardDefaults.defaultColors(),
                    onClick = { if (statusEditable) showModeSheet = true },
                    showIndication = statusEditable,
                    pressFeedbackType = PressFeedbackType.Sink
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.home_mode_label),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                        Text(
                            text = modeText.ifBlank { stringResource(R.string.home_placeholder_dash) }.uppercase(),
                            style = MiuixTheme.textStyles.title4,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                // IPv6 卡片
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    cornerRadius = 18.dp,
                    insideMargin = PaddingValues(0.dp),
                    colors = CardDefaults.defaultColors(),
                    onClick = { if (statusEditable) showIpv6Sheet = true },
                    showIndication = statusEditable,
                    pressFeedbackType = PressFeedbackType.Sink
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.home_ipv6_label),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                        Text(
                            text = if (ipv6Text.equals("true", ignoreCase = true))
                                stringResource(R.string.common_on) else stringResource(R.string.common_off),
                            style = MiuixTheme.textStyles.title4,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // ── 操作区：统一 segmented control（B4） ──
        // 仅按"运行中 / 停止"做布局类型切换，内部 loading/enabled 数据变化由
        // segmented control 内的 animateColorAsState 平滑过渡，不重新触发 size 形变。
        val showRunningControls = isRunning || status is ServiceStatus.Stopping || status is ServiceStatus.Restarting
        androidx.compose.animation.AnimatedContent(
            targetState = showRunningControls,
            transitionSpec = {
                (fadeIn(animationSpec = tween(220)) +
                    slideInVertically(
                        animationSpec = tween(220),
                        initialOffsetY = { it / 6 }
                    )).togetherWith(
                    fadeOut(animationSpec = tween(160))
                ).using(SizeTransform(clip = false))
            },
            label = "hero_segmented_control"
        ) { running ->
            val mode: HeroControlMode = if (running) {
                HeroControlMode.Running(
                    canReloadConfig = canReloadConfig,
                    enabled = isRunning && isEnvReady && !isBusy,
                    isStopping = status is ServiceStatus.Stopping,
                    isRestarting = status is ServiceStatus.Restarting,
                    onReloadConfig = {
                        scope.launch {
                            val res = BoxApi.reloadConfig()
                            toastMessage = if (res.exitCode == 0) {
                                reloadConfigSuccessText
                            } else {
                                res.stderr.ifBlank { reloadConfigFailedText }
                            }
                        }
                    },
                    onStop = onStop,
                    onRestart = onReload
                )
            } else {
                HeroControlMode.Stopped(
                    onStart = onStart,
                    starting = status is ServiceStatus.Starting,
                    enabled = isStopped && isEnvReady
                )
            }
            HomeHeroSegmentedControl(mode = mode)
        }
    }
}

/**
 * 单行跑马灯文字 — 文字超长时自动滚动，两侧带透明渐隐边缘。
 * 文字不超出时静止显示，无边缘裁切。
 */
@Composable
private fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MiuixTheme.textStyles.footnote2,
    color: Color = MiuixTheme.colorScheme.onSurfaceSecondary,
    fadeEdgeWidth: Dp = 10.dp
) {
    val textMeasurer = rememberTextMeasurer()
    var containerWidth by remember { mutableIntStateOf(0) }
    val measuredWidth = remember(text, style) {
        textMeasurer.measure(text, style = style, maxLines = 1).size.width
    }
    val needsMarquee = containerWidth in 1..<measuredWidth

    Text(
        text = text,
        modifier = modifier
            .onSizeChanged { containerWidth = it.width }
            .then(
                if (needsMarquee) Modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val fw = fadeEdgeWidth.toPx()
                        if (fw > 0f && size.width > fw * 2f) {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    0f to Color.Transparent,
                                    fw / size.width to Color.Black,
                                    1f - fw / size.width to Color.Black,
                                    1f to Color.Transparent
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                    }
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        repeatDelayMillis = 3000,
                        initialDelayMillis = 2000,
                        velocity = 30.dp
                    )
                else Modifier
            ),
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = if (needsMarquee) TextOverflow.Clip else TextOverflow.Ellipsis
    )
}

@Composable
private fun HeroInfoRow(
    label: String,
    value: String,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (enabled && onClick != null) {
                    Modifier
                        .clip(RoundedRectangle(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onClick() }
                } else Modifier
            )
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 首页段式胶囊操作条
 *
 * 单个胶囊容器内包含 2-3 个操作：[重载] | [停止] | [重启]
 * 每段使用独立的语义色（Monet 自适应）：info / danger / warning
 * 段间用细垂直分隔线区分，整体符合 miuix 胶囊风格。
 */
/**
 * Hero 卡 segmented control（B4 升级）：
 *
 * 设计：永远是 50dp 高的 Capsule，根据 [mode] 决定段数与主导段位置。
 *   - Stopped：1 段「启动」全宽 dominant pill（success.container 填充）
 *   - Running：3 段（reload-config / stop / restart），中间「停止」dominant
 *     用 danger.container 内嵌 pill 高亮，左右两段透明背景仅文字色
 *
 * 切换 Stopped ↔ Running 时段数变化由外层 AnimatedContent + SizeTransform 平滑形变。
 * Capsule 容器在切换中保持不变，视觉上是"段从中间长大成三段"的过渡。
 */
@Composable
private fun HomeHeroSegmentedControl(mode: HeroControlMode) {
    val scheme = MiuixTheme.colorScheme
    val containerColor = scheme.surfaceContainer
    val dividerColor = scheme.dividerLine.copy(alpha = 0.5f)

    val info = homeInfoColors()
    val danger = homeDangerColors()
    val warning = homeWarningColors()
    val success = homeSuccessColors()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(Capsule())
            .background(containerColor),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (mode) {
            is HeroControlMode.Stopped -> {
                SegmentedActionItem(
                    text = stringResource(R.string.home_action_start),
                    color = success.accent,
                    enabled = mode.enabled,
                    loading = mode.starting,
                    dominant = true,
                    dominantContainer = success.container,
                    dominantContent = success.onContainer,
                    onClick = mode.onStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                )
            }
            is HeroControlMode.Running -> {
                if (mode.canReloadConfig) {
                    SegmentedActionItem(
                        text = stringResource(R.string.home_action_reload_config),
                        color = info.accent,
                        enabled = mode.enabled,
                        onClick = mode.onReloadConfig,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                    SegmentedDivider(color = dividerColor)
                }
                // 中间「停止」段是主导操作 — 用 danger.container 填充内嵌 pill
                SegmentedActionItem(
                    text = stringResource(R.string.home_action_stop),
                    color = danger.accent,
                    enabled = mode.enabled,
                    loading = mode.isStopping,
                    dominant = true,
                    dominantContainer = danger.container,
                    dominantContent = danger.onContainer,
                    onClick = mode.onStop,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                SegmentedDivider(color = dividerColor)
                SegmentedActionItem(
                    text = stringResource(R.string.home_action_reload),
                    color = warning.accent,
                    enabled = mode.enabled,
                    loading = mode.isRestarting,
                    onClick = mode.onRestart,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }
}

/**
 * 单段操作项。
 * - [dominant]=true：段内嵌 Capsule pill 用 [dominantContainer] 填充背景，
 *   文字色用 [dominantContent]，视觉上"占主导"，与停止状态的"启动"段对称
 * - [dominant]=false：透明背景 + [color] 文字色（次要操作）
 */
@Composable
private fun SegmentedActionItem(
    text: String,
    color: Color,
    enabled: Boolean,
    loading: Boolean = false,
    dominant: Boolean = false,
    dominantContainer: Color? = null,
    dominantContent: Color? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val effectiveFg = when {
        !enabled || loading -> color.copy(alpha = 0.38f)
        dominant -> dominantContent ?: color
        else -> color
    }
    val effectiveBg = if (dominant && enabled && !loading) {
        dominantContainer ?: Color.Transparent
    } else Color.Transparent

    val animatedFg by animateColorAsState(
        targetValue = effectiveFg,
        animationSpec = tween(durationMillis = 260),
        label = "segmented_item_fg"
    )
    val animatedBg by animateColorAsState(
        targetValue = effectiveBg,
        animationSpec = tween(durationMillis = 320),
        label = "segmented_item_bg"
    )
    Box(
        modifier = modifier
            .padding(horizontal = 3.dp, vertical = 4.dp)
            .clip(Capsule())
            .background(animatedBg)
            .clickable(enabled = enabled && !loading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = animatedFg
            )
        } else {
            Text(
                text = text,
                style = MiuixTheme.textStyles.button,
                fontWeight = FontWeight.SemiBold,
                color = animatedFg
            )
        }
    }
}

@Composable
private fun SegmentedDivider(color: Color) {
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .padding(vertical = 12.dp)
            .background(color)
    )
}

/** Hero 操作区状态机：根据服务状态选择 segmented control 布局 */
private sealed interface HeroControlMode {
    data class Stopped(
        val onStart: () -> Unit,
        val starting: Boolean,
        val enabled: Boolean
    ) : HeroControlMode

    data class Running(
        val canReloadConfig: Boolean,
        val onReloadConfig: () -> Unit,
        val onStop: () -> Unit,
        val onRestart: () -> Unit,
        val isStopping: Boolean,
        val isRestarting: Boolean,
        val enabled: Boolean
    ) : HeroControlMode
}

@Composable
// 0=check, 1=error, 2=refresh, 3=pause
// 每状态 2 笔画，每笔画 [startX,startY, ctrlX,ctrlY, endX,endY]
// quadraticBezierTo(ctrl, end) — 直线时 ctrl 在中点，弧线时 ctrl 偏移
private fun heroIconPaths(state: Int): Array<FloatArray> = when (state) {
    0 -> arrayOf( // ✓ Check（直线：ctrl 在两端中点）
        floatArrayOf(-0.42f, 0.04f, -0.27f, 0.20f, -0.12f, 0.36f),
        floatArrayOf(-0.12f, 0.36f, 0.18f, 0.02f, 0.48f, -0.32f)
    )
    1 -> arrayOf( // ! Error（两段直线）
        floatArrayOf(0f, -0.42f, 0f, -0.17f, 0f, 0.08f),
        floatArrayOf(0f, 0.28f, 0f, 0.32f, 0f, 0.36f)
    )
    2 -> arrayOf( // ↻ Refresh（两段弧线：ctrl 向外偏移成半圆）
        floatArrayOf(0.38f, 0.18f, 0.38f, -0.38f, -0.18f, -0.38f),
        floatArrayOf(-0.38f, -0.18f, -0.38f, 0.38f, 0.18f, 0.38f)
    )
    else -> arrayOf( // ⏸ Pause（两段直线）
        floatArrayOf(-0.20f, -0.34f, -0.20f, 0f, -0.20f, 0.34f),
        floatArrayOf(0.20f, -0.34f, 0.20f, 0f, 0.20f, 0.34f)
    )
}

@Composable
private fun HomeSheetSummary(text: String) {
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceSecondary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
    )
}

@Composable
private fun HomeSheetOptionItem(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    iconUrl: String? = null
) {
    val containerColor = if (selected) {
        MiuixTheme.colorScheme.primaryContainer
    } else {
        MiuixTheme.colorScheme.surfaceContainerHigh
    }
    val titleColor = if (selected) {
        MiuixTheme.colorScheme.onPrimaryContainer
    } else {
        MiuixTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        colors = CardDefaults.defaultColors(color = containerColor),
        onClick = onClick,
        showIndication = true,
        pressFeedbackType = PressFeedbackType.Sink
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (iconUrl != null) {
                AsyncImage(
                    model = iconUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            }
            Text(
                text = title,
                style = MiuixTheme.textStyles.body1,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = titleColor,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
