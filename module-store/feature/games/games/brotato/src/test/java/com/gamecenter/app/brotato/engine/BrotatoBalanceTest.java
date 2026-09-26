package com.gamecenter.app.brotato.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 数值手感回归：用脚本化的玩家策略跑完整局，把「难度三档的落差 / 波次节奏 / 成长上限」
 * 钉成机器可查的区间。引擎是纯 JVM 的，所以这里不需要 Robolectric，测的是真内容
 * （生产 assets/brotato/*.json）+ 真成长表（{@link Upgrade} 常量）。
 *
 * <p>三种策略是刻意拉开的三个技能档，用来给难度分档定位：</p>
 * <ul>
 *   <li>{@code STANDING_STILL}：完全不走位（挂机）；</li>
 *   <li>{@code MOVES_BLINDLY}：会躲，但永远拿发牌的第一张（不懂 build）；</li>
 *   <li>{@code MOVES_AND_BUILDS}：会躲且按场面选卡（熟练玩家上限）。</li>
 * </ul>
 *
 * <p>2026-09-20 定标依据（5 seeds 实测，跑完会把每档数字打到 stdout）：旧成长表能把 dps 叠到
 * 720，第 11 波之后场上无法聚集、站着不动也能通关普通；收紧叠乘与上限、并把三档标量重锚到
 * 0.7/1.0/1.35 后得到：挂机普通 0/5（均 6.8 波）、乱选卡普通 0/5（均 11.2 波、后半程同屏 9.6 只、
 * 12 波里 6 波掉血）、会走位会选卡普通 5/5（最低血 5）、困难乱选卡 0/5、困难会选卡 3/5、
 * 简单挂机 3/5、简单会走位 4/5。下面的区间就是这组数，任一侧漂移都会红。</p>
 */
public class BrotatoBalanceTest {

    private static final int SEEDS = 5;
    private static final int TICK_BUDGET = 400_000;      // 6400s 内部时钟，远大于任何正常局长

    enum Player { STANDING_STILL, MOVES_BLINDLY, MOVES_AND_BUILDS }

    static final class Stats {
        int wins;
        int runs;
        double avgWavesReached;
        double avgRunSec;
        double maxWaveSec;
        double minHpWorst;
        double avgLatePeakLive;
        double lateWavesWithDamage;
        double lateWavesTotal;
    }

    private static BrotatoContent content;
    private static Stats stillEasy, stillNormal, blindEasy, blindNormal, skilledNormal, blindHard,
            skilledHard;

    @BeforeClass
    public static void runEveryScenarioOnce() throws IOException {
        Path root = findAssetsRoot();
        content = BrotatoContent.load(
                read(root.resolve("brotato/manifest.json")),
                read(root.resolve("brotato/enemies.json")),
                read(root.resolve("brotato/waves.json")));
        stillEasy = measure("still/easy", Player.STANDING_STILL, BrotatoArena.DIFFICULTY_EASY);
        blindEasy = measure("blind/easy", Player.MOVES_BLINDLY, BrotatoArena.DIFFICULTY_EASY);
        stillNormal = measure("still/normal", Player.STANDING_STILL, BrotatoArena.DIFFICULTY_NORMAL);
        blindNormal = measure("blind/normal", Player.MOVES_BLINDLY, BrotatoArena.DIFFICULTY_NORMAL);
        skilledNormal = measure("skilled/normal", Player.MOVES_AND_BUILDS, BrotatoArena.DIFFICULTY_NORMAL);
        blindHard = measure("blind/hard", Player.MOVES_BLINDLY, BrotatoArena.DIFFICULTY_HARD);
        skilledHard = measure("skilled/hard", Player.MOVES_AND_BUILDS, BrotatoArena.DIFFICULTY_HARD);
    }

    // ==== 难度分档：挂机必须输掉普通，熟练玩家必须能吃下普通 =========================

    @Test
    public void standingStillCannotClearNormalDifficulty() {
        assertEquals("普通难度下挂机（不走位）必须一局都赢不了，实测通关 " + stillNormal.wins
                + "/" + stillNormal.runs, 0, stillNormal.wins);
        assertTrue("挂机局应在第 14 波前崩掉（实测平均 " + stillNormal.avgWavesReached + " 波）",
                stillNormal.avgWavesReached < 14);
    }

    @Test
    public void easyStaysForgivingEvenForIdlePlay() {
        assertTrue("简单是给路人的：挂机也该偶尔能通关（实测 " + stillEasy.wins + "/"
                + stillEasy.runs + "）", stillEasy.wins >= 2);
        assertTrue("简单难度下会走位的玩家几乎必通（实测 " + blindEasy.wins + "/" + blindEasy.runs
                + "）", blindEasy.wins >= blindEasy.runs - 1);
    }

    @Test
    public void movingPlayerWithSensibleCardsClearsNormal() {
        assertEquals("普通难度下会走位且会选卡的玩家应通关全部种子局（实测 " + skilledNormal.wins
                + "/" + skilledNormal.runs + "）", skilledNormal.runs, skilledNormal.wins);
        assertTrue("普通通关不该是满血散步，至少要被打掉血（实测最低血量 "
                + skilledNormal.minHpWorst + "）", skilledNormal.minHpWorst < 10);
        assertTrue("同样会走位但乱选卡的玩家必须被打进残局（实测平均 " + round(blindNormal.avgWavesReached)
                + " 波），仍显著低于会选卡的玩家（" + round(skilledNormal.avgWavesReached) + " 波）",
                blindNormal.avgWavesReached >= 10 && blindNormal.avgWavesReached < skilledNormal.avgWavesReached);
    }

    @Test
    public void hardIsContestedRatherThanImpossible() {
        assertEquals("困难难度下乱选卡必须输（实测 " + blindHard.wins + "/" + blindHard.runs
                + " 胜）", 0, blindHard.wins);
        assertTrue("困难难度该由熟练玩家来破，但别让他全胜（实测 " + skilledHard.wins + "/"
                + skilledHard.runs + " 胜）", skilledHard.wins >= 1
                && skilledHard.wins < skilledHard.runs);
    }

    // ==== 波次节奏：既不能干等刷怪，也不能一波秒杀 ================================

    @Test
    public void noWaveStallsOrSnowballsOnNormal() {
        assertTrue("最慢的一波耗时必须仍在可读区间（实测 " + round(skilledNormal.maxWaveSec)
                        + "s），超过 45s 说明这波在干等刷怪",
                skilledNormal.maxWaveSec <= 45.0);
        assertTrue("整局时长应落在 3-12 分钟（实测 " + round(skilledNormal.avgRunSec) + "s）",
                skilledNormal.avgRunSec > 180 && skilledNormal.avgRunSec < 720);
        // 口径取「会走位但乱选卡」这一档：它最接近真实玩家，满配脚本会把后半程打得太干净。
        assertTrue("后半程（8-19 波）平均同屏敌人必须成群（实测 " + round(blindNormal.avgLatePeakLive)
                + "），太小说明弹幕把怪清空了、走位失去意义", blindNormal.avgLatePeakLive >= 6.0);
        assertTrue("后半程至少四成波次要真的掉血（实测 " + round(blindNormal.lateWavesWithDamage)
                + "/" + (int) blindNormal.lateWavesTotal + " 波有接触伤害）",
                blindNormal.lateWavesWithDamage >= blindNormal.lateWavesTotal * 0.4);
    }

    @Test
    public void upgradeCeilingStaysWithinTheWaveLaddersPressure() {
        double maxBuildDps = Upgrade.MAX_SHOT_COUNT * Upgrade.MAX_BULLET_DAMAGE
                / (Upgrade.MIN_FIRE_INTERVAL_MS / 1000.0);
        double finaleArrival = finaleArrivalHPPerSecond();
        assertTrue("满配 dps（" + round(maxBuildDps) + "）必须能打完终幕到达率（"
                + round(finaleArrival) + " hp/s）的 1.2 倍以上，否则最后几波必输",
                maxBuildDps >= finaleArrival * 1.2);
        assertTrue("满配 dps（" + round(maxBuildDps) + "）不得超过终幕到达率（"
                + round(finaleArrival) + " hp/s）的 12 倍，否则第 11 波之后场上无法聚集",
                maxBuildDps <= finaleArrival * 12);
    }

    /** 最后一波（含 Boss 组）按出怪间隔折算的血量到达率，单位 hp/s。 */
    private static double finaleArrivalHPPerSecond() {
        var levels = content.levels();
        var level = levels.get(levels.size() - 1);
        int weight = level.totalWeight();
        double hp = 0;
        for (var entry : level.composition()) {
            hp += content.kinds().get(entry.kind()).hp() * entry.weight();
        }
        double perSpawn = hp / weight;
        double intervalSec = level.spawnIntervalMs() / 1000.0;
        double arrival = perSpawn / intervalSec;
        var boss = level.bossGroup();
        if (boss != null) {
            arrival += content.kinds().get(boss.kind()).hp() / (boss.intervalMs() / 1000.0);
        }
        return arrival;
    }

    // ==== 引擎驱动 =================================================================

    private static Stats measure(String label, Player player, float scalar) {
        Stats s = new Stats();
        double[] waveSec = new double[content.totalWaves() + 1];
        double[] waveLost = new double[content.totalWaves() + 1];
        double[] waveLive = new double[content.totalWaves() + 1];
        int[] waveSeen = new int[content.totalWaves() + 1];
        for (int i = 1; i <= SEEDS; i++) {
            Run r = play(player, scalar, 4242L + i * 7919L);
            s.runs++;
            if ("WIN".equals(r.outcome)) s.wins++;
            s.avgWavesReached += r.wavesReached;
            s.avgRunSec += r.ticks * BrotatoArena.TICK_MS / 1000.0;
            s.minHpWorst = i == 1 ? r.minHp : Math.min(s.minHpWorst, r.minHp);
            for (int w = 1; w <= content.totalWaves(); w++) {
                if (r.sec[w] <= 0) continue;
                waveSec[w] += r.sec[w];
                waveLost[w] += r.lost[w];
                waveLive[w] += r.live[w];
                waveSeen[w]++;
            }
        }
        s.avgWavesReached /= s.runs;
        s.avgRunSec /= s.runs;
        for (int w = 8; w <= content.totalWaves() - 1; w++) {
            if (waveSeen[w] == 0) continue;
            s.maxWaveSec = Math.max(s.maxWaveSec, waveSec[w] / waveSeen[w]);
            s.avgLatePeakLive += waveLive[w] / waveSeen[w];
            if (waveLost[w] / waveSeen[w] > 0) s.lateWavesWithDamage++;
            s.lateWavesTotal++;
        }
        s.avgLatePeakLive /= Math.max(1, s.lateWavesTotal);
        System.out.println("Brotato balance " + label + ": wins=" + s.wins + "/" + s.runs
                + " avgWave=" + round(s.avgWavesReached) + " runSec=" + round(s.avgRunSec)
                + " worstHp=" + round(s.minHpWorst) + " slowestWave=" + round(s.maxWaveSec)
                + " latePeakLive=" + round(s.avgLatePeakLive) + " lateWavesHurt="
                + round(s.lateWavesWithDamage) + "/" + (int) s.lateWavesTotal);
        return s;
    }

    static final class Run {
        String outcome;
        int wavesReached;
        int ticks;
        int minHp;
        final double[] sec = new double[40];
        final double[] lost = new double[40];
        final double[] live = new double[40];
    }

    private static Run play(Player player, float scalar, long seed) {
        BrotatoArena arena = new BrotatoArena(content, scalar, seed);
        arena.setBounds(360f, 640f);
        arena.start();
        Run run = new Run();
        run.minHp = arena.hp();
        int wave = arena.wave();
        int waveTick = 0;
        int hp = arena.hp();
        int guard = 0;
        while (!arena.isTerminal() && guard++ < TICK_BUDGET) {
            if (arena.state() == BrotatoArena.State.REWARD_PENDING) {
                arena.selectUpgrade(card(player, arena));
                continue;
            }
            steer(player, arena);
            int before = arena.wave();
            arena.tick();
            if (arena.hp() < hp) {
                run.lost[Math.min(wave, run.lost.length - 1)] += hp - arena.hp();
                run.minHp = Math.min(run.minHp, arena.hp());
            }
            hp = arena.hp();
            int w = Math.min(wave, run.live.length - 1);
            run.live[w] = Math.max(run.live[w], arena.enemies().size());
            if (before != wave) {
                if (wave > 0 && wave < run.sec.length) {
                    run.sec[wave] = (guard - waveTick) * BrotatoArena.TICK_MS / 1000.0;
                }
                waveTick = guard;
                wave = before;
            }
        }
        run.ticks = guard;
        run.wavesReached = arena.wave();
        run.outcome = arena.state() == BrotatoArena.State.WIN ? "WIN"
                : arena.state() == BrotatoArena.State.GAME_OVER ? "DEAD" : "TIMEOUT";
        assertTrue("脚本对局在 " + TICK_BUDGET + " tick 内没有结束（state=" + arena.state()
                + " wave=" + arena.wave() + "），数值闭环失控", !"TIMEOUT".equals(run.outcome));
        return run;
    }

    private static int card(Player player, BrotatoArena arena) {
        Upgrade[] cards = arena.pendingCards();
        if (cards == null || cards.length == 0) return 0;
        if (player != Player.MOVES_AND_BUILDS) return 0;
        int best = 0;
        double bestValue = -1;
        for (int i = 0; i < cards.length; i++) {
            double v;
            switch (cards[i]) {
                case DAMAGE_UP: v = arena.shotCount() < 2 ? 5 : 4; break;
                case MULTISHOT: v = arena.shotCount() < Upgrade.MAX_SHOT_COUNT ? 4.5 : 1; break;
                case RAPID_FIRE: v = arena.fireIntervalMs() > Upgrade.MIN_FIRE_INTERVAL_MS ? 4 : 1;
                    break;
                case VITALITY: v = arena.hp() <= 4 ? 6 : 2; break;
                case REGEN: v = arena.wave() > 8 ? 3 : 1.5; break;
                default: v = arena.moveSpeed() < 8 ? 2.5 : 1; break;
            }
            if (v > bestValue) {
                bestValue = v;
                best = i;
            }
        }
        return best;
    }

    /** 风筝走位：背离近处敌人、避开四角；挂机档则完全不发输入。 */
    private static void steer(Player player, BrotatoArena arena) {
        if (player == Player.STANDING_STILL) {
            arena.setTouchTarget(0f, 0f, false);
            return;
        }
        float px = arena.playerX(), py = arena.playerY();
        float w = arena.boundsWidth(), h = arena.boundsHeight();
        float reach = 140f;
        float fx = 0f, fy = 0f;
        for (Enemy enemy : arena.enemies()) {
            float dx = px - enemy.x, dy = py - enemy.y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < 0.001f) {
                fx += 1f;
                continue;
            }
            if (d > reach) continue;
            float weight = (reach - d) / reach;
            fx += dx / d * weight;
            fy += dy / d * weight;
        }
        float margin = 60f;
        if (px < margin) fx += (margin - px) / margin * 1.5f;
        if (px > w - margin) fx -= (px - (w - margin)) / margin * 1.5f;
        if (py < margin) fy += (margin - py) / margin * 1.5f;
        if (py > h - margin) fy -= (py - (h - margin)) / margin * 1.5f;
        if (player == Player.MOVES_AND_BUILDS) {     // 熟练档还会往场心回收
            fx += (w / 2f - px) / w * 0.6f;
            fy += (h / 2f - py) / h * 0.6f;
        }
        float norm = (float) Math.sqrt(fx * fx + fy * fy);
        if (norm < 0.0001f) {
            arena.setTouchTarget(px, py, false);
            return;
        }
        float step = Math.max(arena.moveSpeed() * 4f, 40f);
        arena.setTouchTarget(clamp(px + fx / norm * step, 12f, w - 12f),
                clamp(py + fy / norm * step, 12f, h - 12f), true);
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static String round(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

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
}
