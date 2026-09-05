package com.gamecenter.app.td.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 塔防「保卫蛋蛋」无尽模式波次工厂。
 *
 * <p>纯静态、零状态、完全确定性：同样的 (waveNumber, difficulty, routeCount) 永远生成
 * 完全相同的一条波，不含任何随机数。因此同一份规则既能供引擎在定义波次耗尽后合成
 * 正式波次，也能供 HUD 在任意时刻零副作用地预览下一波（两处结果严格一致）。
 *
 * <p>数值语义与战役共用同一套乘法链条：工厂产出的 {@link TdGame.Wave#hpMul} 只承载
 * 波次自身的成长（1.10^(n-1)，上限 40× 防溢出）；难度倍率（血量 difficulty.hpMul、
 * 速度 difficulty.speedMul）仍由引擎 {@code spawnMonster} 按现有乘法语义合成，
 * 工厂不重复叠加，速度波次系数恒为 1。
 *
 * <p>生成规则（n = 无尽第几波，1 起；优先级：BOSS 波 &gt; 飞行波 &gt; 常规波）：
 * <ul>
 *   <li>BOSS 波：n 为 10 的倍数。n 同时为 20 的倍数时为 2 只 BOSS + 2 只 NORMAL 护航
 *       （count=4），否则 1 只 BOSS + 2 只 NORMAL 护航（count=3）；固定出怪间隔 1.2 秒、
 *       开场延迟 1.0 秒，数量不受难度影响。</li>
 *   <li>飞行波：n ≥ 5 且为 3 的倍数（首个命中 n=6）。组成 [FLY, FLY, 地面护航]，
 *       护航取 {FAST, NORMAL, SWARM}[(n/3)%3] 轮换，整波按生成顺序循环，持续逼玩家
 *       配置对空火力。</li>
 *   <li>常规波：2-3 种怪混编。组成池随 n 逐步解锁——1-4 波仅 NORMAL/SWARM；
 *       5+ 解锁 FAST；8+ 解锁 TANK/SHIELD；12+ 解锁 HEALER/CHARGER；15+ 解锁
 *       RESISTANT/RAGER；n ≥ 20 且为 7 的倍数（28/35/49/…）额外偶发加入
 *       {SUMMONER, SPLITTER, SHIELD_GENERATOR}[(n/7)%3]；与飞行/BOSS 周期重合时让位
 *       （如 n=21/42 命中飞行波、n=70 命中 BOSS 波）。</li>
 *   <li>数量：count = min(60, round((6 + 2×(n-1)) × 难度系数))，随 n 线性增长并设
 *       60/波硬上限；难度系数 EASY=0.85、NORMAL=1.0、HARD=1.10（难度只微调数量）。</li>
 *   <li>节奏：常规/飞行波出生间隔 = max(0.35, 0.80 − 0.01×(n−1)) 秒，随 n 轻微收紧并设
 *       下限；开场延迟 0.6 秒。</li>
 *   <li>路线：routeCount &gt; 1 时按 (n−1) % routeCount 轮换；单路线关恒为 0。</li>
 * </ul>
 */
public final class TdEndlessWaveFactory {

    // ===== 成长曲线 =====

    /** 每波血量成长基数：波 n 的血量倍率 = 1.10^(n-1) */
    private static final float HP_GROWTH_PER_WAVE = 1.10f;
    /** 血量倍率硬上限：防止深波次下浮点溢出或出现不可玩数值 */
    private static final float HP_MUL_CAP = 40f;

    // ===== 数量与难度 =====

    /** 首波单波数量基数 */
    private static final int COUNT_BASE = 6;
    /** 每波数量线性增量 */
    private static final int COUNT_STEP_PER_WAVE = 2;
    /** 单波数量硬上限 */
    private static final int COUNT_CAP = 60;
    /** 难度只微调数量；血量/速度的难度合成仍由引擎现有乘法语义完成 */
    private static final float EASY_COUNT_MUL = 0.85f;
    private static final float NORMAL_COUNT_MUL = 1.0f;
    private static final float HARD_COUNT_MUL = 1.10f;

    // ===== 节奏 =====

    /** 首波出生间隔（秒） */
    private static final float INTERVAL_START_SEC = 0.80f;
    /** 每波出生间隔收紧量（秒） */
    private static final float INTERVAL_TIGHTEN_PER_WAVE = 0.01f;
    /** 出生间隔下限（秒）：杜绝深波次同帧连锁生成 */
    private static final float INTERVAL_FLOOR_SEC = 0.35f;
    /** 常规/飞行波开场延迟（秒） */
    private static final float START_DELAY_SEC = 0.6f;
    /** BOSS 波固定出怪间隔（秒） */
    private static final float BOSS_INTERVAL_SEC = 1.2f;
    /** BOSS 波开场延迟（秒） */
    private static final float BOSS_START_DELAY_SEC = 1.0f;
    /** 波次速度系数恒为 1：难度速度倍率由引擎合成，波次自身不再叠加 */
    private static final float WAVE_SPEED_MUL = 1f;

    // ===== 周期规则 =====

    /** BOSS 波周期 */
    private static final int BOSS_WAVE_PERIOD = 10;
    /** 双 BOSS 周期（20/40/… 波为 2 只 BOSS） */
    private static final int DOUBLE_BOSS_WAVE_PERIOD = 20;
    /** 飞行波周期 */
    private static final int FLY_WAVE_PERIOD = 3;
    /** 飞行波起始波次（n ≥ 5 且为 3 的倍数，首个命中 n=6） */
    private static final int FLY_WAVE_MIN_NUMBER = 5;
    /** 特殊机制怪解锁波次（20+） */
    private static final int SPECIAL_UNLOCK_WAVE = 20;
    /** 特殊机制怪出现周期（每 7 波一次） */
    private static final int SPECIAL_WAVE_PERIOD = 7;

    // ===== 组成池解锁门槛 =====

    private static final int FAST_UNLOCK_WAVE = 5;
    private static final int TANK_SHIELD_UNLOCK_WAVE = 8;
    private static final int HEALER_CHARGER_UNLOCK_WAVE = 12;
    private static final int RESISTANT_RAGER_UNLOCK_WAVE = 15;

    /** 基础组成池：1-4 波仅前两种，5+ 解锁 FAST */
    private static final MonsterType[] BASE_POOL = {
            MonsterType.NORMAL, MonsterType.SWARM, MonsterType.FAST
    };
    /** 飞行波的地面护航轮换池 */
    private static final MonsterType[] FLY_ESCORT_POOL = {
            MonsterType.FAST, MonsterType.NORMAL, MonsterType.SWARM
    };
    /** 20+ 偶发特殊机制怪轮换池 */
    private static final MonsterType[] SPECIAL_POOL = {
            MonsterType.SUMMONER, MonsterType.SPLITTER, MonsterType.SHIELD_GENERATOR
    };
    /** 常规波最少的怪物种类数 */
    private static final int MIN_KINDS = 2;
    /** 常规波额外的怪物种类数（按波次奇偶在 2/3 种间轮换） */
    private static final int EXTRA_KIND_PARITY = 1;

    private TdEndlessWaveFactory() {
    }

    /**
     * 确定性合成无尽第 {@code waveNumber} 波。
     *
     * @param waveNumber 无尽波次序号（1 起，定义波耗尽后为合成波的绝对序号）
     * @param difficulty 难度档位；null 防御性回退 NORMAL（与引擎 applyDifficulty 语义一致）
     * @param routeCount 关卡路线数；&lt;1 防御性按单路线处理
     * @return 一条可直接加入引擎的不可变波次
     * @throws IllegalArgumentException waveNumber &lt; 1（推进逻辑缺陷应尽早暴露，不静默吞掉）
     */
    public static TdGame.Wave createWave(int waveNumber, TdGame.Difficulty difficulty, int routeCount) {
        if (waveNumber < 1) throw new IllegalArgumentException("endless wave number must be positive");
        TdGame.Difficulty d = difficulty != null ? difficulty : TdGame.Difficulty.NORMAL;
        int routes = Math.max(1, routeCount);
        int routeIndex = routes == 1 ? 0 : (waveNumber - 1) % routes;
        // 优先级：BOSS 波 > 飞行波 > 常规波（如 n=30 同时命中飞行与 BOSS 周期时按 BOSS 波处理）
        if (waveNumber % BOSS_WAVE_PERIOD == 0) {
            return bossWave(waveNumber, routeIndex);
        }
        if (waveNumber % FLY_WAVE_PERIOD == 0 && waveNumber >= FLY_WAVE_MIN_NUMBER) {
            return flyWave(waveNumber, d, routeIndex);
        }
        return regularWave(waveNumber, d, routeIndex);
    }

    /** BOSS 波：1-2 只 BOSS + NORMAL 护航，节奏固定，数量不受难度影响。 */
    private static TdGame.Wave bossWave(int waveNumber, int routeIndex) {
        boolean doubleBoss = waveNumber % DOUBLE_BOSS_WAVE_PERIOD == 0;
        MonsterType[] composition = doubleBoss
                ? new MonsterType[] {MonsterType.BOSS, MonsterType.BOSS,
                        MonsterType.NORMAL, MonsterType.NORMAL}
                : new MonsterType[] {MonsterType.BOSS, MonsterType.NORMAL, MonsterType.NORMAL};
        return new TdGame.Wave(composition, routeIndex, composition.length,
                BOSS_INTERVAL_SEC, BOSS_START_DELAY_SEC, hpMultiplier(waveNumber), WAVE_SPEED_MUL);
    }

    /** 飞行波：FLY 占多数并按生成顺序循环，逼玩家配置对空火力。 */
    private static TdGame.Wave flyWave(int waveNumber, TdGame.Difficulty difficulty, int routeIndex) {
        MonsterType[] composition = {
                MonsterType.FLY, MonsterType.FLY,
                FLY_ESCORT_POOL[(waveNumber / FLY_WAVE_PERIOD) % FLY_ESCORT_POOL.length]
        };
        return new TdGame.Wave(composition, routeIndex, regularCount(waveNumber, difficulty),
                spawnInterval(waveNumber), START_DELAY_SEC, hpMultiplier(waveNumber), WAVE_SPEED_MUL);
    }

    /** 常规波：2-3 种怪混编，组成随解锁池轮换，20+ 每 7 波偶发加入特殊机制怪。 */
    private static TdGame.Wave regularWave(int waveNumber, TdGame.Difficulty difficulty, int routeIndex) {
        MonsterType[] pool = unlockedPool(waveNumber);
        int kinds = Math.min(MIN_KINDS + waveNumber % 2, pool.length);
        MonsterType special = occasionalSpecial(waveNumber);
        MonsterType[] composition = new MonsterType[kinds + (special != null ? 1 : 0)];
        int cursor = 0;
        if (special != null) {
            // 特殊机制怪放首位：HUD 预览（previewName）以它开头，提示玩家优先处理
            composition[0] = special;
            cursor = 1;
        }
        boolean[] used = new boolean[pool.length];
        int pick = waveNumber; // 确定性游标：同参数永远得到同一组合
        for (int i = cursor; i < composition.length; i++) {
            while (used[pick % pool.length]) pick++;
            int chosen = pick % pool.length;
            used[chosen] = true;
            composition[i] = pool[chosen];
            pick += 2; // 步长 2，拉开相邻槽位的类型距离
        }
        return new TdGame.Wave(composition, routeIndex, regularCount(waveNumber, difficulty),
                spawnInterval(waveNumber), START_DELAY_SEC, hpMultiplier(waveNumber), WAVE_SPEED_MUL);
    }

    /** 按解锁门槛构建有序组成池：1-4 波仅 NORMAL/SWARM，之后按波次逐步追加。 */
    private static MonsterType[] unlockedPool(int waveNumber) {
        List<MonsterType> pool = new ArrayList<>();
        pool.add(MonsterType.NORMAL);
        pool.add(MonsterType.SWARM);
        if (waveNumber >= FAST_UNLOCK_WAVE) pool.add(MonsterType.FAST);
        if (waveNumber >= TANK_SHIELD_UNLOCK_WAVE) {
            pool.add(MonsterType.TANK);
            pool.add(MonsterType.SHIELD);
        }
        if (waveNumber >= HEALER_CHARGER_UNLOCK_WAVE) {
            pool.add(MonsterType.HEALER);
            pool.add(MonsterType.CHARGER);
        }
        if (waveNumber >= RESISTANT_RAGER_UNLOCK_WAVE) {
            pool.add(MonsterType.RESISTANT);
            pool.add(MonsterType.RAGER);
        }
        return pool.toArray(new MonsterType[0]);
    }

    /** 20+ 且每 7 波一次偶发特殊机制怪，按 (n/7)%3 在三种之间轮换；其余波返回 null。 */
    private static MonsterType occasionalSpecial(int waveNumber) {
        if (waveNumber < SPECIAL_UNLOCK_WAVE || waveNumber % SPECIAL_WAVE_PERIOD != 0) return null;
        return SPECIAL_POOL[(waveNumber / SPECIAL_WAVE_PERIOD) % SPECIAL_POOL.length];
    }

    /** 单波数量：随 n 线性增长，难度只微调系数，硬上限 60/波且至少 1。 */
    private static int regularCount(int waveNumber, TdGame.Difficulty difficulty) {
        float mul = difficulty == TdGame.Difficulty.EASY ? EASY_COUNT_MUL
                : difficulty == TdGame.Difficulty.HARD ? HARD_COUNT_MUL : NORMAL_COUNT_MUL;
        int count = Math.round((COUNT_BASE + COUNT_STEP_PER_WAVE * (waveNumber - 1)) * mul);
        return Math.max(1, Math.min(COUNT_CAP, count));
    }

    /** 出生间隔随 n 轻微收紧并设下限，保证深波次仍按帧节流生成。 */
    private static float spawnInterval(int waveNumber) {
        return Math.max(INTERVAL_FLOOR_SEC,
                INTERVAL_START_SEC - INTERVAL_TIGHTEN_PER_WAVE * (waveNumber - 1));
    }

    /**
     * 血量倍率 1.10^(n-1)，上限 40×。使用逐波累乘而非一次幂运算：
     * 累乘在 HotSpot/ART 上位级一致，且天然单调不减，确保跨端确定性。
     */
    private static float hpMultiplier(int waveNumber) {
        float mul = 1f;
        for (int i = 1; i < waveNumber && mul < HP_MUL_CAP; i++) {
            mul *= HP_GROWTH_PER_WAVE;
        }
        return Math.min(mul, HP_MUL_CAP);
    }
}
