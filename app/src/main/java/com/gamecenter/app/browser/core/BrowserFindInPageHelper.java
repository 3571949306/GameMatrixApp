package com.gamecenter.app.browser.core;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 浏览器页面内查找辅助类。
 *
 * <p>封装 WebView 的 findAllAsync + FindListener API，提供：
 * <ul>
 *   <li>实时输入查找（debounce 300ms）</li>
 *   <li>上一个/下一个匹配项</li>
 *   <li>匹配计数显示（"3/15" 格式）</li>
 *   <li>关闭时清除 WebView 高亮</li>
 * </ul>
 */
public class BrowserFindInPageHelper {

    /** 输入 debounce 延迟（ms） */
    private static final long INPUT_DEBOUNCE_MS = 300;

    public interface HostCallback {
        void showFindBar();
        void hideFindBar();
    }

    @Nullable private WebView webView;
    @Nullable private EditText editFind;
    @Nullable private TextView textMatchCount;
    @Nullable private ImageButton btnPrev;
    @Nullable private ImageButton btnNext;
    @Nullable private ImageButton btnClose;
    @Nullable private HostCallback hostCallback;

    private int currentIndex = 0;
    private int matchCount = 0;
    private boolean listenerRegistered = false;
    private final android.os.Handler debounceHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingSearch;

    private final WebView.FindListener findListener = new WebView.FindListener() {
        @Override
        public void onFindResultReceived(int activeMatchOrdinal, int numberOfMatches, boolean isDoneCounting) {
            currentIndex = activeMatchOrdinal;
            matchCount = numberOfMatches;
            updateMatchCountText();
        }
    };

    public void bind(@NonNull WebView webView,
                     @NonNull EditText editFind,
                     @NonNull TextView textMatchCount,
                     @NonNull ImageButton btnPrev,
                     @NonNull ImageButton btnNext,
                     @NonNull ImageButton btnClose,
                     @Nullable HostCallback callback) {
        boolean firstBind = (this.webView == null);
        this.webView = webView;
        this.editFind = editFind;
        this.textMatchCount = textMatchCount;
        this.btnPrev = btnPrev;
        this.btnNext = btnNext;
        this.btnClose = btnClose;
        this.hostCallback = callback;

        // A6: FindListener 必须跟随当前 WebView；切换 Tab 后需重新注册到新 WebView
        webView.setFindListener(findListener);
        listenerRegistered = true;

        // 仅首次绑定时挂接 UI 监听，避免切 Tab 重复 addTextChangedListener/OnClickListener
        if (firstBind) {
            setupListeners();
        }
    }

    private void setupListeners() {
        if (editFind == null) return;

        editFind.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                findNext(true);
                return true;
            }
            return false;
        });

        editFind.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                String query = s.toString().trim();
                if (pendingSearch != null) debounceHandler.removeCallbacks(pendingSearch);
                pendingSearch = () -> doFind(query);
                debounceHandler.postDelayed(pendingSearch, INPUT_DEBOUNCE_MS);
            }
        });

        if (btnPrev != null) {
            btnPrev.setOnClickListener(v -> findNext(false));
        }
        if (btnNext != null) {
            btnNext.setOnClickListener(v -> findNext(true));
        }
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> hide());
        }
    }

    /** 显示查找栏并聚焦输入框 */
    public void show() {
        if (editFind == null) return;
        if (hostCallback != null) hostCallback.showFindBar();
        editFind.requestFocus();
        editFind.setText("");
        updateMatchCountText();
    }

    /** 隐藏查找栏并清除高亮 */
    public void hide() {
        if (webView == null) return;
        // API 16+：传 0 清除高亮
        try { webView.clearMatches(); } catch (Throwable ignored) {}
        if (hostCallback != null) hostCallback.hideFindBar();
        if (editFind != null) {
            editFind.clearFocus();
            // 隐藏软键盘由 Host 负责
        }
        currentIndex = 0;
        matchCount = 0;
    }

    private void doFind(@NonNull String query) {
        if (webView == null) return;
        if (query.isEmpty()) {
            try { webView.clearMatches(); } catch (Throwable ignored) {}
            matchCount = 0;
            currentIndex = 0;
            updateMatchCountText();
            return;
        }
        try {
            webView.findAllAsync(query);
        } catch (Throwable ignored) {}
    }

    /** forward=true 下一个，false 上一个 */
    public void findNext(boolean forward) {
        if (webView == null) return;
        String query = editFind != null ? editFind.getText().toString().trim() : "";
        if (query.isEmpty()) return;
        try {
            // WebView.findNext(forward) 会在已匹配的集合中移动光标
            webView.findNext(forward);
        } catch (Throwable ignored) {}
    }

    private void updateMatchCountText() {
        if (textMatchCount == null) return;
        if (matchCount <= 0) {
            textMatchCount.setText("0/0");
        } else {
            // activeMatchOrdinal 从 0 开始计数，对外显示从 1 开始
            int displayIndex = Math.min(currentIndex + 1, matchCount);
            textMatchCount.setText(displayIndex + "/" + matchCount);
        }
    }

    /** Activity/Fragment onDestroy 时调用，清理 handler */
    public void destroy() {
        if (pendingSearch != null) {
            debounceHandler.removeCallbacks(pendingSearch);
            pendingSearch = null;
        }
        if (webView != null) {
            try { webView.clearMatches(); } catch (Throwable ignored) {}
        }
        listenerRegistered = false;
        webView = null;
        editFind = null;
        textMatchCount = null;
        btnPrev = null;
        btnNext = null;
        btnClose = null;
        hostCallback = null;
    }
}
