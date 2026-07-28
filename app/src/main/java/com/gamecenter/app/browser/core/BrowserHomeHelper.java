package com.gamecenter.app.browser.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.gamecenter.app.R;
import com.gamecenter.app.browser.data.BrowserDatabase;
import com.gamecenter.app.browser.data.dao.BrowserHistoryDao;
import com.gamecenter.app.browser.data.entity.BrowserHistoryEntity;
import com.gamecenter.app.browser.util.UrlUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 浏览器起始页 Helper。
 *
 * <p>管理 3 种风格的起始页：
 * <ul>
 *   <li>{@link BrowserSettingsManager#HOME_PAGE_STYLE_GRID}：搜索框 + 8 宫格常用网站</li>
 *   <li>{@link BrowserSettingsManager#HOME_PAGE_STYLE_CARDS}：搜索框 + 横向卡片</li>
 *   <li>{@link BrowserSettingsManager#HOME_PAGE_STYLE_MINIMAL}：仅搜索框 + 少量入口</li>
 * </ul>
 *
 * <p>常用网站基于历史访问频率自动学习（Top 8，按域名去重）。
 */
public class BrowserHomeHelper {

    private static final String TAG = "BrowserHomeHelper";
    private static final String PREFS_NAME = "browser_home_prefs";
    private static final String KEY_PINNED_SITES = "pinned_sites";
    private static final int MAX_TOP_SITES = 8;

    public interface HomeCallback {
        void onSiteClicked(@NonNull String url);
        void onSearchClicked();
        void onBookmarkClicked();
        void onHistoryClicked();
        void onReadingListClicked();
    }

    private final Context context;
    private final BrowserHistoryDao historyDao;
    private final SharedPreferences prefs;
    private HomeCallback callback;

    private View rootView;
    private ViewGroup gridContainer;
    private ViewGroup cardsContainer;
    private ViewGroup minimalContainer;

    // P0 内存泄漏修复：复用 Handler 以便在 destroy() 中移除待执行回调；
    // 原 loadTopSitesAsync 每次创建新 Handler 且无法移除，异步任务完成前 Helper 不可回收。
    private final android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private volatile boolean destroyed = false;

    public BrowserHomeHelper(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.historyDao = BrowserDatabase.getInstance(this.context).historyDao();
        this.prefs = this.context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void setCallback(@Nullable HomeCallback callback) {
        this.callback = callback;
    }

    /**
     * 创建起始页根 View。根据当前风格返回对应布局。
     */
    @NonNull
    public View createHomeView(@NonNull LayoutInflater inflater, @NonNull ViewGroup parent) {
        String style = BrowserSettingsManager.getInstance(context).getHomePageStyle();
        rootView = inflater.inflate(R.layout.layout_browser_home, parent, false);

        gridContainer = rootView.findViewById(R.id.home_grid_container);
        cardsContainer = rootView.findViewById(R.id.home_cards_container);
        minimalContainer = rootView.findViewById(R.id.home_minimal_container);

        // 搜索框点击 → 回调
        View searchBox = rootView.findViewById(R.id.home_search_box);
        if (searchBox != null) {
            searchBox.setOnClickListener(v -> {
                if (callback != null) callback.onSearchClicked();
            });
        }

        // 书签/历史/阅读列表入口
        View bookmarkEntry = rootView.findViewById(R.id.home_entry_bookmarks);
        View historyEntry = rootView.findViewById(R.id.home_entry_history);
        View readingEntry = rootView.findViewById(R.id.home_entry_reading_list);
        if (bookmarkEntry != null) {
            bookmarkEntry.setOnClickListener(v -> { if (callback != null) callback.onBookmarkClicked(); });
        }
        if (historyEntry != null) {
            historyEntry.setOnClickListener(v -> { if (callback != null) callback.onHistoryClicked(); });
        }
        if (readingEntry != null) {
            readingEntry.setOnClickListener(v -> { if (callback != null) callback.onReadingListClicked(); });
        }

        applyStyle(style);
        loadTopSitesAsync();
        return rootView;
    }

    /** 应用风格切换（不重新加载数据） */
    public void applyStyle(@NonNull String style) {
        if (rootView == null) return;
        gridContainer.setVisibility(
                BrowserSettingsManager.HOME_PAGE_STYLE_GRID.equals(style) ? View.VISIBLE : View.GONE);
        cardsContainer.setVisibility(
                BrowserSettingsManager.HOME_PAGE_STYLE_CARDS.equals(style) ? View.VISIBLE : View.GONE);
        minimalContainer.setVisibility(
                BrowserSettingsManager.HOME_PAGE_STYLE_MINIMAL.equals(style) ? View.VISIBLE : View.GONE);
    }

    /** 异步加载 Top Sites 并填充到当前风格的容器 */
    public void loadTopSitesAsync() {
        AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
            try {
                List<BrowserHistoryEntity> all = historyDao.getAllHistory();
                List<SiteEntry> top = computeTopSites(all);
                Map<String, SiteEntry> pinned = getPinnedSites();
                // 合并：pinned 优先
                Set<String> urls = new HashSet<>();
                List<SiteEntry> merged = new ArrayList<>();
                for (SiteEntry p : pinned.values()) {
                    if (urls.add(p.url)) merged.add(p);
                }
                for (SiteEntry t : top) {
                    if (urls.add(t.url)) merged.add(t);
                    if (merged.size() >= MAX_TOP_SITES) break;
                }
                final List<SiteEntry> finalList = merged;
                // P0 内存泄漏修复：复用 mainHandler 以便 destroy() 时移除回调；
                // 并在 post 前检查 destroyed 标志，避免 Helper 销毁后仍操作 stale view。
                if (!destroyed) {
                    mainHandler.post(() -> {
                        if (!destroyed) bindSites(finalList);
                    });
                }
            } catch (Throwable t) {
                Log.w(TAG, "loadTopSitesAsync failed", t);
            }
        });
    }

    /** 计算 Top Sites：按域名去重，按 visitCount 排序 */
    @NonNull
    private List<SiteEntry> computeTopSites(@NonNull List<BrowserHistoryEntity> all) {
        Map<String, SiteEntry> byDomain = new HashMap<>();
        for (BrowserHistoryEntity e : all) {
            if (e.getUrl() == null || e.getUrl().isEmpty()) continue;
            String domain = UrlUtils.getHost(e.getUrl());
            if (domain == null || domain.isEmpty()) continue;
            SiteEntry cur = byDomain.get(domain);
            if (cur == null || e.getVisitCount() > cur.visitCount) {
                byDomain.put(domain, new SiteEntry(
                        e.getUrl(),
                        e.getTitle() != null && !e.getTitle().isEmpty() ? e.getTitle() : domain,
                        domain,
                        e.getVisitCount()));
            }
        }
        List<SiteEntry> list = new ArrayList<>(byDomain.values());
        // 按 visitCount 降序
        list.sort((a, b) -> Integer.compare(b.visitCount, a.visitCount));
        return list.size() > MAX_TOP_SITES ? list.subList(0, MAX_TOP_SITES) : list;
    }

    @NonNull
    private Map<String, SiteEntry> getPinnedSites() {
        Map<String, SiteEntry> map = new HashMap<>();
        String raw = prefs.getString(KEY_PINNED_SITES, "");
        if (raw == null || raw.isEmpty()) return map;
        try {
            String[] items = raw.split("\\|");
            for (String item : items) {
                String[] parts = item.split("@@");
                if (parts.length == 3) {
                    map.put(parts[0], new SiteEntry(parts[0], parts[1], parts[2], Integer.MAX_VALUE));
                }
            }
        } catch (Exception ignore) {}
        return map;
    }

    /** 绑定站点列表到当前活跃的容器（grid/cards/minimal 共用） */
    private void bindSites(@NonNull List<SiteEntry> sites) {
        if (rootView == null) return;
        bindToContainer(gridContainer, sites, true);
        bindToContainer(cardsContainer, sites, false);
        // minimal 风格不显示站点列表，仅搜索框和入口
    }

    private void bindToContainer(@Nullable ViewGroup container,
                                 @NonNull List<SiteEntry> sites,
                                 boolean gridMode) {
        if (container == null) return;
        container.removeAllViews();
        // 修复：使用 container 的 context（来自 Fragment，带 AppCompat/Material 主题），
        // 而非 application context（DeviceDefault 主题，无法解析 ?attr/selectableItemBackgroundBorderless 等）。
        LayoutInflater inflater = LayoutInflater.from(container.getContext());
        for (SiteEntry site : sites) {
            View item = inflater.inflate(
                    gridMode ? R.layout.item_home_site_grid : R.layout.item_home_site_card,
                    container, false);
            ImageView icon = item.findViewById(R.id.site_icon);
            TextView title = item.findViewById(R.id.site_title);
            if (icon != null) icon.setImageBitmap(generateLetterIcon(site.domain));
            if (title != null) title.setText(site.title);
            item.setOnClickListener(v -> {
                if (callback != null) callback.onSiteClicked(site.url);
            });
            item.setOnLongClickListener(v -> {
                // TODO: 弹出"固定/删除"菜单
                return true;
            });
            container.addView(item);
        }
    }

    /** 生成首字母图标（无 favicon 时的占位） */
    @NonNull
    private Bitmap generateLetterIcon(@NonNull String domain) {
        int size = (int) (48 * context.getResources().getDisplayMetrics().density);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        // 背景色基于域名 hash
        int hash = Math.abs(domain.hashCode());
        int hue = hash % 360;
        float[] hsv = { hue, 0.45f, 0.55f };
        int bgColor = Color.HSVToColor(hsv);
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(bgColor);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint);
        // 首字母
        String letter = domain.substring(0, 1).toUpperCase();
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(size * 0.5f);
        textPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        Rect bounds = new Rect();
        textPaint.getTextBounds(letter, 0, letter.length(), bounds);
        canvas.drawText(letter, size / 2f, size / 2f + bounds.height() / 2f, textPaint);
        return bmp;
    }

    public void destroy() {
        destroyed = true;
        // P0 内存泄漏修复：移除所有待执行的 mainHandler 回调，
        // 避免 AsyncTask 完成后仍通过 stale 引用操作已释放的 View。
        mainHandler.removeCallbacksAndMessages(null);
        callback = null;
        rootView = null;
        gridContainer = null;
        cardsContainer = null;
        minimalContainer = null;
    }

    /** 站点条目数据 */
    public static class SiteEntry {
        public final String url;
        public final String title;
        public final String domain;
        public final int visitCount;
        SiteEntry(String url, String title, String domain, int visitCount) {
            this.url = url; this.title = title; this.domain = domain; this.visitCount = visitCount;
        }
    }
}
