package com.gamecenter.app.checkers;

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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.R;

/**
 * 跳棋模块 Fragment。
 *
 * <p>将原 CheckersActivity 的 UI 与对弈逻辑迁移到 Fragment，
 * 使用纯 Android widget（不依赖 R.layout），支持浅色/深色主题。
 * AI 在主线程延迟 500ms 后执行落子。</p>
 */
public class CheckersModuleFragment extends Fragment {

    private CheckersView checkersView;
    private CheckersGame game;
    private TextView tvStatus;
    private TextView tvDifficulty;
    private LinearLayout gamePanel;
    private LinearLayout menuPanel;

    private int selectedRow = -1;
    private int selectedCol = -1;
    private boolean[][] validMoves = new boolean[CheckersGame.BOARD_SIZE][CheckersGame.BOARD_SIZE];

    private final Handler handler = new Handler(Looper.getMainLooper());

    private int colorBg;
    private int colorStatusText;
    private int colorDiffLabel;
    private int colorBtnBg;
    private int colorBtnText;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        applyThemeColors();

        game = new CheckersGame();

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(colorBg);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(getString(R.string.game_title_checkers));
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

        tvDifficulty = new TextView(ctx);
        tvDifficulty.setGravity(Gravity.CENTER);
        tvDifficulty.setTextSize(14f);
        tvDifficulty.setTextColor(colorDiffLabel);
        tvDifficulty.setPadding(0, (int) (4 * dp), 0, (int) (16 * dp));
        root.addView(tvDifficulty);

        menuPanel = new LinearLayout(ctx);
        menuPanel.setOrientation(LinearLayout.VERTICAL);
        menuPanel.setGravity(Gravity.CENTER);

        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        Button btnEasy = new Button(ctx);
        btnEasy.setText(getString(R.string.game_diff_easy));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins((int) (8 * dp), 0, (int) (8 * dp), 0);
        btnEasy.setLayoutParams(lp);
        btnEasy.setOnClickListener(v -> startNewGame(0));

        Button btnMedium = new Button(ctx);
        btnMedium.setText(getString(R.string.game_diff_medium));
        LinearLayout.LayoutParams lpMed = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpMed.setMargins((int) (8 * dp), 0, (int) (8 * dp), 0);
        btnMedium.setLayoutParams(lpMed);
        btnMedium.setOnClickListener(v -> startNewGame(1));

        Button btnHard = new Button(ctx);
        btnHard.setText(getString(R.string.game_diff_hard));
        btnHard.setOnClickListener(v -> startNewGame(2));

        btnRow.addView(btnEasy);
        btnRow.addView(btnMedium);
        btnRow.addView(btnHard);
        menuPanel.addView(btnRow);
        root.addView(menuPanel);

        gamePanel = new LinearLayout(ctx);
        gamePanel.setOrientation(LinearLayout.VERTICAL);
        gamePanel.setGravity(Gravity.CENTER);
        gamePanel.setVisibility(View.GONE);

        checkersView = new CheckersView(ctx);
        int viewWidth = (int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.9);
        checkersView.setLayoutParams(new FrameLayout.LayoutParams(viewWidth, viewWidth));
        checkersView.setOnCellClickListener(this::onCellClick);
        gamePanel.addView(checkersView);

        Button btnRestart = new Button(ctx);
        btnRestart.setText(getString(R.string.game_btn_restart));
        btnRestart.setOnClickListener(v -> showMenu());
        LinearLayout.LayoutParams restartLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        restartLp.topMargin = (int) (16 * dp);
        btnRestart.setLayoutParams(restartLp);
        gamePanel.addView(btnRestart);

        root.addView(gamePanel);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        applyWidgetColors();
        showMenu();
    }

    private void showMenu() {
        menuPanel.setVisibility(View.VISIBLE);
        gamePanel.setVisibility(View.GONE);
        tvStatus.setText(getString(R.string.game_checkers_status_pick));
        tvDifficulty.setText("");
    }

    private void startNewGame(int level) {
        game.startNewGame(level);

        selectedRow = -1;
        selectedCol = -1;

        menuPanel.setVisibility(View.GONE);
        gamePanel.setVisibility(View.VISIBLE);
        tvDifficulty.setText(level == 0
                ? getString(R.string.game_diff_easy)
                : (level == 1 ? getString(R.string.game_diff_medium) : getString(R.string.game_diff_hard)));
        tvStatus.setText(getString(R.string.game_your_turn));

        checkersView.setBoard(game.getBoard());
        checkersView.clearSelection();
    }

    private void onCellClick(int row, int col) {
        if (!game.isPlayerTurn() || game.isGameOver()) return;

        if (selectedRow >= 0) {
            if (validMoves[row][col]) {
                game.performPlayerMove(selectedRow, selectedCol, row, col);
                checkersView.setBoard(game.getBoard());
                checkersView.clearSelection();
                selectedRow = -1;
                selectedCol = -1;

                if (checkGameEnd()) return;

                game.setPlayerTurn(false);
                tvStatus.setText(getString(R.string.game_ai_turn));
                handler.postDelayed(this::aiMove, 500);
                return;
            }
        }

        int piece = game.getBoard()[row][col];
        if (piece == CheckersGame.BLACK || piece == CheckersGame.BLACK_KING) {
            selectedRow = row;
            selectedCol = col;
            checkersView.setSelected(row, col);
            validMoves = game.calculateValidMoves(row, col);
            checkersView.setValidMoves(validMoves);
        }
    }

    private void aiMove() {
        int[] move = game.getAiMove();
        if (move == null) {
            onPlayerWin();
            return;
        }

        game.performAiMove(move);
        checkersView.setBoard(game.getBoard());

        if (checkGameEnd()) return;

        game.setPlayerTurn(true);
        tvStatus.setText(getString(R.string.game_your_turn));
    }

    private boolean checkGameEnd() {
        int result = game.checkGameEnd();
        if (result == 1) {
            onPlayerWin();
            return true;
        } else if (result == -1) {
            onPlayerLose();
            return true;
        }
        return false;
    }

    private void onPlayerWin() {
        game.setGameOver(true);
        tvStatus.setText(getString(R.string.game_you_win));
    }

    private void onPlayerLose() {
        game.setGameOver(true);
        tvStatus.setText(getString(R.string.game_ai_wins));
    }

    private void applyWidgetColors() {
        for (int i = 0; i < menuPanel.getChildCount(); i++) {
            View child = menuPanel.getChildAt(i);
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
        for (int i = 0; i < gamePanel.getChildCount(); i++) {
            View child = gamePanel.getChildAt(i);
            if (child instanceof Button) {
                child.setBackgroundColor(colorBtnBg);
                ((Button) child).setTextColor(colorBtnText);
            }
        }
    }

    private void applyThemeColors() {
        boolean isDark = isNightMode();
        if (isDark) {
            colorBg = 0xFF1B1E22;
            colorStatusText = 0xFFE4E6F0;
            colorDiffLabel = 0xFF7DC79A;
            colorBtnBg = 0xFF2A2E3A;
            colorBtnText = 0xFFE4E6F0;
        } else {
            colorBg = 0xFFF5F0E8;
            colorStatusText = 0xFF2D2D2D;
            colorDiffLabel = 0xFF5B8A72;
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
        handler.removeCallbacksAndMessages(null);
    }
}
