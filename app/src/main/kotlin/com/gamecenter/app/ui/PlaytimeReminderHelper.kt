package com.gamecenter.app.ui

import android.content.Context
import com.gamecenter.app.games.GameUsageStore

/**
 * 首页今日时长提醒辅助器（Batch 11-3 / HOME_PLAYTIME_REMINDER）。
 *
 * 根据今日累计游玩时长返回当前应展示的提醒档位：
 * - [Level.NONE]：不足 60 分钟，不显示卡片
 * - [Level.MILD]：60~119 分钟，橙色提示
 * - [Level.SEVERE]：≥120 分钟，红色提示
 *
 * 用户主动点击"知道了"或"再玩一会儿"后，本会话内不再显示（通过内存 flag 控制，
 * 不持久化，重启 App 后会重新评估）。
 */
object PlaytimeReminderHelper {

    enum class Level(val minutesThreshold: Int) {
        NONE(0),
        MILD(60),
        SEVERE(120)
    }

    @Volatile
    private var dismissedInSession: Boolean = false

    /** 用户主动关闭提醒卡片后调用，本会话内不再显示。 */
    fun dismissForSession() {
        dismissedInSession = true
    }

    /** 重置会话标记（App 重启后调用）。 */
    fun resetSession() {
        dismissedInSession = false
    }

    /**
     * 评估当前提醒档位。若用户本会话已关闭，返回 [Level.NONE]。
     */
    fun evaluate(context: Context): Level {
        if (dismissedInSession) return Level.NONE
        val todayMs = GameUsageStore(context).todayPlayTimeMs
        val minutes = (todayMs / 60_000L).toInt()
        return when {
            minutes >= Level.SEVERE.minutesThreshold -> Level.SEVERE
            minutes >= Level.MILD.minutesThreshold -> Level.MILD
            else -> Level.NONE
        }
    }

    /** 当前今日累计分钟数（提供给 UI 显示文案）。 */
    fun todayMinutes(context: Context): Int {
        return (GameUsageStore(context).todayPlayTimeMs / 60_000L).toInt()
    }
}
