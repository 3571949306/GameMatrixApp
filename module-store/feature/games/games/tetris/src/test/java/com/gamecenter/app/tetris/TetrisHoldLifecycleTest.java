package com.gamecenter.app.tetris;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
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
import java.util.Collections;
import java.util.List;

/**
 * 正常 7-bag 开局后触摸生产软控制，状态只经公开 getter 读取，不注入棋盘或调用 hold。
 * HUD 部分记录真实 onDraw 发出的 Canvas 命令，不声称它们是设备窗口像素截图。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = Application.class, qualifiers = "mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
public class TetrisHoldLifecycleTest {
    private static final int HOLD_SLOT_COLOR = 0x30000000;
    private static final int CONTROL_COLOR = 0x55000000;
    private static final int[] PIECE_COLORS = {
            0xFF00BCD4, 0xFFFFEB3B, 0xFF9C27B0, 0xFFFF9800,
            0xFF2196F3, 0xFF4CAF50, 0xFFF44336
    };
    // 旋转 0 的占格，按可视形状去掉外围空白后排序，与预览具体像素尺寸无关。
    private static final String[][] SPAWN_CELLS = {
            {"0,0", "1,0", "2,0", "3,0"},
            {"0,0", "0,1", "1,0", "1,1"},
            {"0,1", "1,0", "1,1", "2,1"},
            {"0,1", "1,1", "2,0", "2,1"},
            {"0,0", "0,1", "1,1", "2,1"},
            {"0,1", "1,0", "1,1", "2,0"},
            {"0,0", "1,0", "1,1", "2,1"}
    };

    private TetrisView view;

    @Before
    public void setUp() {
        view = new TetrisView(ApplicationProvider.getApplicationContext());
        layout(360, 640);
        view.startGame();
        assertEquals(-1, view.getHoldPiece());
        assertFalse(view.isHoldUsedThisTurn());
        assertEquals(0, occupiedCells());
    }

    @After
    public void tearDown() {
        if (view != null) view.stopGame();
    }

    @Test
    public void firstHoldThroughTheScreenControlLocksHoldForThisTurn() {
        int original = view.getCurrentPiece();
        List<Integer> queue = new ArrayList<>(view.getNextQueue());
        assertNotEquals("真实 7-bag 的前两个块应不同", original, queue.get(0).intValue());

        touchControl("HOLD");

        assertEquals(original, view.getHoldPiece());
        assertEquals(queue.get(0).intValue(), view.getCurrentPiece());
        assertEquals(queue.subList(1, queue.size()),
                new ArrayList<>(view.getNextQueue()).subList(0, queue.size() - 1));
        assertTrue("首次填入空暂存槽后，本次落块的 HOLD 必须已用", view.isHoldUsedThisTurn());
        assertEquals(0, occupiedCells());
    }

    @Test
    public void aSecondHoldBeforeLockChangesNeitherPieceQueueNorScore() {
        touchControl("HOLD");
        Snapshot afterFirst = snapshot();

        touchControl("HOLD");

        assertSnapshotEquals("同次落块的第二次 HOLD 必须无效", afterFirst);
        assertTrue(view.isHoldUsedThisTurn());
    }

    @Test
    public void hardDropLocksThePieceAndAllowsExactlyOneOccupiedSlotSwap() {
        int firstPiece = view.getCurrentPiece();
        touchControl("HOLD");
        List<Integer> nextBeforeDrop = new ArrayList<>(view.getNextQueue());

        touchControl("▼");

        assertEquals("空棋盘硬降必须实际锁住四格", 4, occupiedCells());
        assertEquals(nextBeforeDrop.get(0).intValue(), view.getCurrentPiece());
        assertEquals(firstPiece, view.getHoldPiece());
        assertFalse("锁块后的新回合应恢复 HOLD", view.isHoldUsedThisTurn());
        int nextPiece = view.getCurrentPiece();
        List<Integer> queueBeforeSwap = new ArrayList<>(view.getNextQueue());

        touchControl("HOLD");

        assertEquals(firstPiece, view.getCurrentPiece());
        assertEquals(nextPiece, view.getHoldPiece());
        assertEquals("已有暂存块的交换不能再消耗 NEXT", queueBeforeSwap,
                new ArrayList<>(view.getNextQueue()));
        assertTrue(view.isHoldUsedThisTurn());
        Snapshot afterSwap = snapshot();
        touchControl("HOLD");
        assertSnapshotEquals("本回合再次交换仍应被拒绝", afterSwap);
    }

    @Test
    public void portraitHudKeepsDrawingTheStoredPieceWhileHoldIsUsed() {
        assertUsedHoldStillDrawsStoredPiece(360, 640);
    }

    @Test
    public void landscapeHudKeepsDrawingTheStoredPieceWhileHoldIsUsed() {
        assertUsedHoldStillDrawsStoredPiece(640, 360);
    }

    private void assertUsedHoldStillDrawsStoredPiece(int width, int height) {
        layout(width, height);
        touchControl("HOLD");
        touchControl("▼");
        assertEquals(4, occupiedCells());
        int expectedStoredPiece = view.getCurrentPiece();
        // 通过真实已占槽交换建立 used 状态；旧生产逻辑也可抵达，不被首次 HOLD 红测遮挡。
        touchControl("HOLD");
        assertTrue(view.isHoldUsedThisTurn());
        assertEquals(expectedStoredPiece, view.getHoldPiece());

        RecordingCanvas canvas = drawCommands();
        RectF slot = canvas.holdSlot();
        assertTrue("暂存预览槽应位于当前视图内",
                new RectF(0, 0, view.getWidth(), view.getHeight()).contains(slot));
        List<DrawCommand> blocks = new ArrayList<>();
        for (DrawCommand command : canvas.commands) {
            if (command.rect != null && command.color == PIECE_COLORS[expectedStoredPiece]
                    // Robolectric legacy Paint reports null for Android's default FILL.
                    && command.style != Paint.Style.STROKE && slot.contains(command.rect)) {
                blocks.add(command);
            }
        }
        StringBuilder observed = new StringBuilder();
        for (DrawCommand command : canvas.commands) {
            if (command.rect != null && slot.contains(command.rect)) {
                observed.append(Integer.toHexString(command.color)).append('/')
                        .append(command.style).append('/').append(command.rect).append(';');
            }
        }
        assertEquals("HOLD 本回合已用时仍须绘制实际暂存块的四个主色格; observed=" + observed,
                4, blocks.size());
        assertEquals("预览必须展示实际暂存块的形状", Arrays.asList(SPAWN_CELLS[expectedStoredPiece]),
                normalizedCells(blocks));
    }

    private static List<String> normalizedCells(List<DrawCommand> blocks) {
        float left = Float.MAX_VALUE, top = Float.MAX_VALUE;
        float step = Float.MAX_VALUE;
        for (DrawCommand block : blocks) {
            left = Math.min(left, block.rect.centerX());
            top = Math.min(top, block.rect.centerY());
            assertEquals("方块主色格应为正方形", block.rect.width(), block.rect.height(), 0.01f);
            for (DrawCommand other : blocks) {
                float distance = Math.abs(block.rect.centerX() - other.rect.centerX());
                if (distance > 0.01f) step = Math.min(step, distance);
            }
        }
        assertTrue("四格预览应包含可分辨的列间距", step > 0 && step < Float.MAX_VALUE);
        List<String> result = new ArrayList<>();
        for (DrawCommand block : blocks) {
            result.add(Math.round((block.rect.centerX() - left) / step) + ","
                    + Math.round((block.rect.centerY() - top) / step));
        }
        Collections.sort(result);
        return result;
    }

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

    private void touchControl(String label) {
        RectF target = drawCommands().control(label);
        assertTrue(new RectF(0, 0, view.getWidth(), view.getHeight()).contains(target));
        long time = SystemClock.uptimeMillis();
        dispatch(time, MotionEvent.ACTION_DOWN, target.centerX(), target.centerY());
        dispatch(time, MotionEvent.ACTION_UP, target.centerX(), target.centerY());
    }

    private void dispatch(long downTime, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0);
        try {
            assertTrue("生产软控制必须接收真实触摸", view.dispatchTouchEvent(event));
        } finally {
            event.recycle();
        }
    }

    private int occupiedCells() {
        int count = 0;
        for (int[] row : view.getGrid()) for (int cell : row) if (cell != 0) count++;
        return count;
    }

    private Snapshot snapshot() {
        return new Snapshot(view.getCurrentPiece(), view.getCurrentRotation(), view.getPieceX(), view.getPieceY(),
                view.getHoldPiece(), view.getScore(), new ArrayList<>(view.getNextQueue()), view.getGrid());
    }

    private void assertSnapshotEquals(String message, Snapshot before) {
        Snapshot after = snapshot();
        assertEquals(message + ": current", before.current, after.current);
        assertEquals(message + ": rotation", before.rotation, after.rotation);
        assertEquals(message + ": x", before.x, after.x);
        assertEquals(message + ": y", before.y, after.y);
        assertEquals(message + ": hold", before.hold, after.hold);
        assertEquals(message + ": score", before.score, after.score);
        assertEquals(message + ": queue", before.queue, after.queue);
        assertTrue(message + ": grid", Arrays.deepEquals(before.grid, after.grid));
    }

    private static final class Snapshot {
        final int current, rotation, x, y, hold, score;
        final List<Integer> queue;
        final int[][] grid;

        Snapshot(int current, int rotation, int x, int y, int hold, int score,
                 List<Integer> queue, int[][] grid) {
            this.current = current;
            this.rotation = rotation;
            this.x = x;
            this.y = y;
            this.hold = hold;
            this.score = score;
            this.queue = queue;
            this.grid = grid;
        }
    }

    private static final class DrawCommand {
        final String text;
        final float x, y;
        final RectF rect;
        final int color;
        final Paint.Style style;

        DrawCommand(String text, float x, float y, RectF rect, Paint paint) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.rect = rect == null ? null : new RectF(rect);
            this.color = paint.getColor();
            this.style = paint.getStyle();
        }
    }

    private static final class RecordingCanvas extends Canvas {
        final List<DrawCommand> commands = new ArrayList<>();

        @Override
        public void drawRoundRect(RectF rect, float rx, float ry, Paint paint) {
            commands.add(new DrawCommand(null, 0, 0, rect, paint));
        }

        @Override
        public void drawText(String text, float x, float y, Paint paint) {
            commands.add(new DrawCommand(text, x, y, null, paint));
        }

        RectF holdSlot() {
            boolean afterHoldLabel = false;
            for (DrawCommand command : commands) {
                if ("HOLD".equals(command.text)) afterHoldLabel = true;
                if (afterHoldLabel && command.rect != null && command.color == HOLD_SLOT_COLOR) {
                    return new RectF(command.rect);
                }
            }
            throw new AssertionError("生产 HUD 必须绘制 HOLD 槽");
        }

        RectF control(String label) {
            DrawCommand text = null;
            for (DrawCommand command : commands) if (label.equals(command.text)) text = command;
            assertNotNull("生产 Canvas 必须绘制软控制标签: " + label, text);
            for (DrawCommand command : commands) {
                if (command.rect != null && command.color == CONTROL_COLOR
                        && command.rect.contains(text.x, text.y)) return new RectF(command.rect);
            }
            throw new AssertionError("找不到标签对应的真实软控制触摸框: " + label);
        }
    }
}
