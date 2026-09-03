package com.gamecenter.app.doudizhu.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.regex.Pattern;

/**
 * P6 数据层英文化回归测试。
 *
 * <p>守卫不变量：model 包的展示名（Rank/Suit/Card/CardType）必须是英文中性数据，
 * 不得内联中文；UI 侧需要中文时一律走宿主本地化资源（com.gamecenter.app.R.string）。
 * 若有人把中文写回 model 层，本测试失败。
 */
public class ModelEnglishDataTest {

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");

    private static void assertNoCjk(String what, String value) {
        assertFalse(what + " 不得包含中文，实际为: " + value,
                value != null && CJK.matcher(value).find());
    }

    @Test
    public void rankDisplayNamesAreEnglish() {
        assertEquals("Three", Rank.THREE.getDisplayName());
        assertEquals("Four", Rank.FOUR.getDisplayName());
        assertEquals("Ten", Rank.TEN.getDisplayName());
        assertEquals("Jack", Rank.JACK.getDisplayName());
        assertEquals("Queen", Rank.QUEEN.getDisplayName());
        assertEquals("King", Rank.KING.getDisplayName());
        assertEquals("Ace", Rank.ACE.getDisplayName());
        assertEquals("Two", Rank.TWO.getDisplayName());
        assertEquals("Small Joker", Rank.SMALL_JOKER.getDisplayName());
        assertEquals("Big Joker", Rank.BIG_JOKER.getDisplayName());

        for (Rank rank : Rank.values()) {
            assertNoCjk("Rank." + rank.name() + ".getDisplayName", rank.getDisplayName());
        }
    }

    @Test
    public void suitDisplayNamesAreEnglish() {
        assertEquals("Spade", Suit.SPADE.getDisplayName());
        assertEquals("Heart", Suit.HEART.getDisplayName());
        assertEquals("Club", Suit.CLUB.getDisplayName());
        assertEquals("Diamond", Suit.DIAMOND.getDisplayName());
        // 王牌无花色前缀
        assertEquals("", Suit.JOKER_small.getDisplayName());
        assertEquals("", Suit.JOKER_big.getDisplayName());

        for (Suit suit : Suit.values()) {
            assertNoCjk("Suit." + suit.name() + ".getDisplayName", suit.getDisplayName());
        }
    }

    @Test
    public void cardDisplayNamesAreEnglish() {
        assertEquals("Spade Three", Card.create(Suit.SPADE, Rank.THREE).getDisplayName());
        assertEquals("Heart King", Card.create(Suit.HEART, Rank.KING).getDisplayName());
        assertEquals("Small Joker", Card.create(Suit.JOKER_small, Rank.SMALL_JOKER).getDisplayName());
        assertEquals("Big Joker", Card.create(Suit.JOKER_big, Rank.BIG_JOKER).getDisplayName());

        for (Card card : Card.createFullDeck()) {
            assertNoCjk("Card.getDisplayName", card.getDisplayName());
            assertNoCjk("Card.toString", card.toString());
        }
    }

    @Test
    public void cardTypeNamesAreEnglish() {
        assertEquals("Single", CardType.SINGLE.getName());
        assertEquals("Bomb", CardType.BOMB.getName());
        assertEquals("Rocket", CardType.JOKER_BOMB.getName());

        for (CardType type : CardType.values()) {
            assertNoCjk("CardType." + type.name() + ".getName", type.getName());
        }
    }

    @Test
    public void resourceSymbolsStayLocaleIndependent() {
        // 资源拼接用的 symbol/name 与展示名解耦：展示名改英文不得影响资源名
        assertEquals("joker_small", Rank.SMALL_JOKER.getSymbol());
        assertEquals("poker_s_3", Card.create(Suit.SPADE, Rank.THREE).getResName());
        assertEquals("poker_joker_big", Card.create(Suit.JOKER_big, Rank.BIG_JOKER).getResName());
        assertTrue(Suit.HEART.getName(), Suit.HEART.getName().equals("heart"));
    }
}
