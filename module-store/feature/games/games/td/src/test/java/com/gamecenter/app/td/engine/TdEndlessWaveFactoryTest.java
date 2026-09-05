package com.gamecenter.app.td.engine;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 无尽波次工厂确定性测试。
 *
 * <p>锁定三层契约：n=1..50 全扫不变量（组成非空、数量上限、路线取模、hp 单调有界、
 * 间隔下限）、周期规则（组成池解锁边界、飞行波、BOSS 波、特殊机制怪偶发）与
 * 同参数两次生成完全等价的确定性。
 */
public class TdEndlessWaveFactoryTest {

    private static final int MAX_SCAN_WAVE = 50;
    private static final int COUNT_CAP = 60;
    private static final float HP_MUL_CAP = 40f;
    private static final float EPS = 0.0001f;

    private static final Set<MonsterType> POOL_1_4 =
            setOf(MonsterType.NORMAL, MonsterType.SWARM);
    private static final Set<MonsterType> POOL_5_7 =
            setOf(MonsterType.NORMAL, MonsterType.SWARM, MonsterType.FAST);
    private static final Set<MonsterType> POOL_8_11 = setOf(
            MonsterType.NORMAL, MonsterType.SWARM, MonsterType.FAST,
            MonsterType.TANK, MonsterType.SHIELD);
    private static final Set<MonsterType> POOL_12_14 = setOf(
            MonsterType.NORMAL, MonsterType.SWARM, MonsterType.FAST,
            MonsterType.TANK, MonsterType.SHIELD, MonsterType.HEALER, MonsterType.CHARGER);
    private static final Set<MonsterType> POOL_15_PLUS = setOf(
            MonsterType.NORMAL, MonsterType.SWARM, MonsterType.FAST,
            MonsterType.TANK, MonsterType.SHIELD, MonsterType.HEALER, MonsterType.CHARGER,
            MonsterType.RESISTANT, MonsterType.RAGER);
    private static final Set<MonsterType> SPECIALS = setOf(
            MonsterType.SUMMONER, MonsterType.SPLITTER, MonsterType.SHIELD_GENERATOR);
    /** BOSS 波允许的构成：BOSS 本体 + NORMAL 护航 */
    private static final Set<MonsterType> BOSS_WAVE_ALLOWED =
            setOf(MonsterType.BOSS, MonsterType.NORMAL);

    // ===== 全扫不变量：n=1..50 × 三难度 × routeCount {1,3} =====

    @Test
    public void sweep_n1to50_numericInvariantsHold() {
        for (TdGame.Difficulty difficulty : TdGame.Difficulty.values()) {
            for (int routeCount : new int[] {1, 3}) {
                for (int n = 1; n <= MAX_SCAN_WAVE; n++) {
                    TdGame.Wave wave = TdEndlessWaveFactory.createWave(n, difficulty, routeCount);
                    assertNotNull("波次不得为 null: n=" + n, wave);
                    assertTrue("组成必须非空: n=" + n, wave.compositionTypes().length >= 1);
                    assertTrue("单波数量必须在 1.." + COUNT_CAP + " 内: n=" + n,
                            wave.count >= 1 && wave.count <= COUNT_CAP);
                    int expectedRoute = routeCount == 1 ? 0 : (n - 1) % routeCount;
                    assertEquals("路线必须按 (n-1)%routeCount 轮换: n=" + n,
                            expectedRoute, wave.routeIndex);
                    assertTrue("hp 倍率必须为正: n=" + n, wave.hpMul > 0f);
                    assertTrue("hp 倍率不得突破 " + HP_MUL_CAP + "× 上限: n=" + n,
                            wave.hpMul <= HP_MUL_CAP + EPS);
                    assertTrue("出生间隔必须达到下限: n=" + n, wave.intervalSec >= 0.35f - EPS);
                    assertEquals("速度不加波次系数（难度速度由引擎合成）: n=" + n,
                            1f, wave.speedMul, EPS);
                }
            }
        }
    }

    @Test
    public void sweep_n1to50_periodicRulesAndUnlockBoundariesHold() {
        for (int n = 1; n <= MAX_SCAN_WAVE; n++) {
            TdGame.Wave wave = TdEndlessWaveFactory.createWave(n, TdGame.Difficulty.NORMAL, 1);
            Set<MonsterType> types = typesOf(wave);
            if (isBossWave(n)) {
                assertTrue("BOSS 波必须含 BOSS: n=" + n, types.contains(MonsterType.BOSS));
                assertTrue("BOSS 波护航只能是 NORMAL: n=" + n,
                        BOSS_WAVE_ALLOWED.containsAll(types));
                int bossSlots = slotsOf(wave, MonsterType.BOSS);
                assertTrue("BOSS 每轮 1-2 只: n=" + n, bossSlots >= 1 && bossSlots <= 2);
                assertTrue("BOSS 波必须小规模: n=" + n, wave.count <= 4);
                assertFalse("BOSS 波不得混入飞行兵: n=" + n, types.contains(MonsterType.FLY));
                assertFalse("BOSS 波不得混入特殊机制怪: n=" + n, intersects(types, SPECIALS));
            } else if (isFlyWave(n)) {
                assertTrue("飞行波必须含 FLY: n=" + n, types.contains(MonsterType.FLY));
                assertTrue("飞行波必须 FLY 为主: n=" + n,
                        slotsOf(wave, MonsterType.FLY) > wave.compositionTypes().length
                                - slotsOf(wave, MonsterType.FLY));
                assertFalse("飞行波不得混入 BOSS: n=" + n, types.contains(MonsterType.BOSS));
                assertFalse("飞行波不得混入特殊机制怪: n=" + n, intersects(types, SPECIALS));
            } else {
                assertFalse("常规波不得混入 BOSS/飞行兵: n=" + n,
                        types.contains(MonsterType.BOSS) || types.contains(MonsterType.FLY));
                Set<MonsterType> unlockedPool = new HashSet<>(unlockedPoolFor(n));
                boolean specialScheduled = n >= 20 && n % 7 == 0;
                if (specialScheduled) unlockedPool.addAll(SPECIALS);
                assertTrue("常规波只能由已解锁池组成: n=" + n, unlockedPool.containsAll(types));
                if (specialScheduled) {
                    // 2-3 种常规混编 + 偶发加入的 1 种特殊机制怪
                    assertTrue("特殊波应为 3-4 种怪: n=" + n, types.size() >= 3 && types.size() <= 4);
                } else {
                    assertTrue("常规波必须 2-3 种怪混编: n=" + n, types.size() >= 2 && types.size() <= 3);
                }
                if (specialScheduled) {
                    assertTrue("每 7 波应偶发特殊机制怪: n=" + n, intersects(types, SPECIALS));
                } else {
                    assertFalse("特殊机制怪不得提前/超额投放: n=" + n, intersects(types, SPECIALS));
                }
            }
        }
    }

    // ===== hp 成长曲线 =====

    @Test
    public void hpMultiplier_monotonicNonDecreasing_boundedAndOneAtFirstWave() {
        for (TdGame.Difficulty difficulty : TdGame.Difficulty.values()) {
            float previous = 0f;
            for (int n = 1; n <= MAX_SCAN_WAVE; n++) {
                float hp = TdEndlessWaveFactory.createWave(n, difficulty, 1).hpMul;
                assertTrue("hp 倍率必须单调不减: n=" + n, hp >= previous - EPS);
                assertTrue("hp 倍率必须为正且有界: n=" + n, hp > 0f && hp <= HP_MUL_CAP + EPS);
                previous = hp;
            }
            assertEquals("首波不得自带成长", 1f,
                    TdEndlessWaveFactory.createWave(1, difficulty, 1).hpMul, EPS);
        }
        assertEquals("第二波成长斜率应为 1.10", 1.10f,
                TdEndlessWaveFactory.createWave(2, TdGame.Difficulty.NORMAL, 1).hpMul, EPS);
        assertEquals("深波次必须触及 40× 上限防溢出", HP_MUL_CAP,
                TdEndlessWaveFactory.createWave(60, TdGame.Difficulty.NORMAL, 1).hpMul, EPS);
    }

    // ===== 数量曲线与难度角色 =====

    @Test
    public void countGrowsLinearly_capsAt60_andDifficultyOnlyScalesCount() {
        assertEquals("首波 NORMAL 数量基数", 6,
                TdEndlessWaveFactory.createWave(1, TdGame.Difficulty.NORMAL, 1).count);
        assertEquals("首波 EASY 数量应下调", 5,
                TdEndlessWaveFactory.createWave(1, TdGame.Difficulty.EASY, 1).count);
        assertEquals("首波 HARD 数量应上调", 7,
                TdEndlessWaveFactory.createWave(1, TdGame.Difficulty.HARD, 1).count);
        // 无 BOSS 打断的窗口内数量严格线性增长
        int countAt2 = TdEndlessWaveFactory.createWave(2, TdGame.Difficulty.NORMAL, 1).count;
        int countAt9 = TdEndlessWaveFactory.createWave(9, TdGame.Difficulty.NORMAL, 1).count;
        assertTrue("数量必须随 n 线性增长", countAt9 > countAt2);
        for (TdGame.Difficulty difficulty : TdGame.Difficulty.values()) {
            assertEquals("数量上限 60/波对三难度都必须生效",
                    COUNT_CAP, TdEndlessWaveFactory.createWave(49, difficulty, 1).count);
            assertEquals("BOSS 波数量固定不受难度影响", 3,
                    TdEndlessWaveFactory.createWave(10, difficulty, 1).count);
            assertEquals("双 BOSS 波数量固定不受难度影响", 4,
                    TdEndlessWaveFactory.createWave(20, difficulty, 1).count);
        }
    }

    // ===== 路线轮换 =====

    @Test
    public void routeRotation_singleRouteAlwaysZero_multiRouteRotates() {
        for (int n = 1; n <= 30; n++) {
            assertEquals("单路线关恒为 0: n=" + n,
                    0, TdEndlessWaveFactory.createWave(n, TdGame.Difficulty.NORMAL, 1).routeIndex);
            assertEquals("三路线关按 (n-1)%3 轮换: n=" + n,
                    (n - 1) % 3, TdEndlessWaveFactory.createWave(n, TdGame.Difficulty.NORMAL, 3).routeIndex);
        }
    }

    // ===== 周期抽验（任务指定的样本波次） =====

    @Test
    public void scheduleSamples_matchDesignContract() {
        // n=5：解锁 FAST，仍未提前投放 8+ 档
        Set<MonsterType> t5 = typesOf(TdEndlessWaveFactory.createWave(5, TdGame.Difficulty.NORMAL, 1));
        assertTrue("第 5 波应解锁 FAST", t5.contains(MonsterType.FAST));
        assertFalse("第 5 波不得提前投放 TANK/SHIELD", intersects(t5, MonsterType.TANK, MonsterType.SHIELD));
        // n=8：解锁 TANK/SHIELD
        Set<MonsterType> t8 = typesOf(TdEndlessWaveFactory.createWave(8, TdGame.Difficulty.NORMAL, 1));
        assertTrue("第 8 波应解锁 TANK/SHIELD", intersects(t8, MonsterType.TANK, MonsterType.SHIELD));
        assertFalse("第 8 波不得提前投放 HEALER/CHARGER",
                intersects(t8, MonsterType.HEALER, MonsterType.CHARGER));
        // n=10：BOSS 波 = 1 BOSS + 2 NORMAL 护航
        TdGame.Wave boss = TdEndlessWaveFactory.createWave(10, TdGame.Difficulty.NORMAL, 1);
        assertEquals(1, slotsOf(boss, MonsterType.BOSS));
        assertEquals(2, slotsOf(boss, MonsterType.NORMAL));
        // n=12 / n=15：飞行波（n≥6 且为 3 的倍数）
        assertTrue("第 12 波应是飞行波", typesOf(TdEndlessWaveFactory.createWave(12, TdGame.Difficulty.NORMAL, 1))
                .contains(MonsterType.FLY));
        assertTrue("第 15 波应是飞行波", typesOf(TdEndlessWaveFactory.createWave(15, TdGame.Difficulty.NORMAL, 1))
                .contains(MonsterType.FLY));
        // n=20：双 BOSS 波 = 2 BOSS + 2 NORMAL 护航
        TdGame.Wave doubleBoss = TdEndlessWaveFactory.createWave(20, TdGame.Difficulty.NORMAL, 1);
        assertEquals(2, slotsOf(doubleBoss, MonsterType.BOSS));
        assertEquals(2, slotsOf(doubleBoss, MonsterType.NORMAL));
        // n=30：BOSS 波优先于飞行波，不得混入 FLY
        TdGame.Wave bossAt30 = TdEndlessWaveFactory.createWave(30, TdGame.Difficulty.NORMAL, 1);
        assertTrue(typesOf(bossAt30).contains(MonsterType.BOSS));
        assertFalse("BOSS 周期优先，第 30 波不得是飞行波", typesOf(bossAt30).contains(MonsterType.FLY));
        // n=16：解锁 RESISTANT/RAGER；n=13：解锁 HEALER/CHARGER
        Set<MonsterType> t16 = typesOf(TdEndlessWaveFactory.createWave(16, TdGame.Difficulty.NORMAL, 1));
        assertTrue("第 16 波应可投放 RESISTANT/RAGER", intersects(t16, MonsterType.RESISTANT, MonsterType.RAGER));
        Set<MonsterType> t13 = typesOf(TdEndlessWaveFactory.createWave(13, TdGame.Difficulty.NORMAL, 1));
        assertTrue("第 13 波应可投放 HEALER/CHARGER", intersects(t13, MonsterType.HEALER, MonsterType.CHARGER));
        // n=28 / n=35：偶发特殊机制怪（n=21 让位飞行波）
        assertTrue("第 28 波应偶发 SPLITTER", typesOf(TdEndlessWaveFactory.createWave(28, TdGame.Difficulty.NORMAL, 1))
                .contains(MonsterType.SPLITTER));
        assertTrue("第 35 波应偶发 SHIELD_GENERATOR",
                typesOf(TdEndlessWaveFactory.createWave(35, TdGame.Difficulty.NORMAL, 1))
                        .contains(MonsterType.SHIELD_GENERATOR));
    }

    // ===== 确定性 =====

    @Test
    public void sameInputs_alwaysProduceIdenticalWaves() {
        int[] samples = {1, 5, 6, 10, 12, 20, 21, 28, 30, 45, 50};
        for (int n : samples) {
            for (TdGame.Difficulty difficulty : TdGame.Difficulty.values()) {
                TdGame.Wave a = TdEndlessWaveFactory.createWave(n, difficulty, 3);
                TdGame.Wave b = TdEndlessWaveFactory.createWave(n, difficulty, 3);
                assertArrayEquals("组成必须逐位一致: n=" + n,
                        a.compositionTypes(), b.compositionTypes());
                assertEquals("数量必须一致: n=" + n, a.count, b.count);
                assertEquals("出生间隔必须一致: n=" + n, a.intervalSec, b.intervalSec, 0f);
                assertEquals("开场延迟必须一致: n=" + n, a.startDelaySec, b.startDelaySec, 0f);
                assertEquals("hp 倍率必须一致: n=" + n, a.hpMul, b.hpMul, 0f);
                assertEquals("速度倍率必须一致: n=" + n, a.speedMul, b.speedMul, 0f);
                assertEquals("路线必须一致: n=" + n, a.routeIndex, b.routeIndex);
            }
        }
    }

    // ===== 非法与空输入防御 =====

    @Test
    public void invalidAndNullInputs_areRejectedOrDefensivelyDefaulted() {
        assertThrows("波次序号必须从 1 起", IllegalArgumentException.class,
                () -> TdEndlessWaveFactory.createWave(0, TdGame.Difficulty.NORMAL, 1));
        assertThrows("负数波次序号必须拒绝", IllegalArgumentException.class,
                () -> TdEndlessWaveFactory.createWave(-7, TdGame.Difficulty.NORMAL, 3));
        // null 难度 → 防御性回退 NORMAL，与同参 NORMAL 生成严格等价
        TdGame.Wave fallback = TdEndlessWaveFactory.createWave(9, null, 2);
        TdGame.Wave normal = TdEndlessWaveFactory.createWave(9, TdGame.Difficulty.NORMAL, 2);
        assertArrayEquals(normal.compositionTypes(), fallback.compositionTypes());
        assertEquals(normal.count, fallback.count);
        assertEquals(normal.hpMul, fallback.hpMul, 0f);
        assertEquals(normal.intervalSec, fallback.intervalSec, 0f);
        assertEquals(normal.startDelaySec, fallback.startDelaySec, 0f);
        assertEquals(normal.routeIndex, fallback.routeIndex);
        // routeCount < 1 → 防御性按单路线处理，恒为 0
        assertEquals(0, TdEndlessWaveFactory.createWave(9, TdGame.Difficulty.NORMAL, 0).routeIndex);
        assertEquals(0, TdEndlessWaveFactory.createWave(9, TdGame.Difficulty.NORMAL, -5).routeIndex);
    }

    // ===== 周期判定辅助（与工厂常量一一对应） =====

    private static boolean isBossWave(int n) {
        return n % 10 == 0;
    }

    private static boolean isFlyWave(int n) {
        // n≥5 且为 3 的倍数，首个命中 n=6
        return n % 3 == 0 && n >= 6;
    }

    private static Set<MonsterType> unlockedPoolFor(int n) {
        if (n <= 4) return POOL_1_4;
        if (n <= 7) return POOL_5_7;
        if (n <= 11) return POOL_8_11;
        if (n <= 14) return POOL_12_14;
        return POOL_15_PLUS;
    }

    private static Set<MonsterType> typesOf(TdGame.Wave wave) {
        return new HashSet<>(Arrays.asList(wave.compositionTypes()));
    }

    private static int slotsOf(TdGame.Wave wave, MonsterType type) {
        int count = 0;
        for (MonsterType candidate : wave.compositionTypes()) {
            if (candidate == type) count++;
        }
        return count;
    }

    private static boolean intersects(Set<MonsterType> types, MonsterType... candidates) {
        for (MonsterType candidate : candidates) {
            if (types.contains(candidate)) return true;
        }
        return false;
    }

    private static boolean intersects(Set<MonsterType> types, Set<MonsterType> candidates) {
        for (MonsterType candidate : candidates) {
            if (types.contains(candidate)) return true;
        }
        return false;
    }

    private static Set<MonsterType> setOf(MonsterType... types) {
        return new HashSet<>(Arrays.asList(types));
    }
}
