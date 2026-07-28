package com.gamecenter.app.rock;

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

/**
 * 猜拳模块 Fragment。
 *
 * <p>从原 {@code RockActivity} 迁移而来。石头剪刀布人机对战，
 * 使用纯 Android widget，支持浅/深主题。</p>
 */
public class RockModuleFragment extends Fragment {

    private static final String GAME_ID = "rock";

    private final RockGame game = new RockGame();

    private TextView tvStatus;
    private TextView tvPlayerChoice;
    private TextView tvAiChoice;
    private TextView tvStats;
    private Button btnRock;
    private Button btnScissors;
    private Button btnPaper;
    private Button btnRestart;

    private GameUsageStore usageStore;
    private int currentScore = 0;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        boolean dark = isDarkTheme(ctx);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(dark ? 0xFF121212 : 0xFFFFF3E0);
        root.setPadding((int) (16 * dp), (int) (24 * dp), (int) (16 * dp), (int) (16 * dp));

        int textPrimary = dark ? 0xFFEEEEEE : 0xFF4E342E;
        int textSecondary = dark ? 0xFFBDBDBD : 0xFF795548;
        int btnColor = dark ? 0xFF424242 : 0xFFFFCC80;

        tvStatus = new TextView(ctx);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(20f);
        tvStatus.setTextColor(textPrimary);
        tvStatus.setPadding(0, (int) (16 * dp), 0, (int) (24 * dp));
        tvStatus.setText("选择你的手势");

        LinearLayout battleArea = new LinearLayout(ctx);
        battleArea.setOrientation(LinearLayout.HORIZONTAL);
        battleArea.setGravity(Gravity.CENTER);
        battleArea.setPadding(0, 0, 0, (int) (24 * dp));

        tvPlayerChoice = new TextView(ctx);
        tvPlayerChoice.setTextSize(48f);
        tvPlayerChoice.setGravity(Gravity.CENTER);
        tvPlayerChoice.setText("❓");
        tvPlayerChoice.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        TextView tvVs = new TextView(ctx);
        tvVs.setTextSize(24f);
        tvVs.setTextColor(textSecondary);
        tvVs.setGravity(Gravity.CENTER);
        tvVs.setText(" VS ");

        tvAiChoice = new TextView(ctx);
        tvAiChoice.setTextSize(48f);
        tvAiChoice.setGravity(Gravity.CENTER);
        tvAiChoice.setText("❓");
        tvAiChoice.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        battleArea.addView(tvPlayerChoice);
        battleArea.addView(tvVs);
        battleArea.addView(tvAiChoice);

        tvStats = new TextView(ctx);
        tvStats.setGravity(Gravity.CENTER);
        tvStats.setTextSize(14f);
        tvStats.setTextColor(textSecondary);
        tvStats.setPadding(0, (int) (8 * dp), 0, (int) (24 * dp));
        tvStats.setText("胜: 0 | 负: 0 | 平: 0");

        LinearLayout buttonArea = new LinearLayout(ctx);
        buttonArea.setOrientation(LinearLayout.HORIZONTAL);
        buttonArea.setGravity(Gravity.CENTER);
        buttonArea.setPadding(0, 0, 0, (int) (16 * dp));

        btnRock = createChoiceButton(ctx, "石头", RockGame.ROCK, btnColor, textPrimary, dp);
        btnScissors = createChoiceButton(ctx, "剪刀", RockGame.SCISSORS, btnColor, textPrimary, dp);
        btnPaper = createChoiceButton(ctx, "布", RockGame.PAPER, btnColor, textPrimary, dp);

        buttonArea.addView(btnRock);
        buttonArea.addView(btnScissors);
        buttonArea.addView(btnPaper);

        btnRestart = new Button(ctx);
        btnRestart.setText("下一局");
        btnRestart.setTextColor(Color.WHITE);
        btnRestart.setBackgroundColor(0xFFFF7043);
        btnRestart.setVisibility(View.GONE);
        btnRestart.setOnClickListener(v -> resetRound());

        root.addView(tvStatus);
        root.addView(battleArea);
        root.addView(tvStats);
        root.addView(buttonArea);
        root.addView(btnRestart);

        return root;
    }

    private Button createChoiceButton(Context ctx, String text, int choice, int bgColor, int textColor, float dp) {
        Button btn = new Button(ctx);
        btn.setText(text);
        btn.setTextSize(14f);
        btn.setBackgroundColor(bgColor);
        btn.setTextColor(textColor);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        params.setMargins((int) (8 * dp), 0, (int) (8 * dp), 0);
        btn.setLayoutParams(params);
        btn.setOnClickListener(v -> onPlayerChoice(choice));
        return btn;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        usageStore = new GameUsageStore(requireContext());
    }

    private void onPlayerChoice(int playerChoice) {
        int[] result = game.play(playerChoice);
        int aiChoice = result[0];
        int outcome = result[1];

        tvPlayerChoice.setText(RockGame.CHOICE_EMOJI[playerChoice]);
        tvAiChoice.setText(RockGame.CHOICE_EMOJI[aiChoice]);

        String resultText;
        if (outcome == RockGame.RESULT_DRAW) {
            resultText = "平局！都出了" + RockGame.CHOICE_NAMES[playerChoice];
        } else if (outcome == RockGame.RESULT_PLAYER_WIN) {
            resultText = "你赢了！" + RockGame.CHOICE_NAMES[playerChoice] + "克" + RockGame.CHOICE_NAMES[aiChoice];
            currentScore += 10;
            usageStore.recordWin(GAME_ID);
        } else {
            resultText = "你输了！" + RockGame.CHOICE_NAMES[aiChoice] + "克" + RockGame.CHOICE_NAMES[playerChoice];
            usageStore.recordLoss(GAME_ID);
        }

        tvStatus.setText(resultText);
        tvStats.setText("胜: " + game.getPlayerWins() + " | 负: " + game.getAiWins() + " | 平: " + game.getDraws());

        enableButtons(false);
        btnRestart.setVisibility(View.VISIBLE);
    }

    private void resetRound() {
        btnRestart.setVisibility(View.GONE);
        tvPlayerChoice.setText("❓");
        tvAiChoice.setText("❓");
        tvStatus.setText("选择你的手势");
        enableButtons(true);
    }

    private void enableButtons(boolean enabled) {
        btnRock.setEnabled(enabled);
        btnScissors.setEnabled(enabled);
        btnPaper.setEnabled(enabled);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    private boolean isDarkTheme(Context ctx) {
        int mode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }
}
