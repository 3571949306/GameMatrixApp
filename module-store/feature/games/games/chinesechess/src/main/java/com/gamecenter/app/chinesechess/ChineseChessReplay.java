package com.gamecenter.app.chinesechess;

import java.util.ArrayList;
import java.util.List;

/**
 * 对局回放记录器：对局中逐步记录棋盘快照（深拷贝），终局后供回放器重放。
 *
 * <p>设计约束：
 * <ul>
 *   <li>快照使用与 {@link ChineseChessGame#getBoardAsIntArray()} 相同的纯数据编码：
 *       {@code int[行][列]}，0=空位，1..7=红子（将/仕/相/马/车/炮/兵），-1..-7=黑子；
 *       另行记录该局面的走子方（0=红、1=黑）。</li>
 *   <li>不依赖 Piece 类与任何 Android 类型，可脱离模块单独编译（回归测试直接覆盖）。</li>
 *   <li>写入深拷贝、读取防御性拷贝：调用方继续修改原盘或篡改返回值，
 *       都不会污染已记录的快照。</li>
 * </ul>
 *
 * <p>快照序列约定：下标 0 为开局前（或残局初始）局面，每经一次
 * {@code ChineseChessGame.commitMove} 成功落子追加一个，下标 size-1 为终局局面。
 * 悔棋一轮撤销两着时由调用方 {@link #pop()} 两次回退。
 */
public final class ChineseChessReplay {

    /** 单个快照：棋盘编码 + 该局面的走子方。 */
    private static final class Snapshot {
        final int[][] board;
        final int sideToMove;

        Snapshot(int[][] board, int sideToMove) {
            this.board = board;
            this.sideToMove = sideToMove;
        }
    }

    /** 已记录的快照序列。 */
    private final List<Snapshot> snapshots = new ArrayList<>();

    /**
     * 记录一步后的快照（深拷贝棋盘；调用方之后修改原盘不影响已存快照）。
     *
     * @param board      棋盘编码（约定同 getBoardAsIntArray）
     * @param sideToMove 该局面的走子方：0=红、1=黑
     */
    public void recordSnapshot(int[][] board, int sideToMove) {
        snapshots.add(new Snapshot(deepCopy(board), sideToMove));
    }

    /** 快照数（= 已走着数 + 1；0 号为开局前局面）。 */
    public int size() {
        return snapshots.size();
    }

    /**
     * 取第 index 个快照的棋盘（防御性拷贝；越界返回 null）。
     *
     * @param index 快照下标（0=开局前，size-1=终局）
     * @return 棋盘编码副本，越界时 null
     */
    public int[][] snapshot(int index) {
        if (index < 0 || index >= snapshots.size()) return null;
        return deepCopy(snapshots.get(index).board);
    }

    /**
     * 取第 index 个快照的走子方。
     *
     * @return 0=红、1=黑；越界返回 -1
     */
    public int snapshotSide(int index) {
        if (index < 0 || index >= snapshots.size()) return -1;
        return snapshots.get(index).sideToMove;
    }

    /** 删除最后一个快照（悔棋回退用；空表时安全无操作）。 */
    public void pop() {
        if (!snapshots.isEmpty()) snapshots.remove(snapshots.size() - 1);
    }

    /** 清空全部快照（新局时）。 */
    public void clear() {
        snapshots.clear();
    }

    /**
     * 将棋盘编码快照转换为 {@link ChineseChessGame#loadEndgamePosition(int[][], int)}
     * 可装载的规格：每个棋子一行 {typeOrdinal, sideOrdinal, x, y}。
     * 复盘渲染时由临时棋局装载任意中间局面，从而零改动核心逻辑类。
     *
     * @param board 棋盘编码（约定同 getBoardAsIntArray）
     * @return 装载规格数组（无棋子时为空数组）
     */
    public static int[][] toEndgameSpec(int[][] board) {
        List<int[]> pieces = new ArrayList<>();
        for (int y = 0; y < board.length; y++) {
            for (int x = 0; x < board[y].length; x++) {
                int v = board[y][x];
                if (v == 0) continue;
                pieces.add(new int[]{Math.abs(v) - 1, v > 0 ? 0 : 1, x, y});
            }
        }
        return pieces.toArray(new int[0][]);
    }

    /** 深拷贝二维 int 数组（逐行 clone）。 */
    private static int[][] deepCopy(int[][] board) {
        int[][] copy = new int[board.length][];
        for (int y = 0; y < board.length; y++) {
            copy[y] = board[y].clone();
        }
        return copy;
    }
}
