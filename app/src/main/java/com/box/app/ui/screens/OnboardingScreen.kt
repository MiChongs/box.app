package com.box.app.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.box.app.data.backend.EnvironmentChecker
import com.box.app.ui.theme.appColors
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val c = appColors()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    var tosAccepted by rememberSaveable { mutableStateOf(false) }

    var permissionRefreshVersion by remember { mutableIntStateOf(0) }
    var envRefreshVersion by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnResume by rememberUpdatedState {
        envRefreshVersion += 1
        permissionRefreshVersion += 1
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                latestOnResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val requestPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissionRefreshVersion += 1 }
    )

    var hasRoot by remember { mutableStateOf(false) }
    var requestingRoot by remember { mutableStateOf(false) }
    LaunchedEffect(envRefreshVersion) {
        hasRoot = runCatching { EnvironmentChecker.check(forceRefresh = true).hasRoot }.getOrDefault(false)
    }

    val hasNotificationsPermission = remember(permissionRefreshVersion) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    val hasWifiMatchingPermission = remember(permissionRefreshVersion) {
        val hasLocation = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val hasNearby = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        hasLocation && hasNearby
    }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }
    }

    fun requestWifiMatchingPermissions() {
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        requestPermissionsLauncher.launch(perms.toTypedArray())
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun requestRootPermission() {
        if (requestingRoot) return
        scope.launch {
            requestingRoot = true
            val granted = runCatching { EnvironmentChecker.requestRootAccess() }.getOrDefault(false)
            hasRoot = if (granted) {
                true
            } else {
                runCatching { EnvironmentChecker.check(forceRefresh = true).hasRoot }.getOrDefault(false)
            }
            requestingRoot = false
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = c.textPrimary
                )

                OnboardingCard(title = stringResource(R.string.onboarding_usage_title)) {
                    Text(
                        text = stringResource(R.string.onboarding_usage_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary
                    )
                }

                OnboardingCard(title = stringResource(R.string.onboarding_tips_title)) {
                    Text(
                        text = stringResource(R.string.onboarding_tips_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary
                    )
                }

                OnboardingCard(title = stringResource(R.string.onboarding_permissions_title)) {
                    PermissionRow(
                        title = stringResource(R.string.onboarding_perm_root_title),
                        desc = stringResource(R.string.onboarding_perm_root_desc),
                        granted = hasRoot,
                        actionLabel = if (hasRoot) null else stringResource(R.string.onboarding_perm_request),
                        onAction = if (hasRoot) null else ({ requestRootPermission() }),
                        actionEnabled = !requestingRoot
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    PermissionRow(
                        title = stringResource(R.string.onboarding_perm_notifications_title),
                        desc = stringResource(R.string.onboarding_perm_notifications_desc),
                        granted = hasNotificationsPermission,
                        actionLabel = if (hasNotificationsPermission) null else stringResource(R.string.onboarding_perm_request),
                        onAction = if (hasNotificationsPermission) null else ({ requestNotificationPermissionIfNeeded() })
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    PermissionRow(
                        title = stringResource(R.string.onboarding_perm_wifi_title),
                        desc = stringResource(R.string.onboarding_perm_wifi_desc),
                        granted = hasWifiMatchingPermission,
                        actionLabel = if (hasWifiMatchingPermission) null else stringResource(R.string.onboarding_perm_request),
                        onAction = if (hasWifiMatchingPermission) null else ({ requestWifiMatchingPermissions() })
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    PermissionRow(
                        title = stringResource(R.string.onboarding_perm_apps_title),
                        desc = stringResource(R.string.onboarding_perm_apps_desc),
                        granted = null,
                        actionLabel = null,
                        onAction = null
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            border = BorderStroke(1.dp, c.divider.copy(alpha = 0.75f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textSecondary),
                            onClick = { openAppSettings() }
                        ) {
                            Text(text = stringResource(R.string.onboarding_permissions_open_settings))
                        }
                    }
                }

                OnboardingCard(title = stringResource(R.string.onboarding_agreement_title)) {
                    Text(
                        text = stringResource(R.string.onboarding_disclaimer_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textSecondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_tos_accept),
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.textPrimary,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                        Checkbox(
                            checked = tosAccepted,
                            onCheckedChange = { tosAccepted = it }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    enabled = tosAccepted && hasRoot,
                    onClick = { onFinish() }
                ) {
                    Text(text = stringResource(R.string.onboarding_finish))
                }
            }
        }
    }
}

@Composable
private fun OnboardingCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = appColors()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = c.card
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    desc: String,
    granted: Boolean?,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    actionEnabled: Boolean = true
) {
    val c = appColors()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = c.textPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = c.textSecondary
            )
        }
        Spacer(modifier = Modifier.height(0.dp))
        when (granted) {
            true -> {
                Checkbox(
                    checked = true,
                    onCheckedChange = null
                )
            }
            false -> {
                if (actionLabel != null && onAction != null) {
                    OutlinedButton(
                        border = BorderStroke(1.dp, c.divider.copy(alpha = 0.75f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textSecondary),
                        enabled = actionEnabled,
                        onClick = onAction
                    ) {
                        Text(text = actionLabel)
                    }
                } else {
                    Checkbox(
                        checked = false,
                        onCheckedChange = null
                    )
                }
            }
            null -> {
                if (actionLabel != null && onAction != null) {
                    OutlinedButton(
                        border = BorderStroke(1.dp, c.divider.copy(alpha = 0.75f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textSecondary),
                        enabled = actionEnabled,
                        onClick = onAction
                    ) {
                        Text(text = actionLabel)
                    }
                }
            }
        }
    }
}
