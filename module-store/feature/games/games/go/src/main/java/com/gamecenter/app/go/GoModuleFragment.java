package com.gamecenter.app.go;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
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

    private static final String TAG = "GoModule";
    private static final String[] DIFFICULTY_NAMES = {"入门", "普通", "困难", "大师"};
    private static final String[] DIFFICULTY_DESCRIPTIONS = {
            "会遵守规则并避开明显坏棋",
            "能提子、救棋并识别打吃与连接",
            "会推演双方应对并兼顾全局",
            "更充分推演，稳定选择主变化"
    };

    private GoGame game;
    private GoAI ai;
    private GoView goView;
    private TextView tvStatus;
    private TextView tvScore;
    private TextView tvMeta;
    private TextView tvDifficultyDescription;
    private LinearLayout gamePanel;
    private LinearLayout menuPanel;
    private final java.util.List<Button> difficultyButtons = new java.util.ArrayList<>();
    private CheckBox simpleBoardCheck;
    private Button boardStyleButton;
    private Button passButton;

    private Handler handler = new Handler(Looper.getMainLooper());
    private ExecutorService aiExecutor;
    private volatile boolean aiThinking = false;
    private volatile long aiGeneration = 0;
    private int moveCount = 0;
    private long gameStartTime = 0;
    private boolean gameStarted = false;
    private int restartCount = 0;
    private int aiContractViolationCount = 0;
    private int aiFallbackCount = 0;

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

        tvMeta = new TextView(ctx);
        tvMeta.setGravity(Gravity.CENTER);
        tvMeta.setTextSize(12f);
        tvMeta.setTextColor(colorLabelText);
        tvMeta.setPadding((int) (12 * dp), 0, (int) (12 * dp), (int) (8 * dp));
        root.addView(tvMeta);

        menuPanel = new LinearLayout(ctx);
        menuPanel.setOrientation(LinearLayout.VERTICAL);
        menuPanel.setGravity(Gravity.CENTER);
        addDifficultyButtonsTo(ctx, dp, menuPanel);

        simpleBoardCheck = new CheckBox(ctx);
        simpleBoardCheck.setText("使用简洁棋盘");
        simpleBoardCheck.setTextColor(colorStatusText);
        simpleBoardCheck.setChecked(GoUiPreferences.isSimpleMode(ctx));
        simpleBoardCheck.setOnCheckedChangeListener((buttonView, isChecked) -> setSimpleBoardMode(isChecked));
        menuPanel.addView(simpleBoardCheck);

        Button btnStart = new Button(ctx);
        btnStart.setStateListAnimator(null);
        btnStart.setText("开始游戏");
        btnStart.setOnClickListener(v -> startNewGame());
        menuPanel.addView(btnStart);
        root.addView(menuPanel);

        gamePanel = new LinearLayout(ctx);
        gamePanel.setOrientation(LinearLayout.VERTICAL);
        gamePanel.setGravity(Gravity.CENTER);
        gamePanel.setVisibility(View.GONE);

        goView = new GoView(ctx);
        goView.setSimpleMode(GoUiPreferences.isSimpleMode(ctx));
        int viewWidth = (int) (ctx.getResources().getDisplayMetrics().widthPixels * 0.9);
        goView.setLayoutParams(new FrameLayout.LayoutParams(viewWidth, viewWidth));
        goView.setOnCellClickListener(this::onCellClick);
        gamePanel.addView(goView);

        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setPadding(0, (int) (16 * dp), 0, 0);

        passButton = new Button(ctx);
        passButton.setStateListAnimator(null);
        passButton.setText("停一手");
        passButton.setOnClickListener(v -> passMove());

        Button btnResign = new Button(ctx);
        btnResign.setStateListAnimator(null);
        btnResign.setText("认输");
        btnResign.setOnClickListener(v -> resign());

        Button btnRestart = new Button(ctx);
        btnRestart.setStateListAnimator(null);
        btnRestart.setText("重新开始");
        btnRestart.setOnClickListener(v -> showMenu());

        boardStyleButton = new Button(ctx);
        boardStyleButton.setStateListAnimator(null);
        boardStyleButton.setOnClickListener(v -> setSimpleBoardMode(!goView.isSimpleMode()));

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins((int) (12 * dp), 0, (int) (12 * dp), 0);
        passButton.setLayoutParams(btnLp);
        btnResign.setLayoutParams(btnLp);
        btnRestart.setLayoutParams(btnLp);
        boardStyleButton.setLayoutParams(btnLp);

        btnRow.addView(passButton);
        btnRow.addView(btnResign);
        btnRow.addView(btnRestart);
        gamePanel.addView(btnRow);
        gamePanel.addView(boardStyleButton);
        root.addView(gamePanel);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        applyWidgetColors();
        if (getActivity() != null && getActivity().getIntent() != null) {
            int prefilledIndex = getActivity().getIntent().getIntExtra("game_difficulty_index", -1);
            if (prefilledIndex >= 0) {
                // 大厅使用 0-based 索引；这里只预选，仍停留在可见菜单等待用户开始。
                ai.setDifficulty(Math.min(prefilledIndex + 1, DIFFICULTY_NAMES.length));
                updateDifficultyButtons();
            }
        }
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
                btn.setStateListAnimator(null);
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

        tvDifficultyDescription = new TextView(ctx);
        tvDifficultyDescription.setGravity(Gravity.CENTER);
        tvDifficultyDescription.setTextSize(12f);
        tvDifficultyDescription.setTextColor(colorLabelText);
        tvDifficultyDescription.setPadding((int) (12 * dp), (int) (2 * dp),
                (int) (12 * dp), (int) (10 * dp));
        parent.addView(tvDifficultyDescription);
    }

    private void showMenu() {
        if (gameStarted) restartCount++;
        cancelPendingAi("show_menu");
        gameStarted = false;
        menuPanel.setVisibility(View.VISIBLE);
        gamePanel.setVisibility(View.GONE);
        tvStatus.setText("欢迎来到围棋");
        tvScore.setText("");
        if (goView != null) {
            goView.hideTerritory();
            goView.clearLastMove();
        }
        updateDifficultyButtons();
        updateBoardStyleControls();
        updatePassButton();
        renderGameMeta();
    }

    private void startNewGame() {
        cancelPendingAi("start_new_game");
        game.startNewGame();
        moveCount = 0;
        aiContractViolationCount = 0;
        aiFallbackCount = 0;
        gameStarted = true;

        menuPanel.setVisibility(View.GONE);
        gamePanel.setVisibility(View.VISIBLE);
        tvStatus.setText("轮到你了");
        updateScoreDisplay();

        goView.hideTerritory();
        goView.clearLastMove();
        goView.setBoard(game.getBoard());

        gameStartTime = System.currentTimeMillis();
        updateBoardStyleControls();
        updatePassButton();
        renderGameMeta();
    }

    private void onCellClick(int row, int col) {
        if (!gameStarted || game.isGameOver() || game.getCurrentPlayer() != GoGame.BLACK || aiThinking) return;

        if (game.playMove(row, col)) {
            moveCount++;
            goView.setBoard(game.getBoard());
            goView.setLastMove(row, col);
            updateScoreDisplay();
            renderGameMeta();
            startAiTurn();
        }
    }

    private void startAiTurn() {
        if (!gameStarted || aiThinking || game.isGameOver()
                || game.getCurrentPlayer() != GoGame.WHITE) return;
        aiThinking = true;
        tvStatus.setText("AI 思考中（" + DIFFICULTY_NAMES[ai.getDifficulty() - 1] + "）");
        updatePassButton();
        renderGameMeta();

        final long gen = ++aiGeneration;
        final GoGame searchGame = createSearchSnapshot();
        aiExecutor.execute(() -> {
            if (gen != aiGeneration) return;
            int[] bestMove = ai.findBestAiMove(searchGame);
            handler.post(() -> applyAiMove(bestMove, gen));
        });
    }

    private void applyAiMove(int[] bestMove, long gen) {
        if (gen != aiGeneration) return;
        if (!gameStarted || game.isGameOver() || game.getCurrentPlayer() != GoGame.WHITE) {
            aiThinking = false;
            updatePassButton();
            return;
        }

        if (bestMove == null) {
            boolean hasLegalMove = hasLegalMove(GoGame.WHITE);
            if (hasLegalMove && !isAcceptableStrategicPass()) {
                logAiContractViolation("null_with_legal_move", null);
                int[] fallback = findCentralLegalMove(GoGame.WHITE);
                if (!commitAiMove(fallback, true)) {
                    game.passMove();
                    moveCount++;
                    goView.clearLastMove();
                }
            } else {
                Log.i(TAG, hasLegalMove ? "GO_AI_STRATEGIC_PASS" : "GO_AI_FORCED_PASS");
                game.passMove();
                moveCount++;
                goView.clearLastMove();
                tvStatus.setText("AI 停一手");
            }
        } else {
            if (!commitAiMove(bestMove, false)) {
                boolean hasLegalMove = hasLegalMove(GoGame.WHITE);
                if (hasLegalMove) {
                    logAiContractViolation("rejected_move", bestMove);
                    int[] fallback = findCentralLegalMove(GoGame.WHITE);
                    if (!commitAiMove(fallback, true)) {
                        Log.e(TAG, "GO_AI_FALLBACK_FAILED");
                        game.passMove();
                        moveCount++;
                        goView.clearLastMove();
                    }
                } else {
                    logAiContractViolation("rejected_move_without_legal_alternative", bestMove);
                    Log.i(TAG, "GO_AI_FORCED_PASS_AFTER_REJECT");
                    game.passMove();
                    moveCount++;
                    goView.clearLastMove();
                }
            }
        }

        if (game.isGameOver()) {
            aiThinking = false;
            updatePassButton();
            onGameEnd();
            return;
        }

        aiThinking = false;
        tvStatus.setText("轮到你了");
        updateScoreDisplay();
        updatePassButton();
        renderGameMeta();
    }

    private void passMove() {
        if (!gameStarted || game.isGameOver() || aiThinking
                || game.getCurrentPlayer() != GoGame.BLACK) return;
        game.passMove();
        moveCount++;
        goView.clearLastMove();
        updateScoreDisplay();
        renderGameMeta();
        if (game.isGameOver()) {
            onGameEnd();
            return;
        }
        tvStatus.setText("AI 思考中");
        startAiTurn();
    }

    private GoGame createSearchSnapshot() {
        GoGame snapshot = new GoGame();
        snapshot.restoreState(
                game.getBoardSnapshot(),
                game.getPreviousBoardSnapshot(),
                game.getCurrentPlayer(),
                game.getCapturedByBlack(),
                game.getCapturedByWhite(),
                game.getConsecutivePasses(),
                game.isGameOver());
        return snapshot;
    }

    private boolean commitAiMove(@Nullable int[] move, boolean fallback) {
        if (move == null || move.length < 2
                || move[0] < 0 || move[0] >= GoGame.BOARD_SIZE
                || move[1] < 0 || move[1] >= GoGame.BOARD_SIZE) {
            return false;
        }
        if (!game.playMove(move[0], move[1])) return false;
        moveCount++;
        if (fallback) {
            aiFallbackCount++;
            Log.w(TAG, "GO_AI_FALLBACK difficulty=" + ai.getDifficulty()
                    + " move=" + move[0] + "," + move[1]
                    + " count=" + aiFallbackCount);
        }
        goView.setBoard(game.getBoard());
        goView.setLastMove(move[0], move[1]);
        return true;
    }

    private boolean hasLegalMove(int color) {
        for (int row = 0; row < GoGame.BOARD_SIZE; row++) {
            for (int col = 0; col < GoGame.BOARD_SIZE; col++) {
                if (game.isValidMove(row, col, color)) return true;
            }
        }
        return false;
    }

    @Nullable
    private int[] findCentralLegalMove(int color) {
        int[] best = null;
        int bestDistance = Integer.MAX_VALUE;
        int center = GoGame.BOARD_SIZE / 2;
        for (int row = 0; row < GoGame.BOARD_SIZE; row++) {
            for (int col = 0; col < GoGame.BOARD_SIZE; col++) {
                if (!game.isValidMove(row, col, color)) continue;
                int distance = Math.abs(row - center) + Math.abs(col - center);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = new int[]{row, col};
                }
            }
        }
        return best;
    }

    private boolean isAcceptableStrategicPass() {
        if (game.getConsecutivePasses() != 1) return false;
        GoGame.Score score = game.calculateScore();
        return score.getWhiteScore() >= score.getBlackScore();
    }

    private void logAiContractViolation(String reason, @Nullable int[] move) {
        aiContractViolationCount++;
        String rawMove = move == null || move.length < 2
                ? "null" : move[0] + "," + move[1];
        Log.e("GO_AI_CONTRACT_VIOLATION",
                "reason=" + reason
                        + " difficulty=" + ai.getDifficulty()
                        + " generation=" + aiGeneration
                        + " move=" + rawMove
                        + " count=" + aiContractViolationCount);
    }

    private void cancelPendingAi(String reason) {
        aiGeneration++;
        aiThinking = false;
        if (ai != null) ai.cancel();
        Log.d(TAG, "AI generation invalidated: " + reason + " gen=" + aiGeneration);
        updatePassButton();
    }

    private void setSimpleBoardMode(boolean simpleMode) {
        if (getContext() == null) return;
        GoUiPreferences.setSimpleMode(requireContext(), simpleMode);
        if (goView != null) goView.setSimpleMode(simpleMode);
        if (simpleBoardCheck != null && simpleBoardCheck.isChecked() != simpleMode) {
            simpleBoardCheck.setChecked(simpleMode);
        }
        updateBoardStyleControls();
        renderGameMeta();
    }

    private void updateBoardStyleControls() {
        boolean simpleMode = goView != null
                ? goView.isSimpleMode()
                : getContext() != null && GoUiPreferences.isSimpleMode(requireContext());
        if (boardStyleButton != null) {
            boardStyleButton.setText(simpleMode ? "棋盘：简洁" : "棋盘：增强");
        }
    }

    private void updatePassButton() {
        if (passButton == null || game == null) return;
        passButton.setEnabled(gameStarted && !aiThinking && !game.isGameOver()
                && game.getCurrentPlayer() == GoGame.BLACK);
        passButton.setAlpha(passButton.isEnabled() ? 1f : 0.45f);
    }

    private void renderGameMeta() {
        if (tvMeta == null || ai == null || game == null) return;
        String turn;
        if (!gameStarted) turn = "等待开始";
        else if (game.isGameOver()) turn = "已结束";
        else turn = game.getCurrentPlayer() == GoGame.BLACK ? "黑方回合" : "白方回合";
        boolean simpleMode = goView != null && goView.isSimpleMode();
        tvMeta.setText(DIFFICULTY_NAMES[ai.getDifficulty() - 1]
                + " · " + moveCount + " 手 · " + turn
                + " · 贴目 " + String.format(Locale.ROOT, "%.1f", GoGame.KOMI)
                + " · " + (simpleMode ? "简洁棋盘" : "增强棋盘"));
    }

    private void resign() {
        if (!gameStarted || game.isGameOver()) return;
        cancelPendingAi("resign");
        game.setGameOver(true);
        gameStarted = false;
        tvStatus.setText("你认输了");
        GoGame.Score score = game.calculateScore();
        renderGameMeta();
        showGameEndDialog(false, score.getBlackScore(), score.getWhiteScore());
    }

    private void onGameEnd() {
        gameStarted = false;
        GoGame.Score score = game.calculateScore();
        double blackTerritory = score.getBlackScore();
        double whiteTerritory = score.getWhiteScore();

        float[][] territory = game.calculateTerritory();
        goView.showTerritory(territory);

        boolean playerWins = score.isBlackWinner();

        if (playerWins) {
            tvStatus.setText(String.format(Locale.ROOT,
                    "你赢了！黑 %.1f : 白 %.1f", blackTerritory, whiteTerritory));
        } else {
            tvStatus.setText(String.format(Locale.ROOT,
                    "AI 赢了。黑 %.1f : 白 %.1f", blackTerritory, whiteTerritory));
        }

        updatePassButton();
        renderGameMeta();
        Log.i(TAG, "GO_GAME_END difficulty=" + ai.getDifficulty()
                + " ply=" + moveCount + " undoCount=0 restartCount=" + restartCount
                + " rawIllegal=" + aiContractViolationCount
                + " fallback=" + aiFallbackCount);
        showGameEndDialog(playerWins, blackTerritory, whiteTerritory);
    }

    private void showGameEndDialog(boolean playerWins, double blackTerritory, double whiteTerritory) {
        if (getContext() == null) return;
        long elapsed = gameStartTime > 0 ? (System.currentTimeMillis() - gameStartTime) : 0L;
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(getString(R.string.go_game_over));
        String winnerText = playerWins ? "你赢了！" : "AI 赢了";
        builder.setMessage(
                winnerText + "\n\n" +
                "手数: " + moveCount + "\n" +
                "用时: " + formatDuration(elapsed) + "\n" +
                String.format(Locale.ROOT, "比分: 黑 %.1f : 白 %.1f",
                        blackTerritory, whiteTerritory));
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
        if (level < 1 || level > DIFFICULTY_NAMES.length || gameStarted) return;
        ai.setDifficulty(level);
        updateDifficultyButtons();
        Toast.makeText(requireContext(), getString(R.string.go_difficulty_format, DIFFICULTY_NAMES[level - 1]), Toast.LENGTH_SHORT).show();
        renderGameMeta();
    }

    private void updateScoreDisplay() {
        tvScore.setText("提子 黑:" + game.getCapturedByBlack() + " 白:" + game.getCapturedByWhite()
                + "  当前: " + (game.getCurrentPlayer() == GoGame.BLACK ? "●" : "○"));
    }

    private void updateDifficultyButtons() {
        int level = ai.getDifficulty();
        for (int i = 0; i < difficultyButtons.size(); i++) {
            Button btn = difficultyButtons.get(i);
            boolean selected = i + 1 == level;
            if (i + 1 == level) {
                btn.setBackgroundColor(colorDiffActive[i]);
            } else {
                btn.setBackgroundColor(colorDiffInactive);
            }
            btn.setText((selected ? "✓ " : "") + DIFFICULTY_NAMES[i]);
            btn.setAlpha(selected ? 1f : 0.72f);
            btn.setTextColor(colorButtonText);
        }
        if (tvDifficultyDescription != null) {
            tvDifficultyDescription.setText(DIFFICULTY_DESCRIPTIONS[level - 1]);
        }
    }

    private void applyWidgetColors() {
        for (int i = 0; i < menuPanel.getChildCount(); i++) {
            View child = menuPanel.getChildAt(i);
            if (child instanceof Button && !(child instanceof CheckBox)) {
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
        if (boardStyleButton != null) {
            boardStyleButton.setBackgroundColor(colorBtnBg);
            boardStyleButton.setTextColor(colorBtnText);
        }
        if (simpleBoardCheck != null) simpleBoardCheck.setTextColor(colorStatusText);
        if (tvDifficultyDescription != null) tvDifficultyDescription.setTextColor(colorLabelText);
        if (tvMeta != null) tvMeta.setTextColor(colorLabelText);
        updateDifficultyButtons();
        updateBoardStyleControls();
        updatePassButton();
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
        cancelPendingAi("destroy");
        handler.removeCallbacksAndMessages(null);
        if (aiExecutor != null) {
            aiExecutor.shutdownNow();
        }
        super.onDestroy();
    }
}
