package com.gamecenter.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 单例设置管理器 — SharedPreferences 持久化用户偏好（主题模式、配色方案）。
 */
public class SettingsManager {

    private static final String PREF_NAME = "app_settings";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_COLOR_SCHEME = "color_scheme";
    private static final String KEY_AUTO_CHECK_UPDATE = "auto_check_update";
    private static final String KEY_ACCEPT_BETA_UPDATE = "accept_beta_update";
    private static final String KEY_AUTO_DOWNLOAD_UPDATE = "auto_download_update";
    private static final String KEY_PROMPT_INSTALL_AFTER_AUTO_DOWNLOAD = "prompt_install_after_auto_download";
    private static final String KEY_UPDATE_SOURCE = "update_source";
    private static final String KEY_SOUND_ENABLED = "sound_enabled";
    private static final String KEY_VIBRATION_ENABLED = "vibration_enabled";
    private static final String KEY_APP_LANGUAGE = "app_language";

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    public static final int UPDATE_SOURCE_AUTO = 0;
    public static final int UPDATE_SOURCE_VPS_HK = 1;
    public static final int UPDATE_SOURCE_VPS_US = 2;
    public static final int UPDATE_SOURCE_GITHUB = 3;

    public static final String LANGUAGE_SYSTEM = "";
    public static final String LANGUAGE_ZH = "zh";
    public static final String LANGUAGE_EN = "en";

    private static SettingsManager instance;
    private final SharedPreferences prefs;

    private SettingsManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SettingsManager getInstance(Context context) {
        if (instance == null) {
            instance = new SettingsManager(context);
        }
        return instance;
    }

    public int getThemeMode() {
        return prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM);
    }

    public void setThemeMode(int mode) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply();
    }

    public int getColorSchemeIndex() {
        return prefs.getInt(KEY_COLOR_SCHEME, 0);
    }

    public void setColorSchemeIndex(int index) {
        prefs.edit().putInt(KEY_COLOR_SCHEME, index).apply();
    }

    public boolean isAutoCheckUpdate() {
        return prefs.getBoolean(KEY_AUTO_CHECK_UPDATE, true);
    }

    public void setAutoCheckUpdate(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_CHECK_UPDATE, enabled).apply();
    }

    public boolean isAcceptBetaUpdate() {
        return prefs.getBoolean(KEY_ACCEPT_BETA_UPDATE, false);
    }

    public void setAcceptBetaUpdate(boolean enabled) {
        prefs.edit().putBoolean(KEY_ACCEPT_BETA_UPDATE, enabled).apply();
    }

    public boolean isAutoDownloadUpdate() {
        return prefs.getBoolean(KEY_AUTO_DOWNLOAD_UPDATE, false);
    }

    public void setAutoDownloadUpdate(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_DOWNLOAD_UPDATE, enabled).apply();
    }

    public boolean isPromptInstallAfterAutoDownload() {
        return prefs.getBoolean(KEY_PROMPT_INSTALL_AFTER_AUTO_DOWNLOAD, false);
    }

    public void setPromptInstallAfterAutoDownload(boolean enabled) {
        prefs.edit().putBoolean(KEY_PROMPT_INSTALL_AFTER_AUTO_DOWNLOAD, enabled).apply();
    }

    public int getUpdateSource() {
        return prefs.getInt(KEY_UPDATE_SOURCE, UPDATE_SOURCE_AUTO);
    }

    public void setUpdateSource(int source) {
        prefs.edit().putInt(KEY_UPDATE_SOURCE, source).apply();
    }

    public boolean isSoundEnabled() {
        return prefs.getBoolean(KEY_SOUND_ENABLED, true);
    }

    public void setSoundEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply();
    }

    public boolean isVibrationEnabled() {
        return prefs.getBoolean(KEY_VIBRATION_ENABLED, true);
    }

    public void setVibrationEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply();
    }

    public String getAppLanguage() {
        return prefs.getString(KEY_APP_LANGUAGE, LANGUAGE_SYSTEM);
    }

    public void setAppLanguage(String languageTag) {
        if (languageTag == null) {
            languageTag = LANGUAGE_SYSTEM;
        }
        prefs.edit().putString(KEY_APP_LANGUAGE, languageTag).apply();
    }

    public static boolean isDarkMode(Context context) {
        int mode = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }
}
