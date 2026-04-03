package com.box.app.ui.components.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import com.box.app.R
import com.box.app.ui.theme.appColors
import com.box.app.utils.ThemeManager
import java.util.Locale

@Composable
fun HomeTwoColumnGrid(models: List<HomeCardModel>) {
    val rows = remember(models) { models.chunked(2) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    model = row[0],
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                if (row.size > 1) {
                    MetricCard(
                        model = row[1],
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                } else {
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Composable
fun MetricCard(model: HomeCardModel, modifier: Modifier = Modifier) {
    val c = appColors()
    val container = c.card

    val clickModifier = if (model.onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            model.onClick.invoke()
        }
    } else {
        Modifier
    }

    Card(
        modifier = modifier.then(clickModifier),
        shape = RoundedRectangle(18.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        if (model.kind == HomeMetricKind.System) {
            SystemMetricCardContent(model = model)
        } else if (model.kind == HomeMetricKind.Subscription) {
            SubscriptionMetricCardContent(model = model)
        } else if (model.kind == HomeMetricKind.Speed) {
            SpeedMetricCardContent(model = model)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 118.dp)
                    .padding(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = model.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = c.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricBadge(
                            kind = model.kind,
                            accent = model.accent,
                            overrideText = model.badgeText
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = model.value,
                        style = if (model.kind == HomeMetricKind.Ip) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.headlineSmall
                        },
                        fontWeight = FontWeight.SemiBold,
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    if (model.subtitle.isNotBlank()) {
                        Text(
                            text = model.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (model.progress != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { model.progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedRectangle(6.dp)),
                            color = model.accent,
                            trackColor = c.divider
                        )
                    }
                }

                if (model.onCornerAction != null && model.cornerActionIcon != null) {
                    Icon(
                        imageVector = model.cornerActionIcon,
                        contentDescription = null,
                        tint = c.textSecondary,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 2.dp, bottom = 2.dp)
                            .size(20.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                model.onCornerAction.invoke()
                            }
                    )
                }
            }
        }
    }
}

private fun extractSpeedValue(raw: String): String {
    val text = raw.trim()
    if (text.isBlank()) return "-"
    val idx = text.indexOf(' ')
    return if (idx in 1 until text.lastIndex) text.substring(idx + 1).trim().ifBlank { text } else text
}

private fun formatSpeedValue(raw: String): String {
    val text = raw.trim()
    if (text.isBlank() || text == "-" || text == "—") return "-"

    val pattern = Regex("""^([0-9]+(?:\.[0-9]+)?)\s*([KMGT]?)(?:i)?B/s$""", RegexOption.IGNORE_CASE)
    val m = pattern.find(text) ?: return text
    val value = m.groupValues[1].toFloatOrNull() ?: return text
    val unit = m.groupValues[2].uppercase(Locale.US)
    val number = String.format(Locale.US, "%.1f", value)
    return if (unit.isBlank()) "${number}B/s" else "${number}${unit}B/s"
}

@Composable
private fun SpeedMetricCardContent(model: HomeCardModel) {
    val c = appColors()
    val isDark = ThemeManager.shouldUseDarkTheme()
    val upColor = if (isDark) Color(0xFF7DE3B5) else Color(0xFF12936A)
    val downColor = if (isDark) Color(0xFF79C6FF) else Color(0xFF1E6EA8)
    val upValue = remember(model.subtitle) { formatSpeedValue(extractSpeedValue(model.subtitle)) }
    val downValue = remember(model.value) { formatSpeedValue(model.value) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 118.dp)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = c.textSecondary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                MetricBadge(
                    kind = model.kind,
                    accent = model.accent,
                    overrideText = model.badgeText
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(0.6f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowUpward,
                            contentDescription = null,
                            tint = upColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = upValue,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowDownward,
                            contentDescription = null,
                            tint = downColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = downValue,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = c.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxWidth()
                ) {
                    if (model.sparkDown != null && model.sparkUp != null) {
                        SpeedSparkline(
                            downSeries = model.sparkDown,
                            upSeries = model.sparkUp,
                            downColor = downColor,
                            upColor = upColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.fillMaxWidth().height(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SubscriptionMetricCardContent(model: HomeCardModel) {
    val c = appColors()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 118.dp)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = c.textSecondary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                MetricBadge(
                    kind = model.kind,
                    accent = model.accent,
                    overrideText = model.badgeText
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.home_subscription_used_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textSecondary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.home_subscription_total_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.value,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = model.subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (model.progress != null) {
                LinearProgressIndicator(
                    progress = { model.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedRectangle(6.dp)),
                    color = model.accent,
                    trackColor = c.divider
                )
            }
        }
    }
}

private fun parseCpuPercent(text: String): Float? {
    val match = Regex("""([0-9]+(?:\.[0-9]+)?)\s*%""").find(text) ?: return null
    return match.groupValues.getOrNull(1)?.toFloatOrNull()
}

private fun parseRamMb(text: String): Float? {
    val match = Regex("""([0-9]+(?:\.[0-9]+)?)\s*MB""", RegexOption.IGNORE_CASE).find(text) ?: return null
    return match.groupValues.getOrNull(1)?.toFloatOrNull()
}

private fun ramDisplayCeilingMb(ramMb: Float?): Int {
    val v = (ramMb ?: 0f).coerceAtLeast(0f)
    return when {
        v <= 64f -> 128
        v <= 128f -> 256
        v <= 256f -> 512
        v <= 512f -> 1024
        v <= 1024f -> 2048
        else -> 4096
    }
}

@Composable
private fun SystemMetricCardContent(model: HomeCardModel) {
    val c = appColors()
    val isDark = ThemeManager.shouldUseDarkTheme()
    val isActive = model.isActive

    val cpuPercent = remember(model.value) { parseCpuPercent(model.value) }
    val ramMb = remember(model.subtitle) { parseRamMb(model.subtitle) }
    val ramCeiling = remember(ramMb) { ramDisplayCeilingMb(ramMb) }

    val cpuProgressRaw = ((cpuPercent ?: 0f) / 100f).coerceIn(0f, 1f)
    val ramProgressRaw = (((ramMb ?: 0f) / ramCeiling.toFloat())).coerceIn(0f, 1f)

    val cpuProgress by animateFloatAsState(
        targetValue = cpuProgressRaw,
        animationSpec = tween(durationMillis = 280),
        label = "system_cpu_progress"
    )
    val ramProgress by animateFloatAsState(
        targetValue = ramProgressRaw,
        animationSpec = tween(durationMillis = 280),
        label = "system_ram_progress"
    )

    val cpuColor = when {
        (cpuPercent ?: 0f) >= 100f -> Color(0xFFE5534B)
        (cpuPercent ?: 0f) >= 85f -> Color(0xFFDD8B28)
        else -> if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
    }
    val ramColor = if (isDark) Color(0xFF34D399) else Color(0xFF059669)
    val track = c.divider

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 118.dp)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = model.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = c.textSecondary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                MetricBadge(
                    kind = model.kind,
                    accent = model.accent,
                    overrideText = model.badgeText
                )
            }

            if (isActive) {
                SystemMetricBar(
                    label = "CPU",
                    valueText = cpuPercent?.let { String.format(Locale.US, "%.1f%%", it) } ?: "--",
                    progress = cpuProgress,
                    color = cpuColor,
                    trackColor = track
                )
                SystemMetricBar(
                    label = "RAM",
                    valueText = ramMb?.let { "${it.toInt()}MB" } ?: "--",
                    rightHint = "/${ramCeiling}MB",
                    progress = ramProgress,
                    color = ramColor,
                    trackColor = track
                )
            } else {
                SystemMetricDisabledHint()
            }
        }
    }
}

@Composable
private fun SystemMetricDisabledHint() {
    val c = appColors()
    val isDark = ThemeManager.shouldUseDarkTheme()
    val hintColor = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309)
    val hintBg = hintColor.copy(alpha = if (isDark) 0.18f else 0.12f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedRectangle(10.dp))
            .background(hintBg)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.home_system_disabled_hint),
            style = MaterialTheme.typography.bodySmall,
            color = c.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SystemMetricBar(
    label: String,
    valueText: String,
    progress: Float,
    color: Color,
    trackColor: Color,
    rightHint: String? = null
) {
    val c = appColors()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = c.textSecondary,
                maxLines = 1,
                modifier = Modifier.weight(1f),
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary,
                maxLines = 1
            )
            if (!rightHint.isNullOrBlank()) {
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = rightHint,
                    style = MaterialTheme.typography.labelSmall,
                    color = c.textSecondary,
                    maxLines = 1
                )
            }
        }

        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedRectangle(6.dp)),
            color = color,
            trackColor = trackColor
        )
    }
}

@Composable
fun MetricBadge(kind: HomeMetricKind, accent: Color, modifier: Modifier = Modifier, overrideText: String? = null) {
    val c = appColors()
    val text = overrideText ?: when (kind) {
        HomeMetricKind.Service -> stringResource(R.string.home_badge_service)
        HomeMetricKind.Ip -> stringResource(R.string.home_badge_ip)
        HomeMetricKind.Speed -> stringResource(R.string.home_badge_net)
        HomeMetricKind.Latency -> stringResource(R.string.home_badge_ms)
        HomeMetricKind.Subscription -> stringResource(R.string.home_badge_sub)
        HomeMetricKind.System -> stringResource(R.string.home_badge_sys)
    }

    val isDark = ThemeManager.shouldUseDarkTheme()
    val bg = accent.copy(alpha = if (isDark) 0.18f else 0.12f)

    Box(
        modifier = modifier
            .clip(Capsule())
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = c.textPrimary
        )
    }
}

@Composable
fun SpeedSparkline(
    downSeries: List<Float>,
    upSeries: List<Float>,
    downColor: Color,
    upColor: Color,
    strokeWidth: Float = 2f,
    modifier: Modifier = Modifier
) {
    val c = appColors()
    val track = c.divider.copy(alpha = 0.35f)
    val all = (downSeries + upSeries)
    val maxV = (all.maxOrNull() ?: 0f).coerceAtLeast(1f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawLine(track, start = Offset(0f, h - 1f), end = Offset(w, h - 1f), strokeWidth = 1f)

        fun buildSmoothPath(series: List<Float>): Path {
            val p = Path()
            if (series.isEmpty()) return p
            val n = series.size
            fun point(i: Int): Offset {
                val x = if (n == 1) 0f else (i.toFloat() / (n - 1).toFloat()) * w
                val v = (series[i] / maxV).coerceIn(0f, 1f)
                val y = h - (v * (h - 2f))
                return Offset(x, y)
            }

            val p0 = point(0)
            p.moveTo(p0.x, p0.y)
            if (n == 1) return p

            // Quadratic smoothing using midpoints.
            for (i in 1 until n) {
                val prev = point(i - 1)
                val cur = point(i)
                val mid = Offset((prev.x + cur.x) / 2f, (prev.y + cur.y) / 2f)
                if (i == 1) {
                    p.quadraticTo(prev.x, prev.y, mid.x, mid.y)
                } else {
                    p.quadraticTo(prev.x, prev.y, mid.x, mid.y)
                }
                if (i == n - 1) {
                    p.quadraticTo(cur.x, cur.y, cur.x, cur.y)
                }
            }
            return p
        }

        val downPath = buildSmoothPath(downSeries)
        val upPath = buildSmoothPath(upSeries)
        drawPath(
            path = downPath,
            color = downColor,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawPath(
            path = upPath,
            color = upColor,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

