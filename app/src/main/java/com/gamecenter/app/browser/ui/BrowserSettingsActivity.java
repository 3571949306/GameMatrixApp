package com.gamecenter.app.browser.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.gamecenter.app.BuildConfig;
import com.gamecenter.app.R;
import com.gamecenter.app.browser.core.BrowserCacheManager;
import com.gamecenter.app.browser.core.BrowserSettingsManager;
import com.gamecenter.app.browser.data.BrowserDownloadManager;
import com.gamecenter.app.browser.data.repository.BrowserBookmarkRepository;
import com.gamecenter.app.browser.data.repository.BrowserHistoryRepository;
import com.gamecenter.app.browser.data.repository.SearchHistoryRepository;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

/**
 * 浏览器设置页面。
 *
 * <p>所有设置通过 BrowserSettingsManager 统一管理；数据清理直接操作 Room Repository。
 */
public class BrowserSettingsActivity extends AppCompatActivity {

    private BrowserHistoryRepository historyRepository;
    private BrowserBookmarkRepository bookmarkRepository;
    private SearchHistoryRepository searchHistoryRepository;

    private TextInputEditText etHomeUrl;
    private TextInputEditText etSearchEngine;

    public static void start(Context context) {
        context.startActivity(new Intent(context, BrowserSettingsActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser_settings);
        historyRepository = new BrowserHistoryRepository(getApplication());
        bookmarkRepository = new BrowserBookmarkRepository(getApplication());
        searchHistoryRepository = new SearchHistoryRepository(getApplication());

        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        etHomeUrl = findViewById(R.id.et_home_url);
        etSearchEngine = findViewById(R.id.et_search_engine);

        setupSwitches();
        setupClearButtons();
        setupResetButton();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveInputs();
    }

    private void setupSwitches() {
        BrowserSettingsManager mgr = BrowserSettingsManager.getInstance(this);

        MaterialSwitch swJs = findViewById(R.id.sw_javascript);
        MaterialSwitch swCookie = findViewById(R.id.sw_cookie);
        MaterialSwitch swThirdParty = findViewById(R.id.sw_third_party_cookie);
        MaterialSwitch swLoadImages = findViewById(R.id.sw_load_images);
        MaterialSwitch swAutoPlayMedia = findViewById(R.id.sw_auto_play_media);
        MaterialSwitch swAdBlock = findViewById(R.id.sw_ad_block);
        MaterialSwitch swSafeBrowsing = findViewById(R.id.sw_safe_browsing);
        MaterialSwitch swWebViewDebug = findViewById(R.id.sw_webview_debug);

        // 读取当前设置
        swJs.setChecked(mgr.isJavaScriptEnabled());
        swCookie.setChecked(mgr.isCookieEnabled());
        swThirdParty.setChecked(mgr.isThirdPartyCookieEnabled());
        swLoadImages.setChecked(mgr.isLoadImagesEnabled());
        swAutoPlayMedia.setChecked(mgr.isAutoPlayMediaEnabled());
        swAdBlock.setChecked(mgr.isAdBlockEnabled());
        swSafeBrowsing.setChecked(mgr.isSafeBrowsingEnabled());
        swWebViewDebug.setChecked(mgr.isWebViewDebuggingEnabled());
        etHomeUrl.setText(mgr.getHomeUrl());
        etSearchEngine.setText(mgr.getSearchEngine());

        // WebView 调试开关只在 Debug 构建下可见
        swWebViewDebug.setVisibility(BuildConfig.BROWSER_WEBVIEW_DEBUG ? View.VISIBLE : View.GONE);

        // 写入通过 BrowserSettingsManager（带通知）
        swJs.setOnCheckedChangeListener((b, checked) -> mgr.setJavaScriptEnabled(checked));
        swCookie.setOnCheckedChangeListener((b, checked) -> mgr.setCookieEnabled(checked));
        swThirdParty.setOnCheckedChangeListener((b, checked) -> mgr.setThirdPartyCookieEnabled(checked));
        swLoadImages.setOnCheckedChangeListener((b, checked) -> mgr.setLoadImagesEnabled(checked));
        swAutoPlayMedia.setOnCheckedChangeListener((b, checked) -> mgr.setAutoPlayMediaEnabled(checked));
        swAdBlock.setOnCheckedChangeListener((b, checked) -> mgr.setAdBlockEnabled(checked));
        swSafeBrowsing.setOnCheckedChangeListener((b, checked) -> mgr.setSafeBrowsingEnabled(checked));
        swWebViewDebug.setOnCheckedChangeListener((b, checked) -> mgr.setWebViewDebuggingEnabled(checked));

        etHomeUrl.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) saveInputs(); });
        etSearchEngine.setOnFocusChangeListener((v, hasFocus) -> { if (!hasFocus) saveInputs(); });
    }

    private void setupClearButtons() {
        findViewById(R.id.btn_clear_history).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle(R.string.browser_history_clear_title)
                .setMessage(R.string.browser_history_clear_message)
                .setPositiveButton(R.string.browser_history_clear_all, (d, w) -> {
                    historyRepository.deleteAll();
                    Toast.makeText(this, R.string.browser_history_cleared, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());

        findViewById(R.id.btn_clear_search_history).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle(R.string.browser_history_clear_title)
                .setMessage(R.string.browser_history_clear_message)
                .setPositiveButton(R.string.browser_history_clear_all, (d, w) -> {
                    searchHistoryRepository.deleteAll();
                    Toast.makeText(this, R.string.browser_history_cleared, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());

        findViewById(R.id.btn_clear_bookmarks).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle(R.string.browser_bookmark_clear_title)
                .setMessage(R.string.browser_bookmark_clear_message)
                .setPositiveButton(R.string.browser_bookmark_clear_all, (d, w) -> {
                    bookmarkRepository.deleteAll();
                    Toast.makeText(this, R.string.browser_bookmark_cleared, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());

        findViewById(R.id.btn_clear_cache).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle(R.string.browser_cache_clear_title)
                .setMessage(R.string.browser_cache_clear_message)
                .setPositiveButton(R.string.browser_cache_clear, (d, w) -> {
                    BrowserCacheManager.clearAllBrowsingData(this);
                    Toast.makeText(this, R.string.browser_cache_cleared, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());

        findViewById(R.id.btn_clear_downloads).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle(R.string.browser_download_clear_title)
                .setMessage(R.string.browser_download_clear_message)
                .setPositiveButton(R.string.browser_download_clear_all, (d, w) -> {
                    BrowserDownloadManager.getInstance(this).clearAllDownloads(false);
                    Toast.makeText(this, R.string.browser_download_cleared, Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton(R.string.browser_download_clear_all_with_file, (d, w) -> {
                    BrowserDownloadManager.getInstance(this).clearAllDownloads(true);
                    Toast.makeText(this, R.string.browser_download_cleared, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());
    }

    private void setupResetButton() {
        findViewById(R.id.btn_reset_settings).setOnClickListener(v ->
            new AlertDialog.Builder(this)
                .setTitle(R.string.browser_settings_reset)
                .setMessage(R.string.browser_settings_reset_message)
                .setPositiveButton(R.string.browser_settings_reset_confirm, (d, w) -> {
                    BrowserSettingsManager.getInstance(this).resetToDefaults();
                    setupSwitches();
                    Toast.makeText(this, R.string.browser_settings_reset_done, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());
    }

    private void saveInputs() {
        BrowserSettingsManager mgr = BrowserSettingsManager.getInstance(this);
        mgr.setHomeUrl(etHomeUrl.getText() != null ? etHomeUrl.getText().toString().trim() : null);
        mgr.setSearchEngine(etSearchEngine.getText() != null ? etSearchEngine.getText().toString().trim() : null);
    }
}
