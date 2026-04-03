package com.box.app.ui.screens.tools

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.location.LocationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.box.app.R
import com.box.app.BuildConfig
import com.box.app.data.backend.BoxApi
import com.box.app.ui.components.ErrorToast
import com.box.app.ui.components.LiquidGlassButton
import com.box.app.ui.components.LiquidGlassIconButton
import com.box.app.ui.components.LocalLiquidBackdrop
import com.box.app.ui.components.SettingsToggleRow
import com.box.app.ui.components.ToolsRowIcon
import com.box.app.ui.components.ToolsSectionCard
import com.box.app.ui.components.contentPaddingWithNavBars
import com.box.app.ui.theme.appAccentColor
import com.box.app.ui.theme.appColors
import com.box.app.utils.ThemeManager
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private enum class WifiListMode { BLACKLIST, WHITELIST }

private enum class MacProxyMode { BLACKLIST, WHITELIST }

private enum class WifiPickType { SSID, BSSID }

private data class MacCandidate(
    val mac: String,
    val isSelected: Boolean = false
)

private data class WifiCandidate(
    val ssid: String,
    val bssid: String,
    val isCurrent: Boolean = false
)

@Composable
fun ToolsNetworkControlScreen(
    onNavVisibilityChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val c = appColors()
    val accent = appAccentColor()
    val context = LocalContext.current
    val isDarkTheme = ThemeManager.shouldUseDarkTheme()
    val pagePadding = 20.dp
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val isBfr = remember { BuildConfig.FLAVOR == "bfr" }

    val loadFailedText = stringResource(R.string.tools_network_control_load_failed)
    val saveFailedText = stringResource(R.string.tools_network_control_save_failed)

    val wifiPickerTitle = stringResource(R.string.tools_network_control_wifi_picker_title)
    val wifiPickerRefresh = stringResource(R.string.action_refresh)
    val wifiPickerLoading = stringResource(R.string.tools_network_control_wifi_picker_loading)
    val wifiPickerEmpty = stringResource(R.string.tools_network_control_wifi_picker_empty)
    val wifiPickerError = stringResource(R.string.tools_network_control_wifi_picker_error)
    val wifiPickerPermissionRequired = stringResource(R.string.tools_network_control_wifi_picker_permission_required)
    val wifiPickerLocationRequired = stringResource(R.string.tools_network_control_wifi_picker_location_required)

    val hotspotMacPickerTitle = stringResource(R.string.tools_network_control_hotspot_mac_picker_title)
    val hotspotMacPickerRefresh = stringResource(R.string.action_refresh)
    val hotspotMacPickerLoading = stringResource(R.string.tools_network_control_hotspot_mac_picker_loading)
    val hotspotMacPickerEmpty = stringResource(R.string.tools_network_control_hotspot_mac_picker_empty)
    val hotspotMacPickerError = stringResource(R.string.tools_network_control_hotspot_mac_picker_error)

    val liquidBackdrop = rememberLayerBackdrop()

    var topBarHeightPx by rememberSaveable { mutableStateOf(0) }
    var lastNonZeroTopBarHeightPx by rememberSaveable { mutableStateOf(0) }
    val effectiveTopBarHeightPx = if (topBarHeightPx > 0) topBarHeightPx else lastNonZeroTopBarHeightPx
    val topInset = with(density) { effectiveTopBarHeightPx.toDp() } + 16.dp

    LaunchedEffect(listState) {
        var last = listState.firstVisibleItemIndex * 10_000 + listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex * 10_000 + listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { now ->
                if (now > last) onNavVisibilityChange(false) else if (now < last) onNavVisibilityChange(true)
                last = now
            }
    }

    var enableNetworkControl by rememberSaveable { mutableStateOf(false) }
    var useOnWifiDisconnect by rememberSaveable { mutableStateOf(true) }
    var useOnWifi by rememberSaveable { mutableStateOf(true) }
    var enableSsidMatching by rememberSaveable { mutableStateOf(false) }
    var wifiListMode by rememberSaveable { mutableStateOf(WifiListMode.BLACKLIST) }
    var enableNetworkControlLog by rememberSaveable { mutableStateOf(false) }

    var macFilterEnable by rememberSaveable { mutableStateOf(false) }
    var macProxyMode by rememberSaveable { mutableStateOf(MacProxyMode.BLACKLIST) }
    var macs by rememberSaveable { mutableStateOf(listOf("")) }

    var ssids by rememberSaveable { mutableStateOf(listOf("")) }
    var bssids by rememberSaveable { mutableStateOf(listOf("")) }

    var initialEnableNetworkControl by rememberSaveable { mutableStateOf(false) }
    var initialUseOnWifiDisconnect by rememberSaveable { mutableStateOf(true) }
    var initialUseOnWifi by rememberSaveable { mutableStateOf(true) }
    var initialEnableSsidMatching by rememberSaveable { mutableStateOf(false) }
    var initialWifiListMode by rememberSaveable { mutableStateOf(WifiListMode.BLACKLIST) }
    var initialEnableNetworkControlLog by rememberSaveable { mutableStateOf(false) }
    var initialSsids by rememberSaveable { mutableStateOf(listOf("")) }
    var initialBssids by rememberSaveable { mutableStateOf(listOf("")) }

    var initialMacFilterEnable by rememberSaveable { mutableStateOf(false) }
    var initialMacProxyMode by rememberSaveable { mutableStateOf(MacProxyMode.BLACKLIST) }
    var initialMacs by rememberSaveable { mutableStateOf(listOf("")) }

    var loading by rememberSaveable { mutableStateOf(true) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    var wifiPickerVisible by rememberSaveable { mutableStateOf(false) }
    var wifiPickerTargetType by rememberSaveable { mutableStateOf(WifiPickType.SSID) }
    var wifiPickerTargetIndex by rememberSaveable { mutableStateOf(0) }
    var wifiPickerCandidates by remember { mutableStateOf(emptyList<WifiCandidate>()) }
    var wifiPickerLoadingState by remember { mutableStateOf(false) }
    var wifiPickerErrorText by remember { mutableStateOf<String?>(null) }

    var hotspotMacPickerVisible by rememberSaveable { mutableStateOf(false) }
    var hotspotMacPickerTargetIndex by rememberSaveable { mutableStateOf(0) }
    var hotspotMacPickerCandidates by remember { mutableStateOf(emptyList<MacCandidate>()) }
    var hotspotMacPickerLoadingState by remember { mutableStateOf(false) }
    var hotspotMacPickerErrorText by remember { mutableStateOf<String?>(null) }

    var wifiPermissionRequestedOnce by rememberSaveable { mutableStateOf(false) }

    fun refreshHotspotMacPicker() {
        scope.launch {
            hotspotMacPickerLoadingState = true
            hotspotMacPickerErrorText = null
            hotspotMacPickerCandidates = emptyList()

            val list = runCatching { BoxApi.getHotspotClientMacs() }.getOrElse {
                hotspotMacPickerErrorText = it.message ?: hotspotMacPickerError
                emptyList()
            }

            val selected = macs.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
            hotspotMacPickerCandidates = list
                .asSequence()
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
                .map { MacCandidate(mac = it, isSelected = selected.contains(it)) }
                .toList()

            if (hotspotMacPickerCandidates.isEmpty() && hotspotMacPickerErrorText.isNullOrBlank()) {
                hotspotMacPickerErrorText = hotspotMacPickerEmpty
            }
            hotspotMacPickerLoadingState = false
        }
    }

    val isDirty by remember {
        derivedStateOf {
            val normalized = ssids.map { it.trim() }.filter { it.isNotEmpty() }
            val initialNormalized = initialSsids.map { it.trim() }.filter { it.isNotEmpty() }
            val normalizedBssids = if (isBfr) emptyList() else bssids.map { it.trim() }.filter { it.isNotEmpty() }
            val initialNormalizedBssids = if (isBfr) emptyList() else initialBssids.map { it.trim() }.filter { it.isNotEmpty() }
            val normalizedMacs = if (isBfr) emptyList() else macs.map { it.trim() }.filter { it.isNotEmpty() }
            val initialNormalizedMacs = if (isBfr) emptyList() else initialMacs.map { it.trim() }.filter { it.isNotEmpty() }
            enableNetworkControl != initialEnableNetworkControl ||
                useOnWifiDisconnect != initialUseOnWifiDisconnect ||
                useOnWifi != initialUseOnWifi ||
                enableSsidMatching != initialEnableSsidMatching ||
                wifiListMode != initialWifiListMode ||
                enableNetworkControlLog != initialEnableNetworkControlLog ||
                (!isBfr && macFilterEnable != initialMacFilterEnable) ||
                (!isBfr && macProxyMode != initialMacProxyMode) ||
                normalized != initialNormalized ||
                normalizedBssids != initialNormalizedBssids ||
                normalizedMacs != initialNormalizedMacs
        }
    }

    ErrorToast(
        message = error,
        onConsumed = { error = null }
    )

    fun parseSetting(settings: String, key: String): String? {
        val regex = Regex("^${key}=\"?(.*?)\"?$", setOf(RegexOption.MULTILINE))
        return regex.find(settings)?.groupValues?.getOrNull(1)
    }

    fun normalizeSsid(raw: String): String {
        val s = raw.trim().trim('"')
        return if (s.isBlank() || s == "<unknown ssid>" || s == WifiManager.UNKNOWN_SSID) "<hidden>" else s
    }

    fun requiredWifiPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(
                Manifest.permission.NEARBY_WIFI_DEVICES,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun hasWifiPermissions(): Boolean {
        return requiredWifiPermissions().all { p ->
            ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isLocationEnabled(ctx: Context): Boolean {
        val lm = ctx.applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    @SuppressLint("MissingPermission")
    fun currentWifiInfo(ctx: Context): WifiInfo? {
        if (!hasWifiPermissions()) return null
        if (!isLocationEnabled(ctx)) return null

        val wifiManager = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null

        @Suppress("DEPRECATION")
        val info = wifiManager.connectionInfo

        // networkId == -1 usually means not connected
        if (info.networkId == -1) return null
        return info
    }

    @SuppressLint("MissingPermission")
    fun currentWifiSsid(ctx: Context): String? {
        val info = currentWifiInfo(ctx) ?: return null

        @Suppress("DEPRECATION")
        val ssidRaw = info.ssid.orEmpty()
        val ssid = normalizeSsid(ssidRaw)
        return ssid.takeIf { it != "<hidden>" }
    }

    @SuppressLint("MissingPermission")
    fun currentWifiCandidate(ctx: Context): WifiCandidate? {
        val info = currentWifiInfo(ctx) ?: return null

        @Suppress("DEPRECATION")
        val ssidRaw = info.ssid.orEmpty()

        val bssidRaw = info.bssid.orEmpty()

        val ssid = normalizeSsid(ssidRaw)
        val bssid = bssidRaw.trim().lowercase()

        if (bssid.isBlank()) return null
        if (bssid == "02:00:00:00:00:00") return null
        if (bssid == "00:00:00:00:00:00") return null

        return WifiCandidate(ssid = ssid, bssid = bssid)
    }

    @SuppressLint("MissingPermission")
    suspend fun loadWifiCandidates(): List<WifiCandidate> {
        if (!hasWifiPermissions()) return emptyList()

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return emptyList()

        val scanResults: List<ScanResult> = runCatching { wifiManager.scanResults }.getOrDefault(emptyList())
        val scannedRaw = scanResults
            .asSequence()
            .mapNotNull { r ->
                val ssidRaw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    r.wifiSsid?.toString().orEmpty()
                } else {
                    @Suppress("DEPRECATION")
                    r.SSID
                }
                val ssid = normalizeSsid(ssidRaw)
                val bssid = r.BSSID?.trim().orEmpty()
                if (bssid.isBlank()) return@mapNotNull null
                if (bssid == "02:00:00:00:00:00") return@mapNotNull null
                WifiCandidate(ssid = ssid, bssid = bssid.lowercase(), isCurrent = false)
            }
            .toList()

        val directCurrent = currentWifiCandidate(context)
        val currentFromScan = if (directCurrent == null) {
            val ssid = currentWifiSsid(context)
            ssid?.let { s -> scannedRaw.firstOrNull { it.ssid == s }?.copy(isCurrent = true) }
        } else {
            null
        }
        val current = directCurrent?.copy(isCurrent = true) ?: currentFromScan

        val scanned = scannedRaw
            .asSequence()
            .filterNot { c ->
                current != null && c.bssid == current.bssid && c.ssid == current.ssid
            }
            .distinctBy { it.bssid + "|" + it.ssid }
            .sortedWith(compareBy<WifiCandidate> { it.ssid }.thenBy { it.bssid })
            .toList()

        return listOfNotNull(current) + scanned
    }

    val wifiPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = requiredWifiPermissions().all { p -> result[p] == true }
        if (!granted) {
            wifiPickerErrorText = wifiPickerPermissionRequired
            return@rememberLauncherForActivityResult
        }

        scope.launch {
            wifiPickerLoadingState = true
            wifiPickerErrorText = null
            wifiPickerCandidates = emptyList()
            val list = runCatching { loadWifiCandidates() }.getOrElse {
                wifiPickerErrorText = it.message ?: wifiPickerError
                emptyList()
            }
            wifiPickerCandidates = list
            wifiPickerLoadingState = false
        }
    }

    LaunchedEffect(Unit) {
        if (wifiPermissionRequestedOnce) return@LaunchedEffect
        if (!hasWifiPermissions()) {
            wifiPermissionRequestedOnce = true
            wifiPermissionLauncher.launch(requiredWifiPermissions())
        }
    }

    fun refreshWifiPicker() {
        if (!hasWifiPermissions()) {
            wifiPermissionLauncher.launch(requiredWifiPermissions())
            return
        }

        if (!isLocationEnabled(context)) {
            wifiPickerErrorText = wifiPickerLocationRequired
            wifiPickerCandidates = emptyList()
            wifiPickerLoadingState = false
            return
        }

        scope.launch {
            wifiPickerLoadingState = true
            wifiPickerErrorText = null
            wifiPickerCandidates = emptyList()

            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                runCatching { wifiManager?.startScan() }
            }
            delay(800)

            val list = runCatching { loadWifiCandidates() }.getOrElse {
                wifiPickerErrorText = it.message ?: wifiPickerError
                emptyList()
            }
            wifiPickerCandidates = list
            wifiPickerLoadingState = false
        }
    }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        val settings = runCatching { BoxApi.getSettings() }.getOrNull().orEmpty()
        if (settings.isBlank()) {
            error = loadFailedText
            loading = false
            return@LaunchedEffect
        }

        val enable = parseSetting(settings, "enable_network_service_control")?.toBooleanStrictOrNull() ?: false
        val wifiDisconnect = parseSetting(settings, "use_module_on_wifi_disconnect")?.toBooleanStrictOrNull() ?: true
        val wifi = parseSetting(settings, "use_module_on_wifi")?.toBooleanStrictOrNull() ?: true
        val ssidMatching = parseSetting(settings, "use_ssid_matching")?.toBooleanStrictOrNull() ?: false
        val logEnabled = parseSetting(settings, "inotify_log_enabled")?.toBooleanStrictOrNull() ?: false

        val listModeRaw = parseSetting(settings, "use_wifi_list_mode")?.trim()?.lowercase() ?: "blacklist"
        val listMode = if (listModeRaw == "whitelist") WifiListMode.WHITELIST else WifiListMode.BLACKLIST

        val ssidArrayRaw = parseSetting(settings, "wifi_ssids_list")
        val parsedSsids = ssidArrayRaw?.let { BoxApi.parseBashArray(it) }.orEmpty()
        val safeSsids = if (parsedSsids.isEmpty()) listOf("") else parsedSsids

        val bssidArrayRaw = if (isBfr) null else parseSetting(settings, "wifi_bssids_list")
        val parsedBssids = bssidArrayRaw?.let { BoxApi.parseBashArray(it) }.orEmpty()
        val safeBssids = if (parsedBssids.isEmpty()) listOf("") else parsedBssids

        val macFilter = if (isBfr) false else parseSetting(settings, "mac_filter")?.toBooleanStrictOrNull() ?: false
        val macModeRaw = if (isBfr) "blacklist" else parseSetting(settings, "mac_mode")?.trim()?.lowercase() ?: "blacklist"
        val parsedMacMode = if (macModeRaw == "whitelist") MacProxyMode.WHITELIST else MacProxyMode.BLACKLIST
        val macArrayRaw = if (isBfr) null else parseSetting(settings, "macs_list")
        val parsedMacs = macArrayRaw?.let { BoxApi.parseBashArray(it) }.orEmpty()
        val safeMacs = if (parsedMacs.isEmpty()) listOf("") else parsedMacs

        enableNetworkControl = enable
        useOnWifiDisconnect = wifiDisconnect
        useOnWifi = wifi
        enableSsidMatching = ssidMatching
        wifiListMode = listMode
        enableNetworkControlLog = logEnabled
        ssids = safeSsids
        bssids = if (isBfr) listOf("") else safeBssids

        macFilterEnable = macFilter
        macProxyMode = parsedMacMode
        macs = if (isBfr) listOf("") else safeMacs

        initialEnableNetworkControl = enable
        initialUseOnWifiDisconnect = wifiDisconnect
        initialUseOnWifi = wifi
        initialEnableSsidMatching = ssidMatching
        initialWifiListMode = listMode
        initialEnableNetworkControlLog = logEnabled
        initialSsids = safeSsids
        initialBssids = if (isBfr) listOf("") else safeBssids

        initialMacFilterEnable = macFilter
        initialMacProxyMode = parsedMacMode
        initialMacs = if (isBfr) listOf("") else safeMacs

        loading = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.pageBg)
            .imePadding()
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
                    title = stringResource(R.string.tools_network_control_section_general_title),
                    subtitle = stringResource(R.string.tools_network_control_section_general_subtitle)
                ) {
                    SettingsToggleRow(
                        icon = Icons.Filled.Router,
                        title = stringResource(R.string.tools_network_control_enable_title),
                        subtitle = stringResource(R.string.tools_network_control_enable_subtitle),
                        checked = enableNetworkControl,
                        onCheckedChange = { enableNetworkControl = it }
                    )
                    SettingsToggleRow(
                        icon = Icons.Filled.Router,
                        title = stringResource(R.string.tools_network_control_use_on_wifi_disconnect_title),
                        subtitle = stringResource(R.string.tools_network_control_use_on_wifi_disconnect_subtitle),
                        checked = useOnWifiDisconnect,
                        onCheckedChange = { useOnWifiDisconnect = it },
                        enabled = enableNetworkControl
                    )
                    SettingsToggleRow(
                        icon = Icons.Filled.Router,
                        title = stringResource(R.string.tools_network_control_use_on_wifi_title),
                        subtitle = stringResource(R.string.tools_network_control_use_on_wifi_subtitle),
                        checked = useOnWifi,
                        onCheckedChange = { useOnWifi = it },
                        enabled = enableNetworkControl
                    )
                    SettingsToggleRow(
                        icon = Icons.Filled.Router,
                        title = stringResource(R.string.tools_network_control_enable_ssid_matching_title),
                        subtitle = stringResource(R.string.tools_network_control_enable_ssid_matching_subtitle),
                        checked = enableSsidMatching,
                        onCheckedChange = { enableSsidMatching = it },
                        enabled = enableNetworkControl
                    )
                    SettingsToggleRow(
                        icon = Icons.Filled.Router,
                        title = stringResource(R.string.tools_network_control_enable_log_title),
                        subtitle = stringResource(R.string.tools_network_control_enable_log_subtitle),
                        checked = enableNetworkControlLog,
                        onCheckedChange = { enableNetworkControlLog = it },
                        enabled = enableNetworkControl,
                        showDivider = false
                    )
                }
            }

            if (!isBfr) {
                item {
                    ToolsSectionCard(
                        title = stringResource(R.string.tools_network_control_hotspot_mac_title),
                        subtitle = stringResource(R.string.tools_network_control_hotspot_mac_subtitle)
                    ) {
                        SettingsToggleRow(
                            icon = Icons.Filled.Router,
                            title = stringResource(R.string.tools_network_control_hotspot_mac_enable_title),
                            subtitle = stringResource(R.string.tools_network_control_hotspot_mac_enable_subtitle),
                            checked = macFilterEnable,
                            onCheckedChange = { macFilterEnable = it }
                        )

                        ToolsRowIcon(
                            icon = if (macProxyMode == MacProxyMode.BLACKLIST) Icons.Filled.CheckCircle else Icons.Filled.Router,
                            title = stringResource(R.string.tools_network_control_hotspot_mac_mode_blacklist_title),
                            subtitle = stringResource(R.string.tools_network_control_hotspot_mac_mode_blacklist_subtitle),
                            enabled = macFilterEnable,
                            onClick = { macProxyMode = MacProxyMode.BLACKLIST }
                        )

                        ToolsRowIcon(
                            icon = if (macProxyMode == MacProxyMode.WHITELIST) Icons.Filled.CheckCircle else Icons.Filled.Router,
                            title = stringResource(R.string.tools_network_control_hotspot_mac_mode_whitelist_title),
                            subtitle = stringResource(R.string.tools_network_control_hotspot_mac_mode_whitelist_subtitle),
                            showDivider = false,
                            enabled = macFilterEnable,
                            onClick = { macProxyMode = MacProxyMode.WHITELIST }
                        )

                        EditableStringList(
                            icon = Icons.Filled.Router,
                            rows = macs,
                            hint = stringResource(R.string.tools_network_control_hint_enter_mac),
                            enabled = macFilterEnable,
                            onRowsChange = { macs = it },
                            onIconClick = { idx ->
                                hotspotMacPickerTargetIndex = idx
                                hotspotMacPickerVisible = true
                            }
                        )
                    }
                }
            }

            item {
                ToolsSectionCard(
                    title = stringResource(R.string.tools_network_control_ssid_mode_title),
                    subtitle = stringResource(R.string.tools_network_control_ssid_mode_subtitle)
                ) {
                    ToolsRowIcon(
                        icon = if (wifiListMode == WifiListMode.BLACKLIST) Icons.Filled.CheckCircle else Icons.Filled.Router,
                        title = stringResource(R.string.tools_network_control_ssid_mode_blacklist),
                        subtitle = stringResource(R.string.tools_network_control_ssid_mode_blacklist_subtitle),
                        enabled = enableNetworkControl,
                        onClick = { wifiListMode = WifiListMode.BLACKLIST }
                    )
                    ToolsRowIcon(
                        icon = if (wifiListMode == WifiListMode.WHITELIST) Icons.Filled.CheckCircle else Icons.Filled.Router,
                        title = stringResource(R.string.tools_network_control_ssid_mode_whitelist),
                        subtitle = stringResource(R.string.tools_network_control_ssid_mode_whitelist_subtitle),
                        showDivider = false,
                        enabled = enableNetworkControl,
                        onClick = { wifiListMode = WifiListMode.WHITELIST }
                    )
                }
            }

            item {
                ToolsSectionCard(
                    title = stringResource(R.string.tools_network_control_ssid_list_title),
                    subtitle = stringResource(R.string.tools_network_control_ssid_list_subtitle)
                ) {
                    EditableStringList(
                        icon = Icons.Filled.Router,
                        rows = ssids,
                        hint = stringResource(R.string.tools_network_control_hint_enter_ssid),
                        enabled = enableNetworkControl,
                        onRowsChange = { ssids = it },
                        onIconClick = { idx ->
                            wifiPickerTargetType = WifiPickType.SSID
                            wifiPickerTargetIndex = idx
                            wifiPickerVisible = true
                        }
                    )
                }
            }

            if (!isBfr) {
                item {
                    ToolsSectionCard(
                        title = stringResource(R.string.tools_network_control_bssid_list_title),
                        subtitle = stringResource(R.string.tools_network_control_bssid_list_subtitle)
                    ) {
                        EditableStringList(
                            icon = Icons.Filled.Router,
                            rows = bssids,
                            hint = stringResource(R.string.tools_network_control_hint_enter_bssid),
                            enabled = enableNetworkControl,
                            onRowsChange = { bssids = it },
                            onIconClick = { idx ->
                                wifiPickerTargetType = WifiPickType.BSSID
                                wifiPickerTargetIndex = idx
                                wifiPickerVisible = true
                            }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        CompositionLocalProvider(LocalLiquidBackdrop provides liquidBackdrop) {
            NetworkControlFloatingTopBar(
                saveEnabled = !loading && !saving && isDirty,
                onBack = onBack,
                onSave = {
                    scope.launch {
                        saving = true
                        error = null

                        val cleanedSsids = ssids.map { it.trim() }.filter { it.isNotEmpty() }
                        val cleanedBssids = if (isBfr) emptyList() else bssids.map { it.trim() }.filter { it.isNotEmpty() }
                        val cleanedMacs = if (isBfr) emptyList() else macs.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                        val listModeValue = if (wifiListMode == WifiListMode.WHITELIST) "whitelist" else "blacklist"
                        val macModeValue = if (macProxyMode == MacProxyMode.WHITELIST) "whitelist" else "blacklist"

                        val r1 = BoxApi.updateBooleanSetting("enable_network_service_control", enableNetworkControl)
                        val r2 = BoxApi.updateBooleanSetting("use_module_on_wifi_disconnect", useOnWifiDisconnect)
                        val r3 = BoxApi.updateBooleanSetting("use_module_on_wifi", useOnWifi)
                        val r4 = BoxApi.updateBooleanSetting("use_ssid_matching", enableSsidMatching)
                        val r5 = BoxApi.updateArraySetting("wifi_ssids_list", cleanedSsids)
                        val r6 = BoxApi.updateSetting("use_wifi_list_mode", listModeValue)
                        val r7 = BoxApi.updateBooleanSetting("inotify_log_enabled", enableNetworkControlLog)
                        val r8 = if (isBfr) true else BoxApi.updateArraySetting("wifi_bssids_list", cleanedBssids)

                        val r9 = if (isBfr) true else BoxApi.updateBooleanSetting("mac_filter", macFilterEnable)
                        val r10 = if (isBfr) true else BoxApi.updateSetting("mac_mode", macModeValue)
                        val r11 = if (isBfr) true else BoxApi.updateArraySetting("macs_list", cleanedMacs)

                        val ok = listOf(r1, r2, r3, r4, r5, r6, r7, r8, r9, r10, r11).all { it }
                        if (ok) {
                            initialEnableNetworkControl = enableNetworkControl
                            initialUseOnWifiDisconnect = useOnWifiDisconnect
                            initialUseOnWifi = useOnWifi
                            initialEnableSsidMatching = enableSsidMatching
                            initialWifiListMode = wifiListMode
                            initialEnableNetworkControlLog = enableNetworkControlLog
                            initialSsids = if (cleanedSsids.isEmpty()) listOf("") else cleanedSsids
                            initialBssids = if (isBfr) listOf("") else (if (cleanedBssids.isEmpty()) listOf("") else cleanedBssids)

                            if (!isBfr) {
                                initialMacFilterEnable = macFilterEnable
                                initialMacProxyMode = macProxyMode
                                initialMacs = if (cleanedMacs.isEmpty()) listOf("") else cleanedMacs
                            } else {
                                initialMacFilterEnable = false
                                initialMacProxyMode = MacProxyMode.BLACKLIST
                                initialMacs = listOf("")
                            }
                            error = null
                        } else {
                            error = saveFailedText
                        }
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

        if (wifiPickerVisible) {
            LaunchedEffect(wifiPickerVisible) {
                if (!wifiPickerVisible) return@LaunchedEffect
                refreshWifiPicker()
            }

            AlertDialog(
                onDismissRequest = { wifiPickerVisible = false },
                containerColor = c.card,
                tonalElevation = 0.dp,
                title = {
                    Text(
                        text = wifiPickerTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = c.textPrimary
                    )
                },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                    ) {
                        when {
                            wifiPickerLoadingState -> {
                                Row(
                                    modifier = Modifier.align(Alignment.Center),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text(text = wifiPickerLoading, color = c.textSecondary)
                                }
                            }

                            !wifiPickerErrorText.isNullOrBlank() -> {
                                Text(
                                    text = wifiPickerErrorText.orEmpty(),
                                    color = c.textSecondary,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }

                            wifiPickerCandidates.isEmpty() -> {
                                Text(
                                    text = wifiPickerEmpty,
                                    color = c.textSecondary,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }

                            else -> {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(wifiPickerCandidates.size) { idx ->
                                        val item = wifiPickerCandidates[idx]
                                        val interactionSource = remember { MutableInteractionSource() }
                                        val bgColor = if (item.isCurrent) {
                                            accent.copy(alpha = if (isDarkTheme) 0.20f else 0.12f)
                                        } else {
                                            c.cardAlt
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedRectangle(14.dp))
                                                .background(bgColor)
                                                .clickable(
                                                    interactionSource = interactionSource,
                                                    indication = null
                                                ) {
                                                    when (wifiPickerTargetType) {
                                                        WifiPickType.SSID -> {
                                                            val next = ssids.toMutableList()
                                                            if (wifiPickerTargetIndex in next.indices) next[wifiPickerTargetIndex] = item.ssid
                                                            ssids = next
                                                        }

                                                        WifiPickType.BSSID -> {
                                                            val next = bssids.toMutableList()
                                                            if (wifiPickerTargetIndex in next.indices) next[wifiPickerTargetIndex] = item.bssid
                                                            bssids = next
                                                        }
                                                    }
                                                    wifiPickerVisible = false
                                                }
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = item.ssid,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = if (item.isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                                                    color = c.textPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = item.bssid,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = c.textSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { refreshWifiPicker() }) {
                        Text(text = wifiPickerRefresh, color = accent)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { wifiPickerVisible = false }) {
                        Text(text = stringResource(R.string.action_cancel), color = c.textPrimary)
                    }
                }
            )
        }

        if (hotspotMacPickerVisible) {
            LaunchedEffect(hotspotMacPickerVisible) {
                if (!hotspotMacPickerVisible) return@LaunchedEffect
                refreshHotspotMacPicker()
            }

            AlertDialog(
                onDismissRequest = { hotspotMacPickerVisible = false },
                containerColor = c.card,
                tonalElevation = 0.dp,
                title = {
                    Text(
                        text = hotspotMacPickerTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = c.textPrimary
                    )
                },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                    ) {
                        when {
                            hotspotMacPickerLoadingState -> {
                                Row(
                                    modifier = Modifier.align(Alignment.Center),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Text(text = hotspotMacPickerLoading, color = c.textSecondary)
                                }
                            }

                            !hotspotMacPickerErrorText.isNullOrBlank() && hotspotMacPickerCandidates.isEmpty() -> {
                                Text(
                                    text = hotspotMacPickerErrorText.orEmpty(),
                                    color = c.textSecondary,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }

                            hotspotMacPickerCandidates.isEmpty() -> {
                                Text(
                                    text = hotspotMacPickerEmpty,
                                    color = c.textSecondary,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }

                            else -> {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .align(Alignment.TopCenter),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(hotspotMacPickerCandidates.size) { idx ->
                                        val item = hotspotMacPickerCandidates[idx]
                                        val interactionSource = remember { MutableInteractionSource() }
                                        val bgColor = if (item.isSelected) {
                                            accent.copy(alpha = if (isDarkTheme) 0.20f else 0.12f)
                                        } else {
                                            c.cardAlt
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedRectangle(14.dp))
                                                .background(bgColor)
                                                .clickable(
                                                    interactionSource = interactionSource,
                                                    indication = null
                                                ) {
                                                    val next = macs.toMutableList().ifEmpty { mutableListOf("") }
                                                    if (hotspotMacPickerTargetIndex in next.indices) {
                                                        next[hotspotMacPickerTargetIndex] = item.mac
                                                    }
                                                    macs = next
                                                    hotspotMacPickerVisible = false
                                                }
                                                .padding(horizontal = 16.dp, vertical = 14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item.mac,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = if (item.isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                                color = c.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { refreshHotspotMacPicker() }) {
                        Text(text = hotspotMacPickerRefresh, color = accent)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { hotspotMacPickerVisible = false }) {
                        Text(text = stringResource(R.string.action_cancel), color = c.textPrimary)
                    }
                }
            )
        }
    }
}

@Composable
private fun NetworkControlFloatingTopBar(
    saveEnabled: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = appColors()
    val backdrop = requireNotNull(LocalLiquidBackdrop.current)
    val isDark = ThemeManager.shouldUseDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }

    val selectedTint = if (isDark) Color(0xFF2B2F37) else Color(0xFFE3E6EA)
    val tint = selectedTint.copy(alpha = 0.25f)
    val fallback = selectedTint.copy(alpha = 0.80f)

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
}

@Composable
private fun EditableStringList(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    rows: List<String>,
    hint: String,
    enabled: Boolean = true,
    onRowsChange: (List<String>) -> Unit,
    onIconClick: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val c = appColors()

    val safeRows = if (rows.isEmpty()) listOf("") else rows

    Column(modifier = modifier.alpha(if (enabled) 1f else 0.45f)) {
        safeRows.forEachIndexed { idx, row ->
            val isLast = idx == safeRows.lastIndex
            EditableStringRow(
                icon = icon,
                value = row,
                hint = hint,
                enabled = enabled,
                showDivider = !isLast,
                onIconClick = { if (enabled) onIconClick?.invoke(idx) },
                onValueChange = { v: String ->
                    val next = safeRows.toMutableList()
                    next[idx] = v
                    onRowsChange(next)
                },
                onDelete = {
                    val next = safeRows.toMutableList()
                    if (next.size <= 1) {
                        next[0] = ""
                    } else {
                        next.removeAt(idx)
                    }
                    onRowsChange(next)
                }
            )
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) {
                onRowsChange(safeRows + "")
            }
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(c.cardAlt, shape = RoundedRectangle(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = c.textPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = stringResource(R.string.action_add),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary
            )
            Text(
                text = stringResource(R.string.tools_network_control_append_item),
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EditableStringRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    hint: String,
    enabled: Boolean = true,
    showDivider: Boolean,
    onIconClick: (() -> Unit)? = null,
    onValueChange: (String) -> Unit,
    onDelete: () -> Unit
) {
    val c = appColors()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedRectangle(10.dp))
                .clickable(
                    enabled = enabled && onIconClick != null,
                    onClick = { onIconClick?.invoke() }
                )
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

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            placeholder = {
                Text(text = hint, style = MaterialTheme.typography.bodyMedium)
            },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = c.textPrimary),
            shape = RoundedRectangle(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = c.divider,
                unfocusedBorderColor = c.divider,
                focusedContainerColor = c.cardAlt,
                unfocusedContainerColor = c.cardAlt,
                cursorColor = c.textPrimary
            )
        )

        IconButton(onClick = onDelete, enabled = enabled) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                tint = c.textSecondary
            )
        }
    }

    if (showDivider) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 52.dp)
                .height(1.dp)
                .background(c.divider)
        )
    }
}
