package com.gamecenter.app.td;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.gamecenter.app.td.engine.TdTowerProgression;
import com.gamecenter.app.td.engine.TowerType;

import org.junit.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

/** 主线塔牌进度必须可预测，且不能因重复结算反复发放解锁。 */
public class TdTowerProgressionTest {

    @Test
    public void firstLevel_onlyExposesStarterTeachingDeck() {
        assertEquals(Arrays.asList(TowerType.BOTTLE, TowerType.SUN, TowerType.SNOW),
                TdTowerProgression.availableForUnlockedLevelCount(1));
        assertTrue(TdTowerProgression.isUnlocked(TowerType.BOTTLE, 1));
        assertFalse(TdTowerProgression.isUnlocked(TowerType.FAN, 1));
        assertEquals("通关第 1 关解锁", TdTowerProgression.unlockRequirement(TowerType.FAN));
    }

    @Test
    public void progression_unlocksSmallTeachingBatchesAtEachMilestone() {
        assertEquals(Arrays.asList(TowerType.FAN, TowerType.ROCKET),
                TdTowerProgression.newlyUnlockedBetween(1, 2));
        assertEquals(Arrays.asList(TowerType.POISON, TowerType.LIGHTNING),
                TdTowerProgression.newlyUnlockedBetween(2, 3));
        assertEquals(Arrays.asList(TowerType.SNIPER, TowerType.MINE),
                TdTowerProgression.newlyUnlockedBetween(3, 4));
        assertEquals(Arrays.asList(TowerType.AMPLIFIER),
                TdTowerProgression.newlyUnlockedBetween(4, 5));
    }

    @Test
    public void repeatOrOutOfOrderProgressCannotDuplicateTowerUnlocks() {
        assertTrue(TdTowerProgression.newlyUnlockedBetween(3, 3).isEmpty());
        assertTrue(TdTowerProgression.newlyUnlockedBetween(4, 3).isEmpty());

        List<TowerType> allTowers = TdTowerProgression.availableForUnlockedLevelCount(99);
        assertEquals(EnumSet.allOf(TowerType.class), EnumSet.copyOf(allTowers));
        assertEquals(TowerType.values().length, allTowers.size());
    }
}
