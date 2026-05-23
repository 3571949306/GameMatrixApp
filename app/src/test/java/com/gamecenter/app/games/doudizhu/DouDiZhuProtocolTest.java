package com.gamecenter.app.games.doudizhu;

import com.gamecenter.app.games.doudizhu.model.Card;
import com.gamecenter.app.games.doudizhu.model.Rank;
import com.gamecenter.app.games.doudizhu.model.Suit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class DouDiZhuProtocolTest {

    private List<Card> cardsOf(Rank... ranks) {
        List<Card> list = new ArrayList<>();
        for (Rank rank : ranks) {
            list.add(Card.create(Suit.SPADE, rank));
        }
        return list;
    }

    @Test
    public void cardsToJson_nullCards_returnsEmptyArray() throws JSONException {
        String json = DouDiZhuProtocol.cardsToJson(null);
        JSONArray array = new JSONArray(json);
        assertEquals(0, array.length());
    }

    @Test
    public void cardsToJson_emptyCards_returnsEmptyArray() throws JSONException {
        String json = DouDiZhuProtocol.cardsToJson(Collections.<Card>emptyList());
        JSONArray array = new JSONArray(json);
        assertEquals(0, array.length());
    }

    @Test
    public void cardsToJson_singleCard_returnsCorrectJson() throws JSONException {
        List<Card> cards = cardsOf(Rank.THREE);
        String json = DouDiZhuProtocol.cardsToJson(cards);
        JSONArray array = new JSONArray(json);
        assertEquals(1, array.length());
        JSONObject obj = array.getJSONObject(0);
        assertEquals("SPADE", obj.getString("suit"));
        assertEquals("THREE", obj.getString("rank"));
    }

    @Test
    public void cardsToJson_multipleCards_returnsCorrectJson() throws JSONException {
        List<Card> cards = new ArrayList<>();
        cards.add(Card.create(Suit.HEART, Rank.ACE));
        cards.add(Card.create(Suit.JOKER_small, Rank.SMALL_JOKER));
        String json = DouDiZhuProtocol.cardsToJson(cards);
        JSONArray array = new JSONArray(json);
        assertEquals(2, array.length());
        assertEquals("HEART", array.getJSONObject(0).getString("suit"));
        assertEquals("ACE", array.getJSONObject(0).getString("rank"));
        assertEquals("JOKER_small", array.getJSONObject(1).getString("suit"));
        assertEquals("SMALL_JOKER", array.getJSONObject(1).getString("rank"));
    }

    @Test
    public void cardsToJson_jokerBig_returnsCorrectJson() throws JSONException {
        List<Card> cards = new ArrayList<>();
        cards.add(Card.create(Suit.JOKER_big, Rank.BIG_JOKER));
        String json = DouDiZhuProtocol.cardsToJson(cards);
        JSONArray array = new JSONArray(json);
        assertEquals(1, array.length());
        assertEquals("JOKER_big", array.getJSONObject(0).getString("suit"));
        assertEquals("BIG_JOKER", array.getJSONObject(0).getString("rank"));
    }

    @Test
    public void parseCardsFromJson_nullJson_returnsEmptyList() {
        assertTrue(DouDiZhuProtocol.parseCardsFromJson(null).isEmpty());
    }

    @Test
    public void parseCardsFromJson_emptyJson_returnsEmptyList() {
        assertTrue(DouDiZhuProtocol.parseCardsFromJson("").isEmpty());
    }

    @Test
    public void parseCardsFromJson_emptyArray_returnsEmptyList() {
        assertTrue(DouDiZhuProtocol.parseCardsFromJson("[]").isEmpty());
    }

    @Test
    public void parseCardsFromJson_singleCard_returnsCorrectCard() {
        String json = "[{\"suit\":\"SPADE\",\"rank\":\"THREE\"}]";
        List<Card> cards = DouDiZhuProtocol.parseCardsFromJson(json);
        assertEquals(1, cards.size());
        assertEquals(Suit.SPADE, cards.get(0).getSuit());
        assertEquals(Rank.THREE, cards.get(0).getRank());
    }

    @Test
    public void parseCardsFromJson_multipleCards_returnsCorrectCards() {
        String json = "[{\"suit\":\"HEART\",\"rank\":\"ACE\"},{\"suit\":\"JOKER_big\",\"rank\":\"BIG_JOKER\"}]";
        List<Card> cards = DouDiZhuProtocol.parseCardsFromJson(json);
        assertEquals(2, cards.size());
        assertEquals(Suit.HEART, cards.get(0).getSuit());
        assertEquals(Rank.ACE, cards.get(0).getRank());
        assertEquals(Suit.JOKER_big, cards.get(1).getSuit());
        assertEquals(Rank.BIG_JOKER, cards.get(1).getRank());
    }

    @Test
    public void cardsToJsonAndParse_roundtrip_normalCards() {
        List<Card> original = new ArrayList<>();
        original.add(Card.create(Suit.SPADE, Rank.THREE));
        original.add(Card.create(Suit.HEART, Rank.KING));
        original.add(Card.create(Suit.CLUB, Rank.ACE));
        String json = DouDiZhuProtocol.cardsToJson(original);
        List<Card> parsed = DouDiZhuProtocol.parseCardsFromJson(json);
        assertEquals(original.size(), parsed.size());
        for (int i = 0; i < original.size(); i++) {
            assertEquals(original.get(i).getSuit(), parsed.get(i).getSuit());
            assertEquals(original.get(i).getRank(), parsed.get(i).getRank());
        }
    }

    @Test
    public void cardsToJsonAndParse_roundtrip_jokers() {
        List<Card> original = new ArrayList<>();
        original.add(Card.create(Suit.JOKER_small, Rank.SMALL_JOKER));
        original.add(Card.create(Suit.JOKER_big, Rank.BIG_JOKER));
        String json = DouDiZhuProtocol.cardsToJson(original);
        List<Card> parsed = DouDiZhuProtocol.parseCardsFromJson(json);
        assertEquals(2, parsed.size());
        assertEquals(Suit.JOKER_small, parsed.get(0).getSuit());
        assertEquals(Rank.SMALL_JOKER, parsed.get(0).getRank());
        assertEquals(Suit.JOKER_big, parsed.get(1).getSuit());
        assertEquals(Rank.BIG_JOKER, parsed.get(1).getRank());
    }

    @Test
    public void seatTypesToJson_returnsCorrectArray() throws JSONException {
        int[] seatTypes = {0, 1, 2};
        JSONArray array = DouDiZhuProtocol.seatTypesToJson(seatTypes);
        assertEquals(3, array.length());
        assertEquals(0, array.getInt(0));
        assertEquals(1, array.getInt(1));
        assertEquals(2, array.getInt(2));
    }

    @Test
    public void booleanArrayToJson_returnsCorrectArray() throws JSONException {
        boolean[] values = {true, false, true};
        JSONArray array = DouDiZhuProtocol.booleanArrayToJson(values);
        assertEquals(3, array.length());
        assertTrue(array.getBoolean(0));
        assertFalse(array.getBoolean(1));
        assertTrue(array.getBoolean(2));
    }

    @Test
    public void booleanArrayToJson_nullInput_returnsEmptyArray() throws JSONException {
        JSONArray array = DouDiZhuProtocol.booleanArrayToJson(null);
        assertEquals(0, array.length());
    }

    @Test
    public void booleanArrayToJson_allFalse_returnsCorrectArray() throws JSONException {
        boolean[] values = {false, false, false};
        JSONArray array = DouDiZhuProtocol.booleanArrayToJson(values);
        assertEquals(3, array.length());
        assertFalse(array.getBoolean(0));
        assertFalse(array.getBoolean(1));
        assertFalse(array.getBoolean(2));
    }

    @Test
    public void handCountsToJson_returnsCorrectArray() throws JSONException {
        List<Card> hand0 = cardsOf(Rank.THREE, Rank.FOUR);
        List<Card> hand1 = cardsOf(Rank.FIVE);
        List<Card> hand2 = cardsOf(Rank.SIX, Rank.SEVEN, Rank.EIGHT);
        JSONArray array = DouDiZhuProtocol.handCountsToJson(hand0, hand1, hand2);
        assertEquals(3, array.length());
        assertEquals(2, array.getInt(0));
        assertEquals(1, array.getInt(1));
        assertEquals(3, array.getInt(2));
    }

    @Test
    public void handCountsToJson_emptyHands_returnsZeros() throws JSONException {
        JSONArray array = DouDiZhuProtocol.handCountsToJson(
                Collections.<Card>emptyList(),
                Collections.<Card>emptyList(),
                Collections.<Card>emptyList());
        assertEquals(3, array.length());
        assertEquals(0, array.getInt(0));
        assertEquals(0, array.getInt(1));
        assertEquals(0, array.getInt(2));
    }

    @Test
    public void intArrayToJson_returnsCorrectArray() throws JSONException {
        int[] values = {10, 20, 30};
        JSONArray array = DouDiZhuProtocol.intArrayToJson(values);
        assertEquals(3, array.length());
        assertEquals(10, array.getInt(0));
        assertEquals(20, array.getInt(1));
        assertEquals(30, array.getInt(2));
    }

    @Test
    public void intArrayToJson_nullInput_returnsEmptyArray() throws JSONException {
        JSONArray array = DouDiZhuProtocol.intArrayToJson(null);
        assertEquals(0, array.length());
    }

    @Test
    public void createFullDeckCounter_returnsCorrectLength() {
        int[] counts = DouDiZhuProtocol.createFullDeckCounter();
        assertEquals(15, counts.length);
    }

    @Test
    public void createFullDeckCounter_normalRanksHaveFour() {
        int[] counts = DouDiZhuProtocol.createFullDeckCounter();
        for (int i = 0; i < 13; i++) {
            assertEquals(4, counts[i]);
        }
    }

    @Test
    public void createFullDeckCounter_jokersHaveOne() {
        int[] counts = DouDiZhuProtocol.createFullDeckCounter();
        assertEquals(1, counts[13]);
        assertEquals(1, counts[14]);
    }

    @Test
    public void rankCounterIndex_three_returns0() {
        Card card = Card.create(Suit.SPADE, Rank.THREE);
        assertEquals(0, DouDiZhuProtocol.rankCounterIndex(card));
    }

    @Test
    public void rankCounterIndex_four_returns1() {
        Card card = Card.create(Suit.SPADE, Rank.FOUR);
        assertEquals(1, DouDiZhuProtocol.rankCounterIndex(card));
    }

    @Test
    public void rankCounterIndex_king_returns10() {
        Card card = Card.create(Suit.SPADE, Rank.KING);
        assertEquals(10, DouDiZhuProtocol.rankCounterIndex(card));
    }

    @Test
    public void rankCounterIndex_ace_returns11() {
        Card card = Card.create(Suit.SPADE, Rank.ACE);
        assertEquals(11, DouDiZhuProtocol.rankCounterIndex(card));
    }

    @Test
    public void rankCounterIndex_two_returns12() {
        Card card = Card.create(Suit.SPADE, Rank.TWO);
        assertEquals(12, DouDiZhuProtocol.rankCounterIndex(card));
    }

    @Test
    public void rankCounterIndex_smallJoker_returns13() {
        Card card = Card.create(Suit.JOKER_small, Rank.SMALL_JOKER);
        assertEquals(13, DouDiZhuProtocol.rankCounterIndex(card));
    }

    @Test
    public void rankCounterIndex_bigJoker_returns14() {
        Card card = Card.create(Suit.JOKER_big, Rank.BIG_JOKER);
        assertEquals(14, DouDiZhuProtocol.rankCounterIndex(card));
    }

    @Test
    public void rankCounterIndex_nullCard_returnsNegative1() {
        assertEquals(-1, DouDiZhuProtocol.rankCounterIndex(null));
    }

    @Test
    public void rankCounterIndex_differentSuitsSameRank_returnsSameIndex() {
        Card spade = Card.create(Suit.SPADE, Rank.FIVE);
        Card heart = Card.create(Suit.HEART, Rank.FIVE);
        assertEquals(DouDiZhuProtocol.rankCounterIndex(spade), DouDiZhuProtocol.rankCounterIndex(heart));
    }

    @Test
    public void subtractCardsFromCounter_subtractsCorrectly() {
        int[] counts = DouDiZhuProtocol.createFullDeckCounter();
        List<Card> cards = cardsOf(Rank.THREE, Rank.THREE);
        DouDiZhuProtocol.subtractCardsFromCounter(counts, cards);
        assertEquals(2, counts[0]);
    }

    @Test
    public void subtractCardsFromCounter_subtractsMultipleRanks() {
        int[] counts = DouDiZhuProtocol.createFullDeckCounter();
        List<Card> cards = new ArrayList<>();
        cards.add(Card.create(Suit.SPADE, Rank.THREE));
        cards.add(Card.create(Suit.HEART, Rank.ACE));
        cards.add(Card.create(Suit.JOKER_small, Rank.SMALL_JOKER));
        DouDiZhuProtocol.subtractCardsFromCounter(counts, cards);
        assertEquals(3, counts[0]);
        assertEquals(3, counts[11]);
        assertEquals(0, counts[13]);
    }

    @Test
    public void subtractCardsFromCounter_doesNotGoBelowZero() {
        int[] counts = new int[]{1, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 1, 1};
        List<Card> cards = cardsOf(Rank.THREE, Rank.THREE);
        DouDiZhuProtocol.subtractCardsFromCounter(counts, cards);
        assertEquals(0, counts[0]);
    }

    @Test
    public void subtractCardsFromCounter_nullCounts_doesNothing() {
        List<Card> cards = cardsOf(Rank.THREE);
        DouDiZhuProtocol.subtractCardsFromCounter(null, cards);
    }

    @Test
    public void subtractCardsFromCounter_nullCards_doesNothing() {
        int[] counts = DouDiZhuProtocol.createFullDeckCounter();
        DouDiZhuProtocol.subtractCardsFromCounter(counts, null);
        assertEquals(4, counts[0]);
    }

    @Test
    public void jsonToCounterArray_nullArray_returnsFullDeck() {
        int[] counts = DouDiZhuProtocol.jsonToCounterArray(null);
        assertEquals(15, counts.length);
        assertEquals(4, counts[0]);
        assertEquals(1, counts[13]);
        assertEquals(1, counts[14]);
    }

    @Test
    public void jsonToCounterArray_partialArray_fillsDefaults() throws JSONException {
        JSONArray array = new JSONArray();
        array.put(2);
        array.put(3);
        int[] counts = DouDiZhuProtocol.jsonToCounterArray(array);
        assertEquals(2, counts[0]);
        assertEquals(3, counts[1]);
        assertEquals(4, counts[2]);
        assertEquals(1, counts[13]);
    }

    @Test
    public void jsonToCounterArray_fullArray_overridesAll() throws JSONException {
        JSONArray array = new JSONArray();
        for (int i = 0; i < 15; i++) {
            array.put(i < 13 ? 0 : 0);
        }
        int[] counts = DouDiZhuProtocol.jsonToCounterArray(array);
        for (int i = 0; i < 15; i++) {
            assertEquals(0, counts[i]);
        }
    }

    @Test
    public void createSeatAssignedMsg_containsCorrectFields() throws JSONException {
        int[] seatTypes = {0, 2, 2};
        JSONObject msg = DouDiZhuProtocol.createSeatAssignedMsg(1, seatTypes, "Player1", true, false);
        assertEquals(DouDiZhuProtocol.TYPE_SEAT_ASSIGNED, msg.getString("type"));
        assertEquals(1, msg.getInt("seatIndex"));
        assertEquals("Player1", msg.getString("seatName"));
        assertTrue(msg.getBoolean("remoteP2P"));
        assertFalse(msg.getBoolean("reconnected"));
        JSONArray types = msg.getJSONArray("seatTypes");
        assertEquals(3, types.length());
    }

    @Test
    public void createSeatUpdateMsg_containsCorrectFields() throws JSONException {
        int[] seatTypes = {0, 1, 2};
        JSONObject msg = DouDiZhuProtocol.createSeatUpdateMsg(seatTypes, 0);
        assertEquals(DouDiZhuProtocol.TYPE_SEAT_UPDATE, msg.getString("type"));
        assertEquals(0, msg.getInt("landlordIndex"));
        assertTrue(msg.has("seatTypes"));
    }

    @Test
    public void createSeatUpdateMsg_landlordNotSet_returnsNegative1() throws JSONException {
        int[] seatTypes = {0, 2, 2};
        JSONObject msg = DouDiZhuProtocol.createSeatUpdateMsg(seatTypes, -1);
        assertEquals(-1, msg.getInt("landlordIndex"));
    }

    @Test
    public void createHandCardsMsg_containsCorrectFields() throws JSONException {
        String cardsJson = "[{\"suit\":\"SPADE\",\"rank\":\"THREE\"}]";
        String bottomJson = "[]";
        JSONObject msg = DouDiZhuProtocol.createHandCardsMsg(cardsJson, bottomJson, 0);
        assertEquals(DouDiZhuProtocol.TYPE_HAND_CARDS, msg.getString("type"));
        assertEquals(cardsJson, msg.getString("cards"));
        assertEquals(bottomJson, msg.getString("bottomCards"));
        assertEquals(0, msg.getInt("seatIndex"));
    }

    @Test
    public void createBidRequestMsg_containsCorrectFields() throws JSONException {
        JSONObject msg = DouDiZhuProtocol.createBidRequestMsg(1L, 0, 0);
        assertEquals(DouDiZhuProtocol.TYPE_BID_REQUEST, msg.getString("type"));
        assertEquals(1L, msg.getLong("stateVersion"));
        assertEquals(0, msg.getInt("seatIndex"));
        assertEquals(0, msg.getInt("currentTurn"));
    }

    @Test
    public void createGameStartMsg_containsCorrectFields() throws JSONException {
        int[] seatTypes = {0, 2, 2};
        String bottomJson = "[]";
        JSONObject msg = DouDiZhuProtocol.createGameStartMsg(0, 0, 1, seatTypes, bottomJson);
        assertEquals(DouDiZhuProtocol.TYPE_GAME_START, msg.getString("type"));
        assertEquals(0, msg.getInt("seatIndex"));
        assertEquals(0, msg.getInt("currentTurn"));
        assertEquals(1, msg.getInt("landlordIndex"));
        assertEquals(bottomJson, msg.getString("bottomCards"));
    }

    @Test
    public void createSyncStateMsg_containsCorrectFields() throws JSONException {
        int[] seatTypes = {0, 2, 2};
        boolean[] playerPassed = {false, false, false};
        JSONArray handCounts = new JSONArray();
        handCounts.put(17).put(17).put(17);
        int[] cardCounter = DouDiZhuProtocol.createFullDeckCounter();
        JSONObject msg = DouDiZhuProtocol.createSyncStateMsg(
                0, 1L, 2, 0, 1, -1, -1,
                seatTypes, playerPassed, handCounts, cardCounter,
                "[]", "[]", "[]", "[]", "[]");
        assertEquals(DouDiZhuProtocol.TYPE_SYNC_STATE, msg.getString("type"));
        assertEquals(0, msg.getInt("seatIndex"));
        assertEquals(1L, msg.getLong("stateVersion"));
        assertEquals(2, msg.getInt("gameState"));
        assertEquals(0, msg.getInt("currentTurn"));
        assertEquals(1, msg.getInt("landlordIndex"));
        assertEquals(-1, msg.getInt("winnerIndex"));
        assertEquals(-1, msg.getInt("lastPlayerWhoPlayed"));
        assertTrue(msg.has("seatTypes"));
        assertTrue(msg.has("playerPassed"));
        assertTrue(msg.has("handCounts"));
        assertTrue(msg.has("cardCounter"));
        assertEquals("[]", msg.getString("myCards"));
        assertEquals("[]", msg.getString("bottomCards"));
        assertEquals("[]", msg.getString("played0"));
        assertEquals("[]", msg.getString("played1"));
        assertEquals("[]", msg.getString("played2"));
    }

    @Test
    public void createAckMsg_accepted_withoutReason() throws JSONException {
        JSONObject msg = DouDiZhuProtocol.createAckMsg("BID", 1L, 2L, true, null);
        assertEquals(DouDiZhuProtocol.TYPE_ACK, msg.getString("type"));
        assertEquals("BID", msg.getString("ackType"));
        assertEquals(1L, msg.getLong("actionId"));
        assertEquals(2L, msg.getLong("stateVersion"));
        assertTrue(msg.getBoolean("accepted"));
        assertFalse(msg.has("reason"));
    }

    @Test
    public void createAckMsg_rejected_withReason() throws JSONException {
        JSONObject msg = DouDiZhuProtocol.createAckMsg("PLAY", 1L, 2L, false, "Invalid play");
        assertEquals(DouDiZhuProtocol.TYPE_ACK, msg.getString("type"));
        assertFalse(msg.getBoolean("accepted"));
        assertEquals("Invalid play", msg.getString("reason"));
    }

    @Test
    public void createAckMsg_rejected_emptyReason_noReasonField() throws JSONException {
        JSONObject msg = DouDiZhuProtocol.createAckMsg("PLAY", 1L, 2L, false, "");
        assertFalse(msg.getBoolean("accepted"));
        assertFalse(msg.has("reason"));
    }

    @Test
    public void createAckMsg_hasTimeField() throws JSONException {
        JSONObject msg = DouDiZhuProtocol.createAckMsg("BID", 1L, 2L, true, null);
        assertTrue(msg.has("time"));
        assertTrue(msg.getLong("time") > 0);
    }

    @Test
    public void createErrorMsg_containsCorrectFields() throws JSONException {
        JSONObject msg = DouDiZhuProtocol.createErrorMsg("Something went wrong");
        assertEquals(DouDiZhuProtocol.TYPE_ERROR, msg.getString("type"));
        assertEquals("Something went wrong", msg.getString("message"));
    }

    @Test
    public void createBroadcastPlayMsg_containsCorrectFields() throws JSONException {
        int[] seatTypes = {0, 2, 2};
        String cardsJson = "[{\"suit\":\"SPADE\",\"rank\":\"THREE\"}]";
        JSONObject msg = DouDiZhuProtocol.createBroadcastPlayMsg(0, cardsJson, "SINGLE", 1, 0, seatTypes);
        assertEquals(DouDiZhuProtocol.TYPE_BROADCAST_ACTION, msg.getString("type"));
        assertEquals(0, msg.getInt("playerIndex"));
        assertEquals(cardsJson, msg.getString("cards"));
        assertEquals("SINGLE", msg.getString("cardType"));
        assertEquals(1, msg.getInt("currentTurn"));
        assertEquals(0, msg.getInt("landlordIndex"));
        assertTrue(msg.has("seatTypes"));
    }

    @Test
    public void createBroadcastPassMsg_containsCorrectFields() throws JSONException {
        int[] seatTypes = {0, 2, 2};
        JSONObject msg = DouDiZhuProtocol.createBroadcastPassMsg(1, 2, 0, seatTypes);
        assertEquals(DouDiZhuProtocol.TYPE_PASS_ACTION, msg.getString("type"));
        assertEquals(1, msg.getInt("playerIndex"));
        assertEquals(2, msg.getInt("currentTurn"));
        assertEquals(0, msg.getInt("landlordIndex"));
        assertTrue(msg.has("seatTypes"));
    }

    @Test
    public void createBidResultMsg_called_returnsCorrectFields() throws JSONException {
        int[] seatTypes = {0, 2, 2};
        JSONObject msg = DouDiZhuProtocol.createBidResultMsg(0, true, 1, 0, seatTypes);
        assertEquals(DouDiZhuProtocol.TYPE_BID_RESULT, msg.getString("type"));
        assertEquals(0, msg.getInt("seatIndex"));
        assertTrue(msg.getBoolean("call"));
        assertEquals(1, msg.getInt("currentTurn"));
        assertEquals(0, msg.getInt("landlordIndex"));
    }

    @Test
    public void createBidResultMsg_notCalled_returnsCorrectFields() throws JSONException {
        int[] seatTypes = {0, 2, 2};
        JSONObject msg = DouDiZhuProtocol.createBidResultMsg(1, false, 2, -1, seatTypes);
        assertFalse(msg.getBoolean("call"));
        assertEquals(2, msg.getInt("currentTurn"));
        assertEquals(-1, msg.getInt("landlordIndex"));
    }

    @Test
    public void createGameOverMsg_containsCorrectFields() throws JSONException {
        JSONObject msg = DouDiZhuProtocol.createGameOverMsg(0);
        assertEquals(DouDiZhuProtocol.TYPE_GAME_OVER, msg.getString("type"));
        assertEquals(0, msg.getInt("winnerIndex"));
    }

    @Test
    public void createGameOverMsg_landlordWins() throws JSONException {
        JSONObject msg = DouDiZhuProtocol.createGameOverMsg(1);
        assertEquals(1, msg.getInt("winnerIndex"));
    }

    @Test
    public void messageTypeConstants_areCorrect() {
        assertEquals("JOIN", DouDiZhuProtocol.TYPE_JOIN);
        assertEquals("SEAT_ASSIGNED", DouDiZhuProtocol.TYPE_SEAT_ASSIGNED);
        assertEquals("SEAT_UPDATE", DouDiZhuProtocol.TYPE_SEAT_UPDATE);
        assertEquals("HAND_CARDS", DouDiZhuProtocol.TYPE_HAND_CARDS);
        assertEquals("BID_REQUEST", DouDiZhuProtocol.TYPE_BID_REQUEST);
        assertEquals("BID_RESPONSE", DouDiZhuProtocol.TYPE_BID_RESPONSE);
        assertEquals("BID_RESULT", DouDiZhuProtocol.TYPE_BID_RESULT);
        assertEquals("GAME_START", DouDiZhuProtocol.TYPE_GAME_START);
        assertEquals("REQUEST_PLAY", DouDiZhuProtocol.TYPE_REQUEST_PLAY);
        assertEquals("PASS", DouDiZhuProtocol.TYPE_PASS);
        assertEquals("SYNC_STATE", DouDiZhuProtocol.TYPE_SYNC_STATE);
        assertEquals("STATE_ACK", DouDiZhuProtocol.TYPE_STATE_ACK);
        assertEquals("ACK", DouDiZhuProtocol.TYPE_ACK);
        assertEquals("GAME_OVER", DouDiZhuProtocol.TYPE_GAME_OVER);
        assertEquals("ERROR", DouDiZhuProtocol.TYPE_ERROR);
        assertEquals("CHAT", DouDiZhuProtocol.TYPE_CHAT);
        assertEquals("CHAT_HISTORY", DouDiZhuProtocol.TYPE_CHAT_HISTORY);
        assertEquals("BROADCAST_ACTION", DouDiZhuProtocol.TYPE_BROADCAST_ACTION);
        assertEquals("PASS_ACTION", DouDiZhuProtocol.TYPE_PASS_ACTION);
        assertEquals("ROOM_STATE", DouDiZhuProtocol.TYPE_ROOM_STATE);
    }
}
