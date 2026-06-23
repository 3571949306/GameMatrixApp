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
 * 模块商店自动化测试。
 *
 * 测试范围：
 * - 打开模块商店
 * - 分类 Tab 切换（游戏/浏览器/工具箱/AI助手/VPN）
 * - 游戏子分类切换（益智/休闲/经典）
 * - 搜索功能
 * - 刷新按钮
 * - 已安装模块入口
 * - 遍历所有可点击元素
 * - 退出模块商店
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-06-22
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ModuleStoreTest : EmulatorTestBase() {

    companion object {
        private const val TAG = "ModuleStoreTest"
        /** 模块商店 Activity 全限定名 */
        private const val MODULE_STORE_ACTIVITY = "com.gamecenter.app.modules.ModuleStoreActivity"
    }

    /**
     * 启动模块商店页面。
     */
    private fun launchModuleStore() {
        Log.d(TAG, "启动模块商店: $MODULE_STORE_ACTIVITY")
        device.executeShellCommand(
            "am start -n ${appContext.packageName}/$MODULE_STORE_ACTIVITY"
        )
        safeSleep(2500)
    }

    /**
     * TC-STORE-001: 验证能正常打开模块商店，不崩溃。
     */
    @Test
    fun test_001_openModuleStore() {
        Log.d(TAG, "=== TC-STORE-001: 打开模块商店测试 ===")
        launchModuleStore()
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-STORE-002: 验证分类 Tab 切换（游戏/浏览器/工具箱/AI助手/VPN）。
     */
    @Test
    fun test_002_categoryTabs() {
        Log.d(TAG, "=== TC-STORE-002: 分类Tab切换测试 ===")
        launchModuleStore()

        // 依次点击各分类 Tab
        val categories = listOf("游戏", "浏览器", "工具箱", "AI助手", "VPN")
        for (category in categories) {
            val clicked = GameTestHelper.clickButtonByText(device, category, 3000)
            if (clicked) {
                Log.d(TAG, "切换到分类: $category")
                safeSleep(1000)
                assertTrue("切换到'$category'后应用不应崩溃",
                    GameTestHelper.isAppAlive(device))
            } else {
                Log.d(TAG, "分类 '$category' 未找到，跳过")
            }
        }
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-STORE-003: 验证游戏子分类切换（益智/休闲/经典）。
     */
    @Test
    fun test_003_subCategoryTabs() {
        Log.d(TAG, "=== TC-STORE-003: 游戏子分类切换测试 ===")
        launchModuleStore()

        // 先确保在"游戏"分类
        GameTestHelper.clickButtonByText(device, "游戏", 3000)
        safeSleep(1000)

        // 依次点击各子分类
        val subCategories = listOf("益智", "休闲", "经典")
        for (sub in subCategories) {
            val clicked = GameTestHelper.clickButtonByText(device, sub, 3000)
            if (clicked) {
                Log.d(TAG, "切换到子分类: $sub")
                safeSleep(800)
                assertTrue("切换到'$sub'后应用不应崩溃",
                    GameTestHelper.isAppAlive(device))
            } else {
                Log.d(TAG, "子分类 '$sub' 未找到，跳过")
            }
        }
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-STORE-004: 验证搜索功能。
     */
    @Test
    fun test_004_searchFunction() {
        Log.d(TAG, "=== TC-STORE-004: 搜索功能测试 ===")
        launchModuleStore()

        // 查找搜索框
        val searchSelector = UiSelector().resourceId("${appContext.packageName}/id/etModuleSearch")
        val searchBox = device.findObject(searchSelector)
        if (searchBox.exists()) {
            searchBox.click()
            safeSleep(500)
            searchBox.setText("五子棋")
            safeSleep(1500)

            // 清空搜索
            searchBox.setText("")
            safeSleep(800)
        } else {
            // 兜底：尝试点击工具栏搜索菜单项
            GameTestHelper.clickButtonByText(device, "搜索", 2000)
            safeSleep(1000)
        }
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-STORE-005: 验证刷新按钮。
     */
    @Test
    fun test_005_refreshModules() {
        Log.d(TAG, "=== TC-STORE-005: 刷新按钮测试 ===")
        launchModuleStore()

        // 尝试点击工具栏"刷新"菜单项
        val refreshed = GameTestHelper.clickButtonByText(device, "刷新", 3000)
        if (refreshed) {
            Log.d(TAG, "点击刷新按钮成功")
            safeSleep(2000)
        } else {
            // 兜底：尝试通过菜单图标点击（溢出菜单）
            val overflowSelector = UiSelector().descriptionContains("更多")
            val overflow = device.findObject(overflowSelector)
            if (overflow.exists()) {
                overflow.click()
                safeSleep(800)
                GameTestHelper.clickButtonByText(device, "刷新", 1500)
                safeSleep(2000)
            }
        }
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-STORE-006: 验证已安装模块入口。
     */
    @Test
    fun test_006_installedModules() {
        Log.d(TAG, "=== TC-STORE-006: 已安装模块入口测试 ===")
        launchModuleStore()

        // 尝试点击"已安装模块"菜单项（可能在溢出菜单中）
        var clicked = GameTestHelper.clickButtonByText(device, "已安装模块", 2000)
        if (!clicked) {
            // 打开溢出菜单
            val overflowSelector = UiSelector().descriptionContains("更多")
            val overflow = device.findObject(overflowSelector)
            if (overflow.exists()) {
                overflow.click()
                safeSleep(800)
                clicked = GameTestHelper.clickButtonByText(device, "已安装模块", 2000)
            }
        }

        if (clicked) {
            Log.d(TAG, "进入已安装模块页面")
            safeSleep(2000)
            // 返回模块商店
            device.pressBack()
            safeSleep(1000)
        }
        GameTestHelper.assertAppAlive(device)
    }

    /**
     * TC-STORE-007: 遍历模块商店所有可点击元素。
     */
    @Test
    fun test_007_clickAllButtons() {
        Log.d(TAG, "=== TC-STORE-007: 遍历所有可点击元素测试 ===")
        launchModuleStore()

        val clickCount = GameTestHelper.clickAllVisibleButtons(
            device,
            maxClicks = 20,
            clickIntervalMs = 800
        )
        Log.d(TAG, "模块商店页面共点击 $clickCount 个元素")

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
     * TC-STORE-008: 验证退出模块商店。
     */
    @Test
    fun test_008_exitStore() {
        Log.d(TAG, "=== TC-STORE-008: 退出模块商店测试 ===")
        launchModuleStore()

        // 优先尝试点击工具栏导航（返回）按钮
        val navSelector = UiSelector().resourceId("${appContext.packageName}/id/moduleToolbar")
        val toolbar = device.findObject(navSelector)
        if (toolbar.exists()) {
            // 工具栏存在，按返回键退出
            device.pressBack()
            safeSleep(1500)
        } else {
            device.pressBack()
            safeSleep(1500)
        }

        assertTrue("退出模块商店后应用不应崩溃", GameTestHelper.isAppAlive(device))
    }
}
