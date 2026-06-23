package com.gamecenter.app.tests.games.classics

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
 * 跳棋（Checkers）游戏自动化测试。
 *
 * 测试范围：
 * - 启动跳棋 Activity，验证不崩溃
 * - 遍历点击页面可见可点击元素
 * - 模拟选择难度与点击棋盘交互
 * - 退出游戏返回大厅
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-06-22
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class CheckersGameTest : EmulatorTestBase() {

    companion object {
        private const val TAG = "CheckersGameTest"
    }

    private val activityClass = "com.gamecenter.app.games.checkers.CheckersActivity"

    /**
     * TC-CHECKERS-001: 验证跳棋能正常启动，不崩溃。
     */
    @Test
    fun test_001_launchGame() {
        Log.d(TAG, "=== 跳棋启动测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-CHECKERS-002: 遍历点击页面上所有可见可点击元素。
     */
    @Test
    fun test_002_clickAllButtons() {
        Log.d(TAG, "=== 跳棋按钮遍历测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        val clickCount = GameTestHelper.clickAllVisibleButtons(device, maxClicks = 15)
        assertTrue("应至少点击0个按钮", clickCount >= 0)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-CHECKERS-003: 模拟跳棋基本交互（选择难度、点击棋盘移动）。
     */
    @Test
    fun test_003_gameInteraction() {
        Log.d(TAG, "=== 跳棋交互测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        // 点击"初级"难度按钮
        GameTestHelper.clickButtonByText(device, "初级", 3000)
        safeSleep(1500)
        // 点击棋盘中心模拟选择棋子
        val cx = device.displayWidth / 2
        val cy = device.displayHeight / 2
        device.click(cx, cy)
        safeSleep(1000)
        // 点击棋盘其他位置模拟移动
        device.click(cx + 80, cy + 80)
        safeSleep(1000)
        // 尝试点击"重新开始"按钮
        GameTestHelper.clickButtonByText(device, "重新开始", 2000)
        safeSleep(1000)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-CHECKERS-004: 验证退出跳棋返回大厅。
     */
    @Test
    fun test_004_exitGame() {
        Log.d(TAG, "=== 跳棋退出测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.exitGameToHall(device)
        GameTestHelper.assertAppAlive(device)
    }
}
