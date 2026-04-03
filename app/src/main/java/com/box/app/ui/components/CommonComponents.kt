package com.box.app.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.box.app.ui.theme.appColors
import com.box.app.utils.AppIcon
import com.box.app.utils.ThemeManager
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle

val LocalFloatingNavBarSpaceDp = staticCompositionLocalOf { 64.dp }
val LocalNavigationBarsPaddingEnabled = staticCompositionLocalOf { true }
val LocalSystemNavBarInsetDp = staticCompositionLocalOf { 0.dp }

fun Modifier.navigationBarsPaddingIfEnabled(): Modifier = composed {
    if (LocalNavigationBarsPaddingEnabled.current) {
        this.windowInsetsPadding(WindowInsets.navigationBars)
    } else {
        this
    }
}

fun Modifier.systemNavBarPadding(): Modifier = composed {
    val inset = LocalSystemNavBarInsetDp.current
    if (inset > 0.dp) this.padding(bottom = inset) else this
}

/**
 * Calculate content padding that accounts for navigation bars and floating nav bar
 */
@Composable
fun contentPaddingWithNavBars(
    start: Dp = 0.dp,
    end: Dp = 0.dp,
    top: Dp = 0.dp,
    extraBottom: Dp = 0.dp
): PaddingValues {
    val floatingNavBarSpace = LocalFloatingNavBarSpaceDp.current
    val systemNavBarInset = LocalSystemNavBarInsetDp.current
    
    return PaddingValues(
        start = start,
        end = end,
        top = top,
        bottom = floatingNavBarSpace + systemNavBarInset + extraBottom
    )
}

@Composable
fun ErrorToast(
    message: String?,
    onConsumed: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(message) {
        val msg = message
        if (!msg.isNullOrBlank()) {
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            onConsumed()
        }
    }
}

@Composable
fun NoRippleDropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedRectangle(10.dp))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leadingIcon != null) {
            Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                leadingIcon()
            }
            Spacer(modifier = Modifier.size(10.dp))
        }
        Box(modifier = Modifier.weight(1f)) {
            text()
        }
    }
}

@Composable
fun MiniBadge(text: String, color: Color? = null) {
    val c = appColors()
    val backgroundColor = color ?: c.cardAlt
    val textColor = if (color != null) Color.White else c.textPrimary
    
    Box(
        modifier = Modifier
            .clip(Capsule())
            .background(backgroundColor)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun StatusBadge(
    text: String,
    isActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val c = appColors()
    val backgroundColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        c.cardAlt
    }
    val textColor = if (isActive) {
        Color.White
    } else {
        c.textSecondary
    }
    
    Box(
        modifier = modifier
            .clip(RoundedRectangle(8.dp))
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
fun FileTypeIcon(fileName: String, modifier: Modifier = Modifier) {
    val c = appColors()
    val ext = fileName.substringAfterLast('.', "").lowercase()
    
    val (icon, iconColor) = when {
        ext in listOf("yaml", "yml") -> Icons.Filled.Description to Color(0xFF4CAF50)
        ext == "json" -> Icons.Filled.Description to Color(0xFF2196F3)
        ext in listOf("ini", "cfg", "conf") -> Icons.Filled.Description to Color(0xFF9C27B0)
        ext == "sh" -> Icons.Filled.Description to Color(0xFF607D8B)
        fileName.startsWith("box.") -> Icons.Filled.Description to Color(0xFFFF9800)
        else -> Icons.Filled.Description to c.textSecondary
    }
    
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(RoundedRectangle(10.dp))
            .background(iconColor.copy(alpha = 0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun ToolsFileRow(
    fileName: String,
    subtitle: String,
    showDivider: Boolean = true,
    modifier: Modifier = Modifier,
    badge: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val c = appColors()
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedRectangle(14.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileTypeIcon(fileName = fileName)

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (badge != null) {
            badge()
            Spacer(modifier = Modifier.padding(end = 8.dp))
        }

        Text(
            text = ">",
            style = MaterialTheme.typography.titleMedium,
            color = c.textSecondary.copy(alpha = 0.75f)
        )
    }

    if (showDivider) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 52.dp)
                .height(1.dp)
                .background(c.divider)
        )
    }
}

@Composable
fun ToolsSubHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    val c = appColors()
    val interactionSource = remember { MutableInteractionSource() }
    Column(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(Capsule())
                    .background(c.cardAlt)
                    .clickable(interactionSource = interactionSource, indication = null, onClick = onBack)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "< Back",
                    style = MaterialTheme.typography.labelLarge,
                    color = c.textPrimary
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = c.textSecondary,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
fun ToolsSearchCard(hint: String) {
    val c = appColors()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedRectangle(18.dp),
        colors = CardDefaults.cardColors(containerColor = c.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedRectangle(18.dp))
                .background(c.cardAlt)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary
            )
        }
    }
}

@Composable
fun ToolsSectionCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = appColors()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedRectangle(18.dp),
        colors = CardDefaults.cardColors(containerColor = c.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.textSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun ToolsRowIcon(
    icon: ImageVector,
    title: String,
    subtitle: String,
    showDivider: Boolean = true,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    badge: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val c = appColors()
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedRectangle(14.dp))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedRectangle(10.dp))
                .background(c.cardAlt),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = c.textPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (badge != null) {
            badge()
            Spacer(modifier = Modifier.width(8.dp))
        }

        Text(
            text = ">",
            style = MaterialTheme.typography.titleMedium,
            color = c.textSecondary.copy(alpha = 0.75f)
        )
    }

    if (showDivider) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 52.dp)
                .height(1.dp)
                .background(c.divider)
        )
    }
}

@Composable
fun ToolsRowBitmapIcon(
    packageName: String,
    fallbackIcon: ImageVector,
    title: String,
    subtitle: String,
    showDivider: Boolean = true,
    clipRow: Boolean = true,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val c = appColors()
    val interactionSource = remember { MutableInteractionSource() }
    val isDark = ThemeManager.shouldUseDarkTheme()
    val accent = if (isDark) Color(0xFF6CB6FF) else Color(0xFF0969DA)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (clipRow) Modifier.clip(RoundedRectangle(14.dp)) else Modifier)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp),
            contentAlignment = Alignment.Center
        ) {
            val fallbackPainter = rememberVectorPainter(image = fallbackIcon)
            AsyncImage(
                model = AppIcon(packageName),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                error = fallbackPainter,
                fallback = fallbackPainter
            )

            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(16.dp)
                        .clip(Capsule())
                        .background(accent)
                        .border(1.dp, Color.White.copy(alpha = 0.9f), Capsule()),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = ">",
            style = MaterialTheme.typography.titleMedium,
            color = c.textSecondary.copy(alpha = 0.75f)
        )
    }

    if (showDivider) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 52.dp)
                .height(1.dp)
                .background(c.divider)
        )
    }
}

@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    showDivider: Boolean = true
) {
    val c = appColors()
    val isDark = ThemeManager.shouldUseDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }

    val checkedTrack = if (isDark) Color(0xFF1F6FEB) else Color(0xFF0969DA)
    val checkedThumb = Color.White
    val uncheckedTrack = c.cardAlt
    val uncheckedThumb = if (isDark) Color(0xFF8B949E) else Color(0xFF6E7781)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedRectangle(14.dp))
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = { onCheckedChange(!checked) }
            )
            .alpha(if (enabled) 1f else 0.45f)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedRectangle(10.dp))
                .background(c.cardAlt),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = c.textPrimary,
                modifier = Modifier.size(18.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = checkedThumb,
                checkedTrackColor = checkedTrack,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = uncheckedThumb,
                uncheckedTrackColor = uncheckedTrack,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }

    if (showDivider) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 52.dp)
                .height(1.dp)
                .background(c.divider)
        )
    }
}

