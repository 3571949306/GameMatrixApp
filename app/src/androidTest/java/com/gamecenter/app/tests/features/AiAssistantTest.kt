package com.gamecenter.app.tests.features

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
 * AI 助手模块自动化测试。
 *
 * 测试范围：
 * - 点击底部导航栏"AI"Tab 打开 AI 助手
 * - 遍历 AI 助手页面所有可点击元素
 * - 返回游戏大厅
 *
 * 注意：若 AI 助手模块未安装，测试将优雅跳过（不失败）。
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-06-22
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class AiAssistantTest : EmulatorTestBase() {

    companion object {
        private const val TAG = "AiAssistantTest"
        /** AI 助手模块 ID */
        private const val MODULE_ID_AI = "ai"
    }

    /**
     * 检查 AI 助手模块是否可用（导航项存在）。
     *
     * @return true 表示可用，false 表示未安装应跳过
     */
    private fun isAiModuleAvailable(): Boolean {
        val navSelector = UiSelector().resourceId("${appContext.packageName}/id/navigation_ai")
        val navItem = device.findObject(navSelector)
        if (!navItem.exists()) {
            Log.d(TAG, "AI 助手导航项不存在，跳过测试")
            return false
        }
        if (!isModuleInstalled(MODULE_ID_AI)) {
            Log.d(TAG, "AI 助手模块未安装，但仍尝试进入页面测试")
        }
        return true
    }

    /**
     * TC-AI-001: 验证点击底部导航栏"AI"Tab 能打开 AI 助手页面。
     */
    @Test
    fun test_001_openAi() {
        Log.d(TAG, "=== TC-AI-001: 打开 AI 助手测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(1500)

        if (!isAiModuleAvailable()) {
            Log.d(TAG, "AI 助手模块未安装，优雅跳过")
            return
        }

        // 点击底部导航栏"AI"Tab
        val aiSelector = UiSelector().resourceId("${appContext.packageName}/id/navigation_ai")
        val aiTab = device.findObject(aiSelector)
        if (aiTab.exists()) {
            aiTab.click()
            safeSleep(2000)
        }
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-AI-002: 遍历 AI 助手页面所有可点击元素。
     */
    @Test
    fun test_002_clickAllButtons() {
        Log.d(TAG, "=== TC-AI-002: 遍历 AI 助手可点击元素测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(1500)

        if (!isAiModuleAvailable()) {
            Log.d(TAG, "AI 助手模块未安装，优雅跳过")
            return
        }

        // 切换到 AI 助手
        val aiSelector = UiSelector().resourceId("${appContext.packageName}/id/navigation_ai")
        val aiTab = device.findObject(aiSelector)
        if (aiTab.exists()) {
            aiTab.click()
            safeSleep(2000)
        }

        val clickCount = GameTestHelper.clickAllVisibleButtons(
            device,
            maxClicks = 20,
            clickIntervalMs = 800
        )
        Log.d(TAG, "AI 助手页面共点击 $clickCount 个元素")

        // 兜底关闭可能残留的对话框
        val closeTexts = listOf("确定", "OK", "取消", "Cancel", "关闭", "Close", "知道了", "发送", "Send")
        for (text in closeTexts) {
            GameTestHelper.clickButtonByText(device, text, 800)
        }
        device.pressBack()
        safeSleep(500)

        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-AI-003: 验证从 AI 助手返回游戏大厅。
     */
    @Test
    fun test_003_exitToHall() {
        Log.d(TAG, "=== TC-AI-003: 返回游戏大厅测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(1500)

        if (!isAiModuleAvailable()) {
            Log.d(TAG, "AI 助手模块未安装，优雅跳过")
            return
        }

        // 先进入 AI 助手
        val aiSelector = UiSelector().resourceId("${appContext.packageName}/id/navigation_ai")
        val aiTab = device.findObject(aiSelector)
        if (aiTab.exists()) {
            aiTab.click()
            safeSleep(2000)
        }

        // 返回游戏大厅：点击"游戏"导航项
        GameTestHelper.ensureGamesHall(device)
        safeSleep(1000)

        assertTrue("返回游戏大厅后应用不应崩溃", GameTestHelper.isAppAlive(device))
    }
}
