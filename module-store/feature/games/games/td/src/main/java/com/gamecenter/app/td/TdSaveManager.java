package com.gamecenter.app.td;

import android.content.Context;
import android.content.SharedPreferences;

import com.gamecenter.app.td.engine.TdGame;
import com.gamecenter.app.td.engine.TdLevels;

import com.gamecenter.app.core.common.ModuleScopedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 塔防「保卫蛋蛋」存档管理器。
 *
 * <p>持久化各关卡的最高星级与解锁进度、各难度的最佳战绩，以及可解锁成就（td_achv_* 布尔键）。
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
    private static final String KEY_DIFFICULTY_CLEAR_PREFIX = "td_campaign_clear_";
    private static final String KEY_PLAY_COUNT = "td_play_count";
    private static final String KEY_EASY_DONE = "td_easy_done";
    private static final String KEY_HARD_DONE = "td_hard_done";
    private static final String KEY_ID_MIGRATED = "td_level_id_migrated_v1";
    /** 一次性脏值清洗旗标：recordWin 硬上限上线前，存量存档可能持有 td_unlocked_levels > 总关数 */
    private static final String KEY_UNLOCKED_CLEANED = "td_unlocked_cleaned_v1";
    /**
     * 与 core/common ModuleScopedPreferences.MIGRATE_FLAG 保持一致，改动需同步。
     * 该常量在 core/common 中为 private 不可引用，故按同一字面量 "__migrated__" 在此复刻。
     */
    private static final String MIGRATE_FLAG_KEY = "__migrated__";
    /** 按难度隔离的最佳无尽波数键前缀（后缀为难度小写名：easy/normal/hard） */
    private static final String KEY_ENDLESS_BEST_PREFIX = "td_endless_best_";

    private final SharedPreferences prefs;
    /** 本实例内新解锁、尚未被 UI 取走的成就（解锁瞬间提示用）；跨实例不共享，仅提示用途 */
    private final List<TdAchievement> pendingUnlocks = new ArrayList<>();

    public TdSaveManager(Context context) {
        Context appContext = context.getApplicationContext();
        // 数据隔离（Phase 3 强约束）：旧扁平 td_save 若有历史数据仅迁移一次到 mod_td__td_save，
        // 之后一律走带 moduleId 前缀的作用域 SP，禁止模块间以任意文件名互读。
        ModuleScopedPreferences.migrateFrom(appContext, MODULE_ID, PREFS);
        this.prefs = ModuleScopedPreferences.get(appContext, MODULE_ID, PREFS);
        migrateLegacyLevelIndexes();
        cleanLegacyUnlockedOverflow();
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

    /**
     * 一次性清洗历史解锁进度脏值（公开构造器在 migrateLegacyLevelIndexes 之后调用）：
     * recordWin 硬上限上线前解锁值无上限，存量存档可能已有 td_unlocked_levels &gt; 战役
     * 总关数（如通关最后一关写入 26/25），显示层虽已自行钳制但脏值仍在。此处把超界值
     * 回写为战役总关数；未超界的正常进度不动。
     *
     * <p>幂等：由独立旗标 td_unlocked_cleaned_v1 保证只清洗一次——旗标置位后即使键再被
     * 外部改写也不再回写（recordWin 的钳制已杜绝新脏值产生）。catalog 未初始化时
     * （TdLevels.levelIds() 抛 IllegalStateException，同 clampNextUnlockedToCampaignSize
     * 的兜底模式）跳过且不落旗标，留待下次构造重试，数据读取失败绝不拦截玩家解锁进度。
     * 包内可见供纯 JVM 测试直接驱动（JVM 接缝构造器按约定不跑迁移/清洗逻辑）。
     */
    void cleanLegacyUnlockedOverflow() {
        if (prefs.getBoolean(KEY_UNLOCKED_CLEANED, false)) return;
        int campaignSize;
        try {
            campaignSize = TdLevels.levelIds().size();
        } catch (IllegalStateException catalogMissing) {
            return;
        }
        SharedPreferences.Editor e = prefs.edit();
        if (prefs.getInt(KEY_UNLOCKED, 1) > campaignSize) {
            e.putInt(KEY_UNLOCKED, campaignSize);
        }
        e.putBoolean(KEY_UNLOCKED_CLEANED, true).apply();
    }

    /** 解锁关卡数量（index 从 0 开始，level 1 恒解锁） */
    public int getUnlockedLevelCount() {
        return Math.max(1, prefs.getInt(KEY_UNLOCKED, 1));
    }

    /** 记录通关，解锁下一关（解锁值硬上限 = 战役总关数，防脏数据超界）；同时判定通关型成就 */
    public void recordWin(int levelIndex) {
        int unlocked = getUnlockedLevelCount();
        int nextUnlocked = clampNextUnlockedToCampaignSize(levelIndex + 2);
        if (nextUnlocked > unlocked) {
            prefs.edit().putInt(KEY_UNLOCKED, nextUnlocked).apply();
        }
        unlockAchievements(TdAchievement.matchingCampaignWin(levelIdForIndex(levelIndex)));
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
        // recordWin(int) 只能经 levelIdForIndex 回溯 index≤4 的关 id；后章终关
        // （main_015/025/035）的通关成就必须用事件原始 id 判定（已解锁者幂等跳过，不重复入列）。
        unlockAchievements(TdAchievement.matchingCampaignWin(levelId));
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
        // 击杀型成就按写入后的累计值判定：一次大额累加跨多档时同批解锁、各档仅触发一次
        unlockAchievements(TdAchievement.matchingTotalKills(getTotalKills()));
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

    /** Stable per-difficulty best-time key; null inputs are rejected defensively. */
    static String bestTimeKey(String levelId, TdGame.Difficulty difficulty) {
        String id = checkedLevelId(levelId);
        if (id == null || difficulty == null) return null;
        return KEY_BEST_TIME_PREFIX
                + difficulty.name().toLowerCase(java.util.Locale.US) + "_" + id;
    }

    /** 某关某难度最佳战绩秒数；旧键混合了所有难度，不能用于推断某个难度。 */
    public int getBestTimeSec(String levelId, TdGame.Difficulty difficulty) {
        String key = bestTimeKey(levelId, difficulty);
        if (key == null) return 0;
        return Math.max(0, prefs.getInt(key, 0));
    }

    /** 写入某关某难度最佳战绩；普通难度可刷新旧总记录，但不能覆盖更快的历史成绩。 */
    public void setBestTimeSec(String levelId, TdGame.Difficulty difficulty, int sec) {
        String key = bestTimeKey(levelId, difficulty);
        if (key == null || sec <= 0) return;
        int cur = getBestTimeSec(levelId, difficulty);
        if (cur != 0 && sec >= cur) return;
        SharedPreferences.Editor editor = prefs.edit().putInt(key, sec);
        int legacyBest = getBestTimeSec(levelId);
        if (difficulty == TdGame.Difficulty.NORMAL && (legacyBest <= 0 || sec < legacyBest)) {
            editor.putInt(KEY_BEST_TIME_PREFIX + checkedLevelId(levelId), sec);
        }
        editor.apply();
    }

    /**
     * 记录战役某难度的一次胜利，并以每关标记计算全章难度完成状态。
     * 星级仍由 UI 单独写入；本方法是难度成绩和双冠成就的唯一写入点。
     */
    public void recordCampaignWin(String levelId, TdGame.Difficulty difficulty, int elapsedSec) {
        String id = checkedLevelId(levelId);
        if (id == null || difficulty == null) return;
        recordWin(id);
        setBestTimeSec(id, difficulty, elapsedSec);
        String clearKey = difficultyLevelClearKey(id, difficulty);
        if (clearKey != null) prefs.edit().putBoolean(clearKey, true).apply();
        refreshDifficultyClearState(difficulty);
    }

    /** 按难度读取战役全章完成状态；没有 catalog 时回退旧状态键。 */
    public boolean isDifficultyCleared(TdGame.Difficulty difficulty) {
        if (difficulty == null) return false;
        String legacyKey = difficultyLegacyClearKey(difficulty);
        try {
            List<String> ids = TdLevels.levelIds();
            if (ids.isEmpty()) return prefs.getBoolean(legacyKey, false);
            boolean allCleared = true;
            for (String id : ids) {
                String clearKey = difficultyLevelClearKey(id, difficulty);
                boolean cleared = clearKey != null && prefs.getBoolean(clearKey, false);
                // Only a time recorded with an explicit difficulty proves that difficulty.
                if (!cleared && getBestTimeSec(id, difficulty) > 0) cleared = true;
                allCleared &= cleared;
            }
            // Older versions set the aggregate flag after any single win. With a catalog,
            // require evidence for every level; previously awarded achievements stay intact.
            return allCleared;
        } catch (IllegalStateException catalogMissing) {
            return prefs.getBoolean(legacyKey, false);
        }
    }

    /** 当前 catalog 下某难度已记录的最快关卡用时（0 表示没有记录）。 */
    public int getBestCampaignTimeSec(TdGame.Difficulty difficulty) {
        if (difficulty == null) return 0;
        int best = 0;
        try {
            for (String id : TdLevels.levelIds()) {
                int time = getBestTimeSec(id, difficulty);
                if (time > 0 && (best == 0 || time < best)) best = time;
            }
        } catch (IllegalStateException catalogMissing) {
            return 0;
        }
        return best;
    }

    /** 简单难度是否已完成整章（旧状态键仅作无 catalog 回退）。 */
    public boolean isEasyCleared() { return isDifficultyCleared(TdGame.Difficulty.EASY); }
    public void setEasyCleared(boolean v) {
        setDifficultyCleared(TdGame.Difficulty.EASY, v);
    }

    /** 困难难度是否已完成整章（旧状态键仅作无 catalog 回退）。 */
    public boolean isHardCleared() { return isDifficultyCleared(TdGame.Difficulty.HARD); }
    public void setHardCleared(boolean v) {
        setDifficultyCleared(TdGame.Difficulty.HARD, v);
    }

    private void setDifficultyCleared(TdGame.Difficulty difficulty, boolean cleared) {
        SharedPreferences.Editor editor = prefs.edit()
                .putBoolean(difficultyLegacyClearKey(difficulty), cleared);
        try {
            if (cleared) {
                for (String id : TdLevels.levelIds()) {
                    String key = difficultyLevelClearKey(id, difficulty);
                    if (key != null) editor.putBoolean(key, true);
                }
            } else {
                for (String id : TdLevels.levelIds()) {
                    String key = difficultyLevelClearKey(id, difficulty);
                    if (key != null) editor.remove(key);
                }
            }
        } catch (IllegalStateException catalogMissing) {
            // The legacy aggregate key remains usable until the catalog is available.
        }
        editor.apply();
        unlockAchievements(TdAchievement.matchingDualClear(isEasyCleared(), isHardCleared()));
    }

    private void refreshDifficultyClearState(TdGame.Difficulty difficulty) {
        boolean cleared = isDifficultyCleared(difficulty);
        prefs.edit().putBoolean(difficultyLegacyClearKey(difficulty), cleared).apply();
        unlockAchievements(TdAchievement.matchingDualClear(isEasyCleared(), isHardCleared()));
    }

    private static String difficultyLevelClearKey(String levelId, TdGame.Difficulty difficulty) {
        String id = checkedLevelId(levelId);
        if (id == null || difficulty == null) return null;
        return KEY_DIFFICULTY_CLEAR_PREFIX
                + difficulty.name().toLowerCase(java.util.Locale.US) + "_" + id;
    }

    private static String difficultyLegacyClearKey(TdGame.Difficulty difficulty) {
        if (difficulty == TdGame.Difficulty.EASY) return KEY_EASY_DONE;
        if (difficulty == TdGame.Difficulty.HARD) return KEY_HARD_DONE;
        return "td_normal_done";
    }

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
        // 无尽型成就走「任意难度」口径：写入后跨难度取最佳最大值判定
        unlockAchievements(TdAchievement.matchingEndlessWaves(bestEndlessWavesAnyDifficulty()));
    }

    // ===== 成就系统（td_achv_* 布尔键，只增不减；条件定义见 TdAchievement） =====

    /** 是否已解锁（战绩面板查询用；null 成就防御性返回 false）。 */
    public boolean isAchievementUnlocked(TdAchievement achievement) {
        return achievement != null && prefs.getBoolean(achievement.saveKey, false);
    }

    /** 全部已解锁成就（战绩面板渲染用；返回集合为独立快照，调用方可自由修改）。 */
    public Set<TdAchievement> getUnlockedAchievements() {
        Set<TdAchievement> out = EnumSet.noneOf(TdAchievement.class);
        for (TdAchievement a : TdAchievement.values()) {
            if (prefs.getBoolean(a.saveKey, false)) out.add(a);
        }
        return out;
    }

    /**
     * 取走并清空本实例自上次取走后新解锁的成就（解锁瞬间 UI 提示用，一次性语义）。
     * 仅本实例内存态：成就状态真源是存档布尔键，重启后不再补提示。
     */
    public List<TdAchievement> drainNewlyUnlockedAchievements() {
        if (pendingUnlocks.isEmpty()) return Collections.emptyList();
        List<TdAchievement> out = new ArrayList<>(pendingUnlocks);
        pendingUnlocks.clear();
        return out;
    }

    /** 幂等解锁：已解锁的跳过（不重复写键、不重复入列）。 */
    private void unlockAchievements(List<TdAchievement> newlySatisfied) {
        for (TdAchievement a : newlySatisfied) {
            if (prefs.getBoolean(a.saveKey, false)) continue;
            prefs.edit().putBoolean(a.saveKey, true).apply();
            pendingUnlocks.add(a);
        }
    }

    /**
     * 幂等补漏：按当前持久化状态补齐「条件已满足但尚未落章」的成就。
     * 覆盖老存档升级（成就系统上线前已通关/已累计）与写入点检测的纵深兜底；
     * 供战绩面板打开时调用（对局处于覆盖层暂停态，无并发写存档问题），
     * 清空战绩后条件不再满足，绝不复活已清掉的成就。
     */
    public void syncAchievementsFromState() {
        int totalKills = getTotalKills();
        int bestEndlessWaves = bestEndlessWavesAnyDifficulty();
        boolean dualCleared = isEasyCleared() && isHardCleared();
        List<TdAchievement> satisfied = new ArrayList<>();
        for (TdAchievement a : TdAchievement.values()) {
            if (isSatisfiedBy(a, totalKills, bestEndlessWaves, dualCleared)) satisfied.add(a);
        }
        unlockAchievements(satisfied);
    }

    /**
     * 单枚成就的条件判定（补漏路径）：与写入点 matching* 共用同一份 TdAchievement
     * 条件数据，不另设语义。「通关」口径与写入点一致：引擎 WON 时 starsEarned ≥ 1，
     * 星数非零即通关标志。
     */
    private boolean isSatisfiedBy(TdAchievement a, int totalKills,
                                  int bestEndlessWaves, boolean dualCleared) {
        switch (a.kind) {
            case CAMPAIGN_CLEAR: return getBestStars(a.campaignLevelId) > 0;
            case TOTAL_KILLS: return totalKills >= a.threshold;
            case ENDLESS_WAVES: return bestEndlessWaves >= a.threshold;
            case DUAL_CLEAR: return dualCleared;
            default: return false;
        }
    }

    /** 各难度无尽最佳波数的最大值（成就「任意难度」口径；纯读取）。 */
    private int bestEndlessWavesAnyDifficulty() {
        int best = 0;
        for (TdGame.Difficulty d : TdGame.Difficulty.values()) {
            best = Math.max(best, getBestEndlessWaves(d));
        }
        return best;
    }

    public void clearAll() {
        // 单个 editor 内先 clear 再写旗标（SP 语义：clear 先于本批次 put 生效，原子提交）。
        // clearAll 不得解除迁移守卫：ModuleScopedPreferences.migrateFrom 从不删除旧扁平
        // td_save，且每次 onCreateView 构造 TdSaveManager 时重跑；若 __migrated__ 被 clear
        // 掉，旧扁平存档会在下次进入模块时被无条件回灌，升级用户清空战绩即整体复活。
        // 同理保留 td_level_id_migrated_v1 与 td_unlocked_cleaned_v1，避免迁移/清洗
        // 钩子重跑（clearAll 后 unlocked 已归零，重跑只会徒增写入）。
        prefs.edit().clear()
                .putBoolean(MIGRATE_FLAG_KEY, true)
                .putBoolean(KEY_ID_MIGRATED, true)
                .putBoolean(KEY_UNLOCKED_CLEANED, true)
                .apply();
        // 成就布尔键（td_achv_*）随上方 clear 一并清除；本实例待提示名单同步作废，
        // 避免清空战绩后仍弹出清空前积压的解锁提示。
        pendingUnlocks.clear();
    }
}
