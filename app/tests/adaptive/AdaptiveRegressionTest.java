import com.gamecenter.app.games.adaptive.AdaptiveAdvisor;
import com.gamecenter.app.games.adaptive.AdaptiveAdvisor.Advice;

/**
 * AI 难度自适应推荐回归测试（纯 Java，javac 即可运行，无需 Android 运行时）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>样本不足（0..4 局）：一律 KEEP，推荐档=当前档；</li>
 *   <li>高胜率（70%）：非顶格升档 UP，顶格 KEEP 钳制；</li>
 *   <li>低胜率（20%）：非下限降档 DOWN，下限 KEEP 钳制；</li>
 *   <li>中性胜率（50%）：KEEP；</li>
 *   <li>极端全败（0%）/全胜（100%）：DOWN / UP；</li>
 *   <li>currentTier 越界入参（0、99）：钳到边界内判定，不抛异常；</li>
 *   <li>{@code recommendedTier} 便捷形式边界（顶格/下限/单档/两档）。</li>
 * </ul>
 *
 * <p>运行：见 scripts/verify_adaptive.py。</p>
 */
public class AdaptiveRegressionTest {

    static int passed = 0;
    static int failed = 0;

    static void check(String name, boolean cond) {
        if (cond) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name);
        }
    }

    static void checkAdvice(String name, int win, int loss, int tier, int maxTier, Advice expect) {
        check(name + " advise=" + expect,
                AdaptiveAdvisor.advise(win, loss, tier, maxTier) == expect);
    }

    static void checkTier(String name, int win, int loss, int tier, int maxTier, int expect) {
        check(name + " 推荐档=" + expect,
                AdaptiveAdvisor.recommendedTier(win, loss, tier, maxTier) == expect);
    }

    // ====================================================================
    // 1. 样本不足（总对局 0..4）：不给建议
    // ====================================================================
    static void testInsufficientSample() {
        System.out.println("[T1] 样本不足（0..4 局）");
        checkAdvice("0 局 (0,0) 档2", 0, 0, 2, 4, Advice.KEEP);
        checkTier("0 局 (0,0) 档2", 0, 0, 2, 4, 2);

        checkAdvice("3 局 (2,1) 档3", 2, 1, 3, 4, Advice.KEEP);
        checkTier("3 局 (2,1) 档3", 2, 1, 3, 4, 3);

        // 4 局高胜率也因样本不足不给建议（宁缺勿错）
        checkAdvice("4 局全胜 (4,0) 档1", 4, 0, 1, 4, Advice.KEEP);
        checkTier("4 局全胜 (4,0) 档1", 4, 0, 1, 4, 1);

        checkAdvice("4 局全败 (0,4) 档4", 0, 4, 4, 4, Advice.KEEP);
        checkTier("4 局全败 (0,4) 档4", 0, 4, 4, 4, 4);

        checkAdvice("4 局 (3,1) 档2", 3, 1, 2, 4, Advice.KEEP);
        checkTier("4 局 (3,1) 档2", 3, 1, 2, 4, 2);
    }

    // ====================================================================
    // 2. 高胜率（70%）：升档；顶格钳制
    // ====================================================================
    static void testHighWinRate() {
        System.out.println("[T2] 高胜率 70% (7,3)");
        checkAdvice("档2/4 升档", 7, 3, 2, 4, Advice.UP);
        checkTier("档2/4 推荐3", 7, 3, 2, 4, 3);

        checkAdvice("档4/4 顶格 KEEP", 7, 3, 4, 4, Advice.KEEP);
        checkTier("档4/4 顶格推荐4", 7, 3, 4, 4, 4);

        checkAdvice("档1/4 升档", 7, 3, 1, 4, Advice.UP);
        checkTier("档1/4 推荐2", 7, 3, 1, 4, 2);

        checkAdvice("档3/4 升档", 7, 3, 3, 4, Advice.UP);
        checkTier("档3/4 推荐4", 7, 3, 3, 4, 4);
    }

    // ====================================================================
    // 3. 低胜率（20%）：降档；下限钳制
    // ====================================================================
    static void testLowWinRate() {
        System.out.println("[T3] 低胜率 20% (2,8)");
        checkAdvice("档3/4 降档", 2, 8, 3, 4, Advice.DOWN);
        checkTier("档3/4 推荐2", 2, 8, 3, 4, 2);

        checkAdvice("档1/4 下限 KEEP", 2, 8, 1, 4, Advice.KEEP);
        checkTier("档1/4 下限推荐1", 2, 8, 1, 4, 1);

        checkAdvice("档4/4 降档", 2, 8, 4, 4, Advice.DOWN);
        checkTier("档4/4 推荐3", 2, 8, 4, 4, 3);

        checkAdvice("档2/4 降档", 2, 8, 2, 4, Advice.DOWN);
        checkTier("档2/4 推荐1", 2, 8, 2, 4, 1);
    }

    // ====================================================================
    // 4. 中性胜率（50%）：维持
    // ====================================================================
    static void testNeutralWinRate() {
        System.out.println("[T4] 中性胜率 50% (5,5)");
        checkAdvice("档2/4 KEEP", 5, 5, 2, 4, Advice.KEEP);
        checkTier("档2/4 推荐2", 5, 5, 2, 4, 2);

        checkAdvice("档4/4 KEEP", 5, 5, 4, 4, Advice.KEEP);
        checkTier("档4/4 推荐4", 5, 5, 4, 4, 4);

        checkAdvice("档1/4 KEEP", 5, 5, 1, 4, Advice.KEEP);
        checkTier("档1/4 推荐1", 5, 5, 1, 4, 1);
    }

    // ====================================================================
    // 5. 极端：全败 0% / 全胜 100%
    // ====================================================================
    static void testExtremeRecords() {
        System.out.println("[T5] 极端全败 (0,10) / 全胜 (10,0)");
        checkAdvice("全败 档3/4 DOWN", 0, 10, 3, 4, Advice.DOWN);
        checkTier("全败 档3/4 推荐2", 0, 10, 3, 4, 2);
        checkAdvice("全败 档1/4 下限 KEEP", 0, 10, 1, 4, Advice.KEEP);
        checkTier("全败 档1/4 推荐1", 0, 10, 1, 4, 1);

        checkAdvice("全胜 档1/4 UP", 10, 0, 1, 4, Advice.UP);
        checkTier("全胜 档1/4 推荐2", 10, 0, 1, 4, 2);
        checkAdvice("全胜 档4/4 顶格 KEEP", 10, 0, 4, 4, Advice.KEEP);
        checkTier("全胜 档4/4 推荐4", 10, 0, 4, 4, 4);
    }

    // ====================================================================
    // 6. 档位越界入参（0、99）：钳到边界内判定，不抛异常
    // ====================================================================
    static void testTierClamping() {
        System.out.println("[T6] 档位越界入参钳制");
        try {
            checkAdvice("高胜率 tier=0 钳1 后 UP", 7, 3, 0, 4, Advice.UP);
            checkTier("高胜率 tier=0 推荐2", 7, 3, 0, 4, 2);

            checkAdvice("高胜率 tier=99 钳4 顶格 KEEP", 7, 3, 99, 4, Advice.KEEP);
            checkTier("高胜率 tier=99 推荐4", 7, 3, 99, 4, 4);

            checkAdvice("低胜率 tier=0 钳1 下限 KEEP", 2, 8, 0, 4, Advice.KEEP);
            checkTier("低胜率 tier=0 推荐1", 2, 8, 0, 4, 1);

            checkAdvice("低胜率 tier=99 钳4 后 DOWN", 2, 8, 99, 4, Advice.DOWN);
            checkTier("低胜率 tier=99 推荐3", 2, 8, 99, 4, 3);

            checkAdvice("中性 tier=0 钳1 KEEP", 5, 5, 0, 4, Advice.KEEP);
            checkTier("中性 tier=0 推荐1", 5, 5, 0, 4, 1);

            checkAdvice("中性 tier=99 钳4 KEEP", 5, 5, 99, 4, Advice.KEEP);
            checkTier("中性 tier=99 推荐4", 5, 5, 99, 4, 4);

            checkAdvice("样本不足 tier=0 KEEP", 0, 0, 0, 4, Advice.KEEP);
            checkTier("样本不足 tier=0 推荐1", 0, 0, 0, 4, 1);

            checkAdvice("样本不足 tier=99 KEEP", 0, 0, 99, 4, Advice.KEEP);
            checkTier("样本不足 tier=99 推荐4", 0, 0, 99, 4, 4);
        } catch (RuntimeException e) {
            check("越界入参不抛异常", false);
        }
    }

    // ====================================================================
    // 7. recommendedTier 边界
    // ====================================================================
    static void testRecommendedTierBounds() {
        System.out.println("[T7] recommendedTier 边界");
        checkTier("UP 恰在顶格前一档 (10,0,3,4)", 10, 0, 3, 4, 4);
        checkTier("DOWN 恰在下限后一档 (0,10,2,4)", 0, 10, 2, 4, 1);
        checkTier("顶格再 UP 不越界 (10,0,4,4)", 10, 0, 4, 4, 4);
        checkTier("下限再 DOWN 不越界 (0,10,1,4)", 0, 10, 1, 4, 1);
        checkTier("单档 maxTier=1 全胜仍为1 (10,0,1,1)", 10, 0, 1, 1, 1);
        checkTier("单档 maxTier=1 全败仍为1 (0,10,1,1)", 0, 10, 1, 1, 1);
        checkTier("两档 maxTier=2 低档全胜升2 (10,0,1,2)", 10, 0, 1, 2, 2);
        checkTier("两档 maxTier=2 高档全败降1 (0,10,2,2)", 0, 10, 2, 2, 1);
    }

    // ====================================================================
    public static void main(String[] args) {
        System.out.println("==== AI 难度自适应推荐回归测试 ====");
        testInsufficientSample();
        testHighWinRate();
        testLowWinRate();
        testNeutralWinRate();
        testExtremeRecords();
        testTierClamping();
        testRecommendedTierBounds();
        System.out.println("=================================");
        System.out.println("通过=" + passed + "  失败=" + failed);
        if (failed == 0) {
            System.out.println("结论：全部通过，样本量/胜率/钳制契约有效。");
        } else {
            System.out.println("结论：存在失败用例，禁止交付。");
        }
        System.out.println("ADAPTIVE_TEST_RESULT=" + (failed == 0 ? "PASS" : "FAIL"));
    }
}
