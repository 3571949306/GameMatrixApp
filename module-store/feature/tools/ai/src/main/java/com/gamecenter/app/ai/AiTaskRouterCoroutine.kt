package com.gamecenter.app.ai

import android.content.Context
import android.util.Log
import com.gamecenter.app.ai.cloud.AiApiClient
import com.gamecenter.app.ai.coroutine.AppDispatchers
import com.gamecenter.app.ai.data.*
import com.gamecenter.app.ai.local.LocalAiProcessor
import com.gamecenter.app.ai.local.LocalLlmOutputGuard
import com.gamecenter.app.ai.local.MediaPipeLocalLlmEngine
import com.gamecenter.app.ai.model.AiModelDownloadManager
import com.gamecenter.app.utils.NetworkErrorHandler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * AI 任务路由器（协程版本） — 使用 Kotlin 协程管理 AI 任务。
 *
 * 相比旧版 AiTaskRouter（Java线程池版本）的优势：
 * - 轻量级：协程只需几KB内存，线程需要~1MB
 * - 结构化并发：任务之间可以协作、取消、超时
 * - 同步风格代码：没有回调地狱
 * - 自动切换线程：withContext 自动切换到合适的线程
 *
 * 使用示例：
 * ```kotlin
 * // 在 ViewModel 中使用
 * class AiViewModel : ViewModel() {
 *     private val router = AiTaskRouterCoroutine(context)
 *
 *     fun submitTask(taskType: String, input: String) {
 *         viewModelScope.launch {
 *             router.executeTask(taskType, input)
 *                 .collect { result ->
 *                     // 更新UI
 *                     _uiState.value = result
 *                 }
 *         }
 *     }
 * }
 * ```
 *
 * 路由优先级：
 * 1. 本地 LLM（Gemma）→ 在 ModelInference 线程执行
 * 2. 本地规则引擎 → 在 Computation 线程执行
 * 3. 云端 API → 在 IO 线程执行
 */
class AiTaskRouterCoroutine(
    private val context: Context
) {
    companion object {
        private const val TAG = "AiTaskRouterCoroutine"
    }

    private val appContext = context.applicationContext
    private val aiPrefs = AiPreferences(appContext)
    private val modelDownloadManager = AiModelDownloadManager()
    private val localLlmEngine = MediaPipeLocalLlmEngine()

    // 统计信息
    private var totalTasks = 0
    private var localTasks = 0
    private var cloudTasks = 0

    /**
     * 执行 AI 任务（返回 Flow，支持进度更新）
     *
     * Flow 的优势：
     * - 可以发射多个值（进度、中间结果、最终结果）
     * - 自动取消（协程取消时 Flow 也取消）
     * - 背压支持（生产者和消费者速度不匹配时自动处理）
     *
     * @param taskType 任务类型（ocr/summary/translate/rewrite/qa等）
     * @param input 用户输入
     * @return Flow<AiResult>，可以 collect 获取结果
     */
    fun executeTask(taskType: String, input: String): Flow<AiResult> = flow {
        totalTasks++
        val task = AiTask(taskType, input)

        // 第1步：尝试本地处理
        val localResult = tryLocalProcessing(task)
        if (localResult != null) {
            if (localResult.success) {
                localTasks++
                emit(localResult)
                return@flow
            } else if (shouldFallbackToCloud(localResult)) {
                Log.w(TAG, "Local failed, falling back to cloud: ${localResult.message}")
                // 继续执行云端
            } else {
                emit(localResult)
                return@flow
            }
        }

        // 第2步：检查网络
        if (!NetworkErrorHandler.isNetworkAvailable(appContext)) {
            emit(AiResult.fail("当前无网络连接")
                .errorCode(AiErrorCode.NETWORK_ERROR)
                .build())
            return@flow
        }

        // 第3步：检查额度
        if (!aiPrefs.hasFreeQuota()) {
            emit(AiResult.fail("今日免费额度已用完")
                .errorCode(AiErrorCode.QUOTA_EXCEEDED)
                .build())
            return@flow
        }

        // 第4步：检查API Key
        if (aiPrefs.apiKey.isEmpty()) {
            emit(AiResult.fail("未配置 API Key")
                .errorCode(AiErrorCode.NO_API_KEY)
                .build())
            return@flow
        }

        // 第5步：调用云端API
        try {
            val config = buildConfigForTask(task)
            val client = AiApiClient(config)
            val prompt = buildPrompt(taskType, input)

            val result = client.chatSync("你是一个有用的助手。", prompt)

            if (result.success) {
                cloudTasks++
                aiPrefs.incrementUsage()
                emit(result)
            } else {
                cloudTasks++
                emit(result)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloud AI task failed", e)
            emit(AiResult.fail("请求失败: ${e.message}")
                .errorCode(AiErrorCode.NETWORK_ERROR)
                .build())
        }
    }.flowOn(AppDispatchers.IO) // 整个 Flow 在 IO 线程执行

    /**
     * 执行 AI 任务（挂起函数版本，只返回最终结果）
     *
     * 使用示例：
     * ```kotlin
     * val result = router.executeTaskSuspend("translate", "Hello")
     * println(result.content)
     * ```
     *
     * @param taskType 任务类型
     * @param input 用户输入
     * @return 最终结果
     */
    suspend fun executeTaskSuspend(taskType: String, input: String): AiResult {
        return withContext(AppDispatchers.IO) {
            totalTasks++
            val task = AiTask(taskType, input)

            // 第1步：尝试本地处理
            val localResult = tryLocalProcessing(task)
            if (localResult != null) {
                if (localResult.success) {
                    localTasks++
                    return@withContext localResult
                } else if (shouldFallbackToCloud(localResult)) {
                    Log.w(TAG, "Local failed, falling back to cloud")
                } else {
                    return@withContext localResult
                }
            }

            // 第2-5步：检查并调用云端
            checkAndCallCloud(task)
        }
    }

    /**
     * 尝试本地处理（在 Computation 线程执行）
     */
    private suspend fun tryLocalProcessing(task: AiTask): AiResult? {
        if (!aiPrefs.isLocalFirst) return null

        // 优先尝试本地 LLM
        val llmResult = tryLocalLlm(task)
        if (llmResult != null) return llmResult

        // 回退到规则引擎
        return withContext(AppDispatchers.Computation) {
            tryLocalRules(task)
        }
    }

    /**
     * 尝试本地 LLM 推理（在 ModelInference 线程执行）
     *
     * 使用专用线程的原因：
     * - 大模型推理占用大量内存（500MB+）
     * - 并行推理会导致 OOM
     * - 单线程排队执行更安全
     */
    private suspend fun tryLocalLlm(task: AiTask): AiResult? {
        val selectedModelId = aiPrefs.localModel
        if ("on-device" == selectedModelId) return null

        if (!supportsLocalLlm(task.taskType)) return null

        val model = aiPrefs.localModelInfo ?: return AiResult.fail("当前本地模型缺少运行元数据")
            .source("local-llm")
            .errorCode(AiErrorCode.LOCAL_LLM_ERROR)
            .build()

        if ("mediapipe-llm" != model.runtime) return AiResult.fail("当前本地模型运行时暂不支持: ${model.runtime}")
            .source("local-llm")
            .errorCode(AiErrorCode.LOCAL_LLM_ERROR)
            .build()

        if (!modelDownloadManager.isDownloaded(appContext, model)) return AiResult.fail("本地模型尚未下载完成")
            .source("local-llm")
            .errorCode(AiErrorCode.LOCAL_LLM_ERROR)
            .build()

        if (!hasEnoughMemory(model.minRamMb)) return AiResult.fail("设备内存不足")
            .source("local-gemma")
            .errorCode(AiErrorCode.LOCAL_LLM_LOW_MEMORY)
            .build()

        // 在专用线程执行模型推理
        return withContext(AppDispatchers.ModelInference) {
            try {
                localLlmEngine.load(appContext, modelDownloadManager.getModelFile(appContext, model))
                val output = localLlmEngine.generate(buildPrompt(task.taskType, task.input))

                val guardMessage = LocalLlmOutputGuard.validate(output)
                if (guardMessage != null) {
                    AiResult.fail(guardMessage)
                        .source("local-gemma")
                        .errorCode(AiErrorCode.LOCAL_LLM_DEGENERATED_OUTPUT)
                        .build()
                } else {
                    AiResult.success(output).source("local-llm").build()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Local Gemma task failed", t)
                AiResult.fail("本地模型推理失败: ${t.message}")
                    .source("local-llm")
                    .errorCode(AiErrorCode.LOCAL_LLM_ERROR)
                    .build()
            }
        }
    }

    /**
     * 本地规则引擎处理
     */
    private fun tryLocalRules(task: AiTask): AiResult? {
        return when (task.taskType) {
            "ocr", "ocr_clean" -> LocalAiProcessor.processOcrResult(task.input)
            "summary" -> LocalAiProcessor.simpleSummarize(task.input, 10)
            "translate" -> {
                if (aiPrefs.apiKey.isNotEmpty()) null
                else LocalAiProcessor.translateText(task.input)
            }
            "rewrite" -> {
                if (aiPrefs.apiKey.isNotEmpty()) null
                else LocalAiProcessor.polishText(task.input)
            }
            "qa", "qa_pairs" -> {
                if (aiPrefs.apiKey.isNotEmpty()) null
                else LocalAiProcessor.generateQaPairs(task.input, 5)
            }
            "keywords" -> LocalAiProcessor.extractKeywords(task.input)
            "classify" -> LocalAiProcessor.classifyText(task.input)
            "template" -> AiResult.success(task.input).source("local").build()
            else -> {
                val cmd = LocalAiProcessor.recognizeCommand(task.input)
                if (cmd.isKnown) {
                    when (cmd.type) {
                        "summarize" -> LocalAiProcessor.simpleSummarize(task.input, 10)
                        "translate" -> if (aiPrefs.apiKey.isNotEmpty()) null else LocalAiProcessor.translateText(task.input)
                        "rewrite" -> if (aiPrefs.apiKey.isNotEmpty()) null else LocalAiProcessor.polishText(task.input)
                        "qa_pairs" -> if (aiPrefs.apiKey.isNotEmpty()) null else LocalAiProcessor.generateQaPairs(task.input, 5)
                        "keywords" -> LocalAiProcessor.extractKeywords(task.input)
                        "classify" -> LocalAiProcessor.classifyText(task.input)
                        else -> null
                    }
                } else null
            }
        }
    }

    /**
     * 检查网络、额度、API Key，然后调用云端
     */
    private suspend fun checkAndCallCloud(task: AiTask): AiResult {
        if (!NetworkErrorHandler.isNetworkAvailable(appContext)) {
            return AiResult.fail("当前无网络连接")
                .errorCode(AiErrorCode.NETWORK_ERROR)
                .build()
        }

        if (!aiPrefs.hasFreeQuota()) {
            return AiResult.fail("今日免费额度已用完")
                .errorCode(AiErrorCode.QUOTA_EXCEEDED)
                .build()
        }

        if (aiPrefs.apiKey.isEmpty()) {
            return AiResult.fail("未配置 API Key")
                .errorCode(AiErrorCode.NO_API_KEY)
                .build()
        }

        return try {
            val config = buildConfigForTask(task)
            val client = AiApiClient(config)
            val prompt = buildPrompt(task.taskType, task.input)

            val result = client.chatSync("你是一个有用的助手。", prompt)

            cloudTasks++
            if (result.success) {
                aiPrefs.incrementUsage()
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Cloud AI task failed", e)
            AiResult.fail("请求失败: ${e.message}")
                .errorCode(AiErrorCode.NETWORK_ERROR)
                .build()
        }
    }

    /**
     * 判断是否应该回退到云端
     */
    private fun shouldFallbackToCloud(localResult: AiResult): Boolean {
        if (localResult.success) return false
        return localResult.hasErrorCode(AiErrorCode.LOCAL_LLM_DEGENERATED_OUTPUT) ||
                localResult.hasErrorCode(AiErrorCode.LOCAL_LLM_ERROR)
    }

    /**
     * 判断任务类型是否支持本地 LLM
     */
    private fun supportsLocalLlm(taskType: String): Boolean {
        return taskType in listOf("summary", "translate", "rewrite", "qa", "qa_pairs", "keywords", "classify", "chat")
    }

    /**
     * 检查设备内存是否足够
     */
    private fun hasEnoughMemory(minRamMb: Int): Boolean {
        return try {
            val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            val info = android.app.ActivityManager.MemoryInfo()
            if (am == null) return true
            am.getMemoryInfo(info)
            val availableMb = info.availMem / 1024L / 1024L
            val safeAvailableMb = availableMb - 500
            safeAvailableMb > 0 && safeAvailableMb >= minRamMb
        } catch (e: Exception) {
            true
        }
    }

    /**
     * 构建供应商配置
     */
    private fun buildConfigForTask(task: AiTask): AiProviderConfig {
        val providers = AiPreferences.getAvailableProviders(appContext)
        for (p in providers) {
            if (p.providerName == aiPrefs.selectedProvider && p.modelName == aiPrefs.selectedModel) {
                return p
            }
        }
        for (p in providers) {
            if (!p.localOnly && p.enabled) {
                return p
            }
        }
        return AiProviderConfig.localConfig()
    }

    /**
     * 构建提示词
     */
    private fun buildPrompt(taskType: String, input: String): String {
        return when (taskType) {
            "ocr" -> "请对以下OCR识别结果进行清洗和格式化，修正错别字和乱码，保持原文结构：\n\n$input"
            "summary" -> "请对以下文本进行摘要，提取要点，简洁明了：\n\n$input"
            "translate" -> "请将以下文本翻译成中文，保持原意：\n\n$input"
            "rewrite" -> "请对以下文本进行润色，使其更通顺、专业：\n\n$input"
            "qa_pairs", "qa" -> "请根据以下文本，生成5个问答对（问题和答案），用于复习和测试：\n\n$input"
            "chat" -> """你是一个运行在手机本地的中文 AI 助手。请用简体中文直接回答。
规则：
1. 不要复述用户输入。
2. 不要输出无意义数字、乱码、重复字符或循环片段。
3. 常规回答控制在800字以内；用户要求长文、方案或分析时可以更完整，但要分段清楚。
4. 如果无法可靠回答，请直接说明不确定，并给出可验证的建议。

用户问题：$input"""
            else -> input
        }
    }

    /**
     * 获取统计信息
     */
    fun getStats(): String {
        return "总任务: $totalTasks | 本地: $localTasks | 云端: $cloudTasks"
    }

    /**
     * 释放资源
     */
    fun shutdown() {
        modelDownloadManager.shutdown()
        localLlmEngine.close()
    }
}
