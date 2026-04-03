package com.box.app

import android.app.Application
import android.content.res.Configuration
import com.box.app.utils.ThemeManager
import com.box.app.utils.LanguageManager
import com.box.app.utils.LatencyTargetsManager
import com.box.app.utils.UpdateCheckManager
import com.box.app.utils.UiScaleManager
import com.box.app.utils.buildAppIconImageLoader
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import org.eclipse.tm4e.core.registry.IThemeSource

class AppApplication : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        ThemeManager.init(this)
        LanguageManager.init(this)
        LatencyTargetsManager.init(this)
        UiScaleManager.init(this)
        UpdateCheckManager.start(this)
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

        val isNightMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        themeRegistry.setTheme(if (isNightMode) "darcula" else "light")

        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
    }
}
