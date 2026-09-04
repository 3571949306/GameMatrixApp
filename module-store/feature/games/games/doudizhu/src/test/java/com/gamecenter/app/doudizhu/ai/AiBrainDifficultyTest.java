package com.gamecenter.app.doudizhu.ai;

import static com.gamecenter.app.doudizhu.logic.TestCards.of;
import static com.gamecenter.app.doudizhu.logic.TestCards.straight;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.gamecenter.app.doudizhu.logic.Combo;
import com.gamecenter.app.doudizhu.model.Card;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 三档难度行为差异测试：同牌面同局面下，简单/普通/困难产出必须可区分，
 * 且差异来自策略结构而非随机因子。
 */
public class AiBrainDifficultyTest {

    // ============ 首发差异 ============

    /**
     * 手牌 34567(顺子) + 33 + 9：
     * 简单档无结构规划，首发最小单张 4（拆顺子成员）；普通/困难按分解路线出
     * 主权重最小的手（对子 33）。
     */
    @Test
    public void easyLeadsSmallestSingleWhileNormalLeadsPlannedHand() {
        List<Card> hand = new ArrayList<>();
        hand.addAll(straight(3, 5));      // 34567
        hand.addAll(of(3, 2));            // 33（注意 3 只有 4 张，straight 已用 1 张 3）
        hand.addAll(of(9, 1));

        List<Card> easy = AiBrain.decidePlay(hand, null, null, AiBrain.DIFFICULTY_EASY,
                null, null);
        assertNotNull(easy);
        assertEquals("简单档首发最小单张", 1, easy.size());
        assertEquals(4, easy.get(0).getWeight());

        List<Card> normal = AiBrain.decidePlay(hand, null, null, AiBrain.DIFFICULTY_NORMAL,
                null, null);
        assertNotNull(normal);
        assertEquals("普通档首发应为分解路线中的对子 33", 2, normal.size());
        assertEquals(3, normal.get(0).getWeight());
    }

    // ============ 接牌差异 ============

    /**
     * 手牌 55、1010、JJ；上家出单张 8：
     * 简单/普通出最小能压的 55 拆单（5<10）；结构差异用队友放行场景断言更清晰，
     * 此处断言三档都合法且困难档不劣于拆最小单。
     */
    @Test
    public void followSingleUsesMinimalBeatingCard() {
        List<Card> hand = new ArrayList<>();
        hand.addAll(of(5, 2));
        hand.addAll(of(10, 2));
        hand.addAll(of(11, 2));
        List<Card> previous = of(8, 1);

        for (int difficulty = AiBrain.DIFFICULTY_EASY;
             difficulty <= AiBrain.DIFFICULTY_HARD; difficulty++) {
            List<Card> move = AiBrain.decidePlay(hand, previous, null, difficulty,
                    null, null);
            assertNotNull("手牌充裕时不应放弃: diff=" + difficulty, move);
            assertEquals(1, move.size());
            assertTrue(move.get(0).getWeight() > 8);
        }
    }

    /**
     * 简单档 15% 放弃概率可观测：固定种子下放弃次数在 (0, 轮数) 之间；
     * 普通/困难同样局面下从不放弃（有非炸可压必出）。
     */
    @Test
    public void easySometimesGivesUpButHardNeverDoes() {
        List<Card> hand = new ArrayList<>();
        hand.addAll(of(5, 2));
        hand.addAll(of(10, 2));
        hand.addAll(of(11, 2));
        List<Card> previous = of(8, 1);

        int easyPass = 0;
        Random random = new Random(42L);
        for (int i = 0; i < 200; i++) {
            List<Card> move = AiBrain.decidePlay(hand, previous, null,
                    AiBrain.DIFFICULTY_EASY, random, null);
            if (move == null) easyPass++;
        }
        assertTrue("简单档应出现放弃（15% 量级），实际 " + easyPass, easyPass > 0);
        assertTrue("简单档放弃率应远低于一半，实际 " + easyPass, easyPass < 100);

        for (int difficulty = AiBrain.DIFFICULTY_NORMAL;
             difficulty <= AiBrain.DIFFICULTY_HARD; difficulty++) {
            for (int i = 0; i < 50; i++) {
                List<Card> move = AiBrain.decidePlay(hand, previous, null, difficulty,
                        new Random(100L + i), null);
                assertNotNull("普通/困难档有牌必接: diff=" + difficulty, move);
            }
        }
    }

    /**
     * 简单档绝不动炸弹；普通/困难在敌方只剩 2 张时用炸弹拦截。
     */
    @Test
    public void bombUsageDiffersByDifficulty() {
        List<Card> hand = new ArrayList<>();
        hand.addAll(of(5, 2));
        hand.addAll(of(3, 4));   // 3333 炸弹
        List<Card> previous = of(8, 1); // 上家地主出单 8

        List<Card> easy = AiBrain.decidePlay(hand, previous, farmerVsLandlord(2),
                AiBrain.DIFFICULTY_EASY, null, null);
        assertNull("简单档不主动用炸弹", easy);

        int[] eager = {AiBrain.DIFFICULTY_NORMAL, AiBrain.DIFFICULTY_HARD};
        for (int difficulty : eager) {
            List<Card> move = AiBrain.decidePlay(hand, previous, farmerVsLandlord(2),
                    difficulty, null, null);
            assertNotNull("敌方快赢时普通/困难应拦截", move);
            assertEquals(4, move.size());
        }
    }

    /**
     * 敌方不紧急时普通/困难也不轻易动炸弹（炸弹保护策略）。
     */
    @Test
    public void bombProtectedWhenEnemyNotUrgent() {
        List<Card> hand = new ArrayList<>();
        hand.addAll(of(5, 2));
        hand.addAll(of(3, 4));
        List<Card> previous = of(8, 1);

        int[] calm = {AiBrain.DIFFICULTY_NORMAL, AiBrain.DIFFICULTY_HARD};
        for (int difficulty : calm) {
            List<Card> move = AiBrain.decidePlay(hand, previous, farmerVsLandlord(17),
                    difficulty, null, null);
            if (move != null) {
                assertTrue("非紧急不动炸弹: diff=" + difficulty, move.size() != 4);
            }
        }
    }

    /**
     * 农民对队友：普通档永远放行；困难档在"打出后手数减少"时接管。
     * 场景：队友出单 4，我手牌 55+66+KK+AA（8 张，接 5 后 7 张仍 3 手 → 不接管）；
     * 反例：我手牌只剩 5+K 两张单（接 5 后剩 1 手 → 接管）。
     */
    @Test
    public void farmerTeammatePolicyDiffers() {
        List<Card> previous = of(4, 1);

        // 普通档：队友牌永远放行
        List<Card> rich = new ArrayList<>();
        rich.addAll(of(5, 2));
        rich.addAll(of(6, 2));
        rich.addAll(of(13, 2));
        rich.addAll(of(14, 2));
        assertNull("普通档对队友放行",
                AiBrain.decidePlay(rich, previous, farmerTeammateContext(),
                        AiBrain.DIFFICULTY_NORMAL, null, null));

        // 困难档：结构差（全是散单、出完这手减一手）时接管
        List<Card> scattered = new ArrayList<>();
        scattered.addAll(of(5, 1));
        scattered.addAll(of(9, 1));
        scattered.addAll(of(13, 1));
        List<Card> takeOver = AiBrain.decidePlay(scattered, previous, farmerTeammateContext(),
                AiBrain.DIFFICULTY_HARD, null, null);
        assertNotNull("困难档散单结构应接管队友小牌", takeOver);
        assertEquals(5, takeOver.get(0).getWeight());
    }

    // ============ 边界场景 ============

    /** 边界：只剩炸弹也必须首出（自由出牌不允许 pass）。 */
    @Test
    public void mustLeadWithOnlyBomb() {
        List<Card> hand = of(9, 4);
        for (int difficulty = AiBrain.DIFFICULTY_EASY;
             difficulty <= AiBrain.DIFFICULTY_HARD; difficulty++) {
            List<Card> move = AiBrain.decidePlay(hand, null, null, difficulty, null, null);
            assertNotNull("只剩炸弹首出不能 pass: diff=" + difficulty, move);
            assertEquals(4, move.size());
        }
    }

    /** 边界：必须跟牌——上家王炸时三档都只能 pass。 */
    @Test
    public void cannotBeatJokerBomb() {
        List<Card> hand = new ArrayList<>();
        hand.addAll(of(15, 2));   // 22
        hand.addAll(of(3, 4));    // 3333
        List<Card> previous = of(16, 1, 17, 1); // 王炸
        for (int difficulty = AiBrain.DIFFICULTY_EASY;
             difficulty <= AiBrain.DIFFICULTY_HARD; difficulty++) {
            assertNull("王炸无法压: diff=" + difficulty,
                    AiBrain.decidePlay(hand, previous, farmerVsLandlord(17),
                            difficulty, null, null));
        }
    }

    /** 边界：一手出完直接赢优先于一切策略。 */
    @Test
    public void finishingMoveAlwaysTaken() {
        List<Card> hand = of(8, 3, 9, 1); // 三带一 888+9 一次出完
        for (int difficulty = AiBrain.DIFFICULTY_EASY;
             difficulty <= AiBrain.DIFFICULTY_HARD; difficulty++) {
            List<Card> move = AiBrain.decidePlay(hand, null, null, difficulty, null, null);
            assertNotNull(move);
            assertEquals("应一手出完", hand.size(), move.size());
        }
    }

    /** 边界：首出只剩王炸。 */
    @Test
    public void mustLeadWithOnlyJokerBomb() {
        List<Card> hand = of(16, 1, 17, 1);
        List<Card> move = AiBrain.decidePlay(hand, null, null, AiBrain.DIFFICULTY_NORMAL,
                null, null);
        assertNotNull(move);
        assertEquals(2, move.size());
    }

    // ============ 困难档记牌器差异 ============

    /**
     * 剩两手（A + 3）且记牌器显示外部已无 2/王、无炸弹可能：
     * 困难档先出"外部压不住"的 A 稳收，普通/简单档出最小的 3。
     */
    @Test
    public void hardUsesCardCounterForSafeFinish() {
        List<Card> hand = new ArrayList<>();
        hand.addAll(of(14, 1));  // A
        hand.addAll(of(3, 1));   // 3
        // 已出牌：2 全出、双王全出、3..K 各出 1 张 → 外部无炸弹可能、无大于 A 的单张
        List<Card> history = new ArrayList<>();
        history.addAll(of(15, 4));
        history.addAll(of(16, 1));
        history.addAll(of(17, 1));
        for (int w = 3; w <= 13; w++) {
            history.addAll(of(w, 1));
        }

        List<Card> easy = AiBrain.decidePlay(hand, null, null, AiBrain.DIFFICULTY_EASY,
                null, history);
        assertNotNull(easy);
        assertEquals("简单档出最小单张", 3, easy.get(0).getWeight());

        List<Card> normal = AiBrain.decidePlay(hand, null, null, AiBrain.DIFFICULTY_NORMAL,
                null, history);
        assertNotNull(normal);
        assertEquals("普通档出主权重最小的手", 3, normal.get(0).getWeight());

        List<Card> hard = AiBrain.decidePlay(hand, null, null, AiBrain.DIFFICULTY_HARD,
                null, history);
        assertNotNull(hard);
        assertEquals("困难档先用外部压不住的 A 收尾", 14, hard.get(0).getWeight());
    }

    /**
     * 跟单 8，手牌 10、J，记牌器显示 J 以上外部全无：
     * 普通/简单出最小的 10；困难档识别 J 是安全牌（外部压不住）出 J 保住出牌权。
     */
    @Test
    public void hardPrefersUnbeatableFollow() {
        List<Card> hand = new ArrayList<>();
        hand.addAll(of(10, 1));
        hand.addAll(of(11, 1));
        List<Card> previous = of(8, 1);
        List<Card> history = new ArrayList<>();
        history.addAll(of(12, 4));
        history.addAll(of(13, 4));
        history.addAll(of(14, 4));
        history.addAll(of(15, 4));
        history.addAll(of(16, 1));
        history.addAll(of(17, 1));
        for (int w = 3; w <= 9; w++) {
            history.addAll(of(w, 1));
        }

        List<Card> normal = AiBrain.decidePlay(hand, previous, null,
                AiBrain.DIFFICULTY_NORMAL, null, history);
        assertNotNull(normal);
        assertEquals("普通档出最小能压的 10", 10, normal.get(0).getWeight());

        List<Card> hard = AiBrain.decidePlay(hand, previous, null,
                AiBrain.DIFFICULTY_HARD, null, history);
        assertNotNull(hard);
        assertEquals("困难档出外部压不住的 J", 11, hard.get(0).getWeight());
    }

    // ============ 叫分 ============

    /** 叫分评估：强牌 3 分、中牌 1-2 分、烂牌 0 分，且简单档阈值更保守。 */
    @Test
    public void bidScoreReflectsHandStrength() {
        List<Card> strong = new ArrayList<>();
        strong.addAll(of(17, 1));
        strong.addAll(of(16, 1));
        strong.addAll(of(15, 2));
        strong.addAll(of(14, 2));
        strong.addAll(of(3, 4));           // 王炸 + 22 + AA + 炸弹
        assertEquals(3, AiBrain.evaluateBidScore(strong));
        assertTrue("强牌普通档必叫", AiBrain.decideBid(strong, AiBrain.DIFFICULTY_NORMAL));
        assertTrue("强牌简单档也叫", AiBrain.decideBid(strong, AiBrain.DIFFICULTY_EASY));

        List<Card> weak = new ArrayList<>();
        weak.addAll(of(3, 2));
        weak.addAll(of(4, 2));
        weak.addAll(of(5, 2));
        weak.addAll(of(6, 2));
        weak.addAll(of(7, 2));
        weak.addAll(of(8, 2));
        weak.addAll(of(9, 2));
        assertEquals(0, AiBrain.evaluateBidScore(weak));
        assertTrue("烂牌任何档都不叫",
                !AiBrain.decideBid(weak, AiBrain.DIFFICULTY_NORMAL)
                        && !AiBrain.decideBid(weak, AiBrain.DIFFICULTY_EASY));

        // 中等牌：有分数但不到 2 分档 → 普通叫、简单保守不叫（差异可观测）
        List<Card> medium = new ArrayList<>();
        medium.addAll(of(17, 1));
        medium.addAll(of(15, 2));
        medium.addAll(of(3, 3));
        medium.addAll(of(4, 3));
        medium.addAll(of(5, 2));
        medium.addAll(of(6, 2));
        int score = AiBrain.evaluateBidScore(medium);
        assertTrue("中等牌评分应在 1-2 档: " + score, score == 1 || score == 2);
        assertEquals("普通档中等牌应叫", true, AiBrain.decideBid(medium, AiBrain.DIFFICULTY_NORMAL));
        if (score < 2) {
            assertEquals("简单档对中等牌保守不叫", false,
                    AiBrain.decideBid(medium, AiBrain.DIFFICULTY_EASY));
        }
    }

    // ============ 上下文工具 ============

    /** 农民（座位1），地主（座位0）剩 enemyCards 张。 */
    private static AiBrain.GameContext farmerVsLandlord(int enemyCards) {
        int[] roles = {AiBrain.ROLE_LANDLORD, AiBrain.ROLE_FARMER, AiBrain.ROLE_FARMER};
        return new AiBrain.GameContext(AiBrain.ROLE_FARMER, 1, roles, 0, enemyCards,
                0, 2, 17, 2, enemyCards);
    }

    /** 农民（座位2），上家（座位1，队友）出牌。 */
    private static AiBrain.GameContext farmerTeammateContext() {
        int[] roles = {AiBrain.ROLE_LANDLORD, AiBrain.ROLE_FARMER, AiBrain.ROLE_FARMER};
        return new AiBrain.GameContext(AiBrain.ROLE_FARMER, 2, roles, 0, 17,
                1, 1, 17, 0, 17);
    }
}
