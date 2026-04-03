package com.box.app.ui.screens

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.roundToInt

import com.box.app.data.backend.BoxApi
import com.box.app.data.backend.ShellExecutor
import com.box.app.data.repo.HomeRepository
import com.box.app.data.repo.ConfigRepository
import com.box.app.ui.components.SettingsToggleRow
import com.box.app.ui.components.ToolsRowIcon
import com.box.app.ui.components.ToolsSectionCard
import com.box.app.ui.components.bottomsheets.AboutBottomSheet
import com.box.app.ui.components.bottomsheets.AppModalBottomSheet
import com.box.app.ui.components.bottomsheets.UpdateSheetMode
import com.box.app.ui.components.bottomsheets.UpdateBottomSheet
import com.box.app.ui.components.contentPaddingWithNavBars
import com.box.app.ui.components.LocalFloatingNavBarSpaceDp
import com.box.app.ui.dialogs.LanguageSelectionDialog
import com.box.app.ui.dialogs.SystemBarSelectionDialog
import com.box.app.ui.dialogs.ThemeSelectionDialog
import com.box.app.ui.theme.appAccentColor
import com.box.app.ui.theme.appColors
import com.box.app.ui.screens.Settings.OpenSourceLicensesScreen
import com.box.app.utils.AppLanguage
import com.box.app.utils.LanguageManager
import com.box.app.utils.LatencyTarget
import com.box.app.utils.LatencyTargetsManager
import com.box.app.utils.ThemeManager
import com.box.app.utils.ThemeMode
import com.box.app.utils.UiScaleManager
import com.box.app.utils.UpdateCheckManager
import com.box.app.BuildConfig
import com.box.app.R
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavVisibilityChange: (Boolean) -> Unit,
    onMainPagerUserScrollEnabledChange: (Boolean) -> Unit = {},
    onMainTabAtRootChange: (Boolean) -> Unit = {}
) {
    val c = appColors()
    val pagePadding = 16.dp
    val listState = rememberLazyListState()

    var showOpenSourceLicenses by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(showOpenSourceLicenses) {
        val atRoot = !showOpenSourceLicenses
        onMainPagerUserScrollEnabledChange(atRoot)
        onMainTabAtRootChange(atRoot)
        if (!atRoot) {
            onNavVisibilityChange(false)
        }
    }

    var containerWidthPx by remember { mutableFloatStateOf(0f) }

    val scope = rememberCoroutineScope()
    val transition = remember { androidx.compose.animation.core.Animatable(0f) }

    LaunchedEffect(showOpenSourceLicenses) {
        val target = if (showOpenSourceLicenses) 1f else 0f
        if (transition.targetValue == target && transition.value == target) return@LaunchedEffect
        transition.animateTo(
            targetValue = target,
            animationSpec = tween(durationMillis = 320)
        )
    }

    if (showOpenSourceLicenses) {
        PredictiveBackHandler {
                progress: Flow<androidx.activity.BackEventCompat> ->
            try {
                progress.collect { backEvent ->
                    transition.snapTo((1f - backEvent.progress).coerceIn(0f, 1f))
                }
                showOpenSourceLicenses = false
            } catch (e: CancellationException) {
                scope.launch {
                    transition.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 220)
                    )
                }
                throw e
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size -> containerWidthPx = size.width.toFloat() }
    ) {
        val w = containerWidthPx
        val t = transition.value

        val mainX = if (w > 0f) (-w / 3f) * t else 0f
        val licensesX = if (w > 0f) w * (1f - t) else 0f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = mainX
                    alpha = 1f
                }
        ) {
            SettingsMainContent(
                onNavVisibilityChange = onNavVisibilityChange,
                listState = listState,
                pagePadding = pagePadding,
                c = c,
                onOpenOpenSourceLicenses = { showOpenSourceLicenses = true }
            )
        }

        if (t > 0f || showOpenSourceLicenses) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = licensesX
                        alpha = t
                    }
            ) {
                CompositionLocalProvider(LocalFloatingNavBarSpaceDp provides 0.dp) {
                    OpenSourceLicensesScreen(
                        onBack = { showOpenSourceLicenses = false },
                        onNavVisibilityChange = onNavVisibilityChange,
                        enableBackHandler = false
                    )
                }
            }
        }
    }

    return

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsMainContent(
    onNavVisibilityChange: (Boolean) -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    pagePadding: androidx.compose.ui.unit.Dp,
    c: com.box.app.ui.theme.AppColors,
    onOpenOpenSourceLicenses: () -> Unit
) {

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

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = appAccentColor()
    val isDark = ThemeManager.shouldUseDarkTheme()
    val currentThemeMode by ThemeManager.themeMode.collectAsState()
    val trueBlackEnabled by ThemeManager.trueBlack.collectAsState()
    val systemBarSettings by ThemeManager.systemBarSettings.collectAsState()
    val liquidGlassTranslucent by ThemeManager.liquidGlassTranslucent.collectAsState()
    val liquidGlassBlurDp by ThemeManager.liquidGlassBlurDp.collectAsState()
    val liquidGlassLensStrength by ThemeManager.liquidGlassLensStrength.collectAsState()
    val bottomSheetBlur by ThemeManager.bottomSheetBlur.collectAsState()
    val blurEffectsEnabled by ThemeManager.blurEffectsEnabled.collectAsState()
    val blurEffectsSupported = ThemeManager.supportsBlurEffects()
    val blurEffectsActive = blurEffectsEnabled && blurEffectsSupported
    val uiScalePercent by UiScaleManager.uiScalePercent.collectAsState()
    val currentLanguage by LanguageManager.language.collectAsState()
    val latencyTargets by LatencyTargetsManager.targets.collectAsState()
    val updateCheckStatus by UpdateCheckManager.status.collectAsState()

    var pendingUiScalePercent by rememberSaveable { mutableStateOf(uiScalePercent.toFloat()) }
    var uiScaleDragging by remember { mutableStateOf(false) }

    LaunchedEffect(uiScalePercent) {
        if (!uiScaleDragging) pendingUiScalePercent = uiScalePercent.toFloat()
    }

    var showThemeDialog by remember { mutableStateOf(false) }
    var showSystemBarDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showAppearanceMoreSheet by remember { mutableStateOf(false) }
    var showBackupRestoreDialog by remember { mutableStateOf(false) }

    val appPrefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }
    var openPanelOnLaunch by rememberSaveable {
        mutableStateOf(appPrefs.getBoolean("open_panel_on_launch", false))
    }

    var enableNotifications by rememberSaveable {
        mutableStateOf(appPrefs.getBoolean("enable_notifications", false))
    }

    var autoStart by remember { mutableStateOf(false) }
    var acceleratedDownload by remember { mutableStateOf(false) }

    val subPrefs = remember { context.getSharedPreferences("subscription_settings", Context.MODE_PRIVATE) }
    var useClashApi by rememberSaveable { mutableStateOf(subPrefs.getBoolean("use_clash_api", false)) }
    var useClashApiForNetSpeed by rememberSaveable { mutableStateOf(subPrefs.getBoolean("use_clash_api_net_speed", false)) }
    val defaultProxyTrafficFilter = "DIRECT,REJECT"
    var proxyTrafficFilterText by rememberSaveable {
        mutableStateOf(subPrefs.getString("proxy_traffic_filter_chains", defaultProxyTrafficFilter) ?: defaultProxyTrafficFilter)
    }

    var showAboutDialog by remember { mutableStateOf(false) }
    var appVersionText by remember { mutableStateOf("") }
    var moduleVersionText by remember { mutableStateOf("-") }
    var updateResult by remember { mutableStateOf<com.box.app.data.model.UpdateCheckResult?>(null) }
    var updateTarget by remember { mutableStateOf(UpdateTarget.MODULE) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var lastDownloadId by remember { mutableStateOf<Long?>(null) }
    var lastDownloadMime by remember { mutableStateOf<String?>(null) }

    var latencyDirty by remember { mutableStateOf(false) }
    var latencyName1 by rememberSaveable { mutableStateOf("") }
    var latencyUrl1 by rememberSaveable { mutableStateOf("") }
    var latencyName2 by rememberSaveable { mutableStateOf("") }
    var latencyUrl2 by rememberSaveable { mutableStateOf("") }
    var latencyName3 by rememberSaveable { mutableStateOf("") }
    var latencyUrl3 by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(latencyTargets) {
        if (latencyDirty) return@LaunchedEffect
        val t1 = latencyTargets.getOrNull(0)
        val t2 = latencyTargets.getOrNull(1)
        val t3 = latencyTargets.getOrNull(2)
        if (t1 != null) {
            latencyName1 = t1.name
            latencyUrl1 = t1.url
        }
        if (t2 != null) {
            latencyName2 = t2.name
            latencyUrl2 = t2.url
        }
        if (t3 != null) {
            latencyName3 = t3.name
            latencyUrl3 = t3.url
        }
    }

    val appIconDrawable = remember {
        runCatching { context.applicationInfo.loadIcon(context.packageManager) }.getOrNull()
    }
    val appIconPainter: Painter? = remember(appIconDrawable) {
        val bmp = appIconDrawable?.toBitmap(width = 128, height = 128)
        bmp?.asImageBitmap()?.let { BitmapPainter(it) }
    }

    DisposableEffect(useClashApi) {
        subPrefs.edit().putBoolean("use_clash_api", useClashApi).apply()
        HomeRepository.setUseClashApiForSubscription(useClashApi)
        onDispose { }
    }

    DisposableEffect(useClashApiForNetSpeed) {
        subPrefs.edit().putBoolean("use_clash_api_net_speed", useClashApiForNetSpeed).apply()
        HomeRepository.setUseClashApiForNetSpeed(useClashApiForNetSpeed)
        onDispose { }
    }

    fun parseProxyTrafficFilter(text: String): List<String> {
        return text
            .split(',', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    DisposableEffect(openPanelOnLaunch) {
        appPrefs.edit().putBoolean("open_panel_on_launch", openPanelOnLaunch).apply()
        onDispose { }
    }

    DisposableEffect(enableNotifications) {
        appPrefs.edit().putBoolean("enable_notifications", enableNotifications).apply()
        onDispose { }
    }

    DisposableEffect(proxyTrafficFilterText) {
        subPrefs.edit().putString("proxy_traffic_filter_chains", proxyTrafficFilterText).apply()
        BoxApi.setProxyTrafficFilterChains(parseProxyTrafficFilter(proxyTrafficFilterText))
        BoxApi.resetProxyTrafficSampler()
        onDispose { }
    }

    LaunchedEffect(Unit) {
        if (BuildConfig.FLAVOR != "bfr") {
            val boot = runCatching { BoxApi.getBooleanSetting("boot_auto_start") }.getOrNull()
            if (boot != null) autoStart = boot
            val ghProxy = runCatching { BoxApi.getBooleanSetting("use_ghproxy") }.getOrNull()
            if (ghProxy != null) acceleratedDownload = ghProxy
        }

        // app version
        val pkg = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        appVersionText = pkg?.versionName?.takeIf { it.isNotBlank() } ?: "-"
    }

    suspend fun refreshModuleVersion() {
        val prop = runCatching { BoxApi.getModuleProp() }.getOrNull()
        val ver = prop?.let { BoxApi.parseModuleVersion(it) }
        moduleVersionText = ver ?: context.getString(R.string.settings_module_not_installed)
    }

    suspend fun checkForModuleUpdates() {
        updateTarget = UpdateTarget.MODULE
        val result = runCatching { BoxApi.checkForModuleUpdates() }.getOrNull()
        updateResult = result
        UpdateCheckManager.setModuleUpdateAvailable(result?.hasUpdate == true)
    }

    suspend fun checkForAppUpdates() {
        updateTarget = UpdateTarget.APP
        val result = runCatching { BoxApi.checkForAppUpdates(appVersionText) }.getOrNull()
        updateResult = result
        UpdateCheckManager.setAppUpdateAvailable(result?.hasUpdate == true)
    }

    val downloadOkHttpClient: OkHttpClient by remember {
        mutableStateOf(
            OkHttpClient.Builder().build()
        )
    }

    fun mimeTypeFromFileName(name: String): String {
        val n = name.lowercase()
        return when {
            n.endsWith(".zip") -> "application/zip"
            n.endsWith(".tar.gz") -> "application/gzip"
            n.endsWith(".tgz") -> "application/gzip"
            n.endsWith(".tar") -> "application/x-tar"
            n.endsWith(".apk") -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }

    suspend fun downloadReleaseAsset(
        release: com.box.app.data.model.ReleaseInfo,
        fallbackFileName: String,
        onProgress: (Int?) -> Unit = {}
    ): Boolean {
        fun reportProgress(percent: Int?) {
            onProgress(percent?.coerceIn(0, 100))
        }

        val url = release.downloadUrl.trim()
        if (url.isBlank()) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.url))) }
            return false
        }

        val fileName = runCatching {
            Uri.parse(url).lastPathSegment?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: fallbackFileName
        val mime = mimeTypeFromFileName(fileName)
        reportProgress(0)

        if (Build.VERSION.SDK_INT >= 29) {
            val uri = withContext(Dispatchers.IO) {
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val targetUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext null

                try {
                    val req = Request.Builder().get().url(url).build()
                    val resp = downloadOkHttpClient.newCall(req).execute()
                    try {
                        if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                        val body = resp.body
                        val totalBytes = body.contentLength()
                        var downloadedBytes = 0L
                        var lastPercent = -1
                        resolver.openOutputStream(targetUri, "w")?.use { out ->
                            body.byteStream().use { input ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    out.write(buffer, 0, read)
                                    downloadedBytes += read
                                    if (totalBytes > 0L) {
                                        val percent = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                        if (percent != lastPercent) {
                                            lastPercent = percent
                                            onProgress(percent)
                                        }
                                    }
                                }
                            }
                            out.flush()
                        } ?: throw IOException("Open output stream failed")
                    } finally {
                        runCatching { resp.close() }
                    }

                    ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    }.also { doneValues ->
                        resolver.update(targetUri, doneValues, null, null)
                    }

                    targetUri
                } catch (_: Exception) {
                    runCatching { resolver.delete(targetUri, null, null) }
                    null
                }
            }

            if (uri == null) {
                Toast.makeText(context, context.getString(R.string.settings_failed_check_updates), Toast.LENGTH_SHORT).show()
                return false
            }
            reportProgress(100)

            runCatching {
                val viewIntent = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, mime)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                context.startActivity(viewIntent)
            }
            return true
        }

        return withContext(Dispatchers.IO) {
            try {
                val req = DownloadManager.Request(Uri.parse(url))
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    .setMimeType(mime)

                val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val downloadId = dm.enqueue(req)
                withContext(Dispatchers.Main) {
                    lastDownloadId = downloadId
                    lastDownloadMime = mime
                }

                var lastPercent = -1
                var completed: Boolean? = null
                while (completed == null) {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = dm.query(query)
                    if (cursor == null) {
                        completed = false
                        break
                    }
                    cursor.use {
                        if (cursor.moveToFirst()) {
                            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))

                            if (total > 0L) {
                                val percent = ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent)
                                }
                            }

                            when (status) {
                                DownloadManager.STATUS_SUCCESSFUL -> {
                                    onProgress(100)
                                    completed = true
                                }
                                DownloadManager.STATUS_FAILED -> completed = false
                            }
                        }
                    }

                    if (completed == null) {
                        delay(220)
                    }
                }
                completed == true
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun downloadModule(
        release: com.box.app.data.model.ReleaseInfo,
        onProgress: (Int?) -> Unit = {}
    ): Boolean {
        return downloadReleaseAsset(release, "box_${release.tag}.zip", onProgress)
    }

    suspend fun downloadApp(
        release: com.box.app.data.model.ReleaseInfo,
        onProgress: (Int?) -> Unit = {}
    ): Boolean {
        val flavor = if (BuildConfig.FLAVOR == "bfr") "bfr" else "box"
        val buildType = BuildConfig.BUILD_TYPE.lowercase()
        return downloadReleaseAsset(release, "app-${release.tag}-$flavor-$buildType.apk", onProgress)
    }

    suspend fun openUpdateSheetFor(
        target: UpdateTarget,
        closeAboutDialog: Boolean
    ): Boolean {
        return try {
            when (target) {
                UpdateTarget.MODULE -> {
                    refreshModuleVersion()
                    checkForModuleUpdates()
                }
                UpdateTarget.APP -> checkForAppUpdates()
            }

            val r = updateResult
            if (r == null) {
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_failed_check_updates),
                    Toast.LENGTH_SHORT
                ).show()
                false
            } else if (!r.hasUpdate) {
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_already_up_to_date),
                    Toast.LENGTH_SHORT
                ).show()
                false
            } else {
                showUpdateDialog = true
                if (closeAboutDialog) showAboutDialog = false
                true
            }
        } catch (_: Exception) {
            Toast.makeText(
                context,
                context.getString(R.string.settings_failed_check_updates),
                Toast.LENGTH_SHORT
            ).show()
            false
        }
    }

    if (showAboutDialog) {
        val aboutSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        LaunchedEffect(Unit) {
            refreshModuleVersion()
        }

        AppModalBottomSheet(
            onDismissRequest = { showAboutDialog = false },
            sheetState = aboutSheetState
        ) {
            AboutBottomSheet(
                appVersion = appVersionText,
                moduleVersion = moduleVersionText,
                appIcon = appIconPainter,
                onModuleClick = {
                    val url = if (BuildConfig.FLAVOR == "bfr") {
                        "https://github.com/taamarin/box_for_magisk"
                    } else {
                        "https://github.com/boxproxy/box"
                    }
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                },
                onChannelClick = {
                    val url = if (BuildConfig.FLAVOR == "bfr") {
                        "https://t.me/nothing_taamarin"
                    } else {
                        "https://t.me/zero_o0"
                    }
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                },
                onAppVersionClick = {
                    scope.launch {
                        openUpdateSheetFor(UpdateTarget.APP, closeAboutDialog = true)
                    }
                },
                onModuleVersionClick = {
                    scope.launch {
                        openUpdateSheetFor(UpdateTarget.MODULE, closeAboutDialog = true)
                    }
                }
            )
        }
    }

    if (showUpdateDialog && updateResult != null) {
        val updateSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        AppModalBottomSheet(
            onDismissRequest = { showUpdateDialog = false },
            sheetState = updateSheetState
        ) {
            UpdateBottomSheet(
                updateResult = updateResult!!,
                mode = if (updateTarget == UpdateTarget.APP) UpdateSheetMode.APP else UpdateSheetMode.MODULE,
                onDismiss = { showUpdateDialog = false },
                onDownload = { release, onProgress ->
                    when (updateTarget) {
                        UpdateTarget.MODULE -> downloadModule(release, onProgress)
                        UpdateTarget.APP -> downloadApp(release, onProgress)
                    }
                },
                onOpenInBrowser = { url ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
            )
        }
    }

    if (showThemeDialog) {
        ThemeSelectionDialog(
            currentMode = currentThemeMode,
            onDismiss = { showThemeDialog = false },
            onSelect = { mode ->
                ThemeManager.setThemeMode(context, mode)
                showThemeDialog = false
            }
        )
    }

    if (showSystemBarDialog) {
        SystemBarSelectionDialog(
            currentSettings = systemBarSettings,
            onDismiss = { showSystemBarDialog = false },
            onConfirm = { settings ->
                ThemeManager.setSystemBarSettings(context, settings)
                showSystemBarDialog = false
            }
        )
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentLanguage = currentLanguage,
            onDismiss = { showLanguageDialog = false },
            onSelect = { lang ->
                LanguageManager.setLanguage(context, lang)
                showLanguageDialog = false
            }
        )
    }

    if (showBackupRestoreDialog) {
        BackupRestoreDialog(
            onDismiss = { showBackupRestoreDialog = false }
        )
    }

    if (showAppearanceMoreSheet) {
        val appearanceMoreSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        AppModalBottomSheet(
            onDismissRequest = { showAppearanceMoreSheet = false },
            sheetState = appearanceMoreSheetState
        ) {
            val sheetScrollState = rememberScrollState()
            Column(modifier = Modifier.fillMaxWidth()) {
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

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(sheetScrollState)
                        .padding(horizontal = 16.dp, vertical = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_appearance_more),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.settings_appearance_more_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary,
                        modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                    )

                    SettingsToggleRow(
                        icon = Icons.Filled.DarkMode,
                        title = stringResource(R.string.settings_true_black),
                        subtitle = stringResource(R.string.settings_true_black_subtitle),
                        checked = trueBlackEnabled,
                        onCheckedChange = { ThemeManager.setTrueBlack(context, it) },
                        showDivider = true
                    )

                    SettingsToggleRow(
                        icon = Icons.Filled.BlurOn,
                        title = stringResource(R.string.settings_blur_effects),
                        subtitle = stringResource(
                            if (blurEffectsSupported) {
                                R.string.settings_blur_effects_subtitle
                            } else {
                                R.string.settings_blur_effects_unsupported_subtitle
                            }
                        ),
                        checked = blurEffectsActive,
                        onCheckedChange = { ThemeManager.setBlurEffectsEnabled(context, it) },
                        enabled = blurEffectsSupported,
                        showDivider = true
                    )

                    if (blurEffectsActive) {
                    SettingsToggleRow(
                        icon = Icons.Filled.Palette,
                        title = stringResource(R.string.settings_liquid_glass_translucent),
                        subtitle = stringResource(R.string.settings_liquid_glass_translucent_subtitle),
                        checked = liquidGlassTranslucent,
                        onCheckedChange = { ThemeManager.setLiquidGlassTranslucent(context, it) },
                        showDivider = true
                    )

                    SettingsToggleRow(
                        icon = Icons.Filled.BlurOn,
                        title = stringResource(R.string.settings_bottom_sheet_blur),
                        subtitle = stringResource(R.string.settings_bottom_sheet_blur_subtitle),
                        checked = bottomSheetBlur,
                        onCheckedChange = { ThemeManager.setBottomSheetBlur(context, it) },
                        showDivider = false
                    )

                    if (liquidGlassTranslucent) {
                        Text(
                            text = stringResource(R.string.settings_liquid_glass_lens_strength),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textPrimary,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        Text(
                            text = stringResource(R.string.settings_liquid_glass_lens_strength_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                        )
                        Slider(
                            value = liquidGlassLensStrength,
                            onValueChange = { ThemeManager.setLiquidGlassLensStrength(context, it) },
                            valueRange = 0f..2f,
                            colors = SliderDefaults.colors(
                                thumbColor = accent,
                                activeTrackColor = accent,
                                inactiveTrackColor = c.divider.copy(alpha = if (isDark) 0.16f else 0.06f)
                            )
                        )
                    }

                    Text(
                        text = stringResource(R.string.settings_liquid_glass_blur_strength),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = c.textPrimary,
                        modifier = Modifier.padding(top = 12.dp)
                    )

                    Text(
                        text = stringResource(R.string.settings_liquid_glass_blur_strength_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                    )
                    Slider(
                        value = liquidGlassBlurDp,
                        onValueChange = { ThemeManager.setLiquidGlassBlurDp(context, it) },
                        valueRange = 0f..20f,
                        colors = SliderDefaults.colors(
                            thumbColor = accent,
                            activeTrackColor = accent,
                            inactiveTrackColor = c.divider.copy(alpha = if (isDark) 0.16f else 0.06f)
                        )
                    )
                    }

                    Text(
                        text = stringResource(R.string.settings_ui_scale, pendingUiScalePercent.roundToInt()),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = c.textPrimary,
                        modifier = Modifier.padding(top = 12.dp)
                    )

                    Text(
                        text = stringResource(R.string.settings_ui_scale_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                    )
                    Slider(
                        value = pendingUiScalePercent,
                        onValueChange = { v ->
                            uiScaleDragging = true
                            pendingUiScalePercent = v.coerceIn(80f, 120f)
                        },
                        onValueChangeFinished = {
                            uiScaleDragging = false
                            UiScaleManager.setUiScalePercent(context, pendingUiScalePercent.roundToInt())
                        },
                        valueRange = 80f..120f,
                        colors = SliderDefaults.colors(
                            thumbColor = accent,
                            activeTrackColor = accent,
                            inactiveTrackColor = c.divider.copy(alpha = if (isDark) 0.16f else 0.06f)
                        )
                    )

                    Spacer(modifier = Modifier.height(WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().coerceAtMost(12.dp)))
                }
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(c.pageBg),
        contentPadding = contentPaddingWithNavBars(
            start = pagePadding,
            end = pagePadding,
            top = 0.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = stringResource(R.string.settings_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        item {
            ToolsSectionCard(
                title = stringResource(R.string.settings_section_appearance),
                subtitle = stringResource(R.string.settings_section_appearance_subtitle)
            ) {
                ToolsRowIcon(
                    icon = Icons.Filled.Palette,
                    title = stringResource(R.string.settings_theme_mode),
                    subtitle = when (currentThemeMode) {
                        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_follow_system)
                    },
                    showDivider = true,
                    onClick = { showThemeDialog = true }
                )

                ToolsRowIcon(
                    icon = Icons.Filled.Translate,
                    title = stringResource(R.string.settings_language),
                    subtitle = when (currentLanguage) {
                        AppLanguage.SYSTEM -> stringResource(R.string.settings_language_follow_system)
                        AppLanguage.ENGLISH -> stringResource(R.string.settings_language_english)
                        AppLanguage.CHINESE -> stringResource(R.string.settings_language_chinese)
                    },
                    showDivider = true,
                    onClick = { showLanguageDialog = true }
                )

                ToolsRowIcon(
                    icon = Icons.Filled.PhoneAndroid,
                    title = stringResource(R.string.settings_system_bars),
                    subtitle = stringResource(
                        R.string.settings_system_bars_subtitle,
                        if (systemBarSettings.statusBar.name == "TRANSPARENT") stringResource(R.string.settings_system_bars_transparent) else stringResource(R.string.settings_system_bars_opaque),
                        stringResource(R.string.settings_system_bars_status),
                        if (systemBarSettings.navigationBar.name == "TRANSPARENT") stringResource(R.string.settings_system_bars_transparent) else stringResource(R.string.settings_system_bars_opaque),
                        stringResource(R.string.settings_system_bars_navigation)
                    ),
                    showDivider = true,
                    onClick = { showSystemBarDialog = true }
                )

                ToolsRowIcon(
                    icon = Icons.Filled.Tune,
                    title = stringResource(R.string.settings_appearance_more),
                    subtitle = stringResource(R.string.settings_appearance_more_subtitle),
                    showDivider = false,
                    onClick = { showAppearanceMoreSheet = true }
                )
            }
        }

        if (BuildConfig.FLAVOR != "bfr") {
            item {
                ToolsSectionCard(
                    title = stringResource(R.string.settings_section_service),
                    subtitle = stringResource(R.string.settings_section_service_subtitle)
                ) {
                    SettingsToggleRow(
                        icon = Icons.Filled.Autorenew,
                        title = stringResource(R.string.settings_auto_start),
                        subtitle = stringResource(R.string.settings_auto_start_subtitle),
                        checked = autoStart,
                        onCheckedChange = { enabled ->
                            autoStart = enabled
                            scope.launch {
                                runCatching { BoxApi.updateBooleanSetting("boot_auto_start", enabled) }
                            }
                        }
                    )
                    SettingsToggleRow(
                        icon = Icons.Filled.Download,
                        title = stringResource(R.string.settings_accelerated_download),
                        subtitle = stringResource(R.string.settings_accelerated_download_subtitle),
                        checked = acceleratedDownload,
                        onCheckedChange = { enabled ->
                            acceleratedDownload = enabled
                            scope.launch {
                                runCatching { BoxApi.updateBooleanSetting("use_ghproxy", enabled) }
                            }
                        },
                        showDivider = false
                    )
                }
            }
        }

        item {
            ToolsSectionCard(
                title = stringResource(R.string.settings_section_subscription),
                subtitle = stringResource(R.string.settings_section_subscription_subtitle)
            ) {
                SettingsToggleRow(
                    icon = Icons.Filled.Analytics,
                    title = stringResource(R.string.settings_use_clash_api),
                    subtitle = if (useClashApi) stringResource(R.string.settings_providers_mode) else stringResource(R.string.settings_url_mode),
                    checked = useClashApi,
                    onCheckedChange = { useClashApi = it },
                    showDivider = false
                )
            }
        }

        item {
            ToolsSectionCard(
                title = stringResource(R.string.settings_section_net_speed),
                subtitle = stringResource(R.string.settings_section_subscription_subtitle)
            ) {
                SettingsToggleRow(
                    icon = Icons.Filled.Analytics,
                    title = stringResource(R.string.settings_use_clash_api),
                    subtitle = if (useClashApiForNetSpeed) stringResource(R.string.settings_core_api_mode) else stringResource(R.string.settings_system_mode),
                    checked = useClashApiForNetSpeed,
                    onCheckedChange = { useClashApiForNetSpeed = it },
                    showDivider = useClashApiForNetSpeed
                )

                if (useClashApiForNetSpeed) {
                    SettingsTextFieldRow(
                        icon = Icons.Filled.Category,
                        title = stringResource(R.string.settings_filter_chains),
                        subtitle = stringResource(R.string.settings_filter_chains_subtitle),
                        value = proxyTrafficFilterText,
                        placeholder = defaultProxyTrafficFilter,
                        onValueChange = { proxyTrafficFilterText = it }
                    )
                }
            }
        }

        item {
            ToolsSectionCard(
                title = stringResource(R.string.settings_latency_targets_title),
                subtitle = stringResource(R.string.settings_latency_targets_subtitle)
            ) {
                fun markDirty() {
                    if (!latencyDirty) latencyDirty = true
                }

                fun previewTitle(name: String, url: String): String {
                    val n = name.trim().ifBlank { "-" }
                    val host = runCatching { Uri.parse(url.trim()).host }.getOrNull().orEmpty().ifBlank { url.trim() }
                    return if (host.isBlank()) n else "$n  ·  $host"
                }

                @Composable
                fun TargetCard(
                    index: Int,
                    name: String,
                    url: String,
                    expanded: Boolean,
                    onToggle: () -> Unit,
                    onNameChange: (String) -> Unit,
                    onUrlChange: (String) -> Unit
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedRectangle(16.dp))
                            .clickable(onClick = onToggle),
                        colors = CardDefaults.cardColors(containerColor = c.cardAlt)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(
                                text = "#${index}",
                                style = MaterialTheme.typography.labelMedium,
                                color = c.textSecondary
                            )
                            Text(
                                text = previewTitle(name, url),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = c.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            if (expanded) {
                                Spacer(modifier = Modifier.height(10.dp))
                                SettingsTextFieldRow(
                                    icon = Icons.Filled.Tune,
                                    title = stringResource(R.string.settings_latency_target_name),
                                    subtitle = "#${index}",
                                    value = name,
                                    placeholder = "",
                                    onValueChange = { v -> markDirty(); onNameChange(v) }
                                )
                                SettingsTextFieldRow(
                                    icon = Icons.Filled.Link,
                                    title = stringResource(R.string.settings_latency_target_url),
                                    subtitle = "#${index}",
                                    value = url,
                                    placeholder = "https://",
                                    onValueChange = { v -> markDirty(); onUrlChange(v) }
                                )
                            }
                        }
                    }
                }

                var expand1 by rememberSaveable { mutableStateOf(false) }
                var expand2 by rememberSaveable { mutableStateOf(false) }
                var expand3 by rememberSaveable { mutableStateOf(false) }

                TargetCard(
                    index = 1,
                    name = latencyName1,
                    url = latencyUrl1,
                    expanded = expand1,
                    onToggle = { expand1 = !expand1 },
                    onNameChange = { latencyName1 = it },
                    onUrlChange = { latencyUrl1 = it }
                )
                Spacer(modifier = Modifier.height(10.dp))
                TargetCard(
                    index = 2,
                    name = latencyName2,
                    url = latencyUrl2,
                    expanded = expand2,
                    onToggle = { expand2 = !expand2 },
                    onNameChange = { latencyName2 = it },
                    onUrlChange = { latencyUrl2 = it }
                )
                Spacer(modifier = Modifier.height(10.dp))
                TargetCard(
                    index = 3,
                    name = latencyName3,
                    url = latencyUrl3,
                    expanded = expand3,
                    onToggle = { expand3 = !expand3 },
                    onNameChange = { latencyName3 = it },
                    onUrlChange = { latencyUrl3 = it }
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, c.divider.copy(alpha = 0.75f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textSecondary),
                        onClick = {
                            LatencyTargetsManager.resetToDefaults(context)
                            latencyDirty = false
                            LatencyTargetsManager.targets.value.let { list ->
                                val t1 = list.getOrNull(0)
                                val t2 = list.getOrNull(1)
                                val t3 = list.getOrNull(2)
                                if (t1 != null) {
                                    latencyName1 = t1.name
                                    latencyUrl1 = t1.url
                                }
                                if (t2 != null) {
                                    latencyName2 = t2.name
                                    latencyUrl2 = t2.url
                                }
                                if (t3 != null) {
                                    latencyName3 = t3.name
                                    latencyUrl3 = t3.url
                                }
                            }
                            scope.launch { HomeRepository.refreshLatencyNow() }
                        }
                    ) {
                        Text(text = stringResource(R.string.settings_latency_reset))
                    }

                    Button(
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        onClick = {
                            LatencyTargetsManager.setTargets(
                                context,
                                LatencyTarget(name = latencyName1, url = latencyUrl1),
                                LatencyTarget(name = latencyName2, url = latencyUrl2),
                                LatencyTarget(name = latencyName3, url = latencyUrl3)
                            )
                            latencyDirty = false
                            LatencyTargetsManager.targets.value.let { list ->
                                val t1 = list.getOrNull(0)
                                val t2 = list.getOrNull(1)
                                val t3 = list.getOrNull(2)
                                if (t1 != null) {
                                    latencyName1 = t1.name
                                    latencyUrl1 = t1.url
                                }
                                if (t2 != null) {
                                    latencyName2 = t2.name
                                    latencyUrl2 = t2.url
                                }
                                if (t3 != null) {
                                    latencyName3 = t3.name
                                    latencyUrl3 = t3.url
                                }
                            }
                            scope.launch { HomeRepository.refreshLatencyNow() }
                        }
                    ) {
                        Text(text = stringResource(R.string.settings_latency_save))
                    }
                }
            }
        }

        item {
            ToolsSectionCard(
                title = stringResource(R.string.settings_section_misc),
                subtitle = stringResource(R.string.settings_section_misc_subtitle)
            ) {
                SettingsToggleRow(
                    icon = Icons.Filled.Dashboard,
                    title = stringResource(R.string.settings_open_panel_on_launch),
                    subtitle = stringResource(R.string.settings_open_panel_on_launch_subtitle),
                    checked = openPanelOnLaunch,
                    onCheckedChange = { openPanelOnLaunch = it }
                )
                SettingsToggleRow(
                    icon = Icons.Filled.Notifications,
                    title = stringResource(R.string.settings_notifications),
                    subtitle = stringResource(R.string.settings_notifications_subtitle),
                    checked = enableNotifications,
                    onCheckedChange = { enableNotifications = it },
                    showDivider = false
                )
                ToolsRowIcon(
                    icon = Icons.Filled.Storage,
                    title = stringResource(R.string.settings_backup_restore_title),
                    subtitle = stringResource(R.string.settings_backup_restore_subtitle),
                    showDivider = false,
                    onClick = { showBackupRestoreDialog = true }
                )
            }
        }

        item {
            ToolsSectionCard(
                title = stringResource(R.string.settings_section_about),
                subtitle = stringResource(R.string.settings_section_about_subtitle)
            ) {
                ToolsRowIcon(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.settings_version),
                    subtitle = "v$appVersionText",
                    badge = {
                        VersionUpdateBadge(
                            isChecking = updateCheckStatus.isChecking,
                            appHasUpdate = updateCheckStatus.appHasUpdate,
                            moduleHasUpdate = updateCheckStatus.moduleHasUpdate,
                            onAppClick = {
                                scope.launch {
                                    openUpdateSheetFor(UpdateTarget.APP, closeAboutDialog = false)
                                }
                            },
                            onModuleClick = {
                                scope.launch {
                                    openUpdateSheetFor(UpdateTarget.MODULE, closeAboutDialog = false)
                                }
                            }
                        )
                    },
                    showDivider = true,
                    onClick = { showAboutDialog = true }
                )

                ToolsRowIcon(
                    icon = Icons.Filled.Settings,
                    title = stringResource(R.string.settings_open_source_licenses),
                    subtitle = stringResource(R.string.settings_open_source_licenses_subtitle),
                    showDivider = false,
                    onClick = { onOpenOpenSourceLicenses() }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }
}

@Composable
private fun VersionUpdateBadge(
    isChecking: Boolean,
    appHasUpdate: Boolean,
    moduleHasUpdate: Boolean,
    onAppClick: (() -> Unit)? = null,
    onModuleClick: (() -> Unit)? = null
) {
    if (!isChecking && !appHasUpdate && !moduleHasUpdate) return

    val c = appColors()
    val isDark = ThemeManager.shouldUseDarkTheme()
    val chipBg = if (isDark) c.cardAlt.copy(alpha = 0.92f) else Color.White
    val chipBorder = c.divider.copy(alpha = if (isDark) 0.9f else 0.78f)
    val appTint = if (isDark) Color(0xFF7DD3FC) else Color(0xFF0369A1)
    val moduleTint = if (isDark) Color(0xFFA7F3D0) else Color(0xFF166534)
    val updateDot = if (isDark) Color(0xFFFF6478) else Color(0xFFE11D48)
    val labelColor = c.textPrimary

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isChecking && !appHasUpdate && !moduleHasUpdate) {
            CheckingUpdateBadge(
                containerColor = chipBg,
                borderColor = chipBorder,
                indicatorColor = if (isDark) Color(0xFFE2E8F0) else Color(0xFF475569)
            )
        } else {
            if (appHasUpdate) {
                UpdateTypeBadge(
                    icon = Icons.Filled.Android,
                    label = stringResource(R.string.settings_update_status_app),
                    contentDescription = stringResource(R.string.settings_update_status_app_available),
                    shape = Capsule(),
                    containerColor = chipBg,
                    borderColor = chipBorder,
                    iconColor = appTint,
                    labelColor = labelColor,
                    dotColor = updateDot,
                    onClick = onAppClick
                )
            }

            if (moduleHasUpdate) {
                UpdateTypeBadge(
                    icon = Icons.Filled.Extension,
                    label = stringResource(R.string.settings_update_status_module),
                    contentDescription = stringResource(R.string.settings_update_status_module_available),
                    shape = RoundedRectangle(8.dp),
                    containerColor = chipBg,
                    borderColor = chipBorder,
                    iconColor = moduleTint,
                    labelColor = labelColor,
                    dotColor = updateDot,
                    onClick = onModuleClick
                )
            }
        }
    }
}

@Composable
private fun CheckingUpdateBadge(
    containerColor: Color,
    borderColor: Color,
    indicatorColor: Color
) {
    val c = appColors()
    Box(
        modifier = Modifier
            .height(24.dp)
            .clip(RoundedRectangle(8.dp))
            .background(containerColor)
            .border(1.dp, borderColor, RoundedRectangle(8.dp))
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(11.dp),
                strokeWidth = 1.8.dp,
                color = indicatorColor
            )
            Text(
                text = stringResource(R.string.settings_update_status_checking),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = c.textSecondary
            )
        }
    }
}

@Composable
private fun UpdateTypeDot(
    color: Color
) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(Capsule())
            .background(color)
    )
}

@Composable
private fun UpdateTypeBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    shape: Shape,
    containerColor: Color,
    borderColor: Color,
    iconColor: Color,
    labelColor: Color,
    dotColor: Color,
    onClick: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .height(24.dp)
            .clip(shape)
            .background(containerColor)
            .border(1.dp, borderColor, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clearAndSetSemantics { this.contentDescription = contentDescription }
                .padding(start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = labelColor
            )
            UpdateTypeDot(color = dotColor)
        }
    }
}

@Composable
private fun SettingsTextFieldRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit
) {
    val c = appColors()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(c.cardAlt, shape = RoundedRectangle(10.dp)),
            contentAlignment = androidx.compose.ui.Alignment.Center
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
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary
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

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp),
        placeholder = {
            Text(text = placeholder, style = MaterialTheme.typography.bodyMedium)
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
}

private enum class UpdateTarget { MODULE, APP }
private enum class BackupRestoreMode { BACKUP, RESTORE }
private enum class BackupRestoreScope { MODULES, APPS, BOTH }

@Composable
private fun BackupRestoreDialog(
    onDismiss: () -> Unit
) {
    val c = appColors()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by rememberSaveable { mutableStateOf(BackupRestoreMode.BACKUP) }
    var backupScope by rememberSaveable { mutableStateOf(BackupRestoreScope.BOTH) }

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var pickedName by remember { mutableStateOf<String?>(null) }

    var detectedHasModules by remember { mutableStateOf(false) }
    var detectedHasApps by remember { mutableStateOf(false) }
    var restoreScope by rememberSaveable { mutableStateOf(BackupRestoreScope.BOTH) }

    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var lastExportedName by remember { mutableStateOf<String?>(null) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    fun stamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    fun restoreScopeLabel(s: BackupRestoreScope): String {
        return when (s) {
            BackupRestoreScope.MODULES -> context.getString(R.string.settings_backup_restore_scope_modules_only)
            BackupRestoreScope.APPS -> context.getString(R.string.settings_backup_restore_scope_apps_only)
            BackupRestoreScope.BOTH -> context.getString(R.string.settings_backup_restore_scope_both)
        }
    }

    suspend fun exportBackupToDownloads(scope: BackupRestoreScope, fileName: String): Uri? = withContext(Dispatchers.IO) {
        val wantModules = scope == BackupRestoreScope.MODULES || scope == BackupRestoreScope.BOTH
        val wantApps = scope == BackupRestoreScope.APPS || scope == BackupRestoreScope.BOTH

        fun obfuscatePrefs(bytes: ByteArray): String {
            val key = (context.packageName + "|box_prefs_v1").toByteArray()
            val out = ByteArray(bytes.size)
            for (i in bytes.indices) {
                out[i] = (bytes[i].toInt() xor key[i % key.size].toInt()).toByte()
            }
            return Base64.encodeToString(out, Base64.NO_WRAP)
        }

        val resolver = context.contentResolver
        val mime = "application/zip"

        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@withContext null

            try {
                    resolver.openOutputStream(uri, "w")?.use { out ->
                    ZipOutputStream(out).use { zip ->
                        val manifest = buildString {
                            append("{\n")
                            append("  \"version\": 1,\n")
                            append("  \"createdAt\": \"").append(stamp()).append("\",\n")
                            append("  \"hasModules\": ").append(wantModules).append(",\n")
                            append("  \"hasApps\": ").append(wantApps).append("\n")
                            append("}\n")
                        }
                        zip.putNextEntry(ZipEntry("manifest.json"))
                        zip.write(manifest.toByteArray())
                        zip.closeEntry()

                        if (wantModules) {
                            val tmpTar = File.createTempFile("box_backup_", ".tar.gz")
                            try {
                                val cmd = "tar -czf '${tmpTar.absolutePath}' -C /data/adb box 2>/dev/null"
                                val res = ShellExecutor.execute(cmd)
                                if (res.exitCode != 0) {
                                    throw IOException(res.stderr.ifBlank { res.stdout }.ifBlank { "Backup modules failed" })
                                }
                                zip.putNextEntry(ZipEntry("modules/box.tar.gz"))
                                tmpTar.inputStream().use { it.copyTo(zip) }
                                zip.closeEntry()
                            } finally {
                                runCatching { tmpTar.delete() }
                            }
                        }

                        if (wantApps) {
                            val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
                            val prefFiles = prefsDir
                                .listFiles()
                                ?.filter { it.isFile && it.name.endsWith(".xml", ignoreCase = true) }
                                .orEmpty()
                                .sortedBy { it.name }

                            val arr = JSONArray()
                            prefFiles.forEach { f ->
                                val raw = runCatching { f.readBytes() }.getOrNull() ?: return@forEach
                                val obj = JSONObject()
                                obj.put("name", f.name)
                                obj.put("data", obfuscatePrefs(raw))
                                arr.put(obj)
                            }

                            val root = JSONObject()
                            root.put("version", 1)
                            root.put("format", "obfuscated_shared_prefs_xml")
                            root.put("prefs", arr)

                            zip.putNextEntry(ZipEntry("apps/settings.json"))
                            zip.write(root.toString().toByteArray())
                            zip.closeEntry()
                        }
                    }
                } ?: return@withContext null

                ContentValues().apply {
                    put(MediaStore.Downloads.IS_PENDING, 0)
                }.also { doneValues ->
                    resolver.update(uri, doneValues, null, null)
                }

                uri
            } catch (_: Exception) {
                runCatching { resolver.delete(uri, null, null) }
                null
            }
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val outFile = File(dir, fileName)
            outFile.outputStream().use { out ->
                ZipOutputStream(out).use { zip ->
                    val manifest = buildString {
                        append("{\n")
                        append("  \"version\": 1,\n")
                        append("  \"createdAt\": \"").append(stamp()).append("\",\n")
                        append("  \"hasModules\": ").append(wantModules).append(",\n")
                        append("  \"hasApps\": ").append(wantApps).append("\n")
                        append("}\n")
                    }
                    zip.putNextEntry(ZipEntry("manifest.json"))
                    zip.write(manifest.toByteArray())
                    zip.closeEntry()

                    if (wantModules) {
                        val tmpTar = File.createTempFile("box_backup_", ".tar.gz")
                        try {
                            val cmd = "tar -czf '${tmpTar.absolutePath}' -C /data/adb box 2>/dev/null"
                            val res = ShellExecutor.execute(cmd)
                            if (res.exitCode != 0) {
                                throw IOException(res.stderr.ifBlank { res.stdout }.ifBlank { "Backup modules failed" })
                            }
                            zip.putNextEntry(ZipEntry("modules/box.tar.gz"))
                            tmpTar.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        } finally {
                            runCatching { tmpTar.delete() }
                        }
                    }

                    if (wantApps) {
                        val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
                        val prefFiles = prefsDir
                            .listFiles()
                            ?.filter { it.isFile && it.name.endsWith(".xml", ignoreCase = true) }
                            .orEmpty()
                            .sortedBy { it.name }

                        val arr = JSONArray()
                        prefFiles.forEach { f ->
                            val raw = runCatching { f.readBytes() }.getOrNull() ?: return@forEach
                            val obj = JSONObject()
                            obj.put("name", f.name)
                            obj.put("data", obfuscatePrefs(raw))
                            arr.put(obj)
                        }

                        val root = JSONObject()
                        root.put("version", 1)
                        root.put("format", "obfuscated_shared_prefs_xml")
                        root.put("prefs", arr)

                        zip.putNextEntry(ZipEntry("apps/settings.json"))
                        zip.write(root.toString().toByteArray())
                        zip.closeEntry()
                    }
                }
            }
            Uri.fromFile(outFile)
        }
    }

    suspend fun detectBackup(uri: Uri) {
        val result = withContext(Dispatchers.IO) {
            var hasModules = false
            var hasApps = false
            val input = context.contentResolver.openInputStream(uri) ?: return@withContext hasModules to hasApps
            ZipInputStream(input).use { zis ->
                while (true) {
                    val e = zis.nextEntry ?: break
                    when (e.name) {
                        "modules/box.tar.gz" -> hasModules = true
                        "apps/settings.json" -> hasApps = true
                    }
                    zis.closeEntry()
                }
            }
            hasModules to hasApps
        }

        detectedHasModules = result.first
        detectedHasApps = result.second
        restoreScope = when {
            detectedHasModules && detectedHasApps -> BackupRestoreScope.BOTH
            detectedHasModules -> BackupRestoreScope.MODULES
            detectedHasApps -> BackupRestoreScope.APPS
            else -> BackupRestoreScope.BOTH
        }
    }

    suspend fun restoreFromBackup(uri: Uri, scope: BackupRestoreScope) {
        val wantModules = scope == BackupRestoreScope.MODULES || scope == BackupRestoreScope.BOTH
        val wantApps = scope == BackupRestoreScope.APPS || scope == BackupRestoreScope.BOTH

        fun deobfuscatePrefs(encoded: String): ByteArray {
            val key = (context.packageName + "|box_prefs_v1").toByteArray()
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            val out = ByteArray(bytes.size)
            for (i in bytes.indices) {
                out[i] = (bytes[i].toInt() xor key[i % key.size].toInt()).toByte()
            }
            return out
        }

        fun readZipEntryTextNoClose(zis: ZipInputStream): String {
            val bos = ByteArrayOutputStream()
            val buf = ByteArray(8 * 1024)
            while (true) {
                val n = zis.read(buf)
                if (n <= 0) break
                bos.write(buf, 0, n)
            }
            return bos.toString(Charsets.UTF_8.name())
        }

        withContext(Dispatchers.IO) {
            var tmpTar: File? = null

            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("Open backup failed")
            ZipInputStream(input).use { zis ->
                while (true) {
                    val e = zis.nextEntry ?: break
                    when (e.name) {
                        "modules/box.tar.gz" -> {
                            if (wantModules) {
                                val f = File.createTempFile("box_restore_", ".tar.gz")
                                f.outputStream().use { out ->
                                    zis.copyTo(out)
                                }
                                tmpTar = f
                            }
                        }
                        "apps/settings.json" -> {
                            if (wantApps) {
                                val json = readZipEntryTextNoClose(zis)
                                val root = JSONObject(json)
                                val arr = root.optJSONArray("prefs") ?: JSONArray()
                                val prefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
                                prefsDir.mkdirs()

                                for (i in 0 until arr.length()) {
                                    val o = arr.optJSONObject(i) ?: continue
                                    val name = o.optString("name").orEmpty()
                                    val data = o.optString("data").orEmpty()
                                    if (name.isBlank() || data.isBlank()) continue
                                    if (name.contains("/") || name.contains("..")) continue
                                    if (!name.endsWith(".xml", ignoreCase = true)) continue

                                    val bytes = runCatching { deobfuscatePrefs(data) }.getOrNull() ?: continue
                                    File(prefsDir, name).outputStream().use { os -> os.write(bytes) }
                                }
                            }
                        }
                    }

                    zis.closeEntry()
                }
            }

            if (wantModules) {
                tmpTar?.let { tarFile ->
                    try {
                        ShellExecutor.execute("mkdir -p /data/adb")
                        ShellExecutor.execute("rm -rf /data/adb/box")
                        val res = ShellExecutor.execute("tar -xzf '${tarFile.absolutePath}' -C /data/adb")
                        if (res.exitCode != 0) {
                            throw IOException(res.stderr.ifBlank { res.stdout }.ifBlank { "Restore modules failed" })
                        }
                    } finally {
                        runCatching { tarFile.delete() }
                    }
                }
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pickedUri = uri
        pickedName = uri.lastPathSegment
        status = null
        working = true
        scope.launch {
            runCatching {
                detectBackup(uri)
            }.onFailure {
                status = it.message ?: context.getString(R.string.settings_backup_restore_failed_detect)
            }
            working = false
        }
    }

    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { if (!working) showRestoreConfirm = false },
            containerColor = c.card,
            titleContentColor = c.textPrimary,
            textContentColor = c.textSecondary,
            title = { Text(text = stringResource(R.string.settings_backup_restore_restore)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.settings_backup_restore_confirm_body,
                        restoreScopeLabel(restoreScope)
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !working,
                    onClick = {
                        val u = pickedUri ?: return@TextButton
                        showRestoreConfirm = false
                        status = null
                        working = true
                        scope.launch {
                            runCatching {
                                restoreFromBackup(u, restoreScope)
                                status = context.getString(R.string.settings_backup_restore_restore_ok)
                            }.onFailure {
                                status = it.message ?: context.getString(R.string.settings_backup_restore_failed)
                            }
                            working = false
                        }
                    }
                ) {
                    Text(text = stringResource(R.string.settings_backup_restore_confirm_continue))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !working,
                    onClick = { showRestoreConfirm = false }
                ) {
                    Text(text = stringResource(R.string.action_cancel), color = c.textPrimary)
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        containerColor = c.card,
        titleContentColor = c.textPrimary,
        textContentColor = c.textSecondary,
        title = { Text(text = stringResource(R.string.settings_backup_restore_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    @Composable
                    fun tab(label: String, selected: Boolean, onClick: () -> Unit) {
                        Box(
                            modifier = Modifier
                                .clip(Capsule())
                                .background(if (selected) c.cardAlt else Color.Transparent)
                                .clickable(
                                    enabled = !working,
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onClick
                                )
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = c.textPrimary
                            )
                        }
                    }

                    tab(
                        label = stringResource(R.string.settings_backup_restore_tab_backup),
                        selected = mode == BackupRestoreMode.BACKUP,
                        onClick = { mode = BackupRestoreMode.BACKUP }
                    )
                    tab(
                        label = stringResource(R.string.settings_backup_restore_tab_restore),
                        selected = mode == BackupRestoreMode.RESTORE,
                        onClick = { mode = BackupRestoreMode.RESTORE }
                    )
                }

                if (mode == BackupRestoreMode.BACKUP) {
                    Text(
                        text = stringResource(R.string.settings_backup_restore_scope_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = c.textPrimary
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        @Composable
                        fun row(label: String, selected: Boolean, onClick: () -> Unit) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedRectangle(12.dp))
                                    .background(c.cardAlt.copy(alpha = if (selected) 0.9f else 0.6f))
                                    .clickable(
                                        enabled = !working,
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = onClick
                                    )
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = c.textPrimary,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (selected) "✓" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = c.textPrimary
                                )
                            }
                        }

                        row(
                            label = stringResource(R.string.settings_backup_restore_scope_modules),
                            selected = backupScope == BackupRestoreScope.MODULES,
                            onClick = { backupScope = BackupRestoreScope.MODULES }
                        )
                        row(
                            label = stringResource(R.string.settings_backup_restore_scope_apps),
                            selected = backupScope == BackupRestoreScope.APPS,
                            onClick = { backupScope = BackupRestoreScope.APPS }
                        )
                        row(
                            label = stringResource(R.string.settings_backup_restore_scope_both),
                            selected = backupScope == BackupRestoreScope.BOTH,
                            onClick = { backupScope = BackupRestoreScope.BOTH }
                        )
                    }

                    if (lastExportedName != null) {
                        Text(
                            text = stringResource(R.string.settings_backup_restore_exported, lastExportedName!!),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.settings_backup_restore_file_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = c.textPrimary
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedRectangle(12.dp))
                            .background(c.cardAlt)
                            .clickable(
                                enabled = !working,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { picker.launch(arrayOf("application/zip")) }
                            )
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = pickedName ?: stringResource(R.string.settings_backup_restore_file_pick),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textPrimary
                        )
                    }

                    if (pickedUri != null) {
                        val detectedLabel = when {
                            detectedHasModules && detectedHasApps -> stringResource(R.string.settings_backup_restore_detect_both)
                            detectedHasModules -> stringResource(R.string.settings_backup_restore_detect_modules)
                            detectedHasApps -> stringResource(R.string.settings_backup_restore_detect_apps)
                            else -> stringResource(R.string.settings_backup_restore_detect_unknown)
                        }
                        Text(
                            text = stringResource(R.string.settings_backup_restore_detected, detectedLabel),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary
                        )

                        Text(
                            text = stringResource(R.string.settings_backup_restore_scope_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textPrimary
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            @Composable
                            fun row(label: String, selected: Boolean, onClick: () -> Unit) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedRectangle(12.dp))
                                        .background(c.cardAlt.copy(alpha = if (selected) 0.9f else 0.6f))
                                        .clickable(
                                            enabled = !working,
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = onClick
                                        )
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = c.textPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = if (selected) "✓" else "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = c.textPrimary
                                    )
                                }
                            }

                            row(
                                label = stringResource(R.string.settings_backup_restore_scope_modules_only),
                                selected = restoreScope == BackupRestoreScope.MODULES,
                                onClick = { restoreScope = BackupRestoreScope.MODULES }
                            )
                            row(
                                label = stringResource(R.string.settings_backup_restore_scope_apps_only),
                                selected = restoreScope == BackupRestoreScope.APPS,
                                onClick = { restoreScope = BackupRestoreScope.APPS }
                            )
                            row(
                                label = stringResource(R.string.settings_backup_restore_scope_both),
                                selected = restoreScope == BackupRestoreScope.BOTH,
                                onClick = { restoreScope = BackupRestoreScope.BOTH }
                            )
                        }
                    }
                }

                if (status != null) {
                    Text(
                        text = status!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary
                    )
                }
            }
        },
        confirmButton = {
            val label = if (mode == BackupRestoreMode.BACKUP) {
                stringResource(R.string.settings_backup_restore_export)
            } else {
                stringResource(R.string.settings_backup_restore_restore)
            }
            TextButton(
                enabled = !working && (mode == BackupRestoreMode.BACKUP || pickedUri != null),
                onClick = {
                    if (working) return@TextButton
                    if (mode == BackupRestoreMode.RESTORE) {
                        showRestoreConfirm = true
                        return@TextButton
                    }

                    status = null
                    lastExportedName = null
                    working = true
                    scope.launch {
                        runCatching {
                            val name = "box_backup_${stamp()}.zip"
                            val saved = exportBackupToDownloads(backupScope, name)
                            if (saved == null) {
                                status = context.getString(R.string.settings_backup_restore_export_failed)
                            } else {
                                lastExportedName = name
                                status = context.getString(R.string.settings_backup_restore_export_ok)
                            }
                        }.onFailure {
                            status = it.message ?: context.getString(R.string.settings_backup_restore_failed)
                        }
                        working = false
                    }
                }
            ) {
                Text(text = label)
            }
        },
        dismissButton = {
            TextButton(
                enabled = !working,
                onClick = onDismiss
            ) {
                Text(text = stringResource(R.string.action_cancel), color = c.textPrimary)
            }
        }
    )
}
