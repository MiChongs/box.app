package com.box.app.provision.compat;

import android.content.Context;

import com.box.app.R;
import com.box.app.utils.AppLanguage;
import com.box.app.utils.LanguageManager;

/**
 * OOBE 语言设置桥接层，连接 provision PreferenceFragment 和 LanguageManager。
 */
public class AppLanguageHelper {

    public static Context wrapContext(Context base) {
        return base;
    }

    public static String[] getLanguageEntries(Context context) {
        return new String[]{
            context.getString(R.string.settings_language_follow_system),
            "English",
            "简体中文"
        };
    }

    public static String[] getLanguageEntryValues() {
        return new String[]{"0", "1", "2"};
    }

    public static int getCurrentLanguageIndex(Context context) {
        AppLanguage lang = LanguageManager.INSTANCE.getLanguage().getValue();
        return switch (lang) {
            case ENGLISH -> 1;
            case CHINESE -> 2;
            default -> 0;
        };
    }

    public static void setIndexLanguage(android.app.Activity activity, int index, boolean refresh) {
        AppLanguage lang = switch (index) {
            case 1 -> AppLanguage.ENGLISH;
            case 2 -> AppLanguage.CHINESE;
            default -> AppLanguage.SYSTEM;
        };
        LanguageManager.INSTANCE.setLanguage(activity, lang);
        if (refresh) {
            activity.recreate();
        }
    }

    public static void freezeCurrentLocaleIfUnset(Context context) {
        // No-op
    }
}
