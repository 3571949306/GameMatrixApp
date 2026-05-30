package com.gamecenter.app.interfaces;

import androidx.annotation.NonNull;
import com.gamecenter.app.models.ModuleInfo;
import com.gamecenter.app.models.ModuleVersion;
import java.util.List;

/**
 * 模块加载器接口。
 * 
 * 定义模块加载、卸载、状态查询的标准接口。
 * 所有模块加载器实现必须遵循此接口。
 * 
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-26
 */
public interface IModuleLoader {
    
    /**
     * 加载指定 ID 的模块。
     * 
     * @param moduleId 模块 ID
     * @return 加载成功的模块实例
     * @throws ModuleLoadException 加载失败时抛出
     */
    @NonNull
    IModule loadModule(@NonNull String moduleId) throws ModuleLoadException;
    
    /**
     * 卸载指定 ID 的模块。
     * 
     * @param moduleId 模块 ID
     * @throws ModuleUnloadException 卸载失败时抛出
     */
    void unloadModule(@NonNull String moduleId) throws ModuleUnloadException;
    
    /**
     * 重新加载指定 ID 的模块（先卸载再加载）。
     * 
     * @param moduleId 模块 ID
     * @return 重新加载后的模块实例
     * @throws ModuleLoadException 加载失败时抛出
     */
    @NonNull
    IModule reloadModule(@NonNull String moduleId) throws ModuleLoadException;
    
    /**
     * 检查指定 ID 的模块是否已加载。
     * 
     * @param moduleId 模块 ID
     * @return 已加载返回 true，否则返回 false
     */
    boolean isModuleLoaded(@NonNull String moduleId);
    
    /**
     * 获取所有已加载的模块 ID 列表。
     * 
     * @return 已加载模块 ID 列表
     */
    @NonNull
    List<String> getLoadedModules();
    
    /**
     * 获取指定 ID 的模块实例（如果已加载）。
     * 
     * @param moduleId 模块 ID
     * @return 模块实例，未加载返回 null
     */
    IModule getModule(@NonNull String moduleId);
    
    /**
     * 加载指定路径的模块 APK 文件。
     * 
     * @param apkPath 模块 APK 文件路径
     * @param moduleInfo 模块信息
     * @return 加载成功的模块实例
     * @throws ModuleLoadException 加载失败时抛出
     */
    @NonNull
    IModule loadModuleFromFile(@NonNull String apkPath, @NonNull ModuleInfo moduleInfo) 
            throws ModuleLoadException;
    
    /**
     * 检查模块更新。
     * 
     * @param moduleId 模块 ID
     * @return 可用更新版本信息，无更新返回 null
     */
    ModuleVersion checkUpdate(@NonNull String moduleId);
    
    /**
     * 获取模块加载器状态。
     * 
     * @return 状态描述
     */
    @NonNull
    String getStatus();
    
    /**
     * 释放所有资源（应用退出时调用）。
     */
    void release();
    
    // ========== 异常类定义 ==========
    
    /**
     * 模块加载异常。
     */
    class ModuleLoadException extends Exception {
        private final int errorCode;
        
        public ModuleLoadException(int errorCode, @NonNull String message) {
            super(message);
            this.errorCode = errorCode;
        }
        
        public ModuleLoadException(int errorCode, @NonNull String message, @NonNull Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }
        
        public int getErrorCode() {
            return errorCode;
        }
        
        // 错误码常量
        public static final int ERROR_MODULE_NOT_FOUND = 1001;
        public static final int ERROR_SIGNATURE_VERIFY_FAILED = 1002;
        public static final int ERROR_INTEGRITY_CHECK_FAILED = 1003;
        public static final int ERROR_VERSION_INCOMPATIBLE = 1004;
        public static final int ERROR_DEPENDENCY_NOT_MET = 1005;
        public static final int ERROR_DEX_LOAD_FAILED = 1006;
        public static final int ERROR_RESOURCE_LOAD_FAILED = 1007;
    }
    
    /**
     * 模块卸载异常。
     */
    class ModuleUnloadException extends Exception {
        private final int errorCode;
        
        public ModuleUnloadException(int errorCode, @NonNull String message) {
            super(message);
            this.errorCode = errorCode;
        }
        
        public ModuleUnloadException(int errorCode, @NonNull String message, @NonNull Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }
        
        public int getErrorCode() {
            return errorCode;
        }
        
        // 错误码常量
        public static final int ERROR_MODULE_NOT_LOADED = 2001;
        public static final int ERROR_RESOURCE_RELEASE_FAILED = 2002;
        public static final int ERROR_CLASSLOADER_RELEASE_FAILED = 2003;
    }
}
