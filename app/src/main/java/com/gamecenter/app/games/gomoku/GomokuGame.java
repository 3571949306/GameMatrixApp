package com.gamecenter.app.games.gomoku;

import java.util.ArrayList;
import java.util.List;

/**
 * 五子棋游戏核心逻辑类。
 * <p>
 * 负责15×15棋盘上的五子棋规则实现，包括：
 * <ul>
 *   <li>落子与合法性判断</li>
 *   <li>五连胜负检测</li>
 *   <li>悔棋（按"一手"为单位，同时撤销双方各一手）</li>
 *   <li>平局检测（棋盘下满）</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>棋盘大小固定为15×15（标准五子棋规格）</li>
 *   <li>方向数组仅包含4个方向（横、竖、正斜、反斜），因为胜负检测时正反双向都扫描</li>
 *   <li>悔棋通过 {@link #undoLastMoves} 实现，每次撤销"一手"即双方各一手</li>
 * </ul>
 */
public class GomokuGame {

    /** 棋盘边长（标准15路） */
    public static final int BOARD_SIZE = 15;

    /** 空位标识 */
    public static final int EMPTY = 0;

    /** 黑子标识 */
    public static final int BLACK = 1;

    /** 白子标识 */
    public static final int WHITE = 2;

    /** 四个方向：水平、垂直、正对角线、反对角线 */
    public static final int[][] DIRECTIONS = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};

    /** 棋盘数据，board[y][x]，0=空 1=黑 2=白 */
    private int[][] board;

    /** 当前执子方 */
    private int currentPlayer;

    /** 对局是否结束 */
    private boolean gameOver;

    /** 获胜方（null表示平局或未结束） */
    private Integer winner;

    /** 落子历史记录 */
    private List<MoveRecord> moveHistory;

    /** 总落子数 */
    private int moveCount;

    /** 最后一手坐标 [x, y] */
    private int[] lastMove;

    /**
     * 构造函数，初始化空棋盘和默认状态。
     */
    public GomokuGame() {
        board = new int[BOARD_SIZE][BOARD_SIZE];
        currentPlayer = BLACK;
        gameOver = false;
        winner = null;
        moveHistory = new ArrayList<>();
        moveCount = 0;
        lastMove = null;
    }

    /**
     * 获取棋盘数据。
     *
     * @return 二维棋盘数组，board[y][x]
     */
    public int[][] getBoard() {
        return board;
    }

    /**
     * 获取当前执子方。
     *
     * @return {@link #BLACK} 或 {@link #WHITE}
     */
    public int getCurrentPlayer() {
        return currentPlayer;
    }

    /**
     * 设置当前执子方（用于联机同步）。
     *
     * @param player 执子方颜色
     */
    public void setCurrentPlayer(int player) {
        this.currentPlayer = player;
    }

    /**
     * 查询对局是否结束。
     *
     * @return 对局结束返回true
     */
    public boolean isGameOver() {
        return gameOver;
    }

    /**
     * 获取获胜方。
     *
     * @return 获胜方颜色，平局或未结束时返回null
     */
    public Integer getWinner() {
        return winner;
    }

    /**
     * 设置对局结束和获胜方。
     *
     * @param winnerPlayer 获胜方颜色
     */
    public void setGameOver(int winnerPlayer) {
        this.gameOver = true;
        this.winner = winnerPlayer;
    }

    /**
     * 获取最后一手坐标。
     *
     * @return 坐标数组 [x, y]，无最后一手时返回null
     */
    public int[] getLastMove() {
        return lastMove;
    }

    /**
     * 获取落子历史记录。
     *
     * @return 落子记录列表
     */
    public List<MoveRecord> getMoveHistory() {
        return moveHistory;
    }

    /**
     * 获取总落子数。
     *
     * @return 落子数
     */
    public int getMoveCount() {
        return moveCount;
    }

    /**
     * 判断指定位置是否为合法落子点。
     * <p>
     * 五子棋规则简单：仅需位置在棋盘内且为空位。
     *
     * @param x 横坐标
     * @param y 纵坐标
     * @return 合法返回true
     */
    public boolean isValidMove(int x, int y) {
        return x >= 0 && x < BOARD_SIZE && y >= 0 && y < BOARD_SIZE && board[y][x] == EMPTY;
    }

    /**
     * 在棋盘上执行落子操作。
     *
     * @param x      横坐标
     * @param y      纵坐标
     * @param player 落子方颜色
     * @return 落子记录对象，非法落子返回null
     */
    public MoveRecord makeMove(int x, int y, int player) {
        if (!isValidMove(x, y)) return null;
        board[y][x] = player;
        MoveRecord record = new MoveRecord(x, y, player);
        moveHistory.add(record);
        moveCount++;
        lastMove = new int[]{x, y};
        return record;
    }

    /**
     * 撤销单手棋（内部方法）。
     * <p>
     * 从历史记录末尾移除，还原棋盘格和落子计数。
     *
     * @param record 要撤销的落子记录
     */
    private void undoMove(MoveRecord record) {
        board[record.y][record.x] = EMPTY;
        moveCount--;
        if (!moveHistory.isEmpty()) {
            MoveRecord last = moveHistory.get(moveHistory.size() - 1);
            lastMove = new int[]{last.x, last.y};
        } else {
            lastMove = null;
        }
    }

    /**
     * 撤销指定手数的棋（每手包含双方各一手）。
     * <p>
     * 每手撤销时先移除AI的棋子，再移除玩家的棋子，
     * 确保撤销后轮到玩家落子。
     *
     * @param count 要撤销的手数
     * @return 实际撤销的手数
     */
    public int undoLastMoves(int count) {
        int undoCount = Math.min(count, moveCount / 2);
        for (int i = 0; i < undoCount; i++) {
            if (moveHistory.size() >= 2) {
                // 先撤销AI的一手
                MoveRecord aiRecord = moveHistory.remove(moveHistory.size() - 1);
                undoMove(aiRecord);
                // 再撤销玩家的一手
                MoveRecord playerRecord = moveHistory.remove(moveHistory.size() - 1);
                undoMove(playerRecord);
            }
        }
        return undoCount;
    }

    /**
     * 检查指定位置是否形成五连。
     * <p>
     * 在四个方向上分别向正反两方向延伸计数，
     * 任一方向连续同色棋子数≥5即获胜。
     *
     * @param x      横坐标
     * @param y      纵坐标
     * @param player 棋子颜色
     * @return 形成五连返回true
     */
    public boolean checkWinAt(int x, int y, int player) {
        if (player == EMPTY) return false;
        for (int[] dir : DIRECTIONS) {
            int count = 1;
            for (int step = 1; step < 5; step++) {
                int nx = x + dir[0] * step;
                int ny = y + dir[1] * step;
                if (nx >= 0 && nx < BOARD_SIZE && ny >= 0 && ny < BOARD_SIZE && board[ny][nx] == player) {
                    count++;
                } else break;
            }
            for (int step = 1; step < 5; step++) {
                int nx = x - dir[0] * step;
                int ny = y - dir[1] * step;
                if (nx >= 0 && nx < BOARD_SIZE && ny >= 0 && ny < BOARD_SIZE && board[ny][nx] == player) {
                    count++;
                } else break;
            }
            if (count >= 5) return true;
        }
        return false;
    }

    /**
     * 检查游戏是否结束。
     * <p>
     * 检查最后一手是否形成五连，或棋盘是否已满（平局）。
     *
     * @return 游戏结束返回true
     */
    public boolean checkGameOver() {
        if (lastMove != null) {
            int x = lastMove[0];
            int y = lastMove[1];
            int player = board[y][x];
            if (checkWinAt(x, y, player)) {
                gameOver = true;
                winner = player;
                return true;
            }
        }
        // 棋盘下满，平局
        if (moveCount >= BOARD_SIZE * BOARD_SIZE) {
            gameOver = true;
            winner = null;
            return true;
        }
        return false;
    }

    /**
     * 切换当前执子方。
     */
    public void switchPlayer() {
        currentPlayer = (currentPlayer == BLACK) ? WHITE : BLACK;
    }

    /**
     * 重置游戏到初始状态。
     */
    public void reset() {
        board = new int[BOARD_SIZE][BOARD_SIZE];
        currentPlayer = BLACK;
        gameOver = false;
        winner = null;
        moveHistory.clear();
        moveCount = 0;
        lastMove = null;
    }

    /**
     * 落子记录，用于历史回放和悔棋。
     */
    public static class MoveRecord {
        /** 落子横坐标 */
        public int x, y;
        /** 落子方颜色 */
        public int player;

        public MoveRecord(int x, int y, int player) {
            this.x = x;
            this.y = y;
            this.player = player;
        }
    }
}
