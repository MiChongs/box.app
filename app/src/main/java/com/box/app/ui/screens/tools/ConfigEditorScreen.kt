package com.box.app.ui.screens.tools

import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.box.app.R
import com.box.app.data.repo.ConfigRepository
import com.box.app.ui.components.ErrorToast
import com.box.app.ui.components.systemNavBarPadding
import com.box.app.ui.miuix.HyperDialog
import com.box.app.ui.miuix.HyperTextField
import com.box.app.ui.theme.AppColors
import com.box.app.ui.theme.appAccentColor
import com.box.app.ui.theme.appColors
import com.box.app.ui.theme.appErrorColor
import com.box.app.utils.EditorThemeManager
import com.box.app.utils.MapleFontManager
import com.box.app.utils.ThemeManager
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup
import java.io.File

// ─── 编辑器文字大小（sp） ──
private const val EDITOR_TEXT_SIZE_SP = 14f
private const val EDITOR_LINE_NUMBER_SIZE_SP = 11f

@Composable
fun ConfigEditorScreen(
    filePath: String,
    onNavVisibilityChange: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    val c = appColors()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = ThemeManager.shouldUseDarkTheme()
    val accent = appAccentColor()
    val accentArgb = accent.toArgb()
    val scheme = MiuixTheme.colorScheme

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // ── Maple 字体 ──
    val mapleFontEnabled by ThemeManager.mapleFontEditor.collectAsState()
    val mapleTypeface = remember(mapleFontEnabled) {
        if (mapleFontEnabled) {
            val fontFile = File(context.filesDir, "maple_font/MapleMono-NF-CN-Regular.ttf")
            if (fontFile.exists()) runCatching { Typeface.createFromFile(fontFile) }.getOrNull()
            else null
        } else null
    }

    // ── 主题选择 ──
    val selectedThemeName by EditorThemeManager.selectedTheme.collectAsState()
    var showThemePopup by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }

    // ── 隐藏导航栏 ──
    LaunchedEffect(Unit) { onNavVisibilityChange(false) }

    // ── 文件状态 ──
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var original by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    ErrorToast(message = error, onConsumed = { error = null })

    // ── 编辑器偏好 ──
    val wordWrapPrefs = remember {
        context.getSharedPreferences("config_editor_prefs", android.content.Context.MODE_PRIVATE)
    }
    val initialWordWrap = remember { wordWrapPrefs.getBoolean("word_wrap", true) }
    var wordWrap by remember { mutableStateOf(initialWordWrap) }

    LaunchedEffect(wordWrap) {
        wordWrapPrefs.edit().putBoolean("word_wrap", wordWrap).apply()
    }

    // ── 搜索状态 ──
    var searchQuery by remember { mutableStateOf("") }
    var selectedMatchIndex by remember { mutableStateOf(0) }
    var searchExpanded by remember { mutableStateOf(false) }
    var lastNonBlankQuery by remember { mutableStateOf<String?>(null) }

    // ── 对话框 ──
    var showConfirmBack by remember { mutableStateOf(false) }

    // ── 编辑器引用 ──
    var editorRef by remember { mutableStateOf<CodeEditor?>(null) }
    var applyTextToken by remember { mutableStateOf(0) }

    // ── 文件名 ──
    val fileName = filePath.substringAfterLast('/')

    fun hasChanges(): Boolean = text != original
    fun handleBack() {
        if (hasChanges()) showConfirmBack = true else onBack()
    }

    // ── 搜索逻辑 ──
    val matches by remember(text, searchQuery) {
        derivedStateOf {
            val q = searchQuery.trim()
            if (q.isEmpty()) return@derivedStateOf emptyList<IntRange>()
            val out = ArrayList<IntRange>()
            var start = 0
            while (start <= text.length) {
                val idx = text.indexOf(q, startIndex = start, ignoreCase = true)
                if (idx < 0) break
                out.add(idx until (idx + q.length))
                start = idx + q.length
            }
            out
        }
    }

    fun indexToLineCol(source: String, index: Int): Pair<Int, Int> {
        var line = 0; var col = 0
        for (i in 0 until index.coerceIn(0, source.length)) {
            if (source[i] == '\n') { line++; col = 0 } else col++
        }
        return line to col
    }

    fun jumpToMatch(targetIndex: Int) {
        val ed = editorRef ?: return
        if (matches.isEmpty()) return
        val range = matches[targetIndex.coerceIn(0, matches.lastIndex)]
        val (sl, sc) = indexToLineCol(text, range.first)
        val (el, ec) = indexToLineCol(text, (range.last + 1).coerceIn(0, text.length))
        runCatching { ed.setSelectionRegion(sl, sc, el, ec, SelectionChangeEvent.CAUSE_SEARCH) }
    }

    fun applySearch() {
        val ed = editorRef ?: return
        if (searchQuery.isBlank()) runCatching { ed.searcher.stopSearch() }
        else runCatching { ed.searcher.search(searchQuery, EditorSearcher.SearchOptions(true, true)) }
    }

    fun clearSearch() {
        searchQuery = ""; selectedMatchIndex = 0; lastNonBlankQuery = null
        runCatching { editorRef?.searcher?.stopSearch() }
    }

    /** 应用编辑器样式（字体、大小、视觉属性） */
    fun applyEditorStyle(editor: CodeEditor) {
        editor.setTextSize(EDITOR_TEXT_SIZE_SP)
        editor.setLineSpacingMultiplier(1.15f)
        // 字体
        val tf = mapleTypeface ?: Typeface.MONOSPACE
        editor.setTypefaceText(tf)
        editor.setTypefaceLineNumber(tf)
        // 视觉属性
        editor.setHighlightCurrentLine(true)
        editor.setLineNumberEnabled(true)
        editor.setCursorAnimationEnabled(true)
        editor.setScrollBarEnabled(true)
        editor.setDividerWidth(
            editor.context.resources.displayMetrics.density * 0.8f
        )
    }

    // ── BackHandler ──
    BackHandler { handleBack() }

    // ── 主题/字体切换时重新应用 ──
    LaunchedEffect(isDark, selectedThemeName, mapleTypeface) {
        editorRef?.let { ed ->
            val themeName = EditorThemeManager.resolveThemeName(isDark)
            applyTextMate(ed, filePath, themeName, c, accentArgb)
            applyEditorStyle(ed)
        }
    }

    // ── 加载文件 ──
    LaunchedEffect(filePath) {
        loading = true; error = null
        ConfigRepository.warmUpShell()
        val res = ConfigRepository.readFile(filePath)
        if (res.error != null) { error = res.error; loading = false }
        else {
            val content = res.data.orEmpty()
            original = content; text = content; applyTextToken += 1; loading = false
        }
    }

    // ── 搜索响应 ──
    LaunchedEffect(matches.size) {
        selectedMatchIndex = if (matches.isEmpty()) 0 else selectedMatchIndex.coerceIn(0, matches.lastIndex)
    }
    LaunchedEffect(searchQuery, matches.size, editorRef) {
        val q = searchQuery.trim()
        if (q.isBlank()) { lastNonBlankQuery = null; return@LaunchedEffect }
        if (matches.isEmpty()) return@LaunchedEffect
        if (lastNonBlankQuery != q) { lastNonBlankQuery = q; selectedMatchIndex = 0 }
    }
    LaunchedEffect(editorRef, searchQuery, text) { applySearch() }
    LaunchedEffect(searchExpanded) {
        if (searchExpanded) { delay(80); runCatching { focusRequester.requestFocus() }; keyboardController?.show() }
        else { keyboardController?.hide(); focusManager.clearFocus(force = true) }
    }

    // ── 未保存确认对话框 ──
    HyperDialog(
        show = showConfirmBack,
        onDismissRequest = { showConfirmBack = false },
        title = stringResource(R.string.config_editor_discard_changes_title),
        summary = stringResource(R.string.config_editor_discard_changes_message),
        confirmText = stringResource(R.string.config_editor_discard),
        onConfirm = { showConfirmBack = false; onBack() },
        dismissText = stringResource(R.string.action_cancel),
        onDismiss = { showConfirmBack = false }
    )

    // ── 主题选择弹窗 ──
    if (showThemePopup) {
        EditorThemePickerDialog(
            isDark = isDark,
            currentTheme = selectedThemeName,
            onSelectTheme = { name ->
                EditorThemeManager.setSelectedTheme(context, name)
                showThemePopup = false
            },
            onDismiss = { showThemePopup = false }
        )
    }

    // ── 主界面 ──
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = fileName,
                navigationIcon = {
                    IconButton(onClick = { handleBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = scheme.onSurface)
                    }
                },
                actions = {
                    // 搜索切换
                    IconButton(onClick = {
                        searchExpanded = !searchExpanded
                        if (!searchExpanded) clearSearch()
                    }) {
                        Icon(Icons.Filled.Search, stringResource(R.string.config_editor_search_hint),
                            tint = if (searchExpanded) scheme.primary else scheme.onSurface)
                    }
                    // 保存
                    val saveEnabled = hasChanges() && !saving && !loading
                    IconButton(
                        onClick = {
                            if (saving || loading || !hasChanges()) return@IconButton
                            saving = true
                            scope.launch {
                                val saveRes = ConfigRepository.writeFile(filePath, text)
                                if (saveRes.error != null) error = saveRes.error else original = text
                                saving = false
                            }
                        },
                        enabled = saveEnabled
                    ) {
                        Icon(Icons.Filled.Save, "Save",
                            tint = if (saveEnabled) scheme.primary else scheme.onSurfaceSecondary)
                    }
                    // 溢出菜单
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(Icons.Filled.MoreVert, null, tint = scheme.onSurface)
                        }
                        EditorOverflowPopup(
                            show = showOverflow,
                            onDismissRequest = { showOverflow = false },
                            wordWrap = wordWrap,
                            onToggleWordWrap = { wordWrap = !wordWrap },
                            onSelectTheme = { showOverflow = false; showThemePopup = true }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // ── 可折叠搜索栏 ──
            AnimatedVisibility(
                visible = searchExpanded,
                enter = fadeIn(tween(140)) + expandVertically(tween(180)),
                exit = fadeOut(tween(110)) + shrinkVertically(tween(160))
            ) {
                val counter = remember(searchQuery, matches, selectedMatchIndex) {
                    if (searchQuery.isBlank()) null
                    else if (matches.isEmpty()) "0/0"
                    else "${selectedMatchIndex + 1}/${matches.size}"
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HyperTextField(
                        value = searchQuery, onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        label = stringResource(R.string.config_editor_search_hint),
                        singleLine = true,
                        trailingIcon = if (searchQuery.isNotBlank()) { {
                            IconButton(onClick = { clearSearch(); runCatching { focusRequester.requestFocus() }; keyboardController?.show() }) {
                                Icon(Icons.Filled.Close, null, tint = scheme.onSurfaceSecondary)
                            }
                        } } else null
                    )
                    if (!counter.isNullOrBlank()) {
                        Text(counter, style = MiuixTheme.textStyles.footnote1, color = scheme.onSurfaceSecondary)
                    }
                    IconButton(
                        onClick = {
                            if (searchQuery.isBlank() || matches.isEmpty()) return@IconButton
                            selectedMatchIndex = if (selectedMatchIndex - 1 < 0) matches.lastIndex else selectedMatchIndex - 1
                            jumpToMatch(selectedMatchIndex)
                        },
                        enabled = searchQuery.isNotBlank()
                    ) { Icon(Icons.Filled.KeyboardArrowUp, "Previous", tint = if (searchQuery.isNotBlank()) scheme.onSurface else scheme.onSurfaceSecondary) }
                    IconButton(
                        onClick = {
                            if (searchQuery.isBlank() || matches.isEmpty()) return@IconButton
                            selectedMatchIndex = (selectedMatchIndex + 1) % matches.size
                            jumpToMatch(selectedMatchIndex)
                        },
                        enabled = searchQuery.isNotBlank()
                    ) { Icon(Icons.Filled.KeyboardArrowDown, "Next", tint = if (searchQuery.isNotBlank()) scheme.onSurface else scheme.onSurfaceSecondary) }
                }
            }

            // ── 编辑器区域 ──
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .systemNavBarPadding()
                    .imePadding()
            ) {
                var lastAppliedToken by remember { mutableStateOf(-1) }
                AndroidView(
                    factory = { ctx ->
                        CodeEditor(ctx).also { editor ->
                            editorRef = editor
                            editor.setWordwrap(wordWrap)
                            editor.setText(text)
                            lastAppliedToken = applyTextToken
                            val themeName = EditorThemeManager.resolveThemeName(isDark)
                            applyTextMate(editor, filePath, themeName, c, accentArgb)
                            applyEditorStyle(editor)
                            editor.subscribeAlways(io.github.rosemoe.sora.event.ContentChangeEvent::class.java) {
                                val cur = editor.text.toString()
                                if (cur != text) text = cur
                            }
                        }
                    },
                    update = { editor ->
                        editorRef = editor
                        editor.setWordwrap(wordWrap)
                        if (!loading && lastAppliedToken != applyTextToken) {
                            editor.setText(text)
                            lastAppliedToken = applyTextToken
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                if (loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            InfiniteProgressIndicator(modifier = Modifier.size(24.dp))
                            Text("Loading file...", style = MiuixTheme.textStyles.body2, color = scheme.onSurfaceSecondary)
                        }
                    }
                }
            }
        }
    }
}

// ─── 溢出菜单 ────────────────────────────────────────────────────────────────

@Composable
private fun EditorOverflowPopup(
    show: Boolean,
    onDismissRequest: () -> Unit,
    wordWrap: Boolean,
    onToggleWordWrap: () -> Unit,
    onSelectTheme: () -> Unit
) {
    val wrapLabel = stringResource(R.string.config_editor_word_wrap) +
        if (wordWrap) " ✓" else ""
    val items = listOf(
        wrapLabel to { onDismissRequest(); onToggleWordWrap() },
        stringResource(R.string.config_editor_theme_select) to { onDismissRequest(); onSelectTheme() }
    )
    WindowListPopup(
        show = show,
        alignment = PopupPositionProvider.Align.TopEnd,
        onDismissRequest = onDismissRequest
    ) {
        ListPopupColumn {
            items.forEachIndexed { index, (label, action) ->
                DropdownImpl(
                    text = label,
                    optionSize = items.size,
                    isSelected = false,
                    onSelectedIndexChange = { action() },
                    index = index
                )
            }
        }
    }
}

// ─── 主题选择对话框 ──────────────────────────────────────────────────────────

@Composable
private fun EditorThemePickerDialog(
    isDark: Boolean,
    currentTheme: String,
    onSelectTheme: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheme = MiuixTheme.colorScheme
    val downloadState by EditorThemeManager.downloadState.collectAsState()

    val available = remember(downloadState) { EditorThemeManager.getAllAvailableThemes() }
    val downloadable = remember(downloadState) { EditorThemeManager.getDownloadableThemes() }

    HyperDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.config_editor_theme_select),
        confirmText = stringResource(R.string.action_cancel),
        onConfirm = onDismiss
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // "自动" 选项
            item {
                BasicComponent(
                    title = stringResource(R.string.config_editor_theme_auto),
                    summary = if (isDark) "Darcula" else "Light",
                    onClick = { onSelectTheme("auto") },
                    titleColor = BasicComponentDefaults.titleColor(
                        color = if (currentTheme == "auto") scheme.primary else scheme.onSurface
                    )
                )
            }
            // 已安装的主题
            items(available) { def ->
                BasicComponent(
                    title = def.displayName,
                    summary = if (def.isDark) "Dark" else "Light",
                    onClick = { onSelectTheme(def.name) },
                    titleColor = BasicComponentDefaults.titleColor(
                        color = if (currentTheme == def.name) scheme.primary else scheme.onSurface
                    )
                )
            }
            // 可下载的主题
            if (downloadable.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.config_editor_theme_downloadable),
                        style = MiuixTheme.textStyles.footnote1,
                        color = scheme.onSurfaceSecondary,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                    )
                }
                items(downloadable) { def ->
                    val isDownloading = downloadState is EditorThemeManager.DownloadState.Downloading &&
                        (downloadState as EditorThemeManager.DownloadState.Downloading).themeName == def.name
                    BasicComponent(
                        title = def.displayName,
                        summary = if (isDownloading) stringResource(R.string.config_editor_theme_downloading)
                            else if (def.isDark) "Dark" else "Light",
                        onClick = {
                            if (!isDownloading) {
                                scope.launch { EditorThemeManager.downloadTheme(context, def) }
                            }
                        },
                        endActions = {
                            if (isDownloading) {
                                InfiniteProgressIndicator(modifier = Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Filled.Download, null,
                                    tint = scheme.onSurfaceSecondary, modifier = Modifier.size(18.dp))
                            }
                        }
                    )
                }
            }
        }
    }
}

// ─── TextMate 语法高亮应用 ────────────────────────────────────────────────────

private fun applyTextMate(
    editor: CodeEditor,
    filePath: String,
    themeName: String,
    c: AppColors,
    primaryArgb: Int
) {
    val fileName = filePath.substringAfterLast('/')
    val ext = filePath.substringAfterLast('.', "").lowercase()

    val scopeName = when {
        ext == "sh" -> "source.shell"
        fileName.startsWith("box.") -> "source.shell"
        fileName.endsWith(".inotify") -> "source.shell"
        fileName == "settings.ini" -> "source.shell"
        fileName.startsWith("start.") -> "source.shell"
        fileName.startsWith("ctr.") -> "source.shell"
        fileName.startsWith("net.") -> "source.shell"
        ext in listOf("ini", "cfg", "conf") -> "source.ini"
        ext in listOf("yaml", "yml") -> "source.yaml"
        ext == "json" -> "source.json"
        else -> null
    }

    runCatching { ThemeRegistry.getInstance().setTheme(themeName) }
    editor.colorScheme = TextMateColorScheme.create(ThemeRegistry.getInstance())
    applyEditorChromeColors(editor, c, primaryArgb)

    scopeName?.let {
        val lang = TextMateLanguage.create(it, true)
        editor.setEditorLanguage(lang)
    }

    editor.invalidate()
}

private fun applyEditorChromeColors(editor: CodeEditor, c: AppColors, primaryArgb: Int) {
    editor.colorScheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, c.pageBg.toArgb())
    editor.colorScheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, c.pageBg.toArgb())
    editor.colorScheme.setColor(EditorColorScheme.LINE_NUMBER, c.textSecondary.toArgb())
    editor.colorScheme.setColor(EditorColorScheme.LINE_DIVIDER, c.divider.toArgb())
    editor.colorScheme.setColor(EditorColorScheme.CURRENT_LINE, c.pageBg.copy(alpha = 0.35f).toArgb())
    editor.colorScheme.setColor(EditorColorScheme.SELECTION_INSERT, primaryArgb)
    editor.colorScheme.setColor(EditorColorScheme.SELECTION_HANDLE, primaryArgb)
    // 搜索高亮背景
    editor.colorScheme.setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, (primaryArgb and 0x00FFFFFF) or 0x66000000)
    // 缩进参考线
    editor.colorScheme.setColor(EditorColorScheme.BLOCK_LINE, c.divider.toArgb())
    editor.colorScheme.setColor(EditorColorScheme.BLOCK_LINE_CURRENT, primaryArgb)
    // 选中文本背景
    editor.colorScheme.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, (primaryArgb and 0x00FFFFFF) or 0x40000000)
}
