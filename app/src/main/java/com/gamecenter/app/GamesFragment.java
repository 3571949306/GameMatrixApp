package com.gamecenter.app;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gamecenter.app.BuildConfig;
import com.gamecenter.app.games.GameRatingStore;
import com.gamecenter.app.games.GameRegistry;
import com.gamecenter.app.games.GameUsageStore;
import com.gamecenter.app.games.RecentGamesManager;
import com.gamecenter.app.games.achievement.AchievementCenterActivity;
import com.gamecenter.app.games.achievement.DailyChallengeManager;
import com.gamecenter.app.games.achievement.DailyCheckInManager;
import com.gamecenter.app.games.achievement.StreakTracker;
import com.gamecenter.app.games.ui.GameLauncherHelper;
import com.gamecenter.app.modules.ModuleManager;
import com.gamecenter.app.modules.ModuleStoreActivity;
import com.gamecenter.app.settings.AppSettingsDialog;
import com.gamecenter.app.ui.DataBackupHelper;
import com.gamecenter.app.ui.GameFavoriteReorderHelper;
import com.gamecenter.app.ui.HeroBannerAdapter;
import com.gamecenter.app.ui.HeroBannerItem;
import com.gamecenter.app.ui.NotificationsDialog;
import com.gamecenter.app.ui.PlaytimeReminderHelper;
import com.gamecenter.app.ui.SearchHistoryManager;
import com.gamecenter.app.ui.BannerAction;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 游戏大厅 Fragment。
 * 加载 fragment_games.xml 布局，包含顶栏问候、搜索栏、分类 Tab、游戏网格。
 *
 * <p>Feature E (HOME_CARD_ENHANCE)：顶栏渐变美化、问候语、通知/头像入口、
 * 游戏卡片激活收藏按钮 / 评分行 / 热门徽章 / 图标点击动效。</p>
 */
public class GamesFragment extends Fragment {

    private static final String TAG = "GamesFragment";

    private View rootView;
    private RecyclerView rvGames;
    private GameCardAdapter adapter;
    private List<GameRegistry.Entry> allEntries = new ArrayList<>();
    private String currentCategoryKey = "all";
    private String currentKeyword = "";

    // Feature C (HOME_REVAMP): 最近游玩区域
    private View recentGamesSection;
    private LinearLayout recentGamesContainer;

    // Feature E (HOME_CARD_ENHANCE): 收藏筛选状态
    private boolean favoritesOnly = false;
    private GameUsageStore usageStore;

    // Batch 7-2 (ANIM_SHIMMER_LOADING): 骨架屏
    private RecyclerView rvShimmer;
    private android.animation.ObjectAnimator shimmerAlphaAnim;
    private boolean hasLoadedOnce = false;

    // 包 D-2 (HOME_DAILY_CARDS): 每日卡片根 View 缓存
    private View dailyCardsSection;
    // 包 B (HOME_CARD_ENHANCE): 收藏筛选 chip 组
    private ChipGroup chipGroupFilter;

    // Batch 8-1 (SEARCH_HISTORY_CHIPS): 搜索历史 Chip 流
    private View searchHistorySection;
    private ChipGroup chipGroupSearchHistory;

    // Batch 8-4 (HOME_HERO_BANNER): 首页英雄横幅
    private View heroBannerSection;
    private RecyclerView rvHeroBanner;
    private LinearLayout heroIndicatorContainer;
    private android.os.Handler heroAutoScrollHandler;
    private Runnable heroAutoScrollRunnable;
    private int heroCurrentPosition = 0;
    private static final long HERO_AUTO_SCROLL_INTERVAL_MS = 4000L;

    // Batch 10-1 (HOME_QUICK_STATS_BAR): 首页快速统计栏
    private View quickStatsSection;
    private TextView tvStatPlaytimeValue;
    private TextView tvStatStreakValue;
    private TextView tvStatAchievementsValue;

    // Batch 10-2 (HOME_GAME_OF_DAY): 今日推荐
    private View gameOfDaySection;
    private ImageView ivGameOfDayIcon;
    private TextView tvGameOfDayName;
    private TextView tvGameOfDayDesc;
    private View btnGameOfDayPlay;
    private GameRegistry.Entry gameOfDayEntry;

    // Batch 10-3 (RANDOM_GAME_FAB): 随机游戏 FAB
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabRandomGame;

    // Batch 11-1 (GAME_RATING_SYSTEM): 评分存储
    private GameRatingStore ratingStore;

    // Batch 11-3 (HOME_PLAYTIME_REMINDER): 今日时长提醒卡片
    private View playtimeReminderSection;
    private TextView tvPlaytimeReminderToday;
    private TextView tvPlaytimeReminderDesc;

    // Batch 12-1 (HOME_RESUME_GAME_CARD): 继续游玩卡片
    private View resumeGameSection;
    private ImageView ivResumeIcon;
    private TextView tvResumeGameName;
    private TextView tvResumeGameTime;
    private View btnResumePlay;
    private GameRegistry.Entry resumeEntry;

    // Batch 12-2 (ACHIEVEMENT_RECENT_UNLOCKED_BANNER): 最近解锁成就横幅
    private View recentAchievementSection;
    private TextView tvRecentAchievementName;
    private TextView tvRecentAchievementDesc;

    // Batch 12-4 (APP_LAUNCH_TIME_DISPLAY): 启动耗时小字
    private TextView tvLaunchTime;

    // Batch 11-2 (DATA_BACKUP_RESTORE): SAF launcher —— 必须在 Fragment 构造期注册
    private final ActivityResultLauncher<String> exportDataLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"),
                    uri -> handleExportResult(uri));
    private final ActivityResultLauncher<String[]> importDataLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                    uri -> handleImportResult(uri));

    public GamesFragment() {
        super(R.layout.fragment_games);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        this.rootView = view;
        if (requireContext() != null) {
            usageStore = new GameUsageStore(requireContext());
            ratingStore = new GameRatingStore(requireContext());
        }
        initViews(view);
        // 包 D-1 (DAILY_CHECKIN): 每日首次进入游戏大厅自动记录登录天数（2026-07-22 起由手动签到改为自动记录）
        recordLoginDay();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 每次可见时同步已安装模块，并刷新游戏列表
        if (requireContext() != null) {
            ModuleManager.INSTANCE.registerInstalledGameModules(requireContext());
        }
        loadGames();
        // Feature C (HOME_REVAMP): 回到大厅时刷新最近游玩记录
        if (BuildConfig.HOME_REVAMP) {
            updateRecentGames();
        }
        // Feature E: 顶栏问候语按时间刷新
        if (BuildConfig.HOME_CARD_ENHANCE) {
            updateGreeting();
        }
        // 包 D-2 (HOME_DAILY_CARDS): 回到大厅时刷新每日挑战与连胜卡片数据
        if (BuildConfig.HOME_DAILY_CARDS && rootView != null) {
            bindDailyChallengeCard(rootView);
            bindStreakSummaryCard(rootView);
        }
        // Batch 10-1 (HOME_QUICK_STATS_BAR): 回到大厅时刷新统计数据（今日时长可能变化）
        if (BuildConfig.HOME_QUICK_STATS_BAR) {
            refreshQuickStats();
        }
        // Batch 11-3 (HOME_PLAYTIME_REMINDER): 回到大厅时刷新今日时长提醒
        if (BuildConfig.HOME_PLAYTIME_REMINDER) {
            refreshPlaytimeReminder();
        }
        // Batch 12-1 (HOME_RESUME_GAME_CARD): 回到大厅时刷新"继续游玩"卡片
        if (BuildConfig.HOME_RESUME_GAME_CARD) {
            refreshResumeGameCard();
        }
        // Batch 12-2 (ACHIEVEMENT_RECENT_UNLOCKED_BANNER): 回到大厅时刷新"最近解锁成就"
        if (BuildConfig.ACHIEVEMENT_RECENT_UNLOCKED_BANNER) {
            refreshRecentAchievementBanner();
        }
        // Batch 8-4 (HOME_HERO_BANNER): 启动自动轮播
        if (BuildConfig.HOME_HERO_BANNER) {
            startHeroAutoScroll();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Batch 8-4 (HOME_HERO_BANNER): 暂停时停止自动轮播
        stopHeroAutoScroll();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Batch 7-2 (ANIM_SHIMMER_LOADING): 清理动画避免泄漏
        if (shimmerAlphaAnim != null) {
            shimmerAlphaAnim.cancel();
            shimmerAlphaAnim = null;
        }
        // Batch 8-4 (HOME_HERO_BANNER): 清理自动轮播 Handler
        stopHeroAutoScroll();
        rootView = null;
    }

    private void initViews(View v) {
        // Feature E (HOME_CARD_ENHANCE): 顶栏初始化
        if (BuildConfig.HOME_CARD_ENHANCE) {
            initTopBar(v);
        } else {
            // 向后兼容：原顶栏的设置/模块市场按钮（已被 Feature E 替换为头像菜单）
            initLegacyTopBar(v);
        }

        // 搜索框
        android.widget.EditText etSearch = v.findViewById(R.id.et_game_search);
        if (etSearch != null) {
            etSearch.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                    currentKeyword = s.toString();
                    filterAndRefresh();
                    // Batch 8-1 (SEARCH_HISTORY_CHIPS): 关键词变化时刷新历史 Chip 流显隐
                    if (BuildConfig.SEARCH_HISTORY_CHIPS) {
                        updateSearchHistoryVisibility();
                    }
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
            // Batch 8-1: 监听 IME 搜索键，把当前关键词记入历史
            if (BuildConfig.SEARCH_HISTORY_CHIPS) {
                etSearch.setOnEditorActionListener((tv, actionId, event) -> {
                    if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                            || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                        String kw = tv.getText() == null ? "" : tv.getText().toString().trim();
                        if (!kw.isEmpty()) {
                            SearchHistoryManager.getInstance(requireContext()).add(kw);
                            refreshSearchHistoryChips();
                            updateSearchHistoryVisibility();
                        }
                    }
                    return false; // 不消费事件，让 IME 默认行为（收起键盘）生效
                });
            }
        }

        // Batch 8-1 (SEARCH_HISTORY_CHIPS): 搜索历史 Chip 流初始化
        if (BuildConfig.SEARCH_HISTORY_CHIPS) {
            searchHistorySection = v.findViewById(R.id.search_history_section);
            chipGroupSearchHistory = v.findViewById(R.id.chip_group_search_history);
            View btnClear = v.findViewById(R.id.btn_search_history_clear);
            if (btnClear != null) {
                btnClear.setOnClickListener(btn -> {
                    SearchHistoryManager.getInstance(requireContext()).clear();
                    refreshSearchHistoryChips();
                    updateSearchHistoryVisibility();
                    Toast.makeText(requireContext(),
                            R.string.search_history_cleared, Toast.LENGTH_SHORT).show();
                });
            }
            refreshSearchHistoryChips();
            updateSearchHistoryVisibility();
        }

        // Batch 8-4 (HOME_HERO_BANNER): 首页英雄横幅
        if (BuildConfig.HOME_HERO_BANNER) {
            initHeroBanner(v);
        }

        // TabLayout
        TabLayout tabLayout = v.findViewById(R.id.tab_layout);
        if (tabLayout != null) {
            tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override public void onTabSelected(TabLayout.Tab tab) {
                    int pos = tab.getPosition();
                    if (pos == 0) currentCategoryKey = "all";
                    else if (pos == 1) currentCategoryKey = GameRegistry.CATEGORY_CLASSICS;
                    else if (pos == 2) currentCategoryKey = GameRegistry.CATEGORY_PUZZLE;
                    else if (pos == 3) currentCategoryKey = GameRegistry.CATEGORY_CASUAL;
                    filterAndRefresh();
                }
                @Override public void onTabUnselected(TabLayout.Tab tab) {}
                @Override public void onTabReselected(TabLayout.Tab tab) {}
            });
        }

        // RecyclerView
        rvGames = v.findViewById(R.id.rv_games);
        if (rvGames != null) {
            rvGames.setLayoutManager(new GridLayoutManager(requireContext(), 2));
            adapter = new GameCardAdapter(requireContext(), this::onGameClick);
            rvGames.setAdapter(adapter);
        }

        // Batch 9-2 (HOME_PULL_REFRESH): 下拉刷新
        if (BuildConfig.HOME_PULL_REFRESH) {
            setupPullRefresh(v);
        }

        // Batch 7-2 (ANIM_SHIMMER_LOADING): 初始化骨架屏
        if (BuildConfig.ANIM_SHIMMER_LOADING) {
            rvShimmer = v.findViewById(R.id.rv_shimmer);
            if (rvShimmer != null) {
                rvShimmer.setLayoutManager(new GridLayoutManager(requireContext(), 2));
                rvShimmer.setAdapter(new ShimmerCardAdapter());
            }
        }

        updateEmptyState(false);

        // Feature C (HOME_REVAMP): 最近游玩区域初始化
        recentGamesSection = v.findViewById(R.id.recent_games_section);
        recentGamesContainer = v.findViewById(R.id.recent_games_container);
        if (BuildConfig.HOME_REVAMP && recentGamesSection != null) {
            recentGamesSection.setVisibility(View.VISIBLE);
            View btnRecentClear = v.findViewById(R.id.btn_recent_clear);
            if (btnRecentClear != null) {
                btnRecentClear.setOnClickListener(btn -> {
                    RecentGamesManager.getInstance(requireContext()).clear();
                    updateRecentGames();
                    Toast.makeText(requireContext(),
                            R.string.home_recent_cleared, Toast.LENGTH_SHORT).show();
                });
            }
            updateRecentGames();
        }

        // 包 D-2 (HOME_DAILY_CARDS): 每日挑战 + 连胜概览卡片
        dailyCardsSection = v.findViewById(R.id.daily_cards_section);
        if (BuildConfig.HOME_DAILY_CARDS && dailyCardsSection != null) {
            dailyCardsSection.setVisibility(View.VISIBLE);
            bindDailyChallengeCard(v);
            bindStreakSummaryCard(v);
        }

        // 包 B (HOME_CARD_ENHANCE): 收藏筛选 chip
        chipGroupFilter = v.findViewById(R.id.chip_group_filter);
        if (BuildConfig.HOME_CARD_ENHANCE && chipGroupFilter != null) {
            chipGroupFilter.setVisibility(View.VISIBLE);
            Chip chipAll = v.findViewById(R.id.chip_filter_all);
            Chip chipFav = v.findViewById(R.id.chip_filter_favorites);
            if (chipAll != null) {
                chipAll.setOnClickListener(c -> {
                    favoritesOnly = false;
                    filterAndRefresh();
                });
            }
            if (chipFav != null) {
                chipFav.setOnClickListener(c -> {
                    favoritesOnly = true;
                    filterAndRefresh();
                });
            }
        }

        // Batch 10-1 (HOME_QUICK_STATS_BAR): 首页快速统计栏
        if (BuildConfig.HOME_QUICK_STATS_BAR) {
            initQuickStats(v);
        }

        // Batch 10-2 (HOME_GAME_OF_DAY): 今日推荐卡片
        if (BuildConfig.HOME_GAME_OF_DAY) {
            initGameOfDay(v);
        }

        // Batch 10-3 (RANDOM_GAME_FAB): 随机游戏 FAB
        if (BuildConfig.RANDOM_GAME_FAB) {
            initRandomFab(v);
        }

        // Batch 11-3 (HOME_PLAYTIME_REMINDER): 今日时长提醒卡片
        if (BuildConfig.HOME_PLAYTIME_REMINDER) {
            initPlaytimeReminder(v);
        }

        // Batch 12-1 (HOME_RESUME_GAME_CARD): 继续游玩卡片
        if (BuildConfig.HOME_RESUME_GAME_CARD) {
            initResumeGameCard(v);
        }

        // Batch 12-2 (ACHIEVEMENT_RECENT_UNLOCKED_BANNER): 最近解锁成就横幅
        if (BuildConfig.ACHIEVEMENT_RECENT_UNLOCKED_BANNER) {
            initRecentAchievementBanner(v);
        }

        // Batch 12-4 (APP_LAUNCH_TIME_DISPLAY): 启动耗时小字
        if (BuildConfig.APP_LAUNCH_TIME_DISPLAY) {
            initLaunchTimeDisplay(v);
        }
    }

    /**
     * Batch 10-1 (HOME_QUICK_STATS_BAR): 初始化首页快速统计栏。
     * 显示今日时长、当前连胜、已解锁成就数。
     */
    private void initQuickStats(View v) {
        quickStatsSection = v.findViewById(R.id.quick_stats_section);
        if (quickStatsSection == null) return;
        quickStatsSection.setVisibility(View.VISIBLE);
        tvStatPlaytimeValue = quickStatsSection.findViewById(R.id.tv_stat_playtime_value);
        tvStatStreakValue = quickStatsSection.findViewById(R.id.tv_stat_streak_value);
        tvStatAchievementsValue = quickStatsSection.findViewById(R.id.tv_stat_achievements_value);
        refreshQuickStats();
    }

    /**
     * Batch 10-1: 刷新快速统计栏数据。
     * 从 GameUsageStore 读取今日时长；StreakTracker 读取连胜；AchievementManager 统计已解锁。
     */
    private void refreshQuickStats() {
        if (quickStatsSection == null || quickStatsSection.getVisibility() != View.VISIBLE) return;
        Context ctx = requireContext();

        // 今日时长
        long todayMs = usageStore != null ? usageStore.getTodayPlayTimeMs() : 0L;
        long todayMinutes = todayMs / 60000L;
        if (tvStatPlaytimeValue != null) {
            if (todayMinutes > 0) {
                tvStatPlaytimeValue.setText(getString(R.string.home_stats_playtime_format,
                        String.valueOf(todayMinutes)));
            } else {
                tvStatPlaytimeValue.setText(R.string.home_stats_playtime_zero);
            }
        }

        // 连胜
        int streak = StreakTracker.getInstance(ctx).getCurrentStreak();
        if (tvStatStreakValue != null) {
            tvStatStreakValue.setText(getString(R.string.home_stats_streak_format, streak));
        }

        // 成就：从 AchievementManager SharedPreferences 读取已解锁数
        SharedPreferences achPrefs = ctx.getSharedPreferences("game_achievements", Context.MODE_PRIVATE);
        int unlocked = 0;
        java.util.Map<String, ?> all = achPrefs.getAll();
        for (java.util.Map.Entry<String, ?> e : all.entrySet()) {
            if (e.getKey().startsWith("unlock_") && Boolean.TRUE.equals(e.getValue())) {
                unlocked++;
            }
        }
        if (tvStatAchievementsValue != null) {
            // 总数未知（无 registry），只展示已解锁数；后缀 "+"
            tvStatAchievementsValue.setText(String.valueOf(unlocked));
        }
    }

    /**
     * Batch 10-2 (HOME_GAME_OF_DAY): 初始化今日推荐卡片。
     * 基于当日日期 hash 选取一款游戏，每天稳定不变。
     */
    private void initGameOfDay(View v) {
        gameOfDaySection = v.findViewById(R.id.game_of_day_section);
        if (gameOfDaySection == null) return;
        ivGameOfDayIcon = gameOfDaySection.findViewById(R.id.iv_game_of_day_icon);
        tvGameOfDayName = gameOfDaySection.findViewById(R.id.tv_game_of_day_name);
        tvGameOfDayDesc = gameOfDaySection.findViewById(R.id.tv_game_of_day_desc);
        btnGameOfDayPlay = gameOfDaySection.findViewById(R.id.btn_game_of_day_play);

        refreshGameOfDay();

        if (btnGameOfDayPlay != null) {
            btnGameOfDayPlay.setOnClickListener(btn -> {
                if (gameOfDayEntry != null) {
                    launchGame(gameOfDayEntry);
                }
            });
        }
        // 整张卡片也可点击
        gameOfDaySection.setOnClickListener(card -> {
            if (gameOfDayEntry != null) {
                launchGame(gameOfDayEntry);
            }
        });
    }

    /**
     * Batch 10-2: 重新计算今日推荐游戏。
     * 当 allEntries 为空时直接隐藏卡片。
     */
    private void refreshGameOfDay() {
        if (gameOfDaySection == null) return;
        if (allEntries == null || allEntries.isEmpty()) {
            gameOfDaySection.setVisibility(View.GONE);
            return;
        }
        // 基于日期的稳定 hash（同一天打开应用看到同一款游戏）
        Calendar cal = Calendar.getInstance();
        long dayKey = cal.get(Calendar.YEAR) * 10000L
                + (cal.get(Calendar.MONTH) + 1) * 100L
                + cal.get(Calendar.DAY_OF_MONTH);
        int idx = (int) (Math.abs(dayKey) % allEntries.size());
        gameOfDayEntry = allEntries.get(idx);
        gameOfDaySection.setVisibility(View.VISIBLE);

        if (ivGameOfDayIcon != null && gameOfDayEntry.iconRes != 0) {
            ivGameOfDayIcon.setImageResource(gameOfDayEntry.iconRes);
        }
        if (tvGameOfDayName != null) {
            tvGameOfDayName.setText(gameOfDayEntry.name);
        }
        if (tvGameOfDayDesc != null) {
            // 根据分类选择描述
            int descRes;
            String cat = gameOfDayEntry.categoryKey;
            if (GameRegistry.CATEGORY_PUZZLE.equals(cat)) {
                descRes = R.string.home_game_of_day_desc_puzzle;
            } else if (GameRegistry.CATEGORY_CASUAL.equals(cat)) {
                descRes = R.string.home_game_of_day_desc_casual;
            } else {
                descRes = R.string.home_game_of_day_desc_classics;
            }
            tvGameOfDayDesc.setText(descRes);
        }
    }

    /**
     * Batch 10-3 (RANDOM_GAME_FAB): 初始化随机游戏悬浮按钮。
     * 点击后从 allEntries 随机抽取一款游戏并启动。
     */
    private void initRandomFab(View v) {
        fabRandomGame = v.findViewById(R.id.fab_random_game);
        if (fabRandomGame == null) return;
        fabRandomGame.setVisibility(View.VISIBLE);
        fabRandomGame.setOnClickListener(btn -> launchRandomGame());
        // 长按提示
        fabRandomGame.setOnLongClickListener(btn -> {
            Toast.makeText(requireContext(),
                    R.string.random_game_fab_desc, Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    /**
     * Batch 10-3: 从 allEntries 随机抽取一款游戏启动。
     * 若列表为空，弹出"暂无可启动的游戏"提示。
     */
    private void launchRandomGame() {
        if (allEntries == null || allEntries.isEmpty()) {
            Toast.makeText(requireContext(),
                    R.string.random_game_no_available, Toast.LENGTH_SHORT).show();
            return;
        }
        int idx = (int) (Math.random() * allEntries.size());
        GameRegistry.Entry entry = allEntries.get(idx);
        Toast.makeText(requireContext(),
                getString(R.string.random_game_launched_format, entry.name),
                Toast.LENGTH_SHORT).show();
        launchGame(entry);
    }

    /**
     * Batch 11-3 (HOME_PLAYTIME_REMINDER): 初始化今日时长提醒卡片。
     * 找到根布局并绑定"知道了"按钮点击事件。
     */
    private void initPlaytimeReminder(View v) {
        playtimeReminderSection = v.findViewById(R.id.playtime_reminder_section);
        if (playtimeReminderSection == null) return;
        tvPlaytimeReminderToday = playtimeReminderSection.findViewById(R.id.tv_playtime_reminder_today);
        tvPlaytimeReminderDesc = playtimeReminderSection.findViewById(R.id.tv_playtime_reminder_desc);
        View btnDismiss = playtimeReminderSection.findViewById(R.id.btn_playtime_reminder_dismiss);
        if (btnDismiss != null) {
            btnDismiss.setOnClickListener(btn -> {
                PlaytimeReminderHelper.INSTANCE.dismissForSession();
                playtimeReminderSection.setVisibility(View.GONE);
            });
        }
        refreshPlaytimeReminder();
    }

    /**
     * Batch 11-3: 评估今日游玩时长档位并刷新提醒卡片。
     * - NONE：隐藏卡片
     * - MILD：橙色渐变 + 60 分钟文案
     * - SEVERE：红色渐变 + 120 分钟文案
     */
    private void refreshPlaytimeReminder() {
        if (playtimeReminderSection == null) return;
        Context ctx = requireContext();
        PlaytimeReminderHelper.Level level = PlaytimeReminderHelper.INSTANCE.evaluate(ctx);
        if (level == PlaytimeReminderHelper.Level.NONE) {
            playtimeReminderSection.setVisibility(View.GONE);
            return;
        }
        playtimeReminderSection.setVisibility(View.VISIBLE);
        int minutes = PlaytimeReminderHelper.INSTANCE.todayMinutes(ctx);
        // 切换背景档位
        View root = playtimeReminderSection.findViewById(R.id.btn_playtime_reminder_dismiss) != null
                ? (View) playtimeReminderSection
                : playtimeReminderSection;
        // playtimeReminderSection 本身是 FrameLayout，背景由内嵌的 LinearLayout 决定；
        // 直接修改其 background 即可
        if (level == PlaytimeReminderHelper.Level.SEVERE) {
            playtimeReminderSection.setBackgroundResource(R.drawable.bg_playtime_reminder_gradient_severe);
        } else {
            playtimeReminderSection.setBackgroundResource(R.drawable.bg_playtime_reminder_gradient_mild);
        }
        if (tvPlaytimeReminderToday != null) {
            tvPlaytimeReminderToday.setText(getString(
                    R.string.playtime_reminder_today_format, String.valueOf(minutes)));
        }
        if (tvPlaytimeReminderDesc != null) {
            tvPlaytimeReminderDesc.setText(level == PlaytimeReminderHelper.Level.SEVERE
                    ? R.string.playtime_reminder_desc_120
                    : R.string.playtime_reminder_desc_60);
        }
    }

    // ==================== Batch 12-1 (HOME_RESUME_GAME_CARD) ====================

    /**
     * Batch 12-1: 初始化"继续游玩"卡片。
     * 绑定 UI 元素与点击事件（卡片整体与"继续"按钮均可启动游戏）。
     */
    private void initResumeGameCard(View v) {
        resumeGameSection = v.findViewById(R.id.resume_game_section);
        if (resumeGameSection == null) return;
        ivResumeIcon = resumeGameSection.findViewById(R.id.iv_resume_icon);
        tvResumeGameName = resumeGameSection.findViewById(R.id.tv_resume_game_name);
        tvResumeGameTime = resumeGameSection.findViewById(R.id.tv_resume_game_time);
        btnResumePlay = resumeGameSection.findViewById(R.id.btn_resume_play);
        View.OnClickListener launch = view -> {
            if (resumeEntry == null) return;
            Toast.makeText(requireContext(),
                    getString(R.string.home_resume_game_action) + ": " + resumeEntry.name,
                    Toast.LENGTH_SHORT).show();
            launchGame(resumeEntry);
        };
        if (btnResumePlay != null) btnResumePlay.setOnClickListener(launch);
        resumeGameSection.setOnClickListener(launch);
        refreshResumeGameCard();
    }

    /**
     * Batch 12-1: 刷新"继续游玩"卡片数据。
     * 通过 [ResumeGameHelper] 获取最近游玩的 Entry，若无记录则隐藏整张卡片。
     */
    private void refreshResumeGameCard() {
        if (resumeGameSection == null) return;
        Context ctx = requireContext();
        GameRegistry.Entry entry = com.gamecenter.app.ui.ResumeGameHelper.INSTANCE.getResumeEntry(ctx);
        resumeEntry = entry;
        if (entry == null) {
            // 首页沉浸式改版 (HOME_IMMERSIVE_REVAMP, 2026-07-21) / V2 游戏活力风 (HOME_REVAMP_V2, 2026-07-22)：
            // 空数据时显示引导卡片而非隐藏，避免首屏空白。
            // 注意：此处仅控制 visibility，具体引导文案由 layout_resume_game 的默认值承载；
            // 若改版 flag 关闭，则保持原行为（GONE）。
            if (BuildConfig.HOME_IMMERSIVE_REVAMP || BuildConfig.HOME_REVAMP_V2) {
                resumeGameSection.setVisibility(View.VISIBLE);
                if (tvResumeGameName != null) {
                    tvResumeGameName.setText(R.string.home_resume_empty_title);
                }
                if (tvResumeGameTime != null) {
                    tvResumeGameTime.setText(R.string.home_resume_empty_hint);
                }
            } else {
                resumeGameSection.setVisibility(View.GONE);
            }
            return;
        }
        resumeGameSection.setVisibility(View.VISIBLE);
        if (ivResumeIcon != null && entry.iconRes != 0) {
            ivResumeIcon.setImageResource(entry.iconRes);
        }
        if (tvResumeGameName != null) {
            tvResumeGameName.setText(entry.name);
        }
        if (tvResumeGameTime != null) {
            String span = com.gamecenter.app.ui.ResumeGameHelper.INSTANCE.getRelativeTimeSpan(ctx, entry.id);
            tvResumeGameTime.setText(getString(R.string.home_resume_game_time_format, span));
        }
    }

    // ==================== Batch 12-2 (ACHIEVEMENT_RECENT_UNLOCKED_BANNER) ====================

    /**
     * Batch 12-2: 初始化"最近解锁成就"横幅。
     * 绑定关闭按钮（当日不再显示）与点击事件。
     */
    private void initRecentAchievementBanner(View v) {
        recentAchievementSection = v.findViewById(R.id.recent_achievement_section);
        if (recentAchievementSection == null) return;
        tvRecentAchievementName = recentAchievementSection.findViewById(R.id.tv_recent_achievement_name);
        tvRecentAchievementDesc = recentAchievementSection.findViewById(R.id.tv_recent_achievement_desc);
        View btnDismiss = recentAchievementSection.findViewById(R.id.btn_recent_achievement_dismiss);
        if (btnDismiss != null) {
            btnDismiss.setOnClickListener(btn -> {
                com.gamecenter.app.ui.RecentAchievementHelper.INSTANCE.dismissForSession(requireContext());
                recentAchievementSection.setVisibility(View.GONE);
            });
        }
        refreshRecentAchievementBanner();
    }

    /**
     * Batch 12-2: 刷新"最近解锁成就"横幅。
     * 若今日已被 dismiss 或无已解锁成就，则隐藏。
     */
    private void refreshRecentAchievementBanner() {
        if (recentAchievementSection == null) return;
        Context ctx = requireContext();
        if (com.gamecenter.app.ui.RecentAchievementHelper.INSTANCE.isDismissedToday(ctx)) {
            recentAchievementSection.setVisibility(View.GONE);
            return;
        }
        com.gamecenter.app.ui.RecentAchievementHelper.RecentAchievement recent =
                com.gamecenter.app.ui.RecentAchievementHelper.INSTANCE.getRecent(ctx);
        if (recent == null) {
            recentAchievementSection.setVisibility(View.GONE);
            return;
        }
        recentAchievementSection.setVisibility(View.VISIBLE);
        if (tvRecentAchievementName != null) {
            String title = com.gamecenter.app.ui.RecentAchievementHelper.INSTANCE.resolveTitle(ctx, recent.getId());
            tvRecentAchievementName.setText(title);
        }
        if (tvRecentAchievementDesc != null) {
            String desc = com.gamecenter.app.ui.RecentAchievementHelper.INSTANCE.resolveDescription(ctx, recent.getId());
            tvRecentAchievementDesc.setText(desc);
        }
    }

    // ==================== Batch 12-4 (APP_LAUNCH_TIME_DISPLAY) ====================

    /**
     * Batch 12-4: 在首页角落小字显示启动耗时。
     * 数据源：[com.gamecenter.app.ui.LaunchTimeTracker]（由 SplashActivity.markStart 标记）。
     * 显示规则：仅 Debug 构建可见，且仅在第一次 onViewCreated 时显示（避免 onResume 重复刷新）。
     */
    private void initLaunchTimeDisplay(View v) {
        // 复用 top_bar 区域的 subtitle TextView，没有则跳过
        TextView tvSubtitle = v.findViewById(R.id.tv_subtitle);
        if (tvSubtitle == null) return;
        tvLaunchTime = tvSubtitle;
        long elapsed = com.gamecenter.app.ui.LaunchTimeTracker.INSTANCE.elapsedMs();
        if (elapsed < 0L) return;
        String original = tvSubtitle.getText() == null ? "" : tvSubtitle.getText().toString();
        String launchInfo = getString(R.string.app_launch_time_format, elapsed);
        // 仅在 Debug 构建下追加显示，避免污染 Release 用户体验
        if (BuildConfig.DEBUG) {
            tvSubtitle.setText(launchInfo + " · " + original);
        }
    }

    // ==================== Batch 11-2 (DATA_BACKUP_RESTORE) ====================

    /**
     * 打开应用设置对话框，并注入数据导出/导入 SAF 回调。
     * 集中构造避免在多处重复 5 参构造函数调用。
     */
    private void openSettings() {
        new AppSettingsDialog(
                GamesFragment.this,
                null,
                null,
                () -> exportDataLauncher.launch(DataBackupHelper.INSTANCE.defaultFilename()),
                () -> importDataLauncher.launch(new String[]{
                        "application/json", "application/octet-stream", "*/*"
                })
        ).show();
    }

    /** SAF 导出回调：在后台线程写入 JSON。 */
    private void handleExportResult(Uri uri) {
        if (uri == null) return; // 用户取消
        Context ctx = requireContext();
        new Thread(() -> {
            try (android.os.ParcelFileDescriptor pfd =
                         ctx.getContentResolver().openFileDescriptor(uri, "w")) {
                if (pfd == null) {
                    toastOnUi(ctx, ctx.getString(R.string.data_backup_export_failed, "openFileDescriptor returned null"));
                    return;
                }
                java.io.FileOutputStream fos = new java.io.FileOutputStream(pfd.getFileDescriptor());
                long bytes = DataBackupHelper.INSTANCE.exportToJson(ctx, fos);
                fos.flush();
                fos.close();
                toastOnUi(ctx, ctx.getString(R.string.data_backup_export_success,
                        humanSize(bytes)));
            } catch (Exception e) {
                Log.e(TAG, "导出数据失败", e);
                toastOnUi(ctx, ctx.getString(R.string.data_backup_export_failed,
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }).start();
    }

    /** SAF 导入回调：在后台线程读取 JSON 并覆盖 SharedPreferences。 */
    private void handleImportResult(Uri uri) {
        if (uri == null) return; // 用户取消
        Context ctx = requireContext();
        new Thread(() -> {
            try (java.io.InputStream is = ctx.getContentResolver().openInputStream(uri)) {
                if (is == null) {
                    toastOnUi(ctx, ctx.getString(R.string.data_backup_import_failed, "openInputStream returned null"));
                    return;
                }
                int count = DataBackupHelper.INSTANCE.importFromJson(ctx, is);
                toastOnUi(ctx, ctx.getString(R.string.data_backup_import_success, count));
                // 重新加载游戏列表 + 刷新各 section（导入可能改变了收藏/评分）
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        try {
                            if (requireContext() != null) {
                                loadGames();
                            }
                            // 主题/语言/声音设置可能变化，重建 Activity 让所有界面元素刷新
                            if (getActivity() != null) {
                                getActivity().recreate();
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "导入后刷新 UI 失败", e);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "导入数据失败", e);
                toastOnUi(ctx, ctx.getString(R.string.data_backup_import_failed,
                        e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }).start();
    }

    /** 在 UI 线程显示 Toast（resId 版本）。 */
    private void toastOnUi(Context ctx, int resId) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() ->
                    Toast.makeText(ctx, resId, Toast.LENGTH_SHORT).show());
        }
    }

    /** 在 UI 线程显示 Toast（文本版本）。 */
    private void toastOnUi(Context ctx, String text) {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() ->
                    Toast.makeText(ctx, text, Toast.LENGTH_LONG).show());
        }
    }

    /** 字节数转人类可读字符串。 */
    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024));
    }

    /**
     * 包 D-2 (HOME_DAILY_CARDS): 绑定每日挑战卡片数据。
     * 从 {@link DailyChallengeManager} 读取今日挑战，刷新状态、描述、进度条。
     */
    private void bindDailyChallengeCard(View v) {
        View cardRoot = v.findViewById(R.id.card_daily_challenge_include);
        if (cardRoot == null) return;
        DailyChallengeManager.Challenge c =
                DailyChallengeManager.getInstance(requireContext()).getTodayChallenge();
        TextView tvStatus = cardRoot.findViewById(R.id.tv_daily_challenge_status);
        TextView tvDesc = cardRoot.findViewById(R.id.tv_daily_challenge_desc);
        ProgressBar pb = cardRoot.findViewById(R.id.progress_daily_challenge);
        TextView tvProgress = cardRoot.findViewById(R.id.tv_daily_challenge_progress);
        if (tvStatus != null) {
            tvStatus.setText(c.progress >= c.target
                    ? R.string.daily_challenge_completed
                    : R.string.daily_challenge);
        }
        if (tvDesc != null) {
            String name = c.gameName == null || c.gameName.isEmpty()
                    ? getString(R.string.daily_challenge) : c.gameName;
            tvDesc.setText(name + " " + c.progress + "/" + c.target);
        }
        if (pb != null) {
            int percent = c.target > 0 ? (int) (c.progress * 100f / c.target) : 0;
            pb.setProgress(Math.min(percent, 100));
        }
        if (tvProgress != null) {
            tvProgress.setText(getString(R.string.daily_challenge_progress_format, c.progress, c.target));
        }
        // 点击卡片提示
        cardRoot.setOnClickListener(card -> Toast.makeText(requireContext(),
                R.string.home_daily_card_click_hint, Toast.LENGTH_SHORT).show());
    }

    /**
     * 包 D-2 (HOME_DAILY_CARDS): 绑定连胜概览卡片数据。
     * 从 {@link StreakTracker} 读取当前连胜、最佳连胜、总对局。
     */
    private void bindStreakSummaryCard(View v) {
        View cardRoot = v.findViewById(R.id.card_streak_summary_include);
        if (cardRoot == null) return;
        StreakTracker tracker = StreakTracker.getInstance(requireContext());
        TextView tvCurrent = cardRoot.findViewById(R.id.tv_streak_current);
        TextView tvCurrentValue = cardRoot.findViewById(R.id.tv_streak_current_value);
        TextView tvBest = cardRoot.findViewById(R.id.tv_streak_best_value);
        TextView tvTotal = cardRoot.findViewById(R.id.tv_streak_total_value);
        int cur = tracker.getCurrentStreak();
        int best = tracker.getBestStreak();
        int total = tracker.getTotalGames();
        if (tvCurrent != null) {
            tvCurrent.setText(getString(R.string.daily_checkin_unit_days, cur));
        }
        if (tvCurrentValue != null) {
            tvCurrentValue.setText(String.valueOf(cur));
        }
        if (tvBest != null) {
            tvBest.setText(String.valueOf(best));
        }
        if (tvTotal != null) {
            tvTotal.setText(String.valueOf(total));
        }
        // 点击卡片提示
        cardRoot.setOnClickListener(card -> Toast.makeText(requireContext(),
                R.string.home_streak_card_click_hint, Toast.LENGTH_SHORT).show());
    }

    // ===== Batch 9-2 (HOME_PULL_REFRESH) =====

    /**
     * 初始化首页下拉刷新：使用主题色 4 段循环刷新头，刷新回调重新加载游戏列表 + 顶栏数据。
     */
    private void setupPullRefresh(View v) {
        androidx.swiperefreshlayout.widget.SwipeRefreshLayout srl =
                v.findViewById(R.id.swipe_refresh_games);
        if (srl == null) return;
        // 4 段主题色循环，刷新头视觉与品牌一致
        int[] colors = new int[]{
                getResources().getColor(R.color.top_bar_gradient_start, requireContext().getTheme()),
                getResources().getColor(R.color.top_bar_gradient_end, requireContext().getTheme()),
                getResources().getColor(R.color.brand_primary, requireContext().getTheme()),
                getResources().getColor(R.color.brand_secondary, requireContext().getTheme())
        };
        try {
            srl.setColorSchemeColors(colors[0], colors[1], colors[2], colors[3]);
        } catch (Exception e) {
            // 颜色读不到时回退到单色
            srl.setColorSchemeColors(colors[2]);
        }
        srl.setOnRefreshListener(() -> {
            // 重新加载游戏列表 + 已安装模块 + 顶栏数据
            try {
                if (requireContext() != null) {
                    ModuleManager.INSTANCE.registerInstalledGameModules(requireContext());
                }
                loadGames();
                if (BuildConfig.HOME_REVAMP) updateRecentGames();
                if (BuildConfig.HOME_DAILY_CARDS && rootView != null) {
                    bindDailyChallengeCard(rootView);
                    bindStreakSummaryCard(rootView);
                }
                Toast.makeText(requireContext(),
                        R.string.home_pull_refresh_done, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.w(TAG, "下拉刷新失败", e);
            } finally {
                srl.setRefreshing(false);
            }
        });
    }

    // ===== Batch 8-1 (SEARCH_HISTORY_CHIPS) =====

    /**
     * 刷新搜索历史 Chip 流内容。从 [SearchHistoryManager] 读取历史，重建 Chip。
     * Chip 点击：填充搜索框并触发过滤；长按：删除单条历史。
     */
    private void refreshSearchHistoryChips() {
        if (chipGroupSearchHistory == null) return;
        chipGroupSearchHistory.removeAllViews();
        List<String> history = SearchHistoryManager.getInstance(requireContext()).getHistory();
        for (String kw : history) {
            Chip chip = new Chip(requireContext(), null,
                    com.google.android.material.R.attr.chipStyle);
            chip.setText(kw);
            chip.setClickable(true);
            chip.setCheckable(false);
            chip.setOnClickListener(c -> {
                android.widget.EditText et = rootView.findViewById(R.id.et_game_search);
                if (et != null) {
                    et.setText(kw);
                    et.setSelection(kw.length());
                }
                currentKeyword = kw;
                filterAndRefresh();
                updateSearchHistoryVisibility();
            });
            chipGroupSearchHistory.addView(chip);
        }
    }

    /**
     * 更新搜索历史区域显隐。仅当搜索框为空且历史非空时显示。
     */
    private void updateSearchHistoryVisibility() {
        if (searchHistorySection == null) return;
        String kw = currentKeyword == null ? "" : currentKeyword.trim();
        boolean shouldShow = kw.isEmpty()
                && !SearchHistoryManager.getInstance(requireContext()).getHistory().isEmpty();
        searchHistorySection.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
    }

    // ===== Batch 8-4 (HOME_HERO_BANNER) =====

    /**
     * 初始化首页英雄横幅：RecyclerView + PagerSnapHelper + 适配器 + 指示器。
     */
    private void initHeroBanner(View v) {
        heroBannerSection = v.findViewById(R.id.hero_banner_section);
        rvHeroBanner = v.findViewById(R.id.rv_hero_banner);
        heroIndicatorContainer = v.findViewById(R.id.hero_indicator_container);
        if (rvHeroBanner == null) return;

        // 构造 3 张横幅
        List<HeroBannerItem> items = Arrays.asList(
                new HeroBannerItem(
                        1L,
                        com.gamecenter.app.ui.BannerType.DAILY_PICK,
                        R.drawable.bg_hero_banner_daily,
                        R.drawable.ic_daily_challenge,
                        R.string.hero_banner_daily_title,
                        R.string.hero_banner_daily_subtitle,
                        R.string.hero_banner_daily_cta,
                        BannerAction.OPEN_DAILY_CHALLENGE
                ),
                new HeroBannerItem(
                        2L,
                        com.gamecenter.app.ui.BannerType.EVENT,
                        R.drawable.bg_hero_banner_event,
                        R.drawable.ic_streak_fire,
                        R.string.hero_banner_event_title,
                        R.string.hero_banner_event_subtitle,
                        R.string.hero_banner_event_cta,
                        BannerAction.OPEN_MODULE_STORE
                ),
                new HeroBannerItem(
                        3L,
                        com.gamecenter.app.ui.BannerType.CHALLENGE,
                        R.drawable.bg_hero_banner_challenge,
                        R.drawable.ic_stats_streak,
                        R.string.hero_banner_challenge_title,
                        R.string.hero_banner_challenge_subtitle,
                        R.string.hero_banner_challenge_cta,
                        BannerAction.OPEN_ACHIEVEMENT
                )
        );

        HeroBannerAdapter heroAdapter = new HeroBannerAdapter(requireContext(), items,
                item -> { onHeroBannerClick(item); return kotlin.Unit.INSTANCE; });
        androidx.recyclerview.widget.LinearLayoutManager lm =
                new androidx.recyclerview.widget.LinearLayoutManager(
                        requireContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false);
        rvHeroBanner.setLayoutManager(lm);
        rvHeroBanner.setAdapter(heroAdapter);
        // PagerSnapHelper 让滑动结束后停在某一页中心
        androidx.recyclerview.widget.PagerSnapHelper snapHelper =
                new androidx.recyclerview.widget.PagerSnapHelper();
        snapHelper.attachToRecyclerView(rvHeroBanner);
        // 监听滑动结束位置，更新指示器
        rvHeroBanner.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    View snappedView = snapHelper.findSnapView(lm);
                    if (snappedView != null) {
                        int pos = lm.getPosition(snappedView);
                        heroCurrentPosition = pos;
                        updateHeroIndicator(pos, items.size());
                    }
                }
            }
        });

        // 初始化指示器
        updateHeroIndicator(0, items.size());

        // 显示横幅区域
        if (heroBannerSection != null) heroBannerSection.setVisibility(View.VISIBLE);

        // 初始化自动轮播 Handler
        if (heroAutoScrollHandler == null) {
            heroAutoScrollHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        }
        heroAutoScrollRunnable = new Runnable() {
            @Override
            public void run() {
                if (rvHeroBanner == null || rvHeroBanner.getAdapter() == null) return;
                int count = rvHeroBanner.getAdapter().getItemCount();
                if (count <= 1) return;
                int next = (heroCurrentPosition + 1) % count;
                rvHeroBanner.smoothScrollToPosition(next);
                heroCurrentPosition = next;
                updateHeroIndicator(next, count);
                heroAutoScrollHandler.postDelayed(this, HERO_AUTO_SCROLL_INTERVAL_MS);
            }
        };
    }

    /**
     * 更新底部圆点指示器。
     */
    private void updateHeroIndicator(int current, int total) {
        if (heroIndicatorContainer == null || total <= 0) return;
        heroIndicatorContainer.removeAllViews();
        int dotSize = (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 6,
                getResources().getDisplayMetrics());
        int dotMargin = (int) android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 4,
                getResources().getDisplayMetrics());
        for (int i = 0; i < total; i++) {
            android.widget.ImageView dot = new android.widget.ImageView(requireContext());
            int size = i == current
                    ? (int) android.util.TypedValue.applyDimension(
                            android.util.TypedValue.COMPLEX_UNIT_DIP, 16,
                            getResources().getDisplayMetrics())
                    : dotSize;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, dotSize);
            lp.setMargins(dotMargin, 0, dotMargin, 0);
            dot.setLayoutParams(lp);
            dot.setImageResource(i == current
                    ? R.drawable.indicator_dot_active
                    : R.drawable.indicator_dot_inactive);
            heroIndicatorContainer.addView(dot);
        }
    }

    /**
     * 横幅点击处理。
     */
    private void onHeroBannerClick(HeroBannerItem item) {
        stopHeroAutoScroll(); // 用户交互时暂停自动轮播
        try {
            if (item.getAction() == BannerAction.OPEN_ACHIEVEMENT) {
                startActivity(new Intent(requireContext(), AchievementCenterActivity.class));
            } else if (item.getAction() == BannerAction.OPEN_MODULE_STORE) {
                startActivity(new Intent(requireContext(), ModuleStoreActivity.class));
            } else if (item.getAction() == BannerAction.OPEN_DAILY_CHALLENGE) {
                // 2026-07-22 起签到改为自动记录登录天数，横幅点击改为展示登录天数提示
                if (BuildConfig.DAILY_CHECKIN) {
                    try {
                        DailyCheckInManager mgr = DailyCheckInManager.getInstance(requireContext());
                        int days = mgr.getTotalCheckInDays();
                        int consecutive = mgr.getConsecutiveDays();
                        Toast.makeText(requireContext(),
                                getString(R.string.auto_login_days_toast, days, consecutive),
                                Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Log.w(TAG, "查询登录天数失败", e);
                    }
                } else {
                    Toast.makeText(requireContext(),
                            R.string.hero_banner_daily_subtitle, Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "横幅点击处理失败", e);
        } finally {
            // 2 秒后恢复自动轮播
            if (heroAutoScrollHandler != null) {
                heroAutoScrollHandler.postDelayed(this::startHeroAutoScroll, 2000L);
            }
        }
    }

    /**
     * 启动 Hero banner 自动轮播。
     */
    private void startHeroAutoScroll() {
        if (heroAutoScrollHandler == null || heroAutoScrollRunnable == null) return;
        heroAutoScrollHandler.removeCallbacks(heroAutoScrollRunnable);
        heroAutoScrollHandler.postDelayed(heroAutoScrollRunnable, HERO_AUTO_SCROLL_INTERVAL_MS);
    }

    /**
     * 停止 Hero banner 自动轮播。
     */
    private void stopHeroAutoScroll() {
        if (heroAutoScrollHandler != null && heroAutoScrollRunnable != null) {
            heroAutoScrollHandler.removeCallbacks(heroAutoScrollRunnable);
        }
    }

    /**
     * 包 D-1 (DAILY_CHECKIN): 每日首次进入游戏大厅自动记录登录天数。
     *
     * <p>2026-07-22 起由"手动签到弹窗"改为"自动记录登录天数"，
     * 降低单机 app 的用户粘性负担。用户无感知，后台自动累计连续登录天数与总登录天数。</p>
     *
     * <p>逻辑幂等：同一天多次进入大厅只会记录一次。</p>
     */
    private void recordLoginDay() {
        if (!BuildConfig.DAILY_CHECKIN) return;
        if (rootView == null) return;
        try {
            DailyCheckInManager.getInstance(requireContext()).recordLoginDay();
        } catch (Exception e) {
            Log.w(TAG, "记录登录天数失败", e);
        }
    }

    /**
     * Feature E (HOME_CARD_ENHANCE): 初始化新顶栏。
     * 头像按钮打开 PopupMenu（模块市场 / 设置 / 成就中心 / 每日签到）；通知按钮打开通知中心；
     * 问候语根据当前小时数自动切换。
     */
    private void initTopBar(View v) {
        updateGreeting();

        View btnAvatar = v.findViewById(R.id.btn_avatar);
        if (btnAvatar != null) {
            btnAvatar.setOnClickListener(btn -> {
                PopupMenu popup = new PopupMenu(requireContext(), btn);
                popup.getMenu().add(0, 1, 0, R.string.nav_games);
                popup.getMenu().add(0, 2, 0, R.string.home_top_avatar);
                popup.getMenu().add(0, 3, 0, R.string.stats_title);
                popup.getMenu().add(0, 4, 0, R.string.settings_title);
                popup.getMenu().add(0, 5, 0, R.string.achievement_center_title);
                // 包 D-1 (DAILY_CHECKIN): 2026-07-22 起签到改为自动记录登录天数，头像菜单不再提供手动签到入口
                // wrongbook 从底部导航移到头像菜单（受 ENABLE_WRONGBOOK 控制）
                if (BuildConfig.ENABLE_WRONGBOOK) {
                    popup.getMenu().add(0, 7, 0, R.string.nav_wrongbook);
                }
                popup.setOnMenuItemClickListener(item -> {
                    int id = item.getItemId();
                    if (id == 2) {
                        // 模块市场
                        try {
                            startActivity(new Intent(requireContext(), ModuleStoreActivity.class));
                        } catch (Exception e) {
                            Log.e(TAG, "启动模块商店失败", e);
                            Toast.makeText(requireContext(), R.string.error_module_store_not_found, Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    } else if (id == 3) {
                        // 游戏统计
                        try {
                            startActivity(new Intent(requireContext(), com.gamecenter.app.games.StatsActivity.class));
                        } catch (Exception e) {
                            Log.e(TAG, "打开游戏统计失败", e);
                            Toast.makeText(requireContext(), R.string.error_stats_not_found, Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    } else if (id == 4) {
                        // 设置
                        try {
                            openSettings();
                        } catch (Exception e) {
                            Log.e(TAG, "打开设置失败", e);
                            Toast.makeText(requireContext(), R.string.error_settings_open_failed, Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    } else if (id == 5) {
                        // 成就中心
                        try {
                            startActivity(new Intent(requireContext(), AchievementCenterActivity.class));
                        } catch (Exception e) {
                            Log.e(TAG, "打开成就中心失败", e);
                            Toast.makeText(requireContext(), R.string.error_achievement_not_found, Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    } else if (id == 7) {
                        // 错题本（从底部导航移到头像菜单）
                        try {
                            NavHostFragment.findNavController(GamesFragment.this)
                                    .navigate(R.id.navigation_wrongbook);
                        } catch (Exception e) {
                            Log.e(TAG, "打开错题本失败", e);
                            Toast.makeText(requireContext(),
                                    R.string.nav_wrongbook, Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    }
                    return false;
                });
                popup.show();
            });
        }

        View btnNotification = v.findViewById(R.id.btn_notification);
        if (btnNotification != null) {
            // 包 A-2 (NOTIFICATIONS_CENTER): 通知按钮打开通知中心对话框
            if (BuildConfig.NOTIFICATIONS_CENTER) {
                btnNotification.setOnClickListener(btn -> {
                    try {
                        new NotificationsDialog()
                                .show(getParentFragmentManager(), "notifications");
                    } catch (Exception e) {
                        Log.e(TAG, "打开通知中心失败", e);
                        Toast.makeText(requireContext(),
                                R.string.home_top_notification_empty, Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                btnNotification.setOnClickListener(btn ->
                    Toast.makeText(requireContext(),
                            R.string.home_top_notification_empty, Toast.LENGTH_SHORT).show());
            }
        }
    }

    /** 向后兼容：原顶栏的设置/模块市场按钮。 */
    private void initLegacyTopBar(View v) {
        TextView tvVersion = v.findViewById(R.id.tv_version);
        if (tvVersion != null) {
            try {
                String vn = requireContext().getPackageManager()
                        .getPackageInfo(requireContext().getPackageName(), 0).versionName;
                int vc = requireContext().getPackageManager()
                        .getPackageInfo(requireContext().getPackageName(), 0).versionCode;
                tvVersion.setText(getString(R.string.settings_version_compact_format, vn, vc));
            } catch (Exception e) {
                Log.e(TAG, "版本号读取失败", e);
            }
        }

        View btnSettings = v.findViewById(R.id.btn_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(btn -> {
                try {
                    openSettings();
                } catch (Exception e) {
                    Log.e(TAG, "打开设置失败", e);
                    Toast.makeText(requireContext(), "设置页面打开失败", Toast.LENGTH_SHORT).show();
                }
            });
        }

        View btnStore = v.findViewById(R.id.btn_module_store);
        if (btnStore != null) {
            btnStore.setOnClickListener(btn -> {
                try {
                    Intent intent = new Intent(requireContext(), ModuleStoreActivity.class);
                    startActivity(intent);
                } catch (Exception e) {
                    Log.e(TAG, "启动模块商店失败", e);
                    Toast.makeText(requireContext(), "模块商店未找到", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Feature E: 按当前小时数更新顶栏问候语。
     * 5-11 早上好，12-17 下午好，18-4 晚上好。
     */
    private void updateGreeting() {
        if (rootView == null) return;
        TextView tvGreeting = rootView.findViewById(R.id.tv_greeting);
        if (tvGreeting == null) return;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int resId;
        if (hour >= 5 && hour <= 11) {
            resId = R.string.home_top_bar_greeting_morning;
        } else if (hour >= 12 && hour <= 17) {
            resId = R.string.home_top_bar_greeting_afternoon;
        } else {
            resId = R.string.home_top_bar_greeting_evening;
        }
        tvGreeting.setText(resId);
    }

    /**
     * Feature C (HOME_REVAMP): 刷新"最近游玩"横向滚动条。
     * 从 {@link RecentGamesManager} 读取最近 gameId 列表，反查 {@link GameRegistry.Entry}，
     * 动态填充 item_recent_game.xml 小卡片。
     */
    private void updateRecentGames() {
        if (recentGamesContainer == null) return;
        recentGamesContainer.removeAllViews();
        if (recentGamesSection == null) return;

        List<String> recentIds = RecentGamesManager.getInstance(requireContext()).getRecentIds();
        if (recentIds.isEmpty()) {
            recentGamesSection.setVisibility(View.GONE);
            return;
        }
        recentGamesSection.setVisibility(View.VISIBLE);

        // 反查每个 gameId 对应的 Entry
        List<GameRegistry.Category> categories = GameRegistry.getCategories(requireContext());
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (String gameId : recentIds) {
            GameRegistry.Entry found = null;
            for (GameRegistry.Category cat : categories) {
                for (GameRegistry.Entry e : cat.games) {
                    if (e.id.equals(gameId)) {
                        found = e;
                        break;
                    }
                }
                if (found != null) break;
            }
            if (found == null) continue;

            final GameRegistry.Entry entry = found;
            View card = inflater.inflate(R.layout.item_recent_game, recentGamesContainer, false);
            ImageView iv = card.findViewById(R.id.iv_recent_game_icon);
            if (iv != null) iv.setImageResource(entry.iconRes);
            TextView tvName = card.findViewById(R.id.tv_recent_game_name);
            if (tvName != null) tvName.setText(entry.name);
            card.setOnClickListener(v -> launchGame(entry));
            recentGamesContainer.addView(card);
        }
    }

    private void loadGames() {
        // Batch 7-2 (ANIM_SHIMMER_LOADING): 仅首次加载时显示骨架屏
        boolean showShimmer = BuildConfig.ANIM_SHIMMER_LOADING && !hasLoadedOnce;
        if (showShimmer) showShimmer();

        allEntries.clear();
        List<GameRegistry.Category> categories = GameRegistry.getCategories(requireContext());
        Log.d("GamesFragment", "加载游戏: 分类数量=" + categories.size());
        for (GameRegistry.Category cat : categories) {
            Log.d("GamesFragment", "分类: " + cat.name + ", 游戏数量=" + cat.games.size());
            allEntries.addAll(cat.games);
        }
        Log.d("GamesFragment", "总游戏数量: " + allEntries.size());
        initTabsIfNeeded();
        filterAndRefresh();
        hasLoadedOnce = true;

        if (showShimmer) {
            // 最短展示 600ms，避免闪烁感
            rvGames.postDelayed(this::hideShimmer, 600);
        }

        // Batch 10-2 (HOME_GAME_OF_DAY): 游戏列表加载完成后刷新今日推荐
        if (BuildConfig.HOME_GAME_OF_DAY) {
            refreshGameOfDay();
        }
    }

    /**
     * Batch 7-2 (ANIM_SHIMMER_LOADING): 显示骨架屏。
     * 隐藏真实数据 RecyclerView + 显示骨架 RecyclerView + 启动 alpha 脉冲动画。
     */
    private void showShimmer() {
        if (rvShimmer == null) return;
        rvShimmer.setVisibility(View.VISIBLE);
        if (rvGames != null) rvGames.setVisibility(View.GONE);
        if (shimmerAlphaAnim == null) {
            shimmerAlphaAnim = android.animation.ObjectAnimator.ofFloat(
                    rvShimmer, "alpha", 0.45f, 1f);
            shimmerAlphaAnim.setDuration(700);
            shimmerAlphaAnim.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            shimmerAlphaAnim.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        }
        if (!shimmerAlphaAnim.isStarted()) shimmerAlphaAnim.start();
    }

    /**
     * Batch 7-2 (ANIM_SHIMMER_LOADING): 隐藏骨架屏。
     * 停止动画 + 隐藏骨架 RecyclerView + 显示真实数据 RecyclerView。
     */
    private void hideShimmer() {
        if (shimmerAlphaAnim != null && shimmerAlphaAnim.isStarted()) {
            shimmerAlphaAnim.cancel();
        }
        if (rvShimmer != null) {
            rvShimmer.setAlpha(1f);
            rvShimmer.setVisibility(View.GONE);
        }
        if (rvGames != null) rvGames.setVisibility(View.VISIBLE);
    }

    private void initTabsIfNeeded() {
        TabLayout tabLayout = rootView.findViewById(R.id.tab_layout);
        if (tabLayout == null) return;
        if (tabLayout.getTabCount() > 0) return;

        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.all)));
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.category_classics)));
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.category_puzzle)));
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.category_casual)));
    }

    private void filterAndRefresh() {
        if (adapter == null) return;
        List<GameRegistry.Entry> filtered = new ArrayList<>();
        String kw = currentKeyword == null ? "" : currentKeyword.toLowerCase(Locale.getDefault()).trim();
        for (GameRegistry.Entry e : allEntries) {
            boolean catMatch = currentCategoryKey.equals("all")
                    || e.categoryKey.equals(currentCategoryKey);
            boolean kwMatch = kw.isEmpty()
                    || e.name.toLowerCase(Locale.getDefault()).contains(kw)
                    || e.desc.toLowerCase(Locale.getDefault()).contains(kw);
            boolean favMatch = !favoritesOnly
                    || (usageStore != null && usageStore.isFavorite(e.id));
            if (catMatch && kwMatch && favMatch) filtered.add(e);
        }
        // Batch 11-4 (GAME_FAVORITE_REORDER): 开启收藏置顶后，已收藏游戏排在前面
        if (BuildConfig.GAME_FAVORITE_REORDER && requireContext() != null) {
            List<GameRegistry.Entry> reordered = GameFavoriteReorderHelper.INSTANCE
                    .sortEntries(requireContext(), filtered);
            filtered.clear();
            filtered.addAll(reordered);
        }
        adapter.setEntries(filtered);
        updateEmptyState(filtered.isEmpty());
    }

    private void updateEmptyState(boolean isEmpty) {
        // Batch 7-3 (EMPTY_STATE_ILLUSTRATION): flag 开启时使用精美插图，回退到原 TextView
        boolean useIllustration = BuildConfig.EMPTY_STATE_ILLUSTRATION;
        TextView tvEmpty = rootView.findViewById(R.id.tv_empty_state);
        View illustration = rootView.findViewById(R.id.empty_state_illustration);

        if (!isEmpty) {
            if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
            if (illustration != null) illustration.setVisibility(View.GONE);
            return;
        }

        if (useIllustration && illustration != null) {
            // 隐藏旧的 TextView
            if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
            illustration.setVisibility(View.VISIBLE);
            bindEmptyStateIllustration(illustration);
        } else if (tvEmpty != null) {
            if (illustration != null) illustration.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(getString(R.string.empty_default_title) + "\n" + getString(R.string.empty_default_subtitle));
        }
    }

    /**
     * Batch 7-3 (EMPTY_STATE_ILLUSTRATION): 根据当前上下文（搜索/收藏/默认）绑定空状态插图。
     * - 搜索无结果：放大镜插图 + 清除筛选按钮
     * - 收藏为空：心形插图 + 清除筛选按钮
     * - 默认无游戏：游戏手柄插图 + 去模块市场按钮
     */
    private void bindEmptyStateIllustration(View root) {
        if (root == null) return;
        android.widget.ImageView ivIcon = root.findViewById(R.id.iv_empty_icon);
        TextView tvTitle = root.findViewById(R.id.tv_empty_title);
        TextView tvSubtitle = root.findViewById(R.id.tv_empty_subtitle);
        com.google.android.material.button.MaterialButton btnAction =
                root.findViewById(R.id.btn_empty_action);

        String kw = currentKeyword == null ? "" : currentKeyword.trim();
        boolean hasKeyword = !kw.isEmpty();
        boolean isFavFilter = favoritesOnly;

        if (hasKeyword) {
            // 搜索无结果
            if (ivIcon != null) ivIcon.setImageResource(R.drawable.ic_empty_search);
            if (tvTitle != null) tvTitle.setText(R.string.empty_search_title);
            if (tvSubtitle != null) tvSubtitle.setText(R.string.empty_search_subtitle);
            if (btnAction != null) {
                btnAction.setVisibility(View.VISIBLE);
                btnAction.setText(R.string.empty_action_clear_filter);
                btnAction.setOnClickListener(v -> {
                    // 清除搜索关键字并刷新
                    android.widget.EditText et = rootView.findViewById(R.id.et_game_search);
                    if (et != null) et.setText("");
                    currentKeyword = "";
                    favoritesOnly = false;
                    currentCategoryKey = "all";
                    TabLayout tabLayout = rootView.findViewById(R.id.tab_layout);
                    if (tabLayout != null && tabLayout.getTabCount() > 0) {
                        tabLayout.getTabAt(0).select();
                    }
                    filterAndRefresh();
                });
            }
        } else if (isFavFilter) {
            // 收藏为空
            if (ivIcon != null) ivIcon.setImageResource(R.drawable.ic_empty_favorite);
            if (tvTitle != null) tvTitle.setText(R.string.empty_favorites_title);
            if (tvSubtitle != null) tvSubtitle.setText(R.string.empty_favorites_subtitle);
            if (btnAction != null) {
                btnAction.setVisibility(View.VISIBLE);
                btnAction.setText(R.string.empty_action_clear_filter);
                btnAction.setOnClickListener(v -> {
                    favoritesOnly = false;
                    filterAndRefresh();
                });
            }
        } else {
            // 默认：暂无游戏
            if (ivIcon != null) ivIcon.setImageResource(R.drawable.ic_empty_default);
            if (tvTitle != null) tvTitle.setText(R.string.empty_default_title);
            if (tvSubtitle != null) tvSubtitle.setText(R.string.empty_default_subtitle);
            if (btnAction != null) {
                btnAction.setVisibility(View.VISIBLE);
                btnAction.setText(R.string.empty_action_browse_modules);
                btnAction.setOnClickListener(v -> {
                    try {
                        startActivity(new Intent(requireContext(),
                                com.gamecenter.app.modules.ModuleStoreActivity.class));
                    } catch (Exception e) {
                        Toast.makeText(requireContext(),
                                "模块市场不可用", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    private void onGameClick(GameRegistry.Entry entry) {
        Log.i(TAG, "点击游戏: " + entry.id + " / " + entry.name);
        // Batch 7-1 (GAME_DETAIL_SHEET): flag 开启时弹出详情 BottomSheet，否则回退到直接启动
        if (BuildConfig.GAME_DETAIL_SHEET) {
            showGameDetailSheet(entry);
            return;
        }
        // Feature C (HOME_REVAMP): 推进最近游玩记录、连胜活跃度、每日挑战进度
        if (BuildConfig.HOME_REVAMP) {
            launchGame(entry);
        } else {
            boolean ok = GameLauncherHelper.launchGameWithDialog(requireContext(), entry.id);
            if (!ok) {
                Toast.makeText(requireContext(), getString(R.string.error_game_launch_failed_format, entry.name), Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Batch 7-1 (GAME_DETAIL_SHEET): 弹出游戏详情 BottomSheet。
     * 用户在面板内点击"立即开始"才会真正启动游戏并推进各项数据。
     *
     * Batch 11-1 (GAME_RATING_SYSTEM): 增加 onRatingChanged 回调，评分变化后刷新卡片徽章。
     */
    private void showGameDetailSheet(GameRegistry.Entry entry) {
        com.gamecenter.app.ui.GameDetailBottomSheet sheet =
                new com.gamecenter.app.ui.GameDetailBottomSheet(
                        entry,
                        e -> launchGame(e),
                        () -> {
                            // 收藏状态变化后刷新列表，使卡片心形图标同步
                            if (adapter != null) adapter.notifyDataSetChanged();
                        },
                        (gameId, stars) -> {
                            // 评分变化后刷新卡片评分徽章
                            if (adapter != null) adapter.notifyDataSetChanged();
                        });
        sheet.show(getChildFragmentManager(), "GameDetailBottomSheet");
    }

    /**
     * Feature C (HOME_REVAMP): 统一的启动入口。
     * 在启动前推进最近游玩、连胜活跃、每日挑战三套数据。
     */
    private void launchGame(GameRegistry.Entry entry) {
        Context ctx = requireContext();
        RecentGamesManager.getInstance(ctx).recordPlay(entry.id);
        StreakTracker.getInstance(ctx).recordActivity();
        DailyChallengeManager.getInstance(ctx).recordGamePlayed(entry.id, false);
        boolean ok = GameLauncherHelper.launchGameWithDialog(ctx, entry.id);
        if (!ok) {
            Toast.makeText(ctx, ctx.getString(R.string.error_game_launch_failed_format, entry.name), Toast.LENGTH_SHORT).show();
        }
    }

    // ======== Adapter ========

    /**
     * Batch 7-2 (ANIM_SHIMMER_LOADING): 骨架屏卡片 Adapter，固定 6 个占位项。
     */
    private static class ShimmerCardAdapter extends RecyclerView.Adapter<ShimmerCardAdapter.VH> {
        private static final int SHIMMER_COUNT = 6;

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_shimmer_game_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            // 静态占位，无需绑定数据
        }

        @Override
        public int getItemCount() {
            return SHIMMER_COUNT;
        }

        static class VH extends RecyclerView.ViewHolder {
            VH(@NonNull View itemView) {
                super(itemView);
            }
        }
    }

    private static class GameCardAdapter extends RecyclerView.Adapter<GameCardAdapter.VH> {

        interface OnItemClick { void onClick(GameRegistry.Entry entry); }
        private final List<GameRegistry.Entry> data = new ArrayList<>();
        private final OnItemClick listener;
        private final Context context;

        GameCardAdapter(Context ctx, OnItemClick listener) {
            this.context = ctx;
            this.listener = listener;
        }

        void setEntries(List<GameRegistry.Entry> list) {
            data.clear();
            data.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_game_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            GameRegistry.Entry e = data.get(pos);
            ImageView iv = h.itemView.findViewById(R.id.iv_game_icon);
            if (iv != null) iv.setImageResource(e.iconRes);

            TextView tvName = h.itemView.findViewById(R.id.tv_game_name);
            if (tvName != null) tvName.setText(e.name);

            TextView tvDesc = h.itemView.findViewById(R.id.tv_game_desc);
            if (tvDesc != null) tvDesc.setText(e.desc);

            TextView tvCat = h.itemView.findViewById(R.id.tv_category_tag);
            if (tvCat != null) {
                String label;
                if (e.categoryKey.equals(GameRegistry.CATEGORY_CLASSICS)) {
                    label = context.getString(R.string.category_classics);
                } else if (e.categoryKey.equals(GameRegistry.CATEGORY_PUZZLE)) {
                    label = context.getString(R.string.category_puzzle);
                } else if (e.categoryKey.equals(GameRegistry.CATEGORY_CASUAL)) {
                    label = context.getString(R.string.category_casual);
                } else {
                    label = e.categoryKey;
                }
                tvCat.setText(label);
            }

            // Batch 8-2 (CARD_TILT_ANIM): 卡片触摸抬升动效
            if (BuildConfig.CARD_TILT_ANIM && h.itemView instanceof com.google.android.material.card.MaterialCardView) {
                com.gamecenter.app.ui.CardTiltHelper.INSTANCE.attach(
                        (com.google.android.material.card.MaterialCardView) h.itemView);
            }

            // Feature E (HOME_CARD_ENHANCE): 评分行 + 热门徽章 + 收藏按钮
            if (BuildConfig.HOME_CARD_ENHANCE) {
                bindCardEnhancements(h, e, iv);
            }

            // Batch 11-1 (GAME_RATING_SYSTEM): 用户评分徽章（左下角）
            if (BuildConfig.GAME_RATING_SYSTEM) {
                bindUserRatingBadge(h, e);
            }

            // Batch 12-3 (GAME_PLAY_TIME_BADGE): 总游玩时长徽章（右上角下方）
            if (BuildConfig.GAME_PLAY_TIME_BADGE) {
                bindPlayTimeBadge(h, e);
            }

            // 点击启动游戏（包含图标缩放动效）
            h.itemView.setOnClickListener(v -> {
                if (BuildConfig.HOME_CARD_ENHANCE) {
                    playClickAnimation(v);
                }
                listener.onClick(e);
            });

            // Batch 9-1 (GAME_LONG_PRESS_MENU): 长按弹出上下文菜单（分享/收藏/桌面快捷方式）
            if (BuildConfig.GAME_LONG_PRESS_MENU) {
                h.itemView.setOnLongClickListener(v -> {
                    com.gamecenter.app.ui.GameLongPressMenu.INSTANCE.show(
                            h.itemView.getContext(), v, e);
                    return true;
                });
            }
        }

        /**
         * Feature E: 绑定卡片增强元素。
         * - 评分行：基于 GameUsageStore.getPlayCount() 估算评分（0~5 星，0.5 步进）
         * - 热门徽章：playCount >= 5 显示
         * - 收藏按钮：根据当前收藏状态切换图标，点击切换并刷新
         */
        private void bindCardEnhancements(VH h, GameRegistry.Entry e, ImageView iconIv) {
            GameUsageStore store = new GameUsageStore(h.itemView.getContext());
            int playCount = store.getPlayCount(e.id);

            // 评分行
            View ratingRow = h.itemView.findViewById(R.id.tv_rating);
            TextView tvRatingValue = h.itemView.findViewById(R.id.tv_rating_value);
            TextView tvRatingCount = h.itemView.findViewById(R.id.tv_rating_count);
            if (ratingRow != null) {
                if (playCount > 0) {
                    // 评分映射：1-2 次→3.0；3-5→3.5；6-10→4.0；11-20→4.5；21+→5.0
                    float rating;
                    if (playCount <= 2) rating = 3.0f;
                    else if (playCount <= 5) rating = 3.5f;
                    else if (playCount <= 10) rating = 4.0f;
                    else if (playCount <= 20) rating = 4.5f;
                    else rating = 5.0f;
                    ratingRow.setVisibility(View.VISIBLE);
                    if (tvRatingValue != null) {
                        tvRatingValue.setText(String.format(Locale.getDefault(), "%.1f", rating));
                    }
                    if (tvRatingCount != null) {
                        tvRatingCount.setText("(" + playCount + ")");
                    }
                } else {
                    ratingRow.setVisibility(View.GONE);
                }
            }

            // 热门徽章
            View hotBadge = h.itemView.findViewById(R.id.iv_hot_badge);
            if (hotBadge != null) {
                hotBadge.setVisibility(playCount >= 5 ? View.VISIBLE : View.GONE);
            }

            // 收藏按钮
            ImageView btnFav = h.itemView.findViewById(R.id.btn_favorite);
            if (btnFav != null) {
                updateFavoriteIcon(btnFav, store.isFavorite(e.id));
                // 阻止点击事件传递到卡片本身
                btnFav.setOnClickListener(v -> {
                    boolean wasFav = store.isFavorite(e.id);
                    store.toggleFavorite(e.id);
                    boolean nowFav = !wasFav;
                    updateFavoriteIcon(btnFav, nowFav);
                    // 添加心形按钮的反馈动画
                    playFavoriteAnimation(btnFav, nowFav);
                    Toast.makeText(v.getContext(),
                            nowFav ? R.string.home_card_favorite_add : R.string.home_card_favorite_remove,
                            Toast.LENGTH_SHORT).show();
                });
            }
        }

        private void updateFavoriteIcon(ImageView btn, boolean isFavorite) {
            btn.setImageResource(isFavorite ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite);
        }

        /**
         * Batch 11-1 (GAME_RATING_SYSTEM): 绑定用户评分徽章。
         * 若用户已对该游戏评分（1~5 星），在卡片左下角显示金色徽章；否则隐藏。
         */
        private void bindUserRatingBadge(VH h, GameRegistry.Entry e) {
            TextView badge = h.itemView.findViewById(R.id.badge_user_rating);
            if (badge == null) return;
            GameRatingStore store = new GameRatingStore(h.itemView.getContext());
            int stars = store.getRating(e.id);
            if (stars > 0) {
                badge.setVisibility(View.VISIBLE);
                badge.setText(h.itemView.getContext()
                        .getString(R.string.game_rating_user_format, stars));
            } else {
                badge.setVisibility(View.GONE);
            }
        }

        /**
         * Batch 12-3 (GAME_PLAY_TIME_BADGE): 绑定总游玩时长徽章。
         * 总时长 > 0 时显示徽章：
         * - ≥ 1 分钟：显示 "X.Y 小时" 或 "X 分钟"
         * - < 1 分钟：隐藏徽章
         */
        private void bindPlayTimeBadge(VH h, GameRegistry.Entry e) {
            TextView badge = h.itemView.findViewById(R.id.badge_play_time);
            if (badge == null) return;
            GameUsageStore store = new GameUsageStore(h.itemView.getContext());
            long totalMs = store.getTotalPlayTimeMs(e.id);
            if (totalMs <= 0L) {
                badge.setVisibility(View.GONE);
                return;
            }
            long totalMinutes = totalMs / 60000L;
            if (totalMinutes < 1L) {
                badge.setVisibility(View.GONE);
                return;
            }
            badge.setVisibility(View.VISIBLE);
            Context ctx = h.itemView.getContext();
            if (totalMinutes >= 60L) {
                float hours = totalMinutes / 60.0f;
                badge.setText(ctx.getString(R.string.game_play_time_badge_format_hours, hours));
            } else {
                badge.setText(ctx.getString(R.string.game_play_time_badge_format_minutes, (int) totalMinutes));
            }
        }

        /** Feature E: 卡片点击缩放动效（按下 0.96→1.0，回弹效果）。 */
        private void playClickAnimation(View v) {
            ObjectAnimator down = ObjectAnimator.ofFloat(v, "scaleX", 1f, 0.96f, 1f);
            ObjectAnimator downY = ObjectAnimator.ofFloat(v, "scaleY", 1f, 0.96f, 1f);
            AnimatorSet set = new AnimatorSet();
            set.playTogether(down, downY);
            set.setDuration(180);
            set.setInterpolator(new DecelerateInterpolator());
            set.start();
        }

        /** Feature E: 收藏按钮点击动效（激活时放大反馈）。 */
        private void playFavoriteAnimation(ImageView btn, boolean nowFav) {
            float end = nowFav ? 1.3f : 0.85f;
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(btn, "scaleX", 1f, end, 1f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(btn, "scaleY", 1f, end, 1f);
            AnimatorSet set = new AnimatorSet();
            set.playTogether(scaleX, scaleY);
            set.setDuration(220);
            set.setInterpolator(new DecelerateInterpolator());
            set.start();
        }

        @Override public int getItemCount() { return data.size(); }

        static class VH extends RecyclerView.ViewHolder {
            VH(View v) { super(v); }
        }
    }
}
