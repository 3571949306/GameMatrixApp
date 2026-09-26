package com.gamecenter.app.widget;

import static org.junit.Assert.assertEquals;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.Test;

/**
 * 桌面小组件三行文案拼装回归测试（JUnit4；Gradle 的 :app:testDebugUnitTest 与
 * scripts/verify_widget.py 的纯 javac+junit 跑法共用同一份用例）。
 *
 * <p>覆盖 GameMatrixWidgetLines.buildLines（GameMatrixWidgetProvider.buildLines
 * 是它的委托门面，逻辑真源在此）：常规 / 挑战完成 / progress&gt;target 钳制边界 /
 * 时长 0 / 59 秒舍入 / 61 秒进位 / gameName 空。</p>
 */
public class GameMatrixWidgetLinesTest {

    private static final long MINUTE_MS = 60_000L;

    @Test
    public void normalLines() {
        String[] lines = GameMatrixWidgetLines.buildLines(1234, "数独", 3, 5, false, 45 * MINUTE_MS);
        assertEquals("金币 1234", lines[0]);
        assertEquals("挑战：数独 3/5", lines[1]);
        assertEquals("今日 45 分钟", lines[2]);
    }

    @Test
    public void completedChallengeShowsDoneLine() {
        String[] lines = GameMatrixWidgetLines.buildLines(500, "贪吃蛇", 5, 5, true, 150 * MINUTE_MS);
        assertEquals("金币 500", lines[0]);
        assertEquals("今日挑战已完成 ✓", lines[1]);
        assertEquals("今日 150 分钟", lines[2]);
        // completed 状态优先于进度数值：即使 progress < target 也显示完成行
        assertEquals("今日挑战已完成 ✓",
                GameMatrixWidgetLines.buildLines(0, "围棋", 1, 5, true, 0)[1]);
    }

    @Test
    public void progressAboveTargetIsClampedForDisplay() {
        assertEquals("挑战：五子棋 5/5",
                GameMatrixWidgetLines.buildLines(0, "五子棋", 7, 5, false, 0)[1]);
    }

    @Test
    public void zeroPlayTimeShowsZeroMinutes() {
        assertEquals("今日 0 分钟",
                GameMatrixWidgetLines.buildLines(0, "跳棋", 0, 3, false, 0L)[2]);
        // 负值（异常数据）防御：不允许显示负分钟
        assertEquals("今日 0 分钟",
                GameMatrixWidgetLines.buildLines(0, "跳棋", 0, 3, false, -1L)[2]);
    }

    @Test
    public void fiftyNineSecondsFloorsToZeroMinutes() {
        assertEquals("今日 0 分钟",
                GameMatrixWidgetLines.buildLines(0, "跳棋", 0, 3, false, 59_000L)[2]);
    }

    @Test
    public void sixtyOneSecondsCarriesToOneMinute() {
        assertEquals("今日 1 分钟",
                GameMatrixWidgetLines.buildLines(0, "跳棋", 0, 3, false, 61_000L)[2]);
    }

    @Test
    public void emptyGameNameOmitsNameSlot() {
        assertEquals("挑战：2/3",
                GameMatrixWidgetLines.buildLines(0, "", 2, 3, false, 0)[1]);
        assertEquals("挑战：2/3",
                GameMatrixWidgetLines.buildLines(0, null, 2, 3, false, 0)[1]);
    }

    /**
     * scripts/verify_widget.py 的纯 javac 自运行入口：反射执行全部 @Test 用例，
     * 输出与既有 verify 脚本一致的 RESULT 标记（WIDGET_TEST_RESULT=PASS/FAIL）。
     */
    public static void main(String[] args) {
        int failed = 0;
        for (Method method : GameMatrixWidgetLinesTest.class.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(Test.class)) {
                continue;
            }
            try {
                method.invoke(new GameMatrixWidgetLinesTest());
                System.out.println("  PASS  " + method.getName());
            } catch (InvocationTargetException e) {
                failed++;
                System.out.println("  FAIL  " + method.getName() + ": " + e.getCause());
            } catch (ReflectiveOperationException e) {
                failed++;
                System.out.println("  FAIL  " + method.getName() + ": " + e);
            }
        }
        System.out.println("WIDGET_TEST_RESULT=" + (failed == 0 ? "PASS" : "FAIL"));
        if (failed > 0) {
            System.exit(1);
        }
    }
}
