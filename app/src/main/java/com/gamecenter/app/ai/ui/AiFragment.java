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
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.gamecenter.app.R;
import com.gamecenter.app.ai.AiPreferences;
import com.gamecenter.app.ai.AiTaskRouter;
import com.gamecenter.app.ai.data.AiMessage;
import com.gamecenter.app.ai.data.AiResult;
import com.gamecenter.app.ai.data.AiTask;
import com.gamecenter.app.ai.history.AiHistoryStore;
import com.gamecenter.app.ai.legal.AiLegalNotices;
import com.gamecenter.app.ai.model.AiModelDownloadManager;
import com.gamecenter.app.ai.model.AiModelInfo;
import com.gamecenter.app.ai.template.AiTemplateManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AI 助手页面 — 聊天式交互界面，支持多种 AI 功能。
 */
public class AiFragment extends Fragment {

    private static final String[] TASK_LABELS = {
            "OCR 清洗", "总结", "翻译", "润色", "简单问答", "关键词", "分类"
    };
    private static final String[] TASK_TYPES = {
            "ocr", "summary", "translate", "rewrite", "qa", "keywords", "classify"
    };

    private AiTaskRouter router;
    private AiHistoryStore historyStore;
    private AiPreferences aiPreferences;
    private AiModelDownloadManager modelDownloadManager;
    private MessageAdapter adapter;
    private final List<AiMessage> messages = new ArrayList<>();
    private final List<AiMessage> visibleMessages = new ArrayList<>();
    private final Set<String> favoriteIds = new HashSet<>();
    private boolean favoritesOnly = false;
    private String currentSearch = "";

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

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        router = new AiTaskRouter(context);
        historyStore = new AiHistoryStore(context);
        aiPreferences = new AiPreferences(context);
        modelDownloadManager = new AiModelDownloadManager();
    }

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

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

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
        favoriteIds.clear();
        favoriteIds.addAll(historyStore.getFavoriteIds());

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, TASK_LABELS);
        actTaskType.setAdapter(typeAdapter);
        actTaskType.setText("总结", false);

        // 消息列表
        adapter = new MessageAdapter(visibleMessages, favoriteIds, this::toggleFavorite);
        rvMessages.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvMessages.setAdapter(adapter);

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
        setupTemplates(view);

        MaterialButton btnClearHistory = view.findViewById(R.id.btn_ai_open_full);
        if (btnClearHistory != null) {
            btnClearHistory.setText("清空历史");
            btnClearHistory.setOnClickListener(v -> clearHistory());
        }

        // 输入框变化时更新按钮状态
        etInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                btnSend.setEnabled(s != null && s.toString().trim().length() > 0);
            }
        });

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                currentSearch = s == null ? "" : s.toString().trim().toLowerCase();
                applyMessageFilter();
            }
        });

        List<AiMessage> savedMessages = historyStore.loadMessages();
        if (savedMessages.isEmpty()) {
            messages.add(new AiMessage("system", "AI 助手已就绪。请选择任务类型并输入内容开始使用。", "chat", "local"));
        } else {
            messages.addAll(savedMessages);
        }
        applyMessageFilter();
        scrollToBottom();

        updateStatus("就绪");
    }

    private void showLocalModelDialog() {
        if (modelDownloadManager == null) return;
        updateStatus("正在获取模型清单");
        modelDownloadManager.fetchModels(new AiModelDownloadManager.Callback<List<AiModelInfo>>() {
            @Override
            public void onSuccess(List<AiModelInfo> models) {
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

    private void showModelList(List<AiModelInfo> models) {
        if (models == null || models.isEmpty()) {
            updateStatus("暂无本地模型");
            Toast.makeText(requireContext(), "服务器暂无可用本地模型", Toast.LENGTH_LONG).show();
            return;
        }
        AiModelInfo model = models.get(0);
        boolean downloaded = modelDownloadManager.isDownloaded(requireContext(), model);
        StringBuilder message = new StringBuilder();
        message.append(model.name).append("\n");
        message.append("运行时: ").append(model.runtime).append("\n");
        message.append("大小: ").append(formatBytes(model.sizeBytes)).append("\n");
        message.append("峰值内存: ").append(formatBytes(model.estimatedPeakMemoryBytes)).append("\n");
        message.append("存储位置: App 私有目录 / Android/data/")
                .append(requireContext().getPackageName())
                .append("/files/Documents/ai_models\n\n");
        if (downloaded) {
            boolean selected = aiPreferences != null && model.id.equals(aiPreferences.getLocalModel());
            message.append(selected
                    ? "状态: 已下载并启用。本地优先任务会尝试使用 Gemma 推理。"
                    : "状态: 已下载。点击启用后，本地优先任务会尝试使用 Gemma 推理。");
        } else if (!model.enabled) {
            message.append("状态: 暂未开放下载。\n").append(model.note);
            if (!model.upstreamUrl.isEmpty()) {
                message.append("\n\n上游: ").append(model.upstreamUrl);
            }
        } else {
            message.append("状态: 可下载。建议在 Wi-Fi 和充电环境下操作。");
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("本地 Gemma 模型")
                .setMessage(message.toString())
                .setNeutralButton("查看条款", (dialog, which) -> openGemmaTerms())
                .setNegativeButton(android.R.string.cancel, null);
        if (model.enabled && !downloaded) {
            builder.setPositiveButton("下载", (dialog, which) -> confirmGemmaNoticeThenDownload(model));
        } else if (downloaded && aiPreferences != null
                && !model.id.equals(aiPreferences.getLocalModel())) {
            builder.setPositiveButton("启用", (dialog, which) -> enableLocalModel(model));
        }
        builder.show();
        updateStatus(downloaded ? "本地模型已下载" : "本地模型未下载");
    }

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
                        aiPreferences.acceptGemmaNotice(AiLegalNotices.GEMMA_NOTICE_VERSION);
                    }
                    downloadModel(model);
                })
                .show();
    }

    private void openGemmaTerms() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(AiLegalNotices.GEMMA_TERMS_URL)));
        } catch (Exception e) {
            Toast.makeText(requireContext(), AiLegalNotices.GEMMA_TERMS_URL, Toast.LENGTH_LONG).show();
        }
    }

    private void enableLocalModel(AiModelInfo model) {
        if (aiPreferences != null) {
            aiPreferences.setLocalModel(model.id);
            aiPreferences.setLocalFirst(true);
        }
        updateStatus("本地 Gemma 已启用");
        Toast.makeText(requireContext(), "已启用本地 Gemma 模型", Toast.LENGTH_LONG).show();
    }

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

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "未知";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.CHINA, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return String.format(java.util.Locale.CHINA, "%.1f MB", bytes / 1024.0 / 1024.0);
        return String.format(java.util.Locale.CHINA, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0);
    }

    private void sendMessage() {
        String input = etInput.getText().toString().trim();
        if (input.isEmpty()) return;

        String taskType = resolveTaskType(actTaskType.getText().toString().trim());
        final String ftaskType = taskType;

        // 添加用户消息
        AiMessage userMsg = new AiMessage("user", input, taskType, "user");
        messages.add(0, userMsg);
        saveHistory();
        applyMessageFilter();
        scrollToBottom();
        etInput.setText("");

        // 显示加载
        progressBar.setVisibility(View.VISIBLE);
        updateStatus("处理中…");
        btnSend.setEnabled(false);

        // 发送任务
        router.submitTask(taskType, input, new AiTaskRouter.AiCallback() {
            @Override
            public void onResult(AiTask task, AiResult result) {
                if (getView() == null) return;
                progressBar.setVisibility(View.GONE);
                btnSend.setEnabled(true);

                if (result.success) {
                    messages.add(0, new AiMessage("assistant", result.content, ftaskType, result.source));
                    saveHistory();
                    applyMessageFilter();
                    scrollToBottom();
                    updateStatus("完成 | " + result.source);
                } else {
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

    private void updateStatus(String text) {
        if (tvStatus != null) tvStatus.setText(text);
    }

    private String resolveTaskType(String labelOrType) {
        if (labelOrType == null || labelOrType.isEmpty()) {
            return "summary";
        }
        for (int i = 0; i < TASK_LABELS.length; i++) {
            if (TASK_LABELS[i].equals(labelOrType) || TASK_TYPES[i].equals(labelOrType)) {
                return TASK_TYPES[i];
            }
        }
        return "summary";
    }

    private void saveHistory() {
        if (historyStore != null) {
            historyStore.saveMessages(messages);
        }
    }

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

    private void setupTemplates(View root) {
        LinearLayout layout = root.findViewById(R.id.layout_ai_templates);
        if (layout == null) return;
        layout.removeAllViews();
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

    private void applyTemplate(AiTemplateManager.Template template) {
        actTaskType.setText(labelForTask(template.taskType), false);
        etInput.setText(template.prompt);
        etInput.setSelection(etInput.getText() != null ? etInput.getText().length() : 0);
        updateStatus("已套用模板: " + template.title);
    }

    private String labelForTask(String taskType) {
        for (int i = 0; i < TASK_TYPES.length; i++) {
            if (TASK_TYPES[i].equals(taskType)) {
                return TASK_LABELS[i];
            }
        }
        return "总结";
    }

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

    private void updateFavoriteFilterButton() {
        if (btnFavorites != null) {
            btnFavorites.setText(favoritesOnly ? "全部" : "收藏");
        }
    }

    private void applyMessageFilter() {
        visibleMessages.clear();
        for (AiMessage message : messages) {
            if (favoritesOnly && !favoriteIds.contains(message.id)) {
                continue;
            }
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

    private String buildStatusText() {
        if (favoritesOnly) {
            return "收藏 " + visibleMessages.size();
        }
        if (!currentSearch.isEmpty()) {
            return "搜索 " + visibleMessages.size();
        }
        return "历史 " + Math.max(0, messages.size() - countSystemMessages());
    }

    private int countSystemMessages() {
        int count = 0;
        for (AiMessage message : messages) {
            if ("system".equals(message.role)) count++;
        }
        return count;
    }

    private void exportMessages() {
        String exportText = buildExportText();
        if (exportText.isEmpty()) {
            Toast.makeText(requireContext(), "没有可导出的内容", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "GameCenter AI 导出");
        intent.putExtra(Intent.EXTRA_TEXT, exportText);
        startActivity(Intent.createChooser(intent, "导出 AI 记录"));
    }

    private String buildExportText() {
        StringBuilder sb = new StringBuilder();
        sb.append("GameCenter AI 记录\n\n");
        List<AiMessage> source = (favoritesOnly || !currentSearch.isEmpty()) ? visibleMessages : messages;
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

    private String roleLabel(String role) {
        if ("user".equals(role)) return "用户";
        if ("assistant".equals(role)) return "AI";
        return "系统";
    }

    private void scrollToBottom() {
        rvMessages.post(() -> {
            if (adapter.getItemCount() > 0) {
                rvMessages.smoothScrollToPosition(0);
            }
        });
    }

    /**
     * 消息列表适配器。
     */
    private static class MessageAdapter extends RecyclerView.Adapter<MessageViewHolder> {

        private final List<AiMessage> messages;
        private final Set<String> favoriteIds;
        private final FavoriteListener favoriteListener;

        MessageAdapter(List<AiMessage> messages, Set<String> favoriteIds, FavoriteListener favoriteListener) {
            this.messages = messages;
            this.favoriteIds = favoriteIds;
            this.favoriteListener = favoriteListener;
        }

        @NonNull
        @Override
        public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_ai_message, parent, false);
            return new MessageViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
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

        void bind(AiMessage msg, boolean favorite, FavoriteListener favoriteListener) {
            if (msg.role.equals("user")) {
                tvRole.setText("你");
                itemView.setBackgroundResource(R.drawable.bg_ai_message_user);
            } else if (msg.role.equals("assistant")) {
                tvRole.setText("AI助手");
                itemView.setBackgroundResource(R.drawable.bg_ai_message_assistant);
            } else {
                tvRole.setText("系统");
                itemView.setBackgroundResource(R.drawable.bg_ai_message_system);
            }
            tvContent.setText(msg.content);
            if ("system".equals(msg.role)) {
                btnFavorite.setVisibility(View.GONE);
            } else {
                btnFavorite.setVisibility(View.VISIBLE);
                btnFavorite.setImageResource(favorite
                        ? android.R.drawable.btn_star_big_on
                        : android.R.drawable.btn_star_big_off);
                btnFavorite.setOnClickListener(v -> favoriteListener.onToggleFavorite(msg));
            }
        }
    }

    private interface FavoriteListener {
        void onToggleFavorite(AiMessage message);
    }
}
