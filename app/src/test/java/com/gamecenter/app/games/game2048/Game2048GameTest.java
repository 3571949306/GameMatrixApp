package com.gamecenter.app.games.game2048;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class Game2048GameTest {

    private Game2048Game game;

    @Before
    public void setUp() {
        game = new Game2048Game();
    }

    @Test
    public void testInitialState() {
        assertFalse(game.isGameOver());
        assertEquals(0, game.getScore());
        
        int[][] board = game.getBoardSnapshot();
        int nonEmpty = 0;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                if (board[y][x] != 0) nonEmpty++;
            }
        }
        assertEquals(2, nonEmpty);
    }

    @Test
    public void testGetTile() {
        int[][] board = game.getBoardSnapshot();
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                assertEquals(board[y][x], game.getTile(x, y));
            }
        }
    }

    @Test
    public void testMoveLeft() {
        int[][] oldBoard = game.getBoardSnapshot();
        game.moveLeft();
        int[][] newBoard = game.getBoardSnapshot();
        
        boolean changed = false;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                if (oldBoard[y][x] != newBoard[y][x]) {
                    changed = true;
                    break;
                }
            }
        }
    }

    @Test
    public void testMoveRight() {
        game.moveRight();
        int[][] board = game.getBoardSnapshot();
        assertNotNull(board);
        assertEquals(4, board.length);
    }

    @Test
    public void testMoveUp() {
        game.moveUp();
        int[][] board = game.getBoardSnapshot();
        assertNotNull(board);
        assertEquals(4, board.length);
    }

    @Test
    public void testMoveDown() {
        game.moveDown();
        int[][] board = game.getBoardSnapshot();
        assertNotNull(board);
        assertEquals(4, board.length);
    }

    @Test
    public void testMergeIncreasesScore() {
        int[][] savedBoard = {
            {2, 2, 0, 0},
            {0, 0, 0, 0},
            {0, 0, 0, 0},
            {0, 0, 0, 0}
        };
        game.restoreState(savedBoard, 0, false);
        
        game.moveLeft();
        assertTrue(game.getScore() > 0);
    }

    @Test
    public void testReset() {
        game.moveLeft();
        game.moveUp();
        game.reset();
        
        assertFalse(game.isGameOver());
        assertEquals(0, game.getScore());
        
        int[][] board = game.getBoardSnapshot();
        int nonEmpty = 0;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                if (board[y][x] != 0) nonEmpty++;
            }
        }
        assertEquals(2, nonEmpty);
    }

    @Test
    public void testGetBoardSnapshotReturnsCopy() {
        int[][] board1 = game.getBoardSnapshot();
        int[][] board2 = game.getBoardSnapshot();
        
        assertNotSame(board1, board2);
        assertArrayEquals(board1, board2);
    }
}
