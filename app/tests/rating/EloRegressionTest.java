import com.gamecenter.app.games.rating.EloCalculator;

/**
 * Elo 棋力等级分回归测试（纯 Java，javac 即可运行，无需 Android 运行时）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>期望胜率：同分 0.5；差 400 ≈0.0909；差 800 ≈0.0099；玩家高 400 ≈0.909；
 *       双向对称（E(a,b)+E(b,a)=1）；rating 越界入参防御不抛异常；</li>
 *   <li>newRating：同分胜 1012 / 负 988 / 和 1000（K=24，24*(1-0.5)=12）；
 *       高 400 分胜 +2、高 200 分胜 +6（容差 1）；上限 3000 / 下限 100 钳制；
 *       rating 越界入参先钳后算；</li>
 *   <li>aiRatingForTier：1..4 档映射与越界钳制（RatingStore.aiRatingForTier 为
 *       本方法的委托门面，逻辑真源在此）。</li>
 * </ul>
 *
 * <p>注：RatingStore 是 SP 持久化门面（Android 层），不进本纯 Java 测试；
 * 计分逻辑全部在 EloCalculator。运行：见 scripts/verify_rating.py。</p>
 */
public class EloRegressionTest {

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

    static void checkNear(String name, double actual, double expect, double tol) {
        check(name + " 实际=" + String.format("%.6f", actual) + " 期望=" + expect + "±" + tol,
                Math.abs(actual - expect) <= tol);
    }

    static void checkRating(String name, int player, int opponent, double score, int expect, int tol) {
        int actual = EloCalculator.newRating(player, opponent, score);
        // 钳制区间 100..3000（与 EloCalculator.MIN_RATING/MAX_RATING 一致，包私有故此处用字面量）
        check(name + " 实际=" + actual + " 期望=" + expect + "±" + tol,
                Math.abs(actual - expect) <= tol && actual >= 100 && actual <= 3000);
    }

    // ====================================================================
    // 1. 期望胜率 expectedScore
    // ====================================================================
    static void testExpectedScore() {
        System.out.println("[T1] 期望胜率");
        checkNear("同分 E=0.5", EloCalculator.expectedScore(1000, 1000), 0.5, 1e-9);
        // 玩家比对手低 400 分：E = 1/(1+10) = 0.0909
        checkNear("差 400 E≈0.0909", EloCalculator.expectedScore(1000, 1400), 0.0909, 0.01);
        // 玩家比对手低 800 分：E = 1/(1+10^2) = 0.0099
        checkNear("差 800 E≈0.0099", EloCalculator.expectedScore(1000, 1800), 0.0099, 0.001);
        // 玩家比对手高 400 分：E = 1/(1+10^-1) = 0.909
        checkNear("高 400 E≈0.909", EloCalculator.expectedScore(1400, 1000), 0.909, 0.01);
        // 对称性：E(a,b) + E(b,a) = 1
        checkNear("对称 E(a,b)+E(b,a)=1",
                EloCalculator.expectedScore(1000, 1400) + EloCalculator.expectedScore(1400, 1000),
                1.0, 1e-9);
        // rating 越界入参防御：钳到 [100,3000] 后计算，不抛异常
        checkNear("入参 5000 钳 3000 E≈1", EloCalculator.expectedScore(5000, 1000), 0.99999, 0.0001);
        checkNear("入参 0 钳 100 E≈0.006", EloCalculator.expectedScore(0, 1000), 0.0056, 0.001);
    }

    // ====================================================================
    // 2. newRating 更新
    // ====================================================================
    static void testNewRating() {
        System.out.println("[T2] newRating 更新");
        // 同分对局：E=0.5，胜 +12 / 负 -12 / 和 ±0（K=24，Math.round 取整）
        checkRating("同分胜 (1000,1000,1.0)=1012", 1000, 1000, 1.0, 1012, 0);
        checkRating("同分负 (1000,1000,0.0)=988", 1000, 1000, 0.0, 988, 0);
        checkRating("同分和 (1000,1000,0.5)=1000", 1000, 1000, 0.5, 1000, 0);
        // 玩家高 400 分获胜：E≈0.909，1000+24*(1-0.909)≈1002（强者的边际收益小）
        checkRating("高400胜 (1000,600,1.0)=1002", 1000, 600, 1.0, 1002, 1);
        // 玩家高 200 分获胜：E≈0.7597，1000+24*0.2403≈1006
        checkRating("高200胜 (1000,800,1.0)=1006", 1000, 800, 1.0, 1006, 1);
        // 钳制：上限 3000（同分胜原始值 3012 被钳）、下限 100（同分负原始值 88 被钳）
        checkRating("上限钳制 (3000,3000,1.0)=3000", 3000, 3000, 1.0, 3000, 0);
        checkRating("上限保持 (3000,1000,1.0)=3000", 3000, 1000, 1.0, 3000, 0);
        checkRating("下限钳制 (100,100,0.0)=100", 100, 100, 0.0, 100, 0);
        // rating 越界入参防御：先钳到 [100,3000] 再计算
        // (5000,1000,0.5)：player 钳 3000，E≈0.99999 → 3000+24*(0.5-0.99999)≈2988
        checkRating("入参5000钳3000后和局≈2988", 5000, 1000, 0.5, 2988, 1);
        // (0,1000,1.0)：player 钳 100，E≈0.0056 → 100+24*0.9944≈124
        checkRating("入参0钳100后胜局≈124", 0, 1000, 1.0, 124, 1);
    }

    // ====================================================================
    // 3. AI 档位 rating 映射 aiRatingForTier
    // ====================================================================
    static void testAiRatingForTier() {
        System.out.println("[T3] AI 档位 rating 映射");
        check("档1→800", EloCalculator.aiRatingForTier(1) == 800);
        check("档2→1200", EloCalculator.aiRatingForTier(2) == 1200);
        check("档3→1600", EloCalculator.aiRatingForTier(3) == 1600);
        check("档4→2000", EloCalculator.aiRatingForTier(4) == 2000);
        check("越界 0 钳1→800", EloCalculator.aiRatingForTier(0) == 800);
        check("越界 -3 钳1→800", EloCalculator.aiRatingForTier(-3) == 800);
        check("越界 5 钳4→2000", EloCalculator.aiRatingForTier(5) == 2000);
        check("越界 99 钳4→2000", EloCalculator.aiRatingForTier(99) == 2000);
    }

    // ====================================================================
    public static void main(String[] args) {
        System.out.println("==== Elo 棋力等级分回归测试 ====");
        testExpectedScore();
        testNewRating();
        testAiRatingForTier();
        System.out.println("=================================");
        System.out.println("通过=" + passed + "  失败=" + failed);
        if (failed == 0) {
            System.out.println("结论：全部通过，Elo 公式/钳制/档位映射契约有效。");
        } else {
            System.out.println("结论：存在失败用例，禁止交付。");
        }
        System.out.println("ELO_TEST_RESULT=" + (failed == 0 ? "PASS" : "FAIL"));
    }
}
