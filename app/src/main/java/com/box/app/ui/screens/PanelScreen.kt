package com.box.app.ui.screens

import android.webkit.URLUtil
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Widgets

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.box.app.R
import com.box.app.data.backend.BoxApi
import com.box.app.data.repo.HomeRepository
import com.box.app.ui.components.bottomsheets.AppModalBottomSheet
import com.box.app.ui.miuix.HyperDialog
import com.box.app.ui.theme.appAccentColor
import com.box.app.ui.theme.appColors
import com.box.app.ui.web.clearWebViewAppData
import com.box.app.ui.web.ThemedWebView
import com.box.app.utils.ThemeManager
import com.box.app.utils.SystemBarMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PanelScreen(
    onNavigateBack: () -> Unit,
    backRequestKey: Int = 0,
    onCanGoBackChange: (Boolean) -> Unit = {}
) {
    val c = appColors()
    val accent = appAccentColor()
    val isDark = ThemeManager.shouldUseDarkTheme()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val sheetTitle = stringResource(R.string.panel_sheet_title)
    val addPanelTitle = stringResource(R.string.panel_sheet_add_title)
    val nameLabel = stringResource(R.string.panel_sheet_field_name)
    val urlLabel = stringResource(R.string.panel_sheet_field_url)
    val localName = stringResource(R.string.panel_local_name)
    val nameRequiredError = stringResource(R.string.panel_error_name_required)
    val urlInvalidError = stringResource(R.string.panel_error_url_invalid)

    val systemBarSettings by ThemeManager.systemBarSettings.collectAsState()

    val topInset = if (systemBarSettings.statusBar == SystemBarMode.OPAQUE) {
        0.dp
    } else {
        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    }
    val topBarHeight = 52.dp

    val prefs = remember(context) {
        context.getSharedPreferences("panel_cache", android.content.Context.MODE_PRIVATE)
    }
    val panelUrlKey = "panel_url_v1"
    val panelListKey = "panel_list_v1"
    val selectedPanelIdKey = "panel_selected_id_v1"
    val builtInPanelId = "local"
    val zashboardPanelId = "zashboard"
    val metaCubeXDPanelId = "metacubexd"

    @Stable
    data class PanelEntry(
        val id: String,
        val name: String,
        val url: String?,
        val isBuiltIn: Boolean
    )

    fun parseCustomPanels(raw: String?): List<PanelEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id").trim()
                    val name = obj.optString("name").trim()
                    val url = obj.optString("url").trim().ifBlank { null }
                    if (id.isNotBlank() && name.isNotBlank() && url != null) {
                        add(PanelEntry(id = id, name = name, url = url, isBuiltIn = false))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    fun persistCustomPanels(custom: List<PanelEntry>) {
        val arr = JSONArray()
        custom.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("url", p.url)
            arr.put(obj)
        }
        prefs.edit().putString(panelListKey, arr.toString()).apply()
    }

    val cachedPanelUrlAtStart = remember(prefs) {
        prefs.getString(panelUrlKey, null)?.takeIf { it.isNotBlank() }
    }

    val initialCustomPanels = remember(prefs) {
        parseCustomPanels(prefs.getString(panelListKey, null))
    }
    val initialSelectedPanelId = remember(prefs, selectedPanelIdKey, builtInPanelId) {
        prefs.getString(selectedPanelIdKey, null)?.takeIf { it.isNotBlank() } ?: builtInPanelId
    }
    val initialPanelUrl = remember(
        initialCustomPanels,
        initialSelectedPanelId,
        builtInPanelId,
        zashboardPanelId,
        metaCubeXDPanelId,
        cachedPanelUrlAtStart
    ) {
        when (initialSelectedPanelId) {
            builtInPanelId -> cachedPanelUrlAtStart
            zashboardPanelId -> "http://board.zash.run.place"
            metaCubeXDPanelId -> "https://metacubex.github.io/metacubexd"
            else -> initialCustomPanels.firstOrNull { it.id == initialSelectedPanelId }?.url
        }
    }

    var panelUrl by rememberSaveable { mutableStateOf<String?>(initialPanelUrl) }
    var panelLoading by rememberSaveable { mutableStateOf(false) }
    var webReloadKey by rememberSaveable { mutableStateOf(0) }
    var webSessionKey by rememberSaveable { mutableStateOf(0) }
    var panelWebError by rememberSaveable { mutableStateOf<String?>(null) }
    var pageTitle by rememberSaveable { mutableStateOf<String?>(null) }

    var refreshing by rememberSaveable { mutableStateOf(false) }
    var clearingWebData by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }

    val builtInPanel = remember(localName) {
        PanelEntry(id = builtInPanelId, name = localName, url = null, isBuiltIn = true)
    }

    val zashboardPanel = remember {
        PanelEntry(
            id = zashboardPanelId,
            name = "Zashboard",
            url = "http://board.zash.run.place",
            isBuiltIn = true
        )
    }

    val metaCubeXDPanel = remember {
        PanelEntry(
            id = metaCubeXDPanelId,
            name = "MetaCubeXD",
            url = "https://metacubex.github.io/metacubexd",
            isBuiltIn = true
        )
    }

    var customPanels by remember { mutableStateOf(initialCustomPanels) }
    var selectedPanelId by rememberSaveable { mutableStateOf(initialSelectedPanelId) }
    var showPanelSheet by rememberSaveable { mutableStateOf(false) }

    var newPanelName by rememberSaveable { mutableStateOf("") }
    var newPanelUrl by rememberSaveable { mutableStateOf("") }
    var addError by rememberSaveable { mutableStateOf<String?>(null) }

    val panels = remember(customPanels) {
        listOf(builtInPanel, zashboardPanel, metaCubeXDPanel) + customPanels
    }

    val selectedPanel = remember(panels, selectedPanelId) {
        panels.firstOrNull { it.id == selectedPanelId } ?: builtInPanel
    }

    fun persistCachedPanelUrl(url: String) {
        prefs.edit().putString(panelUrlKey, url).apply()
    }

    suspend fun resolvePanelUrlAndCache(): String? {
        val url = runCatching { BoxApi.getPanelUrl() }.getOrNull()?.takeIf { it.isNotBlank() }
        if (url != null) {
            persistCachedPanelUrl(url)
        }
        return url
    }

    suspend fun resolvePanelUrlAndCacheWithRetry(): String? {
        var resolved: String? = null
        for (attempt in 0 until 3) {
            resolved = resolvePanelUrlAndCache()
            if (!resolved.isNullOrBlank()) return resolved
            if (attempt != 2) delay(180)
        }
        return resolved
    }

    fun getCachedLocalPanelUrl(): String? {
        return prefs.getString(panelUrlKey, null)?.takeIf { it.isNotBlank() }
    }

    fun syncLocalPanelUrl(showLoading: Boolean) {
        scope.launch {
            if (showLoading) panelLoading = true
            try {
                val resolved = resolvePanelUrlAndCacheWithRetry()
                if (!resolved.isNullOrBlank() && selectedPanelId == builtInPanel.id) {
                    panelUrl = resolved
                }
            } finally {
                if (showLoading) panelLoading = false
            }
        }
    }

    fun applySelectedPanel(panel: PanelEntry) {
        val panelChanged = selectedPanelId != panel.id
        selectedPanelId = panel.id
        prefs.edit().putString(selectedPanelIdKey, panel.id).apply()

        panelWebError = null
        pageTitle = null
        onCanGoBackChange(false)
        if (panelChanged) {
            webSessionKey += 1
        }

        if (panel.id == builtInPanel.id) {
            val cachedLocal = getCachedLocalPanelUrl()
            panelUrl = cachedLocal
            syncLocalPanelUrl(showLoading = cachedLocal.isNullOrBlank())
        } else {
            panelUrl = panel.url
            // Keep local panel URL cache fresh even while currently using online panels.
            syncLocalPanelUrl(showLoading = false)
        }
    }

    LaunchedEffect(Unit) {
        HomeRepository.startPolling()
        val shouldShowLoading = selectedPanelId == builtInPanel.id && panelUrl.isNullOrBlank()
        syncLocalPanelUrl(showLoading = shouldShowLoading)
    }

    LaunchedEffect(panelUrl) {
        if (panelUrl.isNullOrBlank()) {
            onCanGoBackChange(false)
        }
    }

    @Composable
    fun NoRippleIconButton(
        enabled: Boolean = true,
        onClick: () -> Unit,
        content: @Composable () -> Unit
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }

    if (showClearCacheDialog) {
        HyperDialog(
            show = true,
            onDismissRequest = { showClearCacheDialog = false },
            title = stringResource(R.string.web_action_clear_cache),
            summary = stringResource(R.string.web_action_clear_cache_confirm_message),
            confirmText = stringResource(R.string.web_action_clear_cache_confirm),
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { showClearCacheDialog = false },
            onConfirm = {
                if (clearingWebData) return@HyperDialog
                showClearCacheDialog = false
                clearingWebData = true
                scope.launch {
                    val ok = clearWebViewAppData(context)
                    Toast
                        .makeText(
                            context,
                            context.getString(
                                if (ok) R.string.web_action_cache_cleared else R.string.web_action_cache_clear_failed
                            ),
                            Toast.LENGTH_SHORT
                        )
                        .show()
                    if (ok) {
                        panelWebError = null
                        webReloadKey += 1
                    }
                    clearingWebData = false
                }
            },
            content = {
                Text(
                    text = stringResource(R.string.web_action_clear_cache_confirm_message),
                    style = MiuixTheme.textStyles.body2,
                    color = c.textSecondary
                )
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topInset + topBarHeight)
        ) {
            when {
                !panelUrl.isNullOrBlank() -> {
                    ThemedWebView(
                        url = panelUrl!!,
                        isDark = isDark,
                        reloadKey = webReloadKey,
                        sessionKey = webSessionKey,
                        backRequestKey = backRequestKey,
                        hideUntilCommitVisible = false,
                        resetHistoryOnUrlChange = true,
                        onTitleChange = { pageTitle = it },
                        onPageFinished = { refreshing = false },
                        onWebError = { panelWebError = it },
                        onCanGoBackChange = onCanGoBackChange,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                panelLoading -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(42.dp),
                            color = c.textPrimary,
                            strokeWidth = 3.dp
                        )
                    }
                }

                else -> Box(modifier = Modifier.fillMaxSize())
            }

            if (!panelWebError.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .background(
                            color = c.cardAlt,
                            shape = RoundedRectangle(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = panelWebError!!,
                        style = MiuixTheme.textStyles.footnote1,
                        color = c.textSecondary
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                
                .height(topInset + topBarHeight)
                .padding(top = topInset, start = 0.dp, end = 0.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { }
                .zIndex(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NoRippleIconButton(
                onClick = onNavigateBack
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.action_back),
                    tint = c.textPrimary
                )
            }

            Text(
                text = pageTitle?.takeIf { it.isNotBlank() } ?: stringResource(R.string.panel_title),
                style = MiuixTheme.textStyles.title2,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            NoRippleIconButton(
                onClick = {
                    panelWebError = null
                    refreshing = true
                    if (selectedPanelId == builtInPanel.id) {
                        scope.launch {
                            val hadUrlBefore = !panelUrl.isNullOrBlank()
                            if (!hadUrlBefore) panelLoading = true
                            try {
                                val resolved = resolvePanelUrlAndCacheWithRetry()
                                if (!resolved.isNullOrBlank()) {
                                    panelUrl = resolved
                                }
                                if (!panelUrl.isNullOrBlank()) {
                                    webReloadKey += 1
                                } else {
                                    refreshing = false
                                }
                            } finally {
                                if (!hadUrlBefore) panelLoading = false
                            }
                        }
                    } else {
                        webReloadKey += 1
                    }
                },
                enabled = !panelLoading && !refreshing
            ) {
                if (panelLoading || refreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = c.textSecondary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.action_refresh_page),
                        tint = c.textPrimary
                    )
                }
            }

            NoRippleIconButton(
                onClick = {
                    addError = null
                    showPanelSheet = true
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Widgets,
                    contentDescription = null,
                    tint = c.textPrimary
                )
            }

            NoRippleIconButton(
                onClick = {
                    if (clearingWebData) return@NoRippleIconButton
                    showClearCacheDialog = true
                },
                enabled = !panelLoading && !refreshing && !clearingWebData
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.web_action_clear_cache),
                    tint = c.textPrimary
                )
            }
        }

        if (showPanelSheet) {
            AppModalBottomSheet(
                onDismissRequest = { showPanelSheet = false }
            ) {
                @Composable
                fun SheetHandle() {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(width = 28.dp, height = 3.dp)
                                .background(c.divider.copy(alpha = 0.42f), Capsule())
                        )
                    }
                }

                @Composable
                fun DividerLine() {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(c.divider)
                    )
                }

                val sheetScrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(sheetScrollState)
                        .padding(horizontal = 16.dp, vertical = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SheetHandle()

                    Text(
                        text = sheetTitle,
                        style = MiuixTheme.textStyles.title4,
                        color = c.textPrimary
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(c.cardAlt, shape = RoundedRectangle(16.dp))
                    ) {
                        panels.forEachIndexed { index, p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        applySelectedPanel(p)
                                        showPanelSheet = false
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(c.card, RoundedRectangle(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (p.id == builtInPanel.id) Icons.Filled.Home else Icons.Filled.Public,
                                        contentDescription = null,
                                        tint = c.textPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = p.name,
                                        style = MiuixTheme.textStyles.body1,
                                        color = c.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val displayUrl = if (p.isBuiltIn) {
                                        if (p.id == builtInPanel.id) getCachedLocalPanelUrl() ?: panelUrl else p.url
                                    } else {
                                        p.url
                                    }

                                    if (!displayUrl.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = displayUrl,
                                            style = MiuixTheme.textStyles.footnote1,
                                            color = c.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                if (p.id == selectedPanel.id) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                } else {
                                    Spacer(modifier = Modifier.width(24.dp))
                                }

                                if (!p.isBuiltIn) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                val next = customPanels.filterNot { it.id == p.id }
                                                customPanels = next
                                                persistCustomPanels(next)

                                                if (selectedPanelId == p.id) {
                                                    applySelectedPanel(builtInPanel)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = null,
                                            tint = c.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            if (index != panels.lastIndex) {
                                DividerLine()
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(c.cardAlt, shape = RoundedRectangle(16.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = addPanelTitle,
                            style = MiuixTheme.textStyles.body1,
                            color = c.textPrimary
                        )

                        Text(
                            text = nameLabel,
                            style = MiuixTheme.textStyles.footnote1,
                            color = c.textSecondary
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = c.card,
                                    shape = RoundedRectangle(12.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = newPanelName,
                                onValueChange = {
                                    newPanelName = it
                                    addError = null
                                },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = c.textPrimary,
                                    fontSize = MiuixTheme.textStyles.body1.fontSize
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (newPanelName.isBlank()) {
                                Text(
                                    text = nameLabel,
                                    color = c.textSecondary,
                                    style = MiuixTheme.textStyles.body1
                                )
                            }
                        }

                        Text(
                            text = urlLabel,
                            style = MiuixTheme.textStyles.footnote1,
                            color = c.textSecondary
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = c.card,
                                    shape = RoundedRectangle(12.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = newPanelUrl,
                                onValueChange = {
                                    newPanelUrl = it
                                    addError = null
                                },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    color = c.textPrimary,
                                    fontSize = MiuixTheme.textStyles.body1.fontSize
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (newPanelUrl.isBlank()) {
                                Text(
                                    text = urlLabel,
                                    color = c.textSecondary,
                                    style = MiuixTheme.textStyles.body1
                                )
                            }
                        }

                        if (!addError.isNullOrBlank()) {
                            Text(
                                text = addError!!,
                                color = c.textSecondary,
                                style = MiuixTheme.textStyles.footnote1
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    val name = newPanelName.trim()
                                    val url = newPanelUrl.trim()

                                    if (name.isBlank()) {
                                        addError = nameRequiredError
                                        return@TextButton
                                    }
                                    if (url.isBlank() || !(URLUtil.isNetworkUrl(url) || URLUtil.isValidUrl(url))) {
                                        addError = urlInvalidError
                                        return@TextButton
                                    }

                                    val id = "custom_" + System.currentTimeMillis().toString()
                                    val entry = PanelEntry(id = id, name = name, url = url, isBuiltIn = false)
                                    val next = (customPanels + entry)
                                    customPanels = next
                                    persistCustomPanels(next)

                                    newPanelName = ""
                                    newPanelUrl = ""
                                    addError = null
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.action_add),
                                    color = accent,
                                    style = MiuixTheme.textStyles.button
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(com.box.app.ui.components.systemNavBarBottomPadding().coerceAtMost(12.dp)))
                }
            }
        }
    }
}
