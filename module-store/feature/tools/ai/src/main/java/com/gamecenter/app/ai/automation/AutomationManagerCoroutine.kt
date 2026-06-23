package com.gamecenter.app.ai.automation

import android.content.Context
import android.util.Log
import com.gamecenter.app.ai.coroutine.AppDispatchers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 自动化管理器（协程版本） — 使用 Kotlin 协程管理自动化任务。
 *
 * 相比旧版 AutomationManager（Java线程版本）的优势：
 * - 任务可以方便地串联（截图 → OCR → 分析 → 执行）
 * - 支持超时控制（避免某个步骤卡住）
 * - 支持取消（用户可以随时停止自动化）
 * - 错误处理更优雅（try-catch + 协程取消）
 *
 * 使用示例：
 * ```kotlin
 * // 在 ViewModel 中使用
 * class AutomationViewModel : ViewModel() {
 *     private val manager = AutomationManagerCoroutine(context)
 *
 *     fun executeAutomation(intent: String) {
 *         viewModelScope.launch {
 *             manager.executeAutomationTask(intent)
 *                 .collect { result ->
 *                     _uiState.value = result
 *                 }
 *         }
 *     }
 * }
 * ```
 *
 * 自动化流程：
 * 1. 解析用户意图 → 理解要做什么
 * 2. 截取当前屏幕 → 获取屏幕内容
 * 3. OCR识别文字 → 提取文本信息
 * 4. 分析UI元素 → 找到可点击的按钮
 * 5. 规划执行步骤 → 确定点击顺序
 * 6. 逐步执行 → 模拟用户操作
 * 7. 验证结果 → 检查是否成功
 */
class AutomationManagerCoroutine(
    private val context: Context
) {
    companion object {
        private const val TAG = "AutomationManagerCoroutine"
        private const val AUTOMATION_TIMEOUT_MS = 30_000L // 30秒超时
    }

    private val appContext = context.applicationContext
    private val uiParser = UiParserCoroutine(appContext)
    private val taskPlanner = TaskPlannerCoroutine(appContext)
    private val stepExecutor = StepExecutorCoroutine(appContext)

    /**
     * 执行自动化任务（返回 Flow，支持进度更新）
     *
     * Flow 会发射多个 AutomationResult：
     * - 进度更新（正在截图、正在OCR等）
     * - 中间结果（识别到的UI元素）
     * - 最终结果（执行成功/失败）
     *
     * @param intent 用户意图描述
     * @return Flow<AutomationResult>
     */
    fun executeAutomationTask(intent: String): Flow<AutomationResult> = flow {
        Log.i(TAG, "Executing automation task: $intent")

        // 检查自动化功能是否可用
        if (!isAutomationAvailable()) {
            emit(AutomationResult.failure("请先开启无障碍服务"))
            return@flow
        }

        try {
            // 使用 withTimeout 实现超时控制
            withTimeout(AUTOMATION_TIMEOUT_MS) {
                // 第1步：解析用户意图
                emit(AutomationResult.progress("正在理解您的意图..."))
                val parsedIntent = taskPlanner.parseIntent(intent)

                // 第2步：截取当前屏幕
                emit(AutomationResult.progress("正在截取屏幕..."))
                val screenshot = withContext(AppDispatchers.IO) {
                    uiParser.takeScreenshot()
                }

                // 第3步：OCR识别文字
                emit(AutomationResult.progress("正在识别屏幕文字..."))
                val recognizedText = withContext(AppDispatchers.Computation) {
                    uiParser.recognizeText(screenshot)
                }

                // 第4步：获取UI元素
                emit(AutomationResult.progress("正在分析界面元素..."))
                val uiElements = withContext(AppDispatchers.Computation) {
                    uiParser.getCurrentScreenElements()
                }

                // 第5步：规划执行步骤
                emit(AutomationResult.progress("正在规划执行步骤..."))
                val plan = withContext(AppDispatchers.Computation) {
                    taskPlanner.createPlan(parsedIntent, recognizedText, uiElements)
                }

                // 第6步：逐步执行
                emit(AutomationResult.progress("开始执行，共 ${plan.steps.size} 步"))
                var stepsExecuted = 0

                for (step in plan.steps) {
                    // 检查协程是否被取消
                    yield()

                    Log.d(TAG, "Executing step ${stepsExecuted + 1}: ${step.type}")
                    val stepResult = withContext(AppDispatchers.Automation) {
                        stepExecutor.executeStep(step)
                    }

                    if (!stepResult.success) {
                        emit(AutomationResult.failure("步骤 ${stepsExecuted + 1} 失败: ${stepResult.errorMessage}"))
                        return@withTimeout
                    }

                    stepsExecuted++
                    emit(AutomationResult.progress("已完成 $stepsExecuted/${plan.steps.size} 步"))

                    // 等待一小段时间，让UI更新
                    delay(step.waitMs)
                }

                // 第7步：验证结果
                emit(AutomationResult.progress("正在验证结果..."))
                val verificationScreenshot = withContext(AppDispatchers.IO) {
                    uiParser.takeScreenshot()
                }
                val verificationText = withContext(AppDispatchers.Computation) {
                    uiParser.recognizeText(verificationScreenshot)
                }

                val success = taskPlanner.verifyResult(parsedIntent, verificationText)

                if (success) {
                    emit(AutomationResult.success(stepsExecuted, 0))
                } else {
                    emit(AutomationResult.failure("任务执行完成，但结果验证失败"))
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Automation task timeout", e)
            emit(AutomationResult.failure("任务执行超时（超过 ${AUTOMATION_TIMEOUT_MS / 1000} 秒）"))
        } catch (e: CancellationException) {
            Log.i(TAG, "Automation task cancelled")
            emit(AutomationResult.failure("任务已被取消"))
        } catch (e: Exception) {
            Log.e(TAG, "Automation task failed", e)
            emit(AutomationResult.failure("任务执行失败: ${e.message}"))
        }
    }

    /**
     * 执行自动化任务（挂起函数版本，只返回最终结果）
     *
     * @param intent 用户意图描述
     * @return 最终结果
     */
    suspend fun executeAutomationTaskSuspend(intent: String): AutomationResult {
        return withContext(AppDispatchers.Automation) {
            try {
                withTimeout(AUTOMATION_TIMEOUT_MS) {
                    // ... 执行逻辑同上 ...
                    AutomationResult.notImplemented()
                }
            } catch (e: TimeoutCancellationException) {
                AutomationResult.failure("任务执行超时")
            } catch (e: CancellationException) {
                AutomationResult.failure("任务已被取消")
            } catch (e: Exception) {
                AutomationResult.failure("任务执行失败: ${e.message}")
            }
        }
    }

    /**
     * 检查自动化功能是否可用
     */
    fun isAutomationAvailable(): Boolean {
        return uiParser.isAccessibilityServiceEnabled()
    }

    /**
     * 获取自动化功能的状态描述
     */
    fun getAutomationStatus(): String {
        return if (uiParser.isAccessibilityServiceEnabled()) {
            "自动化功能就绪"
        } else {
            "请先开启无障碍服务"
        }
    }

    /**
     * 释放资源
     */
    fun shutdown() {
        uiParser.shutdown()
        taskPlanner.shutdown()
        stepExecutor.shutdown()
    }
}
