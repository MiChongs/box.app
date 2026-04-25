package com.box.app.utils

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppLanguage {
    SYSTEM,
    ENGLISH,
    CHINESE
}

object LanguageManager {
    private const val PREFS_NAME = "language_settings"
    private const val KEY_LANGUAGE = "language"

    private val _language = MutableStateFlow(AppLanguage.SYSTEM)
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_LANGUAGE, AppLanguage.SYSTEM.name)
        val lang = runCatching { AppLanguage.valueOf(saved ?: AppLanguage.SYSTEM.name) }.getOrNull()
            ?: AppLanguage.SYSTEM
        _language.value = lang
        applyLocale(context, lang)
    }

    fun setLanguage(context: Context, lang: AppLanguage) {
        _language.value = lang
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, lang.name).apply()
        applyLocale(context, lang)
    }

    private fun applyLocale(context: Context, lang: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            val locales = when (lang) {
                AppLanguage.SYSTEM -> LocaleList.getEmptyLocaleList()
                AppLanguage.ENGLISH -> LocaleList.forLanguageTags("en")
                AppLanguage.CHINESE -> LocaleList.forLanguageTags("zh-CN")
            }
            localeManager?.applicationLocales = locales
        }
        // Android 12 及以下由 AppTheme 中的 Configuration 处理
    }
}
