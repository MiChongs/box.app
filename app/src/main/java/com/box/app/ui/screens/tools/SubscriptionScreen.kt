package com.box.app.ui.screens.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.box.app.BuildConfig
import com.box.app.R
import com.box.app.data.backend.BoxApi
import com.box.app.ui.components.ErrorToast
import com.box.app.ui.components.LocalFloatingNavBarSpaceDp
import com.box.app.ui.components.LocalSystemNavBarInsetDp
import com.box.app.ui.components.contentPaddingWithNavBars
import com.box.app.ui.miuix.HyperBottomSheet
import com.box.app.ui.miuix.HyperButton
import com.box.app.ui.miuix.HyperIconButton
import com.box.app.ui.miuix.HyperTextField
import com.box.app.ui.miuix.HyperTextButton
import dev.lackluster.hyperx.ui.component.ImageIcon
import dev.lackluster.hyperx.ui.preference.EditTextPreference
import dev.lackluster.hyperx.ui.preference.TextPreference
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SubscriptionScreen(
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

    // 布尔设置（auto-save）
    var renew by remember { mutableStateOf(false) }
    var updateSubscription by remember { mutableStateOf(false) }
    var runCrontab by remember { mutableStateOf(false) }

    // 非布尔设置（FAB save）
    var intervaUpdate by remember { mutableStateOf("") }
    var singboxUrl by remember { mutableStateOf("") }
    var mihomoProvidePath by remember { mutableStateOf("") }
    var mihomoUrls by remember { mutableStateOf(listOf<String>()) }
    var mihomoConfigs by remember { mutableStateOf(listOf<String>()) }

    // dirty tracking（仅非布尔）
    var initialIntervaUpdate by remember { mutableStateOf("") }
    var initialSingboxUrl by remember { mutableStateOf("") }
    var initialMihomoProvidePath by remember { mutableStateOf("") }
    var initialMihomoUrls by remember { mutableStateOf(listOf<String>()) }
    var initialMihomoConfigs by remember { mutableStateOf(listOf<String>()) }

    val isDirty by remember {
        derivedStateOf {
            val urls = mihomoUrls.map { it.trim() }.filter { it.isNotEmpty() }
            val cfgs = mihomoConfigs.map { it.trim() }.filter { it.isNotEmpty() }
            val initUrls = initialMihomoUrls.map { it.trim() }.filter { it.isNotEmpty() }
            val initCfgs = initialMihomoConfigs.map { it.trim() }.filter { it.isNotEmpty() }
            intervaUpdate.trim() != initialIntervaUpdate.trim() ||
                singboxUrl.trim() != initialSingboxUrl.trim() ||
                mihomoProvidePath.trim() != initialMihomoProvidePath.trim() ||
                urls != initUrls ||
                cfgs != initCfgs
        }
    }

    // BottomSheet 状态
    var showUrlSheet by remember { mutableStateOf(false) }
    var showConfigSheet by remember { mutableStateOf(false) }

    // Flavor-aware keys
    val subscriptionUrlKey = remember {
        if (BuildConfig.FLAVOR == "bfr") "subscription_url_clash" else "subscription_url_mihomo"
    }
    val provideConfigKey = remember {
        if (BuildConfig.FLAVOR == "bfr") "name_provide_clash_config" else "name_provide_mihomo_config"
    }
    val providePathKey = remember {
        if (BuildConfig.FLAVOR == "bfr") "clash_provide_path" else "mihomo_provide_path"
    }

    // ── 解析工具 ──
    fun parseSetting(settings: String, key: String): String? {
        val regex = Regex("^${key}=\"?(.*?)\"?$", setOf(RegexOption.MULTILINE))
        return regex.find(settings)?.groupValues?.getOrNull(1)
    }

    // ── 写入辅助 ──
    fun writeBool(key: String, value: Boolean) {
        scope.launch { runCatching { BoxApi.updateBooleanSetting(key, value) } }
    }

    fun saveNonBooleans() {
        scope.launch {
            error = null
            val urls = mihomoUrls.map { it.trim() }.filter { it.isNotEmpty() }
            val cfgs = mihomoConfigs.map { it.trim() }.filter { it.isNotEmpty() }

            val ok = listOf(
                BoxApi.updateSetting("interva_update", intervaUpdate.trim()),
                BoxApi.updateArraySetting(subscriptionUrlKey, urls),
                BoxApi.updateArraySetting(provideConfigKey, cfgs),
                BoxApi.updateSetting(providePathKey, mihomoProvidePath.trim()),
                BoxApi.updateSetting("subscription_url_singbox", singboxUrl.trim())
            ).all { it }

            if (ok) {
                initialIntervaUpdate = intervaUpdate
                initialSingboxUrl = singboxUrl
                initialMihomoProvidePath = mihomoProvidePath
                initialMihomoUrls = mihomoUrls
                initialMihomoConfigs = mihomoConfigs
            } else {
                error = context.getString(R.string.tools_subscription_save_failed)
            }
        }
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
            renew = parseSetting(settings, "renew")?.toBooleanStrictOrNull() ?: false
            updateSubscription = parseSetting(settings, "update_subscription")?.toBooleanStrictOrNull() ?: false
            runCrontab = parseSetting(settings, "run_crontab")?.toBooleanStrictOrNull() ?: false
            intervaUpdate = parseSetting(settings, "interva_update") ?: ""

            // Bash array 解析
            val urlLine = settings.lineSequence().map { it.trim() }
                .firstOrNull { it.startsWith("${subscriptionUrlKey}=") }
            if (urlLine != null) {
                val raw = urlLine.substringAfter("=").trim()
                mihomoUrls = BoxApi.parseBashArray(raw).ifEmpty { emptyList() }
            }

            val cfgLine = settings.lineSequence().map { it.trim() }
                .firstOrNull { it.startsWith("${provideConfigKey}=") }
            if (cfgLine != null) {
                val raw = cfgLine.substringAfter("=").trim()
                mihomoConfigs = BoxApi.parseBashArray(raw).ifEmpty { emptyList() }
            }

            mihomoProvidePath = (parseSetting(settings, providePathKey) ?: "")
                .replace("\${box_dir}", "/data/adb/box")
            singboxUrl = parseSetting(settings, "subscription_url_singbox") ?: ""

            // 初始化 dirty tracking
            initialIntervaUpdate = intervaUpdate
            initialSingboxUrl = singboxUrl
            initialMihomoProvidePath = mihomoProvidePath
            initialMihomoUrls = mihomoUrls
            initialMihomoConfigs = mihomoConfigs
        } else {
            error = context.getString(R.string.tools_subscription_load_failed)
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
                title = stringResource(R.string.tools_update_target_subscription),
                subtitle = stringResource(R.string.tools_subscription_section_general_subtitle),
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
                // ═══ General ═══
                item(key = "section_general") {
                    SmallTitle(text = stringResource(R.string.tools_subscription_section_general_title))
                }
                item(key = "card_general") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SwitchPreference(
                                checked = renew,
                                onCheckedChange = {
                                    renew = it
                                    writeBool("renew", it)
                                },
                                title = stringResource(R.string.tools_subscription_renew_title),
                                summary = stringResource(R.string.tools_subscription_renew_subtitle)
                            )
                            SubscriptionDivider()
                            SwitchPreference(
                                checked = updateSubscription,
                                onCheckedChange = {
                                    updateSubscription = it
                                    writeBool("update_subscription", it)
                                },
                                title = stringResource(R.string.tools_subscription_update_subscription_title),
                                summary = stringResource(R.string.tools_subscription_update_subscription_subtitle)
                            )
                            SubscriptionDivider()
                            SwitchPreference(
                                checked = runCrontab,
                                onCheckedChange = {
                                    runCrontab = it
                                    writeBool("run_crontab", it)
                                },
                                title = stringResource(R.string.tools_subscription_run_crontab_title),
                                summary = stringResource(R.string.tools_subscription_run_crontab_subtitle)
                            )
                            SubscriptionDivider()
                            EditTextPreference(
                                title = stringResource(R.string.tools_subscription_interval_title),
                                text = intervaUpdate,
                                onTextChange = { intervaUpdate = it },
                                icon = ImageIcon(Icons.Filled.Tune),
                                summary = stringResource(R.string.tools_subscription_key_interva_update_subtitle),
                                valuePosition = ValuePosition.Value,
                                dialogTitle = stringResource(R.string.tools_subscription_interval_title),
                                dialogHint = stringResource(R.string.tools_subscription_interval_placeholder)
                            )
                        }
                    }
                }

                // ═══ Mihomo URLs ═══
                item(key = "section_urls") {
                    SmallTitle(text = stringResource(R.string.tools_subscription_mihomo_urls_title))
                }
                item(key = "card_urls") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val displayUrls = mihomoUrls.filter { it.isNotBlank() }
                            if (displayUrls.isEmpty()) {
                                TextPreference(
                                    title = stringResource(R.string.tools_subscription_hint_enter_url),
                                    summary = stringResource(R.string.tools_subscription_key_subscription_url_mihomo_subtitle),
                                    icon = ImageIcon(Icons.Filled.Link),
                                    onClick = { showUrlSheet = true },
                                    enabled = updateSubscription
                                )
                            } else {
                                displayUrls.forEachIndexed { idx, url ->
                                    if (idx > 0) SubscriptionDivider()
                                    TextPreference(
                                        title = url,
                                        icon = ImageIcon(Icons.Filled.Link),
                                        onClick = { showUrlSheet = true },
                                        enabled = updateSubscription
                                    )
                                }
                            }
                        }
                    }
                }

                // ═══ Mihomo Configs ═══
                item(key = "section_configs") {
                    SmallTitle(text = stringResource(R.string.tools_subscription_mihomo_configs_title))
                }
                item(key = "card_configs") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val displayConfigs = mihomoConfigs.filter { it.isNotBlank() }
                            if (displayConfigs.isEmpty()) {
                                TextPreference(
                                    title = stringResource(R.string.tools_subscription_hint_enter_filename),
                                    summary = stringResource(R.string.tools_subscription_key_name_provide_mihomo_config_subtitle),
                                    icon = ImageIcon(Icons.Filled.Description),
                                    onClick = { showConfigSheet = true },
                                    enabled = updateSubscription
                                )
                            } else {
                                displayConfigs.forEachIndexed { idx, cfg ->
                                    if (idx > 0) SubscriptionDivider()
                                    TextPreference(
                                        title = cfg,
                                        icon = ImageIcon(Icons.Filled.Description),
                                        onClick = { showConfigSheet = true },
                                        enabled = updateSubscription
                                    )
                                }
                            }
                        }
                    }
                }

                // ═══ Paths ═══
                item(key = "section_paths") {
                    SmallTitle(text = stringResource(R.string.tools_subscription_paths_title))
                }
                item(key = "card_paths") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 18.dp
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            EditTextPreference(
                                title = stringResource(R.string.tools_subscription_mihomo_provide_path_title),
                                text = mihomoProvidePath,
                                onTextChange = { mihomoProvidePath = it },
                                icon = ImageIcon(Icons.Filled.Folder),
                                summary = stringResource(R.string.tools_subscription_key_mihomo_provide_path_subtitle),
                                valuePosition = ValuePosition.Summary,
                                dialogTitle = stringResource(R.string.tools_subscription_mihomo_provide_path_title),
                                dialogHint = stringResource(R.string.tools_subscription_path_placeholder),
                                enabled = updateSubscription
                            )
                            SubscriptionDivider()
                            EditTextPreference(
                                title = stringResource(R.string.tools_subscription_sing_box_url_title),
                                text = singboxUrl,
                                onTextChange = { singboxUrl = it },
                                icon = ImageIcon(Icons.Filled.Public),
                                summary = stringResource(R.string.tools_subscription_key_subscription_url_singbox_subtitle),
                                valuePosition = ValuePosition.Summary,
                                dialogTitle = stringResource(R.string.tools_subscription_sing_box_url_title),
                                dialogHint = stringResource(R.string.tools_subscription_url_placeholder),
                                enabled = updateSubscription
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            // ── FAB 保存按钮 ──
            val fabBottomPadding = LocalFloatingNavBarSpaceDp.current +
                LocalSystemNavBarInsetDp.current + 16.dp

            AnimatedVisibility(
                visible = isDirty,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = fabBottomPadding),
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                HyperButton(
                    onClick = { saveNonBooleans() },
                    prominent = true
                ) {
                    Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.action_save))
                }
            }

            // ── URL 编辑底部弹窗 ──
            if (showUrlSheet) {
                UrlListEditSheet(
                    urls = mihomoUrls,
                    onUrlsChange = { mihomoUrls = it },
                    onDismiss = { showUrlSheet = false }
                )
            }

            // ── Config 编辑底部弹窗 ──
            if (showConfigSheet) {
                ConfigListEditSheet(
                    configs = mihomoConfigs,
                    onConfigsChange = { mihomoConfigs = it },
                    onDismiss = { showConfigSheet = false }
                )
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

// ─── URL 列表编辑底部弹窗 ─────────────────────────────────────────────────

@Composable
private fun UrlListEditSheet(
    urls: List<String>,
    onUrlsChange: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    HyperBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.tools_subscription_mihomo_urls_title),
        endAction = {
            HyperTextButton(
                text = stringResource(R.string.action_add),
                onClick = { onUrlsChange(urls + "") },
                prominent = true
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val safeUrls = urls.ifEmpty { listOf("") }
            safeUrls.forEachIndexed { idx, url ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HyperTextField(
                        value = url,
                        onValueChange = { v ->
                            onUrlsChange(safeUrls.toMutableList().also { it[idx] = v })
                        },
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.tools_subscription_hint_enter_url),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done
                        )
                    )
                    HyperIconButton(
                        onClick = {
                            val next = safeUrls.toMutableList()
                            if (next.size <= 1) next[0] = "" else next.removeAt(idx)
                            onUrlsChange(next)
                        },
                        icon = Icons.Filled.Delete,
                        modifier = Modifier.padding(start = 8.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ─── Config 列表编辑底部弹窗 ───────────────────────────────────────────────

@Composable
private fun ConfigListEditSheet(
    configs: List<String>,
    onConfigsChange: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    HyperBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.tools_subscription_mihomo_configs_title),
        endAction = {
            HyperTextButton(
                text = stringResource(R.string.action_add),
                onClick = { onConfigsChange(configs + "") },
                prominent = true
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val safeConfigs = configs.ifEmpty { listOf("") }
            safeConfigs.forEachIndexed { idx, cfg ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HyperTextField(
                        value = cfg,
                        onValueChange = { v ->
                            onConfigsChange(safeConfigs.toMutableList().also { it[idx] = v })
                        },
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.tools_subscription_hint_enter_filename),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        )
                    )
                    HyperIconButton(
                        onClick = {
                            val next = safeConfigs.toMutableList()
                            if (next.size <= 1) next[0] = "" else next.removeAt(idx)
                            onConfigsChange(next)
                        },
                        icon = Icons.Filled.Delete,
                        modifier = Modifier.padding(start = 8.dp),
                        tint = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ─── 分隔线 ─────────────────────────────────────────────────────────────────

@Composable
private fun SubscriptionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(0.5.dp)
            .background(MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.08f))
    )
}
