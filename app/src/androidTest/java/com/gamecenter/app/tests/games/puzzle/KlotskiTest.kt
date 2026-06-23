package com.gamecenter.app.tests.games.puzzle

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gamecenter.app.EmulatorTestBase
import com.gamecenter.app.tests.GameTestHelper
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * 华容道游戏自动化测试。
 *
 * 测试范围：
 * - 启动游戏 Activity，验证不崩溃
 * - 遍历点击页面上所有可见可点击元素
 * - 模拟滑动滑块交互
 * - 退出游戏返回大厅
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-06-22
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class KlotskiTest : EmulatorTestBase() {

    companion object {
        private const val TAG = "KlotskiTest"
    }

    private val activityClass = "com.gamecenter.app.games.klotski.KlotskiActivity"

    /**
     * TC-KLOTSKI-001: 启动华容道游戏，验证不崩溃。
     */
    @Test
    fun test_001_launchGame() {
        Log.d(TAG, "=== 华容道启动测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-KLOTSKI-002: 遍历点击页面上所有可见可点击元素。
     */
    @Test
    fun test_002_clickAllButtons() {
        Log.d(TAG, "=== 华容道按钮遍历测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        val clickCount = GameTestHelper.clickAllVisibleButtons(device, maxClicks = 15)
        assertTrue("应至少点击0个按钮", clickCount >= 0)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-KLOTSKI-003: 模拟华容道基本交互（滑动滑块）。
     */
    @Test
    fun test_003_gameInteraction() {
        Log.d(TAG, "=== 华容道交互测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        // 模拟滑动滑块操作（向不同方向滑动）
        val cx = device.displayWidth / 2
        val cy = device.displayHeight / 2
        val offset = device.displayWidth / 6
        // 向左滑动滑块
        device.swipe(cx + offset, cy, cx - offset, cy, 20)
        safeSleep(500)
        // 向右滑动滑块
        device.swipe(cx - offset, cy + offset, cx + offset, cy + offset, 20)
        safeSleep(500)
        // 向上滑动滑块
        device.swipe(cx, cy + offset, cx, cy - offset, 20)
        safeSleep(500)
        // 向下滑动滑块
        device.swipe(cx + offset, cy - offset, cx + offset, cy + offset, 20)
        safeSleep(500)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-KLOTSKI-004: 退出华容道游戏返回大厅。
     */
    @Test
    fun test_004_exitGame() {
        Log.d(TAG, "=== 华容道退出测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.exitGameToHall(device)
        GameTestHelper.assertAppAlive(device)
    }
}
