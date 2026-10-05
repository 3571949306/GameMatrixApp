package com.gamecenter.app.home

import com.gamecenter.app.games.GameRegistry

/** 单 RecyclerView 的行类型（计划 §6.6）。全部 span 的行由 LayoutPolicy 给 spanSize=满。 */
sealed class GameHomeItem {
    /** 健康提醒（仅时长达阈值时出现，低强调、不常驻）。 */
    data class HealthReminder(val text: String) : GameHomeItem()

    /** 继续玩：仅一个、仅在可恢复时出现。 */
    data class ContinueRow(
        override val entry: GameRegistry.Entry,
        override val lastPlayedText: String
    ) : GameHomeItem(), EntryRow

    /** 区块标题（最近玩过可展开）。 */
    data class SectionHeader(
        val title: String,
        val expandable: Boolean = false,
        val expanded: Boolean = false
    ) : GameHomeItem()

    /** 最近玩过单行。 */
    data class RecentRow(
        override val entry: GameRegistry.Entry,
        override val lastPlayedText: String
    ) : GameHomeItem(), EntryRow

    /** 继续行与最近行的公共访问器（Adapter 共用绑定）。 */
    interface EntryRow {
        val entry: GameRegistry.Entry
        val lastPlayedText: String
    }

    /** 快览统计行（批次 2a）：今日时长/连胜/成就三格，值由 ViewModel 用资源格式化。 */
    data class QuickStatsRow(
        val playtimeText: String,
        val streakText: String,
        val achievementsText: String,
    ) : GameHomeItem()

    /**
     * 每日挑战卡（批次 2a）：今日目标 + 进度条，点击直达挑战游戏。
     * entry 为 null（如解锁成就型挑战未绑定游戏）时由 Fragment 提示兜底。
     */
    data class DailyChallengeCard(
        val entry: GameRegistry.Entry?,
        val title: String,
        val gameName: String,
        val progressText: String,
        val progressPercent: Int,
    ) : GameHomeItem()

    /** 连胜概览卡（批次 2a）：当前连胜/最佳连胜（StreakTracker）。 */
    data class StreakSummaryCard(
        val currentDaysText: String,
        val bestDaysText: String,
    ) : GameHomeItem()

    /** 空状态（附可选操作文案；action 为空表示无操作）。 */
    data class EmptyState(val message: String, val action: String? = null) : GameHomeItem()

    /** 紧凑游戏 Tile（只显示图标与名称，最多 2 行）。 */
    data class GameTile(val entry: GameRegistry.Entry, val isFavorite: Boolean) : GameHomeItem()
}
