package com.box.app.ui.components.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.box.app.BuildConfig
import com.box.app.data.backend.BoxApi
import com.box.app.data.backend.ShellExecutor
import com.box.app.data.model.HomeServiceState
import com.box.app.data.model.ServiceStatus
import com.box.app.R
import com.box.app.ui.components.SettingsToggleRow
import com.box.app.ui.components.ToolsRowIcon
import com.box.app.ui.components.ToolsSectionCard
import com.box.app.ui.components.ErrorToast
import com.box.app.ui.components.bottomsheets.AppModalBottomSheet
import com.box.app.ui.theme.appColors
import com.box.app.ui.theme.appAccentColor
import com.box.app.utils.ThemeManager
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Storage
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

data class HomeCardModel(
    val title: String,
    val value: String,
    val subtitle: String,
    val kind: HomeMetricKind,
    val accent: Color,
    val badgeText: String? = null,
    val progress: Float? = null,
    val isActive: Boolean = true,
    val onClick: (() -> Unit)? = null,
    val cornerActionIcon: ImageVector? = null,
    val onCornerAction: (() -> Unit)? = null,
    val sparkDown: List<Float>? = null,
    val sparkUp: List<Float>? = null
)

enum class HomeMetricKind {
    Service,
    Ip,
    Speed,
    Latency,
    Subscription,
    System
}

@Composable
private fun HomeSheetHandle() {
    val c = appColors()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .clearAndSetSemantics { }
                .size(width = 28.dp, height = 3.dp)
                .clip(Capsule())
                .background(c.divider.copy(alpha = 0.42f))
        )
    }
}

@Composable
fun HomeHeader(
    onEdit: (() -> Unit)? = null
) {
    val c = appColors()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_header_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.home_header_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary
            )
        }
        if (onEdit != null) {
            Box(
                modifier = Modifier
                    .clip(Capsule())
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onEdit
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.home_header_edit),
                    tint = c.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HomeHeroCard(
    serviceState: HomeServiceState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReload: () -> Unit
) {

    val c = appColors()
    val scope = rememberCoroutineScope()
    val isDark = ThemeManager.shouldUseDarkTheme()
    val container = c.card
    val panel = if (isDark) c.cardAlt else Color(0xFFF7F8FA)

    val env = serviceState.env

    val status = serviceState.status
    val isRunning = status is ServiceStatus.Running
    val isStopped = status is ServiceStatus.Stopped
    val isBusy = !(isRunning || isStopped)

    val statusEditable = !isRunning

    val showUnavailable = env.checked && !env.isReady
    val isEnvReady = env.isReady

    val statusText = when (status) {
        is ServiceStatus.Running -> stringResource(R.string.home_service_status_running)
        is ServiceStatus.Stopped -> if (showUnavailable) stringResource(R.string.home_service_status_unavailable) else stringResource(R.string.home_service_status_stopped)
        is ServiceStatus.Starting -> stringResource(R.string.home_service_status_starting)
        is ServiceStatus.Stopping -> stringResource(R.string.home_service_status_stopping)
        is ServiceStatus.Restarting -> stringResource(R.string.home_service_status_restarting)
        is ServiceStatus.Checking -> stringResource(R.string.home_service_status_checking)
    }

    val statusAccent = when {
        isRunning -> Color(0xFF2DA44E)
        showUnavailable -> Color(0xFFCF222E)
        isStopped -> c.textPrimary
        else -> Color(0xFFD29922)
    }
    val statusChipBg = statusAccent.copy(alpha = if (isDark) 0.16f else 0.10f)

    var showCoreSheet by remember { mutableStateOf(false) }
    var showModeSheet by remember { mutableStateOf(false) }
    var showIpv6Sheet by remember { mutableStateOf(false) }

    var coreText by remember { mutableStateOf(serviceState.coreDisplayName) }
    var modeText by remember { mutableStateOf(serviceState.networkMode) }
    var ipv6Text by remember { mutableStateOf(serviceState.ipv6Text) }

    LaunchedEffect(serviceState.coreDisplayName, serviceState.networkMode, serviceState.ipv6Text, isRunning) {
        if (isRunning) {
            coreText = serviceState.coreDisplayName
            modeText = serviceState.networkMode
            ipv6Text = serviceState.ipv6Text
        }
    }

    fun parseSetting(settings: String, key: String): String? {
        val regex = Regex("^${key}=\"?(.*?)\"?$", setOf(RegexOption.MULTILINE))
        return regex.find(settings)?.groupValues?.getOrNull(1)
    }

    fun coreDisplayName(binName: String?, xclashOption: String?): String? {
        val bin = binName?.trim().orEmpty()
        if (bin.isBlank()) return null
        if (BuildConfig.FLAVOR == "bfr" && bin == "clash") {
            val opt = xclashOption?.trim().orEmpty()
            if (opt.isNotBlank()) return "clash-$opt"
        }
        return bin
    }

    suspend fun reloadSettingsLocal() {
        val settings = BoxApi.getSettings()
        if (settings.isBlank()) return
        val bin = parseSetting(settings, "bin_name")
        val opt = parseSetting(settings, "xclash_option")
        coreText = coreDisplayName(bin, opt)?.takeIf { it.isNotBlank() } ?: coreText
        modeText = parseSetting(settings, "network_mode")?.takeIf { it.isNotBlank() } ?: modeText
        val ipv6Enabled = when (parseSetting(settings, "ipv6")?.toBooleanStrictOrNull()) {
            true -> true
            false -> false
            else -> null
        }
        if (ipv6Enabled != null) {
            ipv6Text = if (ipv6Enabled) "true" else "false"
        }
    }

    LaunchedEffect(isRunning) {
        if (!isRunning) {
            reloadSettingsLocal()
        }
    }

    suspend fun writeSetting(key: String, value: String) {
        val file = "/data/adb/box/settings.ini"
        val escaped = value.replace("\"", "\\\"")
        val cmd = "if [ -f '$file' ]; then " +
            "if grep -q '^${key}=' '$file'; then " +
            "sed -i 's/^${key}=.*/${key}=\\\"${escaped}\\\"/' '$file'; " +
            "else echo '${key}=\\\"${escaped}\\\"' >> '$file'; fi; " +
            "else echo '${key}=\\\"${escaped}\\\"' > '$file'; fi"
        ShellExecutor.execute(cmd)
    }

    suspend fun removeSetting(key: String) {
        val file = "/data/adb/box/settings.ini"
        val cmd = "if [ -f '$file' ]; then sed -i '/^${key}=/d' '$file'; fi"
        ShellExecutor.execute(cmd)
    }

    suspend fun writeBoolSetting(key: String, enabled: Boolean) {
        val file = "/data/adb/box/settings.ini"
        val v = if (enabled) "true" else "false"
        val cmd = "if [ -f '$file' ]; then " +
            "if grep -q '^${key}=' '$file'; then " +
            "sed -i 's/^${key}=.*/${key}=\\\"${v}\\\"/' '$file'; " +
            "else echo '${key}=\\\"${v}\\\"' >> '$file'; fi; " +
            "else echo '${key}=\\\"${v}\\\"' > '$file'; fi"
        ShellExecutor.execute(cmd)
    }

    if (showCoreSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        data class CoreChoice(
            val title: String,
            val binName: String,
            val xclashOption: String? = null
        )

        val coreOptions = if (BuildConfig.FLAVOR == "bfr") {
            listOf(
                CoreChoice(title = "clash-mihomo", binName = "clash", xclashOption = "mihomo"),
                CoreChoice(title = "clash-premium", binName = "clash", xclashOption = "premium"),
                CoreChoice(title = "sing-box", binName = "sing-box"),
                CoreChoice(title = "xray", binName = "xray"),
                CoreChoice(title = "v2fly", binName = "v2fly"),
                CoreChoice(title = "hysteria", binName = "hysteria")
            )
        } else {
            listOf(
                CoreChoice(title = "mihomo", binName = "mihomo"),
                CoreChoice(title = "sing-box", binName = "sing-box"),
                CoreChoice(title = "xray", binName = "xray"),
                CoreChoice(title = "v2fly", binName = "v2fly"),
                CoreChoice(title = "hysteria", binName = "hysteria")
            )
        }
        AppModalBottomSheet(
            onDismissRequest = { showCoreSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                HomeSheetHandle()

                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.home_sheet_core_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = c.textPrimary
                            )
                            Text(
                                text = stringResource(R.string.home_sheet_core_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    coreOptions.forEachIndexed { idx, item ->
                        val isLast = idx == coreOptions.lastIndex
                        ToolsRowIcon(
                            icon = Icons.Filled.Storage,
                            title = item.title,
                            subtitle = if (item.title == coreText) stringResource(R.string.home_sheet_current) else stringResource(R.string.home_sheet_tap_to_select),
                            showDivider = !isLast,
                            onClick = {
                                showCoreSheet = false
                                coreText = item.title
                                scope.launch {
                                    writeSetting("bin_name", item.binName)
                                    val opt = item.xclashOption
                                    if (!opt.isNullOrBlank()) {
                                        writeSetting("xclash_option", opt)
                                    }
                                    reloadSettingsLocal()
                                }
                            }
                        )
                    }
                }

                // Add navigation bar padding at the bottom
                Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }
    }

    if (showModeSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val modeOptions = listOf("tun", "tproxy", "redirect", "mixed", "enhance")
        AppModalBottomSheet(
            onDismissRequest = { showModeSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                HomeSheetHandle()

                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.home_sheet_network_mode_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = c.textPrimary
                            )
                            Text(
                                text = stringResource(R.string.home_sheet_network_mode_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    modeOptions.forEachIndexed { idx, item ->
                        val isLast = idx == modeOptions.lastIndex
                        ToolsRowIcon(
                            icon = Icons.Filled.Route,
                            title = item,
                            subtitle = if (item == modeText) stringResource(R.string.home_sheet_current) else stringResource(R.string.home_sheet_tap_to_select),
                            showDivider = !isLast,
                            onClick = {
                                showModeSheet = false
                                modeText = item
                                scope.launch {
                                    writeSetting("network_mode", item)
                                    reloadSettingsLocal()
                                }
                            }
                        )
                    }
                }

                // Add navigation bar padding at the bottom
                Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }
    }

    if (showIpv6Sheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val checked = ipv6Text.equals("true", ignoreCase = true)
        AppModalBottomSheet(
            onDismissRequest = { showIpv6Sheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                HomeSheetHandle()

                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.home_sheet_ipv6_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = c.textPrimary
                            )
                            Text(
                                text = stringResource(R.string.home_sheet_ipv6_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = c.textSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    SettingsToggleRow(
                        icon = Icons.Filled.SettingsEthernet,
                        title = stringResource(R.string.home_ipv6_label),
                        subtitle = if (checked) stringResource(R.string.common_on) else stringResource(R.string.common_off),
                        checked = checked,
                        showDivider = false,
                        onCheckedChange = { next ->
                            ipv6Text = if (next) "true" else "false"
                            scope.launch {
                                writeBoolSetting("ipv6", next)
                                reloadSettingsLocal()
                            }
                        }
                    )
                }
                
                // Add navigation bar padding at the bottom
                Spacer(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars))
            }
        }
    }

    var toastMessage by remember { mutableStateOf<String?>(null) }
    ErrorToast(
        message = toastMessage,
        onConsumed = { toastMessage = null }
    )

    val canReloadConfig = remember(isRunning, isEnvReady, coreText) {
        isRunning && isEnvReady && (coreText.equals("mihomo", ignoreCase = true) || coreText.equals("sing-box", ignoreCase = true))
    }

    val reloadConfigSuccessText = stringResource(R.string.home_reload_config_success)
    val reloadConfigFailedText = stringResource(R.string.home_reload_config_failed)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedRectangle(22.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 168.dp)
                .padding(18.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .weight(1.0f)
                            .padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.home_service_status_title),
                            style = MaterialTheme.typography.labelLarge,
                            color = c.textSecondary
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = statusAccent
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(Capsule())
                                .background(statusChipBg)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            val rawChipText = when {
                                isRunning -> serviceState.uptimeText
                                showUnavailable -> when {
                                    !env.hasRoot -> stringResource(R.string.home_env_title_root_required)
                                    !env.hasModule -> stringResource(R.string.home_env_title_module_missing)
                                    !env.hasScripts -> stringResource(R.string.home_env_body_scripts_missing)
                                    else -> stringResource(R.string.home_environment_not_ready)
                                }
                                isBusy -> stringResource(R.string.home_please_wait)
                                else -> stringResource(R.string.home_tap_start_to_enable)
                            }

                            fun forceTimeToSecondLine(text: String): String {
                                val idx = text.indexOf('·')
                                if (idx <= 0 || idx >= text.lastIndex) return text
                                val first = text.substring(0, idx).trimEnd()
                                val second = text.substring(idx + 1).trimStart()
                                return "$first\n$second"
                            }

                            var forceUptimeSecondLine by remember(rawChipText, isRunning) { mutableStateOf(false) }
                            Text(
                                text = if (isRunning && forceUptimeSecondLine) {
                                    forceTimeToSecondLine(rawChipText)
                                } else {
                                    rawChipText
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = c.textPrimary,
                                onTextLayout = { layout ->
                                    if (isRunning && !forceUptimeSecondLine && !rawChipText.contains('\n') && layout.lineCount > 1) {
                                        forceUptimeSecondLine = true
                                    }
                                }
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1.0f)
                            .clip(RoundedRectangle(18.dp))
                            .background(panel)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val interactionSourceCore = remember { MutableInteractionSource() }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedRectangle(10.dp))
                                .clickable(
                                    enabled = statusEditable,
                                    interactionSource = interactionSourceCore,
                                    indication = null
                                ) { showCoreSheet = true }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.home_core_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = c.textSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = coreText.ifBlank { stringResource(R.string.home_placeholder_dash) },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = c.textPrimary
                            )
                        }

                        val interactionSourceMode = remember { MutableInteractionSource() }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedRectangle(10.dp))
                                .clickable(
                                    enabled = statusEditable,
                                    interactionSource = interactionSourceMode,
                                    indication = null
                                ) { showModeSheet = true }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.home_mode_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = c.textSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = modeText.ifBlank { stringResource(R.string.home_placeholder_dash) },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = c.textPrimary
                            )
                        }

                        val interactionSourceIpv6 = remember { MutableInteractionSource() }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedRectangle(10.dp))
                                .clickable(
                                    enabled = statusEditable,
                                    interactionSource = interactionSourceIpv6,
                                    indication = null
                                ) { showIpv6Sheet = true }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.home_ipv6_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = c.textSecondary,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = when {
                                    ipv6Text.equals("true", ignoreCase = true) -> stringResource(R.string.common_on)
                                    ipv6Text.equals("false", ignoreCase = true) -> stringResource(R.string.common_off)
                                    else -> stringResource(R.string.home_placeholder_dash)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = c.textPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(14.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedRectangle(18.dp))
                        .background(panel)
                        .padding(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (isRunning && canReloadConfig) {
                            HeroActionButton(
                                text = stringResource(R.string.home_action_reload_config),
                                tint = Color(0xFF1F6FEB),
                                enabled = isEnvReady,
                                onClick = {
                                    scope.launch {
                                        val res = BoxApi.reloadConfig()
                                        toastMessage = if (res.exitCode == 0) {
                                            reloadConfigSuccessText
                                        } else {
                                            res.stderr.ifBlank { reloadConfigFailedText }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            HeroActionButton(
                                text = stringResource(R.string.home_action_stop),
                                tint = Color(0xFFCF222E),
                                enabled = isEnvReady,
                                onClick = onStop,
                                modifier = Modifier.weight(1f)
                            )
                            HeroActionButton(
                                text = stringResource(R.string.home_action_reload),
                                tint = Color(0xFFD29922),
                                enabled = isEnvReady,
                                onClick = onReload,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            HeroActionButton(
                                text = stringResource(R.string.home_action_start),
                                tint = Color(0xFF2DA44E),
                                enabled = isStopped && isEnvReady,
                                onClick = onStart,
                                modifier = Modifier.weight(1f)
                            )
                            HeroActionButton(
                                text = stringResource(R.string.home_action_stop),
                                tint = Color(0xFFCF222E),
                                enabled = isRunning && isEnvReady,
                                onClick = onStop,
                                modifier = Modifier.weight(1f)
                            )
                            HeroActionButton(
                                text = stringResource(R.string.home_action_reload),
                                tint = Color(0xFFD29922),
                                enabled = isRunning && isEnvReady,
                                onClick = onReload,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroActionButton(
    text: String,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val alpha = if (enabled) 1f else 0.45f

    Box(
        modifier = modifier
            .clip(RoundedRectangle(14.dp))
            .background(tint.copy(alpha = 0.12f * alpha))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = tint.copy(alpha = alpha)
        )
    }
}

