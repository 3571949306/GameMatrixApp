package com.gamecenter.app.ai.local

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * MediaPipe 本地 LLM 推理引擎 — 封装 Google MediaPipe LlmInference API，提供端侧大模型推理能力。
 *
 * 实现了统一的 [ILocalLlmEngine] 接口，支持 Gemma-3 / Gemma-2B (.task) 等模型的端侧推理与流式输出。
 */
class MediaPipeLocalLlmEngine : ILocalLlmEngine {

    companion object {
        private const val TAG = "MediaPipeLocalLlm"
        const val ENGINE_TYPE = "mediapipe-llm"
    }

    private var llmInference: LlmInference? = null
    private var loadedModelPath: String = ""

    @Synchronized
    override fun load(context: Context, modelFile: File, options: ILocalLlmEngine.EngineOptions) {
        if (!modelFile.exists()) {
            throw IllegalStateException("Local model file is missing: ${modelFile.absolutePath}")
        }
        val modelPath = modelFile.absolutePath
        if (llmInference != null && modelPath == loadedModelPath) {
            return // 幂等跳过
        }
        close()

        val inferenceOptions = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelPath)
            .setMaxTokens(options.maxTokens)
            .setMaxTopK(options.topK)
            .build()

        llmInference = LlmInference.createFromOptions(context.applicationContext, inferenceOptions)
        loadedModelPath = modelPath
        Log.i(TAG, "Successfully loaded MediaPipe model from $modelPath")
    }

    // Java 兼容重载
    @Synchronized
    fun load(context: Context, modelFile: File) {
        load(context, modelFile, ILocalLlmEngine.EngineOptions())
    }

    @Synchronized
    override fun generate(prompt: String): String {
        val inference = llmInference ?: throw IllegalStateException("Local LLM is not loaded")
        return inference.generateResponse(prompt)
    }

    override fun generateStream(prompt: String): Flow<String> = flow {
        val fullResponse = withContext(Dispatchers.Default) {
            generate(prompt)
        }
        // 模拟打字机平滑流式分发
        val chunkSize = 4
        var idx = 0
        while (idx < fullResponse.length) {
            val nextEnd = (idx + chunkSize).coerceAtMost(fullResponse.length)
            emit(fullResponse.substring(idx, nextEnd))
            idx = nextEnd
            delay(25)
        }
    }

    @Synchronized
    override fun isLoaded(): Boolean = llmInference != null

    override fun getLoadedModelPath(): String = loadedModelPath

    override fun getEngineType(): String = ENGINE_TYPE

    @Synchronized
    override fun close() {
        try {
            llmInference?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing MediaPipe LlmInference", e)
        } finally {
            llmInference = null
            loadedModelPath = ""
        }
    }
}
