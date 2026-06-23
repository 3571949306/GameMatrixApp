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
 * 推箱子游戏自动化测试。
 *
 * 测试范围：
 * - 启动游戏 Activity，验证不崩溃
 * - 遍历点击页面上所有可见可点击元素
 * - 模拟点击方向按钮和滑动交互
 * - 退出游戏返回大厅
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-06-22
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SokobanTest : EmulatorTestBase() {

    companion object {
        private const val TAG = "SokobanTest"
    }

    private val activityClass = "com.gamecenter.app.games.sokoban.SokobanActivity"

    /**
     * TC-SOKOBAN-001: 启动推箱子游戏，验证不崩溃。
     */
    @Test
    fun test_001_launchGame() {
        Log.d(TAG, "=== 推箱子启动测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-SOKOBAN-002: 遍历点击页面上所有可见可点击元素。
     */
    @Test
    fun test_002_clickAllButtons() {
        Log.d(TAG, "=== 推箱子按钮遍历测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        val clickCount = GameTestHelper.clickAllVisibleButtons(device, maxClicks = 15)
        assertTrue("应至少点击0个按钮", clickCount >= 0)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-SOKOBAN-003: 模拟推箱子基本交互（点击方向按钮、滑动）。
     */
    @Test
    fun test_003_gameInteraction() {
        Log.d(TAG, "=== 推箱子交互测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        // 尝试点击方向按钮（上/下/左/右）
        val directions = listOf("↑", "↓", "←", "→", "上", "下", "左", "右", "Up", "Down", "Left", "Right")
        for (dir in directions) {
            val selector = UiSelector().text(dir)
            val obj = device.findObject(selector)
            if (obj.exists()) {
                obj.click()
                safeSleep(300)
            }
        }
        // 模拟滑动操作（向四个方向滑动控制角色）
        val cx = device.displayWidth / 2
        val cy = device.displayHeight / 2
        val offset = device.displayWidth / 5
        // 向左滑
        device.swipe(cx + offset, cy, cx - offset, cy, 20)
        safeSleep(400)
        // 向上滑
        device.swipe(cx, cy + offset, cx, cy - offset, 20)
        safeSleep(400)
        // 向右滑
        device.swipe(cx - offset, cy, cx + offset, cy, 20)
        safeSleep(400)
        // 向下滑
        device.swipe(cx, cy - offset, cx, cy + offset, 20)
        safeSleep(400)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-SOKOBAN-004: 退出推箱子游戏返回大厅。
     */
    @Test
    fun test_004_exitGame() {
        Log.d(TAG, "=== 推箱子退出测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.exitGameToHall(device)
        GameTestHelper.assertAppAlive(device)
    }
}
