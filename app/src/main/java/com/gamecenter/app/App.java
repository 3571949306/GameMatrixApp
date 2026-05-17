package com.gamecenter.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.gamecenter.app.network.OkHttpClientProvider;
import com.gamecenter.app.update.UpdateManager;

import dagger.hilt.android.HiltAndroidApp;

/**
 * 应用程序全局入口类，负责整个应用生命周期内的全局初始化与状态管理。
 * <p>
 * 核心职责：
 * <ul>
 *   <li>语言设置：根据用户偏好应用应用级语言配置</li>
 *   <li>主题管理：支持浅色/深色/跟随系统三种主题模式，并维护当前深色模式状态</li>
 *   <li>配色方案：在每个 Activity 创建时自动应用用户选择的配色方案</li>
 *   <li>更新检查：提供一次性自动更新检查的门控机制，确保每次应用启动仅自动检查一次</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>使用 Hilt（{@code @HiltAndroidApp}）进行依赖注入，避免手动构建依赖图</li>
 *   <li>OkHttpClient 初始化已迁移至 App Startup 延迟加载，不在 onCreate 中同步执行，以减少启动耗时</li>
 *   <li>通过注册 ActivityLifecycleCallbacks 在每个 Activity 创建时统一应用配色方案，
 *       避免每个 Activity 单独处理</li>
 * </ul>
 */
@HiltAndroidApp
public class App extends Application {

    /** 当前是否处于深色模式，供配色方案判断使用 */
    private boolean isDarkMode = false;

    /** 标记本次应用启动是否已执行过自动更新检查，用于实现"仅检查一次"的语义 */
    private boolean updateAutoCheckDone = false;

    /**
     * 应用程序启动时的入口回调。
     * <p>
     * 执行顺序：语言设置 → 主题设置 → 注册 Activity 生命周期回调。
     * 语言和主题必须尽早应用，以确保首个 Activity 的 UI 正确渲染。
     */
    @Override
    public void onCreate() {
        super.onCreate();
        applyLanguage();
        // 主题设置立即应用（影响 UI）
        applyTheme();
        
        // OkHttpClient 改为 App Startup 延迟初始化
        // 不再在 onCreate 中同步初始化，避免阻塞启动
        
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            /**
             * 每个 Activity 创建时自动应用配色方案。
             * 仅在 onActivityCreated 中处理，因为配色只需在视图创建前设置一次。
             */
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

    /**
     * 判断是否应该执行自动更新检查。
     * <p>
     * 使用"一次性门控"模式：首次调用返回 true 并将标记置为 true，
     * 后续调用均返回 false，确保整个应用生命周期内仅自动检查一次。
     *
     * @return true 表示本次启动尚未执行自动检查，应执行；false 表示已检查过，跳过
     */
    public boolean shouldAutoCheckUpdate() {
        if (updateAutoCheckDone) return false;
        updateAutoCheckDone = true;
        return true;
    }

    /**
     * 根据用户设置应用主题模式（浅色/深色/跟随系统）。
     * <p>
     * 同时更新 {@link #isDarkMode} 字段，供配色方案判断当前是否为深色模式。
     * 跟随系统模式时，通过读取当前 Configuration 的 uiMode 判断系统实际主题。
     */
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
                // 跟随系统：通过系统配置动态判断当前是否为深色模式
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                isDarkMode = (getResources().getConfiguration().uiMode
                        & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                        == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                break;
        }
    }

    /**
     * 根据用户设置应用应用级语言配置。
     * <p>
     * 使用 AppCompatDelegate.setApplicationLocales 实现，兼容 AndroidX 的语言切换机制，
     * 无需重启 Activity 即可生效（API 33+ 自动 per-app language）。
     */
    public void applyLanguage() {
        SettingsManager settings = SettingsManager.getInstance(this);
        AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(settings.getAppLanguage()));
    }

    /**
     * 对指定 Activity 应用用户选择的配色方案。
     * <p>
     * 从 SettingsManager 读取配色方案索引，通过 ColorSchemeManager 获取对应方案并应用。
     * 需要传入当前深色模式状态，因为同一配色方案在深色/浅色模式下可能有不同的颜色值。
     *
     * @param activity 需要应用配色方案的目标 Activity
     */
    private void applyColorScheme(Activity activity) {
        SettingsManager settings = SettingsManager.getInstance(this);
        int schemeIndex = settings.getColorSchemeIndex();
        ColorSchemeManager.Scheme scheme = ColorSchemeManager.getScheme(schemeIndex);
        ColorSchemeManager.applyScheme(activity, scheme, isDarkMode);
    }

    /**
     * 静态工具方法：刷新指定 Activity 的配色方案。
     * <p>
     * 供外部（如设置页面）在用户更改配色方案后调用，立即将新配色应用到当前 Activity，
     * 而无需重启应用。通过检查 Application 实例类型确保安全转型。
     *
     * @param activity 需要刷新配色方案的目标 Activity，若为 null 则不执行任何操作
     */
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
