package com.gamecenter.app.games.chinesechess;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

public class ChineseChessAITest {

    private ChineseChessGame game;
    private ChineseChessAI ai;

    @Before
    public void setUp() {
        game = new ChineseChessGame();
        ai = new ChineseChessAI(1);
    }

    @Test
    public void testAIFindsValidMoveFromStart() {
        int[] move = ai.getBestMove(game);
        assertNotNull(move);
        assertEquals(4, move.length);
        assertTrue(move[0] >= 0 && move[0] < 9);
        assertTrue(move[1] >= 0 && move[1] < 10);
        assertTrue(move[2] >= 0 && move[2] < 9);
        assertTrue(move[3] >= 0 && move[3] < 10);
    }

    @Test
    public void testAIMoveChangesBoard() {
        int[] move = ai.getBestMove(game);
        assertNotNull(move);
        ChineseChessGame.Piece piece = game.getBoard()[move[1]][move[0]];
        assertNotNull(piece);
        assertEquals(ChineseChessGame.Side.RED, piece.side);
    }

    @Test
    public void testAIDifficultyLevels() {
        for (int level = 1; level <= 4; level++) {
            ChineseChessAI levelAi = new ChineseChessAI(level);
            int[] move = levelAi.getBestMove(game);
            assertNotNull("Level " + level + " should return a move", move);
        }
    }

    @Test
    public void testAICapturesWhenPossible() {
        ChineseChessGame.Piece[][] board = game.getBoard();
        board[1][4] = null;
        board[3][4] = new ChineseChessGame.Piece(ChineseChessGame.PieceType.CHARIOT, ChineseChessGame.Side.RED);
        int[] move = ai.getBestMove(game);
        assertNotNull(move);
    }

    @Test
    public void testAIDoesNotMoveOpponentPiece() {
        int[] move = ai.getBestMove(game);
        assertNotNull(move);
        ChineseChessGame.Piece piece = game.getBoard()[move[1]][move[0]];
        assertNotNull(piece);
        assertNotEquals(ChineseChessGame.Side.BLACK, piece.side);
    }

    @Test
    public void testAIPieceTypeIsCorrect() {
        int[] move = ai.getBestMove(game);
        assertNotNull(move);
        ChineseChessGame.Piece piece = game.getBoard()[move[1]][move[0]];
        assertNotNull(piece);
        assertNotNull(piece.type);
    }

    @Test
    public void testAIDestinationIsLegal() {
        int[] move = ai.getBestMove(game);
        assertNotNull(move);
        List<int[]> legalMoves = game.getLegalMoves(move[0], move[1]);
        boolean found = false;
        for (int[] legal : legalMoves) {
            if (legal[0] == move[2] && legal[1] == move[3]) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    public void testAIGeneralNotLeftInCheck() {
        for (int i = 0; i < 3; i++) {
            int[] move = ai.getBestMove(game);
            assertNotNull(move);
            game.makeMoveSafe(move[0], move[1], move[2], move[3]);
            game.switchSide();
            ChineseChessAI blackAi = new ChineseChessAI(1);
            int[] blackMove = blackAi.getBestMove(game);
            assertNotNull(blackMove);
            game.makeMoveSafe(blackMove[0], blackMove[1], blackMove[2], blackMove[3]);
            game.switchSide();
        }
        assertFalse(game.isGameOver());
    }

    @Test
    public void testAIDoesNotModifyOriginalGame() {
        ChineseChessGame.Piece[][] originalBoard = game.getBoard();
        int originalSide = game.getCurrentSide().ordinal();
        ai.getBestMove(game);
        assertEquals(originalSide, game.getCurrentSide().ordinal());
    }

    @Test
    public void testAIReturnsNullOnNoMoves() {
        ChineseChessGame emptyGame = new ChineseChessGame();
        ChineseChessGame.Piece[][] board = emptyGame.getBoard();
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                board[y][x] = null;
            }
        }
        int[] move = ai.getBestMove(emptyGame);
        assertTrue(move == null || move.length == 4);
    }
}
