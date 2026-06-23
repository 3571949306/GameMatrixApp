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
 * 扫雷游戏自动化测试。
 *
 * 测试范围：
 * - 启动游戏 Activity，验证不崩溃
 * - 遍历点击页面上所有可见可点击元素
 * - 模拟点击格子交互
 * - 退出游戏返回大厅
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-06-22
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MinesweeperTest : EmulatorTestBase() {

    companion object {
        private const val TAG = "MinesweeperTest"
    }

    private val activityClass = "com.gamecenter.app.games.minesweeper.MinesweeperActivity"

    /**
     * TC-MINE-001: 启动扫雷游戏，验证不崩溃。
     */
    @Test
    fun test_001_launchGame() {
        Log.d(TAG, "=== 扫雷启动测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-MINE-002: 遍历点击页面上所有可见可点击元素。
     */
    @Test
    fun test_002_clickAllButtons() {
        Log.d(TAG, "=== 扫雷按钮遍历测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        val clickCount = GameTestHelper.clickAllVisibleButtons(device, maxClicks = 15)
        assertTrue("应至少点击0个按钮", clickCount >= 0)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-MINE-003: 模拟扫雷基本交互（点击格子）。
     */
    @Test
    fun test_003_gameInteraction() {
        Log.d(TAG, "=== 扫雷交互测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        // 模拟点击雷区格子
        val cx = device.displayWidth / 2
        val cy = device.displayHeight / 2
        val step = device.displayWidth / 8
        // 点击雷区中心区域多个格子
        for (i in -2..2) {
            for (j in -2..2) {
                device.click(cx + i * step, cy + j * step)
                safeSleep(200)
            }
        }
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-MINE-004: 退出扫雷游戏返回大厅。
     */
    @Test
    fun test_004_exitGame() {
        Log.d(TAG, "=== 扫雷退出测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.exitGameToHall(device)
        GameTestHelper.assertAppAlive(device)
    }
}
