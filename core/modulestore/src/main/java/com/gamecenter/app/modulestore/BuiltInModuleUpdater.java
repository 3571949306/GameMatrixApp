package com.gamecenter.app.modulestore;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.gamecenter.app.interfaces.IModule;
import com.gamecenter.app.models.ModuleInfo;
import com.gamecenter.app.models.ModuleVersion;
import com.gamecenter.app.models.UpdatePolicy;
import com.gamecenter.app.moduleloader.ModuleLoaderV2;
import com.gamecenter.app.interfaces.IModuleStore.DownloadCallback;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 内置模块更新器。
 * 
 * 负责内置游戏（斗地主、五子棋等）的更新：
 * - 检查内置模块是否有更新
 * - 下载更新包
 * - 应用更新（卸载内置版本 → 加载新版本）
 * - 回退到内置版本
 * 
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-26
 */
public class BuiltInModuleUpdater {
    
    private static final String TAG = "BuiltInModuleUpdater";
    
    /** 单例实例 */
    private static volatile BuiltInModuleUpdater instance;
    
    /** 上下文 */
    private final Context context;
    
    /** 模块加载器 */
    private final ModuleLoaderV2 moduleLoader;
    
    /** 下载管理器 */
    private final ModuleDownloadManager downloadManager;
    
    /** 内置模块信息缓存（模块 ID -> ModuleInfo） */
    private final Map<String, ModuleInfo> builtInModules;
    
    /** 更新策略缓存（模块 ID -> UpdatePolicy） */
    private final Map<String, UpdatePolicy> updatePolicies;
    
    /** 内置模块默认版本号：1（基线版本） */
    private static final int BUILT_IN_VERSION_CODE = 1;
    
    /** 内置模块默认版本名：1.0.0 */
    private static final String BUILT_IN_VERSION_NAME = "1.0.0";
    
    /**
     * 获取单例实例（双重检查锁定）。
     * 
     * @param context Android Context
     * @return BuiltInModuleUpdater 单例
     */
    @NonNull
    public static BuiltInModuleUpdater getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (BuiltInModuleUpdater.class) {
                if (instance == null) {
                    instance = new BuiltInModuleUpdater(context);
                }
            }
        }
        return instance;
    }
    
    /**
     * 私有构造函数。
     */
    private BuiltInModuleUpdater(@NonNull Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.moduleLoader = ModuleLoaderV2.getInstance(this.context);
        this.downloadManager = ModuleDownloadManager.getInstance(this.context);
        this.builtInModules = new HashMap<>();
        this.updatePolicies = new HashMap<>();
    }
    
    /**
     * 注册内置模块。
     * 
     * @param moduleInfo 内置模块信息
     */
    public void registerBuiltInModule(@NonNull ModuleInfo moduleInfo) {
        if (moduleInfo == null) {
            Log.e(TAG, "moduleInfo 为 null");
            return;
        }
        
        String moduleId = moduleInfo.getModuleId();
        builtInModules.put(moduleId, moduleInfo);
        
        // 创建默认更新策略
        UpdatePolicy policy = new UpdatePolicy(moduleId);
        policy.setAutoUpdate(true);
        policy.setAllowPrerelease(false);
        policy.setUpdateChannel("stable");
        updatePolicies.put(moduleId, policy);
        
        Log.d(TAG, "已注册内置模块: " + moduleId);
    }
    
    /**
     * 检查内置模块是否有更新。
     * 
     * 从本地 modules.json 读取最新版本信息。
     * modules.json 应放在 assets/ 或 files/ 目录下。
     * 
     * @param moduleId 模块 ID
     * @return 可用更新版本信息，无更新返回 null
     */
    @Nullable
    public ModuleVersion checkBuiltInUpdate(@NonNull String moduleId) {
        if (moduleId == null || moduleId.isEmpty()) {
            Log.e(TAG, "moduleId 为空");
            return null;
        }
        
        Log.d(TAG, "检查内置模块更新: " + moduleId);
        
        // 从 modules.json 读取最新版本信息
        ModuleVersion latestVersion = readLatestVersionFromJson(moduleId);
        
        if (latestVersion == null) {
            Log.d(TAG, "未找到更新信息: " + moduleId);
            return null;
        }
        
        // 检查是否真的有更新
        ModuleInfo builtInInfo = builtInModules.get(moduleId);
        if (builtInInfo != null) {
            if (latestVersion.getVersionCode() <= builtInInfo.getVersionCode()) {
                Log.d(TAG, "内置模块已是最新版本: " + moduleId);
                return null;
            }
        }
        
        Log.i(TAG, "发现更新: " + moduleId + " -> v" + latestVersion.getVersionName());
        return latestVersion;
    }
    
    /**
     * 从 modules.json 读取模块的最新版本信息。
     * 
     * modules.json 格式：
     * {
     *   "version": 11,
     *   "modules": [
     *     {
     *       "id": "gomoku",
     *       "name": "五子棋",
     *       "versionName": "1.0.0",
     *       "versionCode": 100,
     *       "downloadUrl": "https://...",
     *       ...
     *     }
     *   ]
     * }
     * 
     * @param moduleId 模块 ID
     * @return 最新版本信息，未找到返回 null
     */
    @Nullable
    private ModuleVersion readLatestVersionFromJson(@NonNull String moduleId) {
        try {
            // 1. 尝试从 assets 读取
            InputStream is = null;
            try {
                is = context.getAssets().open("modules.json");
            } catch (Exception e) {
                // assets 中不存在，尝试从 files 目录读取
                File jsonFile = new File(context.getFilesDir(), "modules.json");
                if (jsonFile.exists()) {
                    is = new java.io.FileInputStream(jsonFile);
                }
            }
            
            if (is == null) {
                Log.w(TAG, "modules.json 未找到");
                return null;
            }
            
            // 2. 解析 JSON
            java.util.Scanner scanner = new java.util.Scanner(is, "UTF-8");
            String jsonStr = scanner.useDelimiter("\\A").next();
            scanner.close();
            is.close();
            
            JSONObject root = new JSONObject(jsonStr);
            JSONArray modules = root.optJSONArray("modules");
            
            if (modules == null) {
                Log.w(TAG, "modules.json 格式错误：缺少 modules 数组");
                return null;
            }
            
            // 3. 查找对应模块
            for (int i = 0; i < modules.length(); i++) {
                JSONObject module = modules.getJSONObject(i);
                String id = module.optString("id", "");
                
                if (moduleId.equals(id)) {
                    // 找到模块，解析版本信息
                    ModuleVersion version = new ModuleVersion();
                    version.setVersionName(module.optString("versionName", "1.0.0"));
                    version.setVersionCode(module.optInt("versionCode", 1));
                    version.setChangelog(module.optString("description", ""));
                    version.setDownloadUrl(module.optString("downloadUrl", ""));
                    version.setFileSize(module.optLong("fileSize", 0));
                    version.setSha256(module.optString("sha256", ""));
                    
                    // 发布日期（如果有）
                    // modules.json 中没有 releaseDate 字段，使用当前日期
                    version.setReleaseDate(new java.util.Date());
                    
                    version.setPreRelease(false);
                    version.setMinFrameworkVersion(module.optInt("minAppVersion", 1));
                    
                    return version;
                }
            }
            
            Log.d(TAG, "modules.json 中未找到模块: " + moduleId);
            return null;
            
        } catch (Exception e) {
            Log.e(TAG, "读取 modules.json 失败", e);
            return null;
        }
    }
    
    /**
     * 下载内置模块更新。
     * 
     * @param moduleId 模块 ID
     * @param callback 下载进度回调
     */
    public void downloadUpdate(@NonNull String moduleId, 
                               @NonNull DownloadCallback callback) {
        if (moduleId == null || moduleId.isEmpty()) {
            Log.e(TAG, "moduleId 为空");
            callback.onError(moduleId, 1009, "模块 ID 为空");
            return;
        }
        
        // 检查更新
        ModuleVersion updateVersion = checkBuiltInUpdate(moduleId);
        if (updateVersion == null) {
            Log.w(TAG, "无可用更新: " + moduleId);
            callback.onError(moduleId, 1009, "无可用更新");
            return;
        }
        
        // 创建临时 ModuleInfo 用于下载
        ModuleInfo tempInfo = new ModuleInfo();
        tempInfo.setModuleId(moduleId);
        tempInfo.setModuleName(builtInModules.containsKey(moduleId) 
                ? builtInModules.get(moduleId).getModuleName() 
                : moduleId);
        tempInfo.setVersionName(updateVersion.getVersionName());
        tempInfo.setVersionCode(updateVersion.getVersionCode());
        tempInfo.setType("game");
        tempInfo.setBuiltIn(false); // 下载的是外置版本
        tempInfo.setDownloadUrl(updateVersion.getDownloadUrl());
        tempInfo.setFileSize(updateVersion.getFileSize());
        tempInfo.setSha256(updateVersion.getSha256());
        
        // 开始下载
        Log.d(TAG, "开始下载内置模块更新: " + moduleId + " v" + updateVersion.getVersionName());
        downloadManager.downloadModule(tempInfo, callback);
    }
    
    /**
     * 应用内置模块更新（下载完成后调用）。
     * 
     * @param moduleId 模块 ID
     * @param apkFilePath 下载的 APK 文件路径
     * @return 应用成功返回 true，否则返回 false
     */
    public boolean applyUpdate(@NonNull String moduleId, 
                               @NonNull String apkFilePath) {
        if (moduleId == null || moduleId.isEmpty()) {
            Log.e(TAG, "moduleId 为空");
            return false;
        }
        
        if (apkFilePath == null || apkFilePath.isEmpty()) {
            Log.e(TAG, "apkFilePath 为空");
            return false;
        }
        
        File apkFile = new File(apkFilePath);
        if (!apkFile.exists() || !apkFile.isFile()) {
            Log.e(TAG, "APK 文件不存在: " + apkFilePath);
            return false;
        }
        
        try {
            Log.d(TAG, "开始应用内置模块更新: " + moduleId);
            
            // 1. 卸载内置版本（如果存在）
            if (moduleLoader.isModuleLoaded(moduleId)) {
                Log.d(TAG, "卸载内置版本: " + moduleId);
                moduleLoader.unloadModule(moduleId);
            }
            
            // 2. 安装新版本 APK
            ModuleInstaller installer = ModuleInstaller.getInstance(context);
            ModuleInfo newModuleInfo = new ModuleInfo();
            newModuleInfo.setModuleId(moduleId);
            newModuleInfo.setVersionCode(110); // 应从 APK 解析
            newModuleInfo.setBuiltIn(false);
            
            boolean installSuccess = installer.installModule(apkFile, newModuleInfo);
            
            if (installSuccess) {
                Log.i(TAG, "内置模块更新应用成功: " + moduleId);
                return true;
            } else {
                Log.e(TAG, "安装新版本失败，尝试回退: " + moduleId);
                rollbackToBuiltIn(moduleId);
                return false;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "应用更新失败: " + moduleId, e);
            rollbackToBuiltIn(moduleId);
            return false;
        }
    }
    
    /**
     * 回退到内置版本。
     * 
     * @param moduleId 模块 ID
     * @return 回退成功返回 true，否则返回 false
     */
    public boolean rollbackToBuiltIn(@NonNull String moduleId) {
        if (moduleId == null || moduleId.isEmpty()) {
            Log.e(TAG, "moduleId 为空");
            return false;
        }
        
        try {
            Log.d(TAG, "回退到内置版本: " + moduleId);
            
            // 1. 卸载当前版本（如果是外置）
            if (moduleLoader.isModuleLoaded(moduleId)) {
                moduleLoader.unloadModule(moduleId);
            }
            
            // 2. 删除已下载的更新包
            File modulesDir = new File(context.getFilesDir(), "modules");
            if (modulesDir.exists()) {
                File[] files = modulesDir.listFiles((dir, name) -> 
                        name.startsWith(moduleId) && name.endsWith(".apk"));
                
                if (files != null) {
                    for (File f : files) {
                        if (f.delete()) {
                            Log.d(TAG, "已删除更新包: " + f.getName());
                        }
                    }
                }
            }
            
            // 3. 重新加载内置版本
            ModuleInfo builtInInfo = builtInModules.get(moduleId);
            if (builtInInfo != null) {
                IModule module = moduleLoader.loadModule(builtInInfo);
                if (module != null) {
                    Log.i(TAG, "回退成功，内置版本已加载: " + moduleId);
                    return true;
                }
            }
            
            Log.w(TAG, "内置版本信息未找到，无法回退: " + moduleId);
            return false;
            
        } catch (Exception e) {
            Log.e(TAG, "回退失败: " + moduleId, e);
            return false;
        }
    }
    
    /**
     * 获取内置模块的当前版本信息。
     * 
     * @param moduleId 模块 ID
     * @return 版本信息，未找到返回 null
     */
    @Nullable
    public ModuleInfo getBuiltInModuleInfo(@NonNull String moduleId) {
        return builtInModules.get(moduleId);
    }
    
    /**
     * 获取所有已注册的内置模块 ID 列表。
     * 
     * @return 模块 ID 列表
     */
    @NonNull
    public java.util.List<String> getRegisteredBuiltInModules() {
        return new java.util.ArrayList<>(builtInModules.keySet());
    }
    
    /**
     * 检查指定模块是否已下载更新版本。
     * 
     * @param moduleId 模块 ID
     * @return 已下载返回 true，否则返回 false
     */
    public boolean hasDownloadedUpdate(@NonNull String moduleId) {
        if (moduleId == null || moduleId.isEmpty()) {
            return false;
        }
        
        File modulesDir = new File(context.getFilesDir(), "modules");
        if (!modulesDir.exists()) {
            return false;
        }
        
        // 查找匹配的 APK 文件（非内置版本）
        File[] files = modulesDir.listFiles((dir, name) -> 
                name.startsWith(moduleId) && name.endsWith(".apk"));
        
        return files != null && files.length > 0;
    }
    
    /**
     * 设置模块更新策略。
     * 
     * @param moduleId 模块 ID
     * @param policy 更新策略
     */
    public void setUpdatePolicy(@NonNull String moduleId, 
                                @NonNull UpdatePolicy policy) {
        if (moduleId == null || policy == null) {
            return;
        }
        
        updatePolicies.put(moduleId, policy);
        Log.d(TAG, "更新策略已设置: " + moduleId);
    }
    
    /**
     * 获取模块更新策略。
     * 
     * @param moduleId 模块 ID
     * @return 更新策略，未设置返回默认策略
     */
    @NonNull
    public UpdatePolicy getUpdatePolicy(@NonNull String moduleId) {
        UpdatePolicy policy = updatePolicies.get(moduleId);
        if (policy == null) {
            policy = new UpdatePolicy(moduleId);
            updatePolicies.put(moduleId, policy);
        }
        return policy;
    }
    
    /**
     * 释放所有资源（应用退出时调用）。
     */
    public void release() {
        builtInModules.clear();
        updatePolicies.clear();
        
        if (downloadManager != null) {
            downloadManager.release();
        }
        
        Log.d(TAG, "BuiltInModuleUpdater 已释放所有资源");
    }
}
