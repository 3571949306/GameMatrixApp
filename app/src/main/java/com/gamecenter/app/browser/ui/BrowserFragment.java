package com.gamecenter.app.browser.ui;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
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
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.gamecenter.app.R;
import com.gamecenter.app.browser.core.BrowserChromeClient;
import com.gamecenter.app.browser.core.BrowserController;
import com.gamecenter.app.browser.core.BrowserSettingsManager;
import com.gamecenter.app.browser.core.BrowserTabManager;
import com.gamecenter.app.browser.core.BrowserWebViewClient;
import com.gamecenter.app.browser.data.BrowserDatabase;
import com.gamecenter.app.browser.data.BrowserDownloadManager;
import com.gamecenter.app.browser.data.entity.BrowserBookmarkEntity;
import com.gamecenter.app.browser.data.entity.BrowserHistoryEntity;
import com.gamecenter.app.browser.data.repository.SearchHistoryRepository;
import com.gamecenter.app.browser.security.BrowserSecurityPolicy;
import com.gamecenter.app.browser.util.UrlUtils;

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

    private BrowserController controller;
    private BrowserTabManager tabManager;
    private SearchHistoryRepository searchHistoryRepository;

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
    private LinearLayout topBar;
    private LinearLayout bottomBar;
    private TextView incognitoIndicator;

    private boolean isLoading = false;
    private boolean isDesktopMode = false;
    private boolean isIncognitoMode = false;
    private boolean hasPageError = false;
    private String defaultUserAgent;
    private String currentTitle = "";
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean pendingRefreshPrompt = false;
    private long lastBookmarkClickTime = 0;
    private static final long BOOKMARK_CLICK_DEBOUNCE_MS = 600;

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
        setupListeners();

        String initialUrl = null;
        if (getArguments() != null) {
            initialUrl = getArguments().getString(ARG_URL);
        }
        String homeUrl = getHomeUrl();
        if (initialUrl != null && !initialUrl.isEmpty()) {
            controller.loadUrl(initialUrl);
            etUrl.setText(initialUrl);
        } else {
            controller.loadUrl(homeUrl);
            etUrl.setText(homeUrl);
        }

        BrowserSettingsManager.getInstance(requireContext()).addListener(this);
    }

    private void initViews(View view) {
        webViewContainer = view.findViewById(R.id.webview_container);
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
        View btnRetry = view.findViewById(R.id.btn_retry);
        if (btnRetry != null) {
            btnRetry.setOnClickListener(v -> { if (controller != null) controller.reload(); });
        }
    }

    private void initWebView() {
        if (getContext() == null) return;
        WebView tempWebView = new WebView(getContext());
        defaultUserAgent = tempWebView.getSettings().getUserAgentString();
        tempWebView.destroy();
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
                Toast.makeText(getContext(), getString(R.string.browser_download_started, fileName), Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> {
            if (controller == null) return;
            if (customView != null) {
                hideCustomView();
                return;
            }
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
            String homeUrl = getHomeUrl();
            if (controller != null) controller.loadUrl(homeUrl);
            etUrl.setText(homeUrl);
        });
        btnBookmark.setOnClickListener(v -> toggleBookmark());
        btnTabs.setOnClickListener(v -> showTabList());
        btnDownload.setOnClickListener(v -> {
            if (getContext() != null) DownloadActivity.start(getContext());
        });
        btnMenu.setOnClickListener(v -> showMoreMenu(v));
        btnMore.setOnClickListener(v -> showMoreMenu(v));
        etUrl.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                String input = etUrl.getText().toString().trim();
                if (controller != null) controller.loadInput(input);
                saveSearchHistoryIfNeeded(input);
                hideKeyboard();
                return true;
            }
            return false;
        });
        etUrl.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) etUrl.selectAll(); });
    }

    private void saveSearchHistoryIfNeeded(@Nullable String input) {
        if (isIncognitoMode || input == null || input.isEmpty() || searchHistoryRepository == null) return;
        String processed = UrlUtils.processInput(input);
        if (processed != null && processed.startsWith(UrlUtils.SEARCH_ENGINE_URL)) {
            String engine = BrowserSettingsManager.getInstance(requireContext()).getSearchEngine();
            searchHistoryRepository.saveSearchHistory(input, engine);
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
                        Toast.makeText(getContext(), R.string.browser_btn_bookmark_remove, Toast.LENGTH_SHORT).show();
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
                        Toast.makeText(getContext(), R.string.browser_btn_bookmark_add, Toast.LENGTH_SHORT).show();
                        updateBookmarkIcon();
                    });
                }
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
        MenuItem desktopItem = popup.getMenu().findItem(R.id.menu_desktop);
        if (desktopItem != null) {
            desktopItem.setTitle(isDesktopMode ? R.string.browser_menu_desktop_on : R.string.browser_menu_desktop);
        }
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_new_tab) {
                if (tabManager == null && getContext() != null) {
                    tabManager = BrowserTabManager.getInstance(getContext());
                }
                if (tabManager != null) {
                    BrowserTabManager.Tab newTab = tabManager.createTab(null);
                    if (newTab != null && controller != null) {
                        controller.loadUrl(controller.getDefaultHomeUrl());
                        etUrl.setText(controller.getDefaultHomeUrl());
                    }
                }
                return true;
            } else if (id == R.id.menu_incognito) {
                toggleIncognitoMode();
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
            Toast.makeText(ctx, R.string.browser_open_external_failed, Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(getContext(), R.string.browser_open_external_failed, Toast.LENGTH_SHORT).show();
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
        Toast.makeText(getContext(),
            isIncognitoMode ? R.string.browser_incognito_on : R.string.browser_incognito_off,
            Toast.LENGTH_SHORT).show();
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
    public void onPageStarted(String url, Bitmap favicon) {
        isLoading = true;
        hasPageError = false;
        if (etUrl != null) etUrl.setText(url);
        if (progressBar != null) { progressBar.setVisibility(View.VISIBLE); progressBar.setProgress(0); }
        if (errorView != null) errorView.setVisibility(View.GONE);
        if (controller != null) controller.removeJsBridge();
    }

    @Override
    public void onPageFinished(String url) {
        isLoading = false;
        if (etUrl != null) etUrl.setText(url);
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (controller != null) currentTitle = controller.getTitle();
        saveHistoryIfNeeded(url);
        updateBookmarkIcon();

        // 更新当前标签页信息
        if (tabManager != null && controller != null) {
            BrowserTabManager.Tab currentTab = tabManager.getCurrentTab();
            if (currentTab != null && url != null) {
                tabManager.updateTabInfo(currentTab.getId(), currentTitle, url);
            }
        }

        // 仅在可信域名下注入 JSBridge
        if (controller != null && getContext() != null) {
            controller.injectJsBridge(getContext(), url);
        }
    }

    @Override
    public void onPageError(String url, String description) {
        isLoading = false;
        hasPageError = true;
        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (errorView != null) errorView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onReceivedSslError(String url) {
        if (getContext() != null) Toast.makeText(getContext(), R.string.browser_ssl_error, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onTitleChanged(String title) { currentTitle = title; }

    @Override
    public void onProgressChanged(int progress) {
        if (progressBar != null) {
            progressBar.setProgress(progress);
            if (progress >= 100) progressBar.setVisibility(View.GONE);
        }
    }

    @Override
    public void onReceivedIcon(Bitmap icon) {}

    @Override
    public void onResume() {
        super.onResume();
        if (controller != null && getContext() != null) {
            controller.onResume(getContext());
        } else if (controller != null) {
            controller.onResume(requireContext());
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (controller != null) controller.onPause();
    }

    @Override
    public void onDestroyView() {
        if (getContext() != null) {
            BrowserSettingsManager.getInstance(getContext()).removeListener(this);
        }
        if (controller != null) { controller.destroy(); controller = null; }
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
        BrowserChromeClient chromeClient = controller.getChromeClient();
        if (chromeClient == null) return;

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
