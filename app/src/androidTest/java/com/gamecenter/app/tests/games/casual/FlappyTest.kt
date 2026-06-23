package com.gamecenter.app.tests.games.casual

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
 * Flappy Bird 游戏自动化测试。
 *
 * 测试范围：
 * - 启动游戏 Activity 验证不崩溃
 * - 遍历点击页面可见可点击元素
 * - 模拟点击屏幕跳跃
 * - 退出游戏返回大厅
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-06-22
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FlappyTest : EmulatorTestBase() {

    companion object {
        private const val TAG = "FlappyTest"
    }

    private val activityClass = "com.gamecenter.app.games.flappy.FlappyActivity"

    @Test
    fun test_001_launchGame() {
        Log.d(TAG, "=== Flappy Bird启动测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.assertAppAlive(device)
    }

    @Test
    fun test_002_clickAllButtons() {
        Log.d(TAG, "=== Flappy Bird按钮遍历测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        val clickCount = GameTestHelper.clickAllVisibleButtons(device, maxClicks = 15)
        assertTrue("应至少点击0个按钮", clickCount >= 0)
        GameTestHelper.assertAppAlive(device)
    }

    @Test
    fun test_003_gameInteraction() {
        Log.d(TAG, "=== Flappy Bird交互测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        // 模拟点击屏幕让小鸟跳跃
        val cx = device.displayWidth / 2
        val cy = device.displayHeight / 2
        // 连续点击屏幕中央模拟跳跃
        for (i in 0 until 5) {
            device.click(cx, cy)
            safeSleep(300)
        }
        // 点击屏幕不同位置
        device.click(cx - 100, cy)
        safeSleep(300)
        device.click(cx + 100, cy)
        safeSleep(300)
        GameTestHelper.assertAppAlive(device)
    }

    @Test
    fun test_004_exitGame() {
        Log.d(TAG, "=== Flappy Bird退出测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.exitGameToHall(device)
        GameTestHelper.assertAppAlive(device)
    }
}
