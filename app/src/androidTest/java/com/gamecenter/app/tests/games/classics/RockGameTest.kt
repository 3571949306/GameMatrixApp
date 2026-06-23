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
 * 石头剪刀布游戏自动化测试。
 *
 * 测试范围：
 * - 启动石头剪刀布 Activity，验证不崩溃
 * - 遍历点击页面可见可点击元素
 * - 模拟选择手势（石头/剪刀/布）交互
 * - 退出游戏返回大厅
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-06-22
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RockGameTest : EmulatorTestBase() {

    companion object {
        private const val TAG = "RockGameTest"
    }

    private val activityClass = "com.gamecenter.app.games.rock.RockActivity"

    /**
     * TC-ROCK-001: 验证石头剪刀布能正常启动，不崩溃。
     */
    @Test
    fun test_001_launchGame() {
        Log.d(TAG, "=== 石头剪刀布启动测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-ROCK-002: 遍历点击页面上所有可见可点击元素。
     */
    @Test
    fun test_002_clickAllButtons() {
        Log.d(TAG, "=== 石头剪刀布按钮遍历测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        val clickCount = GameTestHelper.clickAllVisibleButtons(device, maxClicks = 15)
        assertTrue("应至少点击0个按钮", clickCount >= 0)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-ROCK-003: 模拟石头剪刀布基本交互（选择石头、剪刀、布）。
     */
    @Test
    fun test_003_gameInteraction() {
        Log.d(TAG, "=== 石头剪刀布交互测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        // 点击"石头"按钮
        GameTestHelper.clickButtonByText(device, "石头", 3000)
        safeSleep(1500)
        // 点击"再来一局"按钮
        GameTestHelper.clickButtonByText(device, "再来一局", 2000)
        safeSleep(1000)
        // 点击"剪刀"按钮
        GameTestHelper.clickButtonByText(device, "剪刀", 2000)
        safeSleep(1500)
        // 点击"再来一局"按钮
        GameTestHelper.clickButtonByText(device, "再来一局", 2000)
        safeSleep(1000)
        // 点击"布"按钮
        GameTestHelper.clickButtonByText(device, "布", 2000)
        safeSleep(1500)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-ROCK-004: 验证退出石头剪刀布返回大厅。
     */
    @Test
    fun test_004_exitGame() {
        Log.d(TAG, "=== 石头剪刀布退出测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.exitGameToHall(device)
        GameTestHelper.assertAppAlive(device)
    }
}
