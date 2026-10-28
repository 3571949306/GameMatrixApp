package com.gamecenter.app.game2048;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 2048 难度模式、Undo 撤回与状态逻辑单元测试。
 */
public class Game2048GameTest {

    @Test
    public void testDifficultySettingsAndClamping() {
        Game2048Game game = new Game2048Game();
        assertEquals(Game2048Game.DIFFICULTY_NORMAL, game.getDifficulty());

        game.setDifficulty(Game2048Game.DIFFICULTY_EASY);
        assertEquals(Game2048Game.DIFFICULTY_EASY, game.getDifficulty());

        game.setDifficulty(Game2048Game.DIFFICULTY_HARD);
        assertEquals(Game2048Game.DIFFICULTY_HARD, game.getDifficulty());

        game.setDifficulty(-5);
        assertEquals(Game2048Game.DIFFICULTY_EASY, game.getDifficulty());

        game.setDifficulty(99);
        assertEquals(Game2048Game.DIFFICULTY_HARD, game.getDifficulty());
    }

    @Test
    public void testUndoAfterMove() {
        Game2048Game game = new Game2048Game();
        int[][] initialBoard = {
                {2, 2, 0, 0},
                {0, 0, 0, 0},
                {0, 0, 0, 0},
                {0, 0, 0, 0}
        };
        game.restoreState(initialBoard, 0, false);
        assertFalse(game.canUndo());

        // 向左滑动：2+2 合并为 4，得分 +4
        game.moveLeft();
        assertEquals(4, game.getScore());
        assertEquals(4, game.getTile(0, 0));
        assertTrue(game.canUndo());

        // 撤回一步
        boolean undoSuccess = game.undo();
        assertTrue(undoSuccess);
        assertEquals(0, game.getScore());
        assertEquals(2, game.getTile(0, 0));
        assertEquals(2, game.getTile(1, 0));
        assertFalse(game.canUndo()); // 不可重复撤回同一状态
    }

    @Test
    public void testMaxTileAndWinDetection() {
        Game2048Game game = new Game2048Game();
        int[][] board = {
                {2, 4, 8, 16},
                {32, 64, 128, 256},
                {512, 1024, 0, 0},
                {0, 0, 0, 0}
        };
        game.restoreState(board, 1000, false);
        assertEquals(1024, game.getMaxTile());
        assertFalse(game.hasWon());

        board[2][2] = 2048;
        game.restoreState(board, 3000, false);
        assertEquals(2048, game.getMaxTile());
        assertTrue(game.hasWon());
    }
}
