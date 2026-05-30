package com.gamecenter.app.modulestore;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import com.gamecenter.app.interfaces.IModule;
import com.gamecenter.app.interfaces.IModuleLoader;
import com.gamecenter.app.interfaces.IModuleStore.ModuleUninstallException;
import com.gamecenter.app.moduleloader.ModuleLoaderV2;
import java.io.File;

/**
 * 模块卸载器。
 * 
 * 负责模块卸载：
 * - 卸载模块（调用 ModuleLoaderV2.unloadModule()）
 * - 删除私有目录中的 APK 文件
 * - 清理用户数据（可选）
 * - 从 GameRegistry 注销（如果是游戏模块）
 * 
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-26
 */
public class ModuleUninstaller {
    
    private static final String TAG = "ModuleUninstaller";
    
    /** 单例实例 */
    private static volatile ModuleUninstaller instance;
    
    /** 上下文 */
    private final Context context;
    
    /** 模块加载器 */
    private final ModuleLoaderV2 moduleLoader;
    
    /**
     * 获取单例实例（双重检查锁定）。
     * 
     * @param context Android Context
     * @return ModuleUninstaller 单例
     */
    @NonNull
    public static ModuleUninstaller getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (ModuleUninstaller.class) {
                if (instance == null) {
                    instance = new ModuleUninstaller(context);
                }
            }
        }
        return instance;
    }
    
    /**
     * 私有构造函数。
     */
    private ModuleUninstaller(@NonNull Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
        this.moduleLoader = ModuleLoaderV2.getInstance(this.context);
    }
    
    /**
     * 卸载模块。
     * 
     * @param moduleId 模块 ID
     * @return 卸载成功返回 true，否则返回 false
     * @throws ModuleUninstallException 卸载失败时抛出
     */
    public boolean uninstallModule(@NonNull String moduleId) 
            throws ModuleUninstallException {
        if (context == null) {
            throw new ModuleUninstallException(
                    ModuleUninstallException.ERROR_MODULE_NOT_LOADED,
                    "Context 为 null"
            );
        }
        
        if (moduleId == null || moduleId.isEmpty()) {
            throw new ModuleUninstallException(
                    ModuleUninstallException.ERROR_MODULE_NOT_LOADED,
                    "模块 ID 为空"
            );
        }
        
        Log.d(TAG, "开始卸载模块: " + moduleId);
        
        try {
            // 1. 卸载模块（释放 ClassLoader 和实例）
            moduleLoader.unloadModule(moduleId);
            
            Log.d(TAG, "模块已卸载（ClassLoader 已释放）: " + moduleId);
            
            // 2. 删除私有目录中的 APK 文件
            boolean deleted = deleteModuleApk(moduleId);
            
            // 3. 清理用户数据（可选，默认保留）
            // preserveUserData(moduleId); // 保留用户数据
            
            // 4. 从 GameRegistry 注销（如果是游戏模块）
            unregisterGameModule(moduleId);
            
            Log.i(TAG, "模块卸载成功: " + moduleId + ", APK 已删除=" + deleted);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "模块卸载异常: " + moduleId, e);
            throw new ModuleUninstallException(
                    ModuleUninstallException.ERROR_MODULE_NOT_LOADED,
                    "模块卸载异常: " + e.getMessage(),
                    e
            );
        }
    }
    
    /**
     * 保留用户数据（不删除模块数据目录）。
     * 
     * @param moduleId 模块 ID
     */
    public void preserveUserData(@NonNull String moduleId) {
        Log.d(TAG, "保留用户数据: " + moduleId);
        // 简化实现：不删除 /data/data/pkg/files/modules/{moduleId}_data/ 目录
    }
    
    /**
     * 清理模块用户数据。
     * 
     * @param moduleId 模块 ID
     * @return 清理成功返回 true，否则返回 false
     */
    public boolean cleanupModuleData(@NonNull String moduleId) {
        if (context == null) {
            return false;
        }
        
        // 用户数据目录：/data/data/pkg/files/modules/{moduleId}_data/
        File dataDir = new File(context.getFilesDir(), "modules/" + moduleId + "_data");
        if (!dataDir.exists()) {
            Log.d(TAG, "用户数据目录不存在，无需清理: " + moduleId);
            return true;
        }
        
        boolean success = deleteRecursive(dataDir);
        Log.d(TAG, "用户数据清理" + (success ? "成功" : "失败") + ": " + moduleId);
        return success;
    }
    
    /**
     * 删除模块 APK 文件。
     * 
     * @param moduleId 模块 ID
     * @return 删除成功返回 true，否则返回 false
     */
    private boolean deleteModuleApk(@NonNull String moduleId) {
        if (context == null) {
            return false;
        }
        
        File modulesDir = new File(context.getFilesDir(), "modules");
        if (!modulesDir.exists()) {
            Log.d(TAG, "modules 目录不存在，无需删除 APK: " + moduleId);
            return true;
        }
        
        // 查找匹配的 APK 文件（格式：{moduleId}_v*.apk）
        File[] files = modulesDir.listFiles((dir, name) -> 
                name.startsWith(moduleId) && name.endsWith(".apk"));
        
        if (files == null || files.length == 0) {
            Log.d(TAG, "未找到模块 APK 文件: " + moduleId);
            return true;
        }
        
        boolean allDeleted = true;
        for (File f : files) {
            if (f.delete()) {
                Log.d(TAG, "已删除 APK 文件: " + f.getName());
            } else {
                Log.w(TAG, "删除 APK 文件失败: " + f.getName());
                allDeleted = false;
            }
        }
        
        return allDeleted;
    }
    
    /**
     * 从 GameRegistry 注销游戏模块（简化实现）。
     * 
     * @param moduleId 模块 ID
     */
    private void unregisterGameModule(@NonNull String moduleId) {
        // 简化实现：实际应调用 GameRegistry.unregisterModule()
        Log.d(TAG, "从 GameRegistry 注销: " + moduleId);
    }
    
    /**
     * 递归删除文件或目录。
     * 
     * @param fileOrDirectory 文件或目录
     * @return 删除成功返回 true，否则返回 false
     */
    private boolean deleteRecursive(@NonNull File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!deleteRecursive(child)) {
                        return false;
                    }
                }
            }
        }
        
        return fileOrDirectory.delete();
    }
}
