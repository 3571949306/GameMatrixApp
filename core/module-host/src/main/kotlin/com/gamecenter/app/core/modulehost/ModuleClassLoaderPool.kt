package com.gamecenter.app.core.modulehost

import android.util.Log
import dalvik.system.DexClassLoader
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap

/**
 * 模块 ClassLoader 池。
 *
 * 解决问题：
 * 1. 同一模块版本复用同一 ClassLoader，避免重复加载的内存开销。
 * 2. 模块版本升级时，自动释放旧版 ClassLoader（Android 8+ 支持 close()）。
 * 3. 卸载模块时，显式释放 ClassLoader 引用，防止内存泄漏。
 *
 * 注意：DexClassLoader.close() 仅 Android 8（API 26）及以上支持。
 * Android 7 上 ClassLoader 不可强制释放，但池化机制可防止无限累积。
 */
object ModuleClassLoaderPool {

    private const val TAG = "ModuleClassLoaderPool"

    /**
     * 带版本的 ClassLoader 记录。
     *
     * @param versionCode 模块版本号，用于判断是否需要替换
     * @param loader ClassLoader 实例
     */
    private data class VersionedLoader(
        val versionCode: Int,
        val loader: ClassLoader
    )

    /** 活跃的 ClassLoader 池，Key = moduleId */
    private val pool = ConcurrentHashMap<String, VersionedLoader>()

    /**
     * 获取指定模块的 ClassLoader。
     *
     * - 如果池中已有相同版本的 ClassLoader，直接复用。
     * - 如果版本不同（升级），先释放旧的，再创建新的。
     * - 如果池中没有，创建并缓存。
     *
     * @param moduleId 模块唯一标识
     * @param versionCode 模块版本号
     * @param apkPath 模块 APK 文件的绝对路径
     * @param parent 父 ClassLoader（通常为 App 的 classLoader）
     * @return 可用的 ClassLoader
     */
    fun obtain(
        moduleId: String,
        versionCode: Int,
        apkPath: String,
        parent: ClassLoader
    ): ClassLoader {
        val existing = pool[moduleId]

        // 同版本直接复用
        if (existing != null && existing.versionCode == versionCode) {
            Log.d(TAG, "复用 ClassLoader: $moduleId v$versionCode")
            return existing.loader
        }

        // 版本不同（升级），释放旧 ClassLoader
        if (existing != null) {
            Log.d(TAG, "模块 $moduleId 版本变更: v${existing.versionCode} → v$versionCode，释放旧 ClassLoader")
            releaseLoader(existing.loader)
        }

        // 创建新 ClassLoader
        Log.d(TAG, "创建 ClassLoader: $moduleId v$versionCode, apkPath=$apkPath")
        val newLoader = DexClassLoader(
            apkPath,
            null, // optimizedDirectory: Android 8+ 忽略此参数
            null, // librarySearchPath
            parent
        )
        pool[moduleId] = VersionedLoader(versionCode, newLoader)
        return newLoader
    }

    /**
     * 显式释放指定模块的 ClassLoader。
     * 在模块卸载时调用。
     *
     * @param moduleId 模块唯一标识
     */
    fun release(moduleId: String) {
        val removed = pool.remove(moduleId)
        if (removed != null) {
            Log.d(TAG, "释放 ClassLoader: $moduleId v${removed.versionCode}")
            releaseLoader(removed.loader)
        }
    }

    /**
     * 释放所有模块的 ClassLoader。
     * 通常不需要调用，仅在 App 完全退出或测试清理时使用。
     */
    fun releaseAll() {
        val ids = pool.keys.toList()
        ids.forEach { release(it) }
        Log.d(TAG, "已释放所有 ClassLoader（共 ${ids.size} 个）")
    }

    /**
     * 获取当前池中已加载的模块 ID 列表（调试用）。
     */
    fun loadedModuleIds(): Set<String> = pool.keys.toSet()

    /**
     * 尝试关闭 ClassLoader（Android 8+ 支持）。
     * Android 7 上 DexClassLoader 不实现 Closeable，此处安全捕获异常。
     */
    private fun releaseLoader(loader: ClassLoader) {
        try {
            if (loader is Closeable) {
                loader.close()
                Log.d(TAG, "ClassLoader.close() 成功")
            }
        } catch (e: Exception) {
            Log.w(TAG, "ClassLoader.close() 失败（可能为 Android 7）: ${e.message}")
        }
    }
}
