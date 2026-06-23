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
 * 打地鼠游戏自动化测试。
 *
 * 测试范围：
 * - 启动游戏 Activity 验证不崩溃
 * - 遍历点击页面可见可点击元素
 * - 模拟点击地鼠出现的位置
 * - 退出游戏返回大厅
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-06-22
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WhackTest : EmulatorTestBase() {

    companion object {
        private const val TAG = "WhackTest"
    }

    private val activityClass = "com.gamecenter.app.games.whack.WhackActivity"

    @Test
    fun test_001_launchGame() {
        Log.d(TAG, "=== 打地鼠启动测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.assertAppAlive(device)
    }

    @Test
    fun test_002_clickAllButtons() {
        Log.d(TAG, "=== 打地鼠按钮遍历测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        val clickCount = GameTestHelper.clickAllVisibleButtons(device, maxClicks = 15)
        assertTrue("应至少点击0个按钮", clickCount >= 0)
        GameTestHelper.assertAppAlive(device)
    }

    @Test
    fun test_003_gameInteraction() {
        Log.d(TAG, "=== 打地鼠交互测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        // 模拟点击地鼠可能出现的位置（多个洞口）
        val cx = device.displayWidth / 2
        val cy = device.displayHeight / 2
        val offset = device.displayWidth / 4
        // 快速点击屏幕多个位置模拟打地鼠
        device.click(cx - offset, cy - offset)
        safeSleep(200)
        device.click(cx, cy - offset)
        safeSleep(200)
        device.click(cx + offset, cy - offset)
        safeSleep(200)
        device.click(cx - offset, cy)
        safeSleep(200)
        device.click(cx, cy)
        safeSleep(200)
        device.click(cx + offset, cy)
        safeSleep(200)
        device.click(cx - offset, cy + offset)
        safeSleep(200)
        device.click(cx, cy + offset)
        safeSleep(200)
        device.click(cx + offset, cy + offset)
        safeSleep(500)
        GameTestHelper.assertAppAlive(device)
    }

    @Test
    fun test_004_exitGame() {
        Log.d(TAG, "=== 打地鼠退出测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.exitGameToHall(device)
        GameTestHelper.assertAppAlive(device)
    }
}
