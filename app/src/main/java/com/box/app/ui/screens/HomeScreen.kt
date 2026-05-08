package com.box.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.box.app.data.backend.BoxApi
import com.box.app.data.backend.HomeMetricsApi
import com.box.app.data.model.IpMode
import com.box.app.data.model.ServiceStatus
import com.box.app.data.repo.HomeRepository
import com.box.app.ui.components.bottomsheets.SheetBlurEffect
import com.box.app.ui.components.bottomsheets.SystemBottomSheet
import com.box.app.ui.components.contentPaddingWithNavBars
import com.box.app.ui.components.home.EnterAnimateOnce
import com.box.app.ui.components.home.HomeCardModel
import com.box.app.ui.components.home.HomeHeader
import com.box.app.ui.components.home.HomeHeroCard
import com.box.app.ui.components.home.HomeLatencyCard
import com.box.app.ui.components.home.HomeMetricKind
import com.box.app.ui.components.home.HomeQuickActions
import com.box.app.ui.components.home.HomeTwoColumnGrid
import com.box.app.utils.LatencyTargetsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

private fun countryCodeToFlagEmoji(countryCode: String): String {
    val cc = countryCode.trim().uppercase()
    if (cc.length != 2) return ""
    val a = cc[0]
    val b = cc[1]
    if (a !in 'A'..'Z' || b !in 'A'..'Z') return ""
    val base = 0x1F1E6
    val first = base + (a.code - 'A'.code)
    val second = base + (b.code - 'A'.code)
    return String(Character.toChars(first)) + String(Character.toChars(second))
}

@Composable
fun HomeScreen(
    onNavVisibilityChange: (Boolean) -> Unit,
    onOpenLogs: () -> Unit,
    onOpenUpdateSubscription: () -> Unit,
    onOpenPanel: () -> Unit,
    onOpenSubStore: () -> Unit,
    onOpenNetSpeed: () -> Unit = {},
    onOpenSubscriptionDetail: () -> Unit = {},
    onOpenBaseProxyConfig: () -> Unit = {},
    onOpenLatencyTargets: () -> Unit = {}
) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            val res =
        }
    }
    val showSubStoreEntry by HomeRepository.showSubStoreEntry.collectAsState()
    val isSubscriptionClashApiEnabled by HomeRepository.useClashApiForSubscription.collectAsState()

    val latencyTargets by LatencyTargetsManager.targets.collectAsState()
    val serviceState by HomeRepository.serviceState.collectAsState()
    val metricsState by HomeRepository.metricsState.collectAsState()
    val context = LocalContext.current

    val subPrefs =
        remember { context.getSharedPreferences("subscription_settings", Context.MODE_PRIVATE) }
    val dialogPrefs =
        remember { context.getSharedPreferences("dialog_settings", Context.MODE_PRIVATE) }
    val layoutPrefs = remember { context.getSharedPreferences("home_layout", Context.MODE_PRIVATE) }

    val defaultOrder = remember {
        listOf("hero", "quick", "latency", "grid")
    }

    fun loadOrder(): List<String> {
        val raw = layoutPrefs.getString("order", null).orEmpty().trim()
        if (raw.isBlank()) return defaultOrder
        val list = raw.split(',').map { it.trim() }.filter { it.isNotBlank() }
        val known = defaultOrder.toSet()
        val filtered = list.filter { it in known }
        return (filtered + defaultOrder.filter { it !in filtered }).distinct()
    }

    fun loadHidden(): Set<String> {
        val raw = layoutPrefs.getString("hidden", null).orEmpty().trim()
        if (raw.isBlank()) return emptySet()
        return raw.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    fun persist(order: List<String>, hidden: Set<String>) {
        layoutPrefs.edit().putString("order", order.joinToString(","))
            .putString("hidden", hidden.joinToString(",")).apply()
    }

    val defaultMetricOrder = remember {
        listOf("ip", "net_speed", "subscription", "system")
    }

    fun loadMetricOrder(): List<String> {
        val raw = layoutPrefs.getString("metric_order", null).orEmpty().trim()
        if (raw.isBlank()) return defaultMetricOrder
        val list = raw.split(',').map { it.trim() }.filter { it.isNotBlank() }
        val known = defaultMetricOrder.toSet()
        val filtered = list.filter { it in known }
        return (filtered + defaultMetricOrder.filter { it !in filtered }).distinct()
    }

    fun loadMetricHidden(): Set<String> {
        val raw = layoutPrefs.getString("metric_hidden", null).orEmpty().trim()
        if (raw.isBlank()) return emptySet()
        return raw.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
    }

    fun persistMetric(order: List<String>, hidden: Set<String>) {
        layoutPrefs.edit().putString("metric_order", order.joinToString(","))
            .putString("metric_hidden", hidden.joinToString(",")).apply()
    }

    var sectionOrder by remember { mutableStateOf(loadOrder()) }
    var hiddenSections by remember { mutableStateOf(loadHidden()) }

    var metricOrder by remember { mutableStateOf(loadMetricOrder()) }
    var hiddenMetrics by remember { mutableStateOf(loadMetricHidden()) }

    LaunchedEffect(Unit) {
        sectionOrder = loadOrder()
        hiddenSections = loadHidden()
        metricOrder = loadMetricOrder()
        hiddenMetrics = loadMetricHidden()
    }

    val moduleLink = "https://github.com/boxproxy/box"
    val env = serviceState.env
    var lastDialogKey by rememberSaveable { mutableStateOf("") }

    var showSystemDialog by rememberSaveable { mutableStateOf(false) }
    var showIpGeoDialog by rememberSaveable { mutableStateOf(false) }
    var ipGeoLoading by remember { mutableStateOf(false) }
    var ipGeoV4 by remember { mutableStateOf<HomeMetricsApi.PublicGeoIpInfo?>(null) }
    var ipGeoV6 by remember { mutableStateOf<HomeMetricsApi.PublicGeoIpInfo?>(null) }

    var showHomeLayoutSheet by rememberSaveable { mutableStateOf(false) }

    val isRunning = serviceState.status is ServiceStatus.Running

    // Bottom sheets

    val navBarPadding = com.box.app.ui.components.systemNavBarBottomPadding()

    val blurEnabled = com.box.app.utils.ThemeManager.shouldUseBlurEffects()
    val sheetBg = MiuixTheme.colorScheme.surfaceContainer

    if (showSystemDialog) {
        if (blurEnabled) SheetBlurEffect()
        WindowBottomSheet(
            show = showSystemDialog,
            onDismissRequest = { showSystemDialog = false },
            backgroundColor = sheetBg,
            dragHandleColor = Color.Transparent
        ) {
            SystemBottomSheet()
            Spacer(modifier = Modifier.height(navBarPadding))
        }
    }

    if (showIpGeoDialog) {
        if (blurEnabled) SheetBlurEffect()
        WindowBottomSheet(
            show = showIpGeoDialog,
            onDismissRequest = { showIpGeoDialog = false },
            backgroundColor = sheetBg,
        ) {
            IpGeoBottomSheet(
                loading = ipGeoLoading, ipv4 = ipGeoV4, ipv6 = ipGeoV6
            )
            Spacer(modifier = Modifier.height(navBarPadding))
        }
    }

    fun moveSection(id: String, delta: Int) {
        val idx = sectionOrder.indexOf(id)
        if (idx < 0) return
        val to = (idx + delta).coerceIn(0, sectionOrder.lastIndex)
        if (to == idx) return
        sectionOrder = sectionOrder.toMutableList().apply {
            add(to, removeAt(idx))
        }
        persist(sectionOrder, hiddenSections)
    }

    fun hideSection(id: String) {
        hiddenSections = hiddenSections + id
        persist(sectionOrder, hiddenSections)
    }

    fun unhideSection(id: String) {
        hiddenSections = hiddenSections - id
        persist(sectionOrder, hiddenSections)
    }

    fun moveMetric(id: String, delta: Int) {
        val idx = metricOrder.indexOf(id)
        if (idx < 0) return
        val to = (idx + delta).coerceIn(0, metricOrder.lastIndex)
        if (to == idx) return
        metricOrder = metricOrder.toMutableList().apply {
            add(to, removeAt(idx))
        }
        persistMetric(metricOrder, hiddenMetrics)
    }

    fun hideMetric(id: String) {
        hiddenMetrics = hiddenMetrics + id
        persistMetric(metricOrder, hiddenMetrics)
    }

    fun unhideMetric(id: String) {
        hiddenMetrics = hiddenMetrics - id
        persistMetric(metricOrder, hiddenMetrics)
    }

    if (showHomeLayoutSheet) {
        if (blurEnabled) SheetBlurEffect()
        WindowBottomSheet(
            show = showHomeLayoutSheet,
            title = stringResource(R.string.home_layout_title),
            onDismissRequest = { showHomeLayoutSheet = false },
            backgroundColor = sheetBg
            // 不再透明化 dragHandleColor，直接复用 WindowBottomSheet 内置 miuix 拖动条；
            // 旧实现额外画了一条 HomeSheetHandle()，导致顶部出现两条拖动条
        ) {
            HomeLayoutSheetContent(
                sectionOrder = sectionOrder,
                hiddenSections = hiddenSections,
                metricOrder = metricOrder,
                hiddenMetrics = hiddenMetrics,
                onMoveSection = ::moveSection,
                onHideSection = ::hideSection,
                onUnhideSection = ::unhideSection,
                onMoveMetric = ::moveMetric,
                onHideMetric = ::hideMetric,
                onUnhideMetric = ::unhideMetric
            )
            Spacer(modifier = Modifier.height(navBarPadding))
        }
    }

    // Environment dialog

    val rawDialogKey = when {
        !env.hasRoot -> "root"
        !env.hasModule && !dialogPrefs.getBoolean("hide_module_dialog", false) -> "module"
        !env.hasScripts && !dialogPrefs.getBoolean("hide_scripts_dialog", false) -> "scripts"
        else -> ""
    }
    var debouncedDialogKey by rememberSaveable { mutableStateOf("") }
    val envDialogDebounceMs = 8_000L

    LaunchedEffect(env.checked, rawDialogKey) {
        if (!env.checked || rawDialogKey.isBlank()) {
            debouncedDialogKey = ""
            return@LaunchedEffect
        }
        delay(envDialogDebounceMs)
        if (env.checked && rawDialogKey.isNotBlank()) {
            debouncedDialogKey = rawDialogKey
        }
    }

    LaunchedEffect(env.isReady) {
        if (env.isReady) {
            lastDialogKey = ""
            debouncedDialogKey = ""
        }
    }

    val dialogKey = debouncedDialogKey
    val shouldShowDialog =
        env.checked && dialogKey.isNotBlank() && dialogKey == rawDialogKey && dialogKey != lastDialogKey

    if (shouldShowDialog) {
        OverlayDialog(
            show = true,
            onDismissRequest = {},
            title = when (dialogKey) {
                "root" -> stringResource(R.string.home_env_title_root_required)
                "module" -> stringResource(R.string.home_env_title_module_missing)
                else -> stringResource(R.string.home_env_title_environment_missing)
            },
            summary = when (dialogKey) {
                "root" -> stringResource(R.string.home_env_body_root_required)
                "module" -> stringResource(R.string.home_env_body_module_missing)
                else -> stringResource(R.string.home_env_body_scripts_missing)
            },
            backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
            titleColor = MiuixTheme.colorScheme.onSurface,
            summaryColor = MiuixTheme.colorScheme.onSurfaceSecondary
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End
            ) {
                when (dialogKey) {
                    "module" -> {
                        TextButton(
                            text = stringResource(R.string.home_env_btn_skip), onClick = {
                                lastDialogKey = dialogKey
                                dialogPrefs.edit().putBoolean("hide_module_dialog", true).apply()
                            })
                        TextButton(
                            text = stringResource(R.string.home_env_btn_exit), onClick = {
                                lastDialogKey = dialogKey
                                (context as? Activity)?.finish()
                            })
                        TextButton(
                            text = stringResource(R.string.home_env_btn_install), onClick = {
                                lastDialogKey = dialogKey
                                try {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW, Uri.parse(moduleLink)
                                        )
                                    )
                                } catch (_: Exception) {
                                } finally {
                                    (context as? Activity)?.finish()
                                }
                            })
                    }

                    "scripts" -> {
                        TextButton(
                            text = stringResource(R.string.home_env_btn_skip), onClick = {
                                lastDialogKey = dialogKey
                                dialogPrefs.edit().putBoolean("hide_scripts_dialog", true).apply()
                            })
                        TextButton(
                            text = stringResource(R.string.home_env_btn_exit), onClick = {
                                lastDialogKey = dialogKey
                                (context as? Activity)?.finish()
                            })
                    }

                    else -> {
                        TextButton(
                            text = stringResource(R.string.home_env_btn_ok), onClick = {
                                lastDialogKey = dialogKey
                                (context as? Activity)?.finish()
                            })
                    }
                }
            }
        }
    }

    // Initialize repository

    LaunchedEffect(Unit) {
        fun parseProxyTrafficFilter(text: String): List<String> {
            return text.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        }

        val defaultProxyTrafficFilter = "DIRECT,REJECT"
        val filterText =
            subPrefs.getString("proxy_traffic_filter_chains", defaultProxyTrafficFilter)
                ?: defaultProxyTrafficFilter
        BoxApi.setProxyTrafficFilterChains(parseProxyTrafficFilter(filterText))

        HomeRepository.setUseClashApiForSubscription(subPrefs.getBoolean("use_clash_api", false))
        HomeRepository.setUseClashApiForNetSpeed(
            subPrefs.getBoolean(
                "use_clash_api_net_speed", false
            )
        )
        HomeRepository.startPolling()
    }

    LaunchedEffect(showIpGeoDialog) {
        if (!showIpGeoDialog) return@LaunchedEffect
        ipGeoLoading = true
        ipGeoV4 = null
        ipGeoV6 = null
        val (v4, v6) = withContext(Dispatchers.IO) {
            coroutineScope {
                val reqV4 = async { HomeMetricsApi.getPublicGeoIp(isIpv6 = false) }
                val reqV6 = async { HomeMetricsApi.getPublicGeoIp(isIpv6 = true) }
                reqV4.await() to reqV6.await()
            }
        }
        ipGeoV4 = v4
        ipGeoV6 = v6
        ipGeoLoading = false
    }

    // 当状态栏为 OPAQUE 时 AppTheme 已全局添加 statusBars padding，此处不再重复
    val systemBarSettings by com.box.app.utils.ThemeManager.systemBarSettings.collectAsState()
    val statusBarInset =
        if (systemBarSettings.statusBar == com.box.app.utils.SystemBarMode.OPAQUE) {
            0.dp
        } else {
            WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        }

    // 根据屏幕可用高度（减去状态栏）决定布局密度
    // 去除 SmallTitle 后改用"呼吸感间距"代替段标题视觉分隔（A2）
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val isCompact = screenHeightDp < 780.dp
    val sectionGap = if (isCompact) 12.dp else 18.dp

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isMediumWidth = maxWidth >= 700.dp
        val isExpandedWidth = maxWidth >= 1000.dp
        val contentMaxWidth = when {
            isExpandedWidth -> 1180.dp
            isMediumWidth -> 960.dp
            else -> Dp.Unspecified
        }
        val pagePadding = when {
            isExpandedWidth -> 24.dp
            isMediumWidth -> 20.dp
            else -> 16.dp
        }
        val metricColumns = when {
            isExpandedWidth -> 4
            isMediumWidth -> 3
            else -> 2
        }
        val useCompactQuickActions = isCompact && !isMediumWidth

        Box(
            modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (contentMaxWidth != Dp.Unspecified) {
                            Modifier.widthIn(max = contentMaxWidth)
                        } else {
                            Modifier
                        }
                    ), contentPadding = contentPaddingWithNavBars(
                    start = pagePadding,
                    end = pagePadding,
                    top = statusBarInset + 4.dp,
                    extraBottom = 8.dp
                ), verticalArrangement = Arrangement.spacedBy(sectionGap)
            ) {
                item(key = "home_header") {
                    EnterAnimateOnce(delayMs = 0) {
                        HomeHeader(onEdit = { showHomeLayoutSheet = true })
                    }
                }
                sectionOrder.filter { it !in hiddenSections }.forEachIndexed { sectionIdx, id ->
                    // 入场延迟：每段错开 60ms 形成"瀑布"入场，最大 240ms 防过长等待
                    val enterDelay = (60 + sectionIdx * 60).coerceAtMost(240)
                    when (id) {
                        "hero" -> item(key = "home_hero") {
                            EnterAnimateOnce(delayMs = enterDelay) {
                                HomeHeroCard(
                                    serviceState = serviceState,
                                    onStart = { HomeRepository.startService() },
                                    onStop = { HomeRepository.stopService() },
                                    onReload = { HomeRepository.restartService() },
                                    onOpenBaseProxyConfig = onOpenBaseProxyConfig
                                )
                            }
                        }

                        "quick" -> item(key = "home_quick") {
                            EnterAnimateOnce(delayMs = enterDelay) {
                                HomeQuickActions(
                                    showSubStore = showSubStoreEntry,
                                    onOpenPanel = onOpenPanel,
                                    onOpenSubStore = onOpenSubStore,
                                    onOpenLogs = onOpenLogs,
                                    compact = useCompactQuickActions
                                )
                            }
                        }

                        "latency" -> item(key = "home_latency") {
                            EnterAnimateOnce(delayMs = enterDelay) {
                                HomeLatencyCard(
                                label1 = latencyTargets.getOrNull(0)?.name
                                    ?: stringResource(R.string.home_latency_baidu),
                                baidu = metricsState.latencyBaiduMs,
                                label2 = latencyTargets.getOrNull(1)?.name
                                    ?: stringResource(R.string.home_latency_cloudflare),
                                cloudflare = metricsState.latencyCloudflareMs,
                                label3 = latencyTargets.getOrNull(2)?.name
                                    ?: stringResource(R.string.home_latency_google),
                                google = metricsState.latencyGoogleMs,
                                loading = metricsState.latencyLoading,
                                onRefresh = { HomeRepository.refreshLatencyNow() },
                                onOpenTargets = onOpenLatencyTargets,
                                compact = isCompact && !isMediumWidth
                            )
                            }
                        }

                        "grid" -> item(key = "home_grid") {
                            EnterAnimateOnce(delayMs = enterDelay) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                val metricModels =
                                    metricOrder.filter { it !in hiddenMetrics }.mapNotNull { mid ->
                                            when (mid) {
                                                "ip" -> HomeCardModel(
                                                    title = stringResource(R.string.home_card_ip),
                                                    value = metricsState.ip,
                                                    subtitle = if (metricsState.ipMode == IpMode.LAN) {
                                                        stringResource(
                                                            R.string.home_ip_subtitle_interface,
                                                            metricsState.lanInterface.takeIf { it.isNotBlank() && it != "-" }
                                                                ?: "-")
                                                    } else {
                                                        stringResource(
                                                            R.string.home_ip_subtitle_region,
                                                            metricsState.publicCountry.takeIf { it.isNotBlank() && it != "-" }
                                                                ?: metricsState.publicCountryCode.takeIf { it.isNotBlank() }
                                                                ?: "-")
                                                    },
                                                    kind = HomeMetricKind.Ip,
                                                    accent = MiuixTheme.colorScheme.primary,
                                                    badgeText = if (metricsState.ipMode.name == "LAN") {
                                                        stringResource(R.string.home_ip_badge_lan)
                                                    } else {
                                                        val cc =
                                                            metricsState.publicCountryCode.trim()
                                                        val flag = countryCodeToFlagEmoji(cc)
                                                        if (flag.isBlank()) stringResource(R.string.home_ip_badge_pub) else stringResource(
                                                            R.string.home_ip_badge_pub_with_flag,
                                                            flag
                                                        )
                                                    },
                                                    cornerActionIcon = if (metricsState.ipMode == IpMode.PUBLIC) Icons.AutoMirrored.Filled.KeyboardArrowRight else null,
                                                    onCornerAction = if (metricsState.ipMode == IpMode.PUBLIC) {
                                                        { showIpGeoDialog = true }
                                                    } else null,
                                                    onClick = { HomeRepository.toggleIpMode() })

                                                "net_speed" -> HomeCardModel(
                                                    title = stringResource(R.string.home_card_net_speed),
                                                    value = metricsState.netDown,
                                                    subtitle = if (metricsState.netUp == "-") stringResource(
                                                        R.string.home_net_up_unknown
                                                    ) else stringResource(
                                                        R.string.home_net_up_value,
                                                        metricsState.netUp
                                                    ),
                                                    kind = HomeMetricKind.Speed,
                                                    accent = MiuixTheme.colorScheme.primary,
                                                    sparkDown = metricsState.netDownHistory,
                                                    sparkUp = metricsState.netUpHistory,
                                                    onClick = onOpenNetSpeed
                                                )

                                                "subscription" -> HomeCardModel(
                                                    title = stringResource(R.string.home_card_subscription),
                                                    value = if (metricsState.subscriptionTotalBytes > java.math.BigInteger.ZERO) HomeMetricsApi.formatBytes(
                                                        metricsState.subscriptionUsedBytes
                                                    ) else "-",
                                                    subtitle = if (metricsState.subscriptionTotalBytes > java.math.BigInteger.ZERO) HomeMetricsApi.formatBytes(
                                                        metricsState.subscriptionTotalBytes
                                                    ) else "-",
                                                    kind = HomeMetricKind.Subscription,
                                                    accent = MiuixTheme.colorScheme.onTertiaryContainer,
                                                    progress = metricsState.subscriptionProgress,
                                                    badgeText = metricsState.subscriptionProgress?.let {
                                                        val remainPct = ((1f - it.coerceIn(
                                                            0f,
                                                            1f
                                                        )) * 100f).toInt().coerceIn(0, 100)
                                                        stringResource(
                                                            R.string.home_subscription_remain_badge,
                                                            remainPct
                                                        )
                                                    },
                                                    onClick = {
                                                        if (isSubscriptionClashApiEnabled) HomeRepository.refreshSubscriptionNow()
                                                        else HomeRepository.refreshSubscriptionIfUrlsChanged()
                                                        onOpenSubscriptionDetail()
                                                    })

                                                "system" -> HomeCardModel(
                                                    title = stringResource(R.string.home_card_system),
                                                    value = metricsState.cpu,
                                                    subtitle = metricsState.ram,
                                                    kind = HomeMetricKind.System,
                                                    accent = MiuixTheme.colorScheme.secondary,
                                                    isActive = isRunning,
                                                    onClick = { showSystemDialog = true })

                                                else -> null
                                            }
                                        }
                                HomeTwoColumnGrid(models = metricModels, columns = metricColumns)
                            }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun homeSectionTitleRes(id: String): Int? = when (id) {
    "hero" -> R.string.home_layout_section_hero
    "quick" -> R.string.home_layout_section_quick
    "latency" -> R.string.home_layout_section_latency
    "grid" -> R.string.home_layout_section_grid
    else -> null
}

private fun homeMetricTitleRes(id: String): Int? = when (id) {
    "ip" -> R.string.home_card_ip
    "net_speed" -> R.string.home_card_net_speed
    "subscription" -> R.string.home_card_subscription
    "system" -> R.string.home_card_system
    else -> null
}

/**
 * 主页布局编辑底部表单内容
 * 使用 miuix 标准模式：SmallTitle 段标题（卡外）+ Card 内分隔的 BasicComponent 行 + 末位 IconButton 操作
 * 底部表单自带的 drag handle / title 由 WindowBottomSheet 提供，避免重复
 *
 * 位置改变动画：每段卡片包裹在 LookaheadScope 中，行使用 Modifier.animateBounds 跟踪
 * 上下移、隐藏后的位置形变；卡片自身用 animateContentSize 处理高度增减
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HomeLayoutSheetContent(
    sectionOrder: List<String>,
    hiddenSections: Set<String>,
    metricOrder: List<String>,
    hiddenMetrics: Set<String>,
    onMoveSection: (String, Int) -> Unit,
    onHideSection: (String) -> Unit,
    onUnhideSection: (String) -> Unit,
    onMoveMetric: (String, Int) -> Unit,
    onHideMetric: (String) -> Unit,
    onUnhideMetric: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val visibleSections =
        remember(sectionOrder, hiddenSections) { sectionOrder.filter { it !in hiddenSections } }
    val hiddenSectionList =
        remember(sectionOrder, hiddenSections) { sectionOrder.filter { it in hiddenSections } }
    val visibleMetrics =
        remember(metricOrder, hiddenMetrics) { metricOrder.filter { it !in hiddenMetrics } }
    val hiddenMetricList =
        remember(metricOrder, hiddenMetrics) { metricOrder.filter { it in hiddenMetrics } }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
    ) {
        // ── 主区块：可见模块 ────────────────────────────────────────────
        HomeLayoutSection(title = stringResource(R.string.home_layout_visible)) { lookahead ->
            visibleSections.forEachIndexed { idx, id ->
                HomeLayoutBasicRow(
                    lookaheadScope = lookahead,
                    rowKey = "vis_$id",
                    title = stringResource(homeSectionTitleRes(id) ?: R.string.home_layout_title),
                    canMoveUp = idx > 0,
                    canMoveDown = idx < visibleSections.lastIndex,
                    showDivider = idx != visibleSections.lastIndex,
                    onMoveUp = { onMoveSection(id, -1) },
                    onMoveDown = { onMoveSection(id, 1) },
                    onHide = { onHideSection(id) })
            }
        }

        // ── 已隐藏（仅在有项时显示） ────────────────────────────────────
        if (hiddenSectionList.isNotEmpty()) {
            HomeLayoutSection(title = stringResource(R.string.home_layout_hidden)) { lookahead ->
                hiddenSectionList.forEachIndexed { idx, id ->
                    HomeLayoutRestoreBasicRow(
                        lookaheadScope = lookahead,
                        rowKey = "hid_$id",
                        title = stringResource(
                            homeSectionTitleRes(id) ?: R.string.home_layout_hidden
                        ),
                        showDivider = idx != hiddenSectionList.lastIndex,
                        onClick = { onUnhideSection(id) })
                }
            }
        }

        // ── 指标卡片（grid 显示时才出现） ───────────────────────────────
        if ("grid" !in hiddenSections) {
            HomeLayoutSection(title = stringResource(R.string.home_layout_metrics_title)) { lookahead ->
                visibleMetrics.forEachIndexed { idx, id ->
                    HomeLayoutBasicRow(
                        lookaheadScope = lookahead,
                        rowKey = "vmetric_$id",
                        title = stringResource(
                            homeMetricTitleRes(id) ?: R.string.home_layout_metrics_title
                        ),
                        canMoveUp = idx > 0,
                        canMoveDown = idx < visibleMetrics.lastIndex,
                        showDivider = idx != visibleMetrics.lastIndex,
                        onMoveUp = { onMoveMetric(id, -1) },
                        onMoveDown = { onMoveMetric(id, 1) },
                        onHide = { onHideMetric(id) })
                }
            }

            if (hiddenMetricList.isNotEmpty()) {
                HomeLayoutSection(title = stringResource(R.string.home_layout_hidden_metrics_title)) { lookahead ->
                    hiddenMetricList.forEachIndexed { idx, id ->
                        HomeLayoutRestoreBasicRow(
                            lookaheadScope = lookahead,
                            rowKey = "hmetric_$id",
                            title = stringResource(
                                homeMetricTitleRes(id) ?: R.string.home_layout_hidden_metrics_title
                            ),
                            showDivider = idx != hiddenMetricList.lastIndex,
                            onClick = { onUnhideMetric(id) })
                    }
                }
            }
        }

        Spacer(
            modifier = Modifier.height(
                com.box.app.ui.components.systemNavBarBottomPadding().coerceAtMost(16.dp)
            )
        )
    }
}

/**
 * miuix 风格段：SmallTitle（卡外，使用 onBackgroundVariant）+ 包裹 Card（onSurface 容器）
 *
 * 内容部分以 LookaheadScope 提供给 content 闭包，让子行能用 animateBounds 跟踪位置；
 * Card 本身使用 animateContentSize 平滑切换高度（行数增减时）
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HomeLayoutSection(
    title: String, content: @Composable (LookaheadScope) -> Unit
) {
    SmallTitle(
        text = title, insideMargin = PaddingValues(horizontal = 28.dp, vertical = 8.dp)
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .padding(bottom = 6.dp)
            .animateContentSize(
                animationSpec = spring(
                    stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f
                )
            ), cornerRadius = 18.dp, insideMargin = PaddingValues(0.dp)
    ) {
        LookaheadScope {
            val ls: LookaheadScope = this
            Column { content(ls) }
        }
    }
}

/**
 * 标准行：BasicComponent + 三个尾部操作图标（上移 / 下移 / 隐藏）
 * 上下移到达边界时禁用对应按钮，给出更准确的可点击提示
 *
 * 位置形变：通过 [animateBounds] 在 [lookaheadScope] 中跟踪上下行的 Y 位移并平滑过渡
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HomeLayoutBasicRow(
    lookaheadScope: LookaheadScope,
    rowKey: String,
    title: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    showDivider: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onHide: () -> Unit
) {
    key(rowKey) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateBounds(
                    lookaheadScope = lookaheadScope, boundsTransform = { _, _ ->
                        spring(
                            stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f
                        )
                    })) {
            BasicComponent(
                title = title, endActions = {
                    HomeLayoutEndIcon(
                        icon = Icons.Filled.ArrowUpward, enabled = canMoveUp, onClick = onMoveUp
                    )
                    HomeLayoutEndIcon(
                        icon = Icons.Filled.ArrowDownward,
                        enabled = canMoveDown,
                        onClick = onMoveDown
                    )
                    HomeLayoutEndIcon(
                        icon = Icons.Filled.VisibilityOff, onClick = onHide
                    )
                })
            if (showDivider) HomeLayoutDivider()
        }
    }
}

/**
 * 已隐藏项的恢复行：整行可点 → 还原显示
 * 同样在 [lookaheadScope] 中应用 [animateBounds] 处理位置变化
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun HomeLayoutRestoreBasicRow(
    lookaheadScope: LookaheadScope,
    rowKey: String,
    title: String,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    key(rowKey) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateBounds(
                    lookaheadScope = lookaheadScope, boundsTransform = { _, _ ->
                        spring(
                            stiffness = Spring.StiffnessMediumLow, dampingRatio = 0.85f
                        )
                    })) {
            BasicComponent(
                title = title, endActions = {
                    Icon(
                        imageVector = Icons.Filled.Visibility,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }, onClick = onClick
            )
            if (showDivider) HomeLayoutDivider()
        }
    }
}

/**
 * 行尾图标按钮：紧凑 32×32，无背景，仅图标 tint 体现可用状态
 */
@Composable
private fun HomeLayoutEndIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val scheme = MiuixTheme.colorScheme
    IconButton(
        onClick = onClick,
        backgroundColor = Color.Transparent,
        cornerRadius = 12.dp,
        enabled = enabled,
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) scheme.onSurface else scheme.onSurfaceSecondary.copy(alpha = 0.4f),
            modifier = Modifier.size(18.dp)
        )
    }
}

/** Card 内行间分隔线 */
@Composable
private fun HomeLayoutDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(0.5.dp)
            .background(MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.10f))
    )
}

@Composable
private fun IpGeoBottomSheet(
    loading: Boolean, ipv4: HomeMetricsApi.PublicGeoIpInfo?, ipv6: HomeMetricsApi.PublicGeoIpInfo?
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 标题区域
        Column {
            Text(
                text = stringResource(R.string.home_ip_geo_sheet_title),
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.home_ip_geo_sheet_subtitle),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        IpGeoSectionCard(
            title = stringResource(R.string.home_ip_geo_ipv4_title), loading = loading, info = ipv4
        )
        IpGeoSectionCard(
            title = stringResource(R.string.home_ip_geo_ipv6_title), loading = loading, info = ipv6
        )

        Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
    }
}

@Composable
private fun IpGeoSectionCard(
    title: String, loading: Boolean, info: HomeMetricsApi.PublicGeoIpInfo?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(16.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface
            )

            when {
                loading -> {
                    val baseText = stringResource(R.string.home_ip_geo_loading)
                    var dotCount by remember { mutableIntStateOf(0) }
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(500L)
                            dotCount = (dotCount + 1) % 4
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator(
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = baseText + ".".repeat(dotCount),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                }

                info == null -> {
                    Text(
                        text = stringResource(R.string.home_ip_geo_unavailable),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }

                else -> {
                    val flag = countryCodeToFlagEmoji(info.countryCode)
                    val locationName =
                        info.country.takeIf { it.isNotBlank() && it != "-" } ?: info.countryCode
                    val locationValue = if (flag.isBlank()) locationName else "$locationName $flag"
                    val asnValue = buildString {
                        val asnRaw = info.asn.trim()
                        val orgRaw = info.asnOrganization.trim()
                        val asnPart = if (asnRaw.isBlank() || asnRaw == "-") "" else {
                            if (asnRaw.startsWith("AS", ignoreCase = true)) asnRaw else "AS$asnRaw"
                        }
                        if (asnPart.isNotBlank()) append(asnPart)
                        if (orgRaw.isNotBlank() && orgRaw != "-") {
                            if (isNotBlank()) append(" ")
                            append(orgRaw)
                        }
                        if (isBlank()) append("-")
                    }
                    IpGeoInfoRow(
                        Icons.Filled.Language,
                        stringResource(R.string.home_ip_geo_field_ip),
                        info.ip
                    )
                    IpGeoInfoRow(
                        Icons.Filled.LocationOn,
                        stringResource(R.string.home_ip_geo_field_location),
                        locationValue
                    )
                    IpGeoInfoRow(
                        Icons.Filled.Business,
                        stringResource(R.string.home_ip_geo_field_isp),
                        info.isp
                    )
                    IpGeoInfoRow(
                        Icons.Filled.Hub, stringResource(R.string.home_ip_geo_field_asn), asnValue
                    )
                }
            }
        }
    }
}

@Composable
private fun IpGeoInfoRow(
    icon: ImageVector, label: String, value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 170.dp)
        )
        Text(
            text = value.ifBlank { "-" },
            style = MiuixTheme.textStyles.body2,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
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
