package com.gamecenter.app.sokoban;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Content acceptance for every built-in board, using the real public movement API.
 * Solutions are independently found legal routes, not a promise of shortest solutions.
 * The test never edits the board or substitutes movement rules.
 */
@RunWith(Parameterized.class)
public class SokobanLevelContentTest {
    @Parameterized.Parameters(name = "level {0}")
    public static Collection<Object[]> levels() {
        Collection<Object[]> cases = new ArrayList<>(Arrays.asList(new Object[][] {
                {1, 2, "DDLU"},
                {2, 3, "RDDDUUULLDRURDLDURDLDDUURRDLULD"},
                {3, 3, "URRDLULDDURDURDLLDURDURD"},
                {4, 3, "LURULDDRRULURRDDLLLLDRRRR"},
                {5, 4, "UUDRRRUDDLLLDURRRD"},
                {6, 4, "UURULDDRRRRULURDDDLLLLDURRRRDULLLDDLUURRRDDR"},
                {7, 4, "UURULRRRDDRULURDDDLLLDLDUURRRDRDLDLLLRRRR"},
                {8, 4, "LLURULDDRRRRULURDDLDDLLLDURRRRDULLLDDLUURRRDDR"},
                {9, 5, "UUUULLLLRRDLLRRDLLRRDLLRRDLL"},
                {10, 5, "UURULRRDRDRULURRDLLLDLDRDLLDURRDLDLRRRRR"}
        }));
        cases.addAll(Arrays.asList(new Object[][] {
                // Turn around offset pillars before delivering boxes to separate target areas.
                {11, 3, "RRUULURULLDRDRDDLLLLUURRRURDLLDDLURULDLUU"},
                // Fill the deep storage targets while preserving a route through the doorway.
                {12, 3, "ULUURRRLLLDDDRRRRUUUDDDLLLUULURRRRDRUDDDLLLUULURRR"},
                // Temporarily move the box already on the doorway target, then return it.
                {13, 3, "UULUURDDURRURRDLLDDRUULUR"},
                // Change pushing sides around the staggered walls to reach three target areas.
                {14, 4, "LLULLUURRLLDDRRUUDRUDRRDRULLLLDLLDRRRRR"},
                // Keep access behind the central lane while bringing lower boxes upstairs.
                {15, 4, "ULULUUULURDDDDRDRUUUUDDDLLUURLDDRRUURLDDRRUUURUL"}
        }));
        assertEquals("All fifteen content fixtures need a legal replay", 15, cases.size());
        return cases;
    }

    private final int level;
    private final int expectedBoxes;
    private final String solution;

    public SokobanLevelContentTest(int level, int expectedBoxes, String solution) {
        this.level = level;
        this.expectedBoxes = expectedBoxes;
        this.solution = solution;
    }

    @Test
    public void builtInBoardHasConsistentObjectsAndClosedBoundary() throws Exception {
        SokobanGame game = new SokobanGame();
        game.startLevel(level);
        int[][] map = game.getMap();
        if (level >= 11) {
            assertEquals("Load the approved candidate, not the default first-level fallback",
                    expectedInitialMapSha256(), initialMapSha256(map));
        }
        assertTrue("Each tested board must be declared as a built-in level",
                level <= SokobanGame.TOTAL_LEVELS);
        if (level == 15) {
            assertEquals("Adding another built-in level requires another content fixture",
                    15, SokobanGame.TOTAL_LEVELS);
        }
        int columns = map[0].length;
        int boxes = 0;
        int targets = 0;
        int players = 0;
        for (int row = 0; row < map.length; row++) {
            assertEquals("Rectangular board at row " + row, columns, map[row].length);
            for (int col = 0; col < columns; col++) {
                int tile = map[row][col];
                assertTrue("Known tile at " + row + "," + col,
                        tile >= SokobanGame.EMPTY && tile <= SokobanGame.PLAYER_ON_TARGET);
                if (tile == SokobanGame.BOX || tile == SokobanGame.BOX_ON_TARGET) boxes++;
                if (tile == SokobanGame.TARGET || tile == SokobanGame.BOX_ON_TARGET
                        || tile == SokobanGame.PLAYER_ON_TARGET) targets++;
                if (tile == SokobanGame.PLAYER || tile == SokobanGame.PLAYER_ON_TARGET) players++;
                if (tile != SokobanGame.EMPTY && tile != SokobanGame.WALL) {
                    assertTrue("Playable cells must remain inside the outer boundary",
                            row > 0 && row < map.length - 1 && col > 0 && col < columns - 1);
                    assertTrue("Playable cells must not border the outside void",
                            map[row - 1][col] != SokobanGame.EMPTY
                                    && map[row + 1][col] != SokobanGame.EMPTY
                                    && map[row][col - 1] != SokobanGame.EMPTY
                                    && map[row][col + 1] != SokobanGame.EMPTY);
                }
            }
        }
        assertEquals("Each board has exactly one player", 1, players);
        assertEquals("Keep the intended box count", expectedBoxes, boxes);
        assertEquals("Every box must have one target", boxes, targets);
        assertFalse("The level must require play", game.isLevelComplete());
    }

    @Test
    public void builtInBoardCanBeCompletedThroughLegalMoves() {
        SokobanGame game = new SokobanGame();
        game.startLevel(level);
        for (int index = 0; index < solution.length(); index++) {
            char direction = solution.charAt(index);
            int rowDelta = direction == 'U' ? -1 : direction == 'D' ? 1 : 0;
            int colDelta = direction == 'L' ? -1 : direction == 'R' ? 1 : 0;
            assertEquals("Replay must use a single cardinal step", 1,
                    Math.abs(rowDelta) + Math.abs(colDelta));
            assertTrue("Level " + level + " rejected step " + (index + 1) + " (" + direction + ")",
                    game.movePlayer(rowDelta, colDelta));
        }
        assertEquals("Count only the accepted moves", solution.length(), game.getMoveCount());
        assertTrue("A box must actually be pushed", game.getPushCount() > 0);
        assertTrue("Every target must be covered after the legal replay", game.isLevelComplete());
        int coveredTargets = 0;
        for (int[] row : game.getMap()) {
            for (int tile : row) {
                assertFalse("No box may remain away from a target", tile == SokobanGame.BOX);
                if (tile == SokobanGame.BOX_ON_TARGET) coveredTargets++;
            }
        }
        assertEquals(expectedBoxes, coveredTargets);
        game.onLevelComplete();
        assertFalse("Completion stops further movement", game.isRunning());
        assertEquals(level, game.getLevelsCleared());
        assertFalse(game.movePlayer(0, 1));
    }

    private String expectedInitialMapSha256() {
        // Fixed hashes of the reviewed external design fixtures; never derived from production.
        switch (level) {
            case 11: return "2fd60097358a836032e0927ade766440640989a7ae3f86686ba22f86ff8b7f3e";
            case 12: return "5e2192f35007ee0f629ec03f849f64cb6a2db8bd1f1d31ceefa426592e6dc01b";
            case 13: return "903529757b5ffa2c9d8428d921249899b6274d4419bac949ec73e85f4154cd6e";
            case 14: return "b43e7bcc3552b241b86f550d232b94e317af6952e9859db845276cff81352627";
            case 15: return "e3bb9a3e8c5588b4e430f05e4e5c09af3e0db17e645f73bbe7b3a53d84e1d44f";
            default: throw new AssertionError("No candidate hash for level " + level);
        }
    }

    private static String initialMapSha256(int[][] map) throws Exception {
        StringBuilder encoded = new StringBuilder();
        for (int row = 0; row < map.length; row++) {
            if (row > 0) encoded.append('/');
            for (int tile : map[row]) encoded.append(tile);
        }
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(encoded.toString().getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte value : digest) {
            hex.append(Character.forDigit((value >>> 4) & 0xf, 16));
            hex.append(Character.forDigit(value & 0xf, 16));
        }
        return hex.toString();
    }
}
