package com.box.app.utils

import android.content.Context
import com.box.app.AppApplication
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.eclipse.tm4e.core.registry.IThemeSource
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Sora 编辑器主题管理器
 *
 * - 管理内置主题（bundled in assets/textmate/）
 * - 支持从网络下载 VS Code TextMate 主题
 * - 下载的主题缓存在 filesDir/editor_themes/
 * - 通过 ThemeRegistry 注册，供 ConfigEditorScreen 使用
 */
object EditorThemeManager {

    private const val THEMES_DIR = "editor_themes"
    private const val PREFS_NAME = "editor_theme_prefs"
    private const val KEY_SELECTED_THEME = "selected_theme"

    /** 内置主题定义 */
    data class ThemeDef(
        val name: String,
        val displayName: String,
        val isDark: Boolean,
        val assetPath: String? = null,
        val downloadUrl: String? = null
    )

    // ── 内置主题（已打包在 assets 中） ──
    private val BUNDLED_THEMES = listOf(
        ThemeDef("darcula", "Darcula", isDark = true, assetPath = "textmate/darcula.json"),
        ThemeDef("light", "Light", isDark = false, assetPath = "textmate/light.json"),
        ThemeDef("monokai", "Monokai", isDark = true, assetPath = "textmate/monokai.json"),
        ThemeDef("github-dark", "GitHub Dark", isDark = true, assetPath = "textmate/github-dark.json"),
        ThemeDef("one-dark", "One Dark", isDark = true, assetPath = "textmate/one-dark.json"),
        ThemeDef("solarized-light", "Solarized Light", isDark = false, assetPath = "textmate/solarized-light.json")
    )

    // ── 可下载主题（VS Code TextMate 格式，从 GitHub 获取） ──
    private val DOWNLOADABLE_THEMES = listOf(
        ThemeDef(
            "dracula",
            "Dracula",
            isDark = true,
            downloadUrl = "https://raw.githubusercontent.com/dracula/visual-studio-code/master/src/dracula.yml"
        ),
        ThemeDef(
            "nord",
            "Nord",
            isDark = true,
            downloadUrl = "https://raw.githubusercontent.com/arcticicestudio/nord-visual-studio-code/develop/themes/nord-color-theme.json"
        ),
        ThemeDef(
            "material-darker",
            "Material Darker",
            isDark = true,
            downloadUrl = "https://raw.githubusercontent.com/material-theme/vsc-material-theme/main/themes/Material-Theme-Darker.json"
        ),
        ThemeDef(
            "catppuccin-mocha",
            "Catppuccin Mocha",
            isDark = true,
            downloadUrl = "https://raw.githubusercontent.com/catppuccin/vscode/main/packages/catppuccin-vsc/themes/mocha.json"
        ),
        ThemeDef(
            "catppuccin-latte",
            "Catppuccin Latte",
            isDark = false,
            downloadUrl = "https://raw.githubusercontent.com/catppuccin/vscode/main/packages/catppuccin-vsc/themes/latte.json"
        ),
        ThemeDef(
            "github-light",
            "GitHub Light",
            isDark = false,
            downloadUrl = "https://raw.githubusercontent.com/primer/github-vscode-theme/main/themes/light.json"
        )
    )

    // ── 下载状态 ──
    sealed class DownloadState {
        data object Idle : DownloadState()
        data class Downloading(val themeName: String) : DownloadState()
        data class Success(val themeName: String) : DownloadState()
        data class Error(val themeName: String, val message: String) : DownloadState()
    }

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _selectedTheme = MutableStateFlow("auto")
    val selectedTheme: StateFlow<String> = _selectedTheme.asStateFlow()

    /** 初始化：加载偏好设置 */
    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _selectedTheme.value = prefs.getString(KEY_SELECTED_THEME, "auto") ?: "auto"
    }

    /** 设置当前选择的主题 */
    fun setSelectedTheme(context: Context, themeName: String) {
        _selectedTheme.value = themeName
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SELECTED_THEME, themeName).apply()
    }

    /**
     * 根据当前设置和深色模式解析最终主题名
     * - "auto" → isDark ? "darcula" : "light"
     * - 其他 → 直接返回
     */
    fun resolveThemeName(isDark: Boolean): String {
        val selected = _selectedTheme.value
        if (selected == "auto") {
            return if (isDark) "darcula" else "light"
        }
        // 检查主题是否已注册
        val all = getAllAvailableThemes()
        return if (all.any { it.name == selected }) selected else if (isDark) "darcula" else "light"
    }

    /** 获取所有可用主题（内置 + 已下载） */
    fun getAllAvailableThemes(): List<ThemeDef> {
        val result = BUNDLED_THEMES.toMutableList()
        // 已下载的主题
        for (def in DOWNLOADABLE_THEMES) {
            if (isDownloaded(def.name)) {
                result.add(def)
            }
        }
        return result
    }

    /** 获取可下载但尚未下载的主题 */
    fun getDownloadableThemes(): List<ThemeDef> {
        return DOWNLOADABLE_THEMES.filter { !isDownloaded(it.name) }
    }

    /** 检查主题是否已下载 */
    private fun isDownloaded(themeName: String): Boolean {
        val context = AppApplication.appContext
        val file = File(File(context.filesDir, THEMES_DIR), "$themeName.json")
        return file.exists() && file.length() > 0
    }

    /** 在 App 启动时注册已下载的主题到 ThemeRegistry */
    fun registerDownloadedThemes(context: Context) {
        val themesDir = File(context.filesDir, THEMES_DIR)
        if (!themesDir.exists()) return

        val themeRegistry = ThemeRegistry.getInstance()
        for (def in DOWNLOADABLE_THEMES) {
            val file = File(themesDir, "${def.name}.json")
            if (file.exists() && file.length() > 0) {
                runCatching {
                    val src = IThemeSource.fromInputStream(
                        FileInputStream(file),
                        file.absolutePath,
                        null
                    )
                    themeRegistry.loadTheme(ThemeModel(src, def.name).apply { isDark = def.isDark })
                }
            }
        }
    }

    /** 从网络下载主题并注册 */
    suspend fun downloadTheme(context: Context, themeDef: ThemeDef): Boolean =
        withContext(Dispatchers.IO) {
            if (themeDef.downloadUrl == null) return@withContext false
            _downloadState.value = DownloadState.Downloading(themeDef.name)

            val themesDir = File(context.filesDir, THEMES_DIR).apply { mkdirs() }
            val targetFile = File(themesDir, "${themeDef.name}.json")

            val success = runCatching {
                val conn = URL(themeDef.downloadUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.instanceFollowRedirects = true

                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    conn.disconnect()
                    return@runCatching false
                }

                conn.inputStream.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                conn.disconnect()
                true
            }.getOrDefault(false)

            if (!success || !targetFile.exists()) {
                _downloadState.value = DownloadState.Error(themeDef.name, "Download failed")
                return@withContext false
            }

            // 注册到 ThemeRegistry
            val registered = runCatching {
                val src = IThemeSource.fromInputStream(
                    FileInputStream(targetFile),
                    targetFile.absolutePath,
                    null
                )
                ThemeRegistry.getInstance()
                    .loadTheme(ThemeModel(src, themeDef.name).apply { isDark = themeDef.isDark })
                true
            }.getOrDefault(false)

            if (registered) {
                _downloadState.value = DownloadState.Success(themeDef.name)
            } else {
                targetFile.delete()
                _downloadState.value = DownloadState.Error(themeDef.name, "Invalid theme format")
            }
            registered
        }

    /** 删除已下载的主题 */
    fun deleteDownloadedTheme(context: Context, themeName: String) {
        val file = File(File(context.filesDir, THEMES_DIR), "$themeName.json")
        if (file.exists()) file.delete()
        // 如果当前选中的就是被删除的主题，切回 auto
        if (_selectedTheme.value == themeName) {
            setSelectedTheme(context, "auto")
        }
    }
}
