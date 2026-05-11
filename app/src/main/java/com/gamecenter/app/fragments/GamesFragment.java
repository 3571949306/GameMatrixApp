package com.gamecenter.app.fragments;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
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
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import com.gamecenter.app.BuildConfig;
import com.gamecenter.app.ColorSchemeManager;
import com.gamecenter.app.MainActivity;
import com.gamecenter.app.R;
import com.gamecenter.app.SettingsManager;
import com.gamecenter.app.games.GameRegistry;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 游戏大厅 Fragment — TabLayout(经典/益智/休闲/反应/其他) + RecyclerView 卡片网格。
 * 右上角设置按钮切换系统/白天/黑暗主题。
 */
public class GamesFragment extends Fragment {

    private static final int TAB_ALL = 0;
    private static final int TAB_RECENT = 1;
    private static final int TAB_FAVORITES = 2;
    private static final int CATEGORY_TAB_OFFSET = 3;

    private List<GameRegistry.Category> categories;
    private List<GameRegistry.Entry> allGames;
    private Map<String, GameRegistry.Entry> gamesById;
    private GameUsageStore usageStore;
    private TabLayout tabLayout;
    private RecyclerView rvGames;
    private EditText etGameSearch;
    private TextView tvEmptyState;
    private GameAdapter currentAdapter;
    private String currentQuery = "";
    private int selectedTabPosition = TAB_ALL;

    public GamesFragment() {
        super(R.layout.fragment_games);
    }

    @Override
    public void onViewCreated(@NonNull View view, android.os.Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tabLayout = view.findViewById(R.id.tab_layout);
        rvGames = view.findViewById(R.id.rv_games);
        etGameSearch = view.findViewById(R.id.et_game_search);
        tvEmptyState = view.findViewById(R.id.tv_empty_state);
        usageStore = new GameUsageStore(requireContext());

        TextView tvVersion = view.findViewById(R.id.tv_version);
        tvVersion.setText("v" + BuildConfig.VERSION_NAME);
        tvVersion.setOnClickListener(v -> showChangelog());

        ImageButton btnSettings = view.findViewById(R.id.btn_settings);
        btnSettings.setOnClickListener(v -> showSettingsDialog());

        initCategories();
        setupSearch();
        setupTabs();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (usageStore != null && currentAdapter != null) {
            applyFilter();
        }
    }

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
                    if (emailFallbackEnabled) {
                        openLocalEmailClient(recipient, body);
                    } else {
                        Toast.makeText(getContext(), R.string.feedback_no_client, Toast.LENGTH_SHORT).show();
                    }
                } else {
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

    private void openLocalEmailClient(String recipient, String body) {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + recipient));
        intent.putExtra(Intent.EXTRA_SUBJECT, "GameCenterApp 意见反馈");
        intent.putExtra(Intent.EXTRA_TEXT, body);
        try {
            startActivity(intent);
            Toast.makeText(getContext(), R.string.feedback_toast, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), R.string.feedback_no_client, Toast.LENGTH_SHORT).show();
        }
    }

    private void submitFeedbackToVps(AlertDialog dialog, EditText etMessage,
                                     EditText etContact, RadioGroup rgFeedbackType,
                                     MaterialButton button) {
        String message = etMessage != null ? etMessage.getText().toString().trim() : "";
        String contact = etContact != null ? etContact.getText().toString().trim() : "";
        String feedbackType = getSelectedFeedbackType(rgFeedbackType);
        if (message.isEmpty()) {
            Toast.makeText(getContext(), "请先填写反馈内容", Toast.LENGTH_SHORT).show();
            return;
        }
        if (button != null) {
            button.setEnabled(false);
            button.setText("提交中...");
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
                    button.setText("提交反馈");
                }
                if (finalError == null) {
                    Toast.makeText(getContext(), "反馈已提交，感谢你的建议", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                } else {
                    Toast.makeText(getContext(),
                            "提交到 VPS 失败，可使用下方邮箱兜底: " + finalError,
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private String getSelectedFeedbackType(RadioGroup rgFeedbackType) {
        if (rgFeedbackType != null && rgFeedbackType.getCheckedRadioButtonId() == R.id.rb_feedback_feature) {
            return "feature";
        }
        return "bug";
    }

    private String getFeedbackTypeLabel(String feedbackType) {
        return "feature".equals(feedbackType)
                ? getString(R.string.feedback_type_feature)
                : getString(R.string.feedback_type_bug);
    }

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

    private void postFeedbackJson(JSONObject payload) throws Exception {
        URL url = new URL(com.gamecenter.app.BuildConfig.FEEDBACK_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(12000);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "GameCenterApp/" + BuildConfig.VERSION_NAME);
        conn.setDoOutput(true);

        byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body);
        }

        int code = conn.getResponseCode();
        java.io.InputStream responseStream = code >= 200 && code < 300
                ? conn.getInputStream()
                : conn.getErrorStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                responseStream != null ? responseStream : conn.getInputStream(),
                StandardCharsets.UTF_8));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        conn.disconnect();

        if (code < 200 || code >= 300) {
            throw new IllegalStateException("HTTP " + code + " " + response);
        }
        JSONObject json = new JSONObject(response.toString());
        if (!json.optBoolean("ok", false)) {
            throw new IllegalStateException(json.optString("error", "服务器未接受反馈"));
        }
    }

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

    private void openWebEmail(String url, int position) {
        try {
            if (url != null) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
            }
        } catch (Exception e) {
            Toast.makeText(getContext(), "无法打开浏览器", Toast.LENGTH_SHORT).show();
        }
    }

    private void showChangelog() {
        String changelog = BuildConfig.CHANGELOG == null ? "" : BuildConfig.CHANGELOG.trim();
        if (changelog.isEmpty()) {
            changelog = "暂无更新日志";
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("关于 " + getString(R.string.app_name) + " v" + BuildConfig.VERSION_NAME)
                .setMessage(changelog)
                .setPositiveButton("检查更新", (d, w) -> {
                    if (getActivity() instanceof MainActivity) {
                        ((MainActivity) getActivity()).checkUpdate(true);
                    }
                })
                .setNegativeButton("关闭", null)
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

    private void setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("全部"));
        tabLayout.addTab(tabLayout.newTab().setText("最近"));
        tabLayout.addTab(tabLayout.newTab().setText("收藏"));

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

        showCategory(TAB_ALL);
    }

    private void showCategory(int position) {
        selectedTabPosition = position;
        applyFilter();
    }

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

    private List<GameRegistry.Entry> getSourceGames() {
        if (!currentQuery.isEmpty()) {
            return allGames;
        }
        if (selectedTabPosition == TAB_RECENT) {
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
            int categoryIndex = selectedTabPosition - CATEGORY_TAB_OFFSET;
            if (categoryIndex >= 0 && categoryIndex < categories.size()) {
                return categories.get(categoryIndex).games;
            }
        }
        return allGames;
    }

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

    private void updateEmptyState(boolean empty) {
        rvGames.setVisibility(empty ? View.GONE : View.VISIBLE);
        tvEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (!empty) {
            return;
        }

        if (!currentQuery.isEmpty()) {
            tvEmptyState.setText("没有找到相关游戏");
        } else if (selectedTabPosition == TAB_RECENT) {
            tvEmptyState.setText("还没有最近游玩");
        } else if (selectedTabPosition == TAB_FAVORITES) {
            tvEmptyState.setText("还没有收藏游戏");
        } else {
            tvEmptyState.setText("这里暂时没有游戏");
        }
    }

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
            Glide.with(holder.itemView.getContext())
                    .load(item.iconRes)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(holder.ivIcon);
            holder.tvName.setText(item.name);
            holder.tvDesc.setText(item.desc);
            holder.tvMeta.setText(getMetaText(item));
            applyCardColorScheme(holder);
            updateFavoriteButton(holder.btnFavorite, item);

            // 点击卡片或按钮均启动对应游戏 Activity
            View.OnClickListener launch = v -> {
                usageStore.recordLaunch(item.id);
                startActivity(new Intent(getActivity(), item.activityClass));
            };
            holder.btnEnter.setOnClickListener(launch);
            holder.cardView.setOnClickListener(launch);
            holder.btnFavorite.setOnClickListener(v -> {
                usageStore.toggleFavorite(item.id);
                applyFilter();
            });
        }

        private void updateFavoriteButton(ImageButton button, GameRegistry.Entry item) {
            boolean favorite = usageStore.isFavorite(item.id);
            button.setImageResource(favorite
                    ? android.R.drawable.btn_star_big_on
                    : android.R.drawable.btn_star_big_off);
            button.setContentDescription((favorite ? "取消收藏 " : "收藏 ") + item.name);
        }

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
            holder.tvMeta.setTextColor(secondaryText);
            holder.btnEnter.setBackgroundTintList(ColorStateList.valueOf(buttonColor));
            holder.btnEnter.setTextColor(buttonText);
            holder.btnFavorite.setColorFilter(scheme.primary);
        }

        @Override
        public int getItemCount() { return gameList.size(); }

        class GameViewHolder extends RecyclerView.ViewHolder {
            ImageView ivIcon;
            TextView tvName;
            TextView tvDesc;
            TextView tvMeta;
            ImageButton btnFavorite;
            com.google.android.material.button.MaterialButton btnEnter;
            CardView cardView;

            GameViewHolder(@NonNull View itemView) {
                super(itemView);
                ivIcon = itemView.findViewById(R.id.iv_game_icon);
                tvName = itemView.findViewById(R.id.tv_game_name);
                tvDesc = itemView.findViewById(R.id.tv_game_desc);
                tvMeta = itemView.findViewById(R.id.tv_game_meta);
                btnFavorite = itemView.findViewById(R.id.btn_favorite);
                btnEnter = itemView.findViewById(R.id.btn_enter);
                cardView = (CardView) itemView;
            }
        }
    }
}
