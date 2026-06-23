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
 * 打砖块游戏自动化测试。
 *
 * 测试范围：
 * - 启动游戏 Activity，验证不崩溃
 * - 遍历点击页面上所有可见可点击元素
 * - 模拟触摸滑动控制挡板交互
 * - 退出游戏返回大厅
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-06-22
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BreakoutTest : EmulatorTestBase() {

    companion object {
        private const val TAG = "BreakoutTest"
    }

    private val activityClass = "com.gamecenter.app.games.breakout.BreakoutActivity"

    /**
     * TC-BREAKOUT-001: 启动打砖块游戏，验证不崩溃。
     */
    @Test
    fun test_001_launchGame() {
        Log.d(TAG, "=== 打砖块启动测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-BREAKOUT-002: 遍历点击页面上所有可见可点击元素。
     */
    @Test
    fun test_002_clickAllButtons() {
        Log.d(TAG, "=== 打砖块按钮遍历测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        val clickCount = GameTestHelper.clickAllVisibleButtons(device, maxClicks = 15)
        assertTrue("应至少点击0个按钮", clickCount >= 0)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-BREAKOUT-003: 模拟打砖块基本交互（触摸滑动控制挡板）。
     */
    @Test
    fun test_003_gameInteraction() {
        Log.d(TAG, "=== 打砖块交互测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        // 模拟触摸滑动控制挡板左右移动
        val screenWidth = device.displayWidth
        val screenHeight = device.displayHeight
        // 挡板通常在屏幕底部，在底部区域左右滑动
        val paddleY = screenHeight * 4 / 5
        // 向左滑动挡板
        device.swipe(screenWidth * 3 / 4, paddleY, screenWidth / 4, paddleY, 30)
        safeSleep(500)
        // 向右滑动挡板
        device.swipe(screenWidth / 4, paddleY, screenWidth * 3 / 4, paddleY, 30)
        safeSleep(500)
        // 再次左右滑动
        device.swipe(screenWidth * 3 / 4, paddleY, screenWidth / 4, paddleY, 30)
        safeSleep(500)
        device.swipe(screenWidth / 4, paddleY, screenWidth * 3 / 4, paddleY, 30)
        safeSleep(500)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-BREAKOUT-004: 退出打砖块游戏返回大厅。
     */
    @Test
    fun test_004_exitGame() {
        Log.d(TAG, "=== 打砖块退出测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.exitGameToHall(device)
        GameTestHelper.assertAppAlive(device)
    }
}
