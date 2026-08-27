package com.gamecenter.app.games.go;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class GoAITest {

    private GoGame game;
    private GoAI ai;

    @Before
    public void setUp() {
        game = new GoGame();
        game.startNewGame();
        ai = new GoAI();
    }

    @Test
    public void testSetDifficulty() {
        ai.setDifficulty(3);
        assertEquals(3, ai.getDifficulty());

        // Invalid difficulty should be ignored
        ai.setDifficulty(5);
        assertEquals(3, ai.getDifficulty());
    }

    @Test
    public void testRandomAiMove() {
        ai.setDifficulty(1); // Easy
        game.playMove(4, 4); // Black plays
        
        int[] move = ai.findBestAiMove(game); // White (AI) plays
        // Random AI might pass if random hits, but for first move usually not null.
        if (move != null) {
            assertEquals(2, move.length);
            assertTrue(move[0] >= 0 && move[0] < GoGame.BOARD_SIZE);
            assertTrue(move[1] >= 0 && move[1] < GoGame.BOARD_SIZE);
            // Shouldn't pick an occupied spot
            assertTrue(move[0] != 4 || move[1] != 4);
        }
    }

    @Test
    public void testGreedyAiMove() {
        ai.setDifficulty(2); // Normal
        game.playMove(4, 4);
        int[] move = ai.findBestAiMove(game);
        if (move != null) {
            assertEquals(2, move.length);
        }
    }

    @Test
    public void testMinimaxAiMove() {
        ai.setDifficulty(3); // Hard
        game.playMove(4, 4);
        int[] move = ai.findBestAiMove(game);
        if (move != null) {
            assertEquals(2, move.length);
        }
    }

    @Test
    public void testMctsAiMove() {
        ai.setDifficulty(4); // Master
        game.playMove(4, 4);
        
        int[] move = ai.findBestAiMove(game);

        // 验证算法契约，不依赖机器速度或墙钟精度；快速环境中合法计算可能在 0ms 内完成。
        assertNotNull(move);
        assertEquals(2, move.length);
        assertTrue(move[0] >= 0 && move[0] < GoGame.BOARD_SIZE);
        assertTrue(move[1] >= 0 && move[1] < GoGame.BOARD_SIZE);
        assertTrue(game.isValidMove(move[0], move[1], GoGame.WHITE));
    }

    /** 构造经典单子劫局面：白(3,3)刚被黑在(3,4)提取（劫点=3,3）。 */
    private GoGame buildKoGame() {
        final int W = GoGame.WHITE, B = GoGame.BLACK;
        int[][] board = new int[GoGame.BOARD_SIZE][GoGame.BOARD_SIZE];
        int[][] previous = new int[GoGame.BOARD_SIZE][GoGame.BOARD_SIZE];
        int[][] blackStones = {{2, 3}, {4, 3}, {3, 2}, {3, 4}, {2, 4}, {4, 4}, {3, 5}};
        for (int[] s : blackStones) board[s[0]][s[1]] = B;
        // previousBoard = 劫提取前：白(3,3)还在、黑(3,4)未落
        for (int[] s : blackStones) {
            if (s[0] == 3 && s[1] == 4) continue;
            previous[s[0]][s[1]] = B;
        }
        previous[3][3] = W;
        // 回提(3,3)按提子规则能提回黑(3,4)，但会复现上一手前局面 → 简单劫禁手
        assertFalse("测试前提：回提劫点必须被判非法（simple ko）",
                GoGame.isValidMove(board, previous, 3, 3, W));

        GoGame game = new GoGame();
        game.restoreState(board, previous, W, 0, 0, 0, false);
        return game;
    }

    /** #2 回归守卫：普通档在简单劫局面不得回提劫点。 */
    @Test
    public void testTacticalAiNeverRecapturesKo() {
        ai.setDifficulty(2);
        int[] move = ai.findBestAiMove(buildKoGame());
        if (move != null) {
            assertFalse("AI 不得回提劫点", move[0] == 3 && move[1] == 3);
            assertTrue("AI 着法必须通过官方合法校验",
                    aiClonedIsValid(move));
        }
    }

    /** #2 回归守卫：MCTS 档（含 playout）在简单劫局面不得回提劫点。 */
    @Test
    public void testMctsAiNeverRecapturesKo() {
        ai.configureSearchForTests(40, 60, 50, 6); // 固定小预算，测试快速且确定
        ai.setDifficulty(4);
        int[] move = ai.findBestAiMove(buildKoGame());
        assertNotNull("MCTS 档在开放局面应能走出着法", move);
        assertFalse("MCTS 不得回提劫点", move[0] == 3 && move[1] == 3);
        assertTrue("MCTS 着法必须通过官方合法校验",
                aiClonedIsValid(move));
    }

    private boolean aiClonedIsValid(int[] move) {
        GoGame probe = new GoGame();
        probe.restoreState(buildKoBoard(), buildKoPrevious(), GoGame.WHITE, 0, 0, 0, false);
        return probe.isValidMove(move[0], move[1], GoGame.WHITE);
    }

    private int[][] buildKoBoard() {
        int[][] board = new int[GoGame.BOARD_SIZE][GoGame.BOARD_SIZE];
        int[][] blackStones = {{2, 3}, {4, 3}, {3, 2}, {3, 4}, {2, 4}, {4, 4}, {3, 5}};
        for (int[] s : blackStones) board[s[0]][s[1]] = GoGame.BLACK;
        return board;
    }

    private int[][] buildKoPrevious() {
        int[][] previous = new int[GoGame.BOARD_SIZE][GoGame.BOARD_SIZE];
        int[][] blackStones = {{2, 3}, {4, 3}, {3, 2}, {2, 4}, {4, 4}, {3, 5}};
        for (int[] s : blackStones) previous[s[0]][s[1]] = GoGame.BLACK;
        previous[3][3] = GoGame.WHITE;
        return previous;
    }
}
