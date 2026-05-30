package com.gamecenter.app

import android.content.Context
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObjectNotFoundException
import androidx.test.uiautomator.UiSelector
import org.junit.After
import org.junit.Before

/**
 * 模拟器测试基类。
 *
 * 提供通用的测试 setup/teardown 和 UI 交互辅助方法。
 *
 * @author Software Engineer (Alex)
 * @date 2026-05-27
 */
open class EmulatorTestBase {

    companion object {
        private const val TAG = "EmulatorTestBase"
    }

    /** 测试上下文 */
    protected lateinit var appContext: Context

    /** UI 设备控制器 */
    protected lateinit var device: UiDevice

    /** 测试开始时间（用于计算耗时）*/
    private var testStartTime: Long = 0

    /**
     * 测试前准备。
     *
     * 初始化测试上下文和 UI 设备控制器。
     */
    @Before
    open fun setUp() {
        Log.d(TAG, "=== 测试开始 ===")

        appContext = InstrumentationRegistry.getInstrumentation().targetContext
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        testStartTime = System.currentTimeMillis()

        // 唤醒屏幕
        device.wakeUp()

        // 解锁屏幕（如果需要）
        if (device.isScreenOn) {
            device.pressHome()
        }

        Log.d(TAG, "测试准备完成")
    }

    /**
     * 测试后清理。
     *
     * 释放资源、记录测试耗时。
     */
    @After
    open fun tearDown() {
        val testEndTime = System.currentTimeMillis()
        val duration = testEndTime - testStartTime

        Log.d(TAG, "=== 测试结束 ===")
        Log.d(TAG, "测试耗时: ${duration}ms")

        // 返回主屏幕
        device.pressHome()

        // 清理测试数据（如果需要）
        // cleanupTestData()
    }

    /**
     * 点击屏幕坐标。
     *
     * @param x X 坐标
     * @param y Y 坐标
     */
    protected fun tap(x: Int, y: Int) {
        Log.d(TAG, "点击坐标: ($x, $y)")
        device.click(x, y)
    }

    /**
     * 点击 UI 元素（通过 resourceId）。
     *
     * @param resourceId 资源 ID（如 "com.gamecenter.app:id/btn_start"）
     * @param timeout 超时时间（毫秒）
     * @return 是否点击成功
     */
    protected fun tapByResourceId(resourceId: String, timeout: Long = 5000): Boolean {
        Log.d(TAG, "点击元素: $resourceId")

        return try {
            val selector = UiSelector().resourceId(resourceId)
            val obj = device.findObject(selector)

            if (obj.waitForExists(timeout)) {
                obj.click()
                Log.d(TAG, "点击成功: $resourceId")
                true
            } else {
                Log.w(TAG, "元素未找到: $resourceId")
                false
            }
        } catch (e: UiObjectNotFoundException) {
            Log.e(TAG, "元素未找到: $resourceId", e)
            false
        }
    }

    /**
     * 点击 UI 元素（通过文本）。
     *
     * @param text 文本内容
     * @param timeout 超时时间（毫秒）
     * @return 是否点击成功
     */
    protected fun tapByText(text: String, timeout: Long = 5000): Boolean {
        Log.d(TAG, "点击文本: $text")

        return try {
            val selector = UiSelector().text(text)
            val obj = device.findObject(selector)

            if (obj.waitForExists(timeout)) {
                obj.click()
                Log.d(TAG, "点击成功: $text")
                true
            } else {
                Log.w(TAG, "文本未找到: $text")
                false
            }
        } catch (e: UiObjectNotFoundException) {
            Log.e(TAG, "文本未找到: $text", e)
            false
        }
    }

    /**
     * 等待 UI 元素出现（通过 resourceId）。
     *
     * @param resourceId 资源 ID
     * @param timeout 超时时间（毫秒）
     * @return 元素是否存在
     */
    protected fun waitForElementByResourceId(resourceId: String, timeout: Long = 5000): Boolean {
        Log.d(TAG, "等待元素: $resourceId (timeout=${timeout}ms)")

        return try {
            val selector = UiSelector().resourceId(resourceId)
            val obj = device.findObject(selector)

            if (obj.waitForExists(timeout)) {
                Log.d(TAG, "元素已出现: $resourceId")
                true
            } else {
                Log.w(TAG, "等待超时: $resourceId")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "等待元素异常: $resourceId", e)
            false
        }
    }

    /**
     * 等待 UI 元素出现（通过文本）。
     *
     * @param text 文本内容
     * @param timeout 超时时间（毫秒）
     * @return 元素是否存在
     */
    protected fun waitForElementByText(text: String, timeout: Long = 5000): Boolean {
        Log.d(TAG, "等待文本: $text (timeout=${timeout}ms)")

        return try {
            val selector = UiSelector().text(text)
            val obj = device.findObject(selector)

            if (obj.waitForExists(timeout)) {
                Log.d(TAG, "文本已出现: $text")
                true
            } else {
                Log.w(TAG, "等待超时: $text")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "等待文本异常: $text", e)
            false
        }
    }

    /**
     * 输入文本。
     *
     * @param text 要输入的文本
     */
    protected fun inputText(text: String) {
        Log.d(TAG, "输入文本: $text")
        device.pressKeyCode(android.view.KeyEvent.KEYCODE_MOVE_END)
        // 使用 ADB 输入文本（需要 root 权限或特殊设置）
        // 这里使用 UiAutomator 的方式
    }

    /**
     * 截图。
     *
     * @param fileName 截图文件名（不含路径）
     * @return 截图文件路径
     */
    protected fun takeScreenshot(fileName: String): String? {
        return try {
            val screenshotDir = appContext.getExternalFilesDir(null)
            val screenshotFile = java.io.File(screenshotDir, fileName)

            val success = device.takeScreenshot(screenshotFile, 0.5f, 50)

            if (success) {
                Log.d(TAG, "截图成功: ${screenshotFile.absolutePath}")
                screenshotFile.absolutePath
            } else {
                Log.w(TAG, "截图失败")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "截图异常", e)
            null
        }
    }

    /**
     * 滑动屏幕。
     *
     * @param startX 起始 X 坐标
     * @param startY 起始 Y 坐标
     * @param endX 结束 X 坐标
     * @param endY 结束 Y 坐标
     * @param steps 滑动步数（越大越慢）
     */
    protected fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, steps: Int = 10) {
        Log.d(TAG, "滑动: ($startX, $startY) -> ($endX, $endY)")
        device.swipe(startX, startY, endX, endY, steps)
    }

    /**
     * 按 HOME 键。
     */
    protected fun pressHome() {
        Log.d(TAG, "按 HOME 键")
        device.pressHome()
    }

    /**
     * 按 BACK 键。
     */
    protected fun pressBack() {
        Log.d(TAG, "按 BACK 键")
        device.pressBack()
    }

    /**
     * 按菜单键。
     */
    protected fun pressMenu() {
        Log.d(TAG, "按菜单键")
        device.pressMenu()
    }

    /**
     * 检查模块是否已安装。
     *
     * @param moduleId 模块 ID
     * @return 是否已安装
     */
    protected fun isModuleInstalled(moduleId: String): Boolean {
        val prefs = appContext.getSharedPreferences("module_manager_prefs", Context.MODE_PRIVATE)
        val installed = prefs.getStringSet("installed_modules", emptySet()) ?: emptySet()
        return installed.contains(moduleId)
    }

    /**
     * 获取已安装的模块列表。
     *
     * @return 已安装的模块 ID 列表
     */
    protected fun getInstalledModules(): Set<String> {
        val prefs = appContext.getSharedPreferences("module_manager_prefs", Context.MODE_PRIVATE)
        return prefs.getStringSet("installed_modules", emptySet()) ?: emptySet()
    }
}
