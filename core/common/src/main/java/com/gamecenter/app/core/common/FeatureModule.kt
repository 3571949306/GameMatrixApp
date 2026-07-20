package com.gamecenter.app.core.common

import android.content.Context
import androidx.fragment.app.Fragment

/**
 * 可下载功能模块的接口（P0 核心协议）。
 *
 * 每个从模块商店下载的功能模块需实现此接口。
 * ModuleShellFragment 通过此接口获取模块的主 Fragment 进行展示。
 *
 * P0 增强：FeatureModule 可声明导航贡献、游戏贡献和权限需求。
 */
interface FeatureModule {

    /** 返回模块提供的主 Fragment */
    fun createFragment(context: Context): Fragment

    /**
     * 返回模块的导航贡献列表。
     * P0/P4：模块通过此接口声明自己希望在底部导航、游戏大厅、工具区等位置贡献入口。
     */
    fun getNavigationContributions(context: Context): List<ModuleNavigationContribution> = emptyList()

    /**
     * 返回模块声明的权限列表。
     * P0：模块可以声明运行所需的权限，由宿主统一申请。
     */
    fun getRequiredPermissions(): List<String> = emptyList()

    /**
     * 返回模块依赖的其他模块 ID 列表。
     * P0：宿主安装前会校验并自动安装依赖模块。
     */
    fun getDependencies(): List<String> = emptyList()

    /**
     * 返回模块类型标识，用于分类和过滤。
     */
    fun getModuleType(): String = "feature"

    /**
     * 返回模块是否需要加载完成后再启动。
     */
    fun shouldPreload(): Boolean = false

    /**
     * P6: 返回模块提供的 Unity 启动器（如适用）。
     *
     * 仅当模块 kind 为 unity-launcher 或 unity-content 时才需要实现。
     * 默认返回 null，表示该模块不是 Unity 模块。
     */
    fun createUnityLauncher(): UnityModuleLauncher? = null
}