package com.gamecenter.app.chinesechess;

import java.util.ArrayList;
import java.util.List;

/**
 * 中国象棋游戏逻辑核心类。
 * <p>
 * 职责：
 * <ul>
 *   <li>管理棋盘状态（10行×9列的二维数组）</li>
 *   <li>实现各棋子的走法规则（将/帅、士/仕、象/相、马、车、炮、兵/卒）</li>
 *   <li>判断将军、将杀、困毙等局面</li>
 *   <li>提供走棋、撤销、合法走法查询等核心接口</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>棋盘使用 {@code Piece[ROWS][COLS]} 二维数组，board[y][x] 表示第y行第x列</li>
 *   <li>走法以 {@code int[]} 表示：[toX, toY]（目标坐标）或 [fromX, fromY, toX, toY]（完整走法）</li>
 *   <li>走棋前不做合法性校验（由调用方负责），走棋后需手动调用 {@link #switchSide()} 切换走棋方</li>
 *   <li>将军检测通过遍历对方所有棋子的走法实现，时间复杂度O(n²)</li>
 * </ul>
 *
 * 【初学者指南】
 * 这个类是中国象棋的"裁判"，是所有棋类游戏中最复杂的裁判：
 * - 7种棋子各有不同的走法规则（将帅走九宫、马走日字、象走田字、车走直线、炮隔子吃等）
 * - 需要判断"将军"（对方的棋子能吃到我方的将帅）
 * - 需要判断"将杀"（被将军且无法解救，游戏结束）
 * - 需要判断"困毙"（没被将军但无路可走，也算输）
 * - 还有特殊规则：飞将（两个将帅不能面对面）、蹩马腿、塞象眼等
 * 棋盘是10行9列，红方在下方，黑方在上方。
 */
public class ChineseChessGame {

    /** 棋盘列数（9路） */
    public static final int COLS = 9;

    /** 棋盘行数（10行） */
    public static final int ROWS = 10;

    /**
     * 棋子类型枚举。
     * <p>顺序：将/帅、士/仕、象/相、马、车、炮、兵/卒
     */
    public enum PieceType {
        GENERAL, ADVISOR, ELEPHANT, HORSE, CHARIOT, CANNON, SOLDIER
    }

    /**
     * 走棋方阵营枚举。
     */
    public enum Side {
        RED, BLACK
    }

    /**
     * 棋子数据类。
     * <p>包含棋子类型、阵营和当前位置。
     * 棋子的x、y坐标在走棋时会被更新。
     */
    public static class Piece {
        /** 棋子类型 */
        public PieceType type;
        /** 所属阵营 */
        public Side side;
        /** 当前列坐标（0~8） */
        public int x, y;

        /**
         * 构造棋子。
         *
         * @param type 棋子类型
         * @param side 所属阵营
         * @param x    列坐标
         * @param y    行坐标
         */
        public Piece(PieceType type, Side side, int x, int y) {
            this.type = type;
            this.side = side;
            this.x = x;
            this.y = y;
        }

        /**
         * 获取棋子的中文名称。
         * <p>红方和黑方同类型棋子名称不同（如：帥/將、仕/士、相/象、炮/砲、兵/卒）。
         *
         * @return 棋子的中文名称
         */
        public String getName() {
            String[] namesRed = {"帥", "仕", "相", "馬", "車", "炮", "兵"};
            String[] namesBlack = {"將", "士", "象", "馬", "車", "砲", "卒"};
            return side == Side.RED ? namesRed[type.ordinal()] : namesBlack[type.ordinal()];
        }
    }

    /**
     * 走棋记录类，用于撤销操作。
     * <p>记录走棋的起止坐标、移动的棋子和被吃的棋子。
     */
    public static class MoveRecord {
        /** 起始列 */
        public int fromX, fromY;
        /** 目标列 */
        public int toX, toY;
        /** 移动的棋子 */
        public Piece piece;
        /** 被吃的棋子（若无则为null） */
        public Piece captured;

        /**
         * 构造走棋记录。
         *
         * @param fx       起始列
         * @param fy       起始行
         * @param tx       目标列
         * @param ty       目标行
         * @param piece    移动的棋子
         * @param captured 被吃的棋子
         */
        public MoveRecord(int fx, int fy, int tx, int ty, Piece piece, Piece captured) {
            this.fromX = fx; this.fromY = fy; this.toX = tx; this.toY = ty;
            this.piece = piece; this.captured = captured;
        }
    }

    /** 棋盘数组，board[y][x] 表示第y行第x列的棋子 */
    private Piece[][] board;

    /** 当前走棋方 */
    private Side currentSide;

    /** 游戏是否结束 */
    private boolean gameOver;

    /** 获胜方 */
    private Side winner;

    /** 走棋历史记录列表 */
    private List<MoveRecord> moveHistory;

    /**
     * 构造游戏对象，初始化棋盘。
     */
    public ChineseChessGame() {
        board = new Piece[ROWS][COLS];
        currentSide = Side.RED;
        gameOver = false;
        winner = null;
        moveHistory = new ArrayList<>();
        initBoard();
    }

    /**
     * 初始化棋盘，按标准中国象棋开局摆放所有棋子。
     * <p>红方在下方（行7~9），黑方在上方（行0~2）。
     * 布局顺序：车马象士将士象马车，炮在行2/行7，兵/卒在行3/行6。
     */
    private void initBoard() {
        board[9][0] = new Piece(PieceType.CHARIOT, Side.RED, 0, 9);
        board[9][1] = new Piece(PieceType.HORSE, Side.RED, 1, 9);
        board[9][2] = new Piece(PieceType.ELEPHANT, Side.RED, 2, 9);
        board[9][3] = new Piece(PieceType.ADVISOR, Side.RED, 3, 9);
        board[9][4] = new Piece(PieceType.GENERAL, Side.RED, 4, 9);
        board[9][5] = new Piece(PieceType.ADVISOR, Side.RED, 5, 9);
        board[9][6] = new Piece(PieceType.ELEPHANT, Side.RED, 6, 9);
        board[9][7] = new Piece(PieceType.HORSE, Side.RED, 7, 9);
        board[9][8] = new Piece(PieceType.CHARIOT, Side.RED, 8, 9);
        board[7][1] = new Piece(PieceType.CANNON, Side.RED, 1, 7);
        board[7][7] = new Piece(PieceType.CANNON, Side.RED, 7, 7);
        board[6][0] = new Piece(PieceType.SOLDIER, Side.RED, 0, 6);
        board[6][2] = new Piece(PieceType.SOLDIER, Side.RED, 2, 6);
        board[6][4] = new Piece(PieceType.SOLDIER, Side.RED, 4, 6);
        board[6][6] = new Piece(PieceType.SOLDIER, Side.RED, 6, 6);
        board[6][8] = new Piece(PieceType.SOLDIER, Side.RED, 8, 6);

        board[0][0] = new Piece(PieceType.CHARIOT, Side.BLACK, 0, 0);
        board[0][1] = new Piece(PieceType.HORSE, Side.BLACK, 1, 0);
        board[0][2] = new Piece(PieceType.ELEPHANT, Side.BLACK, 2, 0);
        board[0][3] = new Piece(PieceType.ADVISOR, Side.BLACK, 3, 0);
        board[0][4] = new Piece(PieceType.GENERAL, Side.BLACK, 4, 0);
        board[0][5] = new Piece(PieceType.ADVISOR, Side.BLACK, 5, 0);
        board[0][6] = new Piece(PieceType.ELEPHANT, Side.BLACK, 6, 0);
        board[0][7] = new Piece(PieceType.HORSE, Side.BLACK, 7, 0);
        board[0][8] = new Piece(PieceType.CHARIOT, Side.BLACK, 8, 0);
        board[2][1] = new Piece(PieceType.CANNON, Side.BLACK, 1, 2);
        board[2][7] = new Piece(PieceType.CANNON, Side.BLACK, 7, 2);
        board[3][0] = new Piece(PieceType.SOLDIER, Side.BLACK, 0, 3);
        board[3][2] = new Piece(PieceType.SOLDIER, Side.BLACK, 2, 3);
        board[3][4] = new Piece(PieceType.SOLDIER, Side.BLACK, 4, 3);
        board[3][6] = new Piece(PieceType.SOLDIER, Side.BLACK, 6, 3);
        board[3][8] = new Piece(PieceType.SOLDIER, Side.BLACK, 8, 3);
    }

    /**
     * 获取棋盘数组。
     *
     * @return 棋盘二维数组
     */
    public Piece[][] getBoard() { return board; }

    /**
     * 将棋盘状态转换为整数数组表示，用于 AI 计算。
     * <p>约定：正值=红子、负值=黑子，绝对值 1..7 对应 将/仕/相/马/车/炮/兵。
     *
     * @return 10x9 的整数数组
     */
    public int[][] getBoardAsIntArray() {
        int[][] result = new int[ROWS][COLS];
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                if (board[y][x] != null) {
                    int pieceValue = board[y][x].type.ordinal() + 1; // 1..7
                    if (board[y][x].side == Side.BLACK) {
                        pieceValue = -pieceValue;
                    }
                    result[y][x] = pieceValue;
                } else {
                    result[y][x] = 0;
                }
            }
        }
        return result;
    }

    /**
     * 获取当前走棋方。
     *
     * @return 当前走棋方阵营
     */
    public Side getCurrentSide() { return currentSide; }

    /**
     * 判断游戏是否结束。
     *
     * @return true 若游戏已结束
     */
    public boolean isGameOver() { return gameOver; }

    /**
     * 获取获胜方。
     *
     * @return 获胜方阵营，游戏未结束时为null
     */
    public Side getWinner() { return winner; }

    /**
     * 获取走棋历史记录。
     *
     * @return 走棋记录列表
     */
    public List<MoveRecord> getMoveHistory() { return moveHistory; }

    /**
     * 检查坐标是否在棋盘范围内。
     *
     * @param x 列坐标
     * @param y 行坐标
     * @return true 若在棋盘内
     */
    private boolean isValidPosition(int x, int y) {
        return x >= 0 && x < COLS && y >= 0 && y < ROWS;
    }

    /**
     * 检查坐标是否在指定阵营的九宫格内。
     * <p>红方九宫：列3~5，行7~9；黑方九宫：列3~5，行0~2。
     *
     * @param x    列坐标
     * @param y    行坐标
     * @param side 阵营
     * @return true 若在九宫格内
     */
    private boolean inPalace(int x, int y, Side side) {
        if (side == Side.RED) return x >= 3 && x <= 5 && y >= 7 && y <= 9;
        else return x >= 3 && x <= 5 && y >= 0 && y <= 2;
    }

    /**
     * 获取指定棋子的所有伪合法走法（不检查走后是否被将）。
     * <p>各棋子走法规则：
     * <ul>
     *   <li>将/帅：九宫内一步直走 + 飞将（同列无阻挡可吃对方将帅）</li>
     *   <li>士/仕：九宫内一步斜走</li>
     *   <li>象/相：田字走法，检查象眼（田字中心），不能过河</li>
     *   <li>马：日字走法，检查蹩马腿</li>
     *   <li>车：直线走法，遇子停止，可吃对方棋子</li>
     *   <li>炮：直线走法，不吃子时同车；吃子时需隔一个炮架</li>
     *   <li>兵/卒：未过河只能前进，过河后可前进或左右</li>
     * </ul>
     *
     * 【初学者提示】"伪合法"是什么意思？
     * 就是说这些走法虽然符合棋子的移动规则，但可能走完后自己的将帅会被对方吃掉。
     * 真正的"合法走法"还需要额外检查走完后自己是否被将，这个检查在 getLegalMoves 中完成。
     * 为什么要分两步？因为检查是否被将比较耗时，先快速筛选出"伪合法"走法可以减少计算量。
     *
     * @param piece 要查询的棋子
     * @return 伪合法走法列表，每个元素为 [toX, toY]
     */
    public List<int[]> getMoves(Piece piece) {
        List<int[]> moves = new ArrayList<>();
        if (piece == null) return moves;
        int x = piece.x, y = piece.y;

        switch (piece.type) {
            case GENERAL: {
                // 将/帅：九宫内上下左右一步
                int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
                for (int[] d : dirs) {
                    int nx = x + d[0], ny = y + d[1];
                    if (inPalace(nx, ny, piece.side) && (board[ny][nx] == null || board[ny][nx].side != piece.side))
                        moves.add(new int[]{nx, ny});
                }
                // 飞将规则：同列且中间无子时可吃对方将帅
                Piece enemyGen = findGeneral(piece.side == Side.RED ? Side.BLACK : Side.RED);
                if (enemyGen != null && enemyGen.x == x) {
                    boolean blocked = false;
                    for (int ty = Math.min(y, enemyGen.y) + 1; ty < Math.max(y, enemyGen.y); ty++) {
                        if (board[ty][x] != null) { blocked = true; break; }
                    }
                    if (!blocked) moves.add(new int[]{enemyGen.x, enemyGen.y});
                }
                break;
            }
            case ADVISOR: {
                // 士/仕：九宫内斜走一步
                int[][] dirs = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
                for (int[] d : dirs) {
                    int nx = x + d[0], ny = y + d[1];
                    if (inPalace(nx, ny, piece.side) && (board[ny][nx] == null || board[ny][nx].side != piece.side))
                        moves.add(new int[]{nx, ny});
                }
                break;
            }
            case ELEPHANT: {
                // 象/相：田字走法，检查象眼，不能过河
                int[][] dirs = {{2, 2}, {2, -2}, {-2, 2}, {-2, -2}};
                int[][] eyes = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
                for (int i = 0; i < dirs.length; i++) {
                    int nx = x + dirs[i][0], ny = y + dirs[i][1];
                    if (isValidPosition(nx, ny)) {
                        // 不能过河：红方y>=5，黑方y<=4
                        if (piece.side == Side.RED && ny < 5) continue;
                        if (piece.side == Side.BLACK && ny > 4) continue;
                        // 象眼（田字中心）无子且目标位置为空或敌方
                        if (board[y + eyes[i][1]][x + eyes[i][0]] == null
                                && (board[ny][nx] == null || board[ny][nx].side != piece.side))
                            moves.add(new int[]{nx, ny});
                    }
                }
                break;
            }
            case HORSE: {
                // 马：日字走法，检查蹩马腿
                int[][] dirs = {{2, 1}, {2, -1}, {-2, 1}, {-2, -1}, {1, 2}, {1, -2}, {-1, 2}, {-1, -2}};
                int[][] legs = {{1, 0}, {1, 0}, {-1, 0}, {-1, 0}, {0, 1}, {0, -1}, {0, 1}, {0, -1}};
                for (int i = 0; i < dirs.length; i++) {
                    int nx = x + dirs[i][0], ny = y + dirs[i][1];
                    // 蹩马腿：马腿位置（直线方向的第一格）有子则不能走
                    if (isValidPosition(nx, ny) && board[y + legs[i][1]][x + legs[i][0]] == null
                            && (board[ny][nx] == null || board[ny][nx].side != piece.side))
                        moves.add(new int[]{nx, ny});
                }
                break;
            }
            case CHARIOT: {
                // 车：直线走法，遇子停止
                int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
                for (int[] d : dirs) {
                    int nx = x + d[0], ny = y + d[1];
                    while (isValidPosition(nx, ny)) {
                        if (board[ny][nx] == null) {
                            moves.add(new int[]{nx, ny});
                        } else {
                            // 遇到敌方棋子可以吃，然后停止
                            if (board[ny][nx].side != piece.side) moves.add(new int[]{nx, ny});
                            break;
                        }
                        nx += d[0]; ny += d[1];
                    }
                }
                break;
            }
            case CANNON: {
                // 炮：不吃子时同车；吃子时需隔一个炮架
                int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
                for (int[] d : dirs) {
                    int nx = x + d[0], ny = y + d[1];
                    boolean jumped = false;
                    while (isValidPosition(nx, ny)) {
                        if (board[ny][nx] == null) {
                            // 未跳过炮架时可以移动到空位
                            if (!jumped) moves.add(new int[]{nx, ny});
                        } else {
                            if (!jumped) {
                                // 遇到第一个子作为炮架
                                jumped = true;
                            } else {
                                // 跳过炮架后遇到第一个子，若是敌方则可吃
                                if (board[ny][nx].side != piece.side) moves.add(new int[]{nx, ny});
                                break;
                            }
                        }
                        nx += d[0]; ny += d[1];
                    }
                }
                break;
            }
            case SOLDIER: {
                // 兵/卒：未过河只能前进，过河后可前进或左右
                if (piece.side == Side.RED) {
                    moves.add(new int[]{x, y - 1}); // 前进（向上）
                    if (y < 5) { // 过河后
                        moves.add(new int[]{x + 1, y});
                        moves.add(new int[]{x - 1, y});
                    }
                } else {
                    moves.add(new int[]{x, y + 1}); // 前进（向下）
                    if (y > 4) { // 过河后
                        moves.add(new int[]{x + 1, y});
                        moves.add(new int[]{x - 1, y});
                    }
                }
                // 过滤掉越界和吃己方棋子的走法
                List<int[]> filtered = new ArrayList<>();
                for (int[] m : moves) {
                    if (isValidPosition(m[0], m[1])
                            && (board[m[1]][m[0]] == null || board[m[1]][m[0]].side != piece.side))
                        filtered.add(m);
                }
                return filtered;
            }
        }
        return moves;
    }

    /**
     * 在棋盘上查找指定阵营的将/帅。
     *
     * @param side 阵营
     * @return 将/帅棋子对象，若未找到返回null
     */
    private Piece findGeneral(Side side) {
        for (int y = 0; y < ROWS; y++)
            for (int x = 0; x < COLS; x++)
                if (board[y][x] != null && board[y][x].type == PieceType.GENERAL && board[y][x].side == side)
                    return board[y][x];
        return null;
    }

    /**
     * 执行走棋操作（不检查合法性）。
     * <p>将棋子从起始位置移动到目标位置，记录被吃的棋子。
     * 注意：此方法不会自动切换走棋方，调用方需手动调用 {@link #switchSide()}。
     *
     * @param fx 起始列
     * @param fy 起始行
     * @param tx 目标列
     * @param ty 目标行
     * @return 走棋记录（用于撤销），若起始位置无棋子返回null
     */
    public MoveRecord makeMoveSafe(int fx, int fy, int tx, int ty) {
        Piece piece = board[fy][fx];
        if (piece == null) return null;
        Piece captured = board[ty][tx];
        MoveRecord record = new MoveRecord(fx, fy, tx, ty, piece, captured);
        board[ty][tx] = piece;
        board[fy][fx] = null;
        piece.x = tx;
        piece.y = ty;
        return record;
    }

    /**
     * 撤销一步走棋。
     * <p>根据走棋记录恢复棋子位置和被吃的棋子。
     * 注意：此方法不会自动切换走棋方，调用方需手动调用 {@link #switchSide()}。
     *
     * @param record 走棋记录
     */
    public void undoMove(MoveRecord record) {
        if (record == null || record.piece == null) return;
        board[record.toY][record.toX] = record.captured;
        board[record.fromY][record.fromX] = record.piece;
        record.piece.x = record.fromX;
        record.piece.y = record.fromY;
    }

    /**
     * 撤销最近若干轮的走棋（每轮=玩家+AI各一步）。
     * <p>撤销后强制将走棋方设为红方，并重置游戏结束状态。
     *
     * @param count 要撤销的轮数
     * @return 实际撤销的轮数
     */
    public int undoLastMoves(int count) {
        int undoCount = Math.min(count, moveHistory.size() / 2);
        for (int i = 0; i < undoCount; i++) {
            if (moveHistory.size() >= 2) {
                // 先撤销AI的走法（后走的），再撤销玩家的走法（先走的）
                MoveRecord aiR = moveHistory.remove(moveHistory.size() - 1);
                undoMove(aiR);
                MoveRecord playerR = moveHistory.remove(moveHistory.size() - 1);
                undoMove(playerR);
            }
        }
        currentSide = Side.RED;
        gameOver = false;
        winner = null;
        return undoCount;
    }

    /**
     * 判断指定阵营是否被将军。
     * <p>遍历对方所有棋子的走法，检查是否有走法能攻击到己方将帅。
     * 此方法利用 {@link #getMoves(Piece)} 的伪合法走法，包含飞将规则。
     *
     * @param side 要检查的阵营
     * @return true 若该阵营被将军
     */
    public boolean isInCheck(Side side) {
        Piece general = findGeneral(side);
        if (general == null) return true; // 将帅不存在视为被将（不应发生）
        Side enemy = side == Side.RED ? Side.BLACK : Side.RED;
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                Piece p = board[y][x];
                if (p != null && p.side == enemy) {
                    for (int[] m : getMoves(p))
                        if (m[0] == general.x && m[1] == general.y) return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断指定阵营是否有合法走法。
     * <p>遍历该阵营所有棋子的伪合法走法，尝试执行后检查是否仍被将，
     * 若存在至少一个走法走后不被将，则返回true。
     *
     * @param side 要检查的阵营
     * @return true 若有合法走法
     */
    public boolean hasLegalMoves(Side side) {
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                Piece p = board[y][x];
                if (p != null && p.side == side) {
                    for (int[] m : getMoves(p)) {
                        MoveRecord r = makeMoveSafe(x, y, m[0], m[1]);
                        if (!isInCheck(side)) { undoMove(r); return true; }
                        undoMove(r);
                    }
                }
            }
        }
        return false;
    }

    /**
     * 切换走棋方。
     */
    public void switchSide() {
        currentSide = currentSide == Side.RED ? Side.BLACK : Side.RED;
    }

    /**
     * 检查游戏是否结束。
     * <p>若当前走棋方被将且无合法走法，则对方获胜（将杀）；
     * 若当前走棋方未被将但无合法走法，则对方获胜（困毙）。
     */
    public void checkGameOver() {
        if (isInCheck(currentSide)) {
            if (!hasLegalMoves(currentSide)) {
                gameOver = true;
                winner = currentSide == Side.RED ? Side.BLACK : Side.RED;
            }
        } else if (!hasLegalMoves(currentSide)) {
            gameOver = true;
            winner = currentSide == Side.RED ? Side.BLACK : Side.RED;
        }
    }

    /**
     * 直接设置游戏结束和获胜方（用于联机对战中接收对方认输等场景）。
     *
     * @param winnerSide 获胜方阵营
     */
    public void setGameOver(Side winnerSide) {
        gameOver = true;
        winner = winnerSide;
    }

    /**
     * 获取指定阵营的所有合法走法。
     * <p>遍历该阵营所有棋子，对每个伪合法走法验证走后不被将，
     * 返回所有合法走法的完整坐标 [fromX, fromY, toX, toY]。
     *
     * @param side 阵营
     * @return 合法走法列表，每个元素为 [fromX, fromY, toX, toY]
     */
    public List<int[]> getAllMoves(Side side) {
        List<int[]> all = new ArrayList<>();
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                Piece p = board[y][x];
                if (p != null && p.side == side) {
                    for (int[] m : getMoves(p)) {
                        MoveRecord r = makeMoveSafe(x, y, m[0], m[1]);
                        if (!isInCheck(side)) all.add(new int[]{x, y, m[0], m[1]});
                        undoMove(r);
                    }
                }
            }
        }
        return all;
    }

    /**
     * 获取指定位置棋子的合法走法。
     * <p>与 {@link #getAllMoves} 类似，但只返回指定棋子的合法走法，
     * 返回格式为 [toX, toY]（仅目标坐标）。
     *
     * @param x 棋子列坐标
     * @param y 棋子行坐标
     * @return 合法走法列表，每个元素为 [toX, toY]
     */
    public List<int[]> getLegalMoves(int x, int y) {
        List<int[]> legal = new ArrayList<>();
        Piece piece = board[y][x];
        if (piece == null) return legal;
        for (int[] m : getMoves(piece)) {
            MoveRecord r = makeMoveSafe(x, y, m[0], m[1]);
            if (!isInCheck(piece.side)) legal.add(new int[]{m[0], m[1]});
            undoMove(r);
        }
        return legal;
    }

    /**
     * 重置游戏到初始状态。
     * <p>清空棋盘并重新摆放所有棋子，走棋方重置为红方。
     */
    public void reset() {
        board = new Piece[ROWS][COLS];
        currentSide = Side.RED;
        gameOver = false;
        winner = null;
        moveHistory.clear();
        initBoard();
    }

    /**
     * 深拷贝整个游戏状态——AI搜索时使用副本，避免污染View渲染的真实棋盘
     * <p>拷贝内容包括：棋盘上所有棋子的独立副本、当前走棋方、游戏结束状态和获胜方。
     * 注意：走棋历史不进行深拷贝（AI搜索不需要）。
     *
     * @return 游戏状态的深拷贝
     */
    public ChineseChessGame deepCopy() {
        ChineseChessGame copy = new ChineseChessGame();
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                copy.board[y][x] = null;
            }
        }
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                Piece orig = board[y][x];
                if (orig != null) {
                    Piece p = new Piece(orig.type, orig.side, orig.x, orig.y);
                    copy.board[y][x] = p;
                }
            }
        }
        copy.currentSide = this.currentSide;
        copy.gameOver = this.gameOver;
        copy.winner = this.winner;
        return copy;
    }
}
