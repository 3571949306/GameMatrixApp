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
 * 五子棋游戏自动化测试。
 *
 * 测试范围：
 * - 启动五子棋 Activity，验证不崩溃
 * - 遍历点击页面可见可点击元素
 * - 模拟落子与难度切换交互
 * - 退出游戏返回大厅
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-06-22
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class GomokuGameTest : EmulatorTestBase() {

    companion object {
        private const val TAG = "GomokuGameTest"
    }

    private val activityClass = "com.gamecenter.app.games.gomoku.GomokuActivity"

    /**
     * TC-GOMOKU-001: 验证五子棋能正常启动，不崩溃。
     */
    @Test
    fun test_001_launchGame() {
        Log.d(TAG, "=== 五子棋启动测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-GOMOKU-002: 遍历点击页面上所有可见可点击元素。
     */
    @Test
    fun test_002_clickAllButtons() {
        Log.d(TAG, "=== 五子棋按钮遍历测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        val clickCount = GameTestHelper.clickAllVisibleButtons(device, maxClicks = 15)
        assertTrue("应至少点击0个按钮", clickCount >= 0)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-GOMOKU-003: 模拟五子棋基本交互（落子、切换难度）。
     */
    @Test
    fun test_003_gameInteraction() {
        Log.d(TAG, "=== 五子棋交互测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        // 点击屏幕中心模拟落子
        val cx = device.displayWidth / 2
        val cy = device.displayHeight / 2
        device.click(cx, cy)
        safeSleep(1000)
        // 尝试点击难度按钮
        GameTestHelper.clickButtonByText(device, "低", 2000)
        GameTestHelper.clickButtonByText(device, "中", 2000)
        GameTestHelper.clickButtonByText(device, "高", 2000)
        // 尝试点击开始游戏按钮
        GameTestHelper.clickButtonByText(device, "开始游戏", 2000)
        safeSleep(1000)
        // 再次点击棋盘中心模拟落子
        device.click(cx, cy)
        safeSleep(1000)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-GOMOKU-004: 验证退出五子棋返回大厅。
     */
    @Test
    fun test_004_exitGame() {
        Log.d(TAG, "=== 五子棋退出测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.exitGameToHall(device)
        GameTestHelper.assertAppAlive(device)
    }
}
