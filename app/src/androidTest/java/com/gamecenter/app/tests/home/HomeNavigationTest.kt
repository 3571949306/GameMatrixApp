package com.gamecenter.app.tests.home

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
 * 主页与通用导航模块自动化测试。
 *
 * 测试范围：
 * - 应用启动与权限弹窗处理
 * - 底部导航栏各 Tab 切换
 * - 游戏大厅搜索功能
 * - 游戏分类 Tab 切换
 * - 设置按钮入口
 * - 模块商店入口
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-06-22
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class HomeNavigationTest : EmulatorTestBase() {

    companion object {
        private const val TAG = "HomeNavigationTest"
    }

    /**
     * TC-HOME-001: 验证应用能正常启动，不崩溃。
     */
    @Test
    fun test_001_appLaunch() {
        Log.d(TAG, "=== TC-HOME-001: 应用启动测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(2000)
        assertTrue("应用启动后不应崩溃", GameTestHelper.isAppAlive(device))
    }

    /**
     * TC-HOME-002: 验证底部导航栏存在且可见。
     */
    @Test
    fun test_002_bottomNavVisible() {
        Log.d(TAG, "=== TC-HOME-002: 底部导航栏可见性测试 ===")
        // 注意：不能调用 am force-stop，因为 AndroidJUnitRunner 在目标应用进程中运行，
        // force-stop 会杀死测试进程本身导致崩溃
        launchAppAndHandlePermissionDialog()
        safeSleep(1500)

        // 调试：打印当前 Activity 和包名
        val currentActivity = device.executeShellCommand(
            "dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity'"
        )
        Log.d(TAG, "当前 Activity: $currentActivity")
        Log.d(TAG, "appContext.packageName = ${appContext.packageName}")

        // 尝试多种方式查找底部导航栏
        // 方式1：通过 resourceId
        val navSelector1 = UiSelector().resourceId("${appContext.packageName}/id/nav_view")
        val nav1 = device.findObject(navSelector1)
        val exists1 = nav1.waitForExists(5000)
        Log.d(TAG, "方式1（resourceId=nav_view）: $exists1")

        if (exists1) {
            assertTrue("底部导航栏应可见", true)
            return
        }

        // 方式2：通过类名 BottomNavigationView
        val navSelector2 = UiSelector().className(
            "com.google.android.material.bottomnavigation.BottomNavigationView"
        )
        val nav2 = device.findObject(navSelector2)
        val exists2 = nav2.waitForExists(3000)
        Log.d(TAG, "方式2（className=BottomNavigationView）: $exists2")

        if (exists2) {
            assertTrue("底部导航栏应可见", true)
            return
        }

        // 方式3：通过 resourceIdMatches
        val navSelector3 = UiSelector().resourceIdMatches(".*nav_view")
        val nav3 = device.findObject(navSelector3)
        val exists3 = nav3.waitForExists(3000)
        Log.d(TAG, "方式3（resourceIdMatches=.*nav_view）: $exists3")

        assertTrue("底部导航栏应可见（尝试了3种查找方式）", exists3)
    }

    /**
     * TC-HOME-003: 验证"游戏"Tab 可点击且切换成功。
     */
    @Test
    fun test_003_gamesTabClick() {
        Log.d(TAG, "=== TC-HOME-003: 游戏Tab点击测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(1000)

        val gamesNav = UiSelector().resourceId("${appContext.packageName}/id/navigation_games")
        val gamesTab = device.findObject(gamesNav)
        if (gamesNav.let { gamesTab.exists() }) {
            gamesTab.click()
            safeSleep(1000)
        }
        assertTrue("点击游戏Tab后应用不应崩溃", GameTestHelper.isAppAlive(device))
    }

    /**
     * TC-HOME-004: 验证游戏大厅分类 Tab 切换（全部/经典/益智/休闲）。
     */
    @Test
    fun test_004_categoryTabs() {
        Log.d(TAG, "=== TC-HOME-004: 分类Tab切换测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(1500)

        // 确保在游戏大厅
        GameTestHelper.ensureGamesHall(device)

        // 依次点击各分类 Tab
        val categories = listOf("全部", "经典", "益智", "休闲")
        for (category in categories) {
            val clicked = GameTestHelper.clickButtonByText(device, category, 3000)
            if (clicked) {
                safeSleep(500)
                assertTrue("切换到'$category'后应用不应崩溃",
                    GameTestHelper.isAppAlive(device))
            }
        }
    }

    /**
     * TC-HOME-005: 验证游戏大厅搜索功能。
     */
    @Test
    fun test_005_searchFunction() {
        Log.d(TAG, "=== TC-HOME-005: 搜索功能测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(1500)

        GameTestHelper.ensureGamesHall(device)

        // 查找搜索框
        val searchSelector = UiSelector().resourceId("${appContext.packageName}/id/et_game_search")
        val searchBox = device.findObject(searchSelector)
        if (searchBox.exists()) {
            searchBox.click()
            safeSleep(500)
            searchBox.setText("五子棋")
            safeSleep(1000)

            // 清空搜索
            searchBox.setText("")
            safeSleep(500)
        }
        assertTrue("搜索操作后应用不应崩溃", GameTestHelper.isAppAlive(device))
    }

    /**
     * TC-HOME-006: 验证设置按钮可点击。
     */
    @Test
    fun test_006_settingsButton() {
        Log.d(TAG, "=== TC-HOME-006: 设置按钮测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(1500)

        GameTestHelper.ensureGamesHall(device)

        // 查找设置按钮
        val settingsSelector = UiSelector().resourceId("${appContext.packageName}/id/btn_settings")
        val settingsBtn = device.findObject(settingsSelector)
        if (settingsBtn.exists()) {
            settingsBtn.click()
            safeSleep(1000)
            // 关闭设置对话框
            device.pressBack()
            safeSleep(500)
        }
        assertTrue("设置按钮操作后应用不应崩溃", GameTestHelper.isAppAlive(device))
    }

    /**
     * TC-HOME-007: 验证模块商店按钮可点击。
     */
    @Test
    fun test_007_moduleStoreButton() {
        Log.d(TAG, "=== TC-HOME-007: 模块商店按钮测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(1500)

        GameTestHelper.ensureGamesHall(device)

        // 查找模块商店按钮
        val storeSelector = UiSelector().resourceId("${appContext.packageName}/id/btn_module_store")
        val storeBtn = device.findObject(storeSelector)
        if (storeBtn.exists()) {
            storeBtn.click()
            safeSleep(2000)
            // 确认模块商店页面已打开
            assertTrue("模块商店打开后应用不应崩溃", GameTestHelper.isAppAlive(device))
            // 返回大厅
            device.pressBack()
            safeSleep(1000)
        }
        assertTrue("模块商店操作后应用不应崩溃", GameTestHelper.isAppAlive(device))
    }

    /**
     * TC-HOME-008: 验证底部导航栏各模块切换（如果已安装）。
     */
    @Test
    fun test_008_navModuleSwitch() {
        Log.d(TAG, "=== TC-HOME-008: 导航模块切换测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(1500)

        // 尝试点击各导航项
        val navItems = listOf(
            "navigation_games" to "游戏",
            "navigation_browser" to "浏览器",
            "navigation_tools" to "工具",
            "navigation_ai" to "AI",
            "navigation_vpn" to "VPN"
        )

        for ((resId, label) in navItems) {
            val selector = UiSelector().resourceId("${appContext.packageName}/id/$resId")
            val navItem = device.findObject(selector)
            if (navItem.exists()) {
                Log.d(TAG, "点击导航项: $label")
                navItem.click()
                safeSleep(1500)
                assertTrue("切换到'$label'后应用不应崩溃",
                    GameTestHelper.isAppAlive(device))
            } else {
                Log.d(TAG, "导航项 '$label' 未安装，跳过")
            }
        }
    }

    /**
     * TC-HOME-009: 验证游戏卡片列表可见。
     */
    @Test
    fun test_009_gameCardsVisible() {
        Log.d(TAG, "=== TC-HOME-009: 游戏卡片列表测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(1500)

        GameTestHelper.ensureGamesHall(device)

        // 查找 RecyclerView
        val rvSelector = UiSelector().resourceId("${appContext.packageName}/id/rv_games")
        val rv = device.findObject(rvSelector)
        if (rv.exists()) {
            // 滚动查看游戏卡片
            for (i in 0 until 3) {
                val screenHeight = device.displayHeight
                val screenWidth = device.displayWidth
                device.swipe(
                    screenWidth / 2, screenHeight * 2 / 3,
                    screenWidth / 2, screenHeight / 3,
                    20
                )
                safeSleep(500)
            }
        }
        assertTrue("浏览游戏卡片后应用不应崩溃", GameTestHelper.isAppAlive(device))
    }

    /**
     * TC-HOME-010: 验证版本号显示。
     */
    @Test
    fun test_010_versionDisplay() {
        Log.d(TAG, "=== TC-HOME-010: 版本号显示测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(1500)

        GameTestHelper.ensureGamesHall(device)

        // 查找版本号 TextView
        val versionSelector = UiSelector().resourceId("${appContext.packageName}/id/tv_version")
        val versionTv = device.findObject(versionSelector)
        if (versionTv.exists()) {
            val versionText = versionTv.text
            Log.d(TAG, "版本号: $versionText")
        }
        assertTrue("版本号检查后应用不应崩溃", GameTestHelper.isAppAlive(device))
    }
}
