package com.gamecenter.app.td;

import android.content.Context;
import android.content.SharedPreferences;

import com.gamecenter.app.td.engine.TdGame;
import com.gamecenter.app.td.engine.TdLevels;

import com.gamecenter.app.core.common.ModuleScopedPreferences;

/**
 * 塔防「保卫蛋蛋」存档管理器。
 *
 * <p>持久化各关卡的最高星级与解锁进度，以及各难度的最佳战绩。
 * 存储走 ModuleScopedPreferences 模块作用域 SP（mod_td__td_save），历史扁平 td_save 数据自动迁移；
 * 键仍以 td_ 前缀命名，并使用 String 存储（见项目规范：避免 StringSet 跨实例缓存问题）。
 */
public class TdSaveManager {

    /** 本模块在 catalog 中的 id，用作数据隔离作用域前缀 */
    private static final String MODULE_ID = "td";
    private static final String PREFS = "td_save"; // 旧扁平名；同时作为作用域 SP 的 baseName
    private static final String KEY_STARS_PREFIX = "td_stars_";
    private static final String KEY_UNLOCKED = "td_unlocked_levels";
    private static final String KEY_KILLS_PREFIX = "td_kills_";
    private static final String KEY_BEST_TIME_PREFIX = "td_time_";
    private static final String KEY_PLAY_COUNT = "td_play_count";
    private static final String KEY_EASY_DONE = "td_easy_done";
    private static final String KEY_HARD_DONE = "td_hard_done";
    private static final String KEY_ID_MIGRATED = "td_level_id_migrated_v1";
    /**
     * 与 core/common ModuleScopedPreferences.MIGRATE_FLAG 保持一致，改动需同步。
     * 该常量在 core/common 中为 private 不可引用，故按同一字面量 "__migrated__" 在此复刻。
     */
    private static final String MIGRATE_FLAG_KEY = "__migrated__";
    /** 按难度隔离的最佳无尽波数键前缀（后缀为难度小写名：easy/normal/hard） */
    private static final String KEY_ENDLESS_BEST_PREFIX = "td_endless_best_";

    private final SharedPreferences prefs;

    public TdSaveManager(Context context) {
        Context appContext = context.getApplicationContext();
        // 数据隔离（Phase 3 强约束）：旧扁平 td_save 若有历史数据仅迁移一次到 mod_td__td_save，
        // 之后一律走带 moduleId 前缀的作用域 SP，禁止模块间以任意文件名互读。
        ModuleScopedPreferences.migrateFrom(appContext, MODULE_ID, PREFS);
        this.prefs = ModuleScopedPreferences.get(appContext, MODULE_ID, PREFS);
        migrateLegacyLevelIndexes();
    }

    /** JVM 测试接缝：直接注入 SharedPreferences，绕过 Android Context 与一次性迁移逻辑。 */
    TdSaveManager(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    /** Returns the stable campaign id for a legacy zero-based index, or null for invalid input. */
    public static String levelIdForIndex(int levelIndex) {
        return levelIndex >= 0 && levelIndex < 5
                ? String.format(java.util.Locale.US, "main_%03d", levelIndex + 1) : null;
    }

    /**
     * Allows bounded main-campaign IDs so later chapter data does not require a save-code release.
     * Callers still receive IDs only from the validated {@code TdLevels} catalog.
     */
    public static boolean isValidLevelId(String levelId) {
        if (levelId == null || !levelId.matches("main_[0-9]{3}")) return false;
        int order = Integer.parseInt(levelId.substring("main_".length()));
        return order >= 1 && order <= 999;
    }

    private static String checkedLevelId(String levelId) {
        return isValidLevelId(levelId) ? levelId : null;
    }

    /** Idempotently copies numeric keys to stable-id keys, retaining all legacy keys. */
    private void migrateLegacyLevelIndexes() {
        if (prefs.getBoolean(KEY_ID_MIGRATED, false)) return;
        SharedPreferences.Editor e = prefs.edit();
        for (int i = 0; i < 5; i++) {
            String id = levelIdForIndex(i);
            copyIfAbsent(e, KEY_STARS_PREFIX + id, KEY_STARS_PREFIX + i, 0);
            copyIfAbsent(e, KEY_BEST_TIME_PREFIX + id, KEY_BEST_TIME_PREFIX + i, 0);
        }
        e.putBoolean(KEY_ID_MIGRATED, true).apply();
    }

    private void copyIfAbsent(SharedPreferences.Editor e, String target, String legacy, int fallback) {
        if (!prefs.contains(target) && prefs.contains(legacy)) {
            e.putInt(target, prefs.getInt(legacy, fallback));
        }
    }

    /** 解锁关卡数量（index 从 0 开始，level 1 恒解锁） */
    public int getUnlockedLevelCount() {
        return Math.max(1, prefs.getInt(KEY_UNLOCKED, 1));
    }

    /** 记录通关，解锁下一关（解锁值硬上限 = 战役总关数，防脏数据超界） */
    public void recordWin(int levelIndex) {
        int unlocked = getUnlockedLevelCount();
        int nextUnlocked = clampNextUnlockedToCampaignSize(levelIndex + 2);
        if (nextUnlocked > unlocked) {
            prefs.edit().putInt(KEY_UNLOCKED, nextUnlocked).apply();
        }
    }

    /**
     * 解锁值硬上限：钳制到 TdLevels catalog 的战役总关数。修复前 nextUnlocked =
     * levelIndex + 2 无上限，通关最后一关后 unlocked 会超过总关数，形成 26/25 这类
     * 脏数据（战绩面板此前只能自行 Math.min 兜底显示）。
     *
     * <p>catalog 未初始化时不钳制、保持旧行为：TdLevels.levelIds() 在无 catalog 时抛
     * IllegalStateException，Fragment 运行时 catalog 必已初始化（onCreateView 加载模块
     * 资产后才构建 UI），这里只为纯 JVM 测试/极端时序兜底——数据读取失败绝不拦截
     * 玩家解锁进度。
     */
    private static int clampNextUnlockedToCampaignSize(int nextUnlocked) {
        try {
            return Math.min(nextUnlocked, TdLevels.levelIds().size());
        } catch (IllegalStateException catalogMissing) {
            return nextUnlocked;
        }
    }

    public void recordWin(String levelId) {
        if (!isValidLevelId(levelId)) return;
        int index = Integer.parseInt(levelId.substring(5)) - 1;
        recordWin(index);
    }

    public void recordPlay() {
        prefs.edit().putInt(KEY_PLAY_COUNT, getPlayCount() + 1).apply();
    }

    public int getPlayCount() {
        return prefs.getInt(KEY_PLAY_COUNT, 0);
    }

    /** 获取某关最高星级（0=未通过） */
    public int getBestStars(int levelIndex) {
        String id = levelIdForIndex(levelIndex);
        return id == null ? 0 : getBestStars(id);
    }

    public int getBestStars(String levelId) {
        String id = checkedLevelId(levelId);
        return id == null ? 0 : prefs.getInt(KEY_STARS_PREFIX + id, 0);
    }

    /** 设定某关最高星级（只增不减） */
    public void setBestStars(int levelIndex, int stars) {
        String id = levelIdForIndex(levelIndex);
        if (id != null) setBestStars(id, stars);
    }

    public void setBestStars(String levelId, int stars) {
        String id = checkedLevelId(levelId);
        if (id == null) return;
        if (stars > getBestStars(id)) prefs.edit().putInt(KEY_STARS_PREFIX + id, stars).apply();
    }

    /** 累计击杀数 */
    public int getTotalKills() {
        return prefs.getInt(KEY_KILLS_PREFIX + "total", 0);
    }

    // ===== 战绩/成就面板聚合（纯函数，JVM 可测） =====

    /**
     * 全战役星级求和（纯函数，JVM 可测）：输入各关最高星级数组，返回累计星数。
     * null/空数组一律为 0；负值（脏数据）按 0 计，绝不产生负总和。
     * 各关逐项读取由调用方（UI 层经 TdLevels.levelIds()）完成，本方法只负责聚合语义。
     */
    static int sumBestStars(int[] perLevelStars) {
        if (perLevelStars == null) return 0;
        int total = 0;
        for (int stars : perLevelStars) total += Math.max(0, stars);
        return total;
    }

    public void addKills(int n) {
        prefs.edit().putInt(KEY_KILLS_PREFIX + "total", getTotalKills() + n).apply();
    }

    /** 某关最佳战绩秒数（0 表示未记录） */
    public int getBestTimeSec(int levelIndex) {
        String id = levelIdForIndex(levelIndex);
        return id == null ? 0 : getBestTimeSec(id);
    }

    public int getBestTimeSec(String levelId) {
        String id = checkedLevelId(levelId);
        return id == null ? 0 : prefs.getInt(KEY_BEST_TIME_PREFIX + id, 0);
    }

    public void setBestTimeSec(int levelIndex, int sec) {
        String id = levelIdForIndex(levelIndex);
        if (id != null) setBestTimeSec(id, sec);
    }

    public void setBestTimeSec(String levelId, int sec) {
        String id = checkedLevelId(levelId);
        if (id == null) return;
        int cur = getBestTimeSec(id);
        if (cur == 0 || sec < cur) {
            prefs.edit().putInt(KEY_BEST_TIME_PREFIX + id, sec).apply();
        }
    }

    /** 简单难度是否通关过（用于成就显示） */
    public boolean isEasyCleared() { return prefs.getBoolean(KEY_EASY_DONE, false); }
    public void setEasyCleared(boolean v) { prefs.edit().putBoolean(KEY_EASY_DONE, v).apply(); }

    /** 困难难度是否通关过 */
    public boolean isHardCleared() { return prefs.getBoolean(KEY_HARD_DONE, false); }
    public void setHardCleared(boolean v) { prefs.edit().putBoolean(KEY_HARD_DONE, v).apply(); }

    // ===== 无尽模式最佳波数（阶段 1：引擎与存档） =====

    /**
     * 按难度隔离的无尽最佳波数存档键（纯函数，JVM 可测）。
     * 键与既有 td_ 前缀风格一致：td_endless_best_easy|normal|hard；
     * null 难度返回 null，调用方防御性忽略。
     */
    static String bestEndlessKey(TdGame.Difficulty difficulty) {
        if (difficulty == null) return null;
        return KEY_ENDLESS_BEST_PREFIX + difficulty.name().toLowerCase(java.util.Locale.US);
    }

    /** 只增不减合并规则（纯函数，JVM 可测）；waves ≤ 0 一律视为无效输入并忽略。 */
    static int mergedBestEndlessWaves(int current, int waves) {
        if (waves <= 0) return current;
        return Math.max(current, waves);
    }

    /** 按难度读取最佳无尽波数（0 = 未记录；null 难度防御性返回 0）。 */
    public int getBestEndlessWaves(TdGame.Difficulty difficulty) {
        String key = bestEndlessKey(difficulty);
        return key == null ? 0 : prefs.getInt(key, 0);
    }

    /** 记录按难度的最佳无尽波数（只增不减；waves ≤ 0 或 null 难度一律忽略）。 */
    public void recordEndlessWaves(TdGame.Difficulty difficulty, int waves) {
        String key = bestEndlessKey(difficulty);
        if (key == null) return;
        int current = prefs.getInt(key, 0);
        int merged = mergedBestEndlessWaves(current, waves);
        if (merged != current) prefs.edit().putInt(key, merged).apply();
    }

    public void clearAll() {
        // 单个 editor 内先 clear 再写旗标（SP 语义：clear 先于本批次 put 生效，原子提交）。
        // clearAll 不得解除迁移守卫：ModuleScopedPreferences.migrateFrom 从不删除旧扁平
        // td_save，且每次 onCreateView 构造 TdSaveManager 时重跑；若 __migrated__ 被 clear
        // 掉，旧扁平存档会在下次进入模块时被无条件回灌，升级用户清空战绩即整体复活。
        // 同理保留 td_level_id_migrated_v1，避免 migrateLegacyLevelIndexes 重跑。
        prefs.edit().clear()
                .putBoolean(MIGRATE_FLAG_KEY, true)
                .putBoolean(KEY_ID_MIGRATED, true)
                .apply();
    }
}
