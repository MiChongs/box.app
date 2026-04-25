package com.box.app

import android.app.Application
import android.content.res.Configuration
import com.box.app.utils.EditorThemeManager
import com.box.app.utils.ThemeManager
import com.box.app.utils.LanguageManager
import com.box.app.utils.LatencyTargetsManager
import com.box.app.utils.UpdateCheckManager
import com.box.app.utils.UiScaleManager
import com.box.app.utils.buildAppIconImageLoader
import com.box.app.ui.web.WebViewPreloader
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.registry.IThemeSource
import com.topjohnwu.superuser.Shell

class AppApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(12)
        )
        ThemeManager.init(this)
        LanguageManager.init(this)
        LatencyTargetsManager.init(this)
        UiScaleManager.init(this)
        UpdateCheckManager.start(this)
        WebViewPreloader.preload(this)
        EditorThemeManager.init(this)
        initTextMate()
    }

    companion object {
        @Volatile
        lateinit var appContext: Context
            private set
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return buildAppIconImageLoader(context)
    }

    private fun initTextMate() {
        FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(assets))

        val themeRegistry = ThemeRegistry.getInstance()
        fun loadTheme(name: String, dark: Boolean) {
            val path = "textmate/$name.json"
            val src = IThemeSource.fromInputStream(
                FileProviderRegistry.getInstance().tryGetInputStream(path),
                path,
                null
            )
            themeRegistry.loadTheme(ThemeModel(src, name).apply { isDark = dark })
        }
        loadTheme("darcula", true)
        loadTheme("light", false)
        loadTheme("monokai", true)
        loadTheme("github-dark", true)
        loadTheme("one-dark", true)
        loadTheme("solarized-light", false)

        // 注册已下载的外部主题
        EditorThemeManager.registerDownloadedThemes(this)

        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        themeRegistry.setTheme(if (isNightMode) "darcula" else "light")

        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
    }
}
