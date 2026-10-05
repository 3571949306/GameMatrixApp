package com.gamecenter.app.games.achievement;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 批次 3 回归：每日挑战"赢 N 局"（TYPE_WIN_ROUNDS）候选池不得包含
 * 已摘出商店目录（清单级 enabled=false 隐藏）的 5 个 demo 游戏，
 * 否则挑战绑定到不可游玩的游戏即成为死任务。
 */
public class DailyChallengeWinningPoolTest {

    /** 2026-10-05 批次 3 隐藏的 10 个 demo 游戏中带胜负判定的 5 个。 */
    private static final String[] HIDDEN_GAME_IDS = {
            "blackjack", "reaction", "rock", "tic", "whack"
    };

    /** 隐藏后仍应保留的可胜利游戏（共 8 个，满足每日轮换）。 */
    private static final String[] EXPECTED_GAME_IDS = {
            "brotato", "chinesechess", "doudizhu", "gomoku",
            "minesweeper", "pipeline", "sokoban", "td"
    };

    @Test
    public void winningPoolExcludesHiddenDemoGames() {
        for (String id : HIDDEN_GAME_IDS) {
            assertFalse("隐藏游戏不应出现在每日挑战候选池: " + id,
                    DailyChallengeManager.WINNABLE_GAME_IDS.contains(id));
        }
    }

    @Test
    public void winningPoolKeepsAllRotatableWinnableGames() {
        for (String id : EXPECTED_GAME_IDS) {
            assertTrue("可胜利游戏应保留在每日挑战候选池: " + id,
                    DailyChallengeManager.WINNABLE_GAME_IDS.contains(id));
        }
    }
}
