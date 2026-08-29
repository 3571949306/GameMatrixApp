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
import com.gamecenter.app.browser.core.incognito.IncognitoProfileManager;
import com.gamecenter.app.browser.core.lifecycle.BrowserRebindContract;
import com.gamecenter.app.browser.core.player.BrowserVideoController;
import com.gamecenter.app.browser.core.player.BrowserVideoState;
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
import java.util.concurrent.RejectedExecutionException;

/**
 * Browser Fragment - 核心浏览器页面。
 */
public class BrowserFragment extends Fragment implements
        BrowserWebViewClient.PageLoadCallback,
        BrowserWebViewClient.ExternalUrlHandler,
        BrowserChromeClient.PageInfoCallback,
        BrowserSettingsManager.OnSettingsChangeListener,
        BrowserRebindContract {

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

    // ===== 内置视频播放器（接管网页播放器 + 长按倍速快进） =====
    @Nullable private BrowserVideoController videoController;
    @Nullable private BrowserPlayerOverlay playerOverlay;
    @Nullable private FrameLayout playerOverlayContainer;
    @Nullable private TextView btnVideoPlayerEntry;

    // H-5 播放历史与续播
    @Nullable private com.gamecenter.app.browser.core.player.BrowserPlayHistoryStore playHistoryStore;
    /** 接管后待应用的续播目标（页面 URL）；时长探明后一次性消费。 */
    @Nullable private String pendingResumeUrl;
    private long lastPlayHistoryRecordUptimeMs = 0L;

    /** 播放器与宿主的交互契约：全屏时隐去浏览器顶/底栏。 */
    private final BrowserPlayerOverlay.Host playerHost = new BrowserPlayerOverlay.Host() {
        @Override
        public void onPlayerExit() {
            exitVideoPlayer(true);
        }

        @Override
        public void onRequestChromeHidden(boolean hidden) {
            if (topBar != null) topBar.setVisibility(hidden ? View.GONE : View.VISIBLE);
            if (bottomBar != null) bottomBar.setVisibility(hidden ? View.GONE : View.VISIBLE);
        }

        @Override
        public void onMiniModeChanged(boolean mini) {
            // 小窗态仍留在浏览器内，无需宿主额外处理
        }

        @Override
        public void onDownloadVideo(@NonNull String videoUrl) {
            // H-1：直链视频下载。文件名来自 URL 末段，经 FileNameSanitizer 净化后
            // 交给 BrowserDownloadManager（危险扩展名/MIME 会自动路由到 app 私有目录）。
            if (!isAdded() || getContext() == null) return;
            String fileName = suggestVideoFileName(videoUrl);
            String userAgent = null;
            WebView active = controller != null ? controller.getWebView() : null;
            if (active != null) {
                try { userAgent = active.getSettings().getUserAgentString(); } catch (Throwable ignored) {}
            }
            long id = BrowserDownloadManager.getInstance(getContext())
                    .downloadFile(videoUrl, fileName, "video/mp4", userAgent);
            if (id > 0) {
                showFeedback(R.string.browser_player_download_started);
            } else {
                showFeedback(R.string.browser_player_download_failed);
            }
        }
    };

    private boolean isLoading = false;
    private boolean isDesktopMode = false;
    private boolean isIncognitoMode = false;
    private boolean hasPageError = false;
    private String currentTitle = "";
    private final Object ioExecutorLock = new Object();
    @Nullable private ExecutorService ioExecutor;
    private boolean viewActive;
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
                        // 池已重建，播放器需重新绑定（旧 WebView 全部销毁）
                        bindVideoPlayerToActiveWebView();
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
    @Nullable private WebView pendingPermissionWebView;
    @Nullable private String pendingPermissionTabId;
    @Nullable private AlertDialog pendingPermissionDialog;
    private int permissionGeneration;
    private int pendingPermissionGeneration = -1;
    /** Android runtime permission UI may pause the host Activity while it is in flight. */
    private boolean mediaPermissionInFlight;

    // 地理位置是 WebChromeClient 的异步回调，不能把回调只捕获在临时
    // AlertDialog 的 lambda 里：Fragment 切换/销毁后，旧对话框仍可能回写旧页面。
    @Nullable private android.webkit.GeolocationPermissions.Callback pendingGeolocationCallback;
    @Nullable private String pendingGeolocationOrigin;
    @Nullable private String pendingGeolocationTabId;
    @Nullable private AlertDialog pendingGeolocationDialog;
    private int geolocationGeneration;
    private final ActivityResultLauncher<String[]> mediaPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                mediaPermissionInFlight = false;
                PermissionRequest request = pendingPermissionRequest;
                WebView requestWebView = pendingPermissionWebView;
                String requestTabId = pendingPermissionTabId;
                int requestGeneration = pendingPermissionGeneration;
                clearPendingPermissionRequest();
                if (request == null) return;
                if (!isCurrentPermissionRequest(requestWebView, requestTabId, requestGeneration)) {
                    denyPermissionRequest(request);
                    return;
                }
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (!Boolean.TRUE.equals(granted)) { allGranted = false; break; }
                }
                if (allGranted) {
                    request.grant(request.getResources());
                } else {
                    denyPermissionRequest(request);
                }
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
        synchronized (ioExecutorLock) {
            viewActive = true;
            if (ioExecutor == null || ioExecutor.isShutdown()) {
                ioExecutor = Executors.newSingleThreadExecutor();
            }
        }
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
                controller.switchToTab(currentTab.getId(), controller.getDefaultHomeUrl(), currentTab);
                // 重新绑定下载监听（新 WebView 需要）
                controller.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) ->
                        handleDownload(url, contentDisposition, mimetype, userAgent));
                syncIncognitoState(currentTab);
            }
        }
        initBrowserHelpers();
        initHomePage();
        setupListeners();
        initVideoPlayer();
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
                    String safeInitialUrl = UrlUtils.processInput(initialUrl);
                    if (safeInitialUrl == null) {
                        if (BuildConfig.BROWSER_HOME_PAGE) {
                            showHomePage();
                            etUrl.setText("");
                        } else {
                            controller.loadUrl(homeUrl);
                            etUrl.setText(homeUrl);
                        }
                    } else {
                        controller.loadUrl(safeInitialUrl);
                        etUrl.setText(safeInitialUrl);
                    }
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

    // ===== 内置视频播放器 =====

    /**
     * 初始化内置播放器：创建控制器与覆盖层，并开始周期性探测页面视频。
     *
     * <p>双门控：构建期 Feature Flag {@code BROWSER_VIDEO_PLAYER} + 运行期用户设置，
     * 任一项关闭则整个能力不启用（探测脚本一次都不会注入）。
     */
    private void initVideoPlayer() {
        if (!BuildConfig.BROWSER_VIDEO_PLAYER || getContext() == null) return;
        BrowserSettingsManager settings = BrowserSettingsManager.getInstance(getContext());
        if (!settings.isVideoPlayerEnabled()) return;

        // H-5：播放历史（SharedPreferences 后端，IO 走 submitIo 的单线程池）
        final android.content.SharedPreferences playHistoryPrefs =
                getContext().getSharedPreferences("browser_play_history", android.content.Context.MODE_PRIVATE);
        playHistoryStore = new com.gamecenter.app.browser.core.player.BrowserPlayHistoryStore(
                new com.gamecenter.app.browser.core.player.BrowserPlayHistoryStore.Prefs() {
                    @Override
                    public String read() {
                        return playHistoryPrefs.getString("snapshot", "{}");
                    }

                    @Override
                    public void write(@NonNull String json) {
                        playHistoryPrefs.edit().putString("snapshot", json).apply();
                    }
                });

        videoController = new BrowserVideoController();
        videoController.setFastForwardRate(settings.getFastForwardRate());
        boolean longPressEnabled = settings.isLongPressFastForwardEnabled();
        videoController.setListener(new BrowserVideoController.VideoStateListener() {
            @Override
            public void onVideoDetected(@NonNull BrowserVideoState state) {
                onVideoStateChanged(state);
            }

            @Override
            public void onStateUpdated(@NonNull BrowserVideoState state) {
                onVideoStateChanged(state);
            }

            @Override
            public void onVideoGone() {
                hideVideoEntry();
                if (playerOverlay != null && playerOverlay.isShowing()) {
                    exitVideoPlayer(false);
                }
            }

            @Override
            public void onTakeOverFailed() {
                if (!isAdded()) return;
                // 两种接管模式都验证不过，控制器已回滚；这里收起覆盖层，
                // 把页面交回给网页自带播放器，并如实告知用户。
                BrowserPlayerOverlay overlay = playerOverlay;
                if (overlay != null && overlay.isShowing()) {
                    overlay.hide();
                }
                hideVideoEntry();
                showFeedback(R.string.browser_player_takeover_failed);
            }
        });

        ViewGroup overlayHost = playerOverlayContainer;
        if (overlayHost != null) {
            playerOverlay = new BrowserPlayerOverlay(getContext(), overlayHost,
                    webViewContainer, videoController, playerHost);
            playerOverlay.setFastForwardRate(settings.getFastForwardRate());
            playerOverlay.setLongPressEnabled(longPressEnabled);
        }
        if (btnVideoPlayerEntry != null) {
            btnVideoPlayerEntry.setOnClickListener(v -> enterVideoPlayer());
        }
        bindVideoPlayerToActiveWebView();
        videoController.startProbing();
    }

    /** 切 Tab / 重建 WebView 后重新绑定到当前 WebView。 */
    private void bindVideoPlayerToActiveWebView() {
        if (videoController == null) return;
        // 接管态无法跨 WebView 保持（那是另一份 DOM），先退出再重绑
        if (playerOverlay != null && playerOverlay.isShowing()) {
            exitVideoPlayer(false);
        }
        hideVideoEntry();
        videoController.bind(controller != null ? controller.getWebView() : null);
    }

    /** 检测状态变化：刷新覆盖层，并在未接管时浮出入入口按钮。 */
    private void onVideoStateChanged(@NonNull BrowserVideoState state) {
        if (!isAdded()) return;
        boolean overlayActive = playerOverlay != null && playerOverlay.isShowing();
        if (overlayActive && playerOverlay != null) {
            playerOverlay.onStateUpdated(state);
            maybeApplyPendingResume(state);
            maybeRecordPlayProgress(state, false);
        }
        if (state.hasVideo() && !overlayActive) {
            if (btnVideoPlayerEntry != null) btnVideoPlayerEntry.setVisibility(View.VISIBLE);
        } else if (!state.hasVideo()) {
            hideVideoEntry();
        }
    }

    /** 内置播放器入口：接管页面 video 元素并铺上原生控件。 */
    private void enterVideoPlayer() {
        BrowserVideoController vc = videoController;
        if (vc == null || getContext() == null) return;
        BrowserVideoState state = vc.getState();
        if (!state.hasVideo()) {
            showFeedback(R.string.browser_player_no_video);
            return;
        }
        if (!vc.takeOver(false)) {
            showFeedback(R.string.browser_player_no_video);
            return;
        }
        // H-5：接管成功后准备续播（时长探明后在状态回调里一次性应用）
        pendingResumeUrl = controller != null ? controller.getCurrentUrl() : null;
        lastPlayHistoryRecordUptimeMs = 0L;
        if (playerOverlay != null) {
            playerOverlay.show();
            playerOverlay.onStateUpdated(state);
        }
        hideVideoEntry();
        showFeedback(R.string.browser_player_entered);
    }

    /** 退出接管：还原页面 DOM 与样式，收起覆盖层。 */
    private void exitVideoPlayer(boolean notify) {
        BrowserPlayerOverlay overlay = playerOverlay;
        if (overlay != null) overlay.hide();
        BrowserVideoController vc = videoController;
        // H-5：退出前把最终进度落盘（绕过节流）
        if (vc != null) {
            maybeRecordPlayProgress(vc.getState(), true);
        }
        pendingResumeUrl = null;
        if (vc != null) vc.releaseTakeOver();
        if (notify) showFeedback(R.string.browser_player_released);
        // 视频仍在页面上时把入口还给用户
        if (btnVideoPlayerEntry != null && vc != null && vc.getState().hasVideo() && isAdded()) {
            btnVideoPlayerEntry.setVisibility(View.VISIBLE);
        }
    }

    private void hideVideoEntry() {
        if (btnVideoPlayerEntry != null) btnVideoPlayerEntry.setVisibility(View.GONE);
    }

    /**
     * H-1：从直链 URL 推导下载文件名。
     *
     * <p>取 URL 最后一个路径段并经 {@link FileNameSanitizer} 净化（去查询串、
     * 路径穿越与控制字符）；拿不到合法名字时回退 {@code video.mp4}。
     */
    @NonNull
    private static String suggestVideoFileName(@NonNull String videoUrl) {
        String name = "";
        try {
            String path = android.net.Uri.parse(videoUrl).getPath();
            if (path != null) {
                int slash = path.lastIndexOf('/');
                name = slash >= 0 ? path.substring(slash + 1) : path;
            }
        } catch (Throwable ignored) {
        }
        String sanitized = com.gamecenter.app.browser.core.security.FileNameSanitizer.sanitize(name);
        if (sanitized.isEmpty()) {
            return "video.mp4";
        }
        // 无扩展名时补 .mp4，避免 DownloadManager 走 */* 的嗅探
        int dot = sanitized.lastIndexOf('.');
        if (dot <= 0 || dot == sanitized.length() - 1) {
            sanitized = sanitized + ".mp4";
        }
        return sanitized;
    }

    /**
     * H-5：接管后首次拿到有效时长时，应用一次续播。
     *
     * <p>页面 URL 是历史键（视频源多为会话级 blob:，不能当键）。
     * 时长未知 / 已看完 / 进度太短都不恢复，避免骚扰。
     */
    private void maybeApplyPendingResume(@NonNull BrowserVideoState state) {
        String pageUrl = pendingResumeUrl;
        if (pageUrl == null || state.durationMs <= 0) return;
        pendingResumeUrl = null; // 只消费一次
        com.gamecenter.app.browser.core.player.BrowserPlayHistoryStore store = playHistoryStore;
        if (store == null) return;
        com.gamecenter.app.browser.core.player.BrowserPlayHistoryStore.Entry entry =
                store.resumeOf(pageUrl);
        if (entry == null
                || !com.gamecenter.app.browser.core.player.BrowserPlayHistoryStore
                        .shouldResume(entry.positionMs, state.durationMs)) {
            return;
        }
        BrowserVideoController vc = videoController;
        if (vc == null) return;
        vc.seekTo(entry.positionMs);
        showFeedback(getString(R.string.browser_player_resumed,
                com.gamecenter.app.browser.core.player.BrowserPlayerMath
                        .formatTime(entry.positionMs)));
    }

    /**
     * H-5：节流记录播放进度（键为页面 URL，IO 走 submitIo）。
     *
     * @param force true 时绕过节流（退出接管前的最终落盘）
     */
    private void maybeRecordPlayProgress(@NonNull BrowserVideoState state, boolean force) {
        com.gamecenter.app.browser.core.player.BrowserPlayHistoryStore store = playHistoryStore;
        if (store == null || controller == null) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (!force && now - lastPlayHistoryRecordUptimeMs
                < com.gamecenter.app.browser.core.player.BrowserPlayHistoryStore.RECORD_INTERVAL_MS) {
            return;
        }
        lastPlayHistoryRecordUptimeMs = now;
        final String pageUrl = controller.getCurrentUrl();
        if (pageUrl == null || pageUrl.isEmpty()) return;
        final String title = state.title != null ? state.title : "";
        final long positionMs = state.currentTimeMs;
        final long durationMs = state.durationMs;
        final long wallClockMs = System.currentTimeMillis();
        submitIo(() -> store.record(pageUrl, title, positionMs, durationMs, wallClockMs));
    }

    /**
     * H-5 销毁路径专用：同步落盘。
     *
     * <p>onDestroyView 尾部会对 ioExecutor 调 shutdownNow（丢弃排队任务），
     * 因此销毁前的最终进度不能走 submitIo；SharedPreferences.apply() 非阻塞
     * 且线程安全，可直接在主线程写。
     */
    private void recordPlayProgressSync(@NonNull BrowserVideoState state) {
        com.gamecenter.app.browser.core.player.BrowserPlayHistoryStore store = playHistoryStore;
        if (store == null || controller == null) return;
        String pageUrl = controller.getCurrentUrl();
        if (pageUrl == null || pageUrl.isEmpty()) return;
        String title = state.title != null ? state.title : "";
        store.record(pageUrl, title, state.currentTimeMs, state.durationMs, System.currentTimeMillis());
    }

    /**
     * 设置变更时同步内置播放器：总开关与长按快进倍速。
     *
     * <p>关掉开关必须真正停掉探测脚本（而不是只藏 UI），否则"关闭"形同虚设。
     */
    private void applyVideoPlayerSettings() {
        if (!BuildConfig.BROWSER_VIDEO_PLAYER || getContext() == null) return;
        BrowserSettingsManager settings = BrowserSettingsManager.getInstance(getContext());

        if (!settings.isVideoPlayerEnabled()) {
            if (playerOverlay != null && playerOverlay.isShowing()) exitVideoPlayer(false);
            hideVideoEntry();
            if (videoController != null) videoController.stopProbing();
            return;
        }

        if (videoController == null) {
            // 用户重新开启：按需初始化（initVideoPlayer 内部有幂等的前置判断）
            initVideoPlayer();
            return;
        }
        float rate = settings.getFastForwardRate();
        videoController.setFastForwardRate(rate);
        boolean longPressEnabled = settings.isLongPressFastForwardEnabled();
        if (playerOverlay != null) {
            playerOverlay.setFastForwardRate(rate);
            playerOverlay.setLongPressEnabled(longPressEnabled);
        }
        videoController.startProbing();
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

        bindGestureNavigation(webView, view);
        android.util.Log.d(TAG, "setupGestureNavigation: listener set on webView");
    }

    /** Bind the existing gesture recognizer to the active WebView after a tab swap. */
    private void bindGestureNavigation(@NonNull WebView webView, @Nullable View hostView) {
        if (gestureHelper == null) return;
        try {
            webView.setOnTouchListener((v, event) -> gestureHelper.onTouch(event));
        } catch (Throwable ignored) {
            return;
        }
        // Android 10+ needs the exclusion rect on every newly-created WebView,
        // not only the WebView that happened to be active at Fragment creation.
        applySystemGestureExclusion(webView);
        if (hostView != null) applySystemGestureExclusion(hostView);
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
                // 0. 退出内置播放器（含小窗）
                if (playerOverlay != null && playerOverlay.isShowing()) {
                    exitVideoPlayer(true);
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

        // 内置视频播放器
        playerOverlayContainer = view.findViewById(R.id.player_overlay_container);
        btnVideoPlayerEntry = view.findViewById(R.id.btn_video_player_entry);

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
        Context context = getContext();
        if (context == null || !isAdded()) return;
        String name = android.webkit.URLUtil.guessFileName(downloadUrl, contentDisposition, mimeType);
        if (name == null || name.isEmpty()) {
            name = "download_" + System.currentTimeMillis();
        }
        final String fileName = name;
        final String url = downloadUrl;
        BrowserDownloadManager downloadMgr = BrowserDownloadManager.getInstance(context);
        boolean isDangerous = downloadMgr.isDangerousFile(fileName, mimeType);

        String message = context.getString(R.string.browser_download_dangerous_message, fileName);
        new AlertDialog.Builder(context)
            .setTitle(isDangerous ? R.string.browser_download_dangerous_title : R.string.browser_download_title)
            .setMessage(isDangerous ? message : fileName)
            .setPositiveButton(isDangerous ? R.string.browser_download_dangerous_confirm : android.R.string.ok, (d, w) -> {
                long downloadId = downloadMgr.downloadFile(url, fileName, mimeType, userAgent);
                if (downloadId >= 0L) {
                    safeToast(R.string.browser_download_started, fileName);
                } else {
                    safeToast(R.string.browser_download_start_failed);
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> {
            if (controller == null) return;
            // 0. 先退出内置播放器
            if (playerOverlay != null && playerOverlay.isShowing()) {
                exitVideoPlayer(true);
                return;
            }
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
                // setText() above runs the URL TextWatcher while this EditText still has focus.
                // Invalidate its delayed/asynchronous result before hiding the popup; otherwise a
                // stale suggestion can reappear over the newly loading page after navigation.
                suggestionSessionId++;
                cancelPendingUrlSuggestions();
                hideUrlSuggestions();
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
        submitIo(() -> {
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
        submitIo(() -> {
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
        submitIo(() -> {
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
        submitIo(() -> {
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
        submitIo(() -> {
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
        // 内置播放器：仅在检测到视频时可见
        MenuItem videoItem = popup.getMenu().findItem(R.id.menu_video_player);
        if (videoItem != null) {
            videoItem.setVisible(BuildConfig.BROWSER_VIDEO_PLAYER && videoController != null
                    && videoController.getState().hasVideo()
                    && !(playerOverlay != null && playerOverlay.isShowing()));
        }
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
                            // 统一走切 Tab 入口：它会在首次加载前应用 Tab profile，
                            // 并重绑手势、查找、阅读模式与播放器到新 WebView。
                            switchToTabById(newTab.getId());
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
            } else if (id == R.id.menu_video_player) {
                enterVideoPlayer();
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
        if (!isAdded() || getContext() == null) return;
        Context ctx = getContext();
        if (!BrowserSecurityPolicy.getInstance().canExternalAppHandle(ctx, url)) {
            safeToast(R.string.browser_open_external_failed);
            return;
        }
        new AlertDialog.Builder(ctx)
            .setTitle(R.string.browser_security_external_title)
            .setMessage(ctx.getString(R.string.browser_security_external_message, url))
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

        // In real multi-tab mode, incognito is a tab property. The old implementation
        // toggled a Fragment-wide boolean and removed the process-wide CookieManager,
        // which could log ordinary tabs out. We currently provide the honest, safe
        // subset (no history/search persistence); true cookie isolation needs a
        // process/profile design and must not be faked by clearing global cookies.
        if (BuildConfig.BROWSER_REAL_MULTI_TAB) {
            if (tabManager == null) tabManager = BrowserTabManager.getInstance(getContext());
            BrowserTabManager.Tab incognito = tabManager.createIncognitoTab(null);
            if (incognito == null) {
                safeToast(R.string.browser_tab_max_reached);
                return;
            }
            switchToTabById(incognito.getId());
            syncIncognitoState(incognito);
            safeToast(R.string.browser_incognito_on);
            return;
        }

        isIncognitoMode = !isIncognitoMode;
        if (incognitoIndicator != null) {
            incognitoIndicator.setVisibility(isIncognitoMode ? View.VISIBLE : View.GONE);
        }
        safeToast(isIncognitoMode ? R.string.browser_incognito_on : R.string.browser_incognito_off);
    }

    /** 同步当前 Tab 的无痕展示状态；不触碰全局 Cookie/WebStorage。 */
    private void syncIncognitoState(@Nullable BrowserTabManager.Tab tab) {
        isIncognitoMode = tab != null && tab.isIncognito();
        if (isIncognitoMode && controller != null) {
            IncognitoProfileManager.applyProfile(controller.getWebView(), tab);
        }
        if (incognitoIndicator != null) {
            incognitoIndicator.setVisibility(isIncognitoMode ? View.VISIBLE : View.GONE);
        }
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
        WebView wv = controller.switchToTab(tabId, fallback, tab);
        if (wv == null) return;
        syncIncognitoState(tab);
        cancelPendingPermissionRequest();
        cancelPendingGeolocationRequest();
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
        rebindHelpers(wv);
    }

    /**
     * Single rebind point for helpers whose callbacks are attached to one concrete
     * WebView. Any path that changes the active tab must call this method.
     */
    @Override
    public void rebindHelpers(@NonNull WebView webView) {
        bindGestureNavigation(webView, getView());
        // 阅读模式是按 WebView 的状态，切换后先退出旧 Tab 的阅读模式再重绑。
        if (readerModeHelper != null && readerModeHelper.isActive()) {
            readerModeHelper.exitReaderMode();
        }
        if (findInPageHelper != null) {
            findInPageHelper.bind(webView, etFindQuery, tvFindMatchCount,
                    btnFindPrev, btnFindNext, btnFindClose, findInPageHostCallback);
        }
        if (readerModeHelper != null) {
            readerModeHelper.bind(webView, readerModeCallback);
        }
        // 内置播放器：接管态绑定的是旧 Tab 的 DOM，必须重绑到新 WebView
        bindVideoPlayerToActiveWebView();
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
                dialog.dismiss();
                BrowserTabManager.Tab selected = tabs.get(which);
                if (BuildConfig.BROWSER_REAL_MULTI_TAB) {
                    switchToTabById(selected.getId());
                } else if (controller != null && selected.getUrl() != null) {
                    tabManager.switchTab(selected.getId());
                    controller.loadUrl(selected.getUrl());
                    etUrl.setText(selected.getUrl());
                }
            })
            .setPositiveButton(R.string.browser_tab_new, (d, w) -> {
                BrowserTabManager.Tab newTab = tabManager.createTab(null);
                if (newTab != null && controller != null) {
                    if (BuildConfig.BROWSER_REAL_MULTI_TAB) {
                        switchToTabById(newTab.getId());
                    } else {
                        controller.loadUrl(controller.getDefaultHomeUrl());
                        etUrl.setText(controller.getDefaultHomeUrl());
                    }
                }
            })
            .setNeutralButton(R.string.browser_tab_close_current, (d, w) -> {
                if (current != null) {
                    tabManager.closeTab(current.getId());
                    BrowserTabManager.Tab next = tabManager.getCurrentTab();
                    if (next != null && next.getUrl() != null && controller != null) {
                        if (BuildConfig.BROWSER_REAL_MULTI_TAB) {
                            switchToTabById(next.getId());
                        } else {
                            controller.loadUrl(next.getUrl());
                            etUrl.setText(next.getUrl());
                        }
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
        submitIo(() -> {
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
        cancelPendingPermissionRequest();
        cancelPendingGeolocationRequest();
        // A navigation invalidates the previous page's video state and native overlay.
        // Otherwise the next page could inherit a stale entry/control surface.
        if (videoController != null) bindVideoPlayerToActiveWebView();
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

        // 内置播放器：页面加载完成立即探测一次，省去等待下一个探测周期
        if (videoController != null) videoController.probeOnce();

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
        // controller == null 代表 View 已销毁；迟到的 Chromium 回调不能再驱动
        // 新一轮 View 的 UI、历史或播放器状态。
        return controller != null && (tabId == null
                || tabId.equals(controller.getActiveTabId()));
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

    /**
     * Submit work tied to the current Fragment view lifecycle.
     *
     * <p>The view can be destroyed and later recreated on the same Fragment instance.
     * Keeping a final executor made the second view throw RejectedExecutionException;
     * keeping a process-lifetime executor leaked a Fragment-owned worker. The worker is
     * now recreated per view and submissions after teardown are ignored.
     */
    private void submitIo(@NonNull Runnable task) {
        synchronized (ioExecutorLock) {
            if (!viewActive) return;
            if (ioExecutor == null || ioExecutor.isShutdown()) {
                ioExecutor = Executors.newSingleThreadExecutor();
            }
            try {
                ioExecutor.execute(task);
            } catch (RejectedExecutionException ignored) {
                // A teardown can race with a final callback; the view is already gone.
            }
        }
    }

    /** Invalidate a pending media permission when its WebView/view lifecycle changes. */
    private void cancelPendingPermissionRequest() {
        mediaPermissionInFlight = false;
        permissionGeneration++;
        PermissionRequest request = pendingPermissionRequest;
        clearPendingPermissionRequest();
        if (request != null) denyPermissionRequest(request);
    }

    /** 取消并拒绝当前地理位置请求，防止旧页面/旧 Fragment 回调泄漏到新页面。 */
    private void cancelPendingGeolocationRequest() {
        geolocationGeneration++;
        android.webkit.GeolocationPermissions.Callback callback = pendingGeolocationCallback;
        String origin = pendingGeolocationOrigin;
        clearPendingGeolocationRequest();
        if (callback != null) {
            try { callback.invoke(origin != null ? origin : "", false, false); }
            catch (Throwable ignored) {}
        }
    }

    private void clearPendingGeolocationRequest() {
        if (pendingGeolocationDialog != null) {
            try { pendingGeolocationDialog.dismiss(); } catch (Throwable ignored) {}
            pendingGeolocationDialog = null;
        }
        pendingGeolocationCallback = null;
        pendingGeolocationOrigin = null;
        pendingGeolocationTabId = null;
    }

    /** 仅允许当前代次的地理位置对话框回写 WebView；过期请求一律拒绝。 */
    private void resolveGeolocationRequest(
            @NonNull android.webkit.GeolocationPermissions.Callback callback,
            @NonNull String origin,
            @Nullable String requestTabId,
            int requestGeneration,
            boolean allow) {
        if (callback != pendingGeolocationCallback
                || pendingGeolocationOrigin == null
                || !origin.equals(pendingGeolocationOrigin)
                || (requestTabId == null
                    ? pendingGeolocationTabId != null
                    : !requestTabId.equals(pendingGeolocationTabId))) {
            return; // 已由生命周期取消并拒绝，避免二次 invoke
        }
        String activeTabId = controller != null ? controller.getActiveTabId() : null;
        boolean current = requestGeneration == geolocationGeneration
                && isAdded() && isResumed() && getView() != null && controller != null;
        current = current && (requestTabId == null
                ? activeTabId == null : requestTabId.equals(activeTabId));
        clearPendingGeolocationRequest();
        try {
            callback.invoke(origin, current && allow, false);
        } catch (Throwable ignored) {}
    }

    private void clearPendingPermissionRequest() {
        if (pendingPermissionDialog != null) {
            try { pendingPermissionDialog.dismiss(); } catch (Throwable ignored) {}
            pendingPermissionDialog = null;
        }
        pendingPermissionRequest = null;
        pendingPermissionWebView = null;
        pendingPermissionTabId = null;
        pendingPermissionGeneration = -1;
    }

    private boolean isCurrentPermissionRequest(@Nullable WebView requestWebView,
                                               @Nullable String requestTabId,
                                               int requestGeneration) {
        if (!isAdded() || !isResumed() || getView() == null || controller == null) return false;
        if (requestGeneration != permissionGeneration) return false;
        if (requestWebView == null || requestWebView != controller.getWebView()) return false;
        String currentTabId = controller.getActiveTabId();
        return requestTabId == null ? currentTabId == null : requestTabId.equals(currentTabId);
    }

    private void denyPermissionRequest(@NonNull PermissionRequest request) {
        try { request.deny(); } catch (Throwable ignored) {}
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
        if (videoController != null) videoController.startProbing();
        if (controller != null && getContext() != null) {
            controller.onResume(getContext());
            // P1-1：onResume 时重新应用设置，确保系统夜间模式变化后
            // 自动模式（DARK_MODE_AUTO）能正确刷新 WebView 的 forceDark 状态
            controller.applySettings(getContext());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Launching the Android runtime permission dialog can itself pause this
        // Activity. Keep the WebView request alive until ActivityResult returns;
        // real navigation/tab/view teardown still cancels it through the other
        // lifecycle paths (and onDestroyView always cancels unconditionally).
        if (!mediaPermissionInFlight) cancelPendingPermissionRequest();
        cancelPendingGeolocationRequest();
        if (controller != null) controller.onPause();
        // 内置播放器：手指可能还按在屏幕上（切模块/锁屏），必须撤销倍速；同时停止探测省电
        if (playerOverlay != null) playerOverlay.cancelGestures();
        if (videoController != null) videoController.stopProbing();
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
            if (!mediaPermissionInFlight) cancelPendingPermissionRequest();
            cancelPendingGeolocationRequest();
            // 内置播放器：切走模块时撤销长按倍速并停止探测
            if (playerOverlay != null) playerOverlay.cancelGestures();
            if (videoController != null) videoController.stopProbing();
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
        cancelPendingPermissionRequest();
        cancelPendingGeolocationRequest();
        if (filePathCallback != null) {
            try { filePathCallback.onReceiveValue(null); } catch (Throwable ignored) {}
            filePathCallback = null;
        }
        if (customView != null) {
            // 文件选择器/全屏回调都可能晚于 View 生命周期返回；先释放
            // 自定义视图和 Activity 全屏状态，再销毁 WebView。
            hideCustomView();
        }
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
        if (urlInputHelper != null) {
            urlInputHelper.destroy();
            urlInputHelper = null;
        }
        // P0 内存泄漏修复：清理 BrowserHomeHelper（之前完全遗漏，导致 View 树和站点图标 Bitmap 无法回收）
        if (homeHelper != null) { homeHelper.destroy(); homeHelper = null; }
        // 内置播放器：先拆 UI（依赖 controller），再还原页面 DOM，最后销毁 controller。
        // 顺序不能反：releaseTakeOver 需要 WebView 还在，否则被接管的 video 元素
        // 会永久停留在 body 末尾并保持 fixed 铺满样式。
        if (playerOverlay != null) { playerOverlay.destroy(); playerOverlay = null; }
        if (videoController != null) {
            // H-5：销毁前把最终播放进度同步落盘（worker 即将被 shutdownNow）
            recordPlayProgressSync(videoController.getState());
            videoController.releaseTakeOver();
            videoController.destroy();
            videoController = null;
        }
        playHistoryStore = null;
        pendingResumeUrl = null;
        if (controller != null) { controller.destroy(); controller = null; }
        if (getContext() != null) {
            BrowserDownloadManager.getInstance(getContext()).shutdown();
        }
        // P0 内存泄漏修复：兜底清理所有 mainHandler 延迟任务（含 pendingRefreshPrompt 等）
        mainHandler.removeCallbacksAndMessages(null);
        // P0 内存泄漏修复：关闭当前 View 的 IO worker；下一次 onViewCreated
        // 会创建新的 worker，避免同一 Fragment 重建后提交到已关闭线程池。
        synchronized (ioExecutorLock) {
            viewActive = false;
            ExecutorService executor = ioExecutor;
            ioExecutor = null;
            if (executor != null && !executor.isShutdown()) {
                executor.shutdownNow();
            }
        }
        super.onDestroyView();
    }

    @Override
    public void onSettingsChanged(int reloadRequired) {
        applyVideoPlayerSettings();
        if (controller != null && getContext() != null) {
            controller.applySettings(getContext());
            if (reloadRequired == BrowserSettingsManager.RELOAD_REQUIRED && !pendingRefreshPrompt) {
                pendingRefreshPrompt = true;
                mainHandler.postDelayed(() -> {
                    pendingRefreshPrompt = false;
                    Context context = getContext();
                    if (controller != null && isAdded() && context != null) {
                        new AlertDialog.Builder(context)
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
                safeToast(R.string.browser_file_chooser_error);
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
            public void onGeolocationPermissionRequest(@Nullable String requestTabId,
                    String origin,
                    android.webkit.GeolocationPermissions.Callback callback) {
                if (callback == null) return;
                if (origin == null || controller == null
                        || (requestTabId != null && !requestTabId.equals(controller.getActiveTabId()))
                        || !isAdded() || !isResumed()
                        || getView() == null || getContext() == null) {
                    try { callback.invoke(origin != null ? origin : "", false, false); }
                    catch (Throwable ignored) {}
                    return;
                }
                // 只保留一个待处理请求；新请求到达时旧请求被明确拒绝，避免
                // 两个对话框交错回写同一个 WebView 的地理权限状态。
                cancelPendingGeolocationRequest();
                final int requestGeneration = geolocationGeneration;
                pendingGeolocationCallback = callback;
                pendingGeolocationOrigin = origin;
                pendingGeolocationTabId = requestTabId;
                pendingGeolocationDialog = new AlertDialog.Builder(getContext())
                    .setTitle(R.string.browser_permission_location_title)
                    .setMessage(getString(R.string.browser_permission_location_message, origin))
                    .setPositiveButton(R.string.browser_permission_allow, (d, w) ->
                        resolveGeolocationRequest(callback, origin, requestTabId, requestGeneration, true))
                    .setNegativeButton(R.string.browser_permission_deny, (d, w) ->
                        resolveGeolocationRequest(callback, origin, requestTabId, requestGeneration, false))
                    .create();
                pendingGeolocationDialog.setOnCancelListener(d ->
                    resolveGeolocationRequest(callback, origin, requestTabId, requestGeneration, false));
                pendingGeolocationDialog.show();
            }

            @Override
            public void onPermissionRequest(@Nullable String requestTabId,
                    android.webkit.PermissionRequest request) {
                if (request == null || !isAdded() || !isResumed()
                        || getView() == null || controller == null
                        || controller.getWebView() == null
                        || (requestTabId != null && !requestTabId.equals(controller.getActiveTabId()))) {
                    if (request != null) denyPermissionRequest(request);
                    return;
                }
                // A second prompt must not replace the request currently waiting for
                // the system permission dialog; deny it deterministically.
                if (pendingPermissionRequest != null) {
                    denyPermissionRequest(request);
                    return;
                }
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
                    if (ContextCompat.checkSelfPermission(getContext(), perm) != PackageManager.PERMISSION_GRANTED) {
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
                final WebView requestWebView = controller.getWebView();
                final int requestGeneration = permissionGeneration;
                pendingPermissionRequest = request;
                pendingPermissionWebView = requestWebView;
                pendingPermissionTabId = requestTabId;
                pendingPermissionGeneration = requestGeneration;

                pendingPermissionDialog = new AlertDialog.Builder(getContext())
                    .setTitle(R.string.browser_permission_media_title)
                    .setMessage(sb.toString().trim())
                    .setPositiveButton(R.string.browser_permission_allow, (d, w) -> {
                        if (!isCurrentPermissionRequest(requestWebView, requestTabId, requestGeneration)) {
                            cancelPendingPermissionRequest();
                            return;
                        }
                        try {
                            mediaPermissionInFlight = true;
                            mediaPermissionLauncher.launch(permissions.toArray(new String[0]));
                        } catch (RuntimeException e) {
                            mediaPermissionInFlight = false;
                            cancelPendingPermissionRequest();
                        }
                    })
                    .setNegativeButton(R.string.browser_permission_deny,
                            (d, w) -> cancelPendingPermissionRequest())
                    .create();
                pendingPermissionDialog.setOnCancelListener(d -> cancelPendingPermissionRequest());
                pendingPermissionDialog.show();
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
