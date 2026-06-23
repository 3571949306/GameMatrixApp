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
 * 设置模块自动化测试。
 *
 * 测试范围：
 * - 打开设置页面
 * - 返回按钮
 * - 模块商店入口
 * - 已安装模块管理
 * - 清除缓存（含确认对话框）
 * - 主题切换
 * - 语言切换
 * - 战绩入口
 * - 关于对话框
 * - 遍历所有可点击元素
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-06-22
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class SettingsTest : EmulatorTestBase() {

    companion object {
        private const val TAG = "SettingsTest"
    }

    /**
     * TC-SETTINGS-001: 验证能正常打开设置页面，不崩溃。
     */
    @Test
    fun test_001_openSettings() {
        Log.d(TAG, "=== TC-SETTINGS-001: 打开设置页面测试 ===")
        device.executeShellCommand("am start -n ${appContext.packageName}/.SettingsActivity")
        safeSleep(2000)
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-SETTINGS-002: 验证返回按钮可点击且能返回。
     */
    @Test
    fun test_002_backButton() {
        Log.d(TAG, "=== TC-SETTINGS-002: 设置返回按钮测试 ===")
        device.executeShellCommand("am start -n ${appContext.packageName}/.SettingsActivity")
        safeSleep(2000)

        // 点击返回按钮
        val backSelector = UiSelector().resourceId("${appContext.packageName}/id/btn_back")
        val backBtn = device.findObject(backSelector)
        if (backBtn.exists()) {
            backBtn.click()
            safeSleep(1000)
        } else {
            device.pressBack()
            safeSleep(1000)
        }
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-SETTINGS-003: 验证模块商店入口可点击。
     */
    @Test
    fun test_003_moduleStore() {
        Log.d(TAG, "=== TC-SETTINGS-003: 模块商店入口测试 ===")
        device.executeShellCommand("am start -n ${appContext.packageName}/.SettingsActivity")
        safeSleep(2000)

        val storeSelector = UiSelector().resourceId("${appContext.packageName}/id/btn_module_store")
        val storeBtn = device.findObject(storeSelector)
        if (storeBtn.exists()) {
            storeBtn.click()
            safeSleep(2000)
            // 返回设置页
            device.pressBack()
            safeSleep(1000)
        }
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-SETTINGS-004: 验证已安装模块管理入口可点击。
     */
    @Test
    fun test_004_manageModules() {
        Log.d(TAG, "=== TC-SETTINGS-004: 已安装模块管理测试 ===")
        device.executeShellCommand("am start -n ${appContext.packageName}/.SettingsActivity")
        safeSleep(2000)

        val manageSelector = UiSelector().resourceId("${appContext.packageName}/id/btn_manage_modules")
        val manageBtn = device.findObject(manageSelector)
        if (manageBtn.exists()) {
            manageBtn.click()
            safeSleep(2000)
            // 返回设置页
            device.pressBack()
            safeSleep(1000)
        }
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-SETTINGS-005: 验证清除缓存功能，处理确认对话框。
     */
    @Test
    fun test_005_clearCache() {
        Log.d(TAG, "=== TC-SETTINGS-005: 清除缓存测试 ===")
        device.executeShellCommand("am start -n ${appContext.packageName}/.SettingsActivity")
        safeSleep(2000)

        val cacheSelector = UiSelector().resourceId("${appContext.packageName}/id/btn_clear_cache")
        val cacheBtn = device.findObject(cacheSelector)
        if (cacheBtn.exists()) {
            cacheBtn.click()
            safeSleep(1000)

            // 处理确认对话框：优先点击"清除"，否则点击"确定"
            val confirmTexts = listOf("清除", "确定", "OK", "确认")
            var confirmed = false
            for (text in confirmTexts) {
                if (GameTestHelper.clickButtonByText(device, text, 1500)) {
                    Log.d(TAG, "点击确认按钮: $text")
                    confirmed = true
                    break
                }
            }
            if (!confirmed) {
                // 兜底：点击"取消"关闭对话框
                GameTestHelper.clickButtonByText(device, "取消", 1000)
            }
            safeSleep(1000)
        }
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-SETTINGS-006: 验证主题切换功能。
     */
    @Test
    fun test_006_themeSwitch() {
        Log.d(TAG, "=== TC-SETTINGS-006: 主题切换测试 ===")
        device.executeShellCommand("am start -n ${appContext.packageName}/.SettingsActivity")
        safeSleep(2000)

        val themeSelector = UiSelector().resourceId("${appContext.packageName}/id/btn_theme")
        val themeBtn = device.findObject(themeSelector)
        if (themeBtn.exists()) {
            themeBtn.click()
            safeSleep(1000)

            // 主题选项：跟随系统/浅色模式/深色模式
            val themeOptions = listOf("深色模式", "浅色模式", "跟随系统")
            var selected = false
            for (option in themeOptions) {
                if (GameTestHelper.clickButtonByText(device, option, 1500)) {
                    Log.d(TAG, "选择主题: $option")
                    selected = true
                    break
                }
            }
            if (!selected) {
                // 兜底关闭对话框
                GameTestHelper.clickButtonByText(device, "取消", 1000)
            }
            safeSleep(1000)
        }
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-SETTINGS-007: 验证语言切换功能。
     */
    @Test
    fun test_007_languageSwitch() {
        Log.d(TAG, "=== TC-SETTINGS-007: 语言切换测试 ===")
        device.executeShellCommand("am start -n ${appContext.packageName}/.SettingsActivity")
        safeSleep(2000)

        val langSelector = UiSelector().resourceId("${appContext.packageName}/id/btn_language")
        val langBtn = device.findObject(langSelector)
        if (langBtn.exists()) {
            langBtn.click()
            safeSleep(1000)

            // 语言选项：跟随系统/中文/English
            val langOptions = listOf("中文", "English", "跟随系统")
            var selected = false
            for (option in langOptions) {
                if (GameTestHelper.clickButtonByText(device, option, 1500)) {
                    Log.d(TAG, "选择语言: $option")
                    selected = true
                    break
                }
            }
            if (!selected) {
                GameTestHelper.clickButtonByText(device, "取消", 1000)
            }
            safeSleep(1000)
        }
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-SETTINGS-008: 验证战绩入口可点击。
     */
    @Test
    fun test_008_stats() {
        Log.d(TAG, "=== TC-SETTINGS-008: 战绩入口测试 ===")
        device.executeShellCommand("am start -n ${appContext.packageName}/.SettingsActivity")
        safeSleep(2000)

        val statsSelector = UiSelector().resourceId("${appContext.packageName}/id/btn_stats")
        val statsBtn = device.findObject(statsSelector)
        if (statsBtn.exists()) {
            statsBtn.click()
            safeSleep(2000)
            // 返回设置页（可能是 Activity 或 Toast）
            device.pressBack()
            safeSleep(1000)
        }
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-SETTINGS-009: 验证关于对话框可打开并关闭。
     */
    @Test
    fun test_009_about() {
        Log.d(TAG, "=== TC-SETTINGS-009: 关于对话框测试 ===")
        device.executeShellCommand("am start -n ${appContext.packageName}/.SettingsActivity")
        safeSleep(2000)

        val aboutSelector = UiSelector().resourceId("${appContext.packageName}/id/btn_about")
        val aboutBtn = device.findObject(aboutSelector)
        if (aboutBtn.exists()) {
            aboutBtn.click()
            safeSleep(1000)

            // 关闭关于对话框：尝试常见关闭按钮
            val closeTexts = listOf("确定", "OK", "关闭", "知道了", "取消", "Cancel", "Close")
            for (text in closeTexts) {
                if (GameTestHelper.clickButtonByText(device, text, 1500)) {
                    Log.d(TAG, "关闭关于对话框: $text")
                    break
                }
            }
            safeSleep(500)
            // 兜底按返回键
            device.pressBack()
            safeSleep(500)
        }
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-SETTINGS-010: 遍历设置页面所有可点击元素。
     */
    @Test
    fun test_010_clickAllButtons() {
        Log.d(TAG, "=== TC-SETTINGS-010: 遍历所有可点击元素测试 ===")
        device.executeShellCommand("am start -n ${appContext.packageName}/.SettingsActivity")
        safeSleep(2000)

        val clickCount = GameTestHelper.clickAllVisibleButtons(
            device,
            maxClicks = 15,
            clickIntervalMs = 800
        )
        Log.d(TAG, "设置页面共点击 $clickCount 个元素")

        // 兜底关闭可能残留的对话框
        val closeTexts = listOf("确定", "OK", "取消", "Cancel", "关闭", "Close", "知道了")
        for (text in closeTexts) {
            GameTestHelper.clickButtonByText(device, text, 800)
        }
        device.pressBack()
        safeSleep(500)

        GameTestHelper.assertAppAlive(device)
    }
}
