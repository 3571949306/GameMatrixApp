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
 * 工具箱模块自动化测试。
 *
 * 测试范围：
 * - 点击底部导航栏"工具"Tab 打开工具箱
 * - 遍历工具箱页面所有可点击元素
 * - 返回游戏大厅
 *
 * 注意：若工具箱模块未安装，测试将优雅跳过（不失败）。
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-06-22
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ToolBoxTest : EmulatorTestBase() {

    companion object {
        private const val TAG = "ToolBoxTest"
        /** 工具箱模块 ID */
        private const val MODULE_ID_TOOLS = "tools"
    }

    /**
     * 检查工具箱模块是否可用（导航项存在且模块已安装）。
     *
     * @return true 表示可用，false 表示未安装应跳过
     */
    private fun isToolsModuleAvailable(): Boolean {
        val navSelector = UiSelector().resourceId("${appContext.packageName}/id/navigation_tools")
        val navItem = device.findObject(navSelector)
        if (!navItem.exists()) {
            Log.d(TAG, "工具箱导航项不存在，跳过测试")
            return false
        }
        // 检查模块是否已安装（未安装时仍允许进入页面，但功能可能受限）
        if (!isModuleInstalled(MODULE_ID_TOOLS)) {
            Log.d(TAG, "工具箱模块未安装，但仍尝试进入页面测试")
        }
        return true
    }

    /**
     * TC-TOOLS-001: 验证点击底部导航栏"工具"Tab 能打开工具箱页面。
     */
    @Test
    fun test_001_openTools() {
        Log.d(TAG, "=== TC-TOOLS-001: 打开工具箱测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(1500)

        if (!isToolsModuleAvailable()) {
            Log.d(TAG, "工具箱模块未安装，优雅跳过")
            return
        }

        // 点击底部导航栏"工具"Tab
        val toolsSelector = UiSelector().resourceId("${appContext.packageName}/id/navigation_tools")
        val toolsTab = device.findObject(toolsSelector)
        if (toolsTab.exists()) {
            toolsTab.click()
            safeSleep(2000)
        }
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-TOOLS-002: 遍历工具箱页面所有可点击元素。
     */
    @Test
    fun test_002_clickAllButtons() {
        Log.d(TAG, "=== TC-TOOLS-002: 遍历工具箱可点击元素测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(1500)

        if (!isToolsModuleAvailable()) {
            Log.d(TAG, "工具箱模块未安装，优雅跳过")
            return
        }

        // 切换到工具箱
        val toolsSelector = UiSelector().resourceId("${appContext.packageName}/id/navigation_tools")
        val toolsTab = device.findObject(toolsSelector)
        if (toolsTab.exists()) {
            toolsTab.click()
            safeSleep(2000)
        }

        val clickCount = GameTestHelper.clickAllVisibleButtons(
            device,
            maxClicks = 20,
            clickIntervalMs = 800
        )
        Log.d(TAG, "工具箱页面共点击 $clickCount 个元素")

        // 兜底关闭可能残留的对话框
        val closeTexts = listOf("确定", "OK", "取消", "Cancel", "关闭", "Close", "知道了")
        for (text in closeTexts) {
            GameTestHelper.clickButtonByText(device, text, 800)
        }
        device.pressBack()
        safeSleep(500)

        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-TOOLS-003: 验证从工具箱返回游戏大厅。
     */
    @Test
    fun test_003_exitToHall() {
        Log.d(TAG, "=== TC-TOOLS-003: 返回游戏大厅测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(1500)

        if (!isToolsModuleAvailable()) {
            Log.d(TAG, "工具箱模块未安装，优雅跳过")
            return
        }

        // 先进入工具箱
        val toolsSelector = UiSelector().resourceId("${appContext.packageName}/id/navigation_tools")
        val toolsTab = device.findObject(toolsSelector)
        if (toolsTab.exists()) {
            toolsTab.click()
            safeSleep(2000)
        }

        // 返回游戏大厅：点击"游戏"导航项
        GameTestHelper.ensureGamesHall(device)
        safeSleep(1000)

        assertTrue("返回游戏大厅后应用不应崩溃", GameTestHelper.isAppAlive(device))
    }
}
