package com.gamecenter.app.td.engine;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * TD 数据层英文化回归守卫（镜像 doudizhu 的 ModelEnglishDataTest）。
 *
 * <p>守卫不变量：engine 包的展示名（TowerType/MonsterType/Difficulty/TargetMode）、
 * HUD 波次预告输出与操作结果参数必须是英文中性数据，不得内联中文；
 * UI 侧需要中文时一律走宿主本地化资源（com.gamecenter.app.R.string.game_td_*）。
 * 若有人把中文写回 engine 层，本测试失败。
 *
 * <p>关卡资产层：章节 JSON 的 name/subtitle 是中文权威源，name_en/subtitle_en 是英文展示字段，
 * 由 {@link TdLevels} 按 locale 解析。守卫锁定全部 35 关英文字段非空且不含中文
 * （漏填会被解析器静默回退成中文，在此处被拦截）。
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

    // ===== 图鉴数据守卫（Codex 浮层依赖的枚举字段必须可安全格式化）=====

    /**
     * 图鉴塔属性行的取值合法性：全部浮点字段有限（无 null 概念、禁 NaN/Infinity），
     * 攻击塔 damage/range/fireInterval 必须为正；非攻击塔按枚举语义单独断言——
     * 太阳花 damage=0 合法但收益必须为正（收益行依赖），增幅塔 damage=0 合法但
     * 光环射程必须为正（射程行依赖）。别把「非攻击塔 damage=0」误判为脏数据。
     */
    @Test
    public void codexTowerStatsAreFiniteAndSemanticallyValid() {
        for (TowerType tower : TowerType.values()) {
            String what = "TowerType." + tower.name();
            assertTrue(what + ".baseCost 必须为正（造价行依赖）", tower.baseCost > 0);
            assertTrue(what + ".dmgMul 必须有限", Float.isFinite(tower.dmgMul));
            assertTrue(what + ".range 必须有限", Float.isFinite(tower.range));
            assertTrue(what + ".damage 必须有限", Float.isFinite(tower.damage));
            assertTrue(what + ".fireInterval 必须有限", Float.isFinite(tower.fireInterval));
            assertTrue(what + ".income 必须有限", Float.isFinite(tower.income));
            assertTrue(what + ".range 不得为负", tower.range >= 0f);
            assertTrue(what + ".damage 不得为负", tower.damage >= 0f);
            assertTrue(what + ".fireInterval 不得为负", tower.fireInterval >= 0f);
            if (tower.damage > 0f) {
                assertTrue(what + " 攻击塔射程必须大于 0", tower.range > 0f);
                assertTrue(what + " 攻击塔开火间隔必须大于 0", tower.fireInterval > 0f);
            } else if (tower == TowerType.SUN) {
                // 非攻击塔合法形态：太阳花零伤害零射程，但必须产出金币
                assertEquals(what + " 太阳花射程应为 0", 0f, tower.range, 0f);
                assertTrue(what + " 太阳花收益必须大于 0", tower.income > 0f);
            } else if (tower == TowerType.AMPLIFIER) {
                // 非攻击塔合法形态：增幅塔零伤害，但光环射程必须大于 0
                assertTrue(what + " 增幅塔光环射程必须大于 0", tower.range > 0f);
            } else {
                fail(what + " 出现未知形态的非攻击塔（damage=0），图鉴属性行分支需同步覆盖");
            }
        }
    }

    /**
     * 图鉴怪属性行的取值合法性：生命/速度/护甲/赏金/漏蛋伤害全部可安全格式化；
     * 飞行标记仅 FLY 为真（「飞行：仅可对空塔能攻击」特性行只挂在 FLY 上）；
     * 护盾兵初始护盾必须为正（「自带护盾」特性行与引擎 shieldHp 契约）。
     */
    @Test
    public void codexMonsterStatsAreFiniteAndSemanticallyValid() {
        for (MonsterType monster : MonsterType.values()) {
            String what = "MonsterType." + monster.name();
            assertTrue(what + ".hp 必须为正且有限",
                    monster.hp > 0f && Float.isFinite(monster.hp));
            assertTrue(what + ".speed 必须为正且有限",
                    monster.speed > 0f && Float.isFinite(monster.speed));
            assertTrue(what + ".value 必须为正（赏金行依赖）", monster.value > 0);
            assertTrue(what + ".armor 不得为负", monster.armor >= 0);
            assertTrue(what + ".leakDamage 必须为正（漏蛋伤害行依赖）", monster.leakDamage > 0);
            assertEquals(what + " 仅 FLY 可飞行", monster == MonsterType.FLY, monster.fly);
        }
        assertEquals("护盾兵初始护盾必须为正", 50,
                MonsterType.SHIELD.shieldHp(MonsterType.SHIELD));
        assertEquals("非护盾兵不得自带护盾", 0,
                MonsterType.NORMAL.shieldHp(MonsterType.NORMAL));
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

    // ===== 关卡资产层守卫（章节 JSON 的中英双字段）=====

    @Test
    public void campaignLevelsCarryEnglishDisplayFields() throws Exception {
        List<TdLevelDefinition> levels = loadProductionCampaign();
        assertEquals("manifest 章节关卡总数应为各章 levelCount 之和",
                manifestLevelCountTotal, levels.size());
        for (TdLevelDefinition level : levels) {
            assertTrue("关卡 id 须为 main_###，实际: " + level.id,
                    level.id.matches("main_\\d{3}"));
            // 中文权威源：必须非空（允许中文）
            assertTrue("level " + level.id + " name 不得为空", !level.name.isEmpty());
            assertTrue("level " + level.id + " subtitle 不得为空", !level.subtitle.isEmpty());
            // 英文字段：必须填写且不含中文；漏填时解析器会回退中文源文本，在此被拦截
            assertNoCjk("level " + level.id + " nameEn", level.nameEn);
            assertTrue("level " + level.id + " nameEn 不得为空", !level.nameEn.isEmpty());
            assertNoCjk("level " + level.id + " subtitleEn", level.subtitleEn);
            assertTrue("level " + level.id + " subtitleEn 不得为空", !level.subtitleEn.isEmpty());
        }
    }

    @Test
    public void levelDisplayFieldsResolveByLocale() throws Exception {
        List<TdLevelDefinition> levels = loadProductionCampaign();
        TdLevels.installForTesting(levels);
        TdLevelDefinition first = levels.get(0);
        assertEquals("main_001", first.id);
        Locale original = Locale.getDefault();
        try {
            // zh 开头 → 中文权威源；其余 → 英文字段（与宿主资源行为一致）
            Locale.setDefault(Locale.CHINA);
            assertEquals(first.name, TdLevels.levelDisplayName(0, "main_001"));
            assertEquals(first.subtitle, TdLevels.levelSub(0, "main_001"));
            Locale.setDefault(Locale.US);
            assertEquals(first.nameEn, TdLevels.levelDisplayName(0, "main_001"));
            assertEquals(first.subtitleEn, TdLevels.levelSub(0, "main_001"));
        } finally {
            Locale.setDefault(original);
        }
    }

    /** manifest 各章 levelCount 之和,由 {@link #loadProductionCampaign()} 加总,随战役扩展动态增长。 */
    private static int manifestLevelCountTotal;

    /** 从模块 assets 读取全部章节并解析（镜像 TdGameTest 的资产定位方式）。 */
    private static List<TdLevelDefinition> loadProductionCampaign() throws IOException {
        Path assetsRoot = findAssetsRoot();
        TdLevelJsonParser.Manifest manifest = TdLevelJsonParser.parseManifest(
                new String(Files.readAllBytes(assetsRoot.resolve("td/manifest.json")),
                        StandardCharsets.UTF_8));
        List<TdLevelDefinition> levels = new ArrayList<>();
        manifestLevelCountTotal = 0;
        for (TdLevelJsonParser.ChapterRef chapter : manifest.chapters) {
            TdLevelJsonParser.Chapter parsed = TdLevelJsonParser.parseChapter(new String(
                    Files.readAllBytes(assetsRoot.resolve("td").resolve(chapter.file)),
                    StandardCharsets.UTF_8));
            assertEquals("chapter " + chapter.file + " levelCount 与 manifest 不一致",
                    chapter.levelCount, parsed.levels.size());
            manifestLevelCountTotal += chapter.levelCount;
            levels.addAll(parsed.levels);
        }
        return levels;
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
