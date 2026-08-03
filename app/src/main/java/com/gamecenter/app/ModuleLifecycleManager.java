package com.gamecenter.app;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.gamecenter.app.interfaces.IModule;
import com.gamecenter.app.interfaces.IModuleLoader;
import com.gamecenter.app.models.ModuleInfo;
import com.gamecenter.app.moduleloader.ModuleLoaderV2;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 模块生命周期管理器。
 * 
 * 负责管理模块的全生命周期：
 * - 按依赖顺序加载模块
 * - 管理模块生命周期（onLoad → onStart → onStop → onUnload）
 * - 处理模块更新（卸载旧版本 → 加载新版本）
 * 
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-26
 */
public class ModuleLifecycleManager {
    
    private static final String TAG = "ModuleLifecycleManager";

    /**
     * 已安装模块持久化 SharedPreferences 文件名。
     * 与 Kotlin ModuleManager 中的 PREFS_NAME 保持一致（模块安装状态的权威来源）。
     */
    private static final String MODULE_INSTALL_PREFS = "module_manager_prefs";

    /** 已安装模块 ID 集合的 SP key（对齐 ModuleManager.KEY_INSTALLED_MODULES） */
    private static final String KEY_INSTALLED_MODULE_IDS = "installed_modules";

    /** 模块版本号 SP key 前缀（对齐 ModuleManager.KEY_MODULE_VERSION_PREFIX） */
    private static final String KEY_MODULE_VERSION_PREFIX = "module_version_";

    /** 单例实例 */
    private static volatile ModuleLifecycleManager instance;
    
    /** 已加载模块缓存（模块 ID -> IModule） */
    private final Map<String, IModule> loadedModules;
    
    /** 模块加载器 */
    private final ModuleLoaderV2 moduleLoader;
    
    /** 模块依赖关系图 */
    private final ModuleDependencyGraph dependencyGraph;
    
    /** 应用上下文 */
    private final Application application;
    
    /**
     * 获取单例实例（双重检查锁定）。
     * 
     * @param application Android Application
     * @return ModuleLifecycleManager 单例
     */
    @NonNull
    public static ModuleLifecycleManager getInstance(@NonNull Application application) {
        if (instance == null) {
            synchronized (ModuleLifecycleManager.class) {
                if (instance == null) {
                    instance = new ModuleLifecycleManager(application);
                }
            }
        }
        return instance;
    }
    
    /**
     * 私有构造函数。
     */
    private ModuleLifecycleManager(@NonNull Application application) {
        this.application = application;
        this.loadedModules = new HashMap<>();
        this.moduleLoader = ModuleLoaderV2.getInstance(application);
        this.dependencyGraph = new ModuleDependencyGraph();
    }
    
    /**
     * 加载模块（自动处理依赖）。
     * 
     * @param moduleInfo 模块信息
     * @return 加载成功的模块实例
     * @throws IModuleLoader.ModuleLoadException 加载失败
     */
    @NonNull
    public IModule loadModule(@NonNull ModuleInfo moduleInfo) throws IModuleLoader.ModuleLoadException {
        if (moduleInfo == null) {
            throw new IModuleLoader.ModuleLoadException(
                    IModuleLoader.ModuleLoadException.ERROR_MODULE_NOT_FOUND,
                    "模块信息为 null"
            );
        }
        
        String moduleId = moduleInfo.getModuleId();
        
        // 检查是否已加载
        IModule loadedModule = loadedModules.get(moduleId);
        if (loadedModule != null) {
            Log.d(TAG, "模块已加载，直接返回: " + moduleId);
            return loadedModule;
        }
        
        // 获取加载顺序（按依赖拓扑排序）
        List<String> loadOrder;
        try {
            loadOrder = dependencyGraph.getLoadOrder(moduleId);
        } catch (ModuleDependencyGraph.CircularDependencyException e) {
            throw new IModuleLoader.ModuleLoadException(
                    IModuleLoader.ModuleLoadException.ERROR_DEPENDENCY_NOT_MET,
                    "检测到循环依赖: " + e.getMessage()
            );
        }
        
        // 按依赖顺序加载
        IModule targetModule = null;
        for (String depModuleId : loadOrder) {
            if (loadedModules.containsKey(depModuleId)) {
                // 已加载，跳过
                continue;
            }
            
            if (depModuleId.equals(moduleId)) {
                // 加载目标模块
                targetModule = moduleLoader.loadModule(moduleInfo);
                loadedModules.put(depModuleId, targetModule);
                Log.i(TAG, "模块加载成功: " + depModuleId);
            } else {
                // 加载依赖模块（简化：实际需要依赖模块的 ModuleInfo）
                Log.d(TAG, "加载依赖模块: " + depModuleId + " (简化实现)");
            }
        }
        
        if (targetModule == null) {
            throw new IModuleLoader.ModuleLoadException(
                    IModuleLoader.ModuleLoadException.ERROR_DEX_LOAD_FAILED,
                    "模块加载失败: " + moduleId
            );
        }
        
        return targetModule;
    }
    
    /**
     * 卸载模块。
     * 
     * @param moduleId 模块 ID
     * @throws IModuleLoader.ModuleUnloadException 卸载失败
     */
    public void unloadModule(@NonNull String moduleId) throws IModuleLoader.ModuleUnloadException {
        if (moduleId == null || moduleId.isEmpty()) {
            throw new IModuleLoader.ModuleUnloadException(
                    IModuleLoader.ModuleUnloadException.ERROR_MODULE_NOT_LOADED,
                    "模块 ID 为空"
            );
        }
        
        IModule module = loadedModules.get(moduleId);
        if (module == null) {
            throw new IModuleLoader.ModuleUnloadException(
                    IModuleLoader.ModuleUnloadException.ERROR_MODULE_NOT_LOADED,
                    "模块未加载: " + moduleId
            );
        }
        
        try {
            // 调用模块 onUnload 生命周期
            module.onUnload();
            
            // 从加载器卸载
            moduleLoader.unloadModule(moduleId);
            
            // 从缓存移除
            loadedModules.remove(moduleId);
            
            Log.i(TAG, "模块卸载成功: " + moduleId);
            
        } catch (Exception e) {
            throw new IModuleLoader.ModuleUnloadException(
                    IModuleLoader.ModuleUnloadException.ERROR_MODULE_NOT_LOADED,
                    "模块卸载失败: " + e.getMessage(),
                    e
            );
        }
    }
    
    /**
     * 重新加载模块（先卸载再加载）。
     * 
     * @param moduleId 模块 ID
     * @return 重新加载后的模块实例
     * @throws IModuleLoader.ModuleLoadException 加载失败
     */
    @NonNull
    public IModule reloadModule(@NonNull String moduleId) throws IModuleLoader.ModuleLoadException {
        Log.d(TAG, "重新加载模块: " + moduleId);
        
        // 获取旧模块信息（简化实现）
        IModule oldModule = loadedModules.get(moduleId);
        if (oldModule == null) {
            throw new IModuleLoader.ModuleLoadException(
                    IModuleLoader.ModuleLoadException.ERROR_MODULE_NOT_FOUND,
                    "模块未加载: " + moduleId
            );
        }
        
        // 卸载
        try {
            unloadModule(moduleId);
        } catch (IModuleLoader.ModuleUnloadException e) {
            Log.w(TAG, "卸载模块失败（继续重新加载）: " + moduleId, e);
        }
        
        // 重新加载（简化：实际需要 ModuleInfo）
        Log.w(TAG, "重新加载需要 ModuleInfo，简化实现中跳过");
        
        throw new IModuleLoader.ModuleLoadException(
                IModuleLoader.ModuleLoadException.ERROR_MODULE_NOT_FOUND,
                "重新加载需要 ModuleInfo，请使用 loadModule(ModuleInfo) 方法"
        );
    }
    
    /**
     * 启动模块（调用 onStart 生命周期）。
     * 
     * @param moduleId 模块 ID
     * @return 启动成功返回 true，否则返回 false
     */
    public boolean startModule(@NonNull String moduleId) {
        IModule module = loadedModules.get(moduleId);
        if (module == null) {
            Log.e(TAG, "模块未加载: " + moduleId);
            return false;
        }
        
        try {
            module.onStart(application);
            Log.d(TAG, "模块已启动: " + moduleId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "模块启动失败: " + moduleId, e);
            return false;
        }
    }
    
    /**
     * 停止模块（调用 onStop 生命周期）。
     * 
     * @param moduleId 模块 ID
     * @return 停止成功返回 true，否则返回 false
     */
    public boolean stopModule(@NonNull String moduleId) {
        IModule module = loadedModules.get(moduleId);
        if (module == null) {
            Log.e(TAG, "模块未加载: " + moduleId);
            return false;
        }
        
        try {
            module.onStop();
            Log.d(TAG, "模块已停止: " + moduleId);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "模块停止失败: " + moduleId, e);
            return false;
        }
    }
    
    /**
     * 检查模块是否已加载。
     * 
     * @param moduleId 模块 ID
     * @return 已加载返回 true，否则返回 false
     */
    public boolean isModuleLoaded(@NonNull String moduleId) {
        return loadedModules.containsKey(moduleId);
    }
    
    /**
     * 获取所有已加载的模块 ID 列表。
     * 
     * @return 模块 ID 列表
     */
    @NonNull
    public List<String> getLoadedModules() {
        return new ArrayList<>(loadedModules.keySet());
    }
    
    /**
     * 获取指定 ID 的模块实例。
     * 
     * @param moduleId 模块 ID
     * @return 模块实例，未加载返回 null
     */
    @Nullable
    public IModule getModule(@NonNull String moduleId) {
        return loadedModules.get(moduleId);
    }
    
    /**
     * 添加模块依赖关系。
     * 
     * @param moduleId 模块 ID
     * @param dependsOn 依赖的模块 ID 列表
     */
    public void addDependency(@NonNull String moduleId, 
                              @NonNull List<String> dependsOn) {
        dependencyGraph.addDependency(moduleId, dependsOn);
        Log.d(TAG, "依赖关系已添加: " + moduleId + " 依赖于 " + dependsOn);
    }
    
    /**
     * 初始化（应用启动时调用）。
     * 
     * 扫描并加载已安装的模块。
     */
    public void initialize() {
        Log.d(TAG, "开始初始化模块生命周期管理器...");

        // 从持久化存储读取已安装模块列表并逐个加载。
        // 模块安装状态由 Kotlin ModuleManager 写入 SharedPreferences（权威来源）：
        //   - 文件名：module_manager_prefs
        //   - 已安装模块 ID 集合：installed_modules (StringSet)
        //   - 各模块版本号：module_version_<moduleId> (int)
        // 单个模块加载失败不影响其他模块与应用启动。
        int successCount = 0;
        int failCount = 0;
        try {
            SharedPreferences prefs = application.getSharedPreferences(
                    MODULE_INSTALL_PREFS, Context.MODE_PRIVATE);
            Set<String> installedIds = prefs.getStringSet(
                    KEY_INSTALLED_MODULE_IDS, Collections.<String>emptySet());

            if (installedIds == null || installedIds.isEmpty()) {
                Log.i(TAG, "未发现已安装模块，跳过批量加载");
            } else {
                Log.d(TAG, "发现 " + installedIds.size() + " 个已安装模块，开始加载...");
                for (String moduleId : installedIds) {
                    if (moduleId == null || moduleId.isEmpty()) {
                        continue;
                    }
                    try {
                        ModuleInfo moduleInfo = new ModuleInfo();
                        moduleInfo.setModuleId(moduleId);
                        moduleInfo.setModuleName(moduleId);
                        int versionCode = prefs.getInt(
                                KEY_MODULE_VERSION_PREFIX + moduleId, 0);
                        if (versionCode > 0) {
                            moduleInfo.setVersionCode(versionCode);
                        }
                        // loadModule 内部会按依赖顺序加载并记录成功日志，
                        // 此处仅统计计数；失败时抛出 ModuleLoadException。
                        loadModule(moduleInfo);
                        successCount++;
                    } catch (Exception e) {
                        // P0-XXX (ModuleLifecycleManager)：模块 APK 不存在时（典型场景：
                        // 模拟器数据残留 / 模块已被卸载但 SP 记录未清理），
                        // 不计入失败计数，并从 SP 中移除该模块 ID，避免重复失败
                        // 触发 CrashDetector 进入恢复模式。
                        if (isModuleApkMissing(e)) {
                            Log.w(TAG, "模块 APK 不存在，从已安装列表移除: " + moduleId);
                            removeInstalledModule(prefs, moduleId);
                        } else {
                            failCount++;
                            Log.e(TAG, "模块加载失败: " + moduleId, e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "读取已安装模块列表失败，初始化中止", e);
        }

        Log.i(TAG, "模块生命周期管理器初始化完成（成功 " + successCount
                + " 个，失败 " + failCount + " 个）");
    }

    /**
     * 判断异常是否是"模块 APK 不存在"导致的。
     * ModuleLoaderV2 在找不到 APK 时会抛出带有"模块 APK 文件不存在"消息的 ModuleLoadException。
     */
    private static boolean isModuleApkMissing(Throwable t) {
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth < 5) {
            String msg = cur.getMessage();
            if (msg != null && msg.contains("模块 APK 文件不存在")) {
                return true;
            }
            cur = cur.getCause();
            depth++;
        }
        return false;
    }

    /**
     * 从已安装模块 SP 中移除指定 moduleId（包含其版本号条目）。
     * 写回 SP 后本次启动及后续启动都不再尝试加载该模块。
     */
    private void removeInstalledModule(SharedPreferences prefs, String moduleId) {
        try {
            Set<String> current = prefs.getStringSet(
                    KEY_INSTALLED_MODULE_IDS, Collections.<String>emptySet());
            if (current != null && current.contains(moduleId)) {
                // 必须复制为新的 Set，否则 StringSet 跨实例缓存陷阱会导致写不生效
                java.util.LinkedHashSet<String> updated = new java.util.LinkedHashSet<>(current);
                updated.remove(moduleId);
                prefs.edit()
                        .putStringSet(KEY_INSTALLED_MODULE_IDS, updated)
                        .remove(KEY_MODULE_VERSION_PREFIX + moduleId)
                        .apply();
            }
        } catch (Exception e) {
            Log.w(TAG, "从 SP 移除模块失败: " + moduleId, e);
        }
    }
    
    /**
     * 释放所有资源（应用退出时调用）。
     */
    public void release() {
        Log.d(TAG, "开始释放所有模块...");
        
        // 按逆序卸载模块
        List<String> loadedIds = new ArrayList<>(loadedModules.keySet());
        for (int i = loadedIds.size() - 1; i >= 0; i--) {
            String moduleId = loadedIds.get(i);
            try {
                unloadModule(moduleId);
            } catch (Exception e) {
                Log.e(TAG, "释放模块失败: " + moduleId, e);
            }
        }
        
        dependencyGraph.clear();
        
        Log.i(TAG, "所有模块已释放");
    }
}
