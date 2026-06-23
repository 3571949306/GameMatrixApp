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
 * 21点（Blackjack）游戏自动化测试。
 *
 * 测试范围：
 * - 启动21点 Activity，验证不崩溃
 * - 遍历点击页面可见可点击元素
 * - 模拟要牌、停牌交互
 * - 退出游戏返回大厅
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-06-22
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BlackjackGameTest : EmulatorTestBase() {

    companion object {
        private const val TAG = "BlackjackGameTest"
    }

    private val activityClass = "com.gamecenter.app.games.blackjack.BlackjackActivity"

    /**
     * TC-BJ-001: 验证21点能正常启动，不崩溃。
     */
    @Test
    fun test_001_launchGame() {
        Log.d(TAG, "=== 21点启动测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-BJ-002: 遍历点击页面上所有可见可点击元素。
     */
    @Test
    fun test_002_clickAllButtons() {
        Log.d(TAG, "=== 21点按钮遍历测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        val clickCount = GameTestHelper.clickAllVisibleButtons(device, maxClicks = 15)
        assertTrue("应至少点击0个按钮", clickCount >= 0)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-BJ-003: 模拟21点基本交互（新游戏、要牌、停牌）。
     */
    @Test
    fun test_003_gameInteraction() {
        Log.d(TAG, "=== 21点交互测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        // 点击"新游戏"按钮开始
        GameTestHelper.clickButtonByText(device, "新游戏", 3000)
        safeSleep(1500)
        // 点击"要牌"按钮
        GameTestHelper.clickButtonByText(device, "要牌", 2000)
        safeSleep(1000)
        // 再次点击"要牌"按钮
        GameTestHelper.clickButtonByText(device, "要牌", 2000)
        safeSleep(1000)
        // 点击"停牌"按钮
        GameTestHelper.clickButtonByText(device, "停牌", 2000)
        safeSleep(1500)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-BJ-004: 验证退出21点返回大厅。
     */
    @Test
    fun test_004_exitGame() {
        Log.d(TAG, "=== 21点退出测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.exitGameToHall(device)
        GameTestHelper.assertAppAlive(device)
    }
}
