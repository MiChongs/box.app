package com.box.app.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.kyant.shapes.RoundedRectangle
import kotlin.math.roundToInt
import com.box.app.R
import com.box.app.ui.components.ErrorToast
import com.box.app.ui.components.LiquidGlassButton
import com.box.app.ui.components.LiquidGlassDropdownMenu
import com.box.app.ui.components.LiquidGlassIconButton
import com.box.app.ui.components.LocalLiquidBackdrop
import com.box.app.ui.components.ToolsRowIcon
import com.box.app.ui.components.ToolsSectionCard
import com.box.app.ui.components.contentPaddingWithNavBars
import com.box.app.data.backend.ShellExecutor
import com.box.app.ui.theme.appAccentColor
import com.box.app.ui.theme.appColors
import com.box.app.ui.theme.appErrorColor
import com.box.app.utils.ThemeManager
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive

private enum class LogsMenuAction { Refresh, AutoRefresh, Delete }

@Composable
fun ToolsLogsScreen(
    onNavVisibilityChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {

    val c = appColors()
    val context = LocalContext.current
    val isDark = ThemeManager.shouldUseDarkTheme()
    val pagePadding = 20.dp
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    val prefs = remember { context.getSharedPreferences("tools_logs", Context.MODE_PRIVATE) }

    val statusAvailableText = stringResource(R.string.tools_logs_status_available)
    val failedLoadText = stringResource(R.string.tools_logs_failed_load)
    val failedReadText = stringResource(R.string.tools_logs_failed_read)
    val failedRefreshText = stringResource(R.string.tools_logs_failed_refresh)
    val failedDeleteText = stringResource(R.string.tools_logs_failed_delete)

    var autoRefresh by rememberSaveable { mutableStateOf(false) }
    var currentLogFile by rememberSaveable { mutableStateOf("core.log") }
    var loading by rememberSaveable { mutableStateOf(false) }
    var menuAction by rememberSaveable { mutableStateOf<LogsMenuAction?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    var logMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var logMenuAnchorBoundsInWindow by remember { mutableStateOf<IntRect?>(null) }

    ErrorToast(
        message = error,
        onConsumed = { error = null }
    )

    var prefsLoaded by remember { mutableStateOf(false) }
    var desiredLogFile by remember { mutableStateOf<String?>(null) }

    var topBarHeightPx by rememberSaveable { mutableStateOf(0) }
    var lastNonZeroTopBarHeightPx by rememberSaveable { mutableStateOf(0) }
    val density = LocalDensity.current
    val effectiveTopBarHeightPx = if (topBarHeightPx > 0) topBarHeightPx else lastNonZeroTopBarHeightPx
    val topInset = with(density) { effectiveTopBarHeightPx.toDp() } + 16.dp

    val runDir = "/data/adb/box/run"
    var logFiles by rememberSaveable { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var logContent by rememberSaveable { mutableStateOf("") }

    val liquidBackdrop = rememberLayerBackdrop()

    suspend fun loadLogFiles() {
        val cmd = "for f in $runDir/*; do [ -f \"\$f\" ] || continue; b=\$(basename \"\$f\"); case \"\$b\" in *.log|*.LOG) echo \"\$b\";; esac; done"
        val result = ShellExecutor.execute(cmd)
        if (result.exitCode != 0 && result.stdout.isBlank()) {
            logFiles = emptyList()
            return
        }
        val names = result.stdout
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

        logFiles = names.map { it to statusAvailableText }
    }

    suspend fun loadLogContent(fileName: String) {
        if (fileName.isBlank()) {
            logContent = ""
            return
        }
        val safeName = fileName.replace("\"", "").replace("'", "")
        val path = "$runDir/$safeName"
        val cmd = "tail -n 600 '$path' 2>/dev/null || true"
        val result = ShellExecutor.execute(cmd)
        logContent = result.stdout
    }

    suspend fun deleteLogFile(fileName: String) {
        if (fileName.isBlank()) return
        val safeName = fileName.replace("\"", "").replace("'", "")
        val path = "$runDir/$safeName"
        ShellExecutor.execute("rm -f '$path' 2>/dev/null || true")
    }

    LaunchedEffect(Unit) {
        autoRefresh = prefs.getBoolean("logs_auto_refresh", false)
        val lastFile = prefs.getString("logs_last_file", null)
        desiredLogFile = lastFile
        if (!lastFile.isNullOrBlank()) {
            currentLogFile = lastFile
        }
        prefsLoaded = true

        loading = true
        error = null
        try {
            loadLogFiles()

            val names = logFiles.map { it.first }
            val chosen = when {
                !desiredLogFile.isNullOrBlank() && desiredLogFile in names -> desiredLogFile!!
                names.isNotEmpty() -> names.first()
                else -> ""
            }
            if (chosen != currentLogFile) currentLogFile = chosen

            loadLogContent(currentLogFile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: failedLoadText
        } finally {
            loading = false
        }
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

    LaunchedEffect(currentLogFile) {
        if (currentLogFile.isBlank()) return@LaunchedEffect
        loading = true
        error = null
        try {
            loadLogContent(currentLogFile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e.message ?: failedReadText
        } finally {
            loading = false
        }
    }

    LaunchedEffect(autoRefresh, currentLogFile) {
        if (!autoRefresh || currentLogFile.isBlank()) return@LaunchedEffect
        while (isActive) {
            try {
                loadLogContent(currentLogFile)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
            delay(1500)
        }
    }

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

    LaunchedEffect(menuAction) {
        when (menuAction) {
            LogsMenuAction.Refresh -> {
                loading = true
                error = null

                try {
                    loadLogFiles()

                    val names = logFiles.map { it.first }
                    if (names.isNotEmpty() && currentLogFile !in names) {
                        currentLogFile = names.first()
                    }
                    loadLogContent(currentLogFile)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = e.message ?: failedRefreshText
                }
                loading = false
            }
            LogsMenuAction.AutoRefresh -> {
                autoRefresh = !autoRefresh
                if (prefsLoaded) {
                    prefs.edit().putBoolean("logs_auto_refresh", autoRefresh).apply()
                }
            }
            LogsMenuAction.Delete -> {
                loading = true
                error = null

                try {
                    val deleting = currentLogFile
                    deleteLogFile(deleting)
                    loadLogFiles()
                    if (logFiles.isNotEmpty()) {
                        currentLogFile = logFiles.first().first
                        loadLogContent(currentLogFile)
                    } else {
                        currentLogFile = ""
                        logContent = ""
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = e.message ?: failedDeleteText
                }
                loading = false
            }
            else -> Unit
        }
        menuAction = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.pageBg)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(liquidBackdrop),
            contentPadding = contentPaddingWithNavBars(
                start = pagePadding,
                end = pagePadding,
                top = topInset
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item(key = "logs_card_combined") {
                Column {
                    // 头部卡片
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = 0.dp,
                            bottomEnd = 0.dp
                        ),
                        colors = CardDefaults.cardColors(containerColor = c.card),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.tools_logs_title),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.tools_logs_current_prefix,
                                            currentLogFile,
                                            if (autoRefresh) stringResource(R.string.tools_logs_auto_refresh_on) else stringResource(R.string.tools_logs_manual_refresh)
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = c.textSecondary
                                    )

                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(if (isDark) c.textSecondary.copy(alpha = 0.28f) else c.divider)
                    )

                    // 内容卡片（紧贴上面的卡片�?
                    if (loading) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(
                                topStart = 0.dp,
                                topEnd = 0.dp,
                                bottomStart = 18.dp,
                                bottomEnd = 18.dp
                            ),
                            colors = CardDefaults.cardColors(containerColor = c.card),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = c.textSecondary,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = stringResource(R.string.tools_logs_loading),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = c.textSecondary
                                )
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(
                                topStart = 0.dp,
                                topEnd = 0.dp,
                                bottomStart = 18.dp,
                                bottomEnd = 18.dp
                            ),
                            colors = CardDefaults.cardColors(containerColor = c.card),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                // 日志内容显示区域
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedRectangle(12.dp))
                                        .background(c.cardAlt)
                                        .padding(12.dp)
                                ) {
                                    val debugColor = if (isDark) Color(0xFF4FC3F7) else Color(0xFF0277BD)
                                    val infoColor = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
                                    val warnColor = if (isDark) Color(0xFFFFB74D) else Color(0xFFF57C00)
                                    val errorColor = appErrorColor()

                                    val coloredLog = remember(logContent, isDark, c.textSecondary) {
                                        val lines = logContent.lines()
                                        buildAnnotatedString {
                                            lines.forEachIndexed { index, line ->
                                                val lower = line.lowercase()
                                                val lineColor = when {
                                                    "error" in lower -> errorColor
                                                    "warn" in lower || "warning" in lower -> warnColor
                                                    "info" in lower -> infoColor
                                                    "debug" in lower -> debugColor
                                                    else -> c.textSecondary
                                                }

                                                withStyle(SpanStyle(color = lineColor)) {
                                                    append(line)
                                                }
                                                if (index != lines.lastIndex) append('\n')
                                            }
                                        }
                                    }

                                    SelectionContainer {
                                        Text(
                                            text = coloredLog,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                            ),
                                            color = c.textSecondary,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "logs_section_spacer") { Spacer(modifier = Modifier.height(14.dp)) }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        CompositionLocalProvider(LocalLiquidBackdrop provides liquidBackdrop) {
            FloatingLogsTopBar(
                onBack = onBack,
                autoRefresh = autoRefresh,
                currentLogFile = currentLogFile,
                onLogFileChange = {
                    currentLogFile = it
                    if (prefsLoaded && it.isNotBlank()) {
                        prefs.edit().putString("logs_last_file", it).apply()
                    }
                },
                onMenuAction = { menuAction = it },
                onDeleteClick = { showDeleteConfirm = true },
                menuExpanded = logMenuExpanded,
                onMenuExpandedChange = { logMenuExpanded = it },
                onMenuAnchorBoundsInWindowChange = { logMenuAnchorBoundsInWindow = it },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .onGloballyPositioned { coordinates ->
                        topBarHeightPx = coordinates.size.height
                    }
            )
        }

        CompositionLocalProvider(LocalLiquidBackdrop provides liquidBackdrop) {
            LogFileDropdownMenu(
                expanded = logMenuExpanded,
                currentLogFile = currentLogFile,
                onDismissRequest = { logMenuExpanded = false },
                onLogFileSelect = { fileName ->
                    logMenuExpanded = false
                    currentLogFile = fileName
                    if (prefsLoaded && fileName.isNotBlank()) {
                        prefs.edit().putString("logs_last_file", fileName).apply()
                    }
                },
                anchorBoundsInWindow = logMenuAnchorBoundsInWindow,
                modifier = Modifier
            )
        }
    }

    if (showDeleteConfirm) {
        val accent = appAccentColor()

        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = c.card,
            titleContentColor = c.textPrimary,
            textContentColor = c.textSecondary,
            title = { Text(text = stringResource(R.string.tools_logs_delete_dialog_title)) },
            text = {
                Text(
                    text = stringResource(R.string.tools_logs_delete_dialog_body, currentLogFile, runDir),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        menuAction = LogsMenuAction.Delete
                    }
                ) {
                    Text(text = stringResource(R.string.action_delete), color = appErrorColor())
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(text = stringResource(R.string.action_cancel), color = c.textPrimary)
                }
            }
        )
    }
}

@Composable
private fun FloatingLogsTopBar(
    onBack: () -> Unit,
    autoRefresh: Boolean,
    currentLogFile: String,
    onLogFileChange: (String) -> Unit,
    onMenuAction: (LogsMenuAction) -> Unit,
    onDeleteClick: () -> Unit,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onMenuAnchorBoundsInWindowChange: (IntRect?) -> Unit,
    modifier: Modifier = Modifier
) {

    val c = appColors()
    val backdrop = requireNotNull(LocalLiquidBackdrop.current)
    val accent = appAccentColor()
    val isDark = ThemeManager.shouldUseDarkTheme()

    val selectedTint = if (isDark) Color(0xFF2B2F37) else Color(0xFFE3E6EA)
    val tint = selectedTint.copy(alpha = 0.25f)
    val fallback = selectedTint.copy(alpha = 0.80f)

    val menu = menuExpanded

    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, top = 8.dp, end = 20.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LiquidGlassButton(
                onClick = onBack,
                backdrop = backdrop,
                surfaceColor = tint
            ) {
                Text(
                    text = stringResource(R.string.tools_logs_back_compact),
                    color = c.textPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            LiquidGlassIconButton(
                onClick = { onMenuAction(LogsMenuAction.Refresh) },
                backdrop = backdrop,
                surfaceColor = tint
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.action_refresh),
                    tint = c.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            LiquidGlassIconButton(
                onClick = { onMenuAction(LogsMenuAction.AutoRefresh) },
                backdrop = backdrop,
                surfaceColor = tint
            ) {
                Icon(
                    imageVector = Icons.Filled.Autorenew,
                    contentDescription = stringResource(R.string.tools_logs_auto_refresh),
                    tint = if (autoRefresh) accent else c.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            LiquidGlassIconButton(
                onClick = onDeleteClick,
                backdrop = backdrop,
                surfaceColor = tint
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = c.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.TopEnd)
                    .onGloballyPositioned { coords ->
                        val r = coords.boundsInWindow()
                        onMenuAnchorBoundsInWindowChange(
                            IntRect(
                                left = r.left.roundToInt(),
                                top = r.top.roundToInt(),
                                right = r.right.roundToInt(),
                                bottom = r.bottom.roundToInt()
                            )
                        )
                    }
            ) {
                LiquidGlassIconButton(
                    onClick = { onMenuExpandedChange(true) },
                    backdrop = backdrop,
                    surfaceColor = tint
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.action_more),
                        tint = c.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LogFileDropdownMenu(
    expanded: Boolean,
    currentLogFile: String,
    onDismissRequest: () -> Unit,
    onLogFileSelect: (String) -> Unit,
    anchorBoundsInWindow: IntRect?,
    modifier: Modifier = Modifier
) {
    val c = appColors()
    val accent = appAccentColor()

    val statusAvailableText = stringResource(R.string.tools_logs_status_available)

    val runDir = "/data/adb/box/run"
    var logFiles by rememberSaveable { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    LaunchedEffect(expanded) {
        if (!expanded) return@LaunchedEffect

        val cmd = "for f in $runDir/*; do [ -f \"\$f\" ] || continue; b=\$(basename \"\$f\"); case \"\$b\" in *.log|*.LOG) echo \"\$b\";; esac; done"
        val result = ShellExecutor.execute(cmd)
        val names = result.stdout
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

        logFiles = names.map { it to statusAvailableText }
    }

    LiquidGlassDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        anchorBoundsInWindow = anchorBoundsInWindow,
        modifier = modifier,
        offset = DpOffset(x = 0.dp, y = 8.dp),
        shape = RoundedRectangle(16.dp),
        containerColor = c.cardAlt
    ) {
        if (logFiles.isEmpty()) {
            com.box.app.ui.components.NoRippleDropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.tools_logs_no_files),
                        color = c.textSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                enabled = false,
                onClick = { }
            )
        } else {
            logFiles.forEachIndexed { index, (fileName, status) ->
                val isSelected = fileName == currentLogFile
                com.box.app.ui.components.NoRippleDropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = fileName,
                                color = if (isSelected) accent else c.textPrimary,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Text(
                                text = if (isSelected) stringResource(R.string.tools_logs_status_current) else status,
                                color = if (isSelected) accent else c.textSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = null,
                            tint = if (isSelected) accent else c.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        onLogFileSelect(fileName)
                    }
                )
            }
        }
    }
}
