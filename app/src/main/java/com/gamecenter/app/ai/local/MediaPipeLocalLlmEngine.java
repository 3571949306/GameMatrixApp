package com.gamecenter.app.ai.local;

import android.content.Context;

import com.google.mediapipe.tasks.genai.llminference.LlmInference;
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions;

import java.io.File;

/**
 * MediaPipe 本地 LLM 推理引擎 — 封装 Google MediaPipe LlmInference API，提供端侧大模型推理能力。
 * <p>
 * 核心设计决策：
 * <ul>
 *   <li>实现 {@link AutoCloseable} 接口，支持 try-with-resources 资源自动释放。</li>
 *   <li>所有方法均使用 {@code synchronized} 修饰，保证线程安全，
 *       防止多线程并发加载/推理导致原生层崩溃。</li>
 *   <li>采用懒加载策略：模型在首次调用 {@link #load} 时加载，
 *       若已加载相同路径的模型则跳过重复加载，避免不必要的内存开销。</li>
 *   <li>切换模型时先关闭旧模型再加载新模型，确保同一时刻只有一个模型实例占用内存。</li>
 * </ul>
 * <p>
 * 当前仅支持 Gemma3-1B-IT-q4 量化模型，通过 MediaPipe LLM Inference API
 * 在设备端执行推理，无需网络连接。
 */
public final class MediaPipeLocalLlmEngine implements AutoCloseable {
    /** MediaPipe LLM 推理实例，为 null 表示未加载模型 */
    private LlmInference llmInference;
    /** 当前已加载模型的文件绝对路径，用于判断是否需要重复加载 */
    private String loadedModelPath = "";

    /**
     * 加载本地 LLM 模型文件。
     * <p>
     * 若模型文件不存在则抛出异常；若已加载相同路径的模型则直接返回（幂等）；
     * 若加载不同路径的模型则先释放旧模型再加载新模型。
     * <p>
     * 推理参数配置：
     * <ul>
     *   <li>{@code maxTokens = 384}：限制最大生成令牌数，控制推理耗时和内存占用</li>
     *   <li>{@code maxTopK = 20}：Top-K 采样参数，限制候选词数量，平衡多样性和质量</li>
     * </ul>
     *
     * @param context   应用上下文，用于创建 LlmInference 实例
     * @param modelFile 模型文件对象，必须存在且可读
     * @throws IllegalStateException 若模型文件不存在
     */
    public synchronized void load(Context context, File modelFile) {
        if (modelFile == null || !modelFile.exists()) {
            throw new IllegalStateException("Local model file is missing");
        }
        String modelPath = modelFile.getAbsolutePath();
        // 幂等检查：若已加载相同路径的模型，跳过重复加载
        if (llmInference != null && modelPath.equals(loadedModelPath)) {
            return;
        }
        // 切换模型前先释放旧模型，避免内存泄漏
        close();
        LlmInferenceOptions options = LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(384)
                .setMaxTopK(20)
                .build();
        llmInference = LlmInference.createFromOptions(context.getApplicationContext(), options);
        loadedModelPath = modelPath;
    }

    /**
     * 执行本地 LLM 推理，生成文本响应。
     * <p>
     * 使用同步阻塞方式调用 MediaPipe 推理，在当前线程执行。
     * 必须在调用前通过 {@link #load} 加载模型，否则抛出异常。
     *
     * @param prompt 输入提示词
     * @return 模型生成的文本响应
     * @throws IllegalStateException 若模型未加载
     */
    public synchronized String generate(String prompt) {
        if (llmInference == null) {
            throw new IllegalStateException("Local LLM is not loaded");
        }
        return llmInference.generateResponse(prompt);
    }

    /**
     * 释放本地 LLM 引擎资源。
     * <p>
     * 关闭 MediaPipe LlmInference 实例并重置状态。
     * 此方法幂等，多次调用安全。
     */
    @Override
    public synchronized void close() {
        if (llmInference != null) {
            llmInference.close();
            llmInference = null;
        }
        loadedModelPath = "";
    }
}
