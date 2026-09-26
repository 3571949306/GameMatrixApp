package com.gamecenter.app.td;

import android.content.SharedPreferences;

import com.gamecenter.app.td.engine.TdGame;
import com.gamecenter.app.td.engine.TdLevelJsonParser;
import com.gamecenter.app.td.engine.TdLevels;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

    // ===== recordWin 硬上限（数据卫生）：unlocked 永不越过战役总关数 =====

    /** 5 个假关的最小合法战役章节（严格解析器 schema 可接受），供 installForTesting 使用。 */
    private static String fakeChapterJson() {
        StringBuilder json = new StringBuilder(
                "{\"schema\":1,\"id\":\"chapter_test\",\"name\":\"测试章节\",\"levels\":[");
        for (int i = 1; i <= 5; i++) {
            if (i > 1) json.append(',');
            json.append(String.format(java.util.Locale.US,
                    "{\"id\":\"main_%03d\",\"order\":%d,\"name\":\"测试关%d\",\"subtitle\":\"测试\","
                            + "\"theme\":\"GARDEN\",\"rows\":5,\"cols\":5,\"egg\":[4,0],"
                            + "\"startCoin\":100,\"mascotHp\":5,"
                            + "\"routes\":[[[0,0],[0,1],[0,2],[0,3],[0,4],[1,4],[1,3],[1,2],[1,1],"
                            + "[1,0],[2,0],[3,0],[4,0]]],"
                            + "\"waves\":[{\"types\":[\"NORMAL\"],\"route\":0,\"count\":1,"
                            + "\"interval\":0.5,\"delay\":0.1,\"hpMul\":1.0,\"speedMul\":1.0}]}",
                    i, i, i));
        }
        return json.append("]}").toString();
    }

    /** 回归：修复前 recordWin("main_005") 后 unlocked=6 > 总关数 5，形成越界脏数据。 */
    @Test public void recordWinClampsUnlockedToCampaignLevelCount() {
        TdLevels.installForTesting(TdLevelJsonParser.parseChapter(fakeChapterJson()).levels);
        MemoryPrefs prefs = new MemoryPrefs();
        TdSaveManager save = new TdSaveManager(prefs);

        save.recordWin("main_005"); // 最后一关：levelIndex+2=6，必须钳到总关数 5
        assertEquals("通关最后一关后 unlocked 必须钳制到战役总关数", 5, save.getUnlockedLevelCount());
        save.recordWin(4); // 旧索引签名与 String 签名共用同一条钳制路径
        assertEquals(5, save.getUnlockedLevelCount());

        // 上限内的正常推进不受钳制影响（旧行为保持）
        TdSaveManager other = new TdSaveManager(new MemoryPrefs());
        other.recordWin("main_002");
        assertEquals(3, other.getUnlockedLevelCount());
    }

    /**
     * catalog 未初始化时（TdLevels.levelIds() 抛 IllegalStateException）不钳制、保持旧行为：
     * Fragment 运行时 catalog 必已初始化，该分支只为纯 JVM 测试/极端时序兜底，
     * 数据读取失败绝不拦截玩家解锁进度。
     * 注意：catalog 是 TdLevels 的共享静态状态，用反射置空并在 finally 中还原，
     * 保证同 JVM 内其他用例（各自在 @BeforeClass/用例内 install）不受污染。
     */
    @Test public void recordWinKeepsLegacyUnboundedBehaviourWithoutCatalog() throws Exception {
        Field catalogField = TdLevels.class.getDeclaredField("catalog");
        catalogField.setAccessible(true);
        Object previous = catalogField.get(null);
        catalogField.set(null, null);
        try {
            TdSaveManager save = new TdSaveManager(new MemoryPrefs());
            save.recordWin(4);
            assertEquals("catalog 缺失时旧索引签名不钳制（修复前行为）", 6, save.getUnlockedLevelCount());
            save.recordWin("main_005"); // String 签名同样走无钳制回退
            assertEquals(6, save.getUnlockedLevelCount());
        } finally {
            catalogField.set(null, previous);
        }
    }

    // ===== 历史脏值清洗（td_unlocked_cleaned_v1）：超界 unlocked 一次性回写总关数 =====

    /**
     * 回归：recordWin 硬上限上线前解锁值无上限，存量存档可能持有 td_unlocked_levels &gt;
     * 总关数（如通关最后一关写入 26）。构造期清洗钩子必须把超界值回写为战役总关数。
     */
    @Test public void unlockOverflowDirtyValueIsCleanedToCampaignSizeOnce() {
        TdLevels.installForTesting(TdLevelJsonParser.parseChapter(fakeChapterJson()).levels);
        MemoryPrefs prefs = new MemoryPrefs();
        prefs.data.put("td_unlocked_levels", 26); // 存量脏值：历史无钳制写入的超界解锁数
        TdSaveManager save = new TdSaveManager(prefs);

        save.cleanLegacyUnlockedOverflow();

        assertEquals("超界脏值必须回写为战役总关数", 5, save.getUnlockedLevelCount());
        assertTrue("清洗必须落一次性旗标 td_unlocked_cleaned_v1",
                prefs.getBoolean("td_unlocked_cleaned_v1", false));

        // 幂等（重进模块二次构造语义）：同一份 SP 上再构造再清洗，旗标置位后不得再改写
        TdSaveManager reopened = new TdSaveManager(prefs);
        prefs.data.put("td_unlocked_levels", 26); // 旗标已在：即便键再被外部改写也不回写
        reopened.cleanLegacyUnlockedOverflow();
        assertEquals("一次性清洗：旗标置位后不得再改写", 26, reopened.getUnlockedLevelCount());
    }

    /** 未超界的正常解锁进度不得被清洗误伤（旗标仍要落，保证钩子只跑一次）。 */
    @Test public void unlockValueWithinCampaignSizeIsLeftUntouched() {
        TdLevels.installForTesting(TdLevelJsonParser.parseChapter(fakeChapterJson()).levels);
        MemoryPrefs prefs = new MemoryPrefs();
        prefs.data.put("td_unlocked_levels", 3);
        TdSaveManager save = new TdSaveManager(prefs);

        save.cleanLegacyUnlockedOverflow();

        assertEquals(3, save.getUnlockedLevelCount());
        assertTrue("未超界也必须落旗标（一次性语义）",
                prefs.getBoolean("td_unlocked_cleaned_v1", false));
    }

    /**
     * catalog 未初始化时（TdLevels.levelIds() 抛 IllegalStateException）跳过清洗且不落旗标：
     * 与 recordWin 钳制兜底同模式，数据读取失败绝不拦截玩家解锁进度，并留待下次构造重试。
     */
    @Test public void unlockCleanupSkipsWithoutCatalogAndRetriesLater() throws Exception {
        Field catalogField = TdLevels.class.getDeclaredField("catalog");
        catalogField.setAccessible(true);
        Object previous = catalogField.get(null);
        catalogField.set(null, null);
        try {
            MemoryPrefs prefs = new MemoryPrefs();
            prefs.data.put("td_unlocked_levels", 26);
            TdSaveManager save = new TdSaveManager(prefs);

            save.cleanLegacyUnlockedOverflow();

            assertEquals("catalog 缺失时不得回写脏值", 26, save.getUnlockedLevelCount());
            assertFalse("catalog 缺失时不得落旗标（下次构造重试）",
                    prefs.getBoolean("td_unlocked_cleaned_v1", false));
        } finally {
            catalogField.set(null, previous);
        }
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

    @Test public void legacyMixedDifficultyTimeDoesNotBecomeNormalTimeOrBlockFirstNormalWin() {
        TdLevels.installForTesting(TdLevelJsonParser.parseChapter(fakeChapterJson()).levels);
        MemoryPrefs prefs = new MemoryPrefs();
        prefs.data.put("td_time_main_001", 60); // Older versions mixed all difficulties in this key.
        TdSaveManager save = new TdSaveManager(prefs);

        for (TdGame.Difficulty difficulty : TdGame.Difficulty.values()) {
            assertEquals("An unclassified legacy time cannot prove a difficulty record",
                    0, save.getBestTimeSec("main_001", difficulty));
        }
        save.recordCampaignWin("main_001", TdGame.Difficulty.NORMAL, 90);

        assertEquals("The first verified normal win must be recorded even when slower than legacy",
                90, save.getBestTimeSec("main_001", TdGame.Difficulty.NORMAL));
        assertEquals(90, prefs.getInt("td_time_normal_main_001", 0));
        assertEquals(90, save.getBestCampaignTimeSec(TdGame.Difficulty.NORMAL));
        assertEquals("Keep the historical all-difficulty best for legacy readers", 60,
                save.getBestTimeSec("main_001"));
    }

    @Test public void legacyMixedDifficultyTimesDoNotCompleteNormalCampaign() {
        TdLevels.installForTesting(TdLevelJsonParser.parseChapter(fakeChapterJson()).levels);
        MemoryPrefs prefs = new MemoryPrefs();
        for (String id : TdLevels.levelIds()) prefs.data.put("td_time_" + id, 60);
        TdSaveManager save = new TdSaveManager(prefs);

        assertFalse("Old mixed times are not evidence of clearing normal difficulty",
                save.isDifficultyCleared(TdGame.Difficulty.NORMAL));
        assertEquals(0, save.getBestCampaignTimeSec(TdGame.Difficulty.NORMAL));
        for (String id : TdLevels.levelIds()) {
            save.recordCampaignWin(id, TdGame.Difficulty.NORMAL, 90);
        }
        assertTrue(save.isDifficultyCleared(TdGame.Difficulty.NORMAL));
        assertFalse(save.isDifficultyCleared(TdGame.Difficulty.EASY));
        assertFalse(save.isDifficultyCleared(TdGame.Difficulty.HARD));
    }

    @Test public void legacySingleLevelClearFlagsDoNotCompleteCampaign() {
        TdLevels.installForTesting(TdLevelJsonParser.parseChapter(fakeChapterJson()).levels);
        MemoryPrefs prefs = new MemoryPrefs();
        // Older versions set these aggregate flags after any one level, not the full campaign.
        prefs.data.put("td_easy_done", Boolean.TRUE);
        prefs.data.put("td_hard_done", Boolean.TRUE);
        TdSaveManager save = new TdSaveManager(prefs);

        assertFalse("An old easy flag cannot prove every campaign level was cleared",
                save.isEasyCleared());
        assertFalse("An old hard flag cannot prove every campaign level was cleared",
                save.isHardCleared());

        save.recordCampaignWin("main_001", TdGame.Difficulty.EASY, 60);
        save.recordCampaignWin("main_001", TdGame.Difficulty.HARD, 90);
        assertFalse("A verified first easy win must not turn an incomplete campaign into complete",
                save.isEasyCleared());
        assertFalse("A verified first hard win must not turn an incomplete campaign into complete",
                save.isHardCleared());
    }

    @Test public void legacySingleLevelClearFlagsDoNotBackfillDualCrown() {
        TdLevels.installForTesting(TdLevelJsonParser.parseChapter(fakeChapterJson()).levels);
        MemoryPrefs prefs = new MemoryPrefs();
        prefs.data.put("td_easy_done", Boolean.TRUE);
        prefs.data.put("td_hard_done", Boolean.TRUE);
        TdSaveManager save = new TdSaveManager(prefs);

        save.syncAchievementsFromState();
        assertFalse("Opening stats must not award dual crown from two ambiguous legacy flags",
                save.isAchievementUnlocked(TdAchievement.DUAL_CROWN));
        assertFalse(save.drainNewlyUnlockedAchievements().contains(TdAchievement.DUAL_CROWN));

        save.recordCampaignWin("main_001", TdGame.Difficulty.EASY, 60);
        save.recordCampaignWin("main_001", TdGame.Difficulty.HARD, 90);
        save.syncAchievementsFromState();
        assertFalse("The first verified win of each difficulty still cannot award dual crown",
                save.isAchievementUnlocked(TdAchievement.DUAL_CROWN));
        assertFalse(save.drainNewlyUnlockedAchievements().contains(TdAchievement.DUAL_CROWN));
    }

    @Test public void legacyUnlockedDualCrownIsPreservedWithoutBeingAwardedAgain() {
        TdLevels.installForTesting(TdLevelJsonParser.parseChapter(fakeChapterJson()).levels);
        MemoryPrefs prefs = new MemoryPrefs();
        prefs.data.put("td_easy_done", Boolean.TRUE);
        prefs.data.put("td_hard_done", Boolean.TRUE);
        prefs.data.put(TdAchievement.DUAL_CROWN.saveKey, Boolean.TRUE);
        TdSaveManager save = new TdSaveManager(prefs);

        save.syncAchievementsFromState();
        assertTrue("Migration must preserve an achievement already earned in an older version",
                save.isAchievementUnlocked(TdAchievement.DUAL_CROWN));
        assertTrue("An existing achievement must not be queued as newly unlocked",
                save.drainNewlyUnlockedAchievements().isEmpty());
        assertFalse("Keeping a historical achievement does not prove current easy completion",
                save.isEasyCleared());
        assertFalse("Keeping a historical achievement does not prove current hard completion",
                save.isHardCleared());

        save.recordCampaignWin("main_001", TdGame.Difficulty.EASY, 60);
        save.recordCampaignWin("main_001", TdGame.Difficulty.HARD, 90);
        assertTrue(save.isAchievementUnlocked(TdAchievement.DUAL_CROWN));
        assertFalse("Recording new wins must not repeat the historical dual crown notification",
                save.drainNewlyUnlockedAchievements().contains(TdAchievement.DUAL_CROWN));
        TdSaveManager reloaded = new TdSaveManager(prefs);
        reloaded.syncAchievementsFromState();
        assertTrue("The historical dual crown must remain persisted after reopening the save",
                reloaded.isAchievementUnlocked(TdAchievement.DUAL_CROWN));
        assertFalse(reloaded.drainNewlyUnlockedAchievements().contains(TdAchievement.DUAL_CROWN));
    }

    @Test public void bestTimesAreIsolatedByDifficulty() {
        assertEquals("td_time_easy_main_001",
                TdSaveManager.bestTimeKey("main_001", TdGame.Difficulty.EASY));
        assertEquals("td_time_hard_main_001",
                TdSaveManager.bestTimeKey("main_001", TdGame.Difficulty.HARD));
        assertNull(TdSaveManager.bestTimeKey("bad", TdGame.Difficulty.EASY));

        TdSaveManager save = new TdSaveManager(new MemoryPrefs());
        save.setBestTimeSec("main_001", TdGame.Difficulty.EASY, 90);
        save.setBestTimeSec("main_001", TdGame.Difficulty.HARD, 120);
        save.setBestTimeSec("main_001", TdGame.Difficulty.EASY, 100);

        assertEquals("简单成绩不能被困难成绩覆盖", 90,
                save.getBestTimeSec("main_001", TdGame.Difficulty.EASY));
        assertEquals("困难成绩必须独立保存", 120,
                save.getBestTimeSec("main_001", TdGame.Difficulty.HARD));
        save.setBestTimeSec("main_001", TdGame.Difficulty.HARD, 80);
        assertEquals(80, save.getBestTimeSec("main_001", TdGame.Difficulty.HARD));
    }

    @Test public void difficultyClearRequiresEveryCampaignLevel() {
        TdLevels.installForTesting(TdLevelJsonParser.parseChapter(fakeChapterJson()).levels);
        TdSaveManager save = new TdSaveManager(new MemoryPrefs());

        for (int i = 0; i < 4; i++) {
            save.recordCampaignWin(String.format(java.util.Locale.US, "main_%03d", i + 1),
                    TdGame.Difficulty.EASY, 100 + i);
            assertFalse("未完成整章前不能标记简单难度完成", save.isEasyCleared());
        }
        save.recordCampaignWin("main_005", TdGame.Difficulty.EASY, 104);
        assertTrue("简单难度必须完成全部战役关卡后才算完成", save.isEasyCleared());
        assertFalse("困难难度尚未完成时不得触发双冠", save.isHardCleared());
        assertFalse(save.isAchievementUnlocked(TdAchievement.DUAL_CROWN));

        for (int i = 0; i < 5; i++) {
            save.recordCampaignWin(String.format(java.util.Locale.US, "main_%03d", i + 1),
                    TdGame.Difficulty.HARD, 200 + i);
        }
        assertTrue("困难难度必须完成全部战役关卡后才算完成", save.isHardCleared());
        assertTrue("两种难度都完成整章后才能获得双冠", save.isAchievementUnlocked(TdAchievement.DUAL_CROWN));
        assertEquals("战役最佳时间按难度读取最小值", 100,
                save.getBestCampaignTimeSec(TdGame.Difficulty.EASY));
        assertEquals(200, save.getBestCampaignTimeSec(TdGame.Difficulty.HARD));
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
        // 模拟真实时序：migrateFrom 已在构造前把 __migrated__ 写为 true（测试接缝不跑迁移），
        // 一次性清洗旗标同理（清洗已发生过）。
        prefs.data.put("__migrated__", Boolean.TRUE);
        prefs.data.put("td_unlocked_cleaned_v1", Boolean.TRUE);
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
        assertTrue("td_unlocked_cleaned_v1 清洗旗标必须在 clearAll 后存活（避免钩子重跑）",
                prefs.getBoolean("td_unlocked_cleaned_v1", false));
    }

    // ===== 成就系统：定义数据 / 写入点判定 / 边界 / 幂等 / 补漏 / 清空 =====

    @Test public void achievementSaveKeysAreUniqueAndUseTdAchvPrefix() {
        Set<String> keys = new HashSet<>();
        for (TdAchievement a : TdAchievement.values()) {
            assertTrue("成就存档键必须带 td_achv_ 前缀（沿用模块 td_ 键约定）: " + a.saveKey,
                    a.saveKey.startsWith("td_achv_"));
            keys.add(a.saveKey);
        }
        assertEquals("各成就存档键必须互不相同（布尔键与枚举一一对应）",
                TdAchievement.values().length, keys.size());
    }

    @Test public void achievementMatchersArePureAndDefensive() {
        assertTrue(TdAchievement.matchingCampaignWin(null).isEmpty());
        assertTrue(TdAchievement.matchingCampaignWin("main_999").isEmpty());
        List<TdAchievement> ch1 = TdAchievement.matchingCampaignWin("main_005");
        assertEquals(1, ch1.size());
        assertEquals(TdAchievement.CHAPTER1_CLEARED, ch1.get(0));
        assertTrue(TdAchievement.matchingTotalKills(0).isEmpty());
        assertTrue(TdAchievement.matchingEndlessWaves(0).isEmpty());
        assertTrue(TdAchievement.matchingDualClear(true, false).isEmpty());
        assertTrue(TdAchievement.matchingDualClear(false, true).isEmpty());
        assertEquals(TdAchievement.DUAL_CROWN, TdAchievement.matchingDualClear(true, true).get(0));
    }

    @Test public void campaignWinAchievementsFireOnlyOnExactBoundaryLevels() {
        // 初试啼声 = 通关 main_001，而非任意通关
        TdSaveManager first = new TdSaveManager(new MemoryPrefs());
        first.recordWin("main_001");
        assertTrue(first.isAchievementUnlocked(TdAchievement.FIRST_WIN));
        assertFalse(first.isAchievementUnlocked(TdAchievement.CHAPTER1_CLEARED));
        assertEquals(1, first.getUnlockedAchievements().size());

        // 第 1 章 = 通关 main_005 而非 main_004
        TdSaveManager early = new TdSaveManager(new MemoryPrefs());
        early.recordWin("main_004");
        assertFalse(early.isAchievementUnlocked(TdAchievement.CHAPTER1_CLEARED));

        // 通关 main_005 只触发第 1 章，不回溯触发首关成就
        TdSaveManager ch1 = new TdSaveManager(new MemoryPrefs());
        ch1.recordWin("main_005");
        assertTrue(ch1.isAchievementUnlocked(TdAchievement.CHAPTER1_CLEARED));
        assertFalse(ch1.isAchievementUnlocked(TdAchievement.FIRST_WIN));

        // 第 2/3/4 章终关：main_015 / main_025 / main_035（索引回溯覆盖不到，走事件原始 id）
        TdSaveManager ch2 = new TdSaveManager(new MemoryPrefs());
        ch2.recordWin("main_015");
        assertTrue(ch2.isAchievementUnlocked(TdAchievement.CHAPTER2_CLEARED));
        TdSaveManager ch3 = new TdSaveManager(new MemoryPrefs());
        ch3.recordWin("main_025");
        assertTrue(ch3.isAchievementUnlocked(TdAchievement.CHAPTER3_CLEARED));
        TdSaveManager ch4 = new TdSaveManager(new MemoryPrefs());
        ch4.recordWin("main_035");
        assertTrue(ch4.isAchievementUnlocked(TdAchievement.CHAPTER4_CLEARED));
        TdSaveManager ch5 = new TdSaveManager(new MemoryPrefs());
        ch5.recordWin("main_045");
        assertTrue(ch5.isAchievementUnlocked(TdAchievement.CHAPTER5_CLEARED));
    }

    /** 旧索引签名 recordWin(int) 同样走写入点成就检测（main_001 / main_005 均在可回溯范围内）。 */
    @Test public void legacyIndexWinSignatureAlsoDetectsAchievements() {
        TdSaveManager save = new TdSaveManager(new MemoryPrefs());
        save.recordWin(0);
        assertTrue(save.isAchievementUnlocked(TdAchievement.FIRST_WIN));
        save.recordWin(4);
        assertTrue(save.isAchievementUnlocked(TdAchievement.CHAPTER1_CLEARED));
    }

    @Test public void killAchievementsUnlockAtExactBoundaries() {
        TdSaveManager save = new TdSaveManager(new MemoryPrefs());
        save.addKills(99);
        assertFalse(save.isAchievementUnlocked(TdAchievement.KILLS_100));
        save.addKills(1); // 恰好 100
        assertTrue(save.isAchievementUnlocked(TdAchievement.KILLS_100));
        assertFalse(save.isAchievementUnlocked(TdAchievement.KILLS_1000));

        TdSaveManager slayer = new TdSaveManager(new MemoryPrefs());
        slayer.addKills(999);
        assertTrue(slayer.isAchievementUnlocked(TdAchievement.KILLS_100));
        assertFalse(slayer.isAchievementUnlocked(TdAchievement.KILLS_1000));
        slayer.addKills(1); // 恰好 1000
        assertTrue(slayer.isAchievementUnlocked(TdAchievement.KILLS_1000));
    }

    @Test public void singleLargeKillBatchUnlocksEachTierExactlyOnce() {
        TdSaveManager save = new TdSaveManager(new MemoryPrefs());
        save.addKills(1500); // 一次大额累加同时跨两档

        List<TdAchievement> newly = save.drainNewlyUnlockedAchievements();
        assertEquals("一次大额 addKills 跨两档时每档必须恰好触发一次", 2, newly.size());
        Set<TdAchievement> expected = new HashSet<>(
                Arrays.asList(TdAchievement.KILLS_100, TdAchievement.KILLS_1000));
        assertEquals(expected, new HashSet<>(newly));
        assertEquals("重复取走必须为空（一次性取走语义）",
                0, save.drainNewlyUnlockedAchievements().size());

        save.addKills(500); // 未跨新档，不得再触发
        assertTrue(save.drainNewlyUnlockedAchievements().isEmpty());
    }

    @Test public void addKillsToPartialTierUnlocksOnlySharpshooter() {
        TdSaveManager save = new TdSaveManager(new MemoryPrefs());
        save.addKills(150);
        List<TdAchievement> newly = save.drainNewlyUnlockedAchievements();
        assertEquals(1, newly.size());
        assertEquals(TdAchievement.KILLS_100, newly.get(0));
    }

    @Test public void endlessAchievementsUseAnyDifficultyWithExactBoundaries() {
        TdSaveManager save = new TdSaveManager(new MemoryPrefs());
        save.recordEndlessWaves(TdGame.Difficulty.EASY, 9);
        assertFalse(save.isAchievementUnlocked(TdAchievement.ENDLESS_10));
        save.recordEndlessWaves(TdGame.Difficulty.EASY, 10); // 恰好 10 波
        assertTrue(save.isAchievementUnlocked(TdAchievement.ENDLESS_10));
        assertFalse(save.isAchievementUnlocked(TdAchievement.ENDLESS_25));

        // 「任意难度」口径：换难度达阈值同样触发，且一次大额波数可同时跨两档
        TdSaveManager warlord = new TdSaveManager(new MemoryPrefs());
        warlord.recordEndlessWaves(TdGame.Difficulty.HARD, 25);
        assertTrue(warlord.isAchievementUnlocked(TdAchievement.ENDLESS_10));
        assertTrue(warlord.isAchievementUnlocked(TdAchievement.ENDLESS_25));
    }

    @Test public void dualCrownRequiresBothDifficultiesRegardlessOfOrder() {
        TdSaveManager save = new TdSaveManager(new MemoryPrefs());
        save.setEasyCleared(true);
        assertFalse(save.isAchievementUnlocked(TdAchievement.DUAL_CROWN));
        save.setHardCleared(true);
        assertTrue(save.isAchievementUnlocked(TdAchievement.DUAL_CROWN));

        // 顺序无关：先困难后简单同样解锁
        TdSaveManager reversed = new TdSaveManager(new MemoryPrefs());
        reversed.setHardCleared(true);
        assertFalse(reversed.isAchievementUnlocked(TdAchievement.DUAL_CROWN));
        reversed.setEasyCleared(true);
        assertTrue(reversed.isAchievementUnlocked(TdAchievement.DUAL_CROWN));
    }

    @Test public void achievementDetectionIsIdempotentOnRepeatedEvents() {
        TdSaveManager save = new TdSaveManager(new MemoryPrefs());
        save.recordWin("main_005");
        List<TdAchievement> first = save.drainNewlyUnlockedAchievements();
        assertEquals(1, first.size());
        assertEquals(TdAchievement.CHAPTER1_CLEARED, first.get(0));

        save.recordWin("main_005"); // 重玩已通关关卡
        assertTrue("重复事件不得再次触发（幂等）", save.drainNewlyUnlockedAchievements().isEmpty());
        assertEquals(1, save.getUnlockedAchievements().size());

        save.addKills(100);
        assertEquals(1, save.drainNewlyUnlockedAchievements().size());
        save.addKills(100); // 累计值仍在阈值上但已解锁
        assertTrue(save.drainNewlyUnlockedAchievements().isEmpty());
        assertEquals(2, save.getUnlockedAchievements().size());
    }

    /**
     * 补漏：成就系统上线前的老存档（直接持有底层键，未经新写入点）经
     * syncAchievementsFromState 幂等补章，且不误发未满足条件的成就。
     */
    @Test public void syncAchievementsFromStateBackfillsLegacyProgress() {
        MemoryPrefs prefs = new MemoryPrefs();
        prefs.data.put("td_stars_main_005", 3);
        prefs.data.put("td_kills_total", 250);
        prefs.data.put("td_endless_best_normal", 12);
        prefs.data.put("td_easy_done", Boolean.TRUE);
        TdSaveManager save = new TdSaveManager(prefs);
        assertEquals("补漏前不得有任何已解锁成就", 0, save.getUnlockedAchievements().size());

        save.syncAchievementsFromState();

        assertTrue(save.isAchievementUnlocked(TdAchievement.CHAPTER1_CLEARED));
        assertTrue(save.isAchievementUnlocked(TdAchievement.KILLS_100));
        assertTrue(save.isAchievementUnlocked(TdAchievement.ENDLESS_10));
        assertFalse("困难未通关不得误发双冠王", save.isAchievementUnlocked(TdAchievement.DUAL_CROWN));
        assertFalse(save.isAchievementUnlocked(TdAchievement.CHAPTER2_CLEARED));

        // 幂等：重复补漏不得再次触发
        save.drainNewlyUnlockedAchievements();
        save.syncAchievementsFromState();
        assertTrue(save.drainNewlyUnlockedAchievements().isEmpty());
    }

    @Test public void clearAllWipesAchievementsAndSyncDoesNotResurrectThem() {
        TdSaveManager save = new TdSaveManager(new MemoryPrefs());
        save.recordWin("main_001");                          // FIRST_WIN
        save.addKills(1200);                                 // KILLS_100 + KILLS_1000
        save.recordEndlessWaves(TdGame.Difficulty.HARD, 30); // ENDLESS_10 + ENDLESS_25
        save.setEasyCleared(true);
        save.setHardCleared(true);                           // DUAL_CROWN
        assertEquals(6, save.getUnlockedAchievements().size());

        save.clearAll();

        assertEquals("清空战绩必须一并清除全部成就", 0, save.getUnlockedAchievements().size());
        assertFalse(save.isAchievementUnlocked(TdAchievement.FIRST_WIN));
        assertFalse(save.isAchievementUnlocked(TdAchievement.DUAL_CROWN));
        assertEquals("清空后待提示名单必须作废", 0, save.drainNewlyUnlockedAchievements().size());
        // 条件已不再满足，补漏不得复活任何成就
        save.syncAchievementsFromState();
        assertEquals(0, save.getUnlockedAchievements().size());
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
