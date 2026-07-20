package com.gamecenter.app.modules;

import android.content.Context;
import android.util.Log;
import com.gamecenter.app.core.common.ModuleManifest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 模块依赖下载器（TD-05）。
 * 
 * 功能：
 * 1. 递归下载模块依赖（深度优先）
 * 2. 防止重复下载（使用 Set<String> downloadedDeps）
 * 3. 检查依赖是否已安装（调用 moduleManager.isModuleInstalled()）
 * 
 * @author 寇豆码 (Kou)
 * @version 1.0
 * @since 2026-05-27
 */
public class ModuleDependencyDownloader {
    
    private static final String TAG = "ModuleDependencyDownloader";
    
    /** 模块管理器 */
    private ModuleManager moduleManager;
    
    /** 模块下载器 */
    private ModuleDownloader moduleDownloader;
    
    /** 已下载的依赖集合（防止重复下载） */
    private Set<String> downloadedDeps;
    
    /** 依赖解析器 */
    private ModuleDependencyResolver resolver;
    
    /**
     * 构造函数。
     * 
     * @param moduleManager 模块管理器实例
     * @param moduleDownloader 模块下载器实例
     */
    public ModuleDependencyDownloader(ModuleManager moduleManager, ModuleDownloader moduleDownloader) {
        this.moduleManager = moduleManager;
        this.moduleDownloader = moduleDownloader;
        this.downloadedDeps = new HashSet<>();
        this.resolver = new ModuleDependencyResolver(moduleManager.getManifests());
    }
    
    /**
     * 下载模块依赖（递归下载）。
     * 
     * @param context Android Context
     * @param moduleId 模块 ID
     * @param callback 下载回调
     */
    public void downloadDependencies(Context context, String moduleId, ModuleDownloader.Callback callback) {
        ModuleManifest manifest = moduleManager.getModuleManifest(moduleId);
        
        if (manifest == null) {
            Log.e(TAG, "模块不存在: " + moduleId);
            if (callback != null) {
                callback.onError(moduleId, "模块不存在: " + moduleId);
            }
            return;
        }
        
        // 解析依赖
        List<String> dependencies;
        try {
            resolver = new ModuleDependencyResolver(moduleManager.getManifests());
            dependencies = resolver.resolveDependencies(manifest);
        } catch (ModuleDependencyResolver.CircularDependencyException e) {
            Log.e(TAG, "循环依赖检测失败: " + e.getMessage());
            if (callback != null) {
                callback.onError(moduleId, "循环依赖: " + e.getMessage());
            }
            return;
        }
        
        // 移除主模块（只下载依赖）
        dependencies.remove(moduleId);
        
        if (dependencies.isEmpty()) {
            Log.d(TAG, "模块 " + moduleId + " 没有依赖需要下载");
            if (callback != null) {
                callback.onComplete("all_deps", null);
            }
            return;
        }
        
        Log.d(TAG, "模块 " + moduleId + " 有 " + dependencies.size() + " 个依赖: " + dependencies);
        
        // 递归下载依赖
        downloadRecursive(context, dependencies, 0, callback);
    }
    
    /**
     * 递归下载依赖。
     * 
     * @param context Android Context
     * @param dependencies 依赖列表（按加载顺序排列）
     * @param index 当前下载索引
     * @param callback 下载回调
     */
    private void downloadRecursive(final Context context, final List<String> dependencies, 
                                final int index, final ModuleDownloader.Callback callback) {
        
        // 所有依赖已下载
        if (index >= dependencies.size()) {
            Log.d(TAG, "所有依赖已下载，开始下载主模块");
            if (callback != null) {
                callback.onComplete("all_deps", null);
            }
            return;
        }
        
        final String depId = dependencies.get(index);
        
        // 检查是否已安装
        if (moduleManager.isModuleInstalled(context, depId)) {
            Log.d(TAG, "依赖已安装，跳过: " + depId);
            downloadRecursive(context, dependencies, index + 1, callback);
            return;
        }
        
        // 检查是否已下载（防止重复下载）
        if (downloadedDeps.contains(depId)) {
            Log.d(TAG, "依赖已下载，跳过: " + depId);
            downloadRecursive(context, dependencies, index + 1, callback);
            return;
        }
        
        // 下载依赖
        Log.d(TAG, "下载依赖: " + depId);
        moduleManager.downloadModule(context, depId, new ModuleDownloader.Callback() {
            @Override
            public void onProgress(String moduleId, long downloaded, long total, long speedKbps) {
                // 转发进度回调
                if (callback != null) {
                    callback.onProgress(moduleId, downloaded, total, speedKbps);
                }
            }
            
            @Override
            public void onComplete(String moduleId, java.io.File file) {
                Log.d(TAG, "依赖下载成功: " + moduleId);
                downloadedDeps.add(moduleId);
                
                // 继续下载下一个依赖
                downloadRecursive(context, dependencies, index + 1, callback);
            }
            
            @Override
            public void onError(String moduleId, String message) {
                Log.e(TAG, "依赖下载失败: " + moduleId + ", error=" + message);
                if (callback != null) {
                    callback.onError(moduleId, message);
                }
            }

            @Override
            public void onError(String moduleId, int errorCode, String message) {
                Log.e(TAG, "依赖下载失败: " + moduleId + ", code=" + errorCode + ", error=" + message);
                if (callback != null) {
                    callback.onError(moduleId, errorCode, message);
                }
            }
            
            @Override
            public void onSourceSwitch(String moduleId, int sourceIndex, String url) {
                if (callback != null) {
                    callback.onSourceSwitch(moduleId, sourceIndex, url);
                }
            }
        });
    }
    
    /**
     * 下载单个依赖（简化版，不递归）。
     * 
     * @param context Android Context
     * @param depId 依赖模块 ID
     * @param callback 下载回调
     */
    public void downloadDependency(Context context, String depId, ModuleDownloader.Callback callback) {
        // 检查是否已安装
        if (moduleManager.isModuleInstalled(context, depId)) {
            Log.d(TAG, "依赖已安装，跳过: " + depId);
            if (callback != null) {
                callback.onComplete(depId, null);
            }
            return;
        }
        
        // 检查是否已下载
        if (downloadedDeps.contains(depId)) {
            Log.d(TAG, "依赖已下载，跳过: " + depId);
            if (callback != null) {
                callback.onComplete(depId, null);
            }
            return;
        }
        
        // 下载依赖
        Log.d(TAG, "下载依赖: " + depId);
        moduleManager.downloadModule(context, depId, new ModuleDownloader.Callback() {
            @Override
            public void onProgress(String moduleId, long downloaded, long total, long speedKbps) {
                if (callback != null) {
                    callback.onProgress(moduleId, downloaded, total, speedKbps);
                }
            }
            
            @Override
            public void onComplete(String moduleId, java.io.File file) {
                Log.d(TAG, "依赖下载成功: " + moduleId);
                downloadedDeps.add(moduleId);
                
                if (callback != null) {
                    callback.onComplete(moduleId, file);
                }
            }
            
            @Override
            public void onError(String moduleId, String message) {
                Log.e(TAG, "依赖下载失败: " + moduleId + ", error=" + message);
                if (callback != null) {
                    callback.onError(moduleId, message);
                }
            }

            @Override
            public void onError(String moduleId, int errorCode, String message) {
                Log.e(TAG, "依赖下载失败: " + moduleId + ", code=" + errorCode + ", error=" + message);
                if (callback != null) {
                    callback.onError(moduleId, errorCode, message);
                }
            }
            
            @Override
            public void onSourceSwitch(String moduleId, int sourceIndex, String url) {
                if (callback != null) {
                    callback.onSourceSwitch(moduleId, sourceIndex, url);
                }
            }
        });
    }
    
    /**
     * 清除已下载依赖记录（用于重新下载）。
     */
    public void clearDownloadedDeps() {
        downloadedDeps.clear();
        Log.d(TAG, "已清除下载记录");
    }
    
    /**
     * 获取已下载依赖集合（只读）。
     * 
     * @return 已下载依赖 ID 集合
     */
    public Set<String> getDownloadedDeps() {
        return new HashSet<>(downloadedDeps);
    }
}
