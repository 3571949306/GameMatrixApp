package com.gamecenter.app.sudoku;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
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

/**
 * 数独模块 Fragment。
 *
 * <p>将原 SudokuActivity 的 UI 与逻辑迁移到 Fragment，
 * 使用纯 Android widget（不依赖 R.layout），支持浅色/深色主题。
 * 包含难度选择、数字键盘、提示、笔记模式与重新开始。</p>
 */
public class SudokuModuleFragment extends Fragment {

    private static final boolean NOTES_FEATURE_ENABLED = true;

    private SudokuView sudokuView;
    private SudokuGame game;
    private TextView tvStatus;
    private TextView tvDifficulty;
    private LinearLayout numPadPanel;
    private LinearLayout menuPanel;
    private LinearLayout gamePanel;
    private Button btnHint;
    private Button btnNotes;
    private boolean notesMode = false;
    private int selectedRow = -1;
    private int selectedCol = -1;
    private long gameStartTime = 0;

    private int colorBg;
    private int colorText;
    private int colorDifficulty;
    private int colorBtnNum;
    private int colorBtnNumText;
    private int colorBtnClear;
    private int colorBtnClearText;
    private int colorBtnNotes;
    private int colorBtnNotesText;
    private int colorBtnNotesOn;
    private int colorBtnNotesOnText;
    private int colorBtnBg;
    private int colorBtnText;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        applyThemeColors();

        game = new SudokuGame();

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(colorBg);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("数独");
        tvTitle.setTextSize(28);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(colorText);
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
        tvStatus.setTextColor(colorText);
        tvStatus.setPadding(0, (int) (24 * dp), 0, (int) (8 * dp));
        root.addView(tvStatus);

        tvDifficulty = new TextView(ctx);
        tvDifficulty.setGravity(Gravity.CENTER);
        tvDifficulty.setTextSize(14f);
        tvDifficulty.setTextColor(colorDifficulty);
        tvDifficulty.setPadding(0, (int) (4 * dp), 0, (int) (16 * dp));
        root.addView(tvDifficulty);

        menuPanel = new LinearLayout(ctx);
        menuPanel.setOrientation(LinearLayout.VERTICAL);
        menuPanel.setGravity(Gravity.CENTER);
        for (int i = 0; i < SudokuGame.DIFFICULTY_NAMES.length; i++) {
            final int idx = i;
            Button btn = new Button(ctx);
            btn.setText(SudokuGame.DIFFICULTY_NAMES[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    (int) (160 * dp), (int) (48 * dp));
            lp.setMargins(0, (int) (8 * dp), 0, (int) (8 * dp));
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> startGameWithDifficulty(idx));
            menuPanel.addView(btn);
        }
        root.addView(menuPanel);

        gamePanel = new LinearLayout(ctx);
        gamePanel.setOrientation(LinearLayout.VERTICAL);
        gamePanel.setGravity(Gravity.CENTER);
        gamePanel.setVisibility(View.GONE);

        sudokuView = new SudokuView(ctx);
        int viewWidth = (int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.92);
        sudokuView.setLayoutParams(new FrameLayout.LayoutParams(viewWidth, viewWidth));
        sudokuView.setOnCellSelectListener((row, col) -> {
            selectedRow = row;
            selectedCol = col;
        });
        gamePanel.addView(sudokuView);

        numPadPanel = new LinearLayout(ctx);
        numPadPanel.setOrientation(LinearLayout.HORIZONTAL);
        numPadPanel.setGravity(Gravity.CENTER);
        numPadPanel.setPadding(0, (int) (16 * dp), 0, (int) (8 * dp));

        int btnSize = ctx.getResources().getDisplayMetrics().widthPixels / 10;
        for (int n = 1; n <= 9; n++) {
            final int num = n;
            Button btn = new Button(ctx);
            btn.setText(String.valueOf(n));
            btn.setTextSize(18f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(btnSize, btnSize);
            lp.setMargins(4, 4, 4, 4);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> inputNumber(num));
            numPadPanel.addView(btn);
        }
        Button btnClear = new Button(ctx);
        btnClear.setText("✕");
        btnClear.setTextSize(18f);
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(btnSize, btnSize);
        clearLp.setMargins(4, 4, 4, 4);
        btnClear.setLayoutParams(clearLp);
        btnClear.setOnClickListener(v -> inputNumber(0));
        numPadPanel.addView(btnClear);
        gamePanel.addView(numPadPanel);

        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        btnHint = new Button(ctx);
        btnHint.setText("提示");
        btnHint.setOnClickListener(v -> showHint());

        Button btnRestart = new Button(ctx);
        btnRestart.setText("重新开始");
        btnRestart.setOnClickListener(v -> showMenu());

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins((int) (16 * dp), 0, (int) (16 * dp), 0);
        btnHint.setLayoutParams(btnLp);
        btnRestart.setLayoutParams(btnLp);

        btnRow.addView(btnHint);
        btnRow.addView(btnRestart);

        if (NOTES_FEATURE_ENABLED) {
            btnNotes = new Button(ctx);
            btnNotes.setText("笔记");
            LinearLayout.LayoutParams notesLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            notesLp.setMargins((int) (16 * dp), 0, (int) (16 * dp), 0);
            btnNotes.setLayoutParams(notesLp);
            btnNotes.setOnClickListener(v -> toggleNotesMode());
            btnRow.addView(btnNotes);
        }

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

    private void showMenu() {
        menuPanel.setVisibility(View.VISIBLE);
        gamePanel.setVisibility(View.GONE);
        tvStatus.setText("请选择难度");
        tvDifficulty.setText("");
    }

    private void startGameWithDifficulty(int difficultyIndex) {
        game.startNewGame(difficultyIndex);

        menuPanel.setVisibility(View.GONE);
        gamePanel.setVisibility(View.VISIBLE);
        tvDifficulty.setText(SudokuGame.DIFFICULTY_NAMES[difficultyIndex]);
        tvStatus.setText("游戏中");

        notesMode = false;
        updateNotesButton();

        sudokuView.setBoard(game.getBoard(), game.getIsGiven());
        gameStartTime = System.currentTimeMillis();
    }

    private void inputNumber(int num) {
        if (selectedRow < 0 || selectedCol < 0) return;

        if (NOTES_FEATURE_ENABLED && notesMode) {
            if (num == 0) {
                sudokuView.clearNotes(selectedRow, selectedCol);
            } else {
                sudokuView.toggleNote(selectedRow, selectedCol, num);
            }
            return;
        }

        boolean complete = game.inputNumber(selectedRow, selectedCol, num);
        boolean hasError = game.hasConflict(selectedRow, selectedCol, num);
        sudokuView.setError(selectedRow, selectedCol, hasError);
        sudokuView.updateCell(selectedRow, selectedCol, num);

        if (num != 0 && !hasError && NOTES_FEATURE_ENABLED) {
            sudokuView.clearNotes(selectedRow, selectedCol);
            sudokuView.removeNoteFromPeers(selectedRow, selectedCol, num);
        }

        if (complete) {
            onPuzzleSolved();
        }
    }

    private void showHint() {
        if (selectedRow < 0 || selectedCol < 0) return;
        boolean complete = game.useHint(selectedRow, selectedCol);
        sudokuView.updateCell(selectedRow, selectedCol, game.getBoard()[selectedRow][selectedCol]);
        sudokuView.setError(selectedRow, selectedCol, false);
        if (complete) {
            onPuzzleSolved();
        }
    }

    private void onPuzzleSolved() {
        long elapsedSec = (System.currentTimeMillis() - gameStartTime) / 1000;
        tvStatus.setText("恭喜完成！用时 " + elapsedSec + " 秒");
        Toast.makeText(requireContext(), R.string.game_sudoku_complete, Toast.LENGTH_SHORT).show();
    }

    private void toggleNotesMode() {
        notesMode = !notesMode;
        updateNotesButton();
    }

    private void updateNotesButton() {
        if (btnNotes == null) return;
        if (notesMode) {
            btnNotes.setText("笔记:开");
            btnNotes.setBackgroundColor(colorBtnNotesOn);
            btnNotes.setTextColor(colorBtnNotesOnText);
        } else {
            btnNotes.setText("笔记");
            btnNotes.setBackgroundColor(colorBtnNotes);
            btnNotes.setTextColor(colorBtnNotesText);
        }
    }

    private void applyWidgetColors() {
        for (int i = 0; i < numPadPanel.getChildCount(); i++) {
            View child = numPadPanel.getChildAt(i);
            if (child instanceof Button) {
                Button btn = (Button) child;
                if (btn.getText().toString().equals("✕")) {
                    btn.setBackgroundColor(colorBtnClear);
                    btn.setTextColor(colorBtnClearText);
                } else {
                    btn.setBackgroundColor(colorBtnNum);
                    btn.setTextColor(colorBtnNumText);
                }
            }
        }
        for (int i = 0; i < menuPanel.getChildCount(); i++) {
            View child = menuPanel.getChildAt(i);
            if (child instanceof Button) {
                child.setBackgroundColor(colorBtnBg);
                ((Button) child).setTextColor(colorBtnText);
            }
        }
        if (btnHint != null) {
            btnHint.setBackgroundColor(colorBtnBg);
            btnHint.setTextColor(colorBtnText);
        }
        View btnRow = gamePanel.getChildAt(gamePanel.getChildCount() - 1);
        if (btnRow instanceof LinearLayout) {
            for (int i = 0; i < ((LinearLayout) btnRow).getChildCount(); i++) {
                View child = ((LinearLayout) btnRow).getChildAt(i);
                if (child instanceof Button && child != btnNotes) {
                    child.setBackgroundColor(colorBtnBg);
                    ((Button) child).setTextColor(colorBtnText);
                }
            }
        }
        updateNotesButton();
    }

    private void applyThemeColors() {
        boolean isDark = isNightMode();
        if (isDark) {
            colorBg = 0xFF1E2220;
            colorText = 0xFFE4E6F0;
            colorDifficulty = 0xFF7DC79A;
            colorBtnNum = 0xFF2A2E3A;
            colorBtnNumText = 0xFFE4E6F0;
            colorBtnClear = 0xFF4A2A2A;
            colorBtnClearText = 0xFFEF9A9A;
            colorBtnNotes = 0xFF2A2E3A;
            colorBtnNotesText = 0xFFE4E6F0;
            colorBtnNotesOn = 0xFF5B8A72;
            colorBtnNotesOnText = 0xFFFFFFFF;
            colorBtnBg = 0xFF2A2E3A;
            colorBtnText = 0xFFE4E6F0;
        } else {
            colorBg = 0xFFF5F0E8;
            colorText = 0xFF2D2D2D;
            colorDifficulty = 0xFF5B8A72;
            colorBtnNum = 0xFFFBF9F6;
            colorBtnNumText = 0xFF2D2D2D;
            colorBtnClear = 0xFFFEE2E2;
            colorBtnClearText = 0xFFDC2626;
            colorBtnNotes = 0xFFE0E0E0;
            colorBtnNotesText = 0xFF2D2D2D;
            colorBtnNotesOn = 0xFF5B8A72;
            colorBtnNotesOnText = 0xFFFFFFFF;
            colorBtnBg = 0xFF5B8A72;
            colorBtnText = 0xFFFFFFFF;
        }
    }

    private boolean isNightMode() {
        return (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }
}
