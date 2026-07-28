package com.gamecenter.app.doudizhu;

import com.gamecenter.app.doudizhu.model.Card;

import java.util.List;

public final class DouDiZhuCardUtil {

    private DouDiZhuCardUtil() {}

    public static void removeCardsFromHand(List<Card> hand, List<Card> cards) {
        for (Card card : cards) {
            hand.remove(card);
        }
    }
}
