package com.gamecenter.app;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.gamecenter.app.modules.ModuleManager;
import com.gamecenter.app.core.common.ModuleManifest;

import java.io.File;
import java.text.DecimalFormat;

/**
 * 设置页面 — 模块管理、缓存清理、主题切换、应用信息。
 */
public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // 返回
        ImageButton btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // 版本号
        showVersion();

        // 模块商店
        View btnStore = findViewById(R.id.btn_module_store);
        if (btnStore != null) {
            btnStore.setOnClickListener(v -> {
                startActivity(new Intent(this, com.gamecenter.app.modules.ModuleStoreActivity.class));
            });
        }

        // 已安装模块管理（跳转到已安装模块列表页）
        View btnManageModules = findViewById(R.id.btn_manage_modules);
        if (btnManageModules != null) {
            btnManageModules.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(this,
                            com.gamecenter.app.modules.InstalledModulesActivity.class));
                } catch (Exception e) {
                    Log.e(TAG, "已安装模块页面未找到，尝试 ModuleStoreActivity", e);
                    startActivity(new Intent(this,
                            com.gamecenter.app.modules.ModuleStoreActivity.class));
                }
            });
        }

        // 已安装模块数量
        updateInstalledCount();

        // 清除缓存
        View btnCache = findViewById(R.id.btn_clear_cache);
        if (btnCache != null) {
            btnCache.setOnClickListener(v -> clearCache());
        }
        // 缓存大小
        updateCacheSize();

        // 主题模式
        View btnTheme = findViewById(R.id.btn_theme);
        if (btnTheme != null) {
            btnTheme.setOnClickListener(v -> showThemeDialog());
        }

        // 语言设置
        View btnLanguage = findViewById(R.id.btn_language);
        if (btnLanguage != null) {
            btnLanguage.setOnClickListener(v -> showLanguageDialog());
        }

        // 游戏战绩
        View btnStats = findViewById(R.id.btn_stats);
        if (btnStats != null) {
            btnStats.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(this, com.gamecenter.app.games.StatsActivity.class));
                } catch (Exception e) {
                    Log.e(TAG, "战绩页面未找到", e);
                    Toast.makeText(this, R.string.settings_stats_unavailable, Toast.LENGTH_SHORT).show();
                }
            });
        }

        // P2-9 (PLAY_TIME_MANAGEMENT): 游戏时长管理
        View btnPlayLimit = findViewById(R.id.btn_play_limit);
        if (btnPlayLimit != null) {
            btnPlayLimit.setOnClickListener(v -> showPlayLimitDialog());
        }
        View btnWeeklyReport = findViewById(R.id.btn_weekly_report);
        if (btnWeeklyReport != null) {
            btnWeeklyReport.setOnClickListener(v -> showWeeklyReportDialog());
        }
        updatePlayLimitSummary();

        // 服务器状态
        checkServerStatus();

        // 新手引导（U2 免登录上手：允许用户重置引导并重新查看）
        View btnOnboarding = findViewById(R.id.btn_onboarding_reset);
        if (btnOnboarding != null) {
            btnOnboarding.setOnClickListener(v -> showOnboardingResetDialog());
        }

        // 关于
        View btnAbout = findViewById(R.id.btn_about);
        if (btnAbout != null) {
            btnAbout.setOnClickListener(v -> showAboutDialog());
        }

        // P3-14 (OFFLINE_MODULE_PRELOAD): 离线模块预下载开关
        View btnModulePreload = findViewById(R.id.btn_module_preload);
        if (btnModulePreload != null) {
            btnModulePreload.setOnClickListener(v -> showModulePreloadDialog());
        }
        updateModulePreloadSwitch();

        // P3-13 (CLOUD_SAVE_SYNC): 云存档同步
        View btnCloudSync = findViewById(R.id.btn_cloud_sync);
        if (btnCloudSync != null) {
            btnCloudSync.setOnClickListener(v -> showCloudSyncDialog());
        }
        updateCloudSyncSummary();

        // #23 数据与连接中心：聚合展示本地数据/网络/同步状态
        View btnDataCenter = findViewById(R.id.btn_data_center);
        if (btnDataCenter != null) {
            btnDataCenter.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(this,
                            com.gamecenter.app.settings.DataConnectionCenterActivity.class));
                } catch (Exception e) {
                    Log.e(TAG, "数据与连接中心页面未找到", e);
                    Toast.makeText(this, R.string.settings_stats_unavailable, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateInstalledCount();
        updatePlayLimitSummary();
        updateModulePreloadSwitch();
        updateCloudSyncSummary();
    }

    /** P3-14: 更新预下载开关状态 */
    private void updateModulePreloadSwitch() {
        com.google.android.material.materialswitch.MaterialSwitch sw =
                findViewById(R.id.switch_module_preload);
        if (sw != null) {
            sw.setChecked(com.gamecenter.app.modules.ModulePreDownloadManager.isEnabled(this));
        }
        TextView tvSummary = findViewById(R.id.tv_module_preload_summary);
        if (tvSummary != null) {
            long last = com.gamecenter.app.modules.ModulePreDownloadManager.getLastRunTime(this);
            if (last > 0) {
                String timeStr = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                        .format(new java.util.Date(last));
                tvSummary.setText(getString(R.string.module_preload_last_run, timeStr));
            } else {
                tvSummary.setText(R.string.module_preload_never_run);
            }
        }
    }

    /** P3-14: 显示预下载设置弹窗 */
    private void showModulePreloadDialog() {
        boolean enabled = com.gamecenter.app.modules.ModulePreDownloadManager.isEnabled(this);
        boolean allowCellular = com.gamecenter.app.modules.ModulePreDownloadManager.isAllowOnCellular(this);
        String[] items = {
                getString(R.string.module_preload_summary),
                getString(R.string.module_preload_allow_cellular),
                getString(R.string.module_preload_now)
        };
        boolean[] checked = { enabled, allowCellular, false };
        new AlertDialog.Builder(this)
                .setTitle(R.string.module_preload_title)
                .setMultiChoiceItems(items, checked, (dialog, which, isChecked) -> {
                    if (which == 0) {
                        com.gamecenter.app.modules.ModulePreDownloadManager.setEnabled(this, isChecked);
                    } else if (which == 1) {
                        com.gamecenter.app.modules.ModulePreDownloadManager.setAllowOnCellular(this, isChecked);
                    }
                })
                .setPositiveButton(R.string.module_preload_now, (dialog, which) -> {
                    boolean onWifi = com.gamecenter.app.modules.ModulePreDownloadManager.isOnWifi(this);
                    boolean allowCell = com.gamecenter.app.modules.ModulePreDownloadManager.isAllowOnCellular(this);
                    if (!onWifi && !allowCell) {
                        Toast.makeText(this, R.string.module_preload_no_wifi, Toast.LENGTH_SHORT).show();
                    } else {
                        com.gamecenter.app.modules.ModulePreDownloadManager.maybePreload(this);
                        Toast.makeText(this, R.string.module_preload_now_toast, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener(d -> updateModulePreloadSwitch())
                .show();
    }

    /** P3-13: 更新云同步摘要 */
    private void updateCloudSyncSummary() {
        TextView tv = findViewById(R.id.tv_cloud_sync_summary);
        if (tv == null) return;
        com.gamecenter.app.cloudsync.WebDavSyncProvider provider =
                new com.gamecenter.app.cloudsync.WebDavSyncProvider(this);
        if (!provider.isConfigured()) {
            tv.setText(R.string.cloud_sync_not_configured);
        } else {
            long last = com.gamecenter.app.cloudsync.CloudSyncManager.getLastSyncTime(this);
            if (last > 0) {
                tv.setText(getString(R.string.cloud_sync_last_sync,
                        com.gamecenter.app.cloudsync.CloudSyncManager.formatTime(last)));
            } else {
                tv.setText(R.string.cloud_sync_never_synced);
            }
        }
    }

    /** P3-13: 显示云存档同步配置弹窗 */
    private void showCloudSyncDialog() {
        com.gamecenter.app.cloudsync.WebDavSyncProvider provider =
                new com.gamecenter.app.cloudsync.WebDavSyncProvider(this);
        // 构建自定义视图
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        final android.widget.EditText etUrl = new android.widget.EditText(this);
        etUrl.setHint(R.string.cloud_sync_webdav_url);
        etUrl.setText(provider.getUrl());
        layout.addView(etUrl);

        final android.widget.EditText etUser = new android.widget.EditText(this);
        etUser.setHint(R.string.cloud_sync_webdav_user);
        etUser.setText(provider.getUser());
        layout.addView(etUser);

        final android.widget.EditText etPass = new android.widget.EditText(this);
        etPass.setHint(R.string.cloud_sync_webdav_pass);
        etPass.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        etPass.setText(provider.getPass());
        layout.addView(etPass);

        final android.widget.EditText etSubdir = new android.widget.EditText(this);
        etSubdir.setHint(R.string.cloud_sync_webdav_subdir);
        etSubdir.setText(provider.getSubdir());
        layout.addView(etSubdir);

        final android.widget.CheckBox cbAuto = new android.widget.CheckBox(this);
        cbAuto.setText(R.string.cloud_sync_auto_sync);
        cbAuto.setChecked(com.gamecenter.app.cloudsync.CloudSyncManager.isAutoSyncEnabled(this));
        layout.addView(cbAuto);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.cloud_sync_title)
                .setView(layout)
                .setPositiveButton(R.string.cloud_sync_save_config, (d, w) -> {
                    provider.setConfig(
                            etUrl.getText().toString(),
                            etUser.getText().toString(),
                            etPass.getText().toString(),
                            etSubdir.getText().toString().isEmpty() ? "GameMatrixApp" : etSubdir.getText().toString());
                    com.gamecenter.app.cloudsync.CloudSyncManager.setAutoSyncEnabled(this, cbAuto.isChecked());
                    Toast.makeText(this, R.string.cloud_sync_config_saved, Toast.LENGTH_SHORT).show();
                    updateCloudSyncSummary();
                })
                .setNeutralButton(R.string.cloud_sync_test_connection, null)
                .setNegativeButton(R.string.cloud_sync_sync_now, null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                // 先保存当前输入，再测试
                provider.setConfig(
                        etUrl.getText().toString(),
                        etUser.getText().toString(),
                        etPass.getText().toString(),
                        etSubdir.getText().toString().isEmpty() ? "GameMatrixApp" : etSubdir.getText().toString());
                Toast.makeText(this, R.string.cloud_sync_syncing, Toast.LENGTH_SHORT).show();
                new Thread(() -> {
                    String err = provider.testConnection();
                    runOnUiThread(() -> {
                        if (err == null) {
                            Toast.makeText(this, R.string.cloud_sync_test_success, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, getString(R.string.cloud_sync_test_failed, err), Toast.LENGTH_LONG).show();
                        }
                    });
                }).start();
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
                if (!provider.isConfigured()) {
                    Toast.makeText(this, R.string.cloud_sync_not_configured, Toast.LENGTH_SHORT).show();
                    return;
                }
                performSyncWithConsent();
            });
        });
        dialog.show();
    }

    /** #24.5: 构建云同步 consent 组件 */
    private com.gamecenter.app.core.common.ConsentComponent buildWebDavConsent() {
        return new com.gamecenter.app.core.common.ConsentComponent(
                "webdav_sync",
                1,
                getString(R.string.consent_sync_title),
                getString(R.string.consent_sync_send),
                getString(R.string.consent_sync_purpose),
                getString(R.string.consent_sync_local),
                getString(R.string.consent_sync_cost),
                getString(R.string.consent_sync_cancel),
                getString(R.string.consent_sync_provider),
                getString(R.string.consent_sync_retention)
        );
    }

    /** #24.5: 同步前 consent 流程 */
    private void performSyncWithConsent() {
        com.gamecenter.app.ui.ConsentDialog.show(this, buildWebDavConsent(), decision -> {
            if (decision == com.gamecenter.app.core.common.ConsentDecision.AGREE_CLOUD) {
                doCloudSync();
            } else if (decision == com.gamecenter.app.core.common.ConsentDecision.USE_LOCAL) {
                Toast.makeText(this, R.string.cloud_sync_use_local_hint, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.cloud_sync_cancelled, Toast.LENGTH_SHORT).show();
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    /** #24.5: 实际执行云端同步 */
    private void doCloudSync() {
        Toast.makeText(this, R.string.cloud_sync_syncing, Toast.LENGTH_SHORT).show();
        com.gamecenter.app.cloudsync.CloudSyncManager.sync(this, result -> {
            runOnUiThread(() -> {
                String msg;
                if (result instanceof com.gamecenter.app.cloudsync.CloudSyncManager.SyncResult.Success) {
                    msg = getString(R.string.cloud_sync_sync_success);
                } else if (result instanceof com.gamecenter.app.cloudsync.CloudSyncManager.SyncResult.Conflict) {
                    showCloudSyncConflictDialog();
                    return;
                } else if (result instanceof com.gamecenter.app.cloudsync.CloudSyncManager.SyncResult.NotConfigured) {
                    msg = getString(R.string.cloud_sync_not_configured);
                } else if (result instanceof com.gamecenter.app.cloudsync.CloudSyncManager.SyncResult.Failure) {
                    msg = ((com.gamecenter.app.cloudsync.CloudSyncManager.SyncResult.Failure) result).getMessage();
                } else {
                    msg = "同步失败";
                }
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                updateCloudSyncSummary();
            });
            return kotlin.Unit.INSTANCE;
        });
    }

    /** #24.5: 下载云端存档前 consent 流程（冲突解决-保留远程） */
    private void performDownloadWithConsent() {
        com.gamecenter.app.ui.ConsentDialog.show(this, buildWebDavConsent(), decision -> {
            if (decision == com.gamecenter.app.core.common.ConsentDecision.AGREE_CLOUD) {
                doCloudDownload();
            } else if (decision == com.gamecenter.app.core.common.ConsentDecision.USE_LOCAL) {
                Toast.makeText(this, R.string.cloud_sync_use_local_hint, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.cloud_sync_cancelled, Toast.LENGTH_SHORT).show();
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    /** #24.5: 实际执行云端下载 */
    private void doCloudDownload() {
        com.gamecenter.app.cloudsync.CloudSyncManager.download(this, result -> {
            runOnUiThread(() -> {
                String msg = (result instanceof com.gamecenter.app.cloudsync.CloudSyncManager.SyncResult.Success)
                        ? getString(R.string.cloud_sync_download_success) : "下载失败";
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                updateCloudSyncSummary();
            });
            return kotlin.Unit.INSTANCE;
        });
    }

    /** #24.5: 上传本地存档前 consent 流程（冲突解决-保留本地） */
    private void performUploadWithConsent() {
        com.gamecenter.app.ui.ConsentDialog.show(this, buildWebDavConsent(), decision -> {
            if (decision == com.gamecenter.app.core.common.ConsentDecision.AGREE_CLOUD) {
                doCloudUpload();
            } else if (decision == com.gamecenter.app.core.common.ConsentDecision.USE_LOCAL) {
                Toast.makeText(this, R.string.cloud_sync_use_local_hint, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.cloud_sync_cancelled, Toast.LENGTH_SHORT).show();
            }
            return kotlin.Unit.INSTANCE;
        });
    }

    /** #24.5: 实际执行云端上传 */
    private void doCloudUpload() {
        com.gamecenter.app.cloudsync.CloudSyncManager.upload(this, true, result -> {
            runOnUiThread(() -> {
                String msg = (result instanceof com.gamecenter.app.cloudsync.CloudSyncManager.SyncResult.Success)
                        ? getString(R.string.cloud_sync_upload_success) : "上传失败";
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                updateCloudSyncSummary();
            });
            return kotlin.Unit.INSTANCE;
        });
    }

    /** P3-13: 冲突解决弹窗 */
    private void showCloudSyncConflictDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.cloud_sync_title)
                .setMessage(R.string.cloud_sync_conflict)
                .setPositiveButton(R.string.cloud_sync_conflict_keep_remote, (d, w) -> {
                    performDownloadWithConsent();
                })
                .setNegativeButton(R.string.cloud_sync_conflict_keep_local, (d, w) -> {
                    performUploadWithConsent();
                })
                .show();
    }

    private void showVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            TextView tv = findViewById(R.id.tv_app_version);
            if (tv != null) tv.setText(getString(R.string.version_format_with_code, info.versionName, info.versionCode));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "获取版本信息失败", e);
        }
    }

    private void updateInstalledCount() {
        try {
            File modulesDir = new File(getFilesDir(), "modules");
            int count = 0;
            if (modulesDir.exists() && modulesDir.isDirectory()) {
                File[] files = modulesDir.listFiles((dir, name) -> name.endsWith(".apk"));
                count = files != null ? files.length : 0;
            }
            TextView tv = findViewById(R.id.tv_installed_count);
            if (tv != null) tv.setText(count + " 个");
        } catch (Exception e) {
            Log.e(TAG, "获取已安装模块数量失败", e);
            TextView tv = findViewById(R.id.tv_installed_count);
            if (tv != null) tv.setText("—");
        }
    }

    private void updateCacheSize() {
        try {
            long size = getDirSize(getCacheDir());
            TextView tv = findViewById(R.id.tv_cache_size);
            if (tv != null) tv.setText(formatSize(size));
        } catch (Exception e) {
            Log.e(TAG, "计算缓存大小失败", e);
            TextView tv = findViewById(R.id.tv_cache_size);
            if (tv != null) tv.setText("—");
        }
    }

    private void clearCache() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_clear_cache_title)
                .setMessage(getString(R.string.settings_clear_cache_msg))
                .setPositiveButton(R.string.settings_clear_cache_confirm, (dialog, which) -> {
                    deleteDir(getCacheDir());
                    updateCacheSize();
                    Toast.makeText(this, R.string.settings_cache_cleared, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.settings_cancel, null)
                .show();
    }

    private void showThemeDialog() {
        String[] themes = {"跟随系统", "浅色模式", "深色模式"};
        // 读取当前保存值
        int currentWhich = getSharedPreferences("settings", MODE_PRIVATE)
                .getInt("night_mode", 0);
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_theme_mode)
                .setSingleChoiceItems(themes, currentWhich, (dialog, which) -> {
                    int mode;
                    switch (which) {
                        case 1: mode = AppCompatDelegate.MODE_NIGHT_NO; break;
                        case 2: mode = AppCompatDelegate.MODE_NIGHT_YES; break;
                        default: mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; break;
                    }
                    AppCompatDelegate.setDefaultNightMode(mode);
                    TextView tv = findViewById(R.id.tv_theme_mode);
                    if (tv != null) tv.setText(themes[which]);
                    getSharedPreferences("settings", MODE_PRIVATE)
                            .edit().putInt("night_mode", which).apply();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.settings_cancel, null)
                .show();
    }

    /**
     * P2-9 (PLAY_TIME_MANAGEMENT): 弹出每日游玩限额选择对话框。
     */
    private void showPlayLimitDialog() {
        com.gamecenter.app.games.PlayTimeManager mgr =
                new com.gamecenter.app.games.PlayTimeManager(this);
        int currentLimit = mgr.getDailyLimitMin();
        int[] options = com.gamecenter.app.games.PlayTimeManager.LIMIT_OPTIONS;
        String[] labels = new String[options.length];
        int checkedIdx = 0;
        for (int i = 0; i < options.length; i++) {
            if (options[i] == 0) {
                labels[i] = getString(R.string.play_limit_option_off);
            } else {
                labels[i] = getString(R.string.play_limit_option_format, options[i]);
            }
            if (options[i] == currentLimit) checkedIdx = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.play_limit_dialog_title)
                .setSingleChoiceItems(labels, checkedIdx, (dialog, which) -> {
                    mgr.setDailyLimitMin(options[which]);
                    updatePlayLimitSummary();
                    dialog.dismiss();
                    String msg = options[which] == 0
                            ? getString(R.string.play_limit_summary_off)
                            : getString(R.string.play_limit_option_format, options[which]);
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.settings_cancel, null)
                .show();
    }

    /** P2-9: 更新限额摘要文本。 */
    private void updatePlayLimitSummary() {
        TextView tv = findViewById(R.id.tv_play_limit_summary);
        if (tv == null) return;
        com.gamecenter.app.games.PlayTimeManager mgr =
                new com.gamecenter.app.games.PlayTimeManager(this);
        if (!mgr.isLimitEnabled()) {
            tv.setText(R.string.play_limit_summary_off);
        } else {
            tv.setText(getString(R.string.play_limit_summary_format,
                    mgr.getTodayPlayedMin(), mgr.getDailyLimitMin()));
        }
    }

    /** P2-9: 弹出本周游玩报告对话框。 */
    private void showWeeklyReportDialog() {
        com.gamecenter.app.games.PlayTimeManager mgr =
                new com.gamecenter.app.games.PlayTimeManager(this);
        com.gamecenter.app.games.PlayTimeManager.WeeklyReport report =
                mgr.generateWeeklyReport();
        new AlertDialog.Builder(this)
                .setTitle(R.string.play_limit_weekly_report_title)
                .setMessage(report.toText(this))
                .setPositiveButton(R.string.settings_ok, null)
                .show();
        mgr.markWeeklyReportShown();
    }

    private void showLanguageDialog() {
        String[] languages = {"跟随系统", "中文", "English"};
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_app_language)
                .setItems(languages, (dialog, which) -> {
                    TextView tv = findViewById(R.id.tv_language);
                    if (tv != null) tv.setText(languages[which]);
                    getSharedPreferences("settings", MODE_PRIVATE)
                            .edit().putInt("app_language", which).apply();
                    Toast.makeText(this, R.string.settings_language_saved_restart, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void checkServerStatus() {
        // Fix memory leak: use WeakReference so thread doesn't pin Activity
        final java.lang.ref.WeakReference<TextView> tvRef =
                new java.lang.ref.WeakReference<>((TextView) findViewById(R.id.tv_server_status));
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(BuildConfig.MODULES_URL);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestMethod("HEAD");
                int code = conn.getResponseCode();
                conn.disconnect();
                final int finalCode = code;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    TextView tv = tvRef.get();
                    if (tv == null) return;
                    tv.setText(finalCode == 200 ? "已连接" : "异常 (" + finalCode + ")");
                    tv.setTextColor(finalCode == 200 ? 0xFF4CAF50 : 0xFFF44336);
                });
            } catch (Exception e) {
                Log.w(TAG, "服务器检测失败", e);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    TextView tv = tvRef.get();
                    if (tv == null) return;
                    tv.setText(getString(R.string.settings_server_unreachable));
                    tv.setTextColor(0xFFF44336);
                });
            }
        }).start();
    }

    private void showAboutDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.about_gamecenter_title)
                .setMessage(getString(R.string.about_gamecenter_message, BuildConfig.MODULE_HOST))
                .setPositiveButton(R.string.settings_ok, null)
                .show();
    }

    /**
     * 新手引导重置对话框（U2 免登录上手）。
     *
     * <p>项目无账号体系，引导完成态持久化在 SharedPreferences("onboarding") 中。
     * 这里提供「全部重置 / 选择性重置」入口，让用户能重新查看引导。</p>
     */
    private void showOnboardingResetDialog() {
        // P3-11: 新增"全局新手引导"选项，与原有游戏内引导并列
        String[] items = {
                getString(R.string.onboarding_reset_global),
                getString(R.string.onboarding_reset_all),
                getString(R.string.onboarding_reset_doudizhu),
                getString(R.string.onboarding_reset_go)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_tutorial_title)
                .setItems(items, (dialog, which) -> {
                    android.content.SharedPreferences prefs =
                            getSharedPreferences("onboarding", MODE_PRIVATE);
                    android.content.SharedPreferences.Editor editor = prefs.edit();
                    int toastRes;
                    switch (which) {
                        case 0:
                            // 仅重置全局新手引导（兴趣+难度自评）
                            com.gamecenter.app.ui.onboarding.OnboardingActivity.reset(this);
                            toastRes = R.string.onboarding_reset_global_toast;
                            break;
                        case 1:
                            // 全部重置（含游戏内引导）
                            editor.clear();
                            com.gamecenter.app.ui.onboarding.OnboardingActivity.reset(this);
                            toastRes = R.string.onboarding_reset_all_toast;
                            break;
                        case 2:
                            editor.putBoolean(
                                    com.gamecenter.app.ui.onboarding.DoudizhuOnboarding.STORAGE_KEY,
                                    false);
                            toastRes = R.string.onboarding_reset_doudizhu_toast;
                            break;
                        case 3:
                            editor.putBoolean(
                                    com.gamecenter.app.ui.onboarding.GoOnboarding.STORAGE_KEY,
                                    false);
                            toastRes = R.string.onboarding_reset_go_toast;
                            break;
                        default:
                            return;
                    }
                    editor.apply();
                    Toast.makeText(this, toastRes, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.settings_cancel, null)
                .show();
    }

    // ======== 工具方法 ========

    private long getDirSize(File dir) {
        long size = 0;
        if (dir == null || !dir.exists()) return 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            size += f.isDirectory() ? getDirSize(f) : f.length();
        }
        return size;
    }

    private boolean deleteDir(File dir) {
        if (dir == null || !dir.exists()) return false;
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) deleteDir(child);
            }
        }
        return dir.delete();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        DecimalFormat df = new DecimalFormat("#.#");
        if (bytes < 1024 * 1024) return df.format(bytes / 1024.0) + " KB";
        return df.format(bytes / (1024.0 * 1024.0)) + " MB";
    }
}
