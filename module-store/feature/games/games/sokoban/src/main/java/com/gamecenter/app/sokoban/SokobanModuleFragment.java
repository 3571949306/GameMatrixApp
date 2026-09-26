package com.gamecenter.app.sokoban;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.R;
import com.gamecenter.app.games.GameUsageStore;
import com.gamecenter.app.games.coin.CoinWallet;

import java.util.List;
import java.util.Map;

/**
 * 推箱子游戏 Fragment（独立 APK 模块版本）。
 *
 * <p>由宿主 SokobanActivity 迁移而来。使用纯 Android widget 构建 UI，
 * 不依赖宿主 R 资源，支持浅色/深色主题。游戏逻辑由 {@link SokobanGame} 承载，
 * 渲染由 {@link SokobanView} 负责，不含成就系统，仅保留基本游戏功能。</p>
 */
public class SokobanModuleFragment extends Fragment {

    private static final String GAME_ID = "sokoban";

    // 主题感知颜色
    private int colorBg;
    private int colorText;
    private int colorLevel;
    private int colorMoves;
    private int colorBtnLevel;
    private int colorBtnDir;

    // UI 组件
    private TextView tvStatus;
    private TextView tvLevel;
    private TextView tvMoves;
    private ScrollView menuScroll;
    private LinearLayout menuPanel;
    private LinearLayout gamePanel;
    /** 战役面板容器（滚动包裹，与 menuScroll/gamePanel 同级显隐管理）。 */
    private ScrollView campaignScroll;
    /** 战役面板内容（章卡片 / 关卡行，每次打开重建）。 */
    private LinearLayout campaignPanel;
    /** 每日面板容器（滚动包裹，与 menuScroll/gamePanel/campaignScroll 同级显隐管理）。 */
    private ScrollView dailyScroll;
    /** 每日面板内容（当日关卡卡 + 近 7 天横条，每次打开重建）。 */
    private LinearLayout dailyPanel;
    private SokobanView sokobanView;

    // 游戏逻辑
    private SokobanGame game;
    private GameUsageStore usageStore;
    private long gameStartTime = 0;
    /** 当前游玩的自定义关卡名（null = 内置关）。 */
    private String currentCustomLevelName;
    /** 当前游玩的战役关卡（null = 非战役局；与内置/自定义局互斥）。 */
    private SokobanCampaign.CampaignLevel currentCampaignLevel;
    /** 当前游玩的每日关卡（null = 非每日局；与内置/自定义/战役局互斥，完成分发最优先判它）。 */
    private SokobanDailyPuzzle.DailyLevel currentDailyLevel;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        int minimumTouchSize = (int) Math.ceil(48 * dp);
        initColors();

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(colorBg);
        root.setPadding(0, (int) (16 * dp), 0, (int) (16 * dp));

        tvStatus = new TextView(ctx);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(16f);
        tvStatus.setTextColor(colorText);
        tvStatus.setPadding(0, (int) (16 * dp), 0, (int) (8 * dp));

        tvLevel = new TextView(ctx);
        tvLevel.setGravity(Gravity.CENTER);
        tvLevel.setTextSize(14f);
        tvLevel.setTextColor(colorLevel);

        tvMoves = new TextView(ctx);
        tvMoves.setGravity(Gravity.CENTER);
        tvMoves.setTextSize(14f);
        tvMoves.setTextColor(colorMoves);
        tvMoves.setPadding(0, (int) (4 * dp), 0, (int) (8 * dp));

        // 菜单面板（关卡选择，用 ScrollView 包裹避免溢出）
        menuScroll = new ScrollView(ctx);
        menuScroll.setFillViewport(true);
        menuPanel = new LinearLayout(ctx);
        menuPanel.setOrientation(LinearLayout.VERTICAL);
        menuPanel.setGravity(Gravity.CENTER);
        for (int i = 1; i <= SokobanGame.TOTAL_LEVELS; i++) {
            final int level = i;
            Button btn = new Button(ctx);
            btn.setText(getString(R.string.game_level_format, i));
            btn.setMinimumHeight(minimumTouchSize);
            btn.setMinHeight(minimumTouchSize);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, (int) (4 * dp), 0, (int) (4 * dp));
            btn.setLayoutParams(lp);
            btn.setBackgroundColor(colorBtnLevel);
            btn.setTextColor(Color.WHITE);
            btn.setOnClickListener(v -> startLevel(level));
            menuPanel.addView(btn);
        }
        menuPanel.addView(createMenuButton(ctx, "自定义关卡", v -> showCustomLevelList()));
        menuPanel.addView(createMenuButton(ctx, "＋ 新建关卡", v -> openEditor()));
        menuPanel.addView(createMenuButton(ctx, "战役模式", v -> showCampaignPanel()));
        menuPanel.addView(createMenuButton(ctx, "每日关卡", v -> showDailyPanel()));
        menuScroll.addView(menuPanel);

        // 游戏面板
        gamePanel = new LinearLayout(ctx);
        gamePanel.setOrientation(LinearLayout.VERTICAL);
        gamePanel.setGravity(Gravity.CENTER);
        gamePanel.setVisibility(View.GONE);

        sokobanView = new SokobanView(ctx);
        // Reserve the controls first; only the board flexes into the remaining height.
        LinearLayout.LayoutParams boardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        boardLp.setMargins((int) (16 * dp), 0, (int) (16 * dp), 0);
        sokobanView.setLayoutParams(boardLp);

        // 方向控制按钮
        LinearLayout controlPanel = new LinearLayout(ctx);
        controlPanel.setOrientation(LinearLayout.VERTICAL);
        controlPanel.setGravity(Gravity.CENTER);
        controlPanel.setPadding(0, (int) (16 * dp), 0, 0);

        LinearLayout topRow = new LinearLayout(ctx);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER);
        Button btnUp = createDirectionButton(ctx, "↑");
        btnUp.setOnClickListener(v -> movePlayer(-1, 0));
        topRow.addView(createSpacer(ctx));
        topRow.addView(btnUp);
        topRow.addView(createSpacer(ctx));

        LinearLayout midRow = new LinearLayout(ctx);
        midRow.setOrientation(LinearLayout.HORIZONTAL);
        midRow.setGravity(Gravity.CENTER);
        Button btnLeft = createDirectionButton(ctx, "←");
        btnLeft.setOnClickListener(v -> movePlayer(0, -1));
        Button btnDown = createDirectionButton(ctx, "↓");
        btnDown.setOnClickListener(v -> movePlayer(1, 0));
        Button btnRight = createDirectionButton(ctx, "→");
        btnRight.setOnClickListener(v -> movePlayer(0, 1));
        midRow.addView(btnLeft);
        midRow.addView(btnDown);
        midRow.addView(btnRight);

        controlPanel.addView(topRow);
        controlPanel.addView(midRow);

        // 底部按钮
        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setPadding(0, (int) (12 * dp), 0, 0);

        Button btnUndo = new Button(ctx);
        btnUndo.setText(getString(R.string.game_btn_undo));
        btnUndo.setOnClickListener(v -> undoMove());

        Button btnReset = new Button(ctx);
        btnReset.setText(getString(R.string.game_btn_reset));
        btnReset.setOnClickListener(v -> {
            // 内置/自定义通用重开：自定义关 currentLevel=-1 不能走 startLevel 路径。
            game.restartLevel();
            sokobanView.setMap(game.getMap());
            tvStatus.setText(getString(R.string.game_sokoban_status));
            updateMovesDisplay();
        });

        Button btnMenu = new Button(ctx);
        btnMenu.setText(getString(R.string.game_btn_back_menu));
        btnMenu.setOnClickListener(v -> showMenu());

        for (Button button : new Button[]{btnUndo, btnReset, btnMenu}) {
            button.setMinimumHeight(minimumTouchSize);
            button.setMinHeight(minimumTouchSize);
            LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            btnLp.setMargins((int) (4 * dp), 0, (int) (4 * dp), 0);
            button.setLayoutParams(btnLp);
        }

        btnRow.addView(btnUndo);
        btnRow.addView(btnReset);
        btnRow.addView(btnMenu);

        gamePanel.addView(sokobanView);
        gamePanel.addView(controlPanel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        gamePanel.addView(btnRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 战役面板（章卡片 + 关卡行，内容每次打开重建）
        campaignScroll = new ScrollView(ctx);
        campaignScroll.setFillViewport(true);
        campaignPanel = new LinearLayout(ctx);
        campaignPanel.setOrientation(LinearLayout.VERTICAL);
        campaignPanel.setGravity(Gravity.CENTER);
        campaignScroll.addView(campaignPanel);
        campaignScroll.setVisibility(View.GONE);

        // 每日面板（当日关卡卡 + 近 7 天横条，内容每次打开重建）
        dailyScroll = new ScrollView(ctx);
        dailyScroll.setFillViewport(true);
        dailyPanel = new LinearLayout(ctx);
        dailyPanel.setOrientation(LinearLayout.VERTICAL);
        dailyPanel.setGravity(Gravity.CENTER);
        dailyScroll.addView(dailyPanel);
        dailyScroll.setVisibility(View.GONE);

        root.addView(tvStatus, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(tvLevel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(tvMoves, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        root.addView(menuScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(gamePanel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(campaignScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(dailyScroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context ctx = requireContext();
        usageStore = new GameUsageStore(ctx);
        game = new SokobanGame();
        showMenu();
    }

    private void initColors() {
        boolean dark = isNightMode();
        colorBg = dark ? 0xFF121622 : 0xFFFAFAFA;
        colorText = dark ? 0xFFE4E6F0 : 0xFF212121;
        colorLevel = dark ? 0xFF90CAF9 : 0xFF1976D2;
        colorMoves = dark ? 0xFFAAAAAA : 0xFF757575;
        colorBtnLevel = dark ? 0xFF3949AB : 0xFF3F51B5;
        colorBtnDir = dark ? 0xFF3949AB : 0xFF3F51B5;
    }

    private Button createDirectionButton(Context ctx, String text) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextSize(20f);
        int size = directionButtonSize(ctx);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins((int) (8 * dp(ctx)), (int) (4 * dp(ctx)), (int) (8 * dp(ctx)), (int) (4 * dp(ctx)));
        btn.setLayoutParams(lp);
        btn.setBackgroundColor(colorBtnDir);
        btn.setTextColor(Color.WHITE);
        return btn;
    }

    private View createSpacer(Context ctx) {
        View spacer = new View(ctx);
        int size = directionButtonSize(ctx);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return spacer;
    }

    private int directionButtonSize(Context ctx) {
        float windowWidthDp = ctx.getResources().getConfiguration().screenWidthDp;
        float sizeDp = Math.max(48f, Math.min(64f, windowWidthDp / 6f));
        return (int) Math.ceil(sizeDp * dp(ctx));
    }

    private float dp(Context ctx) {
        return ctx.getResources().getDisplayMetrics().density;
    }

    private void showMenu() {
        menuScroll.setVisibility(View.VISIBLE);
        gamePanel.setVisibility(View.GONE);
        campaignScroll.setVisibility(View.GONE);
        dailyScroll.setVisibility(View.GONE);
        // 战役/每日局返回主菜单即结束（进度在完成时已落盘），与内置/自定义局同一出口。
        currentCampaignLevel = null;
        currentDailyLevel = null;
        tvStatus.setText(getString(R.string.game_select_level));
        tvLevel.setText("");
        tvMoves.setText("");
    }

    private void startLevel(int level) {
        game.startLevel(level);
        currentCustomLevelName = null;
        currentCampaignLevel = null;
        currentDailyLevel = null;
        menuScroll.setVisibility(View.GONE);
        gamePanel.setVisibility(View.VISIBLE);
        tvStatus.setText(getString(R.string.game_sokoban_status));
        tvLevel.setText(getString(R.string.game_level_format, level));
        updateMovesDisplay();
        sokobanView.setMap(game.getMap());
        gameStartTime = System.currentTimeMillis();
    }

    /** 以内置关同样的流程进入自定义关卡。 */
    private void playCustomLevel(SokobanCustomLevels.CustomLevel level) {
        int[][] map = level.decodeMap();
        if (map == null || !game.startCustomLevel(map)) {
            Toast.makeText(requireContext(), "关卡数据无效", Toast.LENGTH_SHORT).show();
            return;
        }
        currentCustomLevelName = level.name;
        currentCampaignLevel = null;
        currentDailyLevel = null;
        menuScroll.setVisibility(View.GONE);
        gamePanel.setVisibility(View.VISIBLE);
        tvStatus.setText(getString(R.string.game_sokoban_status));
        tvLevel.setText("自定义·" + level.name);
        updateMovesDisplay();
        sokobanView.setMap(game.getMap());
        gameStartTime = System.currentTimeMillis();
    }

    // ==================== 战役模式 ====================

    /** 打开战役面板：每次从持久化进度重建渲染。 */
    private void showCampaignPanel() {
        renderCampaignChapters();
        menuScroll.setVisibility(View.GONE);
        gamePanel.setVisibility(View.GONE);
        dailyScroll.setVisibility(View.GONE);
        campaignScroll.setVisibility(View.VISIBLE);
        tvStatus.setText("战役模式");
        tvLevel.setText("");
        tvMoves.setText("");
    }

    /** 渲染章列表：章卡片 + 返回主菜单。 */
    private void renderCampaignChapters() {
        Context ctx = requireContext();
        SokobanCampaignProgress progress = SokobanCampaignStore.load(ctx);
        campaignPanel.removeAllViews();
        for (SokobanCampaign.Chapter chapter : SokobanCampaign.CHAPTERS) {
            campaignPanel.addView(createChapterCard(ctx, chapter, progress));
        }
        campaignPanel.addView(createMenuButton(ctx, "返回主菜单", v -> showMenu()));
    }

    /** 章卡片：章名 + 进度/锁定/已通关文案；点击已解锁章进入该章关卡行。 */
    private View createChapterCard(Context ctx, SokobanCampaign.Chapter chapter,
                                   SokobanCampaignProgress progress) {
        float dp = dp(ctx);
        boolean unlocked = progress.isChapterUnlocked(chapter.id);
        boolean cleared = progress.isChapterCleared(chapter.id);
        int clearedCount = 0;
        for (SokobanCampaign.CampaignLevel lv : chapter.levels) {
            if (progress.isLevelCleared(chapter.id, lv.indexInChapter)) {
                clearedCount++;
            }
        }

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding((int) (16 * dp), (int) (12 * dp), (int) (16 * dp), (int) (12 * dp));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins((int) (8 * dp), (int) (6 * dp), (int) (8 * dp), (int) (6 * dp));
        card.setLayoutParams(lp);
        card.setBackgroundColor(unlocked ? colorBtnLevel : lockedColor());

        TextView tvName = new TextView(ctx);
        tvName.setGravity(Gravity.CENTER);
        tvName.setTextSize(16f);
        tvName.setTextColor(Color.WHITE);
        tvName.setText(chapter.name);
        card.addView(tvName);

        TextView tvInfo = new TextView(ctx);
        tvInfo.setGravity(Gravity.CENTER);
        tvInfo.setTextSize(12f);
        tvInfo.setTextColor(Color.WHITE);
        if (cleared) {
            tvInfo.setText("✓ 已通关");
        } else if (unlocked) {
            tvInfo.setText("进度 " + clearedCount + "/" + chapter.levels.length);
        } else {
            tvInfo.setText("🔒 通关上一章解锁");
        }
        card.addView(tvInfo);

        card.setOnClickListener(v -> {
            if (unlocked) {
                renderChapterLevels(chapter);
            } else {
                Toast.makeText(requireContext(), "通关上一章后解锁", Toast.LENGTH_SHORT).show();
            }
        });
        return card;
    }

    /** 渲染章内关卡行：章内顺序解锁（第 1 关随章解锁，第 N 关需第 N-1 关完成）。 */
    private void renderChapterLevels(SokobanCampaign.Chapter chapter) {
        Context ctx = requireContext();
        SokobanCampaignProgress progress = SokobanCampaignStore.load(ctx);
        campaignPanel.removeAllViews();

        TextView tvTitle = new TextView(ctx);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setTextSize(16f);
        tvTitle.setTextColor(colorLevel);
        tvTitle.setPadding(0, (int) (8 * dp(ctx)), 0, (int) (8 * dp(ctx)));
        tvTitle.setText(chapter.name);
        campaignPanel.addView(tvTitle);

        LinearLayout levelRow = new LinearLayout(ctx);
        levelRow.setOrientation(LinearLayout.HORIZONTAL);
        levelRow.setGravity(Gravity.CENTER);
        for (int i = 0; i < chapter.levels.length; i++) {
            SokobanCampaign.CampaignLevel level = chapter.levels[i];
            boolean levelCleared = progress.isLevelCleared(chapter.id, level.indexInChapter);
            boolean levelUnlocked = i == 0 || progress.isLevelCleared(
                    chapter.id, chapter.levels[i - 1].indexInChapter);
            levelRow.addView(createCampaignLevelButton(
                    ctx, level, campaignLevelOrdinal(i), levelCleared, levelUnlocked));
        }
        campaignPanel.addView(levelRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        campaignPanel.addView(createMenuButton(ctx, "返回章列表", v -> renderCampaignChapters()));
        campaignPanel.addView(createMenuButton(ctx, "返回主菜单", v -> showMenu()));
    }

    /** 关卡序号文案（一/二/三，超出时退化为数字）。 */
    private static String campaignLevelOrdinal(int index) {
        String[] ordinals = {"一", "二", "三"};
        return index < ordinals.length ? ordinals[index] : String.valueOf(index + 1);
    }

    /** 战役关卡按钮：已完成带 ✓ 可重玩（进度记录幂等）；未解锁置灰并提示。 */
    private Button createCampaignLevelButton(Context ctx, SokobanCampaign.CampaignLevel level,
                                             String ordinal, boolean cleared, boolean unlocked) {
        float dp = dp(ctx);
        Button btn = new Button(ctx);
        btn.setText((unlocked ? ordinal : "🔒" + ordinal) + (cleared ? " ✓" : ""));
        int minimumTouchSize = (int) Math.ceil(48 * dp);
        btn.setMinimumHeight(minimumTouchSize);
        btn.setMinHeight(minimumTouchSize);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins((int) (4 * dp), (int) (8 * dp), (int) (4 * dp), (int) (8 * dp));
        btn.setLayoutParams(lp);
        btn.setBackgroundColor(unlocked ? colorBtnLevel : lockedColor());
        btn.setTextColor(Color.WHITE);
        if (unlocked) {
            btn.setOnClickListener(v -> startCampaignLevel(level));
        } else {
            btn.setOnClickListener(v -> Toast.makeText(requireContext(),
                    "先完成前一关解锁", Toast.LENGTH_SHORT).show());
        }
        return btn;
    }

    /** 进入战役关卡：解码关卡码，走自定义关装载路径（与内置/自定义局互斥）。 */
    private void startCampaignLevel(SokobanCampaign.CampaignLevel level) {
        int[][] map = SokobanLevelCodec.decode(level.code);
        if (map == null || !game.startCustomLevel(map)) {
            Toast.makeText(requireContext(), "关卡数据无效", Toast.LENGTH_SHORT).show();
            return;
        }
        currentCampaignLevel = level;
        currentCustomLevelName = null;
        currentDailyLevel = null;
        campaignScroll.setVisibility(View.GONE);
        gamePanel.setVisibility(View.VISIBLE);
        SokobanCampaign.Chapter chapter = SokobanCampaign.chapter(level.chapter);
        tvStatus.setText(getString(R.string.game_sokoban_status));
        tvLevel.setText("战役·" + (chapter != null ? chapter.name : "") + "·" + level.name);
        updateMovesDisplay();
        sokobanView.setMap(game.getMap());
        gameStartTime = System.currentTimeMillis();
    }

    /**
     * 战役关卡完成：记录进度并持久化；若该章由此关集齐通关（前后态差异），
     * 经宿主 CoinWallet 发放章节金币奖励（与对局奖励共用每日上限）。
     * 走自定义关路径（isCustomLevel=true），不计入 usageStore，防自制对局刷统计。
     */
    private void onCampaignLevelComplete(long elapsedSec) {
        SokobanCampaign.CampaignLevel level = currentCampaignLevel;
        currentCampaignLevel = null;

        SokobanCampaignProgress progress = SokobanCampaignStore.load(requireContext());
        boolean chapterClearedBefore = progress.isChapterCleared(level.chapter);
        progress.recordLevelCleared(level.chapter, level.indexInChapter);
        SokobanCampaignStore.save(requireContext(), progress);

        SokobanCampaign.Chapter chapter = SokobanCampaign.chapter(level.chapter);
        String chapterName = chapter != null ? chapter.name : "";
        tvStatus.setText("🎉 战役·" + chapterName + "·" + level.name + "完成"
                + " | 推动 " + game.getPushCount() + " | 用时 " + elapsedSec + "s");

        if (chapter == null || chapterClearedBefore || !progress.isChapterCleared(level.chapter)) {
            return;
        }
        int earned = new CoinWallet(requireContext())
                .grantBonus(chapter.rewardCoins, "战役通关·" + chapter.name);
        Toast.makeText(requireContext(), earned > 0
                        ? "🎉 通关「" + chapter.name + "」，奖励 " + earned + " 金币"
                        : "🎉 通关「" + chapter.name + "」，今日金币已达上限",
                Toast.LENGTH_LONG).show();
    }

    // ==================== 每日关卡挑战 ====================

    /** 打开每日面板：每次从持久化记录重建渲染。 */
    private void showDailyPanel() {
        renderDailyPanel();
        menuScroll.setVisibility(View.GONE);
        gamePanel.setVisibility(View.GONE);
        campaignScroll.setVisibility(View.GONE);
        dailyScroll.setVisibility(View.VISIBLE);
        tvStatus.setText("每日关卡挑战");
        tvLevel.setText("");
        tvMoves.setText("");
    }

    /** 渲染每日面板：标题 + 当日关卡卡 + 开始挑战 + 近 7 天横条 + 返回主菜单。 */
    private void renderDailyPanel() {
        Context ctx = requireContext();
        Map<String, Integer> bestMoves = SokobanDailyStore.load(ctx);
        String todayKey = SokobanDailyStore.todayKey();
        SokobanDailyPuzzle.DailyLevel daily = SokobanDailyPuzzle.forDate(todayKey);

        dailyPanel.removeAllViews();

        TextView tvTitle = new TextView(ctx);
        tvTitle.setGravity(Gravity.CENTER);
        tvTitle.setTextSize(16f);
        tvTitle.setTextColor(colorLevel);
        tvTitle.setPadding(0, (int) (8 * dp(ctx)), 0, (int) (8 * dp(ctx)));
        tvTitle.setText("每日关卡挑战 · " + todayKey.substring(5));
        dailyPanel.addView(tvTitle);

        dailyPanel.addView(createDailyCard(ctx, daily, bestMoves, todayKey));
        dailyPanel.addView(createMenuButton(ctx, "开始挑战", v -> startDailyLevel()));
        dailyPanel.addView(createDailyStreakRow(ctx, bestMoves, todayKey));
        dailyPanel.addView(createMenuButton(ctx, "返回主菜单", v -> showMenu()));
    }

    /** 当日关卡卡：关卡名 + 完成状态（今日待挑战 / ✓ 今日最佳步数）。 */
    private View createDailyCard(Context ctx, SokobanDailyPuzzle.DailyLevel daily,
                                 Map<String, Integer> bestMoves, String todayKey) {
        float dp = dp(ctx);
        boolean done = SokobanDailyRecord.isCompleted(bestMoves, todayKey);

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding((int) (16 * dp), (int) (12 * dp), (int) (16 * dp), (int) (12 * dp));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins((int) (8 * dp), (int) (6 * dp), (int) (8 * dp), (int) (6 * dp));
        card.setLayoutParams(lp);
        card.setBackgroundColor(colorBtnLevel);

        TextView tvName = new TextView(ctx);
        tvName.setGravity(Gravity.CENTER);
        tvName.setTextSize(16f);
        tvName.setTextColor(Color.WHITE);
        tvName.setText(daily != null ? daily.name : "今日关卡暂不可用");
        card.addView(tvName);

        TextView tvInfo = new TextView(ctx);
        tvInfo.setGravity(Gravity.CENTER);
        tvInfo.setTextSize(12f);
        tvInfo.setTextColor(Color.WHITE);
        if (daily == null) {
            tvInfo.setText("请稍后再试");
        } else {
            tvInfo.setText(done ? "✓ 今日最佳 " + bestMoves.get(todayKey) + " 步" : "今日待挑战");
        }
        card.addView(tvInfo);
        return card;
    }

    /** 近 7 天横条：recentStreak 下标 0=今天（D1），完成格绿底显示步数，未完成格灰底。 */
    private View createDailyStreakRow(Context ctx, Map<String, Integer> bestMoves, String todayKey) {
        List<Integer> streak = SokobanDailyRecord.recentStreak(bestMoves, todayKey, 7);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        for (int i = 0; i < 7; i++) {
            Integer moves = i < streak.size() ? streak.get(i) : null;
            row.addView(createDailyStreakCell(ctx, "D" + (i + 1), moves));
        }
        return row;
    }

    /** 单日格：D1..D7 标签 + 步数（绿=完成）/ —（灰=未完成）。 */
    private View createDailyStreakCell(Context ctx, String label, Integer moves) {
        float dp = dp(ctx);
        boolean done = moves != null;

        LinearLayout cell = new LinearLayout(ctx);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding((int) (2 * dp), (int) (6 * dp), (int) (2 * dp), (int) (6 * dp));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins((int) (2 * dp), (int) (4 * dp), (int) (2 * dp), (int) (4 * dp));
        cell.setLayoutParams(lp);
        cell.setBackgroundColor(done ? 0xFF388E3C : lockedColor());

        TextView tvLabel = new TextView(ctx);
        tvLabel.setGravity(Gravity.CENTER);
        tvLabel.setTextSize(10f);
        tvLabel.setTextColor(Color.WHITE);
        tvLabel.setText(label);
        cell.addView(tvLabel);

        TextView tvMovesCell = new TextView(ctx);
        tvMovesCell.setGravity(Gravity.CENTER);
        tvMovesCell.setTextSize(12f);
        tvMovesCell.setTextColor(Color.WHITE);
        tvMovesCell.setText(done ? moves + "步" : "—");
        cell.addView(tvMovesCell);
        return cell;
    }

    /**
     * 开始每日挑战：内置关复用 startLevel 流程（其会清每日标记，开局后回填每日身份与
     * 标题），码关走自定义装载路径；当日关卡不可用或码非法一律 Toast 拒绝。
     */
    private void startDailyLevel() {
        String todayKey = SokobanDailyStore.todayKey();
        SokobanDailyPuzzle.DailyLevel daily = SokobanDailyPuzzle.forDate(todayKey);
        if (daily == null) {
            Toast.makeText(requireContext(), "今日关卡不可用，请稍后再试", Toast.LENGTH_SHORT).show();
            return;
        }
        if (daily.builtin) {
            startLevel(daily.builtinLevel);
            currentDailyLevel = daily;
            // startLevel 只隐 menuScroll；从每日面板进入需自行收起每日面板。
            dailyScroll.setVisibility(View.GONE);
            tvLevel.setText("每日·" + daily.name);
            return;
        }
        int[][] map = SokobanLevelCodec.decode(daily.code);
        if (map == null || !game.startCustomLevel(map)) {
            Toast.makeText(requireContext(), "关卡数据无效", Toast.LENGTH_SHORT).show();
            return;
        }
        currentDailyLevel = daily;
        currentCustomLevelName = null;
        currentCampaignLevel = null;
        dailyScroll.setVisibility(View.GONE);
        gamePanel.setVisibility(View.VISIBLE);
        tvStatus.setText(getString(R.string.game_sokoban_status));
        tvLevel.setText("每日·" + daily.name);
        updateMovesDisplay();
        sokobanView.setMap(game.getMap());
        gameStartTime = System.currentTimeMillis();
    }

    /**
     * 每日关卡完成：记录当日成绩并持久化；当日首次完成或刷新纪录（record 返回 true）
     * 经宿主 CoinWallet 发放每日奖励（与对局奖励共用每日上限）。
     * 不记 usageStore（含内置关每日局），防重玩刷对局统计。
     */
    private void onDailyLevelComplete(long elapsedSec) {
        SokobanDailyPuzzle.DailyLevel daily = currentDailyLevel;
        currentDailyLevel = null;

        Context ctx = requireContext();
        Map<String, Integer> bestMoves = SokobanDailyStore.load(ctx);
        String todayKey = SokobanDailyStore.todayKey();
        boolean firstOrRecord = SokobanDailyRecord.record(bestMoves, todayKey, game.getMoveCount());
        SokobanDailyStore.save(ctx, bestMoves);

        tvStatus.setText("🎉 每日·" + daily.name + "完成"
                + " | 推动 " + game.getPushCount() + " | 用时 " + elapsedSec + "s");

        if (!firstOrRecord) {
            return;
        }
        int earned = new CoinWallet(ctx)
                .grantBonus(10, "每日关卡完成·" + todayKey.substring(5));
        Toast.makeText(ctx, earned > 0
                        ? "🎉 完成每日关卡，奖励 " + earned + " 金币"
                        : "🎉 完成每日关卡，今日金币已达上限",
                Toast.LENGTH_LONG).show();
    }

    /** 锁定态控件底色（主题感知灰）。 */
    private int lockedColor() {
        return isNightMode() ? 0xFF3A3F4C : 0xFF9FA5B5;
    }

    /** 自定义关卡列表：点按游玩，末项进入删除列表。 */
    private void showCustomLevelList() {
        List<SokobanCustomLevels.CustomLevel> levels =
                SokobanCustomLevels.loadAll(requireContext());
        if (levels.isEmpty()) {
            Toast.makeText(requireContext(), "还没有自定义关卡，先新建一个吧", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[levels.size() + 1];
        for (int i = 0; i < levels.size(); i++) {
            names[i] = levels.get(i).name;
        }
        names[levels.size()] = "✕ 删除关卡…";
        new AlertDialog.Builder(requireContext())
                .setTitle("自定义关卡")
                .setItems(names, (dialog, which) -> {
                    if (which == levels.size()) {
                        showDeleteLevelList(levels);
                    } else {
                        playCustomLevel(levels.get(which));
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeleteLevelList(List<SokobanCustomLevels.CustomLevel> levels) {
        String[] names = new String[levels.size()];
        for (int i = 0; i < levels.size(); i++) {
            names[i] = levels.get(i).name;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("选择要删除的关卡")
                .setItems(names, (dialog, which) -> {
                    SokobanCustomLevels.remove(requireContext(), levels.get(which).id);
                    Toast.makeText(requireContext(),
                            "已删除「" + levels.get(which).name + "」", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 打开关卡编辑器（replace + backstack，返回时菜单自然重建刷新数量文案）。 */
    private void openEditor() {
        getParentFragmentManager().beginTransaction()
                .replace(com.gamecenter.app.R.id.fragment_container, new SokobanEditorFragment())
                .addToBackStack(null)
                .commit();
    }

    /** 菜单按钮构造（与内置关按钮同风格）。 */
    private Button createMenuButton(Context ctx, String text, View.OnClickListener listener) {
        int minimumTouchSize = (int) Math.ceil(48 * dp(ctx));
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setMinimumHeight(minimumTouchSize);
        btn.setMinHeight(minimumTouchSize);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, (int) (4 * dp(ctx)), 0, (int) (4 * dp(ctx)));
        btn.setLayoutParams(lp);
        btn.setBackgroundColor(colorBtnLevel);
        btn.setTextColor(Color.WHITE);
        btn.setOnClickListener(listener);
        return btn;
    }

    private void movePlayer(int dr, int dc) {
        if (!game.isRunning()) return;
        boolean moved = game.movePlayer(dr, dc);
        if (moved) {
            sokobanView.setMap(game.getMap());
            updateMovesDisplay();
            if (game.isLevelComplete()) {
                onLevelComplete();
            }
        }
    }

    private void undoMove() {
        if (!game.isRunning()) return;
        if (game.undoMove()) {
            sokobanView.setMap(game.getMap());
            updateMovesDisplay();
        }
    }

    private void onLevelComplete() {
        long elapsedMs = System.currentTimeMillis() - gameStartTime;
        long elapsedSec = elapsedMs / 1000;
        game.onLevelComplete();

        // 每日局优先分发：内置关每日局 isCustomLevel()==false，若后判会误入
        // usageStore 统计路径（防刷缺口）；码关每日局则会被误入自定义分支。
        if (currentDailyLevel != null) {
            onDailyLevelComplete(elapsedSec);
            return;
        }

        if (game.isCustomLevel()) {
            if (currentCampaignLevel != null) {
                onCampaignLevelComplete(elapsedSec);
                return;
            }
            // 自定义关卡完成不计入使用统计，避免自制关卡刷胜率污染内置对局数据。
            String name = currentCustomLevelName == null ? "" : "《" + currentCustomLevelName + "》";
            tvStatus.setText("🎉 自定义关卡" + name + "完成"
                    + " | 推动 " + game.getPushCount() + " | 用时 " + elapsedSec + "s");
            return;
        }

        tvStatus.setText(getString(R.string.game_sokoban_win_format, game.getMoveCount())
                + " | 推动 " + game.getPushCount() + " | 用时 " + elapsedSec + "s");

        if (usageStore != null) {
            usageStore.recordWin(GAME_ID);
            usageStore.recordPlayTime(GAME_ID, elapsedMs);
        }
    }

    private void updateMovesDisplay() {
        tvMoves.setText(getString(R.string.game_sokoban_moves_format, game.getMoveCount(), game.getPushCount()));
    }

    private boolean isNightMode() {
        int nightMode = requireContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (game != null) {
            game.stop();
        }
    }
}
