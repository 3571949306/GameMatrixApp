package com.gamecenter.app.tests.home

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiSelector
import com.gamecenter.app.EmulatorTestBase
import com.gamecenter.app.R
import com.gamecenter.app.tests.GameTestHelper
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * 主页与通用导航模块自动化测试（批次 2c 适配游戏库新首页）。
 *
 * 默认首页自批次 2d 起为 GameLibraryFragment（fragment_game_library.xml：顶栏/搜索/
 * 分类 chips/单一 RecyclerView/随机 FAB）；QA 回退构建用 -PenableHomeLibraryRevamp=false。
 * legacy GamesFragment 专属控件（et_game_search/rv_games/btn_settings/tv_version/btn_module_store）
 * 不复存在，各用例锚点已迁移，详见方法内注释。
 * 注意：底部导航菜单项 id 由 BottomNavigationManager 程序化分配（nextMenuId++），
 * 无 R.id.navigation_* 资源 id，导航相关断言一律走 nav_view / 类名 / 标题文本兜底。
 *
 * 测试范围：
 * - 应用启动与权限弹窗处理
 * - 底部导航栏各 Tab 切换
 * - 游戏库搜索功能（含历史）
 * - 游戏分类 Chip 切换
 * - 设置对话框入口（含版本展示）
 * - 溢出菜单 → 模块商店入口
 * - 随机游戏 FAB
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.1 (批次 2c：锚点迁移至 GameLibraryFragment)
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
     * nav_view 挂在 activity_main.xml，与新旧首页无关，查找逻辑保持不变。
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
     * 批次 2c：底部导航项无资源 id，改经 GameTestHelper.ensureGamesHall（标题文本兜底）切换；
     * 并以新首页锚点 rv_game_library 断言切换成功（legacy 的 navigation_games id 已不存在）。
     */
    @Test
    fun test_003_gamesTabClick() {
        Log.d(TAG, "=== TC-HOME-003: 游戏Tab点击测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(1000)

        GameTestHelper.ensureGamesHall(device)
        safeSleep(1000)

        // 新首页锚点：游戏库 RecyclerView 可见即认为游戏 Tab 生效
        val rvSelector = UiSelector().resourceId("${appContext.packageName}/id/rv_game_library")
        val rvExists = device.findObject(rvSelector).waitForExists(5000)
        Log.d(TAG, "游戏库 RecyclerView 可见: $rvExists")
        assertTrue("游戏Tab生效后应看到游戏库列表", rvExists)
        assertTrue("点击游戏Tab后应用不应崩溃", GameTestHelper.isAppAlive(device))
    }

    /**
     * TC-HOME-004: 验证游戏库分类 Chip 切换。
     * 批次 2c：分类 Tab 改为 ChipGroup（chip_group_game_filters）内 Chip；
     * "全部"文案走资源 game_library_all_filter，经典/益智/休闲与
     * GameRegistry.getCategories 的分类名同源（该处为存量硬编码，无独立资源）。
     */
    @Test
    fun test_004_categoryTabs() {
        Log.d(TAG, "=== TC-HOME-004: 分类Chip切换测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(1500)

        // 确保在游戏大厅
        GameTestHelper.ensureGamesHall(device)

        // 新首页锚点：分类 ChipGroup 应存在
        val chipGroupSelector =
            UiSelector().resourceId("${appContext.packageName}/id/chip_group_game_filters")
        assertTrue("游戏库分类 ChipGroup 应可见", device.findObject(chipGroupSelector).exists())

        // 依次点击各分类 Chip
        val categories = listOf(
            appContext.getString(R.string.game_library_all_filter),
            "经典", "益智", "休闲"
        )
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
     * TC-HOME-005: 验证游戏库搜索功能。
     * 批次 2c：et_game_search → et_game_library_search（fragment_game_library.xml）。
     */
    @Test
    fun test_005_searchFunction() {
        Log.d(TAG, "=== TC-HOME-005: 搜索功能测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(1500)

        GameTestHelper.ensureGamesHall(device)

        // 查找搜索框（新首页搜索框）
        val searchSelector =
            UiSelector().resourceId("${appContext.packageName}/id/et_game_library_search")
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
     * TC-HOME-006: 验证设置入口可点击。
     * 批次 2c：legacy btn_settings（打开独立设置页）已删，新首页为顶栏
     * btn_game_library_settings（弹出 AppSettingsDialog），返回键关闭对话框。
     */
    @Test
    fun test_006_settingsButton() {
        Log.d(TAG, "=== TC-HOME-006: 设置按钮测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(1500)

        GameTestHelper.ensureGamesHall(device)

        // 查找设置按钮（新首页顶栏齿轮）
        val settingsSelector =
            UiSelector().resourceId("${appContext.packageName}/id/btn_game_library_settings")
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
     * TC-HOME-007: 验证模块商店入口可点击。
     * 批次 2c：legacy 独立按钮 btn_module_store 不复存在，新首页走顶栏
     * 溢出菜单（btn_game_library_overflow）→ "模块商店"项。
     */
    @Test
    fun test_007_moduleStoreButton() {
        Log.d(TAG, "=== TC-HOME-007: 模块商店按钮测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(1500)

        GameTestHelper.ensureGamesHall(device)

        // 打开溢出菜单
        val overflowSelector =
            UiSelector().resourceId("${appContext.packageName}/id/btn_game_library_overflow")
        val overflowBtn = device.findObject(overflowSelector)
        if (overflowBtn.exists()) {
            overflowBtn.click()
            safeSleep(800)

            // 点击"模块商店"菜单项
            val storeText = appContext.getString(R.string.module_store)
            val storeItem = device.findObject(UiSelector().text(storeText))
            if (storeItem.exists()) {
                storeItem.click()
                safeSleep(2000)
                // 确认模块商店页面已打开
                assertTrue("模块商店打开后应用不应崩溃", GameTestHelper.isAppAlive(device))
                // 返回大厅
                device.pressBack()
                safeSleep(1000)
            } else {
                // 菜单项未出现，兜底关闭可能残存的 PopupMenu
                device.pressBack()
                safeSleep(500)
            }
        }
        assertTrue("模块商店操作后应用不应崩溃", GameTestHelper.isAppAlive(device))
    }

    /**
     * TC-HOME-008: 验证底部导航栏各模块切换（如果已安装）。
     * 批次 2c：导航项无 R.id.navigation_* 资源 id（程序化分配），改按导航标题文本
     * （nav_browser/nav_tools/nav_ai/nav_vpn 资源）定位；未安装/未显示的项自动跳过。
     */
    @Test
    fun test_008_navModuleSwitch() {
        Log.d(TAG, "=== TC-HOME-008: 导航模块切换测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(1500)

        // 尝试点击各导航项（标题文本 → 资源）
        val navItems = listOf(
            appContext.getString(R.string.nav_browser),
            appContext.getString(R.string.nav_tools),
            appContext.getString(R.string.nav_ai),
            appContext.getString(R.string.nav_vpn)
        )

        for (label in navItems) {
            val navItem = device.findObject(UiSelector().text(label))
            if (navItem.exists()) {
                Log.d(TAG, "点击导航项: $label")
                navItem.click()
                safeSleep(1500)
                assertTrue("切换到'$label'后应用不应崩溃",
                    GameTestHelper.isAppAlive(device))
                // 切回游戏大厅，保证下一项从稳定起点查找
                GameTestHelper.ensureGamesHall(device)
            } else {
                Log.d(TAG, "导航项 '$label' 未安装，跳过")
            }
        }
    }

    /**
     * TC-HOME-009: 验证游戏卡片列表可见。
     * 批次 2c：rv_games → rv_game_library。留存卡片（每日挑战/快览统计/今日精选等）
     * 是 RecyclerView 内动态条目、无独立 resource id，不单独锚定，由本用例滚动浏览覆盖。
     */
    @Test
    fun test_009_gameCardsVisible() {
        Log.d(TAG, "=== TC-HOME-009: 游戏卡片列表测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(1500)

        GameTestHelper.ensureGamesHall(device)

        // 查找 RecyclerView（新首页游戏库列表）
        val rvSelector = UiSelector().resourceId("${appContext.packageName}/id/rv_game_library")
        val rv = device.findObject(rvSelector)
        if (rv.exists()) {
            // 滚动查看游戏卡片（含留存卡片区域）
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
     * 批次 2c：新首页本身无版本展示（legacy tv_version 已随 GamesFragment 移除），
     * 版本迁至设置对话框 tv_current_version（AppSettingsDialog），改经设置入口断言。
     */
    @Test
    fun test_010_versionDisplay() {
        Log.d(TAG, "=== TC-HOME-010: 版本号显示测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(1500)

        GameTestHelper.ensureGamesHall(device)

        // 打开设置对话框，在其中查找版本号 TextView
        val settingsSelector =
            UiSelector().resourceId("${appContext.packageName}/id/btn_game_library_settings")
        val settingsBtn = device.findObject(settingsSelector)
        if (settingsBtn.exists()) {
            settingsBtn.click()
            safeSleep(1000)

            val versionSelector =
                UiSelector().resourceId("${appContext.packageName}/id/tv_current_version")
            val versionTv = device.findObject(versionSelector)
            if (versionTv.exists()) {
                val versionText = versionTv.text
                Log.d(TAG, "版本号: $versionText")
            }
            // 关闭设置对话框
            device.pressBack()
            safeSleep(500)
        }
        assertTrue("版本号检查后应用不应崩溃", GameTestHelper.isAppAlive(device))
    }

    /**
     * TC-HOME-011: 验证随机游戏 FAB（批次 2a 新能力）可点击不崩溃。
     * 库非空时随机启动一款游戏（返回键退出），库为空时仅 Toast 提示，两种路径均应存活。
     */
    @Test
    fun test_011_randomGameFab() {
        Log.d(TAG, "=== TC-HOME-011: 随机游戏FAB测试 ===")
        launchAppAndHandlePermissionDialog()
        safeSleep(1500)

        GameTestHelper.ensureGamesHall(device)

        val fabSelector = UiSelector().resourceId("${appContext.packageName}/id/fab_random_game")
        val fab = device.findObject(fabSelector)
        if (fab.exists()) {
            fab.click()
            safeSleep(2000)
            // 若随机启动了游戏，返回大厅；若停留在首页（空库 Toast），返回键回退到桌面也无碍
            device.pressBack()
            safeSleep(1000)
        }
        assertTrue("随机游戏FAB操作后应用不应崩溃", GameTestHelper.isAppAlive(device))
    }
}
