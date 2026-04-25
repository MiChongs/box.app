package com.box.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.box.app.data.backend.HomeMetricsApi
import com.box.app.data.model.SubscriptionItem
import com.box.app.data.repo.HomeRepository
import com.box.app.utils.ThemeManager
import com.kyant.shapes.RoundedRectangle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

@Composable
fun SubscriptionDetailScreen(
    onBack: () -> Unit,
    onOpenToolsSubscription: () -> Unit
) {
    val metricsState by HomeRepository.metricsState.collectAsState()
    val items = metricsState.subscriptionItems
    val isClashApiEnabled by HomeRepository.useClashApiForSubscription.collectAsState()

    val isDark = ThemeManager.shouldUseDarkTheme()
    val normalColor = if (isDark) Color(0xFF79B8FF) else Color(0xFF1472B6)
    val warnColor = if (isDark) Color(0xFFFFCC80) else Color(0xFFE67E22)
    val criticalColor = if (isDark) Color(0xFFFF8A80) else Color(0xFFC62828)
    val trackColor = if (isDark) Color(0xFF2A2A2A) else Color(0xFFE8E8E8)

    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.home_card_subscription),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (!isClashApiEnabled) {
                        IconButton(onClick = onOpenToolsSubscription) {
                            Icon(
                                imageVector = Icons.Filled.Settings,
                                contentDescription = null,
                                tint = MiuixTheme.colorScheme.onSurfaceSecondary
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.bottomsheet_subscription_none),
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                    if (!isClashApiEnabled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(
                            text = stringResource(R.string.bottomsheet_subscription_go_tools),
                            onClick = onOpenToolsSubscription
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp, end = 12.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.url }) { item ->
                    val used = item.uploadBytes + item.downloadBytes
                    val remain = (item.totalBytes - used).max(java.math.BigInteger.ZERO)
                    val progress = if (item.totalBytes > java.math.BigInteger.ZERO) {
                        used.toBigDecimal().divide(item.totalBytes.toBigDecimal(), 6, java.math.RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
                    } else 0f
                    val remainRatio = if (item.totalBytes > java.math.BigInteger.ZERO) remain.toBigDecimal().divide(item.totalBytes.toBigDecimal(), 6, java.math.RoundingMode.HALF_UP).toDouble() else 1.0
                    val daysLeft = daysUntilExpiry(item.expiryDate)

                    val statusColor = when {
                        (daysLeft != null && daysLeft < 0) || remainRatio <= 0.05 -> criticalColor
                        (daysLeft != null && daysLeft <= 3) || remainRatio <= 0.10 -> criticalColor
                        (daysLeft != null && daysLeft <= 7) || remainRatio <= 0.20 -> warnColor
                        else -> normalColor
                    }

                    SubCard(
                        item = item,
                        used = used,
                        remain = remain,
                        progress = progress,
                        statusColor = statusColor,
                        trackColor = trackColor,
                        daysLeft = daysLeft,
                        onRefresh = { HomeRepository.refreshSubscriptionItemNow(item.url) }
                    )
                }
            }
        }
    }
}

// ─── 订阅卡片 ──────────────────────────────────────────

@Composable
private fun SubCard(
    item: SubscriptionItem,
    used: java.math.BigInteger,
    remain: java.math.BigInteger,
    progress: Float,
    statusColor: Color,
    trackColor: Color,
    daysLeft: Int?,
    onRefresh: () -> Unit
) {
    val expiryText = humanizeExpiry(daysLeft, item.expiryDate)
    val updateText = humanizeUpdateTime(item.lastUpdatedAtMs)

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 标题行：名称 + 刷新 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name,
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (item.loading) {
                    InfiniteProgressIndicator(modifier = Modifier.size(20.dp))
                } else {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(34.dp),
                        cornerRadius = 10.dp,
                        backgroundColor = Color.Transparent
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Autorenew,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // ── 信息行：到期 · 更新时间 ──
            Text(
                text = expiryText,
                style = MiuixTheme.textStyles.body2,
                color = if (daysLeft != null && daysLeft < 0) statusColor
                        else MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = updateText,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // ── 进度条 + 已用/总量 ──
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedRectangle(8.dp)),
                    colors = ProgressIndicatorDefaults.progressIndicatorColors(
                        foregroundColor = statusColor,
                        backgroundColor = trackColor
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.bottomsheet_subscription_used, HomeMetricsApi.formatBytes(used)),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = stringResource(R.string.bottomsheet_subscription_total, HomeMetricsApi.formatBytes(item.totalBytes)),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                }
            }

            // ── 三列流量明细 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatChip(
                    label = stringResource(R.string.bottomsheet_subscription_upload),
                    value = HomeMetricsApi.formatBytes(item.uploadBytes),
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    label = stringResource(R.string.bottomsheet_subscription_download),
                    value = HomeMetricsApi.formatBytes(item.downloadBytes),
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    label = stringResource(R.string.bottomsheet_subscription_remaining),
                    value = HomeMetricsApi.formatBytes(remain),
                    valueColor = statusColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

// ─── 统计块 ──────────────────────────────────────────

@Composable
private fun StatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MiuixTheme.colorScheme.onSurface
) {
    Card(
        modifier = modifier,
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = value,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = label,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─── 人性化时间格式 ──────────────────────────────────────

@Composable
private fun humanizeExpiry(daysLeft: Int?, rawDate: String): String {
    if (daysLeft == null) {
        val d = rawDate.trim()
        return if (d.isBlank() || d == "-") stringResource(R.string.bottomsheet_subscription_expiry_unknown) else d
    }
    return when {
        daysLeft < 0 -> stringResource(R.string.bottomsheet_subscription_expiry_expired, -daysLeft)
        daysLeft == 0 -> stringResource(R.string.bottomsheet_subscription_expiry_today)
        daysLeft <= 30 -> stringResource(R.string.bottomsheet_subscription_expiry_days, daysLeft)
        else -> {
            val datePart = rawDate.trim().take(10) // yyyy-MM-dd
            stringResource(R.string.bottomsheet_subscription_expiry_days_date, daysLeft, datePart)
        }
    }
}

@Composable
private fun humanizeUpdateTime(ms: Long): String {
    if (ms <= 0L) return stringResource(R.string.bottomsheet_subscription_update_never)
    val now = System.currentTimeMillis()
    val diffMin = ((now - ms) / 60_000L).coerceAtLeast(0L)

    if (diffMin < 1) return stringResource(R.string.bottomsheet_subscription_update_just_now)
    if (diffMin < 60) return stringResource(R.string.bottomsheet_subscription_update_minutes, diffMin)

    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val updateDate = runCatching {
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    }.getOrNull() ?: return "-"
    val timePart = runCatching {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ms))
    }.getOrNull() ?: return "-"

    return when (updateDate) {
        today -> stringResource(R.string.bottomsheet_subscription_update_today, timePart)
        today.minusDays(1) -> stringResource(R.string.bottomsheet_subscription_update_yesterday, timePart)
        else -> runCatching {
            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ms))
        }.getOrNull() ?: timePart
    }
}

private fun daysUntilExpiry(expiryDate: String): Int? {
    val d = expiryDate.trim()
    if (d.isBlank() || d == "-") return null
    return try {
        ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(d)).toInt()
    } catch (_: Exception) { null }
}
