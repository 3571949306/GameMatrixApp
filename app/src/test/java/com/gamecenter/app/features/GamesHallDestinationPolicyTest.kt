package com.gamecenter.app.features

import org.junit.Assert.assertEquals
import org.junit.Test

/** 入口优先级纯逻辑测试：动态大厅 > 游戏库主页 > 旧 GamesFragment（计划 §9.1）。 */
class GamesHallDestinationPolicyTest {

    private fun resolve(dynamic: Boolean, library: Boolean) =
        GamesHallDestinationFactory.resolve(GamesHallDestinationFactory.Options(dynamic, library))

    @Test
    fun `动态大厅开关优先级最高`() {
        assertEquals(
            GamesHallDestinationFactory.Destination.DYNAMIC_GAMES_HALL,
            resolve(dynamic = true, library = false)
        )
        assertEquals(
            GamesHallDestinationFactory.Destination.DYNAMIC_GAMES_HALL,
            resolve(dynamic = true, library = true)
        )
    }

    @Test
    fun `游戏库主页次之`() {
        assertEquals(
            GamesHallDestinationFactory.Destination.GAME_LIBRARY,
            resolve(dynamic = false, library = true)
        )
    }

    @Test
    fun `默认回退旧 GamesFragment`() {
        assertEquals(
            GamesHallDestinationFactory.Destination.LEGACY_GAMES,
            resolve(dynamic = false, library = false)
        )
    }

    @Test
    fun `工厂产物与策略一致`() {
        assertEquals(
            "com.gamecenter.app.features.DynamicGamesHallFragment",
            GamesHallDestinationFactory.createFragment(
                GamesHallDestinationFactory.Options(true, false)
            )::class.java.name
        )
        assertEquals(
            "com.gamecenter.app.home.GameLibraryFragment",
            GamesHallDestinationFactory.createFragment(
                GamesHallDestinationFactory.Options(false, true)
            )::class.java.name
        )
        assertEquals(
            "com.gamecenter.app.GamesFragment",
            GamesHallDestinationFactory.createFragment(
                GamesHallDestinationFactory.Options(false, false)
            )::class.java.name
        )
    }
}
