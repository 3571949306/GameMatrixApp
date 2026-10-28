package com.gamecenter.app.blackjack;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.games.GameUsageStore;

import java.util.List;

/**
 * 现代化 21 点战场 Fragment。
 *
 * <p>特性：
 * <ul>
 *   <li>即开即玩战场，首屏秒入牌局；</li>
 *   <li>标准顶部导航栏：❮ 退出确认、游戏标题、[难度 ▾] 下拉胶囊、ℹ 规则弹窗、⚙ 设置弹窗；</li>
 *   <li>双雄 HUD 看板：双方手牌点数实时统计、胜负率、动态秒表与 🔥 连胜火焰；</li>
 *   <li>拟真仿真牌桌：圆角实体纸牌视图、花色着色 (红心/方块鲜红，黑桃/梅花沉黑)、暗牌立体纹理；</li>
 *   <li>基本策略智能提示 (💡 提示)：基于二十一点经典数学概率论提供最优手牌决策；</li>
 *   <li>底部圆角胶囊控制栏：➕ 要牌、✋ 停牌、💡 提示、↩ 撤回、🔄 新局；</li>
 *   <li>沉浸式对局结算弹窗与用时/成就持久化。</li>
 * </ul>
 * </p>
 */
public class BlackjackModuleFragment extends Fragment {

    private static final String GAME_ID = "blackjack";

    private BlackjackGame game;
    private GameUsageStore usageStore;

    // 计时器相关
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private long roundStartTime = 0;
    private long elapsedSeconds = 0;
    private boolean isTimerRunning = false;

    // 顶部导航与状态组件
    private TextView tvBack;
    private TextView tvTitle;
    private TextView tvDifficultyBadge;
    private TextView tvRules;
    private TextView tvSettings;

    // HUD 组件
    private TextView tvPlayerName;
    private TextView tvPlayerStats;
    private TextView tvTimer;
    private TextView tvStreak;
    private TextView tvDealerName;
    private TextView tvDealerStats;

    // 牌桌组件
    private TextView tvDealerScoreHeader;
    private LinearLayout llDealerCards;
    private TextView tvStatusBanner;
    private TextView tvPlayerScoreHeader;
    private LinearLayout llPlayerCards;

    // 操作胶囊组件
    private Button btnHit;
    private Button btnStand;
    private Button btnHint;
    private Button btnUndo;
    private Button btnNewGame;

    // 主题配色
    private int colorBg;
    private int colorSurface;
    private int colorTableFelt;
    private int colorTextPrimary;
    private int colorTextSecondary;
    private int colorTextMuted;
    private int colorAccent;
    private int colorWin;
    private int colorLose;
    private int colorCardBg;
    private int colorCardBorder;
    private int colorCardBack;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isTimerRunning) {
                elapsedSeconds = (System.currentTimeMillis() - roundStartTime) / 1000;
                long mins = elapsedSeconds / 60;
                long secs = elapsedSeconds % 60;
                if (tvTimer != null) {
                    tvTimer.setText(String.format("⏱ %02d:%02d", mins, secs));
                }
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        game = new BlackjackGame();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        resolveThemeColors(ctx);

        if (usageStore == null) {
            usageStore = new GameUsageStore(ctx);
        }
        usageStore.recordLaunch(GAME_ID);
        game.setHighScore(usageStore.getHighScore(GAME_ID));

        ScrollView rootScroll = new ScrollView(ctx);
        rootScroll.setFillViewport(true);
        rootScroll.setBackgroundColor(colorBg);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (12 * dp), (int) (8 * dp), (int) (12 * dp), (int) (16 * dp));
        rootScroll.addView(root);

        // 1. 顶部标准导航栏
        root.addView(createTopBar(ctx, dp));

        // 2. 双雄对战 HUD 看板
        root.addView(createHudDashboard(ctx, dp));

        // 3. 拟真仿真牌桌
        root.addView(createTabletopView(ctx, dp));

        // 4. 底部操作胶囊栏
        root.addView(createActionCapsules(ctx, dp));

        // 即开即玩：秒入牌局
        startNewRound(false);

        return rootScroll;
    }

    private View createTopBar(Context ctx, float dp) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(0, (int) (4 * dp), 0, (int) (8 * dp));

        tvBack = new TextView(ctx);
        tvBack.setText("❮");
        tvBack.setTextSize(20f);
        tvBack.setTextColor(colorTextPrimary);
        tvBack.setPadding((int) (8 * dp), (int) (8 * dp), (int) (12 * dp), (int) (8 * dp));
        tvBack.setOnClickListener(v -> handleBackPress());
        bar.addView(tvBack);

        tvTitle = new TextView(ctx);
        tvTitle.setText("21点");
        tvTitle.setTextSize(18f);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(colorTextPrimary);
        bar.addView(tvTitle);

        tvDifficultyBadge = new TextView(ctx);
        updateDifficultyBadgeText();
        tvDifficultyBadge.setTextSize(12f);
        tvDifficultyBadge.setTextColor(colorAccent);
        tvDifficultyBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tvDifficultyBadge.setPadding((int) (10 * dp), (int) (4 * dp), (int) (10 * dp), (int) (4 * dp));
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(applyAlpha(colorAccent, 0.15f));
        badgeBg.setCornerRadius(12 * dp);
        tvDifficultyBadge.setBackground(badgeBg);

        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeParams.leftMargin = (int) (8 * dp);
        tvDifficultyBadge.setLayoutParams(badgeParams);
        tvDifficultyBadge.setOnClickListener(v -> showDifficultyDialog());
        bar.addView(tvDifficultyBadge);

        View spacer = new View(ctx);
        LinearLayout.LayoutParams spacerParams = new LinearLayout.LayoutParams(0, 1, 1f);
        bar.addView(spacer, spacerParams);

        tvRules = new TextView(ctx);
        tvRules.setText("ℹ");
        tvRules.setTextSize(18f);
        tvRules.setTextColor(colorTextSecondary);
        tvRules.setPadding((int) (10 * dp), (int) (8 * dp), (int) (8 * dp), (int) (8 * dp));
        tvRules.setOnClickListener(v -> showRulesDialog());
        bar.addView(tvRules);

        tvSettings = new TextView(ctx);
        tvSettings.setText("⚙");
        tvSettings.setTextSize(18f);
        tvSettings.setTextColor(colorTextSecondary);
        tvSettings.setPadding((int) (8 * dp), (int) (8 * dp), (int) (4 * dp), (int) (8 * dp));
        tvSettings.setOnClickListener(v -> showSettingsDialog());
        bar.addView(tvSettings);

        return bar;
    }

    private View createHudDashboard(Context ctx, float dp) {
        LinearLayout hud = new LinearLayout(ctx);
        hud.setOrientation(LinearLayout.HORIZONTAL);
        hud.setGravity(Gravity.CENTER_VERTICAL);
        hud.setPadding((int) (12 * dp), (int) (10 * dp), (int) (12 * dp), (int) (10 * dp));

        GradientDrawable hudBg = new GradientDrawable();
        hudBg.setColor(colorSurface);
        hudBg.setCornerRadius(14 * dp);
        hudBg.setStroke((int) (1 * dp), applyAlpha(colorTextMuted, 0.2f));
        hud.setBackground(hudBg);

        // 玩家左侧
        LinearLayout colPlayer = new LinearLayout(ctx);
        colPlayer.setOrientation(LinearLayout.VERTICAL);
        colPlayer.setGravity(Gravity.START);
        LinearLayout.LayoutParams pParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        colPlayer.setLayoutParams(pParams);

        tvPlayerName = new TextView(ctx);
        tvPlayerName.setText("🧑 我方手牌");
        tvPlayerName.setTextSize(13f);
        tvPlayerName.setTypeface(Typeface.DEFAULT_BOLD);
        tvPlayerName.setTextColor(colorTextPrimary);
        colPlayer.addView(tvPlayerName);

        tvPlayerStats = new TextView(ctx);
        tvPlayerStats.setText("胜 0 | 负 0 (0%)");
        tvPlayerStats.setTextSize(11f);
        tvPlayerStats.setTextColor(colorTextSecondary);
        colPlayer.addView(tvPlayerStats);
        hud.addView(colPlayer);

        // 中间计时与连胜
        LinearLayout colCenter = new LinearLayout(ctx);
        colCenter.setOrientation(LinearLayout.VERTICAL);
        colCenter.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cParams.leftMargin = (int) (4 * dp);
        cParams.rightMargin = (int) (4 * dp);
        colCenter.setLayoutParams(cParams);

        tvTimer = new TextView(ctx);
        tvTimer.setText("⏱ 00:00");
        tvTimer.setTextSize(13f);
        tvTimer.setTypeface(Typeface.MONOSPACE);
        tvTimer.setTextColor(colorAccent);
        colCenter.addView(tvTimer);

        tvStreak = new TextView(ctx);
        tvStreak.setText("🔥 连胜 0 (最高 0)");
        tvStreak.setTextSize(11f);
        tvStreak.setTextColor(colorAccent);
        colCenter.addView(tvStreak);
        hud.addView(colCenter);

        // 庄家右侧
        LinearLayout colDealer = new LinearLayout(ctx);
        colDealer.setOrientation(LinearLayout.VERTICAL);
        colDealer.setGravity(Gravity.END);
        LinearLayout.LayoutParams dParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        colDealer.setLayoutParams(dParams);

        tvDealerName = new TextView(ctx);
        tvDealerName.setText("🤖 庄家 (Dealer)");
        tvDealerName.setTextSize(13f);
        tvDealerName.setTypeface(Typeface.DEFAULT_BOLD);
        tvDealerName.setTextColor(colorTextPrimary);
        colDealer.addView(tvDealerName);

        tvDealerStats = new TextView(ctx);
        tvDealerStats.setText("胜 0 | 平 0");
        tvDealerStats.setTextSize(11f);
        tvDealerStats.setTextColor(colorTextSecondary);
        colDealer.addView(tvDealerStats);
        hud.addView(colDealer);

        LinearLayout.LayoutParams hudParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hudParams.bottomMargin = (int) (10 * dp);
        hud.setLayoutParams(hudParams);

        return hud;
    }

    private View createTabletopView(Context ctx, float dp) {
        LinearLayout table = new LinearLayout(ctx);
        table.setOrientation(LinearLayout.VERTICAL);
        table.setPadding((int) (14 * dp), (int) (14 * dp), (int) (14 * dp), (int) (14 * dp));

        GradientDrawable tableBg = new GradientDrawable();
        tableBg.setColor(colorTableFelt);
        tableBg.setCornerRadius(18 * dp);
        tableBg.setStroke((int) (2 * dp), applyAlpha(colorAccent, 0.4f));
        table.setBackground(tableBg);

        // 庄家区域标题与点数
        tvDealerScoreHeader = new TextView(ctx);
        tvDealerScoreHeader.setText("庄家手牌  点数: ??");
        tvDealerScoreHeader.setTextSize(14f);
        tvDealerScoreHeader.setTypeface(Typeface.DEFAULT_BOLD);
        tvDealerScoreHeader.setTextColor(0xFFE8F5E9);
        table.addView(tvDealerScoreHeader);

        // 庄家卡牌横向滚动行
        HorizontalScrollView hsvDealer = new HorizontalScrollView(ctx);
        hsvDealer.setHorizontalScrollBarEnabled(false);
        llDealerCards = new LinearLayout(ctx);
        llDealerCards.setOrientation(LinearLayout.HORIZONTAL);
        llDealerCards.setGravity(Gravity.CENTER_VERTICAL);
        llDealerCards.setPadding(0, (int) (6 * dp), 0, (int) (10 * dp));
        hsvDealer.addView(llDealerCards);
        table.addView(hsvDealer);

        // 中央状态与提示横幅
        tvStatusBanner = new TextView(ctx);
        tvStatusBanner.setText("轮到你了！请选择【要牌】或【停牌】");
        tvStatusBanner.setTextSize(14f);
        tvStatusBanner.setGravity(Gravity.CENTER);
        tvStatusBanner.setTypeface(Typeface.DEFAULT_BOLD);
        tvStatusBanner.setTextColor(0xFFFFFFFF);
        tvStatusBanner.setPadding((int) (12 * dp), (int) (8 * dp), (int) (12 * dp), (int) (8 * dp));

        GradientDrawable bannerBg = new GradientDrawable();
        bannerBg.setColor(0x88000000);
        bannerBg.setCornerRadius(12 * dp);
        tvStatusBanner.setBackground(bannerBg);

        LinearLayout.LayoutParams bannerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bannerParams.topMargin = (int) (4 * dp);
        bannerParams.bottomMargin = (int) (10 * dp);
        tvStatusBanner.setLayoutParams(bannerParams);
        table.addView(tvStatusBanner);

        // 我方区域标题与点数
        tvPlayerScoreHeader = new TextView(ctx);
        tvPlayerScoreHeader.setText("我方手牌  点数: 0");
        tvPlayerScoreHeader.setTextSize(14f);
        tvPlayerScoreHeader.setTypeface(Typeface.DEFAULT_BOLD);
        tvPlayerScoreHeader.setTextColor(0xFFE8F5E9);
        table.addView(tvPlayerScoreHeader);

        // 我方卡牌横向滚动行
        HorizontalScrollView hsvPlayer = new HorizontalScrollView(ctx);
        hsvPlayer.setHorizontalScrollBarEnabled(false);
        llPlayerCards = new LinearLayout(ctx);
        llPlayerCards.setOrientation(LinearLayout.HORIZONTAL);
        llPlayerCards.setGravity(Gravity.CENTER_VERTICAL);
        llPlayerCards.setPadding(0, (int) (6 * dp), 0, (int) (6 * dp));
        hsvPlayer.addView(llPlayerCards);
        table.addView(hsvPlayer);

        LinearLayout.LayoutParams tableParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        tableParams.bottomMargin = (int) (10 * dp);
        table.setLayoutParams(tableParams);

        return table;
    }

    private View createActionCapsules(Context ctx, float dp) {
        LinearLayout container = new LinearLayout(ctx);
        container.setOrientation(LinearLayout.VERTICAL);

        // Row 1: 要牌 (Hit), 停牌 (Stand), 提示 (Hint)
        LinearLayout row1 = new LinearLayout(ctx);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER);

        btnHit = createCapsuleButton(ctx, dp, "➕ 要牌", colorWin, v -> onPlayerHit());
        btnStand = createCapsuleButton(ctx, dp, "✋ 停牌", 0xFFE65100, v -> onPlayerStand());
        btnHint = createCapsuleButton(ctx, dp, "💡 提示", colorAccent, v -> showStrategyHint());

        row1.addView(btnHit, new LinearLayout.LayoutParams(0, (int) (44 * dp), 1f));
        row1.addView(btnStand, new LinearLayout.LayoutParams(0, (int) (44 * dp), 1f));
        row1.addView(btnHint, new LinearLayout.LayoutParams(0, (int) (44 * dp), 1f));
        container.addView(row1);

        // Row 2: 撤回 (Undo), 新局 (New Round)
        LinearLayout row2 = new LinearLayout(ctx);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams r2Params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        r2Params.topMargin = (int) (8 * dp);
        row2.setLayoutParams(r2Params);

        btnUndo = createCapsuleButton(ctx, dp, "↩ 撤回", colorSurface, v -> onUndoHit());
        btnUndo.setTextColor(colorTextPrimary);
        btnNewGame = createCapsuleButton(ctx, dp, "🔄 新局", colorAccent, v -> startNewRound(true));

        LinearLayout.LayoutParams uParams = new LinearLayout.LayoutParams(0, (int) (40 * dp), 1f);
        uParams.rightMargin = (int) (6 * dp);
        row2.addView(btnUndo, uParams);

        LinearLayout.LayoutParams nParams = new LinearLayout.LayoutParams(0, (int) (40 * dp), 1f);
        nParams.leftMargin = (int) (6 * dp);
        row2.addView(btnNewGame, nParams);

        container.addView(row2);
        return container;
    }

    private Button createCapsuleButton(Context ctx, float dp, String text, int bgColor, View.OnClickListener listener) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextSize(13f);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextColor(Color.WHITE);
        btn.setGravity(Gravity.CENTER);

        GradientDrawable gd = new GradientDrawable();
        gd.setColor(bgColor);
        gd.setCornerRadius(22 * dp);
        btn.setBackground(gd);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) (42 * dp), 1f);
        lp.leftMargin = (int) (3 * dp);
        lp.rightMargin = (int) (3 * dp);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(listener);
        return btn;
    }

    // ==================== 牌面渲染系统 ====================

    private View createCardView(Context ctx, float dp, String card, boolean isHidden) {
        FrameLayout cardFrame = new FrameLayout(ctx);
        int cardWidth = (int) (52 * dp);
        int cardHeight = (int) (74 * dp);

        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setCornerRadius(8 * dp);

        if (isHidden) {
            // 暗牌牌背设计：深蓝/金边花纹
            cardBg.setColor(colorCardBack);
            cardBg.setStroke((int) (2 * dp), 0xFFB0BEC5);
            cardFrame.setBackground(cardBg);

            TextView tvPattern = new TextView(ctx);
            tvPattern.setText("🂠");
            tvPattern.setTextSize(26f);
            tvPattern.setTextColor(0xFFFFFFFF);
            tvPattern.setGravity(Gravity.CENTER);
            cardFrame.addView(tvPattern, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            // 明牌设计：经典白色纸牌
            cardBg.setColor(colorCardBg);
            cardBg.setStroke((int) (1.5f * dp), colorCardBorder);
            cardFrame.setBackground(cardBg);

            String rank = BlackjackGame.getCardRank(card);
            String suit = BlackjackGame.getCardSuit(card);
            boolean isRed = suit.equals("♥") || suit.equals("♦");
            int cardColor = isRed ? 0xFFD32F2F : 0xFF212121;

            // 角标 (Rank + Suit)
            TextView tvCorner = new TextView(ctx);
            tvCorner.setText(rank + "\n" + suit);
            tvCorner.setTextSize(11f);
            tvCorner.setLineSpacing(0, 0.8f);
            tvCorner.setTypeface(Typeface.DEFAULT_BOLD);
            tvCorner.setTextColor(cardColor);
            tvCorner.setPadding((int) (4 * dp), (int) (3 * dp), 0, 0);
            cardFrame.addView(tvCorner, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START));

            // 中央大花色
            TextView tvCenter = new TextView(ctx);
            tvCenter.setText(suit);
            tvCenter.setTextSize(22f);
            tvCenter.setTextColor(cardColor);
            tvCenter.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams cLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            cLp.leftMargin = (int) (6 * dp);
            cardFrame.addView(tvCenter, cLp);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(cardWidth, cardHeight);
        params.rightMargin = (int) (8 * dp);
        cardFrame.setLayoutParams(params);
        return cardFrame;
    }

    private void updateTableDisplay(boolean hideDealerSecond) {
        Context ctx = getContext();
        if (ctx == null) return;
        float dp = ctx.getResources().getDisplayMetrics().density;

        // 1. 庄家卡牌
        llDealerCards.removeAllViews();
        List<String> dealerHand = game.getDealerHand();
        for (int i = 0; i < dealerHand.size(); i++) {
            boolean isHidden = (i == 1 && hideDealerSecond);
            llDealerCards.addView(createCardView(ctx, dp, dealerHand.get(i), isHidden));
        }

        if (hideDealerSecond && dealerHand.size() >= 2) {
            int visibleVal = BlackjackGame.getCardValue(dealerHand.get(0));
            tvDealerScoreHeader.setText("庄家手牌  点数: " + visibleVal + " + ?");
        } else {
            tvDealerScoreHeader.setText("庄家手牌  点数: " + game.getDealerScore());
        }

        // 2. 我方卡牌
        llPlayerCards.removeAllViews();
        List<String> playerHand = game.getPlayerHand();
        for (String card : playerHand) {
            llPlayerCards.addView(createCardView(ctx, dp, card, false));
        }
        tvPlayerScoreHeader.setText("我方手牌  点数: " + game.getPlayerScore());

        // 3. 撤回按钮状态
        btnUndo.setEnabled(game.canUndo());
        btnUndo.setAlpha(game.canUndo() ? 1.0f : 0.4f);

        // 4. 要牌/停牌按钮状态
        btnHit.setEnabled(game.isRoundActive());
        btnStand.setEnabled(game.isRoundActive());
        btnHit.setAlpha(game.isRoundActive() ? 1.0f : 0.4f);
        btnStand.setAlpha(game.isRoundActive() ? 1.0f : 0.4f);
    }

    // ==================== 游戏交互系统 ====================

    private void startNewRound(boolean showToast) {
        game.startNewRound();
        resetTimer();

        if (!game.isRoundActive()) {
            // 开局即决胜 (例如双 Blackjack 或单方天成)
            handleRoundSettlement();
        } else {
            tvStatusBanner.setText("轮到你了！请选择【要牌】或【停牌】");
            tvStatusBanner.setTextColor(0xFFFFFFFF);
            updateTableDisplay(true);
        }
        updateHudStats();

        if (showToast && getContext() != null) {
            Toast.makeText(getContext(), "已重新发牌！", Toast.LENGTH_SHORT).show();
        }
    }

    private void onPlayerHit() {
        if (!game.isRoundActive()) return;
        boolean canContinue = game.hit();
        updateTableDisplay(game.isRoundActive());

        if (!game.isRoundActive()) {
            handleRoundSettlement();
        } else {
            tvStatusBanner.setText("手牌点数: " + game.getPlayerScore() + " 点，继续要牌或停牌？");
        }
    }

    private void onPlayerStand() {
        if (!game.isRoundActive()) return;
        game.stand();
        handleRoundSettlement();
    }

    private void onUndoHit() {
        if (!game.canUndo()) return;
        if (game.undoHit()) {
            updateTableDisplay(true);
            tvStatusBanner.setText("已撤销上一张抽牌，当前点数: " + game.getPlayerScore());
            if (getContext() != null) {
                Toast.makeText(getContext(), "↩ 已撤销抽牌", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void handleRoundSettlement() {
        stopTimer();
        updateTableDisplay(false); // 翻开庄家底牌
        updateHudStats();

        BlackjackGame.RoundResult res = game.getLastResult();
        String bannerMsg;
        int bannerColor;

        switch (res) {
            case PLAYER_BLACKJACK:
                bannerMsg = "🎉 黑杰克 (Blackjack)！天胡通杀！(+50分)";
                bannerColor = 0xFFFFD700;
                break;
            case PLAYER_WIN:
                bannerMsg = "🎉 我方胜出！" + game.getPlayerScore() + " vs " + game.getDealerScore() + " (+20分)";
                bannerColor = 0xFF81C784;
                break;
            case DEALER_BUST:
                bannerMsg = "💥 庄家爆牌 (" + game.getDealerScore() + "点)！我方获胜！(+20分)";
                bannerColor = 0xFF81C784;
                break;
            case PLAYER_BUST:
                bannerMsg = "💥 我方爆牌 (" + game.getPlayerScore() + "点)！惜败庄家";
                bannerColor = 0xFFEF5350;
                break;
            case DEALER_WIN:
                bannerMsg = "🤖 庄家胜出！" + game.getDealerScore() + " vs " + game.getPlayerScore();
                bannerColor = 0xFFEF5350;
                break;
            case TIE:
            default:
                bannerMsg = "🤝 双方势均力敌，战成平局！";
                bannerColor = 0xFFFFF59D;
                break;
        }

        tvStatusBanner.setText(bannerMsg);
        tvStatusBanner.setTextColor(bannerColor);

        // 持久化成就与分数
        if (usageStore != null) {
            usageStore.recordScore(GAME_ID, game.getHighScore());
            usageStore.recordPlayTime(GAME_ID, elapsedSeconds * 1000);
            if (res == BlackjackGame.RoundResult.PLAYER_WIN ||
                res == BlackjackGame.RoundResult.PLAYER_BLACKJACK ||
                res == BlackjackGame.RoundResult.DEALER_BUST) {
                usageStore.recordWin(GAME_ID);
            } else if (res == BlackjackGame.RoundResult.PLAYER_BUST ||
                       res == BlackjackGame.RoundResult.DEALER_WIN) {
                usageStore.recordLoss(GAME_ID);
            }
        }
    }

    private void showStrategyHint() {
        String hint = game.getBasicStrategyHint();
        if (getContext() == null) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("💡 21点基本策略建议")
                .setMessage(hint)
                .setPositiveButton("采纳建议", null)
                .show();
    }

    private void updateHudStats() {
        int total = game.getPlayerWins() + game.getDealerWins() + game.getTies();
        int winRate = total > 0 ? (game.getPlayerWins() * 100 / total) : 0;
        tvPlayerStats.setText("胜 " + game.getPlayerWins() + " | 负 " + game.getDealerWins() + " (" + winRate + "%)");
        tvStreak.setText("🔥 连胜 " + game.getWinStreak() + " (最高 " + game.getMaxStreak() + ")");
        tvDealerStats.setText("胜 " + game.getDealerWins() + " | 平 " + game.getTies());
    }

    private void updateDifficultyBadgeText() {
        if (tvDifficultyBadge == null) return;
        switch (game.getDifficulty()) {
            case BlackjackGame.DIFFICULTY_EASY:
                tvDifficultyBadge.setText("简单 ▾");
                break;
            case BlackjackGame.DIFFICULTY_HARD:
                tvDifficultyBadge.setText("困难 ▾");
                break;
            case BlackjackGame.DIFFICULTY_MEDIUM:
            default:
                tvDifficultyBadge.setText("中等 ▾");
                break;
        }
    }

    private void showDifficultyDialog() {
        if (getContext() == null) return;
        String[] options = {
                "简单 (庄家软硬17停牌，容错率高)",
                "中等 (标准拉斯维加斯规则，软17摸牌)",
                "困难 (真实牌靴记忆，严苛博弈)"
        };
        new AlertDialog.Builder(requireContext())
                .setTitle("选择游戏难度")
                .setSingleChoiceItems(options, game.getDifficulty(), (dialog, which) -> {
                    game.setDifficulty(which);
                    updateDifficultyBadgeText();
                    dialog.dismiss();
                    startNewRound(true);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showRulesDialog() {
        if (getContext() == null) return;
        String rules = "【玩法规则】\n"
                + "• 目标：使手中牌面点数尽量接近或等于 21 点，且不超过 21 点。\n"
                + "• 点数计算：2~10 按牌面计算；J/Q/K 计为 10 点；A 可计为 1 点或 11 点。\n"
                + "• 黑杰克 (Blackjack)：起手两张牌即为 A + 10点牌，直接享受 1.5 倍高额计分！\n"
                + "• 爆牌 (Bust)：任何一方牌面超过 21 点即判负。\n\n"
                + "【操作指南】\n"
                + "• ➕ 要牌：向牌靴额外抽取一张纸牌。\n"
                + "• ✋ 停牌：保持当前点数，交由庄家抽牌决胜。\n"
                + "• 💡 提示：依据经典二十一点数学概率论给出最佳期望值行动建议。\n"
                + "• ↩ 撤回：误触抽牌时可撤销最后一次要牌。";

        new AlertDialog.Builder(requireContext())
                .setTitle("21点玩法规则与策略")
                .setMessage(rules)
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showSettingsDialog() {
        if (getContext() == null) return;
        String[] items = {"重新洗牌靴", "清空本次连胜与战绩"};
        new AlertDialog.Builder(requireContext())
                .setTitle("游戏设置")
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        game.initDeck();
                        Toast.makeText(getContext(), "牌靴已重新洗牌！", Toast.LENGTH_SHORT).show();
                    } else if (which == 1) {
                        game = new BlackjackGame();
                        updateHudStats();
                        startNewRound(false);
                        Toast.makeText(getContext(), "战绩已重置", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("返回", null)
                .show();
    }

    private void handleBackPress() {
        if (game.isRoundActive()) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("退出确认")
                    .setMessage("当前对局正在进行中，退出将记录离开。确定退出吗？")
                    .setPositiveButton("退出", (dialog, which) -> finishFragment())
                    .setNegativeButton("继续对局", null)
                    .show();
        } else {
            finishFragment();
        }
    }

    private void finishFragment() {
        stopTimer();
        if (getActivity() != null) {
            getActivity().onBackPressed();
        }
    }

    private void resetTimer() {
        roundStartTime = System.currentTimeMillis();
        elapsedSeconds = 0;
        isTimerRunning = true;
        timerHandler.removeCallbacks(timerRunnable);
        timerHandler.post(timerRunnable);
    }

    private void stopTimer() {
        isTimerRunning = false;
        timerHandler.removeCallbacks(timerRunnable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopTimer();
    }

    private void resolveThemeColors(Context ctx) {
        int nightMode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isNight = nightMode == Configuration.UI_MODE_NIGHT_YES;

        if (isNight) {
            colorBg = 0xFF121418;
            colorSurface = 0xFF1E222B;
            colorTableFelt = 0xFF143621; // 暗夜墨绿毛呢
            colorTextPrimary = 0xFFEEEEEE;
            colorTextSecondary = 0xFFB0BEC5;
            colorTextMuted = 0xFF78909C;
            colorAccent = 0xFF4CAF50;
            colorWin = 0xFF2E7D32;
            colorLose = 0xFFC62828;
            colorCardBg = 0xFFFFFFFF;
            colorCardBorder = 0xFFCFD8DC;
            colorCardBack = 0xFF1A237E;
        } else {
            colorBg = 0xFFF0F4F2;
            colorSurface = 0xFFFFFFFF;
            colorTableFelt = 0xFF1B5E20; // 经典赌桌绿毛呢
            colorTextPrimary = 0xFF212121;
            colorTextSecondary = 0xFF546E7A;
            colorTextMuted = 0xFF90A4AE;
            colorAccent = 0xFF2E7D32;
            colorWin = 0xFF388E3C;
            colorLose = 0xFFD32F2F;
            colorCardBg = 0xFFFFFFFF;
            colorCardBorder = 0xFFB0BEC5;
            colorCardBack = 0xFF1565C0;
        }
    }

    private int applyAlpha(int color, float alpha) {
        int a = Math.round(Color.alpha(color) * alpha);
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }
}
