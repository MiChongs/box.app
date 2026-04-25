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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.box.app.ui.screens.Settings.AboutScreen
import com.box.app.ui.components.bottomsheets.AppModalBottomSheet
import com.box.app.ui.miuix.HyperBottomSheet
import com.box.app.ui.miuix.HyperFilterChip
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import com.box.app.ui.components.bottomsheets.UpdateSheetMode
import com.box.app.ui.components.bottomsheets.UpdateBottomSheet
import com.box.app.ui.components.contentPaddingWithNavBars
import com.box.app.ui.components.LocalFloatingNavBarSpaceDp
import com.box.app.ui.theme.appAccentColor
import com.box.app.ui.theme.appColors
import com.box.app.ui.screens.Settings.OpenSourceLicensesScreen
import com.box.app.utils.AppLanguage
import com.box.app.utils.LanguageManager
import com.box.app.utils.LatencyTarget
import com.box.app.utils.LatencyTargetsManager
import com.box.app.utils.MapleFontManager
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar

import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import androidx.compose.foundation.shape.RoundedCornerShape
import com.box.app.ui.effect.androidRenderBlur
import com.box.app.ui.effect.navigationCancelSpec
import com.box.app.ui.effect.navigationPredictiveBackProgress
import com.box.app.ui.effect.navigationPopSpec
import com.box.app.ui.effect.navigationPushSpec
import com.box.app.ui.effect.navigationSceneProgress
import com.box.app.ui.effect.supportsAndroidRenderBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

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

    // 子页面状态
    var subPage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(subPage) {
        val atRoot = subPage == null
        onMainPagerUserScrollEnabledChange(atRoot)
        onMainTabAtRootChange(atRoot)
        if (!atRoot) onNavVisibilityChange(false)
    }

    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val transition = remember { androidx.compose.animation.core.Animatable(0f) }

    LaunchedEffect(subPage) {
        val target = if (subPage != null) 1f else 0f
        if (transition.targetValue == target && transition.value == target) return@LaunchedEffect
        transition.animateTo(
            target,
            animationSpec = if (target > transition.value) {
                navigationPushSpec()
            } else {
                navigationPopSpec()
            }
        )
    }

    if (subPage != null) {
        PredictiveBackHandler { progress: Flow<androidx.activity.BackEventCompat> ->
            try {
                progress.collect { backEvent ->
                    transition.snapTo(navigationPredictiveBackProgress(backEvent.progress))
                }
                subPage = null
            } catch (e: CancellationException) {
                scope.launch {
                    transition.animateTo(
                        1f,
                        animationSpec = navigationCancelSpec()
                    )
                }
                throw e
            }
        }
    }

    val blurSupported = remember { supportsAndroidRenderBlur() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size -> containerWidthPx = size.width.toFloat() }
    ) {
        val w = containerWidthPx
        val t = transition.value
        val easedT = navigationSceneProgress(t)
        val mainOffsetX = if (w > 0f) (-w * 0.18f) * easedT else 0f
        val mainScale = 1f - 0.05f * easedT
        val subX = if (w > 0f) w * (1f - easedT) else 0f

        // 主页面 — 作为 backdrop 源
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = mainOffsetX
                    scaleX = mainScale
                    scaleY = mainScale
                }
                .androidRenderBlur(
                    radius = (40f * t).coerceAtMost(40f),
                    enabled = blurSupported && t > 0.02f
                )
        ) {
            SettingsMainContent(
                onNavVisibilityChange = onNavVisibilityChange,
                listState = listState,
                pagePadding = pagePadding,
                c = c,
                onOpenTheme = { subPage = "theme" },
                onOpenLatencyTargets = { subPage = "latency" },
                onOpenOpenSourceLicenses = { subPage = "licenses" },
                onOpenAbout = { subPage = "about" }
            )
        }

        if (t > 0f || subPage != null) {
            // 模糊遮罩
            if (blurSupported && t > 0.02f) {
                val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                val dimColor = if (isDark) Color.Black.copy(alpha = 0.35f * t)
                    else Color(0xFF606060).copy(alpha = 0.12f * t)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(dimColor)
                )
            } else if (t > 0.01f) {
                val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                val fallbackAlpha = if (isDark) 0.35f * t else 0.18f * t
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = fallbackAlpha }
                        .background(if (isDark) Color.Black else Color.Gray)
                )
            }

            // 子页面 — 只平移，不透明度变化
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = subX }
                    .background(MiuixTheme.colorScheme.background)
            ) {
                CompositionLocalProvider(LocalFloatingNavBarSpaceDp provides 0.dp) {
                    when (subPage) {
                        "theme" -> ThemeSettingsScreen(
                            onBack = { subPage = null }
                        )
                        "latency" -> LatencyTargetsScreen(
                            onBack = { subPage = null }
                        )
                        "licenses" -> OpenSourceLicensesScreen(
                            onBack = { subPage = null },
                            onNavVisibilityChange = onNavVisibilityChange,
                            enableBackHandler = false
                        )
                        "about" -> AboutScreen(
                            onBack = { subPage = null }
                        )
                    }
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
    onOpenTheme: () -> Unit = {},
    onOpenLatencyTargets: () -> Unit = {},
    onOpenOpenSourceLicenses: () -> Unit,
    onOpenAbout: () -> Unit = {}
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
    val systemBarSettings by ThemeManager.systemBarSettings.collectAsState()
    val liquidGlassTranslucent by ThemeManager.liquidGlassTranslucent.collectAsState()
    val liquidGlassBlurDp by ThemeManager.liquidGlassBlurDp.collectAsState()
    val liquidGlassLensStrength by ThemeManager.liquidGlassLensStrength.collectAsState()
    val bottomSheetBlur by ThemeManager.bottomSheetBlur.collectAsState()
    val blurEffectsEnabled by ThemeManager.blurEffectsEnabled.collectAsState()
    val blurEffectsSupported = ThemeManager.supportsBlurEffects()
    val blurEffectsActive = blurEffectsEnabled && blurEffectsSupported
    val liquidGlassNavBarEnabled by ThemeManager.liquidGlassNavBar.collectAsState()
    val uiScalePercent by UiScaleManager.uiScalePercent.collectAsState()
    val currentLanguage by LanguageManager.language.collectAsState()
    val latencyTargets by LatencyTargetsManager.targets.collectAsState()
    val updateCheckStatus by UpdateCheckManager.status.collectAsState()

    var pendingUiScalePercent by rememberSaveable { mutableStateOf(uiScalePercent.toFloat()) }
    var uiScaleDragging by remember { mutableStateOf(false) }

    LaunchedEffect(uiScalePercent) {
        if (!uiScaleDragging) pendingUiScalePercent = uiScalePercent.toFloat()
    }

    var showAppearanceTuningSheet by remember { mutableStateOf(false) }
    var showProxyTrafficFilterSheet by remember { mutableStateOf(false) }
    // showLatencyTargetsSheet 已移除 — 延迟目标使用二级页面
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

    var appVersionText by remember { mutableStateOf("") }
    var moduleVersionText by remember { mutableStateOf("-") }
    var updateResult by remember { mutableStateOf<com.box.app.data.model.UpdateCheckResult?>(null) }
    var updateTarget by remember { mutableStateOf(UpdateTarget.MODULE) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var lastDownloadId by remember { mutableStateOf<Long?>(null) }
    var lastDownloadMime by remember { mutableStateOf<String?>(null) }

    // latencyName/Url 状态已移至 LatencyTargetsScreen

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
        target: UpdateTarget
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


    if (showUpdateDialog && updateResult != null) {
        AppModalBottomSheet(
            onDismissRequest = { showUpdateDialog = false }
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

    val themeLabel = when (currentThemeMode) {
        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_follow_system)
    }
    val languageLabel = when (currentLanguage) {
        AppLanguage.SYSTEM -> stringResource(R.string.settings_language_follow_system)
        AppLanguage.ENGLISH -> stringResource(R.string.settings_language_english)
        AppLanguage.CHINESE -> stringResource(R.string.settings_language_chinese)
    }
    val transparentLabel = stringResource(R.string.settings_system_bars_transparent)
    val opaqueLabel = stringResource(R.string.settings_system_bars_opaque)
    val blurSummary = stringResource(
        if (blurEffectsSupported) {
            R.string.settings_blur_effects_subtitle
        } else {
            R.string.settings_blur_effects_unsupported_subtitle
        }
    )
    val uiScaleSummary = stringResource(R.string.settings_ui_scale, pendingUiScalePercent.roundToInt())
    val visualTuneSummary = buildString {
        append(uiScaleSummary)
        if (blurEffectsActive) {
            append("  ·  Blur ")
            append(liquidGlassBlurDp.roundToInt())
            append("dp")
            if (liquidGlassTranslucent) {
                append("  ·  Lens ")
                append((liquidGlassLensStrength * 10f).roundToInt() / 10f)
                append("x")
            }
        }
    }
    val latencyPreview = latencyTargets.joinToString("  ·  ") { target ->
        val resolvedName = target.name.trim().ifBlank { "-" }
        val host = runCatching { Uri.parse(target.url.trim()).host }.getOrNull().orEmpty()
        if (host.isBlank()) resolvedName else "$resolvedName · $host"
    }
    val themeEntries = listOf(
        stringResource(R.string.settings_theme_follow_system),
        stringResource(R.string.settings_theme_light),
        stringResource(R.string.settings_theme_dark)
    )
    val languageEntries = listOf(
        stringResource(R.string.settings_language_follow_system),
        stringResource(R.string.settings_language_english),
        stringResource(R.string.settings_language_chinese)
    )
    val systemBarEntries = listOf(
        transparentLabel,
        opaqueLabel
    )

    BackupRestoreSheet(
        show = showBackupRestoreDialog,
        onDismiss = { showBackupRestoreDialog = false }
    )

    if (showAppearanceTuningSheet) {
        AppModalBottomSheet(
            onDismissRequest = { showAppearanceTuningSheet = false }
        ) {
            val sheetScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(sheetScrollState)
                    .padding(horizontal = 18.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_appearance_more),
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.SemiBold,
                    color = c.textPrimary
                )
                Text(
                    text = stringResource(R.string.settings_appearance_more_subtitle),
                    style = MiuixTheme.textStyles.body2,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 18.dp)
                )

                if (blurEffectsActive) {
                    if (liquidGlassTranslucent) {
                        Text(
                            text = stringResource(R.string.settings_liquid_glass_lens_strength),
                            style = MiuixTheme.textStyles.body1,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textPrimary
                        )
                        Text(
                            text = stringResource(R.string.settings_liquid_glass_lens_strength_subtitle),
                            style = MiuixTheme.textStyles.footnote1,
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
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Text(
                        text = stringResource(R.string.settings_liquid_glass_blur_strength),
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.SemiBold,
                        color = c.textPrimary
                    )
                    Text(
                        text = stringResource(R.string.settings_liquid_glass_blur_strength_subtitle),
                        style = MiuixTheme.textStyles.footnote1,
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
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Text(
                    text = uiScaleSummary,
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.SemiBold,
                    color = c.textPrimary
                )
                Text(
                    text = stringResource(R.string.settings_ui_scale_subtitle),
                    style = MiuixTheme.textStyles.footnote1,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
                )
                Slider(
                    value = pendingUiScalePercent,
                    onValueChange = { value ->
                        uiScaleDragging = true
                        pendingUiScalePercent = value.coerceIn(80f, 120f)
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
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    if (showProxyTrafficFilterSheet) {
        AppModalBottomSheet(
            onDismissRequest = { showProxyTrafficFilterSheet = false }
        ) {
            val sheetScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(sheetScrollState)
                    .padding(horizontal = 18.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_filter_chains),
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.SemiBold,
                    color = c.textPrimary
                )
                Text(
                    text = stringResource(R.string.settings_filter_chains_subtitle),
                    style = MiuixTheme.textStyles.body2,
                    color = c.textSecondary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
                )
                SettingsTextFieldRow(
                    icon = Icons.Filled.Category,
                    title = stringResource(R.string.settings_filter_chains),
                    subtitle = stringResource(R.string.settings_filter_chains_subtitle),
                    value = proxyTrafficFilterText,
                    placeholder = defaultProxyTrafficFilter,
                    onValueChange = { proxyTrafficFilterText = it }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, c.divider.copy(alpha = 0.75f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textSecondary),
                        onClick = { proxyTrafficFilterText = defaultProxyTrafficFilter }
                    ) {
                        Text(text = stringResource(R.string.settings_latency_reset))
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MiuixTheme.colorScheme.primary,
                            contentColor = MiuixTheme.colorScheme.onPrimary
                        ),
                        onClick = { showProxyTrafficFilterSheet = false }
                    ) {
                        Text(text = stringResource(R.string.action_apply))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // 延迟目标编辑已移至 LatencyTargetsScreen 二级页面

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.settings_title),
                subtitle = stringResource(R.string.settings_subtitle)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                ,
            contentPadding = contentPaddingWithNavBars(
                top = innerPadding.calculateTopPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item {
                SettingsPreferenceSection(
                    title = stringResource(R.string.settings_section_appearance)
                ) {
                ArrowPreference(
                    title = stringResource(R.string.settings_theme_appearance),
                    summary = stringResource(R.string.settings_theme_appearance_subtitle),
                    onClick = onOpenTheme
                )
                SettingsPreferenceDivider()
                WindowDropdownPreference(
                    items = languageEntries,
                    selectedIndex = when (currentLanguage) {
                        AppLanguage.SYSTEM -> 0
                        AppLanguage.ENGLISH -> 1
                        AppLanguage.CHINESE -> 2
                    },
                    title = stringResource(R.string.settings_language),

                    onSelectedIndexChange = { index ->
                        val language = when (index) {
                            1 -> AppLanguage.ENGLISH
                            2 -> AppLanguage.CHINESE
                            else -> AppLanguage.SYSTEM
                        }
                        LanguageManager.setLanguage(context, language)
                    }
                )
                SettingsPreferenceDivider()
                WindowDropdownPreference(
                    items = systemBarEntries,
                    selectedIndex = if (systemBarSettings.statusBar == com.box.app.utils.SystemBarMode.TRANSPARENT) 0 else 1,
                    title = stringResource(R.string.settings_system_bars_opaque_status_title),

                    summary = stringResource(R.string.settings_system_bars_opaque_subtitle),
                    onSelectedIndexChange = { index ->
                        ThemeManager.setSystemBarSettings(
                            context,
                            systemBarSettings.copy(
                                statusBar = if (index == 0) com.box.app.utils.SystemBarMode.TRANSPARENT else com.box.app.utils.SystemBarMode.OPAQUE
                            )
                        )
                    }
                )
                SettingsPreferenceDivider()
                WindowDropdownPreference(
                    items = systemBarEntries,
                    selectedIndex = if (systemBarSettings.navigationBar == com.box.app.utils.SystemBarMode.TRANSPARENT) 0 else 1,
                    title = stringResource(R.string.settings_system_bars_opaque_navigation_title),

                    summary = stringResource(R.string.settings_system_bars_opaque_subtitle),
                    onSelectedIndexChange = { index ->
                        ThemeManager.setSystemBarSettings(
                            context,
                            systemBarSettings.copy(
                                navigationBar = if (index == 0) com.box.app.utils.SystemBarMode.TRANSPARENT else com.box.app.utils.SystemBarMode.OPAQUE
                            )
                        )
                    }
                )
                }
            }

            if (BuildConfig.FLAVOR != "bfr") {
                item {
                    SettingsPreferenceSection(
                        title = stringResource(R.string.settings_section_service)
                    ) {
                        SwitchPreference(
                            checked = autoStart,
                            onCheckedChange = { enabled ->
                                autoStart = enabled
                                scope.launch {
                                    runCatching { BoxApi.updateBooleanSetting("boot_auto_start", enabled) }
                                }
                            },
                            title = stringResource(R.string.settings_auto_start),
                            summary = stringResource(R.string.settings_auto_start_subtitle),
                        )
                        SettingsPreferenceDivider()
                        SwitchPreference(
                            checked = acceleratedDownload,
                            onCheckedChange = { enabled ->
                                acceleratedDownload = enabled
                                scope.launch {
                                    runCatching { BoxApi.updateBooleanSetting("use_ghproxy", enabled) }
                                }
                            },
                            title = stringResource(R.string.settings_accelerated_download),
                            summary = stringResource(R.string.settings_accelerated_download_subtitle),
                        )
                    }
                }
            }

            item {
                SettingsPreferenceSection(
                    title = stringResource(R.string.settings_section_subscription)
                ) {
                SwitchPreference(
                    checked = useClashApi,
                    onCheckedChange = { useClashApi = it },
                    title = stringResource(R.string.settings_use_clash_api),
                    summary = if (useClashApi) stringResource(R.string.settings_providers_mode) else stringResource(R.string.settings_url_mode),
                )
                }
            }

            item {
                SettingsPreferenceSection(
                    title = stringResource(R.string.settings_section_net_speed)
                ) {
                SwitchPreference(
                    checked = useClashApiForNetSpeed,
                    onCheckedChange = { useClashApiForNetSpeed = it },
                    title = stringResource(R.string.settings_use_clash_api),
                    summary = if (useClashApiForNetSpeed) stringResource(R.string.settings_core_api_mode) else stringResource(R.string.settings_system_mode),
                )

                if (useClashApiForNetSpeed) {
                    SettingsPreferenceDivider()
                    ArrowPreference(
                        title = stringResource(R.string.settings_filter_chains),
                        summary = proxyTrafficFilterText.ifBlank { defaultProxyTrafficFilter },
                        onClick = { showProxyTrafficFilterSheet = true }
                    )
                }
                }
            }

            item {
                SettingsPreferenceSection(
                    title = stringResource(R.string.settings_latency_targets_title)
                ) {
                ArrowPreference(
                    title = stringResource(R.string.settings_latency_targets_title),
                    summary = latencyPreview,
                    onClick = onOpenLatencyTargets
                )
                }
            }

            item {
                SettingsPreferenceSection(
                    title = stringResource(R.string.settings_section_misc)
                ) {
                SwitchPreference(
                    checked = openPanelOnLaunch,
                    onCheckedChange = { openPanelOnLaunch = it },
                    title = stringResource(R.string.settings_open_panel_on_launch),
                    summary = stringResource(R.string.settings_open_panel_on_launch_subtitle),
                )
                SettingsPreferenceDivider()
                SwitchPreference(
                    checked = enableNotifications,
                    onCheckedChange = { enableNotifications = it },
                    title = stringResource(R.string.settings_notifications),
                    summary = stringResource(R.string.settings_notifications_subtitle),
                )
                SettingsPreferenceDivider()
                ArrowPreference(
                    title = stringResource(R.string.settings_backup_restore_title),
                    summary = stringResource(R.string.settings_backup_restore_subtitle),
                    onClick = { showBackupRestoreDialog = true }
                )
                }
            }

            item {
                SettingsPreferenceSection(
                    title = stringResource(R.string.settings_section_about)
                ) {
                ArrowPreference(
                    title = stringResource(R.string.settings_version),
                    summary = "v$appVersionText",
                    endActions = {
                        VersionUpdateBadge(
                            isChecking = updateCheckStatus.isChecking,
                            appHasUpdate = updateCheckStatus.appHasUpdate,
                            moduleHasUpdate = updateCheckStatus.moduleHasUpdate,
                            onAppClick = {
                                scope.launch {
                                    openUpdateSheetFor(UpdateTarget.APP)
                                }
                            },
                            onModuleClick = {
                                scope.launch {
                                    openUpdateSheetFor(UpdateTarget.MODULE)
                                }
                            }
                        )
                    },
                    onClick = onOpenAbout
                )
                SettingsPreferenceDivider()
                ArrowPreference(
                    title = stringResource(R.string.settings_open_source_licenses),
                    summary = stringResource(R.string.settings_open_source_licenses_subtitle),
                    onClick = { onOpenOpenSourceLicenses() }
                )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun SettingsHeroCard(
    appIconPainter: Painter?,
    appVersionText: String,
    themeLabel: String,
    languageLabel: String,
    isChecking: Boolean,
    appHasUpdate: Boolean,
    moduleHasUpdate: Boolean,
    onOpenAbout: () -> Unit,
    onAppClick: (() -> Unit)? = null,
    onModuleClick: (() -> Unit)? = null
) {
    val c = appColors()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        onClick = onOpenAbout
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (appIconPainter != null) {
                Image(
                    painter = appIconPainter,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedRectangle(14.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedRectangle(14.dp))
                        .background(MiuixTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                top.yukonga.miuix.kmp.basic.Text(
                    text = stringResource(R.string.app_name),
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                top.yukonga.miuix.kmp.basic.Text(
                    text = "v$appVersionText",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
                top.yukonga.miuix.kmp.basic.Text(
                    text = "$themeLabel  ·  $languageLabel",
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }

            VersionUpdateBadge(
                isChecking = isChecking,
                appHasUpdate = appHasUpdate,
                moduleHasUpdate = moduleHasUpdate,
                onAppClick = onAppClick,
                onModuleClick = onModuleClick
            )
        }
    }
}

@Composable
private fun SettingsPreferenceSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallTitle(text = title)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(bottom = 6.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsPreferenceDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(0.5.dp)
            .background(MiuixTheme.colorScheme.dividerLine.copy(alpha = 0.08f))
    )
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

    val scheme = MiuixTheme.colorScheme
    val chipBg = scheme.surfaceContainerHighest
    val chipBorder = scheme.dividerLine
    val appTint = scheme.primary
    val moduleTint = scheme.onSecondaryContainer
    val updateDot = scheme.error
    val labelColor = scheme.onSurface

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isChecking && !appHasUpdate && !moduleHasUpdate) {
            CheckingUpdateBadge(
                containerColor = chipBg,
                borderColor = chipBorder,
                indicatorColor = scheme.onSurfaceSecondary
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
                style = MiuixTheme.textStyles.footnote2,
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
                style = MiuixTheme.textStyles.footnote2,
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
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary
            )
            Text(
                text = subtitle,
                style = MiuixTheme.textStyles.footnote1,
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
            Text(text = placeholder, style = MiuixTheme.textStyles.body2)
        },
        singleLine = true,
        textStyle = MiuixTheme.textStyles.body2.copy(color = c.textPrimary),
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
private fun BackupRestoreSheet(
    show: Boolean,
    onDismiss: () -> Unit
) {
    if (!show) return

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

    // 恢复确认弹窗
    if (showRestoreConfirm) {
        OverlayDialog(
            show = true,
            onDismissRequest = { if (!working) showRestoreConfirm = false },
            title = stringResource(R.string.settings_backup_restore_restore),
            summary = stringResource(R.string.settings_backup_restore_confirm_body, restoreScopeLabel(restoreScope)),
            backgroundColor = MiuixTheme.colorScheme.surfaceContainer,
            titleColor = MiuixTheme.colorScheme.onSurface,
            summaryColor = MiuixTheme.colorScheme.onSurfaceSecondary
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = { showRestoreConfirm = false },
                    enabled = !working
                )
                top.yukonga.miuix.kmp.basic.TextButton(
                    text = stringResource(R.string.settings_backup_restore_confirm_continue),
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
                    },
                    enabled = !working
                )
            }
        }
    }

    // 主 BottomSheet
    HyperBottomSheet(
        show = true,
        onDismissRequest = { if (!working) onDismiss() },
        title = stringResource(R.string.settings_backup_restore_title)
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 模式切换
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HyperFilterChip(
                    selected = mode == BackupRestoreMode.BACKUP,
                    onClick = { if (!working) mode = BackupRestoreMode.BACKUP },
                    label = stringResource(R.string.settings_backup_restore_tab_backup),
                    enabled = !working
                )
                HyperFilterChip(
                    selected = mode == BackupRestoreMode.RESTORE,
                    onClick = { if (!working) mode = BackupRestoreMode.RESTORE },
                    label = stringResource(R.string.settings_backup_restore_tab_restore),
                    enabled = !working
                )
            }

            if (mode == BackupRestoreMode.BACKUP) {
                // ── 备份模式 ──
                Text(
                    text = stringResource(R.string.settings_backup_restore_scope_title),
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HyperFilterChip(
                        selected = backupScope == BackupRestoreScope.MODULES,
                        onClick = { backupScope = BackupRestoreScope.MODULES },
                        label = stringResource(R.string.settings_backup_restore_scope_modules),
                        enabled = !working
                    )
                    HyperFilterChip(
                        selected = backupScope == BackupRestoreScope.APPS,
                        onClick = { backupScope = BackupRestoreScope.APPS },
                        label = stringResource(R.string.settings_backup_restore_scope_apps),
                        enabled = !working
                    )
                    HyperFilterChip(
                        selected = backupScope == BackupRestoreScope.BOTH,
                        onClick = { backupScope = BackupRestoreScope.BOTH },
                        label = stringResource(R.string.settings_backup_restore_scope_both),
                        enabled = !working
                    )
                }

                // 导出按钮
                top.yukonga.miuix.kmp.basic.Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !working,
                    colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColorsPrimary(),
                    onClick = {
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
                    if (working) {
                        top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text(
                            text = stringResource(R.string.settings_backup_restore_export),
                            color = MiuixTheme.colorScheme.onPrimary
                        )
                    }
                }

                if (lastExportedName != null) {
                    Text(
                        text = stringResource(R.string.settings_backup_restore_exported, lastExportedName!!),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            } else {
                // ── 恢复模式 ──
                Text(
                    text = stringResource(R.string.settings_backup_restore_file_title),
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface
                )

                // 文件选择卡片
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    insideMargin = PaddingValues(14.dp),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceVariant),
                    onClick = if (!working) { { picker.launch(arrayOf("application/zip")) } } else null
                ) {
                    Text(
                        text = pickedName ?: stringResource(R.string.settings_backup_restore_file_pick),
                        style = MiuixTheme.textStyles.body2,
                        color = if (pickedName != null) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceSecondary
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
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )

                    Text(
                        text = stringResource(R.string.settings_backup_restore_scope_title),
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HyperFilterChip(
                            selected = restoreScope == BackupRestoreScope.MODULES,
                            onClick = { restoreScope = BackupRestoreScope.MODULES },
                            label = stringResource(R.string.settings_backup_restore_scope_modules_only),
                            enabled = !working
                        )
                        HyperFilterChip(
                            selected = restoreScope == BackupRestoreScope.APPS,
                            onClick = { restoreScope = BackupRestoreScope.APPS },
                            label = stringResource(R.string.settings_backup_restore_scope_apps_only),
                            enabled = !working
                        )
                        HyperFilterChip(
                            selected = restoreScope == BackupRestoreScope.BOTH,
                            onClick = { restoreScope = BackupRestoreScope.BOTH },
                            label = stringResource(R.string.settings_backup_restore_scope_both),
                            enabled = !working
                        )
                    }

                    // 恢复按钮
                    top.yukonga.miuix.kmp.basic.Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !working,
                        colors = top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColorsPrimary(),
                        onClick = { showRestoreConfirm = true }
                    ) {
                        if (working) {
                            top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator(modifier = Modifier.size(16.dp))
                        } else {
                            Text(
                                text = stringResource(R.string.settings_backup_restore_restore),
                                color = MiuixTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }

            // 状态文本
            if (status != null) {
                Text(
                    text = status!!,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
