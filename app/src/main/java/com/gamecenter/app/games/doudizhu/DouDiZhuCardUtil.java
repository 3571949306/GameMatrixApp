package com.gamecenter.app.games.doudizhu;

import com.gamecenter.app.games.doudizhu.model.Card;

import java.util.List;

public final class DouDiZhuCardUtil {

    private DouDiZhuCardUtil() {}

    public static void removeCardsFromHand(List<Card> hand, List<Card> cards) {
        for (Card card : cards) {
            hand.remove(card);
        }
    }
}
