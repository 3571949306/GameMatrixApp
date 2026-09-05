package com.gamecenter.app.td;

import com.gamecenter.app.td.engine.TdGame;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;

/** Pure contract tests for stable campaign identifiers (no Android runtime required). */
public class TdSaveManagerTest {
    @Test public void legacyIndexesMapToStableIds() {
        assertEquals("main_001", TdSaveManager.levelIdForIndex(0));
        assertEquals("main_005", TdSaveManager.levelIdForIndex(4));
        assertNull(TdSaveManager.levelIdForIndex(-1));
        assertNull(TdSaveManager.levelIdForIndex(5));
    }

    @Test public void onlyKnownIdsAreAccepted() {
        assertTrue(TdSaveManager.isValidLevelId("main_001"));
        assertTrue(TdSaveManager.isValidLevelId("main_005"));
        assertTrue("后续章节无需更新存档代码", TdSaveManager.isValidLevelId("main_006"));
        assertFalse(TdSaveManager.isValidLevelId("main_000"));
        assertFalse(TdSaveManager.isValidLevelId("main_1000"));
        assertFalse(TdSaveManager.isValidLevelId("other_001"));
        assertFalse(TdSaveManager.isValidLevelId(null));
    }

    // ===== 无尽模式最佳波数（阶段 1：引擎与存档） =====

    @Test public void endlessBestKeysArePerDifficultyAndNullDefensive() {
        assertEquals("td_endless_best_easy", TdSaveManager.bestEndlessKey(TdGame.Difficulty.EASY));
        assertEquals("td_endless_best_normal", TdSaveManager.bestEndlessKey(TdGame.Difficulty.NORMAL));
        assertEquals("td_endless_best_hard", TdSaveManager.bestEndlessKey(TdGame.Difficulty.HARD));
        Set<String> keys = new HashSet<>();
        keys.add(TdSaveManager.bestEndlessKey(TdGame.Difficulty.EASY));
        keys.add(TdSaveManager.bestEndlessKey(TdGame.Difficulty.NORMAL));
        keys.add(TdSaveManager.bestEndlessKey(TdGame.Difficulty.HARD));
        assertEquals("三个难度必须使用互不相同的键，天然按难度隔离", 3, keys.size());
        assertNull("null 难度必须返回 null 键（调用方防御性忽略）",
                TdSaveManager.bestEndlessKey(null));
    }

    @Test public void endlessBestMergeOnlyIncreasesAndIgnoresInvalid() {
        // 首次记录
        assertEquals(5, TdSaveManager.mergedBestEndlessWaves(0, 5));
        // 只增不减：更低波数不得回退
        assertEquals(7, TdSaveManager.mergedBestEndlessWaves(7, 5));
        assertEquals(7, TdSaveManager.mergedBestEndlessWaves(7, 7));
        // 非法输入（0/负数）一律忽略
        assertEquals(7, TdSaveManager.mergedBestEndlessWaves(7, 0));
        assertEquals(7, TdSaveManager.mergedBestEndlessWaves(7, -3));
        assertEquals("无历史且非法输入必须仍为 0", 0, TdSaveManager.mergedBestEndlessWaves(0, -3));
    }
}
