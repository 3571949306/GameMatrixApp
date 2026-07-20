package com.gamecenter.app.core.common

import android.content.Context
import androidx.fragment.app.Fragment

/**
 * P6 Unity 模块启动器接口（核心协议）。
 *
 * Unity 模块分为两类：
 * - unity-launcher：可独立启动的 Unity Player Activity/Fragment
 * - unity-content：需要被宿主游戏或工具页嵌入的 Unity 内容包
 *
 * 当前为架构占位：实际集成 Unity SDK 时，实现类应持有 UnityPlayer 实例
 * 并处理生命周期、方向、音频焦点等事件。
 */
interface UnityModuleLauncher {

    /**
     * 模块唯一 ID。
     */
    fun getModuleId(): String

    /**
     * 启动独立的 Unity Activity。
     *
     * @param context 上下文
     * @param launchArgs 启动参数（如关卡、角色、配置等）
     * @return 是否成功启动
     */
    fun launchStandalone(context: Context, launchArgs: Map<String, String> = emptyMap()): Boolean

    /**
     * 创建可嵌入的 Unity Fragment。
     *
     * @param context 上下文
     * @param launchArgs 启动参数
     * @return androidx.fragment.app.Fragment 实例，实际集成时返回 UnityPlayerFragment
     */
    fun createEmbeddedFragment(context: Context, launchArgs: Map<String, String> = emptyMap()): Fragment?

    /**
     * 检查当前设备是否支持运行 Unity 内容。
     */
    fun isSupported(context: Context): Boolean
}
