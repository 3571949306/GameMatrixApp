package com.gamecenter.app.features

import androidx.fragment.app.Fragment

/**
 * 游戏大厅入口单一真源（docs/游戏中心主页面重做执行计划_2026-08-30.md §6.1）。
 *
 * 优先级（从高到低）：
 * 1. [Options.dynamicGamesHall] —— 动态模块大厅（P4）
 * 2. [Options.libraryRevamp] —— 游戏库主页重做（本轮）
 * 3. 旧链（回退）：GamesFragment（V2 布局）
 *
 * BottomNavigationManager 与 BuiltInGamesHallModuleEntryPoint 都必须只经由
 * [createFragment] 创建，禁止在调用点复制优先级判断（G1 单一真源）。
 */
object GamesHallDestinationFactory {

    enum class Destination { DYNAMIC_GAMES_HALL, GAME_LIBRARY, LEGACY_GAMES }

    /** 纯数据选项，便于单元测试与调用方解耦。 */
    data class Options(
        val dynamicGamesHall: Boolean,
        val libraryRevamp: Boolean,
    )

    /** 纯函数入口策略：三选一，可单测。 */
    fun resolve(options: Options): Destination = when {
        options.dynamicGamesHall -> Destination.DYNAMIC_GAMES_HALL
        options.libraryRevamp -> Destination.GAME_LIBRARY
        else -> Destination.LEGACY_GAMES
    }

    /** 由入口策略创建 Fragment（单一创建点）。 */
    fun createFragment(options: Options): Fragment = when (resolve(options)) {
        Destination.DYNAMIC_GAMES_HALL ->
            com.gamecenter.app.features.DynamicGamesHallFragment()
        Destination.GAME_LIBRARY ->
            com.gamecenter.app.home.GameLibraryFragment()
        Destination.LEGACY_GAMES ->
            com.gamecenter.app.GamesFragment()
    }
}
