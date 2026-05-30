package com.gamecenter.app.games;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.gamecenter.app.games.R;
import com.google.android.material.appbar.MaterialToolbar;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 游戏战绩统计页面
 * <p>
 * 展示游戏中心所有游戏的汇总统计信息和各游戏的详细战绩数据。
 * 页面顶部显示总游玩时长、总游戏次数和总胜利次数的汇总卡片，
 * 下方以可展开/折叠的列表形式展示每个游戏的详细统计。
 * </p>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>游戏图标和显示名称通过静态Map维护，gameId到图标/名称的映射集中管理</li>
 *   <li>列表项支持展开/折叠交互，展开后显示更多详细统计（胜/负次数、总时长等）</li>
 *   <li>展开状态通过Map按位置存储，滚动时保持各项的展开/折叠状态</li>
 *   <li>未找到对应图标时使用应用默认图标作为兜底</li>
 * </ul>
 * </p>
 */
public class StatsActivity extends AppCompatActivity {

    /** 总游玩时长文本 */
    private TextView tvTotalTime;
    /** 总游戏次数文本 */
    private TextView tvTotalPlays;
    /** 总胜利次数文本 */
    private TextView tvTotalWins;
    /** 游戏统计列表的RecyclerView */
    private RecyclerView rvStats;
    /** 空状态提示文本，无数据时显示 */
    private TextView tvEmpty;
    /** 列表适配器 */
    private StatsAdapter adapter;
    /** 游戏使用数据存储 */
    private GameUsageStore usageStore;

    /** 游戏ID到图标资源ID的映射 */
    private static final Map<String, Integer> GAME_ICONS = new HashMap<>();
    static {
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

    /** 游戏ID到中文显示名称的映射 */
    private static final Map<String, String> GAME_DISPLAY_NAMES = new HashMap<>();
    static {
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

    /**
     * Activity创建时的初始化
     * <p>
     * 初始化数据存储、工具栏和各视图组件，然后加载汇总数据和统计列表。
     * </p>
     *
     * @param savedInstanceState 保存的实例状态
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        usageStore = new GameUsageStore(this);

        // 配置工具栏，设置返回按钮
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

    /**
     * 加载汇总统计数据
     * <p>
     * 从GameUsageStore获取所有游戏的总游玩时长、总游戏次数和总胜利次数，
     * 并更新顶部汇总卡片的显示。
     * </p>
     */
    private void loadSummary() {
        long totalTimeMs = usageStore.getTotalPlayTimeMs();
        int totalPlays = usageStore.getTotalPlays();
        int totalWins = usageStore.getTotalWins();

        tvTotalTime.setText(formatPlayTime(totalTimeMs));
        tvTotalPlays.setText(String.valueOf(totalPlays));
        tvTotalWins.setText(String.valueOf(totalWins));
    }

    /**
     * 将毫秒时长格式化为可读文本
     * <p>
     * 自动选择合适的单位：
     * <ul>
     *   <li>超过1小时：显示"X小时X分"</li>
     *   <li>不足1小时：显示"X分钟"</li>
     * </ul>
     * 无数据时显示"--"。
     * </p>
     *
     * @param ms 时长（毫秒）
     * @return 格式化的时长文本
     */
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

    /**
     * 加载游戏统计列表
     * <p>
     * 从GameUsageStore获取所有游戏的统计数据，构建列表项并设置适配器。
     * 如果没有任何统计数据，隐藏列表并显示空状态提示。
     * </p>
     */
    private void loadStatsList() {
        List<GameStats> allStats = usageStore.getAllStats();
        if (allStats.isEmpty()) {
            // 无数据时显示空状态提示
            rvStats.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            rvStats.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);

            // 将GameStats转换为带显示名称和图标的列表项
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

    /**
     * 获取游戏的中文显示名称
     * <p>
     * 从GAME_DISPLAY_NAMES映射中查找，未找到时直接返回gameId作为兜底。
     * </p>
     *
     * @param gameId 游戏唯一标识符
     * @return 游戏的中文显示名称
     */
    private String getGameDisplayName(String gameId) {
        String name = GAME_DISPLAY_NAMES.get(gameId);
        if (name != null) {
            return name;
        }
        // 兜底：直接使用gameId作为显示名称
        return gameId;
    }

    /**
     * 获取游戏的图标资源ID
     * <p>
     * 从GAME_ICONS映射中查找，未找到时使用应用默认图标作为兜底。
     * </p>
     *
     * @param gameId 游戏唯一标识符
     * @return 图标资源ID
     */
    private int getGameIcon(String gameId) {
        Integer iconRes = GAME_ICONS.get(gameId);
        if (iconRes != null) {
            return iconRes;
        }
        // 兜底：使用应用默认图标
        return R.drawable.ic_launcher_foreground;
    }

    /**
     * 统计列表项数据
     * <p>
     * 封装GameStats及其对应的显示名称和图标资源，
     * 用于在RecyclerView中展示。
     * </p>
     */
    private static class StatsItem {
        /** 游戏统计数据 */
        GameStats stats;
        /** 游戏中文显示名称 */
        String displayName;
        /** 游戏图标资源ID */
        int iconRes;

        StatsItem(GameStats stats, String displayName, int iconRes) {
            this.stats = stats;
            this.displayName = displayName;
            this.iconRes = iconRes;
        }
    }

    /**
     * 游戏统计列表适配器
     * <p>
     * 支持展开/折叠交互，点击列表项可切换详细信息的显示/隐藏。
     * 展开状态通过expandedStates Map按列表位置存储。
     * </p>
     */
    private class StatsAdapter extends RecyclerView.Adapter<StatsAdapter.ViewHolder> {

        /** 统计列表项数据 */
        private final List<StatsItem> items;
        /** 各位置的展开状态，true表示展开显示详细信息 */
        private final Map<Integer, Boolean> expandedStates = new HashMap<>();

        StatsAdapter(List<StatsItem> items) {
            this.items = items;
        }

        /**
         * 创建ViewHolder
         *
         * @param parent   父视图组
         * @param viewType 视图类型
         * @return ViewHolder实例
         */
        @Override
        public ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_stats, parent, false);
            return new ViewHolder(view);
        }

        /**
         * 绑定数据到ViewHolder
         * <p>
         * 设置游戏图标、名称、最后游玩时间等基本信息，
         * 以及最高分、胜率、游戏次数、最佳用时等统计数据。
         * 根据展开状态显示或隐藏详细信息区域，并设置展开箭头的旋转角度。
         * </p>
         *
         * @param holder   ViewHolder实例
         * @param position 列表位置
         */
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            StatsItem item = items.get(position);
            GameStats stats = item.stats;
            boolean expanded = expandedStates.getOrDefault(position, false);

            // 基本信息
            holder.tvGameName.setText(item.displayName);
            holder.ivGameIcon.setImageResource(item.iconRes);
            holder.tvLastPlayed.setText("最后游玩: " + formatLastPlayed(stats.lastPlayedAt));

            // 统计摘要数据
            holder.tvHighScore.setText(stats.highScore > 0 ? String.valueOf(stats.highScore) : "--");
            holder.tvWinRate.setText(stats.getWinRateText());
            holder.tvTotalPlays.setText(String.valueOf(stats.totalPlays > 0 ? stats.totalPlays : 0));
            holder.tvBestTime.setText(stats.getBestTimeText());

            // 详细统计数据（展开时可见）
            holder.tvWins.setText(String.valueOf(stats.totalWins));
            holder.tvLosses.setText(String.valueOf(stats.totalLosses));
            holder.tvTotalTime.setText(stats.getTotalPlayTimeText());

            // 根据展开状态控制详细区域的显示和箭头方向
            holder.layoutDetail.setVisibility(expanded ? View.VISIBLE : View.GONE);
            holder.ivExpand.setRotation(expanded ? 180f : 0f);

            // 点击列表项切换展开/折叠状态
            holder.itemView.setOnClickListener(v -> {
                boolean newState = !expandedStates.getOrDefault(position, false);
                expandedStates.put(position, newState);
                holder.layoutDetail.setVisibility(newState ? View.VISIBLE : View.GONE);
                // 箭头旋转：展开时向上（180度），折叠时向下（0度）
                holder.ivExpand.setRotation(newState ? 180f : 0f);
            });
        }

        /**
         * 格式化最后游玩时间戳为可读文本
         * <p>
         * 格式为"MM-dd HH:mm"，如"03-15 14:30"。
         * 时间戳为0或负数时返回"从未"。
         * </p>
         *
         * @param timestamp 时间戳（毫秒）
         * @return 格式化的时间文本
         */
        private String formatLastPlayed(long timestamp) {
            if (timestamp <= 0) return "从未";
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            return sdf.format(new Date(timestamp));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        /**
         * 统计列表项ViewHolder
         * <p>
         * 持有列表项中所有视图的引用，包括基本信息视图、
         * 统计摘要视图和可展开的详细信息视图。
         * </p>
         */
        class ViewHolder extends RecyclerView.ViewHolder {
            /** 游戏图标 */
            ImageView ivGameIcon;
            /** 游戏名称 */
            TextView tvGameName;
            /** 最后游玩时间 */
            TextView tvLastPlayed;
            /** 展开/折叠箭头图标 */
            ImageView ivExpand;
            /** 最高分 */
            TextView tvHighScore;
            /** 胜率 */
            TextView tvWinRate;
            /** 总游戏次数 */
            TextView tvTotalPlays;
            /** 最佳用时 */
            TextView tvBestTime;
            /** 详细信息区域容器 */
            LinearLayout layoutDetail;
            /** 胜利次数（详细信息） */
            TextView tvWins;
            /** 失败次数（详细信息） */
            TextView tvLosses;
            /** 总游玩时长（详细信息） */
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
