package com.gamecenter.app.td;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
}
