package com.box.app.ui.components.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.box.app.ui.theme.appColors
import com.kyant.shapes.RoundedRectangle

@Composable
fun LatencyWideCard(
    label1: String,
    baidu: String,
    label2: String,
    cloudflare: String,
    label3: String,
    google: String,
    loading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = appColors()
    val container = c.card
    val isOk = { v: String ->
        v.isNotBlank() && v != "-" && v != "N/A" && v != "..." && v != "¡ª"
    }
    val okCount = listOf(baidu, cloudflare, google).count { isOk(it) }

    val badgeText = when {
        loading -> stringResource(R.string.home_latency_badge_test)
        okCount == 3 -> stringResource(R.string.home_latency_badge_ok)
        okCount == 0 -> stringResource(R.string.home_latency_badge_down)
        else -> stringResource(R.string.home_latency_badge_part)
    }

    val accent = when {
        loading -> Color(0xFFD29922)
        okCount == 3 -> Color(0xFF2DA44E)
        okCount == 0 -> Color(0xFFCF222E)
        else -> Color(0xFFD29922)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onRefresh
            ),
        shape = RoundedRectangle(18.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.home_latency_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = c.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                MetricBadge(kind = HomeMetricKind.Latency, accent = accent, overrideText = badgeText)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LatencyMini(label = label1, value = baidu, modifier = Modifier.weight(1f))
                LatencyMini(label = label2, value = cloudflare, modifier = Modifier.weight(1f))
                LatencyMini(label = label3, value = google, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LatencyMini(label: String, value: String, modifier: Modifier = Modifier) {
    val c = appColors()
    val chip = c.cardAlt

    fun parseLatencyMs(raw: String): Int? {
        val cleaned = raw.trim().lowercase()
        if (cleaned.isBlank() || cleaned == "-" || cleaned == "n/a" || cleaned == "..." || cleaned == "¡ª") return null
        return cleaned.replace("ms", "").trim().toIntOrNull()
    }

    val ms = parseLatencyMs(value)
    val valueColor = when {
        ms == null -> c.textPrimary
        ms < 300 -> Color(0xFF2DA44E)
        ms < 500 -> Color(0xFFD29922)
        else -> Color(0xFFCF222E)
    }

    Column(
        modifier = modifier
            .clip(RoundedRectangle(14.dp))
            .background(chip)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = c.textSecondary)
        Text(text = value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

