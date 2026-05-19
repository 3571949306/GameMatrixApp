package com.gamecenter.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.gamecenter.app.update.DownloadState;
import com.gamecenter.app.update.UpdateCheckState;
import com.gamecenter.app.update.UpdateInfo;
import com.gamecenter.app.update.UpdateViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.File;
import java.lang.ref.WeakReference;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {

    private NavController navController;
    private PermissionHelper permissionHelper;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private UpdateViewModel updateViewModel;
    private AlertDialog updateDialog;
    private AlertDialog progressDialog;

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

        updateViewModel = new ViewModelProvider(this).get(UpdateViewModel.class);
        observeUpdateStates();
        scheduleAutoUpdateCheck();
    }

    private void observeUpdateStates() {
        updateViewModel.getUpdateCheckState().observe(this, state -> {
            if (isFinishing() || isDestroyed()) return;

            if (state instanceof UpdateCheckState.Available) {
                UpdateInfo info = ((UpdateCheckState.Available) state).getInfo();
                showUpdateDialog(info);
            } else if (state instanceof UpdateCheckState.NotAvailable) {
                Toast.makeText(this, R.string.update_no_update, Toast.LENGTH_SHORT).show();
            } else if (state instanceof UpdateCheckState.BetaOnly) {
                UpdateInfo info = ((UpdateCheckState.BetaOnly) state).getInfo();
                showBetaOnlyNoticeDialog(info);
            } else if (state instanceof UpdateCheckState.BetaBlocked) {
                Toast.makeText(this, R.string.update_beta_only_toast, Toast.LENGTH_LONG).show();
            } else if (state instanceof UpdateCheckState.Error) {
                String message = ((UpdateCheckState.Error) state).getMessage();
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });

        updateViewModel.getDownloadState().observe(this, state -> {
            if (isFinishing() || isDestroyed()) return;

            if (state instanceof DownloadState.Downloading) {
                DownloadState.Downloading dl = (DownloadState.Downloading) state;
                if (progressDialog != null && progressDialog.isShowing()) {
                    updateProgressDialog(dl.getDownloaded(), dl.getTotal());
                }
            } else if (state instanceof DownloadState.Verifying) {
                if (progressDialog != null && progressDialog.isShowing()) {
                    updateProgressVerifying();
                }
            } else if (state instanceof DownloadState.Completed) {
                dismissProgressDialog();
                File apkFile = ((DownloadState.Completed) state).getApkFile();
                showInstallDialog(apkFile);
            } else if (state instanceof DownloadState.Error) {
                dismissProgressDialog();
                String message = ((DownloadState.Error) state).getMessage();
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            } else if (state instanceof DownloadState.Cancelled) {
                dismissProgressDialog();
            }
        });
    }

    private void scheduleAutoUpdateCheck() {
        if (!(getApplication() instanceof App)) return;
        App app = (App) getApplication();
        new Handler(Looper.getMainLooper()).postDelayed(
                new SafeUpdateCheckRunnable(this, app), 2000);
    }

    private static class SafeUpdateCheckRunnable implements Runnable {
        private final WeakReference<MainActivity> activityRef;
        private final App app;

        SafeUpdateCheckRunnable(MainActivity activity, App app) {
            this.activityRef = new WeakReference<>(activity);
            this.app = app;
        }

        @Override
        public void run() {
            MainActivity activity = activityRef.get();
            if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
            SettingsManager sm = SettingsManager.getInstance(activity);
            if (app.shouldAutoCheckUpdate() && sm.isAutoCheckUpdate()) {
                activity.updateViewModel.checkUpdate(activity, false);
            }
        }
    }

    public void checkUpdate(boolean showToast) {
        if (updateViewModel != null) {
            updateViewModel.checkUpdate(this, showToast);
        }
    }

    private void showUpdateDialog(final UpdateInfo info) {
        if (isFinishing() || isDestroyed()) return;

        String title = info.isForceUpdate()
                ? getString(R.string.update_force) + " - " + getString(R.string.update_new_version)
                : getString(R.string.update_new_version);

        StringBuilder message = new StringBuilder();
        message.append(String.format(getString(R.string.update_version), info.getVersionName()));
        message.append("\n");
        message.append(getString(R.string.update_channel_label, info.getChannelLabel()));
        message.append("\n");
        message.append(getString(R.string.update_version_code, info.getVersionCode()));
        message.append("\n");
        message.append(String.format(getString(R.string.update_size), info.getFileSizeFormatted()));
        message.append("\n\n");
        message.append(getString(R.string.update_changelog));
        message.append("\n");
        message.append(info.getChangelog());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message.toString())
                .setPositiveButton(R.string.update_download, (dialog, which) -> {
                    startDownloadWithProgressDialog(info);
                });

        if (!info.isForceUpdate()) {
            builder.setNegativeButton(R.string.update_later, (dialog, which) -> dialog.dismiss());
        } else {
            builder.setCancelable(false);
        }

        updateDialog = builder.create();
        updateDialog.show();
    }

    private void startDownloadWithProgressDialog(UpdateInfo info) {
        if (isFinishing() || isDestroyed()) return;

        final android.view.View dialogView = android.view.LayoutInflater.from(this)
                .inflate(R.layout.dialog_update_progress, null);
        android.widget.ProgressBar progressBar = dialogView.findViewById(R.id.progress_bar);
        android.widget.TextView tvProgressPercent = dialogView.findViewById(R.id.tv_progress_percent);
        android.widget.TextView tvProgressSize = dialogView.findViewById(R.id.tv_progress_size);

        progressBar.setMax(100);
        progressBar.setProgress(0, true);

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.update_downloading)
                .setView(dialogView)
                .setCancelable(!info.isForceUpdate());

        progressDialog = builder.create();
        progressDialog.show();

        updateViewModel.startDownload(this, info);
    }

    private void updateProgressDialog(long downloaded, long total) {
        if (progressDialog == null || !progressDialog.isShowing()) return;
        android.view.View decorView = progressDialog.getWindow() != null ? progressDialog.getWindow().getDecorView() : null;
        if (decorView == null) return;

        android.widget.ProgressBar progressBar = decorView.findViewById(R.id.progress_bar);
        android.widget.TextView tvProgressPercent = decorView.findViewById(R.id.tv_progress_percent);
        android.widget.TextView tvProgressSize = decorView.findViewById(R.id.tv_progress_size);

        int percent = total > 0 ? (int) (downloaded * 100 / total) : 0;
        if (progressBar != null) progressBar.setProgress(percent, true);
        if (tvProgressPercent != null) tvProgressPercent.setText(percent + "%");
        if (tvProgressSize != null)
            tvProgressSize.setText(formatDownloadProgress(downloaded, total));
    }

    private void updateProgressVerifying() {
        if (progressDialog == null || !progressDialog.isShowing()) return;
        android.view.View decorView = progressDialog.getWindow() != null ? progressDialog.getWindow().getDecorView() : null;
        if (decorView == null) return;

        android.widget.TextView tvProgressPercent = decorView.findViewById(R.id.tv_progress_percent);
        if (tvProgressPercent != null)
            tvProgressPercent.setText(getString(R.string.update_verifying));
    }

    private void showBetaOnlyNoticeDialog(final UpdateInfo info) {
        if (isFinishing() || isDestroyed()) return;

        String lastStableName = info.getLastStableVersionName().isEmpty()
                ? getString(R.string.update_last_stable_default)
                : info.getLastStableVersionName();
        StringBuilder message = new StringBuilder();
        message.append(getString(R.string.update_beta_only_msg,
                info.getVersionName(), info.getVersionCode(),
                info.getLocalVersionCode(), lastStableName));
        if (info.getLastStableVersionCode() > 0) {
            message.append(getString(R.string.update_beta_only_stable_code,
                    info.getLastStableVersionCode()));
        }
        message.append(getString(R.string.update_beta_only_hint));

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.update_beta_only_title)
                .setMessage(message.toString())
                .setPositiveButton(R.string.update_beta_only_enable, (dialog, which) -> {
                    updateViewModel.enableBetaAndRecheck(this);
                })
                .setNegativeButton(R.string.update_beta_only_wait, null)
                .show();
    }

    private void showInstallDialog(final File apkFile) {
        if (isFinishing() || isDestroyed()) return;

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.update_new_version)
                .setMessage(R.string.update_install_prompt)
                .setPositiveButton(R.string.update_install, (dialog, which) -> updateViewModel.installApk(this))
                .setNeutralButton(R.string.update_open_directory, (dialog, which) ->
                        updateViewModel.openDownloadDirectory(this))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == UpdateViewModel.REQUEST_INSTALL_PERMISSION) {
            updateViewModel.onInstallPermissionResult(this, resultCode);
        }
    }

    @Override
    protected void onDestroy() {
        dismissProgressDialog();
        if (updateDialog != null && updateDialog.isShowing()) {
            try { updateDialog.dismiss(); } catch (Exception e) { Log.d("MainActivity", "Dialog dismiss failed", e); }
        }
        updateDialog = null;
        super.onDestroy();
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            try { progressDialog.dismiss(); } catch (Exception e) { Log.d("MainActivity", "Dialog dismiss failed", e); }
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

    private static String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }

    @Override
    public boolean onSupportNavigateUp() {
        return navController.navigateUp() || super.onSupportNavigateUp();
    }
}
