package com.box.app.ui.components

import android.app.Activity
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.saveable.listSaver
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.ui.NavDisplay
import dev.lackluster.hyperx.ui.animation.HyperXNavTransitions
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableFloatStateOf
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.fillMaxWidth
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.All
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Sidebar
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import com.box.app.ui.screens.HomeScreen
import com.box.app.ui.screens.ToolsScreen
import com.box.app.ui.screens.SettingsScreen
import com.box.app.ui.screens.PanelScreen
import com.box.app.ui.screens.SmartDnsWebUiScreen
import com.box.app.ui.screens.SubStoreScreen
import com.box.app.ui.screens.tools.ToolsLogsScreen
import com.box.app.ui.screens.tools.ToolsUpdateSubscriptionScreen
import com.box.app.provision.activity.DefaultActivity
import com.box.app.R
import com.box.app.ui.components.LocalLiquidBackdrop
import com.box.app.ui.components.bottomsheets.LocalSheetBackdrop
import com.box.app.ui.components.bottomsheets.LocalSheetBlurState
import com.box.app.ui.components.bottomsheets.SheetBlurState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

import com.box.app.ui.components.LocalFloatingNavBarSpaceDp
import com.box.app.ui.components.LocalNavigationBarsPaddingEnabled
import com.box.app.ui.components.LocalSystemNavBarInsetDp
import com.box.app.ui.effect.androidRenderBlur
import com.box.app.ui.effect.navigationCancelSpec
import com.box.app.ui.effect.navigationPredictiveBackProgress
import com.box.app.ui.effect.navigationPopSpec
import com.box.app.ui.effect.navigationPushSpec
import com.box.app.ui.effect.navigationSceneProgress
import com.box.app.ui.effect.supportsAndroidRenderBlur
import com.box.app.utils.SystemBarMode
import com.box.app.utils.ThemeManager
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class MainTab(val labelResId: Int) {
    Home(R.string.main_tab_home),
    Tools(R.string.main_tab_tools),
    Settings(R.string.main_tab_settings)
}

class MainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    private var navJob: Job? = null
    private var navSequence: Int = 0

    fun onUserScrollSelectedPage(page: Int) {
        if (!isNavigating && selectedPage != page) {
            selectedPage = page
        }
    }

    fun animateToPage(targetIndex: Int) {
        val boundedTarget = targetIndex.coerceIn(0, pagerState.pageCount - 1)
        if (
            boundedTarget == selectedPage &&
            boundedTarget == pagerState.currentPage &&
            !pagerState.isScrollInProgress &&
            !isNavigating
        ) return

        navJob?.cancel()
        navSequence += 1
        val seq = navSequence
        selectedPage = boundedTarget
        isNavigating = true
        val distance = abs(boundedTarget - pagerState.currentPage).coerceAtLeast(1)
        val duration = 160 * distance + 180

        navJob = coroutineScope.launch {
            try {
                pagerState.animateScrollToPage(
                    page = boundedTarget,
                    pageOffsetFraction = 0f,
                    animationSpec = tween(
                        durationMillis = duration,
                        easing = FastOutSlowInEasing
                    )
                )
                if (pagerState.currentPage != boundedTarget) {
                    pagerState.scrollToPage(
                        page = boundedTarget,
                        pageOffsetFraction = 0f
                    )
                }
            } finally {
                if (seq == navSequence) {
                    try {
                        withContext(NonCancellable) {
                            if (pagerState.currentPage != boundedTarget) {
                                pagerState.scrollToPage(
                                    page = boundedTarget,
                                    pageOffsetFraction = 0f
                                )
                            }
                        }
                    } catch (_: Throwable) {
                        // Keep navigation state recoverable even if pager sync fails transiently.
                    }
                    isNavigating = false
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && !pagerState.isScrollInProgress && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
fun rememberMainPagerState(
    pagerState: PagerState,
    coroutineScope: kotlinx.coroutines.CoroutineScope = rememberCoroutineScope()
): MainPagerState {
    return remember(pagerState, coroutineScope) {
        MainPagerState(pagerState, coroutineScope)
    }
}

enum class AppScreen : NavKey {
    Main,
    Panel,
    SubStore,
    SmartDnsWebUi,
    ToolsLogs,
    ToolsUpdateSubscription,
    NetSpeed,
    SubscriptionDetail
}

fun MainTab.index(): Int = when (this) {
    MainTab.Home -> 0
    MainTab.Tools -> 1
    MainTab.Settings -> 2
}

@Composable
fun AppScaffold() {
    var currentMainTabIndex by rememberSaveable { mutableIntStateOf(MainTab.Home.index()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val openPanelOnLaunch = remember {
        val prefs = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
        prefs.getBoolean("open_panel_on_launch", false)
    }

    val appPrefs = remember { context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE) }
    var onboardingCompleted by remember {
        mutableStateOf(appPrefs.getBoolean("onboarding_completed", false))
    }
    var showOnboarding by remember { mutableStateOf(!onboardingCompleted) }
    var currentScreen by rememberSaveable {
        mutableStateOf(if (openPanelOnLaunch) AppScreen.Panel else AppScreen.Main)
    }
    var panelWebCanGoBack by rememberSaveable { mutableStateOf(false) }
    var subStoreWebCanGoBack by rememberSaveable { mutableStateOf(false) }
    var panelBackRequestKey by rememberSaveable { mutableIntStateOf(0) }
    var subStoreBackRequestKey by rememberSaveable { mutableIntStateOf(0) }
    var navVisible by remember { mutableStateOf(true) }
    var pagerUserScrollEnabled by remember { mutableStateOf(true) }
    var mainTabAtRoot by remember { mutableStateOf(true) }
    var backdropVersion by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                backdropVersion += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val uiBackdrop = key(backdropVersion) { rememberLayerBackdrop() }
    val contentBackdrop = key(backdropVersion) { rememberLayerBackdrop() }
    val navBlurSupported = remember { supportsAndroidRenderBlur() }
    val isDarkTheme = ThemeManager.shouldUseDarkTheme()
    val useHyperXNav by ThemeManager.hyperXNavTransitions.collectAsState()
    val sheetBlurState = remember { SheetBlurState() }

    var appScreenContainerWidthPx by remember { mutableFloatStateOf(0f) }
    val transition = remember(openPanelOnLaunch) { Animatable(if (openPanelOnLaunch) 1f else 0f) }
    var lastNonMainScreen by rememberSaveable {
        mutableStateOf<AppScreen?>(if (openPanelOnLaunch) AppScreen.Panel else null)
    }

    var smartDnsWebCanGoBack by remember { mutableStateOf(false) }
    var smartDnsBackRequestKey by remember { mutableIntStateOf(0) }

    fun exitSubpage() {
        currentScreen = AppScreen.Main
        panelWebCanGoBack = false
        subStoreWebCanGoBack = false
        smartDnsWebCanGoBack = false
    }

    fun openSubpage(screen: AppScreen) {
        scope.launch {
            transition.stop()
            transition.snapTo(0f)
            if (screen == AppScreen.Panel) {
                panelWebCanGoBack = false
                panelBackRequestKey = 0
            }
            if (screen == AppScreen.SubStore) {
                subStoreWebCanGoBack = false
                subStoreBackRequestKey = 0
            }
            if (screen == AppScreen.SmartDnsWebUi) {
                smartDnsWebCanGoBack = false
                smartDnsBackRequestKey = 0
            }
            currentScreen = screen
        }
    }

    LaunchedEffect(currentScreen) {
        val target = if (currentScreen != AppScreen.Main) 1f else 0f
        if (transition.targetValue == target && transition.value == target) return@LaunchedEffect
        if (currentScreen != AppScreen.Main) {
            lastNonMainScreen = currentScreen
            transition.animateTo(1f, animationSpec = navigationPushSpec())
        } else {
            transition.animateTo(0f, animationSpec = navigationPopSpec())
            transition.snapTo(0f)
        }
    }

    var openToolsLogsRequest by rememberSaveable { mutableStateOf(0) }
    var openToolsUpdateSubscriptionRequest by rememberSaveable { mutableStateOf(0) }
    var resetToolsToRootRequest by rememberSaveable { mutableStateOf(0) }
    var logsFromHome by rememberSaveable { mutableStateOf(false) }
    var updateSubscriptionFromHome by rememberSaveable { mutableStateOf(false) }
    var toolsSavedRouteName by rememberSaveable { mutableStateOf<String?>(null) }

    val pagerState = rememberPagerState(initialPage = currentMainTabIndex, pageCount = { 3 })
    val mainPagerState = rememberMainPagerState(pagerState, scope)

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                currentMainTabIndex = page
                pagerUserScrollEnabled = true
                mainTabAtRoot = true
                mainPagerState.syncPage()
            }
    }

    LaunchedEffect(pagerState, mainPagerState) {
        snapshotFlow {
            Triple(
                pagerState.currentPage,
                pagerState.currentPageOffsetFraction,
                pagerState.isScrollInProgress
            )
        }
            .map { (page, offset, inProgress) ->
                if (!inProgress || mainPagerState.isNavigating) {
                    page
                } else {
                    when {
                        offset > 0.5f -> page + 1
                        offset < -0.5f -> page - 1
                        else -> page
                    }
                }
            }
            .map { it.coerceIn(0, pagerState.pageCount - 1) }
            .distinctUntilChanged()
            .collect { page ->
                mainPagerState.onUserScrollSelectedPage(page)
            }
    }

    val density = LocalDensity.current
    var navBarMeasuredHeightPx by remember { mutableIntStateOf(0) }

    val tabStateHolder = rememberSaveableStateHolder()
    var lastBackAt by rememberSaveable { mutableStateOf(0L) }

    BackHandler {
        if (currentScreen != AppScreen.Main) {
            currentScreen = AppScreen.Main
            return@BackHandler
        }
        
        if (mainPagerState.selectedPage != MainTab.Home.index()) {
            mainPagerState.animateToPage(MainTab.Home.index())
            return@BackHandler
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastBackAt < 2000L) {
            (context as? Activity)?.finish()
        } else {
            lastBackAt = now
            Toast.makeText(context, context.getString(R.string.app_back_again_to_exit), Toast.LENGTH_SHORT).show()
        }
    }

    var lastSelectedIndex by remember { mutableIntStateOf(mainPagerState.selectedPage) }
    LaunchedEffect(mainPagerState.selectedPage) {
        lastSelectedIndex = mainPagerState.selectedPage
        if (currentScreen == AppScreen.Main) {
            navVisible = true
        }
        if (mainPagerState.selectedPage != MainTab.Tools.index()) {
            logsFromHome = false
            updateSubscriptionFromHome = false
        }
    }

    LaunchedEffect(currentScreen) {
        if (currentScreen == AppScreen.Main) {
            navVisible = true
        }
    }

    LaunchedEffect(currentScreen, mainTabAtRoot) {
        if (currentScreen == AppScreen.Main && mainTabAtRoot) {
            navVisible = true
        }
    }

    val systemBarSettings by ThemeManager.systemBarSettings.collectAsState()
    val systemNavInsetDp = if (systemBarSettings.navigationBar == SystemBarMode.TRANSPARENT) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        0.dp
    }

    val mainFloatingNavSpaceDp = 64.dp
    val mainSystemNavInsetDp = if (systemBarSettings.navigationBar == SystemBarMode.TRANSPARENT) systemNavInsetDp else 0.dp

    // ── OOBE → 主界面入场动画 ──
    // needsEnterAnim：OOBE 完成瞬间设为 true，触发一次入场动画后复位
    var needsEnterAnim by remember { mutableStateOf(false) }
    // progress 0→1 驱动 alpha 和 scale
    val mainEnterProgress = remember { Animatable(1f) }

    // OOBE 完成检测 + DefaultActivity 启动
    if (showOnboarding) {
        LaunchedEffect(Unit) {
            // 将 progress 预设为 0（动画起点），保证主内容首帧不可见
            mainEnterProgress.snapTo(0f)
            val intent = android.content.Intent(context, DefaultActivity::class.java)
            context.startActivity(intent)
        }
        DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    val done = appPrefs.getBoolean("onboarding_completed", false)
                    if (done) {
                        onboardingCompleted = true
                        needsEnterAnim = true
                        showOnboarding = false
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    // OOBE 刚完成 → 播放入场动画（放大渐入，与完成页缩小渐出形成 zoom-through）
    LaunchedEffect(needsEnterAnim) {
        if (needsEnterAnim) {
            // 确保起点为 0（防止 recomposition 时 progress 已被污染）
            mainEnterProgress.snapTo(0f)
            mainEnterProgress.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
            needsEnterAnim = false
        }
    }

    // 单一 progress 驱动 alpha 和 scale，减少 graphicsLayer 开销
    val enterP = mainEnterProgress.value.coerceIn(0f, 1f)
    val mainAlpha = enterP
    val mainScale = 0.92f + 0.08f * enterP

    Box(modifier = Modifier.fillMaxSize()) {
        // OOBE 进行中：主内容不渲染（DefaultActivity 在最上层覆盖）
        if (showOnboarding) return@Box

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = mainAlpha
                    scaleX = mainScale
                    scaleY = mainScale
                }
                .onSizeChanged {
                    appScreenContainerWidthPx = it.width.toFloat()
                }
        ) {
            CompositionLocalProvider(
                LocalLiquidBackdrop provides uiBackdrop,
                LocalSheetBackdrop provides contentBackdrop,
                LocalSheetBlurState provides sheetBlurState,
                LocalNavigationBarsPaddingEnabled provides (systemBarSettings.navigationBar == SystemBarMode.TRANSPARENT)
            ) {
                val w = appScreenContainerWidthPx
                val t = transition.value
                val easedT = navigationSceneProgress(t)
                val mainOffsetX = if (w > 0f) (-w * 0.18f) * easedT else 0f
                val mainScale = 1f - 0.05f * easedT

                // BottomSheet 模糊半径（spring 物理动画，更丝滑）
                val sheetBlurActive = sheetBlurState.isActive && navBlurSupported
                val sheetBlurRadius by animateFloatAsState(
                    targetValue = if (sheetBlurActive) 20f else 0f,
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 200f),
                    label = "sheet_blur"
                )

                // 导航 + Sheet 模糊叠加：取较大值
                val navBlurRadius = (40f * easedT).coerceAtMost(40f)
                val combinedBlurRadius = maxOf(navBlurRadius, sheetBlurRadius)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = mainOffsetX
                            scaleX = mainScale
                            scaleY = mainScale
                        }
                        .androidRenderBlur(
                            radius = combinedBlurRadius,
                            enabled = navBlurSupported && combinedBlurRadius > 0.1f
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            
                            .layerBackdrop(uiBackdrop)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            
                            .layerBackdrop(contentBackdrop)
                    ) {
                        CompositionLocalProvider(
                            LocalFloatingNavBarSpaceDp provides mainFloatingNavSpaceDp,
                            LocalSystemNavBarInsetDp provides mainSystemNavInsetDp
                        ) {
                            HorizontalPager(
                                state = mainPagerState.pagerState,
                                beyondViewportPageCount = 0,
                                userScrollEnabled = pagerUserScrollEnabled
                            ) { page ->
                                when (page) {
                                    MainTab.Home.index() -> {
                                        tabStateHolder.SaveableStateProvider(MainTab.Home.name) {
                                            HomeScreen(
                                                onNavVisibilityChange = { navVisible = it },
                                                onOpenLogs = { openSubpage(AppScreen.ToolsLogs) },
                                                onOpenUpdateSubscription = { openSubpage(AppScreen.ToolsUpdateSubscription) },
                                                onOpenPanel = { openSubpage(AppScreen.Panel) },
                                                onOpenSubStore = { openSubpage(AppScreen.SubStore) },
                                                onOpenNetSpeed = { openSubpage(AppScreen.NetSpeed) },
                                                onOpenSmartDns = { openSubpage(AppScreen.SmartDnsWebUi) },
                                                onOpenSubscriptionDetail = { openSubpage(AppScreen.SubscriptionDetail) }
                                            )
                                        }
                                    }

                                    MainTab.Tools.index() -> {
                                        tabStateHolder.SaveableStateProvider(MainTab.Tools.name) {
                                            ToolsScreen(
                                                onNavVisibilityChange = { navVisible = it },
                                                onMainPagerUserScrollEnabledChange = {
                                                    if (mainPagerState.selectedPage == MainTab.Tools.index()) {
                                                        pagerUserScrollEnabled = it
                                                    }
                                                },
                                                onMainTabAtRootChange = {
                                                    if (mainPagerState.selectedPage == MainTab.Tools.index()) {
                                                        mainTabAtRoot = it
                                                    }
                                                },
                                                isActive = mainPagerState.selectedPage == MainTab.Tools.index(),
                                                savedRouteName = toolsSavedRouteName,
                                                onRouteSaved = { toolsSavedRouteName = it },
                                                resetToRootRequest = resetToolsToRootRequest,
                                                onResetToRootRequestConsumed = { resetToolsToRootRequest = 0 },
                                                openLogsRequest = openToolsLogsRequest,
                                                onOpenLogsRequestConsumed = { openToolsLogsRequest = 0 },
                                                openUpdateSubscriptionRequest = openToolsUpdateSubscriptionRequest,
                                                onOpenUpdateSubscriptionRequestConsumed = { openToolsUpdateSubscriptionRequest = 0 },
                                                openLogsFromHome = logsFromHome,
                                                onExitLogsToHome = {
                                                    logsFromHome = false
                                                    openToolsLogsRequest = 0
                                                    resetToolsToRootRequest += 1
                                                    mainPagerState.animateToPage(MainTab.Home.index())
                                                },
                                                openUpdateSubscriptionFromHome = updateSubscriptionFromHome,
                                                onExitUpdateSubscriptionToHome = {
                                                    updateSubscriptionFromHome = false
                                                    openToolsUpdateSubscriptionRequest = 0
                                                    resetToolsToRootRequest += 1
                                                    mainPagerState.animateToPage(MainTab.Home.index())
                                                },
                                                onOpenSmartDnsWebUi = { openSubpage(AppScreen.SmartDnsWebUi) }
                                            )
                                        }
                                    }

                                    else -> {
                                        tabStateHolder.SaveableStateProvider(MainTab.Settings.name) {
                                            SettingsScreen(
                                                onNavVisibilityChange = { navVisible = it },
                                                onMainPagerUserScrollEnabledChange = {
                                                    if (mainPagerState.selectedPage == MainTab.Settings.index()) {
                                                        pagerUserScrollEnabled = it
                                                    }
                                                },
                                                onMainTabAtRootChange = {
                                                    if (mainPagerState.selectedPage == MainTab.Settings.index()) {
                                                        mainTabAtRoot = it
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

            }

            // ── HyperX NavDisplay 导航 ──
            if (useHyperXNav) {
                val hyperXBackStack = rememberSaveable(
                    saver = listSaver<MutableList<AppScreen>, AppScreen>(
                        save = { it.toList() },
                        restore = { it.toMutableStateList() }
                    )
                ) { mutableStateListOf(AppScreen.Main) }

                // 同步 currentScreen → backStack
                LaunchedEffect(currentScreen) {
                    if (currentScreen == AppScreen.Main) {
                        while (hyperXBackStack.size > 1) hyperXBackStack.removeLastOrNull()
                    } else if (hyperXBackStack.lastOrNull() != currentScreen) {
                        hyperXBackStack.add(currentScreen)
                    }
                }

                val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current

                NavDisplay(
                    backStack = hyperXBackStack,
                    onBack = {
                        if (hyperXBackStack.size > 1) {
                            hyperXBackStack.removeLastOrNull()
                            exitSubpage()
                        }
                    },
                    transitionSpec = HyperXNavTransitions.normalTransitionSpec(layoutDirection),
                    popTransitionSpec = HyperXNavTransitions.normalPopTransitionSpec(layoutDirection),
                    predictivePopTransitionSpec = HyperXNavTransitions.normalPredictivePopTransitionSpec(layoutDirection),
                    transitionEffects = HyperXNavTransitions.NormalTransitionEffects,
                    entryProvider = { key ->
                        @Suppress("UNCHECKED_CAST")
                        val screen = key as? AppScreen ?: AppScreen.Main
                        NavEntry(key) {
                            if (screen == AppScreen.Main) return@NavEntry
                            val subpageSystemNavInsetDp = if (
                                systemBarSettings.navigationBar == SystemBarMode.TRANSPARENT &&
                                screen != AppScreen.Panel &&
                                screen != AppScreen.SubStore &&
                                screen != AppScreen.SmartDnsWebUi
                            ) systemNavInsetDp else 0.dp

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MiuixTheme.colorScheme.surface)
                            ) {
                                CompositionLocalProvider(
                                    LocalFloatingNavBarSpaceDp provides 0.dp,
                                    LocalSystemNavBarInsetDp provides subpageSystemNavInsetDp
                                ) {
                                    SubpageContent(
                                        screen = screen,
                                        onBack = { exitSubpage(); if (hyperXBackStack.size > 1) hyperXBackStack.removeLastOrNull() },
                                        panelBackRequestKey = panelBackRequestKey,
                                        onPanelCanGoBackChange = { panelWebCanGoBack = it },
                                        subStoreBackRequestKey = subStoreBackRequestKey,
                                        onSubStoreCanGoBackChange = { subStoreWebCanGoBack = it },
                                        smartDnsBackRequestKey = smartDnsBackRequestKey,
                                        onSmartDnsCanGoBackChange = { smartDnsWebCanGoBack = it },
                                        onNavVisibilityChange = { navVisible = it },
                                        onOpenToolsSubscription = {
                                            exitSubpage()
                                            if (hyperXBackStack.size > 1) hyperXBackStack.removeLastOrNull()
                                            scope.launch {
                                                kotlinx.coroutines.delay(100)
                                                openSubpage(AppScreen.ToolsUpdateSubscription)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }

            // ── 默认 Animatable 导航（HyperX 关闭时） ──
            if (!useHyperXNav) {
            val activeSubpage = when {
                currentScreen != AppScreen.Main -> currentScreen
                transition.value > 0f -> lastNonMainScreen
                else -> null
            }

            if (activeSubpage != null && (transition.value > 0f || currentScreen != AppScreen.Main)) {
                val w = appScreenContainerWidthPx
                val t = transition.value
                val easedT = navigationSceneProgress(t)
                val subX = if (w > 0f) w * (1f - easedT) else 0f

                val subpageSystemNavInsetDp = if (
                    systemBarSettings.navigationBar == SystemBarMode.TRANSPARENT &&
                    activeSubpage != AppScreen.Panel &&
                    activeSubpage != AppScreen.SubStore &&
                    activeSubpage != AppScreen.SmartDnsWebUi
                ) {
                    systemNavInsetDp
                } else {
                    0.dp
                }

                if (currentScreen != AppScreen.Main) {
                    val shouldHandleWebBack =
                        (currentScreen == AppScreen.Panel && panelWebCanGoBack) ||
                            (currentScreen == AppScreen.SubStore && subStoreWebCanGoBack) ||
                            (currentScreen == AppScreen.SmartDnsWebUi && smartDnsWebCanGoBack)

                    BackHandler {
                        when {
                            currentScreen == AppScreen.Panel && panelWebCanGoBack -> panelBackRequestKey += 1
                            currentScreen == AppScreen.SubStore && subStoreWebCanGoBack -> subStoreBackRequestKey += 1
                            currentScreen == AppScreen.SmartDnsWebUi && smartDnsWebCanGoBack -> smartDnsBackRequestKey += 1
                            else -> exitSubpage()
                        }
                    }

                    PredictiveBackHandler(enabled = w > 0f && shouldHandleWebBack) { progress ->
                        try {
                            progress.collect { }
                            when {
                                currentScreen == AppScreen.Panel && panelWebCanGoBack -> panelBackRequestKey += 1
                                currentScreen == AppScreen.SubStore && subStoreWebCanGoBack -> subStoreBackRequestKey += 1
                                currentScreen == AppScreen.SmartDnsWebUi && smartDnsWebCanGoBack -> smartDnsBackRequestKey += 1
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        }
                    }

                    PredictiveBackHandler(enabled = w > 0f && !shouldHandleWebBack) { progress ->
                        try {
                            progress.collect { backEvent ->
                                transition.snapTo(navigationPredictiveBackProgress(backEvent.progress))
                            }
                            exitSubpage()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            scope.launch {
                                transition.animateTo(1f, animationSpec = navigationCancelSpec())
                            }
                            throw e
                        }
                    }
                }

                // 模糊遮罩（使用 easedT 实现丝滑渐变）
                if (navBlurSupported && t > 0.02f) {
                    val dimColor = if (isDarkTheme) Color.Black.copy(alpha = 0.35f * easedT)
                        else Color(0xFF606060).copy(alpha = 0.12f * easedT)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(dimColor)
                    )
                } else if (t > 0.01f) {
                    val fallbackAlpha = if (isDarkTheme) 0.35f * easedT else 0.18f * easedT
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = fallbackAlpha }
                            .background(if (isDarkTheme) Color.Black else Color.Gray)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = subX }
                        .background(MiuixTheme.colorScheme.surface)
                        
                ) {
                    CompositionLocalProvider(
                        LocalFloatingNavBarSpaceDp provides 0.dp,
                        LocalSystemNavBarInsetDp provides subpageSystemNavInsetDp
                    ) {
                        when (activeSubpage) {
                            AppScreen.Panel -> PanelScreen(
                                onNavigateBack = { exitSubpage() },
                                backRequestKey = panelBackRequestKey,
                                onCanGoBackChange = { panelWebCanGoBack = it }
                            )
                            AppScreen.SubStore -> SubStoreScreen(
                                onNavigateBack = { exitSubpage() },
                                backRequestKey = subStoreBackRequestKey,
                                onCanGoBackChange = { subStoreWebCanGoBack = it }
                            )
                            AppScreen.SmartDnsWebUi -> SmartDnsWebUiScreen(
                                onNavigateBack = { exitSubpage() },
                                backRequestKey = smartDnsBackRequestKey,
                                onCanGoBackChange = { smartDnsWebCanGoBack = it }
                            )
                            AppScreen.ToolsLogs -> ToolsLogsScreen(
                                onNavVisibilityChange = { navVisible = it },
                                onBack = { exitSubpage() }
                            )
                            AppScreen.ToolsUpdateSubscription -> ToolsUpdateSubscriptionScreen(
                                onNavVisibilityChange = { navVisible = it },
                                onBack = { exitSubpage() }
                            )
                            AppScreen.NetSpeed -> com.box.app.ui.screens.tools.NetSpeedScreen(
                                onBack = { exitSubpage() }
                            )
                            AppScreen.SubscriptionDetail -> com.box.app.ui.screens.SubscriptionDetailScreen(
                                onBack = { exitSubpage() },
                                onOpenToolsSubscription = {
                                    exitSubpage()
                                    scope.launch {
                                        kotlinx.coroutines.delay(100)
                                        openSubpage(AppScreen.ToolsUpdateSubscription)
                                    }
                                }
                            )
                            else -> Unit
                        }
                    }
                }
            }
        }
        } // if (!useHyperXNav)

        val navBarsInsetPx = WindowInsets.navigationBars.getBottom(density).toFloat()
        val extraHidePx = with(density) { 24.dp.toPx() }
        val navHiddenOffsetPx = remember(navBarMeasuredHeightPx, navBarsInsetPx, extraHidePx) {
            val basePx = if (navBarMeasuredHeightPx > 0) navBarMeasuredHeightPx.toFloat() else with(density) { 120.dp.toPx() }
            basePx + navBarsInsetPx + extraHidePx
        }

        val navOffsetYPx by animateFloatAsState(
            targetValue = if (navVisible && currentScreen == AppScreen.Main && mainTabAtRoot) 0f else navHiddenOffsetPx,
            animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
            label = "nav_offset_y"
        )

        // 底栏：液态玻璃 vs 标准
        val useLiquidGlassNav by ThemeManager.liquidGlassNavBar.collectAsState()

        androidx.compose.animation.AnimatedContent(
            targetState = useLiquidGlassNav,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { navBarMeasuredHeightPx = it.height }
                .graphicsLayer { translationY = navOffsetYPx },
            transitionSpec = {
                (androidx.compose.animation.fadeIn(
                    animationSpec = tween(420, easing = FastOutSlowInEasing)
                ) + androidx.compose.animation.scaleIn(
                    initialScale = 0.94f,
                    animationSpec = tween(420, easing = FastOutSlowInEasing)
                )).togetherWith(
                    androidx.compose.animation.fadeOut(
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) + androidx.compose.animation.scaleOut(
                        targetScale = 0.94f,
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    )
                ).using(
                    androidx.compose.animation.SizeTransform(clip = false)
                )
            },
            label = "nav_bar_switch"
        ) { liquidGlass ->
            if (liquidGlass) {
                FloatingPillNavBar(
                    mainPagerState = mainPagerState,
                    backdrop = contentBackdrop,
                    modifier = Modifier
                        .padding(bottom = systemNavInsetDp)
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                )
            } else {
                Box(modifier = Modifier.fillMaxWidth()) {
                    MiuixNavigationBar(
                        defaultWindowInsetsPadding = true
                    ) {
                        MiuixNavigationBarItem(
                            selected = mainPagerState.selectedPage == 0,
                            onClick = { mainPagerState.animateToPage(0) },
                            icon = MiuixIcons.Sidebar,
                            label = context.getString(R.string.main_tab_home)
                        )
                        MiuixNavigationBarItem(
                            selected = mainPagerState.selectedPage == 1,
                            onClick = { mainPagerState.animateToPage(1) },
                            icon = MiuixIcons.All,
                            label = context.getString(R.string.main_tab_tools)
                        )
                        MiuixNavigationBarItem(
                            selected = mainPagerState.selectedPage == 2,
                            onClick = { mainPagerState.animateToPage(2) },
                            icon = MiuixIcons.Settings,
                            label = context.getString(R.string.main_tab_settings)
                        )
                    }
                }
            }
        }
    }
}

/** 子页面内容渲染（NavDisplay 和 Animatable 两种路径共用） */
@Composable
private fun SubpageContent(
    screen: AppScreen,
    onBack: () -> Unit,
    panelBackRequestKey: Int,
    onPanelCanGoBackChange: (Boolean) -> Unit,
    subStoreBackRequestKey: Int,
    onSubStoreCanGoBackChange: (Boolean) -> Unit,
    smartDnsBackRequestKey: Int,
    onSmartDnsCanGoBackChange: (Boolean) -> Unit,
    onNavVisibilityChange: (Boolean) -> Unit,
    onOpenToolsSubscription: () -> Unit
) {
    when (screen) {
        AppScreen.Panel -> PanelScreen(
            onNavigateBack = onBack,
            backRequestKey = panelBackRequestKey,
            onCanGoBackChange = onPanelCanGoBackChange
        )
        AppScreen.SubStore -> SubStoreScreen(
            onNavigateBack = onBack,
            backRequestKey = subStoreBackRequestKey,
            onCanGoBackChange = onSubStoreCanGoBackChange
        )
        AppScreen.SmartDnsWebUi -> SmartDnsWebUiScreen(
            onNavigateBack = onBack,
            backRequestKey = smartDnsBackRequestKey,
            onCanGoBackChange = onSmartDnsCanGoBackChange
        )
        AppScreen.ToolsLogs -> ToolsLogsScreen(
            onNavVisibilityChange = onNavVisibilityChange,
            onBack = onBack
        )
        AppScreen.ToolsUpdateSubscription -> ToolsUpdateSubscriptionScreen(
            onNavVisibilityChange = onNavVisibilityChange,
            onBack = onBack
        )
        AppScreen.NetSpeed -> com.box.app.ui.screens.tools.NetSpeedScreen(
            onBack = onBack
        )
        AppScreen.SubscriptionDetail -> com.box.app.ui.screens.SubscriptionDetailScreen(
            onBack = onBack,
            onOpenToolsSubscription = onOpenToolsSubscription
        )
        else -> Unit
    }
}
