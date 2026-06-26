package com.gamecenter.app.games.go;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;

import androidx.annotation.NonNull;

import com.gamecenter.app.R;
import com.gamecenter.app.games.base.BaseGameActivity;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class GoActivity extends BaseGameActivity {

    private GoGame game;
    private GoAI ai;

    private int totalWins = 0;
    private int winStreak = 0;
    private int moveCount = 0;

    private Handler handler = new Handler(Looper.getMainLooper());

    private GoView goView;
    private TextView tvStatus;
    private TextView tvScore;
    private LinearLayout gamePanel;
    private LinearLayout menuPanel;

    private final List<MaterialButton> difficultyButtons = new ArrayList<>();
    private long aiThinkStartMs = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        game = new GoGame();
        ai = new GoAI();
    }

    @NonNull
    @Override
    protected String getGameId() {
        return "go";
    }

    @NonNull
    @Override
    protected String getGameName() {
        return getString(R.string.game_go_name);
    }

    @Override
    protected void initGame() {
        if (gameContentContainer instanceof FrameLayout) {
            View contentView = createGameContentView();
            ((FrameLayout) gameContentContainer).addView(contentView);
        }
    }

    private View createGameContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFFF5F0E8);

        tvStatus = new TextView(this);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(16f);
        tvStatus.setTextColor(0xFF2D2D2D);
        tvStatus.setPadding(0, 24, 0, 8);

        tvScore = new TextView(this);
        tvScore.setGravity(Gravity.CENTER);
        tvScore.setTextSize(14f);
        tvScore.setTextColor(0xFF5B8A72);
        tvScore.setPadding(0, 4, 0, 16);

        menuPanel = new LinearLayout(this);
        menuPanel.setOrientation(LinearLayout.VERTICAL);
        menuPanel.setGravity(Gravity.CENTER);

        addDifficultyButtonsTo(menuPanel);

        MaterialButton btnStart = new MaterialButton(this);
        btnStart.setText(R.string.game_go_start);
        btnStart.setBackgroundColor(0xFF5B8A72);
        btnStart.setOnClickListener(v -> startNewGame());
        menuPanel.addView(btnStart);

        gamePanel = new LinearLayout(this);
        gamePanel.setOrientation(LinearLayout.VERTICAL);
        gamePanel.setGravity(Gravity.CENTER);
        gamePanel.setVisibility(View.GONE);

        goView = new GoView(this);
        int viewWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
        goView.setLayoutParams(new FrameLayout.LayoutParams(viewWidth, viewWidth));
        goView.setOnCellClickListener(this::onCellClick);

        addDifficultyButtonsTo(gamePanel);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setPadding(0, 16, 0, 0);

        MaterialButton btnPass = new MaterialButton(this);
        btnPass.setText(R.string.game_go_pass);
        btnPass.setOnClickListener(v -> passMove());

        MaterialButton btnResign = new MaterialButton(this);
        btnResign.setText(R.string.game_go_resign);
        btnResign.setOnClickListener(v -> resign());

        MaterialButton btnRestart = new MaterialButton(this);
        btnRestart.setText(R.string.btn_restart);
        btnRestart.setOnClickListener(v -> showMenu());

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(12, 0, 12, 0);
        btnPass.setLayoutParams(btnLp);
        btnResign.setLayoutParams(btnLp);
        btnRestart.setLayoutParams(btnLp);

        btnRow.addView(btnPass);
        btnRow.addView(btnResign);
        btnRow.addView(btnRestart);

        gamePanel.addView(goView);
        gamePanel.addView(btnRow);

        root.addView(tvStatus);
        root.addView(tvScore);
        root.addView(menuPanel);
        root.addView(gamePanel);

        return root;
    }

    private void showMenu() {
        menuPanel.setVisibility(View.VISIBLE);
        gamePanel.setVisibility(View.GONE);
        tvStatus.setText(R.string.game_go_welcome);
        tvScore.setText("");
        goView.hideTerritory();
    }

    private void startNewGame() {
        game.startNewGame();
        moveCount = 0;

        menuPanel.setVisibility(View.GONE);
        gamePanel.setVisibility(View.VISIBLE);
        tvStatus.setText(R.string.game_go_your_turn);
        updateScoreDisplay();

        goView.hideTerritory();
        goView.setBoard(game.getBoard());

        isGameRunning = true;
        gameStartTime = System.currentTimeMillis();
    }

    private void onCellClick(int row, int col) {
        if (game.isGameOver() || !isGameRunning) return;
        if (game.getCurrentPlayer() != GoGame.BLACK) return;

        if (game.playMove(row, col)) {
            moveCount++;
            goView.setBoard(game.getBoard());
            goView.setLastMove(row, col);
            updateScoreDisplay();

            tvStatus.setText(getString(R.string.game_go_ai_thinking_with_difficulty, getDifficultyName(ai.getDifficulty())));
            aiThinkStartMs = System.currentTimeMillis();
            handler.postDelayed(this::aiMove, 300);
        }
    }

    private void aiMove() {
        if (game.isGameOver()) return;

        long thinkMs = System.currentTimeMillis() - aiThinkStartMs;
        Log.i("GoAI", "难度=" + ai.getDifficulty() + " 思考耗时=" + thinkMs + "ms");

        int[] bestMove = ai.findBestAiMove(game);
        if (bestMove == null) {
            game.passMove();
            tvStatus.setText(R.string.game_go_ai_passed);
        } else {
            game.playMove(bestMove[0], bestMove[1]);
            goView.setBoard(game.getBoard());
            goView.setLastMove(bestMove[0], bestMove[1]);

            if (ai.getDifficulty() >= 4 && thinkMs > 100) {
                Toast.makeText(this, "AI 思考 " + thinkMs + "ms", Toast.LENGTH_SHORT).show();
            }
        }

        if (game.isGameOver()) {
            onGameEnd();
            return;
        }

        tvStatus.setText(R.string.game_go_your_turn);
        updateScoreDisplay();
    }

    private void passMove() {
        if (game.isGameOver() || !isGameRunning) return;
        game.passMove();
        if (game.isGameOver()) {
            onGameEnd();
            return;
        }
        tvStatus.setText(R.string.game_go_ai_thinking);
        handler.postDelayed(this::aiMove, 300);
    }

    private void resign() {
        if (game.isGameOver()) return;
        game.setGameOver(true);
        isGameRunning = false;
        tvStatus.setText(R.string.game_go_you_resigned);
        winStreak = 0;
        usageStore.recordLoss(getGameId());
        if (gameStartTime > 0) {
            usageStore.recordPlayTime(getGameId(), System.currentTimeMillis() - gameStartTime);
        }
        
        int blackT = game.countTerritory(GoGame.BLACK) + game.getCapturedByBlack();
        int whiteT = game.countTerritory(GoGame.WHITE) + game.getCapturedByWhite() + (int) GoGame.KOMI;
        showGameEndDialog(false, blackT, whiteT);
    }

    private void onGameEnd() {
        isGameRunning = false;

        float blackTerritory = game.countTerritory(GoGame.BLACK) + game.getCapturedByBlack();
        float whiteTerritory = game.countTerritory(GoGame.WHITE) + game.getCapturedByWhite() + GoGame.KOMI;

        float[][] territory = game.calculateTerritory();
        goView.showTerritory(territory);

        boolean playerWins = blackTerritory > whiteTerritory;
        float blackPercent = blackTerritory / (GoGame.BOARD_SIZE * GoGame.BOARD_SIZE) * 100;

        if (playerWins) {
            totalWins++;
            winStreak++;
            tvStatus.setText(getString(R.string.game_go_you_win, (int) blackTerritory, (int) whiteTerritory));
            usageStore.recordWin(getGameId());

            checkAchievement("win", totalWins);
            checkAchievement("score", (int) blackPercent);
            checkAchievement("streak", winStreak);
            if (game.getCapturedByBlack() > 0) {
                checkAchievement("special", true);
            }
            if (ai.getDifficulty() == 4) {
                checkAchievement("master_win", 1);
                updateScore(currentScore + 500);
            } else {
                updateScore(currentScore + 300);
            }
        } else {
            winStreak = 0;
            tvStatus.setText(getString(R.string.game_go_ai_wins, (int) blackTerritory, (int) whiteTerritory));
            usageStore.recordLoss(getGameId());
        }

        if (gameStartTime > 0) {
            usageStore.recordPlayTime(getGameId(), System.currentTimeMillis() - gameStartTime);
        }

        showGameEndDialog(playerWins, (int) blackTerritory, (int) whiteTerritory);
    }

    private void showGameEndDialog(boolean playerWins, int blackTerritory, int whiteTerritory) {
        long elapsed = gameStartTime > 0 ? (System.currentTimeMillis() - gameStartTime) : 0L;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.game_go_end_title);
        String winnerText = playerWins ? getString(R.string.game_go_end_win) : getString(R.string.game_go_end_lose);
        builder.setMessage(
                winnerText + "\n\n" +
                getString(R.string.game_go_end_moves) + ": " + moveCount + "\n" +
                getString(R.string.game_go_end_duration) + ": " + formatDuration(elapsed) + "\n" +
                "黑方(你): " + blackTerritory + "  |  白方(AI): " + whiteTerritory);
        builder.setPositiveButton(R.string.game_go_end_restart, (d, w) -> startNewGame());
        builder.setNegativeButton(R.string.game_go_back_home, (d, w) -> finish());
        builder.setCancelable(false);
        builder.show();
    }

    private String formatDuration(long ms) {
        long sec = ms / 1000L;
        return String.format("%02d:%02d", sec / 60L, sec % 60L);
    }

    private void addDifficultyButtonsTo(LinearLayout parent) {
        TextView label = new TextView(this);
        label.setText(R.string.game_go_difficulty_label);
        label.setTextSize(13f);
        label.setTextColor(0xFF757575);
        label.setPadding(0, 12, 0, 6);
        parent.addView(label);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setGravity(Gravity.CENTER);

        String[] names = {
                getString(R.string.game_go_diff_1),
                getString(R.string.game_go_diff_2),
                getString(R.string.game_go_diff_3),
                getString(R.string.game_go_diff_4)
        };
        int[] colorActive = {0xFF5B8A72, 0xFFFFA726, 0xFFEF5350, 0xFF8E24AA};
        int colorInactive = 0xFF9E9E9E;

        for (int row = 0; row < 2; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER);
            rowLayout.setPadding(0, 0, 0, 6);
            for (int col = 0; col < 2; col++) {
                int idx = row * 2 + col + 1;
                MaterialButton btn = new MaterialButton(this);
                btn.setText(names[idx - 1]);
                btn.setTextSize(12f);
                btn.setBackgroundColor(idx == ai.getDifficulty() ? colorActive[idx - 1] : colorInactive);
                btn.setTextColor(0xFFFFFFFF);
                btn.setMinWidth(0);
                btn.setPadding(24, 8, 24, 8);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins(6, 0, 6, 0);
                btn.setLayoutParams(lp);
                btn.setOnClickListener(v -> setAiDifficulty(idx));
                rowLayout.addView(btn);
                difficultyButtons.add(btn);
            }
            grid.addView(rowLayout);
        }
        parent.addView(grid);
    }

    public void setAiDifficulty(int level) {
        ai.setDifficulty(level);
        int[] colorActive = {0xFF5B8A72, 0xFFFFA726, 0xFFEF5350, 0xFF8E24AA};
        int colorInactive = 0xFF9E9E9E;
        for (int i = 0; i < difficultyButtons.size(); i++) {
            difficultyButtons.get(i).setBackgroundColor(
                    i + 1 == level ? colorActive[i] : colorInactive);
        }
        Toast.makeText(this, "AI 难度: " + getDifficultyName(level), Toast.LENGTH_SHORT).show();
    }

    private String getDifficultyName(int level) {
        switch (level) {
            case 1: return "简单（随机）";
            case 2: return "普通（贪心）";
            case 3: return "困难（Minimax-2）";
            case 4: return "大师（MCTS）";
            default: return "未知";
        }
    }

    private void updateScoreDisplay() {
        tvScore.setText(getString(R.string.game_go_score_display,
                game.getCapturedByBlack(), game.getCapturedByWhite(), game.getCurrentPlayer() == GoGame.BLACK ? "●" : "○"));
    }

    @Override
    protected void startGame() {
        isGameRunning = true;
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
        handler.removeCallbacksAndMessages(null);
        if (gameStartTime > 0) {
            usageStore.recordPlayTime(getGameId(), System.currentTimeMillis() - gameStartTime);
        }
    }

    @Override
    protected void checkAchievement(@NonNull String eventType, @NonNull Object... params) {
        achievementManager.checkAndUnlock(eventType, params);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}
