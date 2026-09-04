package com.gamecenter.app.doudizhu.logic;

import static com.gamecenter.app.doudizhu.logic.TestCards.of;
import static com.gamecenter.app.doudizhu.logic.TestCards.shuffled;
import static com.gamecenter.app.doudizhu.logic.TestCards.straight;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.gamecenter.app.doudizhu.model.Card;

import org.junit.Test;

import java.util.List;

/**
 * 最少手数分解测试：已知最少手数的典型牌型集 + 分解路线合法性 + 乱序一致性。
 */
public class HandDecomposerTest {

    @Test
    public void singleHandStructures() {
        assertEquals(1, HandDecomposer.minHands(of(3, 2)));                       // 对子
        assertEquals(1, HandDecomposer.minHands(of(15, 2)));                      // 22
        assertEquals(1, HandDecomposer.minHands(of(3, 3)));                       // 三张
        assertEquals(1, HandDecomposer.minHands(of(3, 3, 4, 1)));                 // 三带一
        assertEquals(1, HandDecomposer.minHands(of(3, 3, 4, 2)));                 // 三带二
        assertEquals(1, HandDecomposer.minHands(of(3, 4)));                       // 炸弹
        assertEquals(1, HandDecomposer.minHands(straight(3, 5)));                 // 顺子
        assertEquals(1, HandDecomposer.minHands(of(3, 2, 4, 2, 5, 2)));           // 连对
        assertEquals(1, HandDecomposer.minHands(of(3, 3, 4, 3)));                 // 纯飞机
        assertEquals(1, HandDecomposer.minHands(of(3, 4, 4, 4)));                 // 33334444=飞机带单翅膀(333444+3+4)
        assertEquals(1, HandDecomposer.minHands(of(3, 4, 16, 1, 17, 1)));         // 四带两单(3333+双王)
        assertEquals(1, HandDecomposer.minHands(of(16, 1, 17, 1)));               // 王炸
    }

    @Test
    public void multiHandStructures() {
        assertEquals(3, HandDecomposer.minHands(of(3, 1, 5, 1, 9, 1)));           // 三张散单
        // 33344455667：飞机带对翅膀(333444+5566) + 单7
        assertEquals(2, HandDecomposer.minHands(of(3, 3, 4, 3, 5, 2, 6, 2, 7, 1)));
        // 333344445555：飞机 len3 带三组单翅膀(333444555+3,4,5)
        assertEquals(1, HandDecomposer.minHands(of(3, 4, 4, 4, 5, 4)));
        // 3333 + 55 + 9：四带两单(3333+5,5) + 单9
        assertEquals(2, HandDecomposer.minHands(of(3, 4, 5, 2, 9, 1)));
    }

    @Test
    public void emptyHand() {
        assertEquals(0, HandDecomposer.minHands(new java.util.ArrayList<Card>()));
    }

    @Test
    public void orderIndependent() {
        List<Card> hand = of(3, 3, 4, 3, 5, 2, 6, 2, 7, 1);
        assertEquals(HandDecomposer.minHands(hand), HandDecomposer.minHands(shuffled(hand)));
        hand = of(3, 4, 4, 4, 5, 4, 16, 1, 17, 1);
        assertEquals(HandDecomposer.minHands(hand), HandDecomposer.minHands(shuffled(hand)));
    }

    @Test
    public void decompositionIsValidAndComplete() {
        List<List<Card>> hands = java.util.Arrays.asList(
                of(3, 3, 4, 3, 5, 2, 6, 2, 7, 1),
                of(3, 4, 4, 4, 5, 4),
                of(3, 4, 16, 1, 17, 1, 5, 2, 9, 1),
                of(3, 1, 4, 1, 5, 1, 6, 1, 7, 1, 8, 1, 15, 2, 16, 1, 17, 1),
                straight(3, 12)
        );
        for (List<Card> hand : hands) {
            List<Combo> path = HandDecomposer.bestDecomposition(hand);
            assertEquals("路线手数应等于最少手数",
                    HandDecomposer.minHands(hand), path.size());

            int covered = 0;
            for (Combo combo : path) {
                assertNotNull("每手都必须是合法牌型", combo);
                covered += combo.size();
            }
            assertEquals("分解必须恰好覆盖全部手牌", hand.size(), covered);
        }
    }

    @Test
    public void decompositionDeterministic() {
        List<Card> hand = of(3, 3, 4, 3, 5, 2, 6, 2, 7, 1);
        List<Combo> first = HandDecomposer.bestDecomposition(hand);
        List<Combo> second = HandDecomposer.bestDecomposition(shuffled(hand));
        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).getType(), second.get(i).getType());
            assertEquals(first.get(i).getMainWeight(), second.get(i).getMainWeight());
        }
    }

    @Test
    public void performanceOnFullHand() {
        // 17 张典型复杂手牌，分解应在毫秒级（放宽到 2 秒防 CI 抖动）
        List<Card> hand = of(3, 3, 4, 2, 5, 1, 6, 1, 7, 1, 8, 1, 9, 1,
                10, 2, 11, 1, 12, 2, 15, 2);
        long start = System.nanoTime();
        int hands = HandDecomposer.minHands(hand);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue("手数应合理: " + hands, hands > 0);
        assertTrue("分解耗时 " + elapsedMs + "ms 过长", elapsedMs < 2000);
    }
}
