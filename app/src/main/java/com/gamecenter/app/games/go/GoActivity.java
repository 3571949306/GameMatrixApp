package com.gamecenter.app.games.go;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.gamecenter.app.R;
import com.gamecenter.app.games.base.BaseGameActivity;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 围棋游戏 Activity（简化版 9×9）。
 *
 * <p>支持简化围棋规则：落子、提子、禁入、领地评估。
 * 使用简单 AI 对手。</p>
 *
 * <p>成就系统：
 * <ul>
 *   <li>首次胜利</li>
 *   <li>领地超过 50%</li>
 *   <li>吃子 10 目</li>
 *   <li>无失误胜利</li>
 *   <li>连胜 5 局</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class GoActivity extends BaseGameActivity {

    private static final int BOARD_SIZE = 9;
    private static final int EMPTY = GoView.EMPTY;
    private static final int BLACK = GoView.BLACK;
    private static final int WHITE = GoView.WHITE;
    private static final int PASS_MOVE = -1;

    /** 最大连续虚着（双方都 pass）次数，达到则终局 */
    private static final int MAX_CONSECUTIVE_PASSES = 2;

    /** 贴目 */
    private static final float KOMI = 6.5f;

    // 游戏状态
    private int[][] board = new int[BOARD_SIZE][BOARD_SIZE];
    private int[][] previousBoard = null; // 用于劫争判断
    private int currentPlayer = BLACK; // 黑先
    private int capturedByBlack = 0; // 黑方提子数
    private int capturedByWhite = 0; // 白方提子数
    private int consecutivePasses = 0;
    private int totalCaptures = 0;
    private int totalWins = 0;
    private int winStreak = 0;
    private boolean gameOver = false;
    // 2026-06-23: 步数统计（玩家落子数，用于游戏结束 Dialog）
    private int moveCount = 0;
    // 2026-06-23: AI 难度选择（1=简单/2=普通/3=困难/4=大师）
    private int aiDifficulty = 2;
    // 难度按钮列表（用于切换选中状态）
    private final java.util.List<com.google.android.material.button.MaterialButton> difficultyButtons = new java.util.ArrayList<>();

    private Handler handler = new Handler(Looper.getMainLooper());
    private Random random = new Random();

    // UI 组件
    private GoView goView;
    private TextView tvStatus;
    private TextView tvScore;
    private LinearLayout gamePanel;
    private LinearLayout menuPanel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    // ==================== BaseGameActivity 抽象方法实现 ====================

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

        // 菜单面板
        menuPanel = new LinearLayout(this);
        menuPanel.setOrientation(LinearLayout.VERTICAL);
        menuPanel.setGravity(Gravity.CENTER);

        // 2026-06-23: 难度选择 2x2 网格
        addDifficultyButtonsTo(menuPanel);

        MaterialButton btnStart = new MaterialButton(this);
        btnStart.setText(R.string.game_go_start);
        btnStart.setBackgroundColor(0xFF5B8A72);
        btnStart.setOnClickListener(v -> startNewGame());
        menuPanel.addView(btnStart);

        // 游戏面板
        gamePanel = new LinearLayout(this);
        gamePanel.setOrientation(LinearLayout.VERTICAL);
        gamePanel.setGravity(Gravity.CENTER);
        gamePanel.setVisibility(View.GONE);

        goView = new GoView(this);
        int viewWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
        goView.setLayoutParams(new FrameLayout.LayoutParams(viewWidth, viewWidth));
        goView.setOnCellClickListener(this::onCellClick);

        // 2026-06-23: 游戏中难度切换条
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

    /**
     * 开始新游戏
     */
    private void startNewGame() {
        board = new int[BOARD_SIZE][BOARD_SIZE];
        previousBoard = null;
        currentPlayer = BLACK;
        capturedByBlack = 0;
        capturedByWhite = 0;
        consecutivePasses = 0;
        gameOver = false;

        menuPanel.setVisibility(View.GONE);
        gamePanel.setVisibility(View.VISIBLE);
        tvStatus.setText(R.string.game_go_your_turn);
        updateScoreDisplay();

        goView.hideTerritory();
        goView.setBoard(board);

        isGameRunning = true;
        gameStartTime = System.currentTimeMillis();
    }

    /**
     * 处理落子
     */
    private void onCellClick(int row, int col) {
        if (gameOver || !isGameRunning) return;
        if (currentPlayer != BLACK) return;
        if (board[row][col] != EMPTY) return;

        // 检查合法性（禁入规则）
        if (!isValidMove(row, col, BLACK)) return;

        // 保存当前棋盘用于劫争判断
        previousBoard = copyBoard(board);

        // 落子
        board[row][col] = BLACK;
        moveCount++;
        consecutivePasses = 0;

        // 提子
        int captured = removeCapturedStones(WHITE, row, col);
        capturedByBlack += captured;
        totalCaptures += captured;

        goView.setBoard(board);
        goView.setLastMove(row, col);
        updateScoreDisplay();

        // AI 回合
        currentPlayer = WHITE;
        // 2026-06-23: 显示当前难度（"AI 思考中...（困难）"）
        tvStatus.setText(getString(R.string.game_go_ai_thinking_with_difficulty,
                getDifficultyName(aiDifficulty)));
        // 2026-06-23: 性能监控 — 记录思考开始时间
        aiThinkStartMs = System.currentTimeMillis();
        handler.postDelayed(this::aiMove, 300 + random.nextInt(500));
    }

    /** AI 思考开始时间（用于耗时统计） */
    private long aiThinkStartMs = 0L;

    /**
     * AI 落子
     */
    private void aiMove() {
        if (gameOver) return;

        // 2026-06-23: 性能监控 — 记录 AI 思考耗时
        long thinkMs = System.currentTimeMillis() - aiThinkStartMs;
        android.util.Log.i("GoAI", "难度=" + aiDifficulty + " (" + getDifficultyName(aiDifficulty) + ")"
                + " 思考耗时=" + thinkMs + "ms");

        // 简单 AI：基于领地评估选择落子
        int[] bestMove = findBestAiMove();
        if (bestMove == null) {
            // AI pass
            consecutivePasses++;
            tvStatus.setText(R.string.game_go_ai_passed);
        } else {
            previousBoard = copyBoard(board);
            board[bestMove[0]][bestMove[1]] = WHITE;
            consecutivePasses = 0;

            int captured = removeCapturedStones(BLACK, bestMove[0], bestMove[1]);
            capturedByWhite += captured;

            goView.setBoard(board);
            goView.setLastMove(bestMove[0], bestMove[1]);

            // 2026-06-23: 大师难度显示思考时长（替代进度条）
            if (aiDifficulty >= 4 && thinkMs > 100) {
                android.widget.Toast.makeText(this,
                        "AI 思考 " + thinkMs + "ms",
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        }

        // 检查终局
        if (consecutivePasses >= MAX_CONSECUTIVE_PASSES) {
            onGameEnd();
            return;
        }

        currentPlayer = BLACK;
        tvStatus.setText(R.string.game_go_your_turn);
        updateScoreDisplay();
    }

    /**
     * AI 找到最佳落子位置（4 档难度，2026-06-23 新增）。
     * - 1 (简单): 纯随机合法位置
     * - 2 (普通): 贪心 capture + 位置评估 + 随机扰动
     * - 3 (困难): 贪心 + Minimax depth=2（看对手反应）
     * - 4 (大师): 贪心 + Minimax depth=3 + 领地评估
     */
    private int[] findBestAiMove() {
        return switch (aiDifficulty) {
            case 1 -> findRandomAiMove();
            case 3 -> findMinimaxAiMove(2);
            case 4 -> findMinimaxAiMove(3);
            default -> findGreedyAiMove();
        };
    }

    /**
     * 简单：纯随机合法位置
     */
    private int[] findRandomAiMove() {
        List<int[]> candidates = new ArrayList<>();
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] == EMPTY && isValidMove(r, c, WHITE)) {
                    candidates.add(new int[]{r, c});
                }
            }
        }
        if (candidates.isEmpty()) return null;
        // 10% 概率 pass
        if (random.nextInt(10) == 0 && consecutivePasses == 0) return null;
        return candidates.get(random.nextInt(candidates.size()));
    }

    /**
     * 普通：贪心 capture*10 + 位置评估 + 随机扰动
     */
    private int[] findGreedyAiMove() {
        int bestScore = Integer.MIN_VALUE;
        int[] bestMove = null;
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] == EMPTY && isValidMove(r, c, WHITE)) {
                    int[][] simulated = copyBoard(board);
                    simulated[r][c] = WHITE;
                    int captured = simulateCapture(simulated, BLACK, r, c);
                    int score = captured * 10 + evaluatePosition(r, c) + random.nextInt(3);
                    if (score > bestScore) {
                        bestScore = score;
                        bestMove = new int[]{r, c};
                    }
                }
            }
        }
        if (bestMove != null && random.nextInt(10) < 2 && consecutivePasses == 0) return null;
        return bestMove;
    }

    /**
     * 困难/大师：Minimax 深度搜索。
     * depth=2 看自己+对手反应；depth=3 多看一步。
     */
    private int[] findMinimaxAiMove(int depth) {
        int bestScore = Integer.MIN_VALUE;
        int[] bestMove = null;
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] != EMPTY || !isValidMove(r, c, WHITE)) continue;
                int[][] simulated = copyBoard(board);
                simulated[r][c] = WHITE;
                int captured = simulateCapture(simulated, BLACK, r, c);
                int score = captured * 10 + evaluatePosition(r, c)
                        + minimax(simulated, depth - 1, false, Integer.MIN_VALUE, Integer.MAX_VALUE);
                // 简单难度加随机扰动；大师级别不加
                if (aiDifficulty < 4) score += random.nextInt(2);
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = new int[]{r, c};
                }
            }
        }
        if (bestMove != null && aiDifficulty < 4 && random.nextInt(10) < 2 && consecutivePasses == 0) {
            return null;
        }
        return bestMove;
    }

    /**
     * 极小极大搜索（alpha-beta 剪枝）。
     * @param isMax 当前层是否是 AI 最大化
     */
    private int minimax(int[][] state, int depth, boolean isMax, int alpha, int beta) {
        if (depth == 0) return evaluateBoard(state);
        int best = isMax ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        int color = isMax ? WHITE : BLACK;
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (state[r][c] != EMPTY) continue;
                if (!isValidMove(state, r, c, color)) continue;
                int[][] sim = copyBoard(state);
                sim[r][c] = color;
                simulateCapture(sim, color == WHITE ? BLACK : WHITE, r, c);
                int val = minimax(sim, depth - 1, !isMax, alpha, beta);
                if (isMax) {
                    best = Math.max(best, val);
                    alpha = Math.max(alpha, best);
                } else {
                    best = Math.min(best, val);
                    beta = Math.min(beta, best);
                }
                if (beta <= alpha) return best;
            }
        }
        return best;
    }

    /**
     * 评估整个棋盘：白方领地 - 黑方领地（白方 = AI）
     */
    private int evaluateBoard(int[][] state) {
        int whiteScore = 0, blackScore = 0;
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (state[r][c] == WHITE) whiteScore += 10;
                else if (state[r][c] == BLACK) blackScore += 10;
            }
        }
        return whiteScore - blackScore;
    }

    /**
     * 设置 AI 难度（1-4），游戏中切换立即生效。
     */
    public void setAiDifficulty(int level) {
        if (level < 1 || level > 4) return;
        this.aiDifficulty = level;
        // 更新按钮选中状态
        int[] colorActive = {0xFF5B8A72, 0xFFFFA726, 0xFFEF5350, 0xFF8E24AA};
        int colorInactive = 0xFF9E9E9E;
        for (int i = 0; i < difficultyButtons.size(); i++) {
            difficultyButtons.get(i).setBackgroundColor(
                    i + 1 == level ? colorActive[i] : colorInactive);
        }
        android.widget.Toast.makeText(this,
                "AI 难度: " + getDifficultyName(level),
                android.widget.Toast.LENGTH_SHORT).show();
    }

    private String getDifficultyName(int level) {
        switch (level) {
            case 1: return "简单（随机）";
            case 2: return "普通（贪心）";
            case 3: return "困难（Minimax-2）";
            case 4: return "大师（Minimax-3）";
            default: return "未知";
        }
    }

    /**
     * 评估位置价值
     */
    private int evaluatePosition(int row, int col) {
        int score = 0;
        // 中心位置价值更高
        int centerDist = Math.abs(row - 4) + Math.abs(col - 4);
        score += (8 - centerDist) * 2;

        // 边角位置价值较低
        if (row == 0 || row == BOARD_SIZE - 1) score -= 3;
        if (col == 0 || col == BOARD_SIZE - 1) score -= 3;

        // 相邻已有己方棋子加分
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            int nr = row + d[0];
            int nc = col + d[1];
            if (nr >= 0 && nr < BOARD_SIZE && nc >= 0 && nc < BOARD_SIZE) {
                if (board[nr][nc] == WHITE) score += 2;
            }
        }

        return score;
    }

    /**
     * 检查落子是否合法
     */
    private boolean isValidMove(int row, int col, int color) {
        if (board[row][col] != EMPTY) return false;

        // 模拟落子
        int[][] simulated = copyBoard(board);
        simulated[row][col] = color;

        // 检查是否有气
        int opponent = color == BLACK ? WHITE : BLACK;
        int captured = simulateCapture(simulated, opponent, row, col);

        // 落子后自身是否有气
        if (countLiberties(simulated, row, col) == 0 && captured == 0) {
            return false; // 自杀
        }

        // 劫争检查
        if (previousBoard != null && boardsEqual(simulated, previousBoard)) {
            return false;
        }

        return true;
    }

    /**
     * 2026-06-23: 接收 board 参数的 isValidMove（用于 Minimax 搜索中的模拟棋盘）
     * 劫争检查不适用模拟过程（previousBoard 是 main 的状态）
     */
    private boolean isValidMove(int[][] state, int row, int col, int color) {
        if (state[row][col] != EMPTY) return false;
        int[][] simulated = copyBoard(state);
        simulated[row][col] = color;
        int opponent = color == BLACK ? WHITE : BLACK;
        int captured = simulateCapture(simulated, opponent, row, col);
        if (countLiberties(simulated, row, col) == 0 && captured == 0) return false;
        return true;
    }

    /**
     * 模拟提子并返回提子数
     */
    private int simulateCapture(int[][] sim, int opponent, int row, int col) {
        int totalCaptured = 0;
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            int nr = row + d[0];
            int nc = col + d[1];
            if (nr >= 0 && nr < BOARD_SIZE && nc >= 0 && nc < BOARD_SIZE
                    && sim[nr][nc] == opponent) {
                if (countLiberties(sim, nr, nc) == 0) {
                    totalCaptured += removeGroup(sim, nr, nc);
                }
            }
        }
        return totalCaptured;
    }

    /**
     * 移除被吃掉的棋子（实际棋盘）
     */
    private int removeCapturedStones(int opponent, int row, int col) {
        int totalCaptured = 0;
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            int nr = row + d[0];
            int nc = col + d[1];
            if (nr >= 0 && nr < BOARD_SIZE && nc >= 0 && nc < BOARD_SIZE
                    && board[nr][nc] == opponent) {
                if (countLiberties(board, nr, nc) == 0) {
                    totalCaptured += removeGroup(board, nr, nc);
                }
            }
        }
        return totalCaptured;
    }

    /**
     * 计算棋子（组）的气数
     */
    private int countLiberties(int[][] grid, int row, int col) {
        boolean[][] visited = new boolean[BOARD_SIZE][BOARD_SIZE];
        int[] liberties = {0};
        countLibertiesDFS(grid, row, col, grid[row][col], visited, liberties);
        return liberties[0];
    }

    private void countLibertiesDFS(int[][] grid, int row, int col, int color,
                                   boolean[][] visited, int[] liberties) {
        if (row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE) return;
        if (visited[row][col]) return;
        if (grid[row][col] != color && grid[row][col] != EMPTY) return;

        if (grid[row][col] == EMPTY) {
            liberties[0]++;
            return;
        }

        visited[row][col] = true;
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            countLibertiesDFS(grid, row + d[0], col + d[1], color, visited, liberties);
        }
    }

    /**
     * 移除一个棋子组
     */
    private int removeGroup(int[][] grid, int row, int col) {
        int color = grid[row][col];
        if (color == EMPTY) return 0;
        boolean[][] visited = new boolean[BOARD_SIZE][BOARD_SIZE];
        return removeGroupDFS(grid, row, col, color, visited);
    }

    private int removeGroupDFS(int[][] grid, int row, int col, int color, boolean[][] visited) {
        if (row < 0 || row >= BOARD_SIZE || col < 0 || col >= BOARD_SIZE) return 0;
        if (visited[row][col] || grid[row][col] != color) return 0;

        visited[row][col] = true;
        grid[row][col] = EMPTY;
        int count = 1;

        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            count += removeGroupDFS(grid, row + d[0], col + d[1], color, visited);
        }
        return count;
    }

    /**
     * Pass
     */
    private void passMove() {
        if (gameOver || !isGameRunning) return;
        consecutivePasses++;
        if (consecutivePasses >= MAX_CONSECUTIVE_PASSES) {
            onGameEnd();
            return;
        }
        currentPlayer = WHITE;
        tvStatus.setText(R.string.game_go_ai_thinking);
        handler.postDelayed(this::aiMove, 300);
    }

    /**
     * 认输
     */
    private void resign() {
        if (gameOver) return;
        gameOver = true;
        isGameRunning = false;
        tvStatus.setText(R.string.game_go_you_resigned);
        winStreak = 0;
        usageStore.recordLoss(getGameId());
        if (gameStartTime > 0) {
            usageStore.recordPlayTime(getGameId(), System.currentTimeMillis() - gameStartTime);
        }
        // 2026-06-23: 认输也弹 Dialog（用当前领地估算分数）
        int blackT = countTerritory(BLACK) + capturedByBlack;
        int whiteT = countTerritory(WHITE) + capturedByWhite + (int) KOMI;
        showGameEndDialog(false, blackT, whiteT);
    }

    /**
     * 终局处理
     */
    private void onGameEnd() {
        gameOver = true;
        isGameRunning = false;

        // 简化计分：统计领地
        float blackTerritory = countTerritory(BLACK) + capturedByBlack;
        float whiteTerritory = countTerritory(WHITE) + capturedByWhite + KOMI;

        // 显示领地
        float[][] territory = calculateTerritory();
        goView.showTerritory(territory);

        boolean playerWins = blackTerritory > whiteTerritory;
        float blackPercent = blackTerritory / (BOARD_SIZE * BOARD_SIZE) * 100;

        if (playerWins) {
            totalWins++;
            winStreak++;
            tvStatus.setText(getString(R.string.game_go_you_win,
                    (int) blackTerritory, (int) whiteTerritory));
            usageStore.recordWin(getGameId());

            checkAchievement("win", totalWins);
            checkAchievement("score", (int) blackPercent);
            checkAchievement("streak", winStreak);
            if (capturedByBlack > 0) {
                checkAchievement("special", true);
            }
            // 2026-06-23: 大师难度专用成就（"棋道巅峰"）
            if (aiDifficulty == 4) {
                checkAchievement("master_win", 1);
                updateScore(currentScore + 500); // 大师难度奖励更高
            } else {
                updateScore(currentScore + 300);
            }
        } else {
            winStreak = 0;
            tvStatus.setText(getString(R.string.game_go_ai_wins,
                    (int) blackTerritory, (int) whiteTerritory));
            usageStore.recordLoss(getGameId());
        }

        if (gameStartTime > 0) {
            usageStore.recordPlayTime(getGameId(), System.currentTimeMillis() - gameStartTime);
        }

        // 2026-06-23: 弹出游戏结束总结 Dialog
        showGameEndDialog(playerWins, (int) blackTerritory, (int) whiteTerritory);
    }

    /**
     * 2026-06-23：游戏结束 Dialog（围棋终局后显示战绩）。
     * 含步数、用时、双方领地、胜负结果。
     */
    private void showGameEndDialog(boolean playerWins, int blackTerritory, int whiteTerritory) {
        long elapsed = gameStartTime > 0 ? (System.currentTimeMillis() - gameStartTime) : 0L;
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(R.string.game_go_end_title);
        String winnerText = playerWins
                ? getString(R.string.game_go_end_win)
                : getString(R.string.game_go_end_lose);
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

    /** 格式化毫秒为 mm:ss */
    private String formatDuration(long ms) {
        long sec = ms / 1000L;
        return String.format("%02d:%02d", sec / 60L, sec % 60L);
    }

    /**
     * 2026-06-23: 添加 4 个 AI 难度选择按钮到指定容器。
     * 2x2 网格布局，选中状态用不同背景色。
     * 同一组按钮共享 difficultyButtons 列表，实现选中状态联动。
     */
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
                int idx = row * 2 + col + 1; // 1-4
                MaterialButton btn = new MaterialButton(this);
                btn.setText(names[idx - 1]);
                btn.setTextSize(12f);
                btn.setBackgroundColor(idx == aiDifficulty ? colorActive[idx - 1] : colorInactive);
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

    /**
     * 简化领地计算
     */
    private int countTerritory(int color) {
        int count = 0;
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] == color) count++;
            }
        }
        return count;
    }

    /**
     * 计算领地标记
     */
    private float[][] calculateTerritory() {
        float[][] territory = new float[BOARD_SIZE][BOARD_SIZE];
        boolean[][] visited = new boolean[BOARD_SIZE][BOARD_SIZE];

        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] == EMPTY && !visited[r][c]) {
                    List<int[]> region = new ArrayList<>();
                    int borderBlack = 0;
                    int borderWhite = 0;
                    floodFill(r, c, visited, region, new int[]{0, 0});

                    for (int[] cell : region) {
                        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
                        for (int[] d : dirs) {
                            int nr = cell[0] + d[0];
                            int nc = cell[1] + d[1];
                            if (nr >= 0 && nr < BOARD_SIZE && nc >= 0 && nc < BOARD_SIZE) {
                                if (board[nr][nc] == BLACK) borderBlack = 1;
                                if (board[nr][nc] == WHITE) borderWhite = 1;
                            }
                        }
                    }

                    float owner = 0;
                    if (borderBlack > 0 && borderWhite == 0) owner = -1;
                    if (borderWhite > 0 && borderBlack == 0) owner = 1;

                    for (int[] cell : region) {
                        territory[cell[0]][cell[1]] = owner;
                    }
                }
            }
        }
        return territory;
    }

    private void floodFill(int r, int c, boolean[][] visited, List<int[]> region, int[] count) {
        if (r < 0 || r >= BOARD_SIZE || c < 0 || c >= BOARD_SIZE) return;
        if (visited[r][c] || board[r][c] != EMPTY) return;
        visited[r][c] = true;
        region.add(new int[]{r, c});
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            floodFill(r + d[0], c + d[1], visited, region, count);
        }
    }

    private void updateScoreDisplay() {
        tvScore.setText(getString(R.string.game_go_score_display,
                capturedByBlack, capturedByWhite, currentPlayer == BLACK ? "●" : "○"));
    }

    private int[][] copyBoard(int[][] src) {
        int[][] dst = new int[BOARD_SIZE][BOARD_SIZE];
        for (int r = 0; r < BOARD_SIZE; r++) {
            System.arraycopy(src[r], 0, dst[r], 0, BOARD_SIZE);
        }
        return dst;
    }

    private boolean boardsEqual(int[][] a, int[][] b) {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (a[r][c] != b[r][c]) return false;
            }
        }
        return true;
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
