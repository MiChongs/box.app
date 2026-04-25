package com.box.app.ui.screens.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.box.app.ui.components.contentPaddingWithNavBars
import com.box.app.ui.miuix.HyperTextField
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup

enum class ConfigHubTab { Manage, Select }

@Composable
fun ToolsConfigScreen(
    onNavVisibilityChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    initialTab: ConfigHubTab,
    onOpenEditor: (String) -> Unit = {},
    enableBackHandler: Boolean = true
) {
    ConfigHubScreen(
        onNavVisibilityChange = onNavVisibilityChange,
        onBack = onBack,
        initialTab = initialTab,
        onOpenEditor = onOpenEditor,
        enableBackHandler = enableBackHandler
    )
}

@Composable
private fun ConfigHubScreen(
    onNavVisibilityChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    initialTab: ConfigHubTab,
    onOpenEditor: (String) -> Unit = {},
    enableBackHandler: Boolean = true
) {
    val stateHolder = rememberSaveableStateHolder()
    var tab by rememberSaveable { mutableStateOf(initialTab) }

    // Manage 模式状态
    var manageAction by rememberSaveable { mutableStateOf<ManageAction?>(null) }
    var manageSearchVisible by rememberSaveable { mutableStateOf(false) }
    var manageQuery by rememberSaveable { mutableStateOf("") }
    var manageCurrentPath by rememberSaveable { mutableStateOf("") }
    var manageOverflowExpanded by rememberSaveable { mutableStateOf(false) }

    // Select 模式状态
    var selectAction by rememberSaveable { mutableStateOf<SelectAction?>(null) }
    var selectCoreName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectActive by rememberSaveable { mutableStateOf<String?>(null) }
    var selectSelectedFile by rememberSaveable { mutableStateOf<String?>(null) }
    var selectActivateRequest by rememberSaveable { mutableStateOf<String?>(null) }
    var selectOverflowExpanded by rememberSaveable { mutableStateOf(false) }

    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    var swipeCloseSignal by rememberSaveable { mutableStateOf(0) }
    val scrollBehavior = MiuixScrollBehavior()

    // 搜索框焦点控制
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(manageSearchVisible) {
        if (manageSearchVisible) {
            delay(80)
            runCatching { focusRequester.requestFocus() }
            keyboardController?.show()
        } else {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    // 滚动隐藏导航栏 + 关闭滑动行
    LaunchedEffect(listState) {
        var last = listState.firstVisibleItemIndex * 10_000 + listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex * 10_000 + listState.firstVisibleItemScrollOffset }.distinctUntilChanged()
            .collect { now ->
                swipeCloseSignal += 1
                if (now > last) {
                    onNavVisibilityChange(false)
                } else if (now < last) {
                    onNavVisibilityChange(true)
                }
                last = now
            }
    }

    // 动态标题
    val title = when (tab) {
        ConfigHubTab.Manage -> {
            if (manageCurrentPath.isNotBlank()) {
                manageCurrentPath.substringAfterLast('/')
            } else {
                stringResource(R.string.tools_config_files_folders_title)
            }
        }

        ConfigHubTab.Select -> {
            stringResource(R.string.tools_config_configuration_files) + (selectCoreName?.let { " ($it)" }
                ?: "")
        }
    }

    val subtitle = when (tab) {
        ConfigHubTab.Manage -> {
            if (manageCurrentPath.isNotBlank()) "/data/adb/box/$manageCurrentPath" else "/data/adb/box"
        }

        ConfigHubTab.Select -> {
            selectActive?.let { stringResource(R.string.tools_config_active_configuration) + ": $it" }
                ?: ""
        }
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = title,
                subtitle = subtitle,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    when (tab) {
                        ConfigHubTab.Manage -> {
                            // 搜索按钮
                            IconButton(onClick = {
                                manageSearchVisible = !manageSearchVisible
                                if (!manageSearchVisible) {
                                    manageQuery = ""
                                }
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = if (manageSearchVisible) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
                                )
                            }
                            // 刷新按钮
                            IconButton(onClick = { manageAction = ManageAction.Refresh }) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onSurface
                                )
                            }
                            // 溢出菜单按钮 + Popup 锚定在同一 Box
                            Box {
                                IconButton(onClick = { manageOverflowExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.onSurface
                                    )
                                }
                                ConfigManageOverflowPopup(
                                    show = manageOverflowExpanded,
                                    onDismissRequest = { manageOverflowExpanded = false },
                                    onAction = { action ->
                                        manageOverflowExpanded = false
                                        manageAction = action
                                    })
                            }
                        }

                        ConfigHubTab.Select -> {
                            // 溢出菜单按钮 + Popup 锚定在同一 Box
                            Box {
                                IconButton(onClick = { selectOverflowExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Filled.MoreVert,
                                        contentDescription = null,
                                        tint = MiuixTheme.colorScheme.onSurface
                                    )
                                }
                                ConfigSelectOverflowPopup(
                                    show = selectOverflowExpanded,
                                    onDismissRequest = { selectOverflowExpanded = false },
                                    onAction = { action ->
                                        selectOverflowExpanded = false
                                        selectAction = action
                                    })
                            }
                        }
                    }
                })
        }) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Tab 切换栏
            TabRowWithContour(
                tabs = listOf(
                    stringResource(R.string.tools_config_tab_manage),
                    stringResource(R.string.tools_config_tab_select)
                ),
                selectedTabIndex = if (tab == ConfigHubTab.Manage) 0 else 1,
                onTabSelected = { index ->
                    tab = if (index == 0) ConfigHubTab.Manage else ConfigHubTab.Select
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        top = innerPadding.calculateTopPadding() + 6.dp,
                        bottom = 6.dp
                    )
            )

            // 搜索栏（仅 Manage 模式）
            AnimatedVisibility(
                visible = manageSearchVisible && tab == ConfigHubTab.Manage,
                enter = fadeIn(animationSpec = tween(140)) + expandVertically(
                    animationSpec = tween(
                        180
                    )
                ),
                exit = fadeOut(animationSpec = tween(110)) + shrinkVertically(
                    animationSpec = tween(
                        160
                    )
                )
            ) {
                HyperTextField(
                    value = manageQuery,
                    onValueChange = { manageQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .focusRequester(focusRequester),
                    label = stringResource(R.string.tools_config_search_hint),
                    singleLine = true,
                    trailingIcon = if (manageQuery.isNotBlank()) {
                        {
                            IconButton(onClick = {
                                manageQuery = ""
                                runCatching { focusRequester.requestFocus() }
                                keyboardController?.show()
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = null,
                                    tint = MiuixTheme.colorScheme.onSurfaceSecondary
                                )
                            }
                        }
                    } else null)
            }

            // 内容 LazyColumn
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = contentPaddingWithNavBars(
                    start = 12.dp, end = 12.dp, extraBottom = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    if (tab == ConfigHubTab.Manage) {
                        stateHolder.SaveableStateProvider("config_manage") {
                            ConfigBrowserContent(
                                rootPath = "",
                                titlePath = "/data/adb/box",
                                onExit = onBack,
                                onOpenEditor = onOpenEditor,
                                action = manageAction,
                                onActionConsumed = { manageAction = null },
                                query = manageQuery,
                                onQueryChange = { manageQuery = it },
                                searchVisible = manageSearchVisible,
                                currentPath = manageCurrentPath,
                                onCurrentPathChange = { manageCurrentPath = it },
                                backEnabled = enableBackHandler,
                                swipeCloseSignal = swipeCloseSignal
                            )
                        }
                    } else {
                        stateHolder.SaveableStateProvider("config_select") {
                            ConfigSelectCoreFolderContent(
                                onExit = onBack,
                                onOpenEditor = onOpenEditor,
                                action = selectAction,
                                onActionConsumed = { selectAction = null },
                                onCoreInfo = { core, active ->
                                    selectCoreName = core
                                    selectActive = active
                                    if (selectSelectedFile == null) {
                                        selectSelectedFile = active
                                    }
                                },
                                onSelectFile = { selectSelectedFile = it },
                                activateRequest = selectActivateRequest,
                                onActivateConsumed = { selectActivateRequest = null },
                                backEnabled = enableBackHandler,
                                swipeCloseSignal = swipeCloseSignal
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── 溢出菜单（WindowListPopup） ────────────────────────────────────────────

@Composable
private fun ConfigManageOverflowPopup(
    show: Boolean, onDismissRequest: () -> Unit, onAction: (ManageAction) -> Unit
) {
    val actions = listOf(
        ManageAction.NewFile to stringResource(R.string.tools_config_menu_new_file),
        ManageAction.NewFolder to stringResource(R.string.tools_config_menu_new_folder)
    )

    WindowListPopup(
        show = show,
        alignment = PopupPositionProvider.Align.TopEnd,
        onDismissRequest = onDismissRequest
    ) {
        ListPopupColumn {
            actions.forEachIndexed { index, (action, label) ->
                DropdownImpl(
                    text = label,
                    optionSize = actions.size,
                    isSelected = false,
                    onSelectedIndexChange = {
                        onDismissRequest()
                        onAction(action)
                    },
                    index = index
                )
            }
        }
    }
}

@Composable
private fun ConfigSelectOverflowPopup(
    show: Boolean, onDismissRequest: () -> Unit, onAction: (SelectAction) -> Unit
) {
    val actions = listOf(
        SelectAction.Refresh to stringResource(R.string.tools_config_menu_refresh),
        SelectAction.Create to stringResource(R.string.tools_config_menu_create),
        SelectAction.Download to stringResource(R.string.tools_config_menu_download)
    )

    WindowListPopup(
        show = show,
        alignment = PopupPositionProvider.Align.TopEnd,
        onDismissRequest = onDismissRequest
    ) {
        ListPopupColumn {
            actions.forEachIndexed { index, (action, label) ->
                DropdownImpl(
                    text = label,
                    optionSize = actions.size,
                    isSelected = false,
                    onSelectedIndexChange = {
                        onDismissRequest()
                        onAction(action)
                    },
                    index = index
                )
            }
        }
    }
}
