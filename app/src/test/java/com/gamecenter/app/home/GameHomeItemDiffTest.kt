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

    // ===== 批次 2a：留存卡片稳定 ID =====

    @Test
    fun `留存卡片各自是单例稳定ID_互相不是同一项`() {
        val stats = GameHomeItem.QuickStatsRow("12 分钟", "连3天", "5")
        val challenge = GameHomeItem.DailyChallengeCard(
            entry = tile("g1").entry, title = "每日挑战", gameName = "g1",
            progressText = "进度：1 / 3", progressPercent = 33,
        )
        val streak = GameHomeItem.StreakSummaryCard("3 天", "7 天")
        assertTrue(diff.areItemsTheSame(stats, GameHomeItem.QuickStatsRow("0", "0", "0")))
        assertTrue(diff.areItemsTheSame(challenge, challenge.copy(progressPercent = 100)))
        assertTrue(diff.areItemsTheSame(streak, GameHomeItem.StreakSummaryCard("0 天", "0 天")))
        assertFalse(diff.areItemsTheSame(stats, challenge))
        assertFalse(diff.areItemsTheSame(challenge, streak))
    }

    @Test
    fun `快览统计数值变化_内容不同`() {
        val a = GameHomeItem.QuickStatsRow("12 分钟", "连3天", "5")
        val b = GameHomeItem.QuickStatsRow("13 分钟", "连3天", "5")
        assertFalse(diff.areContentsTheSame(a, b))
    }

    @Test
    fun `每日挑战进度变化_内容不同`() {
        val a = GameHomeItem.DailyChallengeCard(
            entry = null, title = "每日挑战", gameName = "g1",
            progressText = "进度：1 / 3", progressPercent = 33,
        )
        val b = a.copy(progressText = "进度：2 / 3", progressPercent = 66)
        assertFalse(diff.areContentsTheSame(a, b))
    }

    @Test
    fun `连胜概览数值变化_内容不同`() {
        val a = GameHomeItem.StreakSummaryCard("3 天", "7 天")
        val b = GameHomeItem.StreakSummaryCard("4 天", "7 天")
        assertTrue(diff.areItemsTheSame(a, b))
        assertFalse(diff.areContentsTheSame(a, b))
    }

    // ===== 批次 2b：最近成就横幅 / 今日精选 稳定 ID =====

    @Test
    fun `最近成就横幅是单例稳定ID_与其他行不是同一项`() {
        val banner = GameHomeItem.RecentAchievementBanner("连胜大师", "连续胜利 10 次")
        assertTrue(diff.areItemsTheSame(banner, GameHomeItem.RecentAchievementBanner("x", "")))
        assertFalse(diff.areItemsTheSame(banner, tile("g1")))
        assertFalse(diff.areItemsTheSame(banner, GameHomeItem.StreakSummaryCard("3 天", "7 天")))
    }

    @Test
    fun `最近成就横幅文案变化_内容不同`() {
        val a = GameHomeItem.RecentAchievementBanner("连胜大师", "连续胜利 10 次")
        val b = GameHomeItem.RecentAchievementBanner("贪吃蛇新手", "初次通关")
        assertTrue(diff.areItemsTheSame(a, b))
        assertFalse(diff.areContentsTheSame(a, b))
    }

    @Test
    fun `今日精选是单例稳定ID_与其他行不是同一项`() {
        val featured = GameHomeItem.TodayFeaturedCard(tile("g1").entry, "经典打砖块")
        assertTrue(
            diff.areItemsTheSame(featured, GameHomeItem.TodayFeaturedCard(tile("g2").entry, ""))
        )
        assertFalse(diff.areItemsTheSame(featured, tile("g1")))
        assertFalse(diff.areItemsTheSame(featured, GameHomeItem.RecentAchievementBanner("x", "")))
    }

    @Test
    fun `今日精选描述变化_内容不同`() {
        val a = GameHomeItem.TodayFeaturedCard(tile("g1").entry, "经典打砖块")
        val b = GameHomeItem.TodayFeaturedCard(tile("g1").entry, "另一种描述")
        assertTrue(diff.areItemsTheSame(a, b))
        assertFalse(diff.areContentsTheSame(a, b))
    }

    @Test
    fun `时长提醒档位变化_内容不同`() {
        val mild = GameHomeItem.HealthReminder("今日已游玩 73 分钟", "建议起身活动 5 分钟")
        val severe = GameHomeItem.HealthReminder(
            "今日已游玩 130 分钟", "长时间游戏可能影响视力", severe = true
        )
        assertTrue(diff.areItemsTheSame(mild, severe))
        assertFalse(diff.areContentsTheSame(mild, severe))
    }

    @Test
    fun `展开标题收起文案变化_内容不同`() {
        val a = GameHomeItem.SectionHeader("最近玩过", expandable = true, expanded = true, collapseText = "收起")
        val b = GameHomeItem.SectionHeader("最近玩过", expandable = true, expanded = true, collapseText = "Collapse")
        assertTrue(diff.areItemsTheSame(a, b))
        assertFalse(diff.areContentsTheSame(a, b))
    }
}
