package com.gamecenter.app;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Before;
import org.junit.runner.RunWith;

/**
 * Android 测试基类。
 *
 * <p>提供所有 androidTest 共享的基础设施：
 * <ul>
 *   <li>Application Context 获取</li>
 *   <li>通用断言方法</li>
 *   <li>测试生命周期管理</li>
 * </ul>
 *
 * <p>所有 androidTest 类应继承此基类。
 *
 * @author Software Engineer (Alex)
 * @version 1.0
 * @since 2026-05-27
 */
@RunWith(AndroidJUnit4.class)
public abstract class BaseAndroidTest {

    /** Application Context */
    protected Context context;

    /** 测试超时（毫秒） */
    protected static final long DEFAULT_TIMEOUT_MS = 5000;

    /**
     * 测试初始化。
     *
     * <p>在每个测试方法执行前调用，获取 Application Context。
     */
    @Before
    public void baseSetUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    /**
     * 断言两个整数相等（带消息）。
     *
     * @param message  失败消息
     * @param expected 期望值
     * @param actual   实际值
     */
    protected void assertEq(String message, int expected, int actual) {
        if (expected != actual) {
            throw new AssertionError(
                    message + " - expected: " + expected + ", actual: " + actual);
        }
    }

    /**
     * 断言条件为真（带消息）。
     *
     * @param message  失败消息
     * @param condition 条件
     */
    protected void assertTrue(String message, boolean condition) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /**
     * 断言条件为假（带消息）。
     *
     * @param message  失败消息
     * @param condition 条件
     */
    protected void assertFalse(String message, boolean condition) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    /**
     * 断言对象非空（带消息）。
     *
     * @param message 失败消息
     * @param object  待检查对象
     * @param <T>     对象类型
     * @return 对象本身（非空）
     */
    protected <T> T assertNotNull(String message, T object) {
        if (object == null) {
            throw new AssertionError(message);
        }
        return object;
    }

    /**
     * 断言两个字符串相等（带消息）。
     *
     * @param message  失败消息
     * @param expected 期望值
     * @param actual   实际值
     */
    protected void assertEq(String message, String expected, String actual) {
        if (expected == null && actual == null) {
            return;
        }
        if (expected == null || !expected.equals(actual)) {
            throw new AssertionError(
                    message + " - expected: " + expected + ", actual: " + actual);
        }
    }

    /**
     * 安全等待指定时间。
     *
     * @param ms 等待时间（毫秒）
     */
    protected void safeSleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
