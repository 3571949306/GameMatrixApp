package com.gamecenter.app.browser.ui;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.content.ContextCompat;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.BuildConfig;
import com.gamecenter.app.R;
import com.gamecenter.app.browser.core.BrowserChromeClient;
import com.gamecenter.app.browser.core.BrowserController;
import com.gamecenter.app.browser.core.BrowserFindInPageHelper;
import com.gamecenter.app.browser.core.BrowserGestureHelper;
import com.gamecenter.app.browser.core.BrowserReaderModeHelper;
import com.gamecenter.app.browser.core.BrowserScreenshotHelper;
import com.gamecenter.app.browser.core.BrowserSettingsManager;
import com.gamecenter.app.browser.core.BrowserTabManager;
import com.gamecenter.app.browser.core.BrowserWebViewClient;
import com.gamecenter.app.browser.data.BrowserDatabase;
import com.gamecenter.app.browser.data.BrowserDownloadManager;
import com.gamecenter.app.browser.data.entity.BrowserBookmarkEntity;
import com.gamecenter.app.browser.data.entity.BrowserHistoryEntity;
import com.gamecenter.app.browser.data.entity.BrowserReadingListEntity;
import com.gamecenter.app.browser.data.repository.SearchHistoryRepository;
import com.gamecenter.app.browser.security.BrowserSecurityPolicy;
import com.gamecenter.app.browser.util.UrlUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Browser Fragment - 核心浏览器页面。
 */
public class BrowserFragment extends Fragment implements
        BrowserWebViewClient.PageLoadCallback,
        BrowserWebViewClient.ExternalUrlHandler,
        BrowserChromeClient.PageInfoCallback,
        BrowserSettingsManager.OnSettingsChangeListener {

    static final String TAG = "BrowserFragment";
    private static final String ARG_URL = "arg_url";

    public static BrowserFragment newInstance(@Nullable String url) {
        BrowserFragment fragment = new BrowserFragment();
        Bundle args = new Bundle();
        if (url != null && !url.isEmpty()) {
            args.putString(ARG_URL, url);
        }
        fragment.setArguments(args);
        return fragment;
    }

    /** P2-2 暴露给 BrowserActivity 用于音量键滚动 */
    @Nullable
    public WebView getControllerWebView() {
        return controller != null ? controller.getWebView() : null;
    }

    private BrowserController controller;
    private BrowserTabManager tabManager;
    private SearchHistoryRepository searchHistoryRepository;
    private BrowserFindInPageHelper findInPageHelper;
    private BrowserReaderModeHelper readerModeHelper;
    /** A6: 供切 Tab 后重新绑定到新 WebView 的宿主回调引用 */
    @Nullable private BrowserFindInPageHelper.HostCallback findInPageHostCallback;
    @Nullable private BrowserReaderModeHelper.ReaderModeCallback readerModeCallback;
    /** A1: WebView 状态快照 Bundle 的保存键（配置变更后恢复） */
    private static final String STATE_KEY_TAB_STATES = "browser_tab_webview_states";
    /** A1: 单 WebView 模式下的页面状态保存键 */
    private static final String STATE_KEY_SINGLE_STATE = "browser_single_webview_state";
    /** A1: 旋转后是否已恢复单 WebView 页面状态（恢复成功则不再加载初始页） */
    private boolean restoredSingleView;
    private BrowserGestureHelper gestureHelper;
    private com.gamecenter.app.browser.core.BrowserHomeHelper homeHelper;
    private com.gamecenter.app.browser.core.UrlInputHelper urlInputHelper;

    private EditText etUrl;
    private ImageButton btnBack;
    private ImageButton btnForward;
    private ImageButton btnRefresh;
    private ImageButton btnHome;
    private ImageButton btnMenu;
    private ImageButton btnMore;
    private ImageButton btnBookmark;
    private ImageButton btnTabs;
    private ImageButton btnDownload;
    private ProgressBar progressBar;
    private LinearLayout errorView;
    private FrameLayout webViewContainer;
    private FrameLayout homeContainer;
    private View skeletonOverlay;
    private LinearLayout topBar;
    private LinearLayout bottomBar;
    private TextView incognitoIndicator;

    // Find In Page
    private LinearLayout findInPageBar;
    private EditText etFindQuery;
    private TextView tvFindMatchCount;
    private ImageButton btnFindPrev;
    private ImageButton btnFindNext;
    private ImageButton btnFindClose;

    // Gesture indicators
    private ImageView gestureLeftIndicator;
    private ImageView gestureRightIndicator;

    private boolean isLoading = false;
    private boolean isDesktopMode = false;
    private boolean isIncognitoMode = false;
    private boolean hasPageError = false;
    private String currentTitle = "";
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean pendingRefreshPrompt = false;
    private long lastBookmarkClickTime = 0;
    private static final long BOOKMARK_CLICK_DEBOUNCE_MS = 600;

    // P0-4 智能 URL Bar
    private PopupWindow suggestionPopup;
    private PopupWindow enginePopup;
    private Runnable pendingSuggestionRunnable;
    private static final long SUGGESTION_DEBOUNCE_MS = 300;
    // [BUGFIX-2026-08-27] 建议查询会话代次，每次 hide/onPause 递增，异步回调校验防止 Fragment 不可见后仍弹出
    private int suggestionSessionId = 0;

    // 文件上传（支持多选）
    @Nullable private ValueCallback<Uri[]> filePathCallback;
    private final ActivityResultLauncher<Intent> fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (filePathCallback != null) {
                    Uri[] results = null;
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        ClipData clipData = result.getData().getClipData();
                        if (clipData != null && clipData.getItemCount() > 0) {
                            results = new Uri[clipData.getItemCount()];
                            for (int i = 0; i < clipData.getItemCount(); i++) {
                                results[i] = clipData.getItemAt(i).getUri();
                            }
                        } else if (result.getData().getData() != null) {
                            results = new Uri[]{result.getData().getData()};
                        }
                    }
                    filePathCallback.onReceiveValue(results);
                    filePathCallback = null;
                }
            });

    // P0-1 多 Tab：TabManagerActivity 结果回调
    private static final int REQUEST_TAB_MANAGER = 0x1001;
    private final ActivityResultLauncher<Intent> tabManagerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (controller == null || getContext() == null) return;
                Intent data = result.getData();
                // 1. 处理关闭 Tab（释放对应 WebView）
                if (data != null && data.hasExtra(TabManagerActivity.EXTRA_CLOSED_TAB_ID)) {
                    String closedId = data.getStringExtra(TabManagerActivity.EXTRA_CLOSED_TAB_ID);
                    if ("__all__".equals(closedId)) {
                        // 关闭全部：销毁池
                        controller.destroy();
                        // 重新初始化 pool
                        if (webViewContainer != null) {
                            controller.initWebView(getContext(), webViewContainer, this, this, this);
                            setupChromeClientCallbacks();
                        }
                    } else if (closedId != null) {
                        controller.closeTabWebView(closedId);
                    }
                }
                // 2. 切换到目标 Tab（新建 Tab 也走这里）
                if (data != null && data.hasExtra(TabManagerActivity.EXTRA_SELECTED_TAB_ID)) {
                    String targetId = data.getStringExtra(TabManagerActivity.EXTRA_SELECTED_TAB_ID);
                    if (targetId != null) {
                        switchToTabById(targetId);
                        if (result.getResultCode() == TabManagerActivity.RESULT_NEW_TAB) {
                            // 新建 Tab：加载默认首页并显示起始页
                            if (BuildConfig.BROWSER_HOME_PAGE) {
                                showHomePage();
                                etUrl.setText("");
                            } else {
                                String homeUrl = controller.getDefaultHomeUrl();
                                controller.loadUrl(homeUrl);
                                etUrl.setText(homeUrl);
                            }
                            Toast.makeText(getContext(), R.string.browser_tab_created, Toast.LENGTH_SHORT).show();
                        } else if (tabManager != null) {
                            BrowserTabManager.Tab tab = findTabById(targetId);
                            if (tab != null) {
                                Toast.makeText(getContext(),
                                        getString(R.string.browser_tab_switched, tab.getTitle()),
                                        Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                }
            });

    // 媒体权限（相机/麦克风）
    @Nullable private PermissionRequest pendingPermissionRequest;
    private final ActivityResultLauncher<String[]> mediaPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                if (pendingPermissionRequest == null) return;
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (!granted) { allGranted = false; break; }
                }
                if (allGranted) {
                    pendingPermissionRequest.grant(pendingPermissionRequest.getResources());
                } else {
                    pendingPermissionRequest.deny();
                }
                pendingPermissionRequest = null;
            });

    // 全屏视频
    @Nullable private View customView;
    @Nullable private android.webkit.WebChromeClient.CustomViewCallback customViewCallback;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_browser, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        controller = new BrowserController();
        if (getActivity() != null) {
            searchHistoryRepository = new SearchHistoryRepository(getActivity().getApplication());
        }
        initViews(view);
        initWebView();
        // A1: 配置变更（旋转）后恢复 WebView 状态快照（须在首次 switchToTab / loadUrl 之前）
        if (savedInstanceState != null && controller != null) {
            if (BuildConfig.BROWSER_REAL_MULTI_TAB) {
                Bundle stateMap = savedInstanceState.getBundle(STATE_KEY_TAB_STATES);
                if (stateMap != null) {
                    controller.restoreTabStateMap(stateMap);
                }
            } else {
                Bundle singleState = savedInstanceState.getBundle(STATE_KEY_SINGLE_STATE);
                restoredSingleView = singleState != null
                        && controller.restoreSingleWebViewState(singleState);
            }
        }
        // P0-1 多 Tab：初始化 tabManager 并为当前 Tab 创建 WebView
        if (BuildConfig.BROWSER_REAL_MULTI_TAB && getContext() != null) {
            tabManager = BrowserTabManager.getInstance(getContext());
            BrowserTabManager.Tab currentTab = tabManager.getCurrentTab();
            if (currentTab == null) {
                currentTab = tabManager.createTab(null);
            }
            if (currentTab != null && controller != null) {
                // 切换到当前 Tab，按需创建 WebView（fallbackUrl 用 defaultHomeUrl）
                controller.switchToTab(currentTab.getId(), controller.getDefaultHomeUrl());
                // 重新绑定下载监听（新 WebView 需要）
                controller.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) ->
                        handleDownload(url, contentDisposition, mimetype, userAgent));
            }
        }
        initBrowserHelpers();
        initHomePage();
        setupListeners();
        setupGestureNavigation(view);
        setupBackPressHandler();
        // #8：配置变更（旋转）重建后恢复下载广播接收与进度轮询，避免 in-flight 下载卡“下载中”
        if (getContext() != null) {
            BrowserDownloadManager.getInstance(getContext()).refresh();
        }

        String initialUrl = null;
        if (getArguments() != null) {
            initialUrl = getArguments().getString(ARG_URL);
        }
        String homeUrl = getHomeUrl();
        if (!restoredSingleView) {
            if (initialUrl != null && !initialUrl.isEmpty()) {
                if (isHomePageUrl(initialUrl)) {
                    showHomePage();
                    etUrl.setText("");
                } else {
                    controller.loadUrl(initialUrl);
                    etUrl.setText(initialUrl);
                }
            } else {
                // 默认显示起始页而非加载 baidu.com
                if (BuildConfig.BROWSER_HOME_PAGE) {
                    showHomePage();
                    etUrl.setText("");
                } else {
                    controller.loadUrl(homeUrl);
                    etUrl.setText(homeUrl);
                }
            }
        }

        BrowserSettingsManager.getInstance(requireContext()).addListener(this);
    }

    /** 初始化 Find In Page / Reader Mode 辅助类 */
    private void initBrowserHelpers() {
        if (controller == null || getContext() == null) return;
        WebView webView = controller.getWebView();
        if (webView == null) return;

        // P0-4 智能 URL Bar
        if (BuildConfig.BROWSER_SMART_URL_BAR) {
            urlInputHelper = new com.gamecenter.app.browser.core.UrlInputHelper(getContext());
        }

        // Find In Page
        if (BuildConfig.BROWSER_FIND_IN_PAGE) {
            findInPageHelper = new BrowserFindInPageHelper();
            findInPageHostCallback = new BrowserFindInPageHelper.HostCallback() {
                @Override public void showFindBar() {
                    if (findInPageBar != null) findInPageBar.setVisibility(View.VISIBLE);
                }
                @Override public void hideFindBar() {
                    if (findInPageBar != null) findInPageBar.setVisibility(View.GONE);
                    hideKeyboardFrom(findInPageBar);
                }
            };
            findInPageHelper.bind(webView, etFindQuery, tvFindMatchCount,
                    btnFindPrev, btnFindNext, btnFindClose,
                    findInPageHostCallback);
        }

        // Reader Mode
        if (BuildConfig.BROWSER_READER_MODE) {
            readerModeHelper = new BrowserReaderModeHelper();
            readerModeCallback = new BrowserReaderModeHelper.ReaderModeCallback() {
                @Override public void onReaderModeEntered() {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), R.string.browser_reader_entered, Toast.LENGTH_SHORT).show();
                    }
                }
                @Override public void onReaderModeExited() {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), R.string.browser_reader_exited, Toast.LENGTH_SHORT).show();
                    }
                }
            };
            readerModeHelper.bind(webView, readerModeCallback);
        }
    }

    /** 初始化浏览器起始页（Feature Flag: BROWSER_HOME_PAGE） */
    private void initHomePage() {
        if (!BuildConfig.BROWSER_HOME_PAGE || homeContainer == null || getContext() == null) return;
        homeHelper = new com.gamecenter.app.browser.core.BrowserHomeHelper(getContext());
        homeHelper.setCallback(new com.gamecenter.app.browser.core.BrowserHomeHelper.HomeCallback() {
            @Override public void onSiteClicked(@NonNull String url) {
                hideHomePage();
                if (controller != null) controller.loadUrl(url);
                etUrl.setText(url);
            }
            @Override public void onSearchClicked() {
                // 聚焦 URL bar 让用户输入
                etUrl.requestFocus();
                android.view.inputmethod.InputMethodManager imm = (android.view.inputmethod.InputMethodManager)
                        requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(etUrl, 0);
            }
            @Override public void onBookmarkClicked() {
                startActivity(new android.content.Intent(getContext(), com.gamecenter.app.browser.ui.BookmarkActivity.class));
            }
            @Override public void onHistoryClicked() {
                startActivity(new android.content.Intent(getContext(), com.gamecenter.app.browser.ui.HistoryActivity.class));
            }
            @Override public void onReadingListClicked() {
                // Phase 4 (U1): 接线阅读列表按钮，启动 ReadingListActivity
                if (getContext() != null) {
                    ReadingListActivity.start(getContext());
                }
            }
        });
        View homeView = homeHelper.createHomeView(LayoutInflater.from(getContext()), homeContainer);
        homeContainer.addView(homeView);
    }

    /** 显示起始页 */
    private void showHomePage() {
        if (homeContainer != null) {
            homeContainer.setVisibility(View.VISIBLE);
            if (homeHelper != null) homeHelper.loadTopSitesAsync();
        }
    }

    /** 隐藏起始页 */
    private void hideHomePage() {
        if (homeContainer != null) homeContainer.setVisibility(View.GONE);
    }

    /**
     * P3-5 统一交互反馈：Feature Flag 开启时使用 Snackbar，否则使用 Toast。
     * Snackbar 锚定到 bottomBar 上方，避免被系统手势条遮挡。
     */
    private void showFeedback(int stringRes) {
        showFeedback(getString(stringRes));
    }

    private void showFeedback(@NonNull CharSequence text) {
        if (getView() == null) return;
        if (com.gamecenter.app.BuildConfig.BROWSER_SNACKBAR_FEEDBACK) {
            com.google.android.material.snackbar.Snackbar sb =
                    com.google.android.material.snackbar.Snackbar.make(
                            getView(), text, com.google.android.material.snackbar.Snackbar.LENGTH_SHORT);
            if (bottomBar != null) sb.setAnchorView(bottomBar);
            sb.show();
        } else {
            if (getContext() != null) Toast.makeText(getContext(), text, Toast.LENGTH_SHORT).show();
        }
    }

    /** 判断给定 URL 是否为起始页 */
    private boolean isHomePageUrl(@Nullable String url) {
        return url != null && (url.equalsIgnoreCase("about:home")
                || url.equalsIgnoreCase("about:blank") && BuildConfig.BROWSER_HOME_PAGE);
    }


    private void setupGestureNavigation(@NonNull View view) {
        if (!BuildConfig.BROWSER_GESTURE_NAV || controller == null) {
            android.util.Log.w(TAG, "setupGestureNavigation skipped: flag=" + BuildConfig.BROWSER_GESTURE_NAV
                    + " controller=" + (controller != null));
            return;
        }
        WebView webView = controller.getWebView();
        if (webView == null) {
            android.util.Log.w(TAG, "setupGestureNavigation skipped: webView is null");
            return;
        }
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        android.util.Log.d(TAG, "setupGestureNavigation: screenWidth=" + screenWidth);
        gestureHelper = new BrowserGestureHelper(requireContext(),
                new BrowserGestureHelper.GestureActionCallback() {
                    @Override public boolean canGoBack() { return controller != null && controller.canGoBack(); }
                    @Override public boolean canGoForward() { return controller != null && controller.canGoForward(); }
                    @Override public void onGoBack() {
                        android.util.Log.d(TAG, "gesture onGoBack invoked");
                        if (controller != null) controller.goBack();
                    }
                    @Override public void onGoForward() {
                        android.util.Log.d(TAG, "gesture onGoForward invoked");
                        if (controller != null) controller.goForward();
                    }
                    @Override public void onShowHistory() {
                        // P0-3：长按 WebView 触发显示历史记录面板（复用 onHistoryClicked 逻辑）
                        android.util.Log.d(TAG, "gesture onShowHistory invoked");
                        if (getContext() == null) return;
                        startActivity(new android.content.Intent(getContext(), com.gamecenter.app.browser.ui.HistoryActivity.class));
                    }
                },
                screenWidth);
        gestureHelper.bindIndicators(gestureLeftIndicator, gestureRightIndicator);
        // P0-3：从设置读取双击前进 / 长按历史开关
        BrowserSettingsManager settings = BrowserSettingsManager.getInstance(requireContext());
        gestureHelper.setDoubleTapForwardEnabled(settings.isDoubleTapForwardEnabled());
        gestureHelper.setLongPressHistoryEnabled(settings.isLongPressHistoryEnabled());

        webView.setOnTouchListener((v, event) -> {
            // 先让手势识别器处理；若手势未消费（return false），则交还 WebView 处理垂直滚动/点击
            boolean consumed = gestureHelper.onTouch(event);
            if (consumed) return true;
            return false;
        });
        // 关键：Android 10+ 系统手势导航会拦截左右边缘滑动，必须声明 exclusion rect
        // 让我们的自定义手势接管边缘区域，否则 GestureDetector 收不到边缘 swipe
        // 同时在 WebView 和根 View 上声明，确保所有 UI 层级都生效
        applySystemGestureExclusion(webView);
        applySystemGestureExclusion(view);
        android.util.Log.d(TAG, "setupGestureNavigation: listener set on webView");
    }

    /**
     * Android 10+ 系统手势导航会从屏幕左右边缘拦截 swipe（用于全局 back/forward）。
     * 我们的浏览器需要自己处理边缘 swipe 来执行 WebView 的 goBack/goForward，
     * 因此在 WebView 上声明系统手势排除区域（每边最多 200dp 高度）。
     *
     * <p>排除区域使用 BrowserGestureHelper.EDGE_WIDTH_DP 同步宽度（32dp）。
     * <p>API < 29 时此调用为空操作，自定义手势 Helper 在这些版本上天然可用。
     */
    private void applySystemGestureExclusion(@NonNull View view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        view.post(() -> {
            int w = view.getWidth();
            int h = view.getHeight();
            if (w <= 0 || h <= 0) return;
            float density = getResources().getDisplayMetrics().density;
            int edgePx = (int) (BrowserGestureHelper.EDGE_WIDTH_DP * density);
            int exclusionHeight = (int) (200 * density); // 系统限制：每边最多 200dp
            int top = Math.max(0, (h - exclusionHeight) / 2);
            int bottom = Math.min(h, top + exclusionHeight);
            List<Rect> rects = new ArrayList<>(2);
            rects.add(new Rect(0, top, edgePx, bottom));
            rects.add(new Rect(w - edgePx, top, w, bottom));
            try {
                view.setSystemGestureExclusionRects(rects);
                android.util.Log.d(TAG, "applySystemGestureExclusion on " + view.getClass().getSimpleName()
                        + ": left=0," + top + "," + edgePx + "," + bottom
                        + " right=" + (w - edgePx) + "," + top + "," + w + "," + bottom);
            } catch (Throwable t) {
                android.util.Log.w(TAG, "setSystemGestureExclusionRects failed", t);
            }
        });
    }

    private void hideKeyboardFrom(@Nullable View view) {
        if (view == null || getContext() == null) return;
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    /** 注册返回键回调：依次处理 Find Bar → Reader Mode → 全屏视频 → WebView 后退 → 默认 */
    private void setupBackPressHandler() {
        if (getView() == null || getActivity() == null) return;
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (controller == null) {
                    setEnabled(false);
                    requireActivity().onBackPressed();
                    return;
                }
                // 1. 关闭查找栏
                if (findInPageHelper != null && findInPageBar != null
                        && findInPageBar.getVisibility() == View.VISIBLE) {
                    findInPageHelper.hide();
                    return;
                }
                // 2. 退出阅读模式
                if (readerModeHelper != null && readerModeHelper.isActive()) {
                    readerModeHelper.exitReaderMode();
                    return;
                }
                // 3. 退出全屏视频
                if (customView != null) {
                    hideCustomView();
                    return;
                }
                // 4. WebView 后退
                if (controller.canGoBack()) {
                    controller.goBack();
                } else {
                    setEnabled(false);
                    requireActivity().onBackPressed();
                }
            }
        };
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), callback);
    }

    private void initViews(View view) {
        webViewContainer = view.findViewById(R.id.webview_container);
        homeContainer = view.findViewById(R.id.browser_home_container);
        skeletonOverlay = view.findViewById(R.id.skeleton_overlay);
        etUrl = view.findViewById(R.id.et_url);
        btnBack = view.findViewById(R.id.btn_back);
        btnForward = view.findViewById(R.id.btn_forward);
        btnRefresh = view.findViewById(R.id.btn_refresh);
        btnHome = view.findViewById(R.id.btn_home);
        btnMenu = view.findViewById(R.id.btn_menu);
        btnMore = view.findViewById(R.id.btn_more);
        btnBookmark = view.findViewById(R.id.btn_bookmark);
        btnTabs = view.findViewById(R.id.btn_tabs);
        btnDownload = view.findViewById(R.id.btn_download);
        progressBar = view.findViewById(R.id.progress_bar);
        errorView = view.findViewById(R.id.error_view);
        topBar = view.findViewById(R.id.browser_top_bar);
        bottomBar = view.findViewById(R.id.browser_bottom_bar);
        incognitoIndicator = view.findViewById(R.id.incognito_indicator);

        // Find In Page
        findInPageBar = view.findViewById(R.id.find_in_page_bar);
        etFindQuery = view.findViewById(R.id.et_find_query);
        tvFindMatchCount = view.findViewById(R.id.tv_find_match_count);
        btnFindPrev = view.findViewById(R.id.btn_find_prev);
        btnFindNext = view.findViewById(R.id.btn_find_next);
        btnFindClose = view.findViewById(R.id.btn_find_close);

        // Gesture indicators
        gestureLeftIndicator = view.findViewById(R.id.gesture_left_indicator);
        gestureRightIndicator = view.findViewById(R.id.gesture_right_indicator);

        View btnRetry = view.findViewById(R.id.btn_retry);
        if (btnRetry != null) {
            btnRetry.setOnClickListener(v -> { if (controller != null) controller.reload(); });
        }
    }

    private void initWebView() {
        if (getContext() == null) return;
        if (controller != null) {
            controller.initWebView(getContext(), webViewContainer, this, this, this);
            // 设置下载监听
            controller.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
                handleDownload(url, contentDisposition, mimetype, userAgent);
            });
            // 设置 ChromeClient 高级回调
            setupChromeClientCallbacks();
        }
    }

    /** 处理文件下载 */
    private void handleDownload(String downloadUrl, String contentDisposition, String mimeType, String userAgent) {
        if (getContext() == null) return;
        String name = android.webkit.URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType);
        if (name == null || name.isEmpty()) {
            name = "download_" + System.currentTimeMillis();
        }
        final String fileName = name;
        final String url = downloadUrl;
        BrowserDownloadManager downloadMgr = BrowserDownloadManager.getInstance(getContext());
        boolean isDangerous = downloadMgr.isDangerousFile(fileName);

        String message = getString(R.string.browser_download_dangerous_message, fileName);
        new AlertDialog.Builder(requireContext())
            .setTitle(isDangerous ? R.string.browser_download_dangerous_title : R.string.browser_download_title)
            .setMessage(isDangerous ? message : fileName)
            .setPositiveButton(isDangerous ? R.string.browser_download_dangerous_confirm : android.R.string.ok, (d, w) -> {
                downloadMgr.downloadFile(url, fileName, mimeType, userAgent);
                safeToast(R.string.browser_download_started, fileName);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> {
            if (controller == null) return;
            // 1. 先关闭 Find In Page 栏
            if (findInPageHelper != null && findInPageBar != null
                    && findInPageBar.getVisibility() == View.VISIBLE) {
                findInPageHelper.hide();
                return;
            }
            // 2. 退出阅读模式
            if (readerModeHelper != null && readerModeHelper.isActive()) {
                readerModeHelper.exitReaderMode();
                return;
            }
            // 3. 退出全屏视频
            if (customView != null) {
                hideCustomView();
                return;
            }
            // 4. WebView 后退
            if (!controller.goBack()) {
                if (getActivity() != null) getActivity().finish();
            }
        });
        btnForward.setOnClickListener(v -> { if (controller != null) controller.goForward(); });
        btnRefresh.setOnClickListener(v -> {
            if (controller == null) return;
            if (isLoading) controller.stopLoading();
            else controller.reload();
        });
        btnHome.setOnClickListener(v -> {
            // 优先显示起始页；若 Feature Flag 关闭则加载配置的 homeUrl
            if (BuildConfig.BROWSER_HOME_PAGE) {
                showHomePage();
                etUrl.setText("");
                hideKeyboardFrom(topBar);
            } else {
                String homeUrl = getHomeUrl();
                if (controller != null) controller.loadUrl(homeUrl);
                etUrl.setText(homeUrl);
            }
        });
        btnBookmark.setOnClickListener(v -> toggleBookmark());
        btnTabs.setOnClickListener(v -> {
            // P0-1 多 Tab：启动 TabManagerActivity 全屏切换器
            if (BuildConfig.BROWSER_REAL_MULTI_TAB) {
                launchTabManager();
            } else {
                showTabList();
            }
        });
        btnDownload.setOnClickListener(v -> {
            if (getContext() != null) DownloadActivity.start(getContext());
        });
        btnMenu.setOnClickListener(v -> showMoreMenu(v));
        btnMore.setOnClickListener(v -> showMoreMenu(v));
        etUrl.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String input = etUrl.getText().toString().trim();
                hideUrlSuggestions();
                // 优先使用 UrlInputHelper（含 URL/搜索词识别 + 多搜索引擎支持）
                if (BuildConfig.BROWSER_SMART_URL_BAR && urlInputHelper != null && getContext() != null) {
                    String url = urlInputHelper.processInput(getContext(), input);
                    if (controller != null) controller.loadUrl(url);
                    etUrl.setText(url);
                } else {
                    if (controller != null) controller.loadInput(input);
                    etUrl.setText(input);
                }
                saveSearchHistoryIfNeeded(input);
                hideKeyboard();
                hideHomePage();
                return true;
            }
            return false;
        });
        etUrl.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                etUrl.selectAll();
                // 注意：不在获焦时自动弹出建议列表，避免页面加载/焦点回弹等非用户主动操作时遮挡内容。
                // 建议仅在用户实际输入时由 TextWatcher(afterTextChanged) 触发。
            } else {
                hideUrlSuggestions();
            }
        });

        // P0-4 智能 URL Bar：实时输入建议
        if (BuildConfig.BROWSER_SMART_URL_BAR && urlInputHelper != null) {
            etUrl.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    String text = s == null ? "" : s.toString().trim();
                    if (text.isEmpty()) {
                        hideUrlSuggestions();
                        return;
                    }
                    // [BUGFIX-2026-08-27] Bug1：仅在 EditText 有焦点时触发建议查询。
                    // 程序化 setText（页面加载同步 URL、Tab 切换、初始化）不会聚焦 EditText，
                    // 因此 hasFocus() 为 false → 跳过，避免自动弹出建议列表。
                    if (!etUrl.hasFocus()) return;
                    scheduleSuggestionQuery(text);
                }
            });

            // 长按 URL Bar → 弹出搜索引擎选择 + 粘贴并前往
            etUrl.setOnLongClickListener(v -> {
                if (getContext() == null) return false;
                showUrlLongClickMenu(v);
                return true;
            });
        }
    }

    /** 调度建议查询（防抖 300ms） */
    private void scheduleSuggestionQuery(@NonNull String keyword) {
        if (urlInputHelper == null || getContext() == null) return;
        if (pendingSuggestionRunnable != null) {
            mainHandler.removeCallbacks(pendingSuggestionRunnable);
        }
        final String query = keyword;
        // [BUGFIX-2026-08-27] Bug2：捕获当前会话代次，回调中校验防止 Fragment hide 后仍弹出
        final int capturedSession = suggestionSessionId;
        pendingSuggestionRunnable = () -> {
            if (urlInputHelper == null || getContext() == null) return;
            urlInputHelper.querySuggestionsAsync(query, items ->
                    mainHandler.post(() -> {
                        // [BUGFIX-2026-08-27] Bug2 双保险：
                        // 1. 会话代次校验 —— hide/onPause 后 sessionId 已递增，旧回调作废
                        if (capturedSession != suggestionSessionId) return;
                        if (getContext() == null) return;
                        if (!isSuggestionWindowUsable()) return;
                        // 校验当前 EditText 内容仍然匹配（避免延迟导致显示过时建议）
                        String current = etUrl.getText().toString().trim();
                        if (!current.equalsIgnoreCase(query)) return;
                        if (items.isEmpty()) {
                            hideUrlSuggestions();
                        } else {
                            showUrlSuggestions(items);
                        }
                    }));
        };
        mainHandler.postDelayed(pendingSuggestionRunnable, SUGGESTION_DEBOUNCE_MS);
    }

    /** [BUGFIX-2026-08-27] Bug2：Fragment 建议窗口是否可安全显示（可见且未被 hide）。 */
    private boolean isSuggestionWindowUsable() {
        if (!isAdded()) return false;
        if (getView() == null) return false;
        View v = getView();
        if (!v.isShown()) return false;
        if (v.getVisibility() != View.VISIBLE) return false;
        return etUrl != null && etUrl.getWindowToken() != null;
    }

    /** 显示 URL 建议下拉 */
    private void showUrlSuggestions(@NonNull java.util.List<com.gamecenter.app.browser.core.UrlInputHelper.SuggestionItem> items) {
        if (getContext() == null || urlInputHelper == null || etUrl == null) return;
        hideUrlSuggestions();
        suggestionPopup = urlInputHelper.showSuggestionsPopup(getContext(), etUrl, items,
                (url, title) -> {
                    if (getContext() == null) return;
                    hideUrlSuggestions();
                    if ("__clear__".equals(url)) {
                        // 清除建议（清除 browser_history 表所有记录的 visitCount，但保留 URL 本身）
                        clearUrlSuggestions();
                        return;
                    }
                    // 选中建议项 → 直接加载
                    if (controller != null) controller.loadUrl(url);
                    etUrl.setText(url);
                    hideHomePage();
                    hideKeyboard();
                });
    }

    /** 隐藏 URL 建议下拉 */
    private void hideUrlSuggestions() {
        if (suggestionPopup != null) {
            suggestionPopup.dismiss();
            suggestionPopup = null;
        }
    }

    /** 清除 URL 建议（删除所有历史记录） */
    private void clearUrlSuggestions() {
        if (getContext() == null) return;
        final Context ctx = getContext().getApplicationContext();
        ioExecutor.execute(() -> {
            try {
                com.gamecenter.app.browser.data.BrowserDatabase.getInstance(ctx)
                        .historyDao().deleteAll();
                mainHandler.post(() ->
                        safeToast(R.string.browser_url_suggestion_cleared));
            } catch (Exception e) {
                mainHandler.post(() ->
                        safeToast(R.string.browser_url_suggestion_clear_failed));
            }
        });
    }

    /** URL Bar 长按菜单：搜索引擎选择 + 粘贴并前往 */
    private void showUrlLongClickMenu(@NonNull View anchor) {
        if (getContext() == null || urlInputHelper == null) return;
        // 弹出 PopupMenu
        PopupMenu popup = new PopupMenu(getContext(), anchor);
        popup.getMenu().add(0, 1, 0, R.string.browser_url_search_engine);
        // 检查剪贴板
        ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        boolean hasClip = cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null
                && cm.getPrimaryClip().getItemCount() > 0;
        if (hasClip) {
            popup.getMenu().add(0, 2, 1, R.string.browser_url_paste_and_go);
        }
        popup.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                showEngineSelector(anchor);
                return true;
            } else if (item.getItemId() == 2) {
                pasteAndGo(cm);
                return true;
            }
            return false;
        });
        popup.show();
    }

    /** 显示搜索引擎选择 */
    private void showEngineSelector(@NonNull View anchor) {
        if (getContext() == null || urlInputHelper == null) return;
        enginePopup = urlInputHelper.showEngineSelector(getContext(), anchor, engine -> {
            if (enginePopup != null) {
                enginePopup.dismiss();
                enginePopup = null;
            }
            if (getContext() != null) {
                Toast.makeText(getContext(),
                        getString(R.string.browser_url_engine_selected, engine.displayName),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** 粘贴剪贴板内容并立即前往 */
    private void pasteAndGo(@Nullable ClipboardManager cm) {
        if (cm == null || cm.getPrimaryClip() == null || cm.getPrimaryClip().getItemCount() == 0) return;
        CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
        if (text == null) return;
        String input = text.toString().trim();
        if (input.isEmpty()) return;
        etUrl.setText(input);
        etUrl.setSelection(input.length());
        if (BuildConfig.BROWSER_SMART_URL_BAR && urlInputHelper != null && getContext() != null) {
            String url = urlInputHelper.processInput(getContext(), input);
            if (controller != null) controller.loadUrl(url);
            etUrl.setText(url);
        } else {
            if (controller != null) controller.loadInput(input);
        }
        saveSearchHistoryIfNeeded(input);
        hideKeyboard();
        hideHomePage();
        hideUrlSuggestions();
    }

    private void saveSearchHistoryIfNeeded(@Nullable String input) {
        if (isIncognitoMode || input == null || input.isEmpty() || searchHistoryRepository == null) return;
        // P0-4：用 UrlInputHelper 处理输入，支持多搜索引擎
        if (BuildConfig.BROWSER_SMART_URL_BAR && urlInputHelper != null && getContext() != null) {
            String processed = urlInputHelper.processInput(getContext(), input);
            String engine = BrowserSettingsManager.getInstance(requireContext()).getSearchEngine();
            // 判断是否走搜索引擎（任意一个前缀）
            boolean isSearch = false;
            for (com.gamecenter.app.browser.core.UrlInputHelper.SearchEngine e
                    : com.gamecenter.app.browser.core.UrlInputHelper.SearchEngine.values()) {
                if (processed != null && processed.startsWith(e.queryPrefix)) {
                    isSearch = true;
                    break;
                }
            }
            if (isSearch) {
                searchHistoryRepository.saveSearchHistory(input, engine);
            }
        } else {
            // 旧逻辑 fallback
            String processed = UrlUtils.processInput(input);
            if (processed != null && processed.startsWith(UrlUtils.SEARCH_ENGINE_URL)) {
                String engine = BrowserSettingsManager.getInstance(requireContext()).getSearchEngine();
                searchHistoryRepository.saveSearchHistory(input, engine);
            }
        }
    }

    @NonNull
    private String getHomeUrl() {
        if (getContext() == null) return BrowserSettingsManager.DEFAULT_HOME_URL;
        return BrowserSettingsManager.getInstance(requireContext()).getHomeUrl();
    }

    private void toggleBookmark() {
        if (controller == null || getContext() == null) return;
        long now = System.currentTimeMillis();
        if (now - lastBookmarkClickTime < BOOKMARK_CLICK_DEBOUNCE_MS) return;
        lastBookmarkClickTime = now;
        String url = controller.getCurrentUrl();
        if (url == null || url.isEmpty()) {
            Toast.makeText(getContext(), R.string.browser_bookmark_no_url, Toast.LENGTH_SHORT).show();
            return;
        }
        final String bookmarkUrl = url;
        final String bookmarkTitle = currentTitle;
        final Context ctx = getContext().getApplicationContext();
        ioExecutor.execute(() -> {
            try {
                BrowserBookmarkEntity existing = BrowserDatabase.getInstance(ctx)
                        .bookmarkDao().getByUrl(bookmarkUrl);
                if (existing != null) {
                    BrowserDatabase.getInstance(ctx).bookmarkDao().deleteByUrl(bookmarkUrl);
                    mainHandler.post(() -> {
                        safeToast(R.string.browser_btn_bookmark_remove);
                        updateBookmarkIcon();
                    });
                } else {
                    BrowserBookmarkEntity entity = new BrowserBookmarkEntity();
                    entity.setTitle(bookmarkTitle);
                    entity.setUrl(bookmarkUrl);
                    entity.setCreateTime(System.currentTimeMillis());
                    entity.setUpdateTime(System.currentTimeMillis());
                    BrowserDatabase.getInstance(ctx).bookmarkDao().insert(entity);
                    mainHandler.post(() -> {
                        safeToast(R.string.browser_btn_bookmark_add);
                        updateBookmarkIcon();
                    });
                }
            } catch (Exception ignored) {}
        });
    }

    /**
     * P1-3 阅读列表：将当前页面加入稍后阅读列表。
     * 通过 evaluateJavascript 提取页面正文前 200 字作为摘要。
     */
    private void addToReadingList() {
        if (controller == null || getContext() == null) return;
        String url = controller.getCurrentUrl();
        if (url == null || url.isEmpty() || isReaderModeUrl(url)) {
            Toast.makeText(getContext(), R.string.browser_reading_list_no_url, Toast.LENGTH_SHORT).show();
            return;
        }
        final String pageUrl = url;
        final String pageTitle = currentTitle != null ? currentTitle : "";
        final String pageHost;
        try {
            pageHost = android.net.Uri.parse(pageUrl).getHost() != null
                    ? android.net.Uri.parse(pageUrl).getHost() : "";
        } catch (Throwable t) {
            return;
        }
        final Context ctx = getContext().getApplicationContext();
        // 先检查是否已存在
        ioExecutor.execute(() -> {
            try {
                int count = BrowserDatabase.getInstance(ctx).readingListDao().countByUrl(pageUrl);
                if (count > 0) {
                    mainHandler.post(() -> safeToast(R.string.browser_reading_list_already_exists));
                    return;
                }
                // 在主线程调用 evaluateJavascript 提取摘要
                mainHandler.post(() -> extractSummaryAndSave(pageUrl, pageTitle, pageHost));
            } catch (Exception ignored) {}
        });
    }

    /** 通过 evaluateJavascript 提取页面正文摘要，再异步落库 */
    private void extractSummaryAndSave(String pageUrl, String pageTitle, String pageHost) {
        if (controller == null || getContext() == null) return;
        WebView webView = controller.getWebView();
        if (webView == null) {
            saveReadingListItem(pageUrl, pageTitle, "", pageHost);
            return;
        }
        // 显示提取中提示
        Toast.makeText(getContext(),
                R.string.browser_reading_list_extracting, Toast.LENGTH_SHORT).show();
        // 提取页面正文前 200 字符（兼容 paragraph / article / body）
        final String js = "(function(){var t='';var sel=document.querySelectorAll('article p, article, main p, main, p, body');" +
                "for(var i=0;i<sel.length && t.length<400;i++){var s=(sel[i].innerText||sel[i].textContent||'').trim();" +
                "if(s.length>t.length)t=s;}return t.substring(0,200);})()";
        try {
            webView.evaluateJavascript(js, value -> {
                String summary = parseJsString(value);
                saveReadingListItem(pageUrl, pageTitle, summary, pageHost);
            });
        } catch (Throwable t) {
            saveReadingListItem(pageUrl, pageTitle, "", pageHost);
        }
    }

    /** 将 evaluateJavascript 返回值解析为普通 String（去除引号、null） */
    private String parseJsString(String jsResult) {
        if (jsResult == null || "null".equals(jsResult)) return "";
        String s = jsResult.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            s = s.substring(1, s.length() - 1);
        }
        // 反转义常见字符
        s = s.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
        return s.trim();
    }

    /** 异步保存阅读列表项 */
    private void saveReadingListItem(String url, String title, String summary, String host) {
        if (getContext() == null) return;
        final Context ctx = getContext().getApplicationContext();
        final BrowserReadingListEntity entity = new BrowserReadingListEntity();
        entity.setUrl(url);
        entity.setTitle(title != null ? title : "");
        entity.setSummary(summary != null ? summary : "");
        entity.setHost(host != null ? host : "");
        entity.setSavedAt(System.currentTimeMillis());
        entity.setRead(0);
        ioExecutor.execute(() -> {
            try {
                BrowserDatabase.getInstance(ctx).readingListDao().insert(entity);
                mainHandler.post(() -> safeToast(R.string.browser_reading_list_added));
            } catch (Exception ignored) {}
        });
    }

    private void updateBookmarkIcon() {
        if (btnBookmark == null || getContext() == null || controller == null) return;
        String url = controller.getCurrentUrl();
        if (url == null || url.isEmpty()) {
            btnBookmark.setImageResource(R.drawable.ic_browser_bookmark);
            return;
        }
        final String checkUrl = url;
        final Context ctx = getContext().getApplicationContext();
        ioExecutor.execute(() -> {
            try {
                int count = BrowserDatabase.getInstance(ctx).bookmarkDao().countByUrl(checkUrl);
                mainHandler.post(() -> {
                    if (btnBookmark != null) {
                        btnBookmark.setImageResource(count > 0
                                ? R.drawable.ic_browser_bookmark_filled
                                : R.drawable.ic_browser_bookmark);
                    }
                });
            } catch (Exception ignored) {}
        });
    }

    private void hideKeyboard() {
        if (getContext() == null || etUrl == null) return;
        InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etUrl.getWindowToken(), 0);
    }

    private void showMoreMenu(View anchor) {
        if (getContext() == null) return;
        PopupMenu popup = new PopupMenu(getContext(), anchor);
        popup.getMenuInflater().inflate(R.menu.browser_more_menu, popup.getMenu());

        // Feature Flag 控制菜单项可见性
        MenuItem findItem = popup.getMenu().findItem(R.id.menu_find_in_page);
        if (findItem != null) findItem.setVisible(BuildConfig.BROWSER_FIND_IN_PAGE);
        MenuItem readerItem = popup.getMenu().findItem(R.id.menu_reader_mode);
        if (readerItem != null) {
            readerItem.setVisible(BuildConfig.BROWSER_READER_MODE);
            readerItem.setTitle(readerModeHelper != null && readerModeHelper.isActive()
                    ? R.string.browser_menu_reader_mode_exit : R.string.browser_menu_reader_mode);
        }
        MenuItem screenshotItem = popup.getMenu().findItem(R.id.menu_screenshot);
        if (screenshotItem != null) screenshotItem.setVisible(BuildConfig.BROWSER_SCREENSHOT);

        MenuItem desktopItem = popup.getMenu().findItem(R.id.menu_desktop);
        if (desktopItem != null) {
            desktopItem.setTitle(isDesktopMode ? R.string.browser_menu_desktop_on : R.string.browser_menu_desktop);
        }
        // P1-3 阅读列表：Feature Flag 控制菜单项可见性
        MenuItem readingAddItem = popup.getMenu().findItem(R.id.menu_reading_list_add);
        if (readingAddItem != null) readingAddItem.setVisible(BuildConfig.BROWSER_READING_LIST);
        MenuItem readingListItem = popup.getMenu().findItem(R.id.menu_reading_list);
        if (readingListItem != null) readingListItem.setVisible(BuildConfig.BROWSER_READING_LIST);
        // P1-2 追踪保护：Feature Flag 控制菜单项可见性
        MenuItem privacyItem = popup.getMenu().findItem(R.id.menu_privacy_dashboard);
        if (privacyItem != null) privacyItem.setVisible(BuildConfig.BROWSER_TRACKER_PROTECTION);
        // P1-4 离线缓存：Feature Flag 控制菜单项可见性
        MenuItem offlineItem = popup.getMenu().findItem(R.id.menu_offline_cache);
        if (offlineItem != null) offlineItem.setVisible(BuildConfig.BROWSER_OFFLINE_CACHE);
        // P1-5 页面翻译：Feature Flag 控制菜单项可见性
        MenuItem translateItem = popup.getMenu().findItem(R.id.menu_translate);
        if (translateItem != null) translateItem.setVisible(BuildConfig.BROWSER_TRANSLATE);
        // P3-3 底栏可定制：Feature Flag 控制菜单项可见性
        MenuItem customizeBarItem = popup.getMenu().findItem(R.id.menu_customize_bottom_bar);
        if (customizeBarItem != null) customizeBarItem.setVisible(BuildConfig.BROWSER_CUSTOM_BOTTOM_BAR);
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_new_tab) {
                if (tabManager == null && getContext() != null) {
                    tabManager = BrowserTabManager.getInstance(getContext());
                }
                if (tabManager != null) {
                    BrowserTabManager.Tab newTab = tabManager.createTab(null);
                    if (newTab != null) {
                        if (BuildConfig.BROWSER_REAL_MULTI_TAB && controller != null) {
                            // 多 Tab 模式：为新 Tab 创建独立 WebView 并切换
                            controller.switchToTab(newTab.getId(), controller.getDefaultHomeUrl());
                            controller.setDownloadListener((url, ua, cd, mt, cl) ->
                                    handleDownload(url, cd, mt, ua));
                            if (BuildConfig.BROWSER_HOME_PAGE) {
                                showHomePage();
                                etUrl.setText("");
                            } else {
                                String homeUrl = controller.getDefaultHomeUrl();
                                controller.loadUrl(homeUrl);
                                etUrl.setText(homeUrl);
                            }
                        } else if (controller != null) {
                            // 单 WebView 模式：直接加载首页
                            controller.loadUrl(controller.getDefaultHomeUrl());
                            etUrl.setText(controller.getDefaultHomeUrl());
                        }
                        Toast.makeText(getContext(), R.string.browser_tab_created, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getContext(), R.string.browser_tab_max_reached, Toast.LENGTH_SHORT).show();
                    }
                }
                return true;
            } else if (id == R.id.menu_incognito) {
                toggleIncognitoMode();
                return true;
            } else if (id == R.id.menu_find_in_page) {
                if (findInPageHelper != null) findInPageHelper.show();
                return true;
            } else if (id == R.id.menu_reader_mode) {
                if (readerModeHelper != null && getContext() != null) {
                    readerModeHelper.toggle(getContext());
                }
                return true;
            } else if (id == R.id.menu_screenshot) {
                takeScreenshot();
                return true;
            } else if (id == R.id.menu_desktop) {
                isDesktopMode = !isDesktopMode;
                if (controller != null) controller.setDesktopMode(isDesktopMode);
                return true;
            } else if (id == R.id.menu_share) {
                shareCurrentUrl();
                return true;
            } else if (id == R.id.menu_copy) {
                copyCurrentUrl();
                return true;
            } else if (id == R.id.menu_open_external) {
                openInExternalBrowser();
                return true;
            } else if (id == R.id.menu_history) {
                if (getContext() != null) HistoryActivity.start(getContext());
                return true;
            } else if (id == R.id.menu_bookmarks) {
                if (getContext() != null) BookmarkActivity.start(getContext());
                return true;
            } else if (id == R.id.menu_reading_list_add) {
                addToReadingList();
                return true;
            } else if (id == R.id.menu_reading_list) {
                if (getContext() != null) ReadingListActivity.start(getContext());
                return true;
            } else if (id == R.id.menu_privacy_dashboard) {
                if (getContext() != null) PrivacyDashboardActivity.start(getContext());
                return true;
            } else if (id == R.id.menu_offline_cache) {
                if (getContext() != null) OfflineCacheActivity.start(getContext());
                return true;
            } else if (id == R.id.menu_translate) {
                if (getContext() != null) {
                    com.gamecenter.app.browser.core.BrowserTranslateHelper.showEngineDialog(
                            getContext(), controller != null ? controller.getCurrentUrl() : null);
                }
                return true;
            } else if (id == R.id.menu_customize_bottom_bar) {
                if (getContext() != null) BottomBarCustomizeActivity.start(getContext());
                return true;
            } else if (id == R.id.menu_downloads) {
                if (getContext() != null) DownloadActivity.start(getContext());
                return true;
            } else if (id == R.id.menu_settings) {
                if (getContext() != null) BrowserSettingsActivity.start(getContext());
                return true;
            }
            return false;
        });
        popup.show();
    }

    /** 截取当前 WebView 可见区域并保存到相册 */
    private void takeScreenshot() {
        if (controller == null || getContext() == null) return;
        WebView webView = controller.getWebView();
        if (webView == null) return;
        BrowserScreenshotHelper.captureAndSave(getContext(), webView, null);
    }

    private void shareCurrentUrl() {
        if (controller == null || getContext() == null) return;
        String url = controller.getCurrentUrl();
        if (url == null || url.isEmpty()) return;
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, url);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.browser_menu_share)));
    }

    private void copyCurrentUrl() {
        if (controller == null || getContext() == null) return;
        String url = controller.getCurrentUrl();
        if (url == null || url.isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            ClipData clip = ClipData.newPlainText("browser_url", url);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), R.string.browser_copy_url_done, Toast.LENGTH_SHORT).show();
        }
    }

    private void openInExternalBrowser() {
        if (controller == null || getContext() == null) return;
        String url = controller.getCurrentUrl();
        if (url == null || url.isEmpty()) return;
        openExternalUrlWithConfirmation(url);
    }

    /**
     * 外部协议/链接统一确认弹窗。
     */
    private void openExternalUrlWithConfirmation(@NonNull String url) {
        if (getContext() == null) return;
        Context ctx = getContext();
        if (!BrowserSecurityPolicy.getInstance().canExternalAppHandle(ctx, url)) {
            safeToast(R.string.browser_open_external_failed);
            return;
        }
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.browser_security_external_title)
            .setMessage(getString(R.string.browser_security_external_message, url))
            .setPositiveButton(R.string.browser_security_external_confirm, (d, w) -> {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (Exception e) {
                    safeToast(R.string.browser_open_external_failed);
                }
            })
            .setNegativeButton(R.string.browser_security_external_deny, null)
            .show();
    }

    @Override
    public boolean onExternalUrlRequested(@NonNull String url) {
        openExternalUrlWithConfirmation(url);
        return true;
    }

    private void toggleIncognitoMode() {
        if (getContext() == null) return;
        boolean wasIncognito = isIncognitoMode;
        isIncognitoMode = !isIncognitoMode;
        if (incognitoIndicator != null) {
            incognitoIndicator.setVisibility(isIncognitoMode ? View.VISIBLE : View.GONE);
        }
        if (controller != null) {
            controller.clearWebViewData();
            // 切换无痕状态时清理 Cookie，避免非无痕数据残留或无痕数据泄露
            try {
                CookieManager.getInstance().removeAllCookies(null);
            } catch (Exception ignored) {}
        }
        safeToast(isIncognitoMode ? R.string.browser_incognito_on : R.string.browser_incognito_off);
    }

    /** P0-1 多 Tab：启动 TabManagerActivity 全屏切换器（带结果回传）。 */
    private void launchTabManager() {
        if (getContext() == null) return;
        if (tabManager == null) tabManager = BrowserTabManager.getInstance(getContext());
        Intent intent = new Intent(getContext(), TabManagerActivity.class);
        tabManagerLauncher.launch(intent);
    }

    /** P0-1 多 Tab：根据 tabId 切换 WebView，并同步 URL 栏。 */
    private void switchToTabById(@NonNull String tabId) {
        if (controller == null || tabManager == null) return;
        BrowserTabManager.Tab tab = findTabById(tabId);
        if (tab == null) return;
        // fallbackUrl 用 tab 当前 URL（若 WebView 需重建时加载）
        String fallback = tab.getUrl();
        if (fallback == null || fallback.isEmpty()) fallback = controller.getDefaultHomeUrl();
        WebView wv = controller.switchToTab(tabId, fallback);
        if (wv == null) return;
        // 同步 URL 栏和起始页显示状态
        String currentUrl = controller.getCurrentUrl();
        if (currentUrl != null && !currentUrl.isEmpty() && !isReaderModeUrl(currentUrl)) {
            etUrl.setText(currentUrl);
            hideHomePage();
        } else if (BuildConfig.BROWSER_HOME_PAGE) {
            // 空白页 → 显示起始页
            showHomePage();
            etUrl.setText("");
        }
        // 重新绑定手势监听（新 WebView 需要）
        if (gestureHelper != null) {
            try {
                wv.setOnTouchListener((v, event) -> {
                    boolean consumed = gestureHelper.onTouch(event);
                    return consumed || false;
                });
            } catch (Throwable ignored) {}
        }
        // A6: 切换 Tab 后重新绑定 Find-In-Page / Reader-Mode 到新 WebView
        // 阅读模式是按 WebView 的状态，切换后先退出旧 Tab 的阅读模式再重绑
        if (readerModeHelper != null && readerModeHelper.isActive()) {
            readerModeHelper.exitReaderMode();
        }
        if (findInPageHelper != null) {
            findInPageHelper.bind(wv, etFindQuery, tvFindMatchCount,
                    btnFindPrev, btnFindNext, btnFindClose, findInPageHostCallback);
        }
        if (readerModeHelper != null) {
            readerModeHelper.bind(wv, readerModeCallback);
        }
    }

    /** 根据 tabId 查找 Tab 对象。 */
    @Nullable
    private BrowserTabManager.Tab findTabById(@NonNull String tabId) {
        if (tabManager == null) return null;
        for (BrowserTabManager.Tab tab : tabManager.getTabList()) {
            if (tab.getId().equals(tabId)) return tab;
        }
        return null;
    }

    private void showTabList() {
        if (getContext() == null) return;
        if (tabManager == null) tabManager = BrowserTabManager.getInstance(requireContext());
        final java.util.List<BrowserTabManager.Tab> tabs = tabManager.getTabList();
        if (tabs.isEmpty()) {
            Toast.makeText(getContext(), R.string.browser_tab_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        BrowserTabManager.Tab current = tabManager.getCurrentTab();
        final String[] tabTitles = new String[tabs.size()];
        int checkedItem = 0;
        for (int i = 0; i < tabs.size(); i++) {
            BrowserTabManager.Tab tab = tabs.get(i);
            String title = tab.getTitle();
            if (title == null || title.isEmpty()) title = tab.getUrl() != null ? tab.getUrl() : getString(R.string.browser_tab_empty);
            tabTitles[i] = (tab.isIncognito() ? "[" + getString(R.string.browser_incognito_indicator) + "] " : "") + title;
            if (current != null && tab.getId().equals(current.getId())) checkedItem = i;
        }
        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.browser_tab_manager_title)
            .setSingleChoiceItems(tabTitles, checkedItem, (dialog, which) -> {
                tabManager.switchTab(tabs.get(which).getId());
                dialog.dismiss();
                BrowserTabManager.Tab selected = tabs.get(which);
                if (controller != null && selected.getUrl() != null) {
                    controller.loadUrl(selected.getUrl());
                    etUrl.setText(selected.getUrl());
                }
            })
            .setPositiveButton(R.string.browser_tab_new, (d, w) -> {
                BrowserTabManager.Tab newTab = tabManager.createTab(null);
                if (newTab != null && controller != null) {
                    controller.loadUrl(controller.getDefaultHomeUrl());
                    etUrl.setText(controller.getDefaultHomeUrl());
                }
            })
            .setNeutralButton(R.string.browser_tab_close_current, (d, w) -> {
                if (current != null) {
                    tabManager.closeTab(current.getId());
                    BrowserTabManager.Tab next = tabManager.getCurrentTab();
                    if (next != null && next.getUrl() != null && controller != null) {
                        controller.loadUrl(next.getUrl());
                        etUrl.setText(next.getUrl());
                    } else if (controller != null) {
                        controller.loadUrl(controller.getDefaultHomeUrl());
                        etUrl.setText(controller.getDefaultHomeUrl());
                    }
                }
            })
            .setNegativeButton(R.string.browser_tab_close, null)
            .show();
    }

    private void saveHistoryIfNeeded(String url) {
        if (isIncognitoMode || hasPageError || url == null || url.isEmpty()) return;
        if (!url.startsWith("http://") && !url.startsWith("https://")) return;
        if (getContext() == null) return;
        final String historyUrl = url;
        final String historyTitle = currentTitle;
        final Context ctx = getContext().getApplicationContext();
        ioExecutor.execute(() -> {
            try {
                BrowserHistoryEntity existing = BrowserDatabase.getInstance(ctx)
                        .historyDao().getByUrl(historyUrl);
                if (existing != null) {
                    BrowserDatabase.getInstance(ctx).historyDao()
                            .updateByUrl(historyUrl, historyTitle, System.currentTimeMillis());
                } else {
                    BrowserHistoryEntity entity = new BrowserHistoryEntity();
                    entity.setTitle(historyTitle);
                    entity.setUrl(historyUrl);
                    entity.setVisitCount(1);
                    entity.setLastVisitTime(System.currentTimeMillis());
                    entity.setFirstVisitTime(System.currentTimeMillis());
                    entity.setDeleted(false);
                    BrowserDatabase.getInstance(ctx).historyDao().insert(entity);
                }
            } catch (Exception ignored) {}
        });
    }

    @Override
    public void onPageStarted(@Nullable String tabId, String url, Bitmap favicon) {
        if (!isCallbackForActiveTab(tabId)) return; // 后台 Tab 的加载事件不得驱动前台 UI
        isLoading = true;
        hasPageError = false;
        // 阅读模式时 about:blank 不更新地址栏
        if (etUrl != null && !isReaderModeUrl(url)) {
            etUrl.setText(url);
        }
        if (progressBar != null) { progressBar.setVisibility(View.VISIBLE); progressBar.setProgress(0); }
        if (errorView != null) errorView.setVisibility(View.GONE);
        // P3-4 骨架屏：Feature Flag 开启且非阅读模式时显示
        if (BuildConfig.BROWSER_SKELETON_LOADING && skeletonOverlay != null
                && !isReaderModeUrl(url)) {
            skeletonOverlay.setVisibility(View.VISIBLE);
            skeletonOverlay.bringToFront();
        }
        // 页面开始加载时隐藏起始页
        hideHomePage();
        if (controller != null) controller.removeJsBridge();
    }

    @Override
    public void onPageFinished(@Nullable String tabId, String url) {
        boolean forActiveTab = isCallbackForActiveTab(tabId);
        if (!forActiveTab) {
            // 后台 Tab：只更新该 Tab 自己的元数据，不改地址栏/进度/JSBridge/离线缓存
            if (tabManager != null && controller != null && url != null && !isReaderModeUrl(url)) {
                BrowserTabManager.Tab finishedTab = findTabById(tabId);
                if (finishedTab != null && finishedTab.getId() != null) {
                    tabManager.updateTabInfo(finishedTab.getId(), finishedTab.getTitle(), url);
                }
            }
            return;
        }
        isLoading = false;
        if (etUrl != null && !isReaderModeUrl(url)) {
            etUrl.setText(url);
        }
        if (progressBar != null) { progressBar.setVisibility(View.GONE); }
        // P3-4 骨架屏：页面加载完成时隐藏
        if (skeletonOverlay != null) skeletonOverlay.setVisibility(View.GONE);
        if (controller != null) currentTitle = controller.getTitle();
        // 阅读模式时不写历史记录
        if (!isReaderModeUrl(url)) {
            saveHistoryIfNeeded(url);
            // 仅在可信域名下注入 JSBridge（阅读模式 about:blank 不注入）
            if (controller != null && getContext() != null) {
                controller.injectJsBridge(getContext(), url);
            }
            // P1-4 离线缓存：Feature Flag 开启时捕获页面 HTML
            if (BuildConfig.BROWSER_OFFLINE_CACHE && controller != null && getContext() != null) {
                WebView wv = controller.getWebView();
                if (wv != null) {
                    com.gamecenter.app.browser.core.BrowserOfflineCache.getInstance(getContext())
                            .captureAsync(wv, url, currentTitle);
                }
            }
        }
        updateBookmarkIcon();

        // 更新当前标签页信息
        if (tabManager != null && controller != null && !isReaderModeUrl(url)) {
            BrowserTabManager.Tab currentTab = tabManager.getCurrentTab();
            if (currentTab != null && url != null) {
                tabManager.updateTabInfo(currentTab.getId(), currentTitle, url);
            }
        }
    }

    /** 判断回调事件是否属于当前前台 Tab（单 WebView 模式 tabId 为 null，恒视为前台）。 */
    private boolean isCallbackForActiveTab(@Nullable String tabId) {
        return tabId == null || controller == null
                || tabId.equals(controller.getActiveTabId());
    }

    /** 阅读模式触发 about:blank 加载时跳过历史/书签/JSBridge 处理 */
    private boolean isReaderModeUrl(@Nullable String url) {
        return readerModeHelper != null && readerModeHelper.isActive()
                && (url == null || "about:blank".equals(url));
    }

    @Override
    public void onPageError(@Nullable String tabId, String url, String description) {
        if (!isCallbackForActiveTab(tabId)) return; // 后台 Tab 错误不驱动前台错误视图
        isLoading = false;
        hasPageError = true;
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (errorView != null) errorView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onReceivedSslError(@Nullable String tabId, String url) {
        if (!isCallbackForActiveTab(tabId)) return;
        safeToast(R.string.browser_ssl_error);
    }

    /** A7: 安全显示 Toast，检查 Fragment 是否仍然附加到 Activity。 */
    private void safeToast(int resId) {
        if (!isAdded() || getContext() == null) return;
        Toast.makeText(getContext(), resId, Toast.LENGTH_SHORT).show();
    }

    /** A7: 安全显示 Toast（带格式化参数）。 */
    private void safeToast(int resId, Object... formatArgs) {
        if (!isAdded() || getContext() == null) return;
        Toast.makeText(getContext(), getString(resId, formatArgs), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onTitleChanged(@Nullable String tabId, String title) {
        if (isCallbackForActiveTab(tabId)) currentTitle = title;
    }

    @Override
    public void onProgressChanged(@Nullable String tabId, int progress) {
        if (!isCallbackForActiveTab(tabId)) return; // 后台 Tab 进度不驱动前台进度条
        if (progressBar != null) {
            progressBar.setProgress(progress);
            if (progress >= 100) progressBar.setVisibility(View.GONE);
        }
    }

    @Override
    public void onReceivedIcon(@Nullable String tabId, Bitmap icon) {}

    @Override
    public void onResume() {
        super.onResume();
        if (controller != null && getContext() != null) {
            controller.onResume(getContext());
            // P1-1：onResume 时重新应用设置，确保系统夜间模式变化后
            // 自动模式（DARK_MODE_AUTO）能正确刷新 WebView 的 forceDark 状态
            controller.applySettings(getContext());
        } else if (controller != null) {
            controller.onResume(requireContext());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (controller != null) controller.onPause();
        // P0：切走/锁屏/切换模块时强制收起 URL Bar 建议 popup 与搜索引擎选择 popup，
        // 避免 PopupWindow 作为系统浮窗在 Fragment 不可见后仍覆盖其他模块。
        // [BUGFIX-2026-08-27] Bug2：递增会话代次，使所有在飞的异步查询回调在返回时作废。
        suggestionSessionId++;
        cancelPendingUrlSuggestions();
        hideUrlSuggestions();
        hideEngineSelector();
        hideKeyboard();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            // P0：Fragment 被 hide（底部导航切换标签）时，PopupWindow 不会随视图隐藏，必须主动关闭。
            // [BUGFIX-2026-08-27] Bug2：递增会话代次，使所有在飞的异步查询回调在返回时作废。
            suggestionSessionId++;
            cancelPendingUrlSuggestions();
            hideUrlSuggestions();
            hideEngineSelector();
            hideKeyboard();
        }
    }

    /** 取消待执行的 URL 建议查询，防止切换模块后延迟弹出建议框。 */
    private void cancelPendingUrlSuggestions() {
        if (pendingSuggestionRunnable != null) {
            mainHandler.removeCallbacks(pendingSuggestionRunnable);
            pendingSuggestionRunnable = null;
        }
    }

    /** 隐藏搜索引擎选择 popup。 */
    private void hideEngineSelector() {
        if (enginePopup != null) {
            enginePopup.dismiss();
            enginePopup = null;
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // #8：无论单/多 WebView 模式，配置变更（旋转）/进程重建前都保存页面状态，
        // 在 onDestroyView 销毁 WebView 之前取快照，随 savedInstanceState 传递。
        if (controller == null) return;
        try {
            if (BuildConfig.BROWSER_REAL_MULTI_TAB) {
                controller.saveAllTabStates();
                Bundle stateMap = controller.collectTabStateMap();
                if (!stateMap.isEmpty()) {
                    outState.putBundle(STATE_KEY_TAB_STATES, stateMap);
                }
            } else {
                Bundle singleState = controller.saveSingleWebViewState();
                if (singleState != null) {
                    outState.putBundle(STATE_KEY_SINGLE_STATE, singleState);
                }
            }
        } catch (Throwable t) {
            android.util.Log.w(TAG, "onSaveInstanceState save WebView states failed", t);
        }
    }

    @Override
    public void onDestroyView() {
        if (getContext() != null) {
            BrowserSettingsManager.getInstance(getContext()).removeListener(this);
        }
        // P0-4：清理 URL Bar 相关 popup 和防抖任务
        if (pendingSuggestionRunnable != null) {
            mainHandler.removeCallbacks(pendingSuggestionRunnable);
            pendingSuggestionRunnable = null;
        }
        hideUrlSuggestions();
        if (enginePopup != null) { enginePopup.dismiss(); enginePopup = null; }
        if (findInPageHelper != null) { findInPageHelper.destroy(); findInPageHelper = null; }
        if (readerModeHelper != null) { readerModeHelper.destroy(); readerModeHelper = null; }
        gestureHelper = null;
        urlInputHelper = null;
        // P0 内存泄漏修复：清理 BrowserHomeHelper（之前完全遗漏，导致 View 树和站点图标 Bitmap 无法回收）
        if (homeHelper != null) { homeHelper.destroy(); homeHelper = null; }
        if (controller != null) { controller.destroy(); controller = null; }
        if (getContext() != null) {
            BrowserDownloadManager.getInstance(getContext()).shutdown();
        }
        // P0 内存泄漏修复：兜底清理所有 mainHandler 延迟任务（含 pendingRefreshPrompt 等）
        mainHandler.removeCallbacksAndMessages(null);
        // P0 内存泄漏修复：关闭 ioExecutor，避免线程常驻并阻止 Fragment 回收
        if (!ioExecutor.isShutdown()) {
            ioExecutor.shutdownNow();
        }
        super.onDestroyView();
    }

    @Override
    public void onSettingsChanged(int reloadRequired) {
        if (controller != null && getContext() != null) {
            controller.applySettings(getContext());
            if (reloadRequired == BrowserSettingsManager.RELOAD_REQUIRED && !pendingRefreshPrompt) {
                pendingRefreshPrompt = true;
                mainHandler.postDelayed(() -> {
                    pendingRefreshPrompt = false;
                    if (controller != null && isAdded()) {
                        new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.browser_settings_changed_title)
                            .setMessage(R.string.browser_settings_changed_message)
                            .setPositiveButton(R.string.browser_settings_changed_refresh, (d, w) -> {
                                controller.reload();
                            })
                            .setNegativeButton(R.string.browser_settings_changed_later, null)
                            .show();
                    }
                }, 500);
            }
        }
    }

    /**
     * 设置 ChromeClient 高级回调：文件上传、全屏视频、网页权限。
     */
    private void setupChromeClientCallbacks() {
        if (controller == null) return;
        if (BuildConfig.BROWSER_REAL_MULTI_TAB) {
            // A2: 多 Tab 模式下 ChromeClient 由 pool 为每个 WebView 创建，
            // 这里把配置器注册到 pool，使后续每个新建 WebView 的 ChromeClient 都应用宿主回调。
            controller.setChromeClientConfigurator(this::configureChromeClient);
            return;
        }
        BrowserChromeClient chromeClient = controller.getChromeClient();
        if (chromeClient == null) return;
        configureChromeClient(chromeClient);
    }

    /** A2: 将文件上传/全屏/权限回调应用到指定 ChromeClient（单/多 Tab 共用）。 */
    private void configureChromeClient(@NonNull BrowserChromeClient chromeClient) {
        // 文件上传（支持多选）
        chromeClient.setFileChooserCallback((callback, params) -> {
            if (filePathCallback != null) {
                filePathCallback.onReceiveValue(null);
            }
            filePathCallback = callback;
            String[] types = params.getAcceptTypes();
            if (types == null || types.length == 0 || (types.length == 1 && types[0].isEmpty())) {
                types = new String[]{"*/*"};
            }
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            if (types.length == 1) {
                intent.setType(types[0]);
            } else {
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, types);
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                        params.getMode() == android.webkit.WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE);
            }
            try {
                fileChooserLauncher.launch(intent);
            } catch (Exception e) {
                callback.onReceiveValue(null);
                filePathCallback = null;
                Toast.makeText(requireContext(), R.string.browser_file_chooser_error, Toast.LENGTH_SHORT).show();
            }
        });

        // 全屏视频
        chromeClient.setFullscreenCallback(new BrowserChromeClient.FullscreenCallback() {
            @Override
            public void onShowCustomView(View view, android.webkit.WebChromeClient.CustomViewCallback callback) {
                if (customView != null) {
                    callback.onCustomViewHidden();
                    return;
                }
                customView = view;
                customViewCallback = callback;
                showCustomView();
            }

            @Override
            public void onHideCustomView() {
                hideCustomView();
            }

            @Override
            public boolean isCustomViewShowing() {
                return customView != null;
            }
        });

        // 网页权限
        chromeClient.setPermissionCallback(new BrowserChromeClient.PermissionCallback() {
            @Override
            public void onGeolocationPermissionRequest(String origin,
                    android.webkit.GeolocationPermissions.Callback callback) {
                new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.browser_permission_location_title)
                    .setMessage(getString(R.string.browser_permission_location_message, origin))
                    .setPositiveButton(R.string.browser_permission_allow, (d, w) ->
                        callback.invoke(origin, true, false))
                    .setNegativeButton(R.string.browser_permission_deny, (d, w) ->
                        callback.invoke(origin, false, false))
                    .setOnCancelListener(d -> callback.invoke(origin, false, false))
                    .show();
            }

            @Override
            public void onPermissionRequest(android.webkit.PermissionRequest request) {
                String[] resources = request.getResources();
                java.util.List<String> permissions = new java.util.ArrayList<>();
                boolean needsCamera = false;
                boolean needsAudio = false;
                for (String res : resources) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(res)) needsCamera = true;
                    else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(res)) needsAudio = true;
                }
                if (needsCamera) permissions.add(Manifest.permission.CAMERA);
                if (needsAudio) permissions.add(Manifest.permission.RECORD_AUDIO);

                if (permissions.isEmpty()) {
                    request.grant(resources);
                    return;
                }

                // 先检查是否已授予运行时权限
                boolean allGranted = true;
                for (String perm : permissions) {
                    if (ContextCompat.checkSelfPermission(requireContext(), perm) != PackageManager.PERMISSION_GRANTED) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted) {
                    request.grant(resources);
                    return;
                }

                StringBuilder sb = new StringBuilder();
                sb.append(getString(R.string.browser_permission_media_message)).append("\n");
                if (needsCamera) sb.append("- ").append(getString(R.string.browser_permission_camera)).append("\n");
                if (needsAudio) sb.append("- ").append(getString(R.string.browser_permission_microphone)).append("\n");
                new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.browser_permission_media_title)
                    .setMessage(sb.toString().trim())
                    .setPositiveButton(R.string.browser_permission_allow, (d, w) -> {
                        pendingPermissionRequest = request;
                        mediaPermissionLauncher.launch(permissions.toArray(new String[0]));
                    })
                    .setNegativeButton(R.string.browser_permission_deny, (d, w) -> request.deny())
                    .setOnCancelListener(d -> request.deny())
                    .show();
            }
        });
    }

    private void showCustomView() {
        if (customView == null || getView() == null) return;
        FrameLayout root = (FrameLayout) getView();
        root.addView(customView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (topBar != null) topBar.setVisibility(View.GONE);
        if (bottomBar != null) bottomBar.setVisibility(View.GONE);
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (getActivity() != null) {
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        setFullscreen(true);
    }

    private void hideCustomView() {
        if (customView == null || getView() == null) return;
        FrameLayout root = (FrameLayout) getView();
        root.removeView(customView);
        customView = null;
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
        if (topBar != null) topBar.setVisibility(View.VISIBLE);
        if (bottomBar != null) bottomBar.setVisibility(View.VISIBLE);
        if (getActivity() != null) {
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
        setFullscreen(false);
    }

    private void setFullscreen(boolean fullscreen) {
        if (getActivity() == null) return;
        if (fullscreen) {
            getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getActivity().getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        } else {
            getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            getActivity().getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }
}
