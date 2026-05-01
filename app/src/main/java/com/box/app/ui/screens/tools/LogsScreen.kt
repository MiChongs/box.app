package com.box.app.ui.screens.tools

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.shapes.SmoothRoundedCornerShape
import com.box.app.R
import com.box.app.data.backend.LogsRootShell
import com.box.app.ui.components.ErrorToast
import com.box.app.ui.components.contentPaddingWithNavBars
import com.box.app.ui.components.home.HomeSemanticColors
import com.box.app.ui.components.home.homeDangerColors
import com.box.app.ui.components.home.homeInfoColors
import com.box.app.ui.components.home.homeNeutralColors
import com.box.app.ui.components.home.homeSuccessColors
import com.box.app.ui.components.home.homeWarningColors
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.box.app.ui.miuix.HyperDialog
import com.box.app.ui.miuix.HyperFilterChip

import com.box.app.utils.MapleFontManager
import com.box.app.utils.ThemeManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup

private enum class LogLevel {
    Error,
    Warn,
    Info,
    Debug,
    Other
}

private data class LogModuleEntry(
    val index: Int,
    // 稳定 LazyColumn key：基于内容去重，避免日志滚动时所有卡片被强制重组
    val key: String,
    val rawLine: String,
    // 预清洗正文，避免每张卡首次合成时在主线程跑 6 条正则
    val cleanMessage: String,
    val level: LogLevel,
    val tag: String,
    val timestamp: String?
)


@Composable
fun ToolsLogsScreen(
    onNavVisibilityChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val scrollBehavior = MiuixScrollBehavior()
    val prefs = remember { context.getSharedPreferences("tools_logs", Context.MODE_PRIVATE) }

    val runDir = "/data/adb/box/run"
    val statusAvailableText = stringResource(R.string.tools_logs_status_available)
    val failedLoadText = stringResource(R.string.tools_logs_failed_load)
    val failedReadText = stringResource(R.string.tools_logs_failed_read)
    val failedRefreshText = stringResource(R.string.tools_logs_failed_refresh)
    val failedDeleteText = stringResource(R.string.tools_logs_failed_delete)
    val currentStatusText = stringResource(R.string.tools_logs_status_current)

    var autoRefresh by rememberSaveable { mutableStateOf(true) }
    var currentLogFile by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var showFilePopup by rememberSaveable { mutableStateOf(false) }
    var selectedTabIndex by rememberSaveable { mutableStateOf(0) }
    var prefsLoaded by remember { mutableStateOf(false) }
    var logFiles by rememberSaveable { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var logContent by rememberSaveable { mutableStateOf("") }
    var newestFirst by rememberSaveable { mutableStateOf(true) }
    var levelFilter by rememberSaveable { mutableStateOf<LogLevel?>(null) }

    val mapleFontEnabled by ThemeManager.mapleFontLogs.collectAsState()
    val mapleFamily = MapleFontManager.getFontFamily()
    val logFontFamily = if (mapleFontEnabled && mapleFamily != null) {
        mapleFamily
    } else {
        FontFamily.Monospace
    }

    // 语义色调色板在屏幕级采集一次，供所有 ModuleLogCard / 详细视图共用
    val logPaletteOf = rememberLogLevelPaletteProvider()

    // 三条重活并行（produceState 各自独占协程，Dispatchers.Default 多核并行）：
    //   1. parseModuleEntries: 预清洗 + 解析 + 去重 key
    //   2. filteredLogContent: 详细视图的过滤/反转/join
    //   3. rememberHighlightedLog: 正则 + AnnotatedString 构建（见下方组件）
    val moduleEntriesRaw by produceState<List<LogModuleEntry>>(emptyList(), logContent, currentLogFile) {
        value = withContext(Dispatchers.Default) {
            parseModuleEntries(logContent, currentLogFile)
        }
    }
    // filter + reverse 也移到协程，600 条时能节省几 ms 主线程时间
    val moduleEntries by produceState<List<LogModuleEntry>>(
        emptyList(), moduleEntriesRaw, newestFirst, levelFilter
    ) {
        value = withContext(Dispatchers.Default) {
            var list: List<LogModuleEntry> = moduleEntriesRaw
            if (levelFilter != null) list = list.filter { it.level == levelFilter }
            if (newestFirst) list.asReversed() else list
        }
    }
    // 详细日志视图的过滤内容（后台处理）
    val filteredLogContent by produceState("", logContent, levelFilter, newestFirst) {
        value = withContext(Dispatchers.Default) {
            var lines = logContent.lines().filter { it.isNotBlank() }
            if (levelFilter != null) lines = lines.filter { detectLogLevel(it) == levelFilter }
            if (newestFirst) lines = lines.reversed()
            lines.joinToString("\n")
        }
    }
    val dropdownItems = logFiles.map { (fileName, _) -> fileName }
    val selectedLogIndex = logFiles.indexOfFirst { it.first == currentLogFile }.let { index ->
        if (index >= 0) index else 0
    }

    ErrorToast(
        message = error,
        onConsumed = { error = null }
    )

    /**
     * 查询日志文件列表
     * @return 成功时返回文件名；shell 失败返回 null（调用方应跳过清空以免模块重启期间清空 UI）
     */
    suspend fun queryLogFiles(): List<String>? {
        val cmd = "for f in $runDir/*; do [ -f \"\$f\" ] || continue; b=\$(basename \"\$f\"); case \"\$b\" in *.log|*.LOG) echo \"\$b\";; esac; done"
        // 日志读走独立 shell：与全局 cached shell 完全隔离，避免被服务/配置写命令串行阻塞
        val result = LogsRootShell.execute(cmd)
        // shell 失败（进程被 root 管理器 kill、超时、模块重启瞬间的 IPC 问题等）→ null，保留旧列表
        if (result.exitCode != 0) return null
        return result.stdout
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()
    }

    /**
     * 查询日志内容
     * @return 成功返回文件内容（可能为空）；shell 失败返回 null
     */
    suspend fun queryLogContent(fileName: String): String? {
        if (fileName.isBlank()) return ""
        val safeName = fileName.replace("\"", "").replace("'", "")
        val path = "$runDir/$safeName"
        val cmd = "tail -n 600 '$path' 2>/dev/null || true"
        val result = LogsRootShell.execute(cmd)
        // 模块重启瞬间 shell 可能 timeout / IPC 失败 → null，调用方保留旧内容
        if (result.exitCode != 0) return null
        return result.stdout
    }

    suspend fun deleteLogFile(fileName: String) {
        if (fileName.isBlank()) return
        val safeName = fileName.replace("\"", "").replace("'", "")
        val path = "$runDir/$safeName"
        LogsRootShell.execute("rm -f '$path' 2>/dev/null || true")
    }

    suspend fun refreshLogs(
        preferredFile: String? = currentLogFile,
        failureText: String,
        showLoading: Boolean
    ) {
        if (showLoading) loading = true
        error = null
        try {
            // shell 失败返回 null → 回退为「使用当前已知列表」避免模块重启瞬间清空 UI
            val names = queryLogFiles() ?: logFiles.map { it.first }
            if (names != logFiles.map { it.first }) {
                logFiles = names.map { it to statusAvailableText }
            }
            val chosen = when {
                !preferredFile.isNullOrBlank() && preferredFile in names -> preferredFile
                names.isNotEmpty() -> names.first()
                else -> currentLogFile.ifBlank { "" }
            }
            if (chosen != currentLogFile) currentLogFile = chosen
            if (chosen.isBlank()) {
                logContent = ""
            } else {
                // shell 失败时保留旧内容；显式刷新场景若从未有过内容则置空
                val fetched = queryLogContent(chosen)
                if (fetched != null) logContent = fetched
                else if (logContent.isBlank()) logContent = ""
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: failureText
        } finally {
            if (showLoading) loading = false
        }
    }

    suspend fun reloadCurrentLog(
        failureText: String,
        showLoading: Boolean,
        surfaceErrors: Boolean
    ) {
        if (currentLogFile.isBlank()) {
            logContent = ""
            return
        }
        if (showLoading) loading = true
        if (surfaceErrors) error = null
        try {
            // shell 失败 → 保留上一次成功内容，避免模块重启期间把正文清空
            val fetched = queryLogContent(currentLogFile)
            if (fetched != null) logContent = fetched
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (surfaceErrors) {
                error = e.message ?: failureText
            }
        } finally {
            if (showLoading) loading = false
        }
    }

    fun requestRefresh() {
        scope.launch {
            refreshLogs(
                preferredFile = currentLogFile,
                failureText = failedRefreshText,
                showLoading = true
            )
        }
    }

    fun requestSelectLog(fileName: String) {
        if (fileName == currentLogFile) return
        // 切换文件：先清空旧内容避免误读，不翻 loading 旗标——
        // 交由 LaunchedEffect(currentLogFile) 的实时流自动接管，按钮保持可点击
        logContent = ""
        currentLogFile = fileName
        scope.launch {
            reloadCurrentLog(
                failureText = failedReadText,
                showLoading = false,
                surfaceErrors = true
            )
        }
    }

    fun requestDeleteCurrentLog() {
        if (currentLogFile.isBlank()) return
        scope.launch {
            loading = true
            error = null
            try {
                deleteLogFile(currentLogFile)
                // shell 失败时，用当前已知列表兜底
                val names = queryLogFiles() ?: logFiles.map { it.first }.filter { it != currentLogFile }
                logFiles = names.map { it to statusAvailableText }
                val nextFile = names.firstOrNull().orEmpty()
                currentLogFile = nextFile
                logContent = if (nextFile.isBlank()) "" else (queryLogContent(nextFile) ?: "")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: failedDeleteText
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        autoRefresh = prefs.getBoolean("logs_auto_refresh", true)
        val lastFile = prefs.getString("logs_last_file", null)
        prefsLoaded = true
        refreshLogs(
            preferredFile = lastFile,
            failureText = failedLoadText,
            showLoading = true
        )
    }

    LaunchedEffect(autoRefresh, prefsLoaded) {
        if (!prefsLoaded) return@LaunchedEffect
        prefs.edit().putBoolean("logs_auto_refresh", autoRefresh).apply()
    }

    LaunchedEffect(currentLogFile, prefsLoaded) {
        if (!prefsLoaded) return@LaunchedEffect
        if (currentLogFile.isNotBlank()) {
            prefs.edit().putString("logs_last_file", currentLogFile).apply()
        }
    }

    // 文件列表自动发现：始终开启，独立于自动刷新开关
    // 每 ~4s 扫描 run 目录；模块启停/重启瞬间可能短暂为空或 shell 失败，
    // 用「抖动保护」避免瞬态清空 UI 导致按钮被禁用
    LaunchedEffect(prefsLoaded) {
        if (!prefsLoaded) return@LaunchedEffect
        var consecutiveEmpty = 0
        while (isActive) {
            delay(4_000)
            try {
                val names = queryLogFiles()
                if (names == null) {
                    // shell 失败（root 管理器 busy、模块重启 IPC 中断等）→ 跳过，不改 UI
                    continue
                }
                // 真正空：累计 3 次（~12s）再写入，防止模块重启瞬间的一两次空列表清空文件切换按钮
                if (names.isEmpty()) {
                    consecutiveEmpty++
                    if (consecutiveEmpty < 3) continue
                } else {
                    consecutiveEmpty = 0
                }
                if (names != logFiles.map { it.first }) {
                    logFiles = names.map { it to statusAvailableText }
                }
                when {
                    currentLogFile.isBlank() && names.isNotEmpty() -> {
                        currentLogFile = names.first()
                    }
                    currentLogFile.isNotBlank() && names.isNotEmpty() && currentLogFile !in names -> {
                        // 仅当新列表非空且当前文件确实不在其中才切换，避免短暂抖动丢选中
                        currentLogFile = names.first()
                    }
                    currentLogFile.isNotBlank() && names.isEmpty() -> {
                        // 连续 3 次确认为空才清空内容 + 选中
                        currentLogFile = ""
                        logContent = ""
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 静默失败：显式刷新会暴露错误
            }
        }
    }

    // 自动刷新开关：仅驱动「日志内容实时 tail」。关闭 = 暂停实时内容更新
    // 文件切换时立即拉取一次（不等 800ms），然后周期轮询
    LaunchedEffect(autoRefresh, currentLogFile, prefsLoaded) {
        if (!prefsLoaded || currentLogFile.isBlank()) return@LaunchedEffect
        // 即时加载：无论 autoRefresh 开关如何，切换文件后必须第一时间拉到内容
        reloadCurrentLog(
            failureText = failedReadText,
            showLoading = false,
            surfaceErrors = false
        )
        if (!autoRefresh) return@LaunchedEffect
        while (isActive) {
            delay(800)
            reloadCurrentLog(
                failureText = failedReadText,
                showLoading = false,
                surfaceErrors = false
            )
        }
    }

    LaunchedEffect(listState) {
        var last = listState.firstVisibleItemIndex * 10_000 + listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex * 10_000 + listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { now ->
                if (now > last) onNavVisibilityChange(false)
                else if (now < last) onNavVisibilityChange(true)
                last = now
            }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.tools_logs_title),
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
                    // 日志文件切换（WindowListPopup，锚定在按钮上）—— 不受 loading 影响
                    Box {
                        IconButton(
                            onClick = { if (dropdownItems.isNotEmpty()) showFilePopup = true },
                            enabled = dropdownItems.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Description,
                                contentDescription = stringResource(R.string.tools_logs_log_file),
                                tint = if (dropdownItems.isEmpty()) {
                                    MiuixTheme.colorScheme.onSurfaceSecondary
                                } else {
                                    MiuixTheme.colorScheme.onSurface
                                }
                            )
                        }
                        LogFileSelectorPopup(
                            show = showFilePopup,
                            items = dropdownItems,
                            selectedIndex = selectedLogIndex,
                            onDismissRequest = { showFilePopup = false },
                            onSelect = { index ->
                                showFilePopup = false
                                val next = logFiles.getOrNull(index)?.first ?: return@LogFileSelectorPopup
                                requestSelectLog(next)
                            }
                        )
                    }
                    // 自动刷新开关（仅控制日志内容实时 tail）
                    IconButton(
                        onClick = { autoRefresh = !autoRefresh }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Autorenew,
                            contentDescription = stringResource(R.string.tools_logs_auto_refresh),
                            tint = if (autoRefresh) {
                                MiuixTheme.colorScheme.primary
                            } else {
                                MiuixTheme.colorScheme.onSurfaceSecondary
                            }
                        )
                    }
                    IconButton(
                        onClick = ::requestRefresh,
                        enabled = !loading
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_refresh),
                            tint = if (loading) {
                                MiuixTheme.colorScheme.onSurfaceSecondary
                            } else {
                                MiuixTheme.colorScheme.onSurface
                            }
                        )
                    }
                    // 删除按钮 —— 仅以"是否有文件"为启用条件，不受 loading 影响
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = currentLogFile.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = if (currentLogFile.isBlank()) {
                                MiuixTheme.colorScheme.onSurfaceSecondary
                            } else {
                                MiuixTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = contentPaddingWithNavBars(
                start = 12.dp,
                end = 12.dp,
                top = innerPadding.calculateTopPadding(),
                extraBottom = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item(key = "logs_view_tabs") {
                TabRowWithContour(
                    tabs = listOf(
                        stringResource(R.string.tools_logs_tab_module),
                        stringResource(R.string.tools_logs_tab_detail)
                    ),
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = { selectedTabIndex = it },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 排序 + 级别过滤控制栏（两个视图共享）
            item(key = "logs_controls") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    // 排序切换
                    Row(
                        modifier = Modifier
                            .clip(SmoothRoundedCornerShape(12.dp))
                            .background(MiuixTheme.colorScheme.surfaceVariant)
                            .clickable { newestFirst = !newestFirst }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SwapVert,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (newestFirst) stringResource(R.string.tools_logs_sort_newest)
                            else stringResource(R.string.tools_logs_sort_oldest),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                    }

                    // 级别过滤 chips（HyperOS3 风格，水平滚动）
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HyperFilterChip(
                            selected = levelFilter == null,
                            onClick = { levelFilter = null },
                            label = stringResource(R.string.tools_logs_filter_all)
                        )
                        listOf(LogLevel.Error, LogLevel.Warn, LogLevel.Info, LogLevel.Debug).forEach { level ->
                            HyperFilterChip(
                                selected = levelFilter == level,
                                onClick = { levelFilter = if (levelFilter == level) null else level },
                                label = level.displayName()
                            )
                        }
                    }
                }
            }

            // 冷启动占位：只在 loading 且「完全没内容」时展示，避免切换时盖掉旧数据
            val showColdLoading = loading && logContent.isBlank() && moduleEntries.isEmpty()
            if (showColdLoading) {
                item(key = "logs_loading") { LogsLoadingCard() }
            } else if (selectedTabIndex == 0) {
                if (moduleEntries.isEmpty()) {
                    item(key = "logs_module_empty") {
                        LogsEmptyCard(text = stringResource(R.string.tools_logs_no_content))
                    }
                } else {
                    items(
                        items = moduleEntries,
                        key = { entry -> entry.key },
                        contentType = { "moduleLog" }
                    ) { entry ->
                        ModuleLogCard(
                            entry = entry,
                            fontFamily = logFontFamily,
                            paletteOf = logPaletteOf
                        )
                    }
                }
            } else {
                item(key = "logs_detail") {
                    DetailedLogsCard(
                        logContent = filteredLogContent,
                        logFontFamily = logFontFamily,
                        paletteOf = logPaletteOf
                    )
                }
            }
        }
    }

    HyperDialog(
        show = showDeleteConfirm,
        onDismissRequest = { showDeleteConfirm = false },
        title = stringResource(R.string.tools_logs_delete_dialog_title),
        summary = if (currentLogFile.isBlank()) {
            stringResource(R.string.tools_logs_no_files)
        } else {
            stringResource(R.string.tools_logs_delete_dialog_body, currentLogFile, runDir)
        },
        confirmText = stringResource(R.string.action_delete),
        onConfirm = {
            showDeleteConfirm = false
            requestDeleteCurrentLog()
        },
        dismissText = stringResource(R.string.action_cancel),
        onDismiss = { showDeleteConfirm = false },
        icon = Icons.Filled.DeleteOutline
    )
}

@Composable
private fun LogFileSelectorPopup(
    show: Boolean,
    items: List<String>,
    selectedIndex: Int,
    onDismissRequest: () -> Unit,
    onSelect: (Int) -> Unit
) {
    if (items.isEmpty()) return
    WindowListPopup(
        show = show,
        alignment = PopupPositionProvider.Align.TopEnd,
        onDismissRequest = onDismissRequest
    ) {
        ListPopupColumn {
            items.forEachIndexed { index, label ->
                DropdownImpl(
                    text = label,
                    optionSize = items.size,
                    isSelected = index == selectedIndex,
                    onSelectedIndexChange = { onSelect(index) },
                    index = index
                )
            }
        }
    }
}

@Composable
private fun ModuleLogCard(
    entry: LogModuleEntry,
    fontFamily: FontFamily = FontFamily.Monospace,
    paletteOf: (LogLevel) -> HomeSemanticColors
) {
    val palette = paletteOf(entry.level)
    val scheme = MiuixTheme.colorScheme
    // cleanMessage 已在后台线程预清洗，直接读取，不做任何主线程计算
    val cleanMessage = entry.cleanMessage

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 第一行：[级别徽标] 标签名 ··· 时间戳
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 徽标：Monet/主题联动的 container 背景 + onContainer 文字，保证对比度
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(SmoothRoundedCornerShape(6.dp))
                        .background(palette.container),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = entry.level.badgeLetter(),
                        style = MiuixTheme.textStyles.footnote2.copy(fontFamily = fontFamily),
                        fontWeight = FontWeight.SemiBold,
                        color = palette.onContainer
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = entry.tag,
                    style = MiuixTheme.textStyles.body2.copy(fontFamily = fontFamily),
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (entry.timestamp != null) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = entry.timestamp,
                        style = MiuixTheme.textStyles.footnote2.copy(fontFamily = fontFamily),
                        color = scheme.onSurfaceSecondary,
                        maxLines = 1
                    )
                }
            }

            // 第二行：副信息（#index level）
            Text(
                text = "#${entry.index}  ${entry.level.displayName().lowercase()}",
                style = MiuixTheme.textStyles.footnote2.copy(fontFamily = fontFamily),
                color = scheme.onSurfaceSecondary
            )

            // 第三行：日志正文，统一 onSurface 主色
            Text(
                text = cleanMessage.ifBlank { entry.rawLine },
                style = MiuixTheme.textStyles.body2.copy(
                    fontFamily = fontFamily,
                    lineHeight = 20.sp
                ),
                color = scheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DetailedLogsCard(
    logContent: String,
    logFontFamily: FontFamily,
    paletteOf: (LogLevel) -> HomeSemanticColors
) {
    val highlightedLog = rememberHighlightedLog(logContent, paletteOf)

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.tools_logs_detail_hint),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )

            // 代码区：surfaceVariant，对比更柔
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SmoothRoundedCornerShape(16.dp))
                    .background(MiuixTheme.colorScheme.surfaceVariant)
                    .padding(14.dp)
            ) {
                if (logContent.isBlank()) {
                    Text(
                        text = stringResource(R.string.tools_logs_no_content),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                } else {
                    SelectionContainer {
                        BasicText(
                            text = highlightedLog,
                            modifier = Modifier.fillMaxWidth(),
                            style = MiuixTheme.textStyles.footnote1.copy(
                                fontFamily = logFontFamily,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LogsLoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 32.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfiniteProgressIndicator(modifier = Modifier.size(24.dp))
            Text(
                text = stringResource(R.string.tools_logs_loading),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
    }
}

@Composable
private fun LogsEmptyCard(text: String) {
    val scheme = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 36.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 图标容器
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(SmoothRoundedCornerShape(14.dp))
                    .background(scheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = MiuixIcons.Notes,
                    contentDescription = null,
                    tint = scheme.onSurfaceSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = text,
                style = MiuixTheme.textStyles.body2,
                color = scheme.onSurfaceSecondary
            )
        }
    }
}

@Composable
private fun rememberHighlightedLog(
    logContent: String,
    paletteOf: (LogLevel) -> HomeSemanticColors
): androidx.compose.ui.text.AnnotatedString {
    val isDark = ThemeManager.shouldUseDarkTheme()
    val mapleFontActive by ThemeManager.mapleFontLogs.collectAsState()
    val mapleLoaded = MapleFontManager.getFontFamily() != null
    val empty = remember { androidx.compose.ui.text.AnnotatedString("") }

    // 语义色快照：带进 produceState，保证 Monet/深浅切换即时响应
    val errorColor = paletteOf(LogLevel.Error).accent
    val warnColor  = paletteOf(LogLevel.Warn).accent
    val infoColor  = paletteOf(LogLevel.Info).accent
    val debugColor = paletteOf(LogLevel.Debug).accent
    val defaultColor = MiuixTheme.colorScheme.onSurfaceSecondary

    // 并行构建 AnnotatedString：按 CPU 核数切分，每段独立协程跑正则+构建，最后合并
    val result by produceState(
        empty,
        logContent, isDark, mapleFontActive, mapleLoaded,
        errorColor, warnColor, infoColor, debugColor, defaultColor
    ) {
        value = withContext(Dispatchers.Default) {
            val allLines = logContent.lines()
            if (allLines.isEmpty()) return@withContext empty

            val hasNerdFont = mapleFontActive && mapleLoaded
            val iconError = if (hasNerdFont) "\uF057" else "E"
            val iconWarn  = if (hasNerdFont) "\uF071" else "W"
            val iconInfo  = if (hasNerdFont) "\uF05A" else "I"
            val iconDebug = if (hasNerdFont) "\uF188" else "D"
            val iconOther = if (hasNerdFont) "\uF15C" else "\u00B7"

            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
            // 行数少时不值得分片（调度开销大于收益）
            val chunks = if (allLines.size <= 120) {
                listOf(allLines)
            } else {
                val chunkSize = (allLines.size + cores - 1) / cores
                allLines.chunked(chunkSize)
            }

            val parts: List<androidx.compose.ui.text.AnnotatedString> = coroutineScope {
                chunks.map { chunk ->
                    async {
                        buildAnnotatedString {
                            chunk.forEachIndexed { i, line ->
                                val level = detectLogLevel(line)
                                val (icon, lineColor) = when (level) {
                                    LogLevel.Error -> iconError to errorColor
                                    LogLevel.Warn  -> iconWarn  to warnColor
                                    LogLevel.Info  -> iconInfo  to infoColor
                                    LogLevel.Debug -> iconDebug to debugColor
                                    LogLevel.Other -> iconOther to defaultColor
                                }
                                withStyle(SpanStyle(color = lineColor)) { append(icon) }
                                append(' ')
                                val cleaned = cleanLogMessage(line)
                                withStyle(SpanStyle(color = lineColor)) {
                                    append(cleaned.ifBlank { line })
                                }
                                if (i != chunk.lastIndex) append('\n')
                            }
                        }
                    }
                }.awaitAll()
            }

            if (parts.size == 1) parts[0] else buildAnnotatedString {
                parts.forEachIndexed { i, p ->
                    append(p)
                    if (i != parts.lastIndex) append('\n')
                }
            }
        }
    }
    return result
}

/**
 * 日志级别 → 项目语义色映射（自动适配 Monet + 深/浅模式）
 *
 * 必须在屏幕级仅调用一次；600 条日志若各自调用会重复订阅 monetEnabled Flow。
 * 返回一个仅读取的 lookup 函数供子卡片使用。
 */
@Composable
private fun rememberLogLevelPaletteProvider(): (LogLevel) -> HomeSemanticColors {
    val error = homeDangerColors()
    val warn  = homeWarningColors()
    val info  = homeInfoColors()
    val debug = homeSuccessColors()
    val other = homeNeutralColors()
    return remember(error, warn, info, debug, other) {
        { level ->
            when (level) {
                LogLevel.Error -> error
                LogLevel.Warn  -> warn
                LogLevel.Info  -> info
                LogLevel.Debug -> debug
                LogLevel.Other -> other
            }
        }
    }
}

/** 单行解析中间结果：每行独立计算，可安全并行 */
private data class LineParsed(
    val rawLine: String,
    val cleanMessage: String,
    val level: LogLevel,
    val tag: String,
    val timestamp: String?
)

private fun parseLine(line: String, fallbackTag: String): LineParsed = LineParsed(
    rawLine = line,
    cleanMessage = cleanLogMessage(line),
    level = detectLogLevel(line),
    tag = inferLogTag(line, fallbackTag),
    timestamp = extractTimestamp(line)
)

/**
 * 并行解析：per-line 正则无跨行依赖 → 分片并行处理
 * 最后单线程扫一遍分配 index 与 dedup key（O(n)）
 */
private suspend fun parseModuleEntries(
    logContent: String,
    fallbackTag: String
): List<LogModuleEntry> {
    val lines = logContent.lineSequence()
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }
        .toList()
    if (lines.isEmpty()) return emptyList()

    // 小规模单线程（调度开销大于收益）；大规模按核数分片并行跑正则
    val parsed: List<LineParsed> = if (lines.size <= 120) {
        lines.map { parseLine(it, fallbackTag) }
    } else {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val chunkSize = (lines.size + cores - 1) / cores
        coroutineScope {
            lines.chunked(chunkSize).map { chunk ->
                async(Dispatchers.Default) {
                    chunk.map { parseLine(it, fallbackTag) }
                }
            }.awaitAll().flatten()
        }
    }

    // 顺序扫描：分配 index + 基于 rawLine 的去重计数（单线程 O(n) 保证顺序）
    val seen = HashMap<String, Int>(parsed.size)
    val out = ArrayList<LogModuleEntry>(parsed.size)
    parsed.forEachIndexed { index, p ->
        val dup = (seen[p.rawLine] ?: 0) + 1
        seen[p.rawLine] = dup
        val entryKey = if (dup == 1) p.rawLine else p.rawLine + "|" + dup
        out.add(
            LogModuleEntry(
                index = index + 1,
                key = entryKey,
                rawLine = p.rawLine,
                cleanMessage = p.cleanMessage,
                level = p.level,
                tag = p.tag,
                timestamp = p.timestamp
            )
        )
    }
    return out
}

private fun detectLogLevel(line: String): LogLevel {
    val lower = line.lowercase()
    return when {
        "error" in lower || "fatal" in lower || "exception" in lower -> LogLevel.Error
        "warn" in lower || "warning" in lower -> LogLevel.Warn
        "info" in lower -> LogLevel.Info
        "debug" in lower || "trace" in lower -> LogLevel.Debug
        else -> LogLevel.Other
    }
}

// ── 预编译正则：避免每行调用时重新编译（600 行 × 8 个正则 → 4800 次 → 0 次）──
private val TAG_BRACKET_REGEX = Regex("""\[([^\]]+)]""")
private val TAG_COLON_REGEX   = Regex("""\b([A-Za-z][A-Za-z0-9_.-]{1,24})\b(?=:)""")
private val TIMESTAMP_REGEX   = Regex("""\d{4}-\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}(?:[.,]\d+)?|\d{2}:\d{2}:\d{2}(?:[.,]\d+)?""")
private val TIMESTAMP_TRIM_MS_REGEX = Regex("""[.,]\d+$""")

private val CLEAN_TZ_OFFSET_REGEX   = Regex("""^[+-]\d{2}:?\d{2}\s+""")
private val CLEAN_TIME_EQ_REGEX     = Regex("""^time="[^"]*"\s*""")
private val CLEAN_DATETIME_REGEX    = Regex("""^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(?:[.,]\d+)?(?:[+-]\d{2}:?\d{2}|Z)?\s*""")
private val CLEAN_LEVEL_BRACKET_REGEX = Regex(
    """^\[(?:info|warn(?:ing)?|error|fatal|debug|trace|inf|wrn|err|dbg|trc)]:?\s*""",
    RegexOption.IGNORE_CASE
)
private val CLEAN_LEVEL_BARE_REGEX = Regex(
    """^(?:INFO|WARN(?:ING)?|ERROR|FATAL|DEBUG|TRACE|INF|WRN|ERR|DBG|TRC|Info|Warn(?:ing)?|Error|Fatal|Debug|Trace)\s+"""
)
private val CLEAN_LEVEL_EQ_REGEX = Regex("""^level=\w+\s*""", RegexOption.IGNORE_CASE)
private val CLEAN_MSG_EQ_REGEX   = Regex("""^msg="(.*)"$""")

private fun inferLogTag(line: String, fallbackTag: String): String {
    val bracketTag = TAG_BRACKET_REGEX.find(line)
        ?.groupValues?.getOrNull(1)?.trim()
        ?.takeIf { it.length in 2..36 }
    if (!bracketTag.isNullOrBlank()) return bracketTag

    val colonTag = TAG_COLON_REGEX.find(line)
        ?.groupValues?.getOrNull(1)?.trim()
    if (!colonTag.isNullOrBlank()) return colonTag

    return fallbackTag.substringBeforeLast('.').ifBlank { fallbackTag.ifBlank { "Log" } }
}

private fun extractTimestamp(line: String): String? {
    val raw = TIMESTAMP_REGEX.find(line)?.value ?: return null
    return raw.replace(TIMESTAMP_TRIM_MS_REGEX, "")
}

/**
 * 清洗日志正文：去除卡片头部已展示的时间戳和级别前缀，保留纯消息内容。
 * 例: "2026-04-08 10:30:43 [Warning]: 正在清理 TUN 规则。" → "正在清理 TUN 规则。"
 */
private fun cleanLogMessage(rawLine: String): String {
    var s = rawLine
    s = s.replace(CLEAN_TZ_OFFSET_REGEX, "")
    s = s.replace(CLEAN_TIME_EQ_REGEX, "")
    s = s.replace(CLEAN_DATETIME_REGEX, "")
    s = s.replace(CLEAN_LEVEL_BRACKET_REGEX, "")
    s = s.replace(CLEAN_LEVEL_BARE_REGEX, "")
    s = s.replace(CLEAN_LEVEL_EQ_REGEX, "")
    s = s.replace(CLEAN_MSG_EQ_REGEX, "$1")
    return s.trimStart()
}

private fun LogLevel.displayName(): String = when (this) {
    LogLevel.Error -> "ERROR"
    LogLevel.Warn -> "WARN"
    LogLevel.Info -> "INFO"
    LogLevel.Debug -> "DEBUG"
    LogLevel.Other -> "LOG"
}

private fun LogLevel.badgeLetter(): String = when (this) {
    LogLevel.Error -> "E"
    LogLevel.Warn -> "W"
    LogLevel.Info -> "I"
    LogLevel.Debug -> "D"
    LogLevel.Other -> "L"
}

