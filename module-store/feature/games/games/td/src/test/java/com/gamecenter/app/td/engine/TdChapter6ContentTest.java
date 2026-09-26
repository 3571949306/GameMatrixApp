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
 * 番外章「常青之春」（chapter_main_06，main_046~050）内容回归守卫。
 *
 * <p>主线五章 45 关在 main_045「大地之庭」完结后，番外章以赛季守灵的考验收束
 * 「真正的春天」，全部数据驱动、零代码接线（id 延续 main_### 契约，存档与解锁
 * 链自动覆盖）。按位锚定第 5 章后 5 关（main_041~045）：
 * <ul>
 *   <li>manifest 注册 5 关，id/order 连续且不与他章冲突；</li>
 *   <li>经济杠杆不回退：startCoin ∈ [锚点, 锚点×1.08]，mascotHp ≤ 锚点，
 *       首波 hpMul ∈ [锚点, 锚点×1.08]；</li>
 *   <li>包络锁死第 5 章实测峰值：非 BOSS hpMul ≤ 2.1×1.08、speedMul ≤ 1.2×1.05、
 *       interval ≥ 0.28，BOSS 波 hpMul ≤ 第 5 章 BOSS 峰值×1.08；</li>
 *   <li>难度梯度：每关「总 HP/收入」≥ 第 5 章终幕×0.97（番外开局即终局强度），
 *       终幕升至第 5 章终幕×1.08~1.14，且全章单调爬升在终幕见顶；</li>
 *   <li>机制叙事：每关末波含 BOSS；SPLITTER+HEALER、FLY+RAGER 同波；SUMMONER
 *       多路线施压；终幕三 BOSS 分三径且各带护航；全章主题不含 STORM（第 4 章
 *       骨架）、终幕 GARDEN 收束；剧情四字段全勤且英文无 CJK；</li>
 *   <li>地图纪律：2~3 路线、每线 ≥30 格、可建塔面积达标（BL-019 同款路线-叙事
 *       一致性由双径/三径文案与 routes 实数对应）。</li>
 * </ul>
 */
public class TdChapter6ContentTest {

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");
    private static final String CHAPTER_ID = "chapter_main_06";
    private static final int FIRST_ORDER = 46;
    private static final int LEVEL_COUNT = 5;
    /** 第 5 章后 5 关（main_041~045）按位锚点。 */
    private static final int ANCHOR_FIRST_ORDER = 41;
    private static final float ENVELOPE = 1.08f;
    /** 第 5 章实测包络（生成器 td_side_gen.py 同源口径）。 */
    private static final float CH5_NON_BOSS_HP_MUL_PEAK = 2.1f;
    private static final float CH5_NON_BOSS_SPEED_PEAK = 1.2f;
    private static final float CH5_NON_BOSS_INTERVAL_FLOOR = 0.28f;
    private static final float CH5_BOSS_HP_MUL_PEAK = 1.4f;

    private static TdLevelJsonParser.ChapterRef chapter6Ref;
    private static List<TdLevelDefinition> chapter6;
    private static List<TdLevelDefinition> chapter5Tail;
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
            if (CHAPTER_ID.equals(ref.id)) chapter6Ref = ref;
        }
        manifestDeclaredTotal = declared;
        assertNotNull("manifest 必须注册 " + CHAPTER_ID, chapter6Ref);
        fullCampaign = all;
        chapter6 = new ArrayList<>();
        chapter5Tail = new ArrayList<>();
        for (TdLevelDefinition level : all) {
            if (level.order >= FIRST_ORDER && level.order < FIRST_ORDER + LEVEL_COUNT) chapter6.add(level);
            else if (level.order >= ANCHOR_FIRST_ORDER && level.order < FIRST_ORDER) chapter5Tail.add(level);
        }
    }

    @Test
    public void manifestRegistersChapter6WithFiveLevels() {
        assertEquals("manifest levelCount 必须与章节实际关数一致", LEVEL_COUNT, chapter6Ref.levelCount);
        assertEquals("chapter_main_06 必须是 5 关", LEVEL_COUNT, chapter6.size());
        assertEquals("全战役规模必须等于 manifest 各章 levelCount 之和",
                manifestDeclaredTotal, fullCampaign.size());
    }

    @Test
    public void ordersAndIdsAreContiguousAndGlobalUnique() {
        Set<Integer> orders = new HashSet<>();
        Set<String> ids = new HashSet<>();
        for (TdLevelDefinition level : fullCampaign) {
            assertTrue("全局 order 不得重复: " + level.id, orders.add(level.order));
            assertTrue("全局 id 不得重复: " + level.id, ids.add(level.id));
        }
        for (int i = 0; i < chapter6.size(); i++) {
            TdLevelDefinition level = chapter6.get(i);
            assertEquals("id 必须按 main_046~050 连续",
                    String.format(java.util.Locale.US, "main_%03d", FIRST_ORDER + i), level.id);
            assertEquals("order 必须按 46~50 连续", FIRST_ORDER + i, level.order);
        }
    }

    @Test
    public void wavesStayInParserLegalDomainAndChapter5Envelope() {
        for (TdLevelDefinition level : chapter6) {
            assertTrue("关卡 " + level.id + " 短章波次数应在 [6,9]，实际 " + level.waves.size(),
                    level.waves.size() >= 6 && level.waves.size() <= 9);
            for (TdLevelDefinition.Wave wave : level.waves) {
                assertTrue("route 索引必须落在路线数内: " + level.id,
                        wave.routeIndex >= 0 && wave.routeIndex < level.copyRoutes().size());
                assertFalse("wave composition 不得为空: " + level.id, wave.types.isEmpty());
                assertTrue("count ∈ [1,1000]: " + level.id, wave.count >= 1 && wave.count <= 1000);
                if (!wave.types.contains(MonsterType.BOSS)) {
                    assertTrue("非 BOSS 波 hpMul 不得超过第 5 章峰值 2.1×1.08 包络: " + level.id,
                            wave.hpMultiplier <= CH5_NON_BOSS_HP_MUL_PEAK * ENVELOPE);
                    assertTrue("非 BOSS 波 interval 不得低于第 5 章下限 0.28: " + level.id,
                            wave.intervalSec >= CH5_NON_BOSS_INTERVAL_FLOOR);
                    assertTrue("非 BOSS 波 speedMul 不得超过第 5 章峰值 1.2×1.05: " + level.id,
                            wave.speedMultiplier <= CH5_NON_BOSS_SPEED_PEAK * 1.05f);
                } else {
                    assertTrue("BOSS 波 hpMul 不得超过第 5 章 BOSS 峰值×1.08 包络: " + level.id,
                            wave.hpMultiplier <= CH5_BOSS_HP_MUL_PEAK * ENVELOPE);
                }
            }
        }
    }

    @Test
    public void economyAnchorsOnChapter5TailWithBoundedGrowth() {
        assertEquals("锚点段必须同为 5 关才能按位对比", LEVEL_COUNT, chapter5Tail.size());
        for (int i = 0; i < LEVEL_COUNT; i++) {
            TdLevelDefinition level = chapter6.get(i);
            TdLevelDefinition anchor = chapter5Tail.get(i);
            assertEquals("锚点须按同位对齐: " + level.id, ANCHOR_FIRST_ORDER + i, anchor.order);
            assertTrue("startCoin 不得低于第 5 章同位锚点(经济不回退): " + level.id,
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

    /** 难度梯度守卫：开局即终局强度（≥第 5 章终幕×0.97），终幕 ×1.08~1.14 见顶且全章单调爬升。 */
    @Test
    public void difficultyGradientStartsAtChapter5FinaleAndPeaksAtItsOwn() {
        float anchor = measureChapter5FinaleRatio();
        for (TdLevelDefinition level : chapter6) {
            float ratio = totalHpPerCoin(level);
            assertTrue("关卡 " + level.id + " 总HP/收入不得回落到第 5 章终幕×0.97 以下",
                    ratio >= anchor * 0.97f);
        }
        float previous = 0f;
        for (TdLevelDefinition level : chapter6) {
            float ratio = totalHpPerCoin(level);
            assertTrue("难度必须逐关爬升不回退: " + level.id, ratio >= previous - 1e-6f);
            previous = ratio;
        }
        float finale = totalHpPerCoin(chapter6.get(LEVEL_COUNT - 1));
        assertTrue("终幕总HP/收入必须 ≥ 第 5 章终幕×1.08，实际 " + finale, finale >= anchor * 1.08f);
        assertTrue("终幕总HP/收入不得 > 第 5 章终幕×1.14(防数量级跳变)，实际 " + finale,
                finale <= anchor * 1.14f);
    }

    /** 独立重测第 5 章终幕比率（不依赖 chapter5Tail 末位被番外加载顺序影响）。 */
    private static float measureChapter5FinaleRatio() {
        for (TdLevelDefinition level : fullCampaign) {
            if ("main_045".equals(level.id)) return totalHpPerCoin(level);
        }
        throw new IllegalStateException("main_045 不在战役中");
    }

    /** 「总 HP/收入」：Σ(出怪数×hpMul×怪基础血) ÷ (startCoin + Σ击杀赏金)，与第 4/5 章口径一致。 */
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
        for (TdLevelDefinition level : chapter6) {
            assertFalse("中文名不得为空: " + level.id, level.name.isEmpty());
            assertNotNull(level.nameEn);
            assertFalse("name_en 不得为空: " + level.id, level.nameEn.isEmpty());
            assertFalse("name_en 不得含 CJK: " + level.id, CJK.matcher(level.nameEn).find());
            assertFalse("subtitle_en 不得含 CJK: " + level.id,
                    CJK.matcher(level.subtitleEn).find());
            for (String story : new String[] {level.storyIntro, level.storyIntroEn,
                    level.storyOutro, level.storyOutroEn}) {
                assertFalse("番外每关剧情四字段必须全勤: " + level.id,
                        story == null || story.trim().isEmpty());
                assertTrue("故事不得含换行: " + level.id, !story.contains("\n"));
            }
        }
    }

    @Test
    public void designNarrativeCombosArePresent() {
        for (TdLevelDefinition level : chapter6) {
            assertTrue("关卡 " + level.id + " 末波必须含 BOSS",
                    level.waves.get(level.waves.size() - 1).types.contains(MonsterType.BOSS));
        }
        assertTrue("章节必须出现 SPLITTER+HEALER 同波组合",
                chapter6.stream().flatMap(l -> l.waves.stream())
                        .anyMatch(w -> w.types.contains(MonsterType.SPLITTER)
                                && w.types.contains(MonsterType.HEALER)));
        assertTrue("章节必须出现 FLY+RAGER 同波组合",
                chapter6.stream().flatMap(l -> l.waves.stream())
                        .anyMatch(w -> w.types.contains(MonsterType.FLY)
                                && w.types.contains(MonsterType.RAGER)));
        Set<Integer> summonerRoutes = new HashSet<>();
        for (TdLevelDefinition level : chapter6) {
            for (TdLevelDefinition.Wave wave : level.waves) {
                if (wave.types.contains(MonsterType.SUMMONER)) summonerRoutes.add(wave.routeIndex);
            }
        }
        assertTrue("SUMMONER 必须形成多路线同压(≥2 条路线)", summonerRoutes.size() >= 2);
        Set<TdLevelDefinition.Theme> used = EnumSet.noneOf(TdLevelDefinition.Theme.class);
        for (TdLevelDefinition level : chapter6) used.add(level.theme);
        assertFalse("番外章不得复用第 4 章骨架 STORM", used.contains(TdLevelDefinition.Theme.STORM));
        TdLevelDefinition finale = chapter6.get(LEVEL_COUNT - 1);
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
        for (TdLevelDefinition level : chapter6) {
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
