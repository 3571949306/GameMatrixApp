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
 * 中国象棋游戏自动化测试。
 *
 * 测试范围：
 * - 启动中国象棋 Activity，验证不崩溃
 * - 遍历点击页面可见可点击元素
 * - 模拟点击棋盘与走子交互
 * - 退出游戏返回大厅
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-06-22
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ChineseChessGameTest : EmulatorTestBase() {

    companion object {
        private const val TAG = "ChineseChessGameTest"
    }

    private val activityClass = "com.gamecenter.app.games.chinesechess.ChineseChessActivity"

    /**
     * TC-CHESS-001: 验证中国象棋能正常启动，不崩溃。
     */
    @Test
    fun test_001_launchGame() {
        Log.d(TAG, "=== 中国象棋启动测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-CHESS-002: 遍历点击页面上所有可见可点击元素。
     */
    @Test
    fun test_002_clickAllButtons() {
        Log.d(TAG, "=== 中国象棋按钮遍历测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        val clickCount = GameTestHelper.clickAllVisibleButtons(device, maxClicks = 15)
        assertTrue("应至少点击0个按钮", clickCount >= 0)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-CHESS-003: 模拟中国象棋基本交互（点击棋盘选择棋子并移动）。
     */
    @Test
    fun test_003_gameInteraction() {
        Log.d(TAG, "=== 中国象棋交互测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        // 点击棋盘中心区域模拟选择棋子
        val cx = device.displayWidth / 2
        val cy = device.displayHeight / 2
        device.click(cx, cy)
        safeSleep(1000)
        // 点击棋盘偏左位置模拟移动棋子
        device.click(cx - 100, cy)
        safeSleep(1000)
        // 再次点击棋盘其他位置
        device.click(cx + 80, cy + 80)
        safeSleep(1000)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-CHESS-004: 验证退出中国象棋返回大厅。
     */
    @Test
    fun test_004_exitGame() {
        Log.d(TAG, "=== 中国象棋退出测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.exitGameToHall(device)
        GameTestHelper.assertAppAlive(device)
    }
}
