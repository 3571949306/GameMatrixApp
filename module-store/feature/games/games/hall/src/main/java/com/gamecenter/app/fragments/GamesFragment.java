package com.gamecenter.app.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import com.google.android.material.card.MaterialCardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import com.gamecenter.app.BuildConfig;
import com.gamecenter.app.ColorSchemeManager;
import com.gamecenter.app.DynamicGameActivity;
import com.gamecenter.app.MainActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.SettingsManager;
import com.gamecenter.app.games.GameRegistry;
import com.gamecenter.app.modules.ModuleManager;
import com.gamecenter.app.modules.ModuleStoreActivity;
import com.gamecenter.app.utils.NetworkErrorHandler;
import com.gamecenter.app.games.GameUsageStore;
import com.gamecenter.app.settings.AppSettingsDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Call;
import okhttp3.Callback;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import com.gamecenter.app.network.OkHttpClientProvider;

/**
 * 游戏大厅 Fragment — TabLayout(全部/最近/收藏/分类) + RecyclerView 卡片网格。
 * <p>
 * 核心职责：
 * <ul>
 *   <li>展示所有已注册游戏，支持按分类标签页筛选和关键词搜索</li>
 *   <li>记录游戏启动次数和最近游玩时间，支持收藏功能</li>
 *   <li>提供设置入口（主题切换、检查更新）和用户反馈通道</li>
 * </ul>
 * </p>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>游戏数据来源于 GameRegistry 单例，新增游戏只需维护注册表</li>
 *   <li>Tab 位置约定：0=全部、1=最近、2=收藏、3及以上=分类（偏移量 CATEGORY_TAB_OFFSET=3）</li>
 *   <li>搜索时忽略 Tab 筛选，始终从全量游戏列表中过滤</li>
 *   <li>反馈支持 VPS 提交和邮箱兜底两种方式</li>
 * </ul>
 * </p>
 */
public class GamesFragment extends Fragment {

    /** Tab 索引：全部游戏 */
    private static final int TAB_ALL = 0;
    /** Tab 索引：最近游玩 */
    private static final int TAB_RECENT = 1;
    /** Tab 索引：收藏游戏 */
    private static final int TAB_FAVORITES = 2;
    /** 分类 Tab 在 TabLayout 中的起始偏移量 */
    private static final int CATEGORY_TAB_OFFSET = 3;

    /** 所有游戏分类列表 */
    private List<GameRegistry.Category> categories;
    /** 所有游戏的扁平列表 */
    private List<GameRegistry.Entry> allGames;
    /** 以游戏 ID 为键的快速查找映射 */
    private Map<String, GameRegistry.Entry> gamesById;
    /** 游戏使用记录存储（启动次数、最近游玩、收藏） */
    private GameUsageStore usageStore;
    private TabLayout tabLayout;
    private RecyclerView rvGames;
    private EditText etGameSearch;
    private TextView tvEmptyState;
    private GameAdapter currentAdapter;
    /** 当前搜索关键词 */
    private String currentQuery = "";
    /** 当前选中的 Tab 位置 */
    private int selectedTabPosition = TAB_ALL;
    /** 标记 Fragment 是否已销毁，防止异步回调导致内存泄漏 */
    private boolean isDestroyed = false;

    public GamesFragment() {
        super(R.layout.fragment_games);
    }

    /**
     * 视图创建完成后的初始化入口。
     * <p>
     * 依次初始化版本号显示、设置按钮、游戏分类数据、搜索框和标签页。
     * </p>
     */
    @Override
    public void onViewCreated(@NonNull View view, android.os.Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tabLayout = view.findViewById(R.id.tab_layout);
        rvGames = view.findViewById(R.id.rv_games);
        etGameSearch = view.findViewById(R.id.et_game_search);
        tvEmptyState = view.findViewById(R.id.tv_empty_state);
        usageStore = new GameUsageStore(requireContext());

        TextView tvVersion = view.findViewById(R.id.tv_version);
        tvVersion.setText(getString(R.string.version_format_simple, BuildConfig.VERSION_NAME));
        tvVersion.setOnClickListener(v -> showChangelog());

        ImageButton btnSettings = view.findViewById(R.id.btn_settings);
        btnSettings.setOnClickListener(v -> showSettingsDialog());

        com.google.android.material.button.MaterialButton btnModuleStore = view.findViewById(R.id.btn_module_store);
        btnModuleStore.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), ModuleStoreActivity.class);
            startActivity(intent);
        });

        initCategories();
        setupSearch();
        setupTabs();
    }

    /**
     * 页面恢复时刷新列表（反映使用记录变化）并更新离线提示。
     */
    @Override
    public void onResume() {
        super.onResume();
        isDestroyed = false;
        refreshInstalledModuleGames();
        updateOfflineHint();
    }

    /**
     * Fragment 视图销毁时标记状态，防止异步回调持有引用导致内存泄漏。
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isDestroyed = true;
        if (rvGames != null) {
            rvGames.setAdapter(null);
            rvGames = null;
        }
        tabLayout = null;
        etGameSearch = null;
        tvEmptyState = null;
        currentAdapter = null;
        categories = null;
        allGames = null;
        if (gamesById != null) {
            gamesById.clear();
            gamesById = null;
        }
    }

    /**
     * 根据网络状态调整空状态提示的透明度，离线时降低透明度以示区分。
     */
    private void updateOfflineHint() {
        if (tvEmptyState != null && !NetworkErrorHandler.isNetworkAvailable(requireContext())) {
            tvEmptyState.setAlpha(0.7f);
        } else if (tvEmptyState != null) {
            tvEmptyState.setAlpha(1.0f);
        }
    }

    /**
     * 显示应用设置对话框。
     * <p>
     * 设置回调：确认后触发更新检查，反馈按钮打开反馈对话框。
     * </p>
     */
    private void showSettingsDialog() {
        new AppSettingsDialog(
                this,
                () -> {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).checkUpdate(true);
                    }
                },
                this::showFeedbackDialog)
                .show();
    }

    /**
     * 显示用户反馈对话框。
     * <p>
     * 支持两种提交方式：
     * <ol>
     *   <li>通过本地邮箱客户端或网页邮箱发送</li>
     *   <li>通过 VPS API 在线提交</li>
     * </ol>
     * 对话框中可选择反馈类型（Bug / 功能建议）、填写反馈内容和联系方式。
     * </p>
     */
    private void showFeedbackDialog() {
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_feedback, null);

        ListView lvProviders = dialogView.findViewById(R.id.lv_email_providers);
        TextView tvCopyHint = dialogView.findViewById(R.id.tv_copy_hint);
        LinearLayout llCopyRecipient = dialogView.findViewById(R.id.ll_copy_recipient);
        TextView tvRecipient = dialogView.findViewById(R.id.tv_recipient);
        EditText etMessage = dialogView.findViewById(R.id.et_feedback_message);
        EditText etContact = dialogView.findViewById(R.id.et_feedback_contact);
        RadioGroup rgFeedbackType = dialogView.findViewById(R.id.rg_feedback_type);
        MaterialButton btnSubmitFeedback = dialogView.findViewById(R.id.btn_submit_feedback);

        // 检查收件地址是否为有效邮箱，决定是否显示邮箱相关 UI
        String recipient = getString(R.string.feedback_copy_recipient).trim();
        boolean emailFallbackEnabled = recipient.contains("@");
        if (tvRecipient != null) {
            tvRecipient.setText(recipient);
        }
        if (!emailFallbackEnabled) {
            lvProviders.setVisibility(View.GONE);
            llCopyRecipient.setVisibility(View.GONE);
        }

        String[] providerNames = {
                getString(R.string.feedback_default),
                getString(R.string.feedback_qq),
                getString(R.string.feedback_163),
                getString(R.string.feedback_gmail),
                getString(R.string.feedback_outlook),
        };

        final String[] webUrls = {
                null,
                "https://mail.qq.com/",
                "https://mail.163.com/",
                "https://mail.google.com/",
                "https://outlook.live.com/",
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_list_item_1, providerNames);
        lvProviders.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.feedback_title)
                .setView(dialogView)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        lvProviders.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                dialog.dismiss();
                String feedbackType = getSelectedFeedbackType(rgFeedbackType);
                String body = buildFeedbackEmailBody(
                        feedbackType,
                        etMessage != null ? etMessage.getText().toString() : "",
                        etContact != null ? etContact.getText().toString() : "");
                if (position == 0) {
                    // 选择默认邮箱客户端
                    if (emailFallbackEnabled) {
                        openLocalEmailClient(recipient, body);
                    } else {
                        Toast.makeText(getContext(), R.string.feedback_no_client, Toast.LENGTH_SHORT).show();
                    }
                } else {
                    // 选择网页邮箱
                    openWebEmail(webUrls[position], position);
                }
            }
        });

        btnSubmitFeedback.setOnClickListener(v -> submitFeedbackToVps(
                dialog,
                etMessage,
                etContact,
                rgFeedbackType,
                btnSubmitFeedback));

        // 点击收件人区域复制邮箱地址到剪贴板
        if (emailFallbackEnabled) {
            llCopyRecipient.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) requireContext()
                        .getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("feedback_recipient", recipient));
                    Toast.makeText(getContext(), R.string.feedback_copied, Toast.LENGTH_SHORT).show();
                }
            });
        }

        dialog.show();
    }

    /**
     * 通过本地邮箱客户端发送反馈邮件。
     *
     * @param recipient 收件人邮箱地址
     * @param body      邮件正文内容
     */
    private void openLocalEmailClient(String recipient, String body) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + recipient));
        intent.putExtra(Intent.EXTRA_SUBJECT, "GameMatrixApp 意见反馈");
        intent.putExtra(Intent.EXTRA_TEXT, body);
        try {
            startActivity(intent);
            Toast.makeText(getContext(), R.string.feedback_toast, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), R.string.feedback_no_client, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 通过 VPS API 在线提交反馈。
     * <p>
     * 在后台线程构建 JSON 负载并 POST 到反馈服务器，
     * 成功后关闭对话框，失败则提示用户使用邮箱兜底。
     * </p>
     *
     * @param dialog         反馈对话框，提交成功后关闭
     * @param etMessage      反馈内容输入框
     * @param etContact      联系方式输入框
     * @param rgFeedbackType 反馈类型单选组
     * @param button         提交按钮，提交期间禁用
     */
    private void submitFeedbackToVps(AlertDialog dialog, EditText etMessage,
                                     EditText etContact, RadioGroup rgFeedbackType,
                                     MaterialButton button) {
        String message = etMessage != null ? etMessage.getText().toString().trim() : "";
        String contact = etContact != null ? etContact.getText().toString().trim() : "";
        String feedbackType = getSelectedFeedbackType(rgFeedbackType);
        if (message.isEmpty()) {
            Toast.makeText(getContext(), R.string.hall_feedback_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        if (button != null) {
            button.setEnabled(false);
            button.setText(getString(R.string.hall_feedback_submitting));
        }

        ExecutorService feedbackExecutor = Executors.newSingleThreadExecutor();
        feedbackExecutor.execute(() -> {
            String error = null;
            try {
                JSONObject payload = buildFeedbackPayload(feedbackType, message, contact);
                postFeedbackJson(payload);
            } catch (Exception e) {
                error = e.getMessage();
            } finally {
                feedbackExecutor.shutdown();
            }

            final String finalError = error;
            if (!isAdded()) return;
            requireActivity().runOnUiThread(() -> {
                if (button != null) {
                    button.setEnabled(true);
                    button.setText(getString(R.string.hall_feedback_submit));
                }
                if (finalError == null) {
                    Toast.makeText(getContext(), R.string.hall_feedback_success, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                } else {
                    Toast.makeText(getContext(),
                            getString(R.string.hall_feedback_vps_failed_format, finalError),
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    /**
     * 获取用户选择的反馈类型。
     *
     * @param rgFeedbackType 反馈类型单选组
     * @return "feature" 表示功能建议，"bug" 表示问题反馈
     */
    private String getSelectedFeedbackType(RadioGroup rgFeedbackType) {
        if (rgFeedbackType != null && rgFeedbackType.getCheckedRadioButtonId() == R.id.rb_feedback_feature) {
            return "feature";
        }
        return "bug";
    }

    /**
     * 将反馈类型标识转换为可读标签。
     *
     * @param feedbackType 反馈类型标识（"feature" 或 "bug"）
     * @return 对应的本地化标签文本
     */
    private String getFeedbackTypeLabel(String feedbackType) {
        return "feature".equals(feedbackType)
                ? getString(R.string.feedback_type_feature)
                : getString(R.string.feedback_type_bug);
    }

    /**
     * 构建反馈提交的 JSON 负载。
     * <p>
     * 包含反馈内容、联系方式、应用版本、设备信息和诊断数据。
     * </p>
     *
     * @param feedbackType 反馈类型
     * @param message      反馈内容
     * @param contact      联系方式
     * @return 包含所有反馈信息的 JSON 对象
     * @throws Exception JSON 构建异常
     */
    private JSONObject buildFeedbackPayload(String feedbackType, String message, String contact) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("type", feedbackType);
        payload.put("feedbackType", feedbackType);
        payload.put("typeLabel", getFeedbackTypeLabel(feedbackType));
        payload.put("message", message);
        payload.put("contact", contact);
        payload.put("appVersion", BuildConfig.VERSION_NAME);
        payload.put("versionCode", BuildConfig.VERSION_CODE);
        payload.put("channel", BuildConfig.VERSION_CHANNEL);
        payload.put("device", Build.BRAND + " " + Build.MODEL);
        payload.put("androidVersion", Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT);
        payload.put("diagnostics", buildFeedbackDiagnostics());
        return payload;
    }

    /**
     * 通过 HTTP POST 将反馈 JSON 提交到 VPS 服务器。
     *
     * @param payload 反馈 JSON 负载
     * @throws Exception 网络请求或服务器响应异常
     */
    private void postFeedbackJson(JSONObject payload) throws Exception {
        OkHttpClient client = OkHttpClientProvider.getInstance(requireContext())
                .getHttpClient()
                .newBuilder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .build();

        String bodyStr = payload.toString();
        RequestBody body = RequestBody.create(
                MediaType.parse("application/json; charset=utf-8"), bodyStr);

        Request request = new Request.Builder()
                .url(com.gamecenter.app.BuildConfig.FEEDBACK_URL)
                .post(body)
                .addHeader("Accept", "application/json")
                .addHeader("User-Agent", "GameMatrixApp/" + BuildConfig.VERSION_NAME)
                .build();

        try (Response response = client.newCall(request).execute()) {
            String responseStr = response.body() != null ? response.body().string() : "";
            int code = response.code();
            // 非 2xx 状态码视为提交失败
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("HTTP " + code + " " + responseStr);
            }
            JSONObject json = new JSONObject(responseStr);
            // 服务器返回 ok=false 也视为提交失败
            if (!json.optBoolean("ok", false)) {
                throw new IllegalStateException(json.optString("error", "服务器未接受反馈"));
            }
        }
    }

    /**
     * 构建用于邮箱发送的反馈正文。
     *
     * @param feedbackType 反馈类型
     * @param message      反馈内容
     * @param contact      联系方式
     * @return 格式化的反馈邮件正文
     */
    private String buildFeedbackEmailBody(String feedbackType, String message, String contact) {
        StringBuilder sb = new StringBuilder();
        sb.append("反馈类型:\n").append(getFeedbackTypeLabel(feedbackType)).append("\n\n");
        if (message != null && !message.trim().isEmpty()) {
            sb.append("反馈内容:\n").append(message.trim()).append("\n\n");
        }
        if (contact != null && !contact.trim().isEmpty()) {
            sb.append("联系方式:\n").append(contact.trim()).append("\n\n");
        }
        sb.append(buildFeedbackDiagnostics());
        return sb.toString();
    }

    /**
     * 构建反馈诊断信息，包含应用版本、设备型号和系统版本等。
     *
     * @return 格式化的诊断信息文本
     */
    private String buildFeedbackDiagnostics() {
        SettingsManager settings = SettingsManager.getInstance(requireContext());
        return "诊断信息:\n"
                + "App版本: " + BuildConfig.VERSION_NAME + "\n"
                + "内部版本号: " + BuildConfig.VERSION_CODE + "\n"
                + "更新通道: " + BuildConfig.VERSION_CHANNEL + "\n"
                + "接受测试版: " + settings.isAcceptBetaUpdate() + "\n"
                + "自动检查更新: " + settings.isAutoCheckUpdate() + "\n"
                + "设备: " + Build.BRAND + " " + Build.MODEL + "\n"
                + "Android: " + Build.VERSION.RELEASE + " / API " + Build.VERSION.SDK_INT + "\n";
    }

    /**
     * 打开网页邮箱。
     *
     * @param url      邮箱网页地址
     * @param position 邮箱提供商在列表中的位置
     */
    private void openWebEmail(String url, int position) {
        try {
            if (url != null) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), R.string.hall_open_browser_failed, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 显示更新日志对话框，包含版本号和检查更新按钮。
     */
    private void showChangelog() {
        String changelog = BuildConfig.CHANGELOG == null ? "" : BuildConfig.CHANGELOG.trim();
        if (changelog.isEmpty()) {
            changelog = "暂无更新日志";
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.about_with_version_format, getString(R.string.app_name), BuildConfig.VERSION_NAME))
                .setMessage(changelog)
                .setPositiveButton(R.string.about_check_update, (d, w) -> {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).checkUpdate(true);
                    }
                })
                .setNegativeButton(R.string.close, null)
                .show();
    }

    /** 初始化游戏目录，后续新增游戏只需要维护 GameRegistry。 */
    private void initCategories() {
        categories = GameRegistry.getCategories(requireContext());
        allGames = GameRegistry.flatten(categories);
        gamesById = new HashMap<>();
        for (GameRegistry.Entry game : allGames) {
            gamesById.put(game.id, game);
        }
    }

    /** 从模块市场同步已安装游戏，确保下载后的游戏回到大厅可见。 */
    private void refreshInstalledModuleGames() {
        if (!isAdded() || isDestroyed) return;
        final Context appContext = requireContext().getApplicationContext();
        ModuleManager.INSTANCE.loadModuleList(appContext, (modules, error) -> {
            if (isDestroyed || !isAdded() || getActivity() == null) return null;
            try {
                getActivity().runOnUiThread(() -> {
                    if (isDestroyed || !isAdded()) return;
                    try {
                        ModuleManager.INSTANCE.registerInstalledGameModules(appContext);
                        initCategories();
                        setupTabs();
                        applyFilter();
                    } catch (Exception e) {
                        Log.w("GamesFragment", "refreshInstalledModuleGames UI error: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                Log.w("GamesFragment", "refreshInstalledModuleGames error: " + e.getMessage());
            }
            return null;
        });
    }

    /**
     * 设置搜索框的文本变化监听，实时过滤游戏列表。
     */
    private void setupSearch() {
        etGameSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentQuery = s == null ? "" : s.toString().trim();
                applyFilter();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    /**
     * 初始化标签页：全部、最近、收藏，以及各游戏分类标签。
     */
    private void setupTabs() {
        int targetPosition = Math.max(TAB_ALL, selectedTabPosition);
        tabLayout.clearOnTabSelectedListeners();
        tabLayout.removeAllTabs();

        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.hall_tab_all)));
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.hall_tab_recent)));
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.hall_tab_favorite)));

        // 动态添加各游戏分类标签
        for (GameRegistry.Category category : categories) {
            TabLayout.Tab tab = tabLayout.newTab();
            tab.setText(category.name);
            tabLayout.addTab(tab);
        }

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) { showCategory(tab.getPosition()); }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}
            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        if (targetPosition >= tabLayout.getTabCount()) {
            targetPosition = TAB_ALL;
        }
        TabLayout.Tab targetTab = tabLayout.getTabAt(targetPosition);
        if (targetTab != null) {
            targetTab.select();
        }
        showCategory(targetPosition);
    }

    /**
     * 切换到指定标签页并刷新列表。
     *
     * @param position 标签页位置索引
     */
    private void showCategory(int position) {
        selectedTabPosition = position;
        applyFilter();
    }

    /**
     * 根据当前标签页和搜索关键词过滤游戏列表并更新 RecyclerView。
     */
    private void applyFilter() {
        if (allGames == null) {
            return;
        }

        List<GameRegistry.Entry> source = getSourceGames();
        List<GameRegistry.Entry> filtered = filterGames(source, currentQuery);
        currentAdapter = new GameAdapter(filtered);
        rvGames.setAdapter(currentAdapter);
        updateEmptyState(filtered.isEmpty());
    }

    /**
     * 根据当前标签页获取游戏来源列表。
     * <p>
     * 搜索时始终从全量列表过滤；否则根据标签页类型返回对应的子集。
     * </p>
     *
     * @return 当前标签页对应的游戏列表
     */
    private List<GameRegistry.Entry> getSourceGames() {
        // 搜索时忽略 Tab 筛选，从全量列表中过滤
        if (!currentQuery.isEmpty()) {
            return allGames;
        }
        if (selectedTabPosition == TAB_RECENT) {
            // 最近游玩：按使用记录中的最近 ID 顺序排列，最多 12 个
            List<GameRegistry.Entry> recentGames = new ArrayList<>();
            for (String id : usageStore.getRecentIds(12)) {
                GameRegistry.Entry entry = gamesById.get(id);
                if (entry != null) {
                    recentGames.add(entry);
                }
            }
            return recentGames;
        }
        if (selectedTabPosition == TAB_FAVORITES) {
            // 收藏：仅显示已收藏的游戏
            Set<String> favoriteIds = usageStore.getFavoriteIds();
            List<GameRegistry.Entry> favoriteGames = new ArrayList<>();
            for (GameRegistry.Entry game : allGames) {
                if (favoriteIds.contains(game.id)) {
                    favoriteGames.add(game);
                }
            }
            return favoriteGames;
        }
        if (selectedTabPosition >= CATEGORY_TAB_OFFSET) {
            // 分类标签：根据偏移量映射到分类索引
            int categoryIndex = selectedTabPosition - CATEGORY_TAB_OFFSET;
            if (categoryIndex >= 0 && categoryIndex < categories.size()) {
                return categories.get(categoryIndex).games;
            }
        }
        return allGames;
    }

    /**
     * 根据关键词过滤游戏列表，匹配游戏名称、描述和分类。
     *
     * @param source 待过滤的游戏列表
     * @param query  搜索关键词（不区分大小写）
     * @return 匹配的游戏列表
     */
    private List<GameRegistry.Entry> filterGames(List<GameRegistry.Entry> source, String query) {
        if (query == null || query.isEmpty()) {
            return source;
        }
        String normalizedQuery = query.toLowerCase(Locale.ROOT);
        List<GameRegistry.Entry> filtered = new ArrayList<>();
        for (GameRegistry.Entry game : source) {
            if (game.name.toLowerCase(Locale.ROOT).contains(normalizedQuery)
                    || game.desc.toLowerCase(Locale.ROOT).contains(normalizedQuery)
                    || game.category.toLowerCase(Locale.ROOT).contains(normalizedQuery)) {
                filtered.add(game);
            }
        }
        return filtered;
    }

    /**
     * 更新空状态提示的显示内容和可见性。
     *
     * @param empty 列表是否为空
     */
    private void updateEmptyState(boolean empty) {
        rvGames.setVisibility(empty ? View.GONE : View.VISIBLE);
        tvEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (!empty) {
            return;
        }

        // 根据当前上下文显示不同的空状态提示
        if (!currentQuery.isEmpty()) {
            tvEmptyState.setText(getString(R.string.hall_empty_search));
        } else if (selectedTabPosition == TAB_RECENT) {
            tvEmptyState.setText(getString(R.string.hall_empty_recent));
        } else if (selectedTabPosition == TAB_FAVORITES) {
            tvEmptyState.setText(getString(R.string.hall_empty_favorite));
        } else {
            tvEmptyState.setText(getString(R.string.hall_empty_default));
        }
    }

    /**
     * 获取游戏卡片的元信息文本（游玩次数和相对时间）。
     *
     * @param item 游戏条目
     * @return 格式化的元信息文本，如"已玩 3 次 · 2 小时前"
     */
    private String getMetaText(GameRegistry.Entry item) {
        int playCount = usageStore.getPlayCount(item.id);
        if (playCount <= 0) {
            return "未开始";
        }
        long lastPlayedAt = usageStore.getLastPlayedAt(item.id);
        CharSequence relativeTime = DateUtils.getRelativeTimeSpanString(
                lastPlayedAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE);
        return "已玩 " + playCount + " 次 · " + relativeTime;
    }

    /**
     * 游戏卡片列表适配器。
     * <p>
     * 负责渲染游戏卡片，包括图标、名称、描述、元信息、收藏按钮，
     * 以及根据当前主题应用配色方案。
     * </p>
     */
    private class GameAdapter extends RecyclerView.Adapter<GameAdapter.GameViewHolder> {
        private final List<GameRegistry.Entry> gameList;

        GameAdapter(List<GameRegistry.Entry> gameList) { this.gameList = gameList; }

        @NonNull
        @Override
        public GameViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_game_card, parent, false);
            return new GameViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull GameViewHolder holder, int position) {
            GameRegistry.Entry item = gameList.get(position);
            Context bindCtx = holder.itemView.getContext();

            holder.ivIcon.setImageResource(item.iconRes);
                holder.tvDesc.setText(item.desc);
                // btnEnter removed: cardView handles click

            // 分类标签：根据 item.category 设置文字和背景
            String category = item.category != null ? item.category : "";
            holder.tvCategoryTag.setText(category);
            int tagBgRes = getCategoryTagBgRes(category);
            holder.tvCategoryTag.setBackgroundResource(tagBgRes);

            // 评分（模拟数据，实际可接入真实评分源）
            float rating = item.id.hashCode() % 3 == 0 ? 4.9f : (item.id.hashCode() % 2 == 0 ? 4.6f : 4.3f);
            holder.tvRating.setText(String.format(Locale.US, "%.1f", rating));

            // 热度角标：启动次数 > 5 视为热门
            boolean isHot = usageStore.getPlayCount(item.id) >= 5;
            holder.ivHotBadge.setVisibility(isHot ? View.VISIBLE : View.GONE);

            applyCardColorScheme(holder);
            updateFavoriteButton(holder.btnFavorite, item);

            View.OnClickListener launch = v -> {
                usageStore.recordLaunch(item.id);
                Intent intent = new Intent(getActivity(), item.activityClass);
                if (item.activityClass == DynamicGameActivity.class) {
                    intent.putExtra(DynamicGameActivity.EXTRA_GAME_ID, item.id);
                }
                startActivity(intent);
            };
            // btnEnter removed: cardView handles click
            holder.cardView.setOnClickListener(launch);
            holder.btnFavorite.setOnClickListener(v -> {
                usageStore.toggleFavorite(item.id);
                applyFilter();
            });
        }

        /** 根据分类名称返回对应的渐变标签背景 Drawable */
        private int getCategoryTagBgRes(String category) {
            switch (category) {
                case "经典":
                case "棋牌":
                    return R.drawable.bg_category_tag_classics;
                case "益智类":
                case "解谜":
                    return R.drawable.bg_category_tag_puzzle;
                case "休闲类":
                case "街机":
                    return R.drawable.bg_category_tag_arcade;
                case "反应类":
                    return R.drawable.bg_category_tag_reaction;
                default:
                    return R.drawable.bg_category_tag_casual;
            }
        }

        private void updateFavoriteButton(com.google.android.material.button.MaterialButton button, GameRegistry.Entry item) {
            boolean favorite = usageStore.isFavorite(item.id);
            android.graphics.drawable.Drawable icon = ContextCompat.getDrawable(
                    button.getContext(),
                    favorite
                            ? android.R.drawable.btn_star_big_on
                            : android.R.drawable.btn_star_big_off);
            if (icon != null) {
                button.setIcon(icon);
            }
            button.setContentDescription((favorite ? "取消收藏 " : "收藏 ") + item.name);
            
            Context ctx = button.getContext();
            boolean isDark = (ctx.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            int iconColor = favorite
                    ? (isDark ? R.color.md_theme_primary : R.color.md_theme_light_primary)
                    : (isDark ? R.color.md_theme_on_surface_variant : R.color.md_theme_light_on_surface_variant);
            button.setIconTint(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, iconColor)));
        }

        /**
         * 根据当前主题和配色方案为卡片各元素着色。
         * <p>
         * 根据深色/浅色模式选择不同的颜色变体，
         * 包括卡片背景、文字颜色和按钮颜色。
         * </p>
         *
         * @param holder 卡片 ViewHolder
         */
        private void applyCardColorScheme(GameViewHolder holder) {
            Context context = holder.itemView.getContext();
            SettingsManager settings = SettingsManager.getInstance(context);
            ColorSchemeManager.Scheme scheme = ColorSchemeManager.getScheme(
                    settings.getColorSchemeIndex());
            boolean isDark = (context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

            int cardColor = isDark ? scheme.darkSurfaceVariant : scheme.surface;
            int primaryText = isDark ? scheme.darkOnSurface : scheme.onSurface;
            int secondaryText = isDark ? scheme.darkOnSurfaceVariant : scheme.onSurfaceVariant;
            int buttonColor = isDark ? scheme.primary : scheme.primaryContainer;
            int buttonText = isDark ? scheme.onPrimary : scheme.onPrimaryContainer;

            holder.cardView.setCardBackgroundColor(cardColor);
            holder.tvName.setTextColor(primaryText);
            holder.tvDesc.setTextColor(secondaryText);
                holder.tvDesc.setTextColor(secondaryText);
            holder.btnFavorite.setBackgroundTintList(ColorStateList.valueOf(buttonColor));
        }

        @Override
        public int getItemCount() { return gameList.size(); }

        /**
         * 游戏卡片 ViewHolder，持有卡片内各 UI 控件的引用。
         */
        class GameViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvName;
            TextView tvDesc;
            TextView tvCategoryTag;
            TextView tvRating;
            ImageView ivHotBadge;
            com.google.android.material.button.MaterialButton btnFavorite;
            MaterialCardView cardView;
            View gameIconContainer;

            GameViewHolder(@NonNull View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.iv_game_icon);
                tvName = itemView.findViewById(R.id.tv_game_name);
                tvDesc = itemView.findViewById(R.id.tv_game_desc);
                tvCategoryTag = itemView.findViewById(R.id.tv_category_tag);
                tvRating = itemView.findViewById(R.id.tv_rating);
                ivHotBadge = itemView.findViewById(R.id.iv_hot_badge);
                btnFavorite = itemView.findViewById(R.id.btn_favorite);
                cardView = (MaterialCardView) itemView;
                gameIconContainer = itemView.findViewById(R.id.gameIconContainer);
            }
        }
    }
}
