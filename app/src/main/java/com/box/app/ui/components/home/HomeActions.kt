package com.box.app.ui.components.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Link
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.box.app.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HomeQuickActions(
    showSubStore: Boolean,
    onOpenPanel: () -> Unit,
    onOpenSubStore: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenSmartDns: (() -> Unit)? = null,
    compact: Boolean = false
) {
    data class QuickItem(val title: String, val subtitle: String, val icon: ImageVector, val onClick: () -> Unit)
    val items = buildList {
        add(QuickItem(stringResource(R.string.home_quick_panel_title), stringResource(R.string.home_quick_panel_subtitle), Icons.Outlined.Dashboard, onOpenPanel))
        if (showSubStore) add(QuickItem(stringResource(R.string.home_quick_subs_title), stringResource(R.string.home_quick_subs_subtitle), Icons.Outlined.Link, onOpenSubStore))
        add(QuickItem(stringResource(R.string.home_quick_logs_title), stringResource(R.string.home_quick_logs_subtitle), Icons.AutoMirrored.Outlined.Article, onOpenLogs))
        if (onOpenSmartDns != null) add(QuickItem("DNS", "SmartDNS", Icons.Outlined.Dns, onOpenSmartDns))
    }

    // compact: 每行 2 个网格；normal: 尽量一行排完
    val columns = if (compact) 2 else if (items.size <= 3) items.size else 2
    val chunkedRows = items.chunked(columns)

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chunkedRows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { item ->
                    QuickActionCard(
                        title = item.title,
                        subtitle = item.subtitle,
                        icon = item.icon,
                        onClick = item.onClick,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

/**
 * 快捷卡片：左上标题+副标题，右下图标。
 * 复刻目标设计：
 * ┌──────────────┐
 * │ Title        │
 * │ Subtitle     │
 * │          icon│
 * └──────────────┘
 */
@Composable
fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier,
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        onClick = onClick,
        showIndication = onClick != null,
        pressFeedbackType = PressFeedbackType.Sink
    ) {
        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Column(modifier = Modifier.fillMaxWidth().padding(end = 24.dp)) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.body2,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceSecondary.copy(alpha = 0.65f),
                modifier = Modifier
                    .size(20.dp)
                    .align(Alignment.BottomEnd)
            )
        }
    }
}
