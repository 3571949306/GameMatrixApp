package com.gamecenter.app.pipeline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Random;

/**
 * 玩家规则：左上格到右下格之间存在端口双向相接的路线即可通关，允许闲置管和支路。
 * 固定局面只设置初始棋盘；后续旋转、判断和结算均调用生产公开 API。
 * 独立验证器只读取显示字符，不调用生产目标数组或正确性判断来推导期望。
 */
public class PipelineConnectionTest {
    private static final String[] CONNECTED = {
            "───┐",
            " ┌ │",
            "─  │",
            "   └"
    };
    private static final String[] BROKEN = {
            "───┐",
            " ┌ ─",
            "─  │",
            "   └"
    };
    private static final int[] DR = {-1, 0, 1, 0};
    private static final int[] DC = {0, 1, 0, -1};
    private static final String[][] GLYPHS = {
            {"", "", "", ""},
            {"│", "─", "│", "─"},
            {"└", "┌", "┐", "┘"},
            {"├", "┬", "┤", "┴"},
            {"┼", "┼", "┼", "┼"}
    };

    @Test
    public void visibleConnectedRouteWinsWithoutMatchingHiddenTargets() throws Exception {
        PipelineGame game = fixture(CONNECTED, false);
        assertTrue("独立字符端口 BFS 应确认实际通路", visiblyConnected(game));
        assertTrue("玩家只需连接起终点，不必猜隐藏角度", game.isAllCorrect());
    }

    @Test
    public void matchingHiddenTargetsCannotWinWithBrokenVisibleRoute() throws Exception {
        PipelineGame game = fixture(BROKEN, true);
        assertFalse(visiblyConnected(game));
        assertFalse("上下相邻但横管没有互认端口，不能判定通关", game.isAllCorrect());
    }

    @Test
    public void oneSidedContactDoesNotMakeTheDestinationReachable() throws Exception {
        PipelineGame game = fixture(new String[]{"───┘", "   │", "   │", "   └"}, true);
        assertFalse("┘ 没有向下端口，即使下方 │ 向上也不能传水", visiblyConnected(game));
        assertFalse(game.isAllCorrect());
        assertFalse(game.isPipeCorrect(1, 3));
        assertFalse(game.isPipeCorrect(3, 3));
    }

    @Test
    public void pipeFeedbackMarksOnlyCellsReachableFromStart() throws Exception {
        PipelineGame game = fixture(CONNECTED, true);
        boolean[][] reached = visibleReachability(game);
        assertFalse("内部弯管不与水路相连", reached[1][1]);
        assertTrue(reached[3][3]);
        assertFalse("闲置弯管不能仅因匹配隐藏答案而显示已连通", game.isPipeCorrect(1, 1));
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                assertEquals("可达高亮必须依据显示端口: " + row + "," + col,
                        reached[row][col], game.isPipeCorrect(row, col));
            }
        }
    }

    @Test
    public void straightHalfTurnKeepsSameGlyphAndConnectionJudgement() throws Exception {
        PipelineGame game = fixture(CONNECTED, true);
        String before = game.getPipeChar(0, 1);
        boolean beforeFeedback = game.isPipeCorrect(0, 1);
        assertTrue(game.isAllCorrect());
        game.rotatePipe(0, 1);
        game.rotatePipe(0, 1);
        assertEquals(before, game.getPipeChar(0, 1));
        assertTrue(visiblyConnected(game));
        assertEquals("同形直管不能因 180° 的数值差异变错", beforeFeedback, game.isPipeCorrect(0, 1));
        assertTrue(game.isAllCorrect());
    }

    @Test
    public void rotatingAnUnusedPipeCannotBreakAnExistingRoute() throws Exception {
        PipelineGame game = fixture(CONNECTED, true);
        assertTrue(game.isAllCorrect());
        for (int turn = 0; turn < 4; turn++) {
            game.rotatePipe(1, 1);
            assertTrue(visiblyConnected(game));
            assertTrue("非水路上的管道朝向不应阻止通关", game.isAllCorrect());
        }
    }

    @Test
    public void reciprocalRouteMayUseTJunctionsAndCrossesWithOpenBranches() throws Exception {
        PipelineGame game = fixture(new String[]{"├┼┬┐", "   │", "   │", "   └"}, false);
        assertTrue(visiblyConnected(game));
        assertTrue("允许支路和空闲开口，无须覆盖全部端口", game.isAllCorrect());
    }

    @Test
    public void everyElbowOrientationUsesItsVisibleReciprocalPorts() throws Exception {
        PipelineGame game = fixture(new String[]{"───└", "   │", "   │", "   └"}, true);
        for (int turn = 0; turn < 4; turn++) {
            assertEquals("└/┌/┐/┘ 必须按笔画端口判断，只有左下弯 ┐ 接通本局",
                    visiblyConnected(game), game.isAllCorrect());
            assertEquals(visibleReachability(game)[0][3], game.isPipeCorrect(0, 3));
            game.rotatePipe(0, 3);
        }
    }

    @Test
    public void everyTJunctionOrientationUsesItsVisibleReciprocalPorts() throws Exception {
        PipelineGame game = fixture(new String[]{"─├─┐", "   │", "   │", "   └"}, true);
        for (int turn = 0; turn < 4; turn++) {
            assertEquals("├/┬/┤/┴ 必须按笔画端口判断，本局要求 T 管同时开放左右端口",
                    visiblyConnected(game), game.isAllCorrect());
            assertEquals(visibleReachability(game)[0][1], game.isPipeCorrect(0, 1));
            game.rotatePipe(0, 1);
        }
    }

    @Test
    public void verticalTJunctionDistinguishesItsTopAndBottomPorts() throws Exception {
        PipelineGame game = fixture(new String[]{"│   ", "├   ", "│   ", "└───"}, true);
        for (int turn = 0; turn < 4; turn++) {
            assertEquals("纵向路线要求 T 管上下两端互认，不能混淆 ┬ 与 ┴",
                    visiblyConnected(game), game.isAllCorrect());
            assertEquals("起点在 T 管上方，能否到达该格取决于上端口",
                    visibleReachability(game)[1][0], game.isPipeCorrect(1, 0));
            game.rotatePipe(1, 0);
        }
    }

    @Test
    public void elbowEnteredFromAboveMustExposeItsRightPortToWin() throws Exception {
        PipelineGame game = fixture(new String[]{"│   ", "└──┐", "   │", "   │"}, true);
        for (int turn = 0; turn < 4; turn++) {
            assertEquals("上入右出要求 └，不能与下入右出的 ┌ 混淆",
                    visiblyConnected(game), game.isAllCorrect());
            assertEquals(visibleReachability(game)[1][0], game.isPipeCorrect(1, 0));
            game.rotatePipe(1, 0);
        }
    }

    @Test
    public void disconnectedBoardCannotAdvanceOrEarnPoints() throws Exception {
        PipelineGame game = fixture(BROKEN, true);
        assertFalse(visiblyConnected(game));
        assertEquals("结算必须再次检查实际连通", 0, game.completeLevel());
        assertEquals(1, game.getCurrentLevel());
        assertEquals(0, game.getTotalScore());
        assertTrue(game.isGameActive());
    }

    @Test
    public void completedLevelCannotBeAwardedTwiceOrRotatedAgain() throws Exception {
        PipelineGame game = fixture(CONNECTED, true);
        assertTrue(visiblyConnected(game));
        int awarded = game.completeLevel();
        assertTrue(awarded > 0);
        assertFalse(game.isGameActive());
        assertEquals(2, game.getCurrentLevel());
        assertEquals(awarded, game.getTotalScore());
        assertEquals("重复结算不能累加分数或推进关卡", 0, game.completeLevel());
        assertEquals(2, game.getCurrentLevel());
        assertEquals(awarded, game.getTotalScore());
        String before = game.getPipeChar(0, 0);
        int moves = game.getMoveCount();
        game.rotatePipe(0, 0);
        assertEquals(before, game.getPipeChar(0, 0));
        assertEquals(moves, game.getMoveCount());
    }

    @Test
    public void anUnstartedGameCannotBeCompleted() {
        PipelineGame game = new PipelineGame();
        assertEquals(0, game.completeLevel());
        assertEquals(1, game.getCurrentLevel());
        assertEquals(0, game.getTotalScore());
    }

    @Test
    public void generatedFourByFourLevelsHaveARealSolution() throws Exception {
        assertGeneratedSolutions(1, 4);
    }

    @Test
    public void generatedFiveByFiveLevelsHaveARealSolution() throws Exception {
        assertGeneratedSolutions(3, 5);
    }

    @Test
    public void generatedSixBySixLevelsHaveARealSolution() throws Exception {
        assertGeneratedSolutions(5, 6);
    }

    private static void assertGeneratedSolutions(int level, int expectedSize) throws Exception {
        for (int seed = 0; seed < 12; seed++) {
            PipelineGame game = new PipelineGame();
            // 只固定随机源和待生成关卡，不注入生成结果。所有管形均来自真实生成器。
            ((Random) field("random").get(game)).setSeed(0x50495045L + seed);
            field("currentLevel").setInt(game, level);
            game.startLevel();
            assertEquals(expectedSize, game.getGridSize());

            // 生成器的目标旋转只作为一条可解路线的见证；通关语义不依赖它。
            // 先实际显示该见证，再由字符端口 BFS 判断，能捕获路径弯管的朝向映射错误。
            int[][] witness = (int[][]) field("targetRotations").get(game);
            for (int row = 0; row < expectedSize; row++) {
                for (int col = 0; col < expectedSize; col++) {
                    rotateToGlyph(game, row, col,
                            GLYPHS[game.getPipeType(row, col)][witness[row][col]]);
                }
            }
            assertTrue("生成器的解应真实连通: level=" + level + ", seed=" + seed,
                    visiblyConnected(game));

            // 打乱后独立搜一条可由现有管形实现的简单路径；不读取生产目标来选路。
            int[][] solution = new int[expectedSize][expectedSize];
            for (int[] row : solution) Arrays.fill(row, -1);
            for (int row = 0; row < expectedSize; row++) {
                for (int col = 0; col < expectedSize; col++) game.randomizeRotation(row, col);
            }
            assertTrue("真实生成的管形必须存在可实现路线",
                    findRoute(game, 0, 0, 0, new boolean[expectedSize][expectedSize], solution));
            for (int row = 0; row < expectedSize; row++) {
                for (int col = 0; col < expectedSize; col++) {
                    if (solution[row][col] >= 0) {
                        rotateToGlyph(game, row, col,
                                GLYPHS[game.getPipeType(row, col)][solution[row][col]]);
                    }
                }
            }
            assertTrue(visiblyConnected(game));
            assertTrue("独立路线也应被接受，闲置管保持随机朝向", game.isAllCorrect());
        }
    }

    private static boolean findRoute(PipelineGame game, int row, int col, int incoming,
                                     boolean[][] visited, int[][] solution) {
        int size = game.getGridSize();
        int type = game.getPipeType(row, col);
        if (type == PipelineGame.PIPE_NONE) return false;
        if (row == size - 1 && col == size - 1) {
            solution[row][col] = fittingRotation(type, incoming);
            return solution[row][col] >= 0;
        }
        visited[row][col] = true;
        for (int direction : new int[]{1, 2, 3, 0}) {
            int nextRow = row + DR[direction], nextCol = col + DC[direction];
            if (nextRow < 0 || nextCol < 0 || nextRow >= size || nextCol >= size
                    || visited[nextRow][nextCol]) continue;
            int rotation = fittingRotation(type, incoming | (1 << direction));
            if (rotation < 0) continue;
            solution[row][col] = rotation;
            if (findRoute(game, nextRow, nextCol, 1 << ((direction + 2) % 4), visited, solution)) {
                return true;
            }
        }
        solution[row][col] = -1;
        visited[row][col] = false;
        return false;
    }

    private static int fittingRotation(int type, int requiredPorts) {
        for (int rotation = 0; rotation < 4; rotation++) {
            if ((mask(GLYPHS[type][rotation]) & requiredPorts) == requiredPorts) return rotation;
        }
        return -1;
    }

    private static void rotateToGlyph(PipelineGame game, int row, int col, String expected) {
        for (int turns = 0; turns < 4 && !expected.equals(game.getPipeChar(row, col)); turns++) {
            game.rotatePipe(row, col);
        }
        assertEquals("公开旋转必须能得到要求的显示管形", expected, game.getPipeChar(row, col));
    }

    private static PipelineGame fixture(String[] glyphRows, boolean matchTargets) throws Exception {
        PipelineGame game = new PipelineGame();
        game.startLevel();
        int size = game.getGridSize();
        assertEquals(size, glyphRows.length);
        int[][] types = new int[size][size];
        int[][] rotations = new int[size][size];
        int[][] targets = new int[size][size];
        for (int row = 0; row < size; row++) {
            assertEquals(size, glyphRows[row].length());
            for (int col = 0; col < size; col++) {
                String glyph = String.valueOf(glyphRows[row].charAt(col)).trim();
                boolean found = false;
                for (int type = 0; type < GLYPHS.length && !found; type++) {
                    for (int rotation = 0; rotation < 4; rotation++) {
                        if (GLYPHS[type][rotation].equals(glyph)) {
                            types[row][col] = type;
                            rotations[row][col] = rotation;
                            targets[row][col] = matchTargets ? rotation : 0;
                            found = true;
                            break;
                        }
                    }
                }
                assertTrue("Fixture 必须使用已知显示符号: " + glyph, found);
            }
        }
        field("pipeTypes").set(game, types);
        field("pipeRotations").set(game, rotations);
        field("targetRotations").set(game, targets);
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                assertEquals(String.valueOf(glyphRows[row].charAt(col)).trim(), game.getPipeChar(row, col));
            }
        }
        return game;
    }

    private static Field field(String name) throws Exception {
        Field field = PipelineGame.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static boolean visiblyConnected(PipelineGame game) {
        int last = game.getGridSize() - 1;
        return visibleReachability(game)[last][last];
    }

    private static boolean[][] visibleReachability(PipelineGame game) {
        int size = game.getGridSize();
        boolean[][] seen = new boolean[size][size];
        ArrayDeque<int[]> pending = new ArrayDeque<>();
        if (mask(game.getPipeChar(0, 0)) == 0) return seen;
        seen[0][0] = true;
        pending.add(new int[]{0, 0});
        while (!pending.isEmpty()) {
            int[] cell = pending.remove();
            int ports = mask(game.getPipeChar(cell[0], cell[1]));
            for (int direction = 0; direction < 4; direction++) {
                int row = cell[0] + DR[direction], col = cell[1] + DC[direction];
                if (row < 0 || col < 0 || row >= size || col >= size || seen[row][col]) continue;
                int opposite = 1 << ((direction + 2) % 4);
                if ((ports & (1 << direction)) != 0 && (mask(game.getPipeChar(row, col)) & opposite) != 0) {
                    seen[row][col] = true;
                    pending.add(new int[]{row, col});
                }
            }
        }
        return seen;
    }

    /** N=1, E=2, S=4, W=8；该表直接按字符的可见笔画定义，独立于生产端口表。 */
    private static int mask(String glyph) {
        switch (glyph) {
            case "│": return 5;
            case "─": return 10;
            case "└": return 3;
            case "┌": return 6;
            case "┐": return 12;
            case "┘": return 9;
            case "├": return 7;
            case "┬": return 14;
            case "┤": return 13;
            case "┴": return 11;
            case "┼": return 15;
            case "": return 0;
            default: throw new AssertionError("Unknown visible pipe glyph: " + glyph);
        }
    }
}
