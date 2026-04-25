package com.box.app.ui.screens.tools.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.box.app.ui.miuix.HyperBottomSheet
import com.box.app.ui.miuix.HyperFilterChip
import com.box.app.ui.miuix.HyperTextButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ─── 智能选择确认对话框 ─────────────────────────────────────────────────────

@Composable
fun SmartSelectConfirmDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onReplace: () -> Unit,
    onMerge: () -> Unit
) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.tools_apps_smart_select_title),
        summary = stringResource(R.string.tools_apps_smart_select_body),
        onDismissRequest = onDismiss
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                text = stringResource(R.string.tools_apps_smart_select_merge),
                onClick = onMerge
            )
            Spacer(modifier = Modifier.size(10.dp))
            TextButton(
                text = stringResource(R.string.tools_apps_smart_select_replace),
                onClick = onReplace,
                colors = ButtonDefaults.textButtonColorsPrimary()
            )
        }
    }
}

// ─── 智能选择进度对话框 ─────────────────────────────────────────────────────

@Composable
fun SmartSelectProgressDialog(
    show: Boolean
) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.tools_apps_smart_selecting_title),
        onDismissRequest = { /* 不可关闭 */ }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfiniteProgressIndicator(modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(R.string.tools_apps_smart_selecting_analyzing),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
    }
}

// ─── 排序/过滤底部工作表 ────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SortFilterBottomSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    draftSortOrder: AppsSortOrder,
    draftAppType: AppsAppType,
    draftNetworkFilter: AppsNetworkFilter,
    draftUserFilter: AppsUserFilter,
    onSortOrderChange: (AppsSortOrder) -> Unit,
    onAppTypeChange: (AppsAppType) -> Unit,
    onNetworkFilterChange: (AppsNetworkFilter) -> Unit,
    onUserFilterChange: (AppsUserFilter) -> Unit,
    onApply: () -> Unit
) {
    if (!show) return

    val navBarPadding = com.box.app.ui.components.systemNavBarBottomPadding()

    HyperBottomSheet(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.tools_apps_sort_filter_title),
        endAction = {
            HyperTextButton(
                text = stringResource(R.string.action_apply),
                onClick = onApply,
                prominent = true
            )
        }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.tools_apps_sort_by),
                style = MiuixTheme.textStyles.title4
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HyperFilterChip(
                    selected = draftSortOrder == AppsSortOrder.NAME_ASC,
                    onClick = { onSortOrderChange(AppsSortOrder.NAME_ASC) },
                    label = stringResource(R.string.tools_apps_sort_name_asc)
                )
                HyperFilterChip(
                    selected = draftSortOrder == AppsSortOrder.NAME_DESC,
                    onClick = { onSortOrderChange(AppsSortOrder.NAME_DESC) },
                    label = stringResource(R.string.tools_apps_sort_name_desc)
                )
                HyperFilterChip(
                    selected = draftSortOrder == AppsSortOrder.INSTALL_TIME_ASC,
                    onClick = { onSortOrderChange(AppsSortOrder.INSTALL_TIME_ASC) },
                    label = stringResource(R.string.tools_apps_sort_install_asc)
                )
                HyperFilterChip(
                    selected = draftSortOrder == AppsSortOrder.INSTALL_TIME_DESC,
                    onClick = { onSortOrderChange(AppsSortOrder.INSTALL_TIME_DESC) },
                    label = stringResource(R.string.tools_apps_sort_install_desc)
                )
            }

            Text(
                text = stringResource(R.string.tools_apps_app_type),
                style = MiuixTheme.textStyles.title4
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HyperFilterChip(
                    selected = draftAppType == AppsAppType.ALL,
                    onClick = { onAppTypeChange(AppsAppType.ALL) },
                    label = stringResource(R.string.tools_apps_filter_all)
                )
                HyperFilterChip(
                    selected = draftAppType == AppsAppType.USER,
                    onClick = { onAppTypeChange(AppsAppType.USER) },
                    label = stringResource(R.string.tools_apps_filter_user)
                )
                HyperFilterChip(
                    selected = draftAppType == AppsAppType.SYSTEM,
                    onClick = { onAppTypeChange(AppsAppType.SYSTEM) },
                    label = stringResource(R.string.tools_apps_filter_system)
                )
            }

            Text(
                text = stringResource(R.string.tools_apps_network_permission),
                style = MiuixTheme.textStyles.title4
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HyperFilterChip(
                    selected = draftNetworkFilter == AppsNetworkFilter.ALL,
                    onClick = { onNetworkFilterChange(AppsNetworkFilter.ALL) },
                    label = stringResource(R.string.tools_apps_filter_all)
                )
                HyperFilterChip(
                    selected = draftNetworkFilter == AppsNetworkFilter.ONLY_NETWORK,
                    onClick = { onNetworkFilterChange(AppsNetworkFilter.ONLY_NETWORK) },
                    label = stringResource(R.string.tools_apps_filter_only_network)
                )
                HyperFilterChip(
                    selected = draftNetworkFilter == AppsNetworkFilter.EXCLUDE_NETWORK,
                    onClick = { onNetworkFilterChange(AppsNetworkFilter.EXCLUDE_NETWORK) },
                    label = stringResource(R.string.tools_apps_filter_exclude_network)
                )
            }

            Text(
                text = stringResource(R.string.tools_apps_user_space),
                style = MiuixTheme.textStyles.title4
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HyperFilterChip(
                    selected = draftUserFilter == AppsUserFilter.ALL,
                    onClick = { onUserFilterChange(AppsUserFilter.ALL) },
                    label = stringResource(R.string.tools_apps_filter_all_users)
                )
                HyperFilterChip(
                    selected = draftUserFilter == AppsUserFilter.MAIN_ONLY,
                    onClick = { onUserFilterChange(AppsUserFilter.MAIN_ONLY) },
                    label = stringResource(R.string.tools_apps_filter_main_only)
                )
                HyperFilterChip(
                    selected = draftUserFilter == AppsUserFilter.WORK_ONLY,
                    onClick = { onUserFilterChange(AppsUserFilter.WORK_ONLY) },
                    label = stringResource(R.string.tools_apps_filter_work_clone)
                )
                HyperFilterChip(
                    selected = draftUserFilter == AppsUserFilter.OTHER_ONLY,
                    onClick = { onUserFilterChange(AppsUserFilter.OTHER_ONLY) },
                    label = stringResource(R.string.tools_apps_filter_other_users)
                )
            }

            Spacer(modifier = Modifier.height(navBarPadding))
        }
    }
}
