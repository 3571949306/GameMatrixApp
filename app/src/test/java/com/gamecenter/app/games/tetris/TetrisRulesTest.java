package com.gamecenter.app.games.tetris;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * TetrisRules 纯计分规则单元测试（无 Android 依赖）。
 * 覆盖：基础分、Combo 奖励、Perfect Clear 奖励、等级乘子叠加。
 * 注意：Back-to-Back 1.5× 已在 TetrisView 预先并入 baseScore，此处不重复验证。
 */
public class TetrisRulesTest {

    @Test
    public void singleLine_noCombo_noPC_level1() {
        // 单行基础分 100，level1，无 combo / PC
        assertEquals(100, TetrisRules.score(100, 1, 1, false, 1));
    }

    @Test
    public void tetris_noCombo_noPC_level1() {
        // Tetris 基础分 800
        assertEquals(800, TetrisRules.score(800, 1, 1, false, 4));
    }

    @Test
    public void tetris_b2bAppliedToBaseScore_level1() {
        // B2B 已在调用方把 800 -> 1200，此处 level1
        assertEquals(1200, TetrisRules.score(1200, 1, 1, false, 4));
    }

    @Test
    public void doubleLine_withCombo_level1() {
        // Double 基础 300 + Combo x2 (50 * (2-1) * 1 = 50) = 350
        assertEquals(350, TetrisRules.score(300, 2, 1, false, 2));
    }

    @Test
    public void doubleLine_withCombo_level3() {
        // Double 300 * 3 = 900 + Combo x2 (50 * 1 * 3 = 150) = 1050
        assertEquals(1050, TetrisRules.score(300, 2, 3, false, 2));
    }

    @Test
    public void tripleLine_combo3_level2() {
        // Triple 500 * 2 = 1000 + Combo x3 (50 * 2 * 2 = 200) = 1200
        assertEquals(1200, TetrisRules.score(500, 3, 2, false, 3));
    }

    @Test
    public void singleLine_perfectClear_level1() {
        // 单行 100 + PC bonus 800 * 1 = 900
        assertEquals(900, TetrisRules.score(100, 1, 1, true, 1));
    }

    @Test
    public void tetris_perfectClear_level1() {
        // Tetris 800 + PC bonus 2000 * 1 = 2800
        assertEquals(2800, TetrisRules.score(800, 1, 1, true, 4));
    }

    @Test
    public void tetris_perfectClear_level5() {
        // (800 + 2000) * 5 = 14000
        assertEquals(14000, TetrisRules.score(800, 1, 5, true, 4));
    }

    @Test
    public void levelScalesBaseScore() {
        assertEquals(500, TetrisRules.score(100, 1, 5, false, 1));
    }
}
