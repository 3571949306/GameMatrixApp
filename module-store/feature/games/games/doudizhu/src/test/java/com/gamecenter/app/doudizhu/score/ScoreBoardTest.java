package com.gamecenter.app.doudizhu.score;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * ScoreBoard 单测：倍数叠加（叫分/炸弹/春天）、结算口径（零和）、明细值对象。
 */
public class ScoreBoardTest {

    @Test
    public void defaultMultiplierIsBidOne() {
        ScoreBoard board = new ScoreBoard();
        assertEquals(1, board.totalMultiplier());
    }

    @Test
    public void bidScoreClampedToValidRange() {
        ScoreBoard board = new ScoreBoard();
        board.setBidScore(3);
        assertEquals(3, board.getBidScore());
        board.setBidScore(9);
        assertEquals("超出上限收敛到 3", 3, board.getBidScore());
        board.setBidScore(0);
        assertEquals("低于下限收敛到 1", 1, board.getBidScore());
        assertEquals(1, board.totalMultiplier());
    }

    @Test
    public void bombDoublesMultiplierEachTime() {
        ScoreBoard board = new ScoreBoard();
        board.setBidScore(2);
        board.registerBomb();
        assertEquals(4, board.totalMultiplier());
        board.registerBomb();
        assertEquals("两个炸弹再 ×4", 8, board.totalMultiplier());
        board.registerBomb();
        assertEquals(16, board.totalMultiplier());
    }

    @Test
    public void springAndAntiSpringEachDoubleMultiplier() {
        ScoreBoard board = new ScoreBoard();
        board.setBidScore(2);
        board.applySpring(true, false);
        assertEquals("春天 ×2", 4, board.totalMultiplier());
        assertFalse(board.isAntiSpring());
        assertTrue(board.isSpring());

        ScoreBoard anti = new ScoreBoard();
        anti.setBidScore(3);
        anti.applySpring(false, true);
        assertEquals("反春 ×2", 6, anti.totalMultiplier());
        assertTrue(anti.isAntiSpring());
        assertFalse(anti.isSpring());
    }

    @Test
    public void fullFormulaBidTimesBombsTimesSpring() {
        ScoreBoard board = new ScoreBoard();
        board.setBidScore(3);
        board.registerBomb();
        board.registerBomb();
        board.applySpring(false, true);
        assertEquals(3 * 4 * 2, board.totalMultiplier());
    }

    @Test
    public void settleHumanDeltaFarmerSide() {
        ScoreBoard board = new ScoreBoard();
        board.setBidScore(2);
        // 农民视角结算：地主胜 → 农民输 -200；地主负 → 农民赢 +200
        // （地主份额为双倍：两名农民各 ±200，地主 ±400，整局零和）
        assertEquals(-200, board.settleHumanDelta(false, true));
        assertEquals(200, board.settleHumanDelta(false, false));
    }

    @Test
    public void settleHumanDeltaLandlordDoubleStake() {
        ScoreBoard board = new ScoreBoard();
        board.setBidScore(1);
        board.registerBomb();
        assertEquals("地主赢：+2×100×2", 400, board.settleHumanDelta(true, true));
        assertEquals("地主输：-2×100×2", -400, board.settleHumanDelta(true, false));
    }

    @Test
    public void settleSnapshotCarriesAllDetails() {
        ScoreBoard board = new ScoreBoard();
        board.setBidScore(3);
        board.registerBomb();
        board.applySpring(true, false);
        ScoreBoard.Settlement s = board.settle(0, true, true, 95500);

        assertTrue(s.humanWon());
        assertTrue(s.landlordWon);
        assertTrue(s.spring);
        assertEquals(1, s.bombCount);
        assertEquals(12, s.multiplier);
        assertEquals("地主赢按双倍份额：2×100×12", 2400, s.humanScoreDelta);
        assertEquals(0, s.landlordSeat);
        assertEquals("1:35", s.formatDuration());
    }

    @Test
    public void settlementDurationFormatting() {
        ScoreBoard board = new ScoreBoard();
        ScoreBoard.Settlement s = board.settle(1, false, false, 6500);
        assertEquals("0:06", s.formatDuration());
        ScoreBoard.Settlement s2 = board.settle(1, false, false, 60_000);
        assertEquals("1:00", s2.formatDuration());
        ScoreBoard.Settlement s3 = board.settle(1, false, false, 610_000);
        assertEquals("10:10", s3.formatDuration());
    }
}
