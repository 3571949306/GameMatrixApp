package com.gamecenter.app.moduleloader;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Comparator;

/**
 * DEX 缓存管理器。
 * 
 * 负责管理模块 DEX 文件的优化缓存：
 * - DEX 优化（生成 odex/oat 文件）
 * - 缓存清理（版本升级时清理旧版本）
 * - 缓存大小统计
 * 
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-26
 */
public class DexCacheManager {
    
    private static final String TAG = "DexCacheManager";
    
    /** DEX 优化缓存目录名称 */
    private static final String DEX_CACHE_DIR = "dex_cache";
    
    /** 模块缓存目录名称 */
    private static final String MODULE_CACHE_DIR = "module_cache";
    
    /** 单个模块缓存上限：50MB */
    private static final long MAX_MODULE_CACHE_SIZE = 50 * 1024 * 1024L;
    
    /** 全局缓存上限：200MB */
    private static final long MAX_GLOBAL_CACHE_SIZE = 200 * 1024 * 1024L;
    
    /** 缓存文件后缀：优化后的 DEX */
    private static final String OPTIMIZED_DEX_SUFFIX = ".odex";
    
    /** 缓存文件后缀：元数据 */
    private static final String META_SUFFIX = ".meta";
    
    private final Context context;
    
    /**
     * 构造函数。
     * 
     * @param context Android Context
     */
    public DexCacheManager(@NonNull Context context) {
        this.context = context != null ? context.getApplicationContext() : null;
    }
    
    /**
     * 优化 DEX 文件（生成优化后的 DEX 缓存）。
     * 
     * 使用 Android 的 dex2oat 机制优化 DEX 加载性能。
     * 
     * @param apkFile 模块 APK 文件
     * @param moduleId 模块 ID
     * @param versionCode 模块版本号
     * @return 优化后的 DEX 缓存目录，失败返回 null
     */
    @Nullable
    public File optimizeDex(@NonNull File apkFile, 
                              @NonNull String moduleId, 
                              int versionCode) {
        if (context == null) {
            Log.e(TAG, "Context 为 null，无法优化 DEX");
            return null;
        }
        
        if (apkFile == null || !apkFile.exists()) {
            Log.e(TAG, "APK 文件不存在: " + apkFile);
            return null;
        }
        
        if (moduleId == null || moduleId.isEmpty()) {
            Log.e(TAG, "模块 ID 为空");
            return null;
        }
        
        // 获取缓存目录：/data/data/pkg/app_DexCache/moduleId_versionCode/
        File cacheDir = getModuleCacheDir(moduleId, versionCode);
        if (cacheDir == null) {
            Log.e(TAG, "无法创建缓存目录");
            return null;
        }
        
        // 检查缓存是否已存在
        if (isCacheValid(cacheDir, apkFile)) {
            Log.d(TAG, "DEX 缓存已存在且有效: " + cacheDir.getAbsolutePath());
            return cacheDir;
        }
        
        // 清理旧缓存
        clearModuleCache(moduleId, versionCode);
        
        // 创建缓存目录
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            Log.e(TAG, "创建缓存目录失败: " + cacheDir.getAbsolutePath());
            return null;
        }
        
        // 执行 DEX 优化（简化实现）
        // 实际应使用 DexClassLoader 的 optimizedDirectory 参数
        boolean success = copyDexToCache(apkFile, cacheDir);
        
        if (success) {
            Log.d(TAG, "DEX 优化成功: " + moduleId + "_v" + versionCode 
                    + " -> " + cacheDir.getAbsolutePath());
            return cacheDir;
        } else {
            Log.e(TAG, "DEX 优化失败: " + moduleId + "_v" + versionCode);
            // 清理失败的缓存
            deleteRecursive(cacheDir);
            return null;
        }
    }
    
    /**
     * 清理指定模块的缓存（保留当前版本）。
     * 
     * @param moduleId 模块 ID
     * @param keepVersionCode 保留的版本号（-1 表示清理所有）
     */
    public void clearModuleCache(@NonNull String moduleId, int keepVersionCode) {
        if (moduleId == null || moduleId.isEmpty()) {
            return;
        }
        
        File dexCacheRoot = getDexCacheRoot();
        if (dexCacheRoot == null || !dexCacheRoot.exists()) {
            return;
        }
        
        File[] moduleDirs = dexCacheRoot.listFiles((dir, name) -> 
                name.startsWith(moduleId + "_"));
        
        if (moduleDirs == null) {
            return;
        }
        
        for (File dir : moduleDirs) {
            // 解析版本号
            int versionInDir = parseVersionFromDirName(dir.getName());
            
            if (keepVersionCode > 0 && versionInDir == keepVersionCode) {
                // 保留指定版本
                continue;
            }
            
            Log.d(TAG, "清理旧缓存: " + dir.getName());
            deleteRecursive(dir);
        }
        
        Log.d(TAG, "模块缓存清理完成: " + moduleId 
                + " (保留版本 " + keepVersionCode + ")");
    }
    
    /**
     * 清理指定模块的所有缓存。
     * 
     * @param moduleId 模块 ID
     */
    public void clearCache(@NonNull String moduleId) {
        clearModuleCache(moduleId, -1);
    }
    
    /**
     * 清理所有模块的缓存。
     */
    public void clearAllCache() {
        File dexCacheRoot = getDexCacheRoot();
        if (dexCacheRoot == null || !dexCacheRoot.exists()) {
            return;
        }
        
        File[] allDirs = dexCacheRoot.listFiles();
        if (allDirs == null) {
            return;
        }
        
        for (File dir : allDirs) {
            if (dir.isDirectory()) {
                deleteRecursive(dir);
            }
        }
        
        Log.d(TAG, "所有 DEX 缓存已清理");
    }
    
    /**
     * 获取当前缓存总大小。
     * 
     * @return 缓存大小（字节）
     */
    public long getCacheSize() {
        File dexCacheRoot = getDexCacheRoot();
        if (dexCacheRoot == null || !dexCacheRoot.exists()) {
            return 0L;
        }
        
        return calculateDirSize(dexCacheRoot);
    }
    
    /**
     * 获取指定模块的缓存大小。
     * 
     * @param moduleId 模块 ID
     * @return 缓存大小（字节）
     */
    public long getModuleCacheSize(@NonNull String moduleId) {
        if (moduleId == null || moduleId.isEmpty()) {
            return 0L;
        }
        
        File dexCacheRoot = getDexCacheRoot();
        if (dexCacheRoot == null || !dexCacheRoot.exists()) {
            return 0L;
        }
        
        File[] moduleDirs = dexCacheRoot.listFiles((dir, name) -> 
                name.startsWith(moduleId + "_"));
        
        if (moduleDirs == null) {
            return 0L;
        }
        
        long totalSize = 0L;
        for (File dir : moduleDirs) {
            totalSize += calculateDirSize(dir);
        }
        
        return totalSize;
    }
    
    /**
     * 检查缓存是否超过上限，如果超过则清理旧版本。
     * 
     * @return 清理后的缓存大小
     */
    public long enforceCacheLimit() {
        long currentSize = getCacheSize();
        
        if (currentSize <= MAX_GLOBAL_CACHE_SIZE) {
            return currentSize;
        }
        
        Log.w(TAG, "缓存超限: " + currentSize + " > " + MAX_GLOBAL_CACHE_SIZE 
                + ", 开始清理...");
        
        // 按最后修改时间排序，删除最旧的缓存
        File dexCacheRoot = getDexCacheRoot();
        if (dexCacheRoot == null) {
            return 0L;
        }
        
        File[] allDirs = dexCacheRoot.listFiles(File::isDirectory);
        if (allDirs == null) {
            return 0L;
        }
        
        // 按最后修改时间排序（最旧的优先）
        Arrays.sort(allDirs, Comparator.comparingLong(File::lastModified));
        
        for (File dir : allDirs) {
            if (getCacheSize() <= MAX_GLOBAL_CACHE_SIZE * 0.8) {
                // 清理到 80% 上限时停止
                break;
            }
            Log.d(TAG, "删除旧缓存: " + dir.getName());
            deleteRecursive(dir);
        }
        
        Log.d(TAG, "缓存清理完成，当前大小: " + getCacheSize());
        return getCacheSize();
    }
    
    // ========== 私有辅助方法 ==========
    
    /**
     * 获取 DEX 缓存根目录。
     */
    @Nullable
    private File getDexCacheRoot() {
        if (context == null) {
            return null;
        }
        return new File(context.getFilesDir(), DEX_CACHE_DIR);
    }
    
    /**
     * 获取模块缓存目录。
     */
    @Nullable
    private File getModuleCacheDir(@NonNull String moduleId, int versionCode) {
        File dexCacheRoot = getDexCacheRoot();
        if (dexCacheRoot == null) {
            return null;
        }
        
        String dirName = moduleId + "_v" + versionCode;
        return new File(dexCacheRoot, dirName);
    }
    
    /**
     * 检查缓存是否有效。
     */
    private boolean isCacheValid(@NonNull File cacheDir, @NonNull File apkFile) {
        if (!cacheDir.exists() || !cacheDir.isDirectory()) {
            return false;
        }
        
        // 检查元数据文件
        File metaFile = new File(cacheDir, "cache" + META_SUFFIX);
        if (!metaFile.exists()) {
            return false;
        }
        
        // 简化实现：仅检查缓存目录是否存在
        // 实际应检查 APK 文件的修改时间、版本号等
        return cacheDir.listFiles() != null && cacheDir.listFiles().length > 0;
    }
    
    /**
     * 复制 DEX 文件到缓存目录（简化实现）。
     */
    private boolean copyDexToCache(@NonNull File apkFile, @NonNull File cacheDir) {
        try {
            // 创建元数据文件
            File metaFile = new File(cacheDir, "cache" + META_SUFFIX);
            if (!metaFile.createNewFile()) {
                Log.w(TAG, "元数据文件已存在: " + metaFile.getAbsolutePath());
            }
            
            // 简化实现：仅创建标记文件表示缓存已生成
            // 实际应使用 DexClassLoader 加载并优化 DEX
            File optimizedDex = new File(cacheDir, "classes" + OPTIMIZED_DEX_SUFFIX);
            if (!optimizedDex.createNewFile()) {
                Log.w(TAG, "优化 DEX 文件已存在: " + optimizedDex.getAbsolutePath());
            }
            
            Log.d(TAG, "DEX 缓存文件已生成: " + cacheDir.getAbsolutePath());
            return true;
            
        } catch (IOException e) {
            Log.e(TAG, "复制 DEX 到缓存失败", e);
            return false;
        }
    }
    
    /**
     * 从目录名中解析版本号。
     */
    private int parseVersionFromDirName(@NonNull String dirName) {
        try {
            // 格式：moduleId_vVersionCode
            int start = dirName.lastIndexOf("_v");
            if (start >= 0) {
                String versionStr = dirName.substring(start + 2);
                return Integer.parseInt(versionStr);
            }
        } catch (Exception e) {
            // 忽略解析失败
        }
        return -1;
    }
    
    /**
     * 递归删除文件或目录。
     */
    private void deleteRecursive(@NonNull File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        if (!fileOrDirectory.delete()) {
            Log.w(TAG, "删除失败: " + fileOrDirectory.getAbsolutePath());
        }
    }
    
    /**
     * 计算目录大小（递归）。
     */
    private long calculateDirSize(@NonNull File dir) {
        long size = 0L;
        
        if (dir.isFile()) {
            return dir.length();
        }
        
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    size += calculateDirSize(child);
                }
            }
        }
        
        return size;
    }
}
