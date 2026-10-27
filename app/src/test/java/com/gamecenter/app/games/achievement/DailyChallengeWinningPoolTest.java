package com.gamecenter.app.games.achievement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * 每日挑战"赢 N 局"（TYPE_WIN_ROUNDS）候选池契约测试。
 * 必须包含所有带胜负判定的 13 款游戏，且不得包含无尽/无胜负判定游戏（避免死任务）。
 */
public class DailyChallengeWinningPoolTest {

    /** 具备胜负判定的全部 13 款游戏。 */
    private static final String[] WINNABLE_GAME_IDS = {
            "blackjack", "brotato", "chinesechess", "doudizhu", "gomoku",
            "minesweeper", "pipeline", "reaction", "rock", "sokoban", "td",
            "tic", "whack"
    };

    /** 无尽类游戏（终局不产生胜利记录，不得进入"赢 N 局"候选池）。 */
    private static final String[] ENDLESS_GAME_IDS = {
            "tetris", "snake", "flappy", "game_2048", "plane"
    };

    @Test
    public void winningPoolContainsAllWinnableGames() {
        assertEquals("可胜利游戏总数应为 13", WINNABLE_GAME_IDS.length, DailyChallengeManager.WINNABLE_GAME_IDS.size());
        for (String id : WINNABLE_GAME_IDS) {
            assertTrue("带胜负判定游戏应在候选池中: " + id,
                    DailyChallengeManager.WINNABLE_GAME_IDS.contains(id));
        }
    }

    @Test
    public void winningPoolExcludesEndlessGames() {
        for (String id : ENDLESS_GAME_IDS) {
            assertFalse("无尽类游戏不得出现在赢 N 局候选池: " + id,
                    DailyChallengeManager.WINNABLE_GAME_IDS.contains(id));
        }
    }
}
