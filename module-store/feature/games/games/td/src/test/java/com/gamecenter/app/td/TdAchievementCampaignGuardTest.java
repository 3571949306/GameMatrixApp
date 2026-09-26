package com.gamecenter.app.td;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.gamecenter.app.td.engine.TdLevelDefinition;
import com.gamecenter.app.td.engine.TdLevelJsonParser;

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

/**
 * 成就 ↔ 战役内容的前向守卫（纯 JVM，读生产资产，不走 Robolectric）。
 *
 * <p>章节「通关成就」的真相源是资产而非代码：每章终关 id 从
 * {@code assets/td/manifest.json} 的章节顺序 + 各章 JSON 的末位关卡现推，
 * 再与 {@link TdAchievement} 的 CAMPAIGN_CLEAR 条目对账。因此内容侧新增
 * 第 14 章（哪怕只加资产不改成就）会立刻在
 * {@link #everyChapterFinaleHasExactlyOneCampaignClearAchievement} 上失败，
 * 直到该章的 {@code CHAPTER14_CLEARED} 补上为止——不再有「通关了但没奖励」的静默缺口。
 *
 * <p>反向同样锁死：
 * <ul>
 *   <li>每个 CAMPAIGN_CLEAR 的 {@code campaignLevelId} 必须真实存在于 manifest 的关卡集合
 *       （防手写 id 打错、防章节重排后成就指向已删除的关）；</li>
 *   <li>CAMPAIGN_CLEAR 条目数 == 章节数 + 1（首关「初试啼声」），既不多也不少
 *       （防重复登记、防漏登记）；</li>
 *   <li>第 n 章终关必须由 {@code CHAPTER<n>_CLEARED} 承接（防章节顺序与枚举序号错位）；</li>
 *   <li>存档键唯一且带 {@code td_achv_} 前缀（防新章复用旧键导致解锁状态互相覆盖）。</li>
 * </ul>
 */
public class TdAchievementCampaignGuardTest {

    /** 番外章并入后的最小章节数：守卫本身不硬编码上限，但必须确实读到 6~13 章。 */
    private static final int MIN_CHAPTERS = 13;

    private static List<String> chapterIds;
    private static List<String> chapterFinaleLevelIds;
    private static Set<String> allLevelIds;

    @BeforeClass
    public static void loadProductionCampaign() throws IOException {
        Path assetsRoot = findAssetsRoot();
        TdLevelJsonParser.Manifest manifest = TdLevelJsonParser.parseManifest(readAsset(
                assetsRoot.resolve("td/manifest.json")));
        List<String> ids = new ArrayList<>();
        List<String> finales = new ArrayList<>();
        Set<String> levelIds = new HashSet<>();
        for (TdLevelJsonParser.ChapterRef ref : manifest.chapters) {
            TdLevelJsonParser.Chapter chapter = TdLevelJsonParser.parseChapter(readAsset(
                    assetsRoot.resolve("td").resolve(ref.file)));
            assertTrue("章节 " + ref.id + " 不得为空关卡表", !chapter.levels.isEmpty());
            assertEquals("章节 " + ref.id + " 的 manifest levelCount 必须与实关数一致",
                    ref.levelCount, chapter.levels.size());
            ids.add(ref.id);
            finales.add(chapter.levels.get(chapter.levels.size() - 1).id);
            for (TdLevelDefinition level : chapter.levels) levelIds.add(level.id);
        }
        chapterIds = ids;
        chapterFinaleLevelIds = finales;
        allLevelIds = levelIds;
        assertTrue("守卫必须覆盖到全部番外章（实际读到 " + ids.size() + " 章）",
                ids.size() >= MIN_CHAPTERS);
    }

    /** 每章终关恰好一枚 CAMPAIGN_CLEAR 承接；序号必须对齐 CHAPTER<n>_CLEARED。 */
    @Test
    public void everyChapterFinaleHasExactlyOneCampaignClearAchievement() {
        for (int i = 0; i < chapterFinaleLevelIds.size(); i++) {
            String finale = chapterFinaleLevelIds.get(i);
            List<TdAchievement> matched = TdAchievement.matchingCampaignWin(finale);
            int chapterNo = i + 1;
            assertEquals("章节 " + chapterIds.get(i) + "（终关 " + finale
                            + "）必须恰好有一枚通关成就承接，不得为 0 也不得重复登记",
                    1, matched.size());
            assertEquals("第 " + chapterNo + " 章终关 " + finale + " 必须由 "
                            + "CHAPTER" + chapterNo + "_CLEARED 承接（章节顺序与枚举序号错位）",
                    "CHAPTER" + chapterNo + "_CLEARED", matched.get(0).name());
        }
    }

    /** 反向对账：CAMPAIGN_CLEAR 只多出一枚首关成就，不得有悬空/冗余条目。 */
    @Test
    public void campaignClearAchievementsMatchChapterCountPlusFirstWin() {
        int campaignClearCount = 0;
        for (TdAchievement a : TdAchievement.values()) {
            if (a.kind == TdAchievement.Kind.CAMPAIGN_CLEAR) campaignClearCount++;
        }
        assertEquals("CAMPAIGN_CLEAR 条目数必须等于「章节数 + 首关初试啼声」",
                chapterFinaleLevelIds.size() + 1, campaignClearCount);
    }

    /** 每个 CAMPAIGN_CLEAR 的关卡 id 必须真在 manifest 关卡集合里。 */
    @Test
    public void everyCampaignClearLevelIdExistsInManifest() {
        for (TdAchievement a : TdAchievement.values()) {
            if (a.kind != TdAchievement.Kind.CAMPAIGN_CLEAR) continue;
            assertTrue("成就 " + a.name() + " 的 campaignLevelId 不得为空",
                    a.campaignLevelId != null && !a.campaignLevelId.isEmpty());
            assertTrue("成就 " + a.name() + " 指向的关卡 " + a.campaignLevelId
                    + " 不在 manifest 战役关卡集合内（悬空引用）",
                    allLevelIds.contains(a.campaignLevelId));
        }
    }

    /** 存档键唯一：一枚键对应一枚成就的解锁态，重复即互相覆盖。 */
    @Test
    public void achievementSaveKeysAreUnique() {
        Set<String> keys = new HashSet<>();
        for (TdAchievement a : TdAchievement.values()) {
            assertTrue("成就存档键必须带 td_achv_ 前缀: " + a.saveKey,
                    a.saveKey.startsWith("td_achv_"));
            assertTrue("成就存档键重复: " + a.saveKey, keys.add(a.saveKey));
        }
        assertEquals("存档键数量必须与成就数量一致",
                TdAchievement.values().length, keys.size());
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
