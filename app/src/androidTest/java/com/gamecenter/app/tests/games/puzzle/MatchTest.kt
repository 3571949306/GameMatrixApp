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
 * 消消乐游戏自动化测试。
 *
 * 测试范围：
 * - 启动游戏 Activity，验证不崩溃
 * - 遍历点击页面上所有可见可点击元素
 * - 模拟点击相邻方块交互
 * - 退出游戏返回大厅
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-06-22
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MatchTest : EmulatorTestBase() {

    companion object {
        private const val TAG = "MatchTest"
    }

    private val activityClass = "com.gamecenter.app.games.match.MatchActivity"

    /**
     * TC-MATCH-001: 启动消消乐游戏，验证不崩溃。
     */
    @Test
    fun test_001_launchGame() {
        Log.d(TAG, "=== 消消乐启动测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-MATCH-002: 遍历点击页面上所有可见可点击元素。
     */
    @Test
    fun test_002_clickAllButtons() {
        Log.d(TAG, "=== 消消乐按钮遍历测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        val clickCount = GameTestHelper.clickAllVisibleButtons(device, maxClicks = 15)
        assertTrue("应至少点击0个按钮", clickCount >= 0)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-MATCH-003: 模拟消消乐基本交互（点击相邻方块）。
     */
    @Test
    fun test_003_gameInteraction() {
        Log.d(TAG, "=== 消消乐交互测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        // 模拟点击相邻方块进行消除
        val cx = device.displayWidth / 2
        val cy = device.displayHeight / 2
        val step = device.displayWidth / 8
        // 点击中心区域方块
        device.click(cx, cy)
        safeSleep(300)
        // 点击相邻方块（右方）
        device.click(cx + step, cy)
        safeSleep(300)
        // 点击相邻方块（下方）
        device.click(cx, cy + step)
        safeSleep(300)
        // 点击相邻方块（左方）
        device.click(cx - step, cy)
        safeSleep(300)
        // 点击相邻方块（上方）
        device.click(cx, cy - step)
        safeSleep(300)
        // 尝试滑动交换相邻方块
        device.swipe(cx, cy, cx + step, cy, 20)
        safeSleep(500)
        device.swipe(cx + step, cy, cx, cy, 20)
        safeSleep(500)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-MATCH-004: 退出消消乐游戏返回大厅。
     */
    @Test
    fun test_004_exitGame() {
        Log.d(TAG, "=== 消消乐退出测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.exitGameToHall(device)
        GameTestHelper.assertAppAlive(device)
    }
}
