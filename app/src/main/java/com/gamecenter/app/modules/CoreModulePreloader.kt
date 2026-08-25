package com.gamecenter.app.modules

import android.content.Context
import android.util.Log
import java.util.concurrent.Executors

/**
 * P0 流畅度优化：核心模块预加载器。
 *
 * 背景：冷启动时 P4 动态导航依赖 games_hall / browser / tools 三个核心模块的
 * Dex 加载（ModuleManager.loadModule 内部走 DexClassLoader，含 I/O 与 dex 验证，
 * 耗时且原在 MainActivity.onCreate 主线程同步执行，是冷启动卡顿主源）。
 *
 * 本对象把所有核心模块加载串行化到【单一后台线程】，确保：
 *  1. 不阻塞主线程（由 SplashActivity 在启动屏窗口内发起）；
 *  2. 与 MainActivity 的降级补加载（ensureLoadedAsync）不会并发访问
 *     ModuleLoader 内部的 loadedModules Map，避免 HashMap 并发损坏。
 *
 * 注：ModuleManager.loadModule 本身是幂等的（内部 loadedModules 缓存），重复调用安全。
 */
object CoreModulePreloader {

    private const val TAG = "CoreModulePreloader"
    private val CORE_MODULES = listOf("games_hall", "browser", "tools")

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "CoreModulePreload").also { it.isDaemon = true }
    }

    @Volatile
    var isReady: Boolean = false
        private set

    /**
     * 后台串行预加载全部核心模块。onDone 在预加载线程上回调（可为 null）。
     */
    @JvmOverloads
    fun preload(context: Context, onDone: (() -> Unit)? = null) {
        executor.execute {
            for (id in CORE_MODULES) {
                try {
                    ModuleManager.loadModule(context.applicationContext, id)
                    Log.d(TAG, "核心模块已预加载: $id")
                } catch (e: Exception) {
                    Log.w(TAG, "核心模块预加载失败（进入 MainActivity 后降级补加载）: $id", e)
                }
            }
            isReady = true
            onDone?.invoke()
        }
    }

    /**
     * MainActivity 降级路径：仅补加载尚未就绪的核心模块，串行化到同一后台线程。
     * onDone 在预加载线程上回调，调用方需自行切回主线程（如 runOnUiThread）做 UI。
     */
    fun ensureLoadedAsync(context: Context, onDone: () -> Unit) {
        executor.execute {
            for (id in CORE_MODULES) {
                try {
                    if (!ModuleManager.isModuleLoaded(id)) {
                        ModuleManager.loadModule(context.applicationContext, id)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "降级补加载失败: $id", e)
                }
            }
            isReady = true
            onDone()
        }
    }
}
