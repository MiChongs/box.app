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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.lackluster.hyperx.ui.layout.HyperXLayoutConfig
import dev.lackluster.hyperx.ui.layout.HyperXScaffold
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
import androidx.compose.ui.draw.drawWithContent
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
import androidx.compose.ui.graphics.RectangleShape
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import dev.lackluster.hyperx.ui.effect.rememberBlurBackdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop as MiuixLayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop as miuixLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import com.box.app.ui.screens.HomeScreen
import com.box.app.ui.screens.ToolsScreen
import com.box.app.ui.screens.SettingsScreen
import com.box.app.ui.screens.PanelScreen
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
    ToolsLogs,
    ToolsUpdateSubscription,
    NetSpeed,
    SubscriptionDetail,
    BaseProxyConfig,
    LatencyTargets
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
    // miuix 风格 NavBar 模糊源（与 HyperXScaffold 同款）：在内容层用 miuix.layerBackdrop
    // 注册采样区，NavBar 通过 miuix.textureBlur 进行真正的 miuix blur 渲染
    val miuixNavBackdrop: MiuixLayerBackdrop? = key(backdropVersion) {
        rememberBlurBackdrop(MiuixTheme.colorScheme.surface)
    }
    val navBlurSupported = remember { supportsAndroidRenderBlur() }
    val isDarkTheme = ThemeManager.shouldUseDarkTheme()
    val useHyperXNav by ThemeManager.hyperXNavTransitions.collectAsState()
    val sheetBlurState = remember { SheetBlurState() }

    var appScreenContainerWidthPx by remember { mutableFloatStateOf(0f) }
    val transition = remember(openPanelOnLaunch) { Animatable(if (openPanelOnLaunch) 1f else 0f) }
    var lastNonMainScreen by rememberSaveable {
        mutableStateOf<AppScreen?>(if (openPanelOnLaunch) AppScreen.Panel else null)
    }


    fun exitSubpage() {
        currentScreen = AppScreen.Main
        panelWebCanGoBack = false
        subStoreWebCanGoBack = false
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
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
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

    // ── 应用根布局：项目版 AppHyperXLayout（对接 AppTheme，剥离了库版 HyperXTheme） ──
    // 通过 [AppHyperXLayout] 统一注入：
    //   - LocalHyperXLayoutConfig（跟随设置项的磨砂开关 → 解决"模糊内容不正确"）
    //   - LocalNavigator         （HyperXPage 默认返回按钮可调用）
    //   - LocalLayoutPadding     （HyperXPage 内部用于内容左右内边距）
    //   - LocalPageMode          （Normal/LargeScreen → FULL_SCREEN）
    //   - MiuixPopupUtils.MiuixPopupHost（库内弹层宿主）
    //
    // 关键：禁用 split-screen。AppScaffold 是含 HorizontalPager + 自管理子页面动画的
    // 完整应用骨架，被 split 模式劈成左右两半会破坏导航语义。
    val blurEffectsActiveForApp = ThemeManager.shouldUseBlurEffects()
    val hyperXLayoutConfig = remember(blurEffectsActiveForApp) {
        HyperXLayoutConfig(
            isBlurEnabled = blurEffectsActiveForApp,
            isSplitScreenEnabled = false
        )
    }

    AppHyperXLayout(config = hyperXLayoutConfig) {
    Box(modifier = Modifier.fillMaxSize()) {
        // OOBE 进行中：主内容不渲染（DefaultActivity 在最上层覆盖）
        if (showOnboarding) return@Box

        // ── 底部导航条：作为 outer Box 的 overlay 渲染（兄弟节点，不进 Scaffold.bottomBar）
        //   旧设计的根本错误：把 nav 塞进 HyperXScaffold.bottomBar slot → Scaffold 按 nav
        //   测量高度为 body 预留空间。AnimatedVisibility 即便把 nav 测量到 0，过渡帧仍
        //   会出现「内容尚未填满 / Scaffold 渲染顺序」造成的底部空白黑块。
        //   正确做法：HyperXScaffold 不带 bottomBar，body 始终 fillMaxSize；nav 用
        //   `Modifier.align(BottomCenter) + graphicsLayer { translationY = ... }` 单独
        //   悬浮在内容之上，hide 时仅做视觉位移，不收缩布局，因此底部永远是内容自身，
        //   彻底没有黑块。
        val useLiquidGlassNav by ThemeManager.liquidGlassNavBar.collectAsState()

        HyperXScaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { _ ->
        // ── WebView 兼容修复（subpage 内的 ThemedWebView 渲染异常） ──
        // 这个 Box 是「主内容 + subpage」的共同祖先；之前永久挂着 OOBE 入场动画的
        // graphicsLayer。即便 OOBE 完成（mainAlpha == 1f && mainScale == 1f，transform
        // 为 identity），graphicsLayer wrapper 仍把整个子树包成离屏 RenderNode，导致
        // subpage 内 AndroidView 嵌入的 WebView 渲染黑洞 / 不刷新。
        // 修复：仅在 OOBE 动画进行中挂 graphicsLayer，动画完成后退出 RenderNode 包裹路径。
        val needsOobeAnimLayer = mainAlpha < 0.999f || mainScale < 0.999f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .let {
                    if (needsOobeAnimLayer) {
                        it.graphicsLayer {
                            alpha = mainAlpha
                            scaleX = mainScale
                            scaleY = mainScale
                        }
                    } else {
                        it
                    }
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
                            .layerBackdrop(contentBackdrop)
                            .let { if (miuixNavBackdrop != null) it.miuixLayerBackdrop(miuixNavBackdrop) else it }
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
                                                onOpenSubscriptionDetail = { openSubpage(AppScreen.SubscriptionDetail) },
                                                onOpenBaseProxyConfig = { openSubpage(AppScreen.BaseProxyConfig) },
                                                onOpenLatencyTargets = { openSubpage(AppScreen.LatencyTargets) }
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
                            // ── pop 返回不闪白修复 ──
                            // 之前这里用 `return@NavEntry` 空内容。pop 转场需要"主页面"
                            // NavEntry 作为滑入目标，空 lambda 让 NavDisplay 的合成层失去
                            // 落点：subpage 被 dispose 的瞬间，NavDisplay 容器没有任何
                            // 子节点托底 → 露出 surface（亮色主题为白色） → "突然变白"。
                            // 修复：渲染一个**透明** fillMaxSize Box 作为 Main 占位，转场
                            // 期间露出背后的 HorizontalPager 主内容，subpage 平滑滑出。
                            // 真正的主内容仍由外层 HorizontalPager 在 NavDisplay 之外渲染。
                            if (screen == AppScreen.Main) {
                                Box(modifier = Modifier.fillMaxSize())
                                return@NavEntry
                            }
                            val subpageSystemNavInsetDp = if (
                                systemBarSettings.navigationBar == SystemBarMode.TRANSPARENT &&
                                screen != AppScreen.Panel &&
                                screen != AppScreen.SubStore &&
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
                ) {
                    systemNavInsetDp
                } else {
                    0.dp
                }

                if (currentScreen != AppScreen.Main) {
                    val shouldHandleWebBack =
                        (currentScreen == AppScreen.Panel && panelWebCanGoBack) ||
                            (currentScreen == AppScreen.SubStore && subStoreWebCanGoBack) ||

                    BackHandler {
                        when {
                            currentScreen == AppScreen.Panel && panelWebCanGoBack -> panelBackRequestKey += 1
                            currentScreen == AppScreen.SubStore && subStoreWebCanGoBack -> subStoreBackRequestKey += 1
                            else -> exitSubpage()
                        }
                    }

                    PredictiveBackHandler(enabled = w > 0f && shouldHandleWebBack) { progress ->
                        try {
                            progress.collect { }
                            when {
                                currentScreen == AppScreen.Panel && panelWebCanGoBack -> panelBackRequestKey += 1
                                currentScreen == AppScreen.SubStore && subStoreWebCanGoBack -> subStoreBackRequestKey += 1
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

                // ── WebView 兼容修复 ──
                // 之前用 `.graphicsLayer { translationX = subX }` 推动 subpage 滑入，
                // 即便完全展开 (subX == 0f) graphicsLayer 仍创建一层 RenderNode wrapper，
                // AndroidView 内嵌的 WebView 在硬件加速合成路径下被强制走离屏 RenderNode，
                //
                // 改为 Modifier.offset { IntOffset(...) }：位移发生在 layout 阶段，**不**创建
                // graphicsLayer / RenderNode wrapper，AndroidView 子节点不被强制离屏渲染，
                // WebView 渲染路径与无转场时完全一致；视觉等价于原 graphicsLayer translationX。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { androidx.compose.ui.unit.IntOffset(subX.roundToInt(), 0) }
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
                                onNavigateBack = { exitSubpage() },
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
                            AppScreen.BaseProxyConfig -> com.box.app.ui.screens.BaseProxyConfigScreen(
                                onBack = { exitSubpage() }
                            )
                            AppScreen.LatencyTargets -> com.box.app.ui.screens.LatencyTargetsScreen(
                                onBack = { exitSubpage() }
                            )
                            else -> Unit
                        }
                    }
                }
            }
        }
        } // if (!useHyperXNav)

        }  // close HyperXScaffold content lambda

        // ── 底部导航悬浮层（outer Box 的兄弟节点 / overlay 模式）
        //   - hide：仅 translationY 视觉位移，不影响布局 → 内容始终铺满到屏幕底，无黑块
        //   - 液态玻璃模式 → FloatingPillNavBar（自带 backdrop 模糊）
        //   - 普通模式 → MiuixNavigationBar 透明 + 当全局磨砂启用时套 textureBlur 玻璃层
        AppScaffoldBottomNavOverlay(
            mainPagerState = mainPagerState,
            contentBackdrop = contentBackdrop,
            miuixNavBackdrop = miuixNavBackdrop,
            systemNavInsetDp = systemNavInsetDp,
            density = density,
            navBarMeasuredHeightPx = navBarMeasuredHeightPx,
            onMeasured = { navBarMeasuredHeightPx = it },
            navVisible = navVisible,
            currentScreen = currentScreen,
            mainTabAtRoot = mainTabAtRoot,
            useLiquidGlassNav = useLiquidGlassNav,
            blurEffectsActive = blurEffectsActiveForApp,
            context = context,
            boxScope = this
        )
    }
    } // close AppHyperXLayout primaryContent
}

/**
 * 底部导航悬浮层（overlay 模式 — 设计要点）
 *
 * 这是**重新设计**的底部导航；它彻底解决了之前几版的"隐藏时出现底部黑块"问题。
 *
 * 旧设计（错误）：
 *   nav 放在 HyperXScaffold.bottomBar slot → Scaffold 用 nav 的测量高度为 body 预留
 *   bottom 空间。AnimatedVisibility 把 nav 测量收缩到 0 也无法完全规避：
 *     - 过渡帧间，body 与 bottomBar 之间出现尺寸不同步
 *     - HyperXScaffold 的 containerColor / 透明度在中间形成可见的黑色矩形
 *     - 取消 navOffsetYPx 视觉位移后，slide 动画与 layout 收缩不能完美同步
 *
 * 新设计（正确）：
 *   - HyperXScaffold 不带 bottomBar，body 始终 fillMaxSize → 内容**永远**铺满到屏幕底部
 *   - nav 作为 outer Box 的兄弟节点，用 `align(Alignment.BottomCenter)` 对齐底部
 *   - hide 用 `Modifier.graphicsLayer { translationY = navOffsetYPx }`：
 *       * 仅做视觉位移（绘制阶段），**不收缩布局**
 *       * 隐藏时 nav 滑出屏幕外 → 屏幕底部直接显出 body 内容，没有任何空隙
 *   - 切换液态玻璃 / 普通模式：AnimatedContent 形变动画
 *   - 普通模式下，blur 通过 `Modifier.textureBlur(contentBackdrop, ...)` 直接挂在
 *     navBar 容器上 — 这才是真正的「磨砂玻璃悬浮」结构（不再依赖 HyperXScaffold 的
 *     bottomBar slot 机制）
 */
@Composable
private fun AppScaffoldBottomNavOverlay(
    mainPagerState: MainPagerState,
    contentBackdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    miuixNavBackdrop: MiuixLayerBackdrop?,
    systemNavInsetDp: androidx.compose.ui.unit.Dp,
    density: androidx.compose.ui.unit.Density,
    navBarMeasuredHeightPx: Int,
    onMeasured: (Int) -> Unit,
    navVisible: Boolean,
    currentScreen: AppScreen,
    mainTabAtRoot: Boolean,
    useLiquidGlassNav: Boolean,
    blurEffectsActive: Boolean,
    context: android.content.Context,
    boxScope: androidx.compose.foundation.layout.BoxScope
) = with(boxScope) {
    // 隐藏目标：nav 完全滑出屏幕外（高度 + 系统底部 inset + 24dp 余量）
    val navBarsInsetPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    val extraHidePx = with(density) { 24.dp.toPx() }
    val navHiddenOffsetPx = remember(navBarMeasuredHeightPx, navBarsInsetPx, extraHidePx) {
        val basePx = if (navBarMeasuredHeightPx > 0) navBarMeasuredHeightPx.toFloat()
            else with(density) { 120.dp.toPx() }
        basePx + navBarsInsetPx + extraHidePx
    }
    val isVisible = navVisible && currentScreen == AppScreen.Main && mainTabAtRoot
    val navOffsetYPx by animateFloatAsState(
        targetValue = if (isVisible) 0f else navHiddenOffsetPx,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "nav_offset_y"
    )

    androidx.compose.animation.AnimatedContent(
        targetState = useLiquidGlassNav,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .onSizeChanged { onMeasured(it.height) }
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
            // 普通 MiuixNavigationBar 的两种模式：
            //
            // ① 开磨砂 (blurEffectsActive = true)：
            //    - MiuixNavigationBar.color = Color.Transparent（不能挡住模糊层）
            //    - 外层 Box 用 kyant 的 `Modifier.drawBackdrop(contentBackdrop, blur(...))`
            //      真实采样主内容区像素并模糊，同时 onDrawSurface 涂 surface@0.55 玻璃 tint
            //    - 这与 FloatingPillNavBar 共用同一 contentBackdrop，捕获内容是「主页面 +
            //      子页面 + HorizontalPager 内容」的真实像素，绝不会再"模糊内容不正确"
            //
            // ② 关磨砂 (blurEffectsActive = false)：
            //    - MiuixNavigationBar.color = MiuixTheme.colorScheme.surface（标准实色）
            //    - 外层 Box 不叠加背景，避免双重涂色
            val miuixSurface = MiuixTheme.colorScheme.surface
            // ── miuix 原生 blur 路径 ──
            // 与 HyperXScaffold 同款：当全局磨砂启用且 miuixNavBackdrop 可用时，NavBar 走
            // `Modifier.textureBlur(backdrop, RectangleShape, blurRadius, BlurColors)`，让
            // miuix 用其 RuntimeShader 实现真正的 HyperOS 风格毛玻璃 — 边缘自然消隐，无方框。
            // 关 blur 时退化为实色 surface 容器。
            val miuixBlurReady = blurEffectsActive && miuixNavBackdrop != null
            val barColor = if (miuixBlurReady) Color.Transparent else miuixSurface
            val barBlurRadiusPx = with(density) { 25.dp.toPx() }
            val blurTintAlpha = if (ThemeManager.shouldUseDarkTheme()) 0.7f else 0.8f
            val blurColors = remember(miuixSurface, blurTintAlpha) {
                BlurColors(
                    blendColors = listOf(BlendColorEntry(miuixSurface.copy(alpha = blurTintAlpha)))
                )
            }
            val containerModifier = if (miuixBlurReady) {
                Modifier
                    .fillMaxWidth()
                    .textureBlur(
                        backdrop = miuixNavBackdrop,
                        shape = RectangleShape,
                        blurRadius = barBlurRadiusPx,
                        colors = blurColors
                    )
            } else {
                Modifier.fillMaxWidth()
            }
            Box(modifier = containerModifier) {
                MiuixNavigationBar(
                    color = barColor,
                    showDivider = true,
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

/** 子页面内容渲染（NavDisplay 和 Animatable 两种路径共用） */
@Composable
private fun SubpageContent(
    screen: AppScreen,
    onBack: () -> Unit,
    panelBackRequestKey: Int,
    onPanelCanGoBackChange: (Boolean) -> Unit,
    subStoreBackRequestKey: Int,
    onSubStoreCanGoBackChange: (Boolean) -> Unit,
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
            onNavigateBack = onBack,
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
        AppScreen.BaseProxyConfig -> com.box.app.ui.screens.BaseProxyConfigScreen(
            onBack = onBack
        )
        AppScreen.LatencyTargets -> com.box.app.ui.screens.LatencyTargetsScreen(
            onBack = onBack
        )
        else -> Unit
    }
}
