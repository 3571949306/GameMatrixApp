package com.gamecenter.app.browser.core;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.R;
import com.gamecenter.app.browser.data.BrowserDatabase;
import com.gamecenter.app.browser.data.dao.BrowserHistoryDao;
import com.gamecenter.app.browser.data.entity.BrowserHistoryEntity;
import com.gamecenter.app.browser.util.UrlUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能 URL Bar 助手。
 *
 * <p>提供：
 * <ul>
 *   <li>多搜索引擎支持（百度/Bing/Google/DuckDuckGo）</li>
 *   <li>历史建议查询（基于 BrowserHistoryDao 模糊匹配）</li>
 *   <li>URL/搜索词自动识别</li>
 * </ul>
 */
public class UrlInputHelper {

    /** 搜索引擎定义 */
    public enum SearchEngine {
        BAIDU("baidu", "百度", "https://www.baidu.com/s?wd="),
        BING("bing", "Bing", "https://www.bing.com/search?q="),
        GOOGLE("google", "Google", "https://www.google.com/search?q="),
        DUCKDUCKGO("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=");

        public final String key;
        public final String displayName;
        public final String queryPrefix;

        SearchEngine(String key, String displayName, String queryPrefix) {
            this.key = key;
            this.displayName = displayName;
            this.queryPrefix = queryPrefix;
        }

        @Nullable
        public static SearchEngine fromKey(@Nullable String key) {
            if (key == null) return BAIDU;
            for (SearchEngine e : values()) {
                if (e.key.equalsIgnoreCase(key)) return e;
            }
            return BAIDU;
        }
    }

    public interface OnEngineSelectedListener {
        void onEngineSelected(@NonNull SearchEngine engine);
    }

    public interface OnSuggestionSelectedListener {
        void onSuggestionSelected(@NonNull String url, @NonNull String title);
    }

    private final BrowserHistoryDao historyDao;

    public UrlInputHelper(@NonNull Context context) {
        this.historyDao = BrowserDatabase.getInstance(context.getApplicationContext()).historyDao();
    }

    /** 获取当前搜索引擎 */
    @NonNull
    public SearchEngine getCurrentEngine(@NonNull Context context) {
        String key = BrowserSettingsManager.getInstance(context).getSearchEngine();
        return SearchEngine.fromKey(key);
    }

    /** 处理用户输入：URL 直接返回，搜索词构造搜索引擎 URL */
    @NonNull
    public String processInput(@NonNull Context context, @NonNull String input) {
        String s = input.trim();
        if (s.isEmpty()) return BrowserSettingsManager.getInstance(context).getHomeUrl();

        String lower = s.toLowerCase();
        // 已有协议
        if (lower.startsWith("http://") || lower.startsWith("https://")) return s;
        // 危险协议 → 搜索
        if (lower.startsWith("file:") || lower.startsWith("content:")
                || lower.startsWith("javascript:") || lower.startsWith("intent:")
                || lower.startsWith("about:") || lower.startsWith("data:")) {
            return buildSearchUrl(context, s);
        }
        // 域名/IP 判断（无空格且有点）
        if (!s.contains(" ") && s.contains(".")) {
            // 简单域名匹配
            if (s.matches("^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}.*")
                    || s.matches("^(\\d{1,3}\\.){3}\\d{1,3}.*")) {
                return "https://" + s;
            }
        }
        // 默认搜索
        return buildSearchUrl(context, s);
    }

    /** 构造搜索引擎 URL */
    @NonNull
    public String buildSearchUrl(@NonNull Context context, @NonNull String keyword) {
        SearchEngine engine = getCurrentEngine(context);
        return engine.queryPrefix + UrlUtils.encodeKeyword(keyword);
    }

    /** 异步查询历史建议 */
    public void querySuggestionsAsync(@NonNull String keyword,
                                      @NonNull final SuggestionCallback callback) {
        if (TextUtils.isEmpty(keyword) || keyword.length() < 1) {
            callback.onResult(new ArrayList<>());
            return;
        }
        new Thread(() -> {
            try {
                List<BrowserHistoryEntity> entities = historyDao.searchHistory(keyword);
                List<SuggestionItem> items = new ArrayList<>();
                for (BrowserHistoryEntity e : entities) {
                    if (items.size() >= 8) break;
                    items.add(new SuggestionItem(e.getUrl(), e.getTitle(), e.getVisitCount()));
                }
                callback.onResult(items);
            } catch (Throwable t) {
                callback.onResult(new ArrayList<>());
            }
        }).start();
    }

    /** 弹出搜索引擎选择 PopupWindow */
    public PopupWindow showEngineSelector(@NonNull Context context,
                                          @NonNull View anchor,
                                          @NonNull final OnEngineSelectedListener listener) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * context.getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, pad / 2);
        container.setBackground(context.getDrawable(android.R.drawable.dialog_holo_light_frame));

        LayoutInflater inflater = LayoutInflater.from(context);
        SearchEngine current = getCurrentEngine(context);
        for (final SearchEngine engine : SearchEngine.values()) {
            View item = inflater.inflate(R.layout.item_search_engine_selector, container, false);
            TextView tvName = item.findViewById(R.id.engine_name);
            TextView tvCheck = item.findViewById(R.id.engine_check);
            if (tvName != null) tvName.setText(engine.displayName);
            if (tvCheck != null) {
                tvCheck.setVisibility(engine == current ? View.VISIBLE : View.GONE);
                tvCheck.setText(context.getString(R.string.browser_search));
            }
            item.setOnClickListener(v -> {
                BrowserSettingsManager.getInstance(context).setSearchEngine(engine.key);
                listener.onEngineSelected(engine);
            });
            container.addView(item);
        }

        PopupWindow popup = new PopupWindow(container,
                (int) (220 * context.getResources().getDisplayMetrics().density),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popup.setOutsideTouchable(true);
        popup.setFocusable(true);
        popup.setElevation(8f);
        popup.showAsDropDown(anchor, 0, 4);
        return popup;
    }

    /**
     * 显示 URL 建议下拉列表。
     *
     * @param anchor    锚点 View（通常为 etUrl）
     * @param items     建议项列表
     * @param listener  选中回调
     * @return 已显示的 PopupWindow（可在外部 dismiss）
     */
    public PopupWindow showSuggestionsPopup(@NonNull Context context,
                                            @NonNull View anchor,
                                            @NonNull List<SuggestionItem> items,
                                            @NonNull final OnSuggestionSelectedListener listener) {
        if (items.isEmpty()) return null;

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(0, 0, 0, 0);
        container.setBackground(context.getDrawable(android.R.drawable.dialog_holo_light_frame));

        LayoutInflater inflater = LayoutInflater.from(context);
        int maxWidth = anchor.getWidth();
        int width = maxWidth > 0 ? maxWidth : ViewGroup.LayoutParams.MATCH_PARENT;

        for (final SuggestionItem item : items) {
            View row = inflater.inflate(R.layout.item_url_suggestion, container, false);
            TextView tvTitle = row.findViewById(R.id.suggestion_title);
            TextView tvUrl = row.findViewById(R.id.suggestion_url);
            if (tvTitle != null) {
                tvTitle.setText(TextUtils.isEmpty(item.title) ? item.url : item.title);
            }
            if (tvUrl != null) {
                tvUrl.setText(item.url);
            }
            row.setOnClickListener(v -> listener.onSuggestionSelected(item.url, item.title));
            container.addView(row);
        }

        // 清除建议入口
        View clearItem = inflater.inflate(R.layout.item_url_suggestion, container, false);
        TextView tvClearTitle = clearItem.findViewById(R.id.suggestion_title);
        TextView tvClearUrl = clearItem.findViewById(R.id.suggestion_url);
        if (tvClearTitle != null) {
            tvClearTitle.setText(context.getString(R.string.browser_url_suggestion_clear));
            // 使用主题色（深色/浅色自适应）
            android.util.TypedValue tv = new android.util.TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.colorPrimary, tv, true);
            tvClearTitle.setTextColor(tv.data);
        }
        if (tvClearUrl != null) tvClearUrl.setVisibility(View.GONE);
        clearItem.setOnClickListener(v -> listener.onSuggestionSelected("__clear__", ""));
        container.addView(clearItem);

        PopupWindow popup = new PopupWindow(container,
                width,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popup.setOutsideTouchable(true);
        popup.setFocusable(false);  // 让 EditText 保持焦点继续输入
        popup.setElevation(6f);
        popup.showAsDropDown(anchor, 0, 2);
        return popup;
    }

    /** 建议项数据 */
    public static class SuggestionItem {
        public final String url;
        public final String title;
        public final int visitCount;
        public SuggestionItem(String url, String title, int visitCount) {
            this.url = url; this.title = title; this.visitCount = visitCount;
        }
    }

    public interface SuggestionCallback {
        void onResult(@NonNull List<SuggestionItem> items);
    }
}
