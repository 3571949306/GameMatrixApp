package com.gamecenter.app.ui

import android.content.Context
import com.gamecenter.app.core.threading.AppExecutors
import com.gamecenter.app.games.GameRegistry
import com.gamecenter.app.games.GameUsageStore
import com.gamecenter.app.games.RecentGamesManager

/**
 * Batch 12-1 (HOME_RESUME_GAME_CARD): 首页"继续游玩"卡片数据助手。
 *
 * 数据来源：
 * - 优先使用 [RecentGamesManager.getRecentIds]（Feature C / HOME_REVAMP 已写入，覆盖所有 launchGame 调用）
 * - 时间戳通过 [GameUsageStore.getLastPlayedAt] 获取（若 BaseGameActivity 调用了 recordLaunch 则有值，否则返回 0）
 *
 * 提供"获取最近可恢复游戏 Entry"与"格式化相对时间"两个核心 API，Java 友好 singleton。
 */
object ResumeGameHelper {

    /**
     * 获取最近一次游玩的游戏 Entry，若没有任何游玩记录则返回 null。
     */
    @JvmStatic
    fun getResumeEntry(context: Context): GameRegistry.Entry? {
        val recentIds = RecentGamesManager.getInstance(context).recentIds
        if (recentIds.isEmpty()) return null
        val gameId = recentIds[0]
        // 在 GameRegistry 中查找对应 Entry
        for (category in GameRegistry.getCategories(context)) {
            for (entry in category.games) {
                if (entry.id == gameId) return entry
            }
        }
        return null
    }

    /**
     * 异步获取上次游玩到现在的时间间隔（人类可读，例如"3 小时前" / "刚刚"）。
     * 回调在主线程执行。若该游戏无 last_played 记录，回调 "刚刚"。
     */
    @JvmStatic
    fun getRelativeTimeSpanAsync(context: Context, gameId: String, callback: (String) -> Unit) {
        AppExecutors.io().execute {
            try {
                val store = GameUsageStore(context.applicationContext)
                val lastPlayed = store.getLastPlayedAt(gameId)
                val result: String
                if (lastPlayed <= 0L) {
                    result = "刚刚"
                } else {
                    val deltaMs = System.currentTimeMillis() - lastPlayed
                    val minutes = deltaMs / 60000L
                    result = when {
                        minutes < 1L -> "刚刚"
                        minutes < 60L -> "${minutes} 分钟前"
                        minutes < 1440L -> "${minutes / 60L} 小时前"
                        else -> "${minutes / 1440L} 天前"
                    }
                }
                AppExecutors.runOnMain { callback(result) }
            } catch (e: Exception) {
                android.util.Log.w("ResumeGameHelper", "获取相对时间失败", e)
                AppExecutors.runOnMain { callback("刚刚") }
            }
        }
    }

    /**
     * @deprecated 同步版本在主线程执行 Room 查询会导致 ANR/崩溃，请使用 {@link #getRelativeTimeSpanAsync}
     */
    @Deprecated("同步版本在主线程执行 Room 查询会导致 ANR/崩溃，请使用 getRelativeTimeSpanAsync", ReplaceWith("getRelativeTimeSpanAsync(context, gameId) { result -> /* handle result */ }"))
    @JvmStatic
    fun getRelativeTimeSpan(context: Context, gameId: String): String {
        // 仅保留用于兼容旧调用，实际应迁移到 getRelativeTimeSpanAsync
        val store = GameUsageStore(context)
        val lastPlayed = store.getLastPlayedAt(gameId)
        if (lastPlayed <= 0L) return "刚刚"
        val deltaMs = System.currentTimeMillis() - lastPlayed
        val minutes = deltaMs / 60000L
        return when {
            minutes < 1L -> "刚刚"
            minutes < 60L -> "${minutes} 分钟前"
            minutes < 1440L -> "${minutes / 60L} 小时前"
            else -> "${minutes / 1440L} 天前"
        }
    }
}
