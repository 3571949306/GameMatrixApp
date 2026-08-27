package com.gamecenter.app.games.gomoku;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * #3 回归：五子棋 VCF（大师档连续冲四算杀）全应手校验。
 *
 * 修复内容：vcfSearch 对手防守从"评分前 5 手"改为"全部候选防守着法"，
 * 全部失败才算必胜，避免启发式截断把有解局面误判为必胜。
 * 这里的测试守护该代码路径不回归：真实必胜仍能走出、宽局面全扫描不崩溃非法。
 */
public class GomokuAIVcfTest {

    /** 真实必胜局面（闭合四 + 唯一连五点）仍会被大师档找到。 */
    @Test
    public void masterAiFindsClosedFourWin() {
        GomokuGame game = new GomokuGame();
        game.reset();
        // 黑方（AI）在 row=9 形成闭合四：col 5..8，(9,4) 被白档住；(9,9) 是唯一连五着法
        for (int c = 5; c <= 8; c++) {
            game.makeMove(c, 9, GomokuGame.BLACK);
        }
        game.makeMove(4, 9, GomokuGame.WHITE);

        GomokuAI ai = new GomokuAI(4); // 大师档：vcfEnabled = true
        int[] move = ai.getBestMove(game, GomokuGame.BLACK);
        assertNotNull("大师档应能走出制胜手", move);
        assertEquals("制胜手列", 9, move[0]);
        assertEquals("制胜手行", 9, move[1]);
    }

    /** 防守候选众多的开启局面：全应手扫描不崩溃、不返回非法着法。 */
    @Test
    public void masterAiVcfAllDefenseScanIsStable() {
        GomokuGame game = new GomokuGame();
        game.reset();
        // 三个分散棋簇制造大量候选防守点（覆盖原先"前 5 手"截断的分支）
        int[][] blackStones = {{3, 3}, {4, 4}, {3, 4}, {4, 3}, {11, 3}, {11, 4}, {11, 2}};
        int[][] whiteStones = {{6, 7}, {7, 7}, {6, 8}, {10, 11}, {11, 11}, {10, 10}};
        for (int[] s : blackStones) game.makeMove(s[0], s[1], GomokuGame.BLACK);
        for (int[] s : whiteStones) game.makeMove(s[0], s[1], GomokuGame.WHITE);

        GomokuAI ai = new GomokuAI(4);
        int[] move = ai.getBestMove(game, GomokuGame.BLACK);
        assertNotNull("大师档在开放局面应能走出着法", move);
        assertTrue(move[0] >= 0 && move[0] < 15);
        assertTrue(move[1] >= 0 && move[1] < 15);
        assertTrue("着法必须合法", game.isValidMove(move[0], move[1]));
    }
}