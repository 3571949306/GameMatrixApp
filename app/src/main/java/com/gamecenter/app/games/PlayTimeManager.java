package com.gamecenter.app.games;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * P2-9 (PLAY_TIME_MANAGEMENT): 游戏时长管理器。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>每日限额：用户可在设置中配置 0/30/60/90/120/180 分钟（0=关闭）</li>
 *   <li>超时检测：BaseGameActivity 启动时调用 {@link #checkLimitAndWarn} 检测是否超限</li>
 *   <li>周报告：聚合最近 7 天每日游玩时长，生成可读文本报告</li>
 * </ul>
 *
 * <p>数据源：复用 {@link GameUsageStore} 的 "daily_play_time_yyyy-MM-dd" 键。</p>
 */
public final class PlayTimeManager {

    private static final String PREFS_NAME = "play_time_limit";
    private static final String KEY_DAILY_LIMIT_MIN = "daily_limit_min";
    private static final String KEY_LAST_WARN_DATE = "last_warn_date";
    private static final String KEY_LAST_WEEKLY_REPORT_DATE = "last_weekly_report_date";

    /** 限额选项（分钟），0 表示关闭。 */
    public static final int[] LIMIT_OPTIONS = {0, 30, 60, 90, 120, 180, 240};

    private final Context context;
    private final SharedPreferences prefs;
    private final GameUsageStore usageStore;

    public PlayTimeManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.usageStore = new GameUsageStore(this.context);
    }

    /** 获取当前每日限额（分钟）。0 表示关闭。 */
    public int getDailyLimitMin() {
        return prefs.getInt(KEY_DAILY_LIMIT_MIN, 0);
    }

    /** 设置每日限额（分钟）。传 0 关闭。 */
    public void setDailyLimitMin(int minutes) {
        prefs.edit().putInt(KEY_DAILY_LIMIT_MIN, Math.max(0, minutes)).apply();
    }

    /** 是否启用了限额。 */
    public boolean isLimitEnabled() {
        return getDailyLimitMin() > 0;
    }

    /** 获取今日已游玩分钟数。 */
    public int getTodayPlayedMin() {
        return (int) (usageStore.getTodayPlayTimeMs() / 60000L);
    }

    /** 获取今日剩余分钟数（< 0 表示已超限）。限额关闭时返回 Integer.MAX_VALUE。 */
    public int getTodayRemainingMin() {
        if (!isLimitEnabled()) return Integer.MAX_VALUE;
        return getDailyLimitMin() - getTodayPlayedMin();
    }

    /**
     * 启动游戏前检测是否超限。返回非 null 表示应弹出警告对话框，返回 null 表示可正常进入。
     * <p>为避免一天弹多次，同一天只会弹一次警告（除非用户清除数据）。</p>
     */
    @Nullable
    public WarnResult checkLimitAndWarn() {
        if (!isLimitEnabled()) return null;
        int played = getTodayPlayedMin();
        int limit = getDailyLimitMin();
        if (played < limit) return null;

        String today = todayKey();
        String lastWarn = prefs.getString(KEY_LAST_WARN_DATE, "");
        if (today.equals(lastWarn)) return null; // 今天已弹过

        prefs.edit().putString(KEY_LAST_WARN_DATE, today).apply();
        return new WarnResult(played, limit, played - limit);
    }

    /** 生成最近 7 天周报告文本。 */
    @NonNull
    public WeeklyReport generateWeeklyReport() {
        WeeklyReport report = new WeeklyReport();
        List<String> dateKeys = usageStore.getRecentDateKeys(7);
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd EEE", Locale.CHINA);
        Calendar cal = Calendar.getInstance();
        long total = 0L;
        long max = 0L;
        String maxDate = "";
        int activeDays = 0;

        for (int i = 0; i < dateKeys.size(); i++) {
            cal.add(Calendar.DAY_OF_YEAR, -(dateKeys.size() - 1 - i));
            String label = sdf.format(cal.getTime());
            cal.add(Calendar.DAY_OF_YEAR, (dateKeys.size() - 1 - i));
            long ms = usageStore.getDailyPlayTimeMs(dateKeys.get(i));
            int min = (int) (ms / 60000L);
            report.dailyLabels.add(label);
            report.dailyMinutes.add(min);
            total += ms;
            if (min > 0) activeDays++;
            if (min > max) {
                max = min;
                maxDate = label;
            }
        }
        report.totalMinutes = (int) (total / 60000L);
        report.activeDays = activeDays;
        report.maxDayLabel = maxDate;
        report.maxDayMinutes = (int) (max);
        report.avgMinutesPerActiveDay = activeDays > 0 ? report.totalMinutes / activeDays : 0;
        return report;
    }

    /** 标记今日已展示过周报告（用于避免一天弹多次，可选实现）。 */
    public void markWeeklyReportShown() {
        prefs.edit().putString(KEY_LAST_WEEKLY_REPORT_DATE, todayKey()).apply();
    }

    private String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /** 警告结果。 */
    public static class WarnResult {
        public final int playedMin;
        public final int limitMin;
        public final int overMin;

        WarnResult(int playedMin, int limitMin, int overMin) {
            this.playedMin = playedMin;
            this.limitMin = limitMin;
            this.overMin = overMin;
        }
    }

    /** 周报告数据。 */
    public static class WeeklyReport {
        public List<String> dailyLabels = new ArrayList<>();
        public List<Integer> dailyMinutes = new ArrayList<>();
        public int totalMinutes;
        public int activeDays;
        public String maxDayLabel = "";
        public int maxDayMinutes;
        public int avgMinutesPerActiveDay;

        @NonNull
        public String toText(@NonNull Context ctx) {
            StringBuilder sb = new StringBuilder();
            sb.append("本周共游玩 ").append(totalMinutes).append(" 分钟，活跃 ")
                    .append(activeDays).append(" 天\n");
            if (activeDays > 0) {
                sb.append("日均游玩 ").append(avgMinutesPerActiveDay).append(" 分钟\n");
                sb.append("最长一天：").append(maxDayLabel)
                        .append("（").append(maxDayMinutes).append(" 分钟）\n\n");
            } else {
                sb.append("本周还未开始游玩\n\n");
            }
            sb.append("每日明细：\n");
            for (int i = 0; i < dailyLabels.size(); i++) {
                sb.append("  ").append(dailyLabels.get(i))
                        .append("  ").append(dailyMinutes.get(i)).append(" 分钟\n");
            }
            return sb.toString();
        }
    }
}
