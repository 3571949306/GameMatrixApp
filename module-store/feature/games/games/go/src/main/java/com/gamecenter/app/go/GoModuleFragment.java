package com.gamecenter.app.go;

import android.app.AlertDialog;
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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.R;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 围棋模块 Fragment。
 *
 * <p>将原 GoActivity 的 UI 与 AI 对弈逻辑迁移到 Fragment，
 * 使用纯 Android widget（不依赖 R.layout），支持浅色/深色主题。
 * AI 计算在后台线程执行，避免主线程卡顿。</p>
 */
public class GoModuleFragment extends Fragment {

    private static final String[] DIFFICULTY_NAMES = {"入门", "普通", "困难", "大师"};

    private GoGame game;
    private GoAI ai;
    private GoView goView;
    private TextView tvStatus;
    private TextView tvScore;
    private LinearLayout gamePanel;
    private LinearLayout menuPanel;
    private final java.util.List<Button> difficultyButtons = new java.util.ArrayList<>();

    private Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService aiExecutor;
    private volatile boolean aiThinking = false;
    private volatile long aiGeneration = 0;
    private int moveCount = 0;
    private long gameStartTime = 0;

    private int colorBg;
    private int colorStatusText;
    private int colorScoreText;
    private int colorLabelText;
    private int[] colorDiffActive;
    private int colorDiffInactive;
    private int colorButtonText;
    private int colorBtnBg;
    private int colorBtnText;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        game = new GoGame();
        ai = new GoAI();
        aiExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        applyThemeColors();

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(colorBg);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("围棋");
        tvTitle.setTextSize(28);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(colorStatusText);
        tvTitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        titleLp.gravity = Gravity.CENTER;
        titleLp.topMargin = (int) (16 * dp);
        tvTitle.setLayoutParams(titleLp);
        root.addView(tvTitle);

        tvStatus = new TextView(ctx);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(16f);
        tvStatus.setTextColor(colorStatusText);
        tvStatus.setPadding(0, (int) (24 * dp), 0, (int) (8 * dp));
        root.addView(tvStatus);

        tvScore = new TextView(ctx);
        tvScore.setGravity(Gravity.CENTER);
        tvScore.setTextSize(14f);
        tvScore.setTextColor(colorScoreText);
        tvScore.setPadding(0, (int) (4 * dp), 0, (int) (16 * dp));
        root.addView(tvScore);

        menuPanel = new LinearLayout(ctx);
        menuPanel.setOrientation(LinearLayout.VERTICAL);
        menuPanel.setGravity(Gravity.CENTER);
        addDifficultyButtonsTo(ctx, dp, menuPanel);

        Button btnStart = new Button(ctx);
        btnStart.setText("开始游戏");
        btnStart.setOnClickListener(v -> startNewGame());
        menuPanel.addView(btnStart);
        root.addView(menuPanel);

        gamePanel = new LinearLayout(ctx);
        gamePanel.setOrientation(LinearLayout.VERTICAL);
        gamePanel.setGravity(Gravity.CENTER);
        gamePanel.setVisibility(View.GONE);

        goView = new GoView(ctx);
        int viewWidth = (int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.9);
        goView.setLayoutParams(new FrameLayout.LayoutParams(viewWidth, viewWidth));
        goView.setOnCellClickListener(this::onCellClick);
        gamePanel.addView(goView);

        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setPadding(0, (int) (16 * dp), 0, 0);

        Button btnPass = new Button(ctx);
        btnPass.setText("停一手");
        btnPass.setOnClickListener(v -> passMove());

        Button btnResign = new Button(ctx);
        btnResign.setText("认输");
        btnResign.setOnClickListener(v -> resign());

        Button btnRestart = new Button(ctx);
        btnRestart.setText("重新开始");
        btnRestart.setOnClickListener(v -> showMenu());

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins((int) (12 * dp), 0, (int) (12 * dp), 0);
        btnPass.setLayoutParams(btnLp);
        btnResign.setLayoutParams(btnLp);
        btnRestart.setLayoutParams(btnLp);

        btnRow.addView(btnPass);
        btnRow.addView(btnResign);
        btnRow.addView(btnRestart);
        gamePanel.addView(btnRow);
        root.addView(gamePanel);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        applyWidgetColors();
        showMenu();
    }

    private void addDifficultyButtonsTo(Context ctx, float dp, LinearLayout parent) {
        TextView label = new TextView(ctx);
        label.setText("选择难度");
        label.setTextSize(13f);
        label.setTextColor(colorLabelText);
        label.setPadding(0, (int) (12 * dp), 0, (int) (6 * dp));
        parent.addView(label);

        LinearLayout grid = new LinearLayout(ctx);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setGravity(Gravity.CENTER);

        for (int row = 0; row < 2; row++) {
            LinearLayout rowLayout = new LinearLayout(ctx);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(Gravity.CENTER);
            rowLayout.setPadding(0, 0, 0, (int) (6 * dp));
            for (int col = 0; col < 2; col++) {
                int idx = row * 2 + col + 1;
                Button btn = new Button(ctx);
                btn.setText(DIFFICULTY_NAMES[idx - 1]);
                btn.setTextSize(12f);
                btn.setMinWidth(0);
                btn.setPadding((int) (24 * dp), (int) (8 * dp), (int) (24 * dp), (int) (8 * dp));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.setMargins((int) (6 * dp), 0, (int) (6 * dp), 0);
                btn.setLayoutParams(lp);
                btn.setOnClickListener(v -> setAiDifficulty(idx));
                rowLayout.addView(btn);
                difficultyButtons.add(btn);
            }
            grid.addView(rowLayout);
        }
        parent.addView(grid);
    }

    private void showMenu() {
        menuPanel.setVisibility(View.VISIBLE);
        gamePanel.setVisibility(View.GONE);
        tvStatus.setText("欢迎来到围棋");
        tvScore.setText("");
        if (goView != null) goView.hideTerritory();
    }

    private void startNewGame() {
        game.startNewGame();
        moveCount = 0;

        menuPanel.setVisibility(View.GONE);
        gamePanel.setVisibility(View.VISIBLE);
        tvStatus.setText("轮到你了");
        updateScoreDisplay();

        goView.hideTerritory();
        goView.setBoard(game.getBoard());

        gameStartTime = System.currentTimeMillis();
    }

    private void onCellClick(int row, int col) {
        if (game.isGameOver() || game.getCurrentPlayer() != GoGame.BLACK || aiThinking) return;

        if (game.playMove(row, col)) {
            moveCount++;
            goView.setBoard(game.getBoard());
            goView.setLastMove(row, col);
            updateScoreDisplay();
            startAiTurn();
        }
    }

    private void startAiTurn() {
        if (aiThinking) return;
        aiThinking = true;
        tvStatus.setText("AI 思考中（" + DIFFICULTY_NAMES[ai.getDifficulty() - 1] + "）");

        final long gen = ++aiGeneration;
        aiExecutor.execute(() -> {
            if (gen != aiGeneration) return;
            if (game.isGameOver()) {
                handler.post(() -> aiThinking = false);
                return;
            }
            int[] bestMove = ai.findBestAiMove(game);
            handler.post(() -> applyAiMove(bestMove, gen));
        });
    }

    private void applyAiMove(int[] bestMove, long gen) {
        if (gen != aiGeneration) return;
        if (game.isGameOver()) {
            aiThinking = false;
            return;
        }

        if (bestMove == null) {
            game.passMove();
            tvStatus.setText("AI 停一手");
        } else {
            game.playMove(bestMove[0], bestMove[1]);
            goView.setBoard(game.getBoard());
            goView.setLastMove(bestMove[0], bestMove[1]);
        }

        if (game.isGameOver()) {
            aiThinking = false;
            onGameEnd();
            return;
        }

        aiThinking = false;
        tvStatus.setText("轮到你了");
        updateScoreDisplay();
    }

    private void passMove() {
        if (game.isGameOver()) return;
        game.passMove();
        if (game.isGameOver()) {
            onGameEnd();
            return;
        }
        tvStatus.setText("AI 思考中");
        startAiTurn();
    }

    private void resign() {
        if (game.isGameOver()) return;
        game.setGameOver(true);
        tvStatus.setText("你认输了");
        int blackT = game.countTerritory(GoGame.BLACK) + game.getCapturedByBlack();
        int whiteT = game.countTerritory(GoGame.WHITE) + game.getCapturedByWhite() + (int) GoGame.KOMI;
        showGameEndDialog(false, blackT, whiteT);
    }

    private void onGameEnd() {
        float blackTerritory = game.countTerritory(GoGame.BLACK) + game.getCapturedByBlack();
        float whiteTerritory = game.countTerritory(GoGame.WHITE) + game.getCapturedByWhite() + GoGame.KOMI;

        float[][] territory = game.calculateTerritory();
        goView.showTerritory(territory);

        boolean playerWins = blackTerritory > whiteTerritory;

        if (playerWins) {
            tvStatus.setText("你赢了！黑 " + (int) blackTerritory + " : 白 " + (int) whiteTerritory);
        } else {
            tvStatus.setText("AI 赢了。黑 " + (int) blackTerritory + " : 白 " + (int) whiteTerritory);
        }

        showGameEndDialog(playerWins, (int) blackTerritory, (int) whiteTerritory);
    }

    private void showGameEndDialog(boolean playerWins, int blackTerritory, int whiteTerritory) {
        if (getContext() == null) return;
        long elapsed = gameStartTime > 0 ? (System.currentTimeMillis() - gameStartTime) : 0L;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getString(R.string.go_game_over));
        String winnerText = playerWins ? "你赢了！" : "AI 赢了";
        builder.setMessage(
                winnerText + "\n\n" +
                "手数: " + moveCount + "\n" +
                "用时: " + formatDuration(elapsed) + "\n" +
                "比分: 黑 " + blackTerritory + " : 白 " + whiteTerritory);
        builder.setPositiveButton(R.string.go_play_again, (d, w) -> startNewGame());
        builder.setNegativeButton(R.string.go_back_menu, (d, w) -> showMenu());
        builder.setCancelable(false);
        builder.show();
    }

    private String formatDuration(long ms) {
        long sec = ms / 1000L;
        return String.format(Locale.getDefault(), "%02d:%02d", sec / 60L, sec % 60L);
    }

    public void setAiDifficulty(int level) {
        ai.setDifficulty(level);
        updateDifficultyButtons();
        Toast.makeText(requireContext(), getString(R.string.go_difficulty_format, DIFFICULTY_NAMES[level - 1]), Toast.LENGTH_SHORT).show();
    }

    private void updateScoreDisplay() {
        tvScore.setText("提子 黑:" + game.getCapturedByBlack() + " 白:" + game.getCapturedByWhite()
                + "  当前: " + (game.getCurrentPlayer() == GoGame.BLACK ? "●" : "○"));
    }

    private void updateDifficultyButtons() {
        int level = ai.getDifficulty();
        for (int i = 0; i < difficultyButtons.size(); i++) {
            Button btn = difficultyButtons.get(i);
            if (i + 1 == level) {
                btn.setBackgroundColor(colorDiffActive[i]);
            } else {
                btn.setBackgroundColor(colorDiffInactive);
            }
            btn.setTextColor(colorButtonText);
        }
    }

    private void applyWidgetColors() {
        for (int i = 0; i < menuPanel.getChildCount(); i++) {
            View child = menuPanel.getChildAt(i);
            if (child instanceof Button) {
                child.setBackgroundColor(colorBtnBg);
                ((Button) child).setTextColor(colorBtnText);
            }
        }
        for (int i = 0; i < gamePanel.getChildCount(); i++) {
            View child = gamePanel.getChildAt(i);
            if (child instanceof LinearLayout) {
                LinearLayout row = (LinearLayout) child;
                for (int j = 0; j < row.getChildCount(); j++) {
                    View btn = row.getChildAt(j);
                    if (btn instanceof Button) {
                        btn.setBackgroundColor(colorBtnBg);
                        ((Button) btn).setTextColor(colorBtnText);
                    }
                }
            }
        }
        updateDifficultyButtons();
    }

    private void applyThemeColors() {
        boolean isDark = isNightMode();
        if (isDark) {
            colorBg = 0xFF1B1E22;
            colorStatusText = 0xFFE4E6F0;
            colorScoreText = 0xFF7DC79A;
            colorLabelText = 0xFF9AA0A6;
            colorDiffActive = new int[]{0xFF7DC79A, 0xFFFFB74D, 0xFFEF9A9A, 0xFFBA68C8};
            colorDiffInactive = 0xFF616161;
            colorButtonText = 0xFFFFFFFF;
            colorBtnBg = 0xFF2A2E3A;
            colorBtnText = 0xFFE4E6F0;
        } else {
            colorBg = 0xFFF5F0E8;
            colorStatusText = 0xFF2D2D2D;
            colorScoreText = 0xFF5B8A72;
            colorLabelText = 0xFF757575;
            colorDiffActive = new int[]{0xFF5B8A72, 0xFFFFA726, 0xFFEF5350, 0xFF8E24AA};
            colorDiffInactive = 0xFF9E9E9E;
            colorButtonText = 0xFFFFFFFF;
            colorBtnBg = 0xFF5B8A72;
            colorBtnText = 0xFFFFFFFF;
        }
    }

    private boolean isNightMode() {
        return (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        aiGeneration++;
        handler.removeCallbacksAndMessages(null);
        if (aiExecutor != null) {
            aiExecutor.shutdownNow();
        }
    }
}
