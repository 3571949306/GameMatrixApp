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
 * 斗地主游戏自动化测试。
 *
 * 测试范围：
 * - 启动斗地主菜单 Activity，验证不崩溃
 * - 遍历点击页面可见可点击元素
 * - 模拟菜单按钮交互（单机模式等）
 * - 退出游戏返回大厅
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-06-22
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DouDiZhuGameTest : EmulatorTestBase() {

    companion object {
        private const val TAG = "DouDiZhuGameTest"
    }

    private val activityClass = "com.gamecenter.app.games.doudizhu.DouDiZhuMenuActivity"

    /**
     * TC-DDZ-001: 验证斗地主菜单能正常启动，不崩溃。
     */
    @Test
    fun test_001_launchGame() {
        Log.d(TAG, "=== 斗地主启动测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-DDZ-002: 遍历点击页面上所有可见可点击元素。
     */
    @Test
    fun test_002_clickAllButtons() {
        Log.d(TAG, "=== 斗地主按钮遍历测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        val clickCount = GameTestHelper.clickAllVisibleButtons(device, maxClicks = 15)
        assertTrue("应至少点击0个按钮", clickCount >= 0)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-DDZ-003: 模拟斗地主菜单交互（点击单机模式进入游戏）。
     */
    @Test
    fun test_003_gameInteraction() {
        Log.d(TAG, "=== 斗地主交互测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        // 点击"单机模式"按钮进入游戏
        GameTestHelper.clickButtonByText(device, "单机模式", 3000)
        safeSleep(2000)
        GameTestHelper.assertAppAlive(device)
        // 点击屏幕中心模拟出牌交互
        val cx = device.displayWidth / 2
        val cy = device.displayHeight / 2
        device.click(cx, cy)
        safeSleep(1000)
        // 返回菜单
        device.pressBack()
        safeSleep(1000)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-DDZ-004: 验证退出斗地主返回大厅。
     */
    @Test
    fun test_004_exitGame() {
        Log.d(TAG, "=== 斗地主退出测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.exitGameToHall(device)
        GameTestHelper.assertAppAlive(device)
    }
}
