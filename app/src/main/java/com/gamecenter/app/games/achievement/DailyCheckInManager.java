package com.gamecenter.app.games.achievement;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 每日签到管理器（Feature: DAILY_CHECKIN）。
 *
 * <p>记录用户每日签到状态，支持连续签到奖励递增。</p>
 *
 * <p>记录字段：</p>
 * <ul>
 *   <li>{@link #KEY_LAST_CHECKIN_DATE} —— 上次签到日期（yyyy-MM-dd）</li>
 *   <li>{@link #KEY_CONSECUTIVE_DAYS} —— 当前连续签到天数</li>
 *   <li>{@link #KEY_TOTAL_POINTS} —— 累计签到积分</li>
 * </ul>
 *
 * <p>奖励规则：第 1 天 5 分，每多连续一天 +2 分，第 11 天起封顶 25 分。
 * 公式：奖励 = 5 + min(连胜-1, 10) * 2。</p>
 *
 * <p>所有状态写入 SharedPreferences（异步 apply），不依赖数据库，
 * 升级不会丢失签到记录。风格参考 {@link DailyChallengeManager} 与 {@link StreakTracker}。</p>
 */
public class DailyCheckInManager {

    private static final String PREF_NAME = "daily_checkin";
    private static final String KEY_LAST_CHECKIN_DATE = "last_checkin_date";
    private static final String KEY_CONSECUTIVE_DAYS = "consecutive_days";
    private static final String KEY_TOTAL_POINTS = "total_points";
    private static final String KEY_TOTAL_CHECKIN_DAYS = "total_checkin_days";

    private static volatile DailyCheckInManager instance;

    private final SharedPreferences prefs;

    private DailyCheckInManager(@NonNull Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /** 获取单例。 */
    @NonNull
    public static DailyCheckInManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (DailyCheckInManager.class) {
                if (instance == null) {
                    instance = new DailyCheckInManager(context);
                }
            }
        }
        return instance;
    }

    /** 今日日期字符串（yyyy-MM-dd）。 */
    @NonNull
    public static String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /** 今天是否已签到。 */
    public boolean isCheckedInToday() {
        return todayKey().equals(prefs.getString(KEY_LAST_CHECKIN_DATE, ""));
    }

    /** 当前连续签到天数。 */
    public int getConsecutiveDays() {
        return prefs.getInt(KEY_CONSECUTIVE_DAYS, 0);
    }

    /** 累计签到积分。 */
    public int getTotalPoints() {
        return prefs.getInt(KEY_TOTAL_POINTS, 0);
    }

    /** 累计签到总天数（不论是否连续）。 */
    public int getTotalCheckInDays() {
        return prefs.getInt(KEY_TOTAL_CHECKIN_DAYS, 0);
    }

    /**
     * 执行今日签到。
     *
     * <p>若今天已签到，返回失败结果；否则：</p>
     * <ul>
     *   <li>判断上次签到日期是否为昨天：是则连胜 +1，否则重置为 1</li>
     *   <li>奖励积分 = 5 + min(连胜-1, 10) * 2</li>
     *   <li>写入 prefs（apply 异步）</li>
     *   <li>返回 {@link CheckInResult}（success=true）</li>
     * </ul>
     *
     * @return 签到结果
     */
    @NonNull
    public CheckInResult checkInToday() {
        CheckInResult result = new CheckInResult();
        String today = todayKey();
        String last = prefs.getString(KEY_LAST_CHECKIN_DATE, "");

        if (today.equals(last)) {
            // 今天已签到，返回失败
            result.success = false;
            return result;
        }

        boolean wasReset = false;
        int consecutive;
        if (!last.isEmpty() && isYesterday(last, today)) {
            // 昨天签过，连胜延续
            consecutive = getConsecutiveDays() + 1;
        } else {
            // 中断或首次，重置为 1
            consecutive = 1;
            // 有上次记录但不是昨天 → 真正的"中断重置"；首次签到不算
            wasReset = !last.isEmpty();
        }

        int reward = 5 + Math.min(consecutive - 1, 10) * 2;
        int total = getTotalPoints() + reward;
        int totalDays = getTotalCheckInDays() + 1;

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_LAST_CHECKIN_DATE, today);
        editor.putInt(KEY_CONSECUTIVE_DAYS, consecutive);
        editor.putInt(KEY_TOTAL_POINTS, total);
        editor.putInt(KEY_TOTAL_CHECKIN_DAYS, totalDays);
        editor.apply();

        result.success = true;
        result.points = reward;
        result.consecutiveDays = consecutive;
        result.wasReset = wasReset;
        return result;
    }

    // ==================== 内部 ====================

    /** 判断 lhs 是否是 rhs 的前一天。参考 {@link StreakTracker#isYesterday}。 */
    private boolean isYesterday(@NonNull String lhs, @NonNull String rhs) {
        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date d1 = fmt.parse(lhs);
            Date d2 = fmt.parse(rhs);
            if (d1 == null || d2 == null) return false;
            long diff = d2.getTime() - d1.getTime();
            long oneDay = 24L * 60 * 60 * 1000;
            return diff > 0 && diff <= oneDay;
        } catch (Exception e) {
            return false;
        }
    }

    /** 签到结果快照。 */
    public static class CheckInResult {
        /** 是否签到成功（今天首次签到才成功）。 */
        public boolean success;
        /** 本次签到获得的积分。 */
        public int points;
        /** 签到后的连续天数。 */
        public int consecutiveDays;
        /** 是否发生中断重置（首次签到不算重置）。 */
        public boolean wasReset;
    }
}
