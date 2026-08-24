package com.gamecenter.app.ui

import android.content.Context
import com.gamecenter.app.core.threading.AppExecutors

/**
 * Batch 12-2 (ACHIEVEMENT_RECENT_UNLOCKED_BANNER): 首页"最近解锁成就"横幅数据助手。
 *
 * 从 Room achievements 表查询所有已解锁成就，取 unlockedAt 最大的那个，
 * 作为"最近解锁"成就返回。
 *
 * 提供 dismissForSession（当日不再显示）和 getRecent 两个核心 API，Java 友好 singleton。
 */
object RecentAchievementHelper {

    private const val SESSION_PREFS = "home_recent_achievement_session"
    private const val KEY_DISMISS_DATE = "dismiss_date"

    data class RecentAchievement(
        val id: String,
        val unlockedAt: Long
    )

    /**
     * 异步获取最近解锁的成就，回调在主线程执行。
     * 若无任何已解锁成就或查询失败，回调传 null。
     */
    @JvmStatic
    fun getRecentAsync(context: Context, callback: (RecentAchievement?) -> Unit) {
        AppExecutors.io().execute {
            try {
                val dao = com.gamecenter.app.database.AppDatabase.getDatabase(context.applicationContext).achievementDao()
                val unlocked = dao.getUnlockedSync()
                val result = if (unlocked.isEmpty()) {
                    null
                } else {
                    val best = unlocked.maxByOrNull { it.unlockedAt }
                    if (best == null || best.unlockedAt <= 0L) null
                    else RecentAchievement(best.achievementId, best.unlockedAt)
                }
                AppExecutors.runOnMain { callback(result) }
            } catch (e: Exception) {
                android.util.Log.w("RecentAchievementHelper", "查询最近成就失败", e)
                AppExecutors.runOnMain { callback(null) }
            }
        }
    }

    /**
     * @deprecated 同步版本在主线程执行 Room 查询会导致 ANR/崩溃，请使用 {@link #getRecentAsync}
     */
    @Deprecated("同步版本在主线程执行 Room 查询会导致 ANR/崩溃，请使用 getRecentAsync", ReplaceWith("getRecentAsync(context) { result -> /* handle result */ }"))
    @JvmStatic
    fun getRecent(context: Context): RecentAchievement? {
        // 仅保留用于兼容旧调用，实际应迁移到 getRecentAsync
        try {
            val dao = com.gamecenter.app.database.AppDatabase.getDatabase(context).achievementDao()
            val unlocked = dao.getUnlockedSync()
            if (unlocked.isEmpty()) return null
            val best = unlocked.maxByOrNull { it.unlockedAt }
            if (best == null || best.unlockedAt <= 0L) return null
            return RecentAchievement(best.achievementId, best.unlockedAt)
        } catch (e: Exception) {
            android.util.Log.w("RecentAchievementHelper", "查询最近成就失败", e)
            return null
        }
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
