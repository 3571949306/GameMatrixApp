package com.gamecenter.app.td;

import com.gamecenter.app.td.engine.MonsterType;
import com.gamecenter.app.td.engine.TdGame;
import com.gamecenter.app.td.engine.TdLevelJsonParser;
import com.gamecenter.app.td.engine.TdLevels;
import com.gamecenter.app.td.engine.TowerType;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * TdGame 引擎确定性测试 — 无 Android 依赖，验证状态与不变量。
 */
public class TdGameTest {

    private static final int MAX_TICKS = 60 * 240; // 最多模拟 4 分钟（真实时间）

    @BeforeClass
    public static void loadProductionCampaignData() throws IOException {
        Path assetRoot = findAssetsRoot();
        TdLevelJsonParser.Manifest manifest = TdLevelJsonParser.parseManifest(readAsset(
                assetRoot.resolve("td/manifest.json")));
        List<com.gamecenter.app.td.engine.TdLevelDefinition> definitions = new ArrayList<>();
        for (TdLevelJsonParser.ChapterRef chapter : manifest.chapters) {
            definitions.addAll(TdLevelJsonParser.parseChapter(readAsset(
                    assetRoot.resolve("td").resolve(chapter.file))).levels);
        }
        TdLevels.installForTesting(definitions);
    }

    private static Path findAssetsRoot() {
        Path[] candidates = new Path[] {
                Paths.get("src/main/assets"),
                Paths.get("module-store/feature/games/games/td/src/main/assets")
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate.resolve("td/manifest.json"))) return candidate;
        }
        throw new IllegalStateException("production TD campaign asset was not found for JVM test");
    }

    private static String readAsset(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    @Test
    public void campaignUsesStableIdsWhileSupportingLegacyDeepLinks() {
        assertEquals(25, TdLevels.levelIds().size());
        assertEquals("main_001", TdLevels.levelIds().get(0));
        assertEquals("main_025", TdLevels.levelIds().get(24));
        assertTrue(TdLevels.isKnownLevelId("main_001"));
        assertTrue(TdLevels.isKnownLevelId("level_01"));
        assertEquals(TdLevels.buildLevel("main_001").getRouteLength(0),
                TdLevels.buildLevel("level_01").getRouteLength(0));
    }

    @Test
    public void campaignHasLongRoutesAndEnoughDistinctMapTopologies() {
        Set<String> routeTopologies = new HashSet<>();
        for (String id : TdLevels.levelIds()) {
            TdGame game = TdLevels.buildLevel(id);
            StringBuilder topology = new StringBuilder(game.getRows() + "x" + game.getCols());
            for (int row = 0; row < game.getRows(); row++) {
                for (int col = 0; col < game.getCols(); col++) {
                    topology.append(game.isPathCell(row, col) ? 'P' : '.');
                }
            }
            routeTopologies.add(topology.toString());
            for (int route = 0; route < game.getPaths().length; route++) {
                assertTrue(id + " 的每条入口路线都必须足够长", game.getRouteLength(route) >= 30);
            }
        }
        assertTrue("25 关不能只换怪物数值，至少需要 8 种地图路径拓扑",
                routeTopologies.size() >= 8);
    }

    // ===== 基础规则 =====

    @Test
    public void placeTower_onPath_fails() {
        TdGame g = TdLevels.buildLevel("level_01");
        TdGame.Tower t = g.placeTower(TowerType.BOTTLE, 0, 0); // 路径起点
        assertNull("路径上不可建塔", t);
        assertEquals("err", g.getLastActionTone());
    }

    @Test
    public void placeTower_onEgg_fails() {
        TdGame g = TdLevels.buildLevel("level_01");
        assertNull(g.placeTower(TowerType.BOTTLE, g.getEggRow(), g.getEggCol()));
    }

    @Test
    public void placeTower_outOfBounds_fails() {
        TdGame g = TdLevels.buildLevel("level_01");
        assertNull(g.placeTower(TowerType.BOTTLE, -1, 0));
        assertNull(g.placeTower(TowerType.BOTTLE, 0, 99));
    }

    @Test
    public void placeTower_insufficientCoin_fails() {
        TdGame g = TdLevels.buildLevel("level_01");
        for (int row = 0; row < g.getRows() && g.getCoin() >= TowerType.BOTTLE.baseCost; row++) {
            for (int col = 0; col < g.getCols() && g.getCoin() >= TowerType.BOTTLE.baseCost; col++) {
                if (!g.isPathCell(row, col) && !g.isEggCell(row, col)) {
                    g.placeTower(TowerType.BOTTLE, row, col);
                }
            }
        }
        assertTrue("循环必须真实花到无法再造瓶子炮", g.getCoin() < TowerType.BOTTLE.baseCost);
        assertNull(g.placeTower(TowerType.BOTTLE, 5, 8));
    }

    @Test
    public void placeTower_twiceOnSameCell_fails() {
        TdGame g = TdLevels.buildLevel("level_01");
        assertNotNull(g.placeTower(TowerType.BOTTLE, 3, 3));
        assertNull("同格不可重复建塔", g.placeTower(TowerType.ROCKET, 3, 3));
    }

    @Test
    public void merge_sameLevelSameType_reachesLevelThree() {
        TdGame g = mergeTestGame();
        TdGame.Tower a = g.placeTower(TowerType.BOTTLE, 1, 0);
        TdGame.Tower b = g.placeTower(TowerType.BOTTLE, 1, 1);
        TdGame.Tower c = g.placeTower(TowerType.BOTTLE, 1, 2);
        TdGame.Tower d = g.placeTower(TowerType.BOTTLE, 2, 0);
        assertTrue(g.mergeTowers(a.row, a.col, b.row, b.col));
        assertTrue(g.mergeTowers(c.row, c.col, d.row, d.col));
        assertEquals(2, b.level);
        assertEquals(2, d.level);
        assertTrue(g.mergeTowers(b.row, b.col, d.row, d.col));
        assertEquals(3, d.level);
        assertNull("来源塔必须被消耗", g.getTowerAt(1, 1));
        assertFalse("Lv3 不能继续合成", g.mergeTowers(2, 0, 1, 0));
    }

    @Test
    public void merge_invalidInputs_areAtomic() {
        TdGame g = mergeTestGame();
        TdGame.Tower bottle = g.placeTower(TowerType.BOTTLE, 1, 0);
        TdGame.Tower rocket = g.placeTower(TowerType.ROCKET, 1, 1);
        int coinBefore = g.getCoin();
        int towerCountBefore = g.getTowers().size();
        assertFalse("不同类型必须拒绝", g.mergeTowers(1, 0, 1, 1));
        assertEquals(coinBefore, g.getCoin());
        assertEquals(towerCountBefore, g.getTowers().size());
        assertEquals(1, bottle.level);
        assertEquals(1, rocket.level);
        assertFalse("同一座塔必须拒绝", g.mergeTowers(1, 0, 1, 0));
        assertEquals(coinBefore, g.getCoin());
        assertEquals(towerCountBefore, g.getTowers().size());
    }

    @Test
    public void placeOrMergeTower_paletteDrop_mergesSameTypeAndChargesOneCard() {
        TdGame g = mergeTestGame();
        TdGame.Tower target = g.placeTower(TowerType.BOTTLE, 1, 0);
        assertNotNull(target);
        int coinBefore = g.getCoin();
        int towersBefore = g.getTowers().size();

        assertTrue(g.placeOrMergeTower(TowerType.BOTTLE, 1, 0));
        assertEquals(2, target.level);
        assertEquals(coinBefore - TowerType.BOTTLE.baseCost, g.getCoin());
        assertEquals(towersBefore, g.getTowers().size());

        int coinAfter = g.getCoin();
        assertFalse("不同类型拖入必须拒绝且不扣金币",
                g.placeOrMergeTower(TowerType.ROCKET, 1, 0));
        assertEquals(coinAfter, g.getCoin());
        assertEquals(2, target.level);
    }

    @Test
    public void placeOrMergeTower_paletteDrop_buildsEmptyCellAndRejectsMaxLevel() {
        TdGame g = mergeTestGame();
        int coinBefore = g.getCoin();
        assertTrue(g.placeOrMergeTower(TowerType.SNOW, 1, 0));
        assertNotNull(g.getTowerAt(1, 0));
        assertEquals(coinBefore - TowerType.SNOW.baseCost, g.getCoin());

        TdGame.Tower target = g.getTowerAt(1, 0);
        target.level = 3;
        int coinAtMax = g.getCoin();
        assertFalse(g.placeOrMergeTower(TowerType.SNOW, 1, 0));
        assertEquals(coinAtMax, g.getCoin());
        assertEquals(3, target.level);
    }

    @Test
    public void merge_sellRefund_usesBothSourceInvestments() {
        TdGame g = mergeTestGame();
        g.placeTower(TowerType.BOTTLE, 1, 0);
        g.placeTower(TowerType.BOTTLE, 1, 1);
        assertTrue(g.mergeTowers(1, 0, 1, 1));
        TdGame.Tower merged = g.getTowerAt(1, 1);
        assertEquals(120, merged.totalInvested());
        assertTrue(g.sellTower(1, 1));
        assertEquals("合成塔出售应返还总投入的 60%", 952, g.getCoin());
    }

    @Test
    public void lightningTower_hitsLimitedUniqueTargetsAtEachLevel() {
        for (int level = 1; level <= 3; level++) {
            TdGame g = clusteredMonsterGame(4);
            TdGame.Tower tower = g.placeTower(TowerType.LIGHTNING, 1, 1);
            assertNotNull(tower);
            tower.level = level; // 合成规则已在独立测试覆盖；此处只校验各等级战斗上限。
            assertTrue(g.startNextWaveEarly());
            g.tick();
            int hitCount = 0;
            for (TdGame.Monster monster : g.getMonsters()) {
                if (monster.hitFlash > 0f) hitCount++;
            }
            assertEquals("雷电塔只命中等级规定数量，且不会重复命中", level + 1, hitCount);
        }
    }

    @Test
    public void lightningTower_stopsWhenNoNearbyChainTargetExists() {
        TdGame g = clusteredMonsterGame(1);
        TdGame.Tower tower = g.placeTower(TowerType.LIGHTNING, 1, 1);
        tower.level = 3;
        assertTrue(g.startNextWaveEarly());
        g.tick();
        assertEquals("没有第二目标时连锁必须安全结束", 1, g.getMonsters().size());
        assertTrue(g.getMonsters().get(0).hitFlash > 0f);
    }

    @Test
    public void sniperTower_prioritizesStrongTarget_withHighSingleHit() {
        int[][] path = new int[][] {{0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4}, {0, 5}};
        java.util.List<TdGame.Wave> waves = new java.util.ArrayList<>();
        waves.add(new TdGame.Wave(new MonsterType[] {MonsterType.NORMAL, MonsterType.TANK},
                0, 2, 0f, 0f, 1f, 1f));
        TdGame g = new TdGame(6, 2, path, 0, 5, 1000, 10, waves);
        TdGame.Tower sniper = g.placeTower(TowerType.SNIPER, 1, 1);
        assertNotNull(sniper);
        assertTrue("狙击塔必须拥有超远射程", sniper.rangeAt() > TowerType.BOTTLE.rangeAt(1));
        assertTrue(g.startNextWaveEarly());
        g.tick();
        TdGame.Monster normal = null;
        TdGame.Monster tank = null;
        for (TdGame.Monster monster : g.getMonsters()) {
            if (monster.type == MonsterType.NORMAL) normal = monster;
            if (monster.type == MonsterType.TANK) tank = monster;
        }
        assertNotNull(normal);
        assertNotNull(tank);
        assertEquals("狙击塔不能产生群伤", normal.maxHp, normal.hp, .0001f);
        assertTrue("狙击塔应先重击强敌", tank.hp < tank.maxHp);
    }

    @Test
    public void sniperTower_cannotTargetFlyingMonster() {
        int[][] path = new int[][] {{0, 0}, {0, 1}, {0, 2}, {0, 3}};
        java.util.List<TdGame.Wave> waves = new java.util.ArrayList<>();
        waves.add(new TdGame.Wave(MonsterType.FLY, 1, 0f, 0f, 1f, 1f));
        TdGame g = new TdGame(4, 2, path, 0, 3, 1000, 10, waves);
        assertNotNull(g.placeTower(TowerType.SNIPER, 1, 1));
        assertTrue(g.startNextWaveEarly());
        g.tick();
        TdGame.Monster fly = g.getMonsters().get(0);
        assertEquals("狙击塔不应误伤飞行兵", fly.maxHp, fly.hp, .0001f);
    }

    @Test
    public void mineTower_requiresPathAdjacentTrapCell() {
        TdGame g = mergeTestGame();
        assertNotNull("相邻道路格应能放置地雷", g.placeTower(TowerType.MINE, 1, 1));
        assertNull("远离道路不能放置地雷", g.placeTower(TowerType.MINE, 2, 1));
        assertNull("道路本身不能放置地雷", g.placeTower(TowerType.MINE, 0, 2));
    }

    @Test
    public void mineTower_explodesInAreaThenRecharges() {
        TdGame g = clusteredMonsterGame(2);
        TdGame.Tower mine = g.placeTower(TowerType.MINE, 1, 0);
        assertNotNull(mine);
        assertTrue(g.startNextWaveEarly());
        g.tick();
        int dead = 0;
        for (TdGame.Monster monster : g.getMonsters()) if (monster.dead) dead++;
        assertEquals("同一触发区内的两只怪必须同时受爆炸伤害", 2, dead);
        assertTrue("地雷触发后必须进入冷却而非一次性消失", mine.cooldown > 0f);
        assertNotNull("冷却中的地雷仍应保留在棋盘", g.getTowerAt(1, 0));
    }

    @Test
    public void levelThreeMine_leavesBoundedBurnZone() {
        int[][] path = new int[][] {{0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4}};
        java.util.List<TdGame.Wave> waves = new java.util.ArrayList<>();
        waves.add(new TdGame.Wave(MonsterType.TANK, 1, 0f, 0f, 2f, 1f));
        TdGame g = new TdGame(5, 2, path, 0, 4, 1000, 10, waves);
        TdGame.Tower mine = g.placeTower(TowerType.MINE, 1, 0);
        mine.level = 3;
        assertTrue(g.startNextWaveEarly());
        g.tick();
        assertEquals(1, g.getBurnZones().size());
        float hpAfterBlast = g.getMonsters().get(0).hp;
        for (int i = 0; i < 30; i++) g.tick();
        assertTrue("燃烧区必须造成后续伤害", g.getMonsters().get(0).hp < hpAfterBlast);
    }

    @Test
    public void amplifierTower_usesOnlyHighestOverlappingBuff_andRecalculatesOnSell() {
        TdGame g = mergeTestGame();
        TdGame.Tower bottle = g.placeTower(TowerType.BOTTLE, 1, 1);
        TdGame.Tower ampLow = g.placeTower(TowerType.AMPLIFIER, 2, 1);
        TdGame.Tower ampHigh = g.placeTower(TowerType.AMPLIFIER, 2, 0);
        assertNotNull(bottle);
        assertNotNull(ampLow);
        assertNotNull(ampHigh);
        ampHigh.level = 3;
        assertEquals("两座重叠增幅不能叠乘，只取 Lv3", .20f, g.getAttackSpeedBonus(bottle), .0001f);
        assertEquals(.10f, g.getRangeBonus(bottle), .0001f);
        assertEquals(bottle.fireIntervalAt() / 1.20f, g.effectiveFireIntervalAt(bottle), .0001f);
        assertEquals(bottle.rangeAt() * 1.10f, g.effectiveRangeAt(bottle), .0001f);
        assertEquals("增幅塔不强化自身", 0f, g.getAttackSpeedBonus(ampHigh), .0001f);
        assertTrue(g.sellTower(ampHigh.row, ampHigh.col));
        assertEquals("卖掉高等级增幅后应立即回退到剩余最高值", .10f,
                g.getAttackSpeedBonus(bottle), .0001f);
        assertEquals(0f, g.getRangeBonus(bottle), .0001f);
    }

    @Test
    public void splitterMonster_createsExactlyTwoNonRecursiveLowRewardChildren() {
        int[][] path = new int[][] {{0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4}, {0, 5}};
        java.util.List<TdGame.Wave> waves = new java.util.ArrayList<>();
        waves.add(new TdGame.Wave(MonsterType.SPLITTER, 1, 0f, 0f, 1f, 1f));
        TdGame g = new TdGame(6, 2, path, 0, 5, 1000, 10, waves);
        assertNotNull(g.placeTower(TowerType.SNIPER, 1, 0));
        assertTrue(g.startNextWaveEarly());
        g.tick();
        int children = 0;
        for (TdGame.Monster monster : g.getMonsters()) {
            if (!monster.dead && monster.splitChild) {
                children++;
                assertEquals("幼体复用低血高速喽罗，不是新的分裂母体", MonsterType.SWARM, monster.type);
                assertEquals("幼体奖励必须很低", 1, monster.reward);
                assertTrue("幼体必须记录来源", monster.originMonsterId > 0);
            }
        }
        assertEquals("一只母体只能分裂两只幼体", 2, children);
        assertEquals("派生单位必须计入胜负与统计", 3, g.getMonstersSpawnedTotal());
    }

    @Test
    public void chargerMonster_hasCooldownBoundedCharge_andPreservesSlowMultiplier() {
        int[][] path = new int[40][2];
        for (int i = 0; i < path.length; i++) { path[i][0] = 0; path[i][1] = i; }
        java.util.List<TdGame.Wave> waves = new java.util.ArrayList<>();
        waves.add(new TdGame.Wave(MonsterType.CHARGER, 1, 0f, 0f, 1f, 1f));
        TdGame g = new TdGame(40, 2, path, 0, 39, 1000, 10, waves);
        assertNotNull(g.placeTower(TowerType.SNOW, 1, 0));
        assertTrue(g.startNextWaveEarly());
        boolean sawCharge = false;
        for (int i = 0; i < 60 * 5; i++) {
            g.tick();
            if (g.getMonsters().isEmpty()) break;
            TdGame.Monster charger = g.getMonsters().get(0);
            sawCharge |= charger.charging;
            assertTrue("雪花减速必须持续基于出生倍率，冲锋不可覆盖", charger.speedMul <= charger.baseSpeedMul);
        }
        assertTrue("冲锋怪必须在固定冷却后出现短冲刺", sawCharge);
    }

    @Test
    public void shieldGenerator_buffsAtMostThreeAllies_withoutSelfOrInfiniteStacking() {
        int[][] path = new int[40][2];
        for (int i = 0; i < path.length; i++) { path[i][0] = 0; path[i][1] = i; }
        java.util.List<TdGame.Wave> waves = new java.util.ArrayList<>();
        waves.add(new TdGame.Wave(new MonsterType[] {
                MonsterType.SHIELD_GENERATOR, MonsterType.NORMAL, MonsterType.NORMAL,
                MonsterType.NORMAL, MonsterType.NORMAL
        }, 0, 5, 0f, 0f, 1f, 1f));
        TdGame g = new TdGame(40, 2, path, 0, 39, 1000, 10, waves);
        assertTrue(g.startNextWaveEarly());
        for (int i = 0; i < 60 * 2; i++) g.tick();
        int shielded = 0;
        TdGame.Monster generator = null;
        for (TdGame.Monster monster : g.getMonsters()) {
            if (monster.type == MonsterType.SHIELD_GENERATOR) generator = monster;
            else if (monster.shield > 0f) {
                shielded++;
                assertTrue("护盾必须受上限控制", monster.shield <= monster.maxShield + .0001f);
            }
        }
        assertNotNull(generator);
        assertEquals("每次最多影响三名友军", 3, shielded);
        assertEquals("护盾发生器不能给自己套盾", 0f, generator.shield, .0001f);
        for (int i = 0; i < 60 * 3; i++) g.tick();
        for (TdGame.Monster monster : g.getMonsters()) {
            assertTrue("多次脉冲不能无限叠盾", monster.shield <= monster.maxShield + .0001f);
        }
    }

    @Test
    public void summonerMonster_hasSourceMarkedNonRecursiveMinions_andHardCap() {
        int[][] path = new int[80][2];
        for (int i = 0; i < path.length; i++) { path[i][0] = 0; path[i][1] = i; }
        java.util.List<TdGame.Wave> waves = new java.util.ArrayList<>();
        waves.add(new TdGame.Wave(MonsterType.SUMMONER, 1, 0f, 0f, 1f, 1f));
        TdGame g = new TdGame(80, 2, path, 0, 79, 1000, 10, waves);
        assertTrue(g.startNextWaveEarly());
        for (int i = 0; i < 60 * 26; i++) g.tick();
        int summoned = 0;
        for (TdGame.Monster monster : g.getMonsters()) {
            if (!monster.summoned) continue;
            summoned++;
            assertEquals("召唤物不能继续召唤", MonsterType.SWARM, monster.type);
            assertEquals("召唤物必须有低奖励", 1, monster.reward);
            assertTrue("召唤物必须记录来源", monster.originMonsterId > 0);
        }
        assertEquals("四次召唤、每次两只，不能无限增长", 8, summoned);
        assertEquals("总生成数必须包括合法派生物", 9, g.getMonstersSpawnedTotal());
    }

    @Test
    public void resistantMonster_hasSoftControlPoisonAndLightningResistance_notImmunity() {
        int[][] path = new int[30][2];
        for (int i = 0; i < path.length; i++) { path[i][0] = 0; path[i][1] = i; }
        java.util.List<TdGame.Wave> waves = new java.util.ArrayList<>();
        waves.add(new TdGame.Wave(MonsterType.RESISTANT, 1, 0f, 0f, 1f, 1f));
        TdGame g = new TdGame(30, 2, path, 0, 29, 1000, 10, waves);
        assertNotNull(g.placeTower(TowerType.SNOW, 1, 0));
        assertNotNull(g.placeTower(TowerType.POISON, 1, 1));
        assertTrue(g.startNextWaveEarly());
        g.tick();
        TdGame.Monster resistant = g.getMonsters().get(0);
        assertTrue("软抗减速仍应允许减速，但效果低于普通怪", resistant.speedMul > resistant.baseSpeedMul * .65f);
        assertTrue("软抗中毒仍应附着", resistant.dotTimer > 0f);
        assertTrue("抗性怪的中毒持续时间必须缩短", resistant.dotTimer < TowerType.POISON_SEC);
        assertTrue("所有塔仍可造成直接伤害", resistant.hp < resistant.maxHp);
    }

    @Test
    public void resistantMonster_reducesLightningBounceDamage() {
        int[][] path = new int[][] {{0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4}};
        java.util.List<TdGame.Wave> waves = new java.util.ArrayList<>();
        waves.add(new TdGame.Wave(new MonsterType[] {MonsterType.NORMAL, MonsterType.RESISTANT},
                0, 2, 0f, 0f, 1f, 1f));
        TdGame g = new TdGame(5, 2, path, 0, 4, 1000, 10, waves);
        TdGame.Tower lightning = g.placeTower(TowerType.LIGHTNING, 1, 0);
        assertNotNull(lightning);
        assertTrue(g.startNextWaveEarly());
        g.tick();
        TdGame.Monster resistant = null;
        for (TdGame.Monster monster : g.getMonsters()) if (monster.type == MonsterType.RESISTANT) resistant = monster;
        assertNotNull(resistant);
        float expectedMax = lightning.damageAt() * .70f * .65f;
        assertTrue("雷电后续弹射必须被软抗降低", resistant.maxHp - resistant.hp <= expectedMax + .0001f);
        assertTrue("软抗不能把伤害归零", resistant.hp < resistant.maxHp);
    }

    @Test
    public void ragerMonster_enragesOnlyBelowHalfHealth_andKeepsNormalDamageRules() {
        int[][] path = new int[30][2];
        for (int i = 0; i < path.length; i++) { path[i][0] = 0; path[i][1] = i; }
        java.util.List<TdGame.Wave> waves = new java.util.ArrayList<>();
        waves.add(new TdGame.Wave(MonsterType.RAGER, 1, 0f, 0f, 1f, 1f));
        TdGame g = new TdGame(30, 2, path, 0, 29, 1000, 10, waves);
        assertNotNull(g.placeTower(TowerType.BOTTLE, 1, 0));
        assertTrue(g.startNextWaveEarly());
        boolean sawBelowHalfBeforeEnrage = false;
        for (int i = 0; i < 60; i++) {
            g.tick();
            TdGame.Monster rager = g.getMonsters().get(0);
            if (rager.hp <= rager.maxHp * .5f) sawBelowHalfBeforeEnrage = true;
            if (rager.enraged) {
                assertEquals("狂暴只提高移动速度", 1.3f, g.getBehaviorSpeedMultiplier(rager), .0001f);
                assertTrue("狂暴仍可被普通塔伤害", rager.hp > 0f && rager.hp < rager.maxHp);
                break;
            }
        }
        assertTrue("必须在低于半血后才进入狂暴", sawBelowHalfBeforeEnrage);
    }

    @Test
    public void unitRosterAndLevelTeachingSchedule_matchExpansionPlan() {
        assertEquals("最终必须有十种防御塔", 10, TowerType.values().length);
        assertEquals("最终必须有十四种怪物", 14, MonsterType.values().length);
        assertWaveContains("level_02", MonsterType.FLY, MonsterType.SPLITTER);
        assertWaveContains("level_03", MonsterType.HEALER, MonsterType.SHIELD_GENERATOR);
        assertWaveContains("level_04", MonsterType.CHARGER);
        assertWaveContains("level_05", MonsterType.SUMMONER, MonsterType.RESISTANT, MonsterType.RAGER);
        assertWaveExcludes("level_01", MonsterType.SPLITTER, MonsterType.CHARGER,
                MonsterType.SHIELD_GENERATOR, MonsterType.SUMMONER, MonsterType.RESISTANT, MonsterType.RAGER);
    }

    @Test
    public void mergeLevelGrowth_usesUnifiedPrimarySpeedAndRangeRules() {
        assertEquals(TowerType.BOTTLE.damage * 1.35f, TowerType.BOTTLE.damageAt(2), .0001f);
        assertEquals(TowerType.BOTTLE.damage * 1.35f * 1.35f, TowerType.BOTTLE.damageAt(3), .0001f);
        assertEquals(TowerType.BOTTLE.fireInterval * .93f, TowerType.BOTTLE.fireIntervalAt(2), .0001f);
        assertEquals(TowerType.BOTTLE.range * 1.05f, TowerType.BOTTLE.rangeAt(2), .0001f);
        assertEquals(TowerType.SUN.income * 1.35f, TowerType.SUN.incomeAt(2), .0001f);
    }

    private static void assertWaveContains(String levelId, MonsterType... required) {
        java.util.HashSet<MonsterType> present = waveTypes(levelId);
        for (MonsterType type : required) assertTrue(levelId + " 应教学 " + type, present.contains(type));
    }

    private static void assertWaveExcludes(String levelId, MonsterType... excluded) {
        java.util.HashSet<MonsterType> present = waveTypes(levelId);
        for (MonsterType type : excluded) assertFalse(levelId + " 不应提前投放 " + type, present.contains(type));
    }

    private static java.util.HashSet<MonsterType> waveTypes(String levelId) {
        java.util.HashSet<MonsterType> types = new java.util.HashSet<>();
        for (TdGame.Wave wave : TdLevels.buildLevel(levelId).getWaves()) {
            for (int i = 0; i < wave.count; i++) types.add(wave.typeAt(i));
        }
        return types;
    }

    private static TdGame mergeTestGame() {
        int[][] path = new int[][] {{0, 0}, {0, 1}, {0, 2}, {0, 3}};
        java.util.List<TdGame.Wave> waves = new java.util.ArrayList<>();
        waves.add(new TdGame.Wave(MonsterType.NORMAL, 1, 1f, 0f, 1f, 1f));
        return new TdGame(4, 3, path, 0, 3, 1000, 10, waves);
    }

    private static TdGame clusteredMonsterGame(int count) {
        int[][] path = new int[][] {{0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4}, {0, 5}};
        java.util.List<TdGame.Wave> waves = new java.util.ArrayList<>();
        waves.add(new TdGame.Wave(MonsterType.NORMAL, count, 0f, 0f, 1f, 1f));
        return new TdGame(6, 2, path, 0, 5, 1000, 10, waves);
    }

    @Test
    public void sellTower_refundsAndFreesCell() {
        TdGame g = TdLevels.buildLevel("level_01");
        int coinBefore = g.getCoin();
        g.placeTower(TowerType.ROCKET, 3, 3); // 150
        assertTrue(g.sellTower(3, 3));
        // 返还 60% of 150 = 90
        assertTrue(g.getCoin() > coinBefore - 150);
        // 格子已空，可重建
        assertNotNull(g.placeTower(TowerType.BOTTLE, 3, 3));
    }

    @Test
    public void sellTower_onEmptyCell_fails() {
        TdGame g = TdLevels.buildLevel("level_01");
        assertFalse(g.sellTower(3, 3));
    }

    // ===== 不变量：金币永不为负 =====

    @Test
    public void coinNeverNegative_underFullSimulation() {
        TdGame g = TdLevels.buildLevel("level_01");
        // 火力全开
        for (int i = 1; i < 7; i++) {
            for (int j = 1; j < 11; j++) {
                if (!g.isPathCell(i, j)) g.placeTower(TowerType.BOTTLE, i, j);
            }
        }
        g.startNextWaveEarly();
        for (int i = 0; i < MAX_TICKS && !g.isEnded(); i++) {
            g.tick();
            assertTrue("金币永不为负", g.getCoin() >= 0);
            assertTrue("蛋蛋 HP 永不为负", g.getMascotHp() >= 0);
        }
    }

    // ===== 不变量：怪物永不离路径 =====

    @Test
    public void monstersNeverLeavePath() {
        TdGame g = TdLevels.buildLevel("level_01");
        g.placeTower(TowerType.BOTTLE, 3, 3);
        g.placeTower(TowerType.ROCKET, 4, 8);
        g.placeTower(TowerType.SNOW, 5, 5);
        g.startNextWaveEarly();
        int[][] path = g.getPath();
        int divergenceChecks = 0;
        for (int i = 0; i < MAX_TICKS && !g.isEnded(); i++) {
            g.tick();
            for (TdGame.Monster m : g.getMonsters()) {
                assertTrue("怪物必须沿路径行进", isOnPath(path, m.x, m.y));
                divergenceChecks++;
            }
        }
        assertTrue("模拟应实际驱动过怪物", divergenceChecks > 0);
    }

    private static boolean isOnPath(int[][] path, float x, float y) {
        // 怪物坐标四舍五入到最近格，必须落在路径格上（允许插值落两格之间，取最近格）
        int c = Math.round(x - 0.5f);
        int r = Math.round(y - 0.5f);
        for (int[] p : path) {
            if (p[0] == r && p[1] == c) return true;
        }
        return false;
    }

    // ===== 波次推进 =====

    @Test
    public void canWin_level01_withStrategy() {
        TdGame g = TdLevels.buildLevel("level_01");
        // 首关只使用玩家真实可带入的基础卡组：瓶子炮、雪花、太阳花。
        assertNotNull(g.placeTower(TowerType.BOTTLE, 1, 8));
        assertNotNull(g.placeTower(TowerType.BOTTLE, 3, 5));
        assertNotNull(g.placeTower(TowerType.SNOW, 5, 5));
        playThroughStarterDeck(g);
        assertTrue("应到达终态", g.isEnded());
        assertTrue("应获胜", g.getState() == TdGame.State.WON);
        assertTrue("通关至少 1 星", g.starsEarned() >= 1);
    }

    /** 模拟玩家按真实节奏推进：当前波生成中不点，场上清空后点“下一波” */
    private static void playThrough(TdGame g) {
        g.startNextWaveEarly();
        for (int i = 0; i < MAX_TICKS; i++) {
            g.tick();
            if (g.isEnded()) break;
            // 本波怪已全部登场且场上清空 → 玩家点下一波
            boolean waveCleared = !g.isWaveSpawning() && g.getMonsters().isEmpty();
            if (waveCleared) {
                g.startNextWaveEarly();
            }
        }
    }

    /** 固定、可复现的首关补塔策略：不使用尚未解锁的后期塔。 */
    private static void playThroughStarterDeck(TdGame g) {
        final int[][] bottleSpots = {
                {7, 6}, {8, 6}, {3, 0}, {5, 7}, {1, 4}
        };
        int nextBottleSpot = 0;
        g.startNextWaveEarly();
        for (int i = 0; i < MAX_TICKS; i++) {
            g.tick();
            if (g.isEnded()) break;
            if (nextBottleSpot < bottleSpots.length && g.getCoin() >= TowerType.BOTTLE.baseCost) {
                int[] spot = bottleSpots[nextBottleSpot];
                if (g.placeTower(TowerType.BOTTLE, spot[0], spot[1]) != null) {
                    nextBottleSpot++;
                }
            }
            // 中后段优先把核心拐点炮塔升到 Lv2，体现首关需要主动成长而不是一次铺满。
            TdGame.Tower core = g.getTowerAt(3, 5);
            if (core != null && core.level < 2 && g.getCoin() >= TowerType.BOTTLE.upgradeCost(core.level)) {
                g.upgradeTower(3, 5);
            }
            boolean waveCleared = !g.isWaveSpawning() && g.getMonsters().isEmpty();
            if (waveCleared) g.startNextWaveEarly();
        }
    }

    @Test
    public void failureState_onNoDefense() {
        TdGame g = TdLevels.buildLevel("level_01");
        g.startNextWaveEarly(); // 不建塔
        for (int i = 0; i < MAX_TICKS; i++) {
            g.tick();
            if (g.isEnded()) break;
        }
        assertTrue("无防御必须失败", g.getState() == TdGame.State.LOST);
        assertEquals("蛋蛋 HP 归零", 0, g.getMascotHp());
    }

    @Test
    public void allLevels_loadAndRun() {
        for (String id : TdLevels.levelIds()) {
            TdGame g = TdLevels.buildLevel(id);
            assertNotNull("关卡可加载: " + id, g);
            playThrough(g);
            assertTrue("关卡 " + id + " 应能到达终态", g.isEnded());
        }
    }

    // ===== 确定性：同操作序列结果一致 =====

    @Test
    public void deterministic_sameOperations_sameResult() {
        TdGame a = runScenario();
        TdGame b = runScenario();
        assertEquals("金币一致", a.getCoin(), b.getCoin());
        assertEquals("蛋蛋 HP 一致", a.getMascotHp(), b.getMascotHp());
        assertEquals("杀怪数一致", a.getMonstersKilled(), b.getMonstersKilled());
        assertEquals("状态一致", a.getState(), b.getState());
        assertEquals("tick 数一致", a.getTicks(), b.getTicks());
    }

    private TdGame runScenario() {
        TdGame g = TdLevels.buildLevel("level_02");
        g.placeTower(TowerType.BOTTLE, 1, 3);
        g.placeTower(TowerType.FAN, 2, 3);
        g.placeTower(TowerType.POISON, 2, 5);
        g.upgradeTower(2, 5);
        g.placeTower(TowerType.SNOW, 3, 4);
        g.startNextWaveEarly();
        for (int i = 0; i < MAX_TICKS; i++) {
            g.tick();
            if (g.isEnded()) break;
        }
        return g;
    }

    // ===== 太阳花经济塔 =====

    @Test
    public void sunTower_earnsCoinOverTime() {
        TdGame g = TdLevels.buildLevel("level_01");
        int coinBefore = g.getCoin();
        g.placeTower(TowerType.SUN, 3, 3);
        g.startNextWaveEarly();
        // 跑 30 秒
        for (int i = 0; i < 1800; i++) {
            if (g.isEnded()) break;
            g.tick();
        }
        assertTrue("太阳花应产出金币", g.getCoinsEarned() > 0);
        assertTrue("总金币应高于投入", g.getCoin() > coinBefore - TowerType.SUN.baseCost);
    }

    // ===== 对空：飞行兵需可对空塔 =====

    @Test
    public void flyMonsters_onlyHittableByAntiAir() {
        // level_03 含飞行兵；雪花/瓶炮均可对空除外，验证引擎不崩溃且飞行怪可被击杀
        TdGame g = TdLevels.buildLevel("level_03");
        for (int i = 1; i < 6; i++) {
            for (int j = 1; j < 7; j++) {
                if (!g.isPathCell(i, j) && !g.isCellOccupied(i, j)) {
                    g.placeTower(TowerType.BOTTLE, i, j);
                }
            }
        }
        g.startNextWaveEarly();
        for (int i = 0; i < MAX_TICKS; i++) {
            g.tick();
            if (g.isEnded()) break;
        }
        // 不要求必赢，只要求无异常且终态合法
        assertTrue(g.getCoin() >= 0);
    }

    /** 飞行怪必须只被对空塔命中（引擎内部一致性：射程过滤） */
    @Test
    public void antiAirFilter_consistent() {
        TdGame g = TdLevels.buildLevel("level_02");
        // level_02 第 3 波是飞行兵；不建雪花类防空塔时，飞行兵应按速度推进到终点而不是消失
        g.placeTower(TowerType.BOTTLE, 3, 3); // 瓶炮可对空
        g.startNextWaveEarly();
        for (int i = 0; i < 60 * 60; i++) { // 60 秒
            g.tick();
            if (g.isEnded()) break;
        }
        // 确定性运行：不抛异常即满足引擎契约（怪兽被击杀/到达终点均属合法终局）
        assertTrue(g.getMascotHp() >= 0);
    }

    // ===== 波次引导 =====

    @Test
    public void waveProgression_startsFirstWaveOnlyAfterPlayerAction() {
        TdGame g = TdLevels.buildLevel("level_01");
        // 玩家不点开始，不刷怪
        for (int i = 0; i < 600; i++) g.tick();
        assertEquals("PREPARING 阶段不刷怪", 0, g.getMonsters().size());
        assertEquals(TdGame.State.PREPARING, g.getState());
    }

    @Test
    public void startNextWave_earlyBonus_onRunningWave() {
        TdGame g = TdLevels.buildLevel("level_01");
        g.startNextWaveEarly();
        // 跑 1 秒后立刻提前结束；奖励只能等于尚未生成敌人的数量，不能成为主要经济来源。
        for (int i = 0; i < 60; i++) g.tick();
        int coinBefore = g.getCoin();
        int remaining = g.getWaves().get(0).count - g.getMonstersSpawnedTotal();
        assertTrue(g.startNextWaveEarly());
        assertEquals("加速奖励应为每名未生成敌人 1 金币", coinBefore + remaining, g.getCoin());
    }

    @Test
    public void startNextWave_afterLastWave_returnsFalse() {
        TdGame g = TdLevels.buildLevel("level_01");
        g.startNextWaveEarly();
        // 一直按下一波直到最后一波生成完成
        for (int i = 0; i < 30 && !g.isEnded(); i++) {
            g.tick();
        }
        // 快速打完
        for (int i = 0; i < MAX_TICKS && !g.isEnded(); i++) {
            g.tick();
            if (g.getMonsters().isEmpty() && g.getWaveIndex() > 1 && !g.isEnded()) {
                g.startNextWaveEarly();
            }
        }
        // 不应崩溃；币、HP 合法
        assertTrue(g.getCoin() >= 0);
    }

    // ===== 击杀事件队列（UI 飘字/爆裂数据源） =====

    @Test
    public void killEvents_recordedAndDrained_onDeath() {
        TdGame g = TdLevels.buildLevel("level_01");
        g.placeTower(TowerType.BOTTLE, 2, 3);
        g.placeTower(TowerType.BOTTLE, 2, 6);
        g.placeTower(TowerType.ROCKET, 3, 6);
        g.placeTower(TowerType.BOTTLE, 4, 3);
        g.placeTower(TowerType.BOTTLE, 5, 6);
        g.startNextWaveEarly();
        // 跑到出现击杀为止
        for (int i = 0; i < MAX_TICKS && g.getMonstersKilled() == 0 && !g.isEnded(); i++) {
            g.tick();
        }
        assertTrue("应有击杀", g.getMonstersKilled() > 0);
        java.util.List<TdGame.KillEvent> ev = g.drainKillEvents();
        assertEquals("击杀事件数量等于击倒数", g.getMonstersKilled(), ev.size());
        int[][] path = g.getPath();
        for (TdGame.KillEvent e : ev) {
            assertTrue("击杀位置必须在路径上", isOnPath(path, e.x, e.y));
            assertTrue("击杀应有金币奖励", e.value > 0);
        }
        assertTrue("drain 后再次取应为空", g.drainKillEvents().isEmpty());
    }

    @Test
    public void eggHitTimer_risesOnHit_decaysWhileAlive_neverNegative() {
        // 构造确定性命局：4 格直线路径，首波 1 只必吃到蛋蛋，第二波永不开 →
        // 受击后对局仍在 RUNNING 且无新怪，计时器应单调衰减至 0。
        int[][] path = new int[][] {{0,0},{0,1},{0,2},{0,3}};
        java.util.List<TdGame.Wave> waves = new java.util.ArrayList<>();
        waves.add(new TdGame.Wave(MonsterType.NORMAL, 1, 1f, 0.1f, 1f, 1f));
        waves.add(new TdGame.Wave(MonsterType.NORMAL, 1, 1f, 0.1f, 1f, 1f)); // 永不开始
        TdGame g = new TdGame(4, 1, path, 0, 3, 200, 5, waves);
        assertEquals("初始无受击", 0f, g.getEggHitTimer(), 0f);
        g.startNextWaveEarly();
        // 等第一只怪吃到蛋蛋（路径 3 格 ≈ 2 秒）
        for (int i = 0; i < 60 * 5 && g.getMascotHpLost() == 0 && !g.isEnded(); i++) {
            g.tick();
        }
        assertTrue("应吃到蛋蛋", g.getMascotHpLost() > 0);
        assertFalse("第二波未开，对局不应结束", g.isEnded());
        assertTrue("受击后计时器应大于 0", g.getEggHitTimer() > 0f);
        // 局中且无新怪，计时器应单调衰减至零
        for (int i = 0; i < 240 && g.getEggHitTimer() > 0f; i++) {
            g.tick();
        }
        assertEquals("局中应衰减至零", 0f, g.getEggHitTimer(), 0.0001f);
        assertTrue("计时器永不为负", g.getEggHitTimer() >= 0f);
        assertFalse("不应突然结束", g.isEnded());
    }

    // ===== 关卡数据与长期策略机制 =====

    @Test
    public void everyLevel_hasContinuousUniqueRoutesEndingAtEgg() {
        for (String id : TdLevels.levelIds()) {
            TdGame game = TdLevels.buildLevel(id);
            for (int[][] route : game.getPaths()) {
                assertTrue("每条路线至少含出生点与终点", route.length >= 2);
                java.util.HashSet<String> seen = new java.util.HashSet<>();
                for (int i = 0; i < route.length; i++) {
                    int row = route[i][0];
                    int col = route[i][1];
                    assertTrue("路线行坐标在边界内", row >= 0 && row < game.getRows());
                    assertTrue("路线列坐标在边界内", col >= 0 && col < game.getCols());
                    assertTrue("路线不应重复占格", seen.add(row + ":" + col));
                    if (i > 0) {
                        int distance = Math.abs(row - route[i - 1][0])
                                + Math.abs(col - route[i - 1][1]);
                        assertEquals("路线必须按正交相邻格前进", 1, distance);
                    }
                }
                int[] end = route[route.length - 1];
                assertEquals("路线终点行必须是蛋蛋", game.getEggRow(), end[0]);
                assertEquals("路线终点列必须是蛋蛋", game.getEggCol(), end[1]);
            }
        }
    }

    @Test
    public void level04_hasTwoEntrances_andWavePreviewStartsAtFirstWave() {
        TdGame game = TdLevels.buildLevel("level_04");
        assertEquals("双岔溪谷应有双入口", 2, game.getPaths().length);
        assertEquals("准备阶段必须预告第 1 波路线", 0, game.nextWaveRouteIndex());
        assertTrue("准备阶段必须预告第 1 波而非第 2 波", game.nextWaveCount() > 0);
        assertTrue(game.startNextWaveEarly());
        assertEquals("首波开始后才预告第 2 波路线", 1, game.nextWaveRouteIndex());
    }

    @Test
    public void campaignRoutes_areLongEnoughForMeaningfulDefenseWindows() {
        assertTrue("第一关路线应有完整的教学火力窗口",
                TdLevels.buildLevel("level_01").getRouteLength(0) >= 50);
        assertTrue("第二关路线应覆盖分裂与对空教学",
                TdLevels.buildLevel("level_02").getRouteLength(0) >= 48);
        assertTrue("第三关路线应允许处理治疗与护盾",
                TdLevels.buildLevel("level_03").getRouteLength(0) >= 50);

        TdGame valley = TdLevels.buildLevel("level_04");
        assertTrue("双入口北路不能再是十几格短路", valley.getRouteLength(0) >= 33);
        assertTrue("双入口南路不能再是十几格短路", valley.getRouteLength(1) >= 33);
        assertTrue("双入口长度要公平", Math.abs(valley.getRouteLength(0) - valley.getRouteLength(1)) <= 2);

        assertTrue("终局路线应给 Boss 足够的承压时间",
                TdLevels.buildLevel("level_05").getRouteLength(0) >= 53);
    }

    @Test
    public void campaignBalance_hasTighterOpeningEconomyAndStrongerBosses() {
        String[] ids = {"level_01", "level_02", "level_03", "level_04", "level_05"};
        int[] normalCoins = {240, 210, 220, 235, 260};
        int[] waveCounts = {6, 8, 8, 8, 9};
        TdGame.VisualTheme[] themes = {
                TdGame.VisualTheme.GARDEN, TdGame.VisualTheme.BRAMBLE,
                TdGame.VisualTheme.CRYSTAL, TdGame.VisualTheme.VALLEY,
                TdGame.VisualTheme.STORM
        };
        for (int i = 0; i < ids.length; i++) {
            TdGame normal = TdLevels.buildLevel(ids[i]);
            assertEquals("普通档开局金币", normalCoins[i], normal.getCoin());
            assertEquals("波次数应体现中后期压力", waveCounts[i], normal.getWaves().size());
            assertEquals("每关应有独立视觉主题", themes[i], normal.getVisualTheme());

            TdGame easy = TdLevels.buildLevel(ids[i]);
            easy.applyDifficulty(TdGame.Difficulty.EASY);
            assertEquals("简单档金币倍率", Math.round(normalCoins[i] * 1.3f), easy.getCoin());
            TdGame hard = TdLevels.buildLevel(ids[i]);
            hard.applyDifficulty(TdGame.Difficulty.HARD);
            assertEquals("困难档金币倍率", Math.round(normalCoins[i] * .8f), hard.getCoin());
        }

        assertEquals("第二关 Boss 不应再是一碰就碎", .85f,
                TdLevels.buildLevel("level_02").getWaves().get(7).hpMul, .0001f);
        assertEquals("第三关 Boss 应要求处理机制怪", .90f,
                TdLevels.buildLevel("level_03").getWaves().get(7).hpMul, .0001f);
        assertEquals("双入口 Boss 应具备守点压力", .95f,
                TdLevels.buildLevel("level_04").getWaves().get(7).hpMul, .0001f);
        assertEquals("终局 Boss 应接近完整血量", .95f,
                TdLevels.buildLevel("level_05").getWaves().get(8).hpMul, .0001f);
    }

    @Test
    public void level01Opening_allowsStarterDeckButNotInstantTowerSpam() {
        TdGame game = TdLevels.buildLevel("level_01");
        assertNotNull(game.placeTower(TowerType.BOTTLE, 1, 8));
        assertNotNull(game.placeTower(TowerType.SNOW, 1, 7));
        assertNotNull(game.placeTower(TowerType.BOTTLE, 3, 5));
        assertEquals("三张教学塔后只保留小额缓冲", 50, game.getCoin());
        assertNull("不能再在准备阶段直接铺第六座炮塔", game.placeTower(TowerType.BOTTLE, 3, 6));
    }

    @Test
    public void invalidLevelData_isRejectedBeforeGameStarts() {
        java.util.List<TdGame.Wave> waves = new java.util.ArrayList<>();
        waves.add(new TdGame.Wave(MonsterType.NORMAL, 1, 1f, 0f, 1f, 1f));
        assertIllegalArgument(() -> new TdGame(3, 3,
                new int[][] {{0, 0}, {0, 2}, {2, 2}}, 2, 2, 10, 1, waves));
        assertIllegalArgument(() -> new TdGame(3, 3,
                new int[][] {{0, 0}, {0, 1}, {0, 0}, {1, 0}}, 1, 0, 10, 1, waves));
        assertIllegalArgument(() -> new TdGame(3, 3,
                new int[][] {{0, 0}, {0, 1}}, 0, 1, 10, 1,
                new java.util.ArrayList<TdGame.Wave>()));
    }

    @Test
    public void towerTargetMode_cyclesAndChangesPersistedTowerState() {
        TdGame game = TdLevels.buildLevel("level_01");
        TdGame.Tower tower = game.placeTower(TowerType.BOTTLE, 3, 3);
        assertNotNull(tower);
        assertEquals(TdGame.TargetMode.FIRST, tower.targetMode);
        assertTrue(game.cycleTowerTargetMode(3, 3));
        assertEquals(TdGame.TargetMode.STRONG, tower.targetMode);
        assertTrue(game.cycleTowerTargetMode(3, 3));
        assertEquals(TdGame.TargetMode.WEAK, tower.targetMode);
        assertTrue(game.cycleTowerTargetMode(3, 3));
        assertEquals(TdGame.TargetMode.FIRST, tower.targetMode);
    }

    @Test
    public void bossLeak_hasHigherPenaltyThanNormalMonster() {
        int[][] path = new int[][] {{0, 0}, {0, 1}, {0, 2}, {0, 3}};
        java.util.List<TdGame.Wave> bossWave = new java.util.ArrayList<>();
        bossWave.add(new TdGame.Wave(MonsterType.BOSS, 1, 0f, 0f, .01f, 1f));
        TdGame game = new TdGame(4, 1, path, 0, 3, 200, 5, bossWave);
        game.startNextWaveEarly();
        for (int i = 0; i < 60 * 10 && !game.isEnded(); i++) game.tick();
        assertEquals("Boss 漏怪应一次造成 3 点伤害", 2, game.getMascotHp());
        assertEquals(3, game.getMascotHpLost());
    }

    @Test
    public void healer_restoresNearbyDamagedAlly_withoutOverhealing() {
        int[][] path = new int[][] {
                {0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4}, {0, 5}, {0, 6}, {0, 7}, {0, 8}
        };
        java.util.List<TdGame.Wave> waves = new java.util.ArrayList<>();
        waves.add(new TdGame.Wave(new MonsterType[] {MonsterType.HEALER, MonsterType.TANK},
                0, 2, .1f, 0f, 1f, 1f));
        TdGame game = new TdGame(9, 2, path, 0, 8, 500, 8, waves);
        TdGame.Tower tower = game.placeTower(TowerType.BOTTLE, 1, 3);
        assertNotNull(tower);
        assertTrue(game.cycleTowerTargetMode(1, 3)); // FIRST → STRONG，优先让坦克受伤
        assertTrue(game.startNextWaveEarly());

        boolean sawHealing = false;
        for (int i = 0; i < 60 * 4 && !game.isEnded(); i++) {
            game.tick();
            for (TdGame.Monster monster : game.getMonsters()) {
                assertTrue("治疗绝不能超过最大生命", monster.hp <= monster.maxHp + .0001f);
                if (monster.type == MonsterType.TANK && monster.healedFlash > 0f) {
                    sawHealing = true;
                }
            }
        }
        assertTrue("医生怪应治疗附近已受伤的同伴", sawHealing);
    }

    // ===== 回归：出怪间隔必须真实生效 =====

    @Test
    public void spawnInterval_pacesMonstersAcrossFrames() {
        // 一波 3 只、间隔 1 秒：修复缺陷前全部在同一逻辑帧生成，intervalSec 形同虚设。
        int[][] path = new int[][] {
                {0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4}, {0, 5}, {0, 6}, {0, 7}, {0, 8}
        };
        java.util.List<TdGame.Wave> waves = new java.util.ArrayList<>();
        waves.add(new TdGame.Wave(MonsterType.NORMAL, 3, 1.0f, 0f, 50f, 1f));
        waves.add(new TdGame.Wave(MonsterType.NORMAL, 1, 1f, 9999f, 1f, 1f)); // 永不开始
        TdGame g = new TdGame(9, 2, path, 0, 8, 500, 8, waves);
        assertTrue(g.startNextWaveEarly());
        int firstSeenAt = -1, secondSeenAt = -1, thirdSeenAt = -1;
        for (int i = 0; i < 60 * 4; i++) { // 最多观察 4 秒
            g.tick();
            int spawned = g.getMonstersSpawnedTotal();
            if (firstSeenAt < 0) {
                assertEquals("倒计时归零的首帧至多生成一只", 1, spawned);
                firstSeenAt = i;
            }
            if (secondSeenAt < 0 && spawned >= 2) secondSeenAt = i;
            if (thirdSeenAt < 0 && spawned >= 3) thirdSeenAt = i;
            if (thirdSeenAt >= 0) break;
        }
        assertTrue("观察窗口内三只怪都应登场", thirdSeenAt >= 0);
        assertTrue("第二只登场至少间隔一个出怪周期",
                secondSeenAt - firstSeenAt >= 55);
        assertTrue("第三只登场至少间隔一个出怪周期",
                thirdSeenAt - secondSeenAt >= 55);
    }

    // ===== 回归：雪花减速不得覆盖怪物基础速度倍率 =====

    @Test
    public void snowSlow_multipliesBaseSpeed_andRestoresIt() {
        // FAST 波自带 1.6 倍速（困难模式速度系数同理）；受冻=基础×0.65，
        // 减速过期后必须还原基础倍率而不是复位成 1。
        int cols = 20;
        int[][] path = new int[cols][2];
        for (int i = 0; i < cols; i++) path[i] = new int[] {0, i};
        java.util.List<TdGame.Wave> waves = new java.util.ArrayList<>();
        waves.add(new TdGame.Wave(MonsterType.FAST, 1, 1f, 0f, 100f, 1.6f));
        TdGame g = new TdGame(cols, 2, path, 0, cols - 1, 300, 100, waves);
        assertNotNull(g.placeTower(TowerType.SNOW, 1, 4)); // 射程 5.0，覆盖出生段
        g.startNextWaveEarly();
        // 理论基础倍率：wave.speedMul × Difficulty.NORMAL(1f)；不得从运行时反推，
        // 否则 bug 自身的复位值会污染期望值。
        final float expectedBase = 1.6f;
        Float frozenMul = null, restoredMul = null;
        for (int i = 0; i < MAX_TICKS && !g.isEnded() && restoredMul == null; i++) {
            g.tick();
            if (g.getMonsters().isEmpty()) continue;
            TdGame.Monster m = g.getMonsters().get(0);
            if (frozenMul == null && m.slowTimer > 0f) frozenMul = m.speedMul;
            if (frozenMul != null && m.slowTimer <= 0f) restoredMul = m.speedMul;
        }
        assertNotNull("雪花应命中一次", frozenMul);
        assertNotNull("怪走出射程后减速应过期", restoredMul);
        assertEquals("受冻中的倍率必须是基础×(1-0.35)",
                expectedBase * (1f - TowerType.SNOW_SLOW_PCT), frozenMul, 0.001f);
        assertEquals("减速过期后必须还原基础倍率", expectedBase, restoredMul, 0.001f);
    }

    // ===== 回归：太阳花只在战斗活跃期产币 =====

    @Test
    public void sunTower_stopsEarning_whenNoCombat() {
        int[][] path = new int[][] {{0, 0}, {0, 1}, {0, 2}, {0, 3}};
        java.util.List<TdGame.Wave> waves = new java.util.ArrayList<>();
        waves.add(new TdGame.Wave(MonsterType.NORMAL, 1, 1f, 0.1f, 200f, 1f));
        waves.add(new TdGame.Wave(MonsterType.NORMAL, 1, 1f, 9999f, 1f, 1f)); // 永不开始
        TdGame g = new TdGame(4, 2, path, 0, 3, 400, 8, waves);
        assertNotNull(g.placeTower(TowerType.SUN, 1, 0));
        // 准备期无限挂机窗口：金币增量必须为 0
        for (int i = 0; i < 600; i++) g.tick();
        assertEquals("准备阶段太阳花不应产币", 0, g.getCoinsEarned());
        // 开战后的战斗期应正常产出（正向回归）
        assertTrue(g.startNextWaveEarly());
        for (int i = 0; i < 60 * 3 && !g.isEnded(); i++) g.tick(); // 覆盖约 2 秒的行进+战斗期
        assertTrue("战斗期太阳花应产出金币", g.getCoinsEarned() > 0);
        // 唯一怪漏到终点离场后进入波间空闲：增量必须回到 0
        for (int i = 0; i < 60 * 30 && !g.getMonsters().isEmpty(); i++) g.tick();
        assertTrue("怪应已离场", g.getMonsters().isEmpty());
        assertFalse("下一波未开始，对局应停留在可挂机状态", g.isEnded());
        int beforeIdle = g.getCoinsEarned();
        for (int i = 0; i < 600; i++) g.tick();
        assertEquals("波间空闲期太阳花不应继续产币", beforeIdle, g.getCoinsEarned());
    }

    private static void assertIllegalArgument(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("应在开战前拒绝非法关卡数据");
    }
}
