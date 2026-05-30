package com.gamecenter.app;

import android.util.Log;
import androidx.annotation.NonNull;
import com.gamecenter.app.models.ModuleInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 框架配置（模块列表、权限等）。
 * 
 * 集中管理框架级配置：
 * - 内置模块列表
 * - 模块权限配置
 * - 框架特性开关
 * 
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-26
 */
public class FrameworkConfig {
    
    private static final String TAG = "FrameworkConfig";
    
    /** 框架版本名称 */
    public static final String FRAMEWORK_VERSION_NAME = "1.4.0";
    
    /** 框架版本号 */
    public static final int FRAMEWORK_VERSION_CODE = 343;
    
    /** 框架包名 */
    public static final String FRAMEWORK_PACKAGE = "com.gamecenter.app";
    
    /** 框架 APK 体积限制（字节）：15MB */
    public static final long FRAMEWORK_APK_SIZE_LIMIT = 15 * 1024 * 1024L;
    
    /** 内置模块列表（静态配置） */
    private static final List<ModuleInfo> BUILT_IN_MODULES;
    
    /** 模块权限配置（模块 ID -> 所需权限列表） */
    private static final Map<String, List<String>> MODULE_PERMISSIONS;
    
    /** 框架特性开关 */
    private static final Map<String, Boolean> FEATURE_TOGGLES;
    
    // 静态初始化块
    static {
        // 初始化内置模块列表
        BUILT_IN_MODULES = new ArrayList<>();
        
        // 斗地主（内置）
        ModuleInfo doudizhu = new ModuleInfo("game_doudizhu", "斗地主", "1.0.0", 1, "game");
        doudizhu.setBuiltIn(true);
        doudizhu.setDescription("经典斗地主棋牌游戏");
        doudizhu.setStoreCategory("game");
        doudizhu.setMinFrameworkVersion(1);
        BUILT_IN_MODULES.add(doudizhu);
        
        // 五子棋（内置）
        ModuleInfo gomoku = new ModuleInfo("game_gomoku", "五子棋", "1.0.0", 1, "game");
        gomoku.setBuiltIn(true);
        gomoku.setDescription("经典五子棋对战游戏");
        gomoku.setStoreCategory("game");
        gomoku.setMinFrameworkVersion(1);
        BUILT_IN_MODULES.add(gomoku);
        
        // 初始化模块权限配置
        MODULE_PERMISSIONS = new HashMap<>();
        MODULE_PERMISSIONS.put("game_doudizhu", new ArrayList<>());
        MODULE_PERMISSIONS.put("game_gomoku", new ArrayList<>());
        MODULE_PERMISSIONS.put("online_core", new ArrayList<>());
        
        // 初始化框架特性开关
        FEATURE_TOGGLES = new HashMap<>();
        FEATURE_TOGGLES.put("module_hot_reload", false); // 热更新（开发模式）
        FEATURE_TOGGLES.put("module_auto_update", true); // 自动更新
        FEATURE_TOGGLES.put("online_enabled", true); // 联机功能
        FEATURE_TOGGLES.put("analytics_enabled", false); // 数据分析
        FEATURE_TOGGLES.put("crash_report_enabled", true); // 崩溃报告
    }
    
    /**
     * 获取所有内置模块信息。
     * 
     * @return 内置模块信息列表（不可修改）
     */
    @NonNull
    public static List<ModuleInfo> getBuiltInModules() {
        return new ArrayList<>(BUILT_IN_MODULES);
    }
    
    /**
     * 根据模块 ID 获取内置模块信息。
     * 
     * @param moduleId 模块 ID
     * @return 模块信息，未找到返回 null
     */
    @NonNull
    public static ModuleInfo getBuiltInModule(@NonNull String moduleId) {
        for (ModuleInfo info : BUILT_IN_MODULES) {
            if (info.getModuleId().equals(moduleId)) {
                return info;
            }
        }
        return null;
    }
    
    /**
     * 检查指定模块是否为内置模块。
     * 
     * @param moduleId 模块 ID
     * @return 是内置模块返回 true，否则返回 false
     */
    public static boolean isBuiltInModule(@NonNull String moduleId) {
        return getBuiltInModule(moduleId) != null;
    }
    
    /**
     * 获取指定模块所需的权限列表。
     * 
     * @param moduleId 模块 ID
     * @return 权限列表（不可修改，无权限返回空列表）
     */
    @NonNull
    public static List<String> getModulePermissions(@NonNull String moduleId) {
        List<String> permissions = MODULE_PERMISSIONS.get(moduleId);
        return permissions != null ? new ArrayList<>(permissions) : new ArrayList<>();
    }
    
    /**
     * 检查指定模块是否有指定权限。
     * 
     * @param moduleId  模块 ID
     * @param permission 权限名称
     * @return 有权限返回 true，否则返回 false
     */
    public static boolean hasPermission(@NonNull String moduleId, 
                                       @NonNull String permission) {
        List<String> permissions = MODULE_PERMISSIONS.get(moduleId);
        return permissions != null && permissions.contains(permission);
    }
    
    /**
     * 检查框架特性是否启用。
     * 
     * @param featureName 特性名称
     * @return 启用返回 true，否则返回 false
     */
    public static boolean isFeatureEnabled(@NonNull String featureName) {
        Boolean enabled = FEATURE_TOGGLES.get(featureName);
        return enabled != null && enabled;
    }
    
    /**
     * 设置框架特性开关。
     * 
     * @param featureName 特性名称
     * @param enabled     是否启用
     */
    public static void setFeatureEnabled(@NonNull String featureName, 
                                          boolean enabled) {
        FEATURE_TOGGLES.put(featureName, enabled);
        Log.d(TAG, "特性开关已设置: " + featureName + " = " + enabled);
    }
    
    /**
     * 获取框架 APK 体积限制。
     * 
     * @return 体积限制（字节）
     */
    public static long getFrameworkApkSizeLimit() {
        return FRAMEWORK_APK_SIZE_LIMIT;
    }
    
    /**
     * 获取框架版本信息。
     * 
     * @return 版本信息字符串
     */
    @NonNull
    public static String getFrameworkVersionInfo() {
        return FRAMEWORK_VERSION_NAME + " (versionCode=" + FRAMEWORK_VERSION_CODE + ")";
    }
    
    /**
     * 检查模块是否兼容当前框架版本。
     * 
     * @param minFrameworkVersion 模块要求的最低框架版本号
     * @return 兼容返回 true，否则返回 false
     */
    public static boolean isCompatibleWithFramework(int minFrameworkVersion) {
        return FRAMEWORK_VERSION_CODE >= minFrameworkVersion;
    }
    
    /**
     * 获取所有已注册的特性名称。
     * 
     * @return 特性名称列表
     */
    @NonNull
    public static List<String> getRegisteredFeatures() {
        return new ArrayList<>(FEATURE_TOGGLES.keySet());
    }
    
    /**
     * 获取所有已配置的模块 ID。
     * 
     * @return 模块 ID 列表
     */
    @NonNull
    public static List<String> getRegisteredModuleIds() {
        return new ArrayList<>(MODULE_PERMISSIONS.keySet());
    }
}
