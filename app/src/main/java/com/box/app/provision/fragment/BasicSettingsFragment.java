package com.box.app.provision.fragment;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.preference.SwitchPreference;

import com.box.app.R;
import com.box.app.provision.compat.AppLanguageHelper;
import com.box.app.utils.ThemeManager;
import com.box.app.utils.ThemeMode;

import fan.preference.DropDownPreference;
import fan.preference.PreferenceFragment;

public class BasicSettingsFragment extends PreferenceFragment {

    private static final String PREF_APP_LANGUAGE = "prefs_key_settings_app_language";
    private static final String PREF_THEME_MODE = "prefs_key_settings_theme_mode";
    private static final String PREF_NOTIFICATION = "prefs_key_settings_notification";
    private static final String PREF_AUTO_START = "prefs_key_settings_auto_start";

    DropDownPreference mLanguagePreference;
    DropDownPreference mThemeModePreference;
    SwitchPreference mNotificationPreference;
    SwitchPreference mAutoStartPreference;

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.provision_basic_settings, rootKey);

        mLanguagePreference = findPreference(PREF_APP_LANGUAGE);
        mThemeModePreference = findPreference(PREF_THEME_MODE);
        mNotificationPreference = findPreference(PREF_NOTIFICATION);
        mAutoStartPreference = findPreference(PREF_AUTO_START);

        if (mLanguagePreference != null) {
            mLanguagePreference.setPersistent(false);
            mLanguagePreference.setEntries(AppLanguageHelper.getLanguageEntries(requireContext()));
            mLanguagePreference.setEntryValues(AppLanguageHelper.getLanguageEntryValues());
        }
        if (mThemeModePreference != null) {
            mThemeModePreference.setPersistent(false);
        }
        if (mNotificationPreference != null) {
            mNotificationPreference.setPersistent(false);
        }
        if (mAutoStartPreference != null) {
            mAutoStartPreference.setPersistent(false);
        }
    }

    @Override
    public void onViewCreated(View view, Bundle bundle) {
        super.onViewCreated(view, bundle);

        // 语言
        int languageIndex = AppLanguageHelper.getCurrentLanguageIndex(requireContext());
        mLanguagePreference = findPreference(PREF_APP_LANGUAGE);
        if (mLanguagePreference != null) {
            mLanguagePreference.setValueIndex(languageIndex);
            mLanguagePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                int index = Integer.parseInt((String) newValue);
                AppLanguageHelper.setIndexLanguage(requireActivity(), index, true);
                return true;
            });
        }

        // 主题模式
        mThemeModePreference = findPreference(PREF_THEME_MODE);
        if (mThemeModePreference != null) {
            ThemeMode current = ThemeManager.INSTANCE.getThemeMode().getValue();
            int themeIndex = switch (current) {
                case LIGHT -> 1;
                case DARK -> 2;
                default -> 0;
            };
            mThemeModePreference.setValueIndex(themeIndex);
            mThemeModePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                int index = Integer.parseInt((String) newValue);
                ThemeMode mode = switch (index) {
                    case 1 -> ThemeMode.LIGHT;
                    case 2 -> ThemeMode.DARK;
                    default -> ThemeMode.SYSTEM;
                };
                ThemeManager.INSTANCE.setThemeMode(requireContext(), mode);
                return true;
            });
        }

        // 通知
        mNotificationPreference = findPreference(PREF_NOTIFICATION);
        if (mNotificationPreference != null) {
            boolean notifEnabled = requireContext()
                .getSharedPreferences("app_settings", 0)
                .getBoolean("notifications_enabled", true);
            mNotificationPreference.setChecked(notifEnabled);
            mNotificationPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = Boolean.TRUE.equals(newValue);
                requireContext().getSharedPreferences("app_settings", 0)
                    .edit().putBoolean("notifications_enabled", enabled).apply();
                return true;
            });
        }

        // 开机自启
        mAutoStartPreference = findPreference(PREF_AUTO_START);
        if (mAutoStartPreference != null) {
            boolean autoStart = requireContext()
                .getSharedPreferences("app_settings", 0)
                .getBoolean("auto_start_service", false);
            mAutoStartPreference.setChecked(autoStart);
            mAutoStartPreference.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = Boolean.TRUE.equals(newValue);
                requireContext().getSharedPreferences("app_settings", 0)
                    .edit().putBoolean("auto_start_service", enabled).apply();
                return true;
            });
        }
    }
}
