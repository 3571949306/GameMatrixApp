package com.gamecenter.app.ai.ui;

import android.content.Intent;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gamecenter.app.R;
import com.gamecenter.app.ai.AiPreferences;
import com.gamecenter.app.ai.AiTaskRouter;
import com.gamecenter.app.ai.data.AiMessage;
import com.gamecenter.app.ai.data.AiProviderConfig;
import com.gamecenter.app.ai.data.AiResult;
import com.gamecenter.app.ai.data.AiTask;
import com.gamecenter.app.ai.history.AiHistoryStore;
import com.gamecenter.app.ai.legal.AiLegalNotices;
import com.gamecenter.app.ai.model.AiModelDownloadManager;
import com.gamecenter.app.ai.model.AiModelInfo;
import com.gamecenter.app.ai.template.AiTemplateManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 助手页面 — 聊天式交互界面，支持多种 AI 功能。
 *
 * <p>你可以把这个页面想象成一个"智能客服聊天窗口"：
 * 用户在底部输入框打字，选择任务类型（翻译、摘要等），点击发送，
 * AI 就会在后台处理并返回结果，显示在聊天区域中。</p>
 *
 * <p>本 Fragment 是应用 AI 模块的主界面，采用聊天式交互模式，用户可以选择不同的任务类型
 * （闲聊、OCR、摘要、翻译、改写、问答、关键词提取、分类）与 AI 进行交互。</p>
 *
 * <p>核心职责：</p>
 * <ul>
 *   <li>消息的发送、接收与展示（通过 RecyclerView + MessageAdapter）</li>
 *   <li>本地 Gemma 模型的下载、启用与管理</li>
 *   <li>消息历史持久化、收藏、搜索与过滤</li>
 *   <li>预设模板的展示与应用</li>
 *   <li>消息导出（通过系统分享 Intent）</li>
 * </ul>
 *
 * <p>设计决策：</p>
 * <ul>
 *   <li>消息列表采用"新消息在前"（index 0）的倒序排列，配合 LinearLayoutManager 实现最新消息置顶；</li>
 *   <li>维护 messages（全量）和 visibleMessages（过滤后）两个列表，实现收藏/搜索过滤；</li>
 *   <li>所有异步回调中均检查 getActivity() 是否为 null，防止 Fragment 销毁后操作 UI 导致崩溃。</li>
 * </ul>
 */
public class AiFragment extends Fragment {

    /**
     * 支持的 AI 任务类型标识数组，与 taskLabels 一一对应。
     * 顺序为：闲聊、OCR、摘要、翻译、改写、问答、关键词、分类。
     */
    private static final String[] TASK_TYPES = {
            "chat", "ocr", "summary", "translate", "rewrite", "qa", "keywords", "classify"
    };

    /** 任务类型的本地化显示标签，通过 buildTaskLabels() 从字符串资源初始化 */
    private String[] taskLabels;

    /** AI 任务路由器，负责将用户输入分发到本地或云端模型 */
    private AiTaskRouter router;

    /** 消息历史持久化存储 */
    private AiHistoryStore historyStore;

    /** AI 偏好设置（本地模型选择、Gemma 条款同意状态等） */
    private AiPreferences aiPreferences;

    /** 本地模型下载管理器 */
    private AiModelDownloadManager modelDownloadManager;

    /** 消息列表 RecyclerView 的适配器 */
    private MessageAdapter adapter;

    /** 全量消息列表（新消息在 index 0，倒序排列） */
    private final List<AiMessage> messages = new ArrayList<>();

    /** 当前可见的消息列表（经过收藏/搜索过滤后的子集） */
    private final List<AiMessage> visibleMessages = new ArrayList<>();

    /** 已收藏消息的 ID 集合，用于快速判断收藏状态 */
    private final Set<String> favoriteIds = new HashSet<>();

    /** 是否处于"仅显示收藏"过滤模式 */
    private boolean favoritesOnly = false;

    /** 当前搜索关键词（小写），为空表示不过滤 */
    private String currentSearch = "";

    // 以下为界面控件的引用，在 onViewCreated 中通过 findViewById 绑定
    private RecyclerView rvMessages;
    private TextInputEditText etInput;
    private TextInputEditText etSearch;
    private MaterialButton btnSend;
    private MaterialButton btnFavorites;
    private MaterialButton btnExport;
    private MaterialButton btnModelDownload;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private AutoCompleteTextView actTaskType;
    private Chip chipModelStatus;
    private Chip chipModeSwitch;

    /**
     * Fragment 关联到 Activity 时初始化核心依赖。
     * 使用 ApplicationContext 创建依赖，避免 Activity 销毁后持有引用导致泄漏。
     * 就像入职时领工牌，用公司级别的工牌而不是部门级别的，换了部门也不受影响。
     *
     * @param context 宿主 Activity 的上下文
     */
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        router = new AiTaskRouter(context);
        historyStore = new AiHistoryStore(context);
        aiPreferences = new AiPreferences(context);
        modelDownloadManager = new AiModelDownloadManager();
    }

    /**
     * Fragment 从 Activity 分离时释放资源。
     * 关闭任务路由器和模型下载管理器，防止后台线程泄漏。
     * 就像离职时归还工牌和钥匙，避免资源浪费。
     */
    @Override
    public void onDetach() {
        super.onDetach();
        if (router != null) router.shutdown();
        if (modelDownloadManager != null) modelDownloadManager.shutdown();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ai, container, false);
    }

    /**
     * 视图创建完成后的初始化入口。
     * <p>
     * 执行以下初始化（就像新员工入职第一天的各项手续）：
     * <ol>
     *   <li>绑定所有视图引用</li>
     *   <li>从历史存储恢复收藏 ID 集合</li>
     *   <li>初始化任务类型下拉选择器</li>
     *   <li>配置消息列表 RecyclerView</li>
     *   <li>注册按钮点击和文本变化监听器</li>
     *   <li>加载历史消息或显示欢迎提示</li>
     * </ol>
     *
     * @param view               Fragment 的根视图
     * @param savedInstanceState 保存的实例状态（未使用）
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 绑定界面控件
        rvMessages = view.findViewById(R.id.rv_ai_messages);
        etInput = view.findViewById(R.id.et_ai_input);
        etSearch = view.findViewById(R.id.et_ai_search);
        btnSend = view.findViewById(R.id.btn_ai_send);
        btnFavorites = view.findViewById(R.id.btn_ai_favorites);
        btnExport = view.findViewById(R.id.btn_ai_export);
        btnModelDownload = view.findViewById(R.id.btn_ai_model_download);
        progressBar = view.findViewById(R.id.progress_ai);
        tvStatus = view.findViewById(R.id.tv_ai_status);
        actTaskType = view.findViewById(R.id.act_ai_task_type);
        chipModelStatus = view.findViewById(R.id.chip_model_status);
        chipModeSwitch = view.findViewById(R.id.chip_mode_switch);
        // 从持久化存储恢复收藏集合
        favoriteIds.clear();
        favoriteIds.addAll(historyStore.getFavoriteIds());

        // 初始化任务类型下拉框
        taskLabels = buildTaskLabels();
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, taskLabels);
        actTaskType.setAdapter(typeAdapter);
        actTaskType.setText(taskLabels[0], false);

        // 配置消息列表 RecyclerView
        adapter = new MessageAdapter(visibleMessages, favoriteIds, this::toggleFavorite);
        rvMessages.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMessages.setAdapter(adapter);

        // 注册按钮点击事件
        btnSend.setOnClickListener(v -> sendMessage());
        btnFavorites.setOnClickListener(v -> {
            favoritesOnly = !favoritesOnly;
            updateFavoriteFilterButton();
            applyMessageFilter();
        });
        btnExport.setOnClickListener(v -> exportMessages());
        if (btnModelDownload != null) {
            btnModelDownload.setOnClickListener(v -> showLocalModelDialog());
        }
        if (chipModelStatus != null) {
            chipModelStatus.setOnClickListener(v -> showCloudModelDialog());
        }
        if (chipModeSwitch != null) {
            chipModeSwitch.setOnClickListener(v -> {
                boolean useCloud = chipModeSwitch.isChecked();
                aiPreferences.setLocalFirst(!useCloud);
                updateModelControls();
            });
        }
        setupTemplates(view);

        MaterialButton btnClearHistory = view.findViewById(R.id.btn_ai_open_full);
        if (btnClearHistory != null) {
            btnClearHistory.setText("清空历史");
            btnClearHistory.setOnClickListener(v -> clearHistory());
        }

        // 输入框变化时更新发送按钮的可用状态
        etInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                // 输入框有内容时才能发送
                btnSend.setEnabled(s != null && s.toString().trim().length() > 0);
            }
        });

        // 搜索框变化时实时过滤消息列表
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                currentSearch = s == null ? "" : s.toString().trim().toLowerCase();
                applyMessageFilter();
            }
        });

        // 加载历史消息，若无历史则显示欢迎提示
        List<AiMessage> savedMessages = historyStore.loadMessages();
        if (savedMessages.isEmpty()) {
            messages.add(new AiMessage("system", "AI 助手已就绪。请选择任务类型并输入内容开始使用。", "chat", "local"));
        } else {
            messages.addAll(savedMessages);
        }
        applyMessageFilter();
        scrollToBottom();

        updateModelControls();
        updateStatus("就绪");
    }

    /**
     * 显示本地模型下载对话框。
     * <p>
     * 先从服务器获取可用模型清单，成功后展示模型信息对话框，
     * 失败则提示用户。所有 UI 操作通过 runOnUiThread 切换到主线程。
     */
    private void showLocalModelDialog() {
        if (modelDownloadManager == null) return;
        updateStatus("正在获取模型清单");
        modelDownloadManager.fetchModels(new AiModelDownloadManager.Callback<List<AiModelInfo>>() {
            @Override
            public void onSuccess(List<AiModelInfo> models) {
                // 检查 Fragment 是否还关联着 Activity，防止操作已销毁的 UI
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> showModelList(models));
            }

            @Override
            public void onError(Exception error) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    updateStatus("模型清单获取失败");
                    Toast.makeText(requireContext(), "模型清单获取失败: " + error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * 展示本地模型信息对话框。
     * <p>
     * 根据模型当前状态（未下载/已下载未启用/已启用）显示不同的操作按钮：
     * <ul>
     *   <li>未下载且已开放 → 显示"下载"按钮</li>
     *   <li>已下载但未启用 → 显示"启用"按钮</li>
     *   <li>已下载并启用 → 无操作按钮</li>
     * </ul>
     *
     * @param models 从服务器获取的模型信息列表
     */
    private void showModelList(List<AiModelInfo> models) {
        if (models == null || models.isEmpty()) {
            updateStatus("暂无本地模型");
            Toast.makeText(requireContext(), "服务器暂无可用本地模型", Toast.LENGTH_LONG).show();
            return;
        }
        String[] labels = new String[models.size()];
        String selectedId = aiPreferences != null ? aiPreferences.getLocalModel() : "on-device";
        for (int i = 0; i < models.size(); i++) {
            AiModelInfo model = models.get(i);
            labels[i] = (model.id.equals(selectedId) ? "✓ " : "")
                    + model.name
                    + " · "
                    + performanceTier(model)
                    + (model.enabled ? "" : " · 未开放");
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("本地模型")
                .setItems(labels, (dialog, which) -> showModelDetail(models.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        updateStatus("请选择本地模型");
    }

    /**
     * 展示单个本地模型的详情与可执行操作。
     */
    private void showModelDetail(AiModelInfo model) {
        boolean rulesModel = "on-device".equals(model.id);
        boolean downloaded = rulesModel || modelDownloadManager.isDownloaded(requireContext(), model);
        StringBuilder message = new StringBuilder();
        message.append(model.name).append("\n");
        message.append("性能档位: ").append(performanceTier(model)).append("\n");
        message.append("运行时: ").append(model.runtime).append("\n");
        message.append("大小: ").append(formatBytes(model.sizeBytes)).append("\n");
        message.append("峰值内存: ").append(formatBytes(model.estimatedPeakMemoryBytes)).append("\n");
        if (!rulesModel) {
            message.append("存储位置: App 私有目录 / Android/data/")
                    .append(requireContext().getPackageName())
                    .append("/files/Documents/ai_models\n\n");
        } else {
            message.append("\n");
        }
        if (downloaded) {
            boolean selected = aiPreferences != null && model.id.equals(aiPreferences.getLocalModel());
            message.append(selected
                    ? "状态: 已启用。本地优先任务会使用该档位处理。"
                    : "状态: 可启用。点击启用后，本地优先任务会优先使用该档位。");
        } else if (!model.enabled) {
            message.append("状态: 暂未开放下载。\n").append(model.note);
            if (!model.upstreamUrl.isEmpty()) {
                message.append("\n\n上游: ").append(model.upstreamUrl);
            }
        } else {
            message.append("状态: 可下载。建议在 Wi-Fi 和充电环境下操作。");
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("本地模型详情")
                .setMessage(message.toString())
                .setNegativeButton(android.R.string.cancel, null);
        if (!rulesModel) {
            builder.setNeutralButton("查看条款", (dialog, which) -> openGemmaTerms());
        }
        // 根据模型状态动态设置正向按钮
        if (rulesModel && aiPreferences != null && !model.id.equals(aiPreferences.getLocalModel())) {
            builder.setPositiveButton("启用", (dialog, which) -> enableLocalModel(model));
        } else if (model.enabled && !downloaded) {
            builder.setPositiveButton("下载", (dialog, which) -> confirmGemmaNoticeThenDownload(model));
        } else if (downloaded && aiPreferences != null
                && !model.id.equals(aiPreferences.getLocalModel())) {
            builder.setPositiveButton("启用", (dialog, which) -> enableLocalModel(model));
        }
        builder.show();
        updateStatus(downloaded ? "本地模型已下载" : "本地模型未下载");
    }

    private String performanceTier(AiModelInfo model) {
        if (model.minRamMb <= 2048) {
            return "低端机";
        }
        if (model.minRamMb <= 4096) {
            return "中端机";
        }
        return "高端机";
    }

    /**
     * 展示云端模型选择列表。
     * 同一个 API Key 会用于所选 OpenAI 兼容供应商，用户可按自己的 Key 来源切换模型。
     */
    private void showCloudModelDialog() {
        if (aiPreferences == null) return;
        List<AiProviderConfig> providers = AiPreferences.getAvailableProviders(requireContext());
        List<AiProviderConfig> cloudProviders = new ArrayList<>();
        for (AiProviderConfig provider : providers) {
            if (!provider.localOnly) {
                cloudProviders.add(provider);
            }
        }
        if (cloudProviders.isEmpty()) {
            Toast.makeText(requireContext(), "暂无云端模型", Toast.LENGTH_SHORT).show();
            return;
        }
        String selectedProvider = aiPreferences.getSelectedProvider();
        String selectedModel = aiPreferences.getSelectedModel();
        String[] labels = new String[cloudProviders.size()];
        for (int i = 0; i < cloudProviders.size(); i++) {
            AiProviderConfig provider = cloudProviders.get(i);
            labels[i] = (provider.providerName.equals(selectedProvider)
                    && provider.modelName.equals(selectedModel) ? "✓ " : "")
                    + provider.providerName
                    + " · "
                    + provider.modelName
                    + " · "
                    + cloudTier(provider);
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("云端模型")
                .setItems(labels, (dialog, which) -> {
                    AiProviderConfig provider = cloudProviders.get(which);
                    aiPreferences.setSelectedProvider(provider.providerName);
                    aiPreferences.setSelectedModel(provider.modelName);
                    aiPreferences.setLocalFirst(false);
                    updateModelControls();
                    updateStatus("已选择 " + provider.providerName + " · " + provider.modelName);
                })
                .setMessage(aiPreferences.getApiKey().isEmpty()
                        ? "尚未配置 API Key；选择会保存，但调用云端前仍需要先配置 Key。"
                        : null)
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String cloudTier(AiProviderConfig provider) {
        if (provider.costLevel <= 1) {
            return "省电/低延迟";
        }
        if (provider.costLevel == 2) {
            return "均衡";
        }
        return "高能力/长输出";
    }

    private void updateModelControls() {
        if (aiPreferences == null) return;
        boolean localFirst = aiPreferences.isLocalFirst();
        if (chipModeSwitch != null) {
            chipModeSwitch.setChecked(!localFirst);
            chipModeSwitch.setText(localFirst ? "切换到云端" : "切换到本地");
        }
        if (chipModelStatus != null) {
            AiModelInfo localModel = aiPreferences.getLocalModelInfo();
            String localLabel = localModel != null ? localModel.name : aiPreferences.getLocalModel();
            chipModelStatus.setText(localFirst
                    ? "本地: " + localLabel
                    : aiPreferences.getSelectedProvider() + ": " + aiPreferences.getSelectedModel());
        }
    }

    /**
     * 确认 Gemma 条款后下载模型。
     * <p>
     * 若用户已同意当前版本的 Gemma 条款，直接开始下载；
     * 否则弹出条款确认对话框，用户同意后记录同意状态并开始下载。
     * 就像下载付费软件前要先同意用户协议一样。
     *
     * @param model 待下载的模型信息
     */
    private void confirmGemmaNoticeThenDownload(AiModelInfo model) {
        if (aiPreferences != null
                && aiPreferences.hasAcceptedGemmaNotice(AiLegalNotices.GEMMA_NOTICE_VERSION)) {
            downloadModel(model);
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Gemma 模型条款与本地 AI 说明")
                .setMessage(AiLegalNotices.buildGemmaDownloadNotice(model))
                .setNeutralButton("查看条款", (dialog, which) -> openGemmaTerms())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("同意并下载", (dialog, which) -> {
                    if (aiPreferences != null) {
                        // 记录用户已同意当前版本的 Gemma 条款
                        aiPreferences.acceptGemmaNotice(AiLegalNotices.GEMMA_NOTICE_VERSION);
                    }
                    downloadModel(model);
                })
                .show();
    }

    /**
     * 在浏览器中打开 Gemma 使用条款页面。
     * 若无法启动浏览器（如无浏览器应用），则将 URL 以 Toast 形式展示给用户。
     */
    private void openGemmaTerms() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(AiLegalNotices.GEMMA_TERMS_URL)));
        } catch (Exception e) {
            Toast.makeText(requireContext(), AiLegalNotices.GEMMA_TERMS_URL, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 启用指定的本地模型。
     * <p>
     * 将模型 ID 写入偏好设置，并开启"本地优先"模式，
     * 使本地优先任务优先使用该模型进行推理。
     *
     * @param model 要启用的模型信息
     */
    private void enableLocalModel(AiModelInfo model) {
        if (aiPreferences != null) {
            aiPreferences.setLocalModelInfo(model);
            aiPreferences.setLocalFirst(true);
        }
        updateModelControls();
        updateStatus("本地模型已启用");
        Toast.makeText(requireContext(), "已启用 " + model.name, Toast.LENGTH_LONG).show();
    }

    /**
     * 下载本地模型文件。
     * <p>
     * 显示进度条，通过 AiModelDownloadManager 异步下载模型文件。
     * 下载过程中实时更新进度状态，完成后自动启用模型。
     * 所有 UI 更新通过 runOnUiThread 切换到主线程执行。
     *
     * @param model 待下载的模型信息
     */
    private void downloadModel(AiModelInfo model) {
        progressBar.setVisibility(View.VISIBLE);
        updateStatus("模型下载中");
        modelDownloadManager.download(requireContext().getApplicationContext(), model,
                new AiModelDownloadManager.DownloadCallback() {
                    @Override
                    public void onProgress(long downloaded, long total) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> updateStatus("模型下载 " + formatBytes(downloaded)
                                + (total > 0 ? " / " + formatBytes(total) : "")));
                    }

                    @Override
                    public void onComplete(File file) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            // 下载完成后自动启用该模型
                            enableLocalModel(model);
                            updateStatus("本地模型已下载");
                            Toast.makeText(requireContext(), "模型已下载: " + file.getName(), Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            progressBar.setVisibility(View.GONE);
                            updateStatus("模型下载失败");
                            Toast.makeText(requireContext(), "模型下载失败: " + error.getMessage(), Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    /**
     * 将字节数格式化为人类可读的文件大小字符串。
     * <p>
     * 依次使用 B → KB → MB → GB 单位，保留 1-2 位小数。
     *
     * @param bytes 字节数
     * @return 格式化后的字符串，如 "1.5 MB"；bytes ≤ 0 时返回 "未知"
     */
    private String formatBytes(long bytes) {
        if (bytes <= 0) return "未知";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.CHINA, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return String.format(java.util.Locale.CHINA, "%.1f MB", bytes / 1024.0 / 1024.0);
        return String.format(java.util.Locale.CHINA, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0);
    }

    /**
     * 发送用户消息并提交 AI 任务。
     * <p>
     * 流程（就像寄信：写信 → 投递 → 等回信）：
     * <ol>
     *   <li>读取输入框内容，解析任务类型</li>
     *   <li>将用户消息添加到列表头部并持久化</li>
     *   <li>显示加载状态，禁用发送按钮</li>
     *   <li>通过 AiTaskRouter 提交任务，回调中处理成功/失败结果</li>
     * </ol>
     */
    private void sendMessage() {
        String input = etInput.getText().toString().trim();
        if (input.isEmpty()) return;

        String taskType = resolveTaskType(actTaskType.getText().toString().trim());
        final String ftaskType = taskType;

        // 添加用户消息到列表头部（倒序排列，新消息在前）
        AiMessage userMsg = new AiMessage("user", input, taskType, "user");
        messages.add(0, userMsg);
        saveHistory();
        applyMessageFilter();
        scrollToBottom();
        etInput.setText("");

        // 显示加载状态，禁用发送按钮防止重复提交
        progressBar.setVisibility(View.VISIBLE);
        updateStatus("处理中…");
        btnSend.setEnabled(false);

        // 提交 AI 任务，通过回调异步获取结果
        router.submitTask(taskType, input, new AiTaskRouter.AiCallback() {
            @Override
            public void onResult(AiTask task, AiResult result) {
                // 检查视图是否还存在，防止 Fragment 已销毁时操作 UI
                if (getView() == null) return;
                progressBar.setVisibility(View.GONE);
                btnSend.setEnabled(true);

                if (result.success) {
                    // 成功：将 AI 回复添加到消息列表
                    messages.add(0, new AiMessage("assistant", result.content, ftaskType, result.source));
                    saveHistory();
                    applyMessageFilter();
                    scrollToBottom();
                    updateStatus("完成 | " + result.source);
                } else {
                    // 失败：以 error 来源标记，内容前加错误符号
                    messages.add(0, new AiMessage("assistant", "❌ " + result.message, ftaskType, "error"));
                    saveHistory();
                    applyMessageFilter();
                    scrollToBottom();
                    updateStatus("失败: " + result.errorCode);
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    /**
     * 更新底部状态栏文本。
     *
     * @param text 要显示的状态文本
     */
    private void updateStatus(String text) {
        if (tvStatus != null) tvStatus.setText(text);
    }

    /**
     * 将用户在任务类型下拉框中选择的标签解析为内部任务类型标识。
     * <p>
     * 支持传入本地化标签（如"摘要"）或内部标识（如"summary"），
     * 无法匹配时默认返回 "summary"。
     *
     * @param labelOrType 用户选择的标签或内部标识
     * @return 对应的内部任务类型标识
     */
    private String resolveTaskType(String labelOrType) {
        if (labelOrType == null || labelOrType.isEmpty()) {
            return "summary";
        }
        String[] labels = taskLabels != null ? taskLabels : buildTaskLabels();
        for (int i = 0; i < labels.length; i++) {
            if (labels[i].equals(labelOrType) || TASK_TYPES[i].equals(labelOrType)) {
                return TASK_TYPES[i];
            }
        }
        return "summary";
    }

    /**
     * 从字符串资源构建任务类型的本地化显示标签数组。
     * <p>
     * 返回数组与 {@link #TASK_TYPES} 一一对应。
     *
     * @return 本地化标签数组
     */
    private String[] buildTaskLabels() {
        return new String[]{
                getString(R.string.ai_task_chat),
                getString(R.string.ai_task_ocr_clean),
                getString(R.string.ai_task_summary),
                getString(R.string.ai_task_translate),
                getString(R.string.ai_task_rewrite),
                getString(R.string.ai_task_qa_pairs),
                getString(R.string.ai_task_keywords),
                getString(R.string.ai_task_classify)
        };
    }

    /**
     * 将当前消息列表持久化到历史存储。
     */
    private void saveHistory() {
        if (historyStore != null) {
            historyStore.saveMessages(messages);
        }
    }

    /**
     * 清空所有历史记录、收藏和搜索状态，并显示系统提示消息。
     */
    private void clearHistory() {
        if (historyStore != null) {
            historyStore.clear();
        }
        messages.clear();
        favoriteIds.clear();
        favoritesOnly = false;
        currentSearch = "";
        if (etSearch != null) {
            etSearch.setText("");
        }
        messages.add(new AiMessage("system", "历史记录已清空。", "chat", "local"));
        updateFavoriteFilterButton();
        applyMessageFilter();
        updateStatus("历史已清空");
    }

    /**
     * 初始化预设模板按钮区域。
     * <p>
     * 从 {@link AiTemplateManager} 获取所有预设模板，为每个模板创建一个 MaterialButton，
     * 点击后将模板的任务类型和提示词填入输入区域。
     *
     * @param root Fragment 的根视图，用于查找模板容器布局
     */
    private void setupTemplates(View root) {
        LinearLayout layout = root.findViewById(R.id.layout_ai_templates);
        if (layout == null) return;
        layout.removeAllViews();
        // 6dp 的水平间距，转换为像素值
        int margin = (int) (6 * getResources().getDisplayMetrics().density);
        for (AiTemplateManager.Template template : AiTemplateManager.getTemplates()) {
            MaterialButton button = new MaterialButton(requireContext(), null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setText(template.title);
            button.setTextSize(12);
            button.setMinHeight(36);
            button.setMinimumHeight(36);
            button.setOnClickListener(v -> applyTemplate(template));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(margin, 0, margin, 0);
            layout.addView(button, lp);
        }
    }

    /**
     * 将预设模板应用到输入区域。
     * <p>
     * 设置任务类型下拉框为模板对应的类型，并将模板提示词填入输入框，
     * 光标移到末尾方便用户继续编辑。
     *
     * @param template 要应用的预设模板
     */
    private void applyTemplate(AiTemplateManager.Template template) {
        actTaskType.setText(labelForTask(template.taskType), false);
        etInput.setText(template.prompt);
        etInput.setSelection(etInput.getText() != null ? etInput.getText().length() : 0);
        updateStatus("已套用模板: " + template.title);
    }

    /**
     * 根据内部任务类型标识获取对应的本地化显示标签。
     *
     * @param taskType 内部任务类型标识（如 "summary"）
     * @return 对应的本地化标签（如 "摘要"）；未匹配时返回 "总结"
     */
    private String labelForTask(String taskType) {
        String[] labels = taskLabels != null ? taskLabels : buildTaskLabels();
        for (int i = 0; i < TASK_TYPES.length; i++) {
            if (TASK_TYPES[i].equals(taskType)) {
                return labels[i];
            }
        }
        return "总结";
    }

    /**
     * 切换消息的收藏状态。
     * <p>
     * 系统消息（role 为 "system"）不支持收藏。
     * 切换后更新本地收藏集合并刷新消息列表显示。
     *
     * @param message 要切换收藏状态的消息
     */
    private void toggleFavorite(AiMessage message) {
        if ("system".equals(message.role)) {
            return;
        }
        boolean favorite = historyStore.toggleFavorite(message.id);
        if (favorite) {
            favoriteIds.add(message.id);
        } else {
            favoriteIds.remove(message.id);
        }
        applyMessageFilter();
        Toast.makeText(requireContext(), favorite ? "已收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
    }

    /**
     * 更新收藏过滤按钮的显示文本。
     * <p>
     * 收藏模式激活时显示"全部"（点击可切回全部），
     * 非收藏模式时显示"收藏"（点击可进入收藏模式）。
     */
    private void updateFavoriteFilterButton() {
        if (btnFavorites != null) {
            btnFavorites.setText(favoritesOnly ? "全部" : "收藏");
        }
    }

    /**
     * 根据当前过滤条件（收藏模式、搜索关键词）更新可见消息列表。
     * <p>
     * 过滤逻辑（就像用筛子筛沙子，可以叠加多个筛子）：
     * <ul>
     *   <li>收藏模式：仅显示 favoriteIds 中包含的消息</li>
     *   <li>搜索模式：仅显示角色、内容、任务类型中包含搜索关键词的消息</li>
     *   <li>两个条件可叠加（收藏 + 搜索同时生效）</li>
     * </ul>
     */
    private void applyMessageFilter() {
        visibleMessages.clear();
        for (AiMessage message : messages) {
            // 收藏过滤：仅显示已收藏消息
            if (favoritesOnly && !favoriteIds.contains(message.id)) {
                continue;
            }
            // 搜索过滤：在角色、内容、任务类型中查找关键词
            if (!currentSearch.isEmpty()) {
                String haystack = (message.role + " " + message.content + " " + message.taskType).toLowerCase();
                if (!haystack.contains(currentSearch)) {
                    continue;
                }
            }
            visibleMessages.add(message);
        }
        adapter.notifyDataSetChanged();
        updateStatus(buildStatusText());
    }

    /**
     * 构建底部状态栏的文本内容。
     * <p>
     * 根据当前过滤模式显示不同的前缀和计数：
     * 收藏模式 → "收藏 N"；搜索模式 → "搜索 N"；默认 → "历史 N"（排除系统消息）。
     *
     * @return 状态栏文本
     */
    private String buildStatusText() {
        if (favoritesOnly) {
            return "收藏 " + visibleMessages.size();
        }
        if (!currentSearch.isEmpty()) {
            return "搜索 " + visibleMessages.size();
        }
        // 默认模式下排除系统消息计数，只显示用户和 AI 消息数量
        return "历史 " + Math.max(0, messages.size() - countSystemMessages());
    }

    /**
     * 统计全量消息列表中系统消息的数量。
     *
     * @return 系统消息数量
     */
    private int countSystemMessages() {
        int count = 0;
        for (AiMessage message : messages) {
            if ("system".equals(message.role)) count++;
        }
        return count;
    }

    /**
     * 通过系统分享 Intent 导出当前可见消息。
     * <p>
     * 导出内容为纯文本格式，包含每条消息的角色、任务类型和内容。
     * 若处于收藏或搜索模式，仅导出当前可见消息；否则导出全量消息。
     */
    private void exportMessages() {
        String exportText = buildExportText();
        if (exportText.isEmpty()) {
            Toast.makeText(requireContext(), "没有可导出的内容", Toast.LENGTH_SHORT).show();
            return;
        }
        // 使用 Android 系统的分享功能，让用户选择导出方式（微信、邮件等）
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "GameCenter AI 导出");
        intent.putExtra(Intent.EXTRA_TEXT, exportText);
        startActivity(Intent.createChooser(intent, "导出 AI 记录"));
    }

    /**
     * 构建导出文本内容。
     * <p>
     * 消息按时间正序排列（从旧到新），系统消息不导出，
     * 收藏消息会额外标注 [收藏] 标记。
     *
     * @return 格式化后的导出文本
     */
    private String buildExportText() {
        StringBuilder sb = new StringBuilder();
        sb.append("GameCenter AI 记录\n\n");
        // 收藏/搜索模式下导出可见消息，否则导出全量消息
        List<AiMessage> source = (favoritesOnly || !currentSearch.isEmpty()) ? visibleMessages : messages;
        // 倒序列表从末尾遍历，实现时间正序输出
        for (int i = source.size() - 1; i >= 0; i--) {
            AiMessage message = source.get(i);
            if ("system".equals(message.role)) {
                continue;
            }
            sb.append(roleLabel(message.role))
                    .append(" [").append(message.taskType).append("]");
            if (favoriteIds.contains(message.id)) {
                sb.append(" [收藏]");
            }
            sb.append("\n").append(message.content).append("\n\n");
        }
        return sb.toString().trim();
    }

    /**
     * 将消息角色标识转换为中文显示标签。
     *
     * @param role 消息角色（"user"、"assistant" 或 "system"）
     * @return 中文角色标签
     */
    private String roleLabel(String role) {
        if ("user".equals(role)) return "用户";
        if ("assistant".equals(role)) return "AI";
        return "系统";
    }

    /**
     * 将消息列表平滑滚动到最新消息位置（index 0，即列表顶部）。
     * <p>
     * 使用 post 确保在布局更新后再执行滚动，避免 RecyclerView 尚未完成测量导致滚动失败。
     */
    private void scrollToBottom() {
        rvMessages.post(() -> {
            if (adapter.getItemCount() > 0) {
                rvMessages.smoothScrollToPosition(0);
            }
        });
    }

    /**
     * 消息列表适配器。
     * <p>
     * 将 {@link AiMessage} 列表绑定到 RecyclerView，支持收藏状态显示。
     * 使用 visibleMessages 作为数据源，确保过滤后的结果正确展示。
     * 就像翻译官，把 AiMessage 数据"翻译"成界面上的消息气泡。
     */
    private static class MessageAdapter extends RecyclerView.Adapter<MessageViewHolder> {

        private final List<AiMessage> messages;
        private final Set<String> favoriteIds;
        private final FavoriteListener favoriteListener;

        /**
         * @param messages         可见消息列表（过滤后）
         * @param favoriteIds      已收藏消息 ID 集合
         * @param favoriteListener 收藏切换回调
         */
        MessageAdapter(List<AiMessage> messages, Set<String> favoriteIds, FavoriteListener favoriteListener) {
            this.messages = messages;
            this.favoriteIds = favoriteIds;
            this.favoriteListener = favoriteListener;
        }

        @NonNull
        @Override
        public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // 从布局文件创建单条消息的视图
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_ai_message, parent, false);
            return new MessageViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
            // 将消息数据绑定到视图上
            AiMessage msg = messages.get(position);
            holder.bind(msg, favoriteIds.contains(msg.id), favoriteListener);
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }
    }

    /**
     * 消息 ViewHolder。
     * <p>
     * 根据消息角色（user/assistant/system）设置不同的背景色和文字颜色，
     * 并管理收藏按钮的显示与交互。
     * 就像不同身份的人穿不同颜色的衣服：用户蓝色、AI 绿色、系统灰色。
     */
    private static class MessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvRole;
        private final TextView tvContent;
        private final ImageButton btnFavorite;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRole = itemView.findViewById(R.id.tv_msg_role);
            tvContent = itemView.findViewById(R.id.tv_msg_content);
            btnFavorite = itemView.findViewById(R.id.btn_msg_favorite);
        }

        /**
         * 绑定消息数据到视图。
         * <p>
         * 根据消息角色设置不同的视觉样式：
         * <ul>
         *   <li>user — 用户消息样式</li>
         *   <li>assistant — AI 助手消息样式</li>
         *   <li>system — 系统提示样式（隐藏收藏按钮）</li>
         * </ul>
         *
         * @param msg              消息数据
         * @param favorite         是否已收藏
         * @param favoriteListener 收藏切换回调
         */
        void bind(AiMessage msg, boolean favorite, FavoriteListener favoriteListener) {
            if (msg.role.equals("user")) {
                tvRole.setText("你");
                itemView.setBackgroundResource(R.drawable.bg_ai_message_user);
                tvRole.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.ai_message_user_role));
                tvContent.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.ai_message_user_text));
            } else if (msg.role.equals("assistant")) {
                tvRole.setText("AI助手");
                itemView.setBackgroundResource(R.drawable.bg_ai_message_assistant);
                tvRole.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.ai_message_assistant_role));
                tvContent.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.ai_message_assistant_text));
            } else {
                tvRole.setText("系统");
                itemView.setBackgroundResource(R.drawable.bg_ai_message_system);
                tvRole.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.ai_message_system_role));
                tvContent.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.ai_message_system_text));
            }
            tvContent.setText(msg.content);
            btnFavorite.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.ai_message_star));
            // 系统消息不显示收藏按钮
            if ("system".equals(msg.role)) {
                btnFavorite.setVisibility(View.GONE);
            } else {
                btnFavorite.setVisibility(View.VISIBLE);
                // 根据收藏状态显示不同的星星图标
                btnFavorite.setImageResource(favorite
                        ? android.R.drawable.btn_star_big_on
                        : android.R.drawable.btn_star_big_off);
                btnFavorite.setOnClickListener(v -> favoriteListener.onToggleFavorite(msg));
            }
        }
    }

    /**
     * 收藏切换回调接口。
     */
    private interface FavoriteListener {
        /**
         * 用户点击收藏按钮时触发。
         *
         * @param message 被点击的消息
         */
        void onToggleFavorite(AiMessage message);
    }
}
