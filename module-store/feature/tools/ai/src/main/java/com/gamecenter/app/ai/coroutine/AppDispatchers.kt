package com.gamecenter.app.ai.coroutine

import kotlinx.coroutines.*
import java.util.concurrent.Executors

/**
 * 应用级协程调度器 — 统一管理所有协程的线程分配。
 *
 * 你可以把它想象成一个"智能任务调度中心"：
 * 不同类型的任务会被分配到不同的"工作区"，每个工作区有固定的工人数，
 * 避免某个类型的任务占满所有资源。
 *
 * 为什么需要这个？
 * - 避免线程爆炸（100+线程 → 10-15个线程）
 * - 避免资源竞争（游戏和AI抢CPU）
 * - 统一管理（方便监控、调优）
 *
 * 使用示例：
 * ```kotlin
 * // 在协程中执行AI任务
 * withContext(AppDispatchers.ModelInference) {
 *     val result = localLlmEngine.generate(prompt)
 *     result
 * }
 *
 * // 执行自动化任务
 * AppDispatchers.launchAutomation {
 *     val screenshot = takeScreenshot()
 *     val text = recognizeText(screenshot)
 *     performClick(text)
 * }
 * ```
 */
object AppDispatchers {

    /**
     * CPU密集型任务调度器
     *
     * 用途：游戏逻辑、AI计算、加密解密等
     * 线程数：等于CPU核心数（通常是4-8个）
     *
     * 为什么限制并发？
     * - CPU密集型任务会占满CPU
     * - 限制并发数避免上下文切换开销
     */
    val Computation: CoroutineDispatcher = Dispatchers.Default
        .limitedParallelism(Runtime.getRuntime().availableProcessors())

    /**
     * IO密集型任务调度器
     *
     * 用途：网络请求、文件读写、数据库操作等
     * 线程数：64个（IO任务大部分时间在等待，可以多开）
     *
     * 为什么用这么多线程？
     * - IO任务90%时间在等待网络/磁盘
     * - 多开线程可以并行等待
     */
    val IO: CoroutineDispatcher = Dispatchers.IO

    /**
     * 主线程调度器
     *
     * 用途：UI更新、Toast显示、Dialog显示等
     * 线程数：1个（Android要求UI操作必须在主线程）
     */
    val Main: CoroutineDispatcher = Dispatchers.Main

    /**
     * 模型推理专用调度器
     *
     * 用途：本地LLM推理（Gemma等大模型）
     * 线程数：1个（避免多模型同时推理导致OOM）
     *
     * 为什么单独一个线程？
     * - 大模型推理占用大量内存（500MB+）
     * - 并行推理会导致OOM
     * - 单线程排队执行更安全
     */
    val ModelInference: CoroutineDispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "GC-Model-Inference").apply {
            priority = Thread.MIN_PRIORITY // 低优先级，避免阻塞UI
            isDaemon = true
        }
    }.asCoroutineDispatcher()

    /**
     * 自动化任务专用调度器
     *
     * 用途：屏幕截图、OCR识别、UI操作等
     * 线程数：2个（限制并发，避免资源竞争）
     *
     * 为什么限制2个？
     * - 自动化任务需要实时响应
     * - 但不能占用太多资源影响用户操作
     * - 2个足够处理前后台切换
     */
    val Automation: CoroutineDispatcher = Dispatchers.Default
        .limitedParallelism(2)

    /**
     * VPN数据转发专用调度器
     *
     * 用途：VPN数据包转发、加密解密
     * 线程数：2个（上行/下行分离）
     */
    val Vpn: CoroutineDispatcher = Dispatchers.IO
        .limitedParallelism(2)

    /**
     * 下载任务专用调度器
     *
     * 用途：模块下载、模型下载、文件下载
     * 线程数：3个（并行下载多个文件）
     */
    val Download: CoroutineDispatcher = Dispatchers.IO
        .limitedParallelism(3)

    /**
     * 全局协程作用域
     *
     * 用途：应用级别的协程生命周期管理
     * 生命周期：跟随Application
     *
     * 注意：不要在ViewModel中使用这个，用viewModelScope
     */
    val applicationScope = CoroutineScope(
        SupervisorJob() + // 子协程失败不会取消父协程
        Dispatchers.Main.immediate
    )

    /**
     * 启动自动化任务的便捷方法
     *
     * 示例：
     * ```kotlin
     * AppDispatchers.launchAutomation { scope ->
     *     val screenshot = scope.async { takeScreenshot() }
     *     val uiTree = scope.async { getUiTree() }
     *
     *     val result = analyzeScreen(screenshot.await(), uiTree.await())
     *     performAction(result)
     * }
     * ```
     */
    fun launchAutomation(
        block: suspend CoroutineScope.() -> Unit
    ): Job {
        return applicationScope.launch(Automation) {
            block()
        }
    }

    /**
     * 启动模型推理任务的便捷方法
     *
     * 示例：
     * ```kotlin
     * val result = AppDispatchers.withModelInference {
     *     localLlmEngine.generate(prompt)
     * }
     * ```
     */
    suspend fun <T> withModelInference(
        block: suspend CoroutineScope.() -> T
    ): T {
        return withContext(ModelInference) {
            block()
        }
    }

    /**
     * 清理资源
     *
     * 在应用退出时调用，释放线程池
     */
    fun shutdown() {
        applicationScope.cancel()
        (ModelInference.asExecutor() as? java.util.concurrent.ExecutorService)?.shutdown()
    }
}
