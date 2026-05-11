package com.gamecenter.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

import com.gamecenter.app.network.OkHttpClientProvider;
import com.gamecenter.app.update.UpdateManager;

public class App extends Application {

    private boolean isDarkMode = false;
    private boolean updateAutoCheckDone = false;

    @Override
    public void onCreate() {
        super.onCreate();
        applyTheme();
        OkHttpClientProvider.getInstance(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                applyColorScheme(activity);
            }

            @Override
            public void onActivityStarted(@NonNull Activity activity) {}

            @Override
            public void onActivityResumed(@NonNull Activity activity) {}

            @Override
            public void onActivityPaused(@NonNull Activity activity) {}

            @Override
            public void onActivityStopped(@NonNull Activity activity) {}

            @Override
            public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

            @Override
            public void onActivityDestroyed(@NonNull Activity activity) {}
        });
    }

    public boolean shouldAutoCheckUpdate() {
        if (updateAutoCheckDone) return false;
        updateAutoCheckDone = true;
        return true;
    }

    public void applyTheme() {
        SettingsManager settings = SettingsManager.getInstance(this);
        int themeMode = settings.getThemeMode();

        switch (themeMode) {
            case SettingsManager.THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                isDarkMode = false;
                break;
            case SettingsManager.THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                isDarkMode = true;
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                isDarkMode = (getResources().getConfiguration().uiMode
                        & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                        == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                break;
        }
    }

    private void applyColorScheme(Activity activity) {
        SettingsManager settings = SettingsManager.getInstance(this);
        int schemeIndex = settings.getColorSchemeIndex();
        ColorSchemeManager.Scheme scheme = ColorSchemeManager.getScheme(schemeIndex);
        ColorSchemeManager.applyScheme(activity, scheme, isDarkMode);
    }

    public static void refreshColorScheme(Activity activity) {
        if (activity != null && activity.getApplication() instanceof App) {
            App app = (App) activity.getApplication();
            SettingsManager settings = SettingsManager.getInstance(app);
            int schemeIndex = settings.getColorSchemeIndex();
            ColorSchemeManager.Scheme scheme = ColorSchemeManager.getScheme(schemeIndex);
            ColorSchemeManager.applyScheme(activity, scheme, app.isDarkMode);
        }
    }
}