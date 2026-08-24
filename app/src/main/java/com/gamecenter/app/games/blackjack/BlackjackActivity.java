package com.gamecenter.app.games.blackjack;

import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.gamecenter.app.R;
import com.gamecenter.app.games.base.BaseGameActivity;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 21点游戏 Activity（继承 BaseGameActivity）。
 *
 * <p>玩家与庄家对战，目标是使手牌点数接近21点但不超过。
 * 庄家AI策略：点数小于17时必须要牌。</p>
 *
 * <p>成就系统：
 * <ul>
 *   <li>首次胜利</li>
 *   <li>拿到Blackjack（A+10点牌）</li>
 *   <li>三连胜</li>
 *   <li>累计20局</li>
 *   <li>连续5局不输</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class BlackjackActivity extends BaseGameActivity {

    // ==================== 常量 ====================
    private static final String[] SUITS = {"♠", "♥", "♦", "♣"};
    private static final String[] RANKS = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};

    // ==================== 游戏状态 ====================
    private List<String> deck = new ArrayList<>();
    private List<String> playerHand = new ArrayList<>();
    private List<String> dealerHand = new ArrayList<>();
    private int playerScore = 0;
    private int dealerScore = 0;
    private int totalGames = 0;
    private int playerWins = 0;
    private int dealerWins = 0;
    private int ties = 0;
    private int winStreak = 0;
    private int noLossStreak = 0;
    private boolean roundActive = false;
    private Random random = new Random();

    /** 2026-08-23 P3: 统一音效/震动反馈（内部实时遵循设置开关） */
    private com.gamecenter.app.games.base.GameFeedback feedback;

    // ==================== UI 组件 ====================
    private TextView tvStatus;
    private TextView tvPlayerHand;
    private TextView tvPlayerTotal;
    private TextView tvDealerHand;
    private TextView tvDealerTotal;
    private TextView tvStats;
    private MaterialButton btnHit;
    private MaterialButton btnStand;
    private MaterialButton btnNewGame;

    // ==================== BaseGameActivity 实现 ====================

    @NonNull
    @Override
    protected String getGameId() {
        return "blackjack";
    }

    @NonNull
    @Override
    protected String getGameName() {
        return getString(R.string.game_blackjack_name);
    }

    @Override
    protected void initGame() {
        // 2026-08-23 P3：初始化音效/震动反馈
        feedback = new com.gamecenter.app.games.base.GameFeedback(this);
        if (gameContentContainer instanceof FrameLayout) {
            View contentView = createGameContentView();
            ((FrameLayout) gameContentContainer).addView(contentView);
        }
    }

    @Override
    protected void startGame() {
        isGameRunning = true;
        gameStartTime = System.currentTimeMillis();
    }

    @Override
    protected void pauseGame() {
        isGamePaused = true;
    }

    @Override
    protected void resumeGame() {
        isGamePaused = false;
    }

    @Override
    protected void endGame() {
        isGameRunning = false;
        if (gameStartTime > 0) {
            usageStore.recordPlayTime(getGameId(), System.currentTimeMillis() - gameStartTime);
        }
    }

    @Override
    protected void checkAchievement(@NonNull String eventType, @NonNull Object... params) {
        achievementManager.checkAndUnlock(eventType, params);
    }

    // ==================== 游戏视图创建 ====================

    /**
     * 创建游戏内容视图
     */
    private View createGameContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.game_blackjack_color_bg));
        root.setPadding(32, 32, 32, 32);

        // 状态文本
        tvStatus = new TextView(this);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(18f);
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.game_blackjack_color_text_primary));
        tvStatus.setPadding(0, 16, 0, 16);
        tvStatus.setText(getString(R.string.game_blackjack_status_init));

        // 庄家区域
        TextView tvDealerLabel = new TextView(this);
        tvDealerLabel.setGravity(Gravity.CENTER);
        tvDealerLabel.setTextSize(16f);
        tvDealerLabel.setTextColor(ContextCompat.getColor(this, R.color.game_blackjack_color_text_secondary));
        tvDealerLabel.setText(getString(R.string.game_blackjack_dealer_label));

        tvDealerHand = new TextView(this);
        tvDealerHand.setGravity(Gravity.CENTER);
        tvDealerHand.setTextSize(18f);
        tvDealerHand.setTextColor(ContextCompat.getColor(this, R.color.game_blackjack_color_text_primary));
        tvDealerHand.setPadding(0, 8, 0, 4);
        tvDealerHand.setText("");

        tvDealerTotal = new TextView(this);
        tvDealerTotal.setGravity(Gravity.CENTER);
        tvDealerTotal.setTextSize(14f);
        tvDealerTotal.setTextColor(ContextCompat.getColor(this, R.color.game_blackjack_color_dealer_total));
        tvDealerTotal.setPadding(0, 0, 0, 24);
        tvDealerTotal.setText("");

        // 玩家区域
        TextView tvPlayerLabel = new TextView(this);
        tvPlayerLabel.setGravity(Gravity.CENTER);
        tvPlayerLabel.setTextSize(16f);
        tvPlayerLabel.setTextColor(ContextCompat.getColor(this, R.color.game_blackjack_color_text_secondary));
        tvPlayerLabel.setText(getString(R.string.game_blackjack_player_label));

        tvPlayerHand = new TextView(this);
        tvPlayerHand.setGravity(Gravity.CENTER);
        tvPlayerHand.setTextSize(18f);
        tvPlayerHand.setTextColor(ContextCompat.getColor(this, R.color.game_blackjack_color_text_primary));
        tvPlayerHand.setPadding(0, 8, 0, 4);
        tvPlayerHand.setText("");

        tvPlayerTotal = new TextView(this);
        tvPlayerTotal.setGravity(Gravity.CENTER);
        tvPlayerTotal.setTextSize(14f);
        tvPlayerTotal.setTextColor(ContextCompat.getColor(this, R.color.game_blackjack_color_player_total));
        tvPlayerTotal.setPadding(0, 0, 0, 24);
        tvPlayerTotal.setText("");

        // 统计
        tvStats = new TextView(this);
        tvStats.setGravity(Gravity.CENTER);
        tvStats.setTextSize(14f);
        tvStats.setTextColor(ContextCompat.getColor(this, R.color.game_blackjack_color_text_secondary));
        tvStats.setPadding(0, 8, 0, 16);
        tvStats.setText(getString(R.string.game_blackjack_stats_init));

        // 按钮区域
        LinearLayout buttonArea = new LinearLayout(this);
        buttonArea.setOrientation(LinearLayout.HORIZONTAL);
        buttonArea.setGravity(Gravity.CENTER);

        btnHit = new MaterialButton(this);
        btnHit.setText(getString(R.string.game_blackjack_btn_hit));
        btnHit.setBackgroundColor(ContextCompat.getColor(this, R.color.game_blackjack_color_btn_hit));
        btnHit.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams hitParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        hitParams.setMargins(8, 0, 8, 0);
        btnHit.setLayoutParams(hitParams);
        btnHit.setVisibility(View.GONE);
        btnHit.setOnClickListener(v -> onHit());

        btnStand = new MaterialButton(this);
        btnStand.setText(getString(R.string.game_blackjack_btn_stand));
        btnStand.setBackgroundColor(ContextCompat.getColor(this, R.color.game_blackjack_color_btn_stand));
        btnStand.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams standParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        standParams.setMargins(8, 0, 8, 0);
        btnStand.setLayoutParams(standParams);
        btnStand.setVisibility(View.GONE);
        btnStand.setOnClickListener(v -> onStand());

        btnNewGame = new MaterialButton(this);
        btnNewGame.setText(getString(R.string.game_blackjack_btn_new_game));
        btnNewGame.setBackgroundColor(ContextCompat.getColor(this, R.color.game_blackjack_color_text_secondary));
        btnNewGame.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams newGameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        newGameParams.setMargins(8, 0, 8, 0);
        btnNewGame.setLayoutParams(newGameParams);
        btnNewGame.setOnClickListener(v -> startNewRound());

        buttonArea.addView(btnHit);
        buttonArea.addView(btnStand);
        buttonArea.addView(btnNewGame);

        root.addView(tvStatus);
        root.addView(tvDealerLabel);
        root.addView(tvDealerHand);
        root.addView(tvDealerTotal);
        root.addView(tvPlayerLabel);
        root.addView(tvPlayerHand);
        root.addView(tvPlayerTotal);
        root.addView(tvStats);
        root.addView(buttonArea);

        return root;
    }

    // ==================== 游戏逻辑 ====================

    /**
     * 初始化牌组
     */
    private void initDeck() {
        deck.clear();
        for (String suit : SUITS) {
            for (String rank : RANKS) {
                deck.add(rank + suit);
            }
        }
        Collections.shuffle(deck);
    }

    /**
     * 抽一张牌
     */
    private String drawCard() {
        if (deck.isEmpty()) {
            initDeck();
        }
        return deck.remove(deck.size() - 1);
    }

    /**
     * 计算手牌点数
     */
    private int calculateHandValue(List<String> hand) {
        int value = 0;
        int aces = 0;

        for (String card : hand) {
            String rank = card.substring(0, card.length() - 1);
            if (rank.equals("A")) {
                value += 11;
                aces++;
            } else if (rank.equals("K") || rank.equals("Q") || rank.equals("J") || rank.equals("10")) {
                value += 10;
            } else {
                value += Integer.parseInt(rank);
            }
        }

        // A 可以算作 1
        while (value > 21 && aces > 0) {
            value -= 10;
            aces--;
        }

        return value;
    }

    /**
     * 开始新一局
     */
    private void startNewRound() {
        if (!isGameRunning) return;

        totalGames++;
        roundActive = true;
        playerHand.clear();
        dealerHand.clear();

        initDeck();

        // 各发两张牌
        playerHand.add(drawCard());
        playerHand.add(drawCard());
        dealerHand.add(drawCard());
        dealerHand.add(drawCard());

        playerScore = calculateHandValue(playerHand);
        dealerScore = calculateHandValue(dealerHand);

        // 显示手牌
        updateDisplay(true);

        // 检查是否是 Blackjack
        if (playerScore == 21) {
            onStand(); // 直接停牌
            return;
        }

        tvStatus.setText(getString(R.string.game_blackjack_status_your_turn));
        btnNewGame.setVisibility(View.GONE);
        btnHit.setVisibility(View.VISIBLE);
        btnStand.setVisibility(View.VISIBLE);
    }

    /**
     * 要牌
     */
    private void onHit() {
        if (!roundActive) return;

        playerHand.add(drawCard());
        playerScore = calculateHandValue(playerHand);
        updateDisplay(true);

        // 2026-08-23 P3：要牌反馈（爆牌时在 onRoundEnd 给出强反馈）
        if (feedback != null && playerScore <= 21) feedback.playClick();

        if (playerScore > 21) {
            // 爆牌
            onRoundEnd("bust");
        } else if (playerScore == 21) {
            onStand();
        }
    }

    /**
     * 停牌
     */
    private void onStand() {
        if (!roundActive) return;

        btnHit.setVisibility(View.GONE);
        btnStand.setVisibility(View.GONE);

        // 庄家要牌（小于17必须要）
        while (calculateHandValue(dealerHand) < 17) {
            dealerHand.add(drawCard());
        }
        dealerScore = calculateHandValue(dealerHand);

        updateDisplay(false);

        // 判断胜负
        if (dealerScore > 21) {
            onRoundEnd("dealer_bust");
        } else if (playerScore > dealerScore) {
            onRoundEnd("player_win");
        } else if (playerScore < dealerScore) {
            onRoundEnd("dealer_win");
        } else {
            onRoundEnd("tie");
        }
    }

    /**
     * 回合结束
     */
    private void onRoundEnd(String result) {
        roundActive = false;
        updateDisplay(false);

        // 2026-08-23 P3：回合结果反馈
        if (feedback != null) {
            switch (result) {
                case "player_win":
                case "dealer_bust":
                    feedback.feedbackWin();
                    break;
                case "bust":
                case "dealer_win":
                    feedback.feedbackLose();
                    break;
                default:
                    feedback.playNotice();
                    break;
            }
        }

        String resultText;
        switch (result) {
            case "bust":
                resultText = getString(R.string.game_blackjack_result_bust);
                dealerWins++;
                winStreak = 0;
                noLossStreak = 0;
                break;
            case "dealer_bust":
                resultText = getString(R.string.game_blackjack_result_dealer_bust);
                playerWins++;
                winStreak++;
                noLossStreak++;
                currentScore += 20;
                break;
            case "player_win":
                resultText = getString(R.string.game_blackjack_result_win, playerScore, dealerScore);
                playerWins++;
                winStreak++;
                noLossStreak++;
                currentScore += 20;
                break;
            case "dealer_win":
                resultText = getString(R.string.game_blackjack_result_dealer_win, dealerScore, playerScore);
                dealerWins++;
                winStreak = 0;
                noLossStreak = 0;
                break;
            case "tie":
                resultText = getString(R.string.game_blackjack_result_tie, playerScore);
                ties++;
                winStreak = 0;
                noLossStreak++;
                currentScore += 5;
                break;
            default:
                resultText = "";
        }

        // Blackjack 额外加分
        if (playerScore == 21 && playerHand.size() == 2 && !result.equals("bust")) {
            currentScore += 30;
            resultText += getString(R.string.game_blackjack_blackjack_bonus);
            checkAchievement("special", 1);
        }

        tvStatus.setText(resultText);
        updateScore(currentScore);
        tvStats.setText(getString(R.string.game_blackjack_stats_format, playerWins, dealerWins, ties));

        // 成就检查
        if (result.equals("player_win") || result.equals("dealer_bust")) {
            checkAchievement("win", playerWins);
        }
        checkAchievement("streak", winStreak);
        checkAchievement("rounds", totalGames);

        if (noLossStreak >= 5) {
            checkAchievement("special", 2);
        }

        usageStore.recordWin(getGameId());

        btnNewGame.setVisibility(View.VISIBLE);
    }

    /**
     * 更新显示
     */
    private void updateDisplay(boolean hideDealerSecond) {
        // 玩家手牌
        StringBuilder playerStr = new StringBuilder();
        for (String card : playerHand) {
            playerStr.append(card).append("  ");
        }
        tvPlayerHand.setText(playerStr.toString().trim());
        tvPlayerTotal.setText(getString(R.string.game_blackjack_points_format, calculateHandValue(playerHand)));

        // 庄家手牌
        if (hideDealerSecond && dealerHand.size() >= 2) {
            tvDealerHand.setText(dealerHand.get(0) + "  ??");
            tvDealerTotal.setText(getString(R.string.game_blackjack_points_hidden));
        } else {
            StringBuilder dealerStr = new StringBuilder();
            for (String card : dealerHand) {
                dealerStr.append(card).append("  ");
            }
            tvDealerHand.setText(dealerStr.toString().trim());
            tvDealerTotal.setText(getString(R.string.game_blackjack_points_format, calculateHandValue(dealerHand)));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 2026-08-23 P3：释放音效资源
        if (feedback != null) {
            feedback.release();
            feedback = null;
        }
    }
}
