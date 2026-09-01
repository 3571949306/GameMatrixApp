package com.gamecenter.app.doudizhu.save;

import static com.gamecenter.app.doudizhu.logic.TestCards.card;
import static com.gamecenter.app.doudizhu.logic.TestCards.of;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.gamecenter.app.doudizhu.model.Card;
import com.gamecenter.app.doudizhu.model.Rank;
import com.gamecenter.app.doudizhu.model.Suit;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * DoudizhuSnapshot 单测：存档序列化/反序列化往返一致、损坏输入拒绝。
 */
public class DoudizhuSnapshotTest {

    private static DoudizhuSnapshot sample() {
        DoudizhuSnapshot s = new DoudizhuSnapshot();
        s.phase = 2;
        s.currentTurn = 1;
        s.landlordSeat = 2;
        s.highestBid = 3;
        s.bidPlacedCount = 3;
        s.redealCount = 1;
        s.bidScore = 3;
        s.bombCount = 2;
        s.difficulty = 1;
        s.startedAtMs = 1_700_000_000_000L;
        s.playCounts = new int[]{3, 5, 1};
        s.passed = new boolean[]{false, true, false};
        s.hands = new List[]{
                of(15, 1, 16, 1),
                of(3, 4, 7, 1),
                of(10, 1, 11, 1, 12, 1)};
        s.bottomCards = of(14, 1, 4, 1, 9, 1);
        s.lastPlayed = of(8, 4);
        s.lastPlayerSeat = 0;
        return s;
    }

    @Test
    public void roundTripPreservesAllFields() {
        DoudizhuSnapshot s = sample();
        String json = s.serialize();
        DoudizhuSnapshot back = DoudizhuSnapshot.deserialize(json);

        assertEquals(s.phase, back.phase);
        assertEquals(s.currentTurn, back.currentTurn);
        assertEquals(s.landlordSeat, back.landlordSeat);
        assertEquals(s.highestBid, back.highestBid);
        assertEquals(s.bidPlacedCount, back.bidPlacedCount);
        assertEquals(s.redealCount, back.redealCount);
        assertEquals(s.bidScore, back.bidScore);
        assertEquals(s.bombCount, back.bombCount);
        assertEquals(s.difficulty, back.difficulty);
        assertEquals(s.startedAtMs, back.startedAtMs);
        assertEquals(3, back.playCounts.length);
        for (int i = 0; i < 3; i++) {
            assertEquals(s.playCounts[i], back.playCounts[i]);
            assertEquals(s.passed[i], back.passed[i]);
        }
        for (int seat = 0; seat < 3; seat++) {
            assertEquals("座位手牌往返一致：" + seat, s.hands[seat], back.hands[seat]);
        }
        assertEquals(s.bottomCards, back.bottomCards);
        assertEquals(s.lastPlayed, back.lastPlayed);
        assertEquals(0, back.lastPlayerSeat);
    }

    @Test
    public void roundTripWithNullLastPlayed() {
        DoudizhuSnapshot s = sample();
        s.lastPlayed = null;
        s.lastPlayerSeat = -1;
        DoudizhuSnapshot back = DoudizhuSnapshot.deserialize(s.serialize());
        assertNull(back.lastPlayed);
        assertEquals(-1, back.lastPlayerSeat);
    }

    @Test
    public void roundTripWithEmptyHands() {
        DoudizhuSnapshot s = sample();
        s.hands = new List[]{new ArrayList<Card>(), new ArrayList<Card>(),
                new ArrayList<Card>()};
        s.bottomCards = new ArrayList<>();
        DoudizhuSnapshot back = DoudizhuSnapshot.deserialize(s.serialize());
        assertEquals(0, back.hands[0].size());
        assertEquals(0, back.hands[1].size());
        assertEquals(0, back.hands[2].size());
        assertEquals(0, back.bottomCards.size());
    }

    @Test
    public void jokersSurviveRoundTrip() {
        DoudizhuSnapshot s = new DoudizhuSnapshot();
        s.phase = 2;
        s.playCounts = new int[3];
        s.passed = new boolean[3];
        s.hands = new List[]{of(16, 1, 17, 1), new ArrayList<Card>(), new ArrayList<Card>()};
        s.bottomCards = new ArrayList<>();
        DoudizhuSnapshot back = DoudizhuSnapshot.deserialize(s.serialize());
        List<Card> hand = back.hands[0];
        assertEquals(2, hand.size());
        assertEquals(Suit.JOKER_small, hand.get(0).getSuit());
        assertEquals(Rank.SMALL_JOKER, hand.get(0).getRank());
        assertEquals(Rank.BIG_JOKER, hand.get(1).getRank());
    }

    @Test
    public void rejectsCorruptedInput() {
        try {
            DoudizhuSnapshot.deserialize(null);
            fail("null 必须抛异常");
        } catch (DoudizhuSnapshot.SerializeException expected) {
            // ok
        }
        try {
            DoudizhuSnapshot.deserialize("");
            fail("空串必须抛异常");
        } catch (DoudizhuSnapshot.SerializeException expected) {
            // ok
        }
        try {
            DoudizhuSnapshot.deserialize("garbage");
            fail("垃圾串必须抛异常");
        } catch (DoudizhuSnapshot.SerializeException expected) {
            // ok
        }
    }

    @Test
    public void rejectsWrongVersionAndFieldCount() {
        DoudizhuSnapshot s = sample();
        String json = s.serialize();
        String bumped = json.replaceFirst("^2\\|", "9|");
        try {
            DoudizhuSnapshot.deserialize(bumped);
            fail("版本不识别必须抛异常");
        } catch (DoudizhuSnapshot.SerializeException expected) {
            // ok
        }
        try {
            DoudizhuSnapshot.deserialize(json + "|extra");
            fail("字段数不符必须抛异常");
        } catch (DoudizhuSnapshot.SerializeException expected) {
            // ok
        }
    }

    @Test
    public void rejectsIllegalCardTokens() {
        DoudizhuSnapshot s = sample();
        String json = s.serialize();
        // 篡改座位 0 手牌字段（索引 13）为非法 token
        String[] parts = json.split("\\|", -1);
        parts[13] = "s_99";
        String tampered = String.join("|", parts);
        try {
            DoudizhuSnapshot.deserialize(tampered);
            fail("非法牌值必须抛异常");
        } catch (DoudizhuSnapshot.SerializeException expected) {
            assertTrue(expected.getMessage().contains("非法"));
        }
    }

    @Test
    public void cardIdentityEqualsBySuitAndRank() {
        // 存档恢复后的牌与原牌 equals（花色+牌值），控制器手牌移除逻辑依赖此语义
        assertEquals(card(3, 0), Card.create(Suit.SPADE, Rank.THREE));
        assertEquals(card(15, 2), Card.create(Suit.CLUB, Rank.TWO));
    }
}
