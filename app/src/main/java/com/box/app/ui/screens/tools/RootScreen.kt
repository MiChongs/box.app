package com.box.app.ui.screens.tools

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.box.app.BuildConfig
import com.box.app.R
import com.box.app.data.backend.BoxApi
import com.box.app.data.repo.HomeRepository
import com.box.app.ui.components.ErrorToast
import com.box.app.ui.components.contentPaddingWithNavBars
import com.box.app.ui.miuix.HyperBottomSheet
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import dev.lackluster.hyperx.ui.layout.HyperXPage

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import com.box.app.data.backend.ShellExecutor
import com.box.app.utils.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.shapes.SmoothRoundedCornerShape
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ToolsRootScreen(
    onNavVisibilityChange: (Boolean) -> Unit,
    listState: LazyListState,
    onOpenConfigManage: () -> Unit,
    onOpenConfigSelect: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenNetworkControl: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenUpdateSubscription: () -> Unit,
    onOpenUpdateCnip: () -> Unit,
    onOpenMonitorSettings: () -> Unit,
) {
    val context = LocalContext.current
    val updatePrefs = remember { context.getSharedPreferences("tools_update", Context.MODE_PRIVATE) }
    val persistedCore = remember { updatePrefs.getString("selected_update_core", "mihomo") ?: "mihomo" }
    val scope = rememberCoroutineScope()

    var toastMessage by remember { mutableStateOf<String?>(null) }
    var showUpdateCoreSheet by remember { mutableStateOf(false) }
    var selectedCores by remember { mutableStateOf(setOf(persistedCore)) }
    var coreVersions by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var updateStatuses by remember { mutableStateOf<Map<String, CoreUpdateStatus>>(emptyMap()) }
    var isUpdating by remember { mutableStateOf(false) }

    ErrorToast(
        message = toastMessage,
        onConsumed = { toastMessage = null }
    )

    fun mapKernelNameForUpdate(name: String): String = when (name) {
        "v2ray" -> "v2fly"
        else -> name
    }

    /** 并发更新所有已选核心，逐个回报状态 */
    fun updateSelectedCores() {
        if (isUpdating || selectedCores.isEmpty()) return
        isUpdating = true
        updateStatuses = selectedCores.associateWith { CoreUpdateStatus.Updating }

        scope.launch {
            val jobs = selectedCores.map { key ->
                launch(Dispatchers.IO) {
                    val kernel = mapKernelNameForUpdate(key)
                    val success = runCatching { BoxApi.updateKernel(kernel) }.getOrDefault(false)
                    withContext(Dispatchers.Main) {
                        updateStatuses = updateStatuses + (key to
                                if (success) CoreUpdateStatus.Success else CoreUpdateStatus.Failed)
                    }
                }
            }
            jobs.joinAll()
            isUpdating = false

            val sc = updateStatuses.count { it.value == CoreUpdateStatus.Success }
            val fc = updateStatuses.count { it.value == CoreUpdateStatus.Failed }
            toastMessage = if (fc == 0) {
                context.getString(R.string.tools_update_completed, "$sc")
            } else {
                "${context.getString(R.string.tools_update_failed)}: $sc/${sc + fc}"
            }
        }
    }

    // 打开底部工作表时异步检测各核心版本
    LaunchedEffect(showUpdateCoreSheet) {
        if (showUpdateCoreSheet) {
            coreVersions = emptyMap()
            withContext(Dispatchers.IO) {
                val versions = mutableMapOf<String, String>()
                val bins = CORE_OPTIONS.map { it.binName }.distinct()
                for (bin in bins) {
                    runCatching {
                        // 同时执行 -v 和 version，合并输出由 Kotlin 解析器提取版本号
                        val cmd = """for p in "/data/adb/box/bin/$bin" "/data/adb/box/$bin/$bin"; do [ -x "${'$'}p" ] || continue; v=${'$'}( { "${'$'}p" -v 2>/dev/null; "${'$'}p" version 2>/dev/null; } | head -n20 ); [ -n "${'$'}v" ] && echo "${'$'}v" && exit 0; done"""
                        val res = ShellExecutor.execute(cmd)
                        val ver = res.stdout.trim()
                        if (ver.isNotBlank()) {
                            versions[bin] = parseCleanVersion(ver) ?: ver
                        }
                    }
                }
                coreVersions = versions
            }
        }
    }

    LaunchedEffect(Unit) {
        HomeRepository.startPolling()
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

    // 核心多选更新底部工作表（HyperOS3 风格，支持并发更新）
    if (showUpdateCoreSheet) {
        val scheme = MiuixTheme.colorScheme
        val isDark = ThemeManager.shouldUseDarkTheme()
        val successColor = if (isDark) Color(0xFF66BB6A) else Color(0xFF2E7D32)
        val failColor = if (isDark) Color(0xFFEF5350) else Color(0xFFD32F2F)

        HyperBottomSheet(
            show = true,
            onDismissRequest = { if (!isUpdating) showUpdateCoreSheet = false },
            title = stringResource(R.string.tools_update_sheet_title)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.tools_update_sheet_subtitle),
                    style = MiuixTheme.textStyles.body2,
                    color = scheme.onSurfaceSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                CORE_OPTIONS.forEach { option ->
                    val isSelected = option.key in selectedCores
                    val status = updateStatuses[option.key]
                    val version = coreVersions[option.binName]
                    val subtitle = stringResource(option.subtitleRes)

                    val bgColor by animateColorAsState(
                        when (status) {
                            CoreUpdateStatus.Success -> successColor.copy(alpha = 0.10f)
                            CoreUpdateStatus.Failed -> failColor.copy(alpha = 0.10f)
                            CoreUpdateStatus.Updating -> scheme.primary.copy(alpha = 0.08f)
                            else -> if (isSelected) scheme.primary.copy(alpha = 0.12f) else scheme.surfaceContainerHigh
                        },
                        animationSpec = tween(280), label = "upd_bg_${option.key}"
                    )
                    val borderColor by animateColorAsState(
                        when (status) {
                            CoreUpdateStatus.Success -> successColor.copy(alpha = 0.28f)
                            CoreUpdateStatus.Failed -> failColor.copy(alpha = 0.28f)
                            CoreUpdateStatus.Updating -> scheme.primary.copy(alpha = 0.20f)
                            else -> if (isSelected) scheme.primary.copy(alpha = 0.28f) else Color.Transparent
                        },
                        animationSpec = tween(280), label = "upd_bd_${option.key}"
                    )
                    val titleColor by animateColorAsState(
                        when (status) {
                            CoreUpdateStatus.Success -> successColor
                            CoreUpdateStatus.Failed -> failColor
                            else -> if (isSelected) scheme.primary else scheme.onSurface
                        },
                        animationSpec = tween(280), label = "upd_tt_${option.key}"
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SmoothRoundedCornerShape(16.dp))
                            .border(1.dp, borderColor, SmoothRoundedCornerShape(16.dp))
                            .background(bgColor)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = !isUpdating
                            ) {
                                selectedCores = if (option.key in selectedCores)
                                    selectedCores - option.key else selectedCores + option.key
                                // 重选时清除该核心的旧状态
                                updateStatuses = updateStatuses - option.key
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            val fallback = rememberVectorPainter(Icons.Filled.Storage)
                            AsyncImage(
                                model = option.iconUrl,
                                contentDescription = option.displayName,
                                error = fallback,
                                fallback = fallback,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.displayName,
                                    style = MiuixTheme.textStyles.body1,
                                    fontWeight = FontWeight.Medium,
                                    color = titleColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (version != null) "$subtitle · $version" else subtitle,
                                    style = MiuixTheme.textStyles.footnote2,
                                    color = scheme.onSurfaceSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // 右侧状态指示器
                            when (status) {
                                CoreUpdateStatus.Updating ->
                                    InfiniteProgressIndicator(modifier = Modifier.size(20.dp))
                                CoreUpdateStatus.Success ->
                                    Icon(Icons.Filled.CheckCircle, null, tint = successColor, modifier = Modifier.size(20.dp))
                                CoreUpdateStatus.Failed ->
                                    Icon(Icons.Filled.Cancel, null, tint = failColor, modifier = Modifier.size(20.dp))
                                else -> if (isSelected) {
                                    Icon(Icons.Filled.Check, null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 更新按钮：显示选中数量 / 更新中进度
                Button(
                    onClick = { updateSelectedCores() },
                    enabled = selectedCores.isNotEmpty() && !isUpdating,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary()
                ) {
                    if (isUpdating) {
                        val done = updateStatuses.count { it.value != CoreUpdateStatus.Updating }
                        val total = updateStatuses.size
                        InfiniteProgressIndicator(modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$done / $total",
                            style = MiuixTheme.textStyles.button
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.action_update) +
                                    if (selectedCores.size > 1) " (${selectedCores.size})" else "",
                            style = MiuixTheme.textStyles.button
                        )
                    }
                }
            }
        }
    }

    // HyperXPage + HyperXLayoutConfig：模糊跟随全局磨砂设置项；
    // HyperXPage 内部正确处理 topBar 的透明度、backdrop 与 overlapped fraction，
    // 解决直接调 HyperXScaffold 时因 TopAppBar 不透明导致的"模糊内容不正确"问题
    // LocalHyperXLayoutConfig 已由根级 HyperXAppLayout 提供（跟随磨砂设置项）
    // HyperXPage 直接读取，无需在每个页面再次注入
    HyperXPage(
        title = stringResource(R.string.tools_title),
        listState = listState,
        navigationIcon = {} // Tools 是底部 tab 页面，不需要返回按钮
    ) {
            // ═══ 配置 ═══════════════════════════════════════════════════
            // 配置文件管理与活动配置选择
            item("section_config") {
                PreferenceSection(title = stringResource(R.string.tools_section_config_title)) {
                    ArrowPreference(
                        title = stringResource(R.string.tools_row_manage),
                        summary = stringResource(R.string.tools_row_browse_search_create),
                        startAction = { PreferenceIcon(Icons.Filled.Description) },
                        onClick = onOpenConfigManage
                    )
                    PreferenceDivider()
                    ArrowPreference(
                        title = stringResource(R.string.tools_row_select),
                        summary = stringResource(R.string.tools_row_choose_active_config),
                        startAction = { PreferenceIcon(Icons.Filled.Tune) },
                        onClick = onOpenConfigSelect
                    )
                }
            }

            // ═══ 规则与数据 ═════════════════════════════════════════════
            // 而非「模块更新」。CNIP 之前混在 Updates 里语义不清，移到此处
            item("section_rules") {
                PreferenceSection(title = stringResource(R.string.tools_section_rules_title)) {
                    ArrowPreference(
                        title = stringResource(R.string.tools_section_apps_title),
                        summary = stringResource(R.string.tools_row_manage_app_rules),
                        startAction = { PreferenceIcon(Icons.Filled.Apps) },
                        onClick = onOpenApps
                    )
                    PreferenceDivider()
                    ArrowPreference(
                        title = stringResource(R.string.tools_section_network_control_title),
                        summary = stringResource(R.string.tools_row_network_control_subtitle),
                        startAction = { PreferenceIcon(Icons.Filled.Router) },
                        onClick = onOpenNetworkControl
                    )
                    PreferenceDivider()
                    ArrowPreference(
                        startAction = { PreferenceIcon(Icons.Filled.Dns) },
                    )
                    if (BuildConfig.FLAVOR != "bfr") {
                        PreferenceDivider()
                        ArrowPreference(
                            title = stringResource(R.string.tools_update_target_cnip),
                            summary = stringResource(R.string.tools_row_cnip_subtitle),
                            startAction = { PreferenceIcon(Icons.Filled.Public) },
                            onClick = onOpenUpdateCnip
                        )
                    }
                }
            }

            // ═══ 诊断 ═══════════════════════════════════════════════════
            // 日志查看 + 监控守护 — 系统状态观测与守护策略
            item("section_diagnose") {
                PreferenceSection(title = stringResource(R.string.tools_section_diagnose_title)) {
                    ArrowPreference(
                        title = stringResource(R.string.tools_section_logs_title),
                        summary = stringResource(R.string.tools_row_open_unified_logs),
                        startAction = { PreferenceIcon(Icons.AutoMirrored.Filled.Article) },
                        onClick = onOpenLogs
                    )
                    PreferenceDivider()
                    ArrowPreference(
                        title = stringResource(R.string.monitor_title),
                        summary = stringResource(R.string.monitor_subtitle),
                        startAction = { PreferenceIcon(Icons.Filled.Shield) },
                        onClick = onOpenMonitorSettings
                    )
                }
            }

            // ═══ 更新 ═══════════════════════════════════════════════════
            // 模块产物级更新：核心 / 订阅 / WebUI
            // CNIP 数据集已移至「规则与数据」（自带"立即更新"顶部 action）
            item("section_update") {
                PreferenceSection(title = stringResource(R.string.tools_section_update_title)) {
                    ArrowPreference(
                        title = stringResource(R.string.tools_update_target_core),
                        summary = stringResource(R.string.tools_update_subtitle_core),
                        startAction = { PreferenceIcon(Icons.Filled.Storage) },
                        endActions = {
                            Text(
                                text = selectedCores.joinToString(),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        },
                        onClick = {
                            if (BuildConfig.FLAVOR != "bfr") showUpdateCoreSheet = true
                        }
                    )
                    PreferenceDivider()
                    ArrowPreference(
                        title = stringResource(R.string.tools_update_target_subscription),
                        summary = stringResource(R.string.tools_update_subtitle_subscription),
                        startAction = { PreferenceIcon(Icons.Filled.Link) },
                        endActions = {
                            ActionText(
                                text = stringResource(R.string.action_update),
                                onClick = {
                                    scope.launch {
                                        toastMessage = context.getString(
                                            R.string.tools_update_started,
                                            context.getString(R.string.tools_update_target_subscription)
                                        )
                                        val success = BoxApi.updateSubs()
                                        toastMessage = if (success) {
                                            context.getString(R.string.tools_update_completed_subscription)
                                        } else {
                                            context.getString(R.string.tools_update_failed)
                                        }
                                    }
                                }
                            )
                        },
                        onClick = onOpenUpdateSubscription
                    )
                    PreferenceDivider()
                    ArrowPreference(
                        title = stringResource(R.string.tools_update_target_webui),
                        summary = stringResource(R.string.tools_update_subtitle_webui),
                        startAction = { PreferenceIcon(Icons.Filled.Language) },
                        endActions = {
                            ActionText(
                                text = stringResource(R.string.action_update),
                                onClick = {
                                    scope.launch {
                                        toastMessage = context.getString(
                                            R.string.tools_update_started,
                                            context.getString(R.string.tools_update_target_webui)
                                        )
                                        val success = BoxApi.updateWebUI()
                                        toastMessage = if (success) {
                                            context.getString(R.string.tools_update_completed_webui)
                                        } else {
                                            context.getString(R.string.tools_update_failed)
                                        }
                                    }
                                }
                            )
                        },
                        onClick = {}
                    )
                }
            }

            // 底部留白：HyperXPage 内部 LazyColumn 仅含 systemBars 插入边距，
            // 浮动 NavBar 的额外占位高度（LocalFloatingNavBarSpaceDp）需手动补
            item("trailing_space") {
                Spacer(
                    modifier = Modifier.height(
                        com.box.app.ui.components.LocalFloatingNavBarSpaceDp.current + 8.dp
                    )
                )
            }
        }
}

// ─── 更新状态 ─────────────────────────────────────────────────────────────────

private enum class CoreUpdateStatus { Updating, Success, Failed }

// ─── 核心选项配置 ─────────────────────────────────────────────────────────────

private data class CoreDisplayOption(
    val key: String,         // 选择标识（如 "mihomo"）
    val displayName: String, // 显示名（如 "Mihomo"）
    val subtitleRes: Int,    // 描述字符串资源 ID
    val iconUrl: String,     // 核心项目图标 URL
    val binName: String      // 用于版本检测的二进制名
)

// 核心项目图标（jsdelivr CDN / GitHub）
private const val ICON_MIHOMO = "https://cdn.jsdelivr.net/gh/MetaCubeX/mihomo@Alpha/docs/logo.png"
private const val ICON_SINGBOX = "https://cdn.jsdelivr.net/gh/SagerNet/sing-box-for-android@main/app/src/main/ic_launcher-playstore.png"
private const val ICON_XRAY = "https://avatars.githubusercontent.com/u/71564206?s=128&v=4"
private const val ICON_V2FLY = "https://cdn.jsdelivr.net/gh/v2fly/v2fly-github-io@master/docs/.vuepress/public/readme-logo.png"
private const val ICON_HYSTERIA = "https://cdn.jsdelivr.net/gh/apernet/hysteria@master/media-kit/png/symbol%201@2x.png"

private val CORE_OPTIONS = listOf(
    CoreDisplayOption("mihomo", "Mihomo", R.string.tools_update_core_mihomo_subtitle, ICON_MIHOMO, "mihomo"),
    CoreDisplayOption("mihomo_smart", "Mihomo Smart", R.string.tools_update_core_mihomo_smart_subtitle, ICON_MIHOMO, "mihomo"),
    CoreDisplayOption("sing-box", "Sing-Box", R.string.tools_update_core_sing_box_subtitle, ICON_SINGBOX, "sing-box"),
    CoreDisplayOption("xray", "Xray", R.string.tools_update_core_xray_subtitle, ICON_XRAY, "xray"),
    CoreDisplayOption("v2ray", "V2Ray", R.string.tools_update_core_v2ray_subtitle, ICON_V2FLY, "v2fly"),
    CoreDisplayOption("hysteria", "Hysteria", R.string.tools_update_core_hysteria_subtitle, ICON_HYSTERIA, "hysteria"),
)

/**
 * 从 shell 版本输出中提取干净的版本号。
 * 三级匹配策略，避免误匹配时间戳片段（如 55.823415617）。
 */
private fun parseCleanVersion(raw: String): String? {
    val s = raw.trim()
    if (s.isBlank()) return null
    // 1. 结构化标签：version="x.x.x" / Version: x.x.x / version x.x.x
    Regex("""(?i)version[=:\s]+"?v?(\d+\.\d+(?:\.\d+)?(?:-[\w.]+)?)"?""")
        .find(s)?.let { return "v${it.groupValues[1]}" }
    // 2. 显式 v 前缀：v1.18.1（排除 go1.22 等无 v 前缀的）
    Regex("""\bv(\d+\.\d+(?:\.\d+)?(?:-[\w.]+)?)""")
        .find(s)?.let { return "v${it.groupValues[1]}" }
    // 3. 已知核心名 + 版本号：Xray 26.3.27 / V2Ray 5.22.0
    Regex("""(?i)(?:mihomo|sing-box|xray|v2ray|v2fly|hysteria)\s+(?:meta\s+)?v?(\d+\.\d+\.\d+(?:-[\w.]+)?)""")
        .find(s)?.let { return "v${it.groupValues[1]}" }
    return null
}

// ─── 条目图标 ───────────────────────────────────────────────────────────────

@Composable
private fun PreferenceIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MiuixTheme.colorScheme.onBackground,
        modifier = Modifier.padding(end = 16.dp)
    )
}

// ─── 分区容器 ───────────────────────────────────────────────────────────────

@Composable
private fun PreferenceSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(text = title)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 6.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

// ─── 条目间分隔线 ───────────────────────────────────────────────────────────

@Composable
private fun PreferenceDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(0.5.dp)
            .background(MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.08f))
    )
}

// ─── 行内操作文字（无背景，primary 色） ─────────────────────────────────────

@Composable
private fun ActionText(
    text: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Text(
        text = text,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(end = 6.dp)
    )
}
