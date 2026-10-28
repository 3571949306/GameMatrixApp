package com.gamecenter.app.checkers;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 跳棋核心逻辑与现代化能力单元测试。
 */
public class CheckersGameTest {

    private CheckersGame game;

    @Before
    public void setUp() {
        game = new CheckersGame();
        game.startNewGame(1);
    }

    @Test
    public void testInitialBoardSetup() {
        assertEquals(12, game.getBlackCount());
        assertEquals(12, game.getWhiteCount());
        assertEquals(0, game.getBlackKingCount());
        assertEquals(0, game.getWhiteKingCount());
        assertTrue(game.isPlayerTurn());
        assertFalse(game.isGameOver());
        assertEquals(0, game.getPlayerCaptures());
    }

    @Test
    public void testPlayerMoveAndUndo() {
        int[][] board = game.getBoard();
        // 初始状态黑棋在第5,6,7行
        // 寻找一个合法的初始黑棋移动
        int fromR = 5;
        int fromC = -1;
        boolean[][] moves = null;
        for (int c = 0; c < CheckersGame.BOARD_SIZE; c++) {
            if (board[fromR][c] == CheckersGame.BLACK) {
                moves = game.calculateValidMoves(fromR, c);
                for (int tc = 0; tc < CheckersGame.BOARD_SIZE; tc++) {
                    if (moves[4][tc]) {
                        fromC = c;
                        break;
                    }
                }
                if (fromC >= 0) break;
            }
        }
        assertTrue("应至少找到一个合法的黑棋移动", fromC >= 0);

        // 记录快照并移动
        game.recordSnapshot();
        assertTrue(game.canUndo());
        int targetC = -1;
        for (int tc = 0; tc < CheckersGame.BOARD_SIZE; tc++) {
            if (moves[4][tc]) {
                targetC = tc;
                break;
            }
        }

        boolean ok = game.performPlayerMove(fromR, fromC, 4, targetC);
        assertTrue(ok);
        assertEquals(CheckersGame.EMPTY, game.getBoard()[fromR][fromC]);
        assertEquals(CheckersGame.BLACK, game.getBoard()[4][targetC]);

        // 测试悔棋
        assertTrue(game.undo());
        assertEquals(CheckersGame.BLACK, game.getBoard()[fromR][fromC]);
        assertEquals(CheckersGame.EMPTY, game.getBoard()[4][targetC]);
        assertFalse(game.canUndo());
    }

    @Test
    public void testAiMoveGeneratesValidMove() {
        int[] aiMove = game.getAiMove();
        assertNotNull("AI 初始状态应有可用走法", aiMove);
        assertEquals(4, aiMove.length);
        // 白棋从行 2 走向行 3
        assertEquals(2, aiMove[0]);
        assertEquals(3, aiMove[2]);
    }

    @Test
    public void testPlayerHintGeneratesMove() {
        int[] hint = game.getPlayerHint();
        assertNotNull("玩家初始状态应有可用推荐走法", hint);
        assertEquals(4, hint.length);
        assertEquals(5, hint[0]);
        assertEquals(4, hint[2]);
    }

    @Test
    public void testDifficultySettings() {
        game.startNewGame(0);
        assertEquals(0, game.getAiLevel());
        game.startNewGame(2);
        assertEquals(2, game.getAiLevel());
    }

    @Test
    public void testKingPromotion() {
        // 创建快到升王线的情况
        int[][] board = game.getBoard();
        // 清空棋盘
        for (int r = 0; r < CheckersGame.BOARD_SIZE; r++) {
            for (int c = 0; c < CheckersGame.BOARD_SIZE; c++) {
                board[r][c] = CheckersGame.EMPTY;
            }
        }
        // 黑子在 row 1, col 2，向前移动到 row 0, col 1 应升王
        board[1][2] = CheckersGame.BLACK;
        game.performPlayerMove(1, 2, 0, 1);
        assertEquals(CheckersGame.BLACK_KING, board[0][1]);
        assertEquals(1, game.getBlackKingCount());
    }
}
