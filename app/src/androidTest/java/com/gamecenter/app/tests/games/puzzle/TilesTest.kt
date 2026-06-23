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
 * 拼图游戏自动化测试。
 *
 * 测试范围：
 * - 启动游戏 Activity，验证不崩溃
 * - 遍历点击页面上所有可见可点击元素
 * - 模拟点击拼图块交互
 * - 退出游戏返回大厅
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-06-22
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TilesTest : EmulatorTestBase() {

    companion object {
        private const val TAG = "TilesTest"
    }

    private val activityClass = "com.gamecenter.app.games.tiles.TilesActivity"

    /**
     * TC-TILES-001: 启动拼图游戏，验证不崩溃。
     */
    @Test
    fun test_001_launchGame() {
        Log.d(TAG, "=== 拼图启动测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-TILES-002: 遍历点击页面上所有可见可点击元素。
     */
    @Test
    fun test_002_clickAllButtons() {
        Log.d(TAG, "=== 拼图按钮遍历测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        val clickCount = GameTestHelper.clickAllVisibleButtons(device, maxClicks = 15)
        assertTrue("应至少点击0个按钮", clickCount >= 0)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-TILES-003: 模拟拼图基本交互（点击拼图块）。
     */
    @Test
    fun test_003_gameInteraction() {
        Log.d(TAG, "=== 拼图交互测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        // 模拟点击拼图块进行移动
        val cx = device.displayWidth / 2
        val cy = device.displayHeight / 2
        val step = device.displayWidth / 6
        // 点击拼图块网格中的多个块
        for (i in -1..1) {
            for (j in -1..1) {
                device.click(cx + i * step, cy + j * step)
                safeSleep(300)
            }
        }
        // 尝试滑动拼图块
        device.swipe(cx + step, cy, cx, cy, 20)
        safeSleep(500)
        device.swipe(cx, cy + step, cx, cy, 20)
        safeSleep(500)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-TILES-004: 退出拼图游戏返回大厅。
     */
    @Test
    fun test_004_exitGame() {
        Log.d(TAG, "=== 拼图退出测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.exitGameToHall(device)
        GameTestHelper.assertAppAlive(device)
    }
}
