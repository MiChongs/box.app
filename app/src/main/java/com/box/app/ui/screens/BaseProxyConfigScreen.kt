package com.box.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.box.app.BuildConfig
import com.box.app.R
import com.box.app.data.backend.BoxApi
import com.box.app.data.backend.ShellExecutor
import com.box.app.ui.components.ErrorToast
import com.box.app.ui.components.contentPaddingWithNavBars
import com.box.app.utils.ThemeManager
import dev.lackluster.hyperx.ui.layout.HyperXScaffold
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
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
 * 基础代理配置（二级页面）
 *
 * 入口：HomeScreen 状态卡片在「非运行」状态下点击
 * 内容：核心选择 / 运行模式 / IPv6 / 自动覆写 / 配置文件选择
 *
 * 全部使用 miuix 组件（Scaffold + SmallTopAppBar + Card + WindowDropdownPreference
 * + SwitchPreference + BasicComponent），完美适配深浅模式与 Monet 动态取色
 */
@Composable
fun BaseProxyConfigScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val scheme = MiuixTheme.colorScheme

    // ── Flavor-aware 设置 key（与 SubscriptionScreen 保持一致） ─────────────
    val provideConfigKey = remember {
        if (BuildConfig.FLAVOR == "bfr") "name_provide_clash_config" else "name_provide_mihomo_config"
    }

    // ── 选项常量 ───────────────────────────────────────────────────────────
    val coreOptions = remember {
        if (BuildConfig.FLAVOR == "bfr") {
            // bfr 渠道：clash 衍生选项使用 bin_name + xclash_option 双键存储
            listOf("clash-mihomo", "clash-premium", "sing-box", "xray", "v2fly", "hysteria")
        } else {
            listOf("mihomo", "sing-box", "xray", "v2fly", "hysteria")
        }
    }
    val modeOptions = remember { listOf("tun", "tproxy", "redirect", "mixed", "enhance") }

    // ── 屏幕状态 ───────────────────────────────────────────────────────────
    var coreText by rememberSaveable { mutableStateOf("") }
    var modeText by rememberSaveable { mutableStateOf("") }
    var ipv6Enabled by rememberSaveable { mutableStateOf(false) }
    var autoOverwrite by rememberSaveable { mutableStateOf(true) }
    var configFiles by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedConfig by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val saveFailedText = stringResource(R.string.base_proxy_config_save_failed)

    ErrorToast(message = error, onConsumed = { error = null })

    // ── 解析 / 加载 settings.ini ───────────────────────────────────────────
    fun parseSetting(settings: String, key: String): String? {
        val regex = Regex("^${key}=\"?(.*?)\"?$", setOf(RegexOption.MULTILINE))
        return regex.find(settings)?.groupValues?.getOrNull(1)
    }

    fun coreDisplayName(binName: String?, xclashOption: String?): String {
        val bin = binName?.trim().orEmpty()
        if (bin.isBlank()) return ""
        if (BuildConfig.FLAVOR == "bfr" && bin == "clash") {
            val opt = xclashOption?.trim().orEmpty()
            if (opt.isNotBlank()) return "clash-$opt"
        }
        return bin
    }

    /**
     * 当前核心 → 该核心专属的配置目录（位于 /data/adb/box/<dir>）
     * - clash 衍生（bfr 渠道）统一落在 clash/
     * - 其它核心目录与 bin_name 同名
     */
    fun coreConfigDir(core: String): String? {
        val c = core.lowercase().trim()
        return when {
            c.isBlank() -> null
            c == "mihomo" -> "mihomo"
            c == "sing-box" -> "sing-box"
            c == "xray" -> "xray"
            c == "v2fly" || c == "v2ray" -> "v2fly"
            c == "hysteria" -> "hysteria"
            c.startsWith("clash") -> "clash"
            else -> c
        }
    }

    /** 列出当前核心目录下的配置文件，按扩展名过滤（mihomo/clash 偏 yaml；sing-box/xray/v2fly 偏 json） */
    suspend fun loadConfigFilesForCore(core: String): List<String> {
        val dir = coreConfigDir(core) ?: return emptyList()
        val cmd = "ls -1 /data/adb/box/$dir 2>/dev/null"
        val result = ShellExecutor.execute(cmd)
        return result.stdout.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { it.endsWith(".yaml", true) || it.endsWith(".yml", true) || it.endsWith(".json", true) }
            .toList()
    }

    /** 仅加载 settings.ini，不动配置文件列表（列表由 coreText 变化驱动） */
    suspend fun loadSettings() {
        val settings = runCatching { BoxApi.getSettings() }.getOrNull().orEmpty()
        if (settings.isBlank()) return
        val bin = parseSetting(settings, "bin_name")
        val opt = parseSetting(settings, "xclash_option")
        coreText = coreDisplayName(bin, opt)
        modeText = parseSetting(settings, "network_mode").orEmpty()
        ipv6Enabled = parseSetting(settings, "ipv6")?.toBooleanStrictOrNull() ?: false
        // 自动覆写：项目里若不存在该 key 默认 true（图中默认开启）
        autoOverwrite = parseSetting(settings, "auto_overwrite_config")?.toBooleanStrictOrNull() ?: true

        val cfgLine = settings.lineSequence().map { it.trim() }
            .firstOrNull { it.startsWith("$provideConfigKey=") }
        if (cfgLine != null) {
            val raw = cfgLine.substringAfter("=").trim()
            selectedConfig = BoxApi.parseBashArray(raw).firstOrNull().orEmpty()
        }
    }

    LaunchedEffect(Unit) { loadSettings() }

    // 配置文件列表跟随当前核心：核心变更立刻重新扫描对应目录
    LaunchedEffect(coreText) {
        if (coreText.isBlank()) {
            configFiles = emptyList()
            return@LaunchedEffect
        }
        configFiles = loadConfigFilesForCore(coreText)
    }

    // ── 持久化 helpers ─────────────────────────────────────────────────────
    fun persistCore(displayName: String) {
        coreText = displayName
        scope.launch {
            val ok = if (BuildConfig.FLAVOR == "bfr" && displayName.startsWith("clash-")) {
                val variant = displayName.removePrefix("clash-")
                BoxApi.updateSetting("bin_name", "clash") &&
                    BoxApi.updateSetting("xclash_option", variant)
            } else {
                BoxApi.updateSetting("bin_name", displayName)
            }
            if (!ok) error = saveFailedText
        }
    }

    fun persistMode(value: String) {
        modeText = value
        scope.launch {
            if (!BoxApi.updateSetting("network_mode", value)) error = saveFailedText
        }
    }

    fun persistIpv6(value: Boolean) {
        ipv6Enabled = value
        scope.launch {
            if (!BoxApi.updateBooleanSetting("ipv6", value)) error = saveFailedText
        }
    }

    fun persistAutoOverwrite(value: Boolean) {
        autoOverwrite = value
        scope.launch {
            if (!BoxApi.updateBooleanSetting("auto_overwrite_config", value)) error = saveFailedText
        }
    }

    fun persistConfigSelection(file: String) {
        if (file == selectedConfig) return
        selectedConfig = file
        scope.launch {
            if (!BoxApi.updateArraySetting(provideConfigKey, listOf(file))) error = saveFailedText
        }
    }

    // ── UI ─────────────────────────────────────────────────────────────────
    // TopAppBar 模糊跟随全局「磨砂效果」设置项；关闭时纯透明
    val blurTopBar = ThemeManager.shouldUseBlurEffects()

    HyperXScaffold(
        blurTopBar = blurTopBar,
        topBar = {
            // 大标题 TopAppBar：滚动时大标题渐隐，与小标题平滑切换
            TopAppBar(
                title = stringResource(R.string.base_proxy_config_title),
                largeTitle = stringResource(R.string.base_proxy_config_title),
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── 卡 1：核心 / 模式 / IPv6 / 自动覆写 ────────────────────────
            item(key = "card_proxy") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 22.dp,
                    insideMargin = PaddingValues(0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        WindowDropdownPreference(
                            title = stringResource(R.string.base_proxy_config_core_title),
                            items = coreOptions,
                            selectedIndex = coreOptions
                                .indexOfFirst { it.equals(coreText, ignoreCase = true) }
                                .coerceAtLeast(0),
                            onSelectedIndexChange = { idx ->
                                coreOptions.getOrNull(idx)?.let { persistCore(it) }
                            }
                        )
                        BaseProxyDivider()
                        WindowDropdownPreference(
                            title = stringResource(R.string.base_proxy_config_mode_title),
                            items = modeOptions.map { it.uppercase() },
                            selectedIndex = modeOptions
                                .indexOfFirst { it.equals(modeText, ignoreCase = true) }
                                .coerceAtLeast(0),
                            onSelectedIndexChange = { idx ->
                                modeOptions.getOrNull(idx)?.let { persistMode(it) }
                            }
                        )
                        BaseProxyDivider()
                        SwitchPreference(
                            title = stringResource(R.string.base_proxy_config_ipv6_title),
                            checked = ipv6Enabled,
                            onCheckedChange = { persistIpv6(it) }
                        )
                        BaseProxyDivider()
                        SwitchPreference(
                            title = stringResource(R.string.base_proxy_config_auto_overwrite_title),
                            summary = stringResource(R.string.base_proxy_config_auto_overwrite_summary),
                            checked = autoOverwrite,
                            onCheckedChange = { persistAutoOverwrite(it) }
                        )
                    }
                }
            }

            // ── 卡 2：配置文件选择 ─────────────────────────────────────────
            item(key = "card_files") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 22.dp,
                    insideMargin = PaddingValues(0.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SmallTitle(
                            text = stringResource(R.string.base_proxy_config_section_files),
                            insideMargin = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                        )

                        if (configFiles.isEmpty()) {
                            BasicComponent(
                                title = stringResource(R.string.base_proxy_config_no_files),
                                titleColor = top.yukonga.miuix.kmp.basic.BasicComponentDefaults.titleColor(
                                    color = scheme.onSurfaceSecondary
                                )
                            )
                        } else {
                            configFiles.forEachIndexed { idx, file ->
                                BaseProxyConfigFileRow(
                                    name = file,
                                    selected = file == selectedConfig,
                                    onClick = { persistConfigSelection(file) }
                                )
                                if (idx != configFiles.lastIndex) BaseProxyDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 配置文件单行：左侧文件名，选中时右侧显示蓝色 ✓
 */
@Composable
private fun BaseProxyConfigFileRow(
    name: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = name,
            style = MiuixTheme.textStyles.body1,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 12.dp)
        )
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun BaseProxyDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(0.5.dp)
            .background(MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.08f))
    )
}
