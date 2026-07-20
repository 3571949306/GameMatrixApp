package com.gamecenter.app.games.achievement;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gamecenter.app.BuildConfig;
import com.gamecenter.app.R;
import com.gamecenter.app.games.GameRegistry;
import com.gamecenter.app.games.config.GameConfigLoader;
import com.gamecenter.app.games.model.AchievementDef;
import com.gamecenter.app.games.model.GameConfig;
import com.gamecenter.app.games.model.enums.AchievementLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 成就中心 Activity —— 展示所有游戏的成就汇总
 *
 * <p>功能：
 * <ul>
 *   <li>展示所有 27 个游戏的成就汇总</li>
 *   <li>按游戏分组，每个游戏显示已解锁/总数</li>
 *   <li>进度条显示总体完成度</li>
 *   <li>点击游戏展开该游戏的成就列表</li>
 *   <li>使用护眼主题颜色</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-19
 */
public class AchievementCenterActivity extends AppCompatActivity {

    private static final String PREF_NAME = "achievements";
    private static final String KEY_PREFIX = "achievement_";

    private TextView tvTotalUnlocked;
    private TextView tvTotalSeparator;
    private ProgressBar progressTotal;
    private TextView tvGamesSummary;
    private RecyclerView rvAchievementGames;
    // Batch 10-4 (ACHIEVEMENT_PROGRESS_RING): 圆环进度头部
    private View ringProgressCard;
    private com.gamecenter.app.ui.AchievementProgressRingView ringAchievementProgress;

    private GameConfigLoader configLoader;
    private SharedPreferences achievementPrefs;
    private List<GameAchievementInfo> gameAchievementInfos;

    /**
     * 启动成就中心的便捷方法
     *
     * @param context 上下文
     */
    public static void launch(@NonNull Context context) {
        Intent intent = new Intent(context, AchievementCenterActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_achievement_center);

        configLoader = new GameConfigLoader(this);
        achievementPrefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        initViews();
        loadData();
        setupRecyclerView();
        updateSummary();

        // Batch 10-4 (ACHIEVEMENT_PROGRESS_RING): 圆环进度头部
        if (BuildConfig.ACHIEVEMENT_PROGRESS_RING) {
            initRingProgress();
        }

        // Feature A (ACHIEVEMENT_V2): 每日挑战 + 连胜统计卡片
        if (BuildConfig.ACHIEVEMENT_V2) {
            initFeatureACards();
        }
    }

    /**
     * Batch 10-4 (ACHIEVEMENT_PROGRESS_RING): 初始化圆环进度头部。
     * 在 loadData() 之后调用，根据 gameAchievementInfos 累计已解锁/总数并写入圆环 View。
     */
    private void initRingProgress() {
        ringProgressCard = findViewById(R.id.ring_progress_card);
        ringAchievementProgress = findViewById(R.id.ring_achievement_progress);
        if (ringProgressCard == null || ringAchievementProgress == null) return;
        ringProgressCard.setVisibility(View.VISIBLE);
        int totalUnlocked = 0;
        int totalAchievements = 0;
        if (gameAchievementInfos != null) {
            for (GameAchievementInfo info : gameAchievementInfos) {
                totalUnlocked += info.unlockedCount;
                totalAchievements += info.totalCount;
            }
        }
        ringAchievementProgress.setUnlockedAndTotal(totalUnlocked, totalAchievements);
    }

    /**
     * Feature A: 初始化每日挑战卡片 + 连胜概览卡片。
     * <p>仅当 {@link BuildConfig#ACHIEVEMENT_V2} 为 true 时调用。</p>
     */
    private void initFeatureACards() {
        View dailyChallengeCard = findViewById(R.id.card_daily_challenge_include);
        View streakCard = findViewById(R.id.card_streak_summary_include);
        if (dailyChallengeCard != null) {
            dailyChallengeCard.setVisibility(View.VISIBLE);
            fillDailyChallengeCard(dailyChallengeCard);
        }
        if (streakCard != null) {
            streakCard.setVisibility(View.VISIBLE);
            fillStreakCard(streakCard);
        }
    }

    /** 填充每日挑战卡片数据。 */
    private void fillDailyChallengeCard(@NonNull View card) {
        DailyChallengeManager.Challenge c =
                DailyChallengeManager.getInstance(this).getTodayChallenge();

        TextView tvStatus = card.findViewById(R.id.tv_daily_challenge_status);
        TextView tvDesc = card.findViewById(R.id.tv_daily_challenge_desc);
        ProgressBar progressBar = card.findViewById(R.id.progress_daily_challenge);
        TextView tvProgress = card.findViewById(R.id.tv_daily_challenge_progress);

        // 拼接描述
        String desc;
        switch (c.type) {
            case DailyChallengeManager.TYPE_WIN_ROUNDS:
                desc = getString(R.string.daily_challenge_type_win,
                        c.target, c.gameName);
                break;
            case DailyChallengeManager.TYPE_UNLOCK_ACHIEVEMENTS:
                desc = getString(R.string.daily_challenge_type_unlock, c.target);
                break;
            case DailyChallengeManager.TYPE_PLAY_ROUNDS:
            default:
                desc = getString(R.string.daily_challenge_type_play,
                        c.target, c.gameName);
                break;
        }
        tvDesc.setText(desc);

        // 进度
        int percent = c.target > 0
                ? (int) ((float) c.progress / c.target * 100)
                : 0;
        progressBar.setProgress(percent);
        tvProgress.setText(getString(R.string.daily_challenge_progress, c.progress, c.target));

        // 状态
        tvStatus.setText(c.completed
                ? R.string.daily_challenge_status_label_completed
                : R.string.daily_challenge_status_label_not_completed);
    }

    /** 填充连胜概览卡片数据。 */
    private void fillStreakCard(@NonNull View card) {
        StreakTracker tracker = StreakTracker.getInstance(this);
        int current = tracker.getCurrentStreak();
        int best = tracker.getBestStreak();
        int total = tracker.getTotalGames();

        TextView tvCurrentTop = card.findViewById(R.id.tv_streak_current);
        TextView tvCurrent = card.findViewById(R.id.tv_streak_current_value);
        TextView tvBest = card.findViewById(R.id.tv_streak_best_value);
        TextView tvTotal = card.findViewById(R.id.tv_streak_total_value);

        if (tvCurrentTop != null) {
            tvCurrentTop.setText(getString(R.string.streak_days_unit, current));
        }
        if (tvCurrent != null) tvCurrent.setText(String.valueOf(current));
        if (tvBest != null) tvBest.setText(String.valueOf(best));
        if (tvTotal != null) tvTotal.setText(String.valueOf(total));
    }

    /**
     * 初始化视图
     */
    private void initViews() {
        ImageView ivBack = findViewById(R.id.iv_back);
        ivBack.setOnClickListener(v -> finish());

        tvTotalUnlocked = findViewById(R.id.tv_total_unlocked);
        tvTotalSeparator = findViewById(R.id.tv_total_separator);
        progressTotal = findViewById(R.id.progress_total);
        tvGamesSummary = findViewById(R.id.tv_games_summary);
        rvAchievementGames = findViewById(R.id.rv_achievement_games);
    }

    /**
     * 加载所有游戏的成就数据
     */
    private void loadData() {
        gameAchievementInfos = new ArrayList<>();
        List<GameConfig> allConfigs = configLoader.loadAllConfigs();

        for (GameConfig config : allConfigs) {
            if (config.achievements == null || config.achievements.isEmpty()) {
                continue;
            }

            GameAchievementInfo info = new GameAchievementInfo();
            info.gameId = config.gameId;
            info.gameName = config.name;
            info.iconResId = config.iconResId;
            info.totalCount = config.achievements.size();
            info.unlockedCount = 0;
            info.achievementDetails = new ArrayList<>();

            for (AchievementDef def : config.achievements) {
                String fullKey = def.getFullId(config.gameId);
                boolean isUnlocked = achievementPrefs.getBoolean(
                        KEY_PREFIX + fullKey + "_unlocked", false);

                AchievementDetail detail = new AchievementDetail();
                detail.name = def.key;
                detail.level = def.level;
                detail.unlocked = isUnlocked;

                if (isUnlocked) {
                    info.unlockedCount++;
                }

                info.achievementDetails.add(detail);
            }

            info.progressPercent = info.totalCount > 0
                    ? (int) ((float) info.unlockedCount / info.totalCount * 100)
                    : 0;

            gameAchievementInfos.add(info);
        }
    }

    /**
     * 设置 RecyclerView
     */
    private void setupRecyclerView() {
        GameAchievementAdapter adapter = new GameAchievementAdapter(gameAchievementInfos);
        rvAchievementGames.setLayoutManager(new LinearLayoutManager(this));
        rvAchievementGames.setAdapter(adapter);
    }

    /**
     * 更新顶部总进度摘要
     */
    private void updateSummary() {
        int totalUnlocked = 0;
        int totalAchievements = 0;
        int gamesWithProgress = 0;

        for (GameAchievementInfo info : gameAchievementInfos) {
            totalUnlocked += info.unlockedCount;
            totalAchievements += info.totalCount;
            if (info.unlockedCount > 0) {
                gamesWithProgress++;
            }
        }

        tvTotalUnlocked.setText(String.valueOf(totalUnlocked));
        tvTotalSeparator.setText(" / " + totalAchievements);

        int overallPercent = totalAchievements > 0
                ? (int) ((float) totalUnlocked / totalAchievements * 100)
                : 0;
        progressTotal.setProgress(overallPercent);

        tvGamesSummary.setText(String.format(
                getString(R.string.achievement_center_games_summary),
                gamesWithProgress, gameAchievementInfos.size()));
    }

    // ==================== 数据模型 ====================

    /**
     * 单个游戏的成就汇总信息
     */
    private static class GameAchievementInfo {
        String gameId;
        String gameName;
        int iconResId;
        int totalCount;
        int unlockedCount;
        int progressPercent;
        List<AchievementDetail> achievementDetails;
    }

    /**
     * 单个成就详情
     */
    private static class AchievementDetail {
        String name;
        AchievementLevel level;
        boolean unlocked;
    }

    // ==================== 适配器 ====================

    /**
     * 游戏成就列表适配器
     */
    private static class GameAchievementAdapter
            extends RecyclerView.Adapter<GameAchievementAdapter.ViewHolder> {

        private final List<GameAchievementInfo> items;

        GameAchievementAdapter(List<GameAchievementInfo> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_achievement_game, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            GameAchievementInfo info = items.get(position);
            holder.bind(info);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        /**
         * ViewHolder
         */
        static class ViewHolder extends RecyclerView.ViewHolder {
            private final ImageView ivGameIcon;
            private final TextView tvGameName;
            private final TextView tvAchievementSummary;
            private final TextView tvProgressPercent;
            private final ImageView ivExpandArrow;
            private final ProgressBar progressGame;
            private final LinearLayout layoutAchievementDetail;
            private boolean isExpanded = false;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivGameIcon = itemView.findViewById(R.id.iv_game_icon);
                tvGameName = itemView.findViewById(R.id.tv_game_name);
                tvAchievementSummary = itemView.findViewById(R.id.tv_achievement_summary);
                tvProgressPercent = itemView.findViewById(R.id.tv_progress_percent);
                ivExpandArrow = itemView.findViewById(R.id.iv_expand_arrow);
                progressGame = itemView.findViewById(R.id.progress_game);
                layoutAchievementDetail = itemView.findViewById(R.id.layout_achievement_detail);
            }

            void bind(GameAchievementInfo info) {
                // 设置游戏信息
                if (info.iconResId != 0) {
                    ivGameIcon.setImageResource(info.iconResId);
                    ivGameIcon.setVisibility(View.VISIBLE);
                } else {
                    ivGameIcon.setVisibility(View.GONE);
                }

                tvGameName.setText(info.gameName);
                tvAchievementSummary.setText(String.format(
                        itemView.getContext().getString(R.string.achievement_center_unlocked_of),
                        info.unlockedCount, info.totalCount));
                tvProgressPercent.setText(String.format(
                        itemView.getContext().getString(R.string.achievement_center_percent_format),
                        info.progressPercent));
                progressGame.setProgress(info.progressPercent);

                // 设置展开/折叠
                isExpanded = false;
                layoutAchievementDetail.setVisibility(View.GONE);
                ivExpandArrow.setRotation(0f);

                // 清除旧的成就详情
                layoutAchievementDetail.removeAllViews();

                // 添加成就详情视图
                if (info.achievementDetails != null) {
                    for (AchievementDetail detail : info.achievementDetails) {
                        View detailView = LayoutInflater.from(itemView.getContext())
                                .inflate(R.layout.item_achievement_detail,
                                        layoutAchievementDetail, false);

                        View viewLevelBadge = detailView.findViewById(R.id.view_level_badge);
                        TextView tvName = detailView.findViewById(R.id.tv_achievement_name);
                        TextView tvStatus = detailView.findViewById(R.id.tv_achievement_status);

                        // 设置成就名称
                        tvName.setText(formatAchievementName(detail.name));

                        // 设置级别颜色
                        viewLevelBadge.setBackgroundColor(Color.parseColor(
                                detail.level.getColorHex()));

                        // 设置解锁状态
                        if (detail.unlocked) {
                            tvStatus.setText(itemView.getContext().getString(R.string.achievement_unlocked));
                            tvStatus.setTextColor(Color.parseColor("#5B8A72"));
                        } else {
                            tvStatus.setText(itemView.getContext().getString(R.string.achievement_locked));
                            tvStatus.setTextColor(Color.parseColor("#A8A198"));
                        }

                        layoutAchievementDetail.addView(detailView);
                    }
                }

                // 点击展开/折叠
                itemView.setOnClickListener(v -> {
                    isExpanded = !isExpanded;
                    layoutAchievementDetail.setVisibility(
                            isExpanded ? View.VISIBLE : View.GONE);
                    ivExpandArrow.setRotation(isExpanded ? 180f : 0f);
                });

                // Batch 9-3 (ACHIEVEMENT_DETAIL_PAGE): 长按游戏卡片跳转到该游戏的成就详情页
                if (BuildConfig.ACHIEVEMENT_DETAIL_PAGE) {
                    itemView.setOnLongClickListener(v -> {
                        AchievementDetailActivity.launch(v.getContext(), info.gameId);
                        return true;
                    });
                }
            }

            /**
             * 格式化成就名称，将 snake_case 转为可读名称
             */
            private String formatAchievementName(String key) {
                if (key == null || key.isEmpty()) return "";
                // 将下划线替换为空格，首字母大写
                String[] parts = key.split("_");
                StringBuilder sb = new StringBuilder();
                for (String part : parts) {
                    if (sb.length() > 0) sb.append(" ");
                    if (!part.isEmpty()) {
                        sb.append(Character.toUpperCase(part.charAt(0)));
                        if (part.length() > 1) {
                            sb.append(part.substring(1));
                        }
                    }
                }
                return sb.toString();
            }
        }
    }
}
