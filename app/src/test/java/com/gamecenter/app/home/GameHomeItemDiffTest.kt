package com.gamecenter.app.home

import com.gamecenter.app.games.GameRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** DiffUtil 稳定 ID 与内容对比测试（计划 §9.1 GameHomeItemDiffTest）。 */
class GameHomeItemDiffTest {

    private val diff = GameHomeAdapter.DIFF

    private fun tile(id: String, fav: Boolean = false) = GameHomeItem.GameTile(
        GameRegistry.Entry(id, 0, id, "", null, "casual", id), fav
    )

    @Test
    fun `相同gameId视为同一项`() {
        assertTrue(diff.areItemsTheSame(tile("g1"), tile("g1")))
        assertFalse(diff.areItemsTheSame(tile("g1"), tile("g2")))
    }

    @Test
    fun `同id不同收藏状态_内容不同`() {
        assertFalse(diff.areContentsTheSame(tile("g1", fav = true), tile("g1", fav = false)))
    }

    @Test
    fun `同id同实例_内容相同`() {
        // GameRegistry.Entry 无 equals 覆写：同一实例视为内容相同（生产中同一 Registry
        // 快照里的 Entry 是同一对象；对象替换会走重绑，方向安全）
        val same = tile("g1")
        assertTrue(diff.areContentsTheSame(same, same))
    }

    @Test
    fun `不同行类型_即使内容相似也不是同一项`() {
        val row = GameHomeItem.RecentRow(
            GameRegistry.Entry("g1", 0, "g1", "", null, "casual", "g1"), "刚刚"
        )
        assertFalse(diff.areItemsTheSame(tile("g1"), row))
    }

    @Test
    fun `空状态文案变化_内容不同`() {
        val a = GameHomeItem.EmptyState("没有收藏", "查看全部游戏")
        val b = GameHomeItem.EmptyState("未找到相关游戏", "清除搜索")
        assertFalse(diff.areItemsTheSame(a, b))
        assertFalse(diff.areContentsTheSame(a, b))
    }
}
