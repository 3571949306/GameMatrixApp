package com.gamecenter.app.games.codebook;

/**
 * 关卡码格式识别器（纯 Java，无 Android 依赖，可被回归测试直接编译运行）。
 *
 * <p>关卡码合集本只做"格式识别 + 展示 + 复制"，不做内容校验：
 * 识别规则与两种游戏编辑器的关卡码格式对齐——</p>
 * <ul>
 *   <li>数独：81 个 '0'-'9' 数字（{@code SudokuPuzzleCodec}，行优先 9x9 盘面，
 *       '0' 表示空格）；</li>
 *   <li>推箱子：单行文本，行以 '/' 分隔，字符集 {@code #-$.*@+_}
 *       （{@code SokobanLevelCodec}）。</li>
 * </ul>
 *
 * <p>识别顺序：先判数独（81 位纯数字），再判推箱子（含 '/' 且全部字符
 * 在推箱子字符集 + 空白容差内）。两种格式互斥可识别；其余一律
 * {@link Kind#UNKNOWN}。只保证"格式合法"，关卡内容合法性（如数独盘面
 * 是否可解、推箱子地图是否有玩家与箱子）由对应游戏编辑器导入时校验。</p>
 */
public final class CodeBookCodec {

    /** 关卡码归属的游戏类型。 */
    public enum Kind {
        /** 推箱子（'/' 分行的地图码）。 */
        SOKOBAN,
        /** 数独（81 位纯数字盘面码）。 */
        SUDOKU,
        /** 无法识别（既不符合数独也不符合推箱子格式）。 */
        UNKNOWN
    }

    /** 数独关卡码固定长度（9x9 = 81 格）。 */
    private static final int SUDOKU_CODE_LENGTH = 81;

    private CodeBookCodec() {
    }

    /**
     * 识别关卡码归属的游戏。
     *
     * <p>规则（trim 后判定）：null / 空串 / 纯空白 → UNKNOWN；
     * 81 位纯数字（允许前导 0）→ SUDOKU；含 '/' 且全部字符在
     * {@code "#-$.*@+_/" + 空格 + Tab} 内 → SOKOBAN；其余 → UNKNOWN。
     * 空白只作容差（推箱子码内部不应有空行，但历史分享码可能带排版空白，
     * 识别从宽、内容校验留给编辑器从严）。</p>
     *
     * @param code 关卡码文本，允许首尾空白
     * @return 归属游戏类型；无法识别返回 {@link Kind#UNKNOWN}
     */
    public static Kind detect(String code) {
        if (code == null) return Kind.UNKNOWN;
        String trimmed = code.trim();
        if (trimmed.isEmpty()) return Kind.UNKNOWN;
        if (trimmed.length() == SUDOKU_CODE_LENGTH && isAllDigits(trimmed)) {
            return Kind.SUDOKU;
        }
        if (trimmed.indexOf('/') >= 0 && isSokobanChars(trimmed)) {
            return Kind.SOKOBAN;
        }
        return Kind.UNKNOWN;
    }

    /**
     * 游戏类型对应的展示名。
     *
     * @param kind 游戏类型
     * @return SOKOBAN→"推箱子"，SUDOKU→"数独"，UNKNOWN→"未知"；null 视为 UNKNOWN
     */
    public static String gameName(Kind kind) {
        if (kind == Kind.SOKOBAN) return "推箱子";
        if (kind == Kind.SUDOKU) return "数独";
        return "未知";
    }

    /** 是否 81 位中每个字符均为 '0'-'9'（调用方保证长度）。 */
    private static boolean isAllDigits(String text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch < '0' || ch > '9') return false;
        }
        return true;
    }

    /** 是否全部字符均在推箱子合法集（含行分隔符 '/' 与空白容差）内。 */
    private static boolean isSokobanChars(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (!isSokobanChar(text.charAt(i))) return false;
        }
        return true;
    }

    /** 单字符是否为推箱子合法字符（"#-$.*@+_"、行分隔符 '/'，或空白容差）。 */
    private static boolean isSokobanChar(char ch) {
        switch (ch) {
            case '#': case '-': case '$': case '.': case '*':
            case '@': case '+': case '_': case '/':
            case ' ': case '\t':
                return true;
            default:
                return false;
        }
    }
}
