package com.gamecenter.app.browser.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.gamecenter.app.BuildConfig;
import com.gamecenter.app.R;
import com.gamecenter.app.browser.core.BrowserCacheManager;
import com.gamecenter.app.browser.core.BrowserSettingsManager;
import com.gamecenter.app.browser.core.player.BrowserPlayerMath;
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
        setupDarkModeRow();
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

        setupVideoPlayerSection(mgr);
    }

    /**
     * B24：内置播放器设置接线。
     *
     * <p>这两个设置项此前只有读写接口、没有 UI 入口，导致用户既关不掉内置播放器，
     * 也改不了长按快进倍速 —— 属"半接线"，这里补齐。
     */
    private void setupVideoPlayerSection(@NonNull BrowserSettingsManager mgr) {
        MaterialSwitch swVideoPlayer = findViewById(R.id.sw_video_player);
        MaterialSwitch swLongPress = findViewById(R.id.sw_long_press_ff);
        View rowRate = findViewById(R.id.row_fast_forward_rate);
        TextView tvRateSummary = findViewById(R.id.tv_fast_forward_rate_summary);

        // 编译期 Feature Flag 关闭时整个能力不存在，设置项也不该出现
        if (!BuildConfig.BROWSER_VIDEO_PLAYER) {
            if (swVideoPlayer != null) swVideoPlayer.setVisibility(View.GONE);
            if (rowRate != null) rowRate.setVisibility(View.GONE);
            if (swLongPress != null) swLongPress.setVisibility(View.GONE);
            return;
        }
        if (rowRate == null) return;

        if (swLongPress != null) {
            swLongPress.setChecked(mgr.isLongPressFastForwardEnabled());
            swLongPress.setOnCheckedChangeListener((b, checked) ->
                    mgr.setLongPressFastForwardEnabled(checked));
        }

        if (swVideoPlayer != null) {
            swVideoPlayer.setChecked(mgr.isVideoPlayerEnabled());
            swVideoPlayer.setOnCheckedChangeListener((b, checked) -> {
                mgr.setVideoPlayerEnabled(checked);
                rowRate.setEnabled(checked);
                rowRate.setAlpha(checked ? 1f : 0.5f);
                if (swLongPress != null) {
                    swLongPress.setEnabled(checked);
                    swLongPress.setAlpha(checked ? 1f : 0.5f);
                }
            });
        }
        rowRate.setEnabled(mgr.isVideoPlayerEnabled());
        rowRate.setAlpha(mgr.isVideoPlayerEnabled() ? 1f : 0.5f);
        if (swLongPress != null) {
            swLongPress.setEnabled(mgr.isVideoPlayerEnabled());
            swLongPress.setAlpha(mgr.isVideoPlayerEnabled() ? 1f : 0.5f);
        }

        if (tvRateSummary != null) {
            tvRateSummary.setText(BrowserPlayerMath.formatRate(mgr.getFastForwardRate()));
        }
        rowRate.setOnClickListener(v -> showFastForwardRateDialog(mgr, tvRateSummary));
    }

    /** 长按快进倍速选择：0.5x - 3.0x，七档。 */
    private void showFastForwardRateDialog(@NonNull BrowserSettingsManager mgr,
                                           @Nullable TextView tvRateSummary) {
        float[] ladder = BrowserPlayerMath.SPEED_LADDER;
        String[] labels = new String[ladder.length];
        float current = mgr.getFastForwardRate();
        int checked = 0;
        for (int i = 0; i < ladder.length; i++) {
            labels[i] = BrowserPlayerMath.formatRate(ladder[i]);
            if (Math.abs(ladder[i] - current) < 0.01f) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.browser_settings_fast_forward_rate)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    mgr.setFastForwardRate(ladder[which]);
                    if (tvRateSummary != null) {
                        tvRateSummary.setText(BrowserPlayerMath.formatRate(ladder[which]));
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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
                    updateDarkModeSummary();
                    Toast.makeText(this, R.string.browser_settings_reset_done, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show());
    }

    /**
     * P1-1：夜间模式选择条目。
     *
     * <p>点击弹出单选对话框，提供三档选项：自动 / 强制开 / 强制关。
     * 选择后立即写入 BrowserSettingsManager，触发 OnSettingsChangeListener 回调
     * （BrowserFragment 会收到 RELOAD_REQUIRED 并刷新页面）。
     */
    private void setupDarkModeRow() {
        View row = findViewById(R.id.row_dark_mode);
        if (row == null) return;
        updateDarkModeSummary();
        row.setOnClickListener(v -> {
            BrowserSettingsManager mgr = BrowserSettingsManager.getInstance(this);
            int current = mgr.getForceDarkMode();
            int selectedIndex;
            if (current == BrowserSettingsManager.DARK_MODE_FORCE_ON) {
                selectedIndex = 1;
            } else if (current == BrowserSettingsManager.DARK_MODE_FORCE_OFF) {
                selectedIndex = 2;
            } else {
                selectedIndex = 0; // AUTO
            }
            String[] items = new String[] {
                    getString(R.string.browser_dark_mode_auto),
                    getString(R.string.browser_dark_mode_force_on),
                    getString(R.string.browser_dark_mode_force_off)
            };
            new AlertDialog.Builder(this)
                    .setTitle(R.string.browser_dark_mode)
                    .setSingleChoiceItems(items, selectedIndex, (dialog, which) -> {
                        int newMode;
                        if (which == 1) {
                            newMode = BrowserSettingsManager.DARK_MODE_FORCE_ON;
                        } else if (which == 2) {
                            newMode = BrowserSettingsManager.DARK_MODE_FORCE_OFF;
                        } else {
                            newMode = BrowserSettingsManager.DARK_MODE_AUTO;
                        }
                        mgr.setForceDarkMode(newMode);
                        updateDarkModeSummary();
                        dialog.dismiss();
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        });
    }

    /** 更新夜间模式按钮的副标题文案 */
    private void updateDarkModeSummary() {
        TextView tvSummary = findViewById(R.id.tv_dark_mode_summary);
        if (tvSummary == null) return;
        int mode = BrowserSettingsManager.getInstance(this).getForceDarkMode();
        int resId;
        if (mode == BrowserSettingsManager.DARK_MODE_FORCE_ON) {
            resId = R.string.browser_dark_mode_force_on;
        } else if (mode == BrowserSettingsManager.DARK_MODE_FORCE_OFF) {
            resId = R.string.browser_dark_mode_force_off;
        } else {
            resId = R.string.browser_dark_mode_auto;
        }
        tvSummary.setText(resId);
    }

    private void saveInputs() {
        BrowserSettingsManager mgr = BrowserSettingsManager.getInstance(this);
        mgr.setHomeUrl(etHomeUrl.getText() != null ? etHomeUrl.getText().toString().trim() : null);
        mgr.setSearchEngine(etSearchEngine.getText() != null ? etSearchEngine.getText().toString().trim() : null);
    }
}
