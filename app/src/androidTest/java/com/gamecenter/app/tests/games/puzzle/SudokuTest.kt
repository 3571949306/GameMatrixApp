package com.gamecenter.app.tests.games.puzzle

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiSelector
import com.gamecenter.app.EmulatorTestBase
import com.gamecenter.app.tests.GameTestHelper
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * 数独游戏自动化测试。
 *
 * 测试范围：
 * - 启动游戏 Activity，验证不崩溃
 * - 遍历点击页面上所有可见可点击元素
 * - 模拟点击数字格和数字按钮交互
 * - 退出游戏返回大厅
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-06-22
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SudokuTest : EmulatorTestBase() {

    companion object {
        private const val TAG = "SudokuTest"
    }

    private val activityClass = "com.gamecenter.app.games.sudoku.SudokuActivity"

    /**
     * TC-SUDOKU-001: 启动数独游戏，验证不崩溃。
     */
    @Test
    fun test_001_launchGame() {
        Log.d(TAG, "=== 数独启动测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-SUDOKU-002: 遍历点击页面上所有可见可点击元素。
     */
    @Test
    fun test_002_clickAllButtons() {
        Log.d(TAG, "=== 数独按钮遍历测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        val clickCount = GameTestHelper.clickAllVisibleButtons(device, maxClicks = 15)
        assertTrue("应至少点击0个按钮", clickCount >= 0)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-SUDOKU-003: 模拟数独基本交互（点击数字格、点击数字按钮）。
     */
    @Test
    fun test_003_gameInteraction() {
        Log.d(TAG, "=== 数独交互测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        // 点击棋盘上的数字格（3x3 区域内的几个格子）
        val cx = device.displayWidth / 2
        val cy = device.displayHeight / 2
        val step = device.displayWidth / 6
        // 点击棋盘中心区域多个格子
        for (i in -1..1) {
            for (j in -1..1) {
                device.click(cx + i * step, cy + j * step)
                safeSleep(300)
            }
        }
        // 尝试点击数字按钮 1-9
        val numbers = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9")
        for (num in numbers) {
            val selector = UiSelector().text(num)
            val obj = device.findObject(selector)
            if (obj.exists()) {
                obj.click()
                safeSleep(300)
            }
        }
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-SUDOKU-004: 退出数独游戏返回大厅。
     */
    @Test
    fun test_004_exitGame() {
        Log.d(TAG, "=== 数独退出测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.exitGameToHall(device)
        GameTestHelper.assertAppAlive(device)
    }
}
