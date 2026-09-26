import com.gamecenter.app.games.codebook.CodeBookCodec;
import com.gamecenter.app.games.codebook.CodeBookCodec.Kind;

/**
 * 关卡码合集本格式识别回归测试（纯 Java，javac 即可运行，无需 Android 运行时）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>数独识别：81 位纯数字（含前导 0 / 全 1 / 全 0）、首尾空白 trim；</li>
 *   <li>推箱子识别：标准关卡码、内部空格/Tab 容差、仅 "/"；</li>
 *   <li>UNKNOWN：null / 空串 / 纯空白、81 字符含字母、长度 80/82、
 *       含 '/' 但混入非法字符、字符合法但不含 '/'；</li>
 *   <li>gameName 三分支 + null 兜底；</li>
 *   <li>roundtrip：码 → detect → gameName 链路一致性。</li>
 * </ul>
 *
 * <p>分工约定：本测试只验证"格式合法"（detect 契约）。格式合法但内容非法的码
 * （如 81 个 '1' 的数独盘面不满足数独行/列/宫约束、空地图推箱子码无玩家与箱子）
 * 仍应被识别为对应游戏——内容合法性由游戏编辑器导入时校验，合集本不做二次判定。</p>
 *
 * <p>运行：见 scripts/verify_codebook.py。</p>
 */
public class CodeBookRegressionTest {

    static int passed = 0;
    static int failed = 0;

    /** 81 个 '1'：格式合法的数独码，内容是否可解由数独编辑器校验（分工注释见类 javadoc）。 */
    static final String SUDOKU_ALL_ONES = "1".repeat(81);
    /** 81 个 '0'：全空盘面，数独编辑器的合法起点。 */
    static final String SUDOKU_ALL_ZEROS = "0".repeat(81);
    /** 含前导 0 的 81 位数字（首位 '0'，验证数字判定不受前导 0 干扰）。 */
    static final String SUDOKU_LEADING_ZERO = "0" + "123456789".repeat(9).substring(0, 80);
    /** 标准推箱子关卡码：三行矩形地图，'/' 分行。 */
    static final String SOKOBAN_STANDARD = "#####/#@$.$#/#####";

    static void check(String name, boolean cond) {
        if (cond) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name);
        }
    }

    static void checkDetect(String name, String code, Kind expect) {
        Kind actual = CodeBookCodec.detect(code);
        check(name + " detect=" + expect + " 实际=" + actual, actual == expect);
    }

    // ====================================================================
    // 1. 数独识别（81 位纯数字）
    // ====================================================================
    static void testSudoku() {
        System.out.println("[T1] 数独识别（81 位纯数字）");
        checkDetect("81 个 1（内容合法性归编辑器校验）", SUDOKU_ALL_ONES, Kind.SUDOKU);
        checkDetect("81 个 0（全空盘面）", SUDOKU_ALL_ZEROS, Kind.SUDOKU);
        checkDetect("含前导 0 的 81 位", SUDOKU_LEADING_ZERO, Kind.SUDOKU);
        checkDetect("81 位数字首尾空白 trim", "  " + SUDOKU_ALL_ONES + "\t", Kind.SUDOKU);
        check("81 个 1 长度=81", SUDOKU_ALL_ONES.length() == 81);
    }

    // ====================================================================
    // 2. 推箱子识别（含 '/' 且字符均在合法集内）
    // ====================================================================
    static void testSokoban() {
        System.out.println("[T2] 推箱子识别（含 '/' 且字符合法）");
        checkDetect("标准三行码", SOKOBAN_STANDARD, Kind.SOKOBAN);
        checkDetect("内部空格容差", "#####/#@$. #/#####", Kind.SOKOBAN);
        checkDetect("内部 Tab 容差", "#####/#@$. #/#####".replace(' ', '\t'), Kind.SOKOBAN);
        // 边界：仅 "/"——'/' 是合法字符且含 '/'，按规则判 SOKOBAN（接受，
        // 空行地图的内容合法性由推箱子编辑器导入时校验）
        checkDetect("仅 \"/\"（规则接受）", "/", Kind.SOKOBAN);
        checkDetect("首尾空白 trim", "  " + SOKOBAN_STANDARD + "  ", Kind.SOKOBAN);
        checkDetect("含全部 8 种格子字符", "_#-.*@$+/#_-.*@$/###", Kind.SOKOBAN);
    }

    // ====================================================================
    // 3. UNKNOWN（无法识别）
    // ====================================================================
    static void testUnknown() {
        System.out.println("[T3] UNKNOWN 分支");
        checkDetect("null", null, Kind.UNKNOWN);
        checkDetect("空串", "", Kind.UNKNOWN);
        checkDetect("纯空格", "   ", Kind.UNKNOWN);
        checkDetect("纯 Tab", "\t\t", Kind.UNKNOWN);
        checkDetect("81 字符含字母（80 数字+1 字母）",
                SUDOKU_ALL_ONES.substring(0, 80) + "a", Kind.UNKNOWN);
        checkDetect("80 位数字（长度不足）", "1".repeat(80), Kind.UNKNOWN);
        checkDetect("82 位数字（长度超出）", "1".repeat(82), Kind.UNKNOWN);
        // 混合非法：含 '/' 但混入数字与字母
        checkDetect("含 '/' 但混入数字", "#####/#@1$#/#####", Kind.UNKNOWN);
        checkDetect("含 '/' 但混入字母", "abc/def", Kind.UNKNOWN);
        checkDetect("含 '/' 但混入标点", "###/#!/###", Kind.UNKNOWN);
        // 字符合法但不含 '/'：不满足推箱子行分隔特征
        checkDetect("合法字符但不含 '/'", "#####", Kind.UNKNOWN);
        checkDetect("单个 '#'（无数独长度也无 '/'）", "#", Kind.UNKNOWN);
    }

    // ====================================================================
    // 4. gameName 展示名
    // ====================================================================
    static void testGameName() {
        System.out.println("[T4] gameName 展示名");
        check("SOKOBAN→推箱子", "推箱子".equals(CodeBookCodec.gameName(Kind.SOKOBAN)));
        check("SUDOKU→数独", "数独".equals(CodeBookCodec.gameName(Kind.SUDOKU)));
        check("UNKNOWN→未知", "未知".equals(CodeBookCodec.gameName(Kind.UNKNOWN)));
        check("null→未知（兜底）", "未知".equals(CodeBookCodec.gameName(null)));
    }

    // ====================================================================
    // 5. roundtrip：码 → detect → gameName 链路
    // ====================================================================
    static void testRoundtrip() {
        System.out.println("[T5] roundtrip（码 → detect → gameName）");
        check("推箱子码 roundtrip→推箱子",
                "推箱子".equals(CodeBookCodec.gameName(CodeBookCodec.detect(SOKOBAN_STANDARD))));
        check("数独码 roundtrip→数独",
                "数独".equals(CodeBookCodec.gameName(CodeBookCodec.detect(SUDOKU_ALL_ZEROS))));
        check("垃圾码 roundtrip→未知",
                "未知".equals(CodeBookCodec.gameName(CodeBookCodec.detect("not-a-code"))));
        check("空白 roundtrip→未知",
                "未知".equals(CodeBookCodec.gameName(CodeBookCodec.detect("  \t "))));
    }

    // ====================================================================
    public static void main(String[] args) {
        System.out.println("==== 关卡码合集本格式识别回归测试 ====");
        testSudoku();
        testSokoban();
        testUnknown();
        testGameName();
        testRoundtrip();
        System.out.println("=================================");
        System.out.println("通过=" + passed + "  失败=" + failed);
        if (failed == 0) {
            System.out.println("结论：全部通过，数独/推箱子/未知三分支识别契约有效。");
        } else {
            System.out.println("结论：存在失败用例，禁止交付。");
        }
        System.out.println("CODEBOOK_TEST_RESULT=" + (failed == 0 ? "PASS" : "FAIL"));
    }
}
