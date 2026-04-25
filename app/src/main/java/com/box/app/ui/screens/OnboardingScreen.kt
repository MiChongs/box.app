package com.box.app.ui.screens

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.box.app.BuildConfig
import com.box.app.R
import com.box.app.data.backend.EnvironmentChecker
import com.box.app.utils.AppLanguage
import com.box.app.utils.LanguageManager
import com.box.app.utils.Permissions
import com.box.app.utils.ThemeManager
import com.box.app.utils.ThemeMode
import com.box.app.utils.rememberPermissionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.shapes.SmoothRoundedCornerShape
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 5 })

    val permHelper = rememberPermissionHelper()
    var tosAccepted by rememberSaveable { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }

    // 生命周期感知：从设置页返回时刷新权限状态
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnResume by rememberUpdatedState { refreshKey += 1 }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) latestOnResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Root 状态
    var hasRoot by remember { mutableStateOf(false) }
    var requestingRoot by remember { mutableStateOf(false) }
    var rootError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshKey) {
        hasRoot = runCatching { EnvironmentChecker.check(forceRefresh = true).hasRoot }.getOrDefault(false)
    }

    // 权限状态
    val hasNotifications = remember(refreshKey) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permHelper.isGranted(Permissions.POST_NOTIFICATIONS) else true
    }
    val hasWifi = remember(refreshKey) {
        val loc = permHelper.isGranted(Permissions.ACCESS_FINE_LOCATION)
        val nearby = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permHelper.isGranted(Permissions.NEARBY_WIFI_DEVICES) else true
        loc && nearby
    }
    val hasInstalledApps = remember(refreshKey) { permHelper.isGranted(Permissions.GET_INSTALLED_APPS) }

    fun requestRoot() {
        if (requestingRoot) return
        rootError = null
        scope.launch {
            requestingRoot = true
            repeat(2) { attempt ->
                val granted = runCatching { EnvironmentChecker.requestRootAccess() }.getOrDefault(false)
                if (granted) { hasRoot = true; requestingRoot = false; return@launch }
                if (attempt == 0) delay(500)
            }
            hasRoot = runCatching { EnvironmentChecker.check(forceRefresh = true).hasRoot }.getOrDefault(false)
            if (!hasRoot) rootError = context.getString(R.string.onboarding_root_denied)
            requestingRoot = false
        }
    }

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permHelper.request(Permissions.POST_NOTIFICATIONS) { refreshKey += 1 }
    }

    fun requestInstalledApps() {
        permHelper.request(Permissions.GET_INSTALLED_APPS) { result ->
            refreshKey += 1
            if (result.doNotAskAgain && result.denied.isNotEmpty()) permHelper.openSettings(result.denied)
        }
    }

    fun requestWifiPerms() {
        val perms = buildList {
            add(Permissions.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Permissions.NEARBY_WIFI_DEVICES)
        }
        permHelper.request(*perms.toTypedArray()) { result ->
            refreshKey += 1
            if (result.doNotAskAgain && result.denied.isNotEmpty()) permHelper.openSettings(result.denied)
        }
    }

    // 完成页退出动画
    val exitScale = remember { Animatable(1f) }
    val exitAlpha = remember { Animatable(1f) }
    var exiting by remember { mutableStateOf(false) }

    // 系统返回键：非首页返回上一页，首页消费事件不做处理（防止退出引导）
    BackHandler {
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
            .graphicsLayer {
                scaleX = exitScale.value
                scaleY = exitScale.value
                alpha = exitAlpha.value
            }
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false
        ) { page ->
            when (page) {
                0 -> StartupPage(
                    onNext = { scope.launch { pagerState.animateScrollToPage(1) } }
                )
                1 -> PermissionsPage(
                    hasRoot = hasRoot, hasNotifications = hasNotifications,
                    hasWifi = hasWifi, hasInstalledApps = hasInstalledApps,
                    requestingRoot = requestingRoot, rootError = rootError,
                    onRequestRoot = ::requestRoot, onRequestNotifications = ::requestNotifications,
                    onRequestWifi = ::requestWifiPerms, onRequestInstalledApps = ::requestInstalledApps,
                    onBack = { scope.launch { pagerState.animateScrollToPage(0) } },
                    onNext = { scope.launch { pagerState.animateScrollToPage(2) } }
                )
                2 -> AgreementPage(
                    tosAccepted = tosAccepted, onTosChanged = { tosAccepted = it },
                    onBack = { scope.launch { pagerState.animateScrollToPage(1) } },
                    onNext = { scope.launch { pagerState.animateScrollToPage(3) } }
                )
                3 -> BasicSettingsPage(
                    onBack = { scope.launch { pagerState.animateScrollToPage(2) } },
                    onNext = { scope.launch { pagerState.animateScrollToPage(4) } }
                )
                4 -> CompletionPage(
                    onFinish = {
                        if (exiting) return@CompletionPage
                        exiting = true
                        scope.launch {
                            // startPageAnim: scale 1→0.8 spring, alpha 1→0 sinOut 360ms
                            launch { exitScale.animateTo(0.8f, tween(400, easing = FastOutSlowInEasing)) }
                            launch { exitAlpha.animateTo(0f, tween(360, easing = FastOutSlowInEasing)) }
                            delay(400)
                            onFinish()
                        }
                    }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Page 1: 启动页（复刻 HyperCeiler StartupFragment）
// ═══════════════════════════════════════════════════════════════

@Composable
private fun StartupPage(onNext: () -> Unit) {
    // ── Logo 动画: scale 0.5→0.95(440ms sinOut)→1.0(700ms cubicOut), alpha 0→1(delay 60ms) ──
    var logoVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { logoVisible = true }

    val logoScale = remember { Animatable(0.5f) }
    val logoAlpha = remember { Animatable(0f) }
    LaunchedEffect(logoVisible) {
        if (logoVisible) {
            launch { logoAlpha.animateTo(1f, tween(300, delayMillis = 60)) }
            logoScale.animateTo(0.95f, tween(440, easing = FastOutSlowInEasing))
            logoScale.animateTo(1.0f, tween(700, easing = FastOutSlowInEasing))
        }
    }

    // ── 文字动画: translationY 100→0(1700ms spring), alpha 0→1(1400ms, delay 300ms) ──
    val textOffsetY = remember { Animatable(100f) }
    val textAlpha = remember { Animatable(0f) }
    LaunchedEffect(logoVisible) {
        if (logoVisible) {
            launch { textOffsetY.animateTo(0f, tween(1700, easing = FastOutSlowInEasing)) }
            launch { textAlpha.animateTo(1f, tween(1400, delayMillis = 300)) }
        }
    }

    // ── 按钮动画: scale 0.9→1.0, alpha 0→1, delay 1340ms, cubicOut 450ms ──
    var btnVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(1340); btnVisible = true }

    val btnScale = remember { Animatable(0.9f) }
    val btnAlpha = remember { Animatable(0f) }
    LaunchedEffect(btnVisible) {
        if (btnVisible) {
            launch { btnScale.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
            launch { btnAlpha.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // 居中内容：Logo + 品牌名
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .graphicsLayer {
                        scaleX = logoScale.value; scaleY = logoScale.value
                        alpha = logoAlpha.value
                    }
                    .clip(SmoothRoundedCornerShape(24.dp))
                    .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_box_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(70.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.graphicsLayer {
                    translationY = textOffsetY.value
                    alpha = textAlpha.value
                }
            )
        }

        // 底部圆形箭头按钮
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
                .graphicsLayer {
                    scaleX = btnScale.value; scaleY = btnScale.value
                    alpha = btnAlpha.value
                }
        ) {
            IconButton(
                onClick = { if (btnVisible) onNext() },
                modifier = Modifier.size(56.dp),
                cornerRadius = 28.dp,
                backgroundColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Page 2: 权限设置
// ═══════════════════════════════════════════════════════════════

@Composable
private fun PermissionsPage(
    hasRoot: Boolean, hasNotifications: Boolean, hasWifi: Boolean, hasInstalledApps: Boolean,
    requestingRoot: Boolean, rootError: String?,
    onRequestRoot: () -> Unit, onRequestNotifications: () -> Unit,
    onRequestWifi: () -> Unit, onRequestInstalledApps: () -> Unit,
    onBack: () -> Unit, onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // 顶栏
        Box(modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp), contentAlignment = Alignment.CenterStart) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MiuixTheme.colorScheme.onSurface)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.onboarding_permissions_title),
                fontSize = 28.sp, fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_optional_permissions_hint),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            Spacer(modifier = Modifier.height(24.dp))

            // 必需：Root
            SmallTitle(text = stringResource(R.string.onboarding_required_permissions))
            Card(modifier = Modifier.fillMaxWidth()) {
                PermItem(
                    title = stringResource(R.string.onboarding_perm_root_title),
                    summary = when {
                        hasRoot -> stringResource(R.string.onboarding_perm_granted)
                        requestingRoot -> stringResource(R.string.onboarding_perm_requesting)
                        rootError != null -> rootError
                        else -> stringResource(R.string.onboarding_perm_root_summary)
                    },
                    granted = hasRoot,
                    onClick = if (!hasRoot && !requestingRoot) onRequestRoot else null
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // 可选
            SmallTitle(text = stringResource(R.string.onboarding_optional_permissions_hint))
            Card(modifier = Modifier.fillMaxWidth()) {
                PermItem(
                    title = stringResource(R.string.onboarding_perm_notifications_title),
                    summary = if (hasNotifications) stringResource(R.string.onboarding_perm_granted) else stringResource(R.string.onboarding_perm_notifications_summary),
                    granted = hasNotifications, onClick = if (!hasNotifications) onRequestNotifications else null
                )
                PermItem(
                    title = stringResource(R.string.onboarding_perm_wifi_title),
                    summary = if (hasWifi) stringResource(R.string.onboarding_perm_granted) else stringResource(R.string.onboarding_perm_wifi_summary),
                    granted = hasWifi, onClick = if (!hasWifi) onRequestWifi else null
                )
                PermItem(
                    title = stringResource(R.string.onboarding_perm_apps_title),
                    summary = if (hasInstalledApps) stringResource(R.string.onboarding_perm_granted) else stringResource(R.string.onboarding_perm_apps_summary),
                    granted = hasInstalledApps, onClick = if (!hasInstalledApps) onRequestInstalledApps else null
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // 底部按钮
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColorsPrimary()) {
                Text(text = stringResource(R.string.onboarding_continue), style = MiuixTheme.textStyles.button)
            }
        }
    }
}

@Composable
private fun PermItem(title: String, summary: String, granted: Boolean, onClick: (() -> Unit)?) {
    BasicComponent(
        title = title, summary = summary,
        endActions = {
            if (granted) Icon(imageVector = Icons.Filled.Check, contentDescription = null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        },
        onClick = onClick
    )
}

// ═══════════════════════════════════════════════════════════════
// Page 3: 协议与声明
// ═══════════════════════════════════════════════════════════════

@Composable
private fun AgreementPage(
    tosAccepted: Boolean, onTosChanged: (Boolean) -> Unit,
    onBack: () -> Unit, onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp), contentAlignment = Alignment.CenterStart) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MiuixTheme.colorScheme.onSurface)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.onboarding_agreement_title),
                fontSize = 28.sp, fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(24.dp))
            Card(modifier = Modifier.fillMaxWidth(), insideMargin = PaddingValues(16.dp)) {
                Text(
                    text = stringResource(R.string.onboarding_disclaimer_body),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                CheckboxPreference(
                    title = stringResource(R.string.onboarding_tos_accept),
                    checked = tosAccepted,
                    onCheckedChange = { onTosChanged(it) }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Button(
                onClick = onNext, enabled = tosAccepted,
                modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                Text(text = stringResource(R.string.onboarding_agree_and_continue), style = MiuixTheme.textStyles.button)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Page 4: 基本设置
// ═══════════════════════════════════════════════════════════════

@Composable
private fun BasicSettingsPage(onBack: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    val currentLanguage by LanguageManager.language.collectAsState()
    val currentThemeMode by ThemeManager.themeMode.collectAsState()

    val languageEntries = listOf(
        stringResource(R.string.settings_theme_follow_system),
        "English",
        "简体中文"
    )
    val themeEntries = listOf(
        stringResource(R.string.settings_theme_follow_system),
        stringResource(R.string.settings_theme_light),
        stringResource(R.string.settings_theme_dark)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp), contentAlignment = Alignment.CenterStart) {
            IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = MiuixTheme.colorScheme.onSurface)
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.onboarding_basic_settings),
                fontSize = 28.sp, fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_basic_settings_desc),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
            Spacer(modifier = Modifier.height(24.dp))

            // 主题模式
            SmallTitle(text = stringResource(R.string.settings_theme_mode))
            TabRow(
                tabs = themeEntries,
                selectedTabIndex = when (currentThemeMode) {
                    ThemeMode.SYSTEM -> 0; ThemeMode.LIGHT -> 1; ThemeMode.DARK -> 2
                },
                onTabSelected = { index ->
                    val mode = when (index) { 1 -> ThemeMode.LIGHT; 2 -> ThemeMode.DARK; else -> ThemeMode.SYSTEM }
                    ThemeManager.setThemeMode(context, mode)
                },
                modifier = Modifier.fillMaxWidth(),
                height = 48.dp
            )
            Spacer(modifier = Modifier.height(20.dp))

            // 语言
            SmallTitle(text = stringResource(R.string.settings_language))
            Card(modifier = Modifier.fillMaxWidth()) {
                OverlayDropdownPreference(
                    title = stringResource(R.string.settings_language),
                    items = languageEntries,
                    selectedIndex = when (currentLanguage) { AppLanguage.SYSTEM -> 0; AppLanguage.ENGLISH -> 1; AppLanguage.CHINESE -> 2 },
                    onSelectedIndexChange = { index ->
                        val lang = when (index) { 1 -> AppLanguage.ENGLISH; 2 -> AppLanguage.CHINESE; else -> AppLanguage.SYSTEM }
                        LanguageManager.setLanguage(context, lang)
                    }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColorsPrimary()) {
                Text(text = stringResource(R.string.onboarding_continue), style = MiuixTheme.textStyles.button)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// Page 5: 完成页（复刻 HyperCeiler CongratulationFragment）
// ═══════════════════════════════════════════════════════════════

@Composable
private fun CompletionPage(onFinish: () -> Unit) {
    // ── Logo 动画: translationY 100→0(1500ms quartOut), alpha 0→1(1500ms) ──
    val logoOffsetY = remember { Animatable(100f) }
    val logoAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { logoOffsetY.animateTo(0f, tween(1500, easing = FastOutSlowInEasing)) }
        launch { logoAlpha.animateTo(1f, tween(1500, easing = FastOutSlowInEasing)) }
    }

    // ── 按钮动画: alpha 0→1, sinOut 450ms, delay 1000ms, enabled after 2000ms ──
    var btnEnabled by remember { mutableStateOf(false) }
    val btnAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(1000)
        btnAlpha.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
        delay(550) // total 2000ms from page entry
        btnEnabled = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // 居中内容
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    translationY = logoOffsetY.value
                    alpha = logoAlpha.value
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(SmoothRoundedCornerShape(24.dp))
                    .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_box_foreground),
                    contentDescription = null,
                    modifier = Modifier.size(70.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 36.sp, fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.onboarding_setup_complete),
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }

        // 底部按钮
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 44.dp)
                .graphicsLayer { alpha = btnAlpha.value }
        ) {
            Button(
                onClick = { if (btnEnabled) onFinish() },
                enabled = btnEnabled,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                Text(text = stringResource(R.string.onboarding_enter_app), style = MiuixTheme.textStyles.button)
            }
        }
    }
}
