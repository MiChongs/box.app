package com.box.app.ui.screens.tools

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.FlowPreview
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.TextButton
import com.box.app.ui.components.ToolsRowBitmapIcon
import com.box.app.ui.components.contentPaddingWithNavBars
import com.box.app.ui.theme.appColors
import com.box.app.ui.theme.appAccentColor
import com.box.app.utils.AppClassifier
import com.box.app.utils.AppUtils
import com.box.app.utils.ThemeManager
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.box.app.data.backend.ShellExecutor
import com.box.app.data.repo.ConfigRepository
import com.box.app.ui.components.ErrorToast
import com.box.app.R
import com.box.app.BuildConfig
import com.box.app.ui.components.LiquidGlassButton
import com.box.app.ui.components.LiquidGlassDropdownMenu
import com.box.app.ui.components.LiquidGlassIconButton
import com.box.app.ui.components.LocalLiquidBackdrop
import com.box.app.ui.components.LiquidGlassTextFieldPill
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntRect
import kotlin.math.roundToInt

private enum class AppsProxyMode { BLACKLIST, WHITELIST, CORE }

private enum class AppsSortOrder { NAME_ASC, NAME_DESC, INSTALL_TIME_ASC, INSTALL_TIME_DESC }

private enum class AppsAppType { ALL, USER, SYSTEM }

private enum class AppsNetworkFilter { ALL, ONLY_NETWORK, EXCLUDE_NETWORK }

private enum class AppsUserFilter { ALL, MAIN_ONLY, WORK_ONLY, OTHER_ONLY }

private enum class SmartSelectApplyMode { Replace, Merge }

@Composable
@OptIn(FlowPreview::class)
fun ToolsAppsScreen(
    onNavVisibilityChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val c = appColors()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagePadding = 20.dp
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val liquidBackdrop = rememberLayerBackdrop()

    val filterPrefs = remember {
        context.getSharedPreferences("tools_apps_sort_filter", android.content.Context.MODE_PRIVATE)
    }

    val isBfr = BuildConfig.FLAVOR == "bfr"

    var proxyMode by rememberSaveable { mutableStateOf(AppsProxyMode.BLACKLIST) }

    var selectedPackages by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }

    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var debouncedQuery by rememberSaveable { mutableStateOf("") }
    var menuAction by rememberSaveable { mutableStateOf<AppsManageAction?>(null) }

    var showSortFilter by rememberSaveable { mutableStateOf(false) }

    var appliedSortOrder by rememberSaveable { mutableStateOf(AppsSortOrder.NAME_ASC) }
    var appliedAppType by rememberSaveable { mutableStateOf(AppsAppType.ALL) }
    var appliedNetworkFilter by rememberSaveable { mutableStateOf(AppsNetworkFilter.ALL) }
    var appliedUserFilter by rememberSaveable { mutableStateOf(AppsUserFilter.ALL) }

    var draftSortOrder by rememberSaveable { mutableStateOf(AppsSortOrder.NAME_ASC) }
    var draftAppType by rememberSaveable { mutableStateOf(AppsAppType.ALL) }
    var draftNetworkFilter by rememberSaveable { mutableStateOf(AppsNetworkFilter.ALL) }
    var draftUserFilter by rememberSaveable { mutableStateOf(AppsUserFilter.ALL) }

    var filtersLoaded by rememberSaveable { mutableStateOf(false) }

    fun loadSavedFiltersOnce() {
        val so = filterPrefs.getInt("sort_order", -1)
        val at = filterPrefs.getInt("app_type", -1)
        val nf = filterPrefs.getInt("network_filter", -1)
        val uf = filterPrefs.getInt("user_filter", -1)

        appliedSortOrder = AppsSortOrder.values().getOrNull(so) ?: appliedSortOrder
        appliedAppType = AppsAppType.values().getOrNull(at) ?: appliedAppType
        appliedNetworkFilter = AppsNetworkFilter.values().getOrNull(nf) ?: appliedNetworkFilter
        appliedUserFilter = AppsUserFilter.values().getOrNull(uf) ?: appliedUserFilter

        filtersLoaded = true
    }

    LaunchedEffect(appliedSortOrder, appliedAppType, appliedNetworkFilter, appliedUserFilter, filtersLoaded) {
        if (!filtersLoaded) return@LaunchedEffect
        filterPrefs.edit()
            .putInt("sort_order", appliedSortOrder.ordinal)
            .putInt("app_type", appliedAppType.ordinal)
            .putInt("network_filter", appliedNetworkFilter.ordinal)
            .putInt("user_filter", appliedUserFilter.ordinal)
            .apply()
    }

    var showSmartSelectConfirm by rememberSaveable { mutableStateOf(false) }
    var smartSelecting by rememberSaveable { mutableStateOf(false) }
    var smartSelectApplyMode by rememberSaveable { mutableStateOf(SmartSelectApplyMode.Replace) }

    var saving by rememberSaveable { mutableStateOf(false) }
    var saveError by rememberSaveable { mutableStateOf<String?>(null) }

    var savedProxyMode by rememberSaveable { mutableStateOf(AppsProxyMode.BLACKLIST) }
    var savedSelectedPackages by rememberSaveable { mutableStateOf<Set<String>>(emptySet()) }

    var apps by remember { mutableStateOf<List<AppUtils.InstalledApp>>(emptyList()) }
    var loading by rememberSaveable { mutableStateOf(true) }
    var userDisplayNames by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }

    var overflowMenuExpanded by rememberSaveable { mutableStateOf(false) }
    var overflowMenuAnchorBoundsInRoot by remember { mutableStateOf<IntRect?>(null) }

    val hasUnsavedChanges by remember(proxyMode, selectedPackages, savedProxyMode, savedSelectedPackages) {
        derivedStateOf {
            proxyMode != savedProxyMode || selectedPackages != savedSelectedPackages
        }
    }

    val isCoreMode = proxyMode == AppsProxyMode.CORE

    fun AppsProxyMode.toConfigString(): String = when (this) {
        AppsProxyMode.BLACKLIST -> "blacklist"
        AppsProxyMode.WHITELIST -> "whitelist"
        AppsProxyMode.CORE -> if (isBfr) "blacklist" else "core"
    }

    fun parseProxyModeFromSettings(settingsIni: String): AppsProxyMode {
        val line = settingsIni
            .lineSequence()
            .firstOrNull { it.trim().startsWith("proxy_mode=") }
            ?.trim()
            .orEmpty()

        val raw = line
            .substringAfter("=", "")
            .trim()
            .trim('"')
            .lowercase()

        return when (raw) {
            "whitelist", "white" -> AppsProxyMode.WHITELIST
            "core" -> if (isBfr) AppsProxyMode.BLACKLIST else AppsProxyMode.CORE
            else -> AppsProxyMode.BLACKLIST
        }
    }

    fun openSystemAppDetails(packageName: String) {
        runCatching {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    suspend fun loadAppsAndBackendConfig(forceRefresh: Boolean) {
        loading = true

        withContext(Dispatchers.IO) {
            val settingsRes = ShellExecutor.execute("cat /data/adb/box/settings.ini 2>/dev/null")
            if (settingsRes.exitCode == 0 && settingsRes.stdout.isNotBlank()) {
                proxyMode = parseProxyModeFromSettings(settingsRes.stdout)
            }

            val selectedRes = ShellExecutor.execute("cat /data/adb/box/package.list.cfg 2>/dev/null")
            val backendSelected = if (selectedRes.exitCode == 0 && selectedRes.stdout.isNotBlank()) {
                selectedRes.stdout
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .toSet()
            } else {
                emptySet()
            }

            apps = AppUtils.getInstalledApps(context, forceRefresh = forceRefresh)

            // Build user display names cache
            val userIds = apps.map { it.userId }.distinct()
            val userNames = mutableMapOf<Int, String>()
            userIds.forEach { userId ->
                userNames[userId] = AppUtils.getUserDisplayName(userId)
            }
            userDisplayNames = userNames

            // Reconcile: keep only selections that exist in current installed list.
            val availableKeys = apps.asSequence().map { it.userScopedPackageName }.toSet()
            selectedPackages = backendSelected.intersect(availableKeys)

            savedProxyMode = proxyMode
            savedSelectedPackages = selectedPackages
        }

        loading = false
    }

    LaunchedEffect(Unit) {
        loadSavedFiltersOnce()
        loadAppsAndBackendConfig(forceRefresh = false)
    }

    LaunchedEffect(Unit) {
        snapshotFlow { query }
            .debounce(120)
            .collect { debouncedQuery = it }
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

    LaunchedEffect(showSortFilter) {
        if (!showSortFilter) return@LaunchedEffect
        draftSortOrder = appliedSortOrder
        draftAppType = appliedAppType
        draftNetworkFilter = appliedNetworkFilter
        draftUserFilter = appliedUserFilter
    }

    var topBarHeightPx by rememberSaveable { mutableStateOf(0) }
    var lastNonZeroTopBarHeightPx by rememberSaveable { mutableStateOf(0) }
    val density = LocalDensity.current
    val effectiveTopBarHeightPx = if (topBarHeightPx > 0) topBarHeightPx else lastNonZeroTopBarHeightPx
    val topInset = with(density) { effectiveTopBarHeightPx.toDp() } + 16.dp

    val indexedApps by remember(apps) {
        derivedStateOf {
            apps.map { app ->
                val searchable = (app.name + "\n" + app.userScopedPackageName).lowercase()
                app to searchable
            }
        }
    }

    val filtered by remember(indexedApps, debouncedQuery, appliedSortOrder, appliedAppType, appliedNetworkFilter, appliedUserFilter, userDisplayNames) {
        derivedStateOf {
            val q = debouncedQuery.trim().lowercase()

            val searched = if (q.isBlank()) {
                apps
            } else {
                indexedApps
                    .asSequence()
                    .filter { (_, searchable) -> searchable.contains(q) }
                    .map { (app, _) -> app }
                    .toList()
            }

            val typed = searched.asSequence().filter { app ->
                when (appliedAppType) {
                    AppsAppType.ALL -> true
                    AppsAppType.USER -> !app.isSystemApp
                    AppsAppType.SYSTEM -> app.isSystemApp
                }
            }

            val networked = typed.filter { app ->
                when (appliedNetworkFilter) {
                    AppsNetworkFilter.ALL -> true
                    AppsNetworkFilter.ONLY_NETWORK -> app.hasNetworkPermission
                    AppsNetworkFilter.EXCLUDE_NETWORK -> !app.hasNetworkPermission
                }
            }

            val userFiltered = networked.filter { app ->
                when (appliedUserFilter) {
                    AppsUserFilter.ALL -> true
                    AppsUserFilter.MAIN_ONLY -> app.userId == 0
                    AppsUserFilter.WORK_ONLY -> {
                        val userName = (userDisplayNames[app.userId] ?: "").lowercase()
                        userName.contains("work") || userName.contains("clone") || userName.contains("dual")
                    }
                    AppsUserFilter.OTHER_ONLY -> app.userId != 0
                }
            }

            val list = userFiltered.toList()
            when (appliedSortOrder) {
                AppsSortOrder.NAME_ASC -> list.sortedBy { it.name.lowercase() }
                AppsSortOrder.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
                AppsSortOrder.INSTALL_TIME_ASC -> list.sortedBy { it.installTime }
                AppsSortOrder.INSTALL_TIME_DESC -> list.sortedByDescending { it.installTime }
            }
        }
    }

    LaunchedEffect(menuAction) {
        when (menuAction) {
            AppsManageAction.Refresh -> loadAppsAndBackendConfig(forceRefresh = true)
            AppsManageAction.SelectAll -> {
                val allSelected = filtered.isNotEmpty() && filtered.all { it.userScopedPackageName in selectedPackages }
                selectedPackages = if (allSelected) {
                    selectedPackages - filtered.map { it.userScopedPackageName }.toSet()
                } else {
                    selectedPackages + filtered.map { it.userScopedPackageName }.toSet()
                }
            }
            AppsManageAction.Invert -> {
                val set = selectedPackages.toMutableSet()
                filtered.forEach { app ->
                    val key = app.userScopedPackageName
                    if (set.contains(key)) set.remove(key) else set.add(key)
                }
                selectedPackages = set
            }
            AppsManageAction.SmartSelect -> {
                showSmartSelectConfirm = true
            }
            else -> Unit
        }
        menuAction = null
    }

    LaunchedEffect(smartSelecting) {
        if (!smartSelecting) return@LaunchedEffect

        val base = filtered
        val inScope = base.filter { app ->
            when (appliedAppType) {
                AppsAppType.ALL -> true
                AppsAppType.USER -> !app.isSystemApp
                AppsAppType.SYSTEM -> app.isSystemApp
            }
        }

        val appCtx = context.applicationContext
        val chinaPackages = withContext(Dispatchers.Default) {
            inScope
                .asSequence()
                .filter { app -> AppClassifier.isChinaApp(appCtx, app.packageName, apkPathHint = app.apkPath) }
                .map { it.userScopedPackageName }
                .toSet()
        }

        delay(120)
        val computed = when (proxyMode) {
            AppsProxyMode.BLACKLIST, AppsProxyMode.CORE -> chinaPackages
            AppsProxyMode.WHITELIST -> inScope.map { it.userScopedPackageName }.toSet() - chinaPackages
        }

        selectedPackages = when (smartSelectApplyMode) {
            SmartSelectApplyMode.Replace -> computed
            SmartSelectApplyMode.Merge -> selectedPackages + computed
        }
        smartSelecting = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.pageBg)
    ) {
        if (showSmartSelectConfirm) {
            val accent = appAccentColor()

            AlertDialog(
                onDismissRequest = { showSmartSelectConfirm = false },
                containerColor = c.card,
                titleContentColor = c.textPrimary,
                textContentColor = c.textSecondary,
                title = { Text(text = stringResource(R.string.tools_apps_smart_select_title)) },
                text = {
                    Text(
                        text = stringResource(R.string.tools_apps_smart_select_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary
                    )
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                smartSelectApplyMode = SmartSelectApplyMode.Replace
                                showSmartSelectConfirm = false
                                smartSelecting = true
                            }
                        ) {
                            Text(text = stringResource(R.string.tools_apps_smart_select_replace), color = accent)
                        }
                        TextButton(
                            onClick = {
                                smartSelectApplyMode = SmartSelectApplyMode.Merge
                                showSmartSelectConfirm = false
                                smartSelecting = true
                            }
                        ) {
                            Text(text = stringResource(R.string.tools_apps_smart_select_merge), color = accent)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSmartSelectConfirm = false }) {
                        Text(text = stringResource(R.string.action_cancel), color = c.textPrimary)
                    }
                }
            )
        }

        if (smartSelecting) {
            AlertDialog(
                onDismissRequest = { },
                containerColor = c.card,
                titleContentColor = c.textPrimary,
                textContentColor = c.textSecondary,
                title = { Text(text = stringResource(R.string.tools_apps_smart_selecting_title)) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(color = c.textSecondary, strokeWidth = 3.dp, modifier = Modifier.size(18.dp))
                        Text(text = stringResource(R.string.tools_apps_smart_selecting_analyzing), style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                    }
                },
                confirmButton = { }
            )
        }

        if (showSortFilter) {
            val accent = appAccentColor()
            val isDark = ThemeManager.shouldUseDarkTheme()
            val chipBorder = BorderStroke(1.dp, c.divider.copy(alpha = if (isDark) 0.28f else 0.34f))
            val chipColors = FilterChipDefaults.filterChipColors(
                containerColor = c.cardAlt.copy(alpha = if (isDark) 0.72f else 0.86f),
                labelColor = c.textSecondary,
                selectedContainerColor = accent.copy(alpha = if (isDark) 0.22f else 0.16f),
                selectedLabelColor = accent,
                selectedLeadingIconColor = accent,
                selectedTrailingIconColor = accent
            )

            AlertDialog(
                onDismissRequest = { showSortFilter = false },
                containerColor = c.card,
                titleContentColor = c.textPrimary,
                textContentColor = c.textSecondary,
                title = { Text(text = stringResource(R.string.tools_apps_sort_filter_title)) },
                text = {
                    @OptIn(ExperimentalLayoutApi::class)
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        @Composable
                        fun chip(label: String, selected: Boolean, onClick: () -> Unit) {
                            FilterChip(
                                selected = selected,
                                onClick = onClick,
                                label = { Text(text = label) },
                                colors = chipColors,
                                border = chipBorder
                            )
                        }

                        Text(text = stringResource(R.string.tools_apps_sort_by), style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            chip(
                                label = stringResource(R.string.tools_apps_sort_name_asc),
                                selected = draftSortOrder == AppsSortOrder.NAME_ASC,
                                onClick = { draftSortOrder = AppsSortOrder.NAME_ASC }
                            )
                            chip(
                                label = stringResource(R.string.tools_apps_sort_name_desc),
                                selected = draftSortOrder == AppsSortOrder.NAME_DESC,
                                onClick = { draftSortOrder = AppsSortOrder.NAME_DESC }
                            )
                            chip(
                                label = stringResource(R.string.tools_apps_sort_install_asc),
                                selected = draftSortOrder == AppsSortOrder.INSTALL_TIME_ASC,
                                onClick = { draftSortOrder = AppsSortOrder.INSTALL_TIME_ASC }
                            )
                            chip(
                                label = stringResource(R.string.tools_apps_sort_install_desc),
                                selected = draftSortOrder == AppsSortOrder.INSTALL_TIME_DESC,
                                onClick = { draftSortOrder = AppsSortOrder.INSTALL_TIME_DESC }
                            )
                        }

                        Text(text = stringResource(R.string.tools_apps_app_type), style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            chip(
                                label = stringResource(R.string.tools_apps_filter_all),
                                selected = draftAppType == AppsAppType.ALL,
                                onClick = { draftAppType = AppsAppType.ALL }
                            )
                            chip(
                                label = stringResource(R.string.tools_apps_filter_user),
                                selected = draftAppType == AppsAppType.USER,
                                onClick = { draftAppType = AppsAppType.USER }
                            )
                            chip(
                                label = stringResource(R.string.tools_apps_filter_system),
                                selected = draftAppType == AppsAppType.SYSTEM,
                                onClick = { draftAppType = AppsAppType.SYSTEM }
                            )
                        }

                        Text(text = stringResource(R.string.tools_apps_network_permission), style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            chip(
                                label = stringResource(R.string.tools_apps_filter_all),
                                selected = draftNetworkFilter == AppsNetworkFilter.ALL,
                                onClick = { draftNetworkFilter = AppsNetworkFilter.ALL }
                            )
                            chip(
                                label = stringResource(R.string.tools_apps_filter_only_network),
                                selected = draftNetworkFilter == AppsNetworkFilter.ONLY_NETWORK,
                                onClick = { draftNetworkFilter = AppsNetworkFilter.ONLY_NETWORK }
                            )
                            chip(
                                label = stringResource(R.string.tools_apps_filter_exclude_network),
                                selected = draftNetworkFilter == AppsNetworkFilter.EXCLUDE_NETWORK,
                                onClick = { draftNetworkFilter = AppsNetworkFilter.EXCLUDE_NETWORK }
                            )
                        }

                        Text(text = stringResource(R.string.tools_apps_user_space), style = MaterialTheme.typography.titleMedium)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            chip(
                                label = stringResource(R.string.tools_apps_filter_all_users),
                                selected = draftUserFilter == AppsUserFilter.ALL,
                                onClick = { draftUserFilter = AppsUserFilter.ALL }
                            )
                            chip(
                                label = stringResource(R.string.tools_apps_filter_main_only),
                                selected = draftUserFilter == AppsUserFilter.MAIN_ONLY,
                                onClick = { draftUserFilter = AppsUserFilter.MAIN_ONLY }
                            )
                            chip(
                                label = stringResource(R.string.tools_apps_filter_work_clone),
                                selected = draftUserFilter == AppsUserFilter.WORK_ONLY,
                                onClick = { draftUserFilter = AppsUserFilter.WORK_ONLY }
                            )
                            chip(
                                label = stringResource(R.string.tools_apps_filter_other_users),
                                selected = draftUserFilter == AppsUserFilter.OTHER_ONLY,
                                onClick = { draftUserFilter = AppsUserFilter.OTHER_ONLY }
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val newSortOrder = draftSortOrder
                            val newAppType = draftAppType
                            val newNetworkFilter = draftNetworkFilter
                            val newUserFilter = draftUserFilter

                            appliedSortOrder = newSortOrder
                            appliedAppType = newAppType
                            appliedNetworkFilter = newNetworkFilter
                            appliedUserFilter = newUserFilter

                            filterPrefs.edit()
                                .putInt("sort_order", newSortOrder.ordinal)
                                .putInt("app_type", newAppType.ordinal)
                                .putInt("network_filter", newNetworkFilter.ordinal)
                                .putInt("user_filter", newUserFilter.ordinal)
                                .apply()

                            showSortFilter = false
                        }
                    ) {
                        Text(text = stringResource(R.string.action_apply), color = accent)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSortFilter = false }) {
                        Text(text = stringResource(R.string.action_cancel), color = c.textPrimary)
                    }
                }
            )
        }

        ErrorToast(
            message = saveError,
            onConsumed = { saveError = null }
        )

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
            item(key = "apps_card_header") {
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
                                val accent = appAccentColor()

                                val panel = c.cardAlt

                                Text(
                                    text = stringResource(R.string.tools_apps_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )

                                val selectedCount = remember(selectedPackages, filtered) {
                                    filtered.count { it.userScopedPackageName in selectedPackages }
                                }
                                Text(
                                    text = if (isCoreMode) {
                                        stringResource(R.string.tools_apps_header_summary_core, filtered.size)
                                    } else {
                                        stringResource(
                                            R.string.tools_apps_header_summary,
                                            filtered.size,
                                            proxyMode.name.lowercase().replaceFirstChar { it.uppercase() },
                                            selectedCount
                                        )
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = c.textSecondary
                                )

                                // Show user distribution if multiple users exist
                                val userCounts = remember(filtered, userDisplayNames) {
                                    filtered.groupBy { it.userId }.mapValues { it.value.size }
                                }
                                if (userCounts.size > 1) {
                                    Text(
                                        text = userCounts.entries.joinToString(" | ") { (userId, count) ->
                                            "${userDisplayNames[userId] ?: context.getString(R.string.tools_apps_user_fallback, userId)}: $count"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = c.textSecondary.copy(alpha = 0.8f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Box(modifier = Modifier.fillMaxWidth()) {
                                    val outerRadius = 22.dp
                                    val innerRadius = 18.dp
                                    val outerHeight = 44.dp
                                    val tabHeight = 34.dp
                                    val containerPadding = 5.dp
                                    BoxWithConstraints(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(outerHeight)
                                            .clip(RoundedRectangle(outerRadius))
                                            .background(panel)
                                            .padding(containerPadding)
                                    ) {
                                        val tabGap = 6.dp
                                        val tabCount = if (isBfr) 2 else 3
                                        val tabWidth = (maxWidth - tabGap * (tabCount - 1)) / tabCount

                                        val selectedIndex = when (proxyMode) {
                                            AppsProxyMode.BLACKLIST -> 0
                                            AppsProxyMode.WHITELIST -> 1
                                            AppsProxyMode.CORE -> if (isBfr) 0 else 2
                                        }

                                        val indicatorOffset by animateDpAsState(
                                            targetValue = (tabWidth + tabGap) * selectedIndex,
                                            animationSpec = tween(durationMillis = 260),
                                            label = "apps_proxy_indicator_offset"
                                        )

                                        Box(modifier = Modifier.fillMaxSize()) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedRectangle(innerRadius))
                                            )

                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.CenterStart)
                                                    .graphicsLayer { translationX = indicatorOffset.toPx() }
                                                    .size(width = tabWidth, height = tabHeight)
                                                    .clip(RoundedRectangle(innerRadius))
                                                    .background(accent.copy(alpha = 0.20f))
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(tabGap)
                                            ) {
                                                @Composable
                                                fun segment(label: String, selected: Boolean, onClick: () -> Unit) {
                                                    Box(
                                                        modifier = Modifier
                                                            .width(tabWidth)
                                                            .height(tabHeight)
                                                            .clip(RoundedRectangle(innerRadius))
                                                            .clickable(
                                                                interactionSource = remember { MutableInteractionSource() },
                                                                indication = null,
                                                                onClick = onClick
                                                            )
                                                            .padding(horizontal = 12.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = label,
                                                            style = MaterialTheme.typography.labelLarge,

                                                            color = if (selected) accent else c.textPrimary
                                                        )
                                                    }
                                                }

                                                segment(
                                                    label = stringResource(R.string.tools_apps_proxy_blacklist),
                                                    selected = proxyMode == AppsProxyMode.BLACKLIST,
                                                    onClick = { proxyMode = AppsProxyMode.BLACKLIST }
                                                )
                                                segment(
                                                    label = stringResource(R.string.tools_apps_proxy_whitelist),
                                                    selected = proxyMode == AppsProxyMode.WHITELIST,
                                                    onClick = { proxyMode = AppsProxyMode.WHITELIST }
                                                )
                                                if (!isBfr) {
                                                    segment(
                                                        label = stringResource(R.string.tools_apps_proxy_core),
                                                        selected = proxyMode == AppsProxyMode.CORE,
                                                        onClick = { proxyMode = AppsProxyMode.CORE }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

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
                                                text = stringResource(R.string.tools_apps_restart_tip_title),
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = accent
                                            )
                                            Text(
                                                text = if (hasUnsavedChanges) {
                                                    stringResource(R.string.tools_apps_restart_tip_body_pending)
                                                } else {
                                                    stringResource(R.string.tools_apps_restart_tip_body)
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = c.textPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (loading) {
                item(key = "apps_card_loading") {
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
                        }
                    }
                }
            } else if (filtered.isEmpty()) {
                item(key = "apps_card_empty") {
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (debouncedQuery.isNotBlank()) stringResource(R.string.tools_apps_no_results) else stringResource(R.string.tools_apps_no_apps),
                                style = MaterialTheme.typography.bodyMedium,
                                color = c.textSecondary
                            )
                        }
                    }
                }
            } else {
                val selectedApps = filtered.filter { it.userScopedPackageName in selectedPackages }
                val unselectedApps = filtered.filter { it.userScopedPackageName !in selectedPackages }
                val hasDivider = selectedApps.isNotEmpty() && unselectedApps.isNotEmpty()

                items(
                    count = selectedApps.size,
                    key = { index -> "sel:" + selectedApps[index].userScopedPackageName }
                ) { index ->
                    val app = selectedApps[index]

                    val isLastOfSelected = index == selectedApps.lastIndex
                    val isLastOverall = unselectedApps.isEmpty() && isLastOfSelected
                    val shape = if (isLastOverall) {
                        RoundedCornerShape(
                            topStart = 0.dp,
                            topEnd = 0.dp,
                            bottomStart = 18.dp,
                            bottomEnd = 18.dp
                        )
                    } else {
                        RoundedRectangle(0.dp)
                    }

                    ToolsRowBitmapIcon(
                        packageName = app.packageName,
                        fallbackIcon = Icons.Filled.Apps,
                        title = app.name,
                        subtitle = if (app.userId == 0) {
                            app.userScopedPackageName
                        } else {
                            "${app.userScopedPackageName} - ${userDisplayNames[app.userId] ?: stringResource(R.string.tools_apps_user_fallback, app.userId)}"
                        },
                        showDivider = !isLastOfSelected,
                        clipRow = false,
                        selected = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(c.card)
                            .padding(horizontal = 10.dp),
                        onClick = {
                            val key = app.userScopedPackageName
                            selectedPackages = if (key in selectedPackages) {
                                selectedPackages - key
                            } else {
                                selectedPackages + key
                            }
                        },
                        onLongClick = { openSystemAppDetails(app.packageName) }
                    )
                }

                item(key = "apps_selected_divider") {
                    val accent = appAccentColor()

                    AnimatedVisibility(
                        visible = hasDivider,
                        enter = fadeIn(tween(160)) + expandVertically(tween(200)),
                        exit = fadeOut(tween(120)) + shrinkVertically(tween(160))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(c.card)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.tools_apps_selected),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = c.textPrimary
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.tools_apps_selected_count, selectedApps.size),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = c.textSecondary
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = stringResource(R.string.tools_apps_others, unselectedApps.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = c.textSecondary
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .clip(RoundedRectangle(1.dp))
                                    .background(accent.copy(alpha = 0.25f))
                            )
                        }
                    }
                }

                items(
                    count = unselectedApps.size,
                    key = { index -> "unsel:" + unselectedApps[index].userScopedPackageName }
                ) { index ->
                    val app = unselectedApps[index]

                    val isLastOverall = index == unselectedApps.lastIndex
                    val shape = if (isLastOverall) {
                        RoundedCornerShape(
                            topStart = 0.dp,
                            topEnd = 0.dp,
                            bottomStart = 18.dp,
                            bottomEnd = 18.dp
                        )
                    } else {
                        RoundedRectangle(0.dp)
                    }

                    ToolsRowBitmapIcon(
                        packageName = app.packageName,
                        fallbackIcon = Icons.Filled.Apps,
                        title = app.name,
                        subtitle = if (app.userId == 0) {
                            app.userScopedPackageName
                        } else {
                            "${app.userScopedPackageName} - ${userDisplayNames[app.userId] ?: stringResource(R.string.tools_apps_user_fallback, app.userId)}"
                        },
                        showDivider = !isLastOverall,
                        clipRow = false,
                        selected = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shape)
                            .background(c.card)
                            .padding(horizontal = 10.dp),
                        onClick = {
                            val key = app.userScopedPackageName
                            selectedPackages = if (key in selectedPackages) {
                                selectedPackages - key
                            } else {
                                selectedPackages + key
                            }
                        },
                        onLongClick = { openSystemAppDetails(app.packageName) }
                    )
                }
            }

            item(key = "apps_section_spacer") { Spacer(modifier = Modifier.height(14.dp)) }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        CompositionLocalProvider(LocalLiquidBackdrop provides liquidBackdrop) {
            FloatingAppsManageTopBar(
                onBack = onBack,
                searchEnabled = searchVisible,
                query = query,
                onQueryChange = { query = it },
                onToggleSearch = { searchVisible = !searchVisible },
                saveEnabled = hasUnsavedChanges && !saving,
                onSave = {
                    if (saving) return@FloatingAppsManageTopBar
                    if (!hasUnsavedChanges) return@FloatingAppsManageTopBar
                    saving = true
                    scope.launch {
                        val mode = proxyMode.toConfigString()

                        val content = buildString {
                            selectedPackages
                                .asSequence()
                                .sorted()
                                .forEach { append(it).append('\n') }
                        }

                        val modeRes = ShellExecutor.execute(
                            "sed -i 's/^proxy_mode=.*/proxy_mode=\"$mode\"/' /data/adb/box/settings.ini"
                        )

                        val listRes = ConfigRepository.writeFile("package.list.cfg", content)

                        saving = false
                        if (modeRes.exitCode != 0) {
                            saveError = modeRes.stderr.ifBlank { modeRes.stdout }.ifBlank {
                                context.getString(R.string.tools_apps_failed_update_proxy_mode)
                            }
                            return@launch
                        }
                        if (listRes.error != null) {
                            saveError = listRes.error
                            return@launch
                        }

                        savedProxyMode = proxyMode
                        savedSelectedPackages = selectedPackages
                    }
                },
                onSortFilter = { showSortFilter = true },
                onMenuAction = { menuAction = it },
                menuExpanded = overflowMenuExpanded,
                onMenuExpandedChange = { overflowMenuExpanded = it },
                onMenuAnchorBoundsInRootChange = { overflowMenuAnchorBoundsInRoot = it },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .onGloballyPositioned { coordinates ->
                        val h = coordinates.size.height
                        if (h > 0) {
                            topBarHeightPx = h
                            lastNonZeroTopBarHeightPx = h
                        }
                    }
            )
        }

        CompositionLocalProvider(LocalLiquidBackdrop provides liquidBackdrop) {
            AppsOverflowMenu(
                expanded = overflowMenuExpanded,
                onDismissRequest = { overflowMenuExpanded = false },
                anchorBoundsInRoot = overflowMenuAnchorBoundsInRoot,
                onMenuAction = { action ->
                    overflowMenuExpanded = false
                    menuAction = action
                }
            )
        }
    }
}

private enum class AppsManageAction { Refresh, SmartSelect, SelectAll, Invert }

@Composable
private fun AppsOverflowMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    anchorBoundsInRoot: IntRect?,
    onMenuAction: (AppsManageAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = appColors()
    val isDark = ThemeManager.shouldUseDarkTheme()

    LiquidGlassDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        anchorBoundsInWindow = anchorBoundsInRoot,
        modifier = modifier,
        offset = DpOffset(x = 0.dp, y = 8.dp),
        shape = RoundedRectangle(16.dp),
        containerColor = c.cardAlt
    ) {
        val items = remember {
            listOf(
                AppsManageAction.Refresh,
                AppsManageAction.SmartSelect,
                AppsManageAction.SelectAll,
                AppsManageAction.Invert
            )
        }

        items.forEachIndexed { index, action ->
            val label = when (action) {
                AppsManageAction.Refresh -> stringResource(R.string.tools_apps_menu_refresh)
                AppsManageAction.SmartSelect -> stringResource(R.string.tools_apps_menu_smart_select)
                AppsManageAction.SelectAll -> stringResource(R.string.tools_apps_menu_select_all)
                AppsManageAction.Invert -> stringResource(R.string.tools_apps_menu_invert)
            }

            com.box.app.ui.components.NoRippleDropdownMenuItem(
                text = {
                    Text(
                        text = label,
                        color = c.textPrimary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                onClick = { onMenuAction(action) }
            )
        }
    }
}

@Composable
private fun FloatingAppsManageTopBar(
    onBack: () -> Unit,
    searchEnabled: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    saveEnabled: Boolean,
    onSave: () -> Unit,
    onSortFilter: () -> Unit,
    onMenuAction: (AppsManageAction) -> Unit,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    onMenuAnchorBoundsInRootChange: (IntRect?) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = appColors()
    val backdrop = requireNotNull(LocalLiquidBackdrop.current)
    val isDark = ThemeManager.shouldUseDarkTheme()
    val accent = appAccentColor()

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(searchEnabled) {
        if (searchEnabled) {
            delay(80)
            runCatching { focusRequester.requestFocus() }
            keyboardController?.show()
        } else {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    val selectedTint = if (isDark) Color(0xFF2B2F37) else Color(0xFFE3E6EA)
    val tint = selectedTint.copy(alpha = if (isDark) 0.22f else 0.30f)
    val fallback = selectedTint.copy(alpha = if (isDark) 0.82f else 0.74f)

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
                surfaceColor = tint,
            ) {
                Text(
                    text = stringResource(R.string.tools_apps_back_compact),
                    color = c.textPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            LiquidGlassIconButton(
                onClick = onToggleSearch,
                backdrop = backdrop,
                surfaceColor = tint,
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = if (searchEnabled) accent else c.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            LiquidGlassIconButton(
                onClick = onSortFilter,
                backdrop = backdrop,
                surfaceColor = tint,
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    tint = c.textPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            LiquidGlassIconButton(
                onClick = onSave,
                enabled = saveEnabled,
                backdrop = backdrop,
                surfaceColor = tint,
            ) {
                Box(modifier = Modifier.graphicsLayer(alpha = if (saveEnabled) 1f else 0.45f)) {
                    Icon(
                        imageVector = Icons.Filled.Save,
                        contentDescription = null,
                        tint = c.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.TopEnd)
                    .onGloballyPositioned { coords ->
                        val r = coords.boundsInWindow()
                        onMenuAnchorBoundsInRootChange(
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
                    surfaceColor = tint,
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = null,
                        tint = c.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = searchEnabled,
            enter = fadeIn(animationSpec = tween(durationMillis = 140)) +
                expandVertically(animationSpec = tween(durationMillis = 180)),
            exit = fadeOut(animationSpec = tween(durationMillis = 110)) +
                shrinkVertically(animationSpec = tween(durationMillis = 160))
        ) {
            val selectionColors = remember(accent) {
                TextSelectionColors(
                    handleColor = accent,
                    backgroundColor = accent.copy(alpha = 0.28f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CompositionLocalProvider(LocalTextSelectionColors provides selectionColors) {
                    LiquidGlassTextFieldPill(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        surfaceColor = tint
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp)
                        ) {
                            if (query.isBlank()) {
                                Text(
                                    text = stringResource(R.string.tools_apps_search_hint),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = c.textSecondary,
                                    modifier = Modifier.align(Alignment.CenterStart)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = query,
                                    onValueChange = onQueryChange,
                                    singleLine = true,
                                    cursorBrush = SolidColor(accent),
                                    textStyle = TextStyle(
                                        color = c.textPrimary,
                                        fontSize = MaterialTheme.typography.labelLarge.fontSize,
                                        fontWeight = MaterialTheme.typography.labelLarge.fontWeight
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(focusRequester)
                                )

                                AnimatedVisibility(
                                    visible = query.isNotBlank(),
                                    enter = fadeIn(animationSpec = tween(durationMillis = 90)),
                                    exit = fadeOut(animationSpec = tween(durationMillis = 90))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(Capsule())
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = {
                                                    onQueryChange("")
                                                    runCatching { focusRequester.requestFocus() }
                                                    keyboardController?.show()
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = null,
                                            tint = c.textSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

