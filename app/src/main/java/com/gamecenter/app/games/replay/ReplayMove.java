package com.gamecenter.app.games.replay;

/**
 * P2-7 (BOARD_REPLAY): 统一棋类回放走法表示。
 *
 * 兼容两类走法：
 * - 落子型（Gomoku/TicTacToe/Go）：使用 (row, col, player)
 * - 移动型（Checkers/ChineseChess）：使用 (fromRow, fromCol, toRow, toCol, player)
 *
 * 对于落子型，fromRow/fromCol = -1；
 * 对于移动型，所有坐标有效。
 *
 * 额外字段 extra 用于附加信息（如被吃子、是否升王等），可为空。
 */
public class ReplayMove {

    /** 落子型：起始坐标为 -1 */
    public static final int NO_COORD = -1;

    public final int fromRow;
    public final int fromCol;
    public final int toRow;
    public final int toCol;
    public final int player;
    public final String extra;

    public ReplayMove(int row, int col, int player) {
        this(NO_COORD, NO_COORD, row, col, player, null);
    }

    public ReplayMove(int fromRow, int fromCol, int toRow, int toCol, int player) {
        this(fromRow, fromCol, toRow, toCol, player, null);
    }

    public ReplayMove(int fromRow, int fromCol, int toRow, int toCol, int player, String extra) {
        this.fromRow = fromRow;
        this.fromCol = fromCol;
        this.toRow = toRow;
        this.toCol = toCol;
        this.player = player;
        this.extra = extra;
    }

    /** 是否为落子型走法 */
    public boolean isPlacement() {
        return fromRow == NO_COORD && fromCol == NO_COORD;
    }

    /** 序列化为紧凑字符串：placement="R,C,P"; movement="FR,FC,TR,TC,P[;extra]" */
    public String encode() {
        if (isPlacement()) {
            return toRow + "," + toCol + "," + player;
        }
        String base = fromRow + "," + fromCol + "," + toRow + "," + toCol + "," + player;
        return extra != null ? base + ";" + extra : base;
    }

    /** 反序列化 */
    public static ReplayMove decode(String s) {
        String[] parts = s.split(";", 2);
        String extra = parts.length > 1 ? parts[1] : null;
        String[] coords = parts[0].split(",");
        if (coords.length == 3) {
            return new ReplayMove(
                    Integer.parseInt(coords[0]),
                    Integer.parseInt(coords[1]),
                    Integer.parseInt(coords[2]));
        } else if (coords.length == 5) {
            return new ReplayMove(
                    Integer.parseInt(coords[0]),
                    Integer.parseInt(coords[1]),
                    Integer.parseInt(coords[2]),
                    Integer.parseInt(coords[3]),
                    Integer.parseInt(coords[4]),
                    extra);
        }
        throw new IllegalArgumentException("Invalid ReplayMove: " + s);
    }

    @Override
    public String toString() {
        return isPlacement()
                ? "Place(" + toRow + "," + toCol + ",p=" + player + ")"
                : "Move(" + fromRow + "," + fromCol + "->" + toRow + "," + toCol + ",p=" + player + ")";
    }
}
