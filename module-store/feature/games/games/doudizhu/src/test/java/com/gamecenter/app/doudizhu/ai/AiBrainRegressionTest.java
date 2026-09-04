package com.gamecenter.app.doudizhu.ai;

import static com.gamecenter.app.doudizhu.logic.TestCards.of;
import static com.gamecenter.app.doudizhu.logic.TestCards.straight;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.gamecenter.app.doudizhu.model.Card;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * P2 复查回归测试：AI 战术修正。
 *
 * <p>覆盖两处退回问题：地主报警时队友也剩 1 张应送小牌让队友先走（而非无脑顶大）；
 * 普通档混合牌型首发的确定性（分解路线主权重最小的非炸手）。</p>
 */
public class AiBrainRegressionTest {

    /**
     * 地主剩 1 张、队友也剩 1 张：农民应送最小单牌让队友先走，而不是顶最大单牌
     * （顶大牌赢了出牌权也轮不到自己走，还可能被地主抢先）；队友剩牌充裕时
     * 维持顶大牌防守。普通/困难两档一致。
     */
    @Test
    public void farmerSendsSmallWhenTeammateAlsoAlerting() {
        List<Card> hand = of(3, 1, 13, 1);   // 最小单 3 + 最大单 K

        int[] alerting = {AiBrain.DIFFICULTY_NORMAL, AiBrain.DIFFICULTY_HARD};
        for (int difficulty : alerting) {
            List<Card> move = AiBrain.decidePlay(hand, null, farmerAlertContext(1),
                    difficulty, null, null);
            assertNotNull("报警局面必须出牌: diff=" + difficulty, move);
            assertEquals(1, move.size());
            assertEquals("队友也剩 1 张时应送最小单牌 3: diff=" + difficulty,
                    3, move.get(0).getWeight());
        }

        for (int difficulty : alerting) {
            List<Card> move = AiBrain.decidePlay(hand, null, farmerAlertContext(5),
                    difficulty, null, null);
            assertNotNull(move);
            assertEquals(1, move.size());
            assertEquals("队友剩牌充裕时应顶最大单牌 K: diff=" + difficulty,
                    13, move.get(0).getWeight());
        }
    }

    /**
     * P2 第二轮复查退回项：地主报警且队友也报警，但下家是地主（队友在对面座位）时，
     * 送小单牌等于直接喂地主获胜（地主任意更大的单牌压过即赢）——必须维持顶最大
     * 单牌防守，普通/困难两档一致。正向场景（队友在下家 → 送小）由
     * {@link #farmerSendsSmallWhenTeammateAlsoAlerting} 锁定。
     */
    @Test
    public void farmerTopsBigWhenLandlordIsNextSeat() {
        List<Card> hand = of(3, 1, 13, 1);   // 最小单 3 + 最大单 K

        int[] alerting = {AiBrain.DIFFICULTY_NORMAL, AiBrain.DIFFICULTY_HARD};
        for (int difficulty : alerting) {
            List<Card> move = AiBrain.decidePlay(hand, null, landlordNextAlertContext(),
                    difficulty, null, null);
            assertNotNull("报警局面必须出牌: diff=" + difficulty, move);
            assertEquals(1, move.size());
            assertEquals("下家是地主时不得送小牌，应顶最大单牌 K: diff=" + difficulty,
                    13, move.get(0).getWeight());
        }
    }

    /**
     * 普通档混合牌型首发确定性：33 + 99 + 789TJQK + 2（双对子 + 7 张顺子 + 散单），
     * 分解路线为 33 / 99 / 顺子 / 单 2，首发必须是主权重最小的对子 33，重复决策一致。
     */
    @Test
    public void normalLeadsDeterministicallyOnMixedHand() {
        List<Card> hand = new ArrayList<>();
        hand.addAll(of(3, 2));          // 33
        hand.addAll(of(9, 2));          // 99
        hand.addAll(straight(7, 7));    // 789TJQK
        hand.addAll(of(15, 1));         // 2

        List<Card> move = AiBrain.decidePlay(hand, null, null,
                AiBrain.DIFFICULTY_NORMAL, null, null);
        assertNotNull(move);
        assertEquals("普通档首发应为对子 33（主权重最小的非炸手）", 2, move.size());
        assertEquals(3, move.get(0).getWeight());

        List<Card> repeat = AiBrain.decidePlay(hand, null, null,
                AiBrain.DIFFICULTY_NORMAL, null, null);
        assertEquals("同局面重复首发必须完全一致", move, repeat);
    }

    /**
     * 农民（座位 1）自由出牌：地主（座位 0）剩 1 张，队友（座位 2，下家）
     * 剩 teammateRemain 张。
     */
    private static AiBrain.GameContext farmerAlertContext(int teammateRemain) {
        int[] roles = {AiBrain.ROLE_LANDLORD, AiBrain.ROLE_FARMER, AiBrain.ROLE_FARMER};
        return new AiBrain.GameContext(AiBrain.ROLE_FARMER, 1, roles, 0, 1,
                -1, 2, teammateRemain, teammateRemain, 1);
    }

    /**
     * 反向报警局面：农民（座位 0）自由出牌，下家（座位 1）是地主且剩 1 张，
     * 队友（座位 2，上家）也剩 1 张。
     */
    private static AiBrain.GameContext landlordNextAlertContext() {
        int[] roles = {AiBrain.ROLE_FARMER, AiBrain.ROLE_LANDLORD, AiBrain.ROLE_FARMER};
        return new AiBrain.GameContext(AiBrain.ROLE_FARMER, 0, roles, 1, 1,
                -1, 2, 1, 1, 1);
    }
}
