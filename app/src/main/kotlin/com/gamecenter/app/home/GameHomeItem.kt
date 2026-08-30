package com.gamecenter.app.home

import com.gamecenter.app.games.GameRegistry

/** 单 RecyclerView 的行类型（计划 §6.6）。全部 span 的行由 LayoutPolicy 给 spanSize=满。 */
sealed class GameHomeItem {
    /** 健康提醒（仅时长达阈值时出现，低强调、不常驻）。 */
    data class HealthReminder(val text: String) : GameHomeItem()

    /** 继续玩：仅一个、仅在可恢复时出现。 */
    data class ContinueRow(val entry: GameRegistry.Entry, val lastPlayedText: String) : GameHomeItem()

    /** 区块标题（最近玩过可展开）。 */
    data class SectionHeader(
        val title: String,
        val expandable: Boolean = false,
        val expanded: Boolean = false
    ) : GameHomeItem()

    /** 最近玩过单行。 */
    data class RecentRow(val entry: GameRegistry.Entry, val lastPlayedText: String) : GameHomeItem()

    /** 空状态（附可选操作文案；action 为空表示无操作）。 */
    data class EmptyState(val message: String, val action: String? = null) : GameHomeItem()

    /** 紧凑游戏 Tile（只显示图标与名称，最多 2 行）。 */
    data class GameTile(val entry: GameRegistry.Entry, val isFavorite: Boolean) : GameHomeItem()
}
