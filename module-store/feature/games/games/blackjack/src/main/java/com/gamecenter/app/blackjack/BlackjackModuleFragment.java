package com.gamecenter.app.blackjack;

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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.games.GameUsageStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 21点游戏 Fragment（独立 APK 模块版本）。
 *
 * <p>由宿主 BlackjackActivity 迁移而来。使用纯 Android widget 构建 UI，
 * 不依赖宿主 R 资源，支持浅色/深色主题。仅保留基本游戏功能，不含成就系统。</p>
 */
public class BlackjackModuleFragment extends Fragment {

    private static final String GAME_ID = "blackjack";
    private static final String[] SUITS = {"♠", "♥", "♦", "♣"};
    private static final String[] RANKS = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};

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
    private int currentScore = 0;
    private boolean roundActive = false;
    private Random random = new Random();

    private TextView tvStatus;
    private TextView tvPlayerHand;
    private TextView tvPlayerTotal;
    private TextView tvDealerHand;
    private TextView tvDealerTotal;
    private TextView tvStats;
    private TextView tvHighScore;
    private Button btnHit;
    private Button btnStand;
    private Button btnNewGame;

    private GameUsageStore usageStore;
    private int highScore;

    // 主题色（在 onCreateView 中根据昼夜模式初始化）
    private int colorBg;
    private int colorTextPrimary;
    private int colorTextSecondary;
    private int colorDealerTotal;
    private int colorPlayerTotal;
    private int colorBtnHit;
    private int colorBtnStand;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        applyThemeColors();

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(colorBg);
        root.setPadding((int) (16 * dp), (int) (16 * dp), (int) (16 * dp), (int) (16 * dp));

        tvStatus = new TextView(ctx);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(18f);
        tvStatus.setTextColor(colorTextPrimary);
        tvStatus.setPadding(0, (int) (8 * dp), 0, (int) (8 * dp));
        tvStatus.setText("点击“新游戏”开始");
        root.addView(tvStatus);

        // 庄家区域
        TextView tvDealerLabel = new TextView(ctx);
        tvDealerLabel.setGravity(Gravity.CENTER);
        tvDealerLabel.setTextSize(16f);
        tvDealerLabel.setTextColor(colorTextSecondary);
        tvDealerLabel.setText("庄家");
        root.addView(tvDealerLabel);

        tvDealerHand = new TextView(ctx);
        tvDealerHand.setGravity(Gravity.CENTER);
        tvDealerHand.setTextSize(18f);
        tvDealerHand.setTextColor(colorTextPrimary);
        tvDealerHand.setPadding(0, (int) (8 * dp), 0, (int) (4 * dp));
        tvDealerHand.setText("");
        root.addView(tvDealerHand);

        tvDealerTotal = new TextView(ctx);
        tvDealerTotal.setGravity(Gravity.CENTER);
        tvDealerTotal.setTextSize(14f);
        tvDealerTotal.setTextColor(colorDealerTotal);
        tvDealerTotal.setPadding(0, 0, 0, (int) (24 * dp));
        tvDealerTotal.setText("");
        root.addView(tvDealerTotal);

        // 玩家区域
        TextView tvPlayerLabel = new TextView(ctx);
        tvPlayerLabel.setGravity(Gravity.CENTER);
        tvPlayerLabel.setTextSize(16f);
        tvPlayerLabel.setTextColor(colorTextSecondary);
        tvPlayerLabel.setText("玩家");
        root.addView(tvPlayerLabel);

        tvPlayerHand = new TextView(ctx);
        tvPlayerHand.setGravity(Gravity.CENTER);
        tvPlayerHand.setTextSize(18f);
        tvPlayerHand.setTextColor(colorTextPrimary);
        tvPlayerHand.setPadding(0, (int) (8 * dp), 0, (int) (4 * dp));
        tvPlayerHand.setText("");
        root.addView(tvPlayerHand);

        tvPlayerTotal = new TextView(ctx);
        tvPlayerTotal.setGravity(Gravity.CENTER);
        tvPlayerTotal.setTextSize(14f);
        tvPlayerTotal.setTextColor(colorPlayerTotal);
        tvPlayerTotal.setPadding(0, 0, 0, (int) (24 * dp));
        tvPlayerTotal.setText("");
        root.addView(tvPlayerTotal);

        // 统计
        tvStats = new TextView(ctx);
        tvStats.setGravity(Gravity.CENTER);
        tvStats.setTextSize(14f);
        tvStats.setTextColor(colorTextSecondary);
        tvStats.setPadding(0, (int) (8 * dp), 0, (int) (8 * dp));
        tvStats.setText("胜0 / 负0 / 平0");
        root.addView(tvStats);

        tvHighScore = new TextView(ctx);
        tvHighScore.setGravity(Gravity.CENTER);
        tvHighScore.setTextSize(14f);
        tvHighScore.setTextColor(colorTextSecondary);
        tvHighScore.setPadding(0, 0, 0, (int) (8 * dp));
        tvHighScore.setText("最高分: 0");
        root.addView(tvHighScore);

        // 按钮区域
        LinearLayout buttonArea = new LinearLayout(ctx);
        buttonArea.setOrientation(LinearLayout.HORIZONTAL);
        buttonArea.setGravity(Gravity.CENTER);

        btnHit = new Button(ctx);
        btnHit.setText("要牌");
        btnHit.setBackgroundColor(colorBtnHit);
        btnHit.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams hitParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        hitParams.setMargins((int) (8 * dp), 0, (int) (8 * dp), 0);
        btnHit.setLayoutParams(hitParams);
        btnHit.setVisibility(View.GONE);
        btnHit.setOnClickListener(v -> onHit());

        btnStand = new Button(ctx);
        btnStand.setText("停牌");
        btnStand.setBackgroundColor(colorBtnStand);
        btnStand.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams standParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        standParams.setMargins((int) (8 * dp), 0, (int) (8 * dp), 0);
        btnStand.setLayoutParams(standParams);
        btnStand.setVisibility(View.GONE);
        btnStand.setOnClickListener(v -> onStand());

        btnNewGame = new Button(ctx);
        btnNewGame.setText("新游戏");
        btnNewGame.setBackgroundColor(colorTextSecondary);
        btnNewGame.setTextColor(Color.WHITE);
        LinearLayout.LayoutParams newGameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        newGameParams.setMargins((int) (8 * dp), 0, (int) (8 * dp), 0);
        btnNewGame.setLayoutParams(newGameParams);
        btnNewGame.setOnClickListener(v -> startNewRound());

        buttonArea.addView(btnHit);
        buttonArea.addView(btnStand);
        buttonArea.addView(btnNewGame);
        root.addView(buttonArea);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context ctx = requireContext();
        usageStore = new GameUsageStore(ctx);
        highScore = usageStore.getHighScore(GAME_ID);
        if (tvHighScore != null) {
            tvHighScore.setText("最高分: " + highScore);
        }
    }

    private void applyThemeColors() {
        Context ctx = requireContext();
        int nightMode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isNight = nightMode == Configuration.UI_MODE_NIGHT_YES;
        if (isNight) {
            colorBg = 0xFF1A1D24;
            colorTextPrimary = 0xFFE4E6F0;
            colorTextSecondary = 0xFF7DAB94;
            colorDealerTotal = 0xFFEF5350;
            colorPlayerTotal = 0xFF66BB6A;
            colorBtnHit = 0xFF66BB6A;
            colorBtnStand = 0xFFEF5350;
        } else {
            colorBg = 0xFFF5F0E8;
            colorTextPrimary = 0xFF2D2D2D;
            colorTextSecondary = 0xFF5B8A72;
            colorDealerTotal = 0xFFE53935;
            colorPlayerTotal = 0xFF4CAF50;
            colorBtnHit = 0xFF4CAF50;
            colorBtnStand = 0xFFE53935;
        }
    }

    // ==================== 游戏逻辑 ====================

    private void initDeck() {
        deck.clear();
        for (String suit : SUITS) {
            for (String rank : RANKS) {
                deck.add(rank + suit);
            }
        }
        Collections.shuffle(deck);
    }

    private String drawCard() {
        if (deck.isEmpty()) {
            initDeck();
        }
        return deck.remove(deck.size() - 1);
    }

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
        while (value > 21 && aces > 0) {
            value -= 10;
            aces--;
        }
        return value;
    }

    private void startNewRound() {
        totalGames++;
        roundActive = true;
        playerHand.clear();
        dealerHand.clear();

        initDeck();

        playerHand.add(drawCard());
        playerHand.add(drawCard());
        dealerHand.add(drawCard());
        dealerHand.add(drawCard());

        playerScore = calculateHandValue(playerHand);
        dealerScore = calculateHandValue(dealerHand);

        updateDisplay(true);

        if (playerScore == 21) {
            onStand();
            return;
        }

        tvStatus.setText("轮到你了");
        btnNewGame.setVisibility(View.GONE);
        btnHit.setVisibility(View.VISIBLE);
        btnStand.setVisibility(View.VISIBLE);
    }

    private void onHit() {
        if (!roundActive) return;
        playerHand.add(drawCard());
        playerScore = calculateHandValue(playerHand);
        updateDisplay(true);

        if (playerScore > 21) {
            onRoundEnd("bust");
        } else if (playerScore == 21) {
            onStand();
        }
    }

    private void onStand() {
        if (!roundActive) return;
        btnHit.setVisibility(View.GONE);
        btnStand.setVisibility(View.GONE);

        while (calculateHandValue(dealerHand) < 17) {
            dealerHand.add(drawCard());
        }
        dealerScore = calculateHandValue(dealerHand);
        updateDisplay(false);

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

    private void onRoundEnd(String result) {
        roundActive = false;
        updateDisplay(false);

        String resultText;
        switch (result) {
            case "bust":
                resultText = "爆牌！你输了";
                dealerWins++;
                winStreak = 0;
                break;
            case "dealer_bust":
                resultText = "庄家爆牌！你赢了";
                playerWins++;
                winStreak++;
                currentScore += 20;
                break;
            case "player_win":
                resultText = "你赢了 " + playerScore + " vs " + dealerScore;
                playerWins++;
                winStreak++;
                currentScore += 20;
                break;
            case "dealer_win":
                resultText = "庄家赢 " + dealerScore + " vs " + playerScore;
                dealerWins++;
                winStreak = 0;
                break;
            case "tie":
                resultText = "平局 " + playerScore + " 点";
                ties++;
                winStreak = 0;
                currentScore += 5;
                break;
            default:
                resultText = "";
        }

        // Blackjack 额外加分
        if (playerScore == 21 && playerHand.size() == 2 && !result.equals("bust")) {
            currentScore += 30;
            resultText += "  Blackjack 加成！";
        }

        tvStatus.setText(resultText);
        if (currentScore > highScore) {
            highScore = currentScore;
        }
        if (usageStore != null) {
            usageStore.recordScore(GAME_ID, highScore);
            if (result.equals("player_win") || result.equals("dealer_bust")) {
                usageStore.recordWin(GAME_ID);
            }
        }
        if (tvHighScore != null) {
            tvHighScore.setText("最高分: " + highScore);
        }
        tvStats.setText("胜" + playerWins + " / 负" + dealerWins + " / 平" + ties);

        btnNewGame.setVisibility(View.VISIBLE);
    }

    private void updateDisplay(boolean hideDealerSecond) {
        StringBuilder playerStr = new StringBuilder();
        for (String card : playerHand) {
            playerStr.append(card).append("  ");
        }
        tvPlayerHand.setText(playerStr.toString().trim());
        tvPlayerTotal.setText("点数: " + calculateHandValue(playerHand));

        if (hideDealerSecond && dealerHand.size() >= 2) {
            tvDealerHand.setText(dealerHand.get(0) + "  ??");
            tvDealerTotal.setText("点数: ??");
        } else {
            StringBuilder dealerStr = new StringBuilder();
            for (String card : dealerHand) {
                dealerStr.append(card).append("  ");
            }
            tvDealerHand.setText(dealerStr.toString().trim());
            tvDealerTotal.setText("点数: " + calculateHandValue(dealerHand));
        }
    }
}
