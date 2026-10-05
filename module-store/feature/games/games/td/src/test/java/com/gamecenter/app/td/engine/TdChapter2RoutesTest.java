package com.gamecenter.app.td.engine;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Production chapter-two split-route content, including its unchanged pre-change balance. */
@RunWith(Parameterized.class)
public class TdChapter2RoutesTest {
    private static final Map<String, TdLevelDefinition> LEVELS = new LinkedHashMap<>();
    private static final int[][] ORIGINAL_WAYPOINTS = {
            {0, 0}, {0, 11}, {2, 11}, {2, 2}, {4, 2}, {4, 9},
            {6, 9}, {6, 0}, {8, 0}, {8, 4}, {9, 4}, {9, 0}
    };
    private static final int[][] SECOND_WAYPOINTS = {
            {9, 11}, {9, 6}, {7, 6}, {7, 11}, {3, 11}, {3, 2},
            {4, 2}, {4, 9}, {6, 9}, {6, 0}, {8, 0}, {8, 4}, {9, 4}, {9, 0}
    };

    private final String levelId;
    private final int originalRows, originalCoin, originalHp, expectedBuildable;
    private final int[] expectedWaveRoutes;
    private final String[] originalWaves;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> levels() {
        // Each fixed row is types (in order), count, interval, delay, hpMul, speedMul.
        // These values were captured before adding route 1; none come from the parsed fixture.
        return Arrays.asList(new Object[][] {
                {"main_006", 12, 250, 6, 65, new int[] {0, 1, 0, 0, 1, 0}, new String[] {
                        "NORMAL|9|0.7|0.4|1.05|1",
                        "FAST|8|0.52|0.4|1.06|1.08",
                        "NORMAL+FAST|12|0.48|0.45|1.1|1.08",
                        "SWARM+TANK|12|0.42|0.5|1.16|1.08",
                        "FLY+FAST|10|0.5|0.5|1.2|1.12",
                        "TANK+FLY|8|0.62|0.55|1.25|1.1"
                }},
                {"main_007", 10, 255, 6, 41, new int[] {0, 1, 0, 1, 0, 1}, new String[] {
                        "FLY|10|0.62|0.4|1.08|1.1",
                        "SHIELD+NORMAL|12|0.58|0.45|1.12|1.04",
                        "FAST+FLY|14|0.42|0.45|1.16|1.12",
                        "SPLITTER+SHIELD|9|0.68|0.5|1.2|1.06",
                        "FLY+SPLITTER|14|0.45|0.5|1.25|1.14",
                        "TANK+SHIELD_GENERATOR|8|0.72|0.55|1.3|1.06"
                }},
                {"main_009", 10, 270, 7, 41, new int[] {0, 1, 1, 0, 1, 0}, new String[] {
                        "CHARGER+NORMAL|11|0.62|0.4|1.12|1.08",
                        "FLY+FAST|12|0.48|0.45|1.18|1.14",
                        "SHIELD+CHARGER|10|0.62|0.5|1.24|1.1",
                        "HEALER+CHARGER+TANK|13|0.56|0.5|1.3|1.08",
                        "SPLITTER+FLY+CHARGER|16|0.4|0.5|1.34|1.16",
                        "SHIELD_GENERATOR+CHARGER+BOSS|6|0.8|0.65|1.02|1"
                }},
                {"main_012", 10, 295, 7, 41, new int[] {0, 1, 0, 1, 0, 1}, new String[] {
                        "FLY+CHARGER|15|0.46|0.4|1.22|1.16",
                        "SHIELD+FAST|14|0.48|0.45|1.28|1.14",
                        "SPLITTER+FLY+CHARGER|18|0.36|0.5|1.34|1.18",
                        "HEALER+SHIELD_GENERATOR+TANK|14|0.54|0.5|1.4|1.1",
                        "RESISTANT+CHARGER+SPLITTER|17|0.42|0.55|1.46|1.14",
                        "SHIELD_GENERATOR+FLY+BOSS|8|0.72|0.75|1.1|1.02"
                }}
        });
    }

    public TdChapter2RoutesTest(String levelId, int originalRows, int originalCoin,
                               int originalHp, int expectedBuildable,
                               int[] expectedWaveRoutes, String[] originalWaves) {
        this.levelId = levelId;
        this.originalRows = originalRows;
        this.originalCoin = originalCoin;
        this.originalHp = originalHp;
        this.expectedBuildable = expectedBuildable;
        this.expectedWaveRoutes = expectedWaveRoutes;
        this.originalWaves = originalWaves;
    }

    @BeforeClass
    public static void loadRegisteredProductionChapter() throws IOException {
        Path assets = findAssetsRoot();
        TdLevelJsonParser.Manifest manifest = TdLevelJsonParser.parseManifest(
                readAsset(assets.resolve("td/manifest.json")));
        TdLevelJsonParser.ChapterRef chapterRef = null;
        for (TdLevelJsonParser.ChapterRef candidate : manifest.chapters) {
            if ("chapter_main_02".equals(candidate.id)) chapterRef = candidate;
        }
        assertNotNull("manifest must register the production second chapter", chapterRef);
        TdLevelJsonParser.Chapter chapter = TdLevelJsonParser.parseChapter(
                readAsset(assets.resolve("td").resolve(chapterRef.file)));
        assertEquals(chapterRef.id, chapter.id);
        assertEquals(chapterRef.levelCount, chapter.levels.size());
        LEVELS.clear();
        for (TdLevelDefinition level : chapter.levels) LEVELS.put(level.id, level);
    }

    @Test
    public void originalResourcesRouteAndEveryNonRouteWaveParameterRemainUnchanged() {
        TdLevelDefinition level = level();
        assertEquals(levelId + " rows", originalRows, level.rows);
        assertEquals(levelId + " cols", 12, level.cols);
        assertEquals(levelId + " egg row", 9, level.eggRow);
        assertEquals(levelId + " egg col", 0, level.eggCol);
        assertEquals(levelId + " start coin", originalCoin, level.startCoin);
        assertEquals(levelId + " egg HP", originalHp, level.mascotHp);
        assertRouteEquals("original route", expand(ORIGINAL_WAYPOINTS), level.copyRoutes().get(0));
        assertEquals(levelId + " wave count", originalWaves.length, level.waves.size());
        for (int i = 0; i < originalWaves.length; i++) {
            String label = levelId + " wave " + (i + 1);
            String[] expected = originalWaves[i].split("\\|");
            TdLevelDefinition.Wave actual = level.waves.get(i);
            List<MonsterType> types = new ArrayList<>();
            for (String type : expected[0].split("\\+")) types.add(MonsterType.valueOf(type));
            assertEquals(label + " ordered composition", types, actual.types);
            assertEquals(label + " count", Integer.parseInt(expected[1]), actual.count);
            assertEquals(label + " interval", Float.parseFloat(expected[2]), actual.intervalSec, 0f);
            assertEquals(label + " delay", Float.parseFloat(expected[3]), actual.delaySec, 0f);
            assertEquals(label + " HP multiplier", Float.parseFloat(expected[4]), actual.hpMultiplier, 0f);
            assertEquals(label + " speed multiplier", Float.parseFloat(expected[5]), actual.speedMultiplier, 0f);
        }
        TdGame game = level.newGame();
        assertEquals(originalCoin, game.getCoin());
        assertEquals(originalHp, game.getMascotHp());
    }

    @Test
    public void twoIndependentEntrancesJoinTheSameEggWithoutAShortcut() {
        TdLevelDefinition level = level();
        List<int[][]> routes = level.copyRoutes();
        assertEquals(levelId + " must have two real routes", 2, routes.size());
        int[][] first = routes.get(0), second = routes.get(1);
        assertRouteEquals("second route", expand(SECOND_WAYPOINTS), second);
        assertEquals(54, first.length);
        assertEquals(56, second.length);
        assertArrayEquals(new int[] {0, 0}, first[0]);
        assertArrayEquals(new int[] {9, 11}, second[0]);
        int[] egg = {level.eggRow, level.eggCol};
        assertArrayEquals(egg, first[first.length - 1]);
        assertArrayEquals(egg, second[second.length - 1]);

        Set<Integer> firstCells = new HashSet<>();
        for (int[] point : first) firstCells.add(point[0] * level.cols + point[1]);
        for (int i = 0; i < 25; i++) {
            assertFalse(levelId + " entrance branches must stay separate before (3,2)",
                    firstCells.contains(second[i][0] * level.cols + second[i][1]));
        }
        assertArrayEquals(new int[] {3, 2}, second[25]);
        for (int i = 0; i < 31; i++) {
            assertArrayEquals(levelId + " shared defense segment " + i, first[23 + i], second[25 + i]);
        }
    }

    @Test
    public void wavesActuallyUseBothRoutesInTheTeachingOrder() {
        TdLevelDefinition level = level();
        assertEquals(expectedWaveRoutes.length, level.waves.size());
        Set<Integer> usedRoutes = new HashSet<>();
        for (int i = 0; i < expectedWaveRoutes.length; i++) {
            int actualRoute = level.waves.get(i).routeIndex;
            assertEquals(levelId + " wave " + (i + 1) + " route", expectedWaveRoutes[i], actualRoute);
            usedRoutes.add(actualRoute);
        }
        assertEquals(levelId + " both entrances must receive waves",
                new HashSet<>(Arrays.asList(0, 1)), usedRoutes);
    }

    @Test
    public void firstTwoProductionWavesSpawnAndMoveFromTheirOwnEntrances() {
        TdLevelDefinition level = level();
        TdGame game = level.newGame();
        assertEquals(0, game.nextWaveRouteIndex());
        assertTrue(game.startNextWaveEarly()); // Start wave 1 through the actual player action.
        assertTrue(game.startNextWaveEarly()); // Rush its remaining enemies; do not fake monsters.
        assertSpawnedWave(game, 1, level.waves.get(0).count, 0, 0, 0);
        int previewedSecondRoute = game.nextWaveRouteIndex();
        assertTrue(game.startNextWaveEarly()); // Start wave 2 while wave 1 is still alive.
        assertTrue(game.startNextWaveEarly());
        assertSpawnedWave(game, 2, level.waves.get(1).count, 1, 9, 11);
        assertEquals(levelId + " second-wave preview", 1, previewedSecondRoute);
        assertEquals(level.waves.get(0).count + level.waves.get(1).count, game.getMonsters().size());

        TdGame.Monster first = monsterFromWave(game, 1);
        TdGame.Monster second = monsterFromWave(game, 2);
        for (int i = 0; i < 30; i++) game.tick();
        assertEquals(TdGame.State.RUNNING, game.getState());
        assertTrue(levelId + " upper entrance must move right", first.x > .5f);
        assertEquals(.5f, first.y, .0001f);
        assertTrue(levelId + " lower-right entrance must move left", second.x < 11.5f);
        assertEquals(9.5f, second.y, .0001f);
    }

    @Test
    public void layoutKeepsEnoughBuildableCellsAndUsableSharedDefenseSites() {
        TdGame game = level().newGame();
        int buildable = 0;
        for (int row = 0; row < originalRows; row++) {
            for (int col = 0; col < 12; col++) {
                if (!game.isPathCell(row, col) && !game.isEggCell(row, col)) buildable++;
            }
        }
        assertEquals(levelId + " planned buildable space", expectedBuildable, buildable);
        int[][] defenseSites = {{3, 1}, {4, 1}, {5, 2}, {5, 3}, {8, 8}, {6, 10}};
        for (int[] point : defenseSites) {
            // Each site gets a fresh session so this verifies placement, not a six-tower budget.
            assertNotNull(levelId + " usable defense site " + Arrays.toString(point),
                    level().newGame().placeTower(TowerType.BOTTLE, point[0], point[1]));
        }
    }

    private TdLevelDefinition level() {
        TdLevelDefinition level = LEVELS.get(levelId);
        assertNotNull("production level must exist: " + levelId, level);
        return level;
    }

    private void assertSpawnedWave(TdGame game, int waveNo, int expectedCount,
                                   int expectedRoute, int startRow, int startCol) {
        int count = 0;
        for (TdGame.Monster monster : game.getMonsters()) {
            if (monster.waveNo != waveNo) continue;
            count++;
            assertEquals(levelId + " actual spawn route for wave " + waveNo,
                    expectedRoute, monster.routeIndex);
            assertEquals(startCol + .5f, monster.x, 0f);
            assertEquals(startRow + .5f, monster.y, 0f);
        }
        assertEquals(levelId + " actual spawned count for wave " + waveNo, expectedCount, count);
    }

    private static TdGame.Monster monsterFromWave(TdGame game, int waveNo) {
        for (TdGame.Monster monster : game.getMonsters()) {
            if (monster.waveNo == waveNo) return monster;
        }
        throw new AssertionError("expected a real monster from wave " + waveNo);
    }

    private void assertRouteEquals(String label, int[][] expected, int[][] actual) {
        assertEquals(levelId + " " + label + " length", expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertArrayEquals(levelId + " " + label + " cell " + i, expected[i], actual[i]);
        }
    }

    private static int[][] expand(int[][] waypoints) {
        List<int[]> cells = new ArrayList<>();
        cells.add(waypoints[0].clone());
        for (int i = 1; i < waypoints.length; i++) {
            int row = waypoints[i - 1][0], col = waypoints[i - 1][1];
            int targetRow = waypoints[i][0], targetCol = waypoints[i][1];
            assertTrue("test waypoints must be axis-aligned", row == targetRow || col == targetCol);
            int dr = Integer.compare(targetRow, row), dc = Integer.compare(targetCol, col);
            while (row != targetRow || col != targetCol) {
                row += dr;
                col += dc;
                cells.add(new int[] {row, col});
            }
        }
        return cells.toArray(new int[cells.size()][]);
    }

    private static Path findAssetsRoot() {
        for (Path candidate : new Path[] {
                Paths.get("src/main/assets"),
                Paths.get("module-store/feature/games/games/td/src/main/assets")
        }) {
            if (Files.isRegularFile(candidate.resolve("td/manifest.json"))) return candidate;
        }
        throw new IllegalStateException("production TD campaign asset was not found for JVM test");
    }

    private static String readAsset(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
