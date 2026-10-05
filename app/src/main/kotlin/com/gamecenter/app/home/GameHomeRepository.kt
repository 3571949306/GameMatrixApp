package com.gamecenter.app.home

import android.content.Context
import com.gamecenter.app.database.AppDatabase
import com.gamecenter.app.games.GameRecommender
import com.gamecenter.app.games.GameRegistry
import com.gamecenter.app.games.GameUsageStore
import com.gamecenter.app.games.RecentGamesManager
import com.gamecenter.app.games.achievement.DailyChallengeManager
import com.gamecenter.app.games.achievement.StreakTracker
import com.gamecenter.app.ui.PlaytimeReminderHelper
import com.gamecenter.app.ui.RecentAchievementHelper
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
        /** 今日时长提醒档位（PlaytimeReminderHelper，含会话 dismiss；读失败降级 NONE）。 */
        val reminderLevel: PlaytimeReminderHelper.Level,
        /** 最近解锁成就（批次 2b：当日已 dismiss 或无数据为 null → 横幅静默降级）。 */
        val recentAchievement: RecentAchievementHelper.RecentAchievement?,
        /** 今日精选推荐（批次 2b：GameRecommender 偏好推荐，库空/失败为 null）。 */
        val todayFeaturedEntry: GameRegistry.Entry?,
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
        val reminderLevel = runCatching {
            PlaytimeReminderHelper.evaluate(context)
        }.getOrDefault(PlaytimeReminderHelper.Level.NONE)
        val recentAchievement = runCatching {
            if (RecentAchievementHelper.isDismissedToday(context)) null
            else RecentAchievementHelper.getRecentSync(context)
        }.getOrNull()
        val todayFeaturedEntry = runCatching {
            computeTodayFeatured(context, entries)
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
            reminderLevel = reminderLevel,
            recentAchievement = recentAchievement,
            todayFeaturedEntry = todayFeaturedEntry,
        )
    }

    /**
     * 今日精选（批次 2b，决策 b 方案）：与 legacy GamesFragment.refreshTodayFeatured 同口径，
     * 优先 GameRecommender 偏好推荐 Top1，失败回退日期 hash；跨日时把今日分类写为昨日分类
     * （沿用 legacy "daily_recommender" SP 的 yesterday_category/last_run_day 键，避免双键）。
     * 必须在后台线程调用。
     */
    private fun computeTodayFeatured(
        context: Context,
        entries: List<GameRegistry.Entry>,
    ): GameRegistry.Entry? {
        if (entries.isEmpty()) return null
        val cal = java.util.Calendar.getInstance()
        val dayKey = cal.get(java.util.Calendar.YEAR) * DAY_KEY_YEAR_FACTOR +
            (cal.get(java.util.Calendar.MONTH) + 1) * DAY_KEY_MONTH_FACTOR +
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        val prefs = context.getSharedPreferences(RECOMMENDER_PREFS, Context.MODE_PRIVATE)
        val todayKey = dayKey.toString()
        val lastRunDay = prefs.getString(KEY_LAST_RUN_DAY, "")
        val yesterdayCategory = prefs.getString(KEY_YESTERDAY_CATEGORY, null)
        val recommended = runCatching { GameRecommender.recommendOne(context, yesterdayCategory) }
            .getOrNull()
            ?: entries[((dayKey * FALLBACK_HASH_MULTIPLIER + FALLBACK_HASH_OFFSET) % entries.size).toInt()]
        if (todayKey != lastRunDay) {
            prefs.edit()
                .putString(KEY_YESTERDAY_CATEGORY, recommended.category)
                .putString(KEY_LAST_RUN_DAY, todayKey)
                .apply()
        }
        return recommended
    }

    private companion object {
        /** legacy refreshTodayFeatured 的推荐持久化（照抄键名避免双键）。 */
        private const val RECOMMENDER_PREFS = "daily_recommender"
        private const val KEY_YESTERDAY_CATEGORY = "yesterday_category"
        private const val KEY_LAST_RUN_DAY = "last_run_day"
        private const val DAY_KEY_YEAR_FACTOR = 10_000L
        private const val DAY_KEY_MONTH_FACTOR = 100L
        private const val FALLBACK_HASH_MULTIPLIER = 7L
        private const val FALLBACK_HASH_OFFSET = 3L
    }
}
