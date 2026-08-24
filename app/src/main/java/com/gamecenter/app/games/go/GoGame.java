// 同步声明：此文件与 module-store/feature/games/games/go/src/main/java/com/gamecenter/app/go/GoGame.java 保持同步，修改时请同步修改对方文件
package com.gamecenter.app.games.go;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 9x9 围棋规则层。
 *
 * <p>所有真实落子和 AI 模拟都通过 {@link #tryMove(int[][], int[][], int, int, int)}
 * 执行同一套边界、占位、提子、自杀和简单劫校验。计分采用中国面积规则：
 * 盘上棋子数加仅与一方相邻的空点数，白方贴 6.5 目；提子数只作对局信息展示，
 * 不重复计入面积分。</p>
 */
public class GoGame {
    public static final int BOARD_SIZE = 9;
    public static final int EMPTY = 0;
    public static final int BLACK = 1;
    public static final int WHITE = 2;
    public static final int PASS_MOVE = -1;
    public static final int MAX_CONSECUTIVE_PASSES = 2;
    public static final float KOMI = 6.5f;

    public enum MoveError {
        NONE,
        GAME_OVER,
        OUT_OF_BOUNDS,
        INVALID_COLOR,
        OCCUPIED,
        SUICIDE,
        KO
    }

    /** 不修改输入棋盘的试走结果。 */
    public static final class MoveResult {
        private final boolean legal;
        private final MoveError error;
        private final int[][] resultingBoard;
        private final int capturedStones;

        private MoveResult(boolean legal, MoveError error, int[][] resultingBoard,
                           int capturedStones) {
            this.legal = legal;
            this.error = error;
            this.resultingBoard = resultingBoard;
            this.capturedStones = capturedStones;
        }

        private static MoveResult legal(int[][] resultingBoard, int capturedStones) {
            return new MoveResult(true, MoveError.NONE, resultingBoard, capturedStones);
        }

        private static MoveResult illegal(MoveError error) {
            return new MoveResult(false, error, null, 0);
        }

        public boolean isLegal() {
            return legal;
        }

        public MoveError getError() {
            return error;
        }

        /** 返回结果棋盘副本；非法结果返回 null。 */
        public int[][] getBoard() {
            return resultingBoard == null ? null : copyBoard(resultingBoard);
        }

        public int getCapturedStones() {
            return capturedStones;
        }
    }

    /** 中国面积计分结果。 */
    public static final class Score {
        private final int blackArea;
        private final int whiteArea;
        private final double komi;

        private Score(int blackArea, int whiteArea, double komi) {
            this.blackArea = blackArea;
            this.whiteArea = whiteArea;
            this.komi = komi;
        }

        public double getBlackScore() {
            return blackArea;
        }

        public double getWhiteScore() {
            return whiteArea + komi;
        }

        public int getBlackArea() {
            return blackArea;
        }

        public int getWhiteArea() {
            return whiteArea;
        }

        public double getKomi() {
            return komi;
        }

        public boolean isBlackWinner() {
            return getBlackScore() > getWhiteScore();
        }

        public boolean isWhiteWinner() {
            return getWhiteScore() > getBlackScore();
        }
    }

    /** 供 AI 和存档使用的防御性快照。 */
    public static final class PositionSnapshot {
        private final int[][] board;
        private final int[][] previousBoard;
        private final int currentPlayer;
        private final int capturedByBlack;
        private final int capturedByWhite;
        private final int consecutivePasses;
        private final boolean gameOver;

        private PositionSnapshot(int[][] board, int[][] previousBoard, int currentPlayer,
                                 int capturedByBlack, int capturedByWhite,
                                 int consecutivePasses, boolean gameOver) {
            this.board = copyBoard(board);
            this.previousBoard = previousBoard == null ? null : copyBoard(previousBoard);
            this.currentPlayer = currentPlayer;
            this.capturedByBlack = capturedByBlack;
            this.capturedByWhite = capturedByWhite;
            this.consecutivePasses = consecutivePasses;
            this.gameOver = gameOver;
        }

        public int[][] getBoard() {
            return copyBoard(board);
        }

        public int[][] getPreviousBoard() {
            return previousBoard == null ? null : copyBoard(previousBoard);
        }

        public int getCurrentPlayer() {
            return currentPlayer;
        }

        public int getCapturedByBlack() {
            return capturedByBlack;
        }

        public int getCapturedByWhite() {
            return capturedByWhite;
        }

        public int getConsecutivePasses() {
            return consecutivePasses;
        }

        public boolean isGameOver() {
            return gameOver;
        }
    }

    private int[][] board = new int[BOARD_SIZE][BOARD_SIZE];
    /** 禁止下一手复现的局面，用于简单劫。 */
    private int[][] previousBoard;
    private int currentPlayer = BLACK;
    private int capturedByBlack;
    private int capturedByWhite;
    private int consecutivePasses;
    private boolean gameOver;

    public void startNewGame() {
        board = new int[BOARD_SIZE][BOARD_SIZE];
        previousBoard = null;
        currentPlayer = BLACK;
        capturedByBlack = 0;
        capturedByWhite = 0;
        consecutivePasses = 0;
        gameOver = false;
    }

    /**
     * 旧存档兼容入口。旧格式没有保存劫前局面和连续停着状态，因此只能恢复为普通进行中局面。
     * 新存档应调用完整重载或 {@link #restoreState(PositionSnapshot)}。
     */
    public void restoreState(int[][] savedBoard, int currentPlayer,
                             int capturedByBlack, int capturedByWhite) {
        restoreState(savedBoard, null, currentPlayer, capturedByBlack, capturedByWhite, 0, false);
    }

    /** 完整恢复对局状态，保留简单劫和终局所需信息。 */
    public void restoreState(int[][] savedBoard, int[][] savedPreviousBoard, int currentPlayer,
                             int capturedByBlack, int capturedByWhite,
                             int consecutivePasses, boolean gameOver) {
        validateBoard(savedBoard);
        if (savedPreviousBoard != null) validateBoard(savedPreviousBoard);
        if (!isPlayerColor(currentPlayer)) {
            throw new IllegalArgumentException("currentPlayer must be BLACK or WHITE");
        }
        if (capturedByBlack < 0 || capturedByWhite < 0) {
            throw new IllegalArgumentException("captured counts must be non-negative");
        }
        if (consecutivePasses < 0 || consecutivePasses > MAX_CONSECUTIVE_PASSES) {
            throw new IllegalArgumentException("invalid consecutivePasses");
        }
        this.board = copyBoard(savedBoard);
        this.previousBoard = savedPreviousBoard == null ? null : copyBoard(savedPreviousBoard);
        this.currentPlayer = currentPlayer;
        this.capturedByBlack = capturedByBlack;
        this.capturedByWhite = capturedByWhite;
        this.consecutivePasses = consecutivePasses;
        this.gameOver = gameOver;
    }

    public void restoreState(PositionSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("snapshot == null");
        restoreState(snapshot.board, snapshot.previousBoard, snapshot.currentPlayer,
                snapshot.capturedByBlack, snapshot.capturedByWhite,
                snapshot.consecutivePasses, snapshot.gameOver);
    }

    /** 兼容既有绘制代码；新逻辑和异步任务应使用 {@link #getBoardSnapshot()}。 */
    public int[][] getBoard() {
        return board;
    }

    public int[][] getBoardSnapshot() {
        return copyBoard(board);
    }

    public int[][] getPreviousBoardSnapshot() {
        return previousBoard == null ? null : copyBoard(previousBoard);
    }

    public PositionSnapshot snapshot() {
        return new PositionSnapshot(board, previousBoard, currentPlayer,
                capturedByBlack, capturedByWhite, consecutivePasses, gameOver);
    }

    public int getCurrentPlayer() {
        return currentPlayer;
    }

    public int getCapturedByBlack() {
        return capturedByBlack;
    }

    public int getCapturedByWhite() {
        return capturedByWhite;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    /** 兼容既有认输控制器；新控制器应记录明确的结束原因。 */
    public void setGameOver(boolean gameOver) {
        this.gameOver = gameOver;
    }

    public int getConsecutivePasses() {
        return consecutivePasses;
    }

    /** 使用当前局面的简单劫信息试走，但不修改游戏。 */
    public MoveResult tryMove(int row, int col, int color) {
        if (gameOver) return MoveResult.illegal(MoveError.GAME_OVER);
        return tryMove(board, previousBoard, row, col, color);
    }

    /** 提交当前执子方的合法落子。 */
    public boolean playMove(int row, int col) {
        MoveResult result = tryMove(row, col, currentPlayer);
        if (!result.legal) return false;

        int[][] boardBeforeMove = board;
        board = result.resultingBoard;
        previousBoard = copyBoard(boardBeforeMove);
        consecutivePasses = 0;
        if (currentPlayer == BLACK) {
            capturedByBlack += result.capturedStones;
        } else {
            capturedByWhite += result.capturedStones;
        }
        switchPlayer();
        return true;
    }

    public void passMove() {
        if (gameOver) return;
        // A pass is an intervening move for simple-ko purposes. The next player may
        // therefore recreate the board that existed before the opponent's last move.
        previousBoard = copyBoard(board);
        consecutivePasses++;
        if (consecutivePasses >= MAX_CONSECUTIVE_PASSES) {
            gameOver = true;
            return;
        }
        switchPlayer();
    }

    private void switchPlayer() {
        currentPlayer = opposite(currentPlayer);
    }

    public boolean isValidMove(int row, int col, int color) {
        return tryMove(row, col, color).isLegal();
    }

    /** 兼容无劫历史的离线局面校验。 */
    public static boolean isValidMove(int[][] state, int row, int col, int color) {
        return tryMove(state, null, row, col, color).isLegal();
    }

    public static boolean isValidMove(int[][] state, int[][] koForbiddenBoard,
                                      int row, int col, int color) {
        return tryMove(state, koForbiddenBoard, row, col, color).isLegal();
    }

    /**
     * 围棋唯一试走入口。输入棋盘永不被修改；合法时结果包含新棋盘和提子数。
     * {@code koForbiddenBoard} 是落子结果不得复现的上一局面，null 表示无劫限制。
     */
    public static MoveResult tryMove(int[][] state, int[][] koForbiddenBoard,
                                     int row, int col, int color) {
        validateBoard(state);
        if (koForbiddenBoard != null) validateBoard(koForbiddenBoard);
        if (!isPlayerColor(color)) return MoveResult.illegal(MoveError.INVALID_COLOR);
        if (!isOnBoard(row, col)) return MoveResult.illegal(MoveError.OUT_OF_BOUNDS);
        if (state[row][col] != EMPTY) return MoveResult.illegal(MoveError.OCCUPIED);

        int[][] simulated = copyBoard(state);
        simulated[row][col] = color;
        int captured = simulateCapture(simulated, opposite(color), row, col);
        if (countLiberties(simulated, row, col) == 0) {
            return MoveResult.illegal(MoveError.SUICIDE);
        }
        if (koForbiddenBoard != null && boardsEqual(simulated, koForbiddenBoard)) {
            return MoveResult.illegal(MoveError.KO);
        }
        return MoveResult.legal(simulated, captured);
    }

    /** 删除落子点相邻的无气敌方棋块。调用方传入的 sim 会被修改。 */
    public static int simulateCapture(int[][] sim, int opponent, int row, int col) {
        validateBoard(sim);
        if (!isPlayerColor(opponent) || !isOnBoard(row, col)) return 0;
        int totalCaptured = 0;
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            int nr = row + d[0];
            int nc = col + d[1];
            if (isOnBoard(nr, nc) && sim[nr][nc] == opponent
                    && countLiberties(sim, nr, nc) == 0) {
                totalCaptured += removeGroup(sim, nr, nc);
            }
        }
        return totalCaptured;
    }

    /** 返回棋块的唯一气数。 */
    public static int countLiberties(int[][] grid, int row, int col) {
        validateBoard(grid);
        if (!isOnBoard(row, col) || !isPlayerColor(grid[row][col])) return 0;
        boolean[][] visitedStones = new boolean[BOARD_SIZE][BOARD_SIZE];
        boolean[][] visitedLiberties = new boolean[BOARD_SIZE][BOARD_SIZE];
        return countLibertiesDfs(grid, row, col, grid[row][col],
                visitedStones, visitedLiberties);
    }

    private static int countLibertiesDfs(int[][] grid, int row, int col, int color,
                                         boolean[][] visitedStones,
                                         boolean[][] visitedLiberties) {
        if (!isOnBoard(row, col)) return 0;
        if (grid[row][col] == EMPTY) {
            if (visitedLiberties[row][col]) return 0;
            visitedLiberties[row][col] = true;
            return 1;
        }
        if (grid[row][col] != color || visitedStones[row][col]) return 0;
        visitedStones[row][col] = true;
        int count = 0;
        count += countLibertiesDfs(grid, row - 1, col, color, visitedStones, visitedLiberties);
        count += countLibertiesDfs(grid, row + 1, col, color, visitedStones, visitedLiberties);
        count += countLibertiesDfs(grid, row, col - 1, color, visitedStones, visitedLiberties);
        count += countLibertiesDfs(grid, row, col + 1, color, visitedStones, visitedLiberties);
        return count;
    }

    public static int removeGroup(int[][] grid, int row, int col) {
        validateBoard(grid);
        if (!isOnBoard(row, col) || !isPlayerColor(grid[row][col])) return 0;
        int color = grid[row][col];
        boolean[][] visited = new boolean[BOARD_SIZE][BOARD_SIZE];
        return removeGroupDfs(grid, row, col, color, visited);
    }

    private static int removeGroupDfs(int[][] grid, int row, int col, int color,
                                      boolean[][] visited) {
        if (!isOnBoard(row, col) || visited[row][col] || grid[row][col] != color) return 0;
        visited[row][col] = true;
        grid[row][col] = EMPTY;
        int count = 1;
        count += removeGroupDfs(grid, row - 1, col, color, visited);
        count += removeGroupDfs(grid, row + 1, col, color, visited);
        count += removeGroupDfs(grid, row, col - 1, color, visited);
        count += removeGroupDfs(grid, row, col + 1, color, visited);
        return count;
    }

    /**
     * 旧接口实际返回盘上棋子数，保留供旧界面兼容；新胜负判定必须使用 calculateScore()。
     */
    @Deprecated
    public int countTerritory(int color) {
        if (!isPlayerColor(color)) return 0;
        int count = 0;
        for (int[] row : board) {
            for (int cell : row) if (cell == color) count++;
        }
        return count;
    }

    public Score calculateScore() {
        return calculateScore(board);
    }

    public static Score calculateScore(int[][] state) {
        validateBoard(state);
        float[][] territory = calculateTerritory(state);
        int blackArea = 0;
        int whiteArea = 0;
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (state[r][c] == BLACK || territory[r][c] < 0) blackArea++;
                if (state[r][c] == WHITE || territory[r][c] > 0) whiteArea++;
            }
        }
        return new Score(blackArea, whiteArea, KOMI);
    }

    public float[][] calculateTerritory() {
        return calculateTerritory(board);
    }

    /** 空点归属：-1 黑、1 白、0 中立。 */
    public static float[][] calculateTerritory(int[][] state) {
        validateBoard(state);
        float[][] territory = new float[BOARD_SIZE][BOARD_SIZE];
        boolean[][] visited = new boolean[BOARD_SIZE][BOARD_SIZE];
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (state[r][c] != EMPTY || visited[r][c]) continue;
                List<int[]> region = new ArrayList<>();
                Deque<int[]> queue = new ArrayDeque<>();
                queue.add(new int[]{r, c});
                visited[r][c] = true;
                boolean bordersBlack = false;
                boolean bordersWhite = false;

                while (!queue.isEmpty()) {
                    int[] cell = queue.removeFirst();
                    region.add(cell);
                    for (int[] d : dirs) {
                        int nr = cell[0] + d[0];
                        int nc = cell[1] + d[1];
                        if (!isOnBoard(nr, nc)) continue;
                        if (state[nr][nc] == BLACK) bordersBlack = true;
                        else if (state[nr][nc] == WHITE) bordersWhite = true;
                        else if (!visited[nr][nc]) {
                            visited[nr][nc] = true;
                            queue.addLast(new int[]{nr, nc});
                        }
                    }
                }

                float owner = 0;
                if (bordersBlack && !bordersWhite) owner = -1;
                else if (bordersWhite && !bordersBlack) owner = 1;
                for (int[] cell : region) territory[cell[0]][cell[1]] = owner;
            }
        }
        return territory;
    }

    public static int[][] copyBoard(int[][] src) {
        validateBoard(src);
        int[][] dst = new int[BOARD_SIZE][BOARD_SIZE];
        for (int r = 0; r < BOARD_SIZE; r++) {
            System.arraycopy(src[r], 0, dst[r], 0, BOARD_SIZE);
        }
        return dst;
    }

    public static boolean boardsEqual(int[][] a, int[][] b) {
        if (!hasBoardShape(a) || !hasBoardShape(b)) return false;
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (a[r][c] != b[r][c]) return false;
            }
        }
        return true;
    }

    public static int opposite(int color) {
        if (color == BLACK) return WHITE;
        if (color == WHITE) return BLACK;
        throw new IllegalArgumentException("color must be BLACK or WHITE");
    }

    private static boolean isPlayerColor(int color) {
        return color == BLACK || color == WHITE;
    }

    private static boolean isOnBoard(int row, int col) {
        return row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE;
    }

    private static boolean hasBoardShape(int[][] state) {
        if (state == null || state.length != BOARD_SIZE) return false;
        for (int[] row : state) {
            if (row == null || row.length != BOARD_SIZE) return false;
        }
        return true;
    }

    private static void validateBoard(int[][] state) {
        if (!hasBoardShape(state)) throw new IllegalArgumentException("board must be 9x9");
        for (int[] row : state) {
            for (int cell : row) {
                if (cell != EMPTY && cell != BLACK && cell != WHITE) {
                    throw new IllegalArgumentException("board contains invalid color: " + cell);
                }
            }
        }
    }
}
