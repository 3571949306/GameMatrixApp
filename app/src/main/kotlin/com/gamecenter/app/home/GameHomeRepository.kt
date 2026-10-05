package com.gamecenter.app.home

import android.content.Context
import com.gamecenter.app.database.AppDatabase
import com.gamecenter.app.games.GameRegistry
import com.gamecenter.app.games.GameUsageStore
import com.gamecenter.app.games.RecentGamesManager
import com.gamecenter.app.games.achievement.DailyChallengeManager
import com.gamecenter.app.games.achievement.StreakTracker
import com.gamecenter.app.ui.ResumeGameHelper

/**
 * 聚合既有真源（计划 §6.4 + 批次 2a）：只读 GameRegistry / RecentGamesManager /
 * ResumeGameHelper / GameUsageStore / StreakTracker / DailyChallengeManager /
 * AppDatabase，不新建第二套存储，不做主线程 IO。
 * 调用方（ViewModel）负责在后台线程执行 [snapshot]。
 */
class GameHomeRepository(private val context: Context) {

    /** 一次一致的真源快照。 */
    data class Snapshot(
        val entries: List<GameRegistry.Entry>,
        val recentIds: List<String>,
        val categories: List<Pair<String, String>>,
        /** 最近条目的 last_played 时间戳（相对时间文案由 ViewModel 按资源格式化）。 */
        val lastPlayedAtById: Map<String, Long>,
        val favoriteIds: Set<String>,
        val resumeEntry: GameRegistry.Entry?,
        val todayPlayTimeMs: Long,
        /** 当前连胜天数（StreakTracker）。 */
        val streakDays: Int,
        /** 最佳连胜天数（StreakTracker）。 */
        val bestStreakDays: Int,
        /** 已解锁成就数（Room achievements 表；读失败降级 0）。 */
        val unlockedAchievements: Int,
        /** 今日挑战快照（DailyChallengeManager；读失败为 null → 卡片静默降级）。 */
        val todayChallenge: DailyChallengeManager.Challenge?,
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

        val lastPlayedAtById = recentIds.associateWith { usage.getLastPlayedAt(it) }

        val tracker = StreakTracker.getInstance(context)
        val unlockedAchievements = runCatching {
            AppDatabase.getDatabase(context.applicationContext)
                .achievementDao().getUnlockedCountSync()
        }.getOrDefault(0)
        val todayChallenge = runCatching {
            DailyChallengeManager.getInstance(context).getTodayChallenge()
        }.getOrNull()

        return Snapshot(
            entries = entries,
            recentIds = recentIds,
            categories = categories.map {
                com.gamecenter.app.home.GameDisplayNames.categoryName(context, it.categoryKey, it.name)
                    .let { n -> it.categoryKey to n }
            },
            lastPlayedAtById = lastPlayedAtById,
            favoriteIds = favoriteIds,
            resumeEntry = resume,
            todayPlayTimeMs = usage.getTodayPlayTimeMs(),
            streakDays = tracker.currentStreak,
            bestStreakDays = tracker.bestStreak,
            unlockedAchievements = unlockedAchievements,
            todayChallenge = todayChallenge,
        )
    }
}
