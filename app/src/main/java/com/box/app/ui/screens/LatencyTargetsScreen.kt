package com.box.app.ui.screens

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.box.app.data.repo.HomeRepository
import com.box.app.ui.miuix.HyperButton
import com.box.app.ui.miuix.HyperTextButton
import com.box.app.utils.LatencyTarget
import com.box.app.utils.LatencyTargetsManager
import dev.lackluster.hyperx.ui.preference.EditTextPreference
import dev.lackluster.hyperx.ui.preference.PreferenceGroup
import dev.lackluster.hyperx.ui.preference.ItemPosition
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LatencyTargetsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()

    // 从 Manager 读取当前值作为初始状态
    val currentTargets by LatencyTargetsManager.targets.collectAsState()
    var name1 by remember(currentTargets) { mutableStateOf(currentTargets.getOrNull(0)?.name ?: "") }
    var url1 by remember(currentTargets) { mutableStateOf(currentTargets.getOrNull(0)?.url ?: "") }
    var name2 by remember(currentTargets) { mutableStateOf(currentTargets.getOrNull(1)?.name ?: "") }
    var url2 by remember(currentTargets) { mutableStateOf(currentTargets.getOrNull(1)?.url ?: "") }
    var name3 by remember(currentTargets) { mutableStateOf(currentTargets.getOrNull(2)?.name ?: "") }
    var url3 by remember(currentTargets) { mutableStateOf(currentTargets.getOrNull(2)?.url ?: "") }

    // 预览辅助
    fun hostOf(url: String): String =
        runCatching { Uri.parse(url.trim()).host }.getOrNull().orEmpty()

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.settings_latency_targets_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    // 重置按钮
                    IconButton(onClick = {
                        LatencyTargetsManager.resetToDefaults(context)
                        scope.launch { HomeRepository.refreshLatencyNow() }
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Refresh,
                            contentDescription = stringResource(R.string.settings_latency_reset),
                            tint = MiuixTheme.colorScheme.onBackground
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // 说明文字
            item(key = "subtitle") {
                Text(
                    text = stringResource(R.string.settings_latency_targets_subtitle),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }

            // ── 目标 1 ──
            item(key = "target_1") {
                PreferenceGroup(
                    title = "#1  ${name1.ifBlank { "-" }}",
                    position = ItemPosition.First
                ) {
                    EditTextPreference(
                        title = stringResource(R.string.settings_latency_target_name),
                        text = name1,
                        onTextChange = { name1 = it },
                        dialogTitle = "#1 ${stringResource(R.string.settings_latency_target_name)}",
                        dialogHint = "Baidu"
                    )
                    EditTextPreference(
                        title = stringResource(R.string.settings_latency_target_url),
                        text = url1,
                        onTextChange = { url1 = it },
                        dialogTitle = "#1 ${stringResource(R.string.settings_latency_target_url)}",
                        dialogHint = "https://baidu.com",
                        summary = hostOf(url1)
                    )
                }
            }

            // ── 目标 2 ──
            item(key = "target_2") {
                PreferenceGroup(
                    title = "#2  ${name2.ifBlank { "-" }}",
                    position = ItemPosition.Middle
                ) {
                    EditTextPreference(
                        title = stringResource(R.string.settings_latency_target_name),
                        text = name2,
                        onTextChange = { name2 = it },
                        dialogTitle = "#2 ${stringResource(R.string.settings_latency_target_name)}",
                        dialogHint = "Cloudflare"
                    )
                    EditTextPreference(
                        title = stringResource(R.string.settings_latency_target_url),
                        text = url2,
                        onTextChange = { url2 = it },
                        dialogTitle = "#2 ${stringResource(R.string.settings_latency_target_url)}",
                        dialogHint = "https://cloudflare.com",
                        summary = hostOf(url2)
                    )
                }
            }

            // ── 目标 3 ──
            item(key = "target_3") {
                PreferenceGroup(
                    title = "#3  ${name3.ifBlank { "-" }}",
                    position = ItemPosition.Last
                ) {
                    EditTextPreference(
                        title = stringResource(R.string.settings_latency_target_name),
                        text = name3,
                        onTextChange = { name3 = it },
                        dialogTitle = "#3 ${stringResource(R.string.settings_latency_target_name)}",
                        dialogHint = "Google"
                    )
                    EditTextPreference(
                        title = stringResource(R.string.settings_latency_target_url),
                        text = url3,
                        onTextChange = { url3 = it },
                        dialogTitle = "#3 ${stringResource(R.string.settings_latency_target_url)}",
                        dialogHint = "https://google.com",
                        summary = hostOf(url3)
                    )
                }
            }

            // ── 保存按钮 ──
            item(key = "actions") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HyperTextButton(
                        text = stringResource(R.string.settings_latency_reset),
                        onClick = {
                            LatencyTargetsManager.resetToDefaults(context)
                            scope.launch { HomeRepository.refreshLatencyNow() }
                        },
                        modifier = Modifier.weight(1f)
                    )
                    HyperButton(
                        onClick = {
                            LatencyTargetsManager.setTargets(
                                context,
                                LatencyTarget(name = name1, url = url1),
                                LatencyTarget(name = name2, url = url2),
                                LatencyTarget(name = name3, url = url3)
                            )
                            scope.launch { HomeRepository.refreshLatencyNow() }
                            onBack()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = stringResource(R.string.settings_latency_save))
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}
