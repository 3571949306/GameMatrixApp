package com.gamecenter.app.games.tic;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.gamecenter.app.R;
import com.gamecenter.app.games.base.BaseGameActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 井字棋游戏 Activity。
 *
 * <p>继承 BaseGameActivity，实现 3x3 井字棋人机对战。
 * AI 支持简单（随机+少量防守）、中等（深度受限 Minimax）和困难（完美 Minimax）三种模式。</p>
 *
 * <p>难度梯度：
 * <ul>
 *   <li>简单：80% 随机 + 20% 必胜/必堵手，新手友好</li>
 *   <li>中等：Minimax 限制深度=2 + 中心/角偏好，可被战胜</li>
 *   <li>困难：完整 Minimax 必胜或必平，含首手开局库加权随机</li>
 * </ul>
 * </p>
 *
 * <p>开局多样性：困难档首手从 4 角(w3)+4 边(w1)+中心(w2) 中加权随机选，
 * 避免每次都走中心导致开局千篇一律。</p>
 *
 * <p>成就系统：
 * <ul>
 *   <li>首次胜利</li>
 *   <li>连胜 3 局</li>
 *   <li>连胜 10 局</li>
 *   <li>平局大师（累计 10 次平局）</li>
 *   <li>击败困难 AI</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.1
 * @since 2026-06-20
 */
public class TicTacToeActivity extends BaseGameActivity {

    private static final int BOARD_SIZE = 3;
    private static final int PLAYER_X = 1; // 人类
    private static final int PLAYER_O = 2; // AI

    /** P2-7: 对局回放录制器 */
    private com.gamecenter.app.games.replay.ReplayRecorder replayRecorder;

    /** 游戏棋盘：0=空, 1=X(人), 2=O(AI) */
    private int[][] board = new int[BOARD_SIZE][BOARD_SIZE];

    /** 当前轮到谁：true=人类回合 */
    private boolean isPlayerTurn = true;

    /** 游戏是否已结束 */
    private boolean isGameOver = false;

    /** 胜利者：0=无, 1=玩家, 2=AI, 3=平局 */
    private int winner = 0;

    /** AI 难度：0=简单, 1=中等, 2=困难 */
    private int aiLevel = 0;

    /** 连胜计数 */
    private int winStreak = 0;

    /** 总胜利次数 */
    private int totalWins = 0;

    /** 总平局次数 */
    private int totalDraws = 0;

    /** AI 响应延迟处理器 */
    private Handler handler = new Handler(Looper.getMainLooper());

    /** 随机数生成器 */
    private Random random = new Random();

    // UI 组件
    private TicTacToeView ticTacToeView;
    private TextView tvStatus;
    private TextView tvDifficultyLabel;
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
        return "tic";
    }

    @NonNull
    @Override
    protected String getGameName() {
        return getString(R.string.game_tic_name);
    }

    @Override
    protected void initGame() {
        // 创建自定义游戏内容
        if (gameContentContainer instanceof FrameLayout) {
            View contentView = createGameContentView();
            ((FrameLayout) gameContentContainer).addView(contentView);
        }
    }

    /**
     * 创建游戏内容视图
     */
    private View createGameContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.game_tic_color_bg));

        // 状态文本
        tvStatus = new TextView(this);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(18f);
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.game_tic_color_status_text));
        tvStatus.setPadding(0, 24, 0, 12);
        tvStatus.setText(R.string.game_tic_select_difficulty);

        // 难度标签
        tvDifficultyLabel = new TextView(this);
        tvDifficultyLabel.setGravity(Gravity.CENTER);
        tvDifficultyLabel.setTextSize(14f);
        tvDifficultyLabel.setTextColor(ContextCompat.getColor(this, R.color.game_tic_color_diff_label));
        tvDifficultyLabel.setPadding(0, 8, 0, 16);

        // 菜单面板
        menuPanel = new LinearLayout(this);
        menuPanel.setOrientation(LinearLayout.VERTICAL);
        menuPanel.setGravity(Gravity.CENTER);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        android.widget.Button btnEasy = new android.widget.Button(this);
        btnEasy.setText(R.string.difficulty_easy);
        btnEasy.setOnClickListener(v -> startNewGame(0));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(8, 0, 8, 0);
        btnEasy.setLayoutParams(lp);

        android.widget.Button btnMedium = new android.widget.Button(this);
        btnMedium.setText(R.string.game_tic_difficulty_medium);
        btnMedium.setOnClickListener(v -> startNewGame(1));
        LinearLayout.LayoutParams lpMed = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpMed.setMargins(8, 0, 8, 0);
        btnMedium.setLayoutParams(lpMed);

        android.widget.Button btnHard = new android.widget.Button(this);
        btnHard.setText(R.string.difficulty_hard);
        btnHard.setOnClickListener(v -> startNewGame(2));
        btnHard.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        btnRow.addView(btnEasy);
        btnRow.addView(btnMedium);
        btnRow.addView(btnHard);
        menuPanel.addView(btnRow);

        // 游戏面板
        gamePanel = new LinearLayout(this);
        gamePanel.setOrientation(LinearLayout.VERTICAL);
        gamePanel.setGravity(Gravity.CENTER);
        gamePanel.setVisibility(View.GONE);

        // 棋盘视图
        ticTacToeView = new TicTacToeView(this);
        int boardSize = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
        FrameLayout.LayoutParams boardLp = new FrameLayout.LayoutParams(boardSize, boardSize);
        ticTacToeView.setLayoutParams(boardLp);
        ticTacToeView.setOnCellClickListener(this::onCellClick);

        // 重新开始按钮
        android.widget.Button btnRestart = new android.widget.Button(this);
        btnRestart.setText(R.string.btn_restart);
        btnRestart.setOnClickListener(v -> showMenuPanel());
        LinearLayout.LayoutParams restartLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        restartLp.topMargin = 24;
        btnRestart.setLayoutParams(restartLp);

        // 组装视图
        root.addView(tvStatus);
        root.addView(tvDifficultyLabel);
        root.addView(menuPanel);
        gamePanel.addView(ticTacToeView);
        gamePanel.addView(btnRestart);
        root.addView(gamePanel);

        return root;
    }

    /**
     * 显示菜单面板
     */
    private void showMenuPanel() {
        menuPanel.setVisibility(View.VISIBLE);
        gamePanel.setVisibility(View.GONE);
        tvStatus.setText(R.string.game_tic_select_difficulty);
        tvDifficultyLabel.setText("");
        ticTacToeView.clearBoard();
        isGameOver = true;
    }

    /**
     * 开始新游戏
     *
     * @param level AI 难度（0=简单, 1=困难）
     */
    private void startNewGame(int level) {
        aiLevel = level;
        board = new int[BOARD_SIZE][BOARD_SIZE];
        isPlayerTurn = true;
        isGameOver = false;
        winner = 0;

        ticTacToeView.clearBoard();
        ticTacToeView.setBoard(board);

        menuPanel.setVisibility(View.GONE);
        gamePanel.setVisibility(View.VISIBLE);
        tvDifficultyLabel.setText(level == 0
                ? getString(R.string.difficulty_easy)
                : (level == 1 ? getString(R.string.game_tic_difficulty_medium)
                              : getString(R.string.difficulty_hard)));
        tvStatus.setText(R.string.game_tic_your_turn);

        isGameRunning = true;
        gameStartTime = System.currentTimeMillis();

        // P2-7: 开始回放录制
        replayRecorder = new com.gamecenter.app.games.replay.ReplayRecorder(this, getGameId());
        replayRecorder.startRecording(level);
    }

    /**
     * 处理玩家点击棋盘
     */
    private void onCellClick(int row, int col) {
        if (isGameOver || !isPlayerTurn) return;
        if (board[row][col] != 0) return;

        // 玩家落子
        board[row][col] = PLAYER_X;
        // P2-7: 记录玩家走法
        if (replayRecorder != null && replayRecorder.isRecording()) {
            replayRecorder.record(new com.gamecenter.app.games.replay.ReplayMove(row, col, PLAYER_X));
        }
        ticTacToeView.setBoard(board);
        isPlayerTurn = false;

        // 检查玩家是否获胜
        int result = checkGameResult();
        if (result != 0) {
            handleGameEnd(result);
            return;
        }

        // AI 回合
        tvStatus.setText(R.string.game_tic_ai_thinking);
        handler.postDelayed(() -> {
            if (isGameOver) return;
            // P2-7: 快照棋盘以检测 AI 落子位置
            int[][] snapshot = new int[BOARD_SIZE][BOARD_SIZE];
            for (int r = 0; r < BOARD_SIZE; r++) {
                System.arraycopy(board[r], 0, snapshot[r], 0, BOARD_SIZE);
            }
            aiMove();
            // P2-7: 记录 AI 走法（找出新落子位置）
            if (replayRecorder != null && replayRecorder.isRecording()) {
                for (int r = 0; r < BOARD_SIZE; r++) {
                    for (int c = 0; c < BOARD_SIZE; c++) {
                        if (snapshot[r][c] != board[r][c]) {
                            replayRecorder.record(new com.gamecenter.app.games.replay.ReplayMove(r, c, PLAYER_O));
                            break;
                        }
                    }
                }
            }
            ticTacToeView.setBoard(board);
            int aiResult = checkGameResult();
            if (aiResult != 0) {
                handleGameEnd(aiResult);
            } else {
                isPlayerTurn = true;
                tvStatus.setText(R.string.game_tic_your_turn);
            }
        }, 300 + random.nextInt(400));
    }

    /**
     * AI 落子
     */
    private void aiMove() {
        // 困难档首手开局库加权随机选择，增加开局多样性
        if (aiLevel == 2 && isBoardEmpty(board)) {
            int[] opening = pickOpeningMove();
            if (opening != null) {
                board[opening[0]][opening[1]] = PLAYER_O;
                return;
            }
        }

        if (aiLevel == 0) {
            aiMoveEasy();
        } else if (aiLevel == 1) {
            aiMoveMedium();
        } else {
            aiMoveMinimax();
        }
    }

    /**
     * 检查棋盘是否为空（首手判定）
     */
    private boolean isBoardEmpty(int[][] b) {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (b[r][c] != 0) return false;
            }
        }
        return true;
    }

    /**
     * 开局库加权随机选择首手位置。
     * <p>
     * 4 个角 (0,0)/(0,2)/(2,0)/(2,2) 权重 3；
     * 4 个边 (0,1)/(1,0)/(1,2)/(2,1) 权重 1；
     * 中心 (1,1) 权重 2。
     * 加权随机选择避免每局都走中心。
     *
     * @return 首手坐标 [row, col]
     */
    private int[] pickOpeningMove() {
        int[][] points = {
            {0, 0}, {0, 2}, {2, 0}, {2, 2},  // 角，权重3
            {1, 1},                             // 中心，权重2
            {0, 1}, {1, 0}, {1, 2}, {2, 1}    // 边，权重1
        };
        int[] weights = {3, 3, 3, 3, 2, 1, 1, 1, 1};
        int total = 0;
        for (int w : weights) total += w;
        int r = random.nextInt(total);
        int cumulative = 0;
        for (int i = 0; i < points.length; i++) {
            cumulative += weights[i];
            if (r < cumulative) return points[i];
        }
        return points[0];
    }

    /**
     * AI 简单模式：80% 随机 + 20% 必胜/必堵手
     */
    private void aiMoveEasy() {
        // 20% 概率检测必胜手或必堵手
        if (random.nextInt(10) < 2) {
            int[] win = findCriticalMove(PLAYER_O);
            if (win != null) {
                board[win[0]][win[1]] = PLAYER_O;
                return;
            }
            int[] block = findCriticalMove(PLAYER_X);
            if (block != null) {
                board[block[0]][block[1]] = PLAYER_O;
                return;
            }
        }
        aiMoveRandom();
    }

    /**
     * AI 中等模式：Minimax 限制深度=2
     */
    private void aiMoveMedium() {
        int bestScore = Integer.MIN_VALUE;
        int bestRow = -1;
        int bestCol = -1;

        // 中等档限制深度=2，且首手偏好中心/角
        if (isBoardEmpty(board)) {
            int[] opening = pickOpeningMove();
            if (opening != null) {
                board[opening[0]][opening[1]] = PLAYER_O;
                return;
            }
        }

        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] == 0) {
                    board[r][c] = PLAYER_O;
                    int score = minimax(board, 0, false, 2);
                    board[r][c] = 0;
                    if (score > bestScore) {
                        bestScore = score;
                        bestRow = r;
                        bestCol = c;
                    }
                }
            }
        }

        if (bestRow >= 0) {
            board[bestRow][bestCol] = PLAYER_O;
        }
    }

    /**
     * 查找关键手：能让 player 立即获胜的位置。
     *
     * @param player 玩家（PLAYER_X 或 PLAYER_O）
     * @return [row, col] 或 null
     */
    private int[] findCriticalMove(int player) {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] == 0) {
                    board[r][c] = player;
                    int winner = checkBoardWinner(board);
                    board[r][c] = 0;
                    if (winner == player) return new int[]{r, c};
                }
            }
        }
        return null;
    }

    /**
     * AI 随机落子（兜底）
     */
    private void aiMoveRandom() {
        List<int[]> emptyCells = new ArrayList<>();
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] == 0) {
                    emptyCells.add(new int[]{r, c});
                }
            }
        }
        if (!emptyCells.isEmpty()) {
            int[] cell = emptyCells.get(random.nextInt(emptyCells.size()));
            board[cell[0]][cell[1]] = PLAYER_O;
        }
    }

    /**
     * AI Minimax 落子（困难模式，完整搜索）
     */
    private void aiMoveMinimax() {
        int bestScore = Integer.MIN_VALUE;
        int bestRow = -1;
        int bestCol = -1;

        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] == 0) {
                    board[r][c] = PLAYER_O;
                    int score = minimax(board, 0, false, Integer.MAX_VALUE);
                    board[r][c] = 0;
                    if (score > bestScore) {
                        bestScore = score;
                        bestRow = r;
                        bestCol = c;
                    }
                }
            }
        }

        if (bestRow >= 0) {
            board[bestRow][bestCol] = PLAYER_O;
        }
    }

    /**
     * Minimax 算法
     *
     * @param b        棋盘状态
     * @param depth    当前搜索深度
     * @param isMaximizing 是否为最大化玩家（AI）
     * @param maxDepth 最大搜索深度（用于中等档限制视野）
     * @return 评估分数
     */
    private int minimax(int[][] b, int depth, boolean isMaximizing, int maxDepth) {
        int result = checkBoardWinner(b);
        if (result == PLAYER_O) return 10 - depth;
        if (result == PLAYER_X) return depth - 10;
        if (isBoardFull(b)) return 0;
        // 中等档深度受限，提前返回启发式评估
        if (depth >= maxDepth) return evaluateHeuristic(b);

        if (isMaximizing) {
            int best = Integer.MIN_VALUE;
            for (int r = 0; r < BOARD_SIZE; r++) {
                for (int c = 0; c < BOARD_SIZE; c++) {
                    if (b[r][c] == 0) {
                        b[r][c] = PLAYER_O;
                        best = Math.max(best, minimax(b, depth + 1, false, maxDepth));
                        b[r][c] = 0;
                    }
                }
            }
            return best;
        } else {
            int best = Integer.MAX_VALUE;
            for (int r = 0; r < BOARD_SIZE; r++) {
                for (int c = 0; c < BOARD_SIZE; c++) {
                    if (b[r][c] == 0) {
                        b[r][c] = PLAYER_X;
                        best = Math.min(best, minimax(b, depth + 1, true, maxDepth));
                        b[r][c] = 0;
                    }
                }
            }
            return best;
        }
    }

    /**
     * 启发式评估函数（中等档深度受限时使用）。
     * <p>
     * 评估双方在棋盘上的"潜在两连"数量差，AI 视角：
     * 正值表示 AI 优势，负值表示玩家优势。
     *
     * @param b 棋盘
     * @return 评估分数
     */
    private int evaluateHeuristic(int[][] b) {
        return countPotentialLines(b, PLAYER_O) - countPotentialLines(b, PLAYER_X);
    }

    /**
     * 统计指定玩家在所有 8 条胜利线上"两连+空位"的数量。
     *
     * @param b      棋盘
     * @param player 玩家
     * @return 潜在两连数
     */
    private int countPotentialLines(int[][] b, int player) {
        int count = 0;
        int[][] lines = {
            {0, 0, 0, 1, 0, 2}, {1, 0, 1, 1, 1, 2}, {2, 0, 2, 1, 2, 2},  // 行
            {0, 0, 1, 0, 2, 0}, {0, 1, 1, 1, 2, 1}, {0, 2, 1, 2, 2, 2},  // 列
            {0, 0, 1, 1, 2, 2}, {0, 2, 1, 1, 2, 0}                       // 对角线
        };
        for (int[] line : lines) {
            int playerCount = 0;
            int opponentCount = 0;
            for (int i = 0; i < 3; i++) {
                int cell = b[line[i * 2]][line[i * 2 + 1]];
                if (cell == player) playerCount++;
                else if (cell != 0) opponentCount++;
            }
            // 该线上只有己方棋子和空位（两连+空位）
            if (opponentCount == 0 && playerCount == 2) count++;
        }
        return count;
    }

    /**
     * 检查棋盘是否有获胜者
     *
     * @param b 棋盘
     * @return 获胜者（0=无, 1=玩家, 2=AI）
     */
    private int checkBoardWinner(int[][] b) {
        // 检查行
        for (int r = 0; r < BOARD_SIZE; r++) {
            if (b[r][0] != 0 && b[r][0] == b[r][1] && b[r][1] == b[r][2]) {
                return b[r][0];
            }
        }
        // 检查列
        for (int c = 0; c < BOARD_SIZE; c++) {
            if (b[0][c] != 0 && b[0][c] == b[1][c] && b[1][c] == b[2][c]) {
                return b[0][c];
            }
        }
        // 检查对角线
        if (b[0][0] != 0 && b[0][0] == b[1][1] && b[1][1] == b[2][2]) {
            return b[0][0];
        }
        if (b[0][2] != 0 && b[0][2] == b[1][1] && b[1][1] == b[2][0]) {
            return b[0][2];
        }
        return 0;
    }

    /**
     * 检查棋盘是否已满
     */
    private boolean isBoardFull(int[][] b) {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (b[r][c] == 0) return false;
            }
        }
        return true;
    }

    /**
     * 检查游戏结果
     *
     * @return 0=进行中, 1=玩家胜, 2=AI胜, 3=平局
     */
    private int checkGameResult() {
        int w = checkBoardWinner(board);
        if (w == PLAYER_X) return 1;
        if (w == PLAYER_O) return 2;
        if (isBoardFull(board)) return 3;
        return 0;
    }

    /**
     * 获取胜利连线并高亮
     */
    private void highlightWinLine(int winPlayer) {
        // 检查行
        for (int r = 0; r < BOARD_SIZE; r++) {
            if (board[r][0] == winPlayer && board[r][0] == board[r][1] && board[r][1] == board[r][2]) {
                ticTacToeView.setWinLine(r, 0, r, 2);
                return;
            }
        }
        // 检查列
        for (int c = 0; c < BOARD_SIZE; c++) {
            if (board[0][c] == winPlayer && board[0][c] == board[1][c] && board[1][c] == board[2][c]) {
                ticTacToeView.setWinLine(0, c, 2, c);
                return;
            }
        }
        // 对角线
        if (board[0][0] == winPlayer && board[0][0] == board[1][1] && board[1][1] == board[2][2]) {
            ticTacToeView.setWinLine(0, 0, 2, 2);
            return;
        }
        if (board[0][2] == winPlayer && board[0][2] == board[1][1] && board[1][1] == board[2][0]) {
            ticTacToeView.setWinLine(0, 2, 2, 0);
        }
    }

    /**
     * 处理游戏结束
     *
     * @param result 游戏结果
     */
    private void handleGameEnd(int result) {
        isGameOver = true;
        winner = result;

        // P2-7: 结束回放录制
        if (replayRecorder != null && replayRecorder.isRecording()) {
            String recResult;
            if (result == 1) {
                recResult = com.gamecenter.app.games.replay.ReplayRecorder.RESULT_WIN;
            } else if (result == 2) {
                recResult = com.gamecenter.app.games.replay.ReplayRecorder.RESULT_LOSS;
            } else {
                recResult = com.gamecenter.app.games.replay.ReplayRecorder.RESULT_DRAW;
            }
            replayRecorder.endRecording(recResult);
        }

        if (result == 1) {
            // 玩家获胜
            highlightWinLine(PLAYER_X);
            tvStatus.setText(R.string.game_tic_you_win);
            totalWins++;
            winStreak++;
            usageStore.recordWin(getGameId());
            checkAchievement("win", totalWins);
            checkAchievement("streak", winStreak);
            updateScore(currentScore + 100);
        } else if (result == 2) {
            // AI 获胜
            highlightWinLine(PLAYER_O);
            tvStatus.setText(R.string.game_tic_ai_win);
            winStreak = 0;
            usageStore.recordLoss(getGameId());
        } else {
            // 平局
            tvStatus.setText(R.string.game_tic_draw);
            winStreak = 0;
            totalDraws++;
            checkAchievement("special", totalDraws >= 10);
        }
        // 困难档（aiLevel==2）胜利才触发"击败困难AI"成就
        if (result == 1 && aiLevel == 2) {
            checkAchievement("special", true);
        }

        // 记录游戏时长
        if (gameStartTime > 0) {
            usageStore.recordPlayTime(getGameId(), System.currentTimeMillis() - gameStartTime);
        }

        // P2-7: 提示查看回放
        if (replayRecorder != null && replayRecorder.hasHistory()) {
            handler.postDelayed(() -> new android.app.AlertDialog.Builder(this)
                    .setTitle("对局结束")
                    .setMessage("是否查看回放？")
                    .setPositiveButton("查看回放", (d, w) -> {
                        com.gamecenter.app.games.replay.ReplayRecord rec = replayRecorder.loadLatest();
                        com.gamecenter.app.games.replay.ReplayDialog.show(this, rec,
                                new com.gamecenter.app.games.replay.ReplayPlayer.Listener() {
                                    @Override
                                    public void onBoardUpdated(int step, java.util.List<com.gamecenter.app.games.replay.ReplayMove> played) {
                                        board = new int[BOARD_SIZE][BOARD_SIZE];
                                        for (com.gamecenter.app.games.replay.ReplayMove m : played) {
                                            board[m.toRow][m.toCol] = m.player;
                                        }
                                        ticTacToeView.setBoard(board);
                                    }
                                    @Override
                                    public void onReplayFinished() {}
                                    @Override
                                    public void onReplayReset() {}
                                });
                    })
                    .setNegativeButton("关闭", null)
                    .show(), 500);
        }
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
