package com.gamecenter.app.games.achievement;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 每日登录记录器（Feature: DAILY_CHECKIN，2026-07-22 起由"手动签到"改为"自动记录登录天数"）。
 *
 * <p>定位为单机 app 的轻量登录统计：用户打开应用即自动记录当天登录，
 * 无需手动点击签到按钮，避免增加用户粘性负担。</p>
 *
 * <p>记录字段：</p>
 * <ul>
 *   <li>{@link #KEY_LAST_CHECKIN_DATE} —— 上次登录日期（yyyy-MM-dd）</li>
 *   <li>{@link #KEY_CONSECUTIVE_DAYS} —— 当前连续登录天数</li>
 *   <li>{@link #KEY_TOTAL_CHECKIN_DAYS} —— 累计登录总天数</li>
 *   <li>{@link #KEY_BEST_CONSECUTIVE_DAYS} —— 最佳连续登录天数</li>
 * </ul>
 *
 * <p>所有状态写入 SharedPreferences（异步 apply），不依赖数据库，
 * 升级不会丢失登录记录。风格参考 {@link DailyChallengeManager} 与 {@link StreakTracker}。</p>
 *
 * <p>历史兼容：保留 {@link #KEY_TOTAL_POINTS} 字段读取（旧版本写入），
 * 但不再递增；{@link #getTotalPoints()} 返回历史值仅供迁移展示。</p>
 */
public class DailyCheckInManager {

    private static final String PREF_NAME = "daily_checkin";
    private static final String KEY_LAST_CHECKIN_DATE = "last_checkin_date";
    private static final String KEY_CONSECUTIVE_DAYS = "consecutive_days";
    private static final String KEY_TOTAL_POINTS = "total_points"; // 历史字段，不再递增
    private static final String KEY_TOTAL_CHECKIN_DAYS = "total_checkin_days";
    private static final String KEY_BEST_CONSECUTIVE_DAYS = "best_consecutive_days";

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

    /** 今天是否已记录登录。 */
    public boolean isCheckedInToday() {
        return todayKey().equals(prefs.getString(KEY_LAST_CHECKIN_DATE, ""));
    }

    /** 当前连续登录天数。 */
    public int getConsecutiveDays() {
        return prefs.getInt(KEY_CONSECUTIVE_DAYS, 0);
    }

    /** 最佳连续登录天数。 */
    public int getBestConsecutiveDays() {
        return prefs.getInt(KEY_BEST_CONSECUTIVE_DAYS, 0);
    }

    /** 累计登录总天数（不论是否连续）。 */
    public int getTotalCheckInDays() {
        return prefs.getInt(KEY_TOTAL_CHECKIN_DAYS, 0);
    }

    /**
     * 历史累计积分（仅旧版本签到写入，新版本不再递增）。
     * 保留读取以便成就中心/统计页迁移展示历史数据。
     */
    public int getTotalPoints() {
        return prefs.getInt(KEY_TOTAL_POINTS, 0);
    }

    /**
     * 自动记录今日登录（幂等）。
     *
     * <p>应在应用启动或用户进入游戏大厅时调用。同一天多次调用只会记录一次，
     * 不会重复增加登录天数。</p>
     *
     * <p>逻辑：</p>
     * <ul>
     *   <li>今天已记录 → 直接返回 false（无需更新）</li>
     *   <li>上次登录是昨天 → 连续登录 +1</li>
     *   <li>中断或首次 → 重置为 1</li>
     *   <li>更新最佳连续登录天数</li>
     *   <li>累计登录总天数 +1</li>
     * </ul>
     *
     * @return true 表示本次为今日首次登录记录；false 表示今天已记录过
     */
    public boolean recordLoginDay() {
        String today = todayKey();
        String last = prefs.getString(KEY_LAST_CHECKIN_DATE, "");

        if (today.equals(last)) {
            // 今天已记录，幂等返回
            return false;
        }

        int consecutive;
        if (!last.isEmpty() && isYesterday(last, today)) {
            // 昨天登录过，连胜延续
            consecutive = getConsecutiveDays() + 1;
        } else {
            // 中断或首次，重置为 1
            consecutive = 1;
        }

        int totalDays = getTotalCheckInDays() + 1;
        int best = getBestConsecutiveDays();
        if (consecutive > best) {
            best = consecutive;
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_LAST_CHECKIN_DATE, today);
        editor.putInt(KEY_CONSECUTIVE_DAYS, consecutive);
        editor.putInt(KEY_TOTAL_CHECKIN_DAYS, totalDays);
        editor.putInt(KEY_BEST_CONSECUTIVE_DAYS, best);
        editor.apply();

        return true;
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
}
