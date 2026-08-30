package com.gamecenter.app;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * 应用设置管理器。
 * <p>
 * 负责持久化存储和读取用户的所有偏好设置，包括主题模式、配色方案、
 * 更新策略（自动检查/下载/安装/来源/测试版）、音效与振动开关、应用语言等。
 * </p>
 * <p>
 * 你可以把它想象成一个"记忆本"——用户每次修改设置，都会记在这个本子上，
 * 下次打开应用时再从本子上读取，这样用户的偏好就不会丢失。
 * </p>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>使用 {@link SharedPreferences} 作为底层存储，轻量且适合键值对型配置。
 *       SharedPreferences 就像一个简易的字典，每个设置项都有一个"键"（名字）和"值"（内容）</li>
 *   <li>标注 {@code @Singleton} 以配合 Dagger 依赖注入，确保全局唯一实例。
 *       单例就像一个班级只有一个班主任，不管谁问"班主任是谁"，答案都一样</li>
 *   <li>同时提供构造函数注入和 {@link #getInstance(Context)} 静态工厂方法，
 *       兼容 DI 容器与非 DI 场景的获取需求</li>
 *   <li>所有写操作使用 {@code apply()} 异步提交，避免阻塞主线程。
 *       apply() 就像把作业交给后台批改，不用等结果就能继续做别的事；
 *       而commit() 则是当场批改，必须等批完才能走</li>
 * </ul>
 * </p>
 */
@Singleton
public class SettingsManager {

    /** SharedPreferences 文件名，就像这个"记忆本"的封面标题 */
    private static final String PREF_NAME = "app_settings";
    /** 主题模式键名 */
    private static final String KEY_THEME_MODE = "theme_mode";
    /** 配色方案索引键名 */
    private static final String KEY_COLOR_SCHEME = "color_scheme";
    /** 是否自动检查更新键名 */
    private static final String KEY_AUTO_CHECK_UPDATE = "auto_check_update";
    /** 是否接受测试版更新键名 */
    private static final String KEY_ACCEPT_BETA_UPDATE = "accept_beta_update";
    /** 是否自动下载更新键名 */
    private static final String KEY_AUTO_DOWNLOAD_UPDATE = "auto_download_update";
    /** 自动下载后是否提示安装键名 */
    private static final String KEY_PROMPT_INSTALL_AFTER_AUTO_DOWNLOAD = "prompt_install_after_auto_download";
    /** 更新来源键名 */
    private static final String KEY_UPDATE_SOURCE = "update_source";
    /** 分发架构 v2：自动选择下载源主开关 */
    public static final String KEY_DL_AUTO_SELECT = "dl_auto_select";
    /** 分发架构 v2：移动网络下也自动选择（测速） */
    public static final String KEY_DL_MOBILE_AUTO_SELECT = "dl_mobile_auto_select";
    /** 分发架构 v2：移动测速满 N 次后自动关闭 */
    public static final String KEY_DL_MOBILE_AUTO_DISABLE = "dl_mobile_auto_disable";
    /** 分发架构 v2：移动测速自动关闭的样本数阈值 */
    public static final int DL_MOBILE_SAMPLE_TARGET = 3;
    /** 音效开关键名（游戏音效总开关） */
    private static final String KEY_SOUND_ENABLED = "sound_enabled";
    /** 音效总开关键名（控制所有音频，包括BGM和游戏音效） */
    private static final String KEY_SFX_ENABLED = "sfx_enabled";
    /** 振动开关键名 */
    private static final String KEY_VIBRATION_ENABLED = "vibration_enabled";
    /** 应用语言键名 */
    private static final String KEY_APP_LANGUAGE = "app_language";
    /** 字号偏好键名（Feature B / SETTINGS_ENHANCE） */
    private static final String KEY_FONT_SIZE = "font_size";

    /** 跟随系统主题 */
    public static final int THEME_SYSTEM = 0;
    /** 浅色主题 */
    public static final int THEME_LIGHT = 1;
    /** 深色主题 */
    public static final int THEME_DARK = 2;

    /** 自动选择更新源 */
    public static final int UPDATE_SOURCE_AUTO = 0;
    /** 香港VPS更新源 */
    public static final int UPDATE_SOURCE_VPS_HK = 1;
    /**
     * 美国VPS更新源（已废弃）
     * <p>2026-06-19: 已移除美国 VPS 分发渠道。此常量保留以避免 SharedPreferences 已存储值错位，
     * 但 UI 不再展示该选项。若用户历史选择了此值，{@link #getUpdateSource()} 会自动回退到
     * {@link #UPDATE_SOURCE_AUTO}。</p>
     */
    @Deprecated
    public static final int UPDATE_SOURCE_VPS_US = 2;
    /** GitHub更新源 */
    public static final int UPDATE_SOURCE_GITHUB = 3;

    /** 跟随系统语言 */
    public static final String LANGUAGE_SYSTEM = "";
    /** 中文（使用 zh-CN 与 resConfigs "zh-rCN" 及 locales_config 匹配，确保 Android 13+ per-app language 生效） */
    public static final String LANGUAGE_ZH = "zh-CN";
    /** 英文 */
    public static final String LANGUAGE_EN = "en";

    /** 字号：小（Feature B / SETTINGS_ENHANCE） */
    public static final int FONT_SIZE_SMALL = 0;
    /** 字号：中（默认） */
    public static final int FONT_SIZE_MEDIUM = 1;
    /** 字号：大 */
    public static final int FONT_SIZE_LARGE = 2;

    /**
     * 单例引用，使用 {@code volatile} 保证多线程可见性，
     * 配合 {@link #getInstance(Context)} 中的 synchronized 实现双重检查锁定。
     * <p>
     * volatile 的作用：当一个线程修改了 instance 的值，其他线程能立刻看到最新值。
     * 就像教室里有一块公共黑板，volatile 保证任何人擦改黑板后，
     * 其他同学不用离开座位就能立刻看到最新内容，而不是看到旧的缓存版本。
     * </p>
     */
    private static volatile SettingsManager instance;

    /** 底层 SharedPreferences 实例，就是那个"记忆本"本身 */
    private final SharedPreferences prefs;

    /**
     * 构造函数，由 Dagger Hilt 或 {@link #getInstance(Context)} 调用。
     * <p>
     * 使用 {@code context.getApplicationContext()} 避免 Activity 级别 Context 导致内存泄漏。
     * 可以这样理解：ApplicationContext 是整个应用的"大管家"，只要应用还活着它就存在；
     * 而 Activity 的 Context 只是一个"临时工"，Activity 销毁后它就没了，
     * 如果还拿着它的引用，就会导致 Activity 无法被回收，造成内存泄漏。
     * </p>
     * 标注 {@code @Inject} 使 Hilt 可直接通过构造函数注入创建实例，
     * 配合类级 {@code @Singleton} 注解确保全局唯一。
     *
     * @param context 任意上下文，内部会转换为 Application Context
     */
    @Inject
    public SettingsManager(@ApplicationContext Context context) {
        // ⚠️ 直接使用 context，不要调 getApplicationContext()
        // @ApplicationContext 已经保证了传入的是 Application Context
        // 在 Application 初始化早期阶段，getApplicationContext() 可能返回 null
        // MODE_PRIVATE 表示这个文件只有本应用能读写，其他应用无法访问
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        instance = this;
    }

    /**
     * 获取单例实例（双重检查锁定模式）。
     * <p>
     * 当未通过 Dagger 注入时，可使用此方法获取实例。
     * 首次调用时会创建新实例，后续调用直接返回缓存实例。
     * </p>
     * <p>
     * 双重检查锁定（Double-Checked Locking）的工作方式：
     * 第一次检查（不加锁）：如果实例已存在，直接返回，避免不必要的加锁开销；
     * 加锁：确保只有一个线程能创建实例；
     * 第二次检查（加锁后）：防止多个线程同时通过第一次检查后重复创建实例。
     * </p>
     *
     * @param context 上下文，仅首次调用时使用
     * @return 全局唯一的 SettingsManager 实例
     * @deprecated 推荐通过 Hilt 依赖注入获取实例，避免手动管理单例
     */
    @Deprecated
    public static synchronized SettingsManager getInstance(Context context) {
        if (instance == null) {
            // ⚠️ 直接使用 context，不要调 getApplicationContext()
            // 如果 context 是 Application，它本身就是 ApplicationContext
            // 如果在 Application 初始化早期调用，getApplicationContext() 可能返回 null
            Context appContext = context;
            if (context instanceof Application) {
                appContext = context;
            } else {
                Context ac = context.getApplicationContext();
                if (ac != null) {
                    appContext = ac;
                }
            }
            instance = new SettingsManager(appContext);
        }
        return instance;
    }

    /**
     * 获取当前主题模式。
     *
     * @return 主题模式常量：{@link #THEME_SYSTEM}、{@link #THEME_LIGHT} 或 {@link #THEME_DARK}，
     *         默认为 {@link #THEME_SYSTEM}
     */
    public int getThemeMode() {
        // 第二个参数 THEME_SYSTEM 是默认值：如果"记忆本"上还没记过这个设置，就返回这个默认值
        return prefs.getInt(KEY_THEME_MODE, THEME_SYSTEM);
    }

    /**
     * 设置主题模式。
     *
     * @param mode 主题模式常量：{@link #THEME_SYSTEM}、{@link #THEME_LIGHT} 或 {@link #THEME_DARK}
     */
    public void setThemeMode(int mode) {
        // apply() 是异步保存，不会卡住主线程；如果用 commit() 则是同步保存，会等待写入完成
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply();
    }

    /**
     * 获取当前配色方案索引。
     *
     * @return 配色方案索引，默认为 0（清朗紫）
     */
    public int getColorSchemeIndex() {
        return prefs.getInt(KEY_COLOR_SCHEME, 0);
    }

    /**
     * 设置配色方案索引。
     *
     * @param index 配色方案索引，对应 {@link ColorSchemeManager} 中定义的方案序号
     */
    public void setColorSchemeIndex(int index) {
        prefs.edit().putInt(KEY_COLOR_SCHEME, index).apply();
    }

    /**
     * 获取是否自动检查更新。
     *
     * @return {@code true} 表示自动检查，默认开启
     */
    public boolean isAutoCheckUpdate() {
        return prefs.getBoolean(KEY_AUTO_CHECK_UPDATE, true);
    }

    /**
     * 设置是否自动检查更新。
     *
     * @param enabled {@code true} 开启自动检查，{@code false} 关闭
     */
    public void setAutoCheckUpdate(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_CHECK_UPDATE, enabled).apply();
    }

    /**
     * 获取是否接受测试版更新。
     *
     * @return {@code true} 表示接受测试版，默认关闭（仅接收正式版）
     */
    public boolean isAcceptBetaUpdate() {
        return prefs.getBoolean(KEY_ACCEPT_BETA_UPDATE, false);
    }

    // ===== 分发架构 v2：下载源自动选择设置 =====

    /** 主开关：自动选择下载源。默认开启。 */
    public boolean isDlAutoSelect() {
        return prefs.getBoolean(KEY_DL_AUTO_SELECT, true);
    }

    public void setDlAutoSelect(boolean enabled) {
        prefs.edit().putBoolean(KEY_DL_AUTO_SELECT, enabled).apply();
    }

    /** 移动网络下也自动选择（测速）。默认关闭——移动数据用户流量自担。 */
    public boolean isDlMobileAutoSelect() {
        return prefs.getBoolean(KEY_DL_MOBILE_AUTO_SELECT, false);
    }

    public void setDlMobileAutoSelect(boolean enabled) {
        prefs.edit().putBoolean(KEY_DL_MOBILE_AUTO_SELECT, enabled).apply();
    }

    /** 移动测速满 N 次样本后自动关闭移动测速。默认开启。 */
    public boolean isDlMobileAutoDisable() {
        return prefs.getBoolean(KEY_DL_MOBILE_AUTO_DISABLE, true);
    }

    public void setDlMobileAutoDisable(boolean enabled) {
        prefs.edit().putBoolean(KEY_DL_MOBILE_AUTO_DISABLE, enabled).apply();
    }

    /**
     * 设置是否接受测试版更新。
     *
     * @param enabled {@code true} 接受测试版，{@code false} 仅接收正式版
     */
    public void setAcceptBetaUpdate(boolean enabled) {
        prefs.edit().putBoolean(KEY_ACCEPT_BETA_UPDATE, enabled).apply();
    }

    /**
     * 获取是否自动下载更新包。
     *
     * @return {@code true} 表示自动下载，默认关闭
     */
    public boolean isAutoDownloadUpdate() {
        return prefs.getBoolean(KEY_AUTO_DOWNLOAD_UPDATE, false);
    }

    /**
     * 设置是否自动下载更新包。
     *
     * @param enabled {@code true} 开启自动下载，{@code false} 仅提示不自动下载
     */
    public void setAutoDownloadUpdate(boolean enabled) {
        prefs.edit().putBoolean(KEY_AUTO_DOWNLOAD_UPDATE, enabled).apply();
    }

    /**
     * 获取自动下载完成后是否弹出安装提示。
     * <p>
     * 仅在 {@link #isAutoDownloadUpdate()} 为 {@code true} 时有意义，
     * 用于控制下载完成后是直接静默安装还是提示用户确认。
     * </p>
     *
     * @return {@code true} 表示弹出安装提示，默认关闭
     */
    public boolean isPromptInstallAfterAutoDownload() {
        return prefs.getBoolean(KEY_PROMPT_INSTALL_AFTER_AUTO_DOWNLOAD, false);
    }

    /**
     * 设置自动下载完成后是否弹出安装提示。
     *
     * @param enabled {@code true} 下载后提示安装，{@code false} 不提示
     */
    public void setPromptInstallAfterAutoDownload(boolean enabled) {
        prefs.edit().putBoolean(KEY_PROMPT_INSTALL_AFTER_AUTO_DOWNLOAD, enabled).apply();
    }

    /**
     * 获取更新来源。
     * <p>
     * 2026-06-19: 若用户历史选择了已废弃的 {@link #UPDATE_SOURCE_VPS_US}，
     * 自动回退到 {@link #UPDATE_SOURCE_AUTO}（HK VPS → GitHub 两级分发）。
     * </p>
     *
     * @return 更新来源常量：{@link #UPDATE_SOURCE_AUTO}、{@link #UPDATE_SOURCE_VPS_HK}
     *         或 {@link #UPDATE_SOURCE_GITHUB}，默认为 {@link #UPDATE_SOURCE_AUTO}
     */
    public int getUpdateSource() {
        int source = prefs.getInt(KEY_UPDATE_SOURCE, UPDATE_SOURCE_AUTO);
        // 2026-06-19: 美国 VPS 已下线，回退到自动模式
        if (source == UPDATE_SOURCE_VPS_US) {
            return UPDATE_SOURCE_AUTO;
        }
        return source;
    }

    /**
     * 设置更新来源。
     *
     * @param source 更新来源常量
     */
    public void setUpdateSource(int source) {
        prefs.edit().putInt(KEY_UPDATE_SOURCE, source).apply();
    }

    /**
     * 获取音效是否开启（游戏音效总开关）。
     *
     * @return {@code true} 表示音效开启，默认开启
     */
    public boolean isSoundEnabled() {
        return prefs.getBoolean(KEY_SOUND_ENABLED, true);
    }

    /**
     * 设置音效开关（游戏音效总开关）。
     *
     * @param enabled {@code true} 开启音效，{@code false} 关闭
     */
    public void setSoundEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SOUND_ENABLED, enabled).apply();
    }

    /**
     * 获取音效总开关是否开启（控制所有音频，包括BGM和游戏音效）。
     * <p>
     * 这是最高优先级的音频开关。当此开关关闭时，所有音频（BGM、游戏音效、提示音等）
     * 都不应播放，无论其他子开关如何设置。
     * </p>
     *
     * @return {@code true} 表示音效总开关开启，默认开启
     */
    public boolean isSfxEnabled() {
        return prefs.getBoolean(KEY_SFX_ENABLED, true);
    }

    /**
     * 设置音效总开关。
     * <p>
     * 控制所有音频的播放。关闭后所有音频都不播放。
     * </p>
     *
     * @param enabled {@code true} 开启音效总开关，{@code false} 关闭
     */
    public void setSfxEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SFX_ENABLED, enabled).apply();
    }

    /**
     * 综合判断是否应该播放游戏音效。
     * <p>
     * 只有当音效总开关和游戏音效开关都开启时才返回 true。
     * 供游戏模块调用，避免每个游戏都自己判断两个开关。
     * </p>
     *
     * @return {@code true} 表示应该播放游戏音效
     */
    public boolean shouldPlayGameSound() {
        return isSfxEnabled() && isSoundEnabled();
    }

    /**
     * 获取振动是否开启。
     *
     * @return {@code true} 表示振动开启，默认开启
     */
    public boolean isVibrationEnabled() {
        return prefs.getBoolean(KEY_VIBRATION_ENABLED, true);
    }

    /**
     * 设置振动开关。
     *
     * @param enabled {@code true} 开启振动，{@code false} 关闭
     */
    public void setVibrationEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, enabled).apply();
    }

    /**
     * 综合判断是否应该执行振动反馈。
     * <p>
     * 只有当音效总开关和振动开关都开启时才返回 true。
     * 供游戏模块调用，避免每个游戏都自己判断两个开关。
     * </p>
     *
     * @return {@code true} 表示应该执行振动
     */
    public boolean shouldVibrate() {
        return isSfxEnabled() && isVibrationEnabled();
    }

    /**
     * 获取应用语言标签。
     *
     * @return 语言标签字符串，如 {@link #LANGUAGE_ZH}、{@link #LANGUAGE_EN}，
     *         或 {@link #LANGUAGE_SYSTEM}（空字符串）表示跟随系统，默认跟随系统
     */
    public String getAppLanguage() {
        String lang = prefs.getString(KEY_APP_LANGUAGE, LANGUAGE_SYSTEM);
        // 兼容历史值：旧版本 LANGUAGE_ZH = "zh"，现版本 = "zh-CN"
        // 统一迁移为 "zh-CN" 以匹配 resConfigs "zh-rCN" 和 locales_config
        if ("zh".equals(lang)) {
            prefs.edit().putString(KEY_APP_LANGUAGE, LANGUAGE_ZH).apply();
            return LANGUAGE_ZH;
        }
        return lang;
    }

    /**
     * 设置应用语言。
     * <p>
     * 传入 {@code null} 时自动降级为 {@link #LANGUAGE_SYSTEM}（跟随系统），
     * 防止空值导致 SharedPreferences 或后续语言切换逻辑异常。
     * </p>
     *
     * @param languageTag 语言标签，如 "zh"、"en"，传 {@code null} 等同于 {@link #LANGUAGE_SYSTEM}
     */
    public void setAppLanguage(String languageTag) {
        // 防御性编程：如果传入了 null，就用空字符串代替，避免后续代码出错
        if (languageTag == null) {
            languageTag = LANGUAGE_SYSTEM;
        }
        prefs.edit().putString(KEY_APP_LANGUAGE, languageTag).apply();
    }

    // ==================== Feature B: 字号偏好 (SETTINGS_ENHANCE) ====================

    /**
     * 获取字号偏好。
     *
     * @return 字号常量：{@link #FONT_SIZE_SMALL}、{@link #FONT_SIZE_MEDIUM} 或 {@link #FONT_SIZE_LARGE}，
     *         默认为 {@link #FONT_SIZE_MEDIUM}
     */
    public int getFontSize() {
        int v = prefs.getInt(KEY_FONT_SIZE, FONT_SIZE_MEDIUM);
        if (v < FONT_SIZE_SMALL || v > FONT_SIZE_LARGE) {
            return FONT_SIZE_MEDIUM;
        }
        return v;
    }

    /**
     * 设置字号偏好。
     *
     * @param fontSize 字号常量
     */
    public void setFontSize(int fontSize) {
        if (fontSize < FONT_SIZE_SMALL || fontSize > FONT_SIZE_LARGE) {
            fontSize = FONT_SIZE_MEDIUM;
        }
        prefs.edit().putInt(KEY_FONT_SIZE, fontSize).apply();
    }

    /**
     * 获取字号对应的 fontScale 值，供 Activity/Application 应用到 Configuration。
     *
     * @return 0.85 / 1.0 / 1.15
     */
    public float getFontScale() {
        switch (getFontSize()) {
            case FONT_SIZE_SMALL: return 0.85f;
            case FONT_SIZE_LARGE: return 1.15f;
            case FONT_SIZE_MEDIUM:
            default: return 1.0f;
        }
    }

    /**
     * 判断当前系统是否处于深色模式。
     * <p>
     * 通过读取系统 {@code uiMode} 配置中 {@code UI_MODE_NIGHT_MASK} 位来判断，
     * 不依赖本应用的设置项，反映的是系统级别的深色模式状态。
     * </p>
     * <p>
     * 这就像去问手机系统"你现在是深色模式吗？"，而不是看用户在本应用里选了什么主题。
     * </p>
     *
     * @param context 用于获取系统资源配置的上下文
     * @return {@code true} 表示系统当前为深色模式
     */
    public static boolean isDarkMode(Context context) {
        // & 是按位与运算，用来提取 uiMode 中"夜间模式"那几位的信息
        // 类似于用筛子只筛出你关心的那部分数据
        int mode = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }
}
