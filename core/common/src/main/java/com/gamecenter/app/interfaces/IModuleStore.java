package com.gamecenter.app.interfaces;

import androidx.annotation.NonNull;
import com.gamecenter.app.models.ModuleInfo;
import com.gamecenter.app.models.ModuleVersion;
import java.util.List;

/**
 * 模块商店接口。
 * 
 * 定义模块商店的核心功能：获取模块列表、下载、安装、卸载。
 * 所有模块商店实现必须遵循此接口。
 * 
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-26
 */
public interface IModuleStore {
    
    /**
     * 获取所有可用模块列表。
     * 
     * @return 可用模块信息列表
     */
    @NonNull
    List<ModuleInfo> getAvailableModules();
    
    /**
     * 根据模块 ID 获取模块信息。
     * 
     * @param moduleId 模块 ID
     * @return 模块信息，未找到返回 null
     */
    ModuleInfo getModuleInfo(@NonNull String moduleId);
    
    /**
     * 下载指定模块。
     * 
     * @param moduleId 模块 ID
     * @param callback 下载进度回调
     */
    void downloadModule(@NonNull String moduleId, @NonNull DownloadCallback callback);
    
    /**
     * 安装指定模块。
     * 
     * @param moduleId 模块 ID
     * @throws ModuleInstallException 安装失败时抛出
     */
    void installModule(@NonNull String moduleId) throws ModuleInstallException;
    
    /**
     * 卸载指定模块。
     * 
     * @param moduleId 模块 ID
     * @throws ModuleUninstallException 卸载失败时抛出
     */
    void uninstallModule(@NonNull String moduleId) throws ModuleUninstallException;
    
    /**
     * 检查指定模块是否有可用更新。
     * 
     * @param moduleId 模块 ID
     * @return 有更新返回 true，否则返回 false
     */
    boolean checkUpdate(@NonNull String moduleId);
    
    /**
     * 获取指定模块的可用版本列表。
     * 
     * @param moduleId 模块 ID
     * @return 版本信息列表
     */
    @NonNull
    List<ModuleVersion> getAvailableVersions(@NonNull String moduleId);
    
    /**
     * 根据分类获取模块列表。
     * 
     * @param category 分类名称（game、tool、nav 等）
     * @return 该分类下的模块信息列表
     */
    @NonNull
    List<ModuleInfo> getModulesByCategory(@NonNull String category);
    
    /**
     * 搜索模块。
     * 
     * @param query 搜索关键词
     * @return 匹配的模块信息列表
     */
    @NonNull
    List<ModuleInfo> searchModules(@NonNull String query);
    
    /**
     * 刷新模块列表（从服务器重新获取）。
     * 
     * @param callback 刷新结果回调
     */
    void refreshModules(@NonNull RefreshCallback callback);
    
    // ========== 回调接口定义 ==========
    
    /**
     * 下载进度回调接口。
     */
    interface DownloadCallback {
        /**
         * 下载进度更新。
         * 
         * @param moduleId 模块 ID
         * @param progress 进度（0-100）
         * @param downloadedBytes 已下载字节数
         * @param totalBytes 总字节数
         */
        void onProgress(@NonNull String moduleId, int progress, 
                       long downloadedBytes, long totalBytes);
        
        /**
         * 下载成功。
         * 
         * @param moduleId 模块 ID
         * @param filePath 下载的文件路径
         */
        void onSuccess(@NonNull String moduleId, @NonNull String filePath);
        
        /**
         * 下载失败。
         * 
         * @param moduleId 模块 ID
         * @param errorCode 错误码
         * @param errorMessage 错误信息
         */
        void onError(@NonNull String moduleId, int errorCode, 
                     @NonNull String errorMessage);
        
        /**
         * 下载暂停。
         * 
         * @param moduleId 模块 ID
         */
        void onPaused(@NonNull String moduleId);
        
        /**
         * 下载恢复。
         * 
         * @param moduleId 模块 ID
         */
        void onResumed(@NonNull String moduleId);
        
        /**
         * 下载取消。
         * 
         * @param moduleId 模块 ID
         */
        void onCancelled(@NonNull String moduleId);
    }
    
    /**
     * 刷新结果回调接口。
     */
    interface RefreshCallback {
        /**
         * 刷新成功。
         * 
         * @param modules 刷新后的模块列表
         */
        void onSuccess(@NonNull List<ModuleInfo> modules);
        
        /**
         * 刷新失败。
         * 
         * @param errorCode 错误码
         * @param errorMessage 错误信息
         */
        void onError(int errorCode, @NonNull String errorMessage);
    }
    
    // ========== 异常类定义 ==========
    
    /**
     * 模块安装异常。
     */
    class ModuleInstallException extends Exception {
        private final int errorCode;
        
        public ModuleInstallException(int errorCode, @NonNull String message) {
            super(message);
            this.errorCode = errorCode;
        }
        
        public ModuleInstallException(int errorCode, @NonNull String message, 
                                    @NonNull Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }
        
        public int getErrorCode() {
            return errorCode;
        }
        
        // 错误码常量
        public static final int ERROR_INSTALLATION_FAILED = 3001;
        public static final int ERROR_INSUFFICIENT_SPACE = 3002;
        public static final int ERROR_VERIFICATION_FAILED = 3003;
        public static final int ERROR_DEPENDENCY_NOT_MET = 3004;
        public static final int ERROR_VERSION_CONFLICT = 3005;
    }
    
    /**
     * 模块卸载异常。
     */
    class ModuleUninstallException extends Exception {
        private final int errorCode;
        
        public ModuleUninstallException(int errorCode, @NonNull String message) {
            super(message);
            this.errorCode = errorCode;
        }
        
        public ModuleUninstallException(int errorCode, @NonNull String message, 
                                       @NonNull Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }
        
        public int getErrorCode() {
            return errorCode;
        }
        
        // 错误码常量
        public static final int ERROR_MODULE_NOT_LOADED = 4001;
        public static final int ERROR_RESOURCE_CLEANUP_FAILED = 4002;
        public static final int ERROR_USER_DATA_PRESERVED = 4003;
    }
}
