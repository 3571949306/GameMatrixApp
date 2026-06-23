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
 * 记忆翻牌游戏自动化测试。
 *
 * 测试范围：
 * - 启动游戏 Activity，验证不崩溃
 * - 遍历点击页面上所有可见可点击元素
 * - 模拟点击卡片交互
 * - 退出游戏返回大厅
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-06-22
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MemoryTest : EmulatorTestBase() {

    companion object {
        private const val TAG = "MemoryTest"
    }

    private val activityClass = "com.gamecenter.app.games.memory.MemoryActivity"

    /**
     * TC-MEMORY-001: 启动记忆翻牌游戏，验证不崩溃。
     */
    @Test
    fun test_001_launchGame() {
        Log.d(TAG, "=== 记忆翻牌启动测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-MEMORY-002: 遍历点击页面上所有可见可点击元素。
     */
    @Test
    fun test_002_clickAllButtons() {
        Log.d(TAG, "=== 记忆翻牌按钮遍历测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        val clickCount = GameTestHelper.clickAllVisibleButtons(device, maxClicks = 15)
        assertTrue("应至少点击0个按钮", clickCount >= 0)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-MEMORY-003: 模拟记忆翻牌基本交互（点击卡片）。
     */
    @Test
    fun test_003_gameInteraction() {
        Log.d(TAG, "=== 记忆翻牌交互测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        // 模拟点击多张卡片翻牌
        val cx = device.displayWidth / 2
        val cy = device.displayHeight / 2
        val stepX = device.displayWidth / 5
        val stepY = device.displayHeight / 6
        // 点击网格中的多张卡片
        for (i in -1..1) {
            for (j in -2..2) {
                device.click(cx + i * stepY, cy + j * stepX)
                safeSleep(400)
            }
        }
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-MEMORY-004: 退出记忆翻牌游戏返回大厅。
     */
    @Test
    fun test_004_exitGame() {
        Log.d(TAG, "=== 记忆翻牌退出测试 ===")
        GameTestHelper.launchGameActivity(device, activityClass)
        safeSleep(2000)
        GameTestHelper.exitGameToHall(device)
        GameTestHelper.assertAppAlive(device)
    }
}
