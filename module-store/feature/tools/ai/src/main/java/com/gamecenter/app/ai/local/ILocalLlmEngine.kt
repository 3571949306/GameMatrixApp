package com.gamecenter.app.ai.local

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * 通用端侧大模型推理引擎接口。
 *
 * 统一抽象不同的端侧推理后端（如 MediaPipe GenAI、ONNX Runtime GenAI、本地规则引擎等）。
 */
interface ILocalLlmEngine : AutoCloseable {

    /**
     * 推理引擎配置参数（针对端侧小模型优化，采用低温高确定性采样以抑制幻觉与胡言乱语）
     */
    data class EngineOptions(
        val maxTokens: Int = 768,
        val topK: Int = 20,
        val temperature: Float = 0.3f, // 降低温度以确保回答严谨、减少发散与幻觉
        val repetitionPenalty: Float = 1.18f, // 惩罚重复 token，防止死循环
        val enableGpu: Boolean = true
    )

    /**
     * 加载本地模型文件
     *
     * @param context 应用上下文
     * @param modelFile 模型权重文件
     * @param options 推理参数
     */
    fun load(context: Context, modelFile: File, options: EngineOptions = EngineOptions())

    /**
     * 同步生成文本响应（阻塞当前线程）
     *
     * @param prompt 输入提示词
     * @return 生成的文本内容
     */
    fun generate(prompt: String): String

    /**
     * 协程流式生成文本响应（非阻塞，逐 Token 发射）
     *
     * @param prompt 输入提示词
     * @return 逐字/逐 Token 发射的 Flow 流
     */
    fun generateStream(prompt: String): Flow<String>

    /**
     * 判断当前模型是否已加载就绪
     */
    fun isLoaded(): Boolean

    /**
     * 获取当前加载的模型绝对路径
     */
    fun getLoadedModelPath(): String

    /**
     * 获取引擎类型标识（如 "mediapipe", "onnx", "rules"）
     */
    fun getEngineType(): String

    /**
     * 释放模型资源
     */
    override fun close()
}

