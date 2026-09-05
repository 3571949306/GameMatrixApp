package com.gamecenter.app.td;

import android.content.SharedPreferences;

import com.gamecenter.app.td.engine.TdGame;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

    // ===== 战绩/成就面板：聚合与实例语义（经包内测试接缝注入 SharedPreferences） =====

    @Test public void sumBestStarsHandlesEmptyFullAndDirtyInput() {
        assertEquals("空存档求和必须为 0", 0, TdSaveManager.sumBestStars(new int[0]));
        assertEquals("null 输入防御为 0", 0, TdSaveManager.sumBestStars(null));
        int[] full = new int[25];
        Arrays.fill(full, 3);
        assertEquals("满档 25 关 × 3 星必须为 75", 75, TdSaveManager.sumBestStars(full));
        assertEquals("脏数据（负星）按 0 计，不得产生负总和", 7,
                TdSaveManager.sumBestStars(new int[] {3, -2, 4}));
    }

    @Test public void endlessBestReadsBackPerDifficultyAndNeverDecreases() {
        MemoryPrefs prefs = new MemoryPrefs();
        TdSaveManager save = new TdSaveManager(prefs);
        save.recordEndlessWaves(TdGame.Difficulty.EASY, 5);
        save.recordEndlessWaves(TdGame.Difficulty.NORMAL, 7);
        save.recordEndlessWaves(TdGame.Difficulty.HARD, 3);
        assertEquals(5, save.getBestEndlessWaves(TdGame.Difficulty.EASY));
        assertEquals(7, save.getBestEndlessWaves(TdGame.Difficulty.NORMAL));
        assertEquals(3, save.getBestEndlessWaves(TdGame.Difficulty.HARD));
        // 只增不减：模拟重开进程后的新实例读到同一份纪录，且更低波数不得回退
        TdSaveManager reopened = new TdSaveManager(prefs);
        reopened.recordEndlessWaves(TdGame.Difficulty.NORMAL, 4);
        assertEquals(7, reopened.getBestEndlessWaves(TdGame.Difficulty.NORMAL));
    }

    @Test public void fullCampaignStarsSumToSeventyFiveAndNeverDecrease() {
        MemoryPrefs prefs = new MemoryPrefs();
        TdSaveManager save = new TdSaveManager(prefs);
        int[] stars = new int[25];
        for (int i = 0; i < stars.length; i++) {
            String id = String.format(java.util.Locale.US, "main_%03d", i + 1);
            save.setBestStars(id, 3);
            stars[i] = save.getBestStars(id);
        }
        assertEquals("满档累计星级必须为 75", 75, TdSaveManager.sumBestStars(stars));
        // 只增不减：更低的星级不得覆盖既有最高星
        save.setBestStars("main_001", 2);
        assertEquals(3, save.getBestStars("main_001"));
    }

    @Test public void clearAllResetsEveryStatToZero() {
        MemoryPrefs prefs = new MemoryPrefs();
        TdSaveManager save = new TdSaveManager(prefs);
        save.recordWin(4);
        save.setBestStars("main_001", 3);
        save.addKills(123);
        save.recordPlay();
        save.recordPlay();
        save.setBestTimeSec("main_001", 95);
        save.setEasyCleared(true);
        save.setHardCleared(true);
        save.recordEndlessWaves(TdGame.Difficulty.HARD, 11);
        assertTrue(save.getUnlockedLevelCount() > 1);
        assertTrue(save.getTotalKills() > 0);
        assertTrue(save.getPlayCount() > 0);
        assertTrue(save.getBestStars("main_001") > 0);
        assertTrue(save.getBestTimeSec("main_001") > 0);
        assertTrue(save.isEasyCleared());
        assertTrue(save.isHardCleared());
        assertTrue(save.getBestEndlessWaves(TdGame.Difficulty.HARD) > 0);

        save.clearAll();

        assertEquals("重置后解锁关数回到默认第 1 关", 1, save.getUnlockedLevelCount());
        assertEquals(0, save.getTotalKills());
        assertEquals(0, save.getPlayCount());
        assertEquals(0, save.getBestStars("main_001"));
        assertEquals(0, save.getBestTimeSec("main_001"));
        assertFalse(save.isEasyCleared());
        assertFalse(save.isHardCleared());
        assertEquals(0, save.getBestEndlessWaves(TdGame.Difficulty.HARD));
        int[] stars = new int[25];
        for (int i = 0; i < stars.length; i++) {
            stars[i] = save.getBestStars(String.format(java.util.Locale.US, "main_%03d", i + 1));
        }
        assertEquals("重置后全战役星级求和必须为 0", 0, TdSaveManager.sumBestStars(stars));
    }

    /**
     * 回归测试：clearAll 不得解除迁移守卫。
     * ModuleScopedPreferences.migrateFrom 从不删除旧扁平 td_save，且每次 onCreateView
     * 构造 TdSaveManager 时重跑；若 clear 把守卫旗标清掉，旧扁平存档会被无条件回灌，
     * 升级用户清空战绩后重进模块即整体复活。键用真实字面量断言——
     * "__migrated__" 与 core/common ModuleScopedPreferences.MIGRATE_FLAG 保持一致
     * （该常量为 private，无法直接引用），"td_level_id_migrated_v1" 为本类 KEY_ID_MIGRATED。
     */
    @Test public void clearAllKeepsMigrationGuardFlagsSoOldFlatSaveCannotResurrect() {
        MemoryPrefs prefs = new MemoryPrefs();
        // 模拟真实时序：migrateFrom 已在构造前把 __migrated__ 写为 true（测试接缝不跑迁移）。
        prefs.data.put("__migrated__", Boolean.TRUE);
        TdSaveManager save = new TdSaveManager(prefs);
        save.recordWin(4);
        save.setBestStars("main_001", 3);
        save.addKills(123);

        save.clearAll();

        // 业务数据确已清零
        assertEquals(0, save.getTotalKills());
        assertEquals(0, save.getBestStars("main_001"));
        assertEquals(1, save.getUnlockedLevelCount());
        // 守卫旗标必须存活
        assertTrue("__migrated__ 守卫必须在 clearAll 后存活，否则旧扁平存档复活",
                prefs.getBoolean("__migrated__", false));
        assertTrue("td_level_id_migrated_v1 守卫必须在 clearAll 后存活",
                prefs.getBoolean("td_level_id_migrated_v1", false));
    }

    /** 进程内 SharedPreferences 假实现：覆盖 TdSaveManager 用到的键值语义（clear/put/读回）。 */
    private static final class MemoryPrefs implements SharedPreferences {
        final Map<String, Object> data = new HashMap<>();

        @Override public Map<String, ?> getAll() { return new HashMap<>(data); }
        @Override public String getString(String key, String defValue) {
            Object v = data.get(key);
            return v instanceof String ? (String) v : defValue;
        }
        @Override public Set<String> getStringSet(String key, Set<String> defValues) {
            return defValues;
        }
        @Override public int getInt(String key, int defValue) {
            Object v = data.get(key);
            return v instanceof Integer ? (Integer) v : defValue;
        }
        @Override public long getLong(String key, long defValue) {
            Object v = data.get(key);
            return v instanceof Long ? (Long) v : defValue;
        }
        @Override public float getFloat(String key, float defValue) {
            Object v = data.get(key);
            return v instanceof Float ? (Float) v : defValue;
        }
        @Override public boolean getBoolean(String key, boolean defValue) {
            Object v = data.get(key);
            return v instanceof Boolean ? (Boolean) v : defValue;
        }
        @Override public boolean contains(String key) { return data.containsKey(key); }
        @Override public Editor edit() { return new MemoryEditor(); }
        @Override public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) { }
        @Override public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) { }

        /** Editor 假实现：apply/commit 时按 clear → remove → put 的 SP 顺序落到内存 Map。 */
        private final class MemoryEditor implements Editor {
            private final Map<String, Object> pending = new HashMap<>();
            private final Set<String> removed = new HashSet<>();
            private boolean cleared = false;

            @Override public Editor putString(String key, String value) {
                pending.put(key, value);
                return this;
            }
            @Override public Editor putStringSet(String key, Set<String> values) {
                pending.put(key, values);
                return this;
            }
            @Override public Editor putInt(String key, int value) {
                pending.put(key, value);
                return this;
            }
            @Override public Editor putLong(String key, long value) {
                pending.put(key, value);
                return this;
            }
            @Override public Editor putFloat(String key, float value) {
                pending.put(key, value);
                return this;
            }
            @Override public Editor putBoolean(String key, boolean value) {
                pending.put(key, value);
                return this;
            }
            @Override public Editor remove(String key) {
                removed.add(key);
                return this;
            }
            @Override public Editor clear() {
                cleared = true;
                return this;
            }
            @Override public boolean commit() {
                applyToData();
                return true;
            }
            @Override public void apply() { applyToData(); }

            private void applyToData() {
                if (cleared) data.clear();
                for (String key : removed) data.remove(key);
                data.putAll(pending);
            }
        }
    }
}
