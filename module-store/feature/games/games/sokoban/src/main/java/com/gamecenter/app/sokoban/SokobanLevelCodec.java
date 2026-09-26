package com.gamecenter.app.sokoban;

/**
 * 推箱子关卡码编解码器（纯 Java，无 Android 依赖，可被回归测试直接编译运行）。
 *
 * <p>自定义关卡以单行文本「关卡码」表示，用于编辑器保存、导入导出与分享。
 * 各地图行用 {@code /} 分隔，每行每列一个字符：</p>
 * <table border="1">
 *   <tr><th>字符</th><th>格子</th></tr>
 *   <tr><td>{@code #}</td><td>墙 WALL</td></tr>
 *   <tr><td>{@code @}</td><td>玩家 PLAYER</td></tr>
 *   <tr><td>{@code +}</td><td>玩家在目标点 PLAYER_ON_TARGET</td></tr>
 *   <tr><td>{@code $}</td><td>箱子 BOX</td></tr>
 *   <tr><td>{@code *}</td><td>箱子在目标点 BOX_ON_TARGET</td></tr>
 *   <tr><td>{@code .}</td><td>目标点 TARGET</td></tr>
 *   <tr><td>{@code -}</td><td>地板 FLOOR</td></tr>
 *   <tr><td>{@code _}</td><td>地图外空白 EMPTY</td></tr>
 * </table>
 *
 * <p>全字符均为非空白，行尾不会被 trim，适合剪贴板传递。编解码按不可信输入处理：
 * 空文本、非法字符、行长不一致（非矩形）、尺寸超限一律返回 {@code null}。</p>
 */
public final class SokobanLevelCodec {

    /** 关卡码行分隔符。 */
    public static final char ROW_SEP = '/';
    /** 与 {@link SokobanGame#CUSTOM_LEVEL} 校验一致的尺寸上限。 */
    public static final int MAX_DIM = 20;

    private SokobanLevelCodec() {
    }

    /**
     * 编码地图为关卡码。
     *
     * @param map 地图二维数组，元素取值为 {@link SokobanGame} 常量
     * @return 单行关卡码；地图为空/含非法值/超限时返回 {@code null}
     */
    public static String encode(int[][] map) {
        if (map == null || map.length == 0 || map[0].length == 0) return null;
        if (map.length > MAX_DIM || map[0].length > MAX_DIM) return null;

        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < map.length; r++) {
            if (map[r] == null || map[r].length != map[0].length) return null;
            if (r > 0) sb.append(ROW_SEP);
            for (int c = 0; c < map[r].length; c++) {
                char ch = cellToChar(map[r][c]);
                if (ch == 0) return null;
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /**
     * 解码关卡码为地图。
     *
     * @param code 关卡码文本
     * @return 地图二维数组；文本为空、含非法字符、非矩形、尺寸超限时返回 {@code null}
     */
    public static int[][] decode(String code) {
        if (code == null) return null;
        String trimmed = code.trim();
        if (trimmed.isEmpty()) return null;

        String[] rows = trimmed.split(String.valueOf(ROW_SEP), -1);
        if (rows.length == 0 || rows.length > MAX_DIM) return null;
        int rowLen = rows[0].length();
        if (rowLen == 0 || rowLen > MAX_DIM) return null;

        int[][] map = new int[rows.length][rowLen];
        for (int r = 0; r < rows.length; r++) {
            if (rows[r].length() != rowLen) return null;
            for (int c = 0; c < rowLen; c++) {
                int cell = charToCell(rows[r].charAt(c));
                if (cell < 0) return null;
                map[r][c] = cell;
            }
        }
        return map;
    }

    private static char cellToChar(int cell) {
        switch (cell) {
            case SokobanGame.EMPTY: return '_';
            case SokobanGame.WALL: return '#';
            case SokobanGame.FLOOR: return '-';
            case SokobanGame.TARGET: return '.';
            case SokobanGame.BOX: return '$';
            case SokobanGame.BOX_ON_TARGET: return '*';
            case SokobanGame.PLAYER: return '@';
            case SokobanGame.PLAYER_ON_TARGET: return '+';
            default: return 0;
        }
    }

    private static int charToCell(char ch) {
        switch (ch) {
            case '_': return SokobanGame.EMPTY;
            case '#': return SokobanGame.WALL;
            case '-': return SokobanGame.FLOOR;
            case '.': return SokobanGame.TARGET;
            case '$': return SokobanGame.BOX;
            case '*': return SokobanGame.BOX_ON_TARGET;
            case '@': return SokobanGame.PLAYER;
            case '+': return SokobanGame.PLAYER_ON_TARGET;
            default: return -1;
        }
    }
}
