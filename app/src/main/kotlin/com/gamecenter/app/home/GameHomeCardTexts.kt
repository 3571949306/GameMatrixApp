package com.gamecenter.app.home

import android.content.res.Resources
import com.gamecenter.app.R
import com.gamecenter.app.games.GameRegistry
import com.gamecenter.app.games.achievement.DailyChallengeManager
import com.gamecenter.app.ui.PlaytimeReminderHelper

/**
 * 留存卡片文案格式化（批次 2a）：只做「纯值 → 本地化文案」转换，无状态、无 IO。
 * 数据获取在 [GameHomeRepository]（后台线程），行编排在 [GameHomeStateBuilder]；
 * 本类持有 Resources 只读引用由 ViewModel 在主线程调用（getString 线程安全且开销极小）。
 */
object GameHomeCardTexts {

    private const val MILLIS_PER_SECOND = 1_000L
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MINUTES_PER_HOUR = 60L
    private const val MINUTES_PER_DAY = 24L * MINUTES_PER_HOUR
    private const val PLAYTIME_MINUTE_SECONDS = 60L
    private const val PERCENT_MAX = 100

    /**
     * 相对时间文案（口径与 ResumeGameHelper 一致，但全部走资源，
     * 修复 legacy 同步版硬编码中文的问题）。
     */
    fun relativeTime(
        res: Resources,
        lastPlayedAtMs: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): String {
        if (lastPlayedAtMs <= 0L) return res.getString(R.string.home_time_just_now)
        val minutes = (nowMs - lastPlayedAtMs) / MILLIS_PER_MINUTE
        return when {
            minutes < 1L -> res.getString(R.string.home_time_just_now)
            minutes < MINUTES_PER_HOUR -> res.getString(R.string.home_time_minutes_ago, minutes)
            minutes < MINUTES_PER_DAY -> res.getString(
                R.string.home_time_hours_ago, minutes / MINUTES_PER_HOUR
            )
            else -> res.getString(R.string.home_time_days_ago, minutes / MINUTES_PER_DAY)
        }
    }

    /** 快览统计行：今日时长（分/秒/零态三档）+ 连胜 + 已解锁成就数。 */
    fun quickStats(res: Resources, snapshot: GameHomeRepository.Snapshot): GameHomeItem.QuickStatsRow {
        val seconds = snapshot.todayPlayTimeMs / MILLIS_PER_SECOND
        val playtimeText = when {
            seconds >= PLAYTIME_MINUTE_SECONDS ->
                res.getString(R.string.home_stats_playtime_format, (seconds / MINUTES_PER_HOUR).toString())
            seconds > 0L -> res.getString(R.string.home_stats_playtime_seconds_format, seconds)
            else -> res.getString(R.string.home_stats_playtime_zero)
        }
        return GameHomeItem.QuickStatsRow(
            playtimeText = playtimeText,
            streakText = res.getString(R.string.home_stats_streak_format, snapshot.streakDays),
            achievementsText = snapshot.unlockedAchievements.toString(),
        )
    }

    /**
     * 每日挑战卡：状态标题（完成/进行中）+ 挑战游戏名 + 进度文案与百分比。
     * [entry] 为挑战绑定的游戏条目，未绑定（如解锁成就型挑战）时为 null。
     */
    fun dailyChallenge(
        res: Resources,
        challenge: DailyChallengeManager.Challenge,
        entry: GameRegistry.Entry?,
    ): GameHomeItem.DailyChallengeCard {
        val percent = if (challenge.target > 0) {
            (challenge.progress * PERCENT_MAX / challenge.target).coerceAtMost(PERCENT_MAX)
        } else {
            0
        }
        val gameName = challenge.gameName ?: ""
        return GameHomeItem.DailyChallengeCard(
            entry = entry,
            title = res.getString(
                if (challenge.completed) R.string.daily_challenge_completed else R.string.daily_challenge
            ),
            gameName = if (gameName.isEmpty()) res.getString(R.string.daily_challenge) else gameName,
            progressText = res.getString(
                R.string.daily_challenge_progress_format, challenge.progress, challenge.target
            ),
            progressPercent = percent,
        )
    }

    /** 连胜概览卡：当前连胜/最佳连胜数值（标签由布局静态资源承载）。 */
    fun streakSummary(
        res: Resources,
        currentDays: Int,
        bestDays: Int,
    ): GameHomeItem.StreakSummaryCard = GameHomeItem.StreakSummaryCard(
        currentDaysText = res.getString(R.string.daily_checkin_unit_days, currentDays),
        bestDaysText = res.getString(R.string.daily_checkin_unit_days, bestDays),
    )

    /**
     * 今日时长提醒卡（批次 2b 升级为 legacy Batch 11-3 卡片视觉）：
     * NONE → null（不渲染）；MILD/SEVERE → 今日分钟数 + 对应档位建议文案。
     * 档位判定（含会话 dismiss）由 [com.gamecenter.app.ui.PlaytimeReminderHelper.evaluate]
     * 在 Repository 后台完成，本函数只做纯值格式化。
     */
    fun playtimeReminder(
        res: Resources,
        level: PlaytimeReminderHelper.Level,
        todayMinutes: Int,
    ): GameHomeItem.HealthReminder? {
        if (level == PlaytimeReminderHelper.Level.NONE) return null
        val severe = level == PlaytimeReminderHelper.Level.SEVERE
        return GameHomeItem.HealthReminder(
            text = res.getString(R.string.playtime_reminder_today_format, "$todayMinutes"),
            desc = res.getString(
                if (severe) R.string.playtime_reminder_desc_120 else R.string.playtime_reminder_desc_60
            ),
            severe = severe,
        )
    }
}
