package com.gamecenter.app.tic;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import java.util.Random;

/**
 * 井字棋模块 Fragment。
 *
 * <p>从原 {@code TicTacToeActivity} 迁移而来。包含难度选择（简单/困难）、
 * 人机对战流程、AI 延迟落子。使用纯 Android widget 构建 UI，支持浅/深主题。</p>
 */
public class TicTacToeModuleFragment extends Fragment {

    private static final String GAME_ID = "tic";

    private TicTacToeView ticTacToeView;
    private TextView tvStatus;
    private TextView tvDifficultyLabel;
    private LinearLayout gamePanel;
    private LinearLayout menuPanel;

    private final TicTacToeGame game = new TicTacToeGame();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private GameUsageStore usageStore;
    private boolean gameStarted = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        boolean dark = isDarkTheme(ctx);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(dark ? 0xFF121212 : 0xFFF5F0E8);
        root.setPadding((int) (16 * dp), (int) (24 * dp), (int) (16 * dp), (int) (16 * dp));

        int textColor = dark ? 0xFFEEEEEE : 0xFF212121;
        int subColor = dark ? 0xFFBDBDBD : 0xFF5B8A72;

        tvStatus = new TextView(ctx);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(18f);
        tvStatus.setTextColor(textColor);
        tvStatus.setPadding(0, (int) (16 * dp), 0, (int) (8 * dp));
        tvStatus.setText("请选择难度");

        tvDifficultyLabel = new TextView(ctx);
        tvDifficultyLabel.setGravity(Gravity.CENTER);
        tvDifficultyLabel.setTextSize(14f);
        tvDifficultyLabel.setTextColor(subColor);
        tvDifficultyLabel.setPadding(0, (int) (8 * dp), 0, (int) (16 * dp));

        menuPanel = new LinearLayout(ctx);
        menuPanel.setOrientation(LinearLayout.HORIZONTAL);
        menuPanel.setGravity(Gravity.CENTER);

        Button btnEasy = new Button(ctx);
        btnEasy.setText("简单");
        LinearLayout.LayoutParams easyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        easyLp.setMargins((int) (8 * dp), 0, (int) (8 * dp), 0);
        btnEasy.setLayoutParams(easyLp);
        btnEasy.setOnClickListener(v -> startNewGame(0));

        Button btnMedium = new Button(ctx);
        btnMedium.setText("中等");
        LinearLayout.LayoutParams medLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        medLp.setMargins((int) (8 * dp), 0, (int) (8 * dp), 0);
        btnMedium.setLayoutParams(medLp);
        btnMedium.setOnClickListener(v -> startNewGame(1));

        Button btnHard = new Button(ctx);
        btnHard.setText("困难");
        btnHard.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        btnHard.setOnClickListener(v -> startNewGame(2));

        menuPanel.addView(btnEasy);
        menuPanel.addView(btnMedium);
        menuPanel.addView(btnHard);

        gamePanel = new LinearLayout(ctx);
        gamePanel.setOrientation(LinearLayout.VERTICAL);
        gamePanel.setGravity(Gravity.CENTER);
        gamePanel.setVisibility(View.GONE);

        ticTacToeView = new TicTacToeView(ctx);
        int boardSize = (int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.85);
        LinearLayout.LayoutParams boardLp = new LinearLayout.LayoutParams(boardSize, boardSize);
        ticTacToeView.setLayoutParams(boardLp);
        ticTacToeView.setOnCellClickListener(this::onCellClick);

        Button btnRestart = new Button(ctx);
        btnRestart.setText("返回菜单");
        LinearLayout.LayoutParams restartLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        restartLp.topMargin = (int) (16 * dp);
        btnRestart.setLayoutParams(restartLp);
        btnRestart.setOnClickListener(v -> showMenuPanel());

        gamePanel.addView(ticTacToeView);
        gamePanel.addView(btnRestart);

        root.addView(tvStatus);
        root.addView(tvDifficultyLabel);
        root.addView(menuPanel);
        root.addView(gamePanel);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        usageStore = new GameUsageStore(requireContext());
    }

    private void showMenuPanel() {
        menuPanel.setVisibility(View.VISIBLE);
        gamePanel.setVisibility(View.GONE);
        tvStatus.setText("请选择难度");
        tvDifficultyLabel.setText("");
        ticTacToeView.clearBoard();
        gameStarted = false;
    }

    private void startNewGame(int level) {
        game.reset(level);
        ticTacToeView.clearBoard();
        ticTacToeView.setBoard(game.getBoard());

        menuPanel.setVisibility(View.GONE);
        gamePanel.setVisibility(View.VISIBLE);
        tvDifficultyLabel.setText(level == 0 ? "简单" : (level == 1 ? "中等" : "困难"));
        tvStatus.setText("轮到你了");
        gameStarted = true;
    }

    private void onCellClick(int row, int col) {
        if (!gameStarted) return;
        if (!game.isPlayerTurn() || game.isGameOver()) return;

        if (!game.placePlayer(row, col)) return;
        ticTacToeView.setBoard(game.getBoard());

        if (game.isGameOver()) {
            handleGameEnd();
            return;
        }

        tvStatus.setText("AI 思考中...");
        handler.postDelayed(() -> {
            if (game.isGameOver()) return;
            game.aiMove();
            ticTacToeView.setBoard(game.getBoard());
            if (game.isGameOver()) {
                handleGameEnd();
            } else {
                tvStatus.setText("轮到你了");
            }
        }, 300 + random.nextInt(400));
    }

    private void handleGameEnd() {
        int[] line = game.getWinLine();
        if (line != null) {
            ticTacToeView.setWinLine(line[0], line[1], line[2], line[3]);
        }
        switch (game.getWinner()) {
            case TicTacToeGame.RESULT_PLAYER_WIN:
                tvStatus.setText("你赢了！");
                usageStore.recordWin(GAME_ID);
                break;
            case TicTacToeGame.RESULT_AI_WIN:
                tvStatus.setText("AI 获胜");
                usageStore.recordLoss(GAME_ID);
                break;
            case TicTacToeGame.RESULT_DRAW:
                tvStatus.setText("平局");
                break;
        }
        gameStarted = false;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);
    }

    private boolean isDarkTheme(Context ctx) {
        int mode = ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }
}
