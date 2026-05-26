package com.gamecenter.app;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.gamecenter.app.network.OkHttpClientProvider;
import com.gamecenter.app.recovery.CrashDetector;
import com.gamecenter.app.update.UpdateManager;

import dagger.hilt.android.HiltAndroidApp;

/**
 * 应用程序全局入口类，负责整个应用生命周期内的全局初始化与状态管理。
 * <p>
 * 【初学者理解】你可以把 App 类想象成整个应用的"大管家"。
 * 就像一栋大楼需要一位总管理员来处理公共事务一样，App 类负责管理所有页面（Activity）
 * 共享的全局设置，比如语言、主题颜色、深色模式等。每个页面不需要各自处理这些公共事务，
 * 只需要听从"大管家"的统一安排即可。
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
 *   <li>使用 Hilt（{@code @HiltAndroidApp}）进行依赖注入，避免手动构建依赖图
 *       【初学者理解】Hilt 就像一个"自动装配工厂"，你告诉它需要什么零件，它就自动帮你创建好，
 *       不需要你自己 new 对象。@HiltAndroidApp 就是开启这个工厂的开关</li>
 *   <li>OkHttpClient 初始化已迁移至 App Startup 延迟加载，不在 onCreate 中同步执行，以减少启动耗时
 *       【初学者理解】就像开机时不急着打开所有软件，而是等你真正需要用的时候再打开，
 *       这样开机速度更快</li>
 *   <li>通过注册 ActivityLifecycleCallbacks 在每个 Activity 创建时统一应用配色方案，
 *       避免每个 Activity 单独处理
 *       【初学者理解】这就像学校统一发校服，而不是让每个学生自己去买。
 *       ActivityLifecycleCallbacks 是一个"监听器"，当任何页面被创建时都会通知我们</li>
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
     * 【初学者理解】这个方法就像应用开机后的"启动流程清单"。
     * 当用户点击应用图标后，系统会自动调用这个方法，我们在这里依次完成：
     * 设置语言 → 设置主题 → 注册页面监听器。顺序很重要，就像穿衣服要先穿内衣再穿外套。
     * <p>
     * 执行顺序：语言设置 → 主题设置 → 注册 Activity 生命周期回调。
     * 语言和主题必须尽早应用，以确保首个 Activity 的 UI 正确渲染。
     */
    @Override
    public void onCreate() {
        super.onCreate();
        CrashDetector.INSTANCE.markAppStart(this);
        applyLanguage();
        // 主题设置立即应用（影响 UI）
        applyTheme();

        // OkHttpClient 改为 App Startup 延迟初始化
        // 不再在 onCreate 中同步初始化，避免阻塞启动

        // 注册 Activity 生命周期监听器
        // 【初学者理解】这相当于在大门口安排了一个"接待员"，
        // 每当有新的页面（Activity）被创建出来，接待员就会自动给它穿上"配色方案"这件衣服
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            /**
             * 每个 Activity 创建时自动应用配色方案。
             * 仅在 onActivityCreated 中处理，因为配色只需在视图创建前设置一次。
             */
            @Override
            public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
                applyColorScheme(activity);
            }

            // 以下回调方法我们暂时不需要处理，保持空实现即可
            // 【初学者理解】Activity 有多个生命周期阶段（创建→启动→恢复→暂停→停止→销毁），
            // 我们只关心"创建"这个阶段，其他阶段不需要做额外操作

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
     * <p>
     * 【初学者理解】这就像游乐园的"单次入场券"——用一次就作废。
     * 应用启动后第一次问"要不要检查更新？"，回答"要"，然后把券撕掉；
     * 之后再问，券已经没了，就回答"不要"。这样就不会重复检查了。
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
     * <p>
     * 【初学者理解】这个方法就像"灯光开关"——用户可以选择开灯（浅色模式）、
     * 关灯（深色模式）或者自动感应（跟随系统）。选择后，所有页面都会统一切换。
     */
    public void applyTheme() {
        SettingsManager settings = SettingsManager.getInstance(this);
        int themeMode = settings.getThemeMode();

        switch (themeMode) {
            case SettingsManager.THEME_LIGHT:
                // 浅色模式：强制使用日间主题
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                isDarkMode = false;
                break;
            case SettingsManager.THEME_DARK:
                // 深色模式：强制使用夜间主题
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                isDarkMode = true;
                break;
            default:
                // 跟随系统：通过系统配置动态判断当前是否为深色模式
                // 【初学者理解】读取手机系统的"当前是白天还是黑夜"的设置
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
     * <p>
     * 【初学者理解】这个方法就像给整个应用换一种"语言频道"。
     * 用户选择中文，所有文字就显示中文；选择英文，就显示英文。
     * setApplicationLocales 是 Android 提供的官方方法来切换应用语言。
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
     * <p>
     * 【初学者理解】这个方法的工作流程就像"选衣服→穿衣服"：
     * 1. 从设置中读取用户选了第几套配色方案（比如第2套"深海蓝"）
     * 2. 从配色方案仓库中取出这套方案
     * 3. 根据当前是白天还是黑夜，选择对应的颜色版本，应用到页面上
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
     * <p>
     * 【初学者理解】当用户在设置页面换了新的配色方案后，需要让当前页面"立刻换上新衣服"。
     * 这个方法就是做这件事的。它是 static（静态）方法，意味着不需要创建 App 对象就能调用，
     * 任何页面都可以直接用 App.refreshColorScheme(this) 来刷新自己的配色。
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
