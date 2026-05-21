package com.gamecenter.app.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.gamecenter.app.ai.cloud.AiApiClient;
import com.gamecenter.app.ai.data.AiErrorCode;
import com.gamecenter.app.ai.data.AiProviderConfig;
import com.gamecenter.app.ai.data.AiResult;
import com.gamecenter.app.ai.data.AiTask;
import com.gamecenter.app.ai.data.TaskStatus;
import com.gamecenter.app.ai.local.LocalAiProcessor;
import com.gamecenter.app.ai.local.LocalAiProcessor.AiCommand;
import com.gamecenter.app.ai.local.LocalLlmOutputGuard;
import com.gamecenter.app.ai.local.MediaPipeLocalLlmEngine;
import com.gamecenter.app.ai.model.AiModelDownloadManager;
import com.gamecenter.app.ai.model.AiModelInfo;
import com.gamecenter.app.utils.NetworkErrorHandler;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI 功能调度中心 — 决定任务走本地还是云端，管理任务生命周期。
 * <p>
 * 你可以把这个类想象成一个"快递调度站"：
 * 当你提交一个 AI 任务（比如"帮我翻译这段话"），调度站要决定是让"本地快递员"（本地规则引擎/本地模型）
 * 来处理，还是交给"远方仓库"（云端 API）来处理。优先用本地的，因为又快又免费；
 * 本地搞不定的才走云端，因为云端更强大但要花额度。
 * <p>
 * 核心设计决策：
 * <ul>
 *   <li>遵循「本地优先」（Local First）策略：优先尝试本地规则引擎和本地 LLM（Gemma）处理，
 *       仅在本地无法胜任时才回退到云端 API，从而减少网络依赖和 API 消耗。</li>
 *   <li>所有 AI 任务在单线程线程池（{@code aiExecutor}）中串行执行，避免并发推理导致资源竞争。</li>
 *   <li>结果通过 {@link Handler} 回调到主线程，保证 UI 更新安全。</li>
 *   <li>云端调用前依次检查：网络可用性 → 免费额度 → API Key 配置，逐层拦截无效请求。</li>
 * </ul>
 * <p>
 * 路由优先级：本地 LLM（Gemma）→ 本地规则引擎 → 云端 API
 */
public class AiTaskRouter {

    private static final String TAG = "AiTaskRouter";

    /** 应用级 Context，避免持有 Activity 导致泄漏 */
    private final Context appContext;
    // AI 偏好设置，控制本地优先开关、API Key、额度等
    private final AiPreferences aiPrefs;
    // 单线程执行器，保证 AI 任务串行执行，防止本地模型并发加载冲突
    // 就像单窗口办事大厅，一次只处理一个任务，避免混乱
    private final ExecutorService aiExecutor;
    // 主线程 Handler，用于将结果回调投递到主线程
    // Android 中 UI 操作必须在主线程执行，所以结果要"送回"主线程
    private final Handler mainHandler;
    // 模型下载管理器，负责检查/获取本地模型文件
    private final AiModelDownloadManager modelDownloadManager;
    // MediaPipe 本地 LLM 推理引擎实例（即手机上运行的 Gemma 小模型）
    private final MediaPipeLocalLlmEngine localLlmEngine;

    // 以下三个计数器用于统计任务执行情况，方便展示给用户
    private int totalTasks = 0;   // 累计提交的任务总数
    private int localTasks = 0;   // 本地成功处理的任务数
    private int cloudTasks = 0;   // 云端处理的任务数（含成功和失败）

    /**
     * 构造调度器，初始化所有依赖组件。
     *
     * @param context 上下文，内部会转为 Application Context 以避免内存泄漏
     */
    public AiTaskRouter(Context context) {
        // 使用 ApplicationContext 而不是 Activity Context，防止 Activity 销毁后还持有引用
        this.appContext = context.getApplicationContext();
        this.aiPrefs = new AiPreferences(appContext);
        // 创建单线程线程池：所有任务排队执行，不会出现两个任务同时抢模型资源的情况
        this.aiExecutor = Executors.newSingleThreadExecutor();
        // 获取主线程的 Handler，用于把结果"送回"主线程更新 UI
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.modelDownloadManager = new AiModelDownloadManager();
        this.localLlmEngine = new MediaPipeLocalLlmEngine();
    }

    /**
     * 提交一个 AI 任务（异步执行）。
     * <p>
     * 任务提交后立即返回，实际执行在后台线程池中进行，
     * 结果通过 {@link AiCallback} 回调到调用方。
     *
     * @param taskType 任务类型标识，如 "ocr"、"summary"、"translate"、"rewrite"、"qa"、"qa_pairs"、"keywords"、"classify"、"chat"、"template"
     * @param input    用户输入的原始文本
     * @param callback 结果回调，可为 null（表示不关心结果）
     * @return 已创建的 {@link AiTask} 对象，可用于跟踪任务状态
     */
    public AiTask submitTask(String taskType, String input, AiCallback callback) {
        AiTask task = new AiTask(taskType, input);
        totalTasks++;
        executeTask(task, callback);
        return task;
    }

    /**
     * 执行任务路由：先尝试本地处理，本地无法处理再走云端。
     * <p>
     * 路由决策流程（就像看病先去社区诊所，不行再去大医院）：
     * <ol>
     *   <li>尝试本地处理（本地 LLM + 规则引擎）</li>
     *   <li>检查网络可用性</li>
     *   <li>检查每日免费额度</li>
     *   <li>检查 API Key 是否已配置</li>
     *   <li>调用云端 API</li>
     * </ol>
     *
     * @param task     待执行的任务
     * @param callback 结果回调
     */
    private void executeTask(AiTask task, AiCallback callback) {
        // 把任务提交到后台线程池执行，避免阻塞主线程（主线程负责 UI，不能做耗时操作）
        aiExecutor.execute(() -> {
            task.status = TaskStatus.RUNNING;

            // 第1步：尝试本地优先处理（本地 LLM 或规则引擎）
            AiResult localResult = tryLocalProcessing(task);
            if (localResult != null) {
                // 本地处理有结果了
                if (localResult.success) {
                    task.output = localResult.content;
                    task.status = TaskStatus.COMPLETED;
                    task.costLevel = 0; // 本地处理零成本（不消耗云端额度）
                    localTasks++;
                } else {
                    task.output = localResult.message;
                    task.status = TaskStatus.FAILED;
                }
                postResult(callback, task, localResult);
                return; // 本地处理完毕，直接返回
            }

            // 第2步：本地无法处理，检查网络可用性
            // 没有网络就像没有公路，请求无法到达云端服务器
            if (!NetworkErrorHandler.isNetworkAvailable(appContext)) {
                task.status = TaskStatus.FAILED;
                task.output = "当前无网络连接，仅支持本地规则处理（OCR/摘要/关键词/分类等）";
                postResult(callback, task,
                        AiResult.fail(task.output).errorCode(AiErrorCode.NETWORK_ERROR).build());
                return;
            }

            // 第3步：网络可用，检查每日免费额度是否耗尽
            // 免费额度就像每日限量的优惠券，用完了就得等明天
            if (!aiPrefs.hasFreeQuota()) {
                task.status = TaskStatus.FAILED;
                task.output = "今日免费额度已用完，请明天再试或设置 API Key 解锁更多次数";
                postResult(callback, task,
                        AiResult.fail(task.output).errorCode(AiErrorCode.QUOTA_EXCEEDED).build());
                return;
            }

            // 第4步：检查 API Key 是否已配置
            // API Key 就像进入云端服务的门禁卡，没有卡就进不去
            if (aiPrefs.getApiKey().isEmpty()) {
                task.status = TaskStatus.FAILED;
                task.output = "未配置 API Key，无法使用云端 AI 功能";
                postResult(callback, task,
                        AiResult.fail(task.output).errorCode(AiErrorCode.NO_API_KEY).build());
                return;
            }

            // 第5步：所有检查通过，走云端 API 调用
            try {
                task.costLevel = estimateCost(task.taskType);
                task.status = TaskStatus.RUNNING;

                // 根据用户选择的供应商和模型构建配置
                AiProviderConfig config = buildConfigForTask(task);
                AiApiClient client = new AiApiClient(config);

                // 根据任务类型构建对应的提示词
                String prompt = buildPrompt(task.taskType, task.input);

                // 调用云端 API，同步等待结果
                AiResult result = client.chatSync("你是一个有用的助手。", prompt);

                if (result.success) {
                    task.output = result.content;
                    task.status = TaskStatus.COMPLETED;
                    cloudTasks++;
                    aiPrefs.incrementUsage(); // 消耗一次免费额度
                    postResult(callback, task, result);
                } else {
                    task.status = TaskStatus.FAILED;
                    task.output = result.message;
                    cloudTasks++;
                    postResult(callback, task, result);
                }
            } catch (Exception e) {
                task.status = TaskStatus.FAILED;
                task.output = "请求失败: " + e.getMessage();
                Log.e(TAG, "Cloud AI task failed", e);
                postResult(callback, task,
                        AiResult.fail(task.output).errorCode(AiErrorCode.NETWORK_ERROR).build());
            }
        });
    }

    /**
     * 尝试本地处理任务。
     * <p>
     * 处理优先级（就像看病先试偏方，不行再去医院）：
     * <ol>
     *   <li>若用户未开启「本地优先」，直接返回 null 跳过本地处理</li>
     *   <li>尝试本地 LLM（Gemma）推理</li>
     *   <li>根据任务类型匹配本地规则引擎处理</li>
     *   <li>未知类型尝试指令识别后匹配规则</li>
     * </ol>
     * <p>
     * 对于 translate/rewrite/qa 等任务，仅在未配置 API Key 时才使用本地兜底，
     * 因为这些任务的本地处理质量远低于云端。
     *
     * @param task 待处理的任务
     * @return 本地处理结果；若无法本地处理则返回 null，由调用方决定是否走云端
     */
    private AiResult tryLocalProcessing(AiTask task) {
        // 用户关闭了"本地优先"开关，直接跳过本地处理
        if (!aiPrefs.isLocalFirst()) return null;

        // 优先尝试本地 LLM（如 Gemma3-1B），可处理更复杂的语义任务
        AiResult llmResult = tryLocalLlm(task);
        if (llmResult != null) {
            return llmResult;
        }

        // 本地 LLM 不可用，回退到规则引擎（用固定规则处理，不需要 AI 模型）
        switch (task.taskType) {
            case "ocr":
            case "ocr_clean":
                // OCR 后处理：清洗识别结果中的乱码和多余空行
                return LocalAiProcessor.processOcrResult(task.input);
            case "summary":
                // 简单摘要：提取前几行 + 含数字的行 + 短行
                return LocalAiProcessor.simpleSummarize(task.input, 10);
            case "translate":
                // 翻译任务仅在无 API Key 时使用本地兜底，有 Key 时交给云端获得更好质量
                if (!aiPrefs.getApiKey().isEmpty()) return null;
                return LocalAiProcessor.translateText(task.input);
            case "rewrite":
                // 润色任务同翻译，仅无 Key 时本地兜底
                if (!aiPrefs.getApiKey().isEmpty()) return null;
                return LocalAiProcessor.polishText(task.input);
            case "qa":
            case "qa_pairs":
                // 问答对生成同上，仅无 Key 时本地兜底
                if (!aiPrefs.getApiKey().isEmpty()) return null;
                return LocalAiProcessor.generateQaPairs(task.input, 5);
            case "keywords":
                // 关键词提取：基于规则分词和停用词过滤
                return LocalAiProcessor.extractKeywords(task.input);
            case "classify":
                // 文本分类：基于关键词匹配判断类别
                return LocalAiProcessor.classifyText(task.input);
            case "template":
                // 模板任务直接返回原始输入，由上层处理模板填充
                return AiResult.success(task.input).source("local").build();
            default:
                // 未知任务类型，尝试通过指令识别匹配本地可处理的命令
                // 比如用户输入"帮我总结一下这段话"，能识别出"总结"意图
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
                return null; // 无法本地处理，交给云端
        }
    }

    /**
     * 尝试使用本地 LLM（Gemma3-1B-IT）处理任务。
     * <p>
     * 前置条件检查链（就像启动一台精密设备前的安全检查）：
     * <ol>
     *   <li>用户选择的是 gemma3-1b-it-q4 模型</li>
     *   <li>任务类型在本地 LLM 支持范围内</li>
     *   <li>模型文件已下载到本地</li>
     *   <li>设备内存满足最低要求（3GB）</li>
     * </ol>
     * <p>
     * 推理完成后会通过 {@link LocalLlmOutputGuard} 校验输出质量，
     * 防止模型退化输出（乱码、重复、无意义内容）。
     *
     * @param task 待处理的任务
     * @return 推理结果；若前置条件不满足返回 null，推理失败返回失败结果
     */
    private AiResult tryLocalLlm(AiTask task) {
        // 仅支持 gemma3-1b-it-q4 这一个本地模型
        if (!"gemma3-1b-it-q4".equals(aiPrefs.getLocalModel())) {
            return null;
        }
        // 检查任务类型是否在本地 LLM 支持范围内
        if (!supportsLocalLlm(task.taskType)) {
            return null;
        }
        AiModelInfo model = buildGemmaModelInfo();
        // 检查模型文件是否已下载到手机上
        if (!modelDownloadManager.isDownloaded(appContext, model)) {
            return null;
        }
        // 检查设备内存是否足够加载模型（模型很大，内存不够会崩溃）
        if (!hasEnoughMemory(model.minRamMb)) {
            return AiResult.fail("设备内存不足，无法安全加载本地 Gemma 模型")
                    .source("local-gemma")
                    .errorCode(AiErrorCode.LOCAL_LLM_LOW_MEMORY)
                    .build();
        }
        try {
            // 加载模型文件并执行推理（让模型"思考"并给出回答）
            localLlmEngine.load(appContext, modelDownloadManager.getModelFile(appContext, model));
            String output = localLlmEngine.generate(buildPrompt(task.taskType, task.input));
            // 校验输出质量，防止退化输出（乱码、重复等）
            // 就像老师批改作业，如果发现答案是乱写的就打回去
            String guardMessage = LocalLlmOutputGuard.validate(output);
            if (guardMessage != null) {
                return AiResult.fail(guardMessage)
                        .source("local-gemma")
                        .errorCode(AiErrorCode.LOCAL_LLM_DEGENERATED_OUTPUT)
                        .build();
            }
            return AiResult.success(output).source("local-gemma").build();
        } catch (Throwable t) {
            // 使用 Throwable 而非 Exception，以捕获 NoClassDefFoundError 等链接时错误
            // 这类错误在模型库缺失时会出现，用 Exception 抓不住
            Log.e(TAG, "Local Gemma task failed", t);
            return AiResult.fail("本地 Gemma 推理失败: " + t.getMessage())
                    .source("local-gemma")
                    .errorCode(AiErrorCode.LOCAL_LLM_ERROR)
                    .build();
        }
    }

    /**
     * 判断任务类型是否支持本地 LLM 处理。
     * <p>
     * 目前支持：摘要、翻译、润色、问答、问答对、关键词提取、分类、闲聊。
     * OCR 和模板任务不需要 LLM，由规则引擎直接处理。
     *
     * @param taskType 任务类型标识
     * @return 是否支持本地 LLM 处理
     */
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

    /**
     * 构建 Gemma3-1B-IT-q4 模型的元数据信息。
     * <p>
     * 硬编码模型参数，包括文件名、SHA256 校验值、大小、最低 SDK 和内存要求等。
     * 这些信息用于模型下载校验和运行时兼容性检查。
     *
     * @return Gemma 模型的 {@link AiModelInfo} 实例
     * @throws IllegalStateException 若 JSON 构建异常（理论上不会发生）
     */
    private AiModelInfo buildGemmaModelInfo() {
        try {
            // 用 JSON 对象手动构建模型信息，因为当前只有一个本地模型
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("id", "gemma3-1b-it-q4");
            json.put("name", "Gemma3-1B-IT q4");
            json.put("runtime", "mediapipe-llm");  // 使用 MediaPipe LLM 推理引擎
            json.put("fileName", "Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task");
            json.put("sha256", "ddfaf1210d8b4d1b812b5fadb6652999e852c8be6dd9abe353b9213a25262c10");
            json.put("sizeBytes", 554661246L);  // 约 529MB
            json.put("minSdk", 24);              // 最低 Android 7.0
            json.put("minRamMb", 3072);          // 最低 3GB 内存
            json.put("enabled", true);
            return AiModelInfo.fromJson(json);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot build Gemma model metadata", e);
        }
    }

    /**
     * 检查设备总内存是否满足模型最低要求。
     * <p>
     * 通过 {@link android.app.ActivityManager} 获取设备总物理内存，
     * 与模型要求的最低内存比较。若获取失败则默认放行（避免误拦截）。
     *
     * @param minRamMb 模型要求的最低内存（MB）
     * @return 设备内存是否足够；获取信息失败时返回 true（保守放行）
     */
    private boolean hasEnoughMemory(int minRamMb) {
        try {
            // 通过系统服务获取内存信息
            android.app.ActivityManager am =
                    (android.app.ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
            android.app.ActivityManager.MemoryInfo info = new android.app.ActivityManager.MemoryInfo();
            if (am == null) return true; // 无法获取 ActivityManager，保守放行
            am.getMemoryInfo(info);
            long totalMb = info.totalMem / 1024L / 1024L;
            // totalMb <= 0 表示获取异常，放行；否则与最低要求比较
            return totalMb <= 0 || totalMb >= minRamMb;
        } catch (Exception e) {
            return true; // 异常时保守放行，避免误拦截
        }
    }

    /**
     * 根据用户偏好构建当前任务的云端供应商配置。
     * <p>
     * 优先匹配用户选择的供应商和模型；若未找到匹配项，
     * 则回退到第一个可用供应商，或使用本地配置作为最终兜底。
     *
     * @param task 当前任务（当前未使用任务信息，预留扩展）
     * @return 匹配的云端供应商配置
     */
    private AiProviderConfig buildConfigForTask(AiTask task) {
        // 从偏好设置中获取所有可用的 AI 供应商列表
        List<AiProviderConfig> providers = AiPreferences.getAvailableProviders(appContext);
        // 遍历查找用户选择的供应商和模型组合
        for (AiProviderConfig p : providers) {
            if (p.providerName.equals(aiPrefs.getSelectedProvider())
                    && p.modelName.equals(aiPrefs.getSelectedModel())) {
                return p;
            }
        }
        for (AiProviderConfig p : providers) {
            if (!p.localOnly && p.enabled) {
                return p;
            }
        }
        // 没找到匹配项，回退到本地配置兜底
        return AiProviderConfig.localConfig();
    }

    /**
     * 估算任务成本等级。
     * <p>
     * 当前所有任务类型统一为等级 1（低成本），未知类型为等级 2。
     * 预留用于未来按任务复杂度差异化计费。
     *
     * @param taskType 任务类型标识
     * @return 成本等级（1=低成本，2=中等成本）
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
     * 根据任务类型构建对应的提示词（Prompt）。
     * <p>
     * 提示词就像是给 AI 的"工作指令"，告诉它应该怎么回答。
     * 不同任务需要不同的指令，比如翻译要说"请翻译成中文"，
     * 摘要要说"请提取要点"。
     * <p>
     * chat 类型有额外的输出质量约束规则，防止本地小模型产生退化输出。
     *
     * @param taskType 任务类型标识
     * @param input    用户输入的原始文本
     * @return 构建好的提示词
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
                // 闲聊模式的提示词特别加了"规则"约束，防止小模型胡说八道
                return "你是一个运行在手机本地的中文 AI 助手。请用简体中文直接回答。\n"
                        + "规则：\n"
                        + "1. 不要复述用户输入。\n"
                        + "2. 不要输出无意义数字、乱码、重复字符或循环片段。\n"
                        + "3. 常规回答控制在800字以内；用户要求长文、方案或分析时可以更完整，但要分段清楚。\n"
                        + "4. 如果无法可靠回答，请直接说明不确定，并给出可验证的建议。\n\n"
                        + "用户问题："
                        + input;
            default:
                return input;
        }
    }

    /**
     * 将结果通过主线程 Handler 回调给调用方。
     * <p>
     * 保证回调在主线程执行，便于 UI 层直接更新界面。
     * Android 中修改 UI 必须在主线程，否则会崩溃。
     *
     * @param callback 回调接口；若为 null 则不执行回调
     * @param task     已完成的任务
     * @param result   处理结果
     */
    private void postResult(AiCallback callback, AiTask task, AiResult result) {
        if (callback == null) return;
        // mainHandler.post() 把任务投递到主线程的消息队列中执行
        mainHandler.post(() -> callback.onResult(task, result));
    }

    /**
     * 关闭调度器，释放所有资源。
     * <p>
     * 包括：终止线程池、关闭模型下载管理器、释放本地 LLM 引擎。
     * 调用后不应再提交新任务。
     */
    public void shutdown() {
        aiExecutor.shutdownNow();
        modelDownloadManager.shutdown();
        localLlmEngine.close();
    }

    /**
     * 获取统计信息。
     * <p>
     * 返回格式为："总任务: X | 本地: Y | 云端: Z"
     *
     * @return 统计信息字符串
     */
    public String getStats() {
        return String.format("总任务: %d | 本地: %d | 云端: %d", totalTasks, localTasks, cloudTasks);
    }

    /**
     * AI 任务回调接口。
     * <p>
     * 用于异步接收任务处理结果，回调在主线程执行。
     * 就像网购下单后的"收货通知"，货到了会通知你。
     */
    public interface AiCallback {
        /**
         * 任务处理完成时回调。
         *
         * @param task   已完成的任务（包含状态、输出等信息）
         * @param result 处理结果（包含内容、来源、错误码等信息）
         */
        void onResult(AiTask task, AiResult result);
    }
}
