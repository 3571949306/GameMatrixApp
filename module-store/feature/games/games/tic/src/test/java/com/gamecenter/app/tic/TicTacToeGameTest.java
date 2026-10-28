package com.gamecenter.app.tic;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class TicTacToeGameTest {

    private TicTacToeGame game;

    @Before
    public void setUp() {
        game = new TicTacToeGame();
        game.reset(1); // 中等难度
    }

    @Test
    public void testInitialState() {
        assertFalse(game.isGameOver());
        assertTrue(game.isPlayerTurn());
        assertEquals(0, game.getWinner());
        assertNull(game.getWinLine());
        assertEquals(1, game.getAiLevel());
        assertEquals(0, game.getPlayerCount());
        assertEquals(0, game.getAiCount());
        assertEquals(0, game.getTotalMoves());
        assertFalse(game.canUndo());

        int[][] board = game.getBoard();
        for (int r = 0; r < TicTacToeGame.BOARD_SIZE; r++) {
            for (int c = 0; c < TicTacToeGame.BOARD_SIZE; c++) {
                assertEquals(0, board[r][c]);
            }
        }
    }

    @Test
    public void testPlayerMoveAndAiResponse() {
        boolean placed = game.placePlayer(0, 0);
        assertTrue(placed);
        assertEquals(1, game.getPlayerCount());
        assertEquals(0, game.getAiCount());
        assertEquals(1, game.getTotalMoves());
        assertFalse(game.isPlayerTurn());
        assertTrue(game.canUndo());
        assertArrayEquals(new int[]{0, 0}, game.getLastMove());

        int[] aiMove = game.aiMove();
        assertNotNull(aiMove);
        assertEquals(1, game.getPlayerCount());
        assertEquals(1, game.getAiCount());
        assertEquals(2, game.getTotalMoves());
        assertTrue(game.isPlayerTurn());
        assertArrayEquals(aiMove, game.getLastMove());
    }

    @Test
    public void testUndo() {
        game.placePlayer(1, 1);
        game.aiMove();
        assertEquals(2, game.getTotalMoves());

        boolean undone = game.undo();
        assertTrue(undone);
        // 回退后回到玩家落子前的盘面（0步）
        assertEquals(0, game.getTotalMoves());
        assertTrue(game.isPlayerTurn());
        assertFalse(game.canUndo());
    }

    @Test
    public void testPlayerWinHorizontal() {
        game.reset(0);
        int[][] b = game.getBoard();
        b[0][0] = TicTacToeGame.PLAYER_X;
        b[0][1] = TicTacToeGame.PLAYER_X;
        b[1][0] = TicTacToeGame.PLAYER_O;
        b[1][1] = TicTacToeGame.PLAYER_O;

        game.setPlayerTurnForTest(true);
        boolean placed = game.placePlayer(0, 2);
        assertTrue(placed);

        assertTrue(game.isGameOver());
        assertEquals(TicTacToeGame.RESULT_PLAYER_WIN, game.getWinner());
        assertNotNull(game.getWinLine());
        assertArrayEquals(new int[]{0, 0, 0, 2}, game.getWinLine());
    }

    @Test
    public void testPlayerWinDiagonal() {
        game.reset(0);
        int[][] b = game.getBoard();
        b[0][0] = TicTacToeGame.PLAYER_X;
        b[1][1] = TicTacToeGame.PLAYER_X;
        b[0][1] = TicTacToeGame.PLAYER_O;
        b[0][2] = TicTacToeGame.PLAYER_O;

        game.setPlayerTurnForTest(true);
        boolean placed = game.placePlayer(2, 2);
        assertTrue(placed);

        assertTrue(game.isGameOver());
        assertEquals(TicTacToeGame.RESULT_PLAYER_WIN, game.getWinner());
        assertNotNull(game.getWinLine());
        assertArrayEquals(new int[]{0, 0, 2, 2}, game.getWinLine());
    }

    @Test
    public void testPlayerHint() {
        int[] hint = game.getPlayerHint();
        assertNotNull(hint);
        assertTrue(hint[0] >= 0 && hint[0] < TicTacToeGame.BOARD_SIZE);
        assertTrue(hint[1] >= 0 && hint[1] < TicTacToeGame.BOARD_SIZE);
        assertEquals(0, game.getBoard()[hint[0]][hint[1]]);

        // 测试提示必胜落子
        game.reset(0);
        int[][] b = game.getBoard();
        b[0][0] = TicTacToeGame.PLAYER_X;
        b[0][1] = TicTacToeGame.PLAYER_X;
        b[1][0] = TicTacToeGame.PLAYER_O;
        b[1][1] = TicTacToeGame.PLAYER_O;
        game.setPlayerTurnForTest(true);

        // 此时 (0,2) 是必胜手，hint 应该返回 (0,2)
        int[] winningHint = game.getPlayerHint();
        assertNotNull(winningHint);
        assertArrayEquals(new int[]{0, 2}, winningHint);
    }

    @Test
    public void testDifficultyClamping() {
        game.reset(-1);
        assertEquals(0, game.getAiLevel());

        game.reset(5);
        assertEquals(2, game.getAiLevel());

        game.reset(1);
        assertEquals(1, game.getAiLevel());
    }
}
