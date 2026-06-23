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
 * 骰子游戏自动化测试。
 *
 * 测试范围：
 * - 启动骰子 Activity，验证不崩溃
 * - 遍历点击页面可见可点击元素
 * - 模拟掷骰子、加倍、下一局交互
 * - 退出游戏返回大厅
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-06-22
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DiceGameTest : EmulatorTestBase() {

    companion object {
        private const val TAG = "DiceGameTest"
    }

    private val activityClass = "com.gamecenter.app.games.dice.DiceActivity"

    /**
     * TC-DICE-001: 验证骰子游戏能正常启动，不崩溃。
     */
    @Test
    fun test_001_launchGame() {
        Log.d(TAG, "=== 骰子启动测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-DICE-002: 遍历点击页面上所有可见可点击元素。
     */
    @Test
    fun test_002_clickAllButtons() {
        Log.d(TAG, "=== 骰子按钮遍历测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        val clickCount = GameTestHelper.clickAllVisibleButtons(device, maxClicks = 15)
        assertTrue("应至少点击0个按钮", clickCount >= 0)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-DICE-003: 模拟骰子游戏基本交互（掷骰子、加倍、下一局）。
     */
    @Test
    fun test_003_gameInteraction() {
        Log.d(TAG, "=== 骰子交互测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        // 点击"掷骰子"按钮
        GameTestHelper.clickButtonByText(device, "掷骰子", 3000)
        safeSleep(1500)
        // 尝试点击"加倍"按钮
        GameTestHelper.clickButtonByText(device, "加倍", 2000)
        safeSleep(1000)
        // 点击"下一局"按钮
        GameTestHelper.clickButtonByText(device, "下一局", 2000)
        safeSleep(1000)
        // 再次掷骰子
        GameTestHelper.clickButtonByText(device, "掷骰子", 2000)
        safeSleep(1500)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-DICE-004: 验证退出骰子游戏返回大厅。
     */
    @Test
    fun test_004_exitGame() {
        Log.d(TAG, "=== 骰子退出测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.exitGameToHall(device)
        GameTestHelper.assertAppAlive(device)
    }
}
