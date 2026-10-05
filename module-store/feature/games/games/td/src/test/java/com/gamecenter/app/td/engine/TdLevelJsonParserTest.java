package com.gamecenter.app.td.engine;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TdLevelJsonParserTest {
    private static final String CHAPTER = "{\"schema\":1,\"id\":\"chapter_demo\",\"name\":\"Demo\",\"levels\":["
            + "{\"id\":\"main_001\",\"order\":1,\"name\":\"Demo\",\"subtitle\":\"Test\",\"theme\":\"GARDEN\","
            + "\"rows\":4,\"cols\":4,\"egg\":[3,3],\"startCoin\":100,\"mascotHp\":5,"
            + "\"routes\":[[[0,0],[1,0],[1,1],[2,1],[2,2],[3,2],[3,3]]],"
            + "\"waves\":[{\"types\":[\"NORMAL\",\"FAST\"],\"route\":0,\"count\":3,\"interval\":0.5,\"delay\":0,\"hpMul\":1,\"speedMul\":1}]}]}";

    private static final String CHAPTER_EN = CHAPTER.replace("\"subtitle\":\"Test\"",
            "\"subtitle\":\"Test\",\"name_en\":\"Demo En\",\"subtitle_en\":\"Test En\"");

    @Test public void parsesValidatedCampaignDefinitionIntoFreshGame() {
        TdLevelJsonParser.Chapter chapter = TdLevelJsonParser.parseChapter(CHAPTER);
        TdLevelDefinition level = chapter.levels.get(0);
        assertEquals("main_001", level.id);
        assertEquals(2, level.waves.get(0).types.size());
        TdGame game = level.newGame();
        assertEquals(4, game.getRows());
        assertEquals(4, game.getCols());
        assertEquals(TdGame.VisualTheme.GARDEN, game.getVisualTheme());
    }

    @Test public void parsesOptionalEnglishDisplayFields() {
        TdLevelDefinition level = TdLevelJsonParser.parseChapter(CHAPTER_EN).levels.get(0);
        assertEquals("Demo En", level.nameEn);
        assertEquals("Test En", level.subtitleEn);
        // 中文权威源保持原样
        assertEquals("Demo", level.name);
        assertEquals("Test", level.subtitle);
    }

    @Test public void missingEnglishFieldsFallBackToSourceText() {
        // 缺省 name_en/subtitle_en 不 fail:回退中文源文本(解析时记 Log.w)
        TdLevelDefinition level = TdLevelJsonParser.parseChapter(CHAPTER).levels.get(0);
        assertEquals("Demo", level.nameEn);
        assertEquals("Test", level.subtitleEn);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOverlongEnglishName() {
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 65; i++) longName.append('x');
        TdLevelJsonParser.parseChapter(CHAPTER_EN.replace("\"name_en\":\"Demo En\"",
                "\"name_en\":\"" + longName + "\""));
    }

    @Test public void acceptsEnglishDisplayFieldsAtMaxLength() {
        // 64 字符恰好达到上限:必须接受,且值原样保留(65 才硬失败)
        StringBuilder maxText = new StringBuilder();
        for (int i = 0; i < 64; i++) maxText.append('x');
        String chapter = CHAPTER_EN
                .replace("\"name_en\":\"Demo En\"", "\"name_en\":\"" + maxText + "\"")
                .replace("\"subtitle_en\":\"Test En\"", "\"subtitle_en\":\"" + maxText + "\"");
        TdLevelDefinition level = TdLevelJsonParser.parseChapter(chapter).levels.get(0);
        assertEquals(maxText.toString(), level.nameEn);
        assertEquals(maxText.toString(), level.subtitleEn);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonStringEnglishField() {
        TdLevelJsonParser.parseChapter(CHAPTER_EN.replace("\"name_en\":\"Demo En\"",
                "\"name_en\":1"));
    }

    @Test public void parsesStrictManifest() {
        TdLevelJsonParser.Manifest manifest = TdLevelJsonParser.parseManifest(
                "{\"schema\":1,\"contentVersion\":7,\"gameId\":\"td\",\"chapters\":["
                        + "{\"id\":\"chapter_demo\",\"file\":\"chapters/chapter_demo.json\",\"levelCount\":1}]}"
        );
        assertEquals(7, manifest.contentVersion);
        assertEquals("chapters/chapter_demo.json", manifest.chapters.get(0).file);
    }

    @Test public void parsesManifestChapterDisplayNames() {
        TdLevelJsonParser.ChapterRef ref = TdLevelJsonParser.parseManifest(MANIFEST_CHAPTER
                + ",\"name\":\"第一章\",\"name_en\":\"Chapter One\"}]}").chapters.get(0);
        assertEquals("第一章", ref.name);
        assertEquals("Chapter One", ref.nameEn);
    }

    @Test public void manifestWithoutEnglishNameFallsBackToSourceName() {
        // 缺省 name_en 不 fail：回退中文源名（与关卡 name_en 的 optionalDisplayString 先例一致）
        TdLevelJsonParser.ChapterRef ref = TdLevelJsonParser.parseManifest(
                MANIFEST_CHAPTER + ",\"name\":\"第一章\"}]}").chapters.get(0);
        assertEquals("第一章", ref.name);
        assertEquals("第一章", ref.nameEn);
    }

    @Test public void manifestWithoutDisplayNamesYieldsNullRefs() {
        // 历史 manifest 两个 display 字段都可缺省：parser 层留空（记 Log.w），
        // 由 TdLevels 装载时回退章节文件自身的中文名，不拦截加载
        TdLevelJsonParser.ChapterRef ref = TdLevelJsonParser.parseManifest(
                MANIFEST_CHAPTER + "}]}").chapters.get(0);
        assertNull(ref.name);
        assertNull(ref.nameEn);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsOverlongManifestChapterName() {
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < 65; i++) longName.append('x');
        TdLevelJsonParser.parseManifest(
                MANIFEST_CHAPTER + ",\"name\":\"" + longName + "\"}]}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonStringManifestChapterName() {
        TdLevelJsonParser.parseManifest(MANIFEST_CHAPTER + ",\"name_en\":1}]}");
    }

    private static final String MANIFEST_CHAPTER =
            "{\"schema\":1,\"contentVersion\":7,\"gameId\":\"td\",\"chapters\":["
                    + "{\"id\":\"chapter_demo\",\"file\":\"chapters/chapter_demo.json\",\"levelCount\":1";

    @Test public void parsesOptionalStoryFields() {
        String withStory = CHAPTER.replace("\"mascotHp\":5",
                "\"mascotHp\":5,\"story_intro\":\"开战前\",\"story_intro_en\":\"Before\","
                        + "\"story_outro\":\"打赢了\",\"story_outro_en\":\"Won\"");
        TdLevelDefinition level = TdLevelJsonParser.parseChapter(withStory).levels.get(0);
        assertEquals("开战前", level.storyIntro);
        assertEquals("Before", level.storyIntroEn);
        assertEquals("打赢了", level.storyOutro);
        assertEquals("Won", level.storyOutroEn);
    }

    @Test public void missingStoryFieldsDefaultToEmpty() {
        TdLevelDefinition level = TdLevelJsonParser.parseChapter(CHAPTER).levels.get(0);
        assertEquals("", level.storyIntro);
        assertEquals("", level.storyIntroEn);
        assertEquals("", level.storyOutro);
        assertEquals("", level.storyOutroEn);
    }

    @Test public void missingEnglishStoryFallsBackToSourceText() {
        String withStory = CHAPTER.replace("\"mascotHp\":5",
                "\"mascotHp\":5,\"story_intro\":\"开战前\",\"story_outro\":\"打赢了\"");
        TdLevelDefinition level = TdLevelJsonParser.parseChapter(withStory).levels.get(0);
        assertEquals("开战前", level.storyIntroEn);
        assertEquals("打赢了", level.storyOutroEn);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonStringStoryField() {
        TdLevelJsonParser.parseChapter(CHAPTER.replace("\"mascotHp\":5",
                "\"mascotHp\":5,\"story_intro\":1"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownField() {
        TdLevelJsonParser.parseChapter(CHAPTER.replace("\"mascotHp\":5", "\"mascotHp\":5,\"oops\":1"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownMonster() {
        TdLevelJsonParser.parseChapter(CHAPTER.replace("NORMAL\",\"FAST", "BOGUS\",\"FAST"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsDiagonalRoute() {
        TdLevelJsonParser.parseChapter(CHAPTER.replace("[1,0],[1,1]", "[2,2],[1,1]"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsRouteThatLeavesNoTowerSpace() {
        TdLevelJsonParser.parseChapter(CHAPTER.replace(
                "[[0,0],[1,0],[1,1],[2,1],[2,2],[3,2],[3,3]]",
                "[[0,0],[0,1],[0,2],[0,3],[1,3],[1,2],[1,1],[1,0],"
                        + "[2,0],[2,1],[2,2],[2,3],[3,3]]"));
    }

    @Test public void rejectsArbitraryChapterPath() {
        try {
            TdLevelJsonParser.parseManifest("{\"schema\":1,\"contentVersion\":1,\"gameId\":\"td\",\"chapters\":["
                    + "{\"id\":\"chapter_demo\",\"file\":\"../outside.json\",\"levelCount\":1}]}");
        } catch (IllegalArgumentException expected) {
            return;
        }
        assertTrue("章节路径不得逃离受控 assets/td/chapters 目录", false);
    }
}
