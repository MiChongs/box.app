package com.box.app.ui.components

import android.app.Activity
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.box.app.ui.screens.HomeScreen
import com.box.app.ui.screens.ToolsScreen
import com.box.app.ui.screens.SettingsScreen
import com.box.app.ui.screens.PanelScreen
import com.box.app.ui.screens.SubStoreScreen
import com.box.app.ui.screens.tools.ToolsLogsScreen
import com.box.app.ui.screens.tools.ToolsUpdateSubscriptionScreen
import com.box.app.ui.screens.OnboardingScreen
import com.box.app.R
import com.box.app.ui.components.LocalLiquidBackdrop
import com.box.app.ui.components.bottomsheets.LocalSheetBackdrop
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

import com.box.app.ui.components.LocalFloatingNavBarSpaceDp
import com.box.app.ui.components.LocalNavigationBarsPaddingEnabled
import com.box.app.ui.components.LocalSystemNavBarInsetDp
import com.box.app.utils.SystemBarMode
import com.box.app.utils.ThemeManager

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
        val duration = 120 * distance + 120

        navJob = coroutineScope.launch {
            try {
                pagerState.animateScrollToPage(
                    page = boundedTarget,
                    pageOffsetFraction = 0f,
                    animationSpec = tween(
                        durationMillis = duration,
                        easing = EaseInOut
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

enum class AppScreen {
    Main,
    Panel,
    SubStore,
    ToolsLogs,
    ToolsUpdateSubscription
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
    var isInEditorMode by remember { mutableStateOf(false) }

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
            currentScreen = screen
        }
    }

    LaunchedEffect(currentScreen) {
        val target = if (currentScreen != AppScreen.Main) 1f else 0f
        if (transition.targetValue == target && transition.value == target) return@LaunchedEffect
        if (currentScreen != AppScreen.Main) {
            lastNonMainScreen = currentScreen
            transition.animateTo(1f, animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing))
        } else {
            transition.animateTo(0f, animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing))
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

    Box(modifier = Modifier.fillMaxSize()) {
        if (showOnboarding) {
            OnboardingScreen(
                onFinish = {
                    appPrefs.edit().putBoolean("onboarding_completed", true).apply()
                    onboardingCompleted = true
                    showOnboarding = false
                }
            )
            return@Box
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged {
                    appScreenContainerWidthPx = it.width.toFloat()
                }
        ) {
            CompositionLocalProvider(
                LocalLiquidBackdrop provides uiBackdrop,
                LocalSheetBackdrop provides contentBackdrop,
                LocalNavigationBarsPaddingEnabled provides (systemBarSettings.navigationBar == SystemBarMode.TRANSPARENT)
            ) {
                val w = appScreenContainerWidthPx
                val t = transition.value
                val mainX = if (w > 0f) (-w / 3f) * t else 0f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = mainX }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .layerBackdrop(uiBackdrop)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
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
                                                onOpenSubStore = { openSubpage(AppScreen.SubStore) }
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
                                                onEditorModeChange = { isInEditorMode = it },
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
                                                }
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

            val activeSubpage = when {
                currentScreen != AppScreen.Main -> currentScreen
                transition.value > 0f -> lastNonMainScreen
                else -> null
            }

            if (activeSubpage != null && (transition.value > 0f || currentScreen != AppScreen.Main)) {
                val w = appScreenContainerWidthPx
                val t = transition.value
                val subX = if (w > 0f) w * (1f - t) else 0f

                val subpageSystemNavInsetDp = if (
                    systemBarSettings.navigationBar == SystemBarMode.TRANSPARENT &&
                    activeSubpage != AppScreen.Panel &&
                    activeSubpage != AppScreen.SubStore
                ) {
                    systemNavInsetDp
                } else {
                    0.dp
                }

                if (currentScreen != AppScreen.Main) {
                    val shouldHandleWebBack =
                        (currentScreen == AppScreen.Panel && panelWebCanGoBack) ||
                            (currentScreen == AppScreen.SubStore && subStoreWebCanGoBack)

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
                                transition.snapTo((1f - backEvent.progress).coerceIn(0f, 1f))
                            }
                            exitSubpage()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            scope.launch {
                                transition.animateTo(1f, animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing))
                            }
                            throw e
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = subX
                            alpha = t
                        }
                        .background(MaterialTheme.colorScheme.background)
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
                            AppScreen.ToolsLogs -> ToolsLogsScreen(
                                onNavVisibilityChange = { navVisible = it },
                                onBack = { exitSubpage() }
                            )
                            AppScreen.ToolsUpdateSubscription -> ToolsUpdateSubscriptionScreen(
                                onNavVisibilityChange = { navVisible = it },
                                onBack = { exitSubpage() }
                            )
                            else -> Unit
                        }
                    }
                }
            }
        }

        val navBarsInsetPx = WindowInsets.navigationBars.getBottom(density).toFloat()
        val extraHidePx = with(density) { 24.dp.toPx() }
        val navHiddenOffsetPx = remember(navBarMeasuredHeightPx, navBarsInsetPx, extraHidePx) {
            val basePx = if (navBarMeasuredHeightPx > 0) navBarMeasuredHeightPx.toFloat() else with(density) { 120.dp.toPx() }
            basePx + navBarsInsetPx + extraHidePx
        }

        val navOffsetYPx by animateFloatAsState(
            targetValue = if (navVisible && currentScreen == AppScreen.Main && mainTabAtRoot) 0f else navHiddenOffsetPx,
            animationSpec = tween(durationMillis = 220),
            label = "nav_offset_y"
        )

        FloatingPillNavBar(
            mainPagerState = mainPagerState,
            backdrop = contentBackdrop,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = systemNavInsetDp)
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .onSizeChanged { navBarMeasuredHeightPx = it.height }
                .graphicsLayer { translationY = navOffsetYPx }
        )
    }
}
