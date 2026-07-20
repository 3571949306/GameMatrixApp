package com.gamecenter.app.games;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.gamecenter.app.BuildConfig;
import com.gamecenter.app.R;
import com.gamecenter.app.games.achievement.AchievementCenterActivity;
import com.gamecenter.app.games.achievement.StreakTracker;
import com.gamecenter.app.games.config.GameConfigLoader;
import com.gamecenter.app.games.model.AchievementDef;
import com.gamecenter.app.games.model.GameConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 游戏统计仪表盘（Feature F / GAME_STATS_DASHBOARD）。
 *
 * <p>展示用户的总游玩时长、活跃天数、总对局数、当前/最佳连胜、
 * Top 5 最常玩游戏、成就概览、胜负统计。</p>
 *
 * <p>数据源：</p>
 * <ul>
 *   <li>{@link GameUsageStore} — 单游戏游玩次数、时长、胜负</li>
 *   <li>{@link StreakTracker} — 每日活跃连胜、最佳连胜、总对局</li>
 *   <li>{@link GameConfigLoader} — 成就定义列表（统计总/已解锁）</li>
 *   <li>{@link SharedPreferences}("game_achievements") — 成就解锁状态</li>
 * </ul>
 */
public class StatsActivity extends AppCompatActivity {

    private static final String ACHIEVEMENT_PREFS = "game_achievements";
    private static final String ACHIEVEMENT_KEY_PREFIX = "unlock_";
    private static final String ACHIEVEMENT_KEY_SUFFIX = "_unlocked";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        if (!BuildConfig.GAME_STATS_DASHBOARD) {
            // Flag 关闭时回退到原占位文本
            TextView view = new TextView(this);
            int padding = (int) (24 * getResources().getDisplayMetrics().density);
            view.setPadding(padding, padding, padding, padding);
            view.setText("游戏战绩会统计已安装游戏的游玩记录。");
            view.setTextSize(18f);
            setContentView(view);
            return;
        }

        initViews();
        loadData();
    }

    private void initViews() {
        ImageView ivBack = findViewById(R.id.iv_back);
        if (ivBack != null) {
            ivBack.setOnClickListener(v -> finish());
        }

        View achievementCard = findViewById(R.id.card_achievement_summary);
        if (achievementCard != null) {
            achievementCard.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(this, AchievementCenterActivity.class));
                } catch (Exception e) {
                    Toast.makeText(this, "成就中心未找到", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /** 加载所有统计数据并填充视图。 */
    private void loadData() {
        GameUsageStore usageStore = new GameUsageStore(this);
        StreakTracker streak = StreakTracker.getInstance(this);

        // 1. Hero 卡片数据
        long totalPlayMs = 0;
        int totalWin = 0;
        int totalLoss = 0;
        List<GameRegistry.Entry> allGames = collectAllGames();
        List<GamePlayInfo> playInfos = new ArrayList<>();
        for (GameRegistry.Entry entry : allGames) {
            int playCount = usageStore.getPlayCount(entry.id);
            long playTime = usageStore.getTotalPlayTimeMs(entry.id);
            int winCount = usageStore.getWinCount(entry.id);
            int lossCount = usageStore.getLossCount(entry.id);
            totalPlayMs += playTime;
            totalWin += winCount;
            totalLoss += lossCount;
            if (playCount > 0) {
                playInfos.add(new GamePlayInfo(entry, playCount, playTime));
            }
        }

        // 总时长
        TextView tvPlaytime = findViewById(R.id.tv_stat_playtime);
        if (tvPlaytime != null) {
            tvPlaytime.setText(formatDuration(totalPlayMs));
        }

        // 活跃天数（使用最佳连胜作为累计最大活跃天数指标）
        TextView tvActiveDays = findViewById(R.id.tv_stat_active_days);
        if (tvActiveDays != null) {
            tvActiveDays.setText(String.valueOf(streak.getBestStreak()));
        }

        // 总对局
        TextView tvTotalGames = findViewById(R.id.tv_stat_total_games);
        if (tvTotalGames != null) {
            tvTotalGames.setText(String.valueOf(streak.getTotalGames()));
        }

        // 当前连胜
        TextView tvStreak = findViewById(R.id.tv_stat_streak);
        if (tvStreak != null) {
            tvStreak.setText(String.valueOf(streak.getCurrentStreak()));
        }

        // 最佳连胜
        TextView tvBestStreak = findViewById(R.id.tv_best_streak);
        if (tvBestStreak != null) {
            tvBestStreak.setText(getString(R.string.stats_best_streak_format, streak.getBestStreak()));
        }

        // 2. Top 5 最常玩
        LinearLayout topContainer = findViewById(R.id.top_games_container);
        TextView tvNoTopGames = findViewById(R.id.tv_no_top_games);
        if (topContainer != null) {
            topContainer.removeAllViews();
            if (playInfos.isEmpty()) {
                if (tvNoTopGames != null) tvNoTopGames.setVisibility(View.VISIBLE);
            } else {
                if (tvNoTopGames != null) tvNoTopGames.setVisibility(View.GONE);
                // 按 playCount 降序排序，取前 5
                Collections.sort(playInfos, new Comparator<GamePlayInfo>() {
                    @Override
                    public int compare(GamePlayInfo a, GamePlayInfo b) {
                        return Integer.compare(b.playCount, a.playCount);
                    }
                });
                int topN = Math.min(5, playInfos.size());
                LayoutInflater inflater = LayoutInflater.from(this);
                for (int i = 0; i < topN; i++) {
                    GamePlayInfo info = playInfos.get(i);
                    View row = inflater.inflate(R.layout.item_stats_top_game, topContainer, false);
                    bindTopGameRow(row, info, i + 1);
                    topContainer.addView(row);
                }
            }
        }

        // 3. 成就概览
        TextView tvAchProgress = findViewById(R.id.tv_achievement_progress);
        if (tvAchProgress != null) {
            int[] achCounts = computeAchievementCounts();
            tvAchProgress.setText(getString(
                    R.string.stats_achievement_progress_format, achCounts[0], achCounts[1]));
        }

        // 4. 胜负统计
        TextView tvWin = findViewById(R.id.tv_win_count);
        if (tvWin != null) tvWin.setText(String.valueOf(totalWin));
        TextView tvLoss = findViewById(R.id.tv_loss_count);
        if (tvLoss != null) tvLoss.setText(String.valueOf(totalLoss));
    }

    /** 收集 GameRegistry 中所有游戏条目。 */
    private List<GameRegistry.Entry> collectAllGames() {
        List<GameRegistry.Entry> result = new ArrayList<>();
        try {
            List<GameRegistry.Category> categories = GameRegistry.getCategories(this);
            for (GameRegistry.Category cat : categories) {
                result.addAll(cat.games);
            }
        } catch (Exception e) {
            // 忽略，返回空列表
        }
        return result;
    }

    /** 计算成就解锁/总数。返回 int[]{已解锁, 总数}。 */
    private int[] computeAchievementCounts() {
        try {
            GameConfigLoader loader = new GameConfigLoader(this);
            List<GameConfig> configs = loader.loadAllConfigs();
            SharedPreferences prefs = getSharedPreferences(ACHIEVEMENT_PREFS, MODE_PRIVATE);
            int unlocked = 0;
            int total = 0;
            for (GameConfig config : configs) {
                if (config.achievements == null || config.achievements.isEmpty()) continue;
                for (AchievementDef def : config.achievements) {
                    String fullKey = def.getFullId(config.gameId);
                    total++;
                    if (prefs.getBoolean(ACHIEVEMENT_KEY_PREFIX + fullKey + ACHIEVEMENT_KEY_SUFFIX, false)) {
                        unlocked++;
                    }
                }
            }
            return new int[]{unlocked, total};
        } catch (Exception e) {
            return new int[]{0, 0};
        }
    }

    /** 绑定 Top 5 列表项。 */
    private void bindTopGameRow(View row, GamePlayInfo info, int rank) {
        ImageView ivIcon = row.findViewById(R.id.iv_top_game_icon);
        if (ivIcon != null) {
            try {
                ivIcon.setImageResource(info.entry.iconRes);
            } catch (Exception e) {
                // 忽略图标加载失败
            }
        }

        TextView tvRank = row.findViewById(R.id.tv_top_game_rank);
        if (tvRank != null) {
            tvRank.setText(getString(R.string.stats_rank_format, rank));
        }

        TextView tvName = row.findViewById(R.id.tv_top_game_name);
        if (tvName != null) {
            tvName.setText(info.entry.name);
        }

        TextView tvCount = row.findViewById(R.id.tv_top_game_count);
        if (tvCount != null) {
            tvCount.setText(getString(R.string.stats_play_count_format, info.playCount));
        }

        TextView tvDuration = row.findViewById(R.id.tv_top_game_duration);
        if (tvDuration != null) {
            tvDuration.setText(formatDuration(info.playTimeMs));
        }
    }

    /** 格式化时长（毫秒 → "x 分钟" 或 "x 小时 y 分钟"）。 */
    private String formatDuration(long ms) {
        long totalMin = ms / (1000L * 60);
        if (totalMin < 60) {
            return getString(R.string.stats_duration_min_format, totalMin);
        }
        long hours = totalMin / 60;
        long mins = totalMin % 60;
        return getString(R.string.stats_duration_hour_format, hours, mins);
    }

    /** Top 游戏数据载体。 */
    private static class GamePlayInfo {
        final GameRegistry.Entry entry;
        final int playCount;
        final long playTimeMs;

        GamePlayInfo(GameRegistry.Entry entry, int playCount, long playTimeMs) {
            this.entry = entry;
            this.playCount = playCount;
            this.playTimeMs = playTimeMs;
        }
    }
}
