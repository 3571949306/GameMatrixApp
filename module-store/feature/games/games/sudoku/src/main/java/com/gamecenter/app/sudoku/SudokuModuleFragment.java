package com.gamecenter.app.sudoku;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.R;

import java.util.Locale;

/**
 * 数独 2.0 游戏界面。
 *
 * <p>界面只负责展示 {@link SudokuGame.State} 和转发用户意图；规则、草稿、历史与存档均由
 * 模块内的纯逻辑层负责。布局使用宿主资源中的文案和颜色，适配动态模块资源加载失败时的降级路径。</p>
 */
public class SudokuModuleFragment extends Fragment {

    private static final int TOTAL_CELLS = SudokuGame.GRID_SIZE * SudokuGame.GRID_SIZE;

    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            if (!timerRunning) return;
            updateElapsed();
            updateTimerViews();
            timerHandler.postDelayed(this, 1000L);
        }
    };

    private SudokuGame game;
    private SudokuSaveManager saveManager;
    private SudokuView sudokuView;

    private LinearLayout menuPanel;
    private LinearLayout gamePanel;
    private LinearLayout resultPanel;
    private Button btnResume;
    private Button btnNotes;
    private Button btnUndo;
    private Button btnRedo;
    private Button btnHint;
    private Button btnClear;
    private Button[] numberButtons;

    private TextView tvMenuStatus;
    private TextView tvDifficulty;
    private TextView tvHeaderSubtitle;
    private TextView tvTimer;
    private TextView tvProgress;
    private TextView tvMistakes;
    private TextView tvHints;
    private TextView tvMessage;
    private TextView tvResultStats;

    private boolean notesMode;
    private long elapsedMs;
    private long timerAnchorMs;
    private boolean timerRunning;

    private int colorBg;
    private int colorSurface;
    private int colorBoard;
    private int colorText;
    private int colorSecondaryText;
    private int colorPrimary;
    private int colorPrimaryText;
    private int colorMutedButton;
    private int colorMutedButtonText;
    private int colorDangerButton;
    private int colorDangerButtonText;
    private int colorSelectedButton;
    private int colorSelectedButtonText;
    private int colorGrid;
    private int colorGridStrong;
    private int colorSelected;
    private int colorRelated;
    private int colorError;
    private int colorGiven;
    private int colorUser;
    private int colorErrorText;
    private int colorNote;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        applyThemeColors();
        saveManager = new SudokuSaveManager(context);
        game = new SudokuGame();

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(colorBg);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(12), dp(16), dp(24));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        menuPanel = new LinearLayout(context);
        menuPanel.setOrientation(LinearLayout.VERTICAL);
        buildMenu(context);
        content.addView(menuPanel, matchWidthWrapContent());

        gamePanel = new LinearLayout(context);
        gamePanel.setOrientation(LinearLayout.VERTICAL);
        buildGame(context);
        gamePanel.setVisibility(View.GONE);
        content.addView(gamePanel, matchWidthWrapContent());

        return scrollView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        showMenu();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isGameVisible() && game != null && !game.isBoardComplete()) startTimer();
    }

    @Override
    public void onPause() {
        stopTimer();
        saveCurrentGame();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        stopTimer();
        timerHandler.removeCallbacks(timerTick);
        saveCurrentGame();
        super.onDestroyView();
    }

    private void buildMenu(Context context) {
        TextView title = makeText(context, R.string.game_sudoku_name, 32, colorText);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        menuPanel.addView(title, matchWidthWrapContent());

        TextView subtitle = makeText(context, R.string.game_sudoku_menu_subtitle, 15,
                colorSecondaryText);
        subtitle.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitle.setPadding(0, dp(4), 0, dp(20));
        menuPanel.addView(subtitle, matchWidthWrapContent());

        btnResume = makeButton(context, "", colorPrimary, colorPrimaryText);
        btnResume.setTextSize(16);
        btnResume.setGravity(Gravity.CENTER);
        btnResume.setOnClickListener(v -> resumeSavedGame());
        menuPanel.addView(btnResume, matchWidthWrapContent());

        tvMenuStatus = makeText(context, R.string.game_sudoku_select_difficulty, 15,
                colorSecondaryText);
        tvMenuStatus.setGravity(Gravity.CENTER_HORIZONTAL);
        tvMenuStatus.setPadding(0, dp(18), 0, dp(10));
        menuPanel.addView(tvMenuStatus, matchWidthWrapContent());

        LinearLayout difficultyGrid = new LinearLayout(context);
        difficultyGrid.setOrientation(LinearLayout.VERTICAL);
        for (int row = 0; row < 2; row++) {
            LinearLayout difficultyRow = new LinearLayout(context);
            difficultyRow.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 2; col++) {
                int index = row * 2 + col;
                Button button = makeButton(context,
                        difficultyName(index) + "\n" + difficultyDescription(index),
                        colorMutedButton, colorMutedButtonText);
                button.setTextSize(15);
                button.setGravity(Gravity.CENTER);
                button.setMinHeight(dp(76));
                final int difficultyIndex = index;
                button.setOnClickListener(v -> startGameWithDifficulty(difficultyIndex));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(78), 1f);
                params.setMargins(col == 0 ? 0 : dp(6), dp(6), col == 1 ? 0 : dp(6), dp(6));
                difficultyRow.addView(button, params);
            }
            difficultyGrid.addView(difficultyRow, matchWidthWrapContent());
        }
        menuPanel.addView(difficultyGrid, matchWidthWrapContent());

        Button rules = makeButton(context, getString(R.string.game_sudoku_rules_button),
                colorMutedButton, colorMutedButtonText);
        rules.setOnClickListener(v -> showRules());
        LinearLayout.LayoutParams rulesParams = matchWidthWrapContent();
        rulesParams.topMargin = dp(12);
        menuPanel.addView(rules, rulesParams);
    }

    private void buildGame(Context context) {
        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);

        Button back = makeButton(context, getString(R.string.game_sudoku_back),
                colorMutedButton, colorMutedButtonText);
        back.setMinWidth(dp(62));
        back.setOnClickListener(v -> confirmLeaveToMenu());
        toolbar.addView(back, wrapContentParams());

        LinearLayout heading = new LinearLayout(context);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(10), 0, dp(8), 0);
        tvDifficulty = makeText(context, "", 20, colorText);
        tvDifficulty.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.addView(tvDifficulty, matchWidthWrapContent());
        tvHeaderSubtitle = makeText(context, R.string.game_sudoku_playing, 12,
                colorSecondaryText);
        heading.addView(tvHeaderSubtitle, matchWidthWrapContent());
        toolbar.addView(heading, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        tvTimer = makeText(context, formatElapsed(0), 17, colorPrimary);
        tvTimer.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tvTimer.setGravity(Gravity.CENTER);
        toolbar.addView(tvTimer, wrapContentParams());

        Button more = makeButton(context, "⋮", colorMutedButton, colorMutedButtonText);
        more.setContentDescription(getString(R.string.game_sudoku_more));
        more.setMinWidth(dp(48));
        more.setOnClickListener(v -> showMoreMenu());
        toolbar.addView(more, wrapContentParams());
        gamePanel.addView(toolbar, matchWidthWrapContent());

        LinearLayout stats = new LinearLayout(context);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setGravity(Gravity.CENTER);
        tvProgress = makeStatText(context);
        tvMistakes = makeStatText(context);
        tvHints = makeStatText(context);
        stats.addView(tvProgress, weightedItemParams());
        stats.addView(tvMistakes, weightedItemParams());
        stats.addView(tvHints, weightedItemParams());
        LinearLayout.LayoutParams statsParams = matchWidthWrapContent();
        statsParams.topMargin = dp(12);
        statsParams.bottomMargin = dp(10);
        gamePanel.addView(stats, statsParams);

        FrameLayout boardFrame = new FrameLayout(context);
        boardFrame.setBackgroundColor(colorSurface);
        sudokuView = new SudokuView(context);
        sudokuView.setColors(colorBoard, colorSurface, colorSelected, colorRelated, colorError,
                colorGrid, colorGridStrong, colorGiven, colorUser, colorErrorText, colorNote);
        sudokuView.setOnCellSelectListener((row, col) -> {
            updateNumberButtons();
            tvMessage.setText(game.isCellLocked(row, col)
                    ? getString(R.string.game_sudoku_a11y_locked)
                    : getString(R.string.game_sudoku_playing));
        });
        boardFrame.addView(sudokuView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        LinearLayout.LayoutParams boardParams = matchWidthWrapContent();
        boardParams.topMargin = dp(2);
        gamePanel.addView(boardFrame, boardParams);

        tvMessage = makeText(context, R.string.game_sudoku_playing, 14, colorSecondaryText);
        tvMessage.setGravity(Gravity.CENTER);
        tvMessage.setMinHeight(dp(40));
        tvMessage.setPadding(dp(8), dp(6), dp(8), dp(6));
        gamePanel.addView(tvMessage, matchWidthWrapContent());

        TextView padHint = makeText(context, R.string.game_sudoku_playing, 12,
                colorSecondaryText);
        padHint.setGravity(Gravity.CENTER_HORIZONTAL);
        gamePanel.addView(padHint, matchWidthWrapContent());

        numberButtons = new Button[SudokuGame.GRID_SIZE];
        for (int row = 0; row < 3; row++) {
            LinearLayout numberRow = new LinearLayout(context);
            numberRow.setOrientation(LinearLayout.HORIZONTAL);
            for (int col = 0; col < 3; col++) {
                int number = row * 3 + col + 1;
                Button button = makeButton(context, String.valueOf(number),
                        colorSurface, colorText);
                button.setTextSize(20);
                button.setMinHeight(dp(52));
                button.setOnClickListener(v -> inputNumber(number));
                numberButtons[number - 1] = button;
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(54), 1f);
                params.setMargins(col == 0 ? 0 : dp(4), dp(4), col == 2 ? 0 : dp(4), 0);
                numberRow.addView(button, params);
            }
            gamePanel.addView(numberRow, matchWidthWrapContent());
        }

        btnClear = makeButton(context, getString(R.string.game_sudoku_clear),
                colorDangerButton, colorDangerButtonText);
        btnClear.setOnClickListener(v -> inputNumber(0));
        LinearLayout.LayoutParams clearParams = matchWidthWrapContent();
        clearParams.topMargin = dp(4);
        gamePanel.addView(btnClear, clearParams);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        btnUndo = makeButton(context, getString(R.string.game_sudoku_undo),
                colorMutedButton, colorMutedButtonText);
        btnRedo = makeButton(context, getString(R.string.game_sudoku_redo),
                colorMutedButton, colorMutedButtonText);
        btnNotes = makeButton(context, getString(R.string.game_sudoku_notes_off),
                colorMutedButton, colorMutedButtonText);
        btnHint = makeButton(context, getString(R.string.game_sudoku_hint),
                colorPrimary, colorPrimaryText);
        btnUndo.setOnClickListener(v -> undoMove());
        btnRedo.setOnClickListener(v -> redoMove());
        btnNotes.setOnClickListener(v -> toggleNotesMode());
        btnHint.setOnClickListener(v -> showHintConfirmation());
        actions.addView(btnUndo, weightedActionParams());
        actions.addView(btnRedo, weightedActionParams());
        actions.addView(btnNotes, weightedActionParams());
        actions.addView(btnHint, weightedActionParams());
        LinearLayout.LayoutParams actionParams = matchWidthWrapContent();
        actionParams.topMargin = dp(8);
        gamePanel.addView(actions, actionParams);

        TextView autoSaved = makeText(context, R.string.game_sudoku_auto_saved, 12,
                colorSecondaryText);
        autoSaved.setGravity(Gravity.CENTER);
        autoSaved.setPadding(0, dp(12), 0, dp(8));
        gamePanel.addView(autoSaved, matchWidthWrapContent());

        buildResultPanel(context);
    }

    private void buildResultPanel(Context context) {
        resultPanel = new LinearLayout(context);
        resultPanel.setOrientation(LinearLayout.VERTICAL);
        resultPanel.setGravity(Gravity.CENTER_HORIZONTAL);
        resultPanel.setPadding(dp(16), dp(16), dp(16), dp(16));
        resultPanel.setBackground(roundBackground(colorSurface, dp(18)));

        TextView resultTitle = makeText(context, R.string.game_sudoku_result_title, 24, colorPrimary);
        resultTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        resultTitle.setGravity(Gravity.CENTER);
        resultPanel.addView(resultTitle, matchWidthWrapContent());

        tvResultStats = makeText(context, "", 15, colorSecondaryText);
        tvResultStats.setGravity(Gravity.CENTER);
        tvResultStats.setPadding(0, dp(8), 0, dp(14));
        resultPanel.addView(tvResultStats, matchWidthWrapContent());

        LinearLayout resultActions = new LinearLayout(context);
        resultActions.setOrientation(LinearLayout.HORIZONTAL);
        Button replay = makeButton(context, getString(R.string.game_sudoku_result_replay),
                colorPrimary, colorPrimaryText);
        Button choose = makeButton(context, getString(R.string.game_sudoku_result_choose_difficulty),
                colorMutedButton, colorMutedButtonText);
        replay.setOnClickListener(v -> startGameWithDifficulty(game.getCurrentDifficultyIndex()));
        choose.setOnClickListener(v -> showMenu());
        resultActions.addView(replay, weightedActionParams());
        resultActions.addView(choose, weightedActionParams());
        resultPanel.addView(resultActions, matchWidthWrapContent());

        resultPanel.setVisibility(View.GONE);
        LinearLayout.LayoutParams params = matchWidthWrapContent();
        params.topMargin = dp(10);
        gamePanel.addView(resultPanel, params);
    }

    private void showMenu() {
        stopTimer();
        saveCurrentGame();
        if (menuPanel == null || gamePanel == null) return;
        menuPanel.setVisibility(View.VISIBLE);
        gamePanel.setVisibility(View.GONE);
        refreshResumeButton();
    }

    private void refreshResumeButton() {
        if (btnResume == null || saveManager == null) return;
        SudokuSaveManager.SavedGame saved = saveManager.load();
        if (saved == null || saved.getState().getBoard() == null) {
            btnResume.setVisibility(View.GONE);
            return;
        }
        SudokuGame.State state = saved.getState();
        btnResume.setVisibility(View.VISIBLE);
        btnResume.setText(getString(R.string.game_sudoku_resume_game) + "\n"
                + getString(R.string.game_sudoku_resume_detail,
                difficultyName(state.getDifficultyIndex()), countRemaining(state.getBoard())));
    }

    private void startGameWithDifficulty(int difficultyIndex) {
        stopTimer();
        game = new SudokuGame();
        game.startNewGame(difficultyIndex);
        elapsedMs = 0L;
        notesMode = false;
        menuPanel.setVisibility(View.GONE);
        gamePanel.setVisibility(View.VISIBLE);
        resultPanel.setVisibility(View.GONE);
        sudokuView.setInteractionEnabled(true);
        sudokuView.clearSelection();
        tvDifficulty.setText(difficultyName(game.getCurrentDifficultyIndex()));
        tvHeaderSubtitle.setText(getString(R.string.game_sudoku_playing));
        tvMessage.setText(getString(R.string.game_sudoku_playing));
        renderGame();
        startTimer();
        saveCurrentGame();
    }

    private void resumeSavedGame() {
        SudokuSaveManager.SavedGame saved = saveManager.load();
        if (saved == null || !game.restoreState(saved.getState())) {
            saveManager.clear();
            refreshResumeButton();
            tvMenuStatus.setText(getString(R.string.game_sudoku_select_difficulty));
            return;
        }
        stopTimer();
        elapsedMs = saved.getElapsedMs();
        notesMode = false;
        menuPanel.setVisibility(View.GONE);
        gamePanel.setVisibility(View.VISIBLE);
        resultPanel.setVisibility(View.GONE);
        sudokuView.setInteractionEnabled(true);
        sudokuView.clearSelection();
        tvDifficulty.setText(difficultyName(game.getCurrentDifficultyIndex()));
        tvHeaderSubtitle.setText(getString(R.string.game_sudoku_playing));
        tvMessage.setText(getString(R.string.game_sudoku_saved));
        renderGame();
        if (game.isBoardComplete()) onPuzzleSolved();
        else startTimer();
    }

    private void renderGame() {
        if (game == null || sudokuView == null) return;
        sudokuView.render(game.getState(), game.getErrors());
        updateStats();
        updateNotesButton();
        updateNumberButtons();
    }

    private void inputNumber(int number) {
        if (game == null || sudokuView == null) return;
        int row = sudokuView.getSelectedRow();
        int col = sudokuView.getSelectedCol();
        if (row < 0 || col < 0) {
            tvMessage.setText(getString(R.string.game_sudoku_select_cell));
            return;
        }

        if (notesMode) {
            boolean changed;
            if (number == 0 && game.getValue(row, col) == 0) {
                changed = game.clearNotes(row, col);
            } else if (number == 0) {
                changed = game.setValue(row, col, 0) == SudokuGame.InputResult.CLEARED;
            } else {
                changed = game.toggleNote(row, col, number);
            }
            tvMessage.setText(changed ? getString(R.string.game_sudoku_note_added)
                    : getString(R.string.game_sudoku_select_cell));
            renderGame();
            saveCurrentGame();
            return;
        }

        SudokuGame.InputResult result = game.setValue(row, col, number);
        switch (result) {
            case CONFLICT:
                tvMessage.setText(getString(R.string.game_sudoku_conflict));
                break;
            case INCORRECT:
                tvMessage.setText(getString(R.string.game_sudoku_incorrect));
                break;
            case PLACED:
                tvMessage.setText(getString(R.string.game_sudoku_correct));
                break;
            case CLEARED:
                tvMessage.setText(getString(R.string.game_sudoku_playing));
                break;
            case IGNORED_GIVEN:
                tvMessage.setText(getString(R.string.game_sudoku_a11y_locked));
                break;
            default:
                break;
        }
        renderGame();
        if (result == SudokuGame.InputResult.COMPLETED) onPuzzleSolved();
        else saveCurrentGame();
    }

    private void showHintConfirmation() {
        int row = sudokuView.getSelectedRow();
        int col = sudokuView.getSelectedCol();
        if (row < 0 || col < 0) {
            tvMessage.setText(getString(R.string.game_sudoku_select_cell));
            return;
        }
        if (game.isCellLocked(row, col)
                || game.getValue(row, col) == game.getSolution()[row][col]) {
            tvMessage.setText(getString(R.string.game_sudoku_no_hint_available));
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.game_sudoku_hint_confirm_title)
                .setMessage(R.string.game_sudoku_hint_confirm_message)
                .setNegativeButton(R.string.game_sudoku_cancel, null)
                .setPositiveButton(R.string.game_sudoku_hint_confirm,
                        (dialog, which) -> useHint())
                .show();
    }

    private void useHint() {
        int row = sudokuView.getSelectedRow();
        int col = sudokuView.getSelectedCol();
        if (row < 0 || col < 0) return;
        boolean completed = game.useHint(row, col);
        tvMessage.setText(getString(R.string.game_sudoku_hint_used));
        renderGame();
        if (completed) onPuzzleSolved();
        else saveCurrentGame();
    }

    private void toggleNotesMode() {
        notesMode = !notesMode;
        updateNotesButton();
        tvMessage.setText(notesMode ? getString(R.string.game_sudoku_notes_on)
                : getString(R.string.game_sudoku_notes_off));
    }

    private void undoMove() {
        if (!game.undo()) {
            tvMessage.setText(getString(R.string.game_sudoku_playing));
            return;
        }
        notesMode = false;
        resultPanel.setVisibility(View.GONE);
        sudokuView.setInteractionEnabled(true);
        tvMessage.setText(getString(R.string.game_sudoku_undo));
        renderGame();
        saveCurrentGame();
    }

    private void redoMove() {
        if (!game.redo()) {
            tvMessage.setText(getString(R.string.game_sudoku_playing));
            return;
        }
        notesMode = false;
        tvMessage.setText(getString(R.string.game_sudoku_redo));
        renderGame();
        if (game.isBoardComplete()) onPuzzleSolved();
        else saveCurrentGame();
    }

    private void onPuzzleSolved() {
        stopTimer();
        updateStats();
        tvHeaderSubtitle.setText(getString(R.string.game_sudoku_result_title));
        tvMessage.setText(getString(R.string.game_sudoku_result_title));
        tvResultStats.setText(getString(R.string.game_sudoku_result_time, formatElapsed(elapsedMs))
                + "\n" + getString(R.string.game_sudoku_result_mistakes, game.getMistakes())
                + "\n" + getString(R.string.game_sudoku_result_hints, game.getHintsUsed()));
        resultPanel.setVisibility(View.VISIBLE);
        sudokuView.setInteractionEnabled(false);
        updateNumberButtons();
        saveManager.clear();
    }

    private void confirmLeaveToMenu() {
        if (game == null || !game.isStarted() || game.isBoardComplete()) {
            showMenu();
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.game_sudoku_menu_confirm_title)
                .setMessage(R.string.game_sudoku_menu_confirm_message)
                .setNegativeButton(R.string.game_sudoku_cancel, null)
                .setPositiveButton(R.string.game_sudoku_menu_confirm,
                        (dialog, which) -> showMenu())
                .show();
    }

    private void showMoreMenu() {
        CharSequence[] items = {
                getString(R.string.game_sudoku_restart),
                getString(R.string.game_sudoku_rules_button)
        };
        new AlertDialog.Builder(requireContext())
                .setItems(items, (dialog, which) -> {
                    if (which == 0) confirmRestart();
                    else showRules();
                })
                .show();
    }

    private void confirmRestart() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.game_sudoku_restart)
                .setMessage(R.string.game_sudoku_restart_message)
                .setNegativeButton(R.string.game_sudoku_cancel, null)
                .setPositiveButton(R.string.game_sudoku_restart_confirm,
                        (dialog, which) -> startGameWithDifficulty(game.getCurrentDifficultyIndex()))
                .show();
    }

    private void showRules() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.game_sudoku_rules_title)
                .setMessage(R.string.game_sudoku_rules_content)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void updateStats() {
        if (game == null) return;
        tvProgress.setText(getString(R.string.game_sudoku_stats_filled,
                game.getFilledCellCount(), TOTAL_CELLS));
        tvMistakes.setText(getString(R.string.game_sudoku_stats_mistakes, game.getMistakes()));
        tvHints.setText(getString(R.string.game_sudoku_stats_hints, game.getHintsUsed()));
        updateTimerViews();
    }

    private void updateTimerViews() {
        if (tvTimer != null) tvTimer.setText(formatElapsed(elapsedMs));
    }

    private void updateNotesButton() {
        if (btnNotes == null) return;
        if (notesMode) {
            btnNotes.setText(getString(R.string.game_sudoku_notes_on));
            tintButton(btnNotes, colorSelectedButton, colorSelectedButtonText);
        } else {
            btnNotes.setText(getString(R.string.game_sudoku_notes_off));
            tintButton(btnNotes, colorMutedButton, colorMutedButtonText);
        }
    }

    private void updateNumberButtons() {
        if (numberButtons == null || game == null || sudokuView == null) return;
        int row = sudokuView.getSelectedRow();
        int col = sudokuView.getSelectedCol();
        boolean active = game.isStarted() && !game.isBoardComplete()
                && row >= 0 && col >= 0 && !game.isCellLocked(row, col);
        for (int i = 0; i < numberButtons.length; i++) {
            boolean enabled = active && game.canPlace(row, col, i + 1);
            numberButtons[i].setEnabled(enabled);
            numberButtons[i].setAlpha(enabled ? 1f : 0.45f);
        }
        boolean canClear = active && (game.getValue(row, col) != 0 || game.getNoteMask(row, col) != 0);
        btnClear.setEnabled(canClear);
        btnClear.setAlpha(canClear ? 1f : 0.45f);
        btnUndo.setEnabled(game.canUndo() && !game.isBoardComplete());
        btnRedo.setEnabled(game.canRedo() && !game.isBoardComplete());
        btnUndo.setAlpha(btnUndo.isEnabled() ? 1f : 0.45f);
        btnRedo.setAlpha(btnRedo.isEnabled() ? 1f : 0.45f);
        boolean canEditNotes = active && game.getValue(row, col) == 0;
        btnNotes.setEnabled(canEditNotes);
        btnNotes.setAlpha(canEditNotes ? 1f : 0.45f);
        btnHint.setEnabled(active);
        btnHint.setAlpha(active ? 1f : 0.45f);
    }

    private void startTimer() {
        if (timerRunning || game == null || game.isBoardComplete()) return;
        timerRunning = true;
        timerAnchorMs = SystemClock.elapsedRealtime();
        timerHandler.removeCallbacks(timerTick);
        timerHandler.post(timerTick);
    }

    private void stopTimer() {
        if (!timerRunning) return;
        updateElapsed();
        timerRunning = false;
        timerHandler.removeCallbacks(timerTick);
    }

    private void updateElapsed() {
        if (!timerRunning) return;
        long now = SystemClock.elapsedRealtime();
        elapsedMs += Math.max(0L, now - timerAnchorMs);
        timerAnchorMs = now;
    }

    private void saveCurrentGame() {
        if (saveManager == null || game == null || !game.isStarted() || game.isBoardComplete()) return;
        updateElapsed();
        saveManager.save(game, elapsedMs);
    }

    private boolean isGameVisible() {
        return gamePanel != null && gamePanel.getVisibility() == View.VISIBLE;
    }

    private String difficultyName(int index) {
        switch (Math.max(0, Math.min(3, index))) {
            case 1:
                return getString(R.string.game_sudoku_diff_medium);
            case 2:
                return getString(R.string.game_sudoku_diff_hard);
            case 3:
                return getString(R.string.game_sudoku_diff_expert);
            default:
                return getString(R.string.game_sudoku_diff_easy);
        }
    }

    private String difficultyDescription(int index) {
        switch (Math.max(0, Math.min(3, index))) {
            case 1:
                return getString(R.string.game_sudoku_diff_medium_desc);
            case 2:
                return getString(R.string.game_sudoku_diff_hard_desc);
            case 3:
                return getString(R.string.game_sudoku_diff_expert_desc);
            default:
                return getString(R.string.game_sudoku_diff_easy_desc);
        }
    }

    private void applyThemeColors() {
        colorBg = getResources().getColor(R.color.game_sudoku_color_bg);
        colorSurface = getResources().getColor(R.color.game_sudoku_color_surface);
        colorBoard = getResources().getColor(R.color.game_sudoku_color_board);
        colorText = getResources().getColor(R.color.game_sudoku_color_text);
        colorSecondaryText = getResources().getColor(R.color.game_sudoku_color_text_secondary);
        colorPrimary = getResources().getColor(R.color.game_sudoku_color_button);
        colorPrimaryText = getResources().getColor(R.color.game_sudoku_color_button_text);
        colorMutedButton = getResources().getColor(R.color.game_sudoku_color_button_muted);
        colorMutedButtonText = getResources().getColor(R.color.game_sudoku_color_button_muted_text);
        colorDangerButton = getResources().getColor(R.color.game_sudoku_color_button_danger);
        colorDangerButtonText = getResources().getColor(R.color.game_sudoku_color_button_danger_text);
        colorSelectedButton = getResources().getColor(R.color.game_sudoku_color_button_selected);
        colorSelectedButtonText = getResources().getColor(R.color.game_sudoku_color_button_selected_text);
        colorGrid = getResources().getColor(R.color.game_sudoku_color_grid);
        colorGridStrong = getResources().getColor(R.color.game_sudoku_color_grid_strong);
        colorSelected = getResources().getColor(R.color.game_sudoku_color_selected);
        colorRelated = getResources().getColor(R.color.game_sudoku_color_related);
        colorError = getResources().getColor(R.color.game_sudoku_color_error);
        colorGiven = getResources().getColor(R.color.game_sudoku_color_given);
        colorUser = getResources().getColor(R.color.game_sudoku_color_user);
        colorErrorText = getResources().getColor(R.color.game_sudoku_color_error_text);
        colorNote = getResources().getColor(R.color.game_sudoku_color_note);
    }

    private TextView makeText(Context context, int textRes, float size, int color) {
        return makeText(context, getString(textRes), size, color);
    }

    private TextView makeText(Context context, String text, float size, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private TextView makeStatText(Context context) {
        TextView view = makeText(context, "", 12, colorSecondaryText);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(4), dp(8), dp(4), dp(8));
        view.setBackground(roundBackground(colorSurface, dp(12)));
        return view;
    }

    private Button makeButton(Context context, String text, int background, int textColor) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextColor(textColor);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setStateListAnimator(null);
        button.setPadding(dp(8), dp(4), dp(8), dp(4));
        tintButton(button, background, textColor);
        return button;
    }

    private void tintButton(Button button, int background, int textColor) {
        button.setBackground(roundBackground(background, dp(14)));
        button.setTextColor(textColor);
    }

    private GradientDrawable roundBackground(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private LinearLayout.LayoutParams matchWidthWrapContent() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrapContentParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weightedItemParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private LinearLayout.LayoutParams weightedActionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(50), 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private int countRemaining(int[][] board) {
        int remaining = 0;
        for (int[] row : board) {
            for (int value : row) if (value == 0) remaining++;
        }
        return remaining;
    }

    private String formatElapsed(long millis) {
        long totalSeconds = Math.max(0L, millis) / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes >= 60L) {
            long hours = minutes / 60L;
            minutes %= 60L;
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
