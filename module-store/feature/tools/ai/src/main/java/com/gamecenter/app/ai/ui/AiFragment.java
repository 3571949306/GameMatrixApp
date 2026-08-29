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
import android.view.inputmethod.EditorInfo;
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
import com.gamecenter.app.ai.AiRequestGate;
import com.gamecenter.app.ai.AiPreferences;
import com.gamecenter.app.ai.AiRoutingMode;
import com.gamecenter.app.ai.AiTaskCatalog;
import com.gamecenter.app.ai.AiTaskRouter;
import com.gamecenter.app.ai.data.AiErrorCode;
import com.gamecenter.app.ai.data.AiMessage;
import com.gamecenter.app.ai.data.AiProviderConfig;
import com.gamecenter.app.ai.data.AiResult;
import com.gamecenter.app.core.common.ConsentComponent;
import com.gamecenter.app.core.common.ConsentDecision;
import com.gamecenter.app.ui.ConsentDialog;
import com.gamecenter.app.ai.data.AiTask;
import com.gamecenter.app.ai.history.AiHistoryStore;
import com.gamecenter.app.ai.legal.AiLegalNotices;
import com.gamecenter.app.ai.model.AiModelDownloadManager;
import com.gamecenter.app.ai.model.AiModelInfo;
import com.gamecenter.app.ai.template.AiTemplateManager;
// 2026-06-23: MiMoTtsEngine 改为反射加载，避免 mimo-tts 模块未配置时编译失败
// 原硬依赖: import com.gamecenter.capability.tts.MiMoTtsEngine;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 助手页面 — 聊天式交互界面，支持多种 AI 功能。
 *
 * <p>你可以把这个页面想象成一个"智能客服聊天窗口"：
 * 用户在底部输入框直接提问，点击发送，AI 就会在后台处理并返回结果，
 * 显示在聊天区域中；翻译、摘要等一次性任务通过加号菜单按需附加。</p>
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
 *   <li>消息列表采用"新消息在前"（index 0）的倒序排列，配合反向 LinearLayoutManager 将最新消息置于底部；</li>
 *   <li>维护 messages（全量）和 visibleMessages（过滤后）两个列表，实现收藏/搜索过滤；</li>
 *   <li>所有异步回调中均检查 getActivity() 是否为 null，防止 Fragment 销毁后操作 UI 导致崩溃。</li>
 * </ul>
 */
public class AiFragment extends Fragment {

    /**
     * 支持的 AI 任务类型标识数组，与 taskLabels 一一对应。
     * 顺序为：闲聊、OCR、摘要、翻译、改写、问答、关键词、分类。
     */
    private static final String[] TASK_TYPES = AiTaskCatalog.getTypes().toArray(new String[0]);

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

    /** MiMo TTS 朗读引擎（反射加载，运行时可选） */
    private Object ttsEngine;

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

    /** 当前一次性任务上下文；默认直接聊天，不让任务选择器阻挡首条消息。 */
    private String selectedTaskType = AiTaskCatalog.CHAT;

    /** 统一的单请求与会话代次守卫，防止按钮/IME 重入及旧回调污染新会话。 */
    private final AiRequestGate requestGate = new AiRequestGate();

    /** Consent 对话框正在等待用户决定时，阻止重复弹出多个对话框。 */
    private boolean awaitingConsent;

    // 以下为界面控件的引用，在 onViewCreated 中通过 findViewById 绑定
    private RecyclerView rvMessages;
    private TextInputEditText etInput;
    private MaterialButton btnSend;
    private MaterialButton btnAdd;
    private MaterialButton btnModel;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private TextView tvPrivacy;
    private ImageButton btnHistory;
    private ImageButton btnMore;
    private Chip chipTaskContext;
    private View emptyState;

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

        // Phase 1: 初始化 MiMo TTS 引擎（反射加载，mimo-tts 模块未配置时安全失败）
        if (com.gamecenter.app.BuildConfig.ENABLE_MIMO_TTS) {
            try {
                Class<?> cls = Class.forName("com.gamecenter.capability.tts.MiMoTtsEngine");
                ttsEngine = cls.getDeclaredConstructor(Context.class).newInstance(context);
                cls.getMethod("configure", String.class).invoke(ttsEngine,
                        com.gamecenter.app.BuildConfig.MIMO_API_KEY);
                android.util.Log.i("AiFragment", "MiMo TTS 引擎加载成功");
            } catch (Throwable t) {
                ttsEngine = null;
                android.util.Log.w("AiFragment", "MiMo TTS 引擎加载失败（mimo-tts 模块未包含?）: " + t.getMessage());
            }
        }
    }

    /**
     * Fragment 从 Activity 分离时释放资源。
     * 关闭任务路由器和模型下载管理器，防止后台线程泄漏。
     * 就像离职时归还工牌和钥匙，避免资源浪费。
     */
    @Override
    public void onDetach() {
        // A detached Fragment must not accept results from requests submitted
        // by its old view. The shared executor may still finish the work.
        requestGate.invalidateConversation();
        awaitingConsent = false;
        super.onDetach();
        if (router != null) {
            router.shutdown();
            router = null;
        }
        if (modelDownloadManager != null) {
            modelDownloadManager.shutdown();
            modelDownloadManager = null;
        }
        if (ttsEngine != null) {
            try {
                ttsEngine.getClass().getMethod("stop").invoke(ttsEngine);
            } catch (Throwable ignore) {}
            ttsEngine = null;
        }
    }

    @Override
    public void onDestroyView() {
        // Invalidate before clearing view fields so a queued callback cannot
        // repopulate a newly created view with a result from the old one.
        requestGate.invalidateConversation();
        awaitingConsent = false;
        super.onDestroyView();
        if (rvMessages != null) {
            rvMessages.setAdapter(null);
            rvMessages = null;
        }
        adapter = null;
        messages.clear();
        visibleMessages.clear();
        favoriteIds.clear();
        etInput = null;
        btnSend = null;
        btnAdd = null;
        btnModel = null;
        progressBar = null;
        tvStatus = null;
        tvPrivacy = null;
        btnHistory = null;
        btnMore = null;
        chipTaskContext = null;
        emptyState = null;
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
     *   <li>初始化默认聊天模式和按需工具入口</li>
     *   <li>配置消息列表 RecyclerView</li>
     *   <li>注册按钮点击和文本变化监听器</li>
     *   <li>加载历史消息或显示聊天空状态</li>
     * </ol>
     *
     * @param view               Fragment 的根视图
     * @param savedInstanceState 保存的实例状态（未使用）
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rvMessages = view.findViewById(R.id.rv_ai_messages);
        etInput = view.findViewById(R.id.et_ai_input);
        btnSend = view.findViewById(R.id.btn_ai_send);
        btnAdd = view.findViewById(R.id.btn_ai_add);
        btnModel = view.findViewById(R.id.btn_ai_model);
        btnHistory = view.findViewById(R.id.btn_ai_history);
        btnMore = view.findViewById(R.id.btn_ai_more);
        progressBar = view.findViewById(R.id.progress_ai);
        tvStatus = view.findViewById(R.id.tv_ai_status);
        tvPrivacy = view.findViewById(R.id.tv_ai_privacy);
        chipTaskContext = view.findViewById(R.id.chip_ai_task_context);
        emptyState = view.findViewById(R.id.layout_ai_empty_state);

        favoriteIds.clear();
        favoriteIds.addAll(historyStore.getFavoriteIds());
        taskLabels = buildTaskLabels();

        adapter = new MessageAdapter(requireContext(), visibleMessages, favoriteIds,
                this::toggleFavorite, ttsEngine);
        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        // History storage remains newest-first; reverse layout places the newest
        // message at the bottom without migrating the persisted order.
        layoutManager.setReverseLayout(true);
        layoutManager.setStackFromEnd(true);
        rvMessages.setLayoutManager(layoutManager);
        rvMessages.setAdapter(adapter);

        btnSend.setOnClickListener(v -> sendMessage());
        btnAdd.setOnClickListener(v -> showToolsDialog());
        btnModel.setOnClickListener(v -> showModelChooser());
        btnHistory.setOnClickListener(v -> showHistoryDialog());
        btnMore.setOnClickListener(v -> showMoreDialog());
        chipTaskContext.setOnCloseIconClickListener(v -> selectTaskType(AiTaskCatalog.CHAT));

        setupTemplates(view);
        etInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                updateSendEnabled();
            }
        });
        etInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        messages.clear();
        visibleMessages.clear();
        List<AiMessage> savedMessages = historyStore.loadMessages();
        if (!savedMessages.isEmpty()) {
            messages.addAll(savedMessages);
        }
        applyMessageFilter();
        scrollToBottom();

        updateModelControls();
        updateSendEnabled();
        updateStatus(getString(R.string.ai_chat_status_ready));
    }

    /**
     * Opens the secondary tool surface. The composer stays a normal chat
     * input; one-shot tasks are represented by a removable context chip.
     */
    private void showToolsDialog() {
        if (!isAdded()) return;
        List<String> items = new ArrayList<>();
        List<AiTemplateManager.Template> templates = AiTemplateManager.getTemplates();
        for (AiTemplateManager.Template template : templates) {
            items.add(template.title);
        }
        int templateCount = items.size();
        for (int i = 1; i < TASK_TYPES.length; i++) {
            items.add(taskLabels[i]);
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.ai_chat_tools)
                .setItems(items.toArray(new String[0]), (dialog, which) -> {
                    if (which < templateCount) {
                        applyTemplate(templates.get(which));
                    } else {
                        selectTaskType(TASK_TYPES[which - templateCount + 1]);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Keeps model and privacy controls discoverable without occupying the
     * conversation header on every screen size.
     */
    private void showModelChooser() {
        if (!isAdded() || aiPreferences == null) return;
        String[] choices = new String[]{
                getString(R.string.ai_chat_switch_local),
                getString(R.string.ai_chat_switch_cloud),
                getString(R.string.ai_chat_local_models),
                getString(R.string.ai_chat_cloud_models)
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.ai_chat_model)
                .setItems(choices, (dialog, which) -> {
                    if (which == 0) {
                        aiPreferences.setLocalFirst(true);
                        updateModelControls();
                        updateStatus(getString(R.string.ai_chat_status_ready));
                    } else if (which == 1) {
                        aiPreferences.setLocalFirst(false);
                        updateModelControls();
                        showCloudModelDialog();
                    } else if (which == 2) {
                        showLocalModelDialog();
                    } else {
                        showCloudModelDialog();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * History, search, favorites and export are intentionally opened on
     * demand. This keeps the main page focused on composing a message.
     */
    private void showHistoryDialog() {
        if (!isAdded()) return;
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        int horizontalPadding = (int) (getResources().getDisplayMetrics().density * 4);
        content.setPadding(horizontalPadding, 0, horizontalPadding, 0);

        TextInputLayout searchLayout = new TextInputLayout(requireContext());
        searchLayout.setHint(getString(R.string.ai_chat_search_history));
        TextInputEditText searchInput = new TextInputEditText(searchLayout.getContext());
        searchInput.setSingleLine(true);
        searchInput.setText(currentSearch);
        searchLayout.addView(searchInput, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(searchLayout);

        MaterialButton favoriteButton = new MaterialButton(requireContext(), null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        int touchTarget = (int) (48 * getResources().getDisplayMetrics().density + 0.5f);
        favoriteButton.setMinHeight(touchTarget);
        favoriteButton.setMinimumHeight(touchTarget);
        favoriteButton.setText(favoritesOnly
                ? R.string.ai_chat_show_all
                : R.string.ai_chat_show_favorites);
        content.addView(favoriteButton);

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.ai_chat_history)
                .setView(content)
                .setPositiveButton(android.R.string.ok, null)
                .setNegativeButton(android.R.string.cancel, null);
        final androidx.appcompat.app.AlertDialog dialog = builder.create();

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                currentSearch = s == null ? "" : s.toString().trim().toLowerCase(java.util.Locale.ROOT);
                applyMessageFilter();
            }
        });
        favoriteButton.setOnClickListener(v -> {
            favoritesOnly = !favoritesOnly;
            favoriteButton.setText(favoritesOnly
                    ? R.string.ai_chat_show_all
                    : R.string.ai_chat_show_favorites);
            applyMessageFilter();
        });
        // Keep the filter after the dialog closes so the user can inspect the
        // filtered conversation. Clearing the text and turning off favorites
        // restores the full list.
        dialog.show();
    }

    private void showMoreDialog() {
        if (!isAdded()) return;
        String[] items = new String[]{
                getString(R.string.ai_chat_export),
                getString(R.string.ai_chat_clear_history)
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.ai_chat_more)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) {
                        exportMessages();
                    } else {
                        confirmClearHistory();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmClearHistory() {
        if (!isAdded()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.ai_chat_clear_history_title)
                .setMessage(R.string.ai_chat_clear_history_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.ai_chat_clear_history, (dialog, which) -> clearHistory())
                .show();
    }

    private void selectTaskType(String taskType) {
        String normalized = AiTaskCatalog.normalize(taskType);
        selectedTaskType = normalized;
        if (chipTaskContext != null) {
            if (AiTaskCatalog.CHAT.equals(normalized)) {
                chipTaskContext.setVisibility(View.GONE);
            } else {
                chipTaskContext.setText(labelForTask(normalized));
                chipTaskContext.setVisibility(View.VISIBLE);
            }
        }
        if (tvStatus != null && !AiTaskCatalog.CHAT.equals(normalized)) {
            updateStatus(getString(R.string.ai_chat_status_task_selected_format,
                    labelForTask(normalized)));
        }
    }

    private void updateSendEnabled() {
        if (btnSend == null || etInput == null) return;
        boolean hasText = etInput.getText() != null
                && etInput.getText().toString().trim().length() > 0;
        btnSend.setEnabled(hasText && !requestGate.isBusy() && !awaitingConsent);
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
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (isViewActive()) showModelList(models);
                });
            }

            @Override
            public void onError(Exception error) {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(() -> {
                    if (!isViewActive()) return;
                    updateStatus("模型清单获取失败");
                    Toast.makeText(requireContext(), getString(R.string.ai_model_list_failed_format, error.getMessage()), Toast.LENGTH_LONG).show();
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
            Toast.makeText(requireContext(), R.string.ai_no_local_models, Toast.LENGTH_LONG).show();
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
                .setTitle(getString(R.string.ai_local_models))
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
                .setTitle(getString(R.string.ai_local_model_detail))
                .setMessage(message.toString())
                .setNegativeButton(android.R.string.cancel, null);
        if (!rulesModel) {
            builder.setNeutralButton(R.string.ai_view_terms, (dialog, which) -> openGemmaTerms());
        }
        // 根据模型状态动态设置正向按钮
        if (rulesModel && aiPreferences != null && !model.id.equals(aiPreferences.getLocalModel())) {
            builder.setPositiveButton(R.string.ai_enable, (dialog, which) -> enableLocalModel(model));
        } else if (model.enabled && !downloaded) {
            builder.setPositiveButton(R.string.ai_download, (dialog, which) -> confirmGemmaNoticeThenDownload(model));
        } else if (downloaded && aiPreferences != null
                && !model.id.equals(aiPreferences.getLocalModel())) {
            builder.setPositiveButton(R.string.ai_enable, (dialog, which) -> enableLocalModel(model));
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
            Toast.makeText(requireContext(), R.string.ai_no_cloud_models, Toast.LENGTH_SHORT).show();
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
                .setTitle(getString(R.string.ai_cloud_models))
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
        if (btnModel != null) {
            AiModelInfo localModel = aiPreferences.getLocalModelInfo();
            String localLabel = localModel != null ? localModel.name : aiPreferences.getLocalModel();
            btnModel.setText(localFirst
                    ? getString(R.string.ai_chat_local_mode_short) + " · " + localLabel
                    : getString(R.string.ai_chat_cloud_mode_short) + " · "
                    + aiPreferences.getSelectedModel());
        }
        if (tvPrivacy != null) {
            tvPrivacy.setText(localFirst
                    ? R.string.ai_chat_privacy_local
                    : R.string.ai_chat_privacy_cloud);
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
                .setTitle(R.string.gemma_terms_title)
                .setMessage(AiLegalNotices.buildGemmaDownloadNotice(model))
                .setNeutralButton(R.string.gemma_view_terms, (dialog, which) -> openGemmaTerms())
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.gemma_agree_download, (dialog, which) -> {
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
        Toast.makeText(requireContext(), getString(R.string.ai_enabled_format, model.name), Toast.LENGTH_LONG).show();
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
        if (!isViewActive() || modelDownloadManager == null) return;
        progressBar.setVisibility(View.VISIBLE);
        updateStatus("模型下载中");
        modelDownloadManager.download(requireContext().getApplicationContext(), model,
                new AiModelDownloadManager.DownloadCallback() {
                    @Override
                    public void onProgress(long downloaded, long total) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            if (!isViewActive()) return;
                            updateStatus("模型下载 " + formatBytes(downloaded)
                                    + (total > 0 ? " / " + formatBytes(total) : ""));
                        });
                    }

                    @Override
                    public void onComplete(File file) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            if (!isViewActive()) return;
                            progressBar.setVisibility(View.GONE);
                            // 下载完成后自动启用该模型
                            enableLocalModel(model);
                            updateStatus("本地模型已下载");
                            Toast.makeText(requireContext(), getString(R.string.ai_downloaded_format, file.getName()), Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override
                    public void onError(Exception error) {
                        if (getActivity() == null) return;
                        getActivity().runOnUiThread(() -> {
                            if (!isViewActive()) return;
                            progressBar.setVisibility(View.GONE);
                            updateStatus("模型下载失败");
                            Toast.makeText(requireContext(), getString(R.string.ai_download_failed_format, error.getMessage()), Toast.LENGTH_LONG).show();
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
        if (etInput == null || requestGate.isBusy() || awaitingConsent) return;
        String input = etInput.getText() == null ? "" : etInput.getText().toString().trim();
        if (input.isEmpty()) {
            updateSendEnabled();
            return;
        }

        AiRoutingMode routingMode = aiPreferences != null && aiPreferences.isLocalFirst()
                ? AiRoutingMode.LOCAL_ONLY
                : AiRoutingMode.CLOUD_ONLY;

        // #24.3: 云端模式下，发送前需先获取 consent
        if (routingMode == AiRoutingMode.CLOUD_ONLY) {
            ConsentComponent consent = buildAiConsent();
            if (ConsentDialog.needsConsent(requireContext(), consent)) {
                showCloudConsentDialog(input);
                return;
            }
        }

        proceedSendMessage(input, routingMode);
    }

    /** #24.3: 构建 AI 云端调用 consent 组件 */
    private ConsentComponent buildAiConsent() {
        return new ConsentComponent(
                "ai_cloud",
                1,
                getString(R.string.consent_ai_title),
                getString(R.string.consent_ai_send),
                getString(R.string.consent_ai_purpose),
                getString(R.string.consent_ai_local),
                getString(R.string.consent_ai_cost),
                getString(R.string.consent_ai_cancel),
                getString(R.string.consent_ai_provider),
                getString(R.string.consent_ai_retention)
        );
    }

    /** #24.3: 弹出 AI 云端 consent 弹窗 */
    private void showCloudConsentDialog(String input) {
        if (!isAdded() || awaitingConsent) return;
        awaitingConsent = true;
        updateSendEnabled();
        try {
            ConsentDialog.show(requireActivity(), buildAiConsent(), decision -> {
            awaitingConsent = false;
            if (!isAdded() || etInput == null) {
                updateSendEnabled();
                return kotlin.Unit.INSTANCE;
            }
            if (decision == ConsentDecision.AGREE_CLOUD) {
                aiPreferences.setLocalFirst(false);
                updateModelControls();
                proceedSendMessage(input, AiRoutingMode.CLOUD_ONLY);
            } else if (decision == ConsentDecision.USE_LOCAL) {
                aiPreferences.setLocalFirst(true);
                Toast.makeText(requireContext(), R.string.consent_ai_use_local_toast, Toast.LENGTH_SHORT).show();
                proceedSendMessage(input, AiRoutingMode.LOCAL_ONLY);
            } else {
                // REFUSE：取消，不做任何操作
                updateSendEnabled();
            }
            return kotlin.Unit.INSTANCE;
            });
        } catch (RuntimeException error) {
            awaitingConsent = false;
            updateSendEnabled();
            Toast.makeText(requireContext(), safeText(error.getMessage()), Toast.LENGTH_LONG).show();
        }
    }

    private void showCloudRetryPrompt(String input) {
        if (!isAdded() || input == null || input.trim().isEmpty()) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.ai_chat_local_unavailable)
                .setMessage(R.string.ai_chat_cloud_consent_needed)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.ai_chat_retry_cloud, (dialog, which) -> {
                    aiPreferences.setLocalFirst(false);
                    updateModelControls();
                    showCloudConsentDialog(input);
                })
                .show();
    }

    /** 实际执行发送消息与任务提交 */
    private void proceedSendMessage(String input, AiRoutingMode routingMode) {
        if (!isAdded() || etInput == null || btnSend == null || router == null) return;
        AiRequestGate.RequestToken requestToken = requestGate.tryAcquire();
        if (requestToken == null) return;

        String taskType = AiTaskCatalog.normalize(selectedTaskType);
        final String ftaskType = taskType;

        // 添加用户消息到列表头部（持久化顺序仍为最新在前）。
        AiMessage userMsg = new AiMessage("user", input, taskType, "user");
        messages.add(0, userMsg);
        saveHistory();
        applyMessageFilter();
        scrollToBottom();
        etInput.setText("");

        progressBar.setVisibility(View.VISIBLE);
        updateStatus(getString(R.string.ai_chat_status_processing));
        updateSendEnabled();

        // 所有任务统一走 Router；小游戏也在 Router 的后台执行器中运行，
        // 不再让同步规则引擎阻塞 UI 线程。
        try {
            router.submitTask(taskType, input, routingMode, new AiTaskRouter.AiCallback() {
                @Override
                public void onResult(AiTask task, AiResult result) {
                    if (!requestGate.isActive(requestToken) || !isViewActive()) return;
                    try {
                        if (result == null) {
                            result = AiResult.fail("AI 返回了空结果")
                                    .source("unknown")
                                    .errorCode(AiErrorCode.LOCAL_LLM_ERROR)
                                    .build();
                        }
                        progressBar.setVisibility(View.GONE);
                        if (result.success) {
                            messages.add(0, new AiMessage("assistant", safeText(result.content),
                                    ftaskType, safeSource(result.source)));
                            saveHistory();
                            applyMessageFilter();
                            scrollToBottom();
                            updateStatus(getString(R.string.ai_chat_status_done_format,
                                    safeSource(result.source)));
                        } else {
                            messages.add(0, new AiMessage("assistant", "❌ " + safeText(result.message),
                                    ftaskType, "error"));
                            saveHistory();
                            applyMessageFilter();
                            scrollToBottom();
                            updateStatus(getString(R.string.ai_chat_status_failed));
                            if (result.hasErrorCode(AiErrorCode.LOCAL_ONLY_UNAVAILABLE)) {
                                showCloudRetryPrompt(input);
                                Toast.makeText(requireContext(),
                                        R.string.ai_chat_cloud_consent_needed, Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(requireContext(), safeText(result.message),
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    } finally {
                        requestGate.finish(requestToken);
                        updateSendEnabled();
                    }
                }
            });
        } catch (RuntimeException error) {
            // Executor shutdown/rejection must not leave the composer locked.
            if (requestGate.isActive(requestToken)) {
                requestGate.finish(requestToken);
            }
            if (isViewActive()) {
                progressBar.setVisibility(View.GONE);
                messages.add(0, new AiMessage("assistant",
                        "❌ " + safeText(error.getMessage()), ftaskType, "error"));
                saveHistory();
                applyMessageFilter();
                updateSendEnabled();
                updateStatus(getString(R.string.ai_chat_status_failed));
                Toast.makeText(requireContext(), safeText(error.getMessage()),
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    private boolean isViewActive() {
        return isAdded() && getView() != null && rvMessages != null && etInput != null;
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }

    private String safeSource(String source) {
        return source == null || source.trim().isEmpty() ? "unknown" : source;
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
     * 将任务标签或内部标识解析为统一的任务类型标识。
     * <p>
     * 支持传入本地化标签（如"摘要"）或内部标识（如"summary"），
     * 无法匹配时默认返回聊天类型。
     *
     * @param labelOrType 用户选择的标签或内部标识
     * @return 对应的内部任务类型标识
     */
    private String resolveTaskType(String labelOrType) {
        if (labelOrType == null || labelOrType.trim().isEmpty()) {
            return AiTaskCatalog.CHAT;
        }
        String[] labels = taskLabels != null ? taskLabels : buildTaskLabels();
        int count = Math.min(labels.length, TASK_TYPES.length);
        for (int i = 0; i < count; i++) {
            if (labels[i].equals(labelOrType) || TASK_TYPES[i].equals(labelOrType)) {
                return TASK_TYPES[i];
            }
        }
        return AiTaskCatalog.normalize(labelOrType);
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
                getString(R.string.ai_chat_task_chat),
                getString(R.string.ai_chat_task_ocr),
                getString(R.string.ai_chat_task_summary),
                getString(R.string.ai_chat_task_translate),
                getString(R.string.ai_chat_task_rewrite),
                getString(R.string.ai_chat_task_qa),
                getString(R.string.ai_chat_task_keywords),
                getString(R.string.ai_chat_task_classify),
                getString(R.string.ai_chat_task_mini_game)
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
     * 清空所有历史记录、收藏和搜索状态，并使当前请求失效。
     */
    private void clearHistory() {
        requestGate.invalidateConversation();
        if (historyStore != null) {
            historyStore.clear();
        }
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE);
        }
        messages.clear();
        favoriteIds.clear();
        favoritesOnly = false;
        currentSearch = "";
        selectTaskType(AiTaskCatalog.CHAT);
        applyMessageFilter();
        updateStatus(getString(R.string.ai_chat_status_history_cleared));
        updateSendEnabled();
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
        int visibleTemplateCount = Math.min(4, AiTemplateManager.getTemplates().size());
        for (int i = 0; i < visibleTemplateCount; i++) {
            AiTemplateManager.Template template = AiTemplateManager.getTemplates().get(i);
            MaterialButton button = new MaterialButton(requireContext(), null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setText(template.title);
            button.setTextSize(12);
            int touchTarget = (int) (48 * getResources().getDisplayMetrics().density + 0.5f);
            button.setMinHeight(touchTarget);
            button.setMinimumHeight(touchTarget);
            // 动态创建的 MaterialButton 不依赖宿主主题的默认状态动画。
            button.setStateListAnimator(null);
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
     * 设置一次性任务上下文并将模板提示词填入输入框，
     * 光标移到末尾方便用户继续编辑。
     *
     * @param template 要应用的预设模板
     */
    private void applyTemplate(AiTemplateManager.Template template) {
        selectTaskType(template.taskType);
        if (etInput == null) return;
        etInput.setText(template.prompt);
        etInput.setSelection(etInput.getText() != null ? etInput.getText().length() : 0);
        updateStatus(getString(R.string.ai_chat_status_template_applied_format, template.title));
    }

    /**
     * 根据内部任务类型标识获取对应的本地化显示标签。
     *
     * @param taskType 内部任务类型标识（如 "summary"）
     * @return 对应的本地化标签（如 "摘要"）；未匹配时返回 "总结"
     */
    private String labelForTask(String taskType) {
        String[] labels = taskLabels != null ? taskLabels : buildTaskLabels();
        String normalized = AiTaskCatalog.normalize(taskType);
        int count = Math.min(TASK_TYPES.length, labels.length);
        for (int i = 0; i < count; i++) {
            if (TASK_TYPES[i].equals(normalized)) {
                return labels[i];
            }
        }
        return getString(R.string.ai_chat_task_chat);
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
        Toast.makeText(requireContext(), favorite
                ? R.string.ai_chat_favorite
                : R.string.ai_chat_favorite_remove, Toast.LENGTH_SHORT).show();
    }

    /**
     * 更新收藏过滤按钮的显示文本。
     * <p>
     * 收藏模式激活时显示"全部"（点击可切回全部），
     * 非收藏模式时显示"收藏"（点击可进入收藏模式）。
     */
    private void updateFavoriteFilterButton() {
        // Favorite filtering is exposed from the History dialog.
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
                String haystack = (message.role + " " + message.content + " " + message.taskType)
                        .toLowerCase(java.util.Locale.ROOT);
                if (!haystack.contains(currentSearch)) {
                    continue;
                }
            }
            visibleMessages.add(message);
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (emptyState != null) {
            emptyState.setVisibility(visibleMessages.isEmpty() ? View.VISIBLE : View.GONE);
        }
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
            return getString(R.string.ai_chat_status_favorites_count, visibleMessages.size());
        }
        if (!currentSearch.isEmpty()) {
            return getString(R.string.ai_chat_status_search_count, visibleMessages.size());
        }
        // 默认模式下排除系统消息计数，只显示用户和 AI 消息数量
        return getString(R.string.ai_chat_status_history_count,
                Math.max(0, messages.size() - countSystemMessages()));
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
            Toast.makeText(requireContext(), R.string.ai_no_export_content, Toast.LENGTH_SHORT).show();
            return;
        }
        // 使用 Android 系统的分享功能，让用户选择导出方式（微信、邮件等）
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.ai_chat_export_subject));
        intent.putExtra(Intent.EXTRA_TEXT, exportText);
        startActivity(Intent.createChooser(intent, getString(R.string.ai_chat_export_chooser)));
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
        sb.append(getString(R.string.ai_chat_export_subject)).append("\n\n");
        int exportedMessages = 0;
        // 收藏/搜索模式下导出可见消息，否则导出全量消息
        List<AiMessage> source = (favoritesOnly || !currentSearch.isEmpty()) ? visibleMessages : messages;
        // 倒序列表从末尾遍历，实现时间正序输出
        for (int i = source.size() - 1; i >= 0; i--) {
            AiMessage message = source.get(i);
            if ("system".equals(message.role)) {
                continue;
            }
            exportedMessages++;
            sb.append(roleLabel(message.role))
                    .append(" [").append(message.taskType).append("]");
            if (favoriteIds.contains(message.id)) {
                sb.append(" [").append(getString(R.string.ai_chat_export_favorite_marker)).append("]");
            }
            sb.append("\n").append(message.content == null ? "" : message.content).append("\n\n");
        }
        return exportedMessages == 0 ? "" : sb.toString().trim();
    }

    /**
     * 将消息角色标识转换为中文显示标签。
     *
     * @param role 消息角色（"user"、"assistant" 或 "system"）
     * @return 中文角色标签
     */
    private String roleLabel(String role) {
        if ("user".equals(role)) return getString(R.string.ai_chat_role_user);
        if ("assistant".equals(role)) return getString(R.string.ai_chat_role_assistant);
        return getString(R.string.ai_chat_role_system);
    }

    /**
     * 将消息列表平滑滚动到最新消息位置（index 0，即列表顶部）。
     * <p>
     * 使用 post 确保在布局更新后再执行滚动，避免 RecyclerView 尚未完成测量导致滚动失败。
     */
    private void scrollToBottom() {
        final RecyclerView recyclerView = rvMessages;
        final MessageAdapter messageAdapter = adapter;
        if (recyclerView == null || messageAdapter == null) return;
        recyclerView.post(() -> {
            if (recyclerView.getAdapter() == messageAdapter && messageAdapter.getItemCount() > 0) {
                recyclerView.smoothScrollToPosition(0);
            }
        });
    }

    /**
     * 消息列表适配器。
     * <p>
     * 将 {@link AiMessage} 列表绑定到 RecyclerView，根据消息角色使用不同的气泡布局：
     * 用户消息居右、AI 消息居左并带头像、系统消息居中弱化显示。
     * 使用 visibleMessages 作为数据源，确保过滤后的结果正确展示。
     */
    private static class MessageAdapter extends RecyclerView.Adapter<MessageViewHolder> {
        private static final int TYPE_USER = 1;
        private static final int TYPE_ASSISTANT = 2;
        private static final int TYPE_SYSTEM = 3;

        private final LayoutInflater inflater;
        private final List<AiMessage> messages;
        private final Set<String> favoriteIds;
        private final FavoriteListener favoriteListener;
        // 2026-06-23: TTS 引擎类型改为 Object，使用反射调用（避免 mimo-tts 模块未配置时编译失败）
        private final Object ttsEngine;

        /**
         * @param context          用于创建 LayoutInflater 的上下文
         * @param messages         可见消息列表（过滤后）
         * @param favoriteIds      已收藏消息 ID 集合
         * @param favoriteListener 收藏切换回调
         * @param ttsEngine        TTS 朗读引擎（可为 null，mimo-tts 模块未配置时为 null）
         */
        MessageAdapter(Context context, List<AiMessage> messages, Set<String> favoriteIds,
                       FavoriteListener favoriteListener, Object ttsEngine) {
            this.inflater = LayoutInflater.from(context);
            this.messages = messages;
            this.favoriteIds = favoriteIds;
            this.favoriteListener = favoriteListener;
            this.ttsEngine = ttsEngine;
        }

        @Override
        public int getItemViewType(int position) {
            String role = messages.get(position).role;
            if ("user".equals(role)) return TYPE_USER;
            if ("assistant".equals(role)) return TYPE_ASSISTANT;
            return TYPE_SYSTEM;
        }

        @NonNull
        @Override
        public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_USER) {
                return new UserMessageViewHolder(inflater.inflate(R.layout.item_ai_message_user, parent, false));
            } else if (viewType == TYPE_ASSISTANT) {
                return new AssistantMessageViewHolder(inflater.inflate(R.layout.item_ai_message_assistant, parent, false));
            } else {
                return new SystemMessageViewHolder(inflater.inflate(R.layout.item_ai_message_system, parent, false));
            }
        }

        @Override
        public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
            AiMessage msg = messages.get(position);
            holder.bind(msg, favoriteIds.contains(msg.id), favoriteListener, ttsEngine);
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }
    }

    /**
     * 消息 ViewHolder 抽象基类。
     */
    private abstract static class MessageViewHolder extends RecyclerView.ViewHolder {
        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        abstract void bind(AiMessage msg, boolean favorite, FavoriteListener listener, Object ttsEngine);
    }

    private static class UserMessageViewHolder extends MessageViewHolder {
        private final TextView tvContent;
        private final ImageButton btnFavorite;

        UserMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_msg_content);
            btnFavorite = itemView.findViewById(R.id.btn_msg_favorite);
        }

        @Override
        void bind(AiMessage msg, boolean favorite, FavoriteListener listener, Object ttsEngine) {
            tvContent.setText(msg.content);
            bindFavorite(btnFavorite, msg, favorite, listener);
        }
    }

    private static class AssistantMessageViewHolder extends MessageViewHolder {
        private final TextView tvContent;
        private final TextView tvMeta;
        private final ImageButton btnFavorite;
        private final ImageButton btnTts;

        AssistantMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_msg_content);
            tvMeta = itemView.findViewById(R.id.tv_msg_meta);
            btnFavorite = itemView.findViewById(R.id.btn_msg_favorite);
            btnTts = itemView.findViewById(R.id.btn_msg_tts);
        }

        @Override
        void bind(AiMessage msg, boolean favorite, FavoriteListener listener, Object ttsEngine) {
            tvContent.setText(msg.content);
            bindSourceMeta(tvMeta, msg);
            bindFavorite(btnFavorite, msg, favorite, listener);
            bindTts(btnTts, msg, ttsEngine);
        }
    }

    private static class SystemMessageViewHolder extends MessageViewHolder {
        private final TextView tvContent;

        SystemMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_msg_content);
        }

        @Override
        void bind(AiMessage msg, boolean favorite, FavoriteListener listener, Object ttsEngine) {
            tvContent.setText(msg.content);
        }
    }

    /**
     * 绑定收藏按钮状态与点击事件。
     */
    private static void bindFavorite(ImageButton btn, AiMessage msg, boolean favorite, FavoriteListener listener) {
        if (btn == null) return;
        btn.setImageResource(favorite
                ? android.R.drawable.btn_star_big_on
                : android.R.drawable.btn_star_big_off);
        btn.setColorFilter(ContextCompat.getColor(btn.getContext(), R.color.ai_chat_action_tint));
        btn.setContentDescription(btn.getContext().getString(favorite
                ? R.string.ai_chat_favorite_remove
                : R.string.ai_chat_favorite));
        btn.setOnClickListener(v -> listener.onToggleFavorite(msg));
    }

    /**
     * 绑定 TTS 朗读按钮；引擎不可用时隐藏。
     */
    private static void bindTts(ImageButton btn, AiMessage msg, Object ttsEngine) {
        if (btn == null) return;
        if (ttsEngine != null && msg.content != null && !msg.content.isEmpty()
                && !"error".equals(msg.source)) {
            btn.setVisibility(View.VISIBLE);
            btn.setColorFilter(ContextCompat.getColor(btn.getContext(), R.color.ai_chat_action_tint));
            btn.setContentDescription(btn.getContext().getString(R.string.ai_chat_tts));
            btn.setOnClickListener(v -> {
                Toast.makeText(btn.getContext(), R.string.ai_synthesizing_voice, Toast.LENGTH_SHORT).show();
                // 反射调用 ttsEngine.speak(String, Object, Callback)
                try {
                    Class<?> callbackCls = Class.forName("com.gamecenter.capability.tts.MiMoTtsEngine$Callback");
                    Object callback = java.lang.reflect.Proxy.newProxyInstance(
                            callbackCls.getClassLoader(),
                            new Class<?>[]{callbackCls},
                            (proxy, method, args1) -> {
                                if (method.getName().equals("onComplete") && args1[0] != null) {
                                    android.util.Log.w("TTS", "speak failed: " + args1[0]);
                                }
                                return null;
                            });
                    ttsEngine.getClass().getMethod("speak", String.class, Object.class, callbackCls)
                            .invoke(ttsEngine, msg.content, null, callback);
                } catch (Throwable t) {
                    android.util.Log.w("TTS", "speak 反射调用失败: " + t.getMessage());
                    Toast.makeText(btn.getContext(),
                            "TTS 引擎不可用: " + t.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            btn.setVisibility(View.GONE);
            btn.setOnClickListener(null);
        }
    }

    /** 显示每条 AI 回复的来源，但不把内部错误标识暴露给用户。 */
    private static void bindSourceMeta(TextView view, AiMessage msg) {
        if (view == null || msg == null) return;
        String source = msg.source == null ? "" : msg.source.trim();
        if (source.isEmpty() || "error".equals(source) || "user".equals(source)) {
            view.setText("");
            view.setVisibility(View.GONE);
            return;
        }
        String label;
        if (source.startsWith("local")) {
            label = view.getContext().getString(R.string.ai_chat_local_mode_short);
        } else if ("cloud".equals(source)) {
            label = view.getContext().getString(R.string.ai_chat_cloud_mode_short);
        } else {
            // Keep forward compatibility for providers added later without
            // making the binding fail when an old record has a custom source.
            label = source;
        }
        view.setText(label);
        view.setVisibility(View.VISIBLE);
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
