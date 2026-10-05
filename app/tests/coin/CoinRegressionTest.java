import com.gamecenter.app.games.coin.CoinLedger;
import com.gamecenter.app.games.coin.EarnRuleEngine;
import com.gamecenter.app.games.coin.TitleCatalog;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 金币经济核心层回归测试（纯 Java，javac 即可运行，无需 Android 运行时）。
 *
 * <p>覆盖：</p>
 * <ul>
 *   <li>账本 {@link CoinLedger}：初始余额、正/负入账、拒绝路径（余额不足、
 *       delta=0、reason/dateKey 非法）全部无副作用、流水防御性副本、按日赚取统计；</li>
 *   <li>规则 {@link EarnRuleEngine}：胜局奖励与连胜加成封顶、败局奖励、每日上限裁剪；</li>
 *   <li>称号 {@link TitleCatalog}：预置目录完整性（购买型恰 4 个、id 唯一）、按 id 查找、
 *      成就称号目录（恰 3 个、门槛 3/8/15、cost 恒 0、与购买型 id 不冲突）；</li>
 *   <li>端到端：开局 → 胜/败入账 → 按日汇总 → 超额消费被拒 → 消费归零。</li>
 * </ul>
 *
 * <p>运行：见 scripts/verify_coin_economy.py。</p>
 */
public class CoinRegressionTest {

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

    /** 造一条历史流水（用于构造灌入，时间戳显式指定）。 */
    static CoinLedger.Entry entry(long ts, int delta, String reason, String dateKey, long after) {
        return new CoinLedger.Entry(ts, delta, reason, dateKey, after);
    }

    // ====================================================================
    // 1. 账本基础：初始余额、正收入、负支出、构造灌入
    // ====================================================================
    static void testLedgerBasics() {
        System.out.println("[T1] 账本基础记账");
        CoinLedger ledger = new CoinLedger(100, new ArrayList<>());
        check("初始余额正确", ledger.balance() == 100);

        CoinLedger.Entry win = ledger.apply(30, "对局胜利", "2026-09-23");
        check("正收入入账成功", win != null);
        check("正收入后余额增长", ledger.balance() == 130);
        check("正收入 balanceAfter 正确", win != null && win.balanceAfter == 130);
        check("正收入 delta 记录正确", win != null && win.delta == 30);
        check("正收入流水已追加", ledger.entries().size() == 1);

        CoinLedger.Entry spend = ledger.apply(-20, "兑换称号", "2026-09-23");
        check("负支出入账成功", spend != null);
        check("负支出后余额减少", ledger.balance() == 110);
        check("负支出 balanceAfter 正确", spend != null && spend.balanceAfter == 110);
        check("负支出流水已追加", ledger.entries().size() == 2);

        // 构造灌入历史流水：余额以构造参数为准，流水按旧→新顺序保留
        List<CoinLedger.Entry> history = new ArrayList<>();
        history.add(entry(1000L, 5, "对局失败", "2026-09-22", 5));
        history.add(entry(2000L, 10, "对局胜利", "2026-09-22", 15));
        CoinLedger restored = new CoinLedger(15, history);
        check("灌入流水后初始余额正确", restored.balance() == 15);
        check("灌入流水按序保留", restored.entries().size() == 2);
        check("灌入流水顺序为旧→新",
                restored.entries().get(0).timestamp == 1000L
                        && restored.entries().get(1).timestamp == 2000L);
    }

    // ====================================================================
    // 2. 账本拒绝路径：全部无副作用
    // ====================================================================
    static void testLedgerRejection() {
        System.out.println("[T2] 账本拒绝路径（无副作用）");
        CoinLedger ledger = new CoinLedger(10, new ArrayList<>());

        check("余额不足拒绝", ledger.apply(-50, "兑换称号", "2026-09-23") == null);
        check("余额不足拒绝后余额不变", ledger.balance() == 10);
        check("余额不足拒绝后流水不变", ledger.entries().isEmpty());

        check("delta=0 拒绝", ledger.apply(0, "对局胜利", "2026-09-23") == null);
        check("delta=0 拒绝后余额不变", ledger.balance() == 10);
        check("delta=0 拒绝后流水不变", ledger.entries().isEmpty());

        check("reason=null 拒绝", ledger.apply(5, null, "2026-09-23") == null);
        check("reason=空串拒绝", ledger.apply(5, "", "2026-09-23") == null);
        check("dateKey=null 拒绝", ledger.apply(5, "对局胜利", null) == null);
        check("dateKey=空串拒绝", ledger.apply(5, "对局胜利", "") == null);
        check("全部拒绝后余额仍不变", ledger.balance() == 10);
        check("全部拒绝后流水仍为空", ledger.entries().isEmpty());
    }

    // ====================================================================
    // 3. 流水防御性副本
    // ====================================================================
    static void testLedgerDefensiveCopy() {
        System.out.println("[T3] 账本防御性副本");
        CoinLedger ledger = new CoinLedger(0, new ArrayList<>());
        check("前置：入账一笔", ledger.apply(10, "对局胜利", "2026-09-23") != null);
        List<CoinLedger.Entry> copy = ledger.entries();
        copy.clear();
        check("修改流水副本不影响内部", ledger.entries().size() == 1);
    }

    // ====================================================================
    // 4. 按日赚取统计：只统计正 delta 且按 dateKey 过滤
    // ====================================================================
    static void testEarnedOnDate() {
        System.out.println("[T4] 按日赚取统计");
        CoinLedger ledger = new CoinLedger(0, new ArrayList<>());
        check("前置：D1 胜利 +10", ledger.apply(10, "对局胜利", "D1") != null);
        check("前置：D1 消费 -5", ledger.apply(-5, "兑换称号", "D1") != null);
        check("前置：D2 胜利 +7", ledger.apply(7, "对局胜利", "D2") != null);
        check("前置：D1 失败 +3", ledger.apply(3, "对局失败", "D1") != null);
        check("D1 只统计正 delta（10+3=13）", ledger.earnedOnDate("D1") == 13);
        check("D2 按日过滤（7）", ledger.earnedOnDate("D2") == 7);
        check("无流水日期返回 0", ledger.earnedOnDate("D3") == 0);
    }

    // ====================================================================
    // 5. 胜局奖励：连胜加成与封顶
    // ====================================================================
    static void testWinReward() {
        System.out.println("[T5] 胜局奖励");
        EarnRuleEngine engine = new EarnRuleEngine();
        check("winReward(1)=10（无加成）", engine.winReward(1) == 10);
        check("winReward(0)=10（按 1 天计）", engine.winReward(0) == 10);
        check("winReward(6)=20（加成恰好封顶）", engine.winReward(6) == 20);
        check("winReward(10)=20（封顶不再增长）", engine.winReward(10) == 20);
        check("winReward(-5)=10（负 streak 按 1 天计）", engine.winReward(-5) == 10);
    }

    // ====================================================================
    // 6. 败局奖励
    // ====================================================================
    static void testLossReward() {
        System.out.println("[T6] 败局奖励");
        EarnRuleEngine engine = new EarnRuleEngine();
        check("lossReward()=3", engine.lossReward() == 3);
    }

    // ====================================================================
    // 7. 每日上限裁剪
    // ====================================================================
    static void testClampToCap() {
        System.out.println("[T7] 每日上限裁剪");
        EarnRuleEngine engine = new EarnRuleEngine();
        check("clampToCap(10, 0)=10（未达上限）", engine.clampToCap(10, 0) == 10);
        check("clampToCap(10, 195)=5（剩余额度）", engine.clampToCap(10, 195) == 5);
        check("clampToCap(10, 200)=0（恰好达上限）", engine.clampToCap(10, 200) == 0);
        check("clampToCap(10, 250)=0（已超上限）", engine.clampToCap(10, 250) == 0);
        check("clampToCap(-5, 200)=-5（负值原样返回）", engine.clampToCap(-5, 200) == -5);
    }

    // ====================================================================
    // 7b. 每日挑战完成奖励
    // ====================================================================
    static void testChallengeReward() {
        System.out.println("[T7b] 每日挑战完成奖励");
        EarnRuleEngine engine = new EarnRuleEngine();
        check("challengeReward()=20（每日挑战完成奖励）", engine.challengeReward() == 20);
        check("CHALLENGE_REWARD=20（常量契约）", EarnRuleEngine.CHALLENGE_REWARD == 20);
    }

    // ====================================================================
    // 8. 称号目录
    // ====================================================================
    static void testTitles() {
        System.out.println("[T8] 称号目录");
        List<TitleCatalog.Title> all = TitleCatalog.all();
        check("all() 恰 4 个称号", all.size() == 4);
        Set<Integer> ids = new HashSet<>();
        for (TitleCatalog.Title t : all) {
            ids.add(t.id);
        }
        check("称号 id 唯一", ids.size() == 4);

        TitleCatalog.Title t1 = TitleCatalog.find(1);
        check("find(1)=青铜玩家/50",
                t1 != null && "青铜玩家".equals(t1.name) && t1.cost == 50);
        TitleCatalog.Title t2 = TitleCatalog.find(2);
        check("find(2)=白银高手/150",
                t2 != null && "白银高手".equals(t2.name) && t2.cost == 150);
        TitleCatalog.Title t3 = TitleCatalog.find(3);
        check("find(3)=黄金战神/400",
                t3 != null && "黄金战神".equals(t3.name) && t3.cost == 400);
        TitleCatalog.Title t4 = TitleCatalog.find(4);
        check("find(4)=传奇殿堂/1000",
                t4 != null && "传奇殿堂".equals(t4.name) && t4.cost == 1000);
        check("find(99)=null（找不到）", TitleCatalog.find(99) == null);
    }

    // ====================================================================
    // 8b. 成就称号目录（成就→称号联动）
    // ====================================================================
    static void testAchievementTitles() {
        System.out.println("[T8b] 成就称号目录");
        List<TitleCatalog.Title> achievementTitles = TitleCatalog.achievementTitles();
        check("achievementTitles() 恰 3 个称号", achievementTitles.size() == 3);

        TitleCatalog.Title a101 = TitleCatalog.find(101);
        check("find(101)=初露锋芒/需 3 个成就",
                a101 != null && "初露锋芒".equals(a101.name) && a101.requiredAchievements == 3);
        TitleCatalog.Title a102 = TitleCatalog.find(102);
        check("find(102)=成就猎人/需 8 个成就",
                a102 != null && "成就猎人".equals(a102.name) && a102.requiredAchievements == 8);
        TitleCatalog.Title a103 = TitleCatalog.find(103);
        check("find(103)=全收集大师/需 15 个成就",
                a103 != null && "全收集大师".equals(a103.name) && a103.requiredAchievements == 15);
        check("find(101).cost==0（成就称号免费授予）",
                a101 != null && a101.cost == 0);
        check("find(102).cost==0（成就称号免费授予）",
                a102 != null && a102.cost == 0);
        check("find(103).cost==0（成就称号免费授予）",
                a103 != null && a103.cost == 0);

        boolean purchasableZeroReq = true;
        for (TitleCatalog.Title t : TitleCatalog.all()) {
            if (t.requiredAchievements != 0) {
                purchasableZeroReq = false;
            }
        }
        check("购买型 4 个 requiredAchievements==0", purchasableZeroReq);

        // 全集 id 唯一：购买型 1-4 与成就型 101-103 不冲突
        Set<Integer> allIds = new HashSet<>();
        for (TitleCatalog.Title t : TitleCatalog.all()) {
            allIds.add(t.id);
        }
        for (TitleCatalog.Title t : achievementTitles) {
            allIds.add(t.id);
        }
        check("全部 7 个称号 id 唯一", allIds.size() == 7);
    }

    // ====================================================================
    // 9. 端到端模拟：赚 → 花的全链路
    // ====================================================================
    static void testEndToEnd() {
        System.out.println("[T9] 端到端模拟");
        EarnRuleEngine engine = new EarnRuleEngine();
        CoinLedger ledger = new CoinLedger(0, new ArrayList<>());
        String date = "2026-09-23";

        check("前置：winReward(3)=14（10+2*2）", engine.winReward(3) == 14);
        int win = engine.clampToCap(engine.winReward(3), ledger.earnedOnDate(date));
        check("首胜经 clamp 后仍为 14", win == 14);
        check("首胜入账成功", ledger.apply(win, "对局胜利", date) != null);
        check("入账后余额 14", ledger.balance() == 14);

        int loss = engine.clampToCap(engine.lossReward(), ledger.earnedOnDate(date));
        check("败局奖励经 clamp 后为 3", loss == 3);
        check("败局入账成功", ledger.apply(loss, "对局失败", date) != null);
        check("败局后余额 17", ledger.balance() == 17);
        check("earnedOnDate 汇总 17（14+3）", ledger.earnedOnDate(date) == 17);

        check("消费 50 被拒（余额不足）", ledger.apply(-50, "兑换称号", date) == null);
        check("拒付后余额仍 17", ledger.balance() == 17);
        check("拒付后流水未增加", ledger.entries().size() == 2);

        check("消费 17 成功", ledger.apply(-17, "兑换称号", date) != null);
        check("消费后余额归零", ledger.balance() == 0);
    }

    // ====================================================================
    public static void main(String[] args) {
        System.out.println("==== 金币经济核心层回归测试 ====");
        testLedgerBasics();
        testLedgerRejection();
        testLedgerDefensiveCopy();
        testEarnedOnDate();
        testWinReward();
        testLossReward();
        testClampToCap();
        testChallengeReward();
        testTitles();
        testAchievementTitles();
        testEndToEnd();
        System.out.println("================================");
        System.out.println("通过=" + passed + "  失败=" + failed);
        if (failed == 0) {
            System.out.println("结论：全部通过，账本/规则/称号契约有效。");
        } else {
            System.out.println("结论：存在失败用例，禁止交付。");
        }
        System.out.println("COIN_TEST_RESULT=" + (failed == 0 ? "PASS" : "FAIL"));
    }
}
