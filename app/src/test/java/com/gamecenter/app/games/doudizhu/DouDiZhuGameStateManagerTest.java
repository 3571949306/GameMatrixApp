package com.gamecenter.app.games.doudizhu;

import com.gamecenter.app.games.doudizhu.model.Card;
import com.gamecenter.app.games.doudizhu.model.Rank;
import com.gamecenter.app.games.doudizhu.model.Suit;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class DouDiZhuGameStateManagerTest {

    private DouDiZhuGameStateManager manager;

    @Before
    public void setUp() {
        manager = new DouDiZhuGameStateManager();
    }

    private List<Card> cardsOf(Rank... ranks) {
        List<Card> list = new ArrayList<>();
        for (Rank rank : ranks) {
            list.add(Card.create(Suit.SPADE, rank));
        }
        return list;
    }

    @Test
    public void startGame_setsStateToBidding() {
        manager.startGame();
        assertEquals(DouDiZhuGameStateManager.STATE_BIDDING, manager.getGameState());
    }

    @Test
    public void startGame_dealsCardsToAllSeats() {
        manager.startGame();
        assertEquals(17, manager.getPlayerHandCards().size());
        assertEquals(17, manager.getSeat1Cards().size());
        assertEquals(17, manager.getSeat2Cards().size());
        assertEquals(3, manager.getBottomCards().size());
    }

    @Test
    public void startGame_resetsWinnerIndex() {
        manager.startGame();
        assertEquals(-1, manager.getWinnerIndex());
    }

    @Test
    public void startGame_resetsPlayerPassed() {
        manager.startGame();
        boolean[] passed = manager.getPlayerPassed();
        assertFalse(passed[0]);
        assertFalse(passed[1]);
        assertFalse(passed[2]);
    }

    @Test
    public void startGame_resetsBidRound() {
        manager.startGame();
        assertEquals(0, manager.getBidRound());
    }

    @Test
    public void startGame_clearsPlayedCards() {
        manager.startGame();
        assertTrue(manager.getPlayerPlayedCards().isEmpty());
        assertTrue(manager.getSeat1PlayedCards().isEmpty());
        assertTrue(manager.getSeat2PlayedCards().isEmpty());
    }

    @Test
    public void startGame_setsHandCounts() {
        manager.startGame();
        int[] counts = manager.getHandCounts();
        assertEquals(17, counts[0]);
        assertEquals(17, counts[1]);
        assertEquals(17, counts[2]);
    }

    @Test
    public void startGame_currentTurnInRange() {
        manager.startGame();
        int turn = manager.getCurrentTurn();
        assertTrue(turn >= 0 && turn <= 2);
    }

    @Test
    public void startGame_bidTurnMatchesCurrentTurn() {
        manager.startGame();
        assertEquals(manager.getCurrentTurn(), manager.getBidTurn());
    }

    @Test
    public void advanceBidTurn_incrementsBidRound() {
        manager.startGame();
        manager.advanceBidTurn();
        assertEquals(1, manager.getBidRound());
    }

    @Test
    public void advanceBidTurn_cyclesToNextPlayer() {
        manager.startGame();
        int turnBefore = manager.getCurrentTurn();
        manager.advanceBidTurn();
        assertEquals((turnBefore + 1) % 3, manager.getCurrentTurn());
    }

    @Test
    public void advanceBidTurn_bidTurnMatchesCurrentTurn() {
        manager.startGame();
        manager.advanceBidTurn();
        assertEquals(manager.getCurrentTurn(), manager.getBidTurn());
    }

    @Test
    public void advanceBidTurn_threeRoundsForcesLandlord() {
        manager.startGame();
        manager.advanceBidTurn();
        manager.advanceBidTurn();
        manager.advanceBidTurn();
        assertTrue(manager.getLandlordIndex() >= 0 && manager.getLandlordIndex() <= 2);
        assertEquals(DouDiZhuGameStateManager.STATE_PLAYING, manager.getGameState());
    }

    @Test
    public void advanceBidTurn_secondRound_incrementsToTwo() {
        manager.startGame();
        manager.advanceBidTurn();
        manager.advanceBidTurn();
        assertEquals(2, manager.getBidRound());
    }

    @Test
    public void setLandlord_addsBottomCardsToSeat0() {
        manager.startGame();
        int handSizeBefore = manager.getPlayerHandCards().size();
        manager.setLandlord(0);
        assertEquals(handSizeBefore + 3, manager.getPlayerHandCards().size());
    }

    @Test
    public void setLandlord_addsBottomCardsToSeat1() {
        manager.startGame();
        int handSizeBefore = manager.getSeat1Cards().size();
        manager.setLandlord(1);
        assertEquals(handSizeBefore + 3, manager.getSeat1Cards().size());
    }

    @Test
    public void setLandlord_addsBottomCardsToSeat2() {
        manager.startGame();
        int handSizeBefore = manager.getSeat2Cards().size();
        manager.setLandlord(2);
        assertEquals(handSizeBefore + 3, manager.getSeat2Cards().size());
    }

    @Test
    public void setLandlord_updatesLandlordIndex() {
        manager.startGame();
        manager.setLandlord(1);
        assertEquals(1, manager.getLandlordIndex());
    }

    @Test
    public void setLandlord_updatesHandCounts() {
        manager.startGame();
        manager.setLandlord(0);
        assertEquals(20, manager.getHandCounts()[0]);
    }

    @Test
    public void setLandlord_sortsHandByWeight() {
        manager.startGame();
        manager.setLandlord(0);
        List<Card> hand = manager.getPlayerHandCards();
        for (int i = 1; i < hand.size(); i++) {
            assertTrue(hand.get(i).getWeight() >= hand.get(i - 1).getWeight());
        }
    }

    @Test
    public void startPlayingPhase_setsStateToPlaying() {
        manager.startGame();
        manager.setLandlord(0);
        manager.startPlayingPhase();
        assertEquals(DouDiZhuGameStateManager.STATE_PLAYING, manager.getGameState());
    }

    @Test
    public void startPlayingPhase_setsTurnToLandlord() {
        manager.startGame();
        manager.setLandlord(1);
        manager.startPlayingPhase();
        assertEquals(1, manager.getCurrentTurn());
    }

    @Test
    public void startPlayingPhase_resetsLastPlayerWhoPlayed() {
        manager.startGame();
        manager.setLandlord(0);
        manager.startPlayingPhase();
        assertEquals(-1, manager.getLastPlayerWhoPlayed());
    }

    @Test
    public void startPlayingPhase_resetsPlayerPassed() {
        manager.startGame();
        manager.setLandlord(0);
        manager.startPlayingPhase();
        boolean[] passed = manager.getPlayerPassed();
        assertFalse(passed[0]);
        assertFalse(passed[1]);
        assertFalse(passed[2]);
    }

    @Test
    public void startPlayingPhase_clearsAllPlayedCards() {
        manager.startGame();
        manager.setLandlord(0);
        manager.startPlayingPhase();
        assertTrue(manager.getPlayerPlayedCards().isEmpty());
        assertTrue(manager.getSeat1PlayedCards().isEmpty());
        assertTrue(manager.getSeat2PlayedCards().isEmpty());
    }

    @Test
    public void executePlay_removesCardsFromHand() {
        manager.startGame();
        manager.setLandlord(0);
        manager.startPlayingPhase();
        Card cardToPlay = manager.getPlayerHandCards().get(0);
        List<Card> play = new ArrayList<>();
        play.add(cardToPlay);
        int handSizeBefore = manager.getPlayerHandCards().size();
        manager.executePlay(0, play);
        assertEquals(handSizeBefore - 1, manager.getPlayerHandCards().size());
    }

    @Test
    public void executePlay_updatesHandCounts() {
        manager.startGame();
        manager.setLandlord(0);
        manager.startPlayingPhase();
        Card cardToPlay = manager.getPlayerHandCards().get(0);
        List<Card> play = new ArrayList<>();
        play.add(cardToPlay);
        manager.executePlay(0, play);
        assertEquals(manager.getPlayerHandCards().size(), manager.getHandCounts()[0]);
    }

    @Test
    public void executePlay_switchesToNextPlayer() {
        manager.startGame();
        manager.setLandlord(0);
        manager.startPlayingPhase();
        Card cardToPlay = manager.getPlayerHandCards().get(0);
        List<Card> play = new ArrayList<>();
        play.add(cardToPlay);
        manager.executePlay(0, play);
        assertEquals(1, manager.getCurrentTurn());
    }

    @Test
    public void executePlay_resetsPlayerPassed() {
        manager.startGame();
        manager.setLandlord(0);
        manager.startPlayingPhase();
        manager.setPlayerPassed(1, true);
        manager.setPlayerPassed(2, true);
        Card cardToPlay = manager.getPlayerHandCards().get(0);
        List<Card> play = new ArrayList<>();
        play.add(cardToPlay);
        manager.executePlay(0, play);
        assertFalse(manager.getPlayerPassed()[1]);
        assertFalse(manager.getPlayerPassed()[2]);
    }

    @Test
    public void executePlay_updatesLastPlayerWhoPlayed() {
        manager.startGame();
        manager.setLandlord(0);
        manager.startPlayingPhase();
        Card cardToPlay = manager.getPlayerHandCards().get(0);
        List<Card> play = new ArrayList<>();
        play.add(cardToPlay);
        manager.executePlay(0, play);
        assertEquals(0, manager.getLastPlayerWhoPlayed());
    }

    @Test
    public void executePlay_setsPlayedCards() {
        manager.startGame();
        manager.setLandlord(0);
        manager.startPlayingPhase();
        Card cardToPlay = manager.getPlayerHandCards().get(0);
        List<Card> play = new ArrayList<>();
        play.add(cardToPlay);
        manager.executePlay(0, play);
        assertEquals(1, manager.getPlayerPlayedCards().size());
        assertEquals(cardToPlay, manager.getPlayerPlayedCards().get(0));
    }

    @Test
    public void executePlay_emptyHand_triggersGameOver() {
        manager.startGame();
        manager.setLandlord(0);
        manager.startPlayingPhase();
        List<Card> allCards = new ArrayList<>(manager.getPlayerHandCards());
        manager.executePlay(0, allCards);
        assertEquals(DouDiZhuGameStateManager.STATE_GAME_OVER, manager.getGameState());
        assertEquals(0, manager.getWinnerIndex());
    }

    @Test
    public void executePlay_seat1_removesCardsFromSeat1() {
        manager.startGame();
        manager.setLandlord(1);
        manager.startPlayingPhase();
        Card cardToPlay = manager.getSeat1Cards().get(0);
        List<Card> play = new ArrayList<>();
        play.add(cardToPlay);
        int handSizeBefore = manager.getSeat1Cards().size();
        manager.executePlay(1, play);
        assertEquals(handSizeBefore - 1, manager.getSeat1Cards().size());
    }

    @Test
    public void switchToNextPlayer_cyclesThrough() {
        manager.startGame();
        manager.setLandlord(0);
        manager.startPlayingPhase();
        assertEquals(0, manager.getCurrentTurn());
        manager.switchToNextPlayer();
        assertEquals(1, manager.getCurrentTurn());
        manager.switchToNextPlayer();
        assertEquals(2, manager.getCurrentTurn());
        manager.switchToNextPlayer();
        assertEquals(0, manager.getCurrentTurn());
    }

    @Test
    public void checkAndClearTable_notAllPassed_returnsFalse() {
        manager.startGame();
        manager.setLandlord(0);
        manager.startPlayingPhase();
        manager.setPlayerPassed(1, true);
        assertFalse(manager.checkAndClearTable());
    }

    @Test
    public void checkAndClearTable_noOnePassed_returnsFalse() {
        manager.startGame();
        manager.setLandlord(0);
        manager.startPlayingPhase();
        assertFalse(manager.checkAndClearTable());
    }

    @Test
    public void checkAndClearTable_allOthersPassed_returnsTrue() {
        manager.startGame();
        manager.setLandlord(0);
        manager.startPlayingPhase();
        Card cardToPlay = manager.getPlayerHandCards().get(0);
        List<Card> play = new ArrayList<>();
        play.add(cardToPlay);
        manager.executePlay(0, play);
        manager.setPlayerPassed(1, true);
        manager.setPlayerPassed(2, true);
        assertTrue(manager.checkAndClearTable());
    }

    @Test
    public void checkAndClearTable_clearsPlayedCards() {
        manager.startGame();
        manager.setLandlord(0);
        manager.startPlayingPhase();
        Card cardToPlay = manager.getPlayerHandCards().get(0);
        List<Card> play = new ArrayList<>();
        play.add(cardToPlay);
        manager.executePlay(0, play);
        manager.setPlayerPassed(1, true);
        manager.setPlayerPassed(2, true);
        manager.checkAndClearTable();
        assertTrue(manager.getPlayerPlayedCards().isEmpty());
        assertTrue(manager.getSeat1PlayedCards().isEmpty());
        assertTrue(manager.getSeat2PlayedCards().isEmpty());
    }

    @Test
    public void checkAndClearTable_resetsPlayerPassed() {
        manager.startGame();
        manager.setLandlord(0);
        manager.startPlayingPhase();
        Card cardToPlay = manager.getPlayerHandCards().get(0);
        List<Card> play = new ArrayList<>();
        play.add(cardToPlay);
        manager.executePlay(0, play);
        manager.setPlayerPassed(1, true);
        manager.setPlayerPassed(2, true);
        manager.checkAndClearTable();
        assertFalse(manager.getPlayerPassed()[0]);
        assertFalse(manager.getPlayerPassed()[1]);
        assertFalse(manager.getPlayerPassed()[2]);
    }

    @Test
    public void checkAndClearTable_givesTurnToLastPlayer() {
        manager.startGame();
        manager.setLandlord(0);
        manager.startPlayingPhase();
        Card cardToPlay = manager.getPlayerHandCards().get(0);
        List<Card> play = new ArrayList<>();
        play.add(cardToPlay);
        manager.executePlay(0, play);
        manager.setPlayerPassed(1, true);
        manager.setPlayerPassed(2, true);
        manager.checkAndClearTable();
        assertEquals(0, manager.getCurrentTurn());
    }

    @Test
    public void checkGameOver_setsStateToGameOver() {
        manager.checkGameOver(1);
        assertEquals(DouDiZhuGameStateManager.STATE_GAME_OVER, manager.getGameState());
        assertEquals(1, manager.getWinnerIndex());
    }

    @Test
    public void checkGameOver_seat2Wins() {
        manager.checkGameOver(2);
        assertEquals(DouDiZhuGameStateManager.STATE_GAME_OVER, manager.getGameState());
        assertEquals(2, manager.getWinnerIndex());
    }

    @Test
    public void resetGameState_returnsToLobby() {
        manager.startGame();
        manager.setLandlord(0);
        manager.resetGameState();
        assertEquals(DouDiZhuGameStateManager.STATE_LOBBY, manager.getGameState());
    }

    @Test
    public void resetGameState_clearsHands() {
        manager.startGame();
        manager.resetGameState();
        assertTrue(manager.getPlayerHandCards().isEmpty());
        assertTrue(manager.getSeat1Cards().isEmpty());
        assertTrue(manager.getSeat2Cards().isEmpty());
        assertTrue(manager.getBottomCards().isEmpty());
    }

    @Test
    public void resetGameState_clearsPlayedCards() {
        manager.startGame();
        manager.resetGameState();
        assertTrue(manager.getPlayerPlayedCards().isEmpty());
        assertTrue(manager.getSeat1PlayedCards().isEmpty());
        assertTrue(manager.getSeat2PlayedCards().isEmpty());
    }

    @Test
    public void resetGameState_resetsIndices() {
        manager.startGame();
        manager.setLandlord(1);
        manager.resetGameState();
        assertEquals(0, manager.getCurrentTurn());
        assertEquals(-1, manager.getLandlordIndex());
        assertEquals(-1, manager.getWinnerIndex());
        assertEquals(-1, manager.getLastPlayerWhoPlayed());
    }

    @Test
    public void resetGameState_resetsBidTurnAndRound() {
        manager.startGame();
        manager.advanceBidTurn();
        manager.resetGameState();
        assertEquals(0, manager.getBidTurn());
        assertEquals(0, manager.getBidRound());
    }

    @Test
    public void resetGameState_resetsHandCounts() {
        manager.startGame();
        manager.resetGameState();
        int[] counts = manager.getHandCounts();
        assertEquals(17, counts[0]);
        assertEquals(17, counts[1]);
        assertEquals(17, counts[2]);
    }

    @Test
    public void resetGameState_resetsPlayerPassed() {
        manager.startGame();
        manager.setPlayerPassed(0, true);
        manager.resetGameState();
        assertFalse(manager.getPlayerPassed()[0]);
    }

    @Test
    public void getLastPlayedCards_noOnePlayed_returnsNull() {
        manager.startGame();
        manager.setLandlord(0);
        manager.startPlayingPhase();
        assertNull(manager.getLastPlayedCards());
    }

    @Test
    public void getLastPlayedCards_afterPlay_returnsPlayedCards() {
        manager.startGame();
        manager.setLandlord(0);
        manager.startPlayingPhase();
        Card cardToPlay = manager.getPlayerHandCards().get(0);
        List<Card> play = new ArrayList<>();
        play.add(cardToPlay);
        manager.executePlay(0, play);
        List<Card> lastPlayed = manager.getLastPlayedCards();
        assertNotNull(lastPlayed);
        assertEquals(1, lastPlayed.size());
        assertEquals(cardToPlay, lastPlayed.get(0));
    }

    @Test
    public void getLastPlayedCards_afterPass_returnsNull() {
        manager.startGame();
        manager.setLandlord(0);
        manager.startPlayingPhase();
        Card cardToPlay = manager.getPlayerHandCards().get(0);
        List<Card> play = new ArrayList<>();
        play.add(cardToPlay);
        manager.executePlay(0, play);
        manager.setPlayerPassed(0, true);
        assertNull(manager.getLastPlayedCards());
    }

    @Test
    public void setPlayerPassed_setsPassedState() {
        manager.setPlayerPassed(1, true);
        assertTrue(manager.getPlayerPassed()[1]);
        manager.setPlayerPassed(1, false);
        assertFalse(manager.getPlayerPassed()[1]);
    }

    @Test
    public void clearSeatPlayedCards_clearsSpecificSeat() {
        manager.startGame();
        manager.setLandlord(0);
        manager.startPlayingPhase();
        Card cardToPlay = manager.getPlayerHandCards().get(0);
        List<Card> play = new ArrayList<>();
        play.add(cardToPlay);
        manager.executePlay(0, play);
        assertFalse(manager.getPlayerPlayedCards().isEmpty());
        manager.clearSeatPlayedCards(0);
        assertTrue(manager.getPlayerPlayedCards().isEmpty());
    }

    @Test
    public void listener_onStateChanged_calledOnStartGame() {
        final int[] capturedState = {-1};
        manager.setListener(new DouDiZhuGameStateManager.GameStateListener() {
            @Override
            public void onStateChanged(int newState) {
                capturedState[0] = newState;
            }

            @Override
            public void onTurnChanged(int newTurn) {
            }

            @Override
            public void onLandlordSet(int landlordIndex) {
            }

            @Override
            public void onGameOver(int winnerIndex) {
            }
        });
        manager.startGame();
        assertEquals(DouDiZhuGameStateManager.STATE_BIDDING, capturedState[0]);
    }

    @Test
    public void listener_onTurnChanged_calledOnStartGame() {
        final int[] capturedTurn = {-1};
        manager.setListener(new DouDiZhuGameStateManager.GameStateListener() {
            @Override
            public void onStateChanged(int newState) {
            }

            @Override
            public void onTurnChanged(int newTurn) {
                capturedTurn[0] = newTurn;
            }

            @Override
            public void onLandlordSet(int landlordIndex) {
            }

            @Override
            public void onGameOver(int winnerIndex) {
            }
        });
        manager.startGame();
        assertEquals(manager.getCurrentTurn(), capturedTurn[0]);
    }

    @Test
    public void listener_onLandlordSet_calledOnSetLandlord() {
        final int[] capturedLandlord = {-1};
        manager.setListener(new DouDiZhuGameStateManager.GameStateListener() {
            @Override
            public void onStateChanged(int newState) {
            }

            @Override
            public void onTurnChanged(int newTurn) {
            }

            @Override
            public void onLandlordSet(int landlordIndex) {
                capturedLandlord[0] = landlordIndex;
            }

            @Override
            public void onGameOver(int winnerIndex) {
            }
        });
        manager.startGame();
        manager.setLandlord(2);
        assertEquals(2, capturedLandlord[0]);
    }

    @Test
    public void listener_onGameOver_calledOnCheckGameOver() {
        final int[] capturedWinner = {-1};
        final int[] capturedState = {-1};
        manager.setListener(new DouDiZhuGameStateManager.GameStateListener() {
            @Override
            public void onStateChanged(int newState) {
                capturedState[0] = newState;
            }

            @Override
            public void onTurnChanged(int newTurn) {
            }

            @Override
            public void onLandlordSet(int landlordIndex) {
            }

            @Override
            public void onGameOver(int winnerIndex) {
                capturedWinner[0] = winnerIndex;
            }
        });
        manager.checkGameOver(1);
        assertEquals(1, capturedWinner[0]);
        assertEquals(DouDiZhuGameStateManager.STATE_GAME_OVER, capturedState[0]);
    }

    @Test
    public void listener_onStateChanged_calledOnResetGameState() {
        final int[] capturedState = {-1};
        manager.setListener(new DouDiZhuGameStateManager.GameStateListener() {
            @Override
            public void onStateChanged(int newState) {
                capturedState[0] = newState;
            }

            @Override
            public void onTurnChanged(int newTurn) {
            }

            @Override
            public void onLandlordSet(int landlordIndex) {
            }

            @Override
            public void onGameOver(int winnerIndex) {
            }
        });
        manager.resetGameState();
        assertEquals(DouDiZhuGameStateManager.STATE_LOBBY, capturedState[0]);
    }

    @Test
    public void listener_onTurnChanged_calledOnAdvanceBidTurn() {
        manager.startGame();
        final int[] capturedTurn = {-1};
        manager.setListener(new DouDiZhuGameStateManager.GameStateListener() {
            @Override
            public void onStateChanged(int newState) {
            }

            @Override
            public void onTurnChanged(int newTurn) {
                capturedTurn[0] = newTurn;
            }

            @Override
            public void onLandlordSet(int landlordIndex) {
            }

            @Override
            public void onGameOver(int winnerIndex) {
            }
        });
        manager.advanceBidTurn();
        assertEquals(manager.getCurrentTurn(), capturedTurn[0]);
    }

    @Test
    public void listener_onTurnChanged_calledOnSwitchToNextPlayer() {
        manager.startGame();
        manager.setLandlord(0);
        manager.startPlayingPhase();
        final int[] capturedTurn = {-1};
        manager.setListener(new DouDiZhuGameStateManager.GameStateListener() {
            @Override
            public void onStateChanged(int newState) {
            }

            @Override
            public void onTurnChanged(int newTurn) {
                capturedTurn[0] = newTurn;
            }

            @Override
            public void onLandlordSet(int landlordIndex) {
            }

            @Override
            public void onGameOver(int winnerIndex) {
            }
        });
        manager.switchToNextPlayer();
        assertEquals(1, capturedTurn[0]);
    }

    @Test
    public void listener_onStateChanged_calledOnStartPlayingPhase() {
        manager.startGame();
        manager.setLandlord(0);
        final int[] capturedState = {-1};
        manager.setListener(new DouDiZhuGameStateManager.GameStateListener() {
            @Override
            public void onStateChanged(int newState) {
                capturedState[0] = newState;
            }

            @Override
            public void onTurnChanged(int newTurn) {
            }

            @Override
            public void onLandlordSet(int landlordIndex) {
            }

            @Override
            public void onGameOver(int winnerIndex) {
            }
        });
        manager.startPlayingPhase();
        assertEquals(DouDiZhuGameStateManager.STATE_PLAYING, capturedState[0]);
    }

    @Test
    public void listener_null_doesNotThrow() {
        manager.setListener(null);
        manager.startGame();
        manager.setLandlord(0);
        manager.startPlayingPhase();
        manager.checkGameOver(0);
        manager.resetGameState();
    }
}
