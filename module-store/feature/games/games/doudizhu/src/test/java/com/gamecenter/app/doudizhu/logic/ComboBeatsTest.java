package com.gamecenter.app.doudizhu.logic;

import static com.gamecenter.app.doudizhu.logic.TestCards.of;
import static com.gamecenter.app.doudizhu.logic.TestCards.run;
import static com.gamecenter.app.doudizhu.logic.TestCards.straight;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 压牌比较矩阵测试：同型比主权重、炸弹克制关系、长牌型同长度（R7 回归）、翅膀形态一致。
 */
public class ComboBeatsTest {

    private static Combo combo(int... weightCopies) {
        Combo c = Combo.of(of(weightCopies));
        assertNotNull("测试数据本身必须是合法牌型", c);
        return c;
    }

    @Test
    public void freeLeadAlwaysAllowed() {
        assertTrue(combo(9, 1).beats(null));
    }

    @Test
    public void singleBeatsLowerSingle() {
        assertTrue(combo(9, 1).beats(combo(5, 1)));
        assertFalse(combo(5, 1).beats(combo(9, 1)));
        assertFalse(combo(9, 1).beats(combo(9, 1)));
    }

    @Test
    public void differentTypesCannotBeat() {
        assertFalse(combo(9, 2).beats(combo(5, 1)));   // 对子压不了单张
        assertFalse(combo(9, 3).beats(combo(5, 2)));   // 三张压不了对子
        assertFalse(combo(9, 1).beats(combo(5, 3, 7, 1))); // 单张压不了三带一
    }

    @Test
    public void trioSingleBeatsByTrioWeightOnly() {
        // 444+3 压 333+大王：只看三张部分
        assertTrue(combo(8, 3, 3, 1).beats(combo(7, 3, 17, 1)));
        assertFalse(combo(7, 3, 17, 1).beats(combo(8, 3, 3, 1)));
    }

    @Test
    public void bombBeatsNonBomb() {
        assertTrue(combo(3, 4).beats(combo(17, 1)));          // 最小炸弹压大王
        assertTrue(combo(3, 4).beats(Combo.of(straight(3, 5)))); // 炸弹压顺子
        assertFalse(combo(17, 1).beats(combo(3, 4)));
    }

    @Test
    public void bombBeatsBombByWeight() {
        assertTrue(combo(9, 4).beats(combo(5, 4)));
        assertFalse(combo(5, 4).beats(combo(9, 4)));
    }

    @Test
    public void rocketBeatsEverything() {
        Combo rocket = combo(16, 1, 17, 1);
        assertTrue(rocket.beats(combo(9, 4)));
        assertTrue(rocket.beats(combo(17, 1)));
        assertFalse(combo(9, 4).beats(rocket));
    }

    /** R7：顺子必须同长度才能比较（旧内核漏掉长度校验）。 */
    @Test
    public void straightRequiresSameLength() {
        Combo five = Combo.of(straight(3, 5));   // 34567 top=7
        Combo six = Combo.of(straight(3, 6));    // 345678 top=8
        assertNotNull(five);
        assertNotNull(six);
        assertFalse("5 张顺子压不了 6 张顺子", five.beats(six));
        assertFalse("6 张顺子也压不了 5 张顺子", six.beats(five));
        Combo sixHigher = Combo.of(straight(4, 6)); // 456789 top=9
        assertTrue(sixHigher.beats(six));
        assertFalse(Combo.of(straight(3, 6)).beats(sixHigher));
    }

    @Test
    public void straightPairsRequiresSameLength() {
        Combo three = Combo.of(run(3, 3, 2));  // 334455
        Combo four = Combo.of(run(3, 4, 2));   // 33445566
        assertFalse(three.beats(four));
        assertFalse(four.beats(three));
    }

    @Test
    public void airplaneRequiresSameLength() {
        Combo two = Combo.of(run(3, 2, 3));    // 333444
        Combo three = Combo.of(run(3, 3, 3));  // 333444555
        assertFalse(two.beats(three));
        assertFalse(three.beats(two));
    }

    /** 飞机带翅膀：带单与带对不可互压。 */
    @Test
    public void airplaneWingsKindMustMatch() {
        Combo singles = Combo.of(java.util.stream.Stream.concat(
                run(3, 2, 3).stream(), of(9, 1, 11, 1).stream())
                .collect(java.util.stream.Collectors.toList()));
        Combo pairs = Combo.of(java.util.stream.Stream.concat(
                run(3, 2, 3).stream(), of(9, 2, 11, 2).stream())
                .collect(java.util.stream.Collectors.toList()));
        assertNotNull(singles);
        assertNotNull(pairs);
        assertEquals(Combo.WINGS_SINGLES, singles.getWingsKind());
        assertEquals(Combo.WINGS_PAIRS, pairs.getWingsKind());
        assertFalse(singles.beats(pairs));
        assertFalse(pairs.beats(singles));
    }

    private static void assertEquals(int expected, int actual) {
        org.junit.Assert.assertEquals(expected, actual);
    }

    /** 四带二不是炸弹：压不了其他牌型，也压不了炸弹。 */
    @Test
    public void quadIsNotBomb() {
        Combo quadSingle = combo(9, 4, 3, 1, 5, 1);
        Combo bomb = combo(3, 4);
        assertFalse(quadSingle.beats(bomb));
        assertTrue(bomb.beats(quadSingle));
        assertFalse(quadSingle.beats(combo(17, 1))); // 也压不了单张
    }

    @Test
    public void mainWeightOrderIndependentInComparison() {
        // 乱序传入的顺子比较结果与升序一致（R2 回归）
        Combo a = Combo.of(straight(5, 5));
        Combo b = Combo.of(TestCards.shuffled(straight(3, 5)));
        assertTrue(a.beats(b));
        assertFalse(b.beats(a));
    }
}
