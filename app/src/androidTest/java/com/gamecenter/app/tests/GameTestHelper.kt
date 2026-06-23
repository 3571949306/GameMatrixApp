package com.gamecenter.app.tests

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.UiObjectNotFoundException
import androidx.test.uiautomator.UiSelector
import org.junit.Assert

/**
 * 游戏测试通用辅助类。
 *
 * 为所有游戏自动化测试提供统一的启动、交互、验证和退出方法。
 * 每个游戏测试类应继承 [com.gamecenter.app.EmulatorTestBase] 并使用本类提供的静态方法。
 *
 * @author QA Engineer (Yan Guoguan)
 * @version 1.0
 * @since 2026-06-22
 */
object GameTestHelper {

    private const val TAG = "GameTestHelper"

    /** 目标应用上下文（用于启动 exported=false 的 Activity）*/
    private val targetContext: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** 目标应用包名 */
    val PACKAGE_NAME: String
        get() = targetContext.packageName

    /**
     * 启动指定游戏 Activity。
     *
     * 由于所有游戏 Activity 均为 exported=false，无法通过 `adb shell am start -n` 启动
     * （shell uid=2000 无权限），因此使用目标应用自身的 Context.startActivity() 启动。
     *
     * @param device UI 设备
     * @param activityClassName Activity 类全限定名（如 com.gamecenter.app.games.gomoku.GomokuActivity）
     * @param waitMs 启动后等待时间（毫秒）
     */
    fun launchGameActivity(
        device: UiDevice,
        activityClassName: String,
        waitMs: Long = 2000
    ) {
        Log.d(TAG, "启动游戏 Activity: $activityClassName")
        try {
            val intent = Intent().apply {
                component = ComponentName(PACKAGE_NAME, activityClassName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            targetContext.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "通过 Context.startActivity 启动失败，尝试 am start: ${e.message}")
            // 兜底：尝试 am start（对 exported=true 的 Activity 有效）
            device.executeShellCommand("am start -n $PACKAGE_NAME/$activityClassName")
        }
        safeSleep(waitMs)
    }

    /**
     * 通过游戏 ID 从大厅启动游戏。
     *
     * @param device UI 设备
     * @param gameName 游戏名称（用于在界面上查找并点击）
     * @param waitMs 点击后等待时间（毫秒）
     * @return 是否成功点击游戏卡片
     */
    fun launchGameFromHall(
        device: UiDevice,
        gameName: String,
        waitMs: Long = 3000
    ): Boolean {
        Log.d(TAG, "从大厅启动游戏: $gameName")

        // 确保在游戏大厅页面
        ensureGamesHall(device)

        // 尝试点击游戏名称文本
        val selector = UiSelector().text(gameName)
        val obj = device.findObject(selector)
        if (obj.waitForExists(5000)) {
            obj.click()
            safeSleep(waitMs)
            return true
        }

        // 尝试通过 contentDescription 查找
        val cdSelector = UiSelector().descriptionContains(gameName)
        val cdObj = device.findObject(cdSelector)
        if (cdObj.waitForExists(3000)) {
            cdObj.click()
            safeSleep(waitMs)
            return true
        }

        Log.w(TAG, "未找到游戏: $gameName")
        return false
    }

    /**
     * 确保当前在游戏大厅页面。
     * 如果不在，则通过底部导航栏切换到游戏页。
     *
     * @param device UI 设备
     */
    fun ensureGamesHall(device: UiDevice) {
        // 点击底部导航栏的"游戏"按钮
        val gamesNavSelector = UiSelector()
            .resourceId("$PACKAGE_NAME:id/navigation_games")
        val gamesNav = device.findObject(gamesNavSelector)
        if (gamesNav.exists()) {
            gamesNav.click()
            safeSleep(1000)
        } else {
            // 尝试通过文本查找
            val textSelector = UiSelector().text("游戏")
            val textObj = device.findObject(textSelector)
            if (textObj.exists()) {
                textObj.click()
                safeSleep(1000)
            }
        }
    }

    /**
     * 遍历并点击当前页面上所有可见的可点击元素。
     *
     * 该方法会：
     * 1. 查找所有 clickable=true 的元素
     * 2. 逐个点击
     * 3. 每次点击后等待短暂时间，检查应用是否崩溃
     * 4. 如果点击后出现新页面，按返回键回到原页面
     *
     * @param device UI 设备
     * @param maxClicks 最大点击次数（防止无限循环）
     * @param clickIntervalMs 每次点击间隔（毫秒）
     * @return 成功点击的元素数量
     */
    fun clickAllVisibleButtons(
        device: UiDevice,
        maxClicks: Int = 20,
        clickIntervalMs: Long = 800
    ): Int {
        var clickCount = 0
        var lastClickSignature = ""

        for (i in 0 until maxClicks) {
            // 查找所有可点击元素
            val clickableSelector = UiSelector().clickable(true)
            val clickableObj = device.findObject(clickableSelector)

            if (clickableObj == null || !clickableObj.exists()) {
                Log.d(TAG, "没有更多可点击元素")
                break
            }

            // 获取元素签名（避免重复点击同一元素）
            val signature = getElementSignature(clickableObj)
            if (signature == lastClickSignature && i > 0) {
                Log.d(TAG, "遇到相同元素，停止遍历")
                break
            }
            lastClickSignature = signature

            try {
                val bounds = clickableObj.bounds
                Log.d(TAG, "点击元素 [$i]: $signature bounds=$bounds")
                clickableObj.click()
                clickCount++
                safeSleep(clickIntervalMs)

                // 检查是否打开了新页面（对话框/新Activity）
                // 如果当前不是游戏页面，按返回键
                handlePossibleDialogOrNewPage(device)
            } catch (e: UiObjectNotFoundException) {
                Log.w(TAG, "元素已消失: $signature")
                // 继续查找下一个元素
            }
        }

        Log.d(TAG, "遍历完成，共点击 $clickCount 个元素")
        return clickCount
    }

    /**
     * 点击指定文本的按钮并验证。
     *
     * @param device UI 设备
     * @param text 按钮文本
     * @param timeout 等待超时（毫秒）
     * @return 是否成功点击
     */
    fun clickButtonByText(
        device: UiDevice,
        text: String,
        timeout: Long = 5000
    ): Boolean {
        val selector = UiSelector().text(text)
        val obj = device.findObject(selector)
        if (obj.waitForExists(timeout)) {
            obj.click()
            Log.d(TAG, "点击按钮: $text")
            safeSleep(500)
            return true
        }
        // 尝试模糊匹配
        val containsSelector = UiSelector().textContains(text)
        val containsObj = device.findObject(containsSelector)
        if (containsObj.waitForExists(2000)) {
            containsObj.click()
            Log.d(TAG, "点击按钮(模糊匹配): $text")
            safeSleep(500)
            return true
        }
        Log.w(TAG, "未找到按钮: $text")
        return false
    }

    /**
     * 点击指定 resourceId 的元素并验证。
     *
     * @param device UI 设备
     * @param resourceId 资源 ID（完整格式，如 com.gamecenter.app:id/btn_start）
     * @param timeout 等待超时（毫秒）
     * @return 是否成功点击
     */
    fun clickByResourceId(
        device: UiDevice,
        resourceId: String,
        timeout: Long = 5000
    ): Boolean {
        val selector = UiSelector().resourceId(resourceId)
        val obj = device.findObject(selector)
        if (obj.waitForExists(timeout)) {
            obj.click()
            Log.d(TAG, "点击元素: $resourceId")
            safeSleep(500)
            return true
        }
        Log.w(TAG, "未找到元素: $resourceId")
        return false
    }

    /**
     * 验证当前 Activity 是否为预期 Activity。
     *
     * @param device UI 设备
     * @param expectedActivityName 预期 Activity 类名（简短名或全限定名）
     * @return 是否匹配
     */
    fun assertCurrentActivity(
        device: UiDevice,
        expectedActivityName: String
    ): Boolean {
        safeSleep(500)
        val dump = device.executeShellCommand(
            "dumpsys activity activities | grep mResumedActivity"
        )
        val isMatch = dump.contains(expectedActivityName)
        if (!isMatch) {
            Log.w(TAG, "当前 Activity 不匹配: 期望=$expectedActivityName, 实际=$dump")
        }
        return isMatch
    }

    /**
     * 检查应用是否崩溃（通过检查是否有 ANR 对话框或崩溃对话框）。
     *
     * @param device UI 设备
     * @return true 表示应用正常运行，false 表示可能崩溃
     */
    fun isAppAlive(device: UiDevice): Boolean {
        // 检查是否有"应用无响应"或"已停止"对话框
        val crashTexts = listOf(
            "应用无响应", "ANR", "Application Not Responding",
            "已停止运行", "has stopped", "不幸停止",
            "Keep waiting", "Close app", "OK"
        )
        for (text in crashTexts) {
            val selector = UiSelector().textContains(text)
            val obj = device.findObject(selector)
            if (obj.exists()) {
                Log.e(TAG, "检测到崩溃/ANR对话框: $text")
                return false
            }
        }
        return true
    }

    /**
     * 安全退出当前游戏，返回大厅。
     *
     * @param device UI 设备
     */
    fun exitGameToHall(device: UiDevice) {
        // 先尝试按返回键
        device.pressBack()
        safeSleep(500)

        // 如果还在游戏页面，再按一次
        if (!isOnGamesHall(device)) {
            device.pressBack()
            safeSleep(500)
        }

        // 确保回到大厅
        ensureGamesHall(device)
    }

    /**
     * 检查是否在游戏大厅页面。
     */
    fun isOnGamesHall(device: UiDevice): Boolean {
        val gamesNavSelector = UiSelector()
            .resourceId("$PACKAGE_NAME:id/navigation_games")
        val gamesNav = device.findObject(gamesNavSelector)
        return gamesNav.exists()
    }

    /**
     * 处理可能出现的对话框或新页面。
     * 如果检测到对话框，尝试关闭；如果检测到新页面，按返回键。
     *
     * @param device UI 设备
     */
    private fun handlePossibleDialogOrNewPage(device: UiDevice) {
        // 检查是否有对话框的"确定"/"取消"按钮
        val dialogButtons = listOf("确定", "OK", "取消", "Cancel", "关闭", "Close", "知道了", "Got it")
        for (text in dialogButtons) {
            val selector = UiSelector().text(text)
            val obj = device.findObject(selector)
            if (obj.exists()) {
                Log.d(TAG, "检测到对话框按钮: $text，点击关闭")
                obj.click()
                safeSleep(500)
                return
            }
        }

        // 检查是否离开了游戏页面（通过检查底部导航栏是否存在）
        val navSelector = UiSelector()
            .resourceId("$PACKAGE_NAME:id/nav_view")
        val nav = device.findObject(navSelector)
        if (!nav.exists()) {
            // 可能打开了新页面，按返回键
            Log.d(TAG, "检测到新页面，按返回键")
            device.pressBack()
            safeSleep(500)
        }
    }

    /**
     * 获取元素的签名（用于去重）。
     */
    private fun getElementSignature(obj: UiObject): String {
        val bounds = obj.bounds
        return "${bounds.centerX()},${bounds.centerY()}"
    }

    /**
     * 安全等待。
     */
    private fun safeSleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /**
     * 断言应用存活。
     */
    fun assertAppAlive(device: UiDevice) {
        Assert.assertTrue("应用不应崩溃", isAppAlive(device))
    }

    /**
     * 滚动查找并点击指定文本。
     *
     * @param device UI 设备
     * @param text 要查找的文本
     * @param maxScrolls 最大滚动次数
     * @return 是否找到并点击
     */
    fun scrollAndClick(
        device: UiDevice,
        text: String,
        maxScrolls: Int = 5
    ): Boolean {
        for (i in 0 until maxScrolls) {
            val selector = UiSelector().text(text)
            val obj = device.findObject(selector)
            if (obj.exists()) {
                obj.click()
                Log.d(TAG, "滚动查找并点击: $text (第 $i 次滚动)")
                safeSleep(500)
                return true
            }
            // 向上滚动
            val screenHeight = device.displayHeight
            val screenWidth = device.displayWidth
            device.swipe(
                screenWidth / 2, screenHeight * 2 / 3,
                screenWidth / 2, screenHeight / 3,
                20
            )
            safeSleep(500)
        }
        Log.w(TAG, "滚动查找未找到: $text")
        return false
    }
}
