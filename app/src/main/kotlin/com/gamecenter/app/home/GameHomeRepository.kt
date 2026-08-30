package com.gamecenter.app.home

import android.content.Context
import com.gamecenter.app.games.GameRegistry
import com.gamecenter.app.games.GameUsageStore
import com.gamecenter.app.games.RecentGamesManager
import com.gamecenter.app.ui.ResumeGameHelper

/**
 * 聚合既有真源（计划 §6.4）：只读 GameRegistry / RecentGamesManager /
 * ResumeGameHelper / GameUsageStore，不新建第二套存储，不做主线程 IO。
 * 调用方（ViewModel）负责在后台线程执行 [snapshot]。
 */
class GameHomeRepository(private val context: Context) {

    /** 一次一致的真源快照。 */
    data class Snapshot(
        val entries: List<GameRegistry.Entry>,
        val recentIds: List<String>,
        val categories: List<Pair<String, String>>,
        val lastPlayedTextById: Map<String, String>,
        val favoriteIds: Set<String>,
        val resumeEntry: GameRegistry.Entry?,
        val todayPlayTimeMs: Long,
    )

    fun snapshot(): Snapshot {
        val categories = GameRegistry.getCategories(context)
        val entries = GameRegistry.flatten(categories)

        val recentRaw = RecentGamesManager.getInstance(context).getRecentIds()
        // 失效最近 ID 过滤：Registry 里已不存在的条目不进入 UI（§4.2）
        val byId = entries.associateBy { it.id }
        val recentIds = recentRaw.filter { byId.containsKey(it) }

        val usage = GameUsageStore(context)
        val favoriteIds = buildSet {
            entries.forEach { e -> if (usage.isFavorite(e.id)) add(e.id) }
        }

        val resume = ResumeGameHelper.getResumeEntry(context)
            ?.takeIf { byId.containsKey(it.id) }

        val lastPlayedTextById = recentIds.associateWith {
            ResumeGameHelper.getRelativeTimeSpan(context, it)
        }

        return Snapshot(
            entries = entries,
            recentIds = recentIds,
            categories = categories.map { it.categoryKey to it.name },
            lastPlayedTextById = lastPlayedTextById,
            favoriteIds = favoriteIds,
            resumeEntry = resume,
            todayPlayTimeMs = usage.getTodayPlayTimeMs(),
        )
    }
}
