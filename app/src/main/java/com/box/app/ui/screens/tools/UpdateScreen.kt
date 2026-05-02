package com.box.app.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.rounded.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.box.app.R
import com.box.app.data.backend.BoxApi
import com.box.app.ui.components.ErrorToast
import com.box.app.ui.components.LocalFloatingNavBarSpaceDp
import com.box.app.ui.components.home.HomeSemanticColors
import com.box.app.ui.components.home.homeInfoColors
import com.box.app.ui.components.home.homeSuccessColors
import com.box.app.ui.theme.AppFonts
import dev.lackluster.hyperx.ui.dialog.EditTextDialog
import dev.lackluster.hyperx.ui.layout.HyperXPage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.shapes.SmoothRoundedCornerShape
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ToolsUpdateSubscriptionScreen(
    onNavVisibilityChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    SubscriptionScreen(
        onNavVisibilityChange = onNavVisibilityChange,
        onBack = onBack
    )
}

/**
 * CNIP 中国 IP 数据库（二级页面）
 *
 * 设计要点：
 *   - HyperXPage 顶栏右上 action 提供「立即更新」按钮（更新中显示进度环）
 *   - 启用 / 数据 / 来源三大分区，每条目用 IPv4/IPv6 颜色徽章 + outlined 图标
 *   - 路径与 URL 用 IBM Plex Mono 数据字体；图标 tint = onSurfaceSecondary，
 *     完美适配深浅模式（旧版 ImageIcon 用 Image 渲染，深色下 Material 默认黑色 vector
 *     不可见 — 已改用 miuix Icon 自动跟随主题）
 *   - 点击行弹出 hyperx EditTextDialog，与项目其他设置项交互一致
 */
@Composable
fun ToolsUpdateCnipScreen(
    onNavVisibilityChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scheme = MiuixTheme.colorScheme

    // ── 状态 ────────────────────────────────────────────────────────────
    var loading by remember { mutableStateOf(true) }
    var updating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var bypassCnIp by remember { mutableStateOf(false) }
    var bypassCnIpV4 by remember { mutableStateOf(false) }
    var bypassCnIpV6 by remember { mutableStateOf(false) }

    var cnIpFile by remember { mutableStateOf("") }
    var cnIpv6File by remember { mutableStateOf("") }
    var cnIpUrl by remember { mutableStateOf("") }
    var cnIpv6Url by remember { mutableStateOf("") }

    // ── 解析 / 写入辅助 ──────────────────────────────────────────────────
    fun parseSetting(settings: String, key: String): String? {
        val regex = Regex("^${key}=\"?(.*?)\"?$", setOf(RegexOption.MULTILINE))
        return regex.find(settings)?.groupValues?.getOrNull(1)
    }

    fun writeBool(key: String, value: Boolean) {
        scope.launch { runCatching { BoxApi.updateBooleanSetting(key, value) } }
    }

    fun writeString(key: String, value: String) {
        scope.launch { runCatching { BoxApi.updateSetting(key, value.trim()) } }
    }

    ErrorToast(message = error, onConsumed = { error = null })

    // ── 加载 settings.ini ────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        loading = true
        error = null
        val settings = runCatching { BoxApi.getSettings() }.getOrNull().orEmpty()
        if (settings.isNotBlank()) {
            bypassCnIp = parseSetting(settings, "bypass_cn_ip")?.toBooleanStrictOrNull() ?: false
            bypassCnIpV4 = parseSetting(settings, "bypass_cn_ip_v4")?.toBooleanStrictOrNull() ?: false
            bypassCnIpV6 = parseSetting(settings, "bypass_cn_ip_v6")?.toBooleanStrictOrNull() ?: false
            cnIpFile = parseSetting(settings, "cn_ip_file") ?: ""
            cnIpv6File = parseSetting(settings, "cn_ipv6_file") ?: ""
            cnIpUrl = parseSetting(settings, "cn_ip_url") ?: ""
            cnIpv6Url = parseSetting(settings, "cn_ipv6_url") ?: ""
        } else {
            error = context.getString(R.string.tools_update_load_failed)
        }
        loading = false
    }

    // ── 滚动隐藏导航栏 ───────────────────────────────────────────────────
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

    // ── 顶部更新按钮回调 ─────────────────────────────────────────────────
    val updateStartedText = stringResource(
        R.string.tools_update_started,
        stringResource(R.string.tools_update_target_cnip)
    )
    val updateOkText = stringResource(R.string.tools_update_completed_cnip)
    val updateFailText = stringResource(R.string.tools_update_failed)

    fun triggerUpdate() {
        if (updating) return
        updating = true
        error = updateStartedText
        scope.launch {
            val ok = runCatching { BoxApi.updateCnipList() }.getOrDefault(false)
            error = if (ok) updateOkText else updateFailText
            updating = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HyperXPage(
            title = stringResource(R.string.tools_update_target_cnip),
            listState = listState,
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .padding(start = 21.dp)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = scheme.onSurface
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = { triggerUpdate() },
                    modifier = Modifier
                        .padding(end = 21.dp)
                        .size(40.dp)
                ) {
                    if (updating) {
                        InfiniteProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = scheme.primary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Sync,
                            contentDescription = stringResource(R.string.tools_cnip_action_update),
                            tint = scheme.onSurface
                        )
                    }
                }
            }
        ) {
            // ═══ 状态提示 ═══════════════════════════════════════════════
            item(key = "hint") {
                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    CnipStatusHint(scheme = scheme)
                }
            }

            // ═══ 启用 ═══════════════════════════════════════════════════
            item(key = "section_general_title") {
                SmallTitle(text = stringResource(R.string.tools_cnip_section_general_title))
            }
            item(key = "card_general") {
                CnipCard {
                    SwitchPreference(
                        checked = bypassCnIp,
                        onCheckedChange = {
                            bypassCnIp = it
                            writeBool("bypass_cn_ip", it)
                        },
                        title = stringResource(R.string.tools_cnip_bypass_title),
                        summary = stringResource(R.string.tools_cnip_bypass_subtitle)
                    )
                    CnipDivider()
                    SwitchPreference(
                        checked = bypassCnIpV4,
                        onCheckedChange = {
                            bypassCnIpV4 = it
                            writeBool("bypass_cn_ip_v4", it)
                        },
                        title = stringResource(R.string.tools_cnip_bypass_ipv4_title),
                        summary = stringResource(R.string.tools_cnip_bypass_ipv4_subtitle),
                        enabled = bypassCnIp
                    )
                    CnipDivider()
                    SwitchPreference(
                        checked = bypassCnIpV6,
                        onCheckedChange = {
                            bypassCnIpV6 = it
                            writeBool("bypass_cn_ip_v6", it)
                        },
                        title = stringResource(R.string.tools_cnip_bypass_ipv6_title),
                        summary = stringResource(R.string.tools_cnip_bypass_ipv6_subtitle),
                        enabled = bypassCnIp
                    )
                }
            }

            // ═══ 本地数据文件 ═══════════════════════════════════════════
            item(key = "section_files_title") {
                SmallTitle(text = stringResource(R.string.tools_cnip_section_files_title))
            }
            item(key = "card_files") {
                CnipCard {
                    CnipPathRow(
                        isV6 = false,
                        title = stringResource(R.string.tools_cnip_ipv4_file_title),
                        value = cnIpFile,
                        kind = CnipRowKind.File,
                        dialogHint = stringResource(R.string.tools_cnip_placeholder_path),
                        onValueChange = {
                            cnIpFile = it
                            writeString("cn_ip_file", it)
                        },
                        enabled = bypassCnIp,
                        scheme = scheme
                    )
                    CnipDivider()
                    CnipPathRow(
                        isV6 = true,
                        title = stringResource(R.string.tools_cnip_ipv6_file_title),
                        value = cnIpv6File,
                        kind = CnipRowKind.File,
                        dialogHint = stringResource(R.string.tools_cnip_placeholder_path),
                        onValueChange = {
                            cnIpv6File = it
                            writeString("cn_ipv6_file", it)
                        },
                        enabled = bypassCnIp,
                        scheme = scheme
                    )
                }
            }

            // ═══ 数据来源 URL ═══════════════════════════════════════════
            item(key = "section_sources_title") {
                SmallTitle(text = stringResource(R.string.tools_cnip_section_sources_title))
            }
            item(key = "card_sources") {
                CnipCard {
                    CnipPathRow(
                        isV6 = false,
                        title = stringResource(R.string.tools_cnip_ipv4_url_title),
                        value = cnIpUrl,
                        kind = CnipRowKind.Url,
                        dialogHint = stringResource(R.string.tools_cnip_placeholder_url),
                        onValueChange = {
                            cnIpUrl = it
                            writeString("cn_ip_url", it)
                        },
                        enabled = bypassCnIp,
                        scheme = scheme
                    )
                    CnipDivider()
                    CnipPathRow(
                        isV6 = true,
                        title = stringResource(R.string.tools_cnip_ipv6_url_title),
                        value = cnIpv6Url,
                        kind = CnipRowKind.Url,
                        dialogHint = stringResource(R.string.tools_cnip_placeholder_url),
                        onValueChange = {
                            cnIpv6Url = it
                            writeString("cn_ipv6_url", it)
                        },
                        enabled = bypassCnIp,
                        scheme = scheme
                    )
                }
            }

            item(key = "trailing_space") {
                Spacer(Modifier.height(LocalFloatingNavBarSpaceDp.current + 12.dp))
            }
        }

        // ── 加载遮罩 ────────────────────────────────────────────────────
        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(scheme.surface.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                InfiniteProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = scheme.primary
                )
            }
        }
    }
}

// ─── 协议徽章（语义色 — Monet 启用时跟随主题派生，关闭时退化为绿/蓝） ───────

@Composable
private fun ProtocolBadge(
    isV6: Boolean,
    colors: HomeSemanticColors
) {
    Box(
        modifier = Modifier
            .clip(SmoothRoundedCornerShape(6.dp))
            .background(colors.container)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = if (isV6) "v6" else "v4",
            color = colors.onContainer,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            fontFamily = AppFonts.dataFamily
        )
    }
}

// ─── 状态 Hint 卡片（语义 info 色，自动跟随 Monet） ─────────────────────────

@Composable
private fun CnipStatusHint(
    scheme: top.yukonga.miuix.kmp.theme.Colors
) {
    val info = homeInfoColors()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SmoothRoundedCornerShape(16.dp))
            .background(info.container)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = null,
            tint = info.accent,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = stringResource(R.string.tools_cnip_ipset_hint),
            style = MiuixTheme.textStyles.body2,
            color = scheme.onSurface.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f)
        )
    }
}

// ─── 卡片容器（圆角 + onSurface tint） ──────────────────────────────────────

@Composable
private fun CnipCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        cornerRadius = 18.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

// ─── 行类型 ──────────────────────────────────────────────────────────────

private enum class CnipRowKind { File, Url }

// ─── 文件 / URL 行（点击弹 EditTextDialog） ─────────────────────────────────

@Composable
private fun CnipPathRow(
    isV6: Boolean,
    title: String,
    value: String,
    kind: CnipRowKind,
    dialogHint: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    scheme: top.yukonga.miuix.kmp.theme.Colors
) {
    var showDialog by remember { mutableStateOf(false) }
    val icon = when (kind) {
        CnipRowKind.File -> Icons.AutoMirrored.Outlined.InsertDriveFile
        CnipRowKind.Url -> Icons.Outlined.Link
    }
    // IPv4 / IPv6 各取不同语义色：v4 → success (Monet primary), v6 → info (Monet primary lerp blue)
    val protocolColors = if (isV6) homeInfoColors() else homeSuccessColors()

    BasicComponent(
        title = title,
        // summary 显示当前真实值；空值时退化为 placeholder
        summary = value.takeIf { it.isNotBlank() } ?: dialogHint,
        enabled = enabled,
        startAction = {
            Row(
                modifier = Modifier.padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProtocolBadge(isV6 = isV6, colors = protocolColors)
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) scheme.onSurfaceSecondary
                    else scheme.onSurfaceSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        onClick = if (enabled) {
            { showDialog = true }
        } else null
    )

    EditTextDialog(
        visible = showDialog,
        title = title,
        initialText = value,
        hint = dialogHint,
        onConfirm = { newText -> onValueChange(newText) },
        onDismissRequest = { showDialog = false }
    )
}

// ─── 分隔线 ─────────────────────────────────────────────────────────────

@Composable
private fun CnipDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(0.5.dp)
            .background(MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.08f))
    )
}
