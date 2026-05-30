package com.gamecenter.app.modulestore;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.gamecenter.app.interfaces.IModuleStore.DownloadCallback;
import com.gamecenter.app.models.ModuleInfo;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 模块下载管理器。
 * 
 * 负责模块 APK 的下载管理：
 * - 使用 OkHttp 下载 APK
 * - 支持断点续传（Range 请求头）
 * - 下载进度回调
 * - 下载暂停/恢复
 * 
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-26
 */
public class ModuleDownloadManager {
    
    private static final String TAG = "ModuleDownloadManager";
    
    /** 单例实例 */
    private static volatile ModuleDownloadManager instance;
    
    /** OkHttp 客户端 */
    private final OkHttpClient httpClient;
    
    /** 活跃的下载任务（模块 ID -> DownloadTask） */
    private final ConcurrentHashMap<String, DownloadTask> activeDownloads;
    
    /** 主线程 Handler（用于回调） */
    private final Handler mainHandler;
    
    /** 线程池 */
    private final ExecutorService executorService;
    
    /** 下载缓存目录 */
    private final File downloadCacheDir;
    
    /** 缓冲区大小：8KB */
    private static final int BUFFER_SIZE = 8192;
    
    /** 连接超时：10 秒 */
    private static final int CONNECT_TIMEOUT_MS = 10000;
    
    /** 读取超时：30 秒 */
    private static final int READ_TIMEOUT_MS = 30000;
    
    /**
     * 获取单例实例（双重检查锁定）。
     * 
     * @param context Android Context
     * @return ModuleDownloadManager 单例
     */
    @NonNull
    public static ModuleDownloadManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (ModuleDownloadManager.class) {
                if (instance == null) {
                    instance = new ModuleDownloadManager(context);
                }
            }
        }
        return instance;
    }
    
    /**
     * 私有构造函数。
     */
    private ModuleDownloadManager(@NonNull Context context) {
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.activeDownloads = new ConcurrentHashMap<>();
        this.executorService = Executors.newFixedThreadPool(3);
        
        // 创建 OkHttp 客户端
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(READ_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build();
        
        // 下载缓存目录：/storage/emulated/0/Android/data/com.gamecenter.app/cache/modules/
        if (context != null) {
            this.downloadCacheDir = new File(
                    context.getExternalCacheDir(), 
                    "modules"
            );
        } else {
            this.downloadCacheDir = new File("/tmp/modules");
        }
        
        if (!downloadCacheDir.exists() && !downloadCacheDir.mkdirs()) {
            Log.e(TAG, "创建下载缓存目录失败: " + downloadCacheDir.getAbsolutePath());
        }
    }
    
    /**
     * 下载模块。
     * 
     * @param moduleInfo 模块信息
     * @param callback 下载进度回调
     */
    public void downloadModule(@NonNull ModuleInfo moduleInfo, 
                               @NonNull DownloadCallback callback) {
        if (moduleInfo == null) {
            Log.e(TAG, "moduleInfo 为 null");
            mainHandler.post(() -> 
                    callback.onError("", 1009, "模块信息为 null"));
            return;
        }
        
        String moduleId = moduleInfo.getModuleId();
        if (moduleId == null || moduleId.isEmpty()) {
            Log.e(TAG, "moduleId 为空");
            mainHandler.post(() -> 
                    callback.onError(moduleId, 1009, "模块 ID 为空"));
            return;
        }
        
        // 检查是否已在下载
        if (activeDownloads.containsKey(moduleId)) {
            Log.w(TAG, "模块已在下载中: " + moduleId);
            return;
        }
        
        // 创建下载任务
        DownloadTask task = new DownloadTask(moduleInfo, callback);
        activeDownloads.put(moduleId, task);
        
        // 提交到线程池
        Future<?> future = executorService.submit(task);
        task.setFuture(future);
        
        Log.d(TAG, "开始下载模块: " + moduleId);
    }
    
    /**
     * 暂停下载。
     * 
     * @param moduleId 模块 ID
     * @return 暂停成功返回 true，否则返回 false
     */
    public boolean pauseDownload(@NonNull String moduleId) {
        DownloadTask task = activeDownloads.get(moduleId);
        if (task != null) {
            task.pause();
            Log.d(TAG, "暂停下载: " + moduleId);
            return true;
        }
        return false;
    }
    
    /**
     * 恢复下载（断点续传）。
     * 
     * @param moduleId 模块 ID
     * @return 恢复成功返回 true，否则返回 false
     */
    public boolean resumeDownload(@NonNull String moduleId) {
        DownloadTask task = activeDownloads.get(moduleId);
        if (task != null && task.isPaused()) {
            task.resume();
            Log.d(TAG, "恢复下载: " + moduleId);
            return true;
        }
        return false;
    }
    
    /**
     * 取消下载（删除已下载的部分文件）。
     * 
     * @param moduleId 模块 ID
     * @return 取消成功返回 true，否则返回 false
     */
    public boolean cancelDownload(@NonNull String moduleId) {
        DownloadTask task = activeDownloads.remove(moduleId);
        if (task != null) {
            task.cancel();
            Log.d(TAG, "取消下载: " + moduleId);
            return true;
        }
        return false;
    }
    
    /**
     * 获取下载进度。
     * 
     * @param moduleId 模块 ID
     * @return 下载进度（0-100），未找到返回 -1
     */
    public int getDownloadProgress(@NonNull String moduleId) {
        DownloadTask task = activeDownloads.get(moduleId);
        return task != null ? task.getProgress() : -1;
    }
    
    /**
     * 检查模块是否正在下载。
     * 
     * @param moduleId 模块 ID
     * @return 正在下载返回 true，否则返回 false
     */
    public boolean isDownloading(@NonNull String moduleId) {
        return activeDownloads.containsKey(moduleId);
    }
    
    /**
     * 获取所有正在下载的模块 ID 列表。
     * 
     * @return 模块 ID 列表
     */
    @NonNull
    public java.util.List<String> getActiveDownloads() {
        return new java.util.ArrayList<>(activeDownloads.keySet());
    }
    
    /**
     * 释放所有资源（应用退出时调用）。
     */
    public void release() {
        // 取消所有下载任务
        for (String moduleId : new HashMap<>(activeDownloads).keySet()) {
            cancelDownload(moduleId);
        }
        
        // 关闭线程池
        executorService.shutdownNow();
        
        Log.d(TAG, "ModuleDownloadManager 已释放所有资源");
    }
    
    // ========== 下载任务内部类 ==========
    
    /**
     * 下载任务。
     */
    private class DownloadTask implements Runnable {
        private final ModuleInfo moduleInfo;
        private final DownloadCallback callback;
        private final String moduleId;
        private final File tempFile;
        
        private volatile boolean paused;
        private volatile boolean cancelled;
        private Future<?> future;
        private int progress;
        private long downloadedBytes;
        
        DownloadTask(@NonNull ModuleInfo moduleInfo, 
                     @NonNull DownloadCallback callback) {
            this.moduleInfo = moduleInfo;
            this.callback = callback;
            this.moduleId = moduleInfo.getModuleId();
            this.tempFile = new File(downloadCacheDir, moduleId + ".apk.tmp");
            this.paused = false;
            this.cancelled = false;
            this.progress = 0;
            this.downloadedBytes = 0L;
        }
        
        void setFuture(@NonNull Future<?> future) {
            this.future = future;
        }
        
        synchronized void pause() {
            this.paused = true;
        }
        
        synchronized boolean isPaused() {
            return paused;
        }
        
        synchronized void resume() {
            this.paused = false;
            notifyAll();
            mainHandler.post(() -> callback.onResumed(moduleId));
        }
        
        synchronized void cancel() {
            this.cancelled = true;
            notifyAll();
        }
        
        int getProgress() {
            return progress;
        }
        
        @Override
        public void run() {
            try {
                // 检查临时文件是否存在（断点续传）
                if (tempFile.exists()) {
                    downloadedBytes = tempFile.length();
                } else {
                    downloadedBytes = 0L;
                }
                
                // 构建请求
                Request.Builder requestBuilder = new Request.Builder()
                        .url(moduleInfo.getDownloadUrl());
                
                // 如果是断点续传，添加 Range 头
                if (downloadedBytes > 0) {
                    requestBuilder.addHeader("Range", "bytes=" + downloadedBytes + "-");
                    Log.d(TAG, "断点续传: " + moduleId + ", 已下载=" + downloadedBytes);
                }
                
                Request request = requestBuilder.build();
                
                // 执行请求
                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful() && response.code() != 206) {
                        throw new IOException("HTTP " + response.code() + ": " + response.message());
                    }
                    
                    ResponseBody body = response.body();
                    if (body == null) {
                        throw new IOException("Response body 为 null");
                    }
                    
                    long totalBytes = body.contentLength();
                    if (downloadedBytes > 0) {
                        totalBytes += downloadedBytes; // 断点续传时，总大小 = 已下载 + 剩余
                    }
                    
                    // 打开文件（断点续传时追加）
                    try (FileOutputStream fos = new FileOutputStream(tempFile, downloadedBytes > 0);
                         InputStream is = body.byteStream()) {
                        
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int bytesRead;
                        long lastProgressUpdate = 0L;
                        
                        while ((bytesRead = is.read(buffer)) != -1) {
                            // 检查取消
                            if (cancelled) {
                                mainHandler.post(() -> callback.onCancelled(moduleId));
                                return;
                            }
                            
                            // 检查暂停
                            synchronized (this) {
                                while (paused && !cancelled) {
                                    mainHandler.post(() -> callback.onPaused(moduleId));
                                    wait();
                                }
                            }
                            
                            // 写入文件
                            fos.write(buffer, 0, bytesRead);
                            downloadedBytes += bytesRead;
                            
                            // 更新进度（每 1% 或每 500ms 更新一次）
                            if (totalBytes > 0) {
                                int newProgress = (int) (downloadedBytes * 100 / totalBytes);
                                if (newProgress != progress || 
                                        System.currentTimeMillis() - lastProgressUpdate > 500) {
                                    progress = newProgress;
                                    lastProgressUpdate = System.currentTimeMillis();
                                    
                                    // 回调进度（主线程）
                                    final long current = downloadedBytes;
                                    final long total = totalBytes;
                                    mainHandler.post(() -> 
                                            callback.onProgress(moduleId, progress, current, total));
                                }
                            }
                        }
                        
                        fos.flush();
                    }
                    
                    // 下载完成，重命名临时文件
                    File finalFile = new File(downloadCacheDir, moduleId + ".apk");
                    if (tempFile.renameTo(finalFile)) {
                        Log.d(TAG, "下载完成: " + moduleId + " -> " + finalFile.getAbsolutePath());
                        
                        // 回调成功（主线程）
                        mainHandler.post(() -> 
                                callback.onSuccess(moduleId, finalFile.getAbsolutePath()));
                    } else {
                        throw new IOException("重命名临时文件失败");
                    }
                    
                }
                
            } catch (Exception e) {
                Log.e(TAG, "下载失败: " + moduleId, e);
                
                // 回调失败（主线程）
                final String errorMsg = e.getMessage() != null ? e.getMessage() : "未知错误";
                mainHandler.post(() -> 
                        callback.onError(moduleId, 1009, errorMsg));
                
            } finally {
                // 从活跃任务中移除
                activeDownloads.remove(moduleId);
            }
        }
    }
}
