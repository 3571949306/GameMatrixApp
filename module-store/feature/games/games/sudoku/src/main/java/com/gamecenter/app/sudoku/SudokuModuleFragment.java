package com.gamecenter.app.sudoku;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.R;

import java.util.List;
import java.util.Locale;

/**
 * 数独 2.0 现代化即开即玩游戏界面。
 *
 * <p>特性：
 * 1. 摒弃旧版阻断式菜单，直接进入战场；自动恢复未完成存档或即开新局；
 * 2. 顶部标准导航：返回、标题、可点选难度胶囊 [中等 ▾]、规则说明 ℹ、设置 ⚙；
 * 3. 现代化 Player HUD 看板：用时、错误计数 (0/3)、已填进度、提示统计与实时战况反馈；
 * 4. 现代化胶囊控制栏：↩ 撤销、↪ 重做、✏ 笔记（高亮状态）、🧹 擦除、💡 提示；
 * 5. 底部 1~9 现代数字键盘：实时展示每个数字剩余未填数量，填满自动打钩禁用；
 * 6. 终局结算弹窗与无缝难度切换；
 * 7. 完整兼容自定义谜题与编辑器入口。</p>
 */
public class SudokuModuleFragment extends Fragment {

    private static final int TOTAL_CELLS = SudokuGame.GRID_SIZE * SudokuGame.GRID_SIZE;
    private static final String[] DIFFICULTY_LABELS = {"简单", "中等", "困难", "专家"};

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

    /** 当前游玩的自定义谜题名（null = 内置难度局）。 */
    private String currentCustomPuzzleName;
    /** 当前自定义谜题的给定盘快照（重开用，null = 内置难度局）。 */
    private int[][] currentCustomPuzzleGrid;

    // 顶部与 HUD
    private TextView tvDifficultyCapsule;
    private TextView tvTimer;
    private TextView tvMistakes;
    private TextView tvProgress;
    private TextView tvHints;
    private TextView tvMessage;

    // 控制胶囊
    private LinearLayout btnUndo;
    private LinearLayout btnRedo;
    private LinearLayout btnNotes;
    private LinearLayout btnClear;
    private LinearLayout btnHint;
    private TextView tvNotesLabel;
    private TextView tvNotesIcon;

    // 数字键盘 1~9 (容器、大数字、剩余计数)
    private final LinearLayout[] numKeyContainers = new LinearLayout[9];
    private final TextView[] numKeyDigits = new TextView[9];
    private final TextView[] numKeyRemainings = new TextView[9];

    private boolean notesMode = false;
    private long elapsedMs = 0L;
    private long timerAnchorMs = 0L;
    private boolean timerRunning = false;
    private int currentDifficulty = 1; // 默认中等 (1)

    // 配色方案（支持日间/夜间模式）
    private boolean isNight;
    private int colorBg;
    private int colorSurface;
    private int colorBoard;
    private int colorTextPrimary;
    private int colorTextSecondary;
    private int colorAccent;
    private int colorAccentLight;
    private int colorDanger;
    private int colorDangerBg;
    private int colorSuccess;
    private int colorKeyBg;
    private int colorKeyText;
    private int colorGrid;
    private int colorGridStrong;
    private int colorSelected;
    private int colorRelated;
    private int colorGiven;
    private int colorUser;
    private int colorErrorText;
    private int colorNote;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        isNight = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        initThemeColors();

        saveManager = new SudokuSaveManager(context);
        game = new SudokuGame();

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(colorBg);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(8), dp(14), dp(16));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 1. 顶部标准导航栏
        View topBar = buildTopAppBar(context);
        content.addView(topBar, matchWidthWrapContent());

        // 2. 状态看板 HUD
        View hud = buildHud(context);
        LinearLayout.LayoutParams hudParams = matchWidthWrapContent();
        hudParams.topMargin = dp(6);
        hudParams.bottomMargin = dp(6);
        content.addView(hud, hudParams);

        // 3. 9x9 数独棋盘
        FrameLayout boardFrame = new FrameLayout(context);
        boardFrame.setBackground(roundBackground(colorSurface, dp(14)));
        boardFrame.setPadding(dp(4), dp(4), dp(4), dp(4));

        sudokuView = new SudokuView(context);
        sudokuView.setColors(colorBoard, colorSurface, colorSelected, colorRelated, colorDangerBg,
                colorGrid, colorGridStrong, colorGiven, colorUser, colorErrorText, colorNote);
        sudokuView.setOnCellSelectListener((row, col) -> {
            updateNumberButtons();
            updateControlCapsules();
            if (game != null) {
                if (game.isCellLocked(row, col)) {
                    tvMessage.setText("🔒 预设数字（不可修改）");
                } else if (game.getValue(row, col) != 0) {
                    tvMessage.setText("已填入 " + game.getValue(row, col) + "，点击数字可修改，或点击擦除");
                } else {
                    tvMessage.setText(notesMode ? "✏️ 草稿模式：点击数字记录笔记" : "✨ 点击下方数字落子");
                }
            }
        });
        boardFrame.addView(sudokuView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        LinearLayout.LayoutParams boardParams = matchWidthWrapContent();
        boardParams.topMargin = dp(2);
        boardParams.bottomMargin = dp(8);
        content.addView(boardFrame, boardParams);

        // 4. 现代化胶囊控制栏 (撤销, 重做, 笔记, 擦除, 提示)
        View controlBar = buildModernControlBar(context);
        content.addView(controlBar, matchWidthWrapContent());

        // 5. 现代化 1~9 数字键盘
        View numberPad = buildModernNumberPad(context);
        LinearLayout.LayoutParams padParams = matchWidthWrapContent();
        padParams.topMargin = dp(8);
        padParams.bottomMargin = dp(4);
        content.addView(numberPad, padParams);

        return scrollView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 注册系统返回拦截
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPress();
            }
        });

        // 即开即玩：优先恢复存档，若无则开新对局
        SudokuSaveManager.SavedGame saved = saveManager != null ? saveManager.load() : null;
        if (saved != null && saved.getState().getBoard() != null && game.restoreState(saved.getState())) {
            currentCustomPuzzleName = null;
            currentCustomPuzzleGrid = null;
            currentDifficulty = game.getCurrentDifficultyIndex();
            elapsedMs = saved.getElapsedMs();
            notesMode = false;
            sudokuView.setInteractionEnabled(true);
            sudokuView.clearSelection();
            renderGame();
            if (game.isBoardComplete()) {
                onPuzzleSolved();
            } else {
                startTimer();
                tvMessage.setText("已为您恢复上次未完成对局");
            }
        } else {
            startGameWithDifficulty(currentDifficulty);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (game != null && !game.isBoardComplete()) startTimer();
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

    // ==========================================
    // UI 构建逻辑
    // ==========================================

    private View buildTopAppBar(Context context) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(2), dp(2), dp(2), dp(2));

        // ❮ 返回按键
        TextView btnBack = new TextView(context);
        btnBack.setText("❮");
        btnBack.setTextSize(19);
        btnBack.setTextColor(colorTextPrimary);
        btnBack.setGravity(Gravity.CENTER);
        btnBack.setBackground(createCircleRipple(colorSurface, dp(38)));
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        btnBack.setLayoutParams(backLp);
        btnBack.setOnClickListener(v -> handleBackPress());
        bar.addView(btnBack);

        // 标题与难度胶囊
        LinearLayout centerTitleBox = new LinearLayout(context);
        centerTitleBox.setOrientation(LinearLayout.HORIZONTAL);
        centerTitleBox.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        centerTitleBox.setLayoutParams(centerLp);

        TextView tvTitle = new TextView(context);
        tvTitle.setText("数独");
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setTextColor(colorTextPrimary);
        tvTitle.setPadding(0, 0, dp(8), 0);
        centerTitleBox.addView(tvTitle);

        tvDifficultyCapsule = new TextView(context);
        tvDifficultyCapsule.setText(difficultyName(currentDifficulty) + " ▾");
        tvDifficultyCapsule.setTextSize(12);
        tvDifficultyCapsule.setTypeface(Typeface.DEFAULT_BOLD);
        tvDifficultyCapsule.setTextColor(colorAccent);
        tvDifficultyCapsule.setPadding(dp(10), dp(4), dp(10), dp(4));
        tvDifficultyCapsule.setBackground(roundBackground(colorAccentLight, dp(12)));
        tvDifficultyCapsule.setOnClickListener(v -> showDifficultySwitcherDialog());
        centerTitleBox.addView(tvDifficultyCapsule);

        bar.addView(centerTitleBox);

        // 规则说明 ℹ
        TextView btnRules = new TextView(context);
        btnRules.setText("ℹ");
        btnRules.setTextSize(18);
        btnRules.setTextColor(colorTextPrimary);
        btnRules.setGravity(Gravity.CENTER);
        btnRules.setBackground(createCircleRipple(colorSurface, dp(38)));
        LinearLayout.LayoutParams rulesLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        btnRules.setLayoutParams(rulesLp);
        btnRules.setOnClickListener(v -> showRulesDialog());
        bar.addView(btnRules);

        // 对局设置 ⚙
        TextView btnSettings = new TextView(context);
        btnSettings.setText("⚙");
        btnSettings.setTextSize(18);
        btnSettings.setTextColor(colorTextPrimary);
        btnSettings.setGravity(Gravity.CENTER);
        btnSettings.setBackground(createCircleRipple(colorSurface, dp(38)));
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(dp(38), dp(38));
        settingsLp.leftMargin = dp(4);
        btnSettings.setLayoutParams(settingsLp);
        btnSettings.setOnClickListener(v -> showSettingsDialog());
        bar.addView(btnSettings);

        return bar;
    }

    private View buildHud(Context context) {
        LinearLayout hudCard = new LinearLayout(context);
        hudCard.setOrientation(LinearLayout.VERTICAL);
        hudCard.setPadding(dp(12), dp(8), dp(12), dp(8));
        hudCard.setBackground(roundBackground(colorSurface, dp(12)));

        // 第一行：统计项目 (用时, 错误, 进度, 提示)
        LinearLayout statsRow = new LinearLayout(context);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);
        statsRow.setGravity(Gravity.CENTER_VERTICAL);

        tvTimer = makeHudStat(context, "⏱", "00:00");
        tvMistakes = makeHudStat(context, "❌", "0/3");
        tvProgress = makeHudStat(context, "🧩", "0/81");
        tvHints = makeHudStat(context, "💡", "0");

        statsRow.addView(wrapStat(tvTimer), weightedParams(1f));
        statsRow.addView(wrapStat(tvMistakes), weightedParams(1f));
        statsRow.addView(wrapStat(tvProgress), weightedParams(1f));
        statsRow.addView(wrapStat(tvHints), weightedParams(1f));

        hudCard.addView(statsRow, matchWidthWrapContent());

        // 第二行：实时动态状态胶囊
        tvMessage = new TextView(context);
        tvMessage.setTextSize(12);
        tvMessage.setTextColor(colorTextSecondary);
        tvMessage.setGravity(Gravity.CENTER);
        tvMessage.setPadding(dp(8), dp(4), dp(8), dp(2));
        tvMessage.setText("✨ 点击空格后输入数字");
        hudCard.addView(tvMessage, matchWidthWrapContent());

        return hudCard;
    }

    private TextView makeHudStat(Context context, String icon, String initialValue) {
        TextView tv = new TextView(context);
        tv.setText(icon + " " + initialValue);
        tv.setTextSize(13);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(colorTextPrimary);
        tv.setGravity(Gravity.CENTER);
        return tv;
    }

    private View wrapStat(TextView tv) {
        LinearLayout box = new LinearLayout(tv.getContext());
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER);
        box.addView(tv);
        return box;
    }

    private View buildModernControlBar(Context context) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);

        btnUndo = createActionCapsule(context, "↩", "撤销", v -> undoMove());
        btnRedo = createActionCapsule(context, "↪", "重做", v -> redoMove());
        btnNotes = createNotesActionCapsule(context, v -> toggleNotesMode());
        btnClear = createActionCapsule(context, "🧹", "擦除", v -> inputNumber(0));
        btnHint = createActionCapsule(context, "💡", "提示", v -> showHintConfirmation());

        bar.addView(btnUndo, weightedActionParams());
        bar.addView(btnRedo, weightedActionParams());
        bar.addView(btnNotes, weightedActionParams());
        bar.addView(btnClear, weightedActionParams());
        bar.addView(btnHint, weightedActionParams());

        return bar;
    }

    private LinearLayout createActionCapsule(Context context, String icon, String label,
                                             View.OnClickListener listener) {
        LinearLayout capsule = new LinearLayout(context);
        capsule.setOrientation(LinearLayout.VERTICAL);
        capsule.setGravity(Gravity.CENTER);
        capsule.setPadding(dp(4), dp(6), dp(4), dp(6));
        capsule.setBackground(roundBackground(colorSurface, dp(10)));
        capsule.setOnClickListener(listener);

        TextView tvIcon = new TextView(context);
        tvIcon.setText(icon);
        tvIcon.setTextSize(15);
        tvIcon.setTextColor(colorTextPrimary);
        tvIcon.setGravity(Gravity.CENTER);
        capsule.addView(tvIcon);

        TextView tvLabel = new TextView(context);
        tvLabel.setText(label);
        tvLabel.setTextSize(11);
        tvLabel.setTextColor(colorTextSecondary);
        tvLabel.setGravity(Gravity.CENTER);
        capsule.addView(tvLabel);

        return capsule;
    }

    private LinearLayout createNotesActionCapsule(Context context, View.OnClickListener listener) {
        LinearLayout capsule = new LinearLayout(context);
        capsule.setOrientation(LinearLayout.VERTICAL);
        capsule.setGravity(Gravity.CENTER);
        capsule.setPadding(dp(4), dp(6), dp(4), dp(6));
        capsule.setBackground(roundBackground(colorSurface, dp(10)));
        capsule.setOnClickListener(listener);

        tvNotesIcon = new TextView(context);
        tvNotesIcon.setText("✏");
        tvNotesIcon.setTextSize(15);
        tvNotesIcon.setTextColor(colorTextPrimary);
        tvNotesIcon.setGravity(Gravity.CENTER);
        capsule.addView(tvNotesIcon);

        tvNotesLabel = new TextView(context);
        tvNotesLabel.setText("笔记 关");
        tvNotesLabel.setTextSize(11);
        tvNotesLabel.setTextColor(colorTextSecondary);
        tvNotesLabel.setGravity(Gravity.CENTER);
        capsule.addView(tvNotesLabel);

        return capsule;
    }

    private View buildModernNumberPad(Context context) {
        LinearLayout pad = new LinearLayout(context);
        pad.setOrientation(LinearLayout.HORIZONTAL);
        pad.setGravity(Gravity.CENTER);

        for (int i = 0; i < 9; i++) {
            final int num = i + 1;
            LinearLayout key = new LinearLayout(context);
            key.setOrientation(LinearLayout.VERTICAL);
            key.setGravity(Gravity.CENTER);
            key.setPadding(dp(2), dp(4), dp(2), dp(4));
            key.setBackground(roundBackground(colorKeyBg, dp(10)));
            key.setOnClickListener(v -> inputNumber(num));

            TextView tvDigit = new TextView(context);
            tvDigit.setText(String.valueOf(num));
            tvDigit.setTextSize(19);
            tvDigit.setTypeface(Typeface.DEFAULT_BOLD);
            tvDigit.setTextColor(colorKeyText);
            tvDigit.setGravity(Gravity.CENTER);
            key.addView(tvDigit);

            TextView tvRemaining = new TextView(context);
            tvRemaining.setText("9");
            tvRemaining.setTextSize(10.5f);
            tvRemaining.setTextColor(colorTextSecondary);
            tvRemaining.setGravity(Gravity.CENTER);
            key.addView(tvRemaining);

            numKeyContainers[i] = key;
            numKeyDigits[i] = tvDigit;
            numKeyRemainings[i] = tvRemaining;

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(52), 1f);
            lp.setMargins(i == 0 ? 0 : dp(3), 0, i == 8 ? 0 : dp(3), 0);
            pad.addView(key, lp);
        }

        return pad;
    }

    // ==========================================
    // 游戏逻辑控制与状态同步
    // ==========================================

    private void startGameWithDifficulty(int difficultyIndex) {
        stopTimer();
        game = new SudokuGame();
        currentDifficulty = Math.max(0, Math.min(3, difficultyIndex));
        game.startNewGame(currentDifficulty);
        currentCustomPuzzleName = null;
        currentCustomPuzzleGrid = null;
        elapsedMs = 0L;
        notesMode = false;
        if (sudokuView != null) {
            sudokuView.setInteractionEnabled(true);
            sudokuView.clearSelection();
        }
        updateDifficultyCapsule();
        tvMessage.setText("✨ 点击空格落子");
        renderGame();
        startTimer();
        saveCurrentGame();
    }

    private void updateDifficultyCapsule() {
        if (tvDifficultyCapsule == null) return;
        if (currentCustomPuzzleName != null) {
            tvDifficultyCapsule.setText("自定义·" + currentCustomPuzzleName + " ▾");
        } else {
            tvDifficultyCapsule.setText(difficultyName(currentDifficulty) + " ▾");
        }
    }

    private void renderGame() {
        if (game == null || sudokuView == null) return;
        sudokuView.render(game.getState(), game.getErrors());
        updateStats();
        updateNotesCapsule();
        updateNumberButtons();
        updateControlCapsules();
    }

    private void inputNumber(int number) {
        if (game == null || sudokuView == null) return;
        int row = sudokuView.getSelectedRow();
        int col = sudokuView.getSelectedCol();
        if (row < 0 || col < 0) {
            tvMessage.setText("请先点击棋盘空格选中位置");
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
            tvMessage.setText(changed ? "✏️ 草稿笔记已更新" : "请先选中空格");
            renderGame();
            saveCurrentGame();
            return;
        }

        SudokuGame.InputResult result = game.setValue(row, col, number);
        switch (result) {
            case CONFLICT:
                tvMessage.setText("⚠️ 同行、列或九宫格内已有相同数字");
                break;
            case INCORRECT:
                tvMessage.setText("❌ 填入数字有误，错误次数 +1");
                break;
            case PLACED:
                tvMessage.setText("✓ 落子正确");
                break;
            case CLEARED:
                tvMessage.setText("已擦除该格");
                break;
            case IGNORED_GIVEN:
                tvMessage.setText("🔒 预设数字不能修改");
                break;
            default:
                break;
        }
        renderGame();
        if (result == SudokuGame.InputResult.COMPLETED) {
            onPuzzleSolved();
        } else {
            saveCurrentGame();
        }
    }

    private void toggleNotesMode() {
        notesMode = !notesMode;
        updateNotesCapsule();
        tvMessage.setText(notesMode ? "✏️ 笔记模式已开启，输入数字将标记为候选数"
                : "已切回正常输入模式");
    }

    private void updateNotesCapsule() {
        if (btnNotes == null || tvNotesIcon == null || tvNotesLabel == null) return;
        if (notesMode) {
            btnNotes.setBackground(roundBackground(colorAccent, dp(10)));
            tvNotesIcon.setTextColor(Color.WHITE);
            tvNotesLabel.setText("笔记 开");
            tvNotesLabel.setTextColor(Color.WHITE);
            tvNotesLabel.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            btnNotes.setBackground(roundBackground(colorSurface, dp(10)));
            tvNotesIcon.setTextColor(colorTextPrimary);
            tvNotesLabel.setText("笔记 关");
            tvNotesLabel.setTextColor(colorTextSecondary);
            tvNotesLabel.setTypeface(Typeface.DEFAULT);
        }
    }

    private void undoMove() {
        if (game == null || !game.undo()) {
            tvMessage.setText("无可撤销步骤");
            return;
        }
        tvMessage.setText("↩ 已撤销上一步");
        renderGame();
        saveCurrentGame();
    }

    private void redoMove() {
        if (game == null || !game.redo()) {
            tvMessage.setText("无可重做步骤");
            return;
        }
        tvMessage.setText("↪ 已重做一步");
        renderGame();
        if (game.isBoardComplete()) onPuzzleSolved();
        else saveCurrentGame();
    }

    private void showHintConfirmation() {
        if (game == null || sudokuView == null) return;
        int row = sudokuView.getSelectedRow();
        int col = sudokuView.getSelectedCol();

        // 若当前未选空格，智能寻找第一个未填或错误的空格
        if (row < 0 || col < 0 || game.isCellLocked(row, col)
                || game.getValue(row, col) == game.getSolution()[row][col]) {
            boolean found = false;
            int[][] board = game.getBoard();
            int[][] solution = game.getSolution();
            for (int r = 0; r < SudokuGame.GRID_SIZE && !found; r++) {
                for (int c = 0; c < SudokuGame.GRID_SIZE && !found; c++) {
                    if (!game.isCellLocked(r, c) && board[r][c] != solution[r][c]) {
                        row = r;
                        col = c;
                        sudokuView.setSelected(r, c);
                        found = true;
                    }
                }
            }
            if (!found) {
                tvMessage.setText("当前盘面已无需要提示的空格");
                return;
            }
        }

        final int targetRow = row;
        final int targetCol = col;
        new AlertDialog.Builder(requireContext())
                .setTitle("💡 智能落子提示")
                .setMessage("提示将在第 " + (targetRow + 1) + " 行、第 " + (targetCol + 1) + " 列直接填入正确解题数字，确定使用吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("使用提示", (dialog, which) -> {
                    boolean completed = game.useHint(targetRow, targetCol);
                    tvMessage.setText("💡 已填入第 " + (targetRow + 1) + " 行第 " + (targetCol + 1) + " 列正确数字");
                    renderGame();
                    if (completed) onPuzzleSolved();
                    else saveCurrentGame();
                })
                .show();
    }

    private void updateStats() {
        if (game == null) return;
        if (tvProgress != null) {
            tvProgress.setText("🧩 " + game.getFilledCellCount() + "/" + TOTAL_CELLS);
        }
        if (tvMistakes != null) {
            tvMistakes.setText("❌ " + game.getMistakes() + "/3");
        }
        if (tvHints != null) {
            tvHints.setText("💡 " + game.getHintsUsed());
        }
        updateTimerViews();
    }

    private void updateTimerViews() {
        if (tvTimer != null) {
            tvTimer.setText("⏱ " + formatElapsed(elapsedMs));
        }
    }

    private void updateControlCapsules() {
        if (game == null) return;
        boolean canUndo = game.canUndo() && !game.isBoardComplete();
        boolean canRedo = game.canRedo() && !game.isBoardComplete();
        if (btnUndo != null) {
            btnUndo.setEnabled(canUndo);
            btnUndo.setAlpha(canUndo ? 1f : 0.4f);
        }
        if (btnRedo != null) {
            btnRedo.setEnabled(canRedo);
            btnRedo.setAlpha(canRedo ? 1f : 0.4f);
        }
        int row = sudokuView != null ? sudokuView.getSelectedRow() : -1;
        int col = sudokuView != null ? sudokuView.getSelectedCol() : -1;
        boolean cellSelected = row >= 0 && col >= 0 && !game.isCellLocked(row, col);
        boolean canClear = cellSelected && (game.getValue(row, col) != 0 || game.getNoteMask(row, col) != 0);
        if (btnClear != null) {
            btnClear.setEnabled(canClear);
            btnClear.setAlpha(canClear ? 1f : 0.4f);
        }
    }

    private void updateNumberButtons() {
        if (game == null) return;
        int row = sudokuView != null ? sudokuView.getSelectedRow() : -1;
        int col = sudokuView != null ? sudokuView.getSelectedCol() : -1;
        boolean active = !game.isBoardComplete() && row >= 0 && col >= 0 && !game.isCellLocked(row, col);

        for (int i = 0; i < 9; i++) {
            int digit = i + 1;
            int remaining = getRemainingCountForDigit(digit);
            LinearLayout container = numKeyContainers[i];
            TextView tvRemaining = numKeyRemainings[i];
            TextView tvDigit = numKeyDigits[i];
            if (container == null || tvRemaining == null || tvDigit == null) continue;

            if (remaining <= 0) {
                tvRemaining.setText("✓");
                tvRemaining.setTextColor(colorSuccess);
                container.setEnabled(false);
                container.setAlpha(0.35f);
            } else {
                tvRemaining.setText(String.valueOf(remaining));
                tvRemaining.setTextColor(colorTextSecondary);
                boolean canPlace = active && (notesMode || game.canPlace(row, col, digit));
                container.setEnabled(canPlace);
                container.setAlpha(canPlace ? 1f : 0.5f);
            }
        }
    }

    private int getRemainingCountForDigit(int digit) {
        if (game == null) return 9;
        int count = 0;
        int[][] board = game.getBoard();
        for (int r = 0; r < SudokuGame.GRID_SIZE; r++) {
            for (int c = 0; c < SudokuGame.GRID_SIZE; c++) {
                if (board[r][c] == digit) count++;
            }
        }
        return Math.max(0, 9 - count);
    }

    private void onPuzzleSolved() {
        stopTimer();
        updateStats();
        boolean custom = game.isCustomPuzzle();
        String puzzleTitle = custom
                ? "自定义谜题《" + (currentCustomPuzzleName == null ? "" : currentCustomPuzzleName) + "》"
                : difficultyName(currentDifficulty) + "数独";

        if (sudokuView != null) sudokuView.setInteractionEnabled(false);
        updateNumberButtons();
        if (!custom && saveManager != null) saveManager.clear();

        new AlertDialog.Builder(requireContext())
                .setTitle("🎉 恭喜通关！")
                .setMessage("太棒了！您成功解开了这道" + puzzleTitle + "！\n\n" +
                        "• 通关用时: " + formatElapsed(elapsedMs) + "\n" +
                        "• 错误次数: " + game.getMistakes() + " 次\n" +
                        "• 使用提示: " + game.getHintsUsed() + " 次\n" +
                        "• 挑战难度: " + (custom ? "自定义" : difficultyName(currentDifficulty)))
                .setCancelable(false)
                .setPositiveButton("再来一局", (dialog, which) -> restartCurrentGame())
                .setNegativeButton("返回大厅", (dialog, which) -> {
                    if (getActivity() != null) getActivity().finish();
                })
                .show();
    }

    // ==========================================
    // 弹窗与设置
    // ==========================================

    private void showDifficultySwitcherDialog() {
        Context ctx = requireContext();
        float dp = ctx.getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (18 * dp), (int) (12 * dp), (int) (18 * dp), (int) (12 * dp));

        TextView tvHeader = new TextView(ctx);
        tvHeader.setText("切换数独难度");
        tvHeader.setTextSize(16);
        tvHeader.setTypeface(Typeface.DEFAULT_BOLD);
        tvHeader.setTextColor(colorTextPrimary);
        tvHeader.setPadding(0, 0, 0, (int) (10 * dp));
        root.addView(tvHeader);

        AlertDialog[] dialogHolder = new AlertDialog[1];

        // 4 档内置难度选择
        for (int i = 0; i < 4; i++) {
            final int index = i;
            Button b = new Button(ctx);
            b.setStateListAnimator(null);
            b.setText(DIFFICULTY_LABELS[i] + " (" + difficultyDescription(i) + ")");
            b.setTextSize(13);
            boolean isCur = (currentCustomPuzzleName == null && currentDifficulty == index);

            GradientDrawable bBg = new GradientDrawable();
            bBg.setCornerRadius(8 * dp);
            bBg.setColor(isCur ? colorAccent : colorSurface);
            b.setBackground(bBg);
            b.setTextColor(isCur ? Color.WHITE : colorTextPrimary);

            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (int) (44 * dp));
            bLp.bottomMargin = (int) (6 * dp);
            b.setLayoutParams(bLp);

            b.setOnClickListener(v -> {
                if (dialogHolder[0] != null) dialogHolder[0].dismiss();
                if (currentDifficulty != index || currentCustomPuzzleName != null) {
                    startGameWithDifficulty(index);
                    Toast.makeText(ctx, "已开局：" + DIFFICULTY_LABELS[index], Toast.LENGTH_SHORT).show();
                }
            });
            root.addView(b);
        }

        // 自定义谜题行
        LinearLayout customRow = new LinearLayout(ctx);
        customRow.setOrientation(LinearLayout.HORIZONTAL);
        customRow.setPadding(0, (int) (6 * dp), 0, 0);

        Button btnCustom = new Button(ctx);
        btnCustom.setStateListAnimator(null);
        btnCustom.setText("自定义谜题");
        btnCustom.setTextSize(13);
        btnCustom.setBackground(roundBackground(colorSurface, (int) (8 * dp)));
        btnCustom.setTextColor(colorTextPrimary);
        btnCustom.setOnClickListener(v -> {
            if (dialogHolder[0] != null) dialogHolder[0].dismiss();
            showCustomPuzzleList();
        });
        customRow.addView(btnCustom, weightedParams(1f));

        Button btnNewPuzzle = new Button(ctx);
        btnNewPuzzle.setStateListAnimator(null);
        btnNewPuzzle.setText("＋ 新建谜题");
        btnNewPuzzle.setTextSize(13);
        btnNewPuzzle.setBackground(roundBackground(colorSurface, (int) (8 * dp)));
        btnNewPuzzle.setTextColor(colorTextPrimary);
        btnNewPuzzle.setOnClickListener(v -> {
            if (dialogHolder[0] != null) dialogHolder[0].dismiss();
            openEditor();
        });
        customRow.addView(btnNewPuzzle, weightedParams(1f));

        root.addView(customRow, matchWidthWrapContent());

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setView(root)
                .setNegativeButton("取消", null)
                .create();
        dialogHolder[0] = dialog;
        dialog.show();
    }

    private void showSettingsDialog() {
        CharSequence[] items = {
                "重新开始本局",
                "生成新对局",
                "自定义谜题库",
                "数独解题规则说明"
        };
        new AlertDialog.Builder(requireContext())
                .setTitle("⚙ 数独设置")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            confirmRestart();
                            break;
                        case 1:
                            startGameWithDifficulty(currentDifficulty);
                            break;
                        case 2:
                            showCustomPuzzleList();
                            break;
                        case 3:
                            showRulesDialog();
                            break;
                        default:
                            break;
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmRestart() {
        new AlertDialog.Builder(requireContext())
                .setTitle("重新开始")
                .setMessage("确定要清除当前盘面的填入与草稿，从头开始解这道谜题吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("重新开始", (dialog, which) -> restartCurrentGame())
                .show();
    }

    private void restartCurrentGame() {
        if (game != null && game.isCustomPuzzle()) {
            if (currentCustomPuzzleGrid == null
                    || !startCustomGame(currentCustomPuzzleGrid, currentCustomPuzzleName)) {
                Toast.makeText(requireContext(), "谜题数据无效", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        startGameWithDifficulty(currentDifficulty);
    }

    private void showRulesDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle("数独规则与技巧")
                .setMessage(
                        "• 基本规则：\n" +
                        "在 9×9 网格中填入数字 1~9，使每行、每列以及每个 3×3 粗线宫格内的数字均不重复（各出现且仅出现一次）。\n\n" +
                        "• 进阶操作：\n" +
                        "1. 候选笔记：遇到不确定的空格，点击【✏ 笔记】进入草稿模式记录备选数；\n" +
                        "2. 键盘提示：数字键下方展示该数字全盘剩余未填数量，全部填完自动打钩；\n" +
                        "3. 智能提示：陷入僵局时点击【💡 提示】，AI 将为您锁定目标格并填入唯一正解。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private void handleBackPress() {
        if (game != null && game.isStarted() && !game.isBoardComplete() && game.getFilledCellCount() > 0) {
            new AlertDialog.Builder(requireContext())
                    .setTitle("返回大厅")
                    .setMessage("当前数独对局正在进行中，进度已自动保存，确定返回大厅吗？")
                    .setPositiveButton("返回大厅", (d, w) -> {
                        saveCurrentGame();
                        if (getActivity() != null) getActivity().finish();
                    })
                    .setNegativeButton("继续解谜", null)
                    .show();
        } else {
            if (getActivity() != null) getActivity().finish();
        }
    }

    // ==========================================
    // 自定义谜题
    // ==========================================

    private void showCustomPuzzleList() {
        List<SudokuCustomPuzzles.CustomPuzzle> puzzles =
                SudokuCustomPuzzles.loadAll(requireContext());
        if (puzzles.isEmpty()) {
            Toast.makeText(requireContext(), "还没有自定义谜题，先新建一个吧", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[puzzles.size() + 1];
        for (int i = 0; i < puzzles.size(); i++) {
            names[i] = puzzles.get(i).name;
        }
        names[puzzles.size()] = "✕ 删除谜题…";
        new AlertDialog.Builder(requireContext())
                .setTitle("自定义谜题")
                .setItems(names, (dialog, which) -> {
                    if (which == puzzles.size()) {
                        showDeletePuzzleList(puzzles);
                    } else {
                        playCustomPuzzle(puzzles.get(which));
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeletePuzzleList(List<SudokuCustomPuzzles.CustomPuzzle> puzzles) {
        String[] names = new String[puzzles.size()];
        for (int i = 0; i < puzzles.size(); i++) {
            names[i] = puzzles.get(i).name;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle("选择要删除的谜题")
                .setItems(names, (dialog, which) -> {
                    SudokuCustomPuzzles.remove(requireContext(), puzzles.get(which).id);
                    Toast.makeText(requireContext(),
                            "已删除「" + puzzles.get(which).name + "」", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openEditor() {
        getParentFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new SudokuEditorFragment())
                .addToBackStack(null)
                .commit();
    }

    private void playCustomPuzzle(SudokuCustomPuzzles.CustomPuzzle puzzle) {
        int[][] grid = puzzle.decodeMap();
        if (grid == null || !startCustomGame(grid, puzzle.name)) {
            Toast.makeText(requireContext(), "谜题数据无效", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean startCustomGame(int[][] grid, String name) {
        SudokuGame candidate = new SudokuGame();
        if (!candidate.loadCustomPuzzle(grid)) return false;
        stopTimer();
        game = candidate;
        currentCustomPuzzleName = name;
        currentCustomPuzzleGrid = copyGrid(grid);
        elapsedMs = 0L;
        notesMode = false;
        if (sudokuView != null) {
            sudokuView.setInteractionEnabled(true);
            sudokuView.clearSelection();
        }
        updateDifficultyCapsule();
        tvMessage.setText("✨ 自定义谜题已就绪");
        renderGame();
        startTimer();
        return true;
    }

    private static int[][] copyGrid(int[][] source) {
        int[][] result = new int[source.length][];
        for (int r = 0; r < source.length; r++) {
            result[r] = source[r].clone();
        }
        return result;
    }

    // ==========================================
    // 计时与存档
    // ==========================================

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
        if (game.isCustomPuzzle()) return;
        updateElapsed();
        saveManager.save(game, elapsedMs);
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
                return "适合熟悉规则的玩家";
            case 2:
                return "需结合候选数推导";
            case 3:
                return "终极逻辑挑战";
            default:
                return "轻松上手";
        }
    }

    // ==========================================
    // 配色与辅助绘制
    // ==========================================

    private void initThemeColors() {
        if (isNight) {
            colorBg = 0xFF14171F;
            colorSurface = 0xFF1E232F;
            colorBoard = 0xFF252B3A;
            colorTextPrimary = 0xFFEAECEF;
            colorTextSecondary = 0xFF8F9BB3;
            colorAccent = 0xFF5B8A72;
            colorAccentLight = 0xFF2A3D34;
            colorDanger = 0xFFEF5350;
            colorDangerBg = 0xFF3E2723;
            colorSuccess = 0xFF66BB6A;
            colorKeyBg = 0xFF232936;
            colorKeyText = 0xFFFFFFFF;
            colorGrid = 0xFF3E4756;
            colorGridStrong = 0xFF5B8A72;
            colorSelected = 0xFF2D4B3E;
            colorRelated = 0xFF22352C;
            colorGiven = 0xFFFFFFFF;
            colorUser = 0xFF81C784;
            colorErrorText = 0xFFFF5252;
            colorNote = 0xFF90A4AE;
        } else {
            colorBg = 0xFFF5EADA;
            colorSurface = 0xFFFFFFFF;
            colorBoard = 0xFFFFFBF6;
            colorTextPrimary = 0xFF212529;
            colorTextSecondary = 0xFF78909C;
            colorAccent = 0xFF5B8A72;
            colorAccentLight = 0xFFE8F5E9;
            colorDanger = 0xFFDC2626;
            colorDangerBg = 0xFFFEE2E2;
            colorSuccess = 0xFF2E7D32;
            colorKeyBg = 0xFFFFFFFF;
            colorKeyText = 0xFF212529;
            colorGrid = 0xFFCFD8DC;
            colorGridStrong = 0xFF5B8A72;
            colorSelected = 0xFFD4EDE1;
            colorRelated = 0xFFE8F5E9;
            colorGiven = 0xFF212529;
            colorUser = 0xFF2E7D32;
            colorErrorText = 0xFFD32F2F;
            colorNote = 0xFF78909C;
        }
    }

    private GradientDrawable roundBackground(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radiusDp);
        return drawable;
    }

    private RippleDrawable createCircleRipple(int normalColor, int sizeDp) {
        GradientDrawable content = new GradientDrawable();
        content.setShape(GradientDrawable.OVAL);
        content.setColor(normalColor);
        return new RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33888888),
                content,
                null
        );
    }

    private LinearLayout.LayoutParams matchWidthWrapContent() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weightedParams(float weight) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
    }

    private LinearLayout.LayoutParams weightedActionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(50), 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        return params;
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
