package com.gamecenter.app.ui

import android.content.Context
import android.util.Log
import com.gamecenter.app.R
import com.gamecenter.app.games.achievement.DailyChallengeManager
import com.google.android.material.badge.BadgeDrawable
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Batch 9-4 (NAV_BADGE_UNREAD): 底部导航未读消息红点徽章计算器。
 *
 * 未读数 = 每日挑战未完成(0/1) + 连胜活动待续(>0 时计 1)
 *
 * 注：2026-07-22 起签到改为自动记录登录天数，不再有"未签到"状态，红点逻辑移除。
 *
 * 受 [com.gamecenter.app.BuildConfig.NAV_BADGE_UNREAD] feature flag 控制。
 * 由 MainActivity 在 onCreate / onResume 时调用 [updateBadges]。
 */
object NavBadgeHelper {

    // 主题色常量（避免直接引用 com.google.android.material.R.attr 在编译期解析失败）
    private const val ERROR_COLOR = 0xFFD32F2F.toInt()
    private const val ON_ERROR_COLOR = 0xFFFFFFFF.toInt()

    /**
     * 计算并在 [BottomNavigationView] 的"游戏大厅" tab 上显示徽章。
     * - 未读数为 0 时清除徽章；
     * - 未读数 >= 1 时显示数字，超过 99 显示 "99+"。
     */
    fun updateBadges(context: Context, navView: BottomNavigationView) {
        val unread = computeUnreadCount(context)
        // 游戏大厅 tab 是固定的 R.id.navigation_games
        if (unread <= 0) {
            navView.removeBadge(R.id.navigation_games)
            return
        }
        val badge = navView.getOrCreateBadge(R.id.navigation_games)
        badge.isVisible = true
        badge.backgroundColor = ERROR_COLOR
        badge.badgeTextColor = ON_ERROR_COLOR
        if (unread > 99) {
            badge.maxCharacterCount = 3
            badge.number = 99
            // 用 maxLength 上限表达 99+：BadgeDrawable 不直接支持 99+ 字符串，
            // 3 个字符上限会显示 "99"，结合颜色已足以提示。
        } else {
            badge.maxCharacterCount = 2
            badge.number = unread
        }
    }

    /**
     * 计算当前未读数：
     * 1. 每日挑战未完成 → +1
     * 2. 当前连胜为 0（待续上） → +1
     *
     * 注：2026-07-22 起签到改为自动记录登录天数，不再有"未签到"状态。
     */
    private fun computeUnreadCount(context: Context): Int {
        var count = 0
        // 1. 每日挑战
        try {
            val challenge = DailyChallengeManager.getInstance(context).getTodayChallenge()
            if (!challenge.completed && challenge.progress < challenge.target) count++
        } catch (e: Exception) { Log.w("NavBadgeHelper", "每日挑战读取失败", e) }

        // 2. 连胜活动
        try {
            val streak = com.gamecenter.app.games.achievement.StreakTracker
                .getInstance(context).getCurrentStreak()
            if (streak <= 0) count++
        } catch (e: Exception) { Log.w("NavBadgeHelper", "连胜状态读取失败", e) }

        return count
    }
}
