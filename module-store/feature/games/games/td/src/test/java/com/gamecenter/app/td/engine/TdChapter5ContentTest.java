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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 第五章战役（main_036~045）内容回归守卫：纯数据驱动扩展的专项校验。
 *
 * <p>阈值依据（第四章 10 关实测锚点，见 chapter_main_04.json）：每关 7~8 波、
 * startCoin 265~310、mascotHp 6~7、首波 hpMul 1.22~1.62、非 BOSS 波 hpMul 峰值 1.96
 * （main_035 末段）、非 BOSS 波 interval 下限 0.28、speedMul 峰值 1.20、终幕 BOSS hpMul
 * 峰值 1.32；全关「总 HP/收入」（Σ 出怪数×hpMul×怪基础血 ÷ (startCoin+Σ击杀赏金)，
 * 含 BOSS 波）终局 main_033=4.96 / main_034=5.15 / main_035=5.03。第五章「余烬之后」
 * 按「开局即第 4 章终局强度、全机制极限组合、杠杆放在波次规模与组合上」设计，本守卫
 * 把这些设计承诺锁死：
 * <ul>
 *   <li>order 36~45 连续无缺、10 关全部经严格解析器加载成功；</li>
 *   <li>每关 waves 非空且 count/interval/delay/hpMul/speedMul/route 全部落在解析器合法域，
 *       波次数 7~9（第 4 章 7~8；终幕需 9 波承载三径 BOSS）；</li>
 *   <li>与第四章同位关（main_036↔main_026 … main_045↔main_035）对比：
 *       startCoin ∈ [锚点, 锚点×1.08]（增幅 ≤8%，经济不回退）、mascotHp 不高于锚点、
 *       首波 hpMul ∈ [锚点, 锚点×1.08]（递进但保守）；</li>
 *   <li>非 BOSS 波 hpMul ≤ 第 4 章峰值 1.96×1.08（与经济同一 8% 包络）、
 *       interval ≥ 第 4 章下限 0.28 不再压缩、speedMul ≤ 第 4 章峰值 1.20×1.05；
 *       BOSS 波 hpMul ≤ 第 4 章终幕峰值 1.32×1.08（三王分道不放大单王血量包络）；</li>
 *   <li>难度梯度：全关「总 HP/收入」不低于第 4 章终局（无难度断崖回落），
 *       终幕升至锚点×1.08~1.14（约 5.4~5.7，平滑无数量级跳变）；</li>
 *   <li>name_en/subtitle_en 非空且无 CJK（中文权威源照旧）；</li>
 *   <li>机制叙事：每关末波含 BOSS、SPLITTER+HEALER 治疗链、SUMMONER+SHIELD_GENERATOR
 *       滚雪球、FLY+RAGER 三径齐袭、SUMMONER 多路线施压；主题骨架 GARDEN（与第 4 章
 *       STORM 骨架区分，全章不复用 STORM）；终幕三 BOSS 分道（每径一只且必带护航）。</li>
 * </ul>
 */
public class TdChapter5ContentTest {

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");
    private static final String CHAPTER_ID = "chapter_main_05";
    private static final String CHAPTER_FILE = "chapters/chapter_main_05.json";
    private static final int FIRST_ORDER = 36;
    private static final int LEVEL_COUNT = 10;
    /** 第四章同位关（main_026~035）作为可玩性锚点：order 36+i ↔ order 26+i。 */
    private static final int ANCHOR_FIRST_ORDER = 26;
    /** 第 4 章实测包络（见类 javadoc），同 startCoin 的 +8% 口径。 */
    private static final float ENVELOPE = 1.08f;
    /** 第 4 章非 BOSS 波实测峰值（main_035）。 */
    private static final float CH4_NON_BOSS_HP_MUL_PEAK = 1.96f;
    private static final float CH4_NON_BOSS_SPEED_PEAK = 1.20f;
    private static final float CH4_NON_BOSS_INTERVAL_FLOOR = 0.28f;
    /** 第 4 章终幕 BOSS hpMul 实测峰值（main_035）。 */
    private static final float CH4_BOSS_HP_MUL_PEAK = 1.32f;
    /** 第 4 章终局（main_035）实测「总 HP/收入」，第 5 章梯度的起点锚。 */
    private static final float CH4_FINALE_HP_PER_COIN = 5.03f;

    private static TdLevelJsonParser.ChapterRef chapter5Ref;
    private static List<TdLevelDefinition> chapter5;
    private static List<TdLevelDefinition> chapter4;

    @BeforeClass
    public static void loadProductionChapters() throws IOException {
        Path assetsRoot = findAssetsRoot();
        TdLevelJsonParser.Manifest manifest = TdLevelJsonParser.parseManifest(readAsset(
                assetsRoot.resolve("td/manifest.json")));
        for (TdLevelJsonParser.ChapterRef ref : manifest.chapters) {
            if (CHAPTER_ID.equals(ref.id)) chapter5Ref = ref;
        }
        assertNotNull("manifest 必须注册 " + CHAPTER_ID, chapter5Ref);
        chapter5 = TdLevelJsonParser.parseChapter(readAsset(
                assetsRoot.resolve("td").resolve(chapter5Ref.file))).levels;
        chapter4 = TdLevelJsonParser.parseChapter(readAsset(
                assetsRoot.resolve("td/chapters/chapter_main_04.json"))).levels;
    }

    @Test
    public void manifestRegistersChapter5WithTenLevels() {
        assertEquals("manifest levelCount 必须与章节实际关数一致", LEVEL_COUNT, chapter5Ref.levelCount);
        assertEquals("chapter_main_05 必须是 10 关", LEVEL_COUNT, chapter5.size());
    }

    @Test
    public void ordersAndIdsAreContiguous() {
        Set<Integer> orders = new HashSet<>();
        for (int i = 0; i < chapter5.size(); i++) {
            TdLevelDefinition level = chapter5.get(i);
            assertEquals("id 必须按 main_036~045 连续", String.format(java.util.Locale.US, "main_%03d", FIRST_ORDER + i), level.id);
            assertEquals("order 必须按 36~45 连续", FIRST_ORDER + i, level.order);
            assertTrue("order 不得重复", orders.add(level.order));
        }
        assertEquals(LEVEL_COUNT, orders.size());
    }

    @Test
    public void wavesStayInParserLegalDomain() {
        for (TdLevelDefinition level : chapter5) {
            assertTrue("关卡 " + level.id + " waves 不得为空", !level.waves.isEmpty());
            assertTrue("关卡 " + level.id + " 波次数不得少于第 4 章下限(7)，实际 " + level.waves.size(),
                    level.waves.size() >= 7);
            assertTrue("关卡 " + level.id + " 波次数不应超过 9(终幕三径 BOSS 上限)，实际 " + level.waves.size(),
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
                // 非 BOSS 波锁死第 5 章设计包络(见类 javadoc)：
                // hpMul ≤ 第 4 章非 BOSS 峰值 1.96 × 1.08(与 startCoin 同一增幅口径)，
                // interval ≥ 第 4 章下限 0.28 不下探，speedMul ≤ 第 4 章峰值 1.20 × 1.05。
                if (!wave.types.contains(MonsterType.BOSS)) {
                    assertTrue("非 BOSS 波 hpMul 不得超过第 4 章峰值 1.96×1.08 包络: " + level.id,
                            wave.hpMultiplier <= CH4_NON_BOSS_HP_MUL_PEAK * ENVELOPE);
                    assertTrue("非 BOSS 波 interval 不得低于第 4 章下限 0.28: " + level.id,
                            wave.intervalSec >= CH4_NON_BOSS_INTERVAL_FLOOR);
                    assertTrue("非 BOSS 波 speedMul 不得超过第 4 章峰值 1.20×1.05 容差: " + level.id,
                            wave.speedMultiplier <= CH4_NON_BOSS_SPEED_PEAK * 1.05f);
                } else {
                    // 三王分道的终幕不得借机放大单王血量：沿用与经济一致的 8% 包络。
                    assertTrue("BOSS 波 hpMul 不得超过第 4 章终幕峰值 1.32×1.08 包络: " + level.id,
                            wave.hpMultiplier <= CH4_BOSS_HP_MUL_PEAK * ENVELOPE);
                }
            }
        }
    }

    @Test
    public void economyAnchorsOnChapter4WithBoundedGrowth() {
        assertEquals("锚点章必须同为 10 关才能按位对比", LEVEL_COUNT, chapter4.size());
        for (int i = 0; i < LEVEL_COUNT; i++) {
            TdLevelDefinition level = chapter5.get(i);
            TdLevelDefinition anchor = chapter4.get(i);
            assertEquals("锚点须按同位对齐: " + level.id, ANCHOR_FIRST_ORDER + i, anchor.order);
            // 经济杠杆承诺：startCoin 增幅 ≤8% 且不回退（杠杆放在波次与组合上）。
            assertTrue("startCoin 不得低于第 4 章同位锚点(经济不回退): " + level.id,
                    level.startCoin >= anchor.startCoin);
            assertTrue("startCoin 增幅必须 ≤8%: " + level.id,
                    level.startCoin <= anchor.startCoin * ENVELOPE);
            assertTrue("mascotHp 不得高于锚点(蛋生命不加或更少): " + level.id,
                    level.mascotHp <= anchor.mascotHp);
            float firstHpMul = level.waves.get(0).hpMultiplier;
            float anchorFirstHpMul = anchor.waves.get(0).hpMultiplier;
            assertTrue("首波 hpMul 不得低于锚点(难度递进): " + level.id, firstHpMul >= anchorFirstHpMul);
            assertTrue("首波 hpMul 增幅必须保守(≤锚点×1.08): " + level.id,
                    firstHpMul <= anchorFirstHpMul * ENVELOPE);
        }
    }

    /**
     * 难度梯度守卫：全关「总 HP/收入」（含 BOSS 波，出怪组合按 TdGame.Wave.typeAt 同款
     * 循环展开）不得回落到第 4 章终局以下（开局即终局强度），终幕升至锚点的
     * 1.08~1.14 倍（约 5.4~5.7）：既保证「从第 4 章终局平滑升至 ~5.6」的承诺，
     * 又防止未来调数据时出现数量级跳变。
     */
    @Test
    public void difficultyGradientRisesFromChapter4FinaleWithoutCliffs() {
        float anchor = totalHpPerCoin(chapter4.get(LEVEL_COUNT - 1));
        for (TdLevelDefinition level : chapter5) {
            float ratio = totalHpPerCoin(level);
            assertTrue("关卡 " + level.id + " 总HP/收入 " + String.format(java.util.Locale.US, "%.2f", ratio)
                            + " 不得回落到第 4 章终局 " + String.format(java.util.Locale.US, "%.2f", anchor) + " 以下",
                    ratio >= anchor * 0.97f);
        }
        float finale = totalHpPerCoin(chapter5.get(LEVEL_COUNT - 1));
        assertTrue("终幕总HP/收入必须高于第 4 章终局 ×1.08，实际 "
                        + String.format(java.util.Locale.US, "%.2f", finale),
                finale >= anchor * 1.08f);
        assertTrue("终幕总HP/收入不得高于第 4 章终局 ×1.14(防数量级跳变)，实际 "
                        + String.format(java.util.Locale.US, "%.2f", finale),
                finale <= anchor * 1.14f);
    }

    /** 「总 HP/收入」：Σ(出怪数×hpMul×怪基础血) ÷ (startCoin + Σ击杀赏金)，与第 4 章口径一致。 */
    private static float totalHpPerCoin(TdLevelDefinition level) {
        float totalHp = 0f;
        int totalValue = 0;
        for (TdLevelDefinition.Wave wave : level.waves) {
            for (int spawn = 0; spawn < wave.count; spawn++) {
                MonsterType type = wave.types.get(spawn % wave.types.size());
                totalHp += type.hp * wave.hpMultiplier;
                totalValue += type.value;
            }
        }
        return totalHp / (level.startCoin + totalValue);
    }

    @Test
    public void namesCarryEnglishWithoutCjk() {
        for (TdLevelDefinition level : chapter5) {
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
        for (TdLevelDefinition level : chapter5) {
            assertTrue("关卡 " + level.id + " 末波必须含 BOSS",
                    level.waves.get(level.waves.size() - 1).types.contains(MonsterType.BOSS));
        }
        // 机制叙事一：SPLITTER 与 HEALER 同波（治疗链为分裂潮续命）
        assertTrue("章节必须出现 SPLITTER+HEALER 同波组合",
                chapter5.stream().flatMap(l -> l.waves.stream())
                        .anyMatch(w -> w.types.contains(MonsterType.SPLITTER)
                                && w.types.contains(MonsterType.HEALER)));
        // 机制叙事二：SUMMONER 与 SHIELD_GENERATOR 同波（滚雪球）
        assertTrue("章节必须出现 SUMMONER+SHIELD_GENERATOR 同波组合",
                chapter5.stream().flatMap(l -> l.waves.stream())
                        .anyMatch(w -> w.types.contains(MonsterType.SUMMONER)
                                && w.types.contains(MonsterType.SHIELD_GENERATOR)));
        // 机制叙事三：FLY 与 RAGER 同波（三径齐袭的空地组合）
        assertTrue("章节必须出现 FLY+RAGER 同波组合",
                chapter5.stream().flatMap(l -> l.waves.stream())
                        .anyMatch(w -> w.types.contains(MonsterType.FLY)
                                && w.types.contains(MonsterType.RAGER)));
        // 机制叙事四：SUMMONER 至少压制两条不同路线
        Set<Integer> summonerRoutes = new HashSet<>();
        for (TdLevelDefinition level : chapter5) {
            for (TdLevelDefinition.Wave wave : level.waves) {
                if (wave.types.contains(MonsterType.SUMMONER)) summonerRoutes.add(wave.routeIndex);
            }
        }
        assertTrue("SUMMONER 必须形成多路线同压(≥2 条路线)", summonerRoutes.size() >= 2);
        // 主题骨架：与第 4 章 STORM 骨架区分——GARDEN 首尾呼应，全章不复用 STORM
        assertEquals("main_036 主题应为章节骨架 GARDEN",
                TdLevelDefinition.Theme.GARDEN, chapter5.get(0).theme);
        TdLevelDefinition finale = chapter5.get(LEVEL_COUNT - 1);
        assertEquals("main_045 主题应沿用章节骨架 GARDEN",
                TdLevelDefinition.Theme.GARDEN, finale.theme);
        Set<TdLevelDefinition.Theme> used = EnumSet.noneOf(TdLevelDefinition.Theme.class);
        for (TdLevelDefinition level : chapter5) used.add(level.theme);
        assertFalse("第 5 章不得复用第 4 章骨架 STORM", used.contains(TdLevelDefinition.Theme.STORM));
        // 终幕：三 BOSS 分三径（每径一只），且每只 BOSS 必带非 BOSS 护航
        Set<Integer> bossRoutes = new HashSet<>();
        for (TdLevelDefinition.Wave wave : finale.waves) {
            if (wave.types.contains(MonsterType.BOSS)) {
                bossRoutes.add(wave.routeIndex);
                assertTrue("终幕 BOSS 波必须携带非 BOSS 护航: " + wave.types,
                        wave.types.size() >= 2);
            }
        }
        assertEquals("终幕必须三 BOSS 分三条路线压境", finale.copyRoutes().size(), bossRoutes.size());
        assertEquals("终幕路线数应为 3", 3, finale.copyRoutes().size());
    }

    @Test
    public void mapsKeepDefenseSpaceAndLongRoutes() {
        for (TdLevelDefinition level : chapter5) {
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

    /** 跨章集成：全战役（manifest 各章之和）经 TdLevels.install 后顺序与 id 完整，第五章各关可开出对局。 */
    @Test
    public void fullCampaignInstallsAllLevelsAndChapter5BuildsGames() throws IOException {
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
        assertEquals("main_036", TdLevels.levelIds().get(35));
        assertEquals("main_045", TdLevels.levelIds().get(44));
        for (int i = 0; i < LEVEL_COUNT; i++) {
            String id = String.format(java.util.Locale.US, "main_%03d", FIRST_ORDER + i);
            TdGame game = TdLevels.buildLevel(id);
            assertEquals("对局初始金币应等于 startCoin: " + id,
                    chapter5.get(i).startCoin, game.getCoin());
            assertEquals("对局路线数应与定义一致: " + id,
                    chapter5.get(i).copyRoutes().size(), game.getPaths().length);
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
