package com.gamecenter.app.games;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.gamecenter.app.R;
import com.google.android.material.appbar.MaterialToolbar;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StatsActivity extends AppCompatActivity {

    private TextView tvTotalTime;
    private TextView tvTotalPlays;
    private TextView tvTotalWins;
    private RecyclerView rvStats;
    private TextView tvEmpty;
    private StatsAdapter adapter;
    private GameUsageStore usageStore;

    private static final Map<String, Integer> GAME_ICONS = new HashMap<>();
    static {
        GAME_ICONS.put("game_2048", R.drawable.ic_2048);
        GAME_ICONS.put("snake", R.drawable.ic_snake);
        GAME_ICONS.put("breakout", R.drawable.ic_breakout);
        GAME_ICONS.put("flappy", R.drawable.ic_flappy);
        GAME_ICONS.put("plane", R.drawable.ic_launcher_foreground);
        GAME_ICONS.put("whack", R.drawable.ic_whack);
        GAME_ICONS.put("blackjack", R.drawable.ic_blackjack);
        GAME_ICONS.put("doudizhu", R.drawable.ic_game);
        GAME_ICONS.put("checkers", R.drawable.ic_checkers);
        GAME_ICONS.put("chinese_chess", R.drawable.ic_chess);
        GAME_ICONS.put("tic", R.drawable.ic_game);
        GAME_ICONS.put("gomoku", R.drawable.ic_gomoku);
        GAME_ICONS.put("sudoku", R.drawable.ic_sudoku);
        GAME_ICONS.put("klotski", R.drawable.ic_klotski);
        GAME_ICONS.put("sokoban", R.drawable.ic_sokoban);
    }

    private static final Map<String, String> GAME_DISPLAY_NAMES = new HashMap<>();
    static {
        GAME_DISPLAY_NAMES.put("game_2048", "2048");
        GAME_DISPLAY_NAMES.put("snake", "贪吃蛇");
        GAME_DISPLAY_NAMES.put("breakout", "打砖块");
        GAME_DISPLAY_NAMES.put("flappy", "Flappy Bird");
        GAME_DISPLAY_NAMES.put("plane", "飞机大战");
        GAME_DISPLAY_NAMES.put("whack", "打地鼠");
        GAME_DISPLAY_NAMES.put("blackjack", "21点");
        GAME_DISPLAY_NAMES.put("doudizhu", "斗地主");
        GAME_DISPLAY_NAMES.put("checkers", "跳棋");
        GAME_DISPLAY_NAMES.put("chinese_chess", "中国象棋");
        GAME_DISPLAY_NAMES.put("tic", "井字棋");
        GAME_DISPLAY_NAMES.put("gomoku", "五子棋");
        GAME_DISPLAY_NAMES.put("sudoku", "数独");
        GAME_DISPLAY_NAMES.put("klotski", "华容道");
        GAME_DISPLAY_NAMES.put("sokoban", "推箱子");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        usageStore = new GameUsageStore(this);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("游戏战绩");
        toolbar.setNavigationOnClickListener(v -> finish());

        tvTotalTime = findViewById(R.id.tv_total_time);
        tvTotalPlays = findViewById(R.id.tv_total_plays);
        tvTotalWins = findViewById(R.id.tv_total_wins);
        rvStats = findViewById(R.id.rv_stats);
        tvEmpty = findViewById(R.id.tv_empty);

        loadSummary();
        loadStatsList();
    }

    private void loadSummary() {
        long totalTimeMs = usageStore.getTotalPlayTimeMs();
        int totalPlays = usageStore.getTotalPlays();
        int totalWins = usageStore.getTotalWins();

        tvTotalTime.setText(formatPlayTime(totalTimeMs));
        tvTotalPlays.setText(String.valueOf(totalPlays));
        tvTotalWins.setText(String.valueOf(totalWins));
    }

    private String formatPlayTime(long ms) {
        if (ms <= 0) return "--";
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        if (hours > 0) {
            return hours + "小时" + minutes + "分";
        }
        return minutes + "分钟";
    }

    private void loadStatsList() {
        List<GameStats> allStats = usageStore.getAllStats();
        if (allStats.isEmpty()) {
            rvStats.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            rvStats.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);

            List<StatsItem> items = new ArrayList<>();
            for (GameStats stats : allStats) {
                String displayName = getGameDisplayName(stats.gameId);
                int iconRes = getGameIcon(stats.gameId);
                items.add(new StatsItem(stats, displayName, iconRes));
            }

            adapter = new StatsAdapter(items);
            rvStats.setLayoutManager(new LinearLayoutManager(this));
            rvStats.setAdapter(adapter);
        }
    }

    private String getGameDisplayName(String gameId) {
        String name = GAME_DISPLAY_NAMES.get(gameId);
        if (name != null) {
            return name;
        }
        return gameId;
    }

    private int getGameIcon(String gameId) {
        Integer iconRes = GAME_ICONS.get(gameId);
        if (iconRes != null) {
            return iconRes;
        }
        return R.drawable.ic_launcher_foreground;
    }

    private static class StatsItem {
        GameStats stats;
        String displayName;
        int iconRes;

        StatsItem(GameStats stats, String displayName, int iconRes) {
            this.stats = stats;
            this.displayName = displayName;
            this.iconRes = iconRes;
        }
    }

    private class StatsAdapter extends RecyclerView.Adapter<StatsAdapter.ViewHolder> {

        private final List<StatsItem> items;
        private final Map<Integer, Boolean> expandedStates = new HashMap<>();

        StatsAdapter(List<StatsItem> items) {
            this.items = items;
        }

        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_stats, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            StatsItem item = items.get(position);
            GameStats stats = item.stats;
            boolean expanded = expandedStates.getOrDefault(position, false);

            holder.tvGameName.setText(item.displayName);
            holder.ivGameIcon.setImageResource(item.iconRes);
            holder.tvLastPlayed.setText("最后游玩: " + formatLastPlayed(stats.lastPlayedAt));

            holder.tvHighScore.setText(stats.highScore > 0 ? String.valueOf(stats.highScore) : "--");
            holder.tvWinRate.setText(stats.getWinRateText());
            holder.tvTotalPlays.setText(String.valueOf(stats.totalPlays > 0 ? stats.totalPlays : 0));
            holder.tvBestTime.setText(stats.getBestTimeText());

            holder.tvWins.setText(String.valueOf(stats.totalWins));
            holder.tvLosses.setText(String.valueOf(stats.totalLosses));
            holder.tvTotalTime.setText(stats.getTotalPlayTimeText());

            holder.layoutDetail.setVisibility(expanded ? View.VISIBLE : View.GONE);
            holder.ivExpand.setRotation(expanded ? 180f : 0f);

            holder.itemView.setOnClickListener(v -> {
                boolean newState = !expandedStates.getOrDefault(position, false);
                expandedStates.put(position, newState);
                holder.layoutDetail.setVisibility(newState ? View.VISIBLE : View.GONE);
                holder.ivExpand.setRotation(newState ? 180f : 0f);
            });
        }

        private String formatLastPlayed(long timestamp) {
            if (timestamp <= 0) return "从未";
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivGameIcon;
            TextView tvGameName;
            TextView tvLastPlayed;
            ImageView ivExpand;
            TextView tvHighScore;
            TextView tvWinRate;
            TextView tvTotalPlays;
            TextView tvBestTime;
            LinearLayout layoutDetail;
            TextView tvWins;
            TextView tvLosses;
            TextView tvTotalTime;

            ViewHolder(View itemView) {
                super(itemView);
                ivGameIcon = itemView.findViewById(R.id.iv_game_icon);
                tvGameName = itemView.findViewById(R.id.tv_game_name);
                tvLastPlayed = itemView.findViewById(R.id.tv_last_played);
                ivExpand = itemView.findViewById(R.id.iv_expand);
                tvHighScore = itemView.findViewById(R.id.tv_high_score);
                tvWinRate = itemView.findViewById(R.id.tv_win_rate);
                tvTotalPlays = itemView.findViewById(R.id.tv_total_plays);
                tvBestTime = itemView.findViewById(R.id.tv_best_time);
                layoutDetail = itemView.findViewById(R.id.layout_detail);
                tvWins = itemView.findViewById(R.id.tv_wins);
                tvLosses = itemView.findViewById(R.id.tv_losses);
                tvTotalTime = itemView.findViewById(R.id.tv_total_time);
            }
        }
    }
}
