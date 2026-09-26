package com.gamecenter.app.doudizhu.score;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * SpringDetector 单测：春天/反春天判定与边界。
 */
public class SpringDetectorTest {

    @Test
    public void springWhenLandlordWinsAndFarmersNeverPlayed() {
        // 座位 0 为地主获胜，农民 1/2 均未出牌（地主首发 1 手 + 后续自由出牌）
        int[] plays = {4, 0, 0};
        assertTrue(SpringDetector.isSpring(0, 0, plays));
        assertFalse(SpringDetector.isAntiSpring(0, 0, plays));
    }

    @Test
    public void noSpringWhenAnyFarmerPlayed() {
        int[] plays = {4, 1, 0};
        assertFalse("任一农民出过牌即非春天", SpringDetector.isSpring(0, 0, plays));
        int[] plays2 = {4, 0, 1};
        assertFalse(SpringDetector.isSpring(0, 0, plays2));
    }

    @Test
    public void antiSpringWhenFarmersWinAndLandlordOnlyLedOnce() {
        // 地主 0 只出了首发一手，农民 1 获胜
        int[] plays = {1, 2, 0};
        assertTrue(SpringDetector.isAntiSpring(1, 0, plays));
        assertFalse(SpringDetector.isSpring(1, 0, plays));
    }

    @Test
    public void noAntiSpringWhenLandlordPlayedMoreThanOnce() {
        int[] plays = {2, 2, 0};
        assertFalse(SpringDetector.isAntiSpring(1, 0, plays));
    }

    @Test
    public void noSpringWhenFarmersWinEvenIfTheyNeverResponded() {
        // 农民获胜但地主出过 2 手 → 双向都不成立
        int[] plays = {2, 1, 0};
        assertFalse(SpringDetector.isSpring(1, 0, plays));
        assertFalse(SpringDetector.isAntiSpring(1, 0, plays));
    }

    @Test
    public void landlordWinWithFarmerPlaysIsPlainWin() {
        int[] plays = {3, 2, 1};
        assertFalse(SpringDetector.isSpring(0, 0, plays));
        assertFalse(SpringDetector.isAntiSpring(0, 0, plays));
    }

    @Test
    public void invalidInputsReturnFalse() {
        int[] plays = {0, 0, 0};
        assertFalse("非法赢家", SpringDetector.isSpring(-1, 0, plays));
        assertFalse("非法地主", SpringDetector.isAntiSpring(0, 3, plays));
        assertFalse("数据长度不足", SpringDetector.isSpring(0, 0, new int[]{0, 0}));
        assertFalse("null 数据", SpringDetector.isSpring(0, 0, null));
    }
}
