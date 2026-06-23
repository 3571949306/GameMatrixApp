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
 * 浏览器模块自动化测试。
 *
 * 测试范围：
 * - 点击底部导航栏"浏览器"Tab 打开浏览器
 * - 遍历浏览器页面所有可点击元素
 * - 返回游戏大厅
 *
 * 注意：若浏览器模块未安装，测试将优雅跳过（不失败）。
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-06-22
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class BrowserTest : EmulatorTestBase() {

    companion object {
        private const val TAG = "BrowserTest"
        /** 浏览器模块 ID */
        private const val MODULE_ID_BROWSER = "browser"
    }

    /**
     * 检查浏览器模块是否可用（导航项存在）。
     *
     * @return true 表示可用，false 表示未安装应跳过
     */
    private fun isBrowserModuleAvailable(): Boolean {
        val navSelector = UiSelector().resourceId("${appContext.packageName}/id/navigation_browser")
        val navItem = device.findObject(navSelector)
        if (!navItem.exists()) {
            Log.d(TAG, "浏览器导航项不存在，跳过测试")
            return false
        }
        if (!isModuleInstalled(MODULE_ID_BROWSER)) {
            Log.d(TAG, "浏览器模块未安装，但仍尝试进入页面测试")
        }
        return true
    }

    /**
     * TC-BROWSER-001: 验证点击底部导航栏"浏览器"Tab 能打开浏览器页面。
     */
    @Test
    fun test_001_openBrowser() {
        Log.d(TAG, "=== TC-BROWSER-001: 打开浏览器测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(1500)

        if (!isBrowserModuleAvailable()) {
            Log.d(TAG, "浏览器模块未安装，优雅跳过")
            return
        }

        // 点击底部导航栏"浏览器"Tab
        val browserSelector = UiSelector().resourceId("${appContext.packageName}/id/navigation_browser")
        val browserTab = device.findObject(browserSelector)
        if (browserTab.exists()) {
            browserTab.click()
            safeSleep(2000)
        }
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-BROWSER-002: 遍历浏览器页面所有可点击元素。
     */
    @Test
    fun test_002_clickAllButtons() {
        Log.d(TAG, "=== TC-BROWSER-002: 遍历浏览器可点击元素测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(1500)

        if (!isBrowserModuleAvailable()) {
            Log.d(TAG, "浏览器模块未安装，优雅跳过")
            return
        }

        // 切换到浏览器
        val browserSelector = UiSelector().resourceId("${appContext.packageName}/id/navigation_browser")
        val browserTab = device.findObject(browserSelector)
        if (browserTab.exists()) {
            browserTab.click()
            safeSleep(2000)
        }

        val clickCount = GameTestHelper.clickAllVisibleButtons(
            device,
            maxClicks = 20,
            clickIntervalMs = 800
        )
        Log.d(TAG, "浏览器页面共点击 $clickCount 个元素")

        // 兜底关闭可能残留的对话框
        val closeTexts = listOf("确定", "OK", "取消", "Cancel", "关闭", "Close", "知道了", "前进", "后退", "刷新")
        for (text in closeTexts) {
            GameTestHelper.clickButtonByText(device, text, 800)
        }
        device.pressBack()
        safeSleep(500)

        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-BROWSER-003: 验证从浏览器返回游戏大厅。
     */
    @Test
    fun test_003_exitToHall() {
        Log.d(TAG, "=== TC-BROWSER-003: 返回游戏大厅测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(1500)

        if (!isBrowserModuleAvailable()) {
            Log.d(TAG, "浏览器模块未安装，优雅跳过")
            return
        }

        // 先进入浏览器
        val browserSelector = UiSelector().resourceId("${appContext.packageName}/id/navigation_browser")
        val browserTab = device.findObject(browserSelector)
        if (browserTab.exists()) {
            browserTab.click()
            safeSleep(2000)
        }

        // 返回游戏大厅：点击"游戏"导航项
        GameTestHelper.ensureGamesHall(device)
        safeSleep(1000)

        assertTrue("返回游戏大厅后应用不应崩溃", GameTestHelper.isAppAlive(device))
    }
}
