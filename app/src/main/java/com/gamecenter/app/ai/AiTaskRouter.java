package com.gamecenter.app.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.gamecenter.app.ai.cloud.AiApiClient;
import com.gamecenter.app.ai.data.AiProviderConfig;
import com.gamecenter.app.ai.data.AiResult;
import com.gamecenter.app.ai.data.AiTask;
import com.gamecenter.app.ai.local.LocalAiProcessor;
import com.gamecenter.app.ai.local.LocalAiProcessor.AiCommand;
import com.gamecenter.app.ai.local.MediaPipeLocalLlmEngine;
import com.gamecenter.app.ai.model.AiModelDownloadManager;
import com.gamecenter.app.ai.model.AiModelInfo;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 功能调度中心 — 决定任务走本地还是云端，管理任务生命周期。
 * 遵循本地优先（Local First）策略。
 */
public class AiTaskRouter {

    private static final String TAG = "AiTaskRouter";

    private final Context appContext;
    private final AiPreferences aiPrefs;
    private final ExecutorService aiExecutor;
    private final Handler mainHandler;
    private final AiModelDownloadManager modelDownloadManager;
    private final MediaPipeLocalLlmEngine localLlmEngine;

    // 统计
    private int totalTasks = 0;
    private int localTasks = 0;
    private int cloudTasks = 0;

    public AiTaskRouter(Context context) {
        this.appContext = context.getApplicationContext();
        this.aiPrefs = new AiPreferences(appContext);
        this.aiExecutor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.modelDownloadManager = new AiModelDownloadManager();
        this.localLlmEngine = new MediaPipeLocalLlmEngine();
    }

    /**
     * 提交一个 AI 任务（异步）。
     */
    public AiTask submitTask(String taskType, String input, AiCallback callback) {
        AiTask task = new AiTask(taskType, input);
        totalTasks++;
        executeTask(task, callback);
        return task;
    }

    /**
     * 执行任务路由：先尝试本地处理，本地无法处理再走云端。
     */
    private void executeTask(AiTask task, AiCallback callback) {
        aiExecutor.execute(() -> {
            task.status = "running";

            // 1. 尝试本地优先处理
            AiResult localResult = tryLocalProcessing(task);
            if (localResult != null) {
                if (localResult.success) {
                    task.output = localResult.content;
                    task.status = "completed";
                    task.costLevel = 0;
                    localTasks++;
                } else {
                    task.output = localResult.message;
                    task.status = "failed";
                }
                postResult(callback, task, localResult);
                return;
            }

            // 2. 本地无法处理，检查云端
            if (!aiPrefs.hasFreeQuota()) {
                task.status = "failed";
                task.output = "今日免费额度已用完，请明天再试或设置 API Key 解锁更多次数";
                postResult(callback, task,
                        AiResult.fail(task.output).errorCode("QUOTA_EXCEEDED").build());
                return;
            }

            // 3. 走云端
            if (aiPrefs.getApiKey().isEmpty()) {
                task.status = "failed";
                task.output = "未配置 API Key，无法使用云端 AI 功能";
                postResult(callback, task,
                        AiResult.fail(task.output).errorCode("NO_API_KEY").build());
                return;
            }

            try {
                task.costLevel = estimateCost(task.taskType);
                task.status = "running";

                AiApiClient client = new AiApiClient(buildConfigForTask(task));
                AiProviderConfig config = buildConfigForTask(task);
                client = new AiApiClient(config);

                // 构建提示词
                String prompt = buildPrompt(task.taskType, task.input);

                AiResult result = client.chatSync("你是一个有用的助手。", prompt);

                if (result.success) {
                    task.output = result.content;
                    task.status = "completed";
                    cloudTasks++;
                    aiPrefs.incrementUsage();
                    postResult(callback, task, result);
                } else {
                    task.status = "failed";
                    task.output = result.message;
                    cloudTasks++;
                    postResult(callback, task, result);
                }
            } catch (Exception e) {
                task.status = "failed";
                task.output = "请求失败: " + e.getMessage();
                Log.e(TAG, "Cloud AI task failed", e);
                postResult(callback, task,
                        AiResult.fail(task.output).errorCode("NETWORK_ERROR").build());
            }
        });
    }

    /**
     * 尝试本地处理。
     */
    private AiResult tryLocalProcessing(AiTask task) {
        if (!aiPrefs.isLocalFirst()) return null;

        AiResult llmResult = tryLocalLlm(task);
        if (llmResult != null) {
            return llmResult;
        }

        switch (task.taskType) {
            case "ocr":
            case "ocr_clean":
                return LocalAiProcessor.processOcrResult(task.input);
            case "summary":
                return LocalAiProcessor.simpleSummarize(task.input, 10);
            case "translate":
                if (!aiPrefs.getApiKey().isEmpty()) return null;
                return LocalAiProcessor.translateText(task.input);
            case "rewrite":
                if (!aiPrefs.getApiKey().isEmpty()) return null;
                return LocalAiProcessor.polishText(task.input);
            case "qa":
            case "qa_pairs":
                if (!aiPrefs.getApiKey().isEmpty()) return null;
                return LocalAiProcessor.generateQaPairs(task.input, 5);
            case "keywords":
                return LocalAiProcessor.extractKeywords(task.input);
            case "classify":
                return LocalAiProcessor.classifyText(task.input);
            case "template":
                return AiResult.success(task.input).source("local").build();
            default:
                // 未知类型，尝试指令识别
                AiCommand cmd = LocalAiProcessor.recognizeCommand(task.input);
                if (cmd.isKnown()) {
                    switch (cmd.type) {
                        case "summarize":
                            return LocalAiProcessor.simpleSummarize(task.input, 10);
                        case "translate":
                            if (!aiPrefs.getApiKey().isEmpty()) return null;
                            return LocalAiProcessor.translateText(task.input);
                        case "rewrite":
                            if (!aiPrefs.getApiKey().isEmpty()) return null;
                            return LocalAiProcessor.polishText(task.input);
                        case "qa_pairs":
                            if (!aiPrefs.getApiKey().isEmpty()) return null;
                            return LocalAiProcessor.generateQaPairs(task.input, 5);
                        case "keywords":
                            return LocalAiProcessor.extractKeywords(task.input);
                        case "classify":
                            return LocalAiProcessor.classifyText(task.input);
                    }
                }
                return null; // 无法本地处理
        }
    }

    private AiResult tryLocalLlm(AiTask task) {
        if (!"gemma3-1b-it-q4".equals(aiPrefs.getLocalModel())) {
            return null;
        }
        if (!supportsLocalLlm(task.taskType)) {
            return null;
        }
        AiModelInfo model = buildGemmaModelInfo();
        if (!modelDownloadManager.isDownloaded(appContext, model)) {
            return null;
        }
        if (!hasEnoughMemory(model.minRamMb)) {
            return AiResult.fail("设备内存不足，无法安全加载本地 Gemma 模型")
                    .source("local-gemma")
                    .errorCode("LOCAL_LLM_LOW_MEMORY")
                    .build();
        }
        try {
            localLlmEngine.load(appContext, modelDownloadManager.getModelFile(appContext, model));
            String output = localLlmEngine.generate(buildPrompt(task.taskType, task.input));
            return AiResult.success(output).source("local-gemma").build();
        } catch (Throwable t) {
            Log.e(TAG, "Local Gemma task failed", t);
            return AiResult.fail("本地 Gemma 推理失败: " + t.getMessage())
                    .source("local-gemma")
                    .errorCode("LOCAL_LLM_ERROR")
                    .build();
        }
    }

    private boolean supportsLocalLlm(String taskType) {
        return "summary".equals(taskType)
                || "translate".equals(taskType)
                || "rewrite".equals(taskType)
                || "qa".equals(taskType)
                || "qa_pairs".equals(taskType)
                || "keywords".equals(taskType)
                || "classify".equals(taskType)
                || "chat".equals(taskType);
    }

    private AiModelInfo buildGemmaModelInfo() {
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("id", "gemma3-1b-it-q4");
            json.put("name", "Gemma3-1B-IT q4");
            json.put("runtime", "mediapipe-llm");
            json.put("fileName", "Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task");
            json.put("sha256", "ddfaf1210d8b4d1b812b5fadb6652999e852c8be6dd9abe353b9213a25262c10");
            json.put("sizeBytes", 554661246L);
            json.put("minSdk", 24);
            json.put("minRamMb", 3072);
            json.put("enabled", true);
            return AiModelInfo.fromJson(json);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot build Gemma model metadata", e);
        }
    }

    private boolean hasEnoughMemory(int minRamMb) {
        try {
            android.app.ActivityManager am =
                    (android.app.ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo info = new android.app.ActivityManager.MemoryInfo();
            if (am == null) return true;
            am.getMemoryInfo(info);
            long totalMb = info.totalMem / 1024L / 1024L;
            return totalMb <= 0 || totalMb >= minRamMb;
        } catch (Exception e) {
            return true;
        }
    }

    private AiProviderConfig buildConfigForTask(AiTask task) {
        List<AiProviderConfig> providers = AiPreferences.getAvailableProviders(appContext);
        for (AiProviderConfig p : providers) {
            if (p.providerName.equals(aiPrefs.getSelectedProvider())
                    && p.modelName.equals(aiPrefs.getSelectedModel())) {
                return p;
            }
        }
        // fallback to first available
        return providers.isEmpty() ? AiProviderConfig.localConfig() : providers.get(0);
    }

    /**
     * 估算任务成本等级。
     */
    private int estimateCost(String taskType) {
        switch (taskType) {
            case "ocr":
            case "summary":
            case "keywords":
            case "classify":
            case "translate":
                return 1;
            case "rewrite":
                return 1;
            case "qa_pairs":
                return 1;
            default:
                return 2;
        }
    }

    /**
     * 构建提示词。
     */
    private String buildPrompt(String taskType, String input) {
        switch (taskType) {
            case "ocr":
                return "请对以下OCR识别结果进行清洗和格式化，修正错别字和乱码，保持原文结构：\n\n" + input;
            case "summary":
                return "请对以下文本进行摘要，提取要点，简洁明了：\n\n" + input;
            case "translate":
                return "请将以下文本翻译成中文，保持原意：\n\n" + input;
            case "rewrite":
                return "请对以下文本进行润色，使其更通顺、专业：\n\n" + input;
            case "qa_pairs":
            case "qa":
                return "请根据以下文本，生成5个问答对（问题和答案），用于复习和测试：\n\n" + input;
            case "chat":
                return "你是一个运行在手机本地的中文 AI 助手。请直接回答用户问题，保持简洁、清楚、可执行；如果不确定，请说明不确定并给出可验证的建议。\n\n用户："
                        + input;
            default:
                return input;
        }
    }

    private void postResult(AiCallback callback, AiTask task, AiResult result) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onResult(task, result));
    }

    public void shutdown() {
        aiExecutor.shutdownNow();
        modelDownloadManager.shutdown();
        localLlmEngine.close();
    }

    /**
     * 获取统计信息。
     */
    public String getStats() {
        return String.format("总任务: %d | 本地: %d | 云端: %d", totalTasks, localTasks, cloudTasks);
    }

    /**
     * AI 任务回调接口。
     */
    public interface AiCallback {
        void onResult(AiTask task, AiResult result);
    }
}
