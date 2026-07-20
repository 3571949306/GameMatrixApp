package com.gamecenter.app.modules.unity

import android.content.Context
import android.util.Log
import com.gamecenter.app.BuildConfig

/**
 * P6 Unity 模块启动器管理器。
 *
 * 职责：
 * 1. 维护当前已注册的所有 UnityModuleLauncher 实例
 * 2. 提供按 moduleId 查询/启动能力
 * 3. 在 ENABLE_P6_UNITY_MODULE 关闭时返回空集合，避免影响主流程
 *
 * 注意：
 * - 实际 Unity SDK 集成后，模块实现 UnityModuleLauncher 接口并自行注册
 * - 当前占位实现通过 PlaceholderUnityModuleLauncher 提供无 SDK 时的行为
 */
object UnityModuleManager {

    private const val TAG = "UnityModuleManager"

    private val launchers = mutableMapOf<String, UnityModuleLauncher>()

    /**
     * 注册 Unity 模块启动器。
     */
    @Synchronized
    fun register(launcher: UnityModuleLauncher) {
        if (!BuildConfig.ENABLE_P6_UNITY_MODULE) {
            Log.d(TAG, "P6 Unity 模块已禁用，跳过注册: ${launcher.getModuleId()}")
            return
        }
        launchers[launcher.getModuleId()] = launcher
        Log.d(TAG, "注册 Unity 启动器: ${launcher.getModuleId()}")
    }

    /**
     * 注销 Unity 模块启动器。
     */
    @Synchronized
    fun unregister(moduleId: String) {
        launchers.remove(moduleId)
        Log.d(TAG, "注销 Unity 启动器: $moduleId")
    }

    /**
     * 获取指定 moduleId 的启动器。
     */
    @Synchronized
    fun getLauncher(moduleId: String): UnityModuleLauncher? = launchers[moduleId]

    /**
     * 获取所有已注册的启动器。
     */
    @Synchronized
    fun getAllLaunchers(): List<UnityModuleLauncher> = launchers.values.toList()

    /**
     * 启动指定 Unity 模块（独立模式）。
     */
    fun launchStandalone(context: Context, moduleId: String, args: Map<String, String> = emptyMap()): Boolean {
        if (!BuildConfig.ENABLE_P6_UNITY_MODULE) {
            Log.d(TAG, "P6 Unity 模块已禁用，无法启动: $moduleId")
            return false
        }
        val launcher = getLauncher(moduleId)
        if (launcher == null) {
            Log.w(TAG, "未找到 Unity 启动器: $moduleId")
            return false
        }
        if (!launcher.isSupported(context)) {
            Log.w(TAG, "当前设备不支持运行 Unity 模块: $moduleId")
            return false
        }
        return launcher.launchStandalone(context, args)
    }

    /**
     * 创建可嵌入的 Unity Fragment。
     */
    fun createEmbeddedFragment(
        context: Context,
        moduleId: String,
        args: Map<String, String> = emptyMap()
    ): androidx.fragment.app.Fragment? {
        if (!BuildConfig.ENABLE_P6_UNITY_MODULE) {
            Log.d(TAG, "P6 Unity 模块已禁用，无法创建嵌入 Fragment: $moduleId")
            return null
        }
        val launcher = getLauncher(moduleId) ?: return null
        if (!launcher.isSupported(context)) return null
        return launcher.createEmbeddedFragment(context, args)
    }
}
