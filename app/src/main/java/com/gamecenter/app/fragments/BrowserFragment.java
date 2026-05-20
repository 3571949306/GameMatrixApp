package com.gamecenter.app.fragments;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import androidx.core.content.ContextCompat;
import android.net.http.SslError;
import android.os.Build;
import android.util.Log;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebBackForwardList;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.URLUtil;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 浏览器 Fragment — 多标签页 WebView + 底部导航 + 搜索引擎(默认百度) + 搜索建议 + 书签/历史/下载。
 * <p>
 * 核心职责：
 * <ul>
 *   <li>多标签页管理：创建、切换、关闭标签页，支持状态保存与恢复</li>
 *   <li>地址栏智能输入：自动判断 URL 或搜索关键词，支持搜索建议</li>
 *   <li>书签与历史记录：本地持久化，支持添加/删除/查看</li>
 *   <li>文件下载：通过系统 DownloadManager 处理，支持 Cookie 传递</li>
 *   <li>桌面/移动模式切换：通过切换 User-Agent 实现</li>
 * </ul>
 * </p>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>默认标签使用公开搜索入口，避免把个人服务地址写入公开仓库</li>
 *   <li>SSL 错误直接放行（handler.proceed()），适合工具类应用但生产应用需谨慎</li>
 *   <li>搜索建议仅百度引擎支持在线获取，其他引擎回退到本地书签/历史匹配</li>
 *   <li>历史记录使用有序 JSON 存储，最多保留 100 条，去重后最新的排在末尾</li>
 *   <li>标签页状态通过 WebView.saveState/restoreState 保存，支持配置变更后恢复</li>
 * </ul>
 * </p>
 */
public class BrowserFragment extends Fragment {

    private static final String TAG = "BrowserFragment";

    /** 移动端 User-Agent 字符串 */
    private static final String MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";
    /** 桌面端 User-Agent 字符串 */
    private static final String DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    /** 可选搜索引擎名称 */
    private static final String[] SEARCH_ENGINES = {"百度", "Google", "Bing"};
    /** 各搜索引擎对应的搜索 URL 前缀 */
    private static final String[] SEARCH_URLS = {
            "https://www.baidu.com/s?wd=",
            "https://www.google.com/search?q=",
            "https://www.bing.com/search?q="
    };
    /** 搜索建议最大显示条数 */
    private static final int MAX_SUGGESTIONS = 5;

    private WebView webView;
    private ProgressBar progressBar;
    private ProgressBar loadingSpinner;
    private EditText etUrl;
    private ImageButton btnBack;
    private ImageButton btnForward;
    private ImageButton btnHome;
    private ImageButton btnRefresh;
    private ImageButton btnHistory;
    private ImageButton btnBookmark;
    private ImageButton btnDesktop;
    private ImageButton btnSearchEngine;
    private ImageButton btnTabs;
    private TextView tvTabCount;
    private RecyclerView rvSuggestions;
    private LinearLayout tabContainer;
    private LinearLayout emptyState;
    private MaterialButton btnNewTab;
    private HorizontalScrollView tabScrollView;

    /** 所有标签页数据列表 */
    private List<TabInfo> tabs;
    /** 当前激活的标签页索引，-1 表示无激活标签 */
    private int currentTabIndex = -1;
    /** 主页 URL */
    private String homeUrl = "https://www.baidu.com";
    /** 第二主页 URL */
    private String secondHomeUrl = "https://www.bing.com";
    /** 是否为桌面模式 */
    private boolean desktopMode = false;

    /** 浏览历史 SharedPreferences */
    private SharedPreferences historyPrefs;
    /** 书签 SharedPreferences */
    private SharedPreferences bookmarkPrefs;
    /** 浏览器设置 SharedPreferences */
    private SharedPreferences settingsPrefs;
    /** 书签 URL 集合 */
    private Set<String> bookmarkSet = new HashSet<>();
    /** 当前选中的搜索引擎索引 */
    private int searchEngineIndex = 0;
    /** 异步任务线程池 */
    private ExecutorService executor;

    /** 搜索建议延迟处理器 */
    private Handler suggestHandler = new Handler();
    /** 待执行的搜索建议请求 */
    private Runnable suggestRunnable;
    /** 搜索建议列表适配器 */
    private SuggestionAdapter suggestionAdapter;
    /** 当前搜索建议数据 */
    private List<String> suggestions = new ArrayList<>();
    /** 最新的搜索建议查询词，用于防止过期结果覆盖新结果 */
    private String latestSuggestionQuery = "";
    /** 是否抑制地址栏文本变化事件，避免程序性设值触发搜索建议 */
    private boolean suppressAddressTextEvents = false;

    public BrowserFragment() {
        super(R.layout.fragment_browser);
    }

    /**
     * 视图创建完成后的初始化入口。
     * <p>
     * 依次初始化 WebView、地址栏、底部导航、操作按钮、标签页和搜索建议。
     * 如果有保存的状态则恢复标签页，否则创建默认标签。
     * </p>
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        executor = Executors.newSingleThreadExecutor();

        webView = view.findViewById(R.id.webview);
        progressBar = view.findViewById(R.id.browser_progress);
        loadingSpinner = view.findViewById(R.id.loading_spinner);
        etUrl = view.findViewById(R.id.et_url);
        btnBack = view.findViewById(R.id.btn_back);
        btnForward = view.findViewById(R.id.btn_forward);
        btnHome = view.findViewById(R.id.btn_home);
        btnRefresh = view.findViewById(R.id.btn_refresh);
        btnBookmark = view.findViewById(R.id.btn_bookmark);
        btnDesktop = view.findViewById(R.id.btn_desktop_mode);
        btnSearchEngine = view.findViewById(R.id.btn_search_engine);
        btnTabs = view.findViewById(R.id.btn_tabs);
        tvTabCount = view.findViewById(R.id.tv_tab_count);
        btnHistory = view.findViewById(R.id.btn_history);
        rvSuggestions = view.findViewById(R.id.rv_suggestions);
        tabContainer = view.findViewById(R.id.tab_container);
        emptyState = view.findViewById(R.id.empty_state);
        btnNewTab = view.findViewById(R.id.btn_new_tab);
        tabScrollView = view.findViewById(R.id.tab_scroll_view);

        historyPrefs = requireContext().getSharedPreferences("browser_history_v2", Context.MODE_PRIVATE);
        bookmarkPrefs = requireContext().getSharedPreferences("browser_bookmarks", Context.MODE_PRIVATE);
        settingsPrefs = requireContext().getSharedPreferences("browser_settings", Context.MODE_PRIVATE);
        searchEngineIndex = settingsPrefs.getInt("search_engine", 0);
        loadBookmarks();

        tabs = new ArrayList<>();
        restoreTabs(savedInstanceState);

        configureWebView();
        setupUrlInput();
        setupAddressFocus();
        setupBottomBar();
        setupActionButtons();
        setupNewTabButton();
        setupSuggestions();
        setupUrlTextWatcher();

        if (tabs.isEmpty()) {
            createDefaultTabs();
        } else {
            // 恢复已保存的标签页 UI
            int restoredIndex = Math.max(0, Math.min(currentTabIndex, tabs.size() - 1));
            for (int i = 0; i < tabs.size(); i++) {
                tabContainer.addView(createTabButton(i), i);
            }
            currentTabIndex = -1;
            switchToTab(restoredIndex);
            scrollToEnd();
        }
        updateUI();
    }

    /**
     * 配置 WebView 的各项设置和客户端回调。
     * <p>
     * 包括 JavaScript 启用、DOM 存储、缩放控制、UA 切换、Cookie 策略，
     * 以及页面加载生命周期回调（开始/完成/SSL 错误/下载/物理返回键）。
     * </p>
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUserAgentString(desktopMode ? DESKTOP_UA : MOBILE_UA);

        // 允许混合内容，部分 HTTP 资源在 HTTPS 页面中加载
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // 直接放行 SSL 错误，适合工具类应用
                handler.proceed();
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
                loadingSpinner.setVisibility(View.VISIBLE);
                updateAddressFromPage(url);
                updateCurrentTabUrl(url);
                updateNavButtons();
                updateBookmarkIcon();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                loadingSpinner.setVisibility(View.GONE);
                updateAddressFromPage(url);
                updateCurrentTabUrl(url);
                updateNavButtons();
                updateBookmarkIcon();
                addToHistory(url);
                saveCurrentTabState();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                view.loadUrl(request.getUrl().toString());
                return true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                progressBar.setProgress(newProgress);
                if (newProgress >= 100) progressBar.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                // 更新标签页标题，忽略以 http 开头的标题（通常是 URL 而非真实标题）
                if (currentTabIndex >= 0 && title != null && !title.isEmpty()
                        && !title.startsWith("http")) {
                    tabs.get(currentTabIndex).title = title;
                    updateTabButton(currentTabIndex);
                    saveCurrentTabState();
                }
            }
        });

        // 文件下载监听
        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            startDownload(url, userAgent, contentDisposition, mimetype);
        });

        // 物理返回键处理：在 WebView 可后退时拦截
        webView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
                webView.goBack();
                return true;
            }
            return false;
        });
    }

    /**
     * 处理文件下载请求。
     * <p>
     * 优先使用系统 DownloadManager 下载；若 URL 非 HTTP 或 DownloadManager 不可用，
     * 则回退到外部浏览器打开。下载请求会携带当前 URL 的 Cookie 和原始 User-Agent。
     * </p>
     *
     * @param url                下载链接
     * @param userAgent          原始 User-Agent
     * @param contentDisposition Content-Disposition 头
     * @param mimetype           MIME 类型
     */
    private void startDownload(String url, String userAgent, String contentDisposition, String mimetype) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            openExternalUrl(url);
            return;
        }

        try {
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(fileName);
            request.setDescription("正在下载");
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            if (mimetype != null && !mimetype.isEmpty()) {
                request.setMimeType(mimetype);
            }
            // 传递原始 User-Agent 和 Cookie 以支持需要认证的下载
            if (userAgent != null && !userAgent.isEmpty()) {
                request.addRequestHeader("User-Agent", userAgent);
            }
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null && !cookies.isEmpty()) {
                request.addRequestHeader("Cookie", cookies);
            }

            DownloadManager manager = (DownloadManager)
                    requireContext().getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager == null) {
                openExternalUrl(url);
                return;
            }
            manager.enqueue(request);
            Toast.makeText(getContext(), "已开始下载: " + fileName, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            openExternalUrl(url);
        }
    }

    /**
     * 使用外部浏览器打开 URL。
     *
     * @param url 要打开的链接
     */
    private void openExternalUrl(String url) {
        try {
            if (url == null || url.isEmpty()) throw new IllegalArgumentException("empty url");
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(getContext(), "无法处理下载链接", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 创建默认的两个标签页（百度和 Bing）。
     */
    private void createDefaultTabs() {
        TabInfo tab1 = new TabInfo("百度", homeUrl);
        tabs.add(tab1);
        tabContainer.addView(createTabButton(0), 0);

        TabInfo tab2 = new TabInfo("Bing", secondHomeUrl);
        tabs.add(tab2);
        tabContainer.addView(createTabButton(1), 1);

        currentTabIndex = 0;
        updateAllTabStyles();
        switchToTab(0);
        scrollToEnd();
    }

    // ── 地址栏输入 ──

    /**
     * 设置地址栏焦点行为：获取焦点时全选文本，失去焦点时隐藏搜索建议。
     */
    private void setupAddressFocus() {
        etUrl.setSelectAllOnFocus(true);
        etUrl.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                etUrl.selectAll();
            } else {
                hideSuggestions();
            }
        });
        etUrl.setOnClickListener(v -> etUrl.selectAll());
    }

    /**
     * 设置地址栏的回车键监听，按下回车时加载输入内容。
     */
    private void setupUrlInput() {
        etUrl.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String input = etUrl.getText().toString().trim();
                if (!input.isEmpty()) {
                    loadUrlInCurrentTab(processInput(input));
                }
                rvSuggestions.setVisibility(View.GONE);
                etUrl.clearFocus();
                return true;
            }
            return false;
        });
    }

    /**
     * 设置地址栏文本变化监听，输入时延迟获取搜索建议。
     * <p>
     * 使用 300ms 延迟避免频繁请求；如果输入看起来像 URL 则不触发搜索建议。
     * </p>
     */
    private void setupUrlTextWatcher() {
        etUrl.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                // 程序性设值或无焦点时不触发搜索建议
                if (suppressAddressTextEvents || !etUrl.hasFocus()) {
                    return;
                }
                String input = s.toString().trim();
                removePendingSuggestion();
                // 空输入、内部空白页或看起来像 URL 的输入不触发搜索建议
                if (input.isEmpty() || isInternalBlankUrl(input) || isLikelyUrl(input)) {
                    hideSuggestions();
                    return;
                }
                suggestRunnable = () -> fetchSuggestions(input);
                suggestHandler.postDelayed(suggestRunnable, 300);
            }
        });
    }

    /**
     * 判断输入是否看起来像 URL（而非搜索关键词）。
     *
     * @param input 用户输入
     * @return 如果是 URL 则返回 true
     */
    private boolean isLikelyUrl(String input) {
        return input.startsWith("http://") || input.startsWith("https://")
                || input.startsWith("about:")
                || (input.contains(".") && !input.contains(" "));
    }

    /**
     * 判断是否为内部空白页 URL。
     *
     * @param input 用户输入
     * @return 如果是 about:blank 则返回 true
     */
    private boolean isInternalBlankUrl(String input) {
        return "about:blank".equalsIgnoreCase(input);
    }

    /**
     * 静默设置地址栏文本，不触发搜索建议。
     *
     * @param text 要设置的文本
     */
    private void setAddressTextSilently(String text) {
        suppressAddressTextEvents = true;
        try {
            etUrl.setText(text == null ? "" : text);
        } finally {
            suppressAddressTextEvents = false;
        }
    }

    /**
     * 从页面加载事件更新地址栏文本。
     * <p>
     * 仅在地址栏无焦点时更新，避免覆盖用户正在输入的内容。
     * </p>
     *
     * @param url 页面 URL
     */
    private void updateAddressFromPage(String url) {
        if (url == null || isInternalBlankUrl(url) || etUrl.hasFocus()) return;
        setAddressTextSilently(url);
    }

    /**
     * 取消待执行的搜索建议请求。
     */
    private void removePendingSuggestion() {
        if (suggestRunnable != null) {
            suggestHandler.removeCallbacks(suggestRunnable);
        }
    }

    /**
     * 隐藏搜索建议列表并清空数据。
     */
    private void hideSuggestions() {
        removePendingSuggestion();
        latestSuggestionQuery = "";
        suggestions.clear();
        if (suggestionAdapter != null) {
            suggestionAdapter.notifyDataSetChanged();
        }
        if (rvSuggestions != null) {
            rvSuggestions.setVisibility(View.GONE);
        }
    }

    /**
     * 使用当前搜索引擎生成搜索 URL。
     *
     * @param query 搜索关键词
     * @return 编码后的搜索 URL
     */
    private String getSearchUrl(String query) {
        try {
            return SEARCH_URLS[searchEngineIndex] + URLEncoder.encode(query, "UTF-8");
        } catch (Exception e) {
            return SEARCH_URLS[searchEngineIndex] + query;
        }
    }

    /**
     * 处理用户输入，判断是 URL 还是搜索关键词。
     * <p>
     * 规则：http(s):// 开头直接使用；含点且无空格补 https://；否则视为搜索关键词。
     * </p>
     *
     * @param input 用户输入
     * @return 处理后的 URL
     */
    private String processInput(String input) {
        if (input.startsWith("http://") || input.startsWith("https://")) return input;
        if (input.contains(".") && !input.contains(" ")) return "https://" + input;
        return getSearchUrl(input);
    }

    /**
     * 在当前标签页中加载 URL。
     *
     * @param url 要加载的 URL
     */
    private void loadUrlInCurrentTab(String url) {
        if (currentTabIndex >= 0) {
            tabs.get(currentTabIndex).url = url;
            tabs.get(currentTabIndex).title = url;
            tabs.get(currentTabIndex).state = null;
            updateTabButton(currentTabIndex);
        }
        webView.loadUrl(url);
        emptyState.setVisibility(View.GONE);
        updateBookmarkIcon();
    }

    // ── 书签 ──

    /**
     * 从 SharedPreferences 加载书签集合。
     */
    private void loadBookmarks() {
        bookmarkSet = new HashSet<>(bookmarkPrefs.getStringSet("urls", new HashSet<>()));
    }

    /**
     * 将书签集合持久化到 SharedPreferences。
     */
    private void saveBookmarks() {
        bookmarkPrefs.edit().putStringSet("urls", new HashSet<>(bookmarkSet)).apply();
    }

    /**
     * 切换当前页面的书签状态。
     */
    private void toggleBookmark() {
        if (currentTabIndex < 0) return;
        String url = tabs.get(currentTabIndex).url;
        if (url == null || url.isEmpty()) return;

        if (bookmarkSet.contains(url)) {
            bookmarkSet.remove(url);
            Toast.makeText(getContext(), "已取消收藏", Toast.LENGTH_SHORT).show();
        } else {
            bookmarkSet.add(url);
            Toast.makeText(getContext(), "已添加书签", Toast.LENGTH_SHORT).show();
        }
        saveBookmarks();
        updateBookmarkIcon();
    }

    /**
     * 根据当前页面是否已收藏更新书签按钮图标。
     */
    private void updateBookmarkIcon() {
        if (currentTabIndex < 0) { btnBookmark.setImageResource(R.drawable.ic_star_border); return; }
        btnBookmark.setImageResource(
                bookmarkSet.contains(tabs.get(currentTabIndex).url)
                        ? R.drawable.ic_star_filled : R.drawable.ic_star_border);
    }

    /**
     * 显示书签列表对话框，点击书签可在当前标签页打开。
     */
    private void showBookmarksDialog() {
        if (bookmarkSet.isEmpty()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("书签").setMessage("暂无书签").setPositiveButton("确定", null).show();
            return;
        }
        List<String> list = new ArrayList<>(bookmarkSet);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("书签")
                .setItems(list.toArray(new String[0]), (d, w) -> loadUrlInCurrentTab(list.get(w)))
                .setNeutralButton("清除全部", (d, w) -> { bookmarkSet.clear(); saveBookmarks(); })
                .setNegativeButton("取消", null).show();
    }

    // ── 历史记录（有序 JSON）──

    /**
     * 将访问的 URL 添加到历史记录。
     * <p>
     * 去重处理：如果 URL 已存在则移除旧的记录，将新记录追加到末尾。
     * 历史记录上限 100 条，超出时裁剪最早的记录。
     * </p>
     *
     * @param url 访问的 URL
     */
    private void addToHistory(String url) {
        if (url == null || url.isEmpty() || url.equals("about:blank")) return;
        try {
            JSONArray arr = new JSONArray(historyPrefs.getString("list", "[]"));
            JSONArray newArr = new JSONArray();
            // 去重：移除已有的相同 URL 记录
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (!url.equals(obj.optString("url", ""))) newArr.put(obj);
            }
            JSONObject entry = new JSONObject();
            entry.put("url", url);
            entry.put("ts", System.currentTimeMillis());
            newArr.put(entry);
            // 超过 100 条时裁剪最早的记录
            if (newArr.length() > 100) {
                JSONArray trimmed = new JSONArray();
                for (int i = newArr.length() - 100; i < newArr.length(); i++)
                    trimmed.put(newArr.get(i));
                newArr = trimmed;
            }
            historyPrefs.edit().putString("list", newArr.toString()).apply();
        } catch (Exception ignored) { Log.w(TAG, "Save history: " + ignored.getMessage()); }
    }

    /**
     * 显示历史记录对话框，按时间倒序展示，点击可在当前标签页打开。
     */
    private void showHistoryDialog() {
        try {
            JSONArray arr = new JSONArray(historyPrefs.getString("list", "[]"));
            if (arr.length() == 0) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("历史记录").setMessage("暂无").setPositiveButton("确定", null).show();
                return;
            }
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            List<String> display = new ArrayList<>();
            List<String> urls = new ArrayList<>();
            // 倒序遍历，最新的显示在最前面
            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject obj = arr.getJSONObject(i);
                String url = obj.optString("url", "");
                long ts = obj.optLong("ts", 0);
                urls.add(url);
                display.add((ts > 0 ? sdf.format(new Date(ts)) + " " : "") + url);
            }
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("历史记录")
                    .setItems(display.toArray(new String[0]), (d, w) -> loadUrlInCurrentTab(urls.get(w)))
                    .setNegativeButton("取消", null)
                    .setNeutralButton("清除历史", (d, w) ->
                            historyPrefs.edit().putString("list", "[]").apply()).show();
        } catch (Exception e) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("历史记录").setMessage("暂无").setPositiveButton("确定", null).show();
        }
    }

    // ── 搜索引擎选择 ──

    /**
     * 显示搜索引擎选择对话框，选择后持久化到设置。
     */
    private void showSearchEngineDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("选择搜索引擎")
                .setSingleChoiceItems(SEARCH_ENGINES, searchEngineIndex, (d, w) -> {
                    searchEngineIndex = w;
                    settingsPrefs.edit().putInt("search_engine", w).apply();
                    Toast.makeText(getContext(), "已切换: " + SEARCH_ENGINES[w], Toast.LENGTH_SHORT).show();
                    d.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // ── 搜索建议 ──

    /**
     * 初始化搜索建议 RecyclerView。
     */
    private void setupSuggestions() {
        suggestionAdapter = new SuggestionAdapter();
        rvSuggestions.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvSuggestions.setAdapter(suggestionAdapter);
    }

    /**
     * 获取搜索建议。
     * <p>
     * 百度引擎使用在线 API 获取建议；其他引擎回退到本地书签/历史匹配。
     * 使用 latestSuggestionQuery 防止过期结果覆盖新结果。
     * </p>
     *
     * @param query 搜索关键词
     */
    private void fetchSuggestions(String query) {
        latestSuggestionQuery = query;
        // 非百度引擎直接使用本地建议
        if (searchEngineIndex != 0) {
            showLocalSuggestions(query);
            return;
        }

        executor.execute(() -> {
            try {
                String encoded = URLEncoder.encode(query, "UTF-8");
                String apiUrl = "https://suggestion.baidu.com/su?wd=" + encoded + "&json=1&p=3";
                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);
                conn.setRequestProperty("User-Agent", MOBILE_UA);
                // 百度建议 API 返回 GBK 编码
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "GBK"));
                StringBuilder resp = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) resp.append(line);
                reader.close();
                conn.disconnect();

                // 解析百度建议 API 的非标准 JSON 响应
                String raw = resp.toString();
                int start = raw.indexOf("\"s\":[");
                if (start < 0) return;
                start += 5;
                int end = raw.indexOf("]", start);
                if (end < 0) return;
                String[] items = raw.substring(start, end).replace("\"", "").split(",");

                final List<String> result = new ArrayList<>();
                for (String item : items) {
                    String trimmed = item.trim();
                    if (!trimmed.isEmpty() && result.size() < MAX_SUGGESTIONS) {
                        result.add(trimmed);
                    }
                }

                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    // 防止过期结果覆盖新结果
                    if (!query.equals(latestSuggestionQuery)) return;
                    if (result.isEmpty()) {
                        showLocalSuggestions(query);
                    } else {
                        suggestions.clear();
                        suggestions.addAll(result);
                        suggestionAdapter.notifyDataSetChanged();
                        rvSuggestions.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> showLocalSuggestions(query));
            }
        });
    }

    /**
     * 从本地书签和历史记录中匹配搜索建议。
     *
     * @param query 搜索关键词（不区分大小写）
     */
    private void showLocalSuggestions(String query) {
        if (!isAdded() || !query.equals(latestSuggestionQuery)) return;

        String lower = query.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        // 先从书签中匹配
        for (String bookmark : bookmarkSet) {
            if (bookmark.toLowerCase(Locale.ROOT).contains(lower)) {
                result.add(bookmark);
                if (result.size() >= MAX_SUGGESTIONS) break;
            }
        }

        // 再从历史记录中补充匹配
        try {
            JSONArray arr = new JSONArray(historyPrefs.getString("list", "[]"));
            for (int i = arr.length() - 1; i >= 0 && result.size() < MAX_SUGGESTIONS; i--) {
                String url = arr.getJSONObject(i).optString("url", "");
                if (!url.isEmpty() && url.toLowerCase(Locale.ROOT).contains(lower)
                        && !result.contains(url)) {
                    result.add(url);
                }
            }
        } catch (Exception ignored) { Log.w(TAG, "Search suggestions: " + ignored.getMessage()); }

        suggestions.clear();
        suggestions.addAll(result);
        suggestionAdapter.notifyDataSetChanged();
        rvSuggestions.setVisibility(result.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /**
     * 搜索建议列表适配器。
     */
    private class SuggestionAdapter extends RecyclerView.Adapter<SuggestionAdapter.VH> {
        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView tv = new TextView(parent.getContext());
            tv.setPadding(40, 24, 40, 24);
            tv.setTextSize(14);
            try {
                tv.setTextColor(ContextCompat.getColor(parent.getContext(),
                        android.R.color.primary_text_dark));
            } catch (Exception e) {
                tv.setTextColor(0xFF000000);
            }
            tv.setBackgroundResource(android.R.drawable.list_selector_background);
            return new VH(tv);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int pos) {
            holder.tv.setText(suggestions.get(pos));
            holder.tv.setOnClickListener(v -> {
                String kw = suggestions.get(pos);
                loadUrlInCurrentTab(processInput(kw));
                hideSuggestions();
                etUrl.clearFocus();
            });
        }

        @Override public int getItemCount() { return Math.min(suggestions.size(), MAX_SUGGESTIONS); }

        /** 搜索建议 ViewHolder */
        class VH extends RecyclerView.ViewHolder {
            TextView tv;
            VH(@NonNull View itemView) { super(itemView); tv = (TextView) itemView; }
        }
    }

    // ── 顶部操作按钮 ──

    /**
     * 设置顶部操作按钮的点击事件：书签、桌面模式、刷新、历史、搜索引擎。
     */
    private void setupActionButtons() {
        btnBookmark.setOnClickListener(v -> toggleBookmark());
        btnBookmark.setOnLongClickListener(v -> { showBookmarksDialog(); return true; });

        btnDesktop.setOnClickListener(v -> {
            desktopMode = !desktopMode;
            webView.getSettings().setUserAgentString(desktopMode ? DESKTOP_UA : MOBILE_UA);
            Toast.makeText(getContext(),
                    desktopMode ? "桌面版" : "手机版", Toast.LENGTH_SHORT).show();
            webView.reload();
        });

        btnRefresh.setOnClickListener(v -> {
            progressBar.setVisibility(View.VISIBLE);
            loadingSpinner.setVisibility(View.VISIBLE);
            webView.reload();
        });

        btnHistory.setOnClickListener(v -> showHistoryDialog());
        btnSearchEngine.setOnClickListener(v -> showSearchEngineDialog());
    }

    // ── 底部导航栏 ──

    /**
     * 设置底部导航栏按钮事件：后退、前进、主页、标签页管理。
     */
    private void setupBottomBar() {
        btnBack.setOnClickListener(v -> { if (webView.canGoBack()) webView.goBack(); });
        btnForward.setOnClickListener(v -> { if (webView.canGoForward()) webView.goForward(); });
        btnHome.setOnClickListener(v -> {
            if (currentTabIndex >= 0) {
                webView.loadUrl(homeUrl);
                tabs.get(currentTabIndex).url = homeUrl;
                tabs.get(currentTabIndex).title = "百度";
                updateTabButton(currentTabIndex);
            }
        });
        btnTabs.setOnClickListener(v -> showTabsDialog());
        updateNavButtons();
    }

    /**
     * 显示标签页管理对话框，支持切换、新建和关闭标签页。
     */
    private void showTabsDialog() {
        if (tabs.isEmpty()) return;
        String[] items = new String[tabs.size()];
        for (int i = 0; i < tabs.size(); i++) {
            String t = tabs.get(i).title;
            items[i] = (i == currentTabIndex ? "✓ " : "  ") + (t != null ? t : "空白页");
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("标签页 (" + tabs.size() + ")")
                .setItems(items, (d, w) -> switchToTab(w))
                .setPositiveButton("新建标签", (d, w) -> createNewTab())
                .setNeutralButton("关闭当前", (d, w) -> {
                    if (tabs.size() > 1) {
                        closeTab(currentTabIndex);
                    } else {
                        Toast.makeText(getContext(), "至少保留一个标签页", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    /**
     * 设置新建标签页按钮事件。
     */
    private void setupNewTabButton() {
        btnNewTab.setOnClickListener(v -> createNewTab());
    }

    // ── 标签页管理 ──

    /**
     * 创建新标签页并切换到该标签。
     */
    private void createNewTab() {
        int newIndex = tabs.size();
        TabInfo newTab = new TabInfo("新标签页", homeUrl);
        tabs.add(newTab);
        tabContainer.addView(createTabButton(newIndex), newIndex);
        switchToTab(newIndex);
        scrollToEnd();
    }

    /**
     * 创建标签页按钮视图，包含标题按钮和关闭按钮。
     *
     * @param index 标签页索引
     * @return 标签页按钮视图
     */
    private View createTabButton(int index) {
        LinearLayout tabLayout = new LinearLayout(requireContext());
        tabLayout.setOrientation(LinearLayout.HORIZONTAL);
        tabLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        tabLayout.setTag(index);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(36));
        params.setMargins(4, 0, 4, 0);
        tabLayout.setLayoutParams(params);

        MaterialButton titleButton = new MaterialButton(requireContext());
        titleButton.setId(View.generateViewId());
        titleButton.setText(tabs.get(index).title);
        titleButton.setTextSize(12);
        titleButton.setMinWidth(0);
        titleButton.setMaxWidth(dp(160));
        titleButton.setSingleLine(true);
        titleButton.setEllipsize(TextUtils.TruncateAt.END);
        titleButton.setPadding(dp(12), 0, dp(8), 0);
        titleButton.setTag(index);
        titleButton.setOnClickListener(v -> switchToTab((int) v.getTag()));
        // 长按标题按钮关闭标签
        titleButton.setOnLongClickListener(v -> {
            int tabIndex = (int) v.getTag();
            if (tabs.size() > 1) closeTab(tabIndex);
            return true;
        });

        ImageButton closeButton = new ImageButton(requireContext());
        closeButton.setImageResource(R.drawable.ic_close);
        closeButton.setBackgroundResource(android.R.drawable.list_selector_background);
        closeButton.setContentDescription("关闭标签");
        closeButton.setPadding(dp(6), dp(6), dp(6), dp(6));
        closeButton.setTag(index);
        closeButton.setOnClickListener(v -> {
            int tabIndex = (int) v.getTag();
            if (tabs.size() > 1) {
                closeTab(tabIndex);
            } else {
                Toast.makeText(getContext(), "至少保留一个标签页", Toast.LENGTH_SHORT).show();
            }
        });

        tabLayout.addView(titleButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));
        tabLayout.addView(closeButton, new LinearLayout.LayoutParams(dp(32), dp(32)));
        tabLayout.setOnClickListener(v -> switchToTab((int) v.getTag()));
        return tabLayout;
    }

    /**
     * 切换到指定标签页。
     * <p>
     * 保存当前标签页状态，加载目标标签页的 URL 或恢复其 WebView 状态。
     * 切换时先加载 about:blank 并清除 WebView 历史，避免标签页间历史混淆。
     * </p>
     *
     * @param index 目标标签页索引
     */
    private void switchToTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        if (currentTabIndex != index) {
            saveCurrentTabState();
        }
        hideSuggestions();
        etUrl.clearFocus();
        TabInfo tab = tabs.get(index);
        currentTabIndex = index;
        // 先加载空白页并清除历史，防止标签页间的浏览历史互相污染
        webView.stopLoading();
        webView.loadUrl("about:blank");
        webView.clearHistory();

        if (tab.state != null) {
            // 尝试恢复保存的 WebView 状态
            WebBackForwardList restored = webView.restoreState(tab.state);
            if (restored == null && !tab.url.isEmpty()) {
                webView.loadUrl(tab.url);
            }
            emptyState.setVisibility(View.GONE);
            setAddressTextSilently(tab.url);
        } else if (tab.url.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            setAddressTextSilently("");
        } else {
            webView.loadUrl(tab.url);
            emptyState.setVisibility(View.GONE);
            setAddressTextSilently(tab.url);
        }

        updateAllTabStyles();
        updateNavButtons();
        updateBookmarkIcon();
    }

    /**
     * 关闭指定标签页。
     * <p>
     * 至少保留一个标签页。关闭后更新所有标签页的索引和 UI。
     * </p>
     *
     * @param index 要关闭的标签页索引
     */
    private void closeTab(int index) {
        if (index < 0 || index >= tabs.size() || tabs.size() <= 1) return;
        boolean closingCurrent = index == currentTabIndex;
        if (!closingCurrent) {
            saveCurrentTabState();
        }
        tabs.remove(index);
        tabContainer.removeViewAt(index);

        // 更新 currentTabIndex：确保指向有效的标签页
        if (currentTabIndex >= tabs.size()) currentTabIndex = tabs.size() - 1;
        else if (currentTabIndex > index) currentTabIndex--;
        else if (currentTabIndex == index) currentTabIndex = Math.min(index, tabs.size() - 1);

        // 更新所有标签页视图的索引 tag
        for (int i = 0; i < tabs.size(); i++) {
            View tabView = tabContainer.getChildAt(i);
            updateTabViewIndex(tabView, i);
        }
        updateTabCount();
        switchToTab(currentTabIndex);
    }

    /**
     * 更新所有标签页按钮的样式，当前激活标签高亮显示。
     */
    private void updateAllTabStyles() {
        int activeBg, activeText, inactiveText;
        try {
            activeBg = resolveThemeColor(com.google.android.material.R.attr.colorPrimaryContainer);
            activeText = resolveThemeColor(com.google.android.material.R.attr.colorOnPrimaryContainer);
            inactiveText = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface);
        } catch (Exception e) {
            // 主题属性解析失败时使用默认颜色
            activeBg = 0xFFE0E0E0;
            activeText = 0xFF000000;
            inactiveText = 0xFF666666;
        }

        for (int i = 0; i < tabs.size() && i < tabContainer.getChildCount(); i++) {
            View tabView = tabContainer.getChildAt(i);
            MaterialButton button = getTabTitleButton(tabView);
            ImageButton closeButton = getTabCloseButton(tabView);
            if (button != null) {
                if (i == currentTabIndex) {
                    button.setBackgroundColor(activeBg);
                    button.setTextColor(activeText);
                    if (closeButton != null) closeButton.setAlpha(1.0f);
                } else {
                    button.setBackgroundColor(Color.TRANSPARENT);
                    button.setTextColor(inactiveText);
                    if (closeButton != null) closeButton.setAlpha(0.65f);
                }
            }
        }
        updateTabCount();
    }

    /**
     * 将标签页滚动条滚动到最右端（最新标签位置）。
     */
    private void scrollToEnd() {
        tabScrollView.post(() -> tabScrollView.fullScroll(View.FOCUS_RIGHT));
    }

    /**
     * 解析主题属性对应的颜色值。
     *
     * @param attr 主题属性 ID
     * @return 颜色值
     */
    private int resolveThemeColor(int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    /**
     * 根据 WebView 的前进/后退状态更新导航按钮的可用性和透明度。
     */
    private void updateNavButtons() {
        btnBack.setEnabled(webView.canGoBack());
        btnBack.setAlpha(webView.canGoBack() ? 1.0f : 0.4f);
        btnForward.setEnabled(webView.canGoForward());
        btnForward.setAlpha(webView.canGoForward() ? 1.0f : 0.4f);
    }

    /**
     * 更新整体 UI 状态（空状态和标签计数）。
     */
    private void updateUI() {
        emptyState.setVisibility((currentTabIndex < 0 || tabs.isEmpty()) ? View.VISIBLE : View.GONE);
        updateTabCount();
    }

    /**
     * 更新指定标签页按钮的标题文本。
     *
     * @param index 标签页索引
     */
    private void updateTabButton(int index) {
        if (index >= 0 && index < tabContainer.getChildCount()) {
            View tabView = tabContainer.getChildAt(index);
            MaterialButton titleButton = getTabTitleButton(tabView);
            if (titleButton != null) {
                titleButton.setText(tabs.get(index).title);
            }
        }
    }

    /**
     * 更新标签页视图中所有子视图的索引 tag。
     *
     * @param tabView 标签页视图
     * @param index   新的索引值
     */
    private void updateTabViewIndex(View tabView, int index) {
        if (tabView == null) return;
        tabView.setTag(index);
        MaterialButton titleButton = getTabTitleButton(tabView);
        ImageButton closeButton = getTabCloseButton(tabView);
        if (titleButton != null) titleButton.setTag(index);
        if (closeButton != null) closeButton.setTag(index);
    }

    /**
     * 从标签页视图中获取标题按钮。
     *
     * @param tabView 标签页视图
     * @return 标题 MaterialButton，未找到返回 null
     */
    private MaterialButton getTabTitleButton(View tabView) {
        if (tabView instanceof MaterialButton) return (MaterialButton) tabView;
        if (tabView instanceof LinearLayout) {
            LinearLayout layout = (LinearLayout) tabView;
            for (int i = 0; i < layout.getChildCount(); i++) {
                View child = layout.getChildAt(i);
                if (child instanceof MaterialButton) return (MaterialButton) child;
            }
        }
        return null;
    }

    /**
     * 从标签页视图中获取关闭按钮。
     *
     * @param tabView 标签页视图
     * @return 关闭 ImageButton，未找到返回 null
     */
    private ImageButton getTabCloseButton(View tabView) {
        if (tabView instanceof LinearLayout) {
            LinearLayout layout = (LinearLayout) tabView;
            for (int i = 0; i < layout.getChildCount(); i++) {
                View child = layout.getChildAt(i);
                if (child instanceof ImageButton) return (ImageButton) child;
            }
        }
        return null;
    }

    /**
     * 更新标签页计数显示。
     */
    private void updateTabCount() {
        if (tvTabCount == null) return;
        int count = tabs == null ? 0 : tabs.size();
        tvTabCount.setText(String.valueOf(count));
        tvTabCount.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        btnTabs.setContentDescription("标签页 (" + count + ")");
    }

    /**
     * 更新当前标签页的 URL 和标题。
     * <p>
     * 忽略 about:blank 页面；仅当标题为空、以 http 开头或为"新标签页"时更新标题。
     * </p>
     *
     * @param url 页面 URL
     */
    private void updateCurrentTabUrl(String url) {
        if (currentTabIndex < 0 || currentTabIndex >= tabs.size()
                || url == null || "about:blank".equals(url)) {
            return;
        }
        TabInfo tab = tabs.get(currentTabIndex);
        tab.url = url;
        if (tab.title == null || tab.title.isEmpty()
                || tab.title.startsWith("http") || "新标签页".equals(tab.title)) {
            tab.title = url;
            updateTabButton(currentTabIndex);
        }
    }

    /**
     * 保存当前标签页的 WebView 状态（前进/后退历史）。
     * <p>
     * 同时更新标签页的 URL 和标题（优先使用页面真实标题）。
     * </p>
     */
    private void saveCurrentTabState() {
        if (webView == null || currentTabIndex < 0 || currentTabIndex >= tabs.size()) return;

        Bundle state = new Bundle();
        WebBackForwardList history = webView.saveState(state);
        if (history == null || history.getSize() == 0) return;

        // 从 WebView 的前进/后退列表中获取当前页面的 URL 和标题
        WebBackForwardList currentList = webView.copyBackForwardList();
        if (currentList != null && currentList.getCurrentItem() != null) {
            String url = currentList.getCurrentItem().getUrl();
            String title = currentList.getCurrentItem().getTitle();
            if (url != null && !"about:blank".equals(url)) {
                tabs.get(currentTabIndex).url = url;
            }
            // 仅当标题有意义（非 URL 形式）时更新
            if (title != null && !title.isEmpty() && !title.startsWith("http")) {
                tabs.get(currentTabIndex).title = title;
                updateTabButton(currentTabIndex);
            }
        }
        tabs.get(currentTabIndex).state = state;
    }

    /**
     * 将 dp 值转换为像素值。
     *
     * @param value dp 值
     * @return 像素值
     */
    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    // ── 状态保存 ──

    /**
     * 保存 Fragment 状态，包括所有标签页的 URL、标题和 WebView 状态。
     *
     * @param outState 状态 Bundle
     */
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        saveCurrentTabState();
        ArrayList<String> tabUrls = new ArrayList<>();
        ArrayList<String> tabTitles = new ArrayList<>();
        ArrayList<Bundle> tabStates = new ArrayList<>();
        for (TabInfo t : tabs) {
            tabUrls.add(t.url != null ? t.url : "");
            tabTitles.add(t.title != null ? t.title : "");
            tabStates.add(t.state);
        }
        outState.putStringArrayList("tab_urls", tabUrls);
        outState.putStringArrayList("tab_titles", tabTitles);
        outState.putParcelableArrayList("tab_states", tabStates);
        outState.putInt("current_index", currentTabIndex);
        outState.putBoolean("desktop_mode", desktopMode);
    }

    /**
     * 从保存的状态中恢复标签页数据。
     *
     * @param savedInstanceState 保存的状态 Bundle
     */
    private void restoreTabs(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState == null) return;
        ArrayList<String> urls = savedInstanceState.getStringArrayList("tab_urls");
        ArrayList<String> titles = savedInstanceState.getStringArrayList("tab_titles");
        ArrayList<Bundle> states = savedInstanceState.getParcelableArrayList("tab_states");
        int idx = savedInstanceState.getInt("current_index", -1);
        desktopMode = savedInstanceState.getBoolean("desktop_mode", false);

        if (urls != null && titles != null && urls.size() == titles.size()) {
            for (int i = 0; i < urls.size(); i++) {
                TabInfo tab = new TabInfo(titles.get(i), urls.get(i));
                if (states != null && i < states.size()) {
                    tab.state = states.get(i);
                }
                tabs.add(tab);
            }
            currentTabIndex = Math.max(0, Math.min(idx, tabs.size() - 1));
        }
    }

    /**
     * 处理物理返回键：WebView 可后退时后退一步，否则交由上层处理。
     *
     * @return true 表示已处理返回键，false 表示未处理
     */
    public boolean onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return false;
    }

    /**
     * 视图销毁时清理 WebView 和线程池资源，防止内存泄漏。
     */
    @Override
    public void onDestroyView() {
        if (webView != null) {
            webView.stopLoading();
            webView.clearHistory();
            webView.clearCache(true);
            webView.destroy();
            webView = null;
        }
        if (executor != null && !executor.isShutdown()) executor.shutdownNow();
        removePendingSuggestion();
        super.onDestroyView();
    }

    /**
     * 标签页信息数据类，保存单个标签页的标题、URL 和 WebView 状态。
     */
    private static class TabInfo {
        /** 标签页标题 */
        String title;
        /** 标签页 URL */
        String url;
        /** WebView 保存的状态 Bundle，用于恢复浏览历史 */
        Bundle state;
        TabInfo(String title, String url) { this.title = title; this.url = url; }
    }
}
