package com.gamecenter.app.td.engine;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TdLevelJsonParserTest {
    private static final String CHAPTER = "{\"schema\":1,\"id\":\"chapter_demo\",\"name\":\"Demo\",\"levels\":["
            + "{\"id\":\"main_001\",\"order\":1,\"name\":\"Demo\",\"subtitle\":\"Test\",\"theme\":\"GARDEN\","
            + "\"rows\":4,\"cols\":4,\"egg\":[3,3],\"startCoin\":100,\"mascotHp\":5,"
            + "\"routes\":[[[0,0],[1,0],[1,1],[2,1],[2,2],[3,2],[3,3]]],"
            + "\"waves\":[{\"types\":[\"NORMAL\",\"FAST\"],\"route\":0,\"count\":3,\"interval\":0.5,\"delay\":0,\"hpMul\":1,\"speedMul\":1}]}]}";

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

    @Test public void parsesStrictManifest() {
        TdLevelJsonParser.Manifest manifest = TdLevelJsonParser.parseManifest(
                "{\"schema\":1,\"contentVersion\":7,\"gameId\":\"td\",\"chapters\":["
                        + "{\"id\":\"chapter_demo\",\"file\":\"chapters/chapter_demo.json\",\"levelCount\":1}]}"
        );
        assertEquals(7, manifest.contentVersion);
        assertEquals("chapters/chapter_demo.json", manifest.chapters.get(0).file);
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
