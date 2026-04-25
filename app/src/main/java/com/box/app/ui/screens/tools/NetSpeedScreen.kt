package com.box.app.ui.screens.tools

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.withFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.box.app.data.backend.BoxApi
import com.box.app.data.backend.HomeMetricsApi
import com.box.app.data.repo.HomeRepository
import com.box.app.ui.components.contentPaddingWithNavBars
import com.box.app.ui.miuix.HyperBottomSheet
import com.box.app.ui.miuix.HyperFilterChip
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class ConnFilter { Active, Closed }
private enum class ConnSort { Traffic, Time, Host }

@Composable
fun NetSpeedScreen(onBack: () -> Unit) {
    val metrics by HomeRepository.metricsState.collectAsState()
    val scope = rememberCoroutineScope()

    val isDark = isSystemInDarkTheme()
    // 下载绿色 / 上传蓝色 — 与首页语义一致
    val downColor = if (isDark) Color(0xFF66BB6A) else Color(0xFF2E7D32)
    val upColor = MiuixTheme.colorScheme.primary

    // 连接列表
    var activeConnections by remember { mutableStateOf<List<BoxApi.ConnectionInfo>>(emptyList()) }
    var closedConnections by remember { mutableStateOf<List<BoxApi.ConnectionInfo>>(emptyList()) }
    var filter by remember { mutableStateOf(ConnFilter.Active) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(ConnSort.Traffic) }
    // API 返回的全局累计流量（含已关闭连接）
    var globalDownloadTotal by remember { mutableStateOf(0L) }
    var globalUploadTotal by remember { mutableStateOf(0L) }
    var processMemory by remember { mutableStateOf(0L) }
    // 连接详情 BottomSheet
    var detailConn by remember { mutableStateOf<BoxApi.ConnectionInfo?>(null) }

    // 定时刷新连接
    LaunchedEffect(Unit) {
        while (isActive) {
            val snapshot = BoxApi.getAllConnections()
            globalDownloadTotal = snapshot.downloadTotal
            globalUploadTotal = snapshot.uploadTotal
            processMemory = snapshot.memory
            val currentIds = snapshot.connections.map { it.id }.toSet()
            // 检测已关闭的连接：之前活跃但现在不在列表中的
            val prevMap = activeConnections.associateBy { it.id }
            val newlyClosed = prevMap.filterKeys { it !in currentIds }.values.map {
                it.copy(isAlive = false)
            }
            if (newlyClosed.isNotEmpty()) {
                closedConnections = newlyClosed + closedConnections
            }
            activeConnections = snapshot.connections
            delay(2000)
        }
    }

    // 搜索 + 排序
    val displayConnections = remember(activeConnections, closedConnections, filter, searchQuery, sortOrder) {
        val base = when (filter) {
            ConnFilter.Active -> activeConnections
            ConnFilter.Closed -> closedConnections
        }
        val filtered = if (searchQuery.isBlank()) base else base.filter {
            it.host.contains(searchQuery, ignoreCase = true) ||
                    it.destinationIP.contains(searchQuery, ignoreCase = true) ||
                    it.process.contains(searchQuery, ignoreCase = true)
        }
        when (sortOrder) {
            ConnSort.Traffic -> filtered.sortedByDescending { it.download + it.upload }
            ConnSort.Time -> filtered.sortedByDescending { it.start }
            ConnSort.Host -> filtered.sortedBy { (it.host.ifBlank { it.destinationIP }).lowercase() }
        }
    }

    val activeCount = activeConnections.size
    val closedCount = closedConnections.size

    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.bottomsheet_net_speed_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    if (metrics.useClashApiForNetSpeed && activeConnections.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                // 将所有活跃连接移入已关闭列表
                                closedConnections = activeConnections.map { it.copy(isAlive = false) } + closedConnections
                                activeConnections = emptyList()
                                BoxApi.closeAllConnections()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(R.string.net_speed_close_all),
                                tint = MiuixTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = contentPaddingWithNavBars(
                start = 12.dp, end = 12.dp,
                top = innerPadding.calculateTopPadding(),
                extraBottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 速度卡片
            item(key = "speed_stats") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SpeedStatCard(
                        icon = Icons.Filled.ArrowDownward,
                        label = stringResource(R.string.bottomsheet_net_speed_down),
                        value = metrics.netDown,
                        tint = downColor,
                        modifier = Modifier.weight(1f)
                    )
                    SpeedStatCard(
                        icon = Icons.Filled.ArrowUpward,
                        label = stringResource(R.string.bottomsheet_net_speed_up),
                        value = metrics.netUp,
                        tint = upColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 图表
            item(key = "chart") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 18.dp,
                    insideMargin = PaddingValues(16.dp),
                    colors = CardDefaults.defaultColors()
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ChartLegendItem(downColor, stringResource(R.string.bottomsheet_net_speed_down), Icons.Filled.ArrowDownward)
                            ChartLegendItem(upColor, stringResource(R.string.bottomsheet_net_speed_up), Icons.Filled.ArrowUpward)
                        }
                        AnimatedSpeedChart(
                            downSeries = metrics.netDownHistory,
                            upSeries = metrics.netUpHistory,
                            downColor = downColor,
                            upColor = upColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                        )
                    }
                }
            }

            // 总流量
            if (metrics.useClashApiForNetSpeed) {
                item(key = "total_traffic") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp,
                        insideMargin = PaddingValues(14.dp),
                        colors = CardDefaults.defaultColors()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.net_speed_total_traffic),
                                style = MiuixTheme.textStyles.body1,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDownward,
                                        contentDescription = null,
                                        tint = downColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = HomeMetricsApi.formatBytes(globalDownloadTotal),
                                        style = MiuixTheme.textStyles.body2,
                                        color = downColor
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowUpward,
                                        contentDescription = null,
                                        tint = upColor,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = HomeMetricsApi.formatBytes(globalUploadTotal),
                                        style = MiuixTheme.textStyles.body2,
                                        color = upColor
                                    )
                                }
                            }
                        }
                    }
                }

                // 最快连接
                if (metrics.netFastestDownSpeed != "-" || metrics.netFastestUpSpeed != "-") {
                    item(key = "fastest_connections") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Card(
                                modifier = Modifier.weight(1f),
                                cornerRadius = 16.dp,
                                insideMargin = PaddingValues(12.dp),
                                colors = CardDefaults.defaultColors()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = stringResource(R.string.net_speed_fastest_down),
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = downColor,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = metrics.netFastestDownSpeed,
                                        style = MiuixTheme.textStyles.body1,
                                        fontWeight = FontWeight.SemiBold,
                                        color = downColor
                                    )
                                    Text(
                                        text = metrics.netFastestDownHost,
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Card(
                                modifier = Modifier.weight(1f),
                                cornerRadius = 16.dp,
                                insideMargin = PaddingValues(12.dp),
                                colors = CardDefaults.defaultColors()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = stringResource(R.string.net_speed_fastest_up),
                                        style = MiuixTheme.textStyles.footnote1,
                                        color = upColor,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = metrics.netFastestUpSpeed,
                                        style = MiuixTheme.textStyles.body1,
                                        fontWeight = FontWeight.SemiBold,
                                        color = upColor
                                    )
                                    Text(
                                        text = metrics.netFastestUpHost,
                                        style = MiuixTheme.textStyles.footnote2,
                                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // 搜索栏
                item(key = "search") {
                    var searchExpanded by remember { mutableStateOf(false) }
                    SearchBar(
                        modifier = Modifier.fillMaxWidth(),
                        inputField = {
                            InputField(
                                query = searchQuery,
                                onQueryChange = { searchQuery = it },
                                onSearch = { searchExpanded = false },
                                expanded = searchExpanded,
                                onExpandedChange = { searchExpanded = it },
                                label = stringResource(R.string.net_speed_search_hint)
                            )
                        },
                        expanded = searchExpanded,
                        onExpandedChange = { searchExpanded = it }
                    ) {
                        // 搜索建议：匹配的主机名
                        if (searchQuery.isNotBlank()) {
                            val allHosts = activeConnections
                                .map { conn -> conn.host.ifBlank { conn.destinationIP } }
                                .distinct()
                                .filter { h -> h.contains(searchQuery, ignoreCase = true) }
                                .take(5)
                            Column {
                                allHosts.forEach { host ->
                                    Text(
                                        text = host,
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                searchQuery = host
                                                searchExpanded = false
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // 排序选项
                item(key = "sort_options") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HyperFilterChip(
                            selected = sortOrder == ConnSort.Traffic,
                            onClick = { sortOrder = ConnSort.Traffic },
                            label = stringResource(R.string.net_speed_sort_traffic)
                        )
                        HyperFilterChip(
                            selected = sortOrder == ConnSort.Time,
                            onClick = { sortOrder = ConnSort.Time },
                            label = stringResource(R.string.net_speed_sort_time)
                        )
                        HyperFilterChip(
                            selected = sortOrder == ConnSort.Host,
                            onClick = { sortOrder = ConnSort.Host },
                            label = stringResource(R.string.net_speed_sort_host)
                        )
                    }
                }

                // 连接筛选标签
                item(key = "conn_filter") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HyperFilterChip(
                            selected = filter == ConnFilter.Active,
                            onClick = { filter = ConnFilter.Active },
                            label = "${stringResource(R.string.net_speed_active)} ($activeCount)"
                        )
                        HyperFilterChip(
                            selected = filter == ConnFilter.Closed,
                            onClick = { filter = ConnFilter.Closed },
                            label = "${stringResource(R.string.net_speed_closed)} ($closedCount)"
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = stringResource(R.string.net_speed_connections, activeCount),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                }

                if (filter == ConnFilter.Active) {
                    if (displayConnections.isEmpty()) {
                        item(key = "conn_empty") {
                            Text(
                                text = stringResource(R.string.net_speed_no_active_connections),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                modifier = Modifier.padding(vertical = 20.dp)
                            )
                        }
                    } else {
                        items(
                            items = displayConnections,
                            key = { it.id }
                        ) { conn ->
                            ConnectionCard(
                                conn = conn,
                                downColor = downColor,
                                upColor = upColor,
                                onClick = { detailConn = conn },
                                onClose = {
                                    scope.launch {
                                        // 移入已关闭列表
                                        closedConnections = listOf(conn.copy(isAlive = false)) + closedConnections
                                        activeConnections = activeConnections.filter { it.id != conn.id }
                                        BoxApi.closeConnection(conn.id)
                                    }
                                }
                            )
                        }
                    }
                } else {
                    if (displayConnections.isEmpty()) {
                        item(key = "closed_empty") {
                            Text(
                                text = stringResource(R.string.net_speed_no_closed_connections),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                modifier = Modifier.padding(vertical = 20.dp)
                            )
                        }
                    } else {
                        items(
                            items = displayConnections,
                            key = { "closed_${it.id}" }
                        ) { conn ->
                            ConnectionCard(
                                conn = conn,
                                downColor = downColor,
                                upColor = upColor,
                                onClick = { detailConn = conn },
                                onClose = null
                            )
                        }
                    }
                }
            }
        }
    }

    // 连接详情 BottomSheet
    ConnectionDetailSheet(
        conn = detailConn,
        downColor = downColor,
        upColor = upColor,
        onDismiss = { detailConn = null }
    )
}

// ═══════════════════════════════════════════════════════════════════════════
// 连接详情 BottomSheet
// ═══════════════════════════════════════════════════════════════════════════

private enum class DetailMode { Visual, Raw }

@Composable
private fun ConnectionDetailSheet(
    conn: BoxApi.ConnectionInfo?,
    downColor: Color,
    upColor: Color,
    onDismiss: () -> Unit
) {
    HyperBottomSheet(
        show = conn != null,
        onDismissRequest = onDismiss,
        title = conn?.host?.ifBlank { conn.destinationIP } ?: ""
    ) {
        val c = conn ?: return@HyperBottomSheet
        var mode by remember { mutableStateOf(DetailMode.Visual) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 状态指示
            val statusColor = if (c.isAlive) downColor else MiuixTheme.colorScheme.onSurfaceSecondary
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedRectangle(4.dp))
                        .background(statusColor)
                )
                Text(
                    text = if (c.isAlive) stringResource(R.string.net_speed_detail_status_active)
                    else stringResource(R.string.net_speed_detail_status_closed),
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.Medium,
                    color = statusColor
                )
            }

            // 模式切换
            TabRowWithContour(
                tabs = listOf(
                    stringResource(R.string.net_speed_detail_visual),
                    stringResource(R.string.net_speed_detail_raw)
                ),
                selectedTabIndex = if (mode == DetailMode.Visual) 0 else 1,
                onTabSelected = { mode = if (it == 0) DetailMode.Visual else DetailMode.Raw }
            )

            if (mode == DetailMode.Visual) {
                DetailVisualContent(c, downColor, upColor)
            } else {
                DetailRawContent(c)
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

// ── 可视化视图 ──────────────────────────────────────────────────────────────

@Composable
private fun DetailVisualContent(
    conn: BoxApi.ConnectionInfo,
    downColor: Color,
    upColor: Color
) {
    // 流量卡片
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier.weight(1f),
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = null,
                        tint = downColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(R.string.net_speed_detail_download),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
                Text(
                    text = HomeMetricsApi.formatBytes(conn.download),
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.SemiBold,
                    color = downColor
                )
            }
        }
        Card(
            modifier = Modifier.weight(1f),
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = null,
                        tint = upColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = stringResource(R.string.net_speed_detail_upload),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
                Text(
                    text = HomeMetricsApi.formatBytes(conn.upload),
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.SemiBold,
                    color = upColor
                )
            }
        }
    }

    // 代理链可视化
    if (conn.chains.isNotEmpty()) {
        SmallTitle(
            text = stringResource(R.string.net_speed_detail_chains),
            modifier = Modifier.padding(top = 4.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(16.dp)
        ) {
            ProxyChainVisual(
                chains = conn.chains.reversed(),
                host = conn.host.ifBlank { conn.destinationIP },
                accentColor = MiuixTheme.colorScheme.primary
            )
        }
    }

    // 连接详情
    SmallTitle(
        text = stringResource(R.string.bottomsheet_net_speed_details),
        modifier = Modifier.padding(top = 4.dp)
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            if (conn.host.isNotBlank()) {
                DetailInfoItem(stringResource(R.string.net_speed_detail_host), conn.host)
            }
            DetailInfoItem(
                stringResource(R.string.net_speed_detail_destination),
                "${conn.destinationIP}:${conn.destinationPort}"
            )
            if (conn.sourceIP.isNotBlank()) {
                DetailInfoItem(
                    stringResource(R.string.net_speed_detail_source),
                    "${conn.sourceIP}:${conn.sourcePort}"
                )
            }
            DetailInfoItem(stringResource(R.string.net_speed_detail_network), conn.network.uppercase())
            if (conn.type.isNotBlank()) {
                DetailInfoItem(stringResource(R.string.net_speed_detail_type), conn.type)
            }
            if (conn.rule.isNotBlank()) {
                DetailInfoItem(
                    stringResource(R.string.net_speed_detail_rule),
                    conn.rule + if (conn.rulePayload.isNotBlank()) " (${conn.rulePayload})" else ""
                )
            }
            if (conn.destinationGeoIP.isNotBlank()) {
                DetailInfoItem(stringResource(R.string.net_speed_detail_geo), conn.destinationGeoIP)
            }
            if (conn.process.isNotBlank()) {
                DetailInfoItem(stringResource(R.string.net_speed_detail_process), conn.process)
            }
            if (conn.sniffHost.isNotBlank() && conn.sniffHost != conn.host) {
                DetailInfoItem(stringResource(R.string.net_speed_detail_sniff_host), conn.sniffHost)
            }
            if (conn.start.isNotBlank()) {
                DetailInfoItem(
                    stringResource(R.string.net_speed_detail_start),
                    formatStartTime(conn.start),
                    showDivider = false
                )
            }
        }
    }
}

/**
 * 代理链可视化：竖向时间轴，圆点与节点文字垂直居中对齐。
 *
 * 布局结构（每个节点）：
 *   ┌─ 上段连线（首节点无）
 *   ● ── 节点内容（pill 样式）
 *   └─ 下段连线（末节点无）
 */
@Composable
private fun ProxyChainVisual(
    chains: List<String>,
    host: String,
    accentColor: Color
) {
    val lineColor = accentColor.copy(alpha = 0.18f)
    val dotSize = 10.dp
    val railWidth = 32.dp
    val lineWidth = 1.5.dp
    // 节点 pill 高度（用于计算连线段长）
    val nodeHeight = 36.dp
    val gapBetween = 6.dp

    Column(modifier = Modifier.fillMaxWidth()) {
        chains.forEachIndexed { index, node ->
            val isFirst = index == 0
            val isLast = index == chains.lastIndex

            // 上段连线（首节点无）
            if (!isFirst) {
                Box(
                    modifier = Modifier
                        .padding(start = (railWidth - lineWidth) / 2)
                        .width(lineWidth)
                        .height(gapBetween)
                        .background(lineColor)
                )
            }

            // 节点行：圆点 + pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 圆点列
                Box(
                    modifier = Modifier.width(railWidth),
                    contentAlignment = Alignment.Center
                ) {
                    // 外圈光晕（首尾节点）
                    if (isFirst || isLast) {
                        Box(
                            modifier = Modifier
                                .size(dotSize + 6.dp)
                                .clip(RoundedRectangle((dotSize + 6.dp) / 2))
                                .background(accentColor.copy(alpha = 0.12f))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(dotSize)
                            .clip(RoundedRectangle(dotSize / 2))
                            .background(
                                if (isFirst || isLast) accentColor
                                else accentColor.copy(alpha = 0.45f)
                            )
                    )
                }

                // 节点 pill
                val pillBg = when {
                    isFirst || isLast -> accentColor.copy(alpha = 0.10f)
                    else -> MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(nodeHeight)
                        .clip(RoundedRectangle(10.dp))
                        .background(pillBg)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = node,
                        style = MiuixTheme.textStyles.body2,
                        fontWeight = FontWeight.Medium,
                        color = if (isFirst || isLast) accentColor
                        else MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // 角色标签
                    val roleText = when {
                        isFirst -> "Entry"
                        isLast -> "Exit"
                        else -> null
                    }
                    if (roleText != null) {
                        Text(
                            text = roleText,
                            style = MiuixTheme.textStyles.footnote2,
                            color = if (isFirst || isLast) accentColor.copy(alpha = 0.6f)
                            else MiuixTheme.colorScheme.onSurfaceSecondary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }

        // 目标主机行
        Box(
            modifier = Modifier
                .padding(start = (railWidth - lineWidth) / 2)
                .width(lineWidth)
                .height(gapBetween)
                .background(lineColor)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 箭头圆点
            Box(
                modifier = Modifier.width(railWidth),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(dotSize + 6.dp)
                        .clip(RoundedRectangle((dotSize + 6.dp) / 2))
                        .background(accentColor.copy(alpha = 0.12f))
                )
                Box(
                    modifier = Modifier
                        .size(dotSize)
                        .clip(RoundedRectangle(dotSize / 2))
                        .background(accentColor)
                )
            }
            // 目标 pill
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(nodeHeight)
                    .clip(RoundedRectangle(10.dp))
                    .background(accentColor.copy(alpha = 0.10f))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = host,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Medium,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = "Target",
                    style = MiuixTheme.textStyles.footnote2,
                    color = accentColor.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

// ── 原始数据视图 ────────────────────────────────────────────────────────────

@Composable
private fun DetailRawContent(conn: BoxApi.ConnectionInfo) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(16.dp)
    ) {
        val rawText = buildString {
            appendLine("id: ${conn.id}")
            appendLine("host: ${conn.host}")
            if (conn.sniffHost.isNotBlank()) appendLine("sniffHost: ${conn.sniffHost}")
            appendLine("destinationIP: ${conn.destinationIP}")
            appendLine("destinationPort: ${conn.destinationPort}")
            if (conn.sourceIP.isNotBlank()) appendLine("sourceIP: ${conn.sourceIP}")
            if (conn.sourcePort.isNotBlank()) appendLine("sourcePort: ${conn.sourcePort}")
            appendLine("network: ${conn.network}")
            appendLine("type: ${conn.type}")
            appendLine("rule: ${conn.rule}")
            if (conn.rulePayload.isNotBlank()) appendLine("rulePayload: ${conn.rulePayload}")
            appendLine("chains: ${conn.chains.joinToString(" \u2192 ")}")
            appendLine("download: ${conn.download} (${HomeMetricsApi.formatBytes(conn.download)})")
            appendLine("upload: ${conn.upload} (${HomeMetricsApi.formatBytes(conn.upload)})")
            appendLine("start: ${conn.start}")
            if (conn.process.isNotBlank()) appendLine("process: ${conn.process}")
            if (conn.processPath.isNotBlank()) appendLine("processPath: ${conn.processPath}")
            if (conn.destinationGeoIP.isNotBlank()) appendLine("destinationGeoIP: ${conn.destinationGeoIP}")
            append("isAlive: ${conn.isAlive}")
        }
        Text(
            text = rawText,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        )
    }
}

// ── 详情组件 ────────────────────────────────────────────────────────────────

/** 信息行：label + value，带分割线 */
@Composable
private fun DetailInfoItem(
    label: String,
    value: String,
    showDivider: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = value,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.08f))
            )
        }
    }
}

/** 格式化 ISO 8601 时间 */
private fun formatStartTime(isoTime: String): String {
    return try {
        val t = isoTime.substringBefore(".")
        t.replace("T", " ")
    } catch (_: Exception) {
        isoTime
    }
}

@Composable
private fun ConnectionCard(
    conn: BoxApi.ConnectionInfo,
    downColor: Color,
    upColor: Color,
    onClick: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 14.dp,
                    end = if (onClose != null) 8.dp else 14.dp,
                    top = 12.dp,
                    bottom = 12.dp
                )
        ) {
            // 主机 + 关闭按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = conn.host.ifBlank { conn.destinationIP },
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Medium,
                    color = if (conn.isAlive) MiuixTheme.colorScheme.onSurface
                    else MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (onClose != null) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(32.dp),
                        backgroundColor = Color.Transparent,
                        cornerRadius = 10.dp
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // 网络 + 规则 + GeoIP
            Row(
                modifier = Modifier.padding(end = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (conn.network.isNotBlank()) {
                    ConnInfoTag(conn.network.uppercase())
                }
                if (conn.type.isNotBlank()) {
                    ConnInfoTag(conn.type)
                }
                if (conn.rule.isNotBlank()) {
                    ConnInfoTag(conn.rule + if (conn.rulePayload.isNotBlank()) "(${conn.rulePayload})" else "")
                }
                if (conn.destinationGeoIP.isNotBlank()) {
                    ConnInfoTag(conn.destinationGeoIP)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 流量
            Row(
                modifier = Modifier.padding(end = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = null,
                        tint = downColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = HomeMetricsApi.formatBytes(conn.download),
                        style = MiuixTheme.textStyles.footnote1,
                        color = downColor
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = null,
                        tint = upColor,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = HomeMetricsApi.formatBytes(conn.upload),
                        style = MiuixTheme.textStyles.footnote1,
                        color = upColor
                    )
                }
            }

            // 链路
            if (conn.chains.isNotEmpty()) {
                Text(
                    text = conn.chains.reversed().joinToString(" \u2192 "),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp, end = 6.dp)
                )
            }

            // 进程信息
            if (conn.process.isNotBlank()) {
                Text(
                    text = conn.process,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp, end = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun ConnInfoTag(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedRectangle(6.dp))
            .background(MiuixTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSecondaryContainer,
            maxLines = 1
        )
    }
}

@Composable
private fun SpeedStatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedRectangle(10.dp))
                        .background(tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
                }
                Text(
                    text = label,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
            Text(
                text = value,
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChartLegendItem(
    color: Color,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedRectangle(6.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(11.dp)
            )
        }
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            fontWeight = FontWeight.Medium,
            color = color
        )
    }
}

/**
 * 无缝滚动速度图表（优化版）。
 *
 * 设计要点：
 * 1. 帧驱动：withFrameMillis 持续渲染，基于帧时间戳计算水平偏移
 * 2. 无缝推入：新数据到来时 array shift + reset timer，shift 前后偏移量数学连续
 * 3. Y 轴缩放：向上 tween 平滑过渡，向下即时 snap 防止曲线溢出
 * 4. Tip 动画：最新点从旧值 spring 过渡到新值，保证末端平滑
 * 5. 对象复用：Path/PathEffect 缓存，避免每帧分配
 */
@Composable
private fun AnimatedSpeedChart(
    downSeries: List<Float>,
    upSeries: List<Float>,
    downColor: Color,
    upColor: Color,
    modifier: Modifier = Modifier
) {
    val slots = 30
    val dataIntervalMs = 2000f

    // ── 状态 ──
    val downBuf = remember { FloatArray(slots) }
    val upBuf = remember { FloatArray(slots) }
    var bufLen by remember { mutableIntStateOf(0) }
    var lastPushNanos by remember { mutableStateOf(0L) }
    var pushCount by remember { mutableIntStateOf(0) }  // 单调递增，可靠触发 LaunchedEffect
    var initialized by remember { mutableStateOf(false) }

    // Y 轴最大值动画
    val animMaxV = remember { Animatable(1f) }
    // 最新数据点高度动画
    val tipDown = remember { Animatable(0f) }
    val tipUp = remember { Animatable(0f) }

    // 帧时钟（纳秒），withFrameMillis 直接提供帧时间戳
    var frameNanos by remember { mutableStateOf(System.nanoTime()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis { frameNanos = System.nanoTime() }
        }
    }

    // ── Buffer 最大值扫描（零分配） ──
    fun bufMax(buf: FloatArray, len: Int): Float {
        var m = 0f
        for (i in 0 until len) { if (buf[i] > m) m = buf[i] }
        return m
    }

    // ── 数据推入（用 pushCount 作为可靠 key） ──
    val dSize = downSeries.size
    val uSize = upSeries.size
    LaunchedEffect(dSize, uSize) {
        if (dSize == 0 && uSize == 0) return@LaunchedEffect

        if (!initialized) {
            // 首次：填充整个 buffer
            val dPad = if (dSize >= slots) downSeries.takeLast(slots)
            else List(slots - dSize) { 0f } + downSeries
            val uPad = if (uSize >= slots) upSeries.takeLast(slots)
            else List(slots - uSize) { 0f } + upSeries
            for (i in 0 until slots) {
                downBuf[i] = dPad[i]
                upBuf[i] = uPad[i]
            }
            bufLen = slots
            val maxV = maxOf(bufMax(downBuf, slots), bufMax(upBuf, slots)).coerceAtLeast(1f)
            animMaxV.snapTo(maxV)
            tipDown.snapTo(downBuf[slots - 1])
            tipUp.snapTo(upBuf[slots - 1])
            initialized = true
            lastPushNanos = System.nanoTime()
            pushCount++
            return@LaunchedEffect
        }

        val newD = downSeries.lastOrNull() ?: 0f
        val newU = upSeries.lastOrNull() ?: 0f

        // 左移一格，末位填入当前 tip 动画值确保视觉连续
        for (i in 0 until bufLen - 1) {
            downBuf[i] = downBuf[i + 1]
            upBuf[i] = upBuf[i + 1]
        }
        downBuf[bufLen - 1] = tipDown.value
        upBuf[bufLen - 1] = tipUp.value

        lastPushNanos = System.nanoTime()
        pushCount++

        // Tip 弹簧动画过渡到新值
        launch { tipDown.animateTo(newD, spring(dampingRatio = 0.75f, stiffness = 200f)) }
        launch { tipUp.animateTo(newU, spring(dampingRatio = 0.75f, stiffness = 200f)) }

        // Y 轴最大值：加 20% headroom 防止曲线贴顶
        val rawMax = maxOf(bufMax(downBuf, bufLen), bufMax(upBuf, bufLen), newD, newU)
        val targetMax = (rawMax * 1.2f).coerceAtLeast(1f)
        if (targetMax > animMaxV.value) {
            // 上升：tween 平滑过渡
            launch { animMaxV.animateTo(targetMax, androidx.compose.animation.core.tween(500)) }
        } else if (targetMax < animMaxV.value * 0.6f) {
            // 明显下降（低于当前 60%）：快速收缩，防止曲线缩成一条线
            launch { animMaxV.animateTo(targetMax, androidx.compose.animation.core.tween(300)) }
        }
    }

    // ── 预计算颜色 / 样式 ──
    val downGrad = remember(downColor) {
        listOf(downColor.copy(alpha = 0.22f), downColor.copy(alpha = 0f))
    }
    val upGrad = remember(upColor) {
        listOf(upColor.copy(alpha = 0.22f), upColor.copy(alpha = 0f))
    }
    val gridColor = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.10f)
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(4f, 4f)) }
    val lineStroke = remember { Stroke(2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round) }

    // 复用 Path 对象
    val pathLine = remember { Path() }
    val pathFill = remember { Path() }

    // 读取动画值以触发重组
    val maxV = animMaxV.value
    val curNanos = frameNanos
    val tipDVal = tipDown.value
    val tipUVal = tipUp.value

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padTop = 4f
        val padBottom = 2f
        val chartH = h - padTop - padBottom
        val step = w / (slots - 1).toFloat()
        val n = bufLen
        if (n < 2) return@Canvas

        // 基于帧时间计算平移
        val elapsedMs = (curNanos - lastPushNanos) / 1_000_000f
        val t = (elapsedMs / dataIntervalMs).coerceIn(0f, 1f)
        val shiftPx = t * step

        // ── 网格 ──
        for (row in 1..3) {
            val gy = padTop + chartH * (row / 4f)
            drawLine(gridColor, Offset(0f, gy), Offset(w, gy), 0.8f, pathEffect = dashEffect)
        }

        // ── 坐标映射 ──
        fun ptX(i: Int): Float = i * step - shiftPx
        fun ptY(raw: Float): Float {
            val v = (raw / maxV).coerceIn(0f, 1f)
            return padTop + chartH * (1f - v)
        }
        fun rawAt(buf: FloatArray, tipVal: Float, i: Int): Float =
            if (i == n - 1) tipVal else buf[i]

        // ── 构建曲线 ──
        fun buildLine(buf: FloatArray, tipVal: Float, dst: Path) {
            dst.reset()
            val x0 = ptX(0); val y0 = ptY(rawAt(buf, tipVal, 0))
            dst.moveTo(x0, y0)
            var px = x0; var py = y0
            for (i in 1 until n) {
                val cx = ptX(i); val cy = ptY(rawAt(buf, tipVal, i))
                val mx = (px + cx) * 0.5f
                dst.cubicTo(mx, py, mx, cy, cx, cy)
                px = cx; py = cy
            }
        }

        fun buildFillFrom(linePath: Path, buf: FloatArray, tipVal: Float, dst: Path) {
            dst.reset()
            dst.addPath(linePath)
            val lastX = ptX(n - 1); val firstX = ptX(0)
            dst.lineTo(lastX, h)
            dst.lineTo(firstX, h)
            dst.close()
        }

        clipRect(0f, 0f, w, h) {
            // 下载：填充 → 线条
            buildLine(downBuf, tipDVal, pathLine)
            buildFillFrom(pathLine, downBuf, tipDVal, pathFill)
            drawPath(pathFill, Brush.verticalGradient(downGrad, padTop, h))
            drawPath(pathLine, downColor, style = lineStroke)

            // 上传：填充 → 线条
            buildLine(upBuf, tipUVal, pathLine)
            buildFillFrom(pathLine, upBuf, tipUVal, pathFill)
            drawPath(pathFill, Brush.verticalGradient(upGrad, padTop, h))
            drawPath(pathLine, upColor, style = lineStroke)

            // ── 最新数据点标记 ──
            val lastX = ptX(n - 1)
            if (lastX in 0f..w) {
                val dotDY = ptY(tipDVal)
                val dotUY = ptY(tipUVal)
                if (tipDVal / maxV > 0.005f) {
                    drawCircle(downColor.copy(alpha = 0.20f), 7f, Offset(lastX, dotDY))
                    drawCircle(downColor, 4f, Offset(lastX, dotDY))
                    drawCircle(Color.White, 1.5f, Offset(lastX, dotDY))
                }
                if (tipUVal / maxV > 0.005f) {
                    drawCircle(upColor.copy(alpha = 0.20f), 7f, Offset(lastX, dotUY))
                    drawCircle(upColor, 4f, Offset(lastX, dotUY))
                    drawCircle(Color.White, 1.5f, Offset(lastX, dotUY))
                }
            }
        }
    }
}
