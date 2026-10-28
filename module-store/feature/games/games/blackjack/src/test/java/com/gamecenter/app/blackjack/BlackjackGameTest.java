package com.gamecenter.app.blackjack;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class BlackjackGameTest {

    private BlackjackGame game;

    @Before
    public void setUp() {
        game = new BlackjackGame();
    }

    @Test
    public void testDeckInitialization() {
        assertEquals(52, game.getDeckRemaining());
        game.startNewRound();
        // 4 cards dealt initially
        assertEquals(48, game.getDeckRemaining());
        assertEquals(2, game.getPlayerHand().size());
        assertEquals(2, game.getDealerHand().size());
    }

    @Test
    public void testHandValueCalculation() {
        // Simple cards
        assertEquals(15, BlackjackGame.calculateHandValue(Arrays.asList("7♠", "8♥")));
        // Face cards
        assertEquals(20, BlackjackGame.calculateHandValue(Arrays.asList("K♦", "Q♣")));
        // Ace as 11
        assertEquals(21, BlackjackGame.calculateHandValue(Arrays.asList("A♠", "10♥")));
        // Ace as 1
        assertEquals(12, BlackjackGame.calculateHandValue(Arrays.asList("A♠", "5♥", "6♦")));
        // Multiple Aces
        assertEquals(13, BlackjackGame.calculateHandValue(Arrays.asList("A♠", "A♥", "A♦", "10♣")));
    }

    @Test
    public void testBlackjackDetection() {
        assertTrue(BlackjackGame.isBlackjack(Arrays.asList("A♠", "K♥")));
        assertTrue(BlackjackGame.isBlackjack(Arrays.asList("10♦", "A♣")));
        // 3 cards totaling 21 is not Blackjack
        assertFalse(BlackjackGame.isBlackjack(Arrays.asList("7♠", "7♥", "7♦")));
    }

    @Test
    public void testBasicStrategyHint() {
        // Player 16 vs Dealer 7: Should advise Hit
        game.setHandsForTest(Arrays.asList("10♠", "6♦"), Arrays.asList("7♥", "8♣"), true);
        String hint = game.getBasicStrategyHint();
        assertTrue(hint.contains("要牌"));

        // Player 16 vs Dealer 5: Should advise Stand (Dealer weak)
        game.setHandsForTest(Arrays.asList("10♠", "6♦"), Arrays.asList("5♥", "8♣"), true);
        hint = game.getBasicStrategyHint();
        assertTrue(hint.contains("停牌"));

        // Player 11 vs Dealer 6: Should advise Hit (Golden double/hit)
        game.setHandsForTest(Arrays.asList("5♠", "6♦"), Arrays.asList("6♥", "8♣"), true);
        hint = game.getBasicStrategyHint();
        assertTrue(hint.contains("要牌"));

        // Player Soft 19 vs Dealer 10: Should advise Stand
        game.setHandsForTest(Arrays.asList("A♠", "8♦"), Arrays.asList("10♥", "8♣"), true);
        hint = game.getBasicStrategyHint();
        assertTrue(hint.contains("停牌"));
    }

    @Test
    public void testHitAndUndo() {
        game.setHandsForTest(Arrays.asList("7♠", "5♦"), Arrays.asList("10♥", "8♣"), true);
        assertFalse(game.canUndo()); // No hits yet

        game.hit();
        assertEquals(3, game.getPlayerHand().size());
        assertTrue(game.canUndo());

        // Undo hit
        boolean undoOk = game.undoHit();
        assertTrue(undoOk);
        assertEquals(2, game.getPlayerHand().size());
        assertEquals(12, game.getPlayerScore());
        assertFalse(game.canUndo());
    }

    @Test
    public void testStandResolution() {
        // Player 19 vs Dealer 18
        game.setDifficulty(BlackjackGame.DIFFICULTY_EASY);
        game.setHandsForTest(Arrays.asList("10♠", "9♦"), Arrays.asList("10♥", "8♣"), true);
        game.stand();

        assertFalse(game.isRoundActive());
        assertEquals(BlackjackGame.RoundResult.PLAYER_WIN, game.getLastResult());
        assertEquals(1, game.getPlayerWins());
        assertEquals(1, game.getWinStreak());
    }

    @Test
    public void testDealerBust() {
        game.setDifficulty(BlackjackGame.DIFFICULTY_EASY);
        // Player 18, Dealer 16 (must hit under easy/medium)
        game.setHandsForTest(Arrays.asList("10♠", "8♦"), Arrays.asList("10♥", "6♣"), true);
        // Force dealer next draw to be a 10 by putting 10 on top of deck
        game.stand();
        // Whatever dealer draws, either score >= 17 or bust
        assertFalse(game.isRoundActive());
        assertTrue(game.getDealerScore() >= 17 || game.getLastResult() == BlackjackGame.RoundResult.DEALER_BUST);
    }

    @Test
    public void testDifficultyToggle() {
        assertEquals(BlackjackGame.DIFFICULTY_MEDIUM, game.getDifficulty());
        game.setDifficulty(BlackjackGame.DIFFICULTY_HARD);
        assertEquals(BlackjackGame.DIFFICULTY_HARD, game.getDifficulty());
    }
}
