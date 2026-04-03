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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.box.app.ui.components.ToolsSectionCard
import com.box.app.ui.components.ToolsSubHeader
import com.box.app.ui.components.contentPaddingWithNavBars
import com.box.app.ui.theme.appColors
import com.box.app.ui.theme.appAccentColor
import com.box.app.ui.components.ErrorToast
import com.box.app.ui.components.SettingsToggleRow
import com.box.app.ui.components.LiquidGlassButton
import com.box.app.ui.components.LiquidGlassIconButton
import com.box.app.ui.components.LocalLiquidBackdrop
import com.box.app.data.backend.BoxApi
import com.box.app.utils.ThemeManager
import com.box.app.R
import androidx.compose.runtime.CompositionLocalProvider
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.RoundedRectangle

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
    val c = appColors()
    val context = LocalContext.current
    val pagePadding = 16.dp
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val scope = rememberCoroutineScope()

    val liquidBackdrop = rememberLayerBackdrop()

    var topBarHeightPx by rememberSaveable { mutableStateOf(0) }
    var lastNonZeroTopBarHeightPx by rememberSaveable { mutableStateOf(0) }
    val density = LocalDensity.current
    val effectiveTopBarHeightPx = if (topBarHeightPx > 0) topBarHeightPx else lastNonZeroTopBarHeightPx
    val topInset = with(density) { effectiveTopBarHeightPx.toDp() } + 16.dp

    var bypassCnIp by rememberSaveable { mutableStateOf(false) }
    var bypassCnIpV4 by rememberSaveable { mutableStateOf(false) }
    var bypassCnIpV6 by rememberSaveable { mutableStateOf(false) }

    var cnIpFile by rememberSaveable { mutableStateOf("") }
    var cnIpv6File by rememberSaveable { mutableStateOf("") }
    var cnIpUrl by rememberSaveable { mutableStateOf("") }
    var cnIpv6Url by rememberSaveable { mutableStateOf("") }

    var initialBypassCnIp by rememberSaveable { mutableStateOf(false) }
    var initialBypassCnIpV4 by rememberSaveable { mutableStateOf(false) }
    var initialBypassCnIpV6 by rememberSaveable { mutableStateOf(false) }
    var initialCnIpFile by rememberSaveable { mutableStateOf("") }
    var initialCnIpv6File by rememberSaveable { mutableStateOf("") }
    var initialCnIpUrl by rememberSaveable { mutableStateOf("") }
    var initialCnIpv6Url by rememberSaveable { mutableStateOf("") }

    val isDirty by remember {
        derivedStateOf {
            bypassCnIp != initialBypassCnIp ||
                bypassCnIpV4 != initialBypassCnIpV4 ||
                bypassCnIpV6 != initialBypassCnIpV6 ||
                cnIpFile.trim() != initialCnIpFile.trim() ||
                cnIpv6File.trim() != initialCnIpv6File.trim() ||
                cnIpUrl.trim() != initialCnIpUrl.trim() ||
                cnIpv6Url.trim() != initialCnIpv6Url.trim()
        }
    }

    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ErrorToast(
        message = error,
        onConsumed = { error = null }
    )

    LaunchedEffect(Unit) {
        loading = true
        error = null
        val settings = BoxApi.getSettings()
        if (settings.isBlank()) {
            error = context.getString(R.string.tools_update_load_failed)
            loading = false
            return@LaunchedEffect
        }

        settings.lineSequence().map { it.trim() }.forEach { line ->
            when {
                line.startsWith("bypass_cn_ip=") -> bypassCnIp = line.substringAfter("=").trim().replace("\"", "").lowercase() == "true"
                line.startsWith("bypass_cn_ip_v4=") -> bypassCnIpV4 = line.substringAfter("=").trim().replace("\"", "").lowercase() == "true"
                line.startsWith("bypass_cn_ip_v6=") -> bypassCnIpV6 = line.substringAfter("=").trim().replace("\"", "").lowercase() == "true"
                line.startsWith("cn_ip_file=") -> cnIpFile = line.substringAfter("=").trim().trim('"', '\'')
                line.startsWith("cn_ipv6_file=") -> cnIpv6File = line.substringAfter("=").trim().trim('"', '\'')
                line.startsWith("cn_ip_url=") -> cnIpUrl = line.substringAfter("=").trim().trim('"', '\'')
                line.startsWith("cn_ipv6_url=") -> cnIpv6Url = line.substringAfter("=").trim().trim('"', '\'')
            }
        }

        initialBypassCnIp = bypassCnIp
        initialBypassCnIpV4 = bypassCnIpV4
        initialBypassCnIpV6 = bypassCnIpV6
        initialCnIpFile = cnIpFile
        initialCnIpv6File = cnIpv6File
        initialCnIpUrl = cnIpUrl
        initialCnIpv6Url = cnIpv6Url

        loading = false
    }

    LaunchedEffect(listState) {
        var last = listState.firstVisibleItemIndex * 10_000 + listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex * 10_000 + listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { now ->
                if (now > last) onNavVisibilityChange(false) else if (now < last) onNavVisibilityChange(true)
                last = now
            }
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                ToolsSectionCard(
                    title = stringResource(R.string.tools_cnip_section_general_title),
                    subtitle = stringResource(R.string.tools_cnip_section_general_subtitle)
                ) {
                    CnipIpsetHint()
                    Spacer(modifier = Modifier.height(6.dp))
                    SettingsToggleRow(
                        icon = Icons.Filled.Public,
                        title = stringResource(R.string.tools_cnip_bypass_title),
                        subtitle = stringResource(R.string.tools_cnip_bypass_subtitle),
                        checked = bypassCnIp,
                        onCheckedChange = { bypassCnIp = it }
                    )
                    SettingsToggleRow(
                        icon = Icons.Filled.Public,
                        title = stringResource(R.string.tools_cnip_bypass_ipv4_title),
                        subtitle = stringResource(R.string.tools_cnip_bypass_ipv4_subtitle),
                        checked = bypassCnIpV4,
                        onCheckedChange = { bypassCnIpV4 = it },
                        enabled = bypassCnIp
                    )
                    SettingsToggleRow(
                        icon = Icons.Filled.Public,
                        title = stringResource(R.string.tools_cnip_bypass_ipv6_title),
                        subtitle = stringResource(R.string.tools_cnip_bypass_ipv6_subtitle),
                        checked = bypassCnIpV6,
                        onCheckedChange = { bypassCnIpV6 = it },
                        enabled = bypassCnIp,
                        showDivider = false
                    )
                }
            }
            item {
                ToolsSectionCard(
                    title = stringResource(R.string.tools_cnip_section_files_title),
                    subtitle = stringResource(R.string.tools_cnip_section_files_subtitle)
                ) {
                    CnipLabeledTextField(
                        icon = Icons.Filled.Description,
                        title = stringResource(R.string.tools_cnip_ipv4_file_title),
                        subtitle = "cn_ip_file",
                        value = cnIpFile,
                        placeholder = stringResource(R.string.tools_cnip_placeholder_path),
                        enabled = bypassCnIp,
                        onValueChange = { cnIpFile = it }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    CnipLabeledTextField(
                        icon = Icons.Filled.Description,
                        title = stringResource(R.string.tools_cnip_ipv6_file_title),
                        subtitle = "cn_ipv6_file",
                        value = cnIpv6File,
                        placeholder = stringResource(R.string.tools_cnip_placeholder_path),
                        enabled = bypassCnIp,
                        onValueChange = { cnIpv6File = it }
                    )
                }
            }

            item {
                ToolsSectionCard(
                    title = stringResource(R.string.tools_cnip_section_sources_title),
                    subtitle = stringResource(R.string.tools_cnip_section_sources_subtitle)
                ) {
                    CnipLabeledTextField(
                        icon = Icons.Filled.Link,
                        title = stringResource(R.string.tools_cnip_ipv4_url_title),
                        subtitle = "cn_ip_url",
                        value = cnIpUrl,
                        placeholder = stringResource(R.string.tools_cnip_placeholder_url),
                        enabled = bypassCnIp,
                        onValueChange = { cnIpUrl = it }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    CnipLabeledTextField(
                        icon = Icons.Filled.Link,
                        title = stringResource(R.string.tools_cnip_ipv6_url_title),
                        subtitle = "cn_ipv6_url",
                        value = cnIpv6Url,
                        placeholder = stringResource(R.string.tools_cnip_placeholder_url),
                        enabled = bypassCnIp,
                        onValueChange = { cnIpv6Url = it }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        CompositionLocalProvider(LocalLiquidBackdrop provides liquidBackdrop) {
            CnipSettingsFloatingTopBar(
                saveEnabled = !loading && !saving && isDirty,
                onBack = onBack,
                onSave = {
                    if (saving) return@CnipSettingsFloatingTopBar
                    scope.launch {
                        saving = true
                        error = null

                        val rBypass = BoxApi.updateBooleanSetting("bypass_cn_ip", bypassCnIp)
                        if (!rBypass) {
                            error = context.getString(R.string.tools_update_save_failed)
                            saving = false
                            return@launch
                        }
                        val rBypassV4 = BoxApi.updateBooleanSetting("bypass_cn_ip_v4", bypassCnIpV4)
                        if (!rBypassV4) {
                            error = context.getString(R.string.tools_update_save_failed)
                            saving = false
                            return@launch
                        }
                        val rBypassV6 = BoxApi.updateBooleanSetting("bypass_cn_ip_v6", bypassCnIpV6)
                        if (!rBypassV6) {
                            error = context.getString(R.string.tools_update_save_failed)
                            saving = false
                            return@launch
                        }
                        val rFile4 = BoxApi.updateSetting("cn_ip_file", cnIpFile.trim())
                        if (!rFile4) {
                            error = context.getString(R.string.tools_update_save_failed)
                            saving = false
                            return@launch
                        }
                        val rFile6 = BoxApi.updateSetting("cn_ipv6_file", cnIpv6File.trim())
                        if (!rFile6) {
                            error = context.getString(R.string.tools_update_save_failed)
                            saving = false
                            return@launch
                        }
                        val rUrl4 = BoxApi.updateSetting("cn_ip_url", cnIpUrl.trim())
                        if (!rUrl4) {
                            error = context.getString(R.string.tools_update_save_failed)
                            saving = false
                            return@launch
                        }
                        val rUrl6 = BoxApi.updateSetting("cn_ipv6_url", cnIpv6Url.trim())
                        if (!rUrl6) {
                            error = context.getString(R.string.tools_update_save_failed)
                            saving = false
                            return@launch
                        }

                        initialBypassCnIp = bypassCnIp
                        initialBypassCnIpV4 = bypassCnIpV4
                        initialBypassCnIpV6 = bypassCnIpV6
                        initialCnIpFile = cnIpFile
                        initialCnIpv6File = cnIpv6File
                        initialCnIpUrl = cnIpUrl
                        initialCnIpv6Url = cnIpv6Url

                        saving = false
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .onGloballyPositioned {
                        val h = it.size.height
                        if (h > 0) {
                            topBarHeightPx = h
                            lastNonZeroTopBarHeightPx = h
                        }
                    }
            )
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                androidx.compose.material3.CircularProgressIndicator(color = c.textPrimary)
            }
        }
    }
}

@Composable
private fun CnipIpsetHint() {
    val c = appColors()
    val accent = appAccentColor()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedRectangle(14.dp))
            .background(accent.copy(alpha = 0.14f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(accent.copy(alpha = 0.22f), shape = RoundedRectangle(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    text = stringResource(R.string.tools_update_tip_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
                Text(
                    text = stringResource(R.string.tools_cnip_ipset_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textPrimary
                )
            }
        }
    }
}

@Composable
private fun CnipLabeledTextField(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    value: String,
    placeholder: String,
    enabled: Boolean = true,
    onValueChange: (String) -> Unit
) {
    val c = appColors()
    val accent = appAccentColor()

    Column(modifier = Modifier.alpha(if (enabled) 1f else 0.45f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(c.cardAlt, shape = RoundedRectangle(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = c.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = c.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        androidx.compose.material3.OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            placeholder = {
                Text(
                    text = placeholder,
                    color = c.textSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                unfocusedBorderColor = c.divider,
                focusedTextColor = c.textPrimary,
                unfocusedTextColor = c.textPrimary,
                cursorColor = accent
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CnipSettingsFloatingTopBar(
    saveEnabled: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = appColors()
    val backdrop = requireNotNull(LocalLiquidBackdrop.current)
    val context = LocalContext.current
    val isDark = ThemeManager.shouldUseDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }

    val selectedTint = if (isDark) Color(0xFF2B2F37) else Color(0xFFE3E6EA)
    val tint = selectedTint.copy(alpha = 0.25f)
    val fallback = selectedTint.copy(alpha = 0.80f)

    Row(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, top = 8.dp, end = 20.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LiquidGlassButton(
            onClick = onBack,
            backdrop = backdrop,
            surfaceColor = tint
        ) {
            Text(
                text = stringResource(R.string.tools_update_back_compact),
                color = c.textPrimary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        LiquidGlassIconButton(
            onClick = onSave,
            enabled = saveEnabled,
            backdrop = backdrop,
            surfaceColor = tint
        ) {
            Icon(
                imageVector = Icons.Filled.Save,
                contentDescription = stringResource(R.string.action_save),
                tint = if (saveEnabled) c.textPrimary else c.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
