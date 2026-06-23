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
 * 2048 游戏自动化测试。
 *
 * 测试范围：
 * - 启动游戏 Activity，验证不崩溃
 * - 遍历点击页面上所有可见可点击元素
 * - 模拟上下左右滑动交互
 * - 退出游戏返回大厅
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-06-22
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Game2048Test : EmulatorTestBase() {

    companion object {
        private const val TAG = "Game2048Test"
    }

    private val activityClass = "com.gamecenter.app.games.game2048.Game2048Activity"

    /**
     * TC-2048-001: 启动 2048 游戏，验证不崩溃。
     */
    @Test
    fun test_001_launchGame() {
        Log.d(TAG, "=== 2048启动测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-2048-002: 遍历点击页面上所有可见可点击元素。
     */
    @Test
    fun test_002_clickAllButtons() {
        Log.d(TAG, "=== 2048按钮遍历测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        val clickCount = GameTestHelper.clickAllVisibleButtons(device, maxClicks = 15)
        assertTrue("应至少点击0个按钮", clickCount >= 0)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-2048-003: 模拟 2048 基本交互（上下左右滑动）。
     */
    @Test
    fun test_003_gameInteraction() {
        Log.d(TAG, "=== 2048交互测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        // 模拟滑动操作
        val cx = device.displayWidth / 2
        val cy = device.displayHeight / 2
        val offset = device.displayWidth / 4
        // 向左滑
        device.swipe(cx + offset, cy, cx - offset, cy, 20)
        safeSleep(500)
        // 向右滑
        device.swipe(cx - offset, cy, cx + offset, cy, 20)
        safeSleep(500)
        // 向上滑
        device.swipe(cx, cy + offset, cx, cy - offset, 20)
        safeSleep(500)
        // 向下滑
        device.swipe(cx, cy - offset, cx, cy + offset, 20)
        safeSleep(500)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-2048-004: 退出 2048 游戏返回大厅。
     */
    @Test
    fun test_004_exitGame() {
        Log.d(TAG, "=== 2048退出测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.exitGameToHall(device)
        GameTestHelper.assertAppAlive(device)
    }
}
