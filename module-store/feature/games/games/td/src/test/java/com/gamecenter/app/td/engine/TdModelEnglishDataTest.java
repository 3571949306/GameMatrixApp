package com.gamecenter.app.td.engine;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * TD 数据层英文化回归守卫（镜像 doudizhu 的 ModelEnglishDataTest）。
 *
 * <p>守卫不变量：engine 包的展示名（TowerType/MonsterType/Difficulty/TargetMode）、
 * HUD 波次预告输出与操作结果参数必须是英文中性数据，不得内联中文；
 * UI 侧需要中文时一律走宿主本地化资源（com.gamecenter.app.R.string.game_td_*）。
 * 若有人把中文写回 engine 层，本测试失败。
 */
public class TdModelEnglishDataTest {

    private static final Pattern CJK = Pattern.compile("[\u4e00-\u9fff]");

    /**
     * null 一律视为违规（英文中性数据不得为空）。
     * 含中文同样违规；两个分支由 {@link #assertNoCjkRejectsChineseAndNull()} 回归锁定。
     */
    private static void assertNoCjk(String what, String value) {
        if (value == null) {
            fail(what + " 不得为 null（英文中性数据必须非空）");
        }
        assertFalse(what + " 不得包含中文，实际为: " + value,
                CJK.matcher(value).find());
    }

    /** 引擎操作结果的参数必须是中性数据：Integer/枚举，枚举的 displayName 亦须无中文。 */
    private static void assertArgsNeutral(String what, Object[] args) {
        for (Object arg : args) {
            if (arg instanceof String) {
                assertNoCjk(what + " 字符串参数", (String) arg);
            } else if (arg instanceof TowerType) {
                assertNoCjk(what + " TowerType.displayName", ((TowerType) arg).displayName);
            } else if (arg instanceof MonsterType) {
                assertNoCjk(what + " MonsterType.displayName", ((MonsterType) arg).displayName);
            } else if (arg instanceof TdGame.TargetMode) {
                assertNoCjk(what + " TargetMode.displayName", ((TdGame.TargetMode) arg).displayName);
            } else {
                assertTrue(what + " 参数须为 Integer（实际: " + arg + "）",
                        arg instanceof Integer);
            }
        }
    }

    @Test
    public void assertNoCjkRejectsChineseAndNull() {
        // 传入含中文 → 必须抛 AssertionError
        assertThrows(AssertionError.class, () -> assertNoCjk("sample.chinese", "小怪"));
        // 传入 null → 同样必须抛 AssertionError（锁死“修复前失败、修复后通过”的结构）
        assertThrows(AssertionError.class, () -> assertNoCjk("sample.null", null));
    }

    @Test
    public void towerDisplayNamesAreEnglish() {
        assertEquals("Bottle", TowerType.BOTTLE.displayName);
        assertEquals("Sunflower", TowerType.SUN.displayName);
        assertEquals("Snow", TowerType.SNOW.displayName);
        assertEquals("Fan", TowerType.FAN.displayName);
        assertEquals("Poison", TowerType.POISON.displayName);
        assertEquals("Rocket", TowerType.ROCKET.displayName);
        assertEquals("Lightning", TowerType.LIGHTNING.displayName);
        assertEquals("Sniper", TowerType.SNIPER.displayName);
        assertEquals("Mine", TowerType.MINE.displayName);
        assertEquals("Amplifier", TowerType.AMPLIFIER.displayName);

        for (TowerType tower : TowerType.values()) {
            assertNoCjk("TowerType." + tower.name() + ".displayName", tower.displayName);
        }
    }

    @Test
    public void monsterDisplayNamesAreEnglish() {
        assertEquals("Normal", MonsterType.NORMAL.displayName);
        assertEquals("Fast", MonsterType.FAST.displayName);
        assertEquals("Tank", MonsterType.TANK.displayName);
        assertEquals("Fly", MonsterType.FLY.displayName);
        assertEquals("Swarm", MonsterType.SWARM.displayName);
        assertEquals("Healer", MonsterType.HEALER.displayName);
        assertEquals("Shield", MonsterType.SHIELD.displayName);
        assertEquals("Boss", MonsterType.BOSS.displayName);
        assertEquals("Splitter", MonsterType.SPLITTER.displayName);
        assertEquals("Charger", MonsterType.CHARGER.displayName);
        assertEquals("Shield Generator", MonsterType.SHIELD_GENERATOR.displayName);
        assertEquals("Summoner", MonsterType.SUMMONER.displayName);
        assertEquals("Resistant", MonsterType.RESISTANT.displayName);
        assertEquals("Rager", MonsterType.RAGER.displayName);

        for (MonsterType monster : MonsterType.values()) {
            assertNoCjk("MonsterType." + monster.name() + ".displayName", monster.displayName);
        }
    }

    @Test
    public void difficultyAndTargetModeDisplayNamesAreEnglish() {
        assertEquals("Easy", TdGame.Difficulty.EASY.displayName);
        assertEquals("Normal", TdGame.Difficulty.NORMAL.displayName);
        assertEquals("Hard", TdGame.Difficulty.HARD.displayName);
        for (TdGame.Difficulty difficulty : TdGame.Difficulty.values()) {
            assertNoCjk("Difficulty." + difficulty.name() + ".displayName", difficulty.displayName);
        }
        assertEquals("First", TdGame.TargetMode.FIRST.displayName);
        assertEquals("Strong", TdGame.TargetMode.STRONG.displayName);
        assertEquals("Weak", TdGame.TargetMode.WEAK.displayName);
        for (TdGame.TargetMode mode : TdGame.TargetMode.values()) {
            assertNoCjk("TargetMode." + mode.name() + ".displayName", mode.displayName);
        }
    }

    @Test
    public void wavePreviewNamesAreEnglish() {
        // 单类型波次：预告即类型英文名
        assertEquals("Normal",
                new TdGame.Wave(MonsterType.NORMAL, 3, 1f, 0f, 1f, 1f).previewName());
        // 混编波次：以 "+" 连接，无任何中文前缀（旧版为「混编：」）
        TdGame.Wave mixed = new TdGame.Wave(
                new MonsterType[] {MonsterType.NORMAL, MonsterType.TANK},
                0, 5, 1f, 0f, 1f, 1f);
        assertEquals("Normal+Tank", mixed.previewName());
        assertNoCjk("mixed wave previewName", mixed.previewName());
        assertTrue("两种怪构成的波次必须被识别为混编", mixed.isMixedComposition());
        assertArrayEquals(new MonsterType[] {MonsterType.NORMAL, MonsterType.TANK},
                mixed.compositionTypes());
    }

    @Test
    public void hudPreviewOutputsAreEnglish() {
        TdGame game = guardGame();
        assertNoCjk("nextWaveTypeName", game.nextWaveTypeName());
        assertEquals(1, game.nextWaveComposition().size());
        assertTrue(game.startNextWaveEarly());
        assertNoCjk("nextWaveTypeName after first wave", game.nextWaveTypeName());
    }

    @Test
    public void actionResultsAreNeutralCodesWithoutInlineText() {
        TdGame game = guardGame();
        // 失败路径：越界建塔 → 中性消息码，无内联文案
        assertNull(game.placeTower(TowerType.BOTTLE, -1, 0));
        assertEquals(TdGame.ActionMsg.OUT_OF_BOUNDS, game.getLastActionMsg());
        assertEquals("err", game.getLastActionTone());
        assertArgsNeutral("OUT_OF_BOUNDS args", game.getLastActionArgs());
        // 成功路径：PLACED 携带 TowerType 中性参数（不含本地化文案）
        TdGame.Tower tower = game.placeTower(TowerType.BOTTLE, 1, 0);
        assertTrue(tower != null);
        assertEquals(TdGame.ActionMsg.PLACED, game.getLastActionMsg());
        assertEquals("ok", game.getLastActionTone());
        assertArrayEquals(new Object[] {TowerType.BOTTLE}, game.getLastActionArgs());
        // 金币不足：NOT_ENOUGH_COIN 参数为所需金币（Integer）
        TdGame poor = poorGame();
        assertNull(poor.placeTower(TowerType.ROCKET, 1, 0));
        assertEquals(TdGame.ActionMsg.NOT_ENOUGH_COIN, poor.getLastActionMsg());
        assertArrayEquals(new Object[] {TowerType.ROCKET.baseCost}, poor.getLastActionArgs());
    }

    @Test
    public void waveProgressMessagesAreNeutralCodes() {
        TdGame game = guardGame();
        assertTrue(game.startNextWaveEarly());
        assertEquals(TdGame.ActionMsg.FIRST_WAVE_INCOMING, game.getLastActionMsg());
        assertArgsNeutral("FIRST_WAVE_INCOMING args", game.getLastActionArgs());
        // 蛋蛋受击：EGG_HIT 参数为 MonsterType/伤害/剩余生命，全部中性
        forceWaveMonstersToEgg(game);
        assertEquals(TdGame.ActionMsg.EGG_HIT, game.getLastActionMsg());
        assertEquals("err", game.getLastActionTone());
        assertArgsNeutral("EGG_HIT args", game.getLastActionArgs());
    }

    // ===== 测试用最小对局 =====

    private static TdGame guardGame() {
        int[][] path = new int[][] {{0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4}};
        List<TdGame.Wave> waves = new ArrayList<>();
        waves.add(new TdGame.Wave(MonsterType.NORMAL, 2, 1f, 0f, 1f, 1f));
        // 蛋血 1：任意一只怪到达终点即判负，保证受击消息断言确定性
        return new TdGame(5, 3, path, 0, 4, 1000, 1, waves);
    }

    private static TdGame poorGame() {
        int[][] path = new int[][] {{0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4}};
        List<TdGame.Wave> waves = new ArrayList<>();
        waves.add(new TdGame.Wave(MonsterType.NORMAL, 2, 1f, 0f, 1f, 1f));
        return new TdGame(5, 3, path, 0, 4, 1, 10, waves);
    }

    /** 把首波怪直接放到终点格，触发蛋蛋受击消息（不依赖完整模拟）。 */
    private static void forceWaveMonstersToEgg(TdGame game) {
        assertTrue(game.startNextWaveEarly());
        // 零间隔波次会在推进若干帧后出怪；直接快进到全部到达终点
        for (int i = 0; i < 60 * 30 && !game.isEnded(); i++) {
            game.tick();
        }
        assertTrue("模拟必须吃到蛋蛋结束", game.isEnded());
    }
}
