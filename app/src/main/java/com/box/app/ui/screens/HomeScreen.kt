package com.box.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.box.app.utils.ThemeManager
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import com.box.app.data.backend.ShellExecutor
import com.box.app.data.backend.HomeMetricsApi
import com.box.app.data.model.ServiceStatus
import com.box.app.data.model.IpMode
import com.box.app.data.repo.HomeRepository
import com.box.app.ui.components.home.HomeHeader
import com.box.app.ui.components.home.HomeHeroCard
import com.box.app.ui.components.home.HomeQuickActions
import com.box.app.ui.components.home.HomeTwoColumnGrid
import com.box.app.ui.components.home.LatencyWideCard
import com.box.app.ui.components.home.HomeCardModel
import com.box.app.ui.components.home.HomeMetricKind
import com.box.app.ui.components.bottomsheets.SubscriptionBottomSheet
import com.box.app.ui.components.bottomsheets.SystemBottomSheet
import com.box.app.ui.components.bottomsheets.NetSpeedBottomSheet
import com.box.app.ui.components.bottomsheets.AppModalBottomSheet
import com.box.app.ui.components.contentPaddingWithNavBars
import com.box.app.ui.theme.appColors
import com.box.app.ui.theme.appAccentColor
import com.box.app.data.backend.BoxApi
import com.box.app.R
import com.box.app.utils.LatencyTargetsManager

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavVisibilityChange: (Boolean) -> Unit,
    onOpenLogs: () -> Unit,
    onOpenUpdateSubscription: () -> Unit,
    onOpenPanel: () -> Unit,
    onOpenSubStore: () -> Unit
) {
    val c = appColors()
    val scope = rememberCoroutineScope()
    val pagePadding = 16.dp
    val listState = rememberLazyListState()
    val showSubStoreEntry by HomeRepository.showSubStoreEntry.collectAsState()
    val isSubscriptionClashApiEnabled by HomeRepository.useClashApiForSubscription.collectAsState()

    val latencyTargets by LatencyTargetsManager.targets.collectAsState()
    val serviceState by HomeRepository.serviceState.collectAsState()
    val metricsState by HomeRepository.metricsState.collectAsState()
    val context = LocalContext.current

    val subPrefs = remember { context.getSharedPreferences("subscription_settings", Context.MODE_PRIVATE) }
    val dialogPrefs = remember { context.getSharedPreferences("dialog_settings", Context.MODE_PRIVATE) }
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
        layoutPrefs.edit()
            .putString("order", order.joinToString(","))
            .putString("hidden", hidden.joinToString(","))
            .apply()
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
        layoutPrefs.edit()
            .putString("metric_order", order.joinToString(","))
            .putString("metric_hidden", hidden.joinToString(","))
            .apply()
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

    var showSubsDialog by rememberSaveable { mutableStateOf(false) }
    var showSystemDialog by rememberSaveable { mutableStateOf(false) }
    var showNetSpeedDialog by rememberSaveable { mutableStateOf(false) }
    var showIpGeoDialog by rememberSaveable { mutableStateOf(false) }
    var ipGeoLoading by remember { mutableStateOf(false) }
    var ipGeoV4 by remember { mutableStateOf<HomeMetricsApi.PublicGeoIpInfo?>(null) }
    var ipGeoV6 by remember { mutableStateOf<HomeMetricsApi.PublicGeoIpInfo?>(null) }

    var showHomeLayoutSheet by rememberSaveable { mutableStateOf(false) }

    val isRunning = serviceState.status is ServiceStatus.Running

    // Bottom sheets

    if (showSubsDialog) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        AppModalBottomSheet(
            onDismissRequest = { showSubsDialog = false },
            sheetState = sheetState
        ) {
            SubscriptionBottomSheet(
                items = metricsState.subscriptionItems,
                onRefresh = { url: String -> HomeRepository.refreshSubscriptionItemNow(url) },
                isClashApiEnabled = isSubscriptionClashApiEnabled,
                onOpenToolsSubscription = {
                    scope.launch {
                        onOpenUpdateSubscription()
                        sheetState.hide()
                        showSubsDialog = false
                    }
                }
            )
        }
    }

    if (showSystemDialog) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        AppModalBottomSheet(
            onDismissRequest = { showSystemDialog = false },
            sheetState = sheetState
        ) {
            SystemBottomSheet(sheetState = sheetState)
        }
    }

    if (showNetSpeedDialog) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        AppModalBottomSheet(
            onDismissRequest = { showNetSpeedDialog = false },
            sheetState = sheetState
        ) {
            NetSpeedBottomSheet(metrics = metricsState)
        }
    }

    if (showIpGeoDialog) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        AppModalBottomSheet(
            onDismissRequest = { showIpGeoDialog = false },
            sheetState = sheetState
        ) {
            IpGeoBottomSheet(
                loading = ipGeoLoading,
                ipv4 = ipGeoV4,
                ipv6 = ipGeoV6
            )
        }
    }

    if (showHomeLayoutSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        AppModalBottomSheet(
            onDismissRequest = { showHomeLayoutSheet = false },
            sheetState = sheetState
        ) {
            val scrollState = rememberScrollState()
            val isDark = ThemeManager.shouldUseDarkTheme()

            @Composable
            fun titleFor(id: String): String {
                return when (id) {
                    "hero" -> stringResource(R.string.home_layout_section_hero)
                    "quick" -> stringResource(R.string.home_layout_section_quick)
                    "latency" -> stringResource(R.string.home_layout_section_latency)
                    "grid" -> stringResource(R.string.home_layout_section_grid)
                    else -> id
                }
            }

            @Composable
            fun metricTitleFor(id: String): String {
                return when (id) {
                    "ip" -> stringResource(R.string.home_card_ip)
                    "net_speed" -> stringResource(R.string.home_card_net_speed)
                    "subscription" -> stringResource(R.string.home_card_subscription)
                    "system" -> stringResource(R.string.home_card_system)
                    else -> id
                }
            }

            fun move(id: String, delta: Int) {
                val idx = sectionOrder.indexOf(id)
                if (idx < 0) return
                val to = (idx + delta).coerceIn(0, sectionOrder.lastIndex)
                if (to == idx) return
                sectionOrder = sectionOrder.toMutableList().apply {
                    add(to, removeAt(idx))
                }
                persist(sectionOrder, hiddenSections)
            }

            fun hide(id: String) {
                hiddenSections = hiddenSections + id
                persist(sectionOrder, hiddenSections)
            }

            fun unhide(id: String) {
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

            @Composable
            fun SheetHandle() {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.Center)
                            .clearAndSetSemantics { }
                            .size(width = 28.dp, height = 3.dp)
                            .clip(Capsule())
                            .background(c.divider.copy(alpha = 0.42f))
                    )
                }
            }

            @Composable
            fun DividerLine() {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(c.divider)
                )
            }

            @Composable
            fun ActionIcon(
                imageVector: androidx.compose.ui.graphics.vector.ImageVector,
                onClick: () -> Unit
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(Capsule())
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick
                        ),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Icon(
                        imageVector = imageVector,
                        contentDescription = null,
                        tint = c.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 0.dp)
            ) {
                SheetHandle()
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
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
                                text = stringResource(R.string.home_layout_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = c.textPrimary
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = stringResource(R.string.home_layout_visible),
                                style = MaterialTheme.typography.labelLarge,
                                color = c.textSecondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            val visible = sectionOrder.filter { it !in hiddenSections }
                            visible.forEachIndexed { idx, id ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = titleFor(id),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = c.textPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    ActionIcon(Icons.Filled.ArrowUpward) { move(id, -1) }
                                    ActionIcon(Icons.Filled.ArrowDownward) { move(id, +1) }
                                    ActionIcon(Icons.Filled.VisibilityOff) { hide(id) }
                                }
                                if (idx != visible.lastIndex) DividerLine()
                            }

                            val hiddenList = sectionOrder.filter { it in hiddenSections }
                            if (hiddenList.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.home_layout_hidden),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = c.textSecondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                hiddenList.forEachIndexed { idx, id ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) { unhide(id) }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = titleFor(id),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = c.textPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Icon(
                                            imageVector = Icons.Filled.Visibility,
                                            contentDescription = null,
                                            tint = c.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    if (idx != hiddenList.lastIndex) DividerLine()
                                }
                            }
                        }
                    }

                    if ("grid" !in hiddenSections) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedRectangle(18.dp))
                                .background(c.cardAlt.copy(alpha = if (isDark) 0.58f else 0.72f))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.home_layout_metrics_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = c.textPrimary
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                val visibleMetrics = metricOrder.filter { it !in hiddenMetrics }
                                visibleMetrics.forEachIndexed { idx, id ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = metricTitleFor(id),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = c.textPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        ActionIcon(Icons.Filled.ArrowUpward) { moveMetric(id, -1) }
                                        ActionIcon(Icons.Filled.ArrowDownward) { moveMetric(id, +1) }
                                        ActionIcon(Icons.Filled.VisibilityOff) { hideMetric(id) }
                                    }
                                    if (idx != visibleMetrics.lastIndex) DividerLine()
                                }

                                val hiddenMetricList = metricOrder.filter { it in hiddenMetrics }
                                if (hiddenMetricList.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.home_layout_hidden_metrics_title),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = c.textSecondary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))

                                    hiddenMetricList.forEachIndexed { idx, id ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null
                                                ) { unhideMetric(id) }
                                                .padding(vertical = 10.dp),
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = metricTitleFor(id),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = c.textPrimary,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Icon(
                                                imageVector = Icons.Filled.Visibility,
                                                contentDescription = null,
                                                tint = c.textSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        if (idx != hiddenMetricList.lastIndex) DividerLine()
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().coerceAtMost(12.dp)))
                }
            }
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
    val shouldShowDialog = env.checked &&
        dialogKey.isNotBlank() &&
        dialogKey == rawDialogKey &&
        dialogKey != lastDialogKey

    if (shouldShowDialog) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = c.card,
            titleContentColor = c.textPrimary,
            textContentColor = c.textSecondary,
            confirmButton = {
                val accent = appAccentColor()

                when (dialogKey) {
                    "module" -> {
                        // For module dialog, we need multiple buttons in a row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    lastDialogKey = dialogKey
                                    dialogPrefs.edit().putBoolean("hide_module_dialog", true).apply()
                                }
                            ) {
                                Text(text = stringResource(R.string.home_env_btn_skip), color = c.textSecondary)
                            }
                            TextButton(
                                onClick = {
                                    lastDialogKey = dialogKey
                                    (context as? Activity)?.finish()
                                }
                            ) {
                                Text(text = stringResource(R.string.home_env_btn_exit), color = c.textSecondary)
                            }
                            TextButton(
                                onClick = {
                                    lastDialogKey = dialogKey
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(moduleLink)))
                                    } catch (_: Exception) {
                                    } finally {
                                        (context as? Activity)?.finish()
                                    }
                                }
                            ) {
                                Text(text = stringResource(R.string.home_env_btn_install), color = accent)
                            }
                        }
                    }
                    "scripts" -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(
                                onClick = {
                                    lastDialogKey = dialogKey
                                    dialogPrefs.edit().putBoolean("hide_scripts_dialog", true).apply()
                                }
                            ) {
                                Text(text = stringResource(R.string.home_env_btn_skip), color = c.textSecondary)
                            }
                            TextButton(
                                onClick = {
                                    lastDialogKey = dialogKey
                                    (context as? Activity)?.finish()
                                }
                            ) {
                                Text(text = stringResource(R.string.home_env_btn_exit), color = c.textSecondary)
                            }
                        }
                    }
                    else -> {
                        TextButton(
                            onClick = {
                                lastDialogKey = dialogKey
                                (context as? Activity)?.finish()
                            }
                        ) {
                            Text(text = stringResource(R.string.home_env_btn_ok), color = accent)
                        }
                    }
                }
            },
            dismissButton = null,
            title = {
                Text(
                    text = when (dialogKey) {
                        "root" -> stringResource(R.string.home_env_title_root_required)
                        "module" -> stringResource(R.string.home_env_title_module_missing)
                        else -> stringResource(R.string.home_env_title_environment_missing)
                    }
                )
            },
            text = {
                Text(
                    text = when (dialogKey) {
                        "root" -> stringResource(R.string.home_env_body_root_required)
                        "module" -> stringResource(R.string.home_env_body_module_missing)
                        else -> stringResource(R.string.home_env_body_scripts_missing)
                    },
                    color = c.textSecondary
                )
            }
        )
    }

    // Initialize repository

    LaunchedEffect(Unit) {
        fun parseProxyTrafficFilter(text: String): List<String> {
            return text
                .split(',', '\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }

        val defaultProxyTrafficFilter = "DIRECT,REJECT"
        val filterText = subPrefs.getString("proxy_traffic_filter_chains", defaultProxyTrafficFilter) ?: defaultProxyTrafficFilter
        BoxApi.setProxyTrafficFilterChains(parseProxyTrafficFilter(filterText))

        HomeRepository.setUseClashApiForSubscription(subPrefs.getBoolean("use_clash_api", false))
        HomeRepository.setUseClashApiForNetSpeed(subPrefs.getBoolean("use_clash_api_net_speed", false))
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

    // Navigation visibility handling

    LaunchedEffect(listState) {
        var last = listState.firstVisibleItemIndex * 10_000 + listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex * 10_000 + listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { now ->
                if (now > last) {
                    onNavVisibilityChange(false)
                } else if (now < last) {
                    onNavVisibilityChange(true)
                }
                last = now
            }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(c.pageBg),
        contentPadding = contentPaddingWithNavBars(
            start = pagePadding,
            end = pagePadding,
            top = 0.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            HomeHeader(
                onEdit = { showHomeLayoutSheet = true }
            )
        }

        sectionOrder.filter { it !in hiddenSections }.forEach { id ->
            when (id) {
                "hero" -> item(key = "home_hero") {
                    HomeHeroCard(
                        serviceState = serviceState,
                        onStart = { HomeRepository.startService() },
                        onStop = { HomeRepository.stopService() },
                        onReload = { HomeRepository.restartService() }
                    )
                }
                "quick" -> item(key = "home_quick") {
                    HomeQuickActions(
                        showSubStore = showSubStoreEntry,
                        onOpenPanel = onOpenPanel,
                        onOpenSubStore = onOpenSubStore,
                        onOpenLogs = onOpenLogs
                    )
                }
                "latency" -> item(key = "home_latency") {
                    LatencyWideCard(
                        label1 = latencyTargets.getOrNull(0)?.name ?: stringResource(R.string.home_latency_baidu),
                        baidu = metricsState.latencyBaiduMs,
                        label2 = latencyTargets.getOrNull(1)?.name ?: stringResource(R.string.home_latency_cloudflare),
                        cloudflare = metricsState.latencyCloudflareMs,
                        label3 = latencyTargets.getOrNull(2)?.name ?: stringResource(R.string.home_latency_google),
                        google = metricsState.latencyGoogleMs,
                        loading = metricsState.latencyLoading,
                        onRefresh = { HomeRepository.refreshLatencyNow() }
                    )
                }
                "grid" -> item(key = "home_grid") {
                    val metricModels = metricOrder
                        .filter { it !in hiddenMetrics }
                        .mapNotNull { mid ->
                            when (mid) {
                                "ip" -> HomeCardModel(
                                    title = stringResource(R.string.home_card_ip),
                                    value = metricsState.ip,
                                    subtitle = if (metricsState.ipMode == IpMode.LAN) {
                                        stringResource(
                                            R.string.home_ip_subtitle_interface,
                                            metricsState.lanInterface.takeIf { it.isNotBlank() && it != "-" } ?: "-"
                                        )
                                    } else {
                                        stringResource(
                                            R.string.home_ip_subtitle_region,
                                            metricsState.publicCountry.takeIf { it.isNotBlank() && it != "-" }
                                                ?: metricsState.publicCountryCode.takeIf { it.isNotBlank() }
                                                ?: "-"
                                        )
                                    },
                                    kind = HomeMetricKind.Ip,
                                    accent = Color(0xFF2DA44E),
                                    badgeText = if (metricsState.ipMode.name == "LAN") {
                                        stringResource(R.string.home_ip_badge_lan)
                                    } else {
                                        val cc = metricsState.publicCountryCode.trim()
                                        val flag = countryCodeToFlagEmoji(cc)
                                        if (flag.isBlank()) stringResource(R.string.home_ip_badge_pub) else stringResource(R.string.home_ip_badge_pub_with_flag, flag)
                                    },
                                    cornerActionIcon = if (metricsState.ipMode == IpMode.PUBLIC) Icons.AutoMirrored.Filled.KeyboardArrowRight else null,
                                    onCornerAction = if (metricsState.ipMode == IpMode.PUBLIC) {
                                        { showIpGeoDialog = true }
                                    } else {
                                        null
                                    },
                                    onClick = { HomeRepository.toggleIpMode() }
                                )
                                "net_speed" -> HomeCardModel(
                                    title = stringResource(R.string.home_card_net_speed),
                                    value = metricsState.netDown,
                                    subtitle = if (metricsState.netUp == "-") stringResource(R.string.home_net_up_unknown) else stringResource(R.string.home_net_up_value, metricsState.netUp),
                                    kind = HomeMetricKind.Speed,
                                    accent = Color(0xFFF97316),
                                    sparkDown = metricsState.netDownHistory,
                                    sparkUp = metricsState.netUpHistory,
                                    onClick = { showNetSpeedDialog = true }
                                )
                                "subscription" -> HomeCardModel(
                                    title = stringResource(R.string.home_card_subscription),
                                    value = if (metricsState.subscriptionTotalBytes > 0L) {
                                        HomeMetricsApi.formatBytes(metricsState.subscriptionUsedBytes)
                                    } else {
                                        "-"
                                    },
                                    subtitle = if (metricsState.subscriptionTotalBytes > 0L) {
                                        HomeMetricsApi.formatBytes(metricsState.subscriptionTotalBytes)
                                    } else {
                                        "-"
                                    },
                                    kind = HomeMetricKind.Subscription,
                                    accent = Color(0xFF8B5CF6),
                                    progress = metricsState.subscriptionProgress,
                                    onClick = {
                                        showSubsDialog = true
                                        if (isSubscriptionClashApiEnabled) {
                                            HomeRepository.refreshSubscriptionNow()
                                        } else {
                                            HomeRepository.refreshSubscriptionIfUrlsChanged()
                                        }
                                    }
                                )
                                "system" -> HomeCardModel(
                                    title = stringResource(R.string.home_card_system),
                                    value = metricsState.cpu,
                                    subtitle = metricsState.ram,
                                    kind = HomeMetricKind.System,
                                    accent = Color(0xFF64748B),
                                    isActive = isRunning,
                                    onClick = { showSystemDialog = true }
                                )
                                else -> null
                            }
                        }

                    HomeTwoColumnGrid(models = metricModels)
                }
            }
        }
        item { Spacer(modifier = Modifier.height(12.dp)) }
    }
}

@Composable
private fun IpGeoBottomSheet(
    loading: Boolean,
    ipv4: HomeMetricsApi.PublicGeoIpInfo?,
    ipv6: HomeMetricsApi.PublicGeoIpInfo?
) {
    val c = appColors()
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 6.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.Center)
                    .clearAndSetSemantics { }
                    .size(width = 28.dp, height = 3.dp)
                    .clip(Capsule())
                    .background(c.divider.copy(alpha = 0.42f))
            )
        }

        Text(
            text = stringResource(R.string.home_ip_geo_sheet_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = c.textPrimary
        )
        Text(
            text = stringResource(R.string.home_ip_geo_sheet_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = c.textSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        IpGeoSectionCard(
            title = stringResource(R.string.home_ip_geo_ipv4_title),
            loading = loading,
            info = ipv4
        )
        Spacer(modifier = Modifier.height(10.dp))
        IpGeoSectionCard(
            title = stringResource(R.string.home_ip_geo_ipv6_title),
            loading = loading,
            info = ipv6
        )

        Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
    }
}

@Composable
private fun IpGeoSectionCard(
    title: String,
    loading: Boolean,
    info: HomeMetricsApi.PublicGeoIpInfo?
) {
    val c = appColors()
    val isDark = ThemeManager.shouldUseDarkTheme()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedRectangle(16.dp))
            .background(c.cardAlt.copy(alpha = if (isDark) 0.58f else 0.72f))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary
            )

            if (loading) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = c.textSecondary
                    )
                    Text(
                        text = stringResource(R.string.home_ip_geo_loading),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary
                    )
                }
            } else if (info == null) {
                Text(
                    text = stringResource(R.string.home_ip_geo_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary
                )
            } else {
                val flag = countryCodeToFlagEmoji(info.countryCode)
                val locationName = info.country.takeIf { it.isNotBlank() && it != "-" } ?: info.countryCode
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
                IpGeoFieldRow(stringResource(R.string.home_ip_geo_field_ip), info.ip)
                IpGeoFieldRow(stringResource(R.string.home_ip_geo_field_location), locationValue)
                IpGeoFieldRow(stringResource(R.string.home_ip_geo_field_isp), info.isp)
                IpGeoFieldRow(stringResource(R.string.home_ip_geo_field_asn), asnValue)
            }
        }
    }
}

@Composable
private fun IpGeoFieldRow(label: String, value: String) {
    val c = appColors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 170.dp)
        )
        Text(
            text = value.ifBlank { "-" },
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
                .wrapContentWidth(androidx.compose.ui.Alignment.End)
        )
    }
}
