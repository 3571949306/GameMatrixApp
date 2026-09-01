package com.gamecenter.app.modules

import android.os.Looper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.atomic.AtomicInteger

/**
 * 出厂预装安装完成监听器测试（冒烟缺陷守卫：Games 大厅 Classics 首次进入
 * "No games available"，需手动进一次商店才刷新）。
 *
 * 缺陷链：首启预装是重 IO（31 个 APK 提取 + SHA/签名校验 + dex 装载），可能晚于
 * GamesFragment 首次 onResume；底部导航 add/hide/show 切换不触发 onResume，
 * 预装完成后大厅没有任何刷新触发。本测试锁定修复的三个契约：
 * 1. 完成前注册的监听器在完成时收到一次且仅一次回调；
 * 2. 完成后注册的监听器立即收到一次补偿回调（防错过窗口）；
 * 3. 注销后的监听器不再回调；单个监听器抛异常不影响其他监听器。
 *
 * 真实安装链路（installBundledModulesIfNeeded）含真实 APK 装载，无法在
 * Robolectric 下运行，故经 internal 接缝 [ModuleManager.notifyBundledInstallCompleted]
 * 直接驱动完成事件（与 parseModulesArray / shouldLoadExternal 的测试接缝风格一致）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BundledInstallListenerTest {

    private val registeredListeners = mutableListOf<Runnable>()

    @Before
    fun setUp() {
        // ModuleManager 是进程级单例：重置完成标记与监听器列表，保证测试不受
        // 同 JVM 内方法执行顺序/其他测试类影响
        resetBundledInstallState()
        Shadows.shadowOf(Looper.getMainLooper()).pause()
    }

    private fun resetBundledInstallState() {
        val instance = ModuleManager::class.java.getDeclaredField("INSTANCE").get(null)
        // bundledInstallCompleted 是 private var，可反射重置；bundledInstallListeners
        // 是 private val（编译为 static final，反射 set 非法），改为原地 clear()
        ModuleManager::class.java.getDeclaredField("bundledInstallCompleted").apply {
            isAccessible = true
            set(instance, false)
        }
        ModuleManager::class.java.getDeclaredField("bundledInstallListeners").apply {
            isAccessible = true
            (get(instance) as java.util.concurrent.CopyOnWriteArrayList<Runnable>).clear()
        }
    }

    @After
    fun tearDown() {
        for (listener in registeredListeners) {
            ModuleManager.removeBundledInstallListener(listener)
        }
        registeredListeners.clear()
        ShadowLooper.idleMainLooper()
        resetBundledInstallState()
    }

    private fun newListener(counter: AtomicInteger): Runnable {
        val listener = Runnable { counter.incrementAndGet() }
        registeredListeners.add(listener)
        return listener
    }

    @Test
    fun `listener registered before completion fires exactly once`() {
        val counter = AtomicInteger(0)
        val listener = newListener(counter)
        ModuleManager.addBundledInstallListener(listener)
        ShadowLooper.idleMainLooper()
        assertEquals("完成前不得提前回调", 0, counter.get())

        ModuleManager.notifyBundledInstallCompleted()
        ShadowLooper.idleMainLooper()
        assertEquals("完成时应回调一次", 1, counter.get())

        // 幂等：重复标记完成不再派发（installBundledModulesIfNeeded 每进程只跑一次）
        ModuleManager.notifyBundledInstallCompleted()
        ShadowLooper.idleMainLooper()
        assertEquals("重复标记完成不得重复回调", 1, counter.get())
    }

    @Test
    fun `listener registered after completion gets immediate callback`() {
        // 无论本 JVM 内预装此前是否已完成，先确保完成标记就位
        ModuleManager.notifyBundledInstallCompleted()
        ShadowLooper.idleMainLooper()

        val counter = AtomicInteger(0)
        val listener = newListener(counter)
        ModuleManager.addBundledInstallListener(listener)
        ShadowLooper.idleMainLooper()
        assertEquals("完成后注册的监听器应立即补偿回调一次（防错过窗口）", 1, counter.get())
    }

    @Test
    fun `removed listener does not receive callback`() {
        ModuleManager.notifyBundledInstallCompleted()
        ShadowLooper.idleMainLooper()

        val counter = AtomicInteger(0)
        val listener = newListener(counter)
        ModuleManager.addBundledInstallListener(listener)
        // 在主线程 idle 之前注销：posted 回调执行时监听器已不在列表中
        ModuleManager.removeBundledInstallListener(listener)
        ShadowLooper.idleMainLooper()
        assertEquals("注销后的监听器不得收到回调", 0, counter.get())
    }

    @Test
    fun `exception in one listener does not block others`() {
        ModuleManager.notifyBundledInstallCompleted()
        ShadowLooper.idleMainLooper()

        val counter = AtomicInteger(0)
        val broken = Runnable { throw RuntimeException("boom") }
        registeredListeners.add(broken)
        ModuleManager.addBundledInstallListener(broken)
        val healthy = newListener(counter)
        ModuleManager.addBundledInstallListener(healthy)
        ShadowLooper.idleMainLooper()
        assertEquals("坏监听器抛异常不得阻断其他监听器", 1, counter.get())
    }
}
