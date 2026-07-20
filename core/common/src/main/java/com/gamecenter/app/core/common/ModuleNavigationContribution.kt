package com.gamecenter.app.core.common

import android.content.Context
import androidx.fragment.app.Fragment

/**
 * 模块导航贡献接口。
 *
 * P0/P4 阶段核心协议：模块通过此接口声明自己希望在主界面底部导航栏、
 * 游戏大厅、工具区等位置贡献一个入口。
 *
 * 实现类必须提供一个稳定的入口点（通常是 Fragment），以及用于展示
 * 的标题、图标、顺序等元数据。
 */
interface ModuleNavigationContribution {

    /** 导航入口唯一 ID，通常与模块 ID 一致。 */
    fun getContributionId(): String

    /** 导航入口标题，用于底部导航栏文字。 */
    fun getTitle(context: Context): String

    /** 导航入口图标资源 ID，如果返回 0 表示使用默认图标。 */
    fun getIconResId(): Int

    /**
     * 导航入口排序权重，值越小越靠前。
     * 标准值：
     * - 游戏大厅：10
     * - 浏览器：20
     * - 工具箱：30
     * - AI 助手：40
     * - VPN：50
     * - 错题本：60
     * - 我的：100
     */
    fun getOrder(): Int

    /** 导航槽位，决定入口出现在哪里。 */
    fun getSlot(): NavigationSlot

    /**
     * 创建导航入口对应的主 Fragment。
     * 注意：Fragment 必须是无参构造的 androidx.fragment.app.Fragment。
     */
    fun createFragment(context: Context): Fragment

    /** 是否启用此导航贡献。 */
    fun isEnabled(): Boolean = true
}

/**
 * 导航槽位类型。
 */
enum class NavigationSlot {
    /** 底部导航栏 */
    BOTTOM_NAV,

    /** 游戏大厅内部的游戏卡片 */
    GAMES_HALL,

    /** 工具箱列表 */
    TOOLS_GRID,

    /** 商店分类列表 */
    STORE_CATEGORY,

    /** 设置页面入口 */
    SETTINGS
}
