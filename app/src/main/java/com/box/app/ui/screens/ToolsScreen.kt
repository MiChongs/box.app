package com.box.app.ui.screens

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.box.app.BuildConfig
import com.box.app.ui.components.LocalFloatingNavBarSpaceDp
import com.box.app.ui.effect.navigationCancelSpec
import com.box.app.ui.effect.navigationPredictiveBackProgress
import com.box.app.ui.effect.navigationPopSpec
import com.box.app.ui.effect.navigationPushSpec
import com.box.app.ui.effect.navigationSceneProgress
import androidx.compose.ui.unit.dp
import com.box.app.ui.effect.androidRenderBlur
import com.box.app.ui.effect.supportsAndroidRenderBlur
import com.box.app.ui.screens.tools.ToolsRootScreen
import kotlinx.coroutines.launch
import com.box.app.ui.screens.tools.ToolsConfigScreen
import com.box.app.ui.screens.tools.ToolsAppsScreen
import com.box.app.ui.screens.tools.ToolsLogsScreen
import com.box.app.ui.screens.tools.ToolsNetworkControlScreen
import com.box.app.ui.screens.tools.ToolsUpdateSubscriptionScreen
import com.box.app.ui.screens.tools.ToolsUpdateCnipScreen
import com.box.app.ui.screens.tools.MonitorSettingsScreen
import com.box.app.ui.screens.tools.ConfigEditorScreen
import com.box.app.ui.screens.tools.SmartDnsConfigScreen
import com.box.app.ui.screens.tools.SmartDnsScreen

private enum class ToolsRoute {
    Root,
    ConfigManage,
    ConfigSelect,
    ConfigEditor,
    Apps,
    NetworkControl,
    Logs,
    UpdateSubscription,
    UpdateCnip,
    MonitorSettings,
    SmartDns,
    SmartDnsConfig;

    /** 是否为嵌套路由（需要在父路由之上以二级动画推入） */
    val isNested: Boolean get() = this == ConfigEditor || this == SmartDnsConfig
}

@Composable
fun ToolsScreen(
    onNavVisibilityChange: (Boolean) -> Unit,
    onMainPagerUserScrollEnabledChange: (Boolean) -> Unit = {},
    onMainTabAtRootChange: (Boolean) -> Unit = {},
    isActive: Boolean = true,
    savedRouteName: String? = null,
    onRouteSaved: (String) -> Unit = {},
    resetToRootRequest: Int = 0,
    onResetToRootRequestConsumed: () -> Unit = {},
    openLogsRequest: Int = 0,
    onOpenLogsRequestConsumed: () -> Unit = {},
    openUpdateSubscriptionRequest: Int = 0,
    onOpenUpdateSubscriptionRequestConsumed: () -> Unit = {},
    openLogsFromHome: Boolean = false,
    onExitLogsToHome: () -> Unit = {},
    openUpdateSubscriptionFromHome: Boolean = false,
    onExitUpdateSubscriptionToHome: () -> Unit = {},
    onOpenSmartDnsWebUi: () -> Unit = {}
) {
    var route by rememberSaveable {
        mutableStateOf(savedRouteName?.let { name ->
            runCatching { ToolsRoute.valueOf(name) }.getOrNull() ?: ToolsRoute.Root
        } ?: ToolsRoute.Root)
    }
    // SmartDNS 配置编辑器需要的文件路径参数
    var smartDnsConfigPath by rememberSaveable { mutableStateOf("") }
    // 通用配置编辑器需要的参数
    var configEditorPath by rememberSaveable { mutableStateOf("") }
    var configEditorReturnRoute by rememberSaveable { mutableStateOf(ToolsRoute.ConfigManage) }

    LaunchedEffect(resetToRootRequest) {
        if (resetToRootRequest <= 0) return@LaunchedEffect
        route = ToolsRoute.Root
        onResetToRootRequestConsumed()
    }

    LaunchedEffect(savedRouteName) {
        val restored = savedRouteName?.let { name ->
            runCatching { ToolsRoute.valueOf(name) }.getOrNull()
        }
        if (restored != null && restored != route) {
            route = restored
        }
    }

    LaunchedEffect(route) {
        onRouteSaved(route.name)
    }

    LaunchedEffect(isActive, route) {
        if (!isActive) {
            onMainPagerUserScrollEnabledChange(true)
            onMainTabAtRootChange(true)
            return@LaunchedEffect
        }

        val atRoot = route == ToolsRoute.Root
        onMainPagerUserScrollEnabledChange(atRoot)
        onMainTabAtRootChange(atRoot)
        if (!atRoot) {
            onNavVisibilityChange(false)
        }
    }

    val rootListState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }

    LaunchedEffect(openLogsRequest) {
        if (openLogsRequest <= 0) return@LaunchedEffect
        route = ToolsRoute.Logs
        onOpenLogsRequestConsumed()
    }

    LaunchedEffect(openUpdateSubscriptionRequest) {
        if (openUpdateSubscriptionRequest <= 0) return@LaunchedEffect
        route = ToolsRoute.UpdateSubscription
        onOpenUpdateSubscriptionRequestConsumed()
    }

    fun exitCurrentRoute() {
        when {
            route == ToolsRoute.ConfigEditor -> route = configEditorReturnRoute
            route == ToolsRoute.SmartDnsConfig -> route = ToolsRoute.SmartDns
            route == ToolsRoute.Logs && openLogsFromHome -> onExitLogsToHome()
            route == ToolsRoute.UpdateSubscription && openUpdateSubscriptionFromHome -> onExitUpdateSubscriptionToHome()
            else -> route = ToolsRoute.Root
        }
    }

    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

    // ── 一级动画：Root ↔ depth-1 ──
    val transition = remember { androidx.compose.animation.core.Animatable(0f) }
    var lastBaseRoute by rememberSaveable { mutableStateOf<ToolsRoute?>(null) }

    // ── 二级动画：depth-1 ↔ depth-2（嵌套路由） ──
    val nestedTransition = remember { androidx.compose.animation.core.Animatable(0f) }
    var nestedParentRoute by rememberSaveable { mutableStateOf<ToolsRoute?>(null) }
    var lastNestedRoute by rememberSaveable { mutableStateOf<ToolsRoute?>(null) }

    LaunchedEffect(route) {
        when {
            route == ToolsRoute.Root -> {
                // 返回根页面：先收起嵌套层，再收起主层
                if (nestedTransition.value > 0f) nestedTransition.snapTo(0f)
                nestedParentRoute = null
                transition.animateTo(0f, animationSpec = navigationPopSpec())
            }
            route.isNested -> {
                // 进入嵌套路由（ConfigEditor / SmartDnsConfig）
                lastNestedRoute = route
                // 记住父路由
                nestedParentRoute = when (route) {
                    ToolsRoute.ConfigEditor -> configEditorReturnRoute
                    ToolsRoute.SmartDnsConfig -> ToolsRoute.SmartDns
                    else -> lastBaseRoute
                }
                lastBaseRoute = nestedParentRoute
                // 确保一级动画已完成
                if (transition.value < 1f) transition.snapTo(1f)
                nestedTransition.animateTo(1f, animationSpec = navigationPushSpec())
            }
            else -> {
                // 进入普通 depth-1 路由
                lastBaseRoute = route
                if (nestedTransition.value > 0f) {
                    // 从嵌套路由返回父级
                    nestedTransition.animateTo(0f, animationSpec = navigationPopSpec())
                    nestedParentRoute = null
                }
                if (transition.value < 1f) {
                    transition.animateTo(1f, animationSpec = navigationPushSpec())
                }
            }
        }
    }

    if (isActive && route != ToolsRoute.Root) {
        PredictiveBackHandler {
                progress: kotlinx.coroutines.flow.Flow<androidx.activity.BackEventCompat> ->
            val isInNested = route.isNested
            val anim = if (isInNested) nestedTransition else transition
            try {
                progress.collect { backEvent ->
                    anim.snapTo(navigationPredictiveBackProgress(backEvent.progress))
                }
                exitCurrentRoute()
            } catch (e: kotlinx.coroutines.CancellationException) {
                scope.launch {
                    anim.animateTo(1f, animationSpec = navigationCancelSpec())
                }
                throw e
            }
        }
    }

    // 一级活跃路由（depth-1）
    val activeBaseRoute: ToolsRoute? = when {
        route != ToolsRoute.Root && !route.isNested -> route
        nestedParentRoute != null -> nestedParentRoute
        transition.value > 0f -> lastBaseRoute
        else -> null
    }
    // 二级活跃路由（depth-2，嵌套）
    val activeNestedRoute: ToolsRoute? = when {
        route.isNested -> route
        nestedTransition.value > 0f -> lastNestedRoute
        else -> null
    }

    val toolsBlurSupported = remember { supportsAndroidRenderBlur() }

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
                    enabled = toolsBlurSupported && t > 0.02f
                )
        ) {
            ToolsRootScreen(
                onNavVisibilityChange = onNavVisibilityChange,
                listState = rootListState,
                onOpenConfigManage = { route = ToolsRoute.ConfigManage },
                onOpenConfigSelect = { route = ToolsRoute.ConfigSelect },
                onOpenApps = { route = ToolsRoute.Apps },
                onOpenNetworkControl = { route = ToolsRoute.NetworkControl },
                onOpenLogs = { route = ToolsRoute.Logs },
                onOpenUpdateSubscription = { route = ToolsRoute.UpdateSubscription },
                onOpenUpdateCnip = {
                    if (BuildConfig.FLAVOR != "bfr") {
                        route = ToolsRoute.UpdateCnip
                    }
                },
                onOpenMonitorSettings = { route = ToolsRoute.MonitorSettings },
                onOpenSmartDns = { route = ToolsRoute.SmartDns }
            )
        }

        val hasBaseLayer = activeBaseRoute != null && (t > 0f || route != ToolsRoute.Root)

        if (hasBaseLayer) {
            // ── Root 上方的模糊遮罩 ──
            if (toolsBlurSupported && t > 0.02f) {
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

            // ── 一级子页面容器（从右滑入） ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = subX }
            ) {
                // ── 一级内容层（depth-1），嵌套路由激活时加模糊和位移 ──
                val nt = nestedTransition.value
                val nestedEasedT = navigationSceneProgress(nt)
                val baseOffsetX = if (w > 0f && nt > 0f) (-w * 0.18f) * nestedEasedT else 0f
                val baseScale = if (nt > 0f) 1f - 0.05f * nestedEasedT else 1f

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationX = baseOffsetX
                            scaleX = baseScale
                            scaleY = baseScale
                        }
                        .androidRenderBlur(
                            radius = (40f * nt).coerceAtMost(40f),
                            enabled = toolsBlurSupported && nt > 0.02f
                        )
                        .background(MiuixTheme.colorScheme.surface)
                ) {
                    CompositionLocalProvider(LocalFloatingNavBarSpaceDp provides 0.dp) {
                        when (activeBaseRoute) {
                            ToolsRoute.ConfigManage -> ToolsConfigScreen(
                                onNavVisibilityChange = onNavVisibilityChange,
                                initialTab = com.box.app.ui.screens.tools.ConfigHubTab.Manage,
                                onBack = { exitCurrentRoute() },
                                onOpenEditor = { path ->
                                    configEditorPath = path
                                    configEditorReturnRoute = ToolsRoute.ConfigManage
                                    route = ToolsRoute.ConfigEditor
                                },
                                enableBackHandler = false
                            )

                            ToolsRoute.ConfigSelect -> ToolsConfigScreen(
                                onNavVisibilityChange = onNavVisibilityChange,
                                initialTab = com.box.app.ui.screens.tools.ConfigHubTab.Select,
                                onBack = { exitCurrentRoute() },
                                onOpenEditor = { path ->
                                    configEditorPath = path
                                    configEditorReturnRoute = ToolsRoute.ConfigSelect
                                    route = ToolsRoute.ConfigEditor
                                },
                                enableBackHandler = false
                            )

                            ToolsRoute.Apps -> ToolsAppsScreen(
                                onNavVisibilityChange = onNavVisibilityChange,
                                onBack = { exitCurrentRoute() }
                            )

                            ToolsRoute.NetworkControl -> ToolsNetworkControlScreen(
                                onNavVisibilityChange = onNavVisibilityChange,
                                onBack = { exitCurrentRoute() }
                            )

                            ToolsRoute.Logs -> ToolsLogsScreen(
                                onNavVisibilityChange = onNavVisibilityChange,
                                onBack = { exitCurrentRoute() }
                            )

                            ToolsRoute.UpdateSubscription -> ToolsUpdateSubscriptionScreen(
                                onNavVisibilityChange = onNavVisibilityChange,
                                onBack = { exitCurrentRoute() }
                            )

                            ToolsRoute.UpdateCnip -> {
                                if (BuildConfig.FLAVOR == "bfr") {
                                    ToolsRootScreen(
                                        onNavVisibilityChange = onNavVisibilityChange,
                                        listState = rootListState,
                                        onOpenConfigManage = { route = ToolsRoute.ConfigManage },
                                        onOpenConfigSelect = { route = ToolsRoute.ConfigSelect },
                                        onOpenApps = { route = ToolsRoute.Apps },
                                        onOpenNetworkControl = { route = ToolsRoute.NetworkControl },
                                        onOpenLogs = { route = ToolsRoute.Logs },
                                        onOpenUpdateSubscription = { route = ToolsRoute.UpdateSubscription },
                                        onOpenUpdateCnip = { },
                                        onOpenMonitorSettings = { route = ToolsRoute.MonitorSettings },
                                        onOpenSmartDns = { route = ToolsRoute.SmartDns }
                                    )
                                } else {
                                    ToolsUpdateCnipScreen(
                                        onNavVisibilityChange = onNavVisibilityChange,
                                        onBack = { exitCurrentRoute() }
                                    )
                                }
                            }

                            ToolsRoute.MonitorSettings -> MonitorSettingsScreen(
                                onNavVisibilityChange = onNavVisibilityChange,
                                onBack = { exitCurrentRoute() }
                            )

                            ToolsRoute.SmartDns -> SmartDnsScreen(
                                onNavVisibilityChange = onNavVisibilityChange,
                                onBack = { exitCurrentRoute() },
                                onOpenConfigEditor = { path ->
                                    smartDnsConfigPath = path
                                    route = ToolsRoute.SmartDnsConfig
                                },
                                onOpenWebUi = onOpenSmartDnsWebUi
                            )

                            else -> Unit
                        }
                    }
                }

                // ── 嵌套层遮罩 ──
                if (nt > 0.02f) {
                    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
                    val nestedDimColor = if (isDark) Color.Black.copy(alpha = 0.35f * nt)
                        else Color(0xFF606060).copy(alpha = 0.12f * nt)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(nestedDimColor)
                    )
                }

                // ── 二级内容层（depth-2，嵌套路由从右滑入） ──
                if (activeNestedRoute != null && nt > 0f) {
                    val nestedSubX = if (w > 0f) w * (1f - nestedEasedT) else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationX = nestedSubX }
                            .background(MiuixTheme.colorScheme.surface)
                    ) {
                        CompositionLocalProvider(LocalFloatingNavBarSpaceDp provides 0.dp) {
                            when (activeNestedRoute) {
                                ToolsRoute.ConfigEditor -> ConfigEditorScreen(
                                    filePath = configEditorPath,
                                    onNavVisibilityChange = onNavVisibilityChange,
                                    onBack = { exitCurrentRoute() }
                                )

                                ToolsRoute.SmartDnsConfig -> SmartDnsConfigScreen(
                                    filePath = smartDnsConfigPath,
                                    onNavVisibilityChange = onNavVisibilityChange,
                                    onBack = { exitCurrentRoute() }
                                )

                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}
