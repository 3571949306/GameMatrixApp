package com.gamecenter.app.td.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

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
import java.util.regex.Pattern;

/**
 * 第四章战役（main_026~035）内容回归守卫：纯数据驱动扩展的专项校验。
 *
 * <p>阈值依据（第三章 10 关实测锚点，见 chapter_main_03.json）：每关 4~5 波、
 * startCoin 250~300、mascotHp 6~7、首波 hpMul 1.12~1.50、末波(非 Boss) hpMul ≤1.84、
 * interval ≥0.28、speedMul ≤1.22。第四章按「波次更多(7~8)、单波经济更紧、
 * 关卡增幅保守(首波 hpMul ≤ 锚点×1.15)」设计，本守卫把这些设计承诺锁死：
 * <ul>
 *   <li>order 26~35 连续无缺、10 关全部经严格解析器加载成功；</li>
 *   <li>每关 waves 非空且 count/interval/delay/hpMul/speedMul/route 全部落在解析器合法域；</li>
 *   <li>与第三章同位关（main_026↔main_016 … main_035↔main_025）对比：
 *       startCoin ≥ 0.8×锚点、单波经济(startCoin/波数)严格更紧、mascotHp 不高于锚点；</li>
 *   <li>name_en/subtitle_en 非空且无 CJK（中文权威源照旧）；</li>
 *   <li>机制演进叙事：每关末波含 BOSS、CHARGER+SHIELD_GENERATOR 同波、
 *       RESISTANT+HEALER 铁壁波、SUMMONER 多路线施压、终幕双 BOSS 分道。</li>
 * </ul>
 */
public class TdChapter4ContentTest {

    private static final Pattern CJK = Pattern.compile("[\u4e00-\u9fff]");
    private static final String CHAPTER_ID = "chapter_main_04";
    private static final String CHAPTER_FILE = "chapters/chapter_main_04.json";
    private static final int FIRST_ORDER = 26;
    private static final int LEVEL_COUNT = 10;
    /** 第三章同位关（main_016~025）作为可玩性锚点：order 26+i ↔ order 16+i。 */
    private static final int ANCHOR_FIRST_ORDER = 16;

    private static TdLevelJsonParser.ChapterRef chapter4Ref;
    private static List<TdLevelDefinition> chapter4;
    private static List<TdLevelDefinition> chapter3;

    @BeforeClass
    public static void loadProductionChapters() throws IOException {
        Path assetsRoot = findAssetsRoot();
        TdLevelJsonParser.Manifest manifest = TdLevelJsonParser.parseManifest(readAsset(
                assetsRoot.resolve("td/manifest.json")));
        for (TdLevelJsonParser.ChapterRef ref : manifest.chapters) {
            if (CHAPTER_ID.equals(ref.id)) chapter4Ref = ref;
        }
        assertNotNull("manifest 必须注册 " + CHAPTER_ID, chapter4Ref);
        chapter4 = TdLevelJsonParser.parseChapter(readAsset(
                assetsRoot.resolve("td").resolve(chapter4Ref.file))).levels;
        chapter3 = TdLevelJsonParser.parseChapter(readAsset(
                assetsRoot.resolve("td/chapters/chapter_main_03.json"))).levels;
    }

    @Test
    public void manifestRegistersChapter4WithTenLevels() {
        assertEquals("manifest levelCount 必须与章节实际关数一致", LEVEL_COUNT, chapter4Ref.levelCount);
        assertEquals("chapter_main_04 必须是 10 关", LEVEL_COUNT, chapter4.size());
    }

    @Test
    public void ordersAndIdsAreContiguous() {
        Set<Integer> orders = new HashSet<>();
        for (int i = 0; i < chapter4.size(); i++) {
            TdLevelDefinition level = chapter4.get(i);
            assertEquals("id 必须按 main_026~035 连续", String.format(java.util.Locale.US, "main_%03d", FIRST_ORDER + i), level.id);
            assertEquals("order 必须按 26~35 连续", FIRST_ORDER + i, level.order);
            assertTrue("order 不得重复", orders.add(level.order));
        }
        assertEquals(LEVEL_COUNT, orders.size());
    }

    @Test
    public void wavesStayInParserLegalDomain() {
        for (TdLevelDefinition level : chapter4) {
            assertTrue("关卡 " + level.id + " waves 不得为空", !level.waves.isEmpty());
            assertTrue("关卡 " + level.id + " 波次数应多于第三章(≥6)，实际 " + level.waves.size(),
                    level.waves.size() >= 6);
            assertTrue("关卡 " + level.id + " 波次数不应超过第一章终局(9)，实际 " + level.waves.size(),
                    level.waves.size() <= 9);
            for (TdLevelDefinition.Wave wave : level.waves) {
                assertTrue("route 索引必须落在路线数内: " + level.id,
                        wave.routeIndex >= 0 && wave.routeIndex < level.copyRoutes().size());
                assertTrue("wave composition 不得为空: " + level.id, !wave.types.isEmpty());
                assertTrue("count 必须在 [1,1000]: " + level.id,
                        wave.count >= 1 && wave.count <= 1000);
                assertTrue("interval 必须在 [0,60]: " + level.id,
                        wave.intervalSec >= 0f && wave.intervalSec <= 60f);
                assertTrue("delay 必须在 [0,600]: " + level.id,
                        wave.delaySec >= 0f && wave.delaySec <= 600f);
                assertTrue("hpMul 必须在 [0.01,100]: " + level.id,
                        wave.hpMultiplier >= 0.01f && wave.hpMultiplier <= 100f);
                assertTrue("speedMul 必须在 [0.01,100]: " + level.id,
                        wave.speedMultiplier >= 0.01f && wave.speedMultiplier <= 100f);
                // 非 BOSS 波必须落在第三章锚点的容差域内(见类 javadoc):
                // hpMul ≤ 第三章末波(非 Boss)峰值 1.84 × 1.15 容差(同首波增幅承诺),
                // speedMul ≤ 第三章锚点峰值 1.22 × 1.05 容差,interval ≥ 锚点下限 0.28 不下探。
                if (!wave.types.contains(MonsterType.BOSS)) {
                    assertTrue("非 BOSS 波 hpMul 不得超过第 3 章末波峰值 1.84×1.15 容差: " + level.id,
                            wave.hpMultiplier <= 1.84f * 1.15f);
                    assertTrue("非 BOSS 波 interval 不得低于第 3 章锚点下限 0.28: " + level.id,
                            wave.intervalSec >= 0.28f);
                    assertTrue("非 BOSS 波 speedMul 不得超过第 3 章锚点 1.22×1.05 容差: " + level.id,
                            wave.speedMultiplier <= 1.22f * 1.05f);
                }
            }
        }
    }

    @Test
    public void economyIsTighterThanChapter3AnchorsButFair() {
        assertEquals("锚点章必须同为 10 关才能按位对比", LEVEL_COUNT, chapter3.size());
        for (int i = 0; i < LEVEL_COUNT; i++) {
            TdLevelDefinition level = chapter4.get(i);
            TdLevelDefinition anchor = chapter3.get(i);
            assertEquals("锚点须按同位对齐: " + level.id, ANCHOR_FIRST_ORDER + i, anchor.order);
            float coinPerWave = (float) level.startCoin / level.waves.size();
            float anchorCoinPerWave = (float) anchor.startCoin / anchor.waves.size();
            assertTrue("startCoin 不得低于锚点 0.8 倍(防过度紧缩): " + level.id,
                    level.startCoin >= anchor.startCoin * 0.8f);
            assertTrue("单波经济必须比锚点更紧(波次更多但金币不加成比例): " + level.id,
                    coinPerWave < anchorCoinPerWave);
            assertTrue("mascotHp 不得高于锚点(蛋生命不加或更少): " + level.id,
                    level.mascotHp <= anchor.mascotHp);
            float firstHpMul = level.waves.get(0).hpMultiplier;
            float anchorFirstHpMul = anchor.waves.get(0).hpMultiplier;
            assertTrue("首波 hpMul 不得低于锚点(难度递进): " + level.id, firstHpMul >= anchorFirstHpMul);
            assertTrue("首波 hpMul 增幅必须保守(≤锚点×1.15): " + level.id,
                    firstHpMul <= anchorFirstHpMul * 1.15f);
        }
    }

    @Test
    public void namesCarryEnglishWithoutCjk() {
        for (TdLevelDefinition level : chapter4) {
            assertTrue("关卡 " + level.id + " 中文名不得为空", !level.name.isEmpty());
            assertFalse("关卡 " + level.id + " 副标题不得为空", level.subtitle.isEmpty());
            assertNotNull("关卡 " + level.id + " name_en 不得缺失", level.nameEn);
            assertTrue("关卡 " + level.id + " name_en 不得为空", !level.nameEn.isEmpty());
            assertFalse("关卡 " + level.id + " name_en 不得含 CJK: " + level.nameEn,
                    CJK.matcher(level.nameEn).find());
            assertNotNull("关卡 " + level.id + " subtitle_en 不得缺失", level.subtitleEn);
            assertTrue("关卡 " + level.id + " subtitle_en 不得为空", !level.subtitleEn.isEmpty());
            assertFalse("关卡 " + level.id + " subtitle_en 不得含 CJK: " + level.subtitleEn,
                    CJK.matcher(level.subtitleEn).find());
        }
    }

    @Test
    public void designNarrativeCombosArePresent() {
        // 每关末波含 BOSS（延续全部既有章的终局惯例）
        for (TdLevelDefinition level : chapter4) {
            assertTrue("关卡 " + level.id + " 末波必须含 BOSS",
                    level.waves.get(level.waves.size() - 1).types.contains(MonsterType.BOSS));
        }
        // 新组合一：CHARGER 与 SHIELD_GENERATOR 同波（护盾掩护短冲）
        assertTrue("章节必须出现 CHARGER+SHIELD_GENERATOR 同波组合",
                chapter4.stream().flatMap(l -> l.waves.stream())
                        .anyMatch(w -> w.types.contains(MonsterType.CHARGER)
                                && w.types.contains(MonsterType.SHIELD_GENERATOR)));
        // 新组合二：RESISTANT 与 HEALER 同波的「铁壁波」
        assertTrue("章节必须出现 RESISTANT+HEALER 铁壁组合",
                chapter4.stream().flatMap(l -> l.waves.stream())
                        .anyMatch(w -> w.types.contains(MonsterType.RESISTANT)
                                && w.types.contains(MonsterType.HEALER)));
        // 新组合三：SUMMONER 至少压制两条不同路线
        Set<Integer> summonerRoutes = new HashSet<>();
        for (TdLevelDefinition level : chapter4) {
            for (TdLevelDefinition.Wave wave : level.waves) {
                if (wave.types.contains(MonsterType.SUMMONER)) summonerRoutes.add(wave.routeIndex);
            }
        }
        assertTrue("SUMMONER 必须形成多路线同压(≥2 条路线)", summonerRoutes.size() >= 2);
        // 终幕：双 BOSS 分路线
        TdLevelDefinition finale = chapter4.get(LEVEL_COUNT - 1);
        assertEquals("main_035 主题应沿用章系 STORM", TdLevelDefinition.Theme.STORM, finale.theme);
        Set<Integer> bossRoutes = new HashSet<>();
        for (TdLevelDefinition.Wave wave : finale.waves) {
            if (wave.types.contains(MonsterType.BOSS)) bossRoutes.add(wave.routeIndex);
        }
        assertEquals("终幕必须双 BOSS 分两条路线压境", 2, bossRoutes.size());
    }

    @Test
    public void mapsKeepDefenseSpaceAndLongRoutes() throws IOException {
        for (TdLevelDefinition level : chapter4) {
            List<int[][]> routes = level.copyRoutes();
            assertTrue("关卡 " + level.id + " 应以 2~3 路线为主，实际 " + routes.size(),
                    routes.size() >= 2 && routes.size() <= 3);
            Set<Integer> occupied = new HashSet<>();
            for (int[][] route : routes) {
                assertTrue("关卡 " + level.id + " 每条路线须 ≥30 格（战役守卫规则）",
                        route.length >= 30);
                for (int[] point : route) occupied.add(point[0] * level.cols + point[1]);
            }
            int minimum = Math.max(6, (level.rows * level.cols) / 5);
            assertTrue("关卡 " + level.id + " 可建塔格不足: "
                            + (level.rows * level.cols - occupied.size()) + " < " + minimum,
                    level.rows * level.cols - occupied.size() >= minimum);
        }
    }

    /** 跨章集成：全战役（manifest 各章之和，随内容扩展增长）经 TdLevels.install 后顺序与 id 完整，第四章各关可开出对局。 */
    @Test
    public void fullCampaignInstallsAllLevelsAndChapter4BuildsGames() throws IOException {
        Path assetsRoot = findAssetsRoot();
        TdLevelJsonParser.Manifest manifest = TdLevelJsonParser.parseManifest(readAsset(
                assetsRoot.resolve("td/manifest.json")));
        List<TdLevelDefinition> all = new ArrayList<>();
        int expectedLevelTotal = 0;
        for (TdLevelJsonParser.ChapterRef ref : manifest.chapters) {
            all.addAll(TdLevelJsonParser.parseChapter(readAsset(
                    assetsRoot.resolve("td").resolve(ref.file))).levels);
            expectedLevelTotal += ref.levelCount;
        }
        TdLevels.installForTesting(all);
        assertEquals("全战役应安装 manifest 各章 levelCount 之和",
                expectedLevelTotal, TdLevels.levelIds().size());
        assertEquals("main_026", TdLevels.levelIds().get(25));
        assertEquals("main_035", TdLevels.levelIds().get(34));
        for (int i = 0; i < LEVEL_COUNT; i++) {
            String id = String.format(java.util.Locale.US, "main_%03d", FIRST_ORDER + i);
            TdGame game = TdLevels.buildLevel(id);
            assertEquals("对局初始金币应等于 startCoin: " + id,
                    chapter4.get(i).startCoin, game.getCoin());
            assertEquals("对局路线数应与定义一致: " + id,
                    chapter4.get(i).copyRoutes().size(), game.getPaths().length);
        }
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
}
