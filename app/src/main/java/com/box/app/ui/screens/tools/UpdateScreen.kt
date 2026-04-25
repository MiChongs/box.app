package com.box.app.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.box.app.data.backend.BoxApi
import com.box.app.ui.components.ErrorToast
import com.box.app.ui.components.contentPaddingWithNavBars
import dev.lackluster.hyperx.ui.component.Hint
import dev.lackluster.hyperx.ui.component.ImageIcon
import dev.lackluster.hyperx.ui.preference.EditTextPreference
import dev.lackluster.hyperx.ui.preference.ValuePosition
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.preference.SwitchPreference
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

@Composable
fun ToolsUpdateCnipScreen(
    onNavVisibilityChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scrollBehavior = MiuixScrollBehavior()

    // ── 状态 ──
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    var bypassCnIp by remember { mutableStateOf(false) }
    var bypassCnIpV4 by remember { mutableStateOf(false) }
    var bypassCnIpV6 by remember { mutableStateOf(false) }

    var cnIpFile by remember { mutableStateOf("") }
    var cnIpv6File by remember { mutableStateOf("") }
    var cnIpUrl by remember { mutableStateOf("") }
    var cnIpv6Url by remember { mutableStateOf("") }

    // ── 解析工具 ──
    fun parseSetting(settings: String, key: String): String? {
        val regex = Regex("^${key}=\"?(.*?)\"?$", setOf(RegexOption.MULTILINE))
        return regex.find(settings)?.groupValues?.getOrNull(1)
    }

    // ── 写入辅助 ──
    fun writeBool(key: String, value: Boolean) {
        scope.launch { runCatching { BoxApi.updateBooleanSetting(key, value) } }
    }

    fun writeString(key: String, value: String) {
        scope.launch { runCatching { BoxApi.updateSetting(key, value.trim()) } }
    }

    // ── 错误提示 ──
    ErrorToast(
        message = error,
        onConsumed = { error = null }
    )

    // ── 加载设置 ──
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

    // ── 滚动隐藏导航栏 ──
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

    // ── 页面布局 ──
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.tools_update_target_cnip),
                subtitle = stringResource(R.string.tools_update_subtitle_cnip),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurface
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = contentPaddingWithNavBars(
                    start = 12.dp, end = 12.dp,
                    top = innerPadding.calculateTopPadding(),
                    extraBottom = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ═══ 提示 ═══
                item(key = "hint_cnip") {
                    Hint(text = stringResource(R.string.tools_cnip_ipset_hint))
                }

                // ═══ General ═══
                item(key = "section_general") {
                    SmallTitle(text = stringResource(R.string.tools_cnip_section_general_title))
                }
                item(key = "card_general") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
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
                }

                // ═══ Files ═══
                item(key = "section_files") {
                    SmallTitle(text = stringResource(R.string.tools_cnip_section_files_title))
                }
                item(key = "card_files") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            EditTextPreference(
                                title = stringResource(R.string.tools_cnip_ipv4_file_title),
                                text = cnIpFile,
                                onTextChange = {
                                    cnIpFile = it
                                    writeString("cn_ip_file", it)
                                },
                                icon = ImageIcon(Icons.Filled.Description),
                                summary = "cn_ip_file",
                                valuePosition = ValuePosition.Summary,
                                dialogTitle = stringResource(R.string.tools_cnip_ipv4_file_title),
                                dialogHint = stringResource(R.string.tools_cnip_placeholder_path),
                                enabled = bypassCnIp
                            )
                            CnipDivider()
                            EditTextPreference(
                                title = stringResource(R.string.tools_cnip_ipv6_file_title),
                                text = cnIpv6File,
                                onTextChange = {
                                    cnIpv6File = it
                                    writeString("cn_ipv6_file", it)
                                },
                                icon = ImageIcon(Icons.Filled.Description),
                                summary = "cn_ipv6_file",
                                valuePosition = ValuePosition.Summary,
                                dialogTitle = stringResource(R.string.tools_cnip_ipv6_file_title),
                                dialogHint = stringResource(R.string.tools_cnip_placeholder_path),
                                enabled = bypassCnIp
                            )
                        }
                    }
                }

                // ═══ Sources ═══
                item(key = "section_sources") {
                    SmallTitle(text = stringResource(R.string.tools_cnip_section_sources_title))
                }
                item(key = "card_sources") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            EditTextPreference(
                                title = stringResource(R.string.tools_cnip_ipv4_url_title),
                                text = cnIpUrl,
                                onTextChange = {
                                    cnIpUrl = it
                                    writeString("cn_ip_url", it)
                                },
                                icon = ImageIcon(Icons.Filled.Link),
                                summary = "cn_ip_url",
                                valuePosition = ValuePosition.Summary,
                                dialogTitle = stringResource(R.string.tools_cnip_ipv4_url_title),
                                dialogHint = stringResource(R.string.tools_cnip_placeholder_url),
                                enabled = bypassCnIp
                            )
                            CnipDivider()
                            EditTextPreference(
                                title = stringResource(R.string.tools_cnip_ipv6_url_title),
                                text = cnIpv6Url,
                                onTextChange = {
                                    cnIpv6Url = it
                                    writeString("cn_ipv6_url", it)
                                },
                                icon = ImageIcon(Icons.Filled.Link),
                                summary = "cn_ipv6_url",
                                valuePosition = ValuePosition.Summary,
                                dialogTitle = stringResource(R.string.tools_cnip_ipv6_url_title),
                                dialogHint = stringResource(R.string.tools_cnip_placeholder_url),
                                enabled = bypassCnIp
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            // ── 加载遮罩 ──
            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = MiuixTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

// ─── 分隔线 ─────────────────────────────────────────────────────────────────

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
