package com.gamecenter.app.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 自适应列数策略测试（计划 §9.1 GameHomeLayoutPolicyTest）。 */
class GameHomeLayoutPolicyTest {

    @Test
    fun `320dp至少3列`() {
        assertEquals(3, GameHomeLayoutPolicy.spanCount(320, 1.0f))
    }

    @Test
    fun `超小屏280dp降为2列`() {
        assertTrue(GameHomeLayoutPolicy.spanCount(280, 1.0f) <= 2)
    }

    @Test
    fun `360dp默认4列`() {
        assertEquals(4, GameHomeLayoutPolicy.spanCount(360, 1.0f))
    }

    @Test
    fun `大字体降列`() {
        assertTrue(GameHomeLayoutPolicy.spanCount(360, 2.0f) <= 3)
        assertTrue(GameHomeLayoutPolicy.spanCount(411, 2.0f) <= 4)
    }

    @Test
    fun `600dp平板最多6列`() {
        val n = GameHomeLayoutPolicy.spanCount(600, 1.0f)
        assertTrue("600dp 应扩展到 6 列: $n", n in 4..6)
        val wide = GameHomeLayoutPolicy.spanCount(840, 1.0f)
        assertTrue(wide in 4..6)
    }

    @Test
    fun `全宽行占满span_Tile占1列`() {
        val tile = GameHomeItem.GameTile(
            com.gamecenter.app.games.GameRegistry.Entry("g", 0, "g", "", null, "c", "casual"),
            isFavorite = false
        )
        val header = GameHomeItem.SectionHeader("最近玩过")
        assertEquals(1, GameHomeLayoutPolicy.spanSizeFor(tile, 4))
        assertEquals(4, GameHomeLayoutPolicy.spanSizeFor(header, 4))
    }
}
