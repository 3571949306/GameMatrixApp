package com.gamecenter.app.td.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

/**
 * 剧情内容回归守卫：全战役每一关都必须有开战引子与胜利尾声（中英）。
 *
 * <p>剧情字段是可选加载的（老存档兼容），但成品内容要求每一关全勤（总量随 manifest 章节增长）：
 * 缺任何一关的故事都会让“每关都有剧情”的承诺食言，故在此锁死。
 */
public class TdStoryContentTest {

    private static List<TdLevelDefinition> all;
    private static int declaredTotal;

    @BeforeClass
    public static void loadFullCampaign() throws IOException {
        Path assetsRoot = findAssetsRoot();
        TdLevelJsonParser.Manifest manifest = TdLevelJsonParser.parseManifest(readAsset(
                assetsRoot.resolve("td/manifest.json")));
        all = new ArrayList<>();
        declaredTotal = 0;
        for (TdLevelJsonParser.ChapterRef ref : manifest.chapters) {
            all.addAll(TdLevelJsonParser.parseChapter(readAsset(
                    assetsRoot.resolve("td").resolve(ref.file))).levels);
            declaredTotal += ref.levelCount;
        }
    }

    @Test
    public void everyLevelHasIntroAndOutroStories() {
        assertTrue("全战役规模必须等于 manifest 各章之和且已成规模",
                declaredTotal >= 45 && declaredTotal == all.size());
        Set<String> ids = new HashSet<>();
        for (TdLevelDefinition level : all) {
            assertTrue("关卡 id 不得重复: " + level.id, ids.add(level.id));
            assertFalse("缺少开战故事: " + level.id, level.storyIntro == null
                    || level.storyIntro.isEmpty());
            assertFalse("缺少胜利故事: " + level.id, level.storyOutro == null
                    || level.storyOutro.isEmpty());
            assertFalse("缺少英文开战故事: " + level.id, level.storyIntroEn == null
                    || level.storyIntroEn.isEmpty());
            assertFalse("缺少英文胜利故事: " + level.id, level.storyOutroEn == null
                    || level.storyOutroEn.isEmpty());
            assertTrue("开战故事过长: " + level.id, level.storyIntro.length() <= 800);
            assertTrue("胜利故事过长: " + level.id, level.storyOutro.length() <= 800);
            assertFalse("故事不得含换行: " + level.id, level.storyIntro.contains("\n")
                    || level.storyOutro.contains("\n"));
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
