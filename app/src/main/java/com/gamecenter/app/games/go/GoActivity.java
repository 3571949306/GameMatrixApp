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

    /** P2-7: 对局回放录制器 */
    private com.gamecenter.app.games.replay.ReplayRecorder replayRecorder;

    /** 新手引导序列（首次开始游戏后弹出，3 步引导） */
    private com.gamecenter.app.ui.onboarding.CoachmarkSequence onboardingSequence;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        game = new GoGame();
        ai = new GoAI();
        aiExecutor = Executors.newSingleThreadExecutor();
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
        if (gameContentContainer instanceof FrameLayout) {
            View contentView = createGameContentView();
            ((FrameLayout) gameContentContainer).addView(contentView);
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

        menuPanel = new LinearLayout(this);
        menuPanel.setOrientation(LinearLayout.VERTICAL);
        menuPanel.setGravity(Gravity.CENTER);

        addDifficultyButtonsTo(menuPanel);

        MaterialButton btnStart = new MaterialButton(this);
        btnStart.setText(R.string.game_go_start);
        btnStart.setBackgroundColor(ContextCompat.getColor(this, R.color.game_go_color_score_text));
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
        // 给棋盘打上稳定 id，供 Coachmark 定位（围棋新手引导第 1 步目标）
        goView.setId(R.id.go_board_view);

        addDifficultyButtonsTo(gamePanel);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setPadding(0, 16, 0, 0);
        // 给按钮行打上稳定 id，供 Coachmark 定位（围棋新手引导第 3 步目标）
        btnRow.setId(R.id.go_buttons_view);

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

        // P2-7: 开始回放录制
        replayRecorder = new com.gamecenter.app.games.replay.ReplayRecorder(this, getGameId());
        replayRecorder.startRecording(ai.getDifficulty());

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
            goView.setBoard(game.getBoard());
            goView.setLastMove(row, col);
            updateScoreDisplay();
            startAiTurn();
        }
    }

    /**
     * 启动 AI 回合。
     * <p>将耗时搜索（大师难度 MCTS 约 1.5s）放到后台线程执行，
     * 计算完成后通过主线程 Handler 回写棋盘，彻底消除主线程卡顿/ANR。</p>
     */
    private void startAiTurn() {
        if (aiThinking) return;
        aiThinking = true;
        aiThinkStartMs = System.currentTimeMillis();
        tvStatus.setText(getString(R.string.game_go_ai_thinking_with_difficulty, getDifficultyName(ai.getDifficulty())));

        final long gen = ++aiGeneration;
        // 提交前确保线程池可用（endGame/onDestroy 已 shutdown 则重建，避免 RejectedExecutionException）
        ensureAiExecutor();
        aiExecutor.execute(() -> {
            if (gen != aiGeneration) return;
            if (game.isGameOver()) {
                handler.post(() -> aiThinking = false);
                return;
            }
            int[] bestMove = ai.findBestAiMove(game);
            long thinkMs = System.currentTimeMillis() - aiThinkStartMs;
            Log.i("GoAI", "难度=" + ai.getDifficulty() + " 思考耗时=" + thinkMs + "ms");
            handler.post(() -> applyAiMove(bestMove, thinkMs, gen));
        });
    }

    /**
     * 主线程回写 AI 着法结果。
     */
    private void applyAiMove(int[] bestMove, long thinkMs, long gen) {
        if (gen != aiGeneration) return;
        if (game.isGameOver()) {
            aiThinking = false;
            return;
        }

        if (bestMove == null) {
            game.passMove();
            // P2-7: 记录 AI 弃权（pass），用 (-1,-1) 标记
            if (replayRecorder != null && replayRecorder.isRecording()) {
                replayRecorder.record(new com.gamecenter.app.games.replay.ReplayMove(-1, -1, GoGame.WHITE));
            }
            tvStatus.setText(R.string.game_go_ai_passed);
        } else {
            game.playMove(bestMove[0], bestMove[1]);
            // P2-7: 记录 AI 落子
            if (replayRecorder != null && replayRecorder.isRecording()) {
                replayRecorder.record(new com.gamecenter.app.games.replay.ReplayMove(bestMove[0], bestMove[1], GoGame.WHITE));
            }
            goView.setBoard(game.getBoard());
            goView.setLastMove(bestMove[0], bestMove[1]);

            if (ai.getDifficulty() >= 4 && thinkMs > 100) {
                Toast.makeText(this, getString(R.string.game_go_ai_think_ms, thinkMs), Toast.LENGTH_SHORT).show();
            }
        }

        if (game.isGameOver()) {
            aiThinking = false;
            onGameEnd();
            return;
        }

        aiThinking = false;
        tvStatus.setText(R.string.game_go_your_turn);
        updateScoreDisplay();
    }

    private void passMove() {
        if (game.isGameOver() || !isGameRunning) return;
        game.passMove();
        // P2-7: 记录玩家弃权（pass），用 (-1,-1) 标记
        if (replayRecorder != null && replayRecorder.isRecording()) {
            replayRecorder.record(new com.gamecenter.app.games.replay.ReplayMove(-1, -1, GoGame.BLACK));
        }
        if (game.isGameOver()) {
            onGameEnd();
            return;
        }
        tvStatus.setText(R.string.game_go_ai_thinking);
        startAiTurn();
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

        // 最高分持久化（按累计得分记录）
        recordHighScore(currentScore);

        showGameEndDialog(playerWins, (int) blackTerritory, (int) whiteTerritory);

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

    private void showGameEndDialog(boolean playerWins, int blackTerritory, int whiteTerritory) {
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
    }

    public void setAiDifficulty(int level) {
        ai.setDifficulty(level);
        int[] colorActive = {
                ContextCompat.getColor(this, R.color.game_go_color_diff_1),
                ContextCompat.getColor(this, R.color.game_go_color_diff_2),
                ContextCompat.getColor(this, R.color.game_go_color_diff_3),
                ContextCompat.getColor(this, R.color.game_go_color_diff_4)
        };
        int colorInactive = ContextCompat.getColor(this, R.color.game_go_color_diff_inactive);
        for (int i = 0; i < difficultyButtons.size(); i++) {
            difficultyButtons.get(i).setBackgroundColor(
                    i + 1 == level ? colorActive[i] : colorInactive);
        }
        Toast.makeText(this, getString(R.string.game_go_ai_difficulty_toast, getDifficultyName(level)), Toast.LENGTH_SHORT).show();
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
    }

    @Override
    protected void resumeGame() {
        isGamePaused = false;
    }

    @Override
    protected void endGame() {
        isGameRunning = false;
        aiGeneration++;
        aiThinking = false;
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
        super.onDestroy();
        aiGeneration++;
        handler.removeCallbacksAndMessages(null);
        if (aiExecutor != null) {
            aiExecutor.shutdownNow();
        }
        if (onboardingSequence != null) {
            onboardingSequence.destroy();
            onboardingSequence = null;
        }
    }
}
