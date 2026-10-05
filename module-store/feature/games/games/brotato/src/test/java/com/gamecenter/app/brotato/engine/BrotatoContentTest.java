package com.gamecenter.app.brotato.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BrotatoContent 装载回归：生产资产必须解析成 8 种敌人 + 20 波，且任何引用/取值缺陷都要 fail-closed。
 *
 * <p>契约里 BrotatoContent.load(...) 只吃三段 JSON 文本（不自己读 assets），所以本类按兄弟模块
 * td 的 TdStoryContentTest 同款 findAssetsRoot()/readAsset() 模式自行取生产资产，保证测的是真内容。
 */
public class BrotatoContentTest {

    private static final float EPS = 1e-6f;

    private static String manifest;
    private static String enemies;
    private static String waves;
    private static BrotatoContent content;

    @BeforeClass
    public static void loadProductionBrotatoAssets() throws IOException {
        Path assetsRoot = findAssetsRoot();
        manifest = readAsset(assetsRoot.resolve("brotato/manifest.json"));
        enemies = readAsset(assetsRoot.resolve("brotato/enemies.json"));
        waves = readAsset(assetsRoot.resolve("brotato/waves.json"));
        content = BrotatoContent.load(manifest, enemies, waves);
    }

    // ==== 用例 1：真实资产完整装载 ==================================================

    @Test
    public void realAssetsDeclareEightKindsAndTwentyContiguousWaves() {
        assertEquals("contentVersion 应与 manifest 一致", 1, content.contentVersion());
        assertEquals("totalWaves 应与 waves.json 声明一致", 20, content.totalWaves());

        assertEquals("敌人种类应为 8 种", new HashSet<>(Arrays.asList(
                "swarm", "grunt", "runner", "zigzag", "splitter", "minion", "tank", "boss")),
                content.kinds().keySet());

        var levels = content.levels();
        assertEquals("波次表应装载 20 条", 20, levels.size());
        for (int i = 0; i < levels.size(); i++) {
            var level = levels.get(i);
            assertEquals("levels() 必须按 wave 升序且 1..20 连续", i + 1, level.wave());
            assertTrue("spawnCount 必须为正: wave " + level.wave(), level.spawnCount() > 0);
            assertTrue("spawnIntervalMs 必须为正: wave " + level.wave(),
                    level.spawnIntervalMs() > 0);
            assertFalse("composition 不得为空: wave " + level.wave(), level.composition().isEmpty());
            int weightSum = 0;
            for (var entry : level.composition()) {
                assertTrue("composition 引用的 kind 必须在 kinds() 中定义: wave " + level.wave()
                        + " kind=" + entry.kind(), content.kinds().containsKey(entry.kind()));
                assertTrue("composition weight 必须为正: wave " + level.wave() + " kind="
                        + entry.kind(), entry.weight() > 0);
                weightSum += entry.weight();
            }
            assertTrue("composition 权重之和必须为正: wave " + level.wave(), weightSum > 0);
        }

        // 抽查首尾两波的数值，锁死"解析没错位、没串波"
        var first = levels.get(0);
        assertEquals("第 1 波刷怪数应为 10", 10, first.spawnCount());
        assertEquals("第 1 波刷怪间隔应为 900ms", 900, first.spawnIntervalMs());
        var last = levels.get(19);
        assertEquals("第 20 波刷怪数应为 24", 24, last.spawnCount());
        assertEquals("第 20 波刷怪间隔应为 520ms", 520, last.spawnIntervalMs());
    }

    @Test
    public void bossWavesMatchAuthoredSchedule() {
        int[] bossWaves = new int[content.levels().size()];
        int bossWaveCount = 0;
        for (var level : content.levels()) {
            var group = level.bossGroup();
            if (group == null) continue;
            bossWaves[bossWaveCount++] = level.wave();
            assertEquals("boss 波的 kind 应为 boss", "boss", group.kind());
            assertEquals("boss 波 " + level.wave() + " 的 count 应与资产一致",
                    expectedBossCount(level.wave()), group.count());
            assertTrue("boss 波 " + level.wave() + " 的 intervalMs 必须为正",
                    group.intervalMs() > 0);
            assertTrue("boss 波 " + level.wave() + " 的 delayMs 必须非负", group.delayMs() >= 0);
        }
        assertEquals("boss 波数量应为 4", 4, bossWaveCount);
        assertEquals("boss 应落在 5/10/15/20 波", "[5, 10, 15, 20]",
                Arrays.toString(Arrays.copyOf(bossWaves, bossWaveCount)));
    }

    private static int expectedBossCount(int wave) {
        if (wave == 5) return 1;
        if (wave == 10) return 2;
        if (wave == 15) return 3;
        if (wave == 20) return 5;
        return -1;
    }

    @Test
    public void kindBehaviorsArePresentAndMapped() {
        int zigzagKinds = 0;
        int splitKinds = 0;
        for (var entry : content.kinds().entrySet()) {
            var kind = entry.getValue();
            assertEquals("kinds() 的 key 必须是小写 id", entry.getKey(), kind.id());
            assertEquals("kind id 必须小写: " + kind.id(), kind.id(), kind.id().toLowerCase());
            assertTrue("hp 必须为正: " + kind.id(), kind.hp() > 0f);
            assertTrue("speed 必须为正: " + kind.id(), kind.speed() > 0f);
            assertTrue("sizeDp 必须为正: " + kind.id(), kind.sizeDp() > 0f);
            assertTrue("score 必须为正: " + kind.id(), kind.score() > 0);
            assertTrue("contactDamage 不得为负: " + kind.id(), kind.contactDamage() >= 0);
            assertFalse("中文名不得缺省: " + kind.id(), isEmpty(kind.name()));
            assertFalse("英文名不得缺省: " + kind.id(), isEmpty(kind.nameEn()));
            if (kind.zigzag()) zigzagKinds++;
            if (kind.split()) splitKinds++;
        }
        assertTrue("至少要有 1 种 zigzag 行为敌人", zigzagKinds >= 1);
        assertTrue("至少要有 1 种 split 行为敌人", splitKinds >= 1);

        assertTrue("飘忽蛾应带 ZIGZAG 行为", content.kinds().get("zigzag").zigzag());
        assertFalse("grunt 不该带 ZIGZAG 行为", content.kinds().get("grunt").zigzag());
        assertTrue("胞裂兽应带 SPLIT 行为", content.kinds().get("splitter").split());
        assertFalse("tank 不该带 SPLIT 行为", content.kinds().get("tank").split());

        // minion 只由分裂产出：它必须存在，但不能被任何波次直接引用
        assertNotNull("minion 必须存在（分裂产物）", content.kinds().get("minion"));
        for (var level : content.levels()) {
            for (var entry : level.composition()) {
                assertFalse("minion 只应来自分裂，不该出现在刷怪表: wave " + level.wave(),
                        "minion".equals(entry.kind()));
            }
        }
    }

    @Test
    public void kindDisplayFieldsMatchAssetValues() {
        var grunt = content.kinds().get("grunt");
        assertEquals("杂兵", grunt.name());
        assertEquals("Grunt", grunt.nameEn());
        assertEquals(3.0f, grunt.hp(), EPS);
        assertEquals(2.1f, grunt.speed(), EPS);
        assertEquals(12.0f, grunt.sizeDp(), EPS);
        assertEquals(-2355168, grunt.color());
        assertEquals(1, grunt.contactDamage());
        assertEquals(8, grunt.score());

        var boss = content.kinds().get("boss");
        assertEquals("巢穴主宰", boss.name());
        assertEquals("Hive Lord", boss.nameEn());
        assertEquals(40.0f, boss.hp(), EPS);
        assertEquals(1.0f, boss.speed(), EPS);
        assertEquals(30.0f, boss.sizeDp(), EPS);
        assertEquals(3, boss.contactDamage());
        assertEquals(120, boss.score());

        var swarm = content.kinds().get("swarm");
        assertEquals(1.0f, swarm.hp(), EPS);
        assertEquals(3.2f, swarm.speed(), EPS);
        assertEquals(8.0f, swarm.sizeDp(), EPS);

        // 分裂链路的数值关系本身就是玩法契约：裂子必须比母体脆、比母体快
        var splitter = content.kinds().get("splitter");
        var minion = content.kinds().get("minion");
        assertTrue("裂子 hp 必须低于母体", minion.hp() < splitter.hp());
        assertTrue("裂子得分必须低于母体", minion.score() < splitter.score());
    }

    // ==== 用例 2：fail-closed ======================================================

    @Test
    public void failClosedOnUnknownKindReference() {
        String broken = replaceFirst(waves, "\"kind\": \"swarm\"", "\"kind\": \"ghost_swarm\"");
        IllegalStateException error = assertThrows(
                "waves 引用了未定义 kind，必须 fail-closed 而不是静默丢弃",
                IllegalStateException.class,
                () -> BrotatoContent.load(manifest, enemies, broken));
        assertHasReason(error);
    }

    @Test
    public void failClosedOnUnknownBehaviorName() {
        String broken = replaceFirst(enemies, "\"ZIGZAG\"", "\"TELEPORT\"");
        IllegalStateException error = assertThrows(
                "未知 behavior 名必须 fail-closed，禁止当作无行为静默放过",
                IllegalStateException.class,
                () -> BrotatoContent.load(manifest, broken, waves));
        assertHasReason(error);
    }

    @Test
    public void failClosedOnZeroCompositionWeight() {
        String broken = replaceFirst(waves, "\"weight\": 6", "\"weight\": 0");
        IllegalStateException error = assertThrows(
                "composition weight 为 0 会让加权随机退化，必须 fail-closed",
                IllegalStateException.class,
                () -> BrotatoContent.load(manifest, enemies, broken));
        assertHasReason(error);
    }

    @Test
    public void failClosedOnNonContiguousWaveSequence() {
        // 把第 2 波改成第 21 波：wave 序列出现空洞，且与 totalWaves=20 冲突
        String broken = replaceFirst(waves, "\"wave\": 2,", "\"wave\": 21,");
        IllegalStateException error = assertThrows(
                "levels 的 wave 必须 1..totalWaves 连续，缺号必须 fail-closed",
                IllegalStateException.class,
                () -> BrotatoContent.load(manifest, enemies, broken));
        assertHasReason(error);
    }

    @Test
    public void failClosedOnManifestMissingRequiredField() {
        IllegalStateException missingVersion = assertThrows(
                "manifest 缺 contentVersion 必须 fail-closed",
                IllegalStateException.class,
                () -> BrotatoContent.load(replaceFirst(manifest, "\"contentVersion\": 1,", ""),
                        enemies, waves));
        assertHasReason(missingVersion);

        IllegalStateException missingGameId = assertThrows(
                "manifest 缺 gameId 必须 fail-closed",
                IllegalStateException.class,
                () -> BrotatoContent.load(replaceFirst(manifest, "\"gameId\": \"brotato\",", ""),
                        enemies, waves));
        assertHasReason(missingGameId);
    }

    @Test
    public void failClosedOnForeignGameId() {
        IllegalStateException error = assertThrows(
                "gameId 不是 brotato 时必须拒绝装载（防止串用别家内容）",
                IllegalStateException.class,
                () -> BrotatoContent.load(
                        replaceFirst(manifest, "\"gameId\": \"brotato\"", "\"gameId\": \"td\""),
                        enemies, waves));
        assertHasReason(error);
    }

    @Test
    public void failClosedOnBlankOrMalformedJson() {
        assertThrows("空 manifest 必须 fail-closed", IllegalStateException.class,
                () -> BrotatoContent.load("", enemies, waves));
        assertThrows("空 enemies 必须 fail-closed", IllegalStateException.class,
                () -> BrotatoContent.load(manifest, "", waves));
        assertThrows("空 waves 必须 fail-closed", IllegalStateException.class,
                () -> BrotatoContent.load(manifest, enemies, ""));
        assertThrows("被截断的 waves 必须 fail-closed", IllegalStateException.class,
                () -> BrotatoContent.load(manifest, enemies,
                        waves.substring(0, waves.length() / 2)));
    }

    // ==== 辅助 =====================================================================

    private static void assertHasReason(IllegalStateException error) {
        assertTrue("fail-closed 必须带可读原因，便于定位资产缺陷",
                error.getMessage() != null && !error.getMessage().trim().isEmpty());
    }

    private static boolean isEmpty(String text) {
        return text == null || text.trim().isEmpty();
    }

    /** 用真实资产文本做最小改动构造非法样本：只替换第一处命中，命中不到即视为样本失效。 */
    private static String replaceFirst(String text, String literal, String replacement) {
        Matcher matcher = Pattern.compile(Pattern.quote(literal)).matcher(text);
        assertTrue("构造非法样本失败：真实资产里找不到片段 " + literal, matcher.find());
        return matcher.replaceFirst(Matcher.quoteReplacement(replacement));
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

    private static String readAsset(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
