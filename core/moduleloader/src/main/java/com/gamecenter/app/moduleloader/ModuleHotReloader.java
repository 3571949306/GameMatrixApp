package com.gamecenter.app.moduleloader;

import android.content.Context;
import android.os.FileObserver;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.gamecenter.app.interfaces.IModule;
import com.gamecenter.app.interfaces.IModuleLoader.ModuleLoadException;
import com.gamecenter.app.models.ModuleInfo;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 模块热更新器。
 * 
 * 支持模块文件变化检测和热更新：
 * - 检测模块 APK 文件变化（修改时间、文件大小）
 * - 热更新（卸载旧版本 → 加载新版本）
 * 
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-26
 */
public class ModuleHotReloader {
    
    private static final String TAG = "ModuleHotReloader";
    
    /** 热更新检查间隔（毫秒）：5 秒 */
    private static final long CHECK_INTERVAL_MS = 5000L;
    
    /** 模块加载器引用 */
    private final ModuleLoaderV2 moduleLoader;
    
    /** 已加载模块的文件状态缓存（模块 ID -> 文件状态） */
    private final Map<String, ModuleFileState> fileStateCache;
    
    /** FileObserver 缓存 */
    private final Map<String, FileObserver> fileObserverCache;
    
    /** 热更新开关 */
    private boolean hotReloadEnabled;
    
    /** 后台检查线程 */
    private Thread backgroundCheckThread;
    
    /** 停止标志 */
    private volatile boolean stopRequested;
    
    /** ModuleInfo 缓存（模块 ID -> ModuleInfo） */
    private final Map<String, ModuleInfo> moduleInfoCache;
    
    /**
     * 模块文件状态。
     */
    private static class ModuleFileState {
        final String filePath;
        long lastModified;
        long fileSize;
        
        ModuleFileState(@NonNull String filePath, long lastModified, long fileSize) {
            this.filePath = filePath;
            this.lastModified = lastModified;
            this.fileSize = fileSize;
        }
        
        /**
         * 检查文件是否发生变化。
         */
        boolean hasChanged(@NonNull File file) {
            return file.lastModified() != lastModified || file.length() != fileSize;
        }
        
        /**
         * 更新状态为当前文件状态。
         */
        void update(@NonNull File file) {
            this.lastModified = file.lastModified();
            this.fileSize = file.length();
        }
    }
    
    /**
     * 构造函数。
     * 
     * @param moduleLoader 模块加载器
     */
    public ModuleHotReloader(@NonNull ModuleLoaderV2 moduleLoader) {
        this.moduleLoader = moduleLoader;
        this.fileStateCache = new HashMap<>();
        this.fileObserverCache = new HashMap<>();
        this.moduleInfoCache = new HashMap<>();
        this.hotReloadEnabled = false;
        this.stopRequested = false;
    }
    
    /**
     * 启用热更新（开始监控模块文件变化）。
     * 
     * @param context Android Context
     */
    public void enableHotReload(@NonNull Context context) {
        if (hotReloadEnabled) {
            Log.w(TAG, "热更新已启用，无需重复启用");
            return;
        }
        
        hotReloadEnabled = true;
        stopRequested = false;
        
        Log.d(TAG, "热更新已启用");
        
        // 启动后台检查线程
        startBackgroundCheck();
    }
    
    /**
     * 禁用热更新（停止监控）。
     */
    public void disableHotReload() {
        if (!hotReloadEnabled) {
            return;
        }
        
        hotReloadEnabled = false;
        stopRequested = true;
        
        if (backgroundCheckThread != null) {
            backgroundCheckThread.interrupt();
            backgroundCheckThread = null;
        }
        
        // 停止所有 FileObserver
        for (FileObserver observer : fileObserverCache.values()) {
            observer.stopWatching();
        }
        fileObserverCache.clear();
        
        Log.d(TAG, "热更新已禁用");
    }
    
    /**
     * 检查指定模块是否可以热更新。
     * 
     * @param moduleId 模块 ID
     * @return 可以热更新返回 true，否则返回 false
     */
    public boolean checkHotReload(@NonNull String moduleId) {
        if (!hotReloadEnabled) {
            return false;
        }
        
        // 检查模块是否已加载
        if (!moduleLoader.isModuleLoaded(moduleId)) {
            Log.d(TAG, "模块未加载，无需热更新: " + moduleId);
            return false;
        }
        
        // 检查模块文件是否变化
        ModuleFileState state = fileStateCache.get(moduleId);
        if (state == null) {
            Log.d(TAG, "模块文件状态未缓存: " + moduleId);
            return false;
        }
        
        File moduleFile = new File(state.filePath);
        if (!moduleFile.exists()) {
            Log.w(TAG, "模块文件不存在: " + state.filePath);
            return false;
        }
        
        boolean changed = state.hasChanged(moduleFile);
        if (changed) {
            Log.d(TAG, "检测到模块文件变化: " + moduleId);
        }
        
        return changed;
    }
    
    /**
     * 执行热更新（卸载旧版本 → 加载新版本）。
     * 
     * @param moduleId 模块 ID
     * @return 热更新成功返回 true，否则返回 false
     */
    public boolean performHotReload(@NonNull String moduleId) {
        if (!moduleLoader.isModuleLoaded(moduleId)) {
            Log.e(TAG, "模块未加载，无法热更新: " + moduleId);
            return false;
        }
        
        try {
            Log.d(TAG, "开始热更新: " + moduleId);
            
            // 1. 获取缓存的 ModuleInfo
            ModuleInfo moduleInfo = moduleInfoCache.get(moduleId);
            if (moduleInfo == null) {
                Log.e(TAG, "ModuleInfo 未缓存，无法热更新: " + moduleId);
                return false;
            }
            
            // 2. 卸载旧版本
            moduleLoader.unloadModule(moduleId);
            Log.d(TAG, "旧版本已卸载: " + moduleId);
            
            // 3. 加载新版本
            IModule newModule = moduleLoader.loadModule(moduleInfo);
            if (newModule == null) {
                Log.e(TAG, "热更新失败：加载新版本失败: " + moduleId);
                return false;
            }
            
            // 4. 更新文件状态缓存
            ModuleFileState state = fileStateCache.get(moduleId);
            if (state != null) {
                File moduleFile = new File(state.filePath);
                if (moduleFile.exists()) {
                    state.update(moduleFile);
                }
            }
            
            Log.i(TAG, "热更新完成: " + moduleId + " v" + moduleInfo.getVersionName());
            return true;
            
        } catch (ModuleLoadException e) {
            Log.e(TAG, "热更新失败: " + moduleId, e);
            return false;
        } catch (Exception e) {
            Log.e(TAG, "热更新失败: " + moduleId, e);
            return false;
        }
    }
    
    /**
     * 注册模块文件监控。
     * 
     * @param moduleId 模块 ID
     * @param apkFile 模块 APK 文件
     * @param moduleInfo 模块信息（用于热更新时重新加载）
     */
    public void registerModule(@NonNull String moduleId, @NonNull File apkFile, 
                              @NonNull ModuleInfo moduleInfo) {
        if (!apkFile.exists()) {
            Log.e(TAG, "模块文件不存在，无法注册监控: " + apkFile.getAbsolutePath());
            return;
        }
        
        // 缓存 ModuleInfo（用于热更新时重新加载）
        moduleInfoCache.put(moduleId, moduleInfo);
        
        // 缓存文件状态
        ModuleFileState state = new ModuleFileState(
                apkFile.getAbsolutePath(),
                apkFile.lastModified(),
                apkFile.length()
        );
        fileStateCache.put(moduleId, state);
        
        // 创建 FileObserver（监控文件修改）
        if (hotReloadEnabled) {
            startFileObserver(moduleId, apkFile);
        }
        
        Log.d(TAG, "已注册模块文件监控: " + moduleId + " -> " + apkFile.getName());
    }
    
    /**
     * 注册模块文件监控（重载方法，不提供 ModuleInfo）。
     * 
     * @param moduleId 模块 ID
     * @param apkFile 模块 APK 文件
     * @deprecated 建议使用 {@link #registerModule(String, File, ModuleInfo)}
     */
    @Deprecated
    public void registerModule(@NonNull String moduleId, @NonNull File apkFile) {
        registerModule(moduleId, apkFile, new ModuleInfo());
    }
    
    /**
     * 取消注册模块文件监控。
     * 
     * @param moduleId 模块 ID
     */
    public void unregisterModule(@NonNull String moduleId) {
        // 移除 ModuleInfo 缓存
        moduleInfoCache.remove(moduleId);
        
        // 移除文件状态缓存
        fileStateCache.remove(moduleId);
        
        // 停止 FileObserver
        FileObserver observer = fileObserverCache.remove(moduleId);
        if (observer != null) {
            observer.stopWatching();
        }
        
        Log.d(TAG, "已取消注册模块文件监控: " + moduleId);
    }
    
    // ========== 私有方法 ==========
    
    /**
     * 启动后台检查线程（轮询方式）。
     */
    private void startBackgroundCheck() {
        backgroundCheckThread = new Thread(() -> {
            Log.d(TAG, "后台检查线程已启动");
            
            while (!stopRequested && hotReloadEnabled) {
                try {
                    // 遍历所有已注册模块，检查文件变化
                    for (String moduleId : new HashMap<>(fileStateCache).keySet()) {
                        if (checkHotReload(moduleId)) {
                            Log.i(TAG, "检测到模块变化，执行热更新: " + moduleId);
                            performHotReload(moduleId);
                        }
                    }
                    
                    // 等待
                    Thread.sleep(CHECK_INTERVAL_MS);
                    
                } catch (InterruptedException e) {
                    Log.d(TAG, "后台检查线程被中断");
                    break;
                } catch (Exception e) {
                    Log.e(TAG, "后台检查异常", e);
                }
            }
            
            Log.d(TAG, "后台检查线程已停止");
        });
        
        backgroundCheckThread.setName("ModuleHotReload-CheckThread");
        backgroundCheckThread.setDaemon(true);
        backgroundCheckThread.start();
    }
    
    /**
     * 启动 FileObserver（文件系统事件监控）。
     * 
     * 监控 CLOSE_WRITE 事件（文件写入完成）。
     */
    @SuppressWarnings("deprecation")
    private void startFileObserver(@NonNull String moduleId, @NonNull File apkFile) {
        try {
            // 使用掩码指定监听的事件类型
            int mask = FileObserver.CLOSE_WRITE | FileObserver.MODIFY;
            
            FileObserver observer = new FileObserver(apkFile.getAbsolutePath(), mask) {
                @Override
                public void onEvent(int event, @Nullable String path) {
                    if (path == null) return;
                    
                    if ((event & FileObserver.CLOSE_WRITE) != 0 ||
                        (event & FileObserver.MODIFY) != 0) {
                        Log.i(TAG, "检测到模块文件变化，执行热更新: " + moduleId);
                        performHotReload(moduleId);
                    }
                }
            };
            
            observer.startWatching();
            fileObserverCache.put(moduleId, observer);
            
            Log.d(TAG, "FileObserver 已启动: " + moduleId);
            
        } catch (Exception e) {
            Log.e(TAG, "启动 FileObserver 失败: " + moduleId, e);
        }
    }
}
