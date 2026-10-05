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
 * 番外章 II「未醒之谷」（chapter_main_07，main_051~055）内容回归守卫。
 *
 * <p>承接 chapter_main_06 终幕尾声「远方还有未醒的山谷」的预告，第二个季轮章：
 * 荆棘守灵、霜镜、雾谷钟、风车镇与长夜本殿五段试炼。数据驱动、零代码接线，
 * 按位锚定番外章 I（main_046~050）：
 * <ul>
 *   <li>manifest 注册 5 关，全战役 55 关，id/order 全局唯一且本章连续；</li>
 *   <li>经济：startCoin ∈ [锚点, ×1.08]、mascotHp ≤ 锚点、首波 hpMul ∈ [锚点, ×1.08]；</li>
 *   <li>包络锁死番外章 I 实测峰值：非 BOSS hpMul ≤ 1.78×1.08、speedMul ≤ 1.1×1.05、
 *       interval ≥ 0.6，BOSS 波 hpMul ≤ 1.42×1.08；</li>
 *   <li>梯度：每关总 HP/收入 ≥ 第 6 章终幕×0.97（开局即上章终局），逐关爬升不回退，
 *       终幕 ×1.08~1.14 见顶；</li>
 *   <li>叙事组合：每关末波含 BOSS；SPLITTER+HEALER 与 FLY+RAGER 同波；SUMMONER 多路线
 *       施压；终幕三 BOSS 分三径各带护航；主题不含 STORM、终幕 GARDEN；剧情四字段全勤、
 *       英文无 CJK、故事无换行；</li>
 *   <li>路线-叙事一致（BL-019 纪律）：文案称双径者 routes==2、称三径者 routes==3，
 *       每线 ≥30 格、可建塔面积达标。</li>
 * </ul>
 */
public class TdChapter7ContentTest {

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");
    private static final String CHAPTER_ID = "chapter_main_07";
    private static final int FIRST_ORDER = 51;
    private static final int LEVEL_COUNT = 5;
    /** 番外章 I（main_046~050）按位锚点。 */
    private static final int ANCHOR_FIRST_ORDER = 46;
    private static final float ENVELOPE = 1.08f;
    private static final float CH6_NON_BOSS_HP_MUL_PEAK = 1.78f;
    private static final float CH6_NON_BOSS_SPEED_PEAK = 1.1f;
    private static final float CH6_NON_BOSS_INTERVAL_FLOOR = 0.6f;
    private static final float CH6_BOSS_HP_MUL_PEAK = 1.42f;

    private static TdLevelJsonParser.ChapterRef chapter7Ref;
    private static List<TdLevelDefinition> chapter7;
    private static List<TdLevelDefinition> chapter6;
    private static List<TdLevelDefinition> fullCampaign;
    private static int manifestDeclaredTotal;

    @BeforeClass
    public static void loadProductionChapters() throws IOException {
        Path assetsRoot = findAssetsRoot();
        TdLevelJsonParser.Manifest manifest = TdLevelJsonParser.parseManifest(readAsset(
                assetsRoot.resolve("td/manifest.json")));
        List<TdLevelDefinition> all = new ArrayList<>();
        int declared = 0;
        for (TdLevelJsonParser.ChapterRef ref : manifest.chapters) {
            TdLevelJsonParser.Chapter chapter = TdLevelJsonParser.parseChapter(readAsset(
                    assetsRoot.resolve("td").resolve(ref.file)));
            all.addAll(chapter.levels);
            declared += ref.levelCount;
            if (CHAPTER_ID.equals(ref.id)) chapter7Ref = ref;
        }
        manifestDeclaredTotal = declared;
        assertNotNull("manifest 必须注册 " + CHAPTER_ID, chapter7Ref);
        fullCampaign = all;
        chapter7 = new ArrayList<>();
        chapter6 = new ArrayList<>();
        for (TdLevelDefinition level : all) {
            if (level.order >= FIRST_ORDER && level.order < FIRST_ORDER + LEVEL_COUNT) chapter7.add(level);
            else if (level.order >= ANCHOR_FIRST_ORDER && level.order < FIRST_ORDER) chapter6.add(level);
        }
    }

    @Test
    public void manifestRegistersChapter7WithFiveLevels() {
        assertEquals("manifest levelCount 必须与章节实际关数一致", LEVEL_COUNT, chapter7Ref.levelCount);
        assertEquals("chapter_main_07 必须是 5 关", LEVEL_COUNT, chapter7.size());
        assertEquals("全战役规模必须等于 manifest 各章 levelCount 之和",
                manifestDeclaredTotal, fullCampaign.size());
        assertEquals("锚点章必须是完整的 5 关", LEVEL_COUNT, chapter6.size());
    }

    @Test
    public void ordersAndIdsAreContiguousAndGlobalUnique() {
        Set<Integer> orders = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (TdLevelDefinition level : fullCampaign) {
            assertTrue("全局 order 不得重复: " + level.id, orders.add(level.order));
            assertTrue("全局 id 不得重复: " + level.id, ids.add(level.id));
        }
        for (int i = 0; i < chapter7.size(); i++) {
            TdLevelDefinition level = chapter7.get(i);
            assertEquals("id 必须按 main_051~055 连续",
                    String.format(java.util.Locale.US, "main_%03d", FIRST_ORDER + i), level.id);
            assertEquals("order 必须按 51~55 连续", FIRST_ORDER + i, level.order);
        }
    }

    @Test
    public void wavesStayInParserLegalDomainAndChapter6Envelope() {
        for (TdLevelDefinition level : chapter7) {
            assertTrue("关卡 " + level.id + " 波次数应在 [6,9]，实际 " + level.waves.size(),
                    level.waves.size() >= 6 && level.waves.size() <= 9);
            for (TdLevelDefinition.Wave wave : level.waves) {
                assertTrue("route 索引必须落在路线数内: " + level.id,
                        wave.routeIndex >= 0 && wave.routeIndex < level.copyRoutes().size());
                assertFalse("wave composition 不得为空: " + level.id, wave.types.isEmpty());
                assertTrue("count ∈ [1,1000]: " + level.id, wave.count >= 1 && wave.count <= 1000);
                if (!wave.types.contains(MonsterType.BOSS)) {
                    assertTrue("非 BOSS 波 hpMul 不得超过番外章 I 峰值 1.78×1.08: " + level.id,
                            wave.hpMultiplier <= CH6_NON_BOSS_HP_MUL_PEAK * ENVELOPE);
                    assertTrue("非 BOSS 波 interval 不得低于番外章 I 下限 0.6: " + level.id,
                            wave.intervalSec >= CH6_NON_BOSS_INTERVAL_FLOOR);
                    assertTrue("非 BOSS 波 speedMul 不得超过番外章 I 峰值 1.1×1.05: " + level.id,
                            wave.speedMultiplier <= CH6_NON_BOSS_SPEED_PEAK * 1.05f);
                } else {
                    assertTrue("BOSS 波 hpMul 不得超过番外章 I BOSS 峰值×1.08: " + level.id,
                            wave.hpMultiplier <= CH6_BOSS_HP_MUL_PEAK * ENVELOPE);
                }
            }
        }
    }

    @Test
    public void economyAnchorsOnChapter6WithBoundedGrowth() {
        for (int i = 0; i < LEVEL_COUNT; i++) {
            TdLevelDefinition level = chapter7.get(i);
            TdLevelDefinition anchor = chapter6.get(i);
            assertEquals("锚点须按同位对齐: " + level.id, ANCHOR_FIRST_ORDER + i, anchor.order);
            assertTrue("startCoin 不得低于锚点(经济不回退): " + level.id,
                    level.startCoin >= anchor.startCoin);
            assertTrue("startCoin 增幅必须 ≤8%: " + level.id,
                    level.startCoin <= anchor.startCoin * ENVELOPE);
            assertTrue("mascotHp 不得高于锚点: " + level.id, level.mascotHp <= anchor.mascotHp);
            float firstHpMul = level.waves.get(0).hpMultiplier;
            float anchorFirstHpMul = anchor.waves.get(0).hpMultiplier;
            assertTrue("首波 hpMul 不得低于锚点(难度递进): " + level.id,
                    firstHpMul >= anchorFirstHpMul);
            assertTrue("首波 hpMul 增幅必须保守(≤锚点×1.08): " + level.id,
                    firstHpMul <= anchorFirstHpMul * ENVELOPE);
        }
    }

    /** 难度梯度守卫：开局即上章终局强度，逐关爬升，终幕 ×1.08~1.14 见顶。 */
    @Test
    public void difficultyGradientStartsAtChapter6FinaleAndPeaksAtItsOwn() {
        float anchor = totalHpPerCoin(chapter6.get(LEVEL_COUNT - 1));
        for (TdLevelDefinition level : chapter7) {
            float ratio = totalHpPerCoin(level);
            assertTrue("关卡 " + level.id + " 总HP/收入不得回落到上章终幕×0.97 以下",
                    ratio >= anchor * 0.97f);
        }
        float previous = 0f;
        for (TdLevelDefinition level : chapter7) {
            float ratio = totalHpPerCoin(level);
            assertTrue("难度必须逐关爬升不回退: " + level.id, ratio >= previous - 1e-6f);
            previous = ratio;
        }
        float finale = totalHpPerCoin(chapter7.get(LEVEL_COUNT - 1));
        assertTrue("终幕总HP/收入必须 ≥ 上章终幕×1.08，实际 " + finale, finale >= anchor * 1.08f);
        assertTrue("终幕总HP/收入不得 > 上章终幕×1.14(防数量级跳变)，实际 " + finale,
                finale <= anchor * 1.14f);
    }

    /** 「总 HP/收入」：Σ(出怪数×hpMul×怪基础血) ÷ (startCoin + Σ击杀赏金)，与各章口径一致。 */
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
    public void namesAndStoriesAreBilingualWithoutCjkInEnglish() {
        for (TdLevelDefinition level : chapter7) {
            assertFalse(level.name.isEmpty());
            assertNotNull(level.nameEn);
            assertFalse("name_en 不得为空: " + level.id, level.nameEn.isEmpty());
            assertFalse("name_en 不得含 CJK: " + level.id, CJK.matcher(level.nameEn).find());
            assertFalse("subtitle_en 不得含 CJK: " + level.id,
                    CJK.matcher(level.subtitleEn).find());
            for (String story : new String[] {level.storyIntro, level.storyIntroEn,
                    level.storyOutro, level.storyOutroEn}) {
                assertFalse("番外 II 每关剧情四字段必须全勤: " + level.id,
                        story == null || story.trim().isEmpty());
                assertTrue("故事不得含换行: " + level.id, !story.contains("\n"));
            }
        }
    }

    /** BL-019 纪律的显式化：文案里的路径数承诺必须与 routes 实数一致。 */
    @Test
    public void narrativeLaneClaimsMatchRouteCounts() {
        for (TdLevelDefinition level : chapter7) {
            int routes = level.copyRoutes().size();
            String text = level.subtitle + level.storyIntro;
            if (text.contains("三径") || text.contains("三条")) {
                assertEquals("文案称三径的关卡必须真有 3 条路线: " + level.id, 3, routes);
            }
            if (text.contains("双径") || text.contains("两条")) {
                assertEquals("文案称双径的关卡必须真有 2 条路线: " + level.id, 2, routes);
            }
        }
    }

    @Test
    public void designNarrativeCombosArePresent() {
        for (TdLevelDefinition level : chapter7) {
            assertTrue("关卡 " + level.id + " 末波必须含 BOSS",
                    level.waves.get(level.waves.size() - 1).types.contains(MonsterType.BOSS));
        }
        assertTrue("章节必须出现 SPLITTER+HEALER 同波组合",
                chapter7.stream().flatMap(l -> l.waves.stream())
                        .anyMatch(w -> w.types.contains(MonsterType.SPLITTER)
                                && w.types.contains(MonsterType.HEALER)));
        assertTrue("章节必须出现 FLY+RAGER 同波组合",
                chapter7.stream().flatMap(l -> l.waves.stream())
                        .anyMatch(w -> w.types.contains(MonsterType.FLY)
                                && w.types.contains(MonsterType.RAGER)));
        assertTrue("章节必须出现 SUMMONER+SHIELD_GENERATOR 同波组合",
                chapter7.stream().flatMap(l -> l.waves.stream())
                        .anyMatch(w -> w.types.contains(MonsterType.SUMMONER)
                                && w.types.contains(MonsterType.SHIELD_GENERATOR)));
        Set<Integer> summonerRoutes = new HashSet<>();
        for (TdLevelDefinition level : chapter7) {
            for (TdLevelDefinition.Wave wave : level.waves) {
                if (wave.types.contains(MonsterType.SUMMONER)) summonerRoutes.add(wave.routeIndex);
            }
        }
        assertTrue("SUMMONER 必须形成多路线同压(≥2 条路线)", summonerRoutes.size() >= 2);
        Set<TdLevelDefinition.Theme> used = EnumSet.noneOf(TdLevelDefinition.Theme.class);
        for (TdLevelDefinition level : chapter7) used.add(level.theme);
        assertFalse("番外章 II 不得复用第 4 章骨架 STORM", used.contains(TdLevelDefinition.Theme.STORM));
        TdLevelDefinition finale = chapter7.get(LEVEL_COUNT - 1);
        assertEquals("终幕主题必须 GARDEN 收束", TdLevelDefinition.Theme.GARDEN, finale.theme);
        Set<Integer> bossRoutes = new HashSet<>();
        for (TdLevelDefinition.Wave wave : finale.waves) {
            if (wave.types.contains(MonsterType.BOSS)) {
                bossRoutes.add(wave.routeIndex);
                assertTrue("终幕 BOSS 波必须携带非 BOSS 护航: " + wave.types, wave.types.size() >= 2);
            }
        }
        assertEquals("终幕必须三 BOSS 分三条路线压境", 3, bossRoutes.size());
        assertEquals("终幕路线数应为 3", 3, finale.copyRoutes().size());
    }

    @Test
    public void mapsKeepDefenseSpaceAndLongRoutes() {
        for (TdLevelDefinition level : chapter7) {
            List<int[][]> routes = level.copyRoutes();
            assertTrue("关卡 " + level.id + " 应为 2~3 路线，实际 " + routes.size(),
                    routes.size() >= 2 && routes.size() <= 3);
            Set<Integer> occupied = new HashSet<>();
            for (int[][] route : routes) {
                assertTrue("关卡 " + level.id + " 每条路线须 ≥30 格", route.length >= 30);
                for (int[] point : route) occupied.add(point[0] * level.cols + point[1]);
            }
            int minimum = Math.max(6, (level.rows * level.cols) / 5);
            assertTrue("关卡 " + level.id + " 可建塔格不足",
                    level.rows * level.cols - occupied.size() >= minimum);
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
