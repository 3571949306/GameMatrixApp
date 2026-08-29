package com.gamecenter.app.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.gamecenter.app.ai.cloud.AiApiClient;
import com.gamecenter.app.ai.bridge.CoreAiService;
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
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 功能调度中心 — 决定任务走本地还是云端，管理任务生命周期。
 * <p>
 * 你可以把这个类想象成一个"快递调度站"：
 * 当你提交一个 AI 任务（比如"帮我翻译这段话"），调度站会按本次请求的边界选择
 * "本地快递员"（本地规则引擎/本地模型）或"远方仓库"（云端 API）。
 * 本地模式不会因为失败而暗中改走云端，云端模式也不会先触碰本地输入。
 * <p>
 * 核心设计决策：
 * <ul>
 *   <li>遵循请求级路由边界：LOCAL_ONLY 只尝试本地，CLOUD_ONLY 只在已获授权后调用云端。</li>
 *   <li>使用双线程池架构：高优先级池处理轻量任务（规则引擎/云端API），低优先级池处理重量任务（本地LLM推理），
 *       避免本地LLM推理阻塞其他任务。</li>
 *   <li>结果通过 {@link Handler} 回调到主线程，保证 UI 更新安全。</li>
 *   <li>云端调用前依次检查：网络可用性 → 免费额度 → API Key 配置，逐层拦截无效请求。</li>
 *   <li>支持外部注入 ExecutorService，便于统一线程模型管理。</li>
 * </ul>
 * <p>
 * 本地路径优先级：本地 LLM（Gemma）→ 本地规则引擎；云端路径单独执行。
 */
public class AiTaskRouter {

    private static final String TAG = "AiTaskRouter";

    private final Context appContext;
    private final AiPreferences aiPrefs;
    // 2026-06-23: 改为非 final（修复"已分配变量"编译错误 — try/catch 双路径赋值 final 字段不被允许）
    private ExecutorService highPriorityExecutor;  // 高优先级：规则引擎、云端API
    private ExecutorService lowPriorityExecutor;   // 低优先级：本地LLM推理
    private final Handler mainHandler;
    private final AiModelDownloadManager modelDownloadManager;
    private final MediaPipeLocalLlmEngine localLlmEngine;

    private final AtomicInteger totalTasks = new AtomicInteger();
    private final AtomicInteger localTasks = new AtomicInteger();
    private final AtomicInteger cloudTasks = new AtomicInteger();
    /** Number of submitted router tasks that have not reached a terminal path. */
    private final AtomicInteger inFlightTasks = new AtomicInteger();
    /** Set during Fragment teardown; prevents new work and defers engine close. */
    private final AtomicBoolean shutdownRequested = new AtomicBoolean();

    /**
     * 构造调度器，初始化所有依赖组件（使用统一线程管理器）。
     *
     * @param context 上下文，内部会转为 Application Context 以避免内存泄漏
     */
    public AiTaskRouter(Context context) {
        this.appContext = context.getApplicationContext();
        this.aiPrefs = new AiPreferences(appContext);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.modelDownloadManager = new AiModelDownloadManager();
        this.localLlmEngine = new MediaPipeLocalLlmEngine();

        // 使用统一线程管理器，避免线程爆炸
        // 高优先级：计算线程池（规则引擎、云端API）
        // 低优先级：AI推理线程（本地LLM）
        try {
            Class<?> appExecutors = Class.forName("com.gamecenter.app.core.threading.AppExecutors");
            this.highPriorityExecutor = (ExecutorService) appExecutors.getMethod("compute").invoke(null);
            this.lowPriorityExecutor = (ExecutorService) appExecutors.getMethod("ai").invoke(null);
        } catch (Exception e) {
            // 回退到自建线程池（兼容模式）
            this.highPriorityExecutor = Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "GC-AI-High");
                t.setDaemon(true);
                return t;
            });
            this.lowPriorityExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "GC-AI-LLM");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });
        }
    }

    /**
     * 构造调度器，初始化所有依赖组件（使用外部注入的线程池）。
     *
     * <p>此构造器支持 Hilt 依赖注入和统一线程模型管理。
     * 推荐使用此构造器以便线程池由 AppModule 统一管理。</p>
     *
     * @param context 上下文，内部会转为 Application Context 以避免内存泄漏
     * @param highPriorityExecutor 高优先级任务执行器（规则引擎、云端API）
     * @param lowPriorityExecutor 低优先级任务执行器（本地LLM推理）
     */
    public AiTaskRouter(Context context, ExecutorService highPriorityExecutor, ExecutorService lowPriorityExecutor) {
        this.appContext = context.getApplicationContext();
        this.aiPrefs = new AiPreferences(appContext);
        this.highPriorityExecutor = highPriorityExecutor;
        this.lowPriorityExecutor = lowPriorityExecutor;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.modelDownloadManager = new AiModelDownloadManager();
        this.localLlmEngine = new MediaPipeLocalLlmEngine();
    }

    /**
     * @deprecated 使用 {@link #AiTaskRouter(Context, ExecutorService, ExecutorService)} 替代
     */
    @Deprecated
    public AiTaskRouter(Context context, ExecutorService executor) {
        this.appContext = context.getApplicationContext();
        this.aiPrefs = new AiPreferences(appContext);
        this.highPriorityExecutor = executor;
        this.lowPriorityExecutor = executor;
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
        AiRoutingMode mode = aiPrefs.isLocalFirst()
                ? AiRoutingMode.LOCAL_ONLY
                : AiRoutingMode.CLOUD_ONLY;
        return submitTask(taskType, input, mode, callback);
    }

    /**
     * 提交一个带有明确路由模式的 AI 任务。
     *
     * <p>路由模式在提交瞬间冻结，避免用户在请求执行期间切换设置后，
     * 同一请求从本地路径意外变成网络请求。</p>
     *
     * @param taskType 任务类型标识
     * @param input 用户输入
     * @param routingMode 本次请求允许使用的处理边界
     * @param callback 结果回调，可为 null
     * @return 已创建的任务
     */
    public AiTask submitTask(String taskType, String input,
                             AiRoutingMode routingMode, AiCallback callback) {
        String safeTaskType = taskType == null || taskType.trim().isEmpty()
                ? AiTaskCatalog.CHAT
                : taskType.trim();
        String safeInput = input == null ? "" : input;
        AiTask task = new AiTask(safeTaskType, safeInput);
        totalTasks.incrementAndGet();
        AiRoutingMode mode = routingMode == null ? AiRoutingMode.LOCAL_ONLY : routingMode;
        executeTask(task, mode, callback);
        return task;
    }

    /**
     * 执行任务路由。路由模式在提交时冻结，防止隐私边界在异步执行期间漂移。
     * <p>LOCAL_ONLY 在本地能力不足时返回带错误码的失败结果；CLOUD_ONLY 跳过本地路径，
     * 依次检查网络、额度、API Key 后再调用云端。</p>
     *
     * @param task     待执行的任务
     * @param callback 结果回调
     */
    private void executeTask(AiTask task, AiRoutingMode routingMode, AiCallback callback) {
        if (shutdownRequested.get()) {
            task.status = TaskStatus.FAILED;
            task.output = "AI 任务路由器已关闭";
            postResult(callback, task,
                    AiResult.fail(task.output).source("local").build());
            return;
        }

        // 根据任务类型选择合适的线程池
        // 本地LLM推理是耗时任务，使用低优先级线程池，避免阻塞其他快速任务
        boolean isLlmTask = routingMode == AiRoutingMode.LOCAL_ONLY && isLocalLlmCandidate(task);
        ExecutorService targetExecutor = isLlmTask ? lowPriorityExecutor : highPriorityExecutor;

        // 把任务提交到后台线程池执行，避免阻塞主线程（主线程负责 UI，不能做耗时操作）
        inFlightTasks.incrementAndGet();
        if (shutdownRequested.get()) {
            finishInFlightTask();
            task.status = TaskStatus.FAILED;
            task.output = "AI 任务路由器已关闭";
            postResult(callback, task,
                    AiResult.fail(task.output).source("local").build());
            return;
        }
        try {
            targetExecutor.execute(() -> {
                try {
                    task.status = TaskStatus.RUNNING;

            if ("mini-game".equals(task.taskType)) {
                // Mini-game rules are local and synchronous internally, but
                // this branch is already on the router executor, never the UI
                // thread. It therefore shares the same callback lifecycle as
                // every other assistant task.
                AiResult miniGameResult = tryMiniGame(task);
                if (miniGameResult.success) {
                    task.output = miniGameResult.content;
                    task.status = TaskStatus.COMPLETED;
                    task.costLevel = 0;
                    localTasks.incrementAndGet();
                } else {
                    task.output = miniGameResult.message;
                    task.status = TaskStatus.FAILED;
                }
                postResult(callback, task, miniGameResult);
                return;
            }

            // 第1步：只在明确的本地模式下尝试本地处理。
            // CLOUD_ONLY 必须跳过本地路径，LOCAL_ONLY 绝不进入网络路径。
            AiResult localResult = tryLocalProcessing(task, routingMode);
            if (localResult != null) {
                // 本地处理有结果了
                if (localResult.success) {
                    task.output = localResult.content;
                    task.status = TaskStatus.COMPLETED;
                    task.costLevel = 0; // 本地处理零成本（不消耗云端额度）
                    localTasks.incrementAndGet();
                    postResult(callback, task, localResult);
                    return; // 本地处理成功，直接返回
                } else if (routingMode == AiRoutingMode.LOCAL_ONLY) {
                    // 无论是模型未下载、内存不足还是推理异常，本地模式都
                    // 必须返回统一的可恢复错误，不能静默升级为网络请求。
                    postLocalOnlyUnavailable(callback, task, localResult.message);
                    return;
                } else if (shouldFallbackToCloud(localResult)) {
                    // 目前 CLOUD_ONLY 不会进入本地路径；保留日志以防未来增加自动模式时遗漏边界。
                    Log.w(TAG, "Local processing failed before cloud routing: " + localResult.message);
                } else {
                    // 本地处理失败，不需要回退到云端
                    task.output = localResult.message;
                    task.status = TaskStatus.FAILED;
                    postResult(callback, task, localResult);
                    return;
                }
            }

            if (routingMode == AiRoutingMode.LOCAL_ONLY) {
                postLocalOnlyUnavailable(callback, task,
                        "当前任务没有可用的本地处理能力。切换到云端模式并确认后可继续。");
                return;
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
                    cloudTasks.incrementAndGet();
                    aiPrefs.incrementUsage(); // 消耗一次免费额度
                    postResult(callback, task, result);
                } else {
                    task.status = TaskStatus.FAILED;
                    task.output = result.message;
                    cloudTasks.incrementAndGet();
                    postResult(callback, task, result);
                }
            } catch (Exception e) {
                task.status = TaskStatus.FAILED;
                task.output = "请求失败: " + e.getMessage();
                Log.e(TAG, "Cloud AI task failed", e);
                postResult(callback, task,
                        AiResult.fail(task.output).errorCode(AiErrorCode.NETWORK_ERROR).build());
            }
                } finally {
                    finishInFlightTask();
                }
            });
        } catch (RuntimeException error) {
            finishInFlightTask();
            throw error;
        }
    }

    private void finishInFlightTask() {
        if (inFlightTasks.decrementAndGet() == 0 && shutdownRequested.get()) {
            localLlmEngine.close();
        }
    }

    /**
     * 判断任务是否是本地LLM的候选任务。
     * 用于决定将任务提交到哪个线程池。
     *
     * @param task 待检查的任务
     * @return 是否可能是本地LLM任务
     */
    private boolean isLocalLlmCandidate(AiTask task) {
        return supportsLocalLlm(task.taskType);
    }

    /**
     * 判断某类本地失败是否属于“如果未来引入自动模式，可以考虑回退”的类型。
     * <p>
     * 以下情况具有可回退语义，但当前 LOCAL_ONLY 仍不会自动联网：
     * <ul>
     *   <li>本地LLM输出退化（乱码、重复、无意义内容）</li>
     *   <li>本地LLM推理失败（模型加载失败等）</li>
     * </ul>
     * 以下情况不应该回退：
     * <ul>
     *   <li>内存不足（设备硬件限制，云端也无法解决）</li>
     *   <li>模型未下载（用户需要手动下载）</li>
     * </ul>
     *
     * @param localResult 本地处理结果
     * @return 是否属于可回退失败类型
     */
    private boolean shouldFallbackToCloud(AiResult localResult) {
        if (localResult.success) {
            return false; // 成功不需要回退
        }
        // 输出退化或推理失败，应该回退到云端
        return localResult.hasErrorCode(AiErrorCode.LOCAL_LLM_DEGENERATED_OUTPUT)
                || localResult.hasErrorCode(AiErrorCode.LOCAL_LLM_ERROR);
    }

    /**
     * 将本地模式下的“本地无法完成”转换成明确的、不可联网的结果。
     */
    private void postLocalOnlyUnavailable(AiCallback callback, AiTask task, String detail) {
        String suffix = detail == null || detail.trim().isEmpty() ? "" : "\n" + detail.trim();
        task.status = TaskStatus.FAILED;
        task.output = "本地模式未上传数据，无法完成本次请求。请切换到云端模式并确认后重试。" + suffix;
        postResult(callback, task,
                AiResult.fail(task.output)
                        .source("local")
                        .errorCode(AiErrorCode.LOCAL_ONLY_UNAVAILABLE)
                        .build());
    }

    private AiResult tryMiniGame(AiTask task) {
        String[] parts = task.input == null ? new String[0] : task.input.split(";", 2);
        String gameId = parts.length > 0 ? parts[0].trim() : "";
        String gameInput = parts.length > 1 ? parts[1].trim() : "";
        try {
            String result = CoreAiService.getInstance(appContext)
                    .evaluateMiniGameSync(gameId, gameInput);
            return AiResult.success(result).source("local").build();
        } catch (Throwable error) {
            Log.e(TAG, "Mini-game task failed", error);
            return AiResult.fail("小游戏处理失败: " + error.getMessage())
                    .source("local")
                    .errorCode(AiErrorCode.LOCAL_LLM_ERROR)
                    .build();
        }
    }

    /**
     * 尝试本地处理任务。
     * <p>
     * 仅在 routingMode 为 LOCAL_ONLY 时尝试本地 LLM、规则引擎和指令识别；
     * CLOUD_ONLY 直接返回 null，由调用方进入云端检查链。
     *
     * @param task 待处理的任务
     * @return 本地处理结果；若无法本地处理则返回 null，由调用方决定是否走云端
     */
    private AiResult tryLocalProcessing(AiTask task, AiRoutingMode routingMode) {
        // CLOUD_ONLY 明确跳过本地处理；LOCAL_ONLY 才能进入本地路径。
        if (routingMode != AiRoutingMode.LOCAL_ONLY) return null;

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
                return LocalAiProcessor.translateText(task.input);
            case "rewrite":
                return LocalAiProcessor.polishText(task.input);
            case "qa":
            case "qa_pairs":
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
                            return LocalAiProcessor.translateText(task.input);
                        case "rewrite":
                            return LocalAiProcessor.polishText(task.input);
                        case "qa_pairs":
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
        String selectedModelId = aiPrefs.getLocalModel();
        if ("on-device".equals(selectedModelId)) {
            return null;
        }
        // 检查任务类型是否在本地 LLM 支持范围内
        if (!supportsLocalLlm(task.taskType)) {
            return null;
        }
        AiModelInfo model = aiPrefs.getLocalModelInfo();
        if (model == null) {
            return AiResult.fail("当前本地模型缺少运行元数据，请在“本地模型”列表中重新启用或重新下载该模型。")
                    .source("local-llm")
                    .errorCode(AiErrorCode.LOCAL_LLM_ERROR)
                    .build();
        }
        if (!"mediapipe-llm".equals(model.runtime)) {
            return AiResult.fail("当前本地模型运行时暂不支持: " + model.runtime)
                    .source("local-llm")
                    .errorCode(AiErrorCode.LOCAL_LLM_ERROR)
                    .build();
        }
        // 检查模型文件是否已下载到手机上
        if (!modelDownloadManager.isDownloaded(appContext, model)) {
            return AiResult.fail("本地模型尚未下载完成，请先进入“本地模型”下载并启用: " + model.name)
                    .source("local-llm")
                    .errorCode(AiErrorCode.LOCAL_LLM_ERROR)
                    .build();
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
            return AiResult.success(output).source("local-llm").build();
        } catch (Throwable t) {
            // 使用 Throwable 而非 Exception，以捕获 NoClassDefFoundError 等链接时错误
            // 这类错误在模型库缺失时会出现，用 Exception 抓不住
            Log.e(TAG, "Local Gemma task failed", t);
            return AiResult.fail("本地模型推理失败: " + t.getMessage())
                    .source("local-llm")
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
     * 检查设备可用内存是否满足模型最低要求。
     * <p>
     * 通过 {@link android.app.ActivityManager} 获取设备可用内存，
     * 与模型要求的最低内存比较。若获取失败则默认放行（避免误拦截）。
     * <p>
     * 注意：使用可用内存而非总内存，因为多任务场景下即使总内存满足要求，
     * 可用内存可能不足，导致 OOM。
     *
     * @param minRamMb 模型要求的最低内存（MB）
     * @return 设备内存是否足够；获取信息失败时返回 true（保守放行）
     */
    private boolean hasEnoughMemory(int minRamMb) {
        return com.gamecenter.app.ai.model.DeviceProfiler.INSTANCE.canRunModel(appContext, minRamMb, 24);
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
        String selectedProvider = aiPrefs.getSelectedProvider();
        String selectedModel = aiPrefs.getSelectedModel();
        for (AiProviderConfig p : providers) {
            if (p.enabled && !p.localOnly
                    && p.providerName.equals(selectedProvider)
                    && p.modelName.equals(selectedModel)) {
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
            case "ocr_clean":
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
            case "ocr_clean":
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
                // 通用提示词不宣称执行位置，避免云端模式继续使用端侧语境。
                return "你是一个有用的中文 AI 助手。请用简体中文直接回答。\n"
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
     * 注意：不再关闭线程池，因为使用的是 AppExecutors 统一管理的共享线程池，
     * 线程池生命周期由应用统一管理。
     * 仅关闭模型下载管理器和释放本地 LLM 引擎。
     * </p>
     */
    public void shutdown() {
        if (!shutdownRequested.compareAndSet(false, true)) return;
        // 线程池由 AppExecutors 统一管理，不在此关闭。
        // 等已提交任务结束后再关闭本地引擎，避免与 generate() 并发释放资源。
        modelDownloadManager.shutdown();
        if (inFlightTasks.get() == 0) {
            localLlmEngine.close();
        }
    }

    /**
     * 获取统计信息。
     * <p>
     * 返回格式为："总任务: X | 本地: Y | 云端: Z"
     *
     * @return 统计信息字符串
     */
    public String getStats() {
        return String.format("总任务: %d | 本地: %d | 云端: %d",
                totalTasks.get(), localTasks.get(), cloudTasks.get());
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
