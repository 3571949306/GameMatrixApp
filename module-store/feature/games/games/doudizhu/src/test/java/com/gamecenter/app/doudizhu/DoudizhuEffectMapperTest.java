package com.gamecenter.app.doudizhu;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.gamecenter.app.doudizhu.logic.TestCards;
import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.model.CardType;
import com.gamecenter.app.doudizhu.utils.GameRuleUtil;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

/**
 * P5 反馈映射契约测试：锁定"牌型 → 特效/音效/震动档"映射，防止后续改动
 * 无意破坏特效接线（炸弹/王炸/飞机/春天/连对为计划验收范围）。
 */
public class DoudizhuEffectMapperTest {

    // ============ 牌型 → 特效 ============

    @Test
    public void bombMapsToBombEffect() {
        assertEquals(DoudizhuEffectMapper.Effect.BOMB,
                DoudizhuEffectMapper.effectFor(CardType.BOMB));
    }

    @Test
    public void jokerBombMapsToRocketEffect() {
        assertEquals(DoudizhuEffectMapper.Effect.ROCKET,
                DoudizhuEffectMapper.effectFor(CardType.JOKER_BOMB));
    }

    @Test
    public void airplaneWithAndWithoutWingsMapToPlaneEffect() {
        assertEquals(DoudizhuEffectMapper.Effect.PLANE,
                DoudizhuEffectMapper.effectFor(CardType.AIRPLANE));
        assertEquals(DoudizhuEffectMapper.Effect.PLANE,
                DoudizhuEffectMapper.effectFor(CardType.AIRPLANE_WITH_WINGS));
    }

    @Test
    public void straightPairsMapToDoubleLineEffect() {
        assertEquals(DoudizhuEffectMapper.Effect.DOUBLE_LINE,
                DoudizhuEffectMapper.effectFor(CardType.STRAIGHT_PAIRS));
    }

    @Test
    public void plainTypesHaveNoEffect() {
        assertNull(DoudizhuEffectMapper.effectFor(CardType.SINGLE));
        assertNull(DoudizhuEffectMapper.effectFor(CardType.PAIR));
        assertNull(DoudizhuEffectMapper.effectFor(CardType.TRIO_SINGLE));
        assertNull(DoudizhuEffectMapper.effectFor(CardType.STRAIGHT));
        assertNull(DoudizhuEffectMapper.effectFor(CardType.QUAD_SINGLE));
        assertNull(DoudizhuEffectMapper.effectFor(CardType.ERROR));
        assertNull(DoudizhuEffectMapper.effectFor(null));
    }

    // ============ 牌型 → 音效 ============

    @Test
    public void specialTypesMapToTheirOwnSfx() {
        assertEquals(DoudizhuEffectMapper.Sfx.BOMB,
                DoudizhuEffectMapper.sfxFor(CardType.BOMB));
        assertEquals(DoudizhuEffectMapper.Sfx.ROCKET,
                DoudizhuEffectMapper.sfxFor(CardType.JOKER_BOMB));
        assertEquals(DoudizhuEffectMapper.Sfx.PLANE,
                DoudizhuEffectMapper.sfxFor(CardType.AIRPLANE));
        assertEquals(DoudizhuEffectMapper.Sfx.PLANE,
                DoudizhuEffectMapper.sfxFor(CardType.AIRPLANE_WITH_WINGS));
        assertEquals(DoudizhuEffectMapper.Sfx.DOUBLE_LINE,
                DoudizhuEffectMapper.sfxFor(CardType.STRAIGHT_PAIRS));
    }

    @Test
    public void plainTypesAndInvalidInputFallBackToPlaySfx() {
        assertEquals(DoudizhuEffectMapper.Sfx.PLAY,
                DoudizhuEffectMapper.sfxFor(CardType.SINGLE));
        assertEquals(DoudizhuEffectMapper.Sfx.PLAY,
                DoudizhuEffectMapper.sfxFor(CardType.STRAIGHT));
        assertEquals(DoudizhuEffectMapper.Sfx.PLAY,
                DoudizhuEffectMapper.sfxFor(CardType.ERROR));
        assertEquals(DoudizhuEffectMapper.Sfx.PLAY,
                DoudizhuEffectMapper.sfxFor(null));
    }

    // ============ 特效 → 震动档 ============

    @Test
    public void bombAndRocketShakeStrong() {
        assertEquals(DoudizhuEffectMapper.Shake.STRONG,
                DoudizhuEffectMapper.shakeFor(DoudizhuEffectMapper.Effect.BOMB));
        assertEquals(DoudizhuEffectMapper.Shake.STRONG,
                DoudizhuEffectMapper.shakeFor(DoudizhuEffectMapper.Effect.ROCKET));
    }

    @Test
    public void springShakesMedium() {
        assertEquals(DoudizhuEffectMapper.Shake.MEDIUM,
                DoudizhuEffectMapper.shakeFor(DoudizhuEffectMapper.Effect.SPRING));
    }

    @Test
    public void planeAndDoubleLineShakeLight() {
        assertEquals(DoudizhuEffectMapper.Shake.LIGHT,
                DoudizhuEffectMapper.shakeFor(DoudizhuEffectMapper.Effect.PLANE));
        assertEquals(DoudizhuEffectMapper.Shake.LIGHT,
                DoudizhuEffectMapper.shakeFor(DoudizhuEffectMapper.Effect.DOUBLE_LINE));
    }

    @Test
    public void nullEffectShakesNone() {
        assertEquals(DoudizhuEffectMapper.Shake.NONE,
                DoudizhuEffectMapper.shakeFor(null));
    }

    // ============ 端到端：真实牌组识别后映射（防 GameRuleUtil 与映射脱节） ============

    @Test
    public void realCardCombosMapThroughGameRuleUtil() {
        List<Card> bomb = TestCards.of(5, 4);
        List<Card> rocket = TestCards.of(16, 1, 17, 1);
        List<Card> plane = TestCards.of(6, 3, 7, 3, 8, 3);
        List<Card> doubleLine = TestCards.run(4, 3, 2);

        assertEquals(CardType.BOMB, GameRuleUtil.getCardType(bomb));
        assertEquals(DoudizhuEffectMapper.Effect.BOMB,
                DoudizhuEffectMapper.effectFor(GameRuleUtil.getCardType(bomb)));

        assertEquals(CardType.JOKER_BOMB, GameRuleUtil.getCardType(rocket));
        assertEquals(DoudizhuEffectMapper.Effect.ROCKET,
                DoudizhuEffectMapper.effectFor(GameRuleUtil.getCardType(rocket)));

        assertEquals(CardType.AIRPLANE, GameRuleUtil.getCardType(plane));
        assertEquals(DoudizhuEffectMapper.Effect.PLANE,
                DoudizhuEffectMapper.effectFor(GameRuleUtil.getCardType(plane)));

        assertEquals(CardType.STRAIGHT_PAIRS, GameRuleUtil.getCardType(doubleLine));
        assertEquals(DoudizhuEffectMapper.Effect.DOUBLE_LINE,
                DoudizhuEffectMapper.effectFor(GameRuleUtil.getCardType(doubleLine)));
    }

    /** 空牌列表应识别为 ERROR 且不触发任何特效（防御：映射不抛异常）。 */
    @Test
    public void emptyCardsAreErrorAndProduceNoEffect() {
        assertEquals(CardType.ERROR,
                GameRuleUtil.getCardType(Collections.<Card>emptyList()));
        assertNull(DoudizhuEffectMapper.effectFor(
                GameRuleUtil.getCardType(Collections.<Card>emptyList())));
        assertEquals(DoudizhuEffectMapper.Sfx.PLAY,
                DoudizhuEffectMapper.sfxFor(
                        GameRuleUtil.getCardType(Collections.<Card>emptyList())));
    }
}
