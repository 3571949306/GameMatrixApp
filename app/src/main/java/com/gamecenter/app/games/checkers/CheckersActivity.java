package com.gamecenter.app.games.checkers;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.gamecenter.app.R;
import com.gamecenter.app.games.base.BaseGameActivity;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 跳棋（国际跳棋）游戏 Activity。
 *
 * <p>8×8 棋盘，黑色棋子 vs 白色棋子（AI）。
 * 支持跳过吃子和升王规则。</p>
 *
 * <p>成就系统：
 * <ul>
 *   <li>首次胜利</li>
 *   <li>连胜 3 局</li>
 *   <li>吃子大师（单局吃子超过5个）</li>
 *   <li>击败困难 AI</li>
 *   <li>全吃胜利（吃掉所有对手棋子）</li>
 * </ul>
 * </p>
 *
 * @author Kou Dou Ma (Alex)
 * @version 1.0
 * @since 2026-06-20
 */
public class CheckersActivity extends BaseGameActivity {

    private static final int BOARD_SIZE = 8;
    private static final int EMPTY = CheckersView.EMPTY;
    private static final int BLACK = CheckersView.BLACK;
    private static final int BLACK_KING = CheckersView.BLACK_KING;
    private static final int WHITE = CheckersView.WHITE;
    private static final int WHITE_KING = CheckersView.WHITE_KING;

    /** 玩家为黑色，AI 为白色 */
    private static final int PLAYER_COLOR = BLACK;
    private static final int AI_COLOR = WHITE;

    // 游戏状态
    private int[][] board = new int[BOARD_SIZE][BOARD_SIZE];
    private boolean isPlayerTurn = true;
    private int selectedRow = -1;
    private int selectedCol = -1;
    private boolean[][] validMoves = new boolean[BOARD_SIZE][BOARD_SIZE];
    private List<int[]> jumpMoves = new ArrayList<>();
    private int playerCaptures = 0;
    private int totalWins = 0;
    private int winStreak = 0;
    private int aiLevel = 1; // 0=简单, 1=困难

    private Handler handler = new Handler(Looper.getMainLooper());
    private Random random = new Random();

    // UI 组件
    private CheckersView checkersView;
    private TextView tvStatus;
    private TextView tvDifficulty;
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
        return "checkers";
    }

    @NonNull
    @Override
    protected String getGameName() {
        return getString(R.string.game_checkers_name);
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
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.game_checkers_color_bg));

        tvStatus = new TextView(this);
        tvStatus.setGravity(Gravity.CENTER);
        tvStatus.setTextSize(16f);
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.game_checkers_color_status_text));
        tvStatus.setPadding(0, 24, 0, 8);

        tvDifficulty = new TextView(this);
        tvDifficulty.setGravity(Gravity.CENTER);
        tvDifficulty.setTextSize(14f);
        tvDifficulty.setTextColor(ContextCompat.getColor(this, R.color.game_checkers_color_diff_label));
        tvDifficulty.setPadding(0, 4, 0, 16);

        // 菜单面板
        menuPanel = new LinearLayout(this);
        menuPanel.setOrientation(LinearLayout.VERTICAL);
        menuPanel.setGravity(Gravity.CENTER);

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);

        MaterialButton btnEasy = new MaterialButton(this);
        btnEasy.setText(R.string.difficulty_easy);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(16, 0, 16, 0);
        btnEasy.setLayoutParams(lp);
        btnEasy.setOnClickListener(v -> startNewGame(0));

        MaterialButton btnHard = new MaterialButton(this);
        btnHard.setText(R.string.difficulty_hard);
        btnHard.setOnClickListener(v -> startNewGame(1));

        btnRow.addView(btnEasy);
        btnRow.addView(btnHard);
        menuPanel.addView(btnRow);

        // 游戏面板
        gamePanel = new LinearLayout(this);
        gamePanel.setOrientation(LinearLayout.VERTICAL);
        gamePanel.setGravity(Gravity.CENTER);
        gamePanel.setVisibility(View.GONE);

        checkersView = new CheckersView(this);
        int viewWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.9);
        checkersView.setLayoutParams(new FrameLayout.LayoutParams(viewWidth, viewWidth));
        checkersView.setOnCellClickListener(this::onCellClick);

        MaterialButton btnRestart = new MaterialButton(this);
        btnRestart.setText(R.string.btn_restart);
        btnRestart.setOnClickListener(v -> showMenu());
        LinearLayout.LayoutParams restartLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        restartLp.topMargin = 16;
        btnRestart.setLayoutParams(restartLp);

        gamePanel.addView(checkersView);
        gamePanel.addView(btnRestart);

        root.addView(tvStatus);
        root.addView(tvDifficulty);
        root.addView(menuPanel);
        root.addView(gamePanel);

        return root;
    }

    private void showMenu() {
        menuPanel.setVisibility(View.VISIBLE);
        gamePanel.setVisibility(View.GONE);
        tvStatus.setText(R.string.game_checkers_select_difficulty);
        tvDifficulty.setText("");
    }

    /**
     * 开始新游戏
     */
    private void startNewGame(int level) {
        aiLevel = level;
        isPlayerTurn = true;
        selectedRow = -1;
        selectedCol = -1;
        playerCaptures = 0;
        initBoard();

        menuPanel.setVisibility(View.GONE);
        gamePanel.setVisibility(View.VISIBLE);
        tvDifficulty.setText(level == 0
                ? getString(R.string.difficulty_easy) : getString(R.string.difficulty_hard));
        tvStatus.setText(R.string.game_checkers_your_turn);

        checkersView.setBoard(board);
        checkersView.clearSelection();

        isGameRunning = true;
        gameStartTime = System.currentTimeMillis();
    }

    /**
     * 初始化棋盘
     */
    private void initBoard() {
        board = new int[BOARD_SIZE][BOARD_SIZE];
        // 白色棋子（AI）在上方 3 行
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if ((r + c) % 2 == 1) {
                    board[r][c] = WHITE;
                }
            }
        }
        // 黑色棋子（玩家）在下方 3 行
        for (int r = 5; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if ((r + c) % 2 == 1) {
                    board[r][c] = BLACK;
                }
            }
        }
    }

    /**
     * 处理棋盘点击
     */
    private void onCellClick(int row, int col) {
        if (!isPlayerTurn || !isGameRunning) return;

        // 如果已有选中棋子，尝试移动
        if (selectedRow >= 0) {
            if (validMoves[row][col]) {
                // 执行移动
                performMove(selectedRow, selectedCol, row, col);
                checkersView.clearSelection();
                selectedRow = -1;
                selectedCol = -1;
                return;
            }
        }

        // 选择新棋子
        int piece = board[row][col];
        if (piece == BLACK || piece == BLACK_KING) {
            selectedRow = row;
            selectedCol = col;
            checkersView.setSelected(row, col);

            // 计算合法移动
            validMoves = calculateValidMoves(row, col);
            checkersView.setValidMoves(validMoves);
        }
    }

    /**
     * 计算指定棋子的合法移动
     */
    private boolean[][] calculateValidMoves(int row, int col) {
        boolean[][] moves = new boolean[BOARD_SIZE][BOARD_SIZE];
        int piece = board[row][col];

        // 跳吃优先
        List<int[]> jumps = getJumpMoves(row, col, piece);
        if (!jumps.isEmpty()) {
            for (int[] jump : jumps) {
                moves[jump[0]][jump[1]] = true;
            }
            return moves;
        }

        // 普通移动
        int[][] directions = getDirections(piece);
        for (int[] dir : directions) {
            int newRow = row + dir[0];
            int newCol = col + dir[1];
            if (isValidPosition(newRow, newCol) && board[newRow][newCol] == EMPTY) {
                moves[newRow][newCol] = true;
            }
        }

        return moves;
    }

    /**
     * 获取跳吃移动
     */
    private List<int[]> getJumpMoves(int row, int col, int piece) {
        List<int[]> jumps = new ArrayList<>();
        int[][] directions = getDirections(piece);
        for (int[] dir : directions) {
            int midRow = row + dir[0];
            int midCol = col + dir[1];
            int landRow = row + 2 * dir[0];
            int landCol = col + 2 * dir[1];
            if (isValidPosition(landRow, landCol) && board[landRow][landCol] == EMPTY
                    && isOpponent(board[midRow][midCol], piece)) {
                jumps.add(new int[]{landRow, landCol});
            }
        }
        return jumps;
    }

    /**
     * 获取棋子移动方向
     */
    private int[][] getDirections(int piece) {
        switch (piece) {
            case BLACK:      return new int[][]{{-1, -1}, {-1, 1}};
            case BLACK_KING: return new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
            case WHITE:      return new int[][]{{1, -1}, {1, 1}};
            case WHITE_KING: return new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
            default:         return new int[0][];
        }
    }

    /**
     * 检查是否为对手棋子
     */
    private boolean isOpponent(int piece, int myPiece) {
        if (myPiece == BLACK || myPiece == BLACK_KING) {
            return piece == WHITE || piece == WHITE_KING;
        } else {
            return piece == BLACK || piece == BLACK_KING;
        }
    }

    /**
     * 检查位置是否有效
     */
    private boolean isValidPosition(int row, int col) {
        return row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE;
    }

    /**
     * 执行移动
     */
    private void performMove(int fromRow, int fromCol, int toRow, int toCol) {
        int piece = board[fromRow][fromCol];
        board[fromRow][fromCol] = EMPTY;

        // 检查是否跳吃
        if (Math.abs(toRow - fromRow) == 2) {
            int midRow = (fromRow + toRow) / 2;
            int midCol = (fromCol + toCol) / 2;
            board[midRow][midCol] = EMPTY;
            playerCaptures++;
        }

        // 升王检查
        if (piece == BLACK && toRow == 0) {
            piece = BLACK_KING;
        }
        board[toRow][toCol] = piece;

        checkersView.setBoard(board);

        // 检查游戏结束
        if (checkGameEnd()) return;

        // AI 回合
        isPlayerTurn = false;
        tvStatus.setText(R.string.game_checkers_ai_turn);
        handler.postDelayed(this::aiMove, 500);
    }

    /**
     * AI 移动
     */
    private void aiMove() {
        List<int[]> allMoves = getAllMoves(AI_COLOR);
        if (allMoves.isEmpty()) {
            // AI 无棋可走，玩家获胜
            onPlayerWin(true);
            return;
        }

        int[] move;
        if (aiLevel == 0) {
            // 简单 AI：随机走
            move = allMoves.get(random.nextInt(allMoves.size()));
        } else {
            // 困难 AI：优先跳吃，否则随机
            List<int[]> jumpMovesList = new ArrayList<>();
            for (int[] m : allMoves) {
                if (Math.abs(m[2] - m[0]) == 2) {
                    jumpMovesList.add(m);
                }
            }
            if (!jumpMovesList.isEmpty()) {
                move = jumpMovesList.get(random.nextInt(jumpMovesList.size()));
            } else {
                move = allMoves.get(random.nextInt(allMoves.size()));
            }
        }

        // 执行 AI 移动
        int piece = board[move[0]][move[1]];
        board[move[0]][move[1]] = EMPTY;

        if (Math.abs(move[2] - move[0]) == 2) {
            int midRow = (move[0] + move[2]) / 2;
            int midCol = (move[1] + move[3]) / 2;
            board[midRow][midCol] = EMPTY;
        }

        if (piece == WHITE && move[2] == BOARD_SIZE - 1) {
            piece = WHITE_KING;
        }
        board[move[2]][move[3]] = piece;

        checkersView.setBoard(board);

        if (checkGameEnd()) return;

        isPlayerTurn = true;
        tvStatus.setText(R.string.game_checkers_your_turn);
    }

    /**
     * 获取指定颜色所有合法移动
     */
    private List<int[]> getAllMoves(int color) {
        List<int[]> moves = new ArrayList<>();
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] == color || board[r][c] == color + 1) {
                    int[][] directions = getDirections(board[r][c]);
                    // 优先跳吃
                    for (int[] dir : directions) {
                        int midR = r + dir[0];
                        int midC = c + dir[1];
                        int landR = r + 2 * dir[0];
                        int landC = c + 2 * dir[1];
                        if (isValidPosition(landR, landC) && board[landR][landC] == EMPTY
                                && isOpponent(board[midR][midC], board[r][c])) {
                            moves.add(new int[]{r, c, landR, landC});
                        }
                    }
                }
            }
        }
        if (!moves.isEmpty()) return moves;

        // 无跳吃，普通移动
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] == color || board[r][c] == color + 1) {
                    int[][] directions = getDirections(board[r][c]);
                    for (int[] dir : directions) {
                        int newR = r + dir[0];
                        int newC = c + dir[1];
                        if (isValidPosition(newR, newC) && board[newR][newC] == EMPTY) {
                            moves.add(new int[]{r, c, newR, newC});
                        }
                    }
                }
            }
        }
        return moves;
    }

    /**
     * 检查游戏是否结束
     */
    private boolean checkGameEnd() {
        int blackCount = countPieces(BLACK) + countPieces(BLACK_KING);
        int whiteCount = countPieces(WHITE) + countPieces(WHITE_KING);

        if (blackCount == 0) {
            onPlayerLose();
            return true;
        }
        if (whiteCount == 0) {
            onPlayerWin(false);
            return true;
        }
        if (isPlayerTurn && getAllMoves(PLAYER_COLOR).isEmpty()) {
            onPlayerLose();
            return true;
        }
        if (!isPlayerTurn && getAllMoves(AI_COLOR).isEmpty()) {
            onPlayerWin(false);
            return true;
        }
        return false;
    }

    /**
     * 统计棋子数量
     */
    private int countPieces(int pieceType) {
        int count = 0;
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] == pieceType) count++;
            }
        }
        return count;
    }

    /**
     * 玩家获胜
     */
    private void onPlayerWin(boolean allEaten) {
        isGameRunning = false;
        totalWins++;
        winStreak++;

        tvStatus.setText(R.string.game_checkers_you_win);
        usageStore.recordWin(getGameId());

        checkAchievement("win", totalWins);
        checkAchievement("streak", winStreak);
        checkAchievement("score", playerCaptures);
        if (aiLevel == 1) {
            checkAchievement("special", true);
        }
        if (allEaten || countPieces(WHITE) + countPieces(WHITE_KING) == 0) {
            checkAchievement("special", true);
        }

        updateScore(currentScore + 200);
        if (gameStartTime > 0) {
            usageStore.recordPlayTime(getGameId(), System.currentTimeMillis() - gameStartTime);
        }
    }

    /**
     * 玩家失败
     */
    private void onPlayerLose() {
        isGameRunning = false;
        winStreak = 0;
        tvStatus.setText(R.string.game_checkers_ai_win);
        usageStore.recordLoss(getGameId());
        if (gameStartTime > 0) {
            usageStore.recordPlayTime(getGameId(), System.currentTimeMillis() - gameStartTime);
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
