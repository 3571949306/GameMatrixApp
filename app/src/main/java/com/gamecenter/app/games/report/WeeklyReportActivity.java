package com.gamecenter.app.games.report;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.gamecenter.app.R;
import com.gamecenter.app.games.GameRegistry;
import com.gamecenter.app.games.GameUsageStore;
import com.gamecenter.app.games.coin.CoinLedger;
import com.gamecenter.app.games.coin.CoinWallet;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 游戏周报页 —— 本周游玩时长 / 活跃天数 / 全期战绩 / 金币收支 / 最常游玩 / 每日时长。
 *
 * <p>数据口径（诚实标注，不虚构"本周"精度）：</p>
 * <ul>
 *   <li>本周时长 / 活跃天数 / 每日时长：精确本周（近 7 日滚动窗口，
 *       GameUsageStore.daily_play_time_yyyy-MM-dd 按日累计，非自然周）</li>
 *   <li>金币收支：精确本周（金币流水 entry.dateKey 命中本周 7 个日期 key 才计入）</li>
 *   <li>对局数 / 胜率：全期口径——GameUsageStore 无按日胜负记录，
 *       Room 仅有全期 wins/losses/playCount，无法拆出"本周对局"</li>
 *   <li>最常游玩：全期口径——按日时长未按游戏拆分，
 *       仅 totalPlayTimeMs(gameId) 全期可查，故展示全期时长 Top1（时长并列取胜场多者）</li>
 * </ul>
 *
 * <p>数据计算在后台线程完成（Room 同步读不占主线程），经 runOnUiThread 回主线程渲染；
 * 页面为纯聚合展示，进入时计算一次，无需 onResume 刷新。</p>
 */
public class WeeklyReportActivity extends AppCompatActivity {

    private static final String TAG = "WeeklyReportActivity";

    /** 周报窗口天数（getRecentDateKeys(7)：下标 0 = 6 天前 .. 6 = 今天）。 */
    private static final int WEEK_DAYS = 7;
    /** CoinWallet 持久化流水上限为 60 行，取 60 即全部流水。 */
    private static final int ALL_LEDGER_ENTRIES = 60;
    /** 每日时长迷你条形图的单格柱区最大高度（dp）。 */
    private static final int MAX_BAR_AREA_HEIGHT_DP = 56;
    /** 非零柱的最小高度（dp），保证极短时长也可见。 */
    private static final int MIN_BAR_HEIGHT_DP = 4;
    /** 柱条宽度（dp）。 */
    private static final int BAR_WIDTH_DP = 12;

    private GameUsageStore usageStore;
    private CoinWallet wallet;

    private TextView tvPeriod;
    private TextView tvPlaytime;
    private TextView tvActiveDays;
    private TextView tvTotalGames;
    private TextView tvWinRate;
    private TextView tvCoins;
    private TextView tvFavoriteName;
    private TextView tvFavoriteTime;
    private LinearLayout layoutDaily;
    private TextView tvDailyEmpty;

    /**
     * 启动游戏周报的便捷方法
     *
     * @param context 上下文
     */
    public static void launch(@NonNull Context context) {
        context.startActivity(new Intent(context, WeeklyReportActivity.class));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weekly_report);

        usageStore = new GameUsageStore(this);
        wallet = new CoinWallet(this);

        ImageView ivBack = findViewById(R.id.iv_back);
        ivBack.setOnClickListener(v -> finish());

        tvPeriod = findViewById(R.id.tv_weekly_period);
        tvPlaytime = findViewById(R.id.tv_weekly_playtime);
        tvActiveDays = findViewById(R.id.tv_weekly_active_days);
        tvTotalGames = findViewById(R.id.tv_weekly_total_games);
        tvWinRate = findViewById(R.id.tv_weekly_win_rate);
        tvCoins = findViewById(R.id.tv_weekly_coins);
        tvFavoriteName = findViewById(R.id.tv_weekly_favorite_name);
        tvFavoriteTime = findViewById(R.id.tv_weekly_favorite_time);
        layoutDaily = findViewById(R.id.layout_weekly_daily);
        tvDailyEmpty = findViewById(R.id.tv_weekly_daily_empty);

        loadAsync();
    }

    /** 后台线程计算周报数据（Room/SP 同步读），完成后回主线程渲染。 */
    private void loadAsync() {
        new Thread(() -> {
            ReportData data;
            try {
                data = computeReport();
            } catch (Exception e) {
                // 读取失败不崩溃：回退全零数据（页面显示占位文案），并留日志
                Log.w(TAG, "游戏周报数据读取失败", e);
                data = new ReportData();
            }
            final ReportData result = data;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                render(result);
            });
        }, "weekly-report").start();
    }

    /** 汇总周报数据（仅在此方法内做存储读取，调用方保证后台线程）。 */
    private ReportData computeReport() {
        ReportData data = new ReportData();

        // ===== 本周口径：近 7 日每日时长（SP 按日累计） =====
        List<String> dateKeys = usageStore.getRecentDateKeys(WEEK_DAYS);
        long[] dailyMs = new long[dateKeys.size()];
        for (int i = 0; i < dateKeys.size(); i++) {
            dailyMs[i] = usageStore.getDailyPlayTimeMs(dateKeys.get(i));
            data.weekTotalMs += dailyMs[i];
            if (dailyMs[i] > 0) {
                data.activeDays++;
            }
        }
        data.dailyMs = dailyMs;

        // ===== 全期口径：总对局（Room SUM(playCount)） =====
        data.totalPlayCount = usageStore.getAllTotalPlayCount();

        // ===== 全期口径：胜负与最常游玩（遍历注册表逐游戏读取） =====
        // 简化口径：GameUsageStore 无按日胜负、按日时长不按游戏拆分，
        // 胜率与"最常游玩"均为全期累计（与 StatsActivity 同源同口径）。
        for (GameRegistry.Category category : GameRegistry.getCategories(this)) {
            for (GameRegistry.Entry entry : category.games) {
                int wins = usageStore.getWinCount(entry.id);
                data.totalWins += wins;
                data.totalLosses += usageStore.getLossCount(entry.id);
                long playTimeMs = usageStore.getTotalPlayTimeMs(entry.id);
                // 最常游玩：全期时长 Top1；时长并列取胜场多者
                if (playTimeMs > 0 && (data.topGameMs < playTimeMs
                        || (data.topGameMs == playTimeMs && data.topGameWins < wins))) {
                    data.topGameMs = playTimeMs;
                    data.topGameWins = wins;
                    data.topGameId = entry.id;
                }
            }
        }
        if (data.topGameId != null) {
            String name = GameRegistry.getGameNameById(this, data.topGameId);
            // 注册表查不到名称时显示 id，不虚构
            data.topGameName = name != null ? name : data.topGameId;
        }

        // ===== 本周口径：金币收支（流水 dateKey 命中本周 7 个日期 key） =====
        Set<String> weekKeys = new HashSet<>(dateKeys);
        for (CoinLedger.Entry entry : wallet.recentEntries(ALL_LEDGER_ENTRIES)) {
            if (!weekKeys.contains(entry.dateKey)) {
                continue;
            }
            if (entry.delta > 0) {
                data.coinsEarned += entry.delta;
            } else {
                data.coinsSpent -= entry.delta;
            }
        }
        return data;
    }

    /** 主线程渲染全部卡片。 */
    private void render(ReportData data) {
        // 周期标题用纯日历计算（无存储读取），主线程直接算
        List<String> dateKeys = usageStore.getRecentDateKeys(WEEK_DAYS);
        tvPeriod.setText(getString(R.string.weekly_report_period_format,
                shortDate(dateKeys.get(0)), shortDate(dateKeys.get(dateKeys.size() - 1))));

        tvPlaytime.setText(formatDuration(data.weekTotalMs));
        tvActiveDays.setText(getString(
                R.string.weekly_report_active_days_format, data.activeDays));
        tvTotalGames.setText(getString(
                R.string.weekly_report_total_games_format, data.totalPlayCount));

        int decided = data.totalWins + data.totalLosses;
        if (decided > 0) {
            int winRate = (int) (data.totalWins * 100L / decided);
            tvWinRate.setText(getString(R.string.weekly_report_win_rate_format,
                    winRate, data.totalWins, data.totalLosses));
        } else {
            tvWinRate.setText(R.string.weekly_report_no_decided_games);
        }

        tvCoins.setText(getString(
                R.string.weekly_report_coin_format, data.coinsEarned, data.coinsSpent));

        if (data.topGameMs > 0 && data.topGameName != null) {
            tvFavoriteName.setText(data.topGameName);
            tvFavoriteTime.setText(formatDuration(data.topGameMs));
        } else {
            tvFavoriteName.setText(R.string.weekly_report_no_play);
            tvFavoriteTime.setText("");
        }

        renderDailyBars(data.dailyMs, dateKeys);
    }

    /** 渲染近 7 日迷你条形：非零日显示柱条；全零时显示占位文案。 */
    private void renderDailyBars(long[] dailyMs, List<String> dateKeys) {
        layoutDaily.removeAllViews();
        long maxMs = 0;
        if (dailyMs != null) {
            for (long ms : dailyMs) {
                maxMs = Math.max(maxMs, ms);
            }
        }
        boolean allZero = maxMs <= 0;
        layoutDaily.setVisibility(allZero ? View.GONE : View.VISIBLE);
        tvDailyEmpty.setVisibility(allZero ? View.VISIBLE : View.GONE);
        if (allZero || dailyMs == null) {
            return;
        }
        for (int i = 0; i < dateKeys.size() && i < dailyMs.length; i++) {
            layoutDaily.addView(createDayColumn(dailyMs[i], shortDate(dateKeys.get(i)), maxMs));
        }
    }

    /**
     * 单日柱格：固定高度柱区内底部对齐的竖条 + MM-dd 日期标签。
     * 时长为 0 的天不显示柱条（仅保留日期标签）。
     */
    private View createDayColumn(long ms, String label, long maxMs) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout barArea = new FrameLayout(this);
        barArea.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(MAX_BAR_AREA_HEIGHT_DP)));

        if (ms > 0) {
            View bar = new View(this);
            bar.setBackgroundColor(themeColor(androidx.appcompat.R.attr.colorPrimary));
            int height = (int) (MAX_BAR_AREA_HEIGHT_DP * (ms / (double) maxMs)
                    * getResources().getDisplayMetrics().density + 0.5f);
            height = Math.max(height, dp(MIN_BAR_HEIGHT_DP));
            bar.setLayoutParams(new FrameLayout.LayoutParams(
                    dp(BAR_WIDTH_DP), height, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
            barArea.addView(bar);
        }

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(10);
        tvLabel.setTextColor(themeColor(
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = dp(4);
        tvLabel.setLayoutParams(labelParams);

        column.addView(barArea);
        column.addView(tvLabel);
        return column;
    }

    /** 格式化时长（毫秒 → "X 小时 Y 分"，不足 1 小时 → "Y 分钟"）。 */
    private String formatDuration(long ms) {
        long totalMin = ms / (1000L * 60);
        long hours = totalMin / 60;
        long mins = totalMin % 60;
        if (hours > 0) {
            return getString(R.string.weekly_report_playtime_hour_min_format, hours, mins);
        }
        return getString(R.string.weekly_report_playtime_min_format, totalMin);
    }

    /** 日期 key "yyyy-MM-dd" 截取为 "MM-dd"。 */
    private static String shortDate(String dateKey) {
        return dateKey.length() > 5 ? dateKey.substring(5) : dateKey;
    }

    /** 解析主题属性颜色（护眼主题可随 DayNight 切换）。 */
    private int themeColor(int attr) {
        TypedValue typedValue = new TypedValue();
        getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }

    /** dp 转 px。 */
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 周报数据载体（后台线程产出，主线程只读渲染）。 */
    private static final class ReportData {
        /** 近 7 日逐日时长（毫秒），下标与 getRecentDateKeys(7) 对齐；读取失败为 null。 */
        long[] dailyMs;
        /** 本周总时长（毫秒）。 */
        long weekTotalMs;
        /** 本周有游玩记录的天数。 */
        int activeDays;
        /** 全期总对局数。 */
        int totalPlayCount;
        /** 全期总胜场。 */
        int totalWins;
        /** 全期总负场。 */
        int totalLosses;
        /** 最常游玩游戏的 id（全期时长 Top1）。 */
        String topGameId;
        /** 最常游玩游戏名（注册表查不到时为 id）。 */
        String topGameName;
        /** 最常游玩游戏的全期时长（毫秒）。 */
        long topGameMs;
        /** 最常游玩游戏的全期胜场（并列时决胜用）。 */
        int topGameWins;
        /** 本周金币赚取合计。 */
        long coinsEarned;
        /** 本周金币消费合计（正数）。 */
        long coinsSpent;
    }
}
