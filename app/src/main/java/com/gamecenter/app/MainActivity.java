package com.gamecenter.app;

import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.gamecenter.app.update.UpdateInfo;
import com.gamecenter.app.update.UpdateManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_INSTALL_PERMISSION = 1001;

    private NavController navController;
    private AlertDialog updateDialog;
    private AlertDialog progressDialog;
    private ProgressBar progressBar;
    private TextView tvProgressPercent;
    private TextView tvProgressSize;
    private File downloadedApk;
    private boolean isCheckingUpdate = false;
    private boolean isAutoDownloadingUpdate = false;

    private PermissionHelper permissionHelper;
    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        permissionHelper = new PermissionHelper(this);
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean[] grantResults = new boolean[result.size()];
                    int i = 0;
                    for (Boolean granted : result.values()) {
                        grantResults[i++] = granted != null && granted;
                    }
                    permissionHelper.onPermissionsResult(grantResults);
                }
        );

        if (permissionHelper.isFirstLaunch()) {
            permissionHelper.showPermissionDialog(permissionLauncher);
        }

        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment != null) {
            navController = navHostFragment.getNavController();
        }

        BottomNavigationView navView = findViewById(R.id.nav_view);
        NavigationUI.setupWithNavController(navView, navController);

        scheduleAutoUpdateCheck();
    }

    private void scheduleAutoUpdateCheck() {
        if (!(getApplication() instanceof App)) return;
        App app = (App) getApplication();

        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                SettingsManager sm = SettingsManager.getInstance(MainActivity.this);
                if (!isFinishing() && !isDestroyed()
                        && app.shouldAutoCheckUpdate()
                        && sm.isAutoCheckUpdate()) {
                    checkUpdate(false);
                }
            }
        }, 2000);
    }

    public void checkUpdate(boolean showToast) {
        if (isCheckingUpdate) return;
        isCheckingUpdate = true;

        UpdateManager.getInstance().checkUpdate(this, new UpdateManager.UpdateCheckCallback() {
            @Override
            public void onResult(final UpdateInfo info) {
                isCheckingUpdate = false;
                if (!isFinishing() && !isDestroyed()) {
                    if (info != null && info.hasUpdate()) {
                        SettingsManager settings = SettingsManager.getInstance(MainActivity.this);
                        if (settings.isAutoDownloadUpdate()) {
                            startAutoDownload(info, showToast);
                        } else {
                            showUpdateDialog(info);
                        }
                    } else if (info != null && info.isBetaUpdateOutdated()) {
                        showBetaOnlyNoticeDialog(info);
                    } else if (info != null && info.isBetaUpdateBlocked() && showToast) {
                        Toast.makeText(MainActivity.this,
                                R.string.update_beta_only_toast, Toast.LENGTH_LONG).show();
                    } else if (showToast) {
                        Toast.makeText(MainActivity.this,
                                R.string.update_no_update, Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onError(final String message) {
                isCheckingUpdate = false;
                if (showToast && !isFinishing() && !isDestroyed()) {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled() {
                isCheckingUpdate = false;
            }
        });
    }

    private void showUpdateDialog(final UpdateInfo info) {
        if (isFinishing() || isDestroyed()) return;

        String title = info.isForceUpdate()
                ? getString(R.string.update_force) + " - " + getString(R.string.update_new_version)
                : getString(R.string.update_new_version);

        StringBuilder message = new StringBuilder();
        message.append(String.format(getString(R.string.update_version), info.getVersionName()));
        message.append("\n");
        message.append("类型: ").append(info.getChannelLabel());
        message.append("\n");
        message.append("内部版本号: ").append(info.getVersionCode());
        message.append("\n");
        message.append(String.format(getString(R.string.update_size), info.getFileSizeFormatted()));
        message.append("\n\n");
        message.append(getString(R.string.update_changelog));
        message.append("\n");
        message.append(info.getChangelog());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message.toString())
                .setPositiveButton(R.string.update_download, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startDownload(info);
                    }
                });

        if (!info.isForceUpdate()) {
            builder.setNegativeButton(R.string.update_later, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
        } else {
            builder.setCancelable(false);
        }

        updateDialog = builder.create();
        updateDialog.show();
    }

    private void showBetaOnlyNoticeDialog(final UpdateInfo info) {
        if (isFinishing() || isDestroyed()) return;

        String lastStableName = info.getLastStableVersionName().isEmpty()
                ? "上一个正式版"
                : info.getLastStableVersionName();
        StringBuilder message = new StringBuilder();
        message.append("服务器当前最新版是测试版 ")
                .append(info.getVersionName())
                .append("（内部版本号 ")
                .append(info.getVersionCode())
                .append("）。\n\n");
        message.append("你当前安装的是内部版本号 ")
                .append(info.getLocalVersionCode())
                .append("，距离上一个正式版 ")
                .append(lastStableName);
        if (info.getLastStableVersionCode() > 0) {
            message.append("（内部版本号 ")
                    .append(info.getLastStableVersionCode())
                    .append("）");
        }
        message.append(" 已经相差较大。\n\n");
        message.append("现在只能开启“接受测试版安装包”后更新到当前内容，或者等待下一次正式版发布。");

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.update_beta_only_title)
                .setMessage(message.toString())
                .setPositiveButton(R.string.update_beta_only_enable, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        SettingsManager.getInstance(MainActivity.this).setAcceptBetaUpdate(true);
                        checkUpdate(true);
                    }
                })
                .setNegativeButton(R.string.update_beta_only_wait, null)
                .show();
    }

    private void startDownload(final UpdateInfo info) {
        if (isFinishing() || isDestroyed()) return;

        final android.view.View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_update_progress, null);
        progressBar = dialogView.findViewById(R.id.progress_bar);
        tvProgressPercent = dialogView.findViewById(R.id.tv_progress_percent);
        tvProgressSize = dialogView.findViewById(R.id.tv_progress_size);

        progressBar.setMax(100);
        progressBar.setProgress(0, true);

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.update_downloading)
                .setView(dialogView)
                .setCancelable(!info.isForceUpdate());

        progressDialog = builder.create();
        progressDialog.show();

        UpdateManager.getInstance().downloadApk(this, info, new UpdateManager.DownloadCallback() {
            @Override
            public void onProgress(long downloaded, long total) {
                if (isFinishing() || isDestroyed()) return;
                int percent = total > 0 ? (int) (downloaded * 100 / total) : 0;
                if (progressBar != null) {
                    progressBar.setProgress(percent, true);
                }
                if (tvProgressPercent != null) {
                    tvProgressPercent.setText(percent + "%");
                }
                if (tvProgressSize != null) {
                    tvProgressSize.setText(formatDownloadProgress(downloaded, total));
                }
            }

            @Override
            public void onVerifying() {
                if (isFinishing() || isDestroyed()) return;
                if (tvProgressPercent != null) {
                    tvProgressPercent.setText(getString(R.string.update_verifying));
                }
            }

            @Override
            public void onComplete(final File apkFile) {
                downloadedApk = apkFile;
                if (isFinishing() || isDestroyed()) return;
                dismissProgressDialog();
                showInstallDialog(apkFile);
            }

            @Override
            public void onError(final String message) {
                if (isFinishing() || isDestroyed()) return;
                dismissProgressDialog();
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancelled() {
                if (isFinishing() || isDestroyed()) return;
                dismissProgressDialog();
            }
        });
    }

    private void startAutoDownload(final UpdateInfo info, boolean showToast) {
        if (isFinishing() || isDestroyed()) return;
        if (isAutoDownloadingUpdate) {
            if (showToast) {
                Toast.makeText(this, "更新安装包正在后台下载", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        isAutoDownloadingUpdate = true;
        Toast.makeText(this, "发现新版本，正在后台下载安装包", Toast.LENGTH_SHORT).show();

        UpdateManager.getInstance().downloadApk(this, info, new UpdateManager.DownloadCallback() {
            @Override
            public void onProgress(long downloaded, long total) {
                // 后台自动下载不展示进度弹窗，避免打断游戏大厅操作。
            }

            @Override
            public void onVerifying() {
                // 校验完成后会统一进入 onComplete 或 onError。
            }

            @Override
            public void onComplete(final File apkFile) {
                isAutoDownloadingUpdate = false;
                downloadedApk = apkFile;
                if (isFinishing() || isDestroyed()) return;

                SettingsManager settings = SettingsManager.getInstance(MainActivity.this);
                if (settings.isPromptInstallAfterAutoDownload()) {
                    showInstallDialog(apkFile);
                } else {
                    Toast.makeText(MainActivity.this,
                            "更新安装包已下载，可在设置中打开下载目录", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onError(final String message) {
                isAutoDownloadingUpdate = false;
                if (isFinishing() || isDestroyed()) return;
                if (showToast) {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled() {
                isAutoDownloadingUpdate = false;
            }
        });
    }

    private void showInstallDialog(final File apkFile) {
        if (isFinishing() || isDestroyed()) return;

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.update_new_version)
                .setMessage("下载完成，是否立即安装？")
                .setPositiveButton(R.string.update_install, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        installApk(apkFile);
                    }
                })
                .setNeutralButton("打开目录", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        UpdateManager.getInstance().openDownloadDirectory(MainActivity.this);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void installApk(File apkFile) {
        if (!UpdateManager.getInstance().canRequestInstall(this)) {
            UpdateManager.getInstance().requestInstallPermission(this, REQUEST_INSTALL_PERMISSION);
            return;
        }
        UpdateManager.getInstance().installApk(this, apkFile);
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            try {
                progressDialog.dismiss();
            } catch (Exception ignored) {}
        }
        progressDialog = null;
    }

    private String formatDownloadProgress(long downloaded, long total) {
        String downloadedStr = formatFileSize(downloaded);
        int percent = total > 0 ? (int) (downloaded * 100 / total) : 0;
        if (total > 0) {
            String totalStr = formatFileSize(total);
            return downloadedStr + " / " + totalStr + " (" + percent + "%)";
        }
        return downloadedStr + " (" + percent + "%)";
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_INSTALL_PERMISSION) {
            if (downloadedApk == null || !downloadedApk.exists()) {
                Toast.makeText(this, "安装包已丢失，请重新下载", Toast.LENGTH_SHORT).show();
                downloadedApk = null;
                return;
            }
            if (resultCode == RESULT_OK || UpdateManager.getInstance().canRequestInstall(this)) {
                installApk(downloadedApk);
            } else {
                Toast.makeText(this, "需要安装权限才能安装更新，请在设置中授予", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        dismissProgressDialog();
        if (updateDialog != null && updateDialog.isShowing()) {
            try { updateDialog.dismiss(); } catch (Exception ignored) {}
        }
        updateDialog = null;
        super.onDestroy();
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }
}
