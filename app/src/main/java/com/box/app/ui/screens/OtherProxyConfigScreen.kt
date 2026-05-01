package com.box.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.box.app.data.backend.BoxApi
import com.box.app.ui.components.ErrorToast
import com.box.app.ui.components.contentPaddingWithNavBars
import com.box.app.utils.ThemeManager
import dev.lackluster.hyperx.ui.layout.HyperXScaffold
import dev.lackluster.hyperx.ui.preference.EditableTextPreference
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 其他代理配置（二级页面）
 *
 * 入口：SettingsScreen 「其他代理配置」
 *
 * 内容（与 settings.ini 严格对应）：
 *   - 端口             ：tproxy_port / redir_port
 *   - 代理能力         ：performance_mode（条件）/ quic / mihomo_dns_forward / proxy_tcp / proxy_udp
 *   - DNS 劫持         ：dns_hijack_tcp / dns_hijack_udp / dns_hijack_mode
 *   - 资源限制         ：cgroup_memcg + memcg_limit / cgroup_cpuset + allow_cpu / cgroup_blkio + weight
 *
 * 性能模式仅当 settings.ini 中存在 performance_mode 键时显示，否则隐藏整个 SwitchPreference。
 *
 * 全部使用 miuix 组件（SwitchPreference / WindowDropdownPreference / EditableTextPreference / Card /
 * SmallTitle / TopAppBar），完美适配深浅模式与 Monet 动态取色。
 */
@Composable
fun OtherProxyConfigScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()

    // ── 端口 ───────────────────────────────────────────────────────────
    var tproxyPort by rememberSaveable { mutableStateOf("") }
    var redirPort by rememberSaveable { mutableStateOf("") }

    // ── 代理能力 ───────────────────────────────────────────────────────
    var performanceModeSupported by rememberSaveable { mutableStateOf(false) }
    var performanceMode by rememberSaveable { mutableStateOf(false) }
    var quicEnabled by rememberSaveable { mutableStateOf(false) }
    var mihomoDnsForward by rememberSaveable { mutableStateOf(false) }
    var proxyTcp by rememberSaveable { mutableStateOf(false) }
    var proxyUdp by rememberSaveable { mutableStateOf(false) }

    // ── DNS 劫持 ───────────────────────────────────────────────────────
    var dnsHijackTcp by rememberSaveable { mutableStateOf(false) }
    var dnsHijackUdp by rememberSaveable { mutableStateOf(false) }
    val dnsHijackOptions = remember { listOf("disable", "tproxy", "redirect") }
    var dnsHijackMode by rememberSaveable { mutableStateOf("tproxy") }

    // ── 资源限制 ───────────────────────────────────────────────────────
    var memcgEnabled by rememberSaveable { mutableStateOf(false) }
    var memcgLimit by rememberSaveable { mutableStateOf("") }
    var cpusetEnabled by rememberSaveable { mutableStateOf(false) }
    var cpuList by rememberSaveable { mutableStateOf("") }
    var blkioEnabled by rememberSaveable { mutableStateOf(false) }
    var blkioWeight by rememberSaveable { mutableStateOf("") }

    var error by remember { mutableStateOf<String?>(null) }
    val saveFailedText = stringResource(R.string.base_proxy_config_save_failed)
    val invalidPortText = stringResource(R.string.other_proxy_config_invalid_port)
    val disableLabel = stringResource(R.string.other_proxy_config_dns_hijack_mode_disable)

    ErrorToast(message = error, onConsumed = { error = null })

    // ── settings.ini 解析工具 ─────────────────────────────────────────
    fun parseValue(settings: String, key: String): String? {
        val regex = Regex("^${key}=\"?(.*?)\"?$", setOf(RegexOption.MULTILINE))
        return regex.find(settings)?.groupValues?.getOrNull(1)
    }

    /** 仅检测键是否存在（区别"键存在但值为空"与"键不存在"） */
    fun keyExists(settings: String, key: String): Boolean =
        Regex("^${key}=", setOf(RegexOption.MULTILINE)).containsMatchIn(settings)

    fun parseEnableLike(settings: String, key: String): Boolean {
        val v = parseValue(settings, key)?.trim()?.lowercase().orEmpty()
        return v == "enable" || v == "true" || v == "1" || v == "on" || v == "yes"
    }

    suspend fun loadSettings() {
        val settings = runCatching { BoxApi.getSettings() }.getOrNull().orEmpty()
        if (settings.isBlank()) return

        tproxyPort = parseValue(settings, "tproxy_port").orEmpty()
        redirPort = parseValue(settings, "redir_port").orEmpty()

        performanceModeSupported = keyExists(settings, "performance_mode")
        performanceMode = parseValue(settings, "performance_mode")?.toBooleanStrictOrNull() ?: false
        quicEnabled = parseEnableLike(settings, "quic")
        mihomoDnsForward = parseEnableLike(settings, "mihomo_dns_forward")
        proxyTcp = parseValue(settings, "proxy_tcp")?.toBooleanStrictOrNull() ?: true
        proxyUdp = parseValue(settings, "proxy_udp")?.toBooleanStrictOrNull() ?: true

        dnsHijackTcp = parseValue(settings, "dns_hijack_tcp")?.toBooleanStrictOrNull() ?: true
        dnsHijackUdp = parseValue(settings, "dns_hijack_udp")?.toBooleanStrictOrNull() ?: true
        val rawMode = parseValue(settings, "dns_hijack_mode")?.trim()?.lowercase().orEmpty()
        dnsHijackMode = if (dnsHijackOptions.contains(rawMode)) rawMode else "tproxy"

        memcgEnabled = parseValue(settings, "cgroup_memcg")?.toBooleanStrictOrNull() ?: false
        memcgLimit = parseValue(settings, "memcg_limit").orEmpty()
        cpusetEnabled = parseValue(settings, "cgroup_cpuset")?.toBooleanStrictOrNull() ?: false
        cpuList = parseValue(settings, "allow_cpu").orEmpty()
        blkioEnabled = parseValue(settings, "cgroup_blkio")?.toBooleanStrictOrNull() ?: false
        blkioWeight = parseValue(settings, "weight").orEmpty()
    }

    LaunchedEffect(Unit) { loadSettings() }

    // ── 持久化 helpers ─────────────────────────────────────────────────
    fun persistString(key: String, value: String, fallback: () -> Unit = {}) {
        scope.launch {
            if (!BoxApi.updateSetting(key, value)) {
                fallback()
                error = saveFailedText
            }
        }
    }

    fun persistBool(key: String, value: Boolean, fallback: () -> Unit = {}) {
        scope.launch {
            if (!BoxApi.updateBooleanSetting(key, value)) {
                fallback()
                error = saveFailedText
            }
        }
    }

    /** quic / mihomo_dns_forward 的值是 "enable" / "disable" 字符串 */
    fun persistEnableLike(key: String, enable: Boolean, fallback: () -> Unit = {}) {
        persistString(key, if (enable) "enable" else "disable", fallback)
    }

    /** 端口校验：1-65535 整数；空字符串表示不修改 */
    fun validatePort(value: String): Boolean {
        if (value.isBlank()) return true
        val n = value.toIntOrNull() ?: return false
        return n in 1..65535
    }

    fun onPortChange(key: String, newValue: String, currentSetter: (String) -> Unit, oldValue: String) {
        currentSetter(newValue)
        if (newValue.isBlank()) return
        if (!validatePort(newValue)) {
            error = invalidPortText
            return
        }
        persistString(key, newValue) { currentSetter(oldValue) }
    }

    // ── UI ─────────────────────────────────────────────────────────────
    val blurTopBar = ThemeManager.shouldUseBlurEffects()

    HyperXScaffold(
        blurTopBar = blurTopBar,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.settings_other_proxy_title),
                largeTitle = stringResource(R.string.settings_other_proxy_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        cornerRadius = 16.dp
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        // 输入法体验优化：
        //   - imePadding()：键盘弹起时，LazyColumn 视口高度自动缩短到键盘上方，
        //     使得 BasicTextField 聚焦时的 bringIntoView 能把当前输入项滚到可见区
        //   - 不使用 imeNestedScroll()：它会让向上滑动时强制把键盘弹回（即便用户当前
        //     无意聚焦输入框），体验非常突兀；只在用户主动点击输入框时才弹出键盘
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = contentPaddingWithNavBars(
                start = 12.dp,
                end = 12.dp,
                top = innerPadding.calculateTopPadding(),
                extraBottom = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── 卡 1：端口 ───────────────────────────────────────────
            item(key = "card_ports") {
                OtherProxyCard {
                    OtherProxySectionTitle(stringResource(R.string.other_proxy_config_section_ports))
                    EditableTextPreference(
                        title = stringResource(R.string.other_proxy_config_port_tproxy),
                        text = tproxyPort,
                        hint = stringResource(R.string.other_proxy_config_port_hint),
                        onTextChange = { newValue ->
                            val old = tproxyPort
                            onPortChange("tproxy_port", newValue, { tproxyPort = it }, old)
                        }
                    )
                    OtherProxyDivider()
                    EditableTextPreference(
                        title = stringResource(R.string.other_proxy_config_port_redirect),
                        text = redirPort,
                        hint = stringResource(R.string.other_proxy_config_port_hint),
                        onTextChange = { newValue ->
                            val old = redirPort
                            onPortChange("redir_port", newValue, { redirPort = it }, old)
                        }
                    )
                }
            }

            // ── 卡 2：代理能力 ─────────────────────────────────────────
            item(key = "card_capabilities") {
                OtherProxyCard {
                    OtherProxySectionTitle(stringResource(R.string.other_proxy_config_section_capabilities))

                    // 性能模式：仅当 settings.ini 中存在该键才显示
                    if (performanceModeSupported) {
                        SwitchPreference(
                            title = stringResource(R.string.other_proxy_config_performance_mode_title),
                            summary = stringResource(R.string.other_proxy_config_performance_mode_summary),
                            checked = performanceMode,
                            onCheckedChange = {
                                performanceMode = it
                                persistBool("performance_mode", it) { performanceMode = !it }
                            }
                        )
                        OtherProxyDivider()
                    }

                    SwitchPreference(
                        title = stringResource(R.string.other_proxy_config_quic),
                        checked = quicEnabled,
                        onCheckedChange = {
                            quicEnabled = it
                            persistEnableLike("quic", it) { quicEnabled = !it }
                        }
                    )
                    OtherProxyDivider()
                    SwitchPreference(
                        title = stringResource(R.string.other_proxy_config_mihomo_dns_forward),
                        checked = mihomoDnsForward,
                        onCheckedChange = {
                            mihomoDnsForward = it
                            persistEnableLike("mihomo_dns_forward", it) { mihomoDnsForward = !it }
                        }
                    )
                    OtherProxyDivider()
                    SwitchPreference(
                        title = stringResource(R.string.other_proxy_config_proxy_tcp),
                        checked = proxyTcp,
                        onCheckedChange = {
                            proxyTcp = it
                            persistBool("proxy_tcp", it) { proxyTcp = !it }
                        }
                    )
                    OtherProxyDivider()
                    SwitchPreference(
                        title = stringResource(R.string.other_proxy_config_proxy_udp),
                        checked = proxyUdp,
                        onCheckedChange = {
                            proxyUdp = it
                            persistBool("proxy_udp", it) { proxyUdp = !it }
                        }
                    )
                }
            }

            // ── 卡 3：DNS 劫持 ───────────────────────────────────────
            item(key = "card_dns_hijack") {
                OtherProxyCard {
                    OtherProxySectionTitle(stringResource(R.string.other_proxy_config_section_dns_hijack))
                    SwitchPreference(
                        title = stringResource(R.string.other_proxy_config_dns_hijack_tcp),
                        checked = dnsHijackTcp,
                        onCheckedChange = {
                            dnsHijackTcp = it
                            persistBool("dns_hijack_tcp", it) { dnsHijackTcp = !it }
                        }
                    )
                    OtherProxyDivider()
                    SwitchPreference(
                        title = stringResource(R.string.other_proxy_config_dns_hijack_udp),
                        checked = dnsHijackUdp,
                        onCheckedChange = {
                            dnsHijackUdp = it
                            persistBool("dns_hijack_udp", it) { dnsHijackUdp = !it }
                        }
                    )
                    OtherProxyDivider()
                    val dnsModeDisplay = remember(disableLabel) {
                        listOf(disableLabel, "TPROXY", "REDIRECT")
                    }
                    WindowDropdownPreference(
                        title = stringResource(R.string.other_proxy_config_dns_hijack_mode),
                        items = dnsModeDisplay,
                        selectedIndex = dnsHijackOptions
                            .indexOfFirst { it.equals(dnsHijackMode, ignoreCase = true) }
                            .coerceAtLeast(0),
                        onSelectedIndexChange = { idx ->
                            dnsHijackOptions.getOrNull(idx)?.let { picked ->
                                val old = dnsHijackMode
                                dnsHijackMode = picked
                                persistString("dns_hijack_mode", picked) { dnsHijackMode = old }
                            }
                        }
                    )
                }
            }

            // ── 卡 4：资源限制 ───────────────────────────────────────
            item(key = "card_resource_limit") {
                OtherProxyCard {
                    OtherProxySectionTitle(stringResource(R.string.other_proxy_config_section_resource_limit))

                    SwitchPreference(
                        title = stringResource(R.string.other_proxy_config_memcg_title),
                        checked = memcgEnabled,
                        onCheckedChange = {
                            memcgEnabled = it
                            persistBool("cgroup_memcg", it) { memcgEnabled = !it }
                        }
                    )
                    EditableTextPreference(
                        title = stringResource(R.string.other_proxy_config_memcg_title),
                        text = memcgLimit,
                        hint = stringResource(R.string.other_proxy_config_memcg_hint),
                        enabled = memcgEnabled,
                        onTextChange = { newValue ->
                            val old = memcgLimit
                            memcgLimit = newValue
                            persistString("memcg_limit", newValue) { memcgLimit = old }
                        }
                    )
                    OtherProxyDivider()

                    SwitchPreference(
                        title = stringResource(R.string.other_proxy_config_cpuset_title),
                        checked = cpusetEnabled,
                        onCheckedChange = {
                            cpusetEnabled = it
                            persistBool("cgroup_cpuset", it) { cpusetEnabled = !it }
                        }
                    )
                    EditableTextPreference(
                        title = stringResource(R.string.other_proxy_config_cpuset_title),
                        text = cpuList,
                        hint = stringResource(R.string.other_proxy_config_cpuset_hint),
                        enabled = cpusetEnabled,
                        onTextChange = { newValue ->
                            val old = cpuList
                            cpuList = newValue
                            persistString("allow_cpu", newValue) { cpuList = old }
                        }
                    )
                    OtherProxyDivider()

                    SwitchPreference(
                        title = stringResource(R.string.other_proxy_config_blkio_title),
                        checked = blkioEnabled,
                        onCheckedChange = {
                            blkioEnabled = it
                            persistBool("cgroup_blkio", it) { blkioEnabled = !it }
                        }
                    )
                    EditableTextPreference(
                        title = stringResource(R.string.other_proxy_config_blkio_title),
                        text = blkioWeight,
                        hint = stringResource(R.string.other_proxy_config_blkio_hint),
                        enabled = blkioEnabled,
                        onTextChange = { newValue ->
                            val old = blkioWeight
                            blkioWeight = newValue
                            persistString("weight", newValue) { blkioWeight = old }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun OtherProxyCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 22.dp,
        insideMargin = PaddingValues(0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun OtherProxySectionTitle(text: String) {
    SmallTitle(
        text = text,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun OtherProxyDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(0.5.dp)
            .background(MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.08f))
    )
}
