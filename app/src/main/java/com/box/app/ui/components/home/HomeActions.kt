package com.box.app.ui.components.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.box.app.ui.theme.appColors
import com.box.app.utils.ThemeManager
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

@Composable
fun HomeQuickActions(
    showSubStore: Boolean,
    onOpenPanel: () -> Unit,
    onOpenSubStore: () -> Unit,
    onOpenLogs: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionCard(
            title = stringResource(R.string.home_quick_panel_title),
            subtitle = stringResource(R.string.home_quick_panel_subtitle),
            accent = Color(0xFF3B82F6),
            modifier = Modifier.weight(1f),
            onClick = onOpenPanel
        )
        if (showSubStore) {
            QuickActionCard(
                title = stringResource(R.string.home_quick_subs_title),
                subtitle = stringResource(R.string.home_quick_subs_subtitle),
                accent = Color(0xFFF97316),
                modifier = Modifier.weight(1f),
                onClick = onOpenSubStore
            )
        }
        QuickActionCard(
            title = stringResource(R.string.home_quick_logs_title),
            subtitle = stringResource(R.string.home_quick_logs_subtitle),
            accent = Color(0xFF14B8A6),
            modifier = Modifier.weight(1f),
            onClick = onOpenLogs
        )
    }
}

@Composable
fun QuickActionCard(
    title: String,
    subtitle: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val c = appColors()
    val isDark = ThemeManager.shouldUseDarkTheme()
    val container = c.card

    val glowLargeAlpha = if (isDark) 0.22f else 0.16f
    val glowSmallAlpha = if (isDark) 0.18f else 0.12f
    Card(
        modifier = if (onClick != null) {
            modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick.invoke() }
        } else {
            modifier
        },
        shape = RoundedRectangle(18.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp)
                .clip(RoundedRectangle(18.dp))
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawWithCache {
                        val largeBrush = Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = glowLargeAlpha),
                                Color.Transparent
                            ),
                            center = Offset(0f, 0f),
                            radius = size.minDimension * 0.95f
                        )

                        val smallBrush = Brush.radialGradient(
                            colors = listOf(
                                accent.copy(alpha = glowSmallAlpha),
                                Color.Transparent
                            ),
                            center = Offset(size.width, size.height),
                            radius = size.minDimension * 0.55f
                        )

                        onDrawBehind {
                            drawRect(brush = largeBrush)
                            drawRect(brush = smallBrush)
                        }
                    }
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(Capsule())
                            .background(accent)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = c.textPrimary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = c.textSecondary.copy(alpha = if (isDark) 0.65f else 0.50f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = 6.dp)
            )
        }
    }
}

