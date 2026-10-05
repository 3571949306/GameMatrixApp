package com.gamecenter.app.klotski;

import static org.junit.Assert.*;

import org.junit.Test;
import java.util.List;

/** Replays frozen teaching solutions through the real public game rules. */
public class KlotskiPracticeContentTest {
    private static final String CLASSIC = "0,1,0,0,0,3,0,0,2,3,2,1,2,1,3,2,3,0,4,3,4";
    private static final int[] WIDTHS = {2,1,1,1,1,2,1,1,1,1};
    private static final int[] HEIGHTS = {2,2,2,2,2,1,1,1,1,1};
    private static final int[] TYPES = {0,1,1,1,1,2,3,3,3,3};

    @Test
    public void guanyuPracticeNeedsFourLegalSingleCellMoves() {
        verifyPractice("practice_exit_01", 4, new int[][]{
                {5,0,-1},{7,0,-1},{7,1,0},{0,1,0}
        });
    }

    @Test
    public void twoSoldierPracticeNeedsEightLegalSingleCellMoves() {
        verifyPractice("practice_exit_02", 8, new int[][]{
                {6,-1,0},{6,-1,0},{9,-1,0},{9,-1,0},{5,0,-1},{7,0,-1},{7,1,0},{0,1,0}
        });
    }

    @Test
    public void lowerLanePracticeNeedsSixteenLegalSingleCellMoves() {
        verifyPractice("practice_exit_03", 16, new int[][]{
                {6,-1,0},{6,0,-1},{5,0,-1},{8,1,0},{7,1,0},{8,1,0},{7,1,0},{0,0,1},
                {6,-1,0},{6,-1,0},{9,-1,0},{9,-1,0},{5,0,-1},{7,0,-1},{7,1,0},{0,1,0}
        });
    }

    // practice_exit_04~08: layouts and shortest paths produced and pre-verified by the
    // repo-external klotski_factory.py (multi-source reverse BFS over the canonical
    // 53,954-state space); verifyPractice re-checks optimality through the Java solver.
    @Test
    public void flankGuardPracticeNeedsTwentyLegalSingleCellMoves() {
        verifyPractice("practice_exit_04", 20, new int[][]{
                {6,0,-1},{3,0,-1},{5,0,-1},{9,1,0},{9,1,0},{8,1,0},{8,1,0},{0,0,1},
                {2,0,1},{6,-1,0},{7,-1,0},{1,0,1},{6,-1,0},{7,-1,0},{4,0,-1},{3,0,-1},
                {5,0,-1},{8,0,-1},{8,1,0},{0,1,0}
        });
    }

    @Test
    public void splitSoldiersPracticeNeedsTwentySixLegalSingleCellMoves() {
        verifyPractice("practice_exit_05", 26, new int[][]{
                {1,0,1},{0,0,1},{5,-1,0},{3,0,-1},{7,1,0},{5,-1,0},{6,0,-1},{0,1,0},
                {1,0,-1},{1,0,-1},{2,-1,0},{9,-1,0},{8,0,1},{0,0,1},{6,0,1},{6,-1,0},
                {3,-1,0},{7,0,-1},{4,0,-1},{8,1,0},{9,1,0},{7,0,-1},{4,0,-1},{8,0,-1},
                {9,1,0},{0,0,1}
        });
    }

    @Test
    public void gatePassPracticeNeedsThirtyTwoLegalSingleCellMoves() {
        verifyPractice("practice_exit_06", 32, new int[][]{
                {6,1,0},{2,0,1},{0,0,1},{7,-1,0},{8,-1,0},{7,-1,0},{8,-1,0},{5,0,-1},
                {3,0,-1},{6,0,-1},{9,-1,0},{4,0,1},{3,1,0},{0,1,0},{1,0,-1},{1,0,-1},
                {2,-1,0},{6,-1,0},{6,0,1},{0,0,1},{8,0,1},{8,1,0},{7,1,0},{7,0,1},
                {5,-1,0},{3,0,-1},{4,0,-1},{9,1,0},{1,0,-1},{2,0,-1},{6,-1,0},{0,0,1}
        });
    }

    @Test
    public void shiftingBoardPracticeNeedsFortyLegalSingleCellMoves() {
        verifyPractice("practice_exit_07", 40, new int[][]{
                {2,-1,0},{9,-1,0},{9,0,-1},{4,0,-1},{6,-1,0},{7,0,1},{4,1,0},{6,0,-1},
                {7,-1,0},{8,-1,0},{6,0,-1},{7,0,-1},{8,-1,0},{4,0,1},{3,0,1},{0,0,1},
                {5,1,0},{5,1,0},{9,0,-1},{2,0,-1},{6,-1,0},{0,-1,0},{3,0,-1},{3,0,-1},
                {4,1,0},{8,1,0},{7,0,1},{0,0,1},{9,0,1},{9,1,0},{2,1,0},{6,0,-1},
                {1,0,-1},{7,-1,0},{8,-1,0},{6,0,-1},{1,0,-1},{7,0,-1},{8,-1,0},{0,0,1}
        });
    }

    @Test
    public void bottomSweepPracticeNeedsFortyEightLegalSingleCellMoves() {
        verifyPractice("practice_exit_08", 48, new int[][]{
                {9,1,0},{9,0,-1},{8,1,0},{8,1,0},{1,0,1},{6,1,0},{7,0,-1},{1,-1,0},
                {9,-1,0},{8,-1,0},{2,0,1},{2,0,1},{6,1,0},{6,0,-1},{2,0,-1},{8,1,0},
                {9,0,1},{2,-1,0},{8,0,-1},{9,1,0},{2,0,1},{7,1,0},{7,1,0},{0,0,1},
                {5,-1,0},{3,0,-1},{7,1,0},{5,-1,0},{6,0,-1},{0,1,0},{1,0,-1},{1,0,-1},
                {2,-1,0},{9,-1,0},{8,0,1},{0,0,1},{6,0,1},{6,-1,0},{3,-1,0},{7,0,-1},
                {4,0,-1},{8,1,0},{9,1,0},{7,0,-1},{4,0,-1},{8,0,-1},{9,1,0},{0,0,1}
        });
    }

    @Test
    public void classicLayoutAnd116MoveSolutionRemainUnchanged() {
        KlotskiGame game = new KlotskiGame();
        assertEquals(CLASSIC, game.serializeState());
        assertGeometry(game);
        List<int[]> path = game.getSolutionPath();
        assertNotNull(path);
        assertEquals(116, path.size());
        assertEquals(CLASSIC, game.serializeState());
        replay(game, path.toArray(new int[0][]));
        game.reset();
        assertEquals(CLASSIC, game.serializeState());
        assertEquals(0, game.getMoves());
        assertFalse(game.isWon());
    }

    private static void verifyPractice(String id, int expectedDistance, int[][] frozenSolution) {
        KlotskiPracticeLevels.Level level = KlotskiPracticeLevels.find(id);
        assertNotNull(id, level);
        assertEquals(expectedDistance, level.referenceMoves);
        KlotskiGame game = new KlotskiGame();
        assertTrue(game.restoreState(level.initialStateCsv));
        assertEquals(level.initialStateCsv, game.serializeState());
        assertEquals(0, game.getMoves());
        assertFalse(game.isWon());
        assertGeometry(game);

        List<int[]> shortest = game.getSolutionPath();
        assertNotNull(id + " must be solvable", shortest);
        assertEquals(expectedDistance, shortest.size());
        assertEquals("Searching must not change the live board", level.initialStateCsv, game.serializeState());

        // The path was frozen before integration, independently of the definition class.
        replay(game, frozenSolution);
        assertEquals(expectedDistance, game.getMoves());
        game.reset();
        assertEquals(CLASSIC, game.serializeState());
        assertFalse(game.isWon());
        assertEquals(0, game.getMoves());
    }

    private static void replay(KlotskiGame game, int[][] moves) {
        for (int step = 0; step < moves.length; step++) {
            assertFalse("Must not win before final move " + step, game.isWon());
            int[] move = moves[step];
            int id = move[0], dx = move[1], dy = move[2];
            assertEquals(1, Math.abs(dx) + Math.abs(dy));
            int[][] before = positions(game);
            assertIndependentPlacement(game, id, before[id][0] + dx, before[id][1] + dy);
            assertTrue(game.canMove(game.getBlocks().get(id), dx, dy));
            assertTrue(game.moveBlock(game.getBlocks().get(id), dx, dy));
            assertEquals(step + 1, game.getMoves());
            for (int other = 0; other < 10; other++) {
                KlotskiGame.Block block = game.getBlocks().get(other);
                assertEquals(before[other][0] + (other == id ? dx : 0), block.x);
                assertEquals(before[other][1] + (other == id ? dy : 0), block.y);
            }
            assertGeometry(game);
            assertEquals(step == moves.length - 1, game.isWon());
        }
        assertTrue(game.isWon());
    }

    private static int[][] positions(KlotskiGame game) {
        int[][] result = new int[10][2];
        for (int id = 0; id < 10; id++) {
            result[id][0] = game.getBlocks().get(id).x;
            result[id][1] = game.getBlocks().get(id).y;
        }
        return result;
    }

    private static void assertIndependentPlacement(KlotskiGame game, int id, int x, int y) {
        assertTrue(x >= 0 && y >= 0 && x + WIDTHS[id] <= 4 && y + HEIGHTS[id] <= 5);
        for (KlotskiGame.Block other : game.getBlocks()) {
            if (other.id != id) {
                assertFalse(x < other.x + other.width && x + WIDTHS[id] > other.x
                        && y < other.y + other.height && y + HEIGHTS[id] > other.y);
            }
        }
    }

    private static void assertGeometry(KlotskiGame game) {
        assertEquals(10, game.getBlocks().size());
        boolean[][] cells = new boolean[5][4];
        int occupied = 0;
        for (int id = 0; id < 10; id++) {
            KlotskiGame.Block block = game.getBlocks().get(id);
            assertEquals(id, block.id);
            assertEquals(WIDTHS[id], block.width);
            assertEquals(HEIGHTS[id], block.height);
            assertEquals(TYPES[id], block.type);
            assertTrue(block.x >= 0 && block.y >= 0 && block.x + block.width <= 4 && block.y + block.height <= 5);
            for (int y = block.y; y < block.y + block.height; y++) {
                for (int x = block.x; x < block.x + block.width; x++) {
                    assertFalse(cells[y][x]);
                    cells[y][x] = true;
                    occupied++;
                }
            }
        }
        assertEquals(18, occupied);
        assertEquals(game.getBlocks().get(0).x == 1 && game.getBlocks().get(0).y == 3, game.isWon());
    }
}
