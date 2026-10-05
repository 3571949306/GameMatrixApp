package com.gamecenter.app.tetris;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 正常随机 7-bag、公开旋转/硬降操作和只读 getter；不注入棋盘、随机种子或生产形状表。
 * 记录真实 onDraw 发出的半透明 Canvas 格子，再与独立旋转/碰撞计算及实际锁块比较。
 * 这是绘图命令回归，不代替设备窗口像素验证。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = Application.class, qualifiers = "mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class TetrisGhostPreviewTest {
    private static final int COLS = 10;
    private static final int ROWS = 20;
    private static final int[] COLORS = {
            0xFF00BCD4, 0xFFFFEB3B, 0xFF9C27B0, 0xFFFF9800,
            0xFF2196F3, 0xFF4CAF50, 0xFFF44336
    };
    // 独立规定七种经典出生形状；其余方向由几何顺时针旋转推导。
    private static final int[][][] SPAWN_CELLS = {
            {{0, 1}, {1, 1}, {2, 1}, {3, 1}},
            {{0, 0}, {1, 0}, {0, 1}, {1, 1}},
            {{1, 0}, {0, 1}, {1, 1}, {2, 1}},
            {{2, 0}, {0, 1}, {1, 1}, {2, 1}},
            {{0, 0}, {0, 1}, {1, 1}, {2, 1}},
            {{1, 0}, {2, 0}, {0, 1}, {1, 1}},
            {{0, 0}, {1, 0}, {1, 1}, {2, 1}}
    };

    private TetrisView view;

    @Before
    public void setUp() {
        view = new TetrisView(ApplicationProvider.getApplicationContext());
        layout(360, 640);
        view.startGame();
        assertTrue(view.isGhostEnabled());
    }

    @After
    public void tearDown() {
        if (view != null) view.stopGame();
    }

    @Test
    public void portraitGhostShowsAllFourLandingCellsForEveryPieceAndRotation() {
        verifyOneNaturalBag(360, 640);
    }

    @Test
    public void landscapeGhostShowsAllFourLandingCellsForEveryPieceAndRotation() {
        verifyOneNaturalBag(640, 360);
    }

    @Test
    public void disabledGhostAndAlreadyLandedPieceHaveNoSeparatePreview() {
        view.setGhostEnabled(false);
        assertEquals("关闭落点预览时不能绘制 ghost", 0, drawCommands().ghosts(view.getCurrentPiece()).size());
        view.setGhostEnabled(true);
        int steps = 0;
        while (view.softDrop()) {
            assertTrue("真实软降应在棋盘底部停止", ++steps <= ROWS);
        }
        assertTrue("自然出生块必须实际下落过", steps > 0);
        assertFalse(view.isGameOver());
        int[][] cells = shape(view.getCurrentPiece(), view.getCurrentRotation());
        assertEquals("已触底的方块不能另画重叠 ghost", view.getPieceY(), landingY(cells, view.getGrid()));
        assertEquals(0, drawCommands().ghosts(view.getCurrentPiece()).size());
    }

    private void verifyOneNaturalBag(int width, int height) {
        layout(width, height);
        Set<Integer> seen = new HashSet<>();
        int previews = 0;
        for (int turn = 0; turn < 7; turn++) {
            assertFalse("前七块居中堆叠应仍可正常游戏", view.isGameOver());
            int piece = view.getCurrentPiece();
            assertTrue("正常 7-bag 首袋不应重复方块", seen.add(piece));
            assertEquals(0, view.getCurrentRotation());
            for (int rotation = 0; rotation < 4; rotation++) {
                assertEquals("公开旋转必须进入待测方向", rotation, view.getCurrentRotation());
                assertGhostMatchesIndependentLanding("piece=" + piece + " rotation=" + rotation);
                previews++;
                view.rotate();
            }
            assertEquals("四次顺时针旋转应回到出生方向", 0, view.getCurrentRotation());
            Set<String> displayedLanding = assertGhostMatchesIndependentLanding("before hard drop piece=" + piece);
            int[][] before = view.getGrid();
            view.hardDrop();
            assertEquals("仅居中堆叠不会铺满十列，比较不受消行动画影响", 0, view.getLines());
            assertEquals("硬降实际新增四格必须与刚才画出的落点完全一致",
                    displayedLanding, newlyLockedCells(before, view.getGrid(), piece));
        }
        assertEquals(new HashSet<>(Arrays.asList(0, 1, 2, 3, 4, 5, 6)), seen);
        assertEquals("七种方块各覆盖四个旋转状态", 28, previews);
    }

    private Set<String> assertGhostMatchesIndependentLanding(String label) {
        int piece = view.getCurrentPiece();
        int[][] cells = shape(piece, view.getCurrentRotation());
        int[][] board = view.getGrid();
        int landing = landingY(cells, board);
        assertTrue(label + ": 当前块与落点间应有可见距离", landing > view.getPieceY());
        assertFalse(label + ": 再落一行必须触底或碰到已锁方块",
                valid(cells, board, view.getPieceX(), landing + 1));
        Set<String> expected = new TreeSet<>();
        for (int[] cell : cells) expected.add(key(view.getPieceX() + cell[0], landing + cell[1]));

        RecordingCanvas canvas = drawCommands();
        List<RectF> ghosts = canvas.ghosts(piece);
        assertEquals(label + ": 落点预览必须画出整块四格，不能只画锚点一格", 4, ghosts.size());
        BoardGeometry boardGeometry = canvas.boardGeometry();
        Set<String> actual = new TreeSet<>();
        for (RectF ghost : ghosts) {
            assertEquals(label + ": ghost 必须是方形格子", ghost.width(), ghost.height(), 0.01f);
            float column = (ghost.centerX() - boardGeometry.left) / boardGeometry.cell - 0.5f;
            float row = (ghost.centerY() - boardGeometry.top) / boardGeometry.cell - 0.5f;
            int x = Math.round(column), y = Math.round(row);
            assertEquals(label + ": 预览横向应对齐真实网格", x, column, 0.01f);
            assertEquals(label + ": 预览纵向应对齐真实网格", y, row, 0.01f);
            assertTrue(label + ": 每个预览格都必须在棋盘内", x >= 0 && x < COLS && y >= 0 && y < ROWS);
            assertTrue(label + ": 四格不能重复绘制在同一位置", actual.add(key(x, y)));
        }
        assertEquals(label + ": 四格形状及位置应等于独立计算的合法最终落点", expected, actual);
        return actual;
    }

    private int landingY(int[][] cells, int[][] board) {
        int y = view.getPieceY();
        assertTrue("真实活动块当前占格必须合法", valid(cells, board, view.getPieceX(), y));
        while (valid(cells, board, view.getPieceX(), y + 1)) y++;
        return y;
    }

    private static boolean valid(int[][] cells, int[][] board, int x, int y) {
        for (int[] cell : cells) {
            int column = x + cell[0], row = y + cell[1];
            if (column < 0 || column >= COLS || row < 0 || row >= ROWS || board[row][column] != 0) return false;
        }
        return true;
    }

    private static int[][] shape(int piece, int rotation) {
        int[][] cells = new int[4][2];
        int size = piece == TetrisView.PIECE_I ? 4 : piece == TetrisView.PIECE_O ? 2 : 3;
        for (int index = 0; index < cells.length; index++) {
            int x = SPAWN_CELLS[piece][index][0], y = SPAWN_CELLS[piece][index][1];
            for (int turn = 0; turn < rotation; turn++) {
                int previousX = x;
                x = size - 1 - y;
                y = previousX;
            }
            cells[index][0] = x;
            cells[index][1] = y;
        }
        return cells;
    }

    private static Set<String> newlyLockedCells(int[][] before, int[][] after, int piece) {
        Set<String> added = new TreeSet<>();
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLS; column++) {
                if (before[row][column] != 0) {
                    assertEquals("旧锁块不能被硬降覆盖", before[row][column], after[row][column]);
                } else if (after[row][column] != 0) {
                    assertEquals("新增格子必须属于刚才的活动块", piece + 1, after[row][column]);
                    added.add(key(column, row));
                }
            }
        }
        assertEquals("硬降应实际锁住完整四格", 4, added.size());
        return added;
    }

    private static String key(int column, int row) { return column + "," + row; }

    private void layout(int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private RecordingCanvas drawCommands() {
        RecordingCanvas canvas = new RecordingCanvas();
        view.onDraw(canvas);
        return canvas;
    }

    private static final class BoardGeometry {
        final float left, top, cell;

        BoardGeometry(float left, float top, float cell) {
            this.left = left;
            this.top = top;
            this.cell = cell;
        }
    }

    private static final class BlockCommand {
        final RectF rect;
        final int color;
        final Paint.Style style;

        BlockCommand(RectF rect, Paint paint) {
            this.rect = new RectF(rect);
            color = paint.getColor();
            style = paint.getStyle();
        }
    }

    private static final class RecordingCanvas extends Canvas {
        final List<BlockCommand> blocks = new ArrayList<>();
        final Set<Float> verticalLines = new TreeSet<>();
        final Set<Float> horizontalLines = new TreeSet<>();

        @Override
        public void drawRoundRect(RectF rect, float rx, float ry, Paint paint) {
            blocks.add(new BlockCommand(rect, paint));
        }

        @Override
        public void drawLine(float startX, float startY, float stopX, float stopY, Paint paint) {
            if (startX == stopX && stopY > startY) verticalLines.add(startX);
            if (startY == stopY && stopX > startX) horizontalLines.add(startY);
        }

        List<RectF> ghosts(int piece) {
            int expected = (COLORS[piece] & 0x00FFFFFF) | 0x60000000;
            List<RectF> result = new ArrayList<>();
            for (BlockCommand block : blocks) {
                // Legacy Robolectric can report null for the default FILL style.
                if (block.color == expected && block.style != Paint.Style.STROKE) result.add(block.rect);
            }
            return result;
        }

        BoardGeometry boardGeometry() {
            assertEquals("真实棋盘必须绘制十一条竖线", COLS + 1, verticalLines.size());
            assertEquals("真实棋盘必须绘制二十一条横线", ROWS + 1, horizontalLines.size());
            List<Float> xs = new ArrayList<>(verticalLines), ys = new ArrayList<>(horizontalLines);
            float cell = (xs.get(COLS) - xs.get(0)) / COLS;
            assertTrue(cell > 0);
            assertEquals(cell, (ys.get(ROWS) - ys.get(0)) / ROWS, 0.01f);
            return new BoardGeometry(xs.get(0), ys.get(0), cell);
        }
    }
}
