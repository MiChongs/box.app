package com.box.app.ui.screens.tools.apps

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.box.app.R
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField as MiuixTextField
import top.yukonga.miuix.kmp.window.WindowListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ─── 浮动顶部工具栏 ─────────────────────────────────────────────────────────

@Composable
fun FloatingAppsManageTopBar(
    onBack: () -> Unit,
    searchEnabled: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    saveEnabled: Boolean,
    onSave: () -> Unit,
    onSortFilter: () -> Unit,
    onMenuShow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(searchEnabled) {
        if (searchEnabled) {
            delay(80)
            runCatching { focusRequester.requestFocus() }
            keyboardController?.show()
        } else {
            keyboardController?.hide()
            focusManager.clearFocus(force = true)
        }
    }

    Column(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 12.dp, top = 8.dp, end = 12.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 按钮行：返回 | 搜索 | 过滤 | 保存 | 更多
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                text = stringResource(R.string.tools_apps_back_compact),
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors()
            )

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = onToggleSearch
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = if (searchEnabled) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = onSortFilter
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }

            IconButton(
                onClick = onSave,
                enabled = saveEnabled
            ) {
                Icon(
                    imageVector = Icons.Filled.Save,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.graphicsLayer(alpha = if (saveEnabled) 1f else 0.45f)
                )
            }

            IconButton(
                onClick = onMenuShow
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }
        }

        // 搜索栏
        AnimatedVisibility(
            visible = searchEnabled,
            enter = fadeIn(animationSpec = tween(durationMillis = 140)) +
                expandVertically(animationSpec = tween(durationMillis = 180)),
            exit = fadeOut(animationSpec = tween(durationMillis = 110)) +
                shrinkVertically(animationSpec = tween(durationMillis = 160))
        ) {
            MiuixTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                label = stringResource(R.string.tools_apps_search_hint),
                singleLine = true,
                trailingIcon = if (query.isNotBlank()) {
                    {
                        IconButton(
                            onClick = {
                                onQueryChange("")
                                runCatching { focusRequester.requestFocus() }
                                keyboardController?.show()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        }
                    }
                } else null
            )
        }
    }
}

// ─── 溢出菜单（WindowListPopup — 锚定在触发按钮上） ────────────────────────

@Composable
fun AppsOverflowMenu(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onMenuAction: (AppsManageAction) -> Unit
) {
    val actions = listOf(
        AppsManageAction.Refresh to stringResource(R.string.tools_apps_menu_refresh),
        AppsManageAction.SmartSelect to stringResource(R.string.tools_apps_menu_smart_select),
        AppsManageAction.SelectAll to stringResource(R.string.tools_apps_menu_select_all),
        AppsManageAction.Invert to stringResource(R.string.tools_apps_menu_invert)
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
                        onMenuAction(action)
                    },
                    index = index
                )
            }
        }
    }
}
