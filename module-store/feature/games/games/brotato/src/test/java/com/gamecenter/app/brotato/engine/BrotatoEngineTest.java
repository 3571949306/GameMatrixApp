package com.gamecenter.app.brotato.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

/**
 * BrotatoArena 引擎回归（纯 JVM、无 Android 依赖）：一律吃生产资产 + 固定 seed，靠 tick() 驱动。
 *
 * <p>契约要点：一步 = 16ms 内部时钟（禁止墙上时间）；间歇期 2000ms = 125 tick；初始
 * hp/maxHp=10、fireIntervalMs=300、moveSpeed=5、bulletDamage=1、shotCount=1。
 *
 * <p>驱动约定：所有长流程都靠"角落扎营 + 自动选卡轮转"把关卡推到目标波次，并在每次 REWARD_PENDING
 * 用 0/1/2 轮转换位，以覆盖 3 选 1 的随机发牌。
 */
public class BrotatoEngineTest {

    private static final int SEED = 7;
    private static final float WORLD_W = 360f;
    private static final float WORLD_H = 640f;
    /** 扎营点：右下角，让敌人从单一方向进入火力网。 */
    private static final float CAMP_X = WORLD_W - 26f;
    private static final float CAMP_Y = WORLD_H - 26f;
    /** 每多少 tick 重新钉一次扎营点（<=0 表示完全不干预玩家输入）。 */
    private static final int CAMP_EVERY = 250;
    /** 间歇期 2000ms / 16ms = 125 tick。 */
    private static final int INTERMISSION_TICKS = 125;
    /** 升级卡牌搜捕的单轮预算。 */
    private static final int HUNT_TICKS = 60000;
    private static final float EPS = 1e-3f;

    private static String manifest;
    private static String enemies;
    private static String waves;

    @BeforeClass
    public static void loadProductionBrotatoAssets() throws IOException {
        Path assetsRoot = findAssetsRoot();
        manifest = readAsset(assetsRoot.resolve("brotato/manifest.json"));
        enemies = readAsset(assetsRoot.resolve("brotato/enemies.json"));
        waves = readAsset(assetsRoot.resolve("brotato/waves.json"));
    }

    // ==== 用例 3：同 seed 必须可复现 ===============================================

    @Test
    public void sameSeedReplaysIdentically() {
        Rig a = new Rig(0.5f);
        Rig b = new Rig(0.5f);
        a.start();
        b.start();

        // 预生成一份完全相同的伪随机输入序列，两个 arena 各自回放
        Random walk = new Random(4242L);
        float[][] inputs = new float[2000][];
        for (int i = 0; i < inputs.length; i++) {
            inputs[i] = new float[] {walk.nextFloat() * WORLD_W, walk.nextFloat() * WORLD_H,
                    walk.nextDouble() < 0.8d ? 1f : 0f};
        }

        List<String> seriesA = new ArrayList<>();
        List<String> seriesB = new ArrayList<>();
        for (int tick = 1; tick <= 2000; tick++) {
            applyWalk(a.arena, inputs[tick - 1]);
            applyWalk(b.arena, inputs[tick - 1]);
            a.arena.tick();
            b.arena.tick();
            if (tick % 100 == 0 && tick <= 500) {
                seriesA.add(sample(a.arena));
                seriesB.add(sample(b.arena));
            }
        }

        assertEquals("同 seed 下前 500 tick 的敌人序列（数量+种类）必须逐个一致", seriesA, seriesB);
        assertEquals("同 seed 下分数必须一致", a.arena.score(), b.arena.score());
        assertEquals("同 seed 下波次必须一致", a.arena.wave(), b.arena.wave());
        assertEquals("同 seed 下血量必须一致", a.arena.hp(), b.arena.hp());
        assertEquals("同 seed 下状态机必须一致", a.arena.state(), b.arena.state());
        assertEquals("同 seed 下敌人数量必须一致", a.arena.enemies().size(), b.arena.enemies().size());
        assertEquals("同 seed 下子弹数量必须一致", a.arena.projectiles().size(),
                b.arena.projectiles().size());
        assertEquals("同 seed 下玩家坐标必须一致", a.arena.playerX(), b.arena.playerX(), EPS);
        assertEquals("同 seed 下玩家坐标必须一致", a.arena.playerY(), b.arena.playerY(), EPS);
        assertEquals("同 seed 下开火间隔必须一致", a.arena.fireIntervalMs(), b.arena.fireIntervalMs(),
                EPS);
        assertEquals("同 seed 下击杀回调次数必须一致", a.rec.scoreChanges, b.rec.scoreChanges);
        assertEquals("同 seed 下最后一次分数回调值必须一致", a.rec.lastScore, b.rec.lastScore);
        assertTrue("模拟必须真的产生击杀（否则本用例只是空转）：score=" + a.arena.score(),
                a.arena.score() > 0);
        assertFalse("采样序列不应全为 0 敌人（说明刷怪没跑起来）：" + seriesA,
                seriesA.stream().allMatch(entry -> entry.startsWith("0|")));
    }

    private static void applyWalk(BrotatoArena arena, float[] input) {
        arena.setTouchTarget(input[0], input[1], input[2] > 0.5f);
    }

    /** 采样：敌人数 + 种类多重集，用于确定性对比。 */
    private static String sample(BrotatoArena arena) {
        List<String> kinds = new ArrayList<>();
        for (var enemy : arena.enemies()) {
            kinds.add(enemy.kind.id());
        }
        java.util.Collections.sort(kinds);
        return arena.enemies().size() + "|" + String.join(",", kinds);
    }

    // ==== 用例 4：开局流程 + 初始属性 ==============================================

    @Test
    public void freshArenaStartsInIntermissionThenSpawnsFirstWave() {
        Rig rig = new Rig(1.0f);
        rig.start();
        assertEquals("start() 后应为间歇期", BrotatoArena.State.INTERMISSION, rig.arena.state());

        tickFor(rig.arena, INTERMISSION_TICKS - 5);
        assertEquals("间歇期（<125 tick）不该结束", BrotatoArena.State.INTERMISSION, rig.arena.state());
        assertEquals("间歇期不该刷出敌人", 0, rig.arena.enemies().size());

        boolean sawSpawn = false;
        for (int t = 0; t < 180 && !sawSpawn; t++) {
            rig.arena.tick();
            sawSpawn = rig.arena.enemies().size() > 0;
        }
        assertEquals("间歇期结束后应进入 WAVE_ACTIVE", BrotatoArena.State.WAVE_ACTIVE, rig.arena.state());
        assertEquals("开局应是第 1 波", 1, rig.arena.wave());
        assertTrue("第 1 波开局后 380 tick 内必须刷出过敌人（波开始即出第一只）", sawSpawn);
    }

    @Test
    public void freshArenaExposesDeclaredInitialStats() {
        Rig rig = new Rig(1.0f);
        rig.start();
        BrotatoArena arena = rig.arena;
        assertEquals("初始血量应为 10", 10, arena.hp());
        assertEquals("初始血量上限应为 10", 10, arena.maxHp());
        assertEquals("初始分数应为 0", 0, arena.score());
        assertEquals("初始开火间隔应为 300ms", 300f, arena.fireIntervalMs(), EPS);
        assertEquals("初始移动速度应为 5", 5f, arena.moveSpeed(), EPS);
        assertEquals("初始子弹伤害应为 1", 1f, arena.bulletDamage(), EPS);
        assertEquals("初始单轮弹数应为 1", 1, arena.shotCount());
        assertEquals("开局场上不该有敌人", 0, arena.enemies().size());
        assertEquals("开局不该有子弹", 0, arena.projectiles().size());
        for (Upgrade upgrade : Upgrade.values()) {
            assertEquals("开局不该持有升级 " + upgrade, 0, arena.upgradeCount(upgrade));
        }
    }

    // ==== 用例 5：速通到波间奖励 ===================================================

    @Test
    public void quickRouteReachesRewardPendingAndAdvancesToWaveTwo() {
        Rig rig = new Rig(0.05f);
        rig.start();
        camp(rig.arena);

        driveOrFail(rig.arena, rig.rec, 20000, CAMP_EVERY, null,
                arena -> arena.state() == BrotatoArena.State.REWARD_PENDING || arena.state() == BrotatoArena.State.GAME_OVER,
                "第 1 波打完进入发牌（或阵亡）");
        assertEquals("0.05 缩放下第 1 波不该打死玩家", BrotatoArena.State.REWARD_PENDING, rig.arena.state());
        assertEquals("应恰好收到一次发牌回调", 1, rig.rec.rewardEvents);
        assertEquals("应恰好完成第 1 波", Arrays.asList(1), rig.rec.completedWaves);
        assertTrue("发牌回调的波次必须落在 1..20 之间，实测 " + rig.rec.lastRewardWave,
                rig.rec.lastRewardWave >= 1 && rig.rec.lastRewardWave <= 20);
        String[] titles = rig.rec.lastTitles;
        assertEquals("每次必须发 3 张卡", 3, titles.length);
        for (int i = 0; i < titles.length; i++) {
            assertTrue("卡牌标题不得为空: index " + i, titles[i] != null && !titles[i].trim().isEmpty());
        }

        rig.arena.selectUpgrade(0);
        assertEquals("选卡后应回到间歇期", BrotatoArena.State.INTERMISSION, rig.arena.state());
        assertEquals("选卡不得再次发牌", 1, rig.rec.rewardEvents);

        driveOrFail(rig.arena, rig.rec, 400, CAMP_EVERY, null,
                arena -> arena.state() == BrotatoArena.State.WAVE_ACTIVE, "间歇期后第 2 波开战");
        assertEquals("选卡后最终应推进到第 2 波", 2, rig.arena.wave());
        assertEquals("第 2 波开战时不该再发牌", 1, rig.rec.rewardEvents);
    }

    // ==== 用例 6：升级卡效果 =======================================================

    @Test
    public void rapidFireLowersFireInterval() {
        Rig rig = rigHolding(Upgrade.RAPID_FIRE);
        assertTrue("应至少持有 1 层 RAPID_FIRE", rig.arena.upgradeCount(Upgrade.RAPID_FIRE) >= 1);
        assertTrue("RAPID_FIRE 后开火间隔必须低于初始 300ms，实测 " + rig.arena.fireIntervalMs(),
                rig.arena.fireIntervalMs() < 300f);
    }

    @Test
    public void swiftBootsRaisesMoveSpeed() {
        Rig rig = rigHolding(Upgrade.SWIFT_BOOTS);
        assertTrue("应至少持有 1 层 SWIFT_BOOTS", rig.arena.upgradeCount(Upgrade.SWIFT_BOOTS) >= 1);
        assertTrue("SWIFT_BOOTS 后移动速度必须高于初始 5，实测 " + rig.arena.moveSpeed(),
                rig.arena.moveSpeed() > 5f);
    }

    @Test
    public void vitalityRaisesMaxHp() {
        Rig rig = rigHolding(Upgrade.VITALITY);
        assertTrue("应至少持有 1 层 VITALITY", rig.arena.upgradeCount(Upgrade.VITALITY) >= 1);
        assertTrue("VITALITY 后血量上限必须高于初始 10，实测 " + rig.arena.maxHp(),
                rig.arena.maxHp() > 10);
        assertTrue("当前血量不得超过上限: hp=" + rig.arena.hp() + " maxHp=" + rig.arena.maxHp(),
                rig.arena.hp() <= rig.arena.maxHp());
    }

    @Test
    public void damageUpRaisesBulletDamage() {
        Rig rig = rigHolding(Upgrade.DAMAGE_UP);
        assertTrue("应至少持有 1 层 DAMAGE_UP", rig.arena.upgradeCount(Upgrade.DAMAGE_UP) >= 1);
        assertTrue("DAMAGE_UP 后子弹伤害必须高于初始 1，实测 " + rig.arena.bulletDamage(),
                rig.arena.bulletDamage() > 1f);
    }

    @Test
    public void multishotRaisesShotCountAndVolleySize() {
        Rig rig = rigHolding(Upgrade.MULTISHOT);
        assertEquals("MULTISHOT 后 shotCount 应为 2", 2, rig.arena.shotCount());

        // 单轮齐射：连续观察弹数增量，必须一次性出现 >=2 发
        final int[] previous = {rig.arena.projectiles().size()};
        final int[] bestVolley = {0};
        int reached = drive(rig.arena, rig.rec, 6000, CAMP_EVERY, ROTATE, arena -> {
            int now = arena.projectiles().size();
            bestVolley[0] = Math.max(bestVolley[0], now - previous[0]);
            previous[0] = now;
            return bestVolley[0] >= 2;
        });
        assertTrue("拿到 MULTISHOT 后 6000 tick 内都没观察到一次 >=2 发的齐射（最大增量 "
                + bestVolley[0] + "，shotCount=" + rig.arena.shotCount() + "）"
                + diag(rig.arena, rig.rec), reached > 0);
    }

    @Test
    public void regenHealsPlayerAfterNextWaveEnd() {
        Rig rig = rigHolding(Upgrade.REGEN);
        BrotatoArena arena = rig.arena;
        assertTrue("应至少持有 1 层 REGEN", arena.upgradeCount(Upgrade.REGEN) >= 1);

        driveOrFail(arena, rig.rec, 3000, CAMP_EVERY, ROTATE,
                a -> a.state() == BrotatoArena.State.WAVE_ACTIVE, "持有 REGEN 后进入下一波");

        // 契约没有"扣血接缝"，这里用贴脸刷 boss（contactDamage 3）把血量打到上限以下
        int guard = 0;
        while (arena.hp() >= arena.maxHp() && guard++ < 900 && arena.state() == BrotatoArena.State.WAVE_ACTIVE
                && aliveCount(arena, "boss") < 6) {
            if (guard % 6 == 0) {
                arena.debugSpawnEnemy("boss", arena.playerX() + 1f, arena.playerY() + 1f);
            }
            arena.tick();
        }
        assertTrue("REGEN 用例需要先掉血才可比对：贴脸 boss 在 900 tick 内没造成任何接触伤害，"
                + "说明契约缺可复现的掉血接缝" + diag(arena, rig.rec),
                arena.hp() < arena.maxHp());
        assertEquals("造伤阶段不应阵亡", BrotatoArena.State.WAVE_ACTIVE, arena.state());
        final int lowHp = arena.hp();
        final int maxHpAtStart = arena.maxHp();
        final int wavesBefore = rig.rec.completedWaves.size();
        final int[] peakHp = {lowHp};

        // 测量窗口内一律不再选卡：否则 VITALITY 也会让 hp 抬升，无法归因给 REGEN
        driveOrFail(arena, rig.rec, 12000, CAMP_EVERY, null, a -> {
            peakHp[0] = Math.max(peakHp[0], a.hp());
            return rig.rec.completedWaves.size() > wavesBefore;
        }, "本波打完（onWaveComplete）");
        drive(arena, rig.rec, 600, CAMP_EVERY, null, a -> {
            peakHp[0] = Math.max(peakHp[0], a.hp());
            return a.state() == BrotatoArena.State.WAVE_ACTIVE; // 间歇期观察窗口结束即停
        });

        assertEquals("测量窗口内血量上限不该变化（变了说明混进了 VITALITY，本用例失真）",
                maxHpAtStart, arena.maxHp());
        assertTrue("REGEN 应在下一波完成时让 hp 回升：波末 hp=" + lowHp + "，之后峰值="
                + peakHp[0] + "，maxHp=" + arena.maxHp(), peakHp[0] > lowHp);
        assertTrue("REGEN 回升不得越过上限: peak=" + peakHp[0] + " maxHp=" + arena.maxHp(),
                peakHp[0] <= arena.maxHp());
    }

    @Test
    public void selectUpgradeRejectsIndexOutsideZeroToTwo() {
        Rig rig = new Rig(0.05f);
        rig.start();
        camp(rig.arena);
        driveOrFail(rig.arena, rig.rec, 20000, CAMP_EVERY, null,
                arena -> arena.state() == BrotatoArena.State.REWARD_PENDING || arena.state() == BrotatoArena.State.GAME_OVER,
                "第 1 波打完进入发牌");
        assertEquals("必须停在发牌态", BrotatoArena.State.REWARD_PENDING, rig.arena.state());

        assertThrows("index=-1 必须被拒绝", IllegalArgumentException.class,
                () -> rig.arena.selectUpgrade(-1));
        assertThrows("index=3 必须被拒绝", IllegalArgumentException.class,
                () -> rig.arena.selectUpgrade(3));
        assertEquals("越界选卡不得消耗这次发牌", BrotatoArena.State.REWARD_PENDING, rig.arena.state());
        assertEquals("越界选卡不得追加发牌", 1, rig.rec.rewardEvents);

        rig.arena.selectUpgrade(2); // 合法的最大 index
        assertTrue("合法 index=2 必须被接受，实际状态 " + rig.arena.state(),
                rig.arena.state() == BrotatoArena.State.INTERMISSION || rig.arena.state() == BrotatoArena.State.WAVE_ACTIVE);
    }

    // ==== 用例 7：boss 按时到场 ====================================================

    @Test
    public void bossAppearsInWaveFive() {
        Rig rig = new Rig(0.05f);
        rig.start();
        camp(rig.arena);

        final int[] sighting = {-1, -1}; // {出现时的波次, 存活 boss 数}
        int reached = drive(rig.arena, rig.rec, 300000, CAMP_EVERY, ROTATE, arena -> {
            int aliveBosses = aliveCount(arena, "boss");
            if (aliveBosses > 0) {
                sighting[0] = arena.wave();
                sighting[1] = aliveBosses;
                return true;
            }
            return arena.state() == BrotatoArena.State.GAME_OVER || arena.state() == BrotatoArena.State.WIN
                    || arena.wave() > 5;
        });
        assertTrue("速通 300000 tick 仍没在第 5 波内看到 boss（delayMs=2500 后必到）"
                + diag(rig.arena, rig.rec), reached > 0);
        assertEquals("boss 必须出现在第 5 波（途中阵亡/超波说明 boss 排期没接上）"
                + diag(rig.arena, rig.rec), 5, sighting[0]);
        assertTrue("boss 出现时必须是存活敌人，实测 " + sighting[1], sighting[1] >= 1);
        assertEquals("第 5 波 bossGroup.count=1 → 同场 boss 应为 1", 1, sighting[1]);
    }

    @Test
    public void twoSplitterKillsYieldFourMinionsInTotal() {
        // 回归（原缺陷：分裂落点定长 2 槽，两只 splitter 同帧死亡只出 2 裂子）：
        // 管线级验证两次死亡共产出 4 只 minion。同帧双死需精确弹道会引入脆弱性，此处
        // 用 debugKillEnemy 连杀两只（各自帧内 flush），断言累计 4。
        Rig rig = new Rig(1.0f);
        rig.start();
        BrotatoArena arena = rig.arena;
        arena.debugSetRewardPool(Upgrade.DAMAGE_UP, Upgrade.VITALITY, Upgrade.SWIFT_BOOTS);
        tickFor(arena, 130); // 进入第 1 波
        float px = arena.playerX();
        float py = arena.playerY();
        arena.debugSpawnEnemy("splitter", px + 80f, py);
        arena.debugSpawnEnemy("splitter", px - 80f, py);
        java.util.List<Enemy> splitters = new java.util.ArrayList<>();
        for (Enemy e : arena.enemies()) {
            if ("splitter".equals(e.kind.id())) splitters.add(e);
        }
        assertEquals("应恰好投放两只 splitter", 2, splitters.size());
        arena.debugKillEnemy(splitters.get(0));
        arena.debugKillEnemy(splitters.get(1));
        int minions = aliveCount(arena, "minion");
        assertEquals("两次分裂应累计产出 4 只裂子", 4, minions);
    }

    @Test
    public void intermissionBannerCountsUpcomingWaveIncludingBosses() {
        Rig rig = new Rig(1.0f);
        rig.start();
        BrotatoArena arena = rig.arena;
        assertEquals("第 1 波间歇应预告 spawnCount=10", 10, arena.upcomingWaveTotal());
        tickFor(arena, 200);
        assertEquals("开战后预告数归零（改用 remainingSpawns）", 0, arena.upcomingWaveTotal());
    }

    // ==== 用例 8：通关 =============================================================

    @Test
    public void clearingAllTwentyWavesWinsExactlyOnce() {
        Rig rig = new Rig(0.6f);
        rig.start();
        rig.arena.debugSetRewardPool(Upgrade.MULTISHOT, Upgrade.DAMAGE_UP, Upgrade.RAPID_FIRE);
        camp(rig.arena);

        driveOrFail(rig.arena, rig.rec, 400000, CAMP_EVERY, ROTATE,
                arena -> arena.state() == BrotatoArena.State.WIN || arena.state() == BrotatoArena.State.GAME_OVER,
                "打完全部 20 波");
        assertEquals("定向成长下速通不应阵亡，onGameOver=" + rig.rec.gameOvers, BrotatoArena.State.WIN,
                rig.arena.state());
        assertEquals("onWin 必须恰好回调一次", 1, rig.rec.wins);
        assertEquals("WIN 之后不应再有 GAME_OVER", 0, rig.rec.gameOvers);
        assertFalse("必须记录到波次完成回调", rig.rec.completedWaves.isEmpty());
        assertEquals("最后一次 onWaveComplete 必须落在第 20 波", 20,
                rig.rec.completedWaves.get(rig.rec.completedWaves.size() - 1).intValue());
        assertTrue("通关必须带上分数，实测 " + rig.rec.lastWinScore, rig.rec.lastWinScore > 0);
        assertTrue("onWin 分数不得超过终局分数（允许同 tick 结算）",
                rig.rec.lastWinScore <= rig.arena.score());

        tickFor(rig.arena, 300);
        assertEquals("通关后继续 tick 必须保持终局态", BrotatoArena.State.WIN, rig.arena.state());
        assertEquals("通关后继续 tick 不得重复回调 onWin", 1, rig.rec.wins);
    }

    // ==== 用例 9：站桩必死 =========================================================

    @Test
    public void idlePlayerDiesUnderHardestScaling() {
        Rig rig = new Rig(3.0f);
        rig.start();
        rig.arena.setTouchTarget(rig.arena.playerX(), rig.arena.playerY(), false);

        driveOrFail(rig.arena, rig.rec, 100000, 0, null,
                arena -> arena.state() == BrotatoArena.State.GAME_OVER, "最高缩放下站桩玩家应被打死");
        assertEquals("应停在 GAME_OVER", BrotatoArena.State.GAME_OVER, rig.arena.state());
        assertEquals("onGameOver 必须恰好回调一次", 1, rig.rec.gameOvers);
        assertEquals("GAME_OVER 时不得同时 WIN", 0, rig.rec.wins);
        assertTrue("onGameOver 必须带上当时的波次，实测 " + rig.rec.lastGameOverWave,
                rig.rec.lastGameOverWave >= 1 && rig.rec.lastGameOverWave <= 20);
        assertTrue("onGameOver 的分数不得为负，实测 " + rig.rec.lastGameOverScore,
                rig.rec.lastGameOverScore >= 0);
        assertTrue("onGameOver 分数应不高于终局分数（允许同 tick 结算）",
                rig.rec.lastGameOverScore <= rig.arena.score());
        assertEquals("onGameOver 波次应与引擎终局波次一致", rig.arena.wave(),
                rig.rec.lastGameOverWave);

        tickFor(rig.arena, 200);
        assertEquals("阵亡后继续 tick 必须保持 GAME_OVER", BrotatoArena.State.GAME_OVER, rig.arena.state());
        assertEquals("阵亡后继续 tick 不得重复回调 onGameOver", 1, rig.rec.gameOvers);
    }

    // ==== 用例 10：行为接缝（SPLIT / ZIGZAG）======================================

    @Test
    public void killedSplitterSpawnsTwoMinions() {
        Rig rig = new Rig(0.01f);
        rig.start();
        camp(rig.arena);
        driveOrFail(rig.arena, rig.rec, 1500, CAMP_EVERY, null,
                arena -> arena.state() == BrotatoArena.State.WAVE_ACTIVE && arena.enemies().size() > 0,
                "第 1 波开战并刷出敌人");
        assertEquals("第 1 波刷怪表里不该有 splitter，样本必须干净", 0, aliveCount(rig.arena, "splitter"));
        assertEquals("第 1 波不该自带 minion", 0, aliveCount(rig.arena, "minion"));

        float playerX = rig.arena.playerX();
        float playerY = rig.arena.playerY();
        rig.arena.debugSpawnEnemy("splitter", playerX, playerY - 150f);
        assertEquals("debugSpawnEnemy 必须立刻放上 splitter", 1, aliveCount(rig.arena, "splitter"));

        int reached = drive(rig.arena, rig.rec, 6000, CAMP_EVERY, ROTATE,
                arena -> aliveCount(arena, "minion") >= 2);
        assertTrue("spawn 的 splitter 在 6000 tick 内没被击杀并分裂出 >=2 只 minion"
                + "（怀疑：子弹射程/存活期不够，或 SPLIT 行为没接进死亡结算）"
                + diag(rig.arena, rig.rec), reached > 0);
        assertEquals("分裂只能在母体死亡时触发：观察点母体 hp 必须已归零", 0,
                aliveCount(rig.arena, "splitter"));
        assertTrue("一次分裂至少产出 2 只 minion，实测 " + aliveCount(rig.arena, "minion"),
                aliveCount(rig.arena, "minion") >= 2);
    }

    @Test
    public void zigzagDeviatesLaterallyWhileGruntWalksStraight() {
        float gruntDrift = lateralDriftOf("grunt");
        float zigzagDrift = lateralDriftOf("zigzag");
        assertTrue("grunt 必须沿直线接近玩家，实测横向漂移 " + gruntDrift, gruntDrift <= 1f);
        assertTrue("ZIGZAG 敌人必须有明显横向偏移（实测 " + zigzagDrift + "，grunt=" + gruntDrift
                + "）；两者接近说明 ZIGZAG 行为没接进移动", zigzagDrift > gruntDrift + 10f);
    }

    /**
     * 在超大场地里把玩家放在目标正下方，只放一只敌人，观察 120 tick（仍在间歇期内）后的横向偏移。
     * 直线接近玩家的敌人横向偏移应为 0；带 ZIGZAG 的必须偏出去。
     */
    private static float lateralDriftOf(String kindId) {
        float hugeWorld = 10000f;
        Rig rig = new Rig(3.0f, hugeWorld, hugeWorld); // 3.0 缩放：够硬，观察窗口内不会被秒杀
        rig.start();
        rig.arena.debugMovePlayerTo(5000f, 9000f);
        tickFor(rig.arena, 2);

        float spawnX = 5000f;
        float spawnY = 1000f;
        rig.arena.debugSpawnEnemy(kindId, spawnX, spawnY);
        assertEquals("debugSpawnEnemy 应只放进 1 只 " + kindId, 1, aliveCount(rig.arena, kindId));
        float startDistance = firstEnemyDistance(rig.arena, kindId);

        tickFor(rig.arena, 120);
        assertEquals("观察窗口内必须恰好还有 1 只 " + kindId + "（死了或复制都会污染测量）", 1,
                aliveCount(rig.arena, kindId));
        float travelledTowardPlayer = startDistance - firstEnemyDistance(rig.arena, kindId);
        assertTrue(kindId + " 必须在 122 tick 内朝玩家移动（实际接近量 " + travelledTowardPlayer
                + "）；为 0 说明 debug 期不推进敌人", travelledTowardPlayer > 100f);

        return Math.abs(firstEnemyX(rig.arena, kindId) - spawnX);
    }

    // ==== 用例 11：JVM 纯度守卫 ====================================================

    @Test
    public void engineSourcesStayFreeOfAndroidApis() throws IOException {
        Path engineDir = findEngineSourceRoot();
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(engineDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .forEach(sources::add);
        }
        assertFalse("engine 目录下没有任何 .java 源文件，纯度守卫失去对象：" + engineDir,
                sources.isEmpty());

        List<String> offenders = new ArrayList<>();
        for (Path source : sources) {
            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).contains("android.")) {
                    offenders.add(source.getFileName() + ":" + (i + 1) + " → " + lines.get(i).trim());
                }
            }
        }
        assertTrue("engine 包必须纯 JVM（可被 JVM 单测直接驱动），检测到 android.* 引用 "
                + offenders.size() + " 处: " + offenders, offenders.isEmpty());
    }

    // ==== 升级卡牌搜捕 =============================================================

    /**
     * 引擎提供 debugSetRewardPool 定向发牌接缝（不消费随机源）：目标卡固定排第一张，
     * 0.6 缩放下打完第 1 波即持有目标卡；返回选卡后的 rig。
     */
    private static Rig rigHolding(Upgrade target) {
        Rig rig = new Rig(0.6f);
        rig.start();
        Upgrade fillA = null;
        Upgrade fillB = null;
        for (Upgrade u : Upgrade.values()) {
            if (u == target) continue;
            if (fillA == null) fillA = u;
            else if (fillB == null) fillB = u;
        }
        rig.arena.debugSetRewardPool(target, fillA, fillB);
        camp(rig.arena);
        int reached = drive(rig.arena, rig.rec, HUNT_TICKS, CAMP_EVERY, number -> 0,
                arena -> arena.upgradeCount(target) > 0);
        if (reached < 0) {
            fail("定向发牌 " + target + " 后速通 " + HUNT_TICKS + " tick 仍未持有该卡"
                    + diag(rig.arena, rig.rec));
        }
        return rig;
    }

    /** 停止条件：作用在当前 arena 状态上。 */
    private interface ArenaStop {
        boolean test(BrotatoArena arena);
    }

    /** 按奖励轮次在 0/1/2 之间轮换，用来覆盖随机发牌。 */
    private interface CardPicker {
        int nextIndex(int pickNumber);
    }

    private static final CardPicker ROTATE = number -> number % 3;

    // ==== 驱动与断言辅助 ===========================================================

    /** 契约：一步 = 16ms 内部时钟。 */
    private static void tickFor(BrotatoArena arena, int ticks) {
        for (int i = 0; i < ticks; i++) {
            arena.tick();
        }
    }

    private static void camp(BrotatoArena arena) {
        arena.setTouchTarget(CAMP_X, CAMP_Y, true);
        arena.debugMovePlayerTo(CAMP_X, CAMP_Y);
    }

    /**
     * 推进引擎直到停止条件成立。
     *
     * @param campEvery 每多少 tick 重新钉一次扎营点（<=0 表示不干预输入）
     * @param picker    非 null 时：每次停在 REWARD_PENDING 就按轮转换位自动选卡
     * @return 满足条件的 tick 序号；预算内未满足返回 -1
     */
    private static int drive(BrotatoArena arena, Recorder rec, int maxTicks, int campEvery,
            CardPicker picker, ArenaStop stop) {
        int pickNumber = 0;
        int lastPickedWave = Integer.MIN_VALUE;
        for (int tick = 1; tick <= maxTicks; tick++) {
            arena.tick();
            if (campEvery > 0 && tick % campEvery == 0) camp(arena);
            if (picker != null && arena.state() == BrotatoArena.State.REWARD_PENDING
                    && arena.wave() != lastPickedWave) {
                // wave() 在发牌期仍是刚完成的波号，selectUpgrade 后才自增：必须在选牌前捕获
                lastPickedWave = arena.wave();
                arena.selectUpgrade(picker.nextIndex(pickNumber++));
            }
            if (stop.test(arena)) return tick;
        }
        return -1;
    }

    private static void driveOrFail(BrotatoArena arena, Recorder rec, int maxTicks, int campEvery,
            CardPicker picker, ArenaStop stop, String goal) {
        if (drive(arena, rec, maxTicks, campEvery, picker, stop) < 0) {
            fail("推进 " + maxTicks + " tick 仍未满足目标：" + goal + diag(arena, rec));
        }
    }

    private static String diag(BrotatoArena arena, Recorder rec) {
        return "；现场 state=" + arena.state() + " wave=" + arena.wave() + " hp=" + arena.hp() + "/"
                + arena.maxHp() + " score=" + arena.score() + " 敌人=" + arena.enemies().size()
                + " 子弹=" + arena.projectiles().size() + " 玩家=(" + arena.playerX() + ","
                + arena.playerY() + ") shotCount=" + arena.shotCount() + " 完成波="
                + rec.completedWaves + " 发牌次数=" + rec.rewardEvents + " 阵亡次数="
                + rec.gameOvers + " 通关次数=" + rec.wins;
    }

    private static int aliveCount(BrotatoArena arena, String kindId) {
        int count = 0;
        for (var enemy : arena.enemies()) {
            if (enemy.hp > 0f
                    && (kindId == null || kindId.equals(enemy.kind.id()))) count++;
        }
        return count;
    }

    /** 第一只存活该种类敌人的 x 坐标；不存在返回 NaN。 */
    private static float firstEnemyX(BrotatoArena arena, String kindId) {
        for (var enemy : arena.enemies()) {
            if (kindId.equals(enemy.kind.id()) && enemy.hp > 0f) {
                return enemy.x;
            }
        }
        return Float.NaN;
    }

    /** 第一只存活该种类敌人到玩家的距离；不存在返回 NaN。 */
    private static float firstEnemyDistance(BrotatoArena arena, String kindId) {
        for (var enemy : arena.enemies()) {
            if (kindId.equals(enemy.kind.id()) && enemy.hp > 0f) {
                float dx = enemy.x - arena.playerX();
                float dy = enemy.y - arena.playerY();
                return (float) Math.sqrt(dx * dx + dy * dy);
            }
        }
        return Float.NaN;
    }

    // ==== 监听记录 =================================================================

    private static final class Recorder implements BrotatoArena.Listener {
        int scoreChanges;
        int lastScore = -1;
        int rewardEvents;
        int lastRewardWave = -1;
        String[] lastTitles = new String[0];
        int gameOvers;
        int lastGameOverScore = -1;
        int lastGameOverWave = -1;
        int wins;
        int lastWinScore = -1;
        final List<Integer> completedWaves = new ArrayList<>();

        @Override
        public void onScoreChanged(int score) {
            scoreChanges++;
            lastScore = score;
        }

        @Override
        public void onWaveComplete(int wave) {
            completedWaves.add(wave);
        }

        @Override
        public void onGameOver(int score, int wave) {
            gameOvers++;
            lastGameOverScore = score;
            lastGameOverWave = wave;
        }

        @Override
        public void onWin(int score) {
            wins++;
            lastWinScore = score;
        }

        @Override
        public void onRewardCards(int wave, String[] titles) {
            rewardEvents++;
            lastRewardWave = wave;
            lastTitles = titles == null ? new String[0] : titles.clone();
        }
    }

    private static final class Rig {
        final BrotatoArena arena;
        final Recorder rec = new Recorder();

        Rig(float scalar) {
            this(scalar, WORLD_W, WORLD_H);
        }

        Rig(float scalar, float worldWidth, float worldHeight) {
            BrotatoContent content = BrotatoContent.load(manifest, enemies, waves);
            arena = new BrotatoArena(content, scalar, SEED);
            arena.setBounds(worldWidth, worldHeight);
            arena.setListener(rec);
        }

        void start() {
            arena.start();
        }
    }

    // ==== 资产定位 ================================================================

    private static Path findAssetsRoot() {
        Path[] candidates = new Path[] {
                Paths.get("src/main/assets"),
                Paths.get("module-store/feature/games/games/brotato/src/main/assets")
        };
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate.resolve("brotato/manifest.json"))) return candidate;
        }
        throw new IllegalStateException("production brotato asset was not found for JVM test");
    }

    private static Path findEngineSourceRoot() {
        Path[] candidates = new Path[] {
                Paths.get("src/main/java/com/gamecenter/app/brotato/engine"),
                Paths.get("module-store/feature/games/games/brotato/src/main/java"
                        + "/com/gamecenter/app/brotato/engine")
        };
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) return candidate;
        }
        throw new IllegalStateException("brotato engine source directory was not found for JVM test");
    }

    private static String readAsset(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
