package com.box.app.ui.components.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.box.app.ui.theme.AppFonts
import com.kyant.shapes.Capsule
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class LatencySeverity {
    Testing,
    Unknown,
    Fast,
    Medium,
    Slow,
    Error
}

/**
 * 延迟卡片 — 仿参考设计
 *
 * ┌─ SmallTitle「延迟」    状态 ● ↻─┐  ← 标题行（Card 外）
 * └─────────────────────────────────┘
 * ┌─────────────────────────────────┐  ← Card
 * │ Baidu ●    Cloudflare ●  Google ●│
 * │ -- ms       -- ms         -- ms  │
 * └─────────────────────────────────┘
 */
@Composable
fun HomeLatencyCard(
    label1: String,
    baidu: String,
    label2: String,
    cloudflare: String,
    label3: String,
    google: String,
    loading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val badgeRes = latencyBadgeTexts(loading, listOf(baidu, cloudflare, google))
    val statusAccent = when (badgeRes) {
        R.string.home_latency_badge_ok -> homeSuccessColors().accent
        R.string.home_latency_badge_part -> homeWarningColors().accent
        R.string.home_latency_badge_down -> homeDangerColors().accent
        else -> MiuixTheme.colorScheme.onSurfaceSecondary
    }
    val animatedStatusColor by animateColorAsState(
        targetValue = statusAccent,
        animationSpec = tween(durationMillis = 360),
        label = "home_latency_status_color"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // ── 标题行（Card 外部）──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // SmallTitle 使用默认的 onBackgroundVariant 色；去掉默认 28dp 水平内边距，与卡片左缘对齐
            SmallTitle(
                text = stringResource(R.string.home_latency_title),
                modifier = Modifier.weight(1f),
                insideMargin = PaddingValues(horizontal = 0.dp, vertical = 8.dp)
            )
            // 状态文字 + 状态点
            Text(
                text = stringResource(badgeRes),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onBackgroundVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(Capsule())
                    .background(animatedStatusColor)
            )
            // 刷新按钮
            IconButton(
                onClick = onRefresh,
                modifier = Modifier.size(28.dp),
                backgroundColor = Color.Transparent,
                cornerRadius = 8.dp,
                enabled = !loading
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // ── 数据卡片 ──
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = if (compact) 14.dp else 18.dp,
            insideMargin = PaddingValues(horizontal = 8.dp, vertical = if (compact) 12.dp else 14.dp),
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LatencyChip(
                    label = label1,
                    value = baidu,
                    loading = loading,
                    modifier = Modifier.weight(1f)
                )
                LatencyChip(
                    label = label2,
                    value = cloudflare,
                    loading = loading,
                    modifier = Modifier.weight(1f)
                )
                LatencyChip(
                    label = label3,
                    value = google,
                    loading = loading,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * 单个延迟条目：
 * ┌─────────────┐
 * │ Label ●     │  ← 名称 + 状态点
 * │ -- ms       │  ← 数值（使用等宽数据字体）
 * └─────────────┘
 */
/**
 * 延迟单元：
 * ┌─────────────┐
 * │  ● Label    │  ← 状态点 + 名称（居中）
 * │   -- ms     │  ← 数值（居中，突出）
 * └─────────────┘
 */
@Composable
private fun LatencyChip(
    label: String,
    value: String,
    loading: Boolean,
    modifier: Modifier = Modifier
) {
    val severity = latencySeverity(value, loading)
    val dotColor = latencyDotColor(severity)
    val valueColor = latencyValueColor(severity)
    val animatedDotColor by animateColorAsState(
        targetValue = dotColor,
        animationSpec = tween(durationMillis = 360),
        label = "home_latency_dot_$label"
    )
    val animatedValueColor by animateColorAsState(
        targetValue = valueColor,
        animationSpec = tween(durationMillis = 360),
        label = "home_latency_value_$label"
    )

    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 状态点 + 名称
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(Capsule())
                    .background(animatedDotColor)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // 数值：逐字符滚动动画
        RollingLatencyText(
            text = normalizeLatency(value),
            style = MiuixTheme.textStyles.body1,
            color = animatedValueColor
        )
    }
}

/**
 * 延迟数值的「翻牌式」动画：
 * 每个字符独立动画，数字上升从下方滑入，下降从上方滑入，
 * 非数字字符（如 " ", "m", "s"）使用淡入淡出过渡。
 */
@Composable
private fun RollingLatencyText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { index, char ->
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    val prev = initialState
                    val curr = targetState
                    val bothDigits = prev.isDigit() && curr.isDigit()
                    if (bothDigits) {
                        val goingUp = curr.digitToInt() > prev.digitToInt()
                        if (goingUp) {
                            (slideInVertically(tween(320)) { it } + fadeIn(tween(220)))
                                .togetherWith(slideOutVertically(tween(320)) { -it } + fadeOut(tween(220)))
                        } else {
                            (slideInVertically(tween(320)) { -it } + fadeIn(tween(220)))
                                .togetherWith(slideOutVertically(tween(320)) { it } + fadeOut(tween(220)))
                        }
                    } else {
                        fadeIn(tween(200)).togetherWith(fadeOut(tween(200)))
                    }
                },
                label = "rolling_digit_$index"
            ) { ch ->
                Text(
                    text = ch.toString(),
                    style = style,
                    fontFamily = AppFonts.dataFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = color,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun latencyDotColor(severity: LatencySeverity): Color {
    val scheme = MiuixTheme.colorScheme
    return when (severity) {
        LatencySeverity.Fast -> homeSuccessColors().accent
        LatencySeverity.Medium -> homeInfoColors().accent
        LatencySeverity.Slow -> homeWarningColors().accent
        LatencySeverity.Error -> homeDangerColors().accent
        LatencySeverity.Testing -> scheme.primary
        LatencySeverity.Unknown -> scheme.onSurfaceSecondary.copy(alpha = 0.4f)
    }
}

@Composable
private fun latencyValueColor(severity: LatencySeverity): Color {
    val scheme = MiuixTheme.colorScheme
    return when (severity) {
        LatencySeverity.Unknown -> scheme.onSurfaceSecondary
        LatencySeverity.Testing -> scheme.onSurfaceSecondary
        else -> scheme.onSurface
    }
}

private fun latencySeverity(value: String, loading: Boolean = false): LatencySeverity {
    val trimmed = value.trim()
    if (loading || trimmed == "...") return LatencySeverity.Testing
    if (trimmed.isBlank() || trimmed == "-" || trimmed == "—") return LatencySeverity.Unknown
    if (trimmed.equals("timeout", ignoreCase = true) ||
        trimmed.equals("down", ignoreCase = true) ||
        trimmed.equals("DNS", ignoreCase = true) ||
        trimmed.equals("N/A", ignoreCase = true) ||
        trimmed.startsWith("HTTP ", ignoreCase = true)
    ) return LatencySeverity.Error

    val latencyMs = Regex("""([0-9]+(?:\.[0-9]+)?)""")
        .find(trimmed)?.groupValues?.getOrNull(1)?.toFloatOrNull()
        ?: return LatencySeverity.Unknown

    return when {
        latencyMs <= 80f -> LatencySeverity.Fast
        latencyMs <= 180f -> LatencySeverity.Medium
        latencyMs <= 400f -> LatencySeverity.Slow
        else -> LatencySeverity.Error
    }
}

private fun normalizeLatency(value: String): String {
    val trimmed = value.trim()
    if (trimmed.isBlank() || trimmed == "-" || trimmed == "—") return "-- ms"
    if (trimmed == "...") return trimmed
    if (trimmed.endsWith("ms", ignoreCase = true)) return trimmed
    return when {
        trimmed.all { it.isDigit() } -> "${trimmed} ms"
        else -> trimmed
    }
}

private fun latencyBadgeTexts(loading: Boolean, values: List<String>): Int {
    if (loading || values.any { it.trim() == "..." }) return R.string.home_latency_badge_test
    val severities = values.map { latencySeverity(it) }
    if (severities.all { it == LatencySeverity.Error || it == LatencySeverity.Unknown }) {
        return R.string.home_latency_badge_down
    }
    if (severities.any { it == LatencySeverity.Error || it == LatencySeverity.Unknown }) {
        return R.string.home_latency_badge_part
    }
    return R.string.home_latency_badge_ok
}
