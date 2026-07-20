package com.gamecenter.app.ui

import android.content.Context

/**
 * Batch 12-2 (ACHIEVEMENT_RECENT_UNLOCKED_BANNER): 首页"最近解锁成就"横幅数据助手。
 *
 * 从 `game_achievements` SharedPreferences 遍历所有 `unlock_<id>` = true 的成就，
 * 取 `unlocked_at_<id>` 最大的那个，作为"最近解锁"成就返回。
 *
 * 提供 dismissForSession（当日不再显示）和 getRecent 两个核心 API，Java 友好 singleton。
 */
object RecentAchievementHelper {

    private const val PREFS_NAME = "game_achievements"
    private const val KEY_UNLOCK_PREFIX = "unlock_"
    private const val KEY_UNLOCKED_AT_PREFIX = "unlocked_at_"
    private const val SESSION_PREFS = "home_recent_achievement_session"
    private const val KEY_DISMISS_DATE = "dismiss_date"

    data class RecentAchievement(
        val id: String,
        val unlockedAt: Long
    )

    /**
     * 获取最近解锁的成就，若无任何已解锁成就则返回 null。
     */
    @JvmStatic
    fun getRecent(context: Context): RecentAchievement? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val all = prefs.all
        var bestId: String? = null
        var bestTime = 0L
        for ((key, value) in all) {
            if (!key.startsWith(KEY_UNLOCK_PREFIX)) continue
            if (value !is Boolean || !value) continue
            val id = key.removePrefix(KEY_UNLOCK_PREFIX)
            val unlockedAt = prefs.getLong(KEY_UNLOCKED_AT_PREFIX + id, 0L)
            if (unlockedAt > bestTime) {
                bestTime = unlockedAt
                bestId = id
            }
        }
        if (bestId == null || bestTime <= 0L) return null
        return RecentAchievement(bestId, bestTime)
    }

    /**
     * 当日不再显示。用单独的 SharedPreferences 记录 dismiss 日期。
     */
    @JvmStatic
    fun dismissForSession(context: Context) {
        val prefs = context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_DISMISS_DATE, todayDate())
            .apply()
    }

    /**
     * 判断今日是否已被 dismiss。
     */
    @JvmStatic
    fun isDismissedToday(context: Context): Boolean {
        val prefs = context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE)
        return todayDate() == prefs.getString(KEY_DISMISS_DATE, null)
    }

    /**
     * 解析成就标题（先查 `achievement_title_<id>` 资源，找不到则 fallback 到 id）。
     */
    @JvmStatic
    fun resolveTitle(context: Context, achievementId: String): String {
        val resId = context.resources.getIdentifier(
            "achievement_title_$achievementId", "string", context.packageName
        )
        return if (resId != 0) context.getString(resId) else achievementId
    }

    /**
     * 解析成就描述（先查 `achievement_desc_<id>` 资源，找不到则 fallback 到空串）。
     */
    @JvmStatic
    fun resolveDescription(context: Context, achievementId: String): String {
        val resId = context.resources.getIdentifier(
            "achievement_desc_$achievementId", "string", context.packageName
        )
        return if (resId != 0) context.getString(resId) else ""
    }

    private fun todayDate(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
    }
}
