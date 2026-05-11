package com.gamecenter.app.games.chinesechess;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

public class ChineseChessGameTest {

    private ChineseChessGame game;

    @Before
    public void setUp() {
        game = new ChineseChessGame();
    }

    @Test
    public void testInitialState() {
        assertFalse(game.isGameOver());
        assertNull(game.getWinner());
        assertEquals(ChineseChessGame.Side.RED, game.getCurrentSide());
        assertEquals(9, ChineseChessGame.COLS);
        assertEquals(10, ChineseChessGame.ROWS);
    }

    @Test
    public void testInitialBoardSetup() {
        ChineseChessGame.Piece[][] board = game.getBoard();
        
        assertNotNull(board[0][0]);
        assertEquals(ChineseChessGame.PieceType.CHARIOT, board[0][0].type);
        assertEquals(ChineseChessGame.Side.BLACK, board[0][0].side);
        
        assertNotNull(board[9][0]);
        assertEquals(ChineseChessGame.PieceType.CHARIOT, board[9][0].type);
        assertEquals(ChineseChessGame.Side.RED, board[9][0].side);
    }

    @Test
    public void testInitialPieceCount() {
        ChineseChessGame.Piece[][] board = game.getBoard();
        int redCount = 0, blackCount = 0;
        
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                if (board[y][x] != null) {
                    if (board[y][x].side == ChineseChessGame.Side.RED) redCount++;
                    else blackCount++;
                }
            }
        }
        
        assertEquals(16, redCount);
        assertEquals(16, blackCount);
    }

    @Test
    public void testGetMovesForChariot() {
        ChineseChessGame.Piece chariot = game.getBoard()[9][0];
        assertNotNull(chariot);
        assertEquals(ChineseChessGame.PieceType.CHARIOT, chariot.type);
        
        List<int[]> moves = game.getMoves(chariot);
        assertFalse(moves.isEmpty());
    }

    @Test
    public void testGetMovesForGeneral() {
        ChineseChessGame.Piece general = game.getBoard()[9][4];
        assertNotNull(general);
        assertEquals(ChineseChessGame.PieceType.GENERAL, general.type);
        
        List<int[]> moves = game.getMoves(general);
        assertNotNull(moves);
    }

    @Test
    public void testMakeMove() {
        ChineseChessGame.MoveRecord record = game.makeMoveSafe(0, 6, 0, 5);
        assertNotNull(record);
        assertEquals(0, record.fromX);
        assertEquals(6, record.fromY);
        assertEquals(0, record.toX);
        assertEquals(5, record.toY);
    }

    @Test
    public void testSwitchSide() {
        assertEquals(ChineseChessGame.Side.RED, game.getCurrentSide());
        game.switchSide();
        assertEquals(ChineseChessGame.Side.BLACK, game.getCurrentSide());
        game.switchSide();
        assertEquals(ChineseChessGame.Side.RED, game.getCurrentSide());
    }

    @Test
    public void testReset() {
        game.makeMoveSafe(0, 6, 0, 5);
        game.switchSide();
        game.reset();
        
        assertFalse(game.isGameOver());
        assertNull(game.getWinner());
        assertEquals(ChineseChessGame.Side.RED, game.getCurrentSide());
        assertTrue(game.getMoveHistory().isEmpty());
    }

    @Test
    public void testSetGameOver() {
        game.setGameOver(ChineseChessGame.Side.RED);
        assertTrue(game.isGameOver());
        assertEquals(ChineseChessGame.Side.RED, game.getWinner());
    }

    @Test
    public void testDeepCopy() {
        ChineseChessGame copy = game.deepCopy();
        
        assertEquals(game.getCurrentSide(), copy.getCurrentSide());
        assertEquals(game.isGameOver(), copy.isGameOver());
        
        ChineseChessGame.Piece[][] origBoard = game.getBoard();
        ChineseChessGame.Piece[][] copyBoard = copy.getBoard();
        
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 9; x++) {
                if (origBoard[y][x] == null) {
                    assertNull(copyBoard[y][x]);
                } else {
                    assertNotNull(copyBoard[y][x]);
                    assertEquals(origBoard[y][x].type, copyBoard[y][x].type);
                    assertEquals(origBoard[y][x].side, copyBoard[y][x].side);
                }
            }
        }
    }

    @Test
    public void testGetLegalMoves() {
        List<int[]> legalMoves = game.getLegalMoves(0, 6);
        assertNotNull(legalMoves);
        assertFalse(legalMoves.isEmpty());
    }
}
