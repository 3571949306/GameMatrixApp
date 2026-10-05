package com.gamecenter.app.minesweeper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.graphics.RectF;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/** 只发送真实触摸与公开开局操作，不注入雷图、格状态或 Handler 回调。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class MinesweeperTouchLifecycleTest {
    private static final int VIEW_WIDTH = 600;
    private static final int VIEW_HEIGHT = 640;
    private static final int HARD_ROWS = 16;
    private static final int HARD_COLS = 30;
    private static final float LAST_CELL_X = 590f;
    private static final float LAST_CELL_Y = 470f;
    private static final float BELOW_GRID_Y = 510f;

    private MinesweeperView view;

    @Before
    public void setUp() {
        view = new MinesweeperView(ApplicationProvider.getApplicationContext());
        view.setDifficulty(MinesweeperView.DIFF_HARD);
        view.startGame();
        view.measure(View.MeasureSpec.makeMeasureSpec(VIEW_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(VIEW_HEIGHT, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, VIEW_WIDTH, VIEW_HEIGHT);
        assertHardBoardGeometry();
    }

    @After
    public void tearDown() {
        // 模拟真实 View 移除，防止失败用例留下的延迟任务污染其他用例。
        if (view != null) view.onDetachedFromWindow();
    }

    @Test
    public void releaseOutsideGridDoesNotFlagAfterFingerUp() {
        assertOutsideTerminationCancelsLongPress(MotionEvent.ACTION_UP);
    }

    @Test
    public void cancelOutsideGridDoesNotFlagAfterGestureEnds() {
        assertOutsideTerminationCancelsLongPress(MotionEvent.ACTION_CANCEL);
    }

    @Test
    public void pendingHardBoardLongPressDoesNotSurviveEasyRestart() {
        long downTime = pressLastHardCell();
        advanceMainClock(100);

        // 与 Fragment 难度按钮一致的公开调用链；新局必须结束尚未完成的旧局手势。
        // 旧坐标 (15, 29) 在简单棋盘 (9 x 9) 外，旧回调不能访问新数组。
        view.setDifficulty(MinesweeperView.DIFF_EASY);
        view.startGame();
        assertEquals(MinesweeperView.DIFF_EASY, view.getDifficulty());
        assertEquals(10, view.getMineCount());
        assertEquals(0, view.getFlaggedCount());

        advanceMainClock(600);
        assertEquals("旧局长按不能在新局插旗", 0, view.getFlaggedCount());
        assertTrue(view.isGameStarted());
        assertFalse(view.isGameOver());
        dispatch(downTime, MotionEvent.ACTION_CANCEL, LAST_CELL_X, LAST_CELL_Y);
    }

    @Test
    public void heldLongPressStillFlagsAndUnflagsWithoutRevealingCell() {
        AtomicInteger revealCallbacks = new AtomicInteger();
        view.setOnCellRevealedListener(count -> revealCallbacks.incrementAndGet());

        long firstDown = pressLastHardCell();
        advanceMainClock(499);
        assertEquals("长按阈值前不能插旗", 0, view.getFlaggedCount());
        advanceMainClock(2);
        assertEquals("持续长按应插旗", 1, view.getFlaggedCount());
        dispatch(firstDown, MotionEvent.ACTION_UP, LAST_CELL_X, LAST_CELL_Y);
        advanceMainClock(600);
        assertEquals("长按抬手不能再次切换旗标", 1, view.getFlaggedCount());

        long secondDown = pressLastHardCell();
        advanceMainClock(501);
        assertEquals("再次长按同格应撤旗", 0, view.getFlaggedCount());
        dispatch(secondDown, MotionEvent.ACTION_UP, LAST_CELL_X, LAST_CELL_Y);
        advanceMainClock(600);
        assertEquals(0, view.getFlaggedCount());
        assertEquals("长按插撤旗不能同时触发翻格", 0, revealCallbacks.get());
        assertFalse(view.isGameOver());
    }

    @Test
    public void movingToAnotherCellThenBackCancelsTheWholeGesture() {
        AtomicInteger revealCallbacks = new AtomicInteger();
        view.setOnCellRevealedListener(count -> revealCallbacks.incrementAndGet());
        long downTime = pressLastHardCell();
        advanceMainClock(100);
        dispatch(downTime, MotionEvent.ACTION_MOVE, LAST_CELL_X - 20, LAST_CELL_Y);
        dispatch(downTime, MotionEvent.ACTION_MOVE, LAST_CELL_X, LAST_CELL_Y);
        advanceMainClock(600);
        assertEquals("离开起始格后移回也不能恢复原长按", 0, view.getFlaggedCount());
        dispatch(downTime, MotionEvent.ACTION_UP, LAST_CELL_X, LAST_CELL_Y);
        assertEquals("取消的拖动不能变成短按翻格", 0, revealCallbacks.get());
    }

    @Test
    public void movingOutsideGridCancelsLongPressBeforeFingerUp() {
        long downTime = pressLastHardCell();
        advanceMainClock(100);
        dispatch(downTime, MotionEvent.ACTION_MOVE, LAST_CELL_X, BELOW_GRID_Y);
        advanceMainClock(600);
        assertEquals(0, view.getFlaggedCount());
        dispatch(downTime, MotionEvent.ACTION_UP, LAST_CELL_X, BELOW_GRID_Y);
    }

    @Test
    public void touchJustAboveGridCannotRoundIntoFirstRow() {
        AtomicInteger revealCallbacks = new AtomicInteger();
        view.setOnCellRevealedListener(count -> revealCallbacks.incrementAndGet());
        long downTime = SystemClock.uptimeMillis();
        // 上边界为 160px；159.75px 仍在 View 内，却不属于棋盘。
        dispatch(downTime, MotionEvent.ACTION_DOWN, LAST_CELL_X, 159.75f);
        advanceMainClock(600);
        assertEquals("负小数行坐标不能截断成第 0 行", 0, view.getFlaggedCount());
        dispatch(downTime, MotionEvent.ACTION_UP, LAST_CELL_X, 159.75f);
        assertEquals(0, revealCallbacks.get());
    }

    @Test
    public void pauseResumeDropsOldPressAndAllowsANewLongPress() {
        AtomicInteger revealCallbacks = new AtomicInteger();
        view.setOnCellRevealedListener(count -> revealCallbacks.incrementAndGet());
        long oldDown = pressLastHardCell();
        advanceMainClock(100);
        view.pauseGame();
        advanceMainClock(600);
        assertEquals("进入后台时旧长按不能继续插旗", 0, view.getFlaggedCount());
        view.resumeGame();
        dispatch(oldDown, MotionEvent.ACTION_UP, LAST_CELL_X, LAST_CELL_Y);
        assertEquals("恢复后的旧抬手不能翻格", 0, revealCallbacks.get());

        long newDown = pressLastHardCell();
        advanceMainClock(501);
        assertEquals("恢复后新手势仍应有效", 1, view.getFlaggedCount());
        dispatch(newDown, MotionEvent.ACTION_UP, LAST_CELL_X, LAST_CELL_Y);
    }

    @Test
    public void stopDropsPendingLongPress() {
        pressLastHardCell();
        advanceMainClock(100);
        view.stopGame();
        advanceMainClock(600);
        assertFalse(view.isGameStarted());
        assertEquals("结束旧局不能继续改变旗标", 0, view.getFlaggedCount());
    }

    @Test
    public void sameDifficultyRestartGivesNewPressItsOwnFullDelay() {
        pressLastHardCell();
        advanceMainClock(100);
        view.startGame();
        long newDown = pressLastHardCell();
        // 此时旧手势已超过 500ms，新手势还没有；旧任务不能提前触发新手势。
        advanceMainClock(450);
        assertEquals("重开后的新手势不能继承旧倒计时", 0, view.getFlaggedCount());
        advanceMainClock(51);
        assertEquals(1, view.getFlaggedCount());
        dispatch(newDown, MotionEvent.ACTION_UP, LAST_CELL_X, LAST_CELL_Y);
    }

    @Test
    public void detachedViewDropsPendingPressAndItsLaterRelease() {
        AtomicInteger revealCallbacks = new AtomicInteger();
        view.setOnCellRevealedListener(count -> revealCallbacks.incrementAndGet());
        long downTime = pressLastHardCell();
        advanceMainClock(100);
        view.onDetachedFromWindow();
        advanceMainClock(600);
        assertEquals(0, view.getFlaggedCount());
        dispatch(downTime, MotionEvent.ACTION_UP, LAST_CELL_X, LAST_CELL_Y);
        assertEquals("移除 View 后不能保留旧格选择", 0, revealCallbacks.get());
    }

    @Test
    public void shortFirstTapStillRevealsSafeCellsWithoutFlagging() {
        AtomicInteger revealedCount = new AtomicInteger();
        AtomicInteger losses = new AtomicInteger();
        view.setOnCellRevealedListener(revealedCount::set);
        view.setOnGameLoseListener(losses::incrementAndGet);
        long downTime = pressLastHardCell();
        advanceMainClock(100);
        dispatch(downTime, MotionEvent.ACTION_UP, LAST_CELL_X, LAST_CELL_Y);
        // 角落首击及其三个相邻格均属于生产逻辑保证的无雷区。
        assertTrue("首击仍应实际展开安全格", revealedCount.get() >= 4);
        assertEquals("生产首击保护不能退化", 0, losses.get());
        advanceMainClock(600);
        assertEquals("短按抬手必须取消长按任务", 0, view.getFlaggedCount());
    }

    private void assertOutsideTerminationCancelsLongPress(int action) {
        long downTime = pressLastHardCell();
        advanceMainClock(100);
        dispatch(downTime, action, LAST_CELL_X, BELOW_GRID_Y);
        assertEquals(0, view.getFlaggedCount());

        advanceMainClock(600);
        assertEquals("手势已在棋盘外结束，500ms 回调不能再插旗", 0, view.getFlaggedCount());
        assertFalse(view.isGameOver());
    }

    private long pressLastHardCell() {
        long downTime = SystemClock.uptimeMillis();
        dispatch(downTime, MotionEvent.ACTION_DOWN, LAST_CELL_X, LAST_CELL_Y);
        return downTime;
    }

    private void dispatch(long downTime, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                action, x, y, 0);
        try {
            assertTrue("生产 View 应接收触摸事件", view.dispatchTouchEvent(event));
        } finally {
            event.recycle();
        }
    }

    private static void advanceMainClock(long millis) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
    }

    private void assertHardBoardGeometry() {
        assertEquals(MinesweeperView.DIFF_HARD, view.getDifficulty());
        assertEquals(99, view.getMineCount());
        assertEquals(VIEW_WIDTH, view.getWidth());
        assertEquals(VIEW_HEIGHT, view.getHeight());
        // 与生产 onDraw 的居中等宽格几何相同：600 x 640 View 中，困难棋盘为
        // 600 x 320，范围 [0, 160, 600, 480)，每格 20px；下方空白仍属于此 View。
        float cellSize = Math.min((float) view.getWidth() / HARD_COLS,
                (float) view.getHeight() / HARD_ROWS);
        float left = (view.getWidth() - cellSize * HARD_COLS) / 2f;
        float top = (view.getHeight() - cellSize * HARD_ROWS) / 2f;
        RectF board = new RectF(left, top, left + cellSize * HARD_COLS,
                top + cellSize * HARD_ROWS);
        assertEquals(20f, cellSize, 0f);
        assertEquals(160f, board.top, 0f);
        assertEquals(480f, board.bottom, 0f);
        assertTrue(board.contains(LAST_CELL_X, LAST_CELL_Y));
        assertEquals(15, (int) ((LAST_CELL_Y - top) / cellSize));
        assertEquals(29, (int) ((LAST_CELL_X - left) / cellSize));
        assertFalse(board.contains(LAST_CELL_X, BELOW_GRID_Y));
        assertTrue(new RectF(0, 0, view.getWidth(), view.getHeight())
                .contains(LAST_CELL_X, BELOW_GRID_Y));
    }
}
