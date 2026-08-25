package com.gamecenter.app.games.go;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.gamecenter.app.R;
import com.gamecenter.app.games.base.BaseGameActivity;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GoActivity extends BaseGameActivity {

    private static final String TAG = "GoActivity";
    private static final String CONTRACT_TAG = "GO_AI_CONTRACT_VIOLATION";

    private GoGame game;
    private GoAI ai;

    private int totalWins = 0;
    private int winStreak = 0;
    private int moveCount = 0;

    private Handler handler = new Handler(Looper.getMainLooper());

    private GoView goView;
    private TextView tvStatus;
    private TextView tvScore;
    private TextView tvMeta;
    private TextView tvDifficultyDescription;
    private LinearLayout gamePanel;
    private LinearLayout menuPanel;
    private CheckBox simpleBoardCheck;
    private MaterialButton boardStyleButton;
    private MaterialButton passButton;

    private final List<MaterialButton> difficultyButtons = new ArrayList<>();
    private long aiThinkStartMs = 0L;

    /** AI 计算线程池：将 MCTS/Minimax 等耗时搜索移出主线程，避免卡顿/ANR。 */
    private ExecutorService aiExecutor;

    /**
     * 确保 aiExecutor 可用：endGame/onDestroy 会调用 shutdownNow() 关闭线程池，
     * 若后续再提交任务会抛 RejectedExecutionException。这里在提交前检查并按需重建。
     */
    private void ensureAiExecutor() {
        if (aiExecutor == null || aiExecutor.isShutdown()) {
            aiExecutor = Executors.newSingleThreadExecutor();
        }
    }

    /** AI 是否正在思考（防止重复触发与重复落子）。 */
    private volatile boolean aiThinking = false;

    /** AI 回合代际：pause/restart/destroy 时自增，使过期计算不再回写 UI。 */
    private volatile long aiGeneration = 0;
    private int restartCount = 0;
    private int aiContractViolationCount = 0;
    private int aiFallbackCount = 0;

    /** P2-7: 对局回放录制器 */
    private com.gamecenter.app.games.replay.ReplayRecorder replayRecorder;

    /** 新手引导序列（首次开始游戏后弹出，3 步引导） */
    private com.gamecenter.app.ui.onboarding.CoachmarkSequence onboardingSequence;

    /** 2026-08-23 P2-2: 中断续玩存档管理器 */
    private com.gamecenter.app.games.save.GameSaveManager saveManager;

    /** 2026-08-23 P3: 统一音效/震动反馈（内部实时遵循设置开关） */
    private com.gamecenter.app.games.base.GameFeedback feedback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 2026-08-23 修复：BaseGameActivity.onCreate 在 super 调用中执行 initGame()，
        // initGame() → createGameContentView() → addDifficultyButtonsTo() 会读取
        // ai.getDifficulty()。若 ai 此时未初始化，将触发 NullPointerException。
        // 因此必须在 super.onCreate 之前完成 game/ai/aiExecutor 的初始化。
        game = new GoGame();
        ai = new GoAI();
        int prefilledIndex = getIntent().getIntExtra("game_difficulty_index", -1);
        // 大厅传入值只作为 0-based 预选；没有推荐值时默认普通档（level 2）。
        ai.setDifficulty(prefilledIndex >= 0 ? Math.min(prefilledIndex + 1, 4) : 2);
        aiExecutor = Executors.newSingleThreadExecutor();
        super.onCreate(savedInstanceState);
        onboardingSequence = new com.gamecenter.app.ui.onboarding.CoachmarkSequence(
                this,
                com.gamecenter.app.ui.onboarding.GoOnboarding.steps,
                com.gamecenter.app.ui.onboarding.GoOnboarding.STORAGE_KEY
        );
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
        // 2026-08-23 P2-2：初始化存档管理器
        saveManager = new com.gamecenter.app.games.save.GameSaveManager(this);
        // 2026-08-23 P3：初始化音效/震动反馈
        feedback = new com.gamecenter.app.games.base.GameFeedback(this);
        if (gameContentContainer instanceof FrameLayout) {
            View contentView = createGameContentView();
            ((FrameLayout) gameContentContainer).addView(contentView);
            showMenu(false);
        }
    }

    private View createGameContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.game_go_color_bg));

        tvStatus = new TextView(this);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(16f);
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.game_go_color_status_text));
        tvStatus.setPadding(0, 24, 0, 8);
        // 给状态栏打上稳定 id，供 Coachmark 定位（围棋新手引导第 2 步目标）
        tvStatus.setId(R.id.go_status_view);

        tvScore = new TextView(this);
        tvScore.setGravity(Gravity.CENTER);
        tvScore.setTextSize(14f);
        tvScore.setTextColor(ContextCompat.getColor(this, R.color.game_go_color_score_text));
        tvScore.setPadding(0, 4, 0, 16);

        tvMeta = new TextView(this);
        tvMeta.setGravity(Gravity.CENTER);
        tvMeta.setTextSize(12f);
        tvMeta.setTextColor(ContextCompat.getColor(this, R.color.game_go_color_label_text));
        tvMeta.setPadding(12, 0, 12, 8);

        menuPanel = new LinearLayout(this);
        menuPanel.setOrientation(LinearLayout.VERTICAL);
        menuPanel.setGravity(Gravity.CENTER);

        addDifficultyButtonsTo(menuPanel);

        simpleBoardCheck = new CheckBox(this);
        simpleBoardCheck.setText("使用简洁棋盘");
        simpleBoardCheck.setTextColor(ContextCompat.getColor(this, R.color.game_go_color_status_text));
        simpleBoardCheck.setChecked(GoUiPreferences.isSimpleMode(this));
        simpleBoardCheck.setOnCheckedChangeListener((buttonView, isChecked) -> setSimpleBoardMode(isChecked));
        menuPanel.addView(simpleBoardCheck);

        MaterialButton btnStart = new MaterialButton(this);
        btnStart.setText(R.string.game_go_start);
        btnStart.setBackgroundColor(ContextCompat.getColor(this, R.color.game_go_color_score_text));
        btnStart.setOnClickListener(v -> beginPlay());
        menuPanel.addView(btnStart);

        gamePanel = new LinearLayout(this);
        gamePanel.setOrientation(LinearLayout.VERTICAL);
        gamePanel.setGravity(Gravity.CENTER);
        gamePanel.setVisibility(View.GONE);

        goView = new GoView(this);
        goView.setSimpleMode(GoUiPreferences.isSimpleMode(this));
        int viewWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
        goView.setLayoutParams(new FrameLayout.LayoutParams(viewWidth, viewWidth));
        goView.setOnCellClickListener(this::onCellClick);
        // 给棋盘打上稳定 id，供 Coachmark 定位（围棋新手引导第 1 步目标）
        goView.setId(R.id.go_board_view);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setPadding(0, 16, 0, 0);
        // 给按钮行打上稳定 id，供 Coachmark 定位（围棋新手引导第 3 步目标）
        btnRow.setId(R.id.go_buttons_view);

        passButton = new MaterialButton(this);
        passButton.setText(R.string.game_go_pass);
        passButton.setOnClickListener(v -> passMove());

        MaterialButton btnResign = new MaterialButton(this);
        btnResign.setText(R.string.game_go_resign);
        btnResign.setOnClickListener(v -> resign());

        MaterialButton btnRestart = new MaterialButton(this);
        btnRestart.setText(R.string.game_go_restart);
        btnRestart.setOnClickListener(v -> showMenu());

        boardStyleButton = new MaterialButton(this);
        boardStyleButton.setOnClickListener(v -> setSimpleBoardMode(!goView.isSimpleMode()));

        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnLp.setMargins(12, 0, 12, 0);
        passButton.setLayoutParams(btnLp);
        btnResign.setLayoutParams(btnLp);
        btnRestart.setLayoutParams(btnLp);
        boardStyleButton.setLayoutParams(btnLp);

        btnRow.addView(passButton);
        btnRow.addView(btnResign);
        btnRow.addView(btnRestart);

        gamePanel.addView(goView);
        gamePanel.addView(btnRow);
        gamePanel.addView(boardStyleButton);

        root.addView(tvStatus);
        root.addView(tvScore);
        root.addView(tvMeta);
        root.addView(menuPanel);
        root.addView(gamePanel);

        return root;
    }

    private void showMenu() {
        showMenu(true);
    }

    private void showMenu(boolean countRestart) {
        if (countRestart && isGameRunning) restartCount++;
        cancelPendingAi("show_menu");
        isGameRunning = false;
        menuPanel.setVisibility(View.VISIBLE);
        gamePanel.setVisibility(View.GONE);
        tvStatus.setText(R.string.game_go_welcome);
        tvScore.setText("");
        goView.hideTerritory();
        goView.clearLastMove();
        updateDifficultyButtons();
        updateBoardStyleControls();
        updatePassButton();
        renderGameMeta();
    }

    /**
     * 2026-08-23 P2-2：开始游戏入口——检测未完成对局存档，
     * 有存档时弹"继续上局"对话框，否则直接新开一局。
     */
    private void beginPlay() {
        if (saveManager != null && saveManager.hasSave(getGameId())) {
            new AlertDialog.Builder(this)
                    .setTitle("继续上局？")
                    .setMessage("检测到上次未完成的对局，是否继续？")
                    .setPositiveButton("继续上局", (d, w) -> restoreFromSave())
                    .setNegativeButton("新开一局", (d, w) -> {
                        saveManager.clear(getGameId());
                        startNewGame();
                    })
                    .setCancelable(true)
                    .show();
        } else {
            startNewGame();
        }
    }

    /** 2026-08-23 P2-2：从存档恢复对局 */
    private void restoreFromSave() {
        org.json.JSONObject state = saveManager == null ? null : saveManager.load(getGameId());
        if (state == null) {
            startNewGame();
            return;
        }
        try {
            org.json.JSONArray rows = state.getJSONArray("board");
            int[][] savedBoard = boardFromJson(rows);
            int[][] savedPreviousBoard = state.isNull("previousBoard")
                    ? null : boardFromJson(state.optJSONArray("previousBoard"));
            int difficulty = Math.max(1, Math.min(4, state.optInt("difficulty", 2)));
            int currentPlayer = state.optInt("currentPlayer", GoGame.BLACK);
            int capBlack = state.optInt("capturedByBlack", 0);
            int capWhite = state.optInt("capturedByWhite", 0);
            int consecutivePasses = state.optInt("consecutivePasses", 0);
            boolean gameOver = state.optBoolean("gameOver", false);

            // 作废可能进行中的后台搜索
            cancelPendingAi("restore_save");

            game.restoreState(savedBoard, savedPreviousBoard, currentPlayer, capBlack, capWhite,
                    consecutivePasses, gameOver);
            ai.setDifficulty(difficulty);
            moveCount = state.optInt("moveCount", 0);
            isGameRunning = !gameOver;
            gameStartTime = System.currentTimeMillis();

            menuPanel.setVisibility(View.GONE);
            gamePanel.setVisibility(View.VISIBLE);
            tvStatus.setText(currentPlayer == GoGame.BLACK
                    ? R.string.game_go_your_turn : R.string.game_go_ai_thinking);
            updateScoreDisplay();
            goView.hideTerritory();
            goView.clearLastMove();
            goView.setBoard(game.getBoard());
            updateDifficultyButtons();
            updateBoardStyleControls();
            updatePassButton();
            renderGameMeta();

            // 重新开始回放录制（从恢复点继续记）
            replayRecorder = new com.gamecenter.app.games.replay.ReplayRecorder(this, getGameId());
            replayRecorder.startRecording(difficulty);

            // 若存档停在 AI（白方）回合，恢复后触发 AI 行动
            if (!gameOver && currentPlayer == GoGame.WHITE) {
                startAiTurn();
            }
        } catch (Exception e) {
            android.util.Log.w("GoActivity", "存档恢复失败，新开一局: " + e.getMessage());
            startNewGame();
        }
    }

    /** 2026-08-23 P2-2：保存当前对局进度 */
    private void saveProgress() {
        if (saveManager == null || !isGameRunning || game == null) return;
        try {
            org.json.JSONObject state = new org.json.JSONObject();
            org.json.JSONArray rows = new org.json.JSONArray();
            int[][] b = game.getBoardSnapshot();
            rows = boardToJson(b);
            state.put("board", rows);
            int[][] previousBoard = game.getPreviousBoardSnapshot();
            state.put("previousBoard", previousBoard == null
                    ? org.json.JSONObject.NULL : boardToJson(previousBoard));
            state.put("difficulty", ai.getDifficulty());
            state.put("currentPlayer", game.getCurrentPlayer());
            state.put("capturedByBlack", game.getCapturedByBlack());
            state.put("capturedByWhite", game.getCapturedByWhite());
            state.put("consecutivePasses", game.getConsecutivePasses());
            state.put("gameOver", game.isGameOver());
            state.put("moveCount", moveCount);
            saveManager.save(getGameId(), state);
        } catch (Exception ignored) {
            // 存档失败不影响游戏主流程
        }
    }

    @NonNull
    private org.json.JSONArray boardToJson(@NonNull int[][] board) {
        org.json.JSONArray rows = new org.json.JSONArray();
        for (int row = 0; row < GoGame.BOARD_SIZE; row++) {
            org.json.JSONArray columns = new org.json.JSONArray();
            for (int col = 0; col < GoGame.BOARD_SIZE; col++) {
                columns.put(board[row][col]);
            }
            rows.put(columns);
        }
        return rows;
    }

    @NonNull
    private int[][] boardFromJson(@Nullable org.json.JSONArray rows) throws org.json.JSONException {
        if (rows == null || rows.length() != GoGame.BOARD_SIZE) {
            throw new org.json.JSONException("invalid go board row count");
        }
        int[][] board = new int[GoGame.BOARD_SIZE][GoGame.BOARD_SIZE];
        for (int row = 0; row < GoGame.BOARD_SIZE; row++) {
            org.json.JSONArray columns = rows.getJSONArray(row);
            if (columns.length() != GoGame.BOARD_SIZE) {
                throw new org.json.JSONException("invalid go board column count");
            }
            for (int col = 0; col < GoGame.BOARD_SIZE; col++) {
                int value = columns.getInt(col);
                if (value < GoGame.EMPTY || value > GoGame.WHITE) {
                    throw new org.json.JSONException("invalid go stone value");
                }
                board[row][col] = value;
            }
        }
        return board;
    }

    private void startNewGame() {
        cancelPendingAi("start_new_game");
        game.startNewGame();
        moveCount = 0;
        aiContractViolationCount = 0;
        aiFallbackCount = 0;

        menuPanel.setVisibility(View.GONE);
        gamePanel.setVisibility(View.VISIBLE);
        tvStatus.setText(R.string.game_go_your_turn);
        updateScoreDisplay();

        goView.hideTerritory();
        goView.clearLastMove();
        goView.setBoard(game.getBoard());

        isGameRunning = true;
        gameStartTime = System.currentTimeMillis();

        // P2-7: 开始回放录制
        replayRecorder = new com.gamecenter.app.games.replay.ReplayRecorder(this, getGameId());
        replayRecorder.startRecording(ai.getDifficulty());
        updateBoardStyleControls();
        updatePassButton();
        renderGameMeta();

        // 首次开始游戏后触发新手引导（gamePanel 此时已 VISIBLE，goView 拿得到尺寸）
        // Spec §6 / 设计 §5.6：U2 免登录上手
        if (onboardingSequence != null) {
            goView.postDelayed(() -> onboardingSequence.start(), 300L);
        }
    }

    private void onCellClick(int row, int col) {
        if (game.isGameOver() || !isGameRunning) return;
        if (game.getCurrentPlayer() != GoGame.BLACK) return;
        if (aiThinking) return;

        if (game.playMove(row, col)) {
            moveCount++;
            // P2-7: 记录玩家落子
            if (replayRecorder != null && replayRecorder.isRecording()) {
                replayRecorder.record(new com.gamecenter.app.games.replay.ReplayMove(row, col, GoGame.BLACK));
            }
            // 2026-08-23 P3：玩家落子反馈
            if (feedback != null) feedback.feedbackMove();
            goView.setBoard(game.getBoard());
            goView.setLastMove(row, col);
            updateScoreDisplay();
            renderGameMeta();
            // 2026-08-23 P2-2：玩家落子后保存进度（轮到 AI）
            saveProgress();
            startAiTurn();
        }
    }

    /**
     * 启动 AI 回合。
     * <p>将耗时搜索（大师难度 MCTS 约 1.5s）放到后台线程执行，
     * 计算完成后通过主线程 Handler 回写棋盘，彻底消除主线程卡顿/ANR。</p>
     */
    private void startAiTurn() {
        if (aiThinking || !isGameRunning || game.isGameOver()
                || game.getCurrentPlayer() != GoGame.WHITE) return;
        aiThinking = true;
        aiThinkStartMs = System.currentTimeMillis();
        tvStatus.setText(getString(R.string.game_go_ai_thinking_with_difficulty, getDifficultyName(ai.getDifficulty())));
        updatePassButton();
        renderGameMeta();

        final long gen = ++aiGeneration;
        final long searchStartedMs = aiThinkStartMs;
        final GoGame searchGame = createSearchSnapshot();
        // 提交前确保线程池可用（endGame/onDestroy 已 shutdown 则重建，避免 RejectedExecutionException）
        ensureAiExecutor();
        aiExecutor.execute(() -> {
            if (gen != aiGeneration) return;
            int[] bestMove = ai.findBestAiMove(searchGame);
            long thinkMs = System.currentTimeMillis() - searchStartedMs;
            Log.i("GoAI", "难度=" + ai.getDifficulty() + " 思考耗时=" + thinkMs + "ms");
            handler.post(() -> applyAiMove(bestMove, thinkMs, gen));
        });
    }

    /**
     * 主线程回写 AI 着法结果。
     */
    private void applyAiMove(int[] bestMove, long thinkMs, long gen) {
        if (gen != aiGeneration) return;
        if (!isGameRunning || game.isGameOver() || game.getCurrentPlayer() != GoGame.WHITE) {
            aiThinking = false;
            updatePassButton();
            return;
        }

        if (bestMove == null) {
            boolean hasLegalMove = hasLegalMove(GoGame.WHITE);
            if (hasLegalMove && !isAcceptableStrategicPass()) {
                logAiContractViolation("null_with_legal_move", null);
                int[] fallback = findCentralLegalMove(GoGame.WHITE);
                if (!commitAiMove(fallback, true)) recordAiPass("fallback_failed");
            } else {
                recordAiPass(hasLegalMove ? "strategic" : "forced");
            }
        } else {
            if (!commitAiMove(bestMove, false)) {
                if (hasLegalMove(GoGame.WHITE)) {
                    logAiContractViolation("rejected_move", bestMove);
                    int[] fallback = findCentralLegalMove(GoGame.WHITE);
                    if (!commitAiMove(fallback, true)) recordAiPass("fallback_failed");
                } else {
                    logAiContractViolation("rejected_move_without_legal_alternative", bestMove);
                    recordAiPass("forced_after_reject");
                }
            }

            if (ai.getDifficulty() >= 4 && thinkMs > 100) {
                Toast.makeText(this, getString(R.string.game_go_ai_think_ms, thinkMs), Toast.LENGTH_SHORT).show();
            }
        }

        if (game.isGameOver()) {
            aiThinking = false;
            updatePassButton();
            onGameEnd();
            return;
        }

        aiThinking = false;
        tvStatus.setText(R.string.game_go_your_turn);
        updateScoreDisplay();
        updatePassButton();
        renderGameMeta();
        // 2026-08-23 P2-2：AI 落子后保存进度（轮到玩家）
        saveProgress();
    }

    private void passMove() {
        if (game.isGameOver() || !isGameRunning || aiThinking
                || game.getCurrentPlayer() != GoGame.BLACK) return;
        game.passMove();
        moveCount++;
        goView.clearLastMove();
        // P2-7: 记录玩家弃权（pass），用 (-1,-1) 标记
        if (replayRecorder != null && replayRecorder.isRecording()) {
            replayRecorder.record(new com.gamecenter.app.games.replay.ReplayMove(-1, -1, GoGame.BLACK));
        }
        updateScoreDisplay();
        renderGameMeta();
        saveProgress();
        if (game.isGameOver()) {
            onGameEnd();
            return;
        }
        tvStatus.setText(R.string.game_go_ai_thinking);
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
        if (replayRecorder != null && replayRecorder.isRecording()) {
            replayRecorder.record(new com.gamecenter.app.games.replay.ReplayMove(
                    move[0], move[1], GoGame.WHITE));
        }
        if (feedback != null) feedback.playMove();
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

    private void recordAiPass(@NonNull String reason) {
        game.passMove();
        moveCount++;
        if (replayRecorder != null && replayRecorder.isRecording()) {
            replayRecorder.record(new com.gamecenter.app.games.replay.ReplayMove(
                    -1, -1, GoGame.WHITE));
        }
        goView.clearLastMove();
        tvStatus.setText(R.string.game_go_ai_passed);
        Log.i(TAG, "GO_AI_PASS reason=" + reason + " difficulty=" + ai.getDifficulty());
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

    private void logAiContractViolation(@NonNull String reason, @Nullable int[] move) {
        aiContractViolationCount++;
        String rawMove = move == null || move.length < 2
                ? "null" : move[0] + "," + move[1];
        Log.e(CONTRACT_TAG,
                "reason=" + reason
                        + " difficulty=" + ai.getDifficulty()
                        + " generation=" + aiGeneration
                        + " move=" + rawMove
                        + " count=" + aiContractViolationCount);
    }

    private void cancelPendingAi(@NonNull String reason) {
        aiGeneration++;
        aiThinking = false;
        if (ai != null) ai.cancel();
        Log.d(TAG, "AI generation invalidated: " + reason + " gen=" + aiGeneration);
        updatePassButton();
    }

    private void setSimpleBoardMode(boolean simpleMode) {
        GoUiPreferences.setSimpleMode(this, simpleMode);
        if (goView != null) goView.setSimpleMode(simpleMode);
        if (simpleBoardCheck != null && simpleBoardCheck.isChecked() != simpleMode) {
            simpleBoardCheck.setChecked(simpleMode);
        }
        updateBoardStyleControls();
        renderGameMeta();
    }

    private void updateBoardStyleControls() {
        boolean simpleMode = goView != null
                ? goView.isSimpleMode() : GoUiPreferences.isSimpleMode(this);
        if (boardStyleButton != null) {
            boardStyleButton.setText(simpleMode ? "棋盘：简洁" : "棋盘：增强");
        }
    }

    private void updatePassButton() {
        if (passButton == null || game == null) return;
        passButton.setEnabled(isGameRunning && !aiThinking && !game.isGameOver()
                && game.getCurrentPlayer() == GoGame.BLACK);
        passButton.setAlpha(passButton.isEnabled() ? 1f : 0.45f);
    }

    private void renderGameMeta() {
        if (tvMeta == null || game == null || ai == null) return;
        String turn;
        if (!isGameRunning) turn = game.isGameOver() ? "已结束" : "等待开始";
        else turn = game.getCurrentPlayer() == GoGame.BLACK ? "黑方回合" : "白方回合";
        boolean simpleMode = goView != null && goView.isSimpleMode();
        tvMeta.setText(getDifficultyName(ai.getDifficulty())
                + " · " + moveCount + " 手 · " + turn
                + " · 贴目 " + String.format(Locale.ROOT, "%.1f", GoGame.KOMI)
                + " · " + (simpleMode ? "简洁棋盘" : "增强棋盘"));
    }

    private void resign() {
        if (game.isGameOver() || !isGameRunning) return;
        cancelPendingAi("resign");
        game.setGameOver(true);
        isGameRunning = false;
        // 2026-08-23 P2-2：认输结束对局，清除存档
        if (saveManager != null) saveManager.clear(getGameId());
        tvStatus.setText(R.string.game_go_you_resigned);
        winStreak = 0;
        usageStore.recordLoss(getGameId());
        if (gameStartTime > 0) {
            usageStore.recordPlayTime(getGameId(), System.currentTimeMillis() - gameStartTime);
        }
        
        GoGame.Score score = game.calculateScore();
        updatePassButton();
        renderGameMeta();
        showGameEndDialog(false, score.getBlackScore(), score.getWhiteScore());

        // P2-7: 结束录制并提供回放（认输=负）
        offerReplay(com.gamecenter.app.games.replay.ReplayRecorder.RESULT_LOSS);
    }

    /**
     * P2-7: 结束回放录制并弹出回放查看对话框。
     *
     * @param result 对局结果（{@link com.gamecenter.app.games.replay.ReplayRecorder#RESULT_WIN} /
     *               {@link com.gamecenter.app.games.replay.ReplayRecorder#RESULT_LOSS} /
     *               {@link com.gamecenter.app.games.replay.ReplayRecorder#RESULT_DRAW}）
     */
    private void offerReplay(String result) {
        if (replayRecorder != null && replayRecorder.isRecording()) {
            replayRecorder.endRecording(result);
        }
        if (replayRecorder != null && replayRecorder.hasHistory()) {
            new AlertDialog.Builder(this)
                    .setTitle("对局结束")
                    .setMessage("是否查看回放？")
                    .setPositiveButton("查看回放", (d, w) -> {
                        com.gamecenter.app.games.replay.ReplayRecord rec = replayRecorder.loadLatest();
                        com.gamecenter.app.games.replay.ReplayDialog.show(this, rec,
                                new com.gamecenter.app.games.replay.ReplayPlayer.Listener() {
                                    @Override
                                    public void onBoardUpdated(int step,
                                                                List<com.gamecenter.app.games.replay.ReplayMove> played) {
                                        // 重置棋盘并按已播放走法重建局面
                                        game.startNewGame();
                                        for (com.gamecenter.app.games.replay.ReplayMove m : played) {
                                            if (m.toRow == -1 && m.toCol == -1) {
                                                // pass 标记
                                                game.passMove();
                                            } else {
                                                game.playMove(m.toRow, m.toCol);
                                            }
                                        }
                                        goView.hideTerritory();
                                        goView.setBoard(game.getBoard());
                                    }

                                    @Override
                                    public void onReplayFinished() {}

                                    @Override
                                    public void onReplayReset() {}
                                });
                    })
                    .setNegativeButton("关闭", null)
                    .show();
        }
    }

    private void onGameEnd() {
        isGameRunning = false;
        cancelPendingAi("game_end");
        // 2026-08-23 P2-2：对局正常结束，清除存档
        if (saveManager != null) saveManager.clear(getGameId());

        GoGame.Score score = game.calculateScore();
        double blackTerritory = score.getBlackScore();
        double whiteTerritory = score.getWhiteScore();

        float[][] territory = game.calculateTerritory();
        goView.showTerritory(territory);

        boolean playerWins = score.isBlackWinner();
        double blackPercent = blackTerritory / (GoGame.BOARD_SIZE * GoGame.BOARD_SIZE) * 100;

        if (playerWins) {
            totalWins++;
            winStreak++;
            tvStatus.setText(getString(R.string.game_go_you_win, blackTerritory, whiteTerritory));
            usageStore.recordWin(getGameId());
            // 2026-08-23 P3：胜利反馈
            if (feedback != null) feedback.feedbackWin();

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
            tvStatus.setText(getString(R.string.game_go_ai_wins, blackTerritory, whiteTerritory));
            usageStore.recordLoss(getGameId());
            // 2026-08-23 P3：失败反馈
            if (feedback != null) feedback.feedbackLose();
        }

        if (gameStartTime > 0) {
            usageStore.recordPlayTime(getGameId(), System.currentTimeMillis() - gameStartTime);
        }

        // 最高分持久化（按累计得分记录）
        recordHighScore(currentScore);

        updatePassButton();
        renderGameMeta();
        Log.i(TAG, "GO_GAME_END difficulty=" + ai.getDifficulty()
                + " ply=" + moveCount + " undoCount=0 restartCount=" + restartCount
                + " rawIllegal=" + aiContractViolationCount
                + " fallback=" + aiFallbackCount);
        showGameEndDialog(playerWins, blackTerritory, whiteTerritory);

        // P2-7: 结束录制并提供回放，结果按目数比较判定
        String replayResult;
        if (blackTerritory > whiteTerritory) {
            replayResult = com.gamecenter.app.games.replay.ReplayRecorder.RESULT_WIN;
        } else if (blackTerritory < whiteTerritory) {
            replayResult = com.gamecenter.app.games.replay.ReplayRecorder.RESULT_LOSS;
        } else {
            replayResult = com.gamecenter.app.games.replay.ReplayRecorder.RESULT_DRAW;
        }
        offerReplay(replayResult);
    }

    private void showGameEndDialog(boolean playerWins, double blackTerritory, double whiteTerritory) {
        long elapsed = gameStartTime > 0 ? (System.currentTimeMillis() - gameStartTime) : 0L;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.game_go_end_title);
        String winnerText = playerWins ? getString(R.string.game_go_end_win) : getString(R.string.game_go_end_lose);
        builder.setMessage(
                winnerText + "\n\n" +
                getString(R.string.game_go_end_moves) + ": " + moveCount + "\n" +
                getString(R.string.game_go_end_duration) + ": " + formatDuration(elapsed) + "\n" +
                getString(R.string.game_go_end_score, blackTerritory, whiteTerritory));
        builder.setPositiveButton(R.string.game_go_end_restart, (d, w) -> startNewGame());
        builder.setNegativeButton(R.string.game_go_back_home, (d, w) -> finish());
        builder.setCancelable(false);
        builder.show();
    }

    private String formatDuration(long ms) {
        long sec = ms / 1000L;
        return String.format(Locale.getDefault(), "%02d:%02d", sec / 60L, sec % 60L);
    }

    private void addDifficultyButtonsTo(LinearLayout parent) {
        TextView label = new TextView(this);
        label.setText(R.string.game_go_difficulty_label);
        label.setTextSize(13f);
        label.setTextColor(ContextCompat.getColor(this, R.color.game_go_color_label_text));
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
        int[] colorActive = {
                ContextCompat.getColor(this, R.color.game_go_color_diff_1),
                ContextCompat.getColor(this, R.color.game_go_color_diff_2),
                ContextCompat.getColor(this, R.color.game_go_color_diff_3),
                ContextCompat.getColor(this, R.color.game_go_color_diff_4)
        };
        int colorInactive = ContextCompat.getColor(this, R.color.game_go_color_diff_inactive);

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
                btn.setTextColor(ContextCompat.getColor(this, R.color.game_go_color_button_text));
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

        tvDifficultyDescription = new TextView(this);
        tvDifficultyDescription.setGravity(Gravity.CENTER);
        tvDifficultyDescription.setTextSize(12f);
        tvDifficultyDescription.setTextColor(
                ContextCompat.getColor(this, R.color.game_go_color_label_text));
        tvDifficultyDescription.setPadding(12, 2, 12, 10);
        parent.addView(tvDifficultyDescription);
    }

    public void setAiDifficulty(int level) {
        if (level < 1 || level > 4 || isGameRunning) return;
        ai.setDifficulty(level);
        updateDifficultyButtons();
        Toast.makeText(this, getString(R.string.game_go_ai_difficulty_toast, getDifficultyName(level)), Toast.LENGTH_SHORT).show();
        renderGameMeta();
    }

    private void updateDifficultyButtons() {
        int[] colorActive = {
                ContextCompat.getColor(this, R.color.game_go_color_diff_1),
                ContextCompat.getColor(this, R.color.game_go_color_diff_2),
                ContextCompat.getColor(this, R.color.game_go_color_diff_3),
                ContextCompat.getColor(this, R.color.game_go_color_diff_4)
        };
        int colorInactive = ContextCompat.getColor(this, R.color.game_go_color_diff_inactive);
        String[] names = {
                getString(R.string.game_go_diff_1),
                getString(R.string.game_go_diff_2),
                getString(R.string.game_go_diff_3),
                getString(R.string.game_go_diff_4)
        };
        for (int i = 0; i < difficultyButtons.size(); i++) {
            boolean selected = i + 1 == ai.getDifficulty();
            MaterialButton button = difficultyButtons.get(i);
            button.setText((selected ? "✓ " : "") + names[i]);
            button.setBackgroundColor(selected ? colorActive[i] : colorInactive);
            button.setAlpha(selected ? 1f : 0.72f);
        }
        if (tvDifficultyDescription != null) {
            int[] descriptions = {
                    R.string.game_go_diff_1_desc,
                    R.string.game_go_diff_2_desc,
                    R.string.game_go_diff_3_desc,
                    R.string.game_go_diff_4_desc
            };
            tvDifficultyDescription.setText(descriptions[ai.getDifficulty() - 1]);
        }
    }

    private String getDifficultyName(int level) {
        switch (level) {
            case 1: return getString(R.string.game_go_diff_name_1);
            case 2: return getString(R.string.game_go_diff_name_2);
            case 3: return getString(R.string.game_go_diff_name_3);
            case 4: return getString(R.string.game_go_diff_name_4);
            default: return getString(R.string.game_go_diff_name_unknown);
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
        cancelPendingAi("pause");
    }

    @Override
    protected void resumeGame() {
        isGamePaused = false;
        if (isGameRunning && !game.isGameOver()
                && game.getCurrentPlayer() == GoGame.WHITE) {
            startAiTurn();
        } else {
            updatePassButton();
        }
    }

    @Override
    protected void endGame() {
        isGameRunning = false;
        cancelPendingAi("end_game");
        handler.removeCallbacksAndMessages(null);
        if (aiExecutor != null) {
            aiExecutor.shutdownNow();
        }
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
        cancelPendingAi("destroy");
        handler.removeCallbacksAndMessages(null);
        if (aiExecutor != null) {
            aiExecutor.shutdownNow();
        }
        if (onboardingSequence != null) {
            onboardingSequence.destroy();
            onboardingSequence = null;
        }
        // 2026-08-23 P3：释放音效资源
        if (feedback != null) {
            feedback.release();
            feedback = null;
        }
        super.onDestroy();
    }

    /**
     * P1-内存：系统内存压力回调。
     * 后台 / 严重级别时取消 AI 搜索以减少 CPU/内存占用；UI_HIDDEN 不打断前台对局。
     */
    protected void onMemoryTrim(int level) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND && aiThinking) {
            Log.d(TAG, "[trim] 后台 trim(level=" + level + ")，取消 AI 搜索以释放 CPU/内存");
            cancelPendingAi("trim-background");
        }
    }
}
