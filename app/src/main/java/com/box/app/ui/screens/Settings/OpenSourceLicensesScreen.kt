package com.box.app.ui.screens.Settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Description
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.box.app.R
import com.box.app.ui.components.contentPaddingWithNavBars
import com.box.app.utils.ThemeManager
import com.mikepenz.aboutlibraries.Libs
import dev.lackluster.hyperx.ui.layout.HyperXScaffold
import kotlinx.coroutines.flow.distinctUntilChanged
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 开源许可证页（miuix 风格重设计）
 *
 * - HyperXScaffold + 大标题 TopAppBar，blur 跟随全局磨砂设置
 * - 每个第三方库一张 miuix Card，使用 BasicComponent 标准行：
 *   - title  = 库名
 *   - summary = 许可证名 · 版本 · uniqueId（自动隐藏空字段）
 *   - startAction = License 图标
 *   - endActions  = 链接按钮（仅当库提供 website / license URL 时）
 * - 加载态 / 空态各自单卡呈现，与 LogsScreen 等其它页面一致
 */
@Composable
fun OpenSourceLicensesScreen(
    onBack: () -> Unit,
    onNavVisibilityChange: (Boolean) -> Unit,
    enableBackHandler: Boolean = true
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    if (enableBackHandler) {
        BackHandler { onBack() }
    }

    // 滚动方向 → 通知导航栏显隐
    LaunchedEffect(listState) {
        var last = listState.firstVisibleItemIndex * 10_000 + listState.firstVisibleItemScrollOffset
        snapshotFlow {
            listState.firstVisibleItemIndex * 10_000 + listState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect { now ->
                if (now > last) onNavVisibilityChange(false)
                else if (now < last) onNavVisibilityChange(true)
                last = now
            }
    }

    // 异步从 raw 资源加载 aboutlibraries 元数据
    val loadResult by produceState<LicensesLoadResult>(initialValue = LicensesLoadResult.Loading) {
        value = try {
            val candidateNames = listOf(
                "aboutlibraries",
                "aboutlibraries_debug",
                "aboutlibraries_release"
            )
            val resId = candidateNames
                .map { name -> context.resources.getIdentifier(name, "raw", context.packageName) }
                .firstOrNull { it != 0 } ?: 0
            if (resId == 0) {
                LicensesLoadResult.Error("raw not found")
            } else {
                val json = context.resources.openRawResource(resId)
                    .bufferedReader().use { it.readText() }
                val libs = Libs.Builder().withJson(json).build()
                LicensesLoadResult.Success(libs)
            }
        } catch (e: Exception) {
            LicensesLoadResult.Error("load failed: ${e::class.java.simpleName}")
        }
    }

    val items = remember(loadResult) {
        (loadResult as? LicensesLoadResult.Success)?.libs?.libraries
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()
    }

    // TopAppBar 模糊跟随全局设置项
    val blurTopBar = ThemeManager.shouldUseBlurEffects()

    HyperXScaffold(
        modifier = Modifier.imePadding(),
        blurTopBar = blurTopBar,
        topBar = {
            TopAppBar(
                modifier = Modifier.offset(y = (-12).dp),
                title = stringResource(R.string.settings_open_source_licenses),
                largeTitle = stringResource(R.string.settings_open_source_licenses),
                subtitle = if (items.isNotEmpty()) {
                    stringResource(R.string.settings_open_source_licenses_count, items.size)
                } else {
                    stringResource(R.string.settings_open_source_licenses_subtitle)
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack, cornerRadius = 16.dp) {
                        Icon(
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = contentPaddingWithNavBars(
                start = 12.dp,
                end = 12.dp,
                top = innerPadding.calculateTopPadding(),
                extraBottom = 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (val result = loadResult) {
                LicensesLoadResult.Loading -> item(key = "licenses_loading") {
                    LicensesLoadingCard()
                }
                is LicensesLoadResult.Error -> item(key = "licenses_empty") {
                    LicensesEmptyCard(message = result.message)
                }
                is LicensesLoadResult.Success -> {
                    if (items.isEmpty()) {
                        item(key = "licenses_empty") {
                            LicensesEmptyCard(
                                message = stringResource(R.string.settings_open_source_licenses_empty)
                            )
                        }
                    } else {
                        items(items, key = { it.uniqueId }) { lib ->
                            val licenseName = lib.licenses
                                .firstOrNull()?.name?.takeIf { it.isNotBlank() }
                            val url = lib.website ?: lib.licenses.firstOrNull()?.url
                            val summaryParts = buildList {
                                if (!licenseName.isNullOrBlank()) add(licenseName)
                                if (!lib.artifactVersion.isNullOrBlank()) add("v${lib.artifactVersion}")
                                if (lib.uniqueId.isNotBlank()) add(lib.uniqueId)
                            }
                            val summary = summaryParts.joinToString("  ·  ")

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 18.dp,
                                insideMargin = PaddingValues(0.dp)
                            ) {
                                BasicComponent(
                                    title = lib.name,
                                    summary = summary,
                                    startAction = {
                                        Icon(
                                            imageVector = Icons.Filled.Description,
                                            contentDescription = null,
                                            tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    },
                                    endActions = {
                                        if (!url.isNullOrBlank()) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                contentDescription = null,
                                                tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    onClick = if (!url.isNullOrBlank()) {
                                        { uriHandler.openUri(url) }
                                    } else null
                                )
                            }
                        }
                    }
                }
            }

            item(key = "licenses_bottom_spacer") {
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

/** 加载态：圆环进度 + 提示文字 */
@Composable
private fun LicensesLoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 32.dp)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfiniteProgressIndicator(modifier = Modifier.size(24.dp))
            Text(
                text = stringResource(R.string.settings_open_source_licenses_loading),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 空态 / 错误态：单条文字提示 */
@Composable
private fun LicensesEmptyCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 36.dp)
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Description,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceSecondary,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = message,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
    }
}

/** 加载结果状态机 */
private sealed interface LicensesLoadResult {
    data object Loading : LicensesLoadResult
    data class Success(val libs: Libs) : LicensesLoadResult
    data class Error(val message: String) : LicensesLoadResult
}
