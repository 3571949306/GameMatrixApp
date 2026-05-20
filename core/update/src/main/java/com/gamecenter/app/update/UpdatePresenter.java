package com.gamecenter.app.update;

import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.gamecenter.app.update.R;
import com.gamecenter.app.SettingsManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import android.util.Log;

import java.io.File;

/**
 * 更新展示器（Presenter），负责管理更新流程的 UI 交互。
 * <p>
 * 作为 MVP 架构中的 Presenter 层，协调 {@link UpdateManager} 的业务逻辑与 Activity 的 UI 展示。
 * 管理更新检查、下载进度、安装确认等对话框的生命周期。
 * </p>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>所有 UI 操作前都会检查 Activity 是否已 finishing/destroyed，避免窗口泄漏</li>
 *   <li>支持自动下载模式：用户开启后，检查到更新自动下载，无需手动确认</li>
 *   <li>强制更新时，对话框不可取消，用户必须下载更新</li>
 *   <li>Beta 版本被阻止时，会显示提示对话框引导用户开启 Beta 更新</li>
 * </ul>
 * </p>
 */
public class UpdatePresenter {

    private static final String TAG = "UpdatePresenter";
    /** 安装权限请求码，用于 onActivityResult 中识别 */
    private static final int REQUEST_INSTALL_PERMISSION = 1001;

    private final AppCompatActivity activity;
    /** 更新信息对话框 */
    private AlertDialog updateDialog;
    /** 下载进度对话框 */
    private AlertDialog progressDialog;
    /** 进度条控件 */
    private ProgressBar progressBar;
    /** 进度百分比文本控件 */
    private TextView tvProgressPercent;
    /** 进度大小文本控件 */
    private TextView tvProgressSize;
    /** 已下载完成的 APK 文件引用 */
    private File downloadedApk;
    /** 是否正在检查更新（防止重复检查） */
    private boolean isCheckingUpdate = false;
    /** 是否正在自动下载更新（防止重复下载） */
    private boolean isAutoDownloadingUpdate = false;

    /**
     * 构造函数。
     *
     * @param activity 关联的 Activity，用于显示对话框和 Toast
     */
    public UpdatePresenter(AppCompatActivity activity) {
        this.activity = activity;
    }

    /**
     * 检查应用更新。
     * <p>
     * 检查结果处理逻辑：
     * <ol>
     *   <li>有更新且开启自动下载 → 自动下载</li>
     *   <li>有更新且未开启自动下载 → 显示更新对话框</li>
     *   <li>Beta 版本已过时 → 显示 Beta 提示对话框</li>
     *   <li>Beta 版本被阻止 → 显示 Beta Toast 提示</li>
     *   <li>无更新 → 显示"已是最新版本" Toast</li>
     * </ol>
     * </p>
     *
     * @param showToast 是否在无更新或出错时显示 Toast 提示
     */
    public void checkUpdate(boolean showToast) {
        // 防止重复检查
        if (isCheckingUpdate) return;
        isCheckingUpdate = true;

        UpdateManager.getInstance().checkUpdate(activity, new UpdateManager.UpdateCheckCallback() {
            @Override
            public void onResult(final UpdateInfo info) {
                isCheckingUpdate = false;
                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    if (info != null && info.hasUpdate()) {
                        // 有可用更新
                        SettingsManager settings = SettingsManager.getInstance(activity);
                        if (settings.isAutoDownloadUpdate()) {
                            startAutoDownload(info, showToast);
                        } else {
                            showUpdateDialog(info);
                        }
                    } else if (info != null && info.isBetaUpdateOutdated()) {
                        // Beta 版本已严重落后，提示用户
                        showBetaOnlyNoticeDialog(info);
                    } else if (info != null && info.isBetaUpdateBlocked() && showToast) {
                        // Beta 更新被用户设置阻止
                        android.widget.Toast.makeText(activity,
                                R.string.update_beta_only_toast, android.widget.Toast.LENGTH_LONG).show();
                    } else if (showToast) {
                        // 无更新
                        android.widget.Toast.makeText(activity,
                                R.string.update_no_update, android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onError(final String message) {
                isCheckingUpdate = false;
                if (showToast && !activity.isFinishing() && !activity.isDestroyed()) {
                    android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled() {
                isCheckingUpdate = false;
            }
        });
    }

    /**
     * 显示更新信息对话框，包含版本号、更新日志等信息。
     * 强制更新时，对话框不可取消且不显示"稍后"按钮。
     *
     * @param info 更新信息
     */
    private void showUpdateDialog(final UpdateInfo info) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        // 强制更新时在标题中添加"强制更新"标识
        String title = info.isForceUpdate()
                ? activity.getString(R.string.update_force) + " - " + activity.getString(R.string.update_new_version)
                : activity.getString(R.string.update_new_version);

        // 构建更新信息文本
        StringBuilder message = new StringBuilder();
        message.append(String.format(activity.getString(R.string.update_version), info.getVersionName()));
        message.append("\n");
        message.append(activity.getString(R.string.update_channel_label, info.getChannelLabel()));
        message.append("\n");
        message.append(activity.getString(R.string.update_version_code, info.getVersionCode()));
        message.append("\n");
        message.append(String.format(activity.getString(R.string.update_size), info.getFileSizeFormatted()));
        message.append("\n\n");
        message.append(activity.getString(R.string.update_changelog));
        message.append("\n");
        message.append(info.getChangelog());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setMessage(message.toString())
                .setPositiveButton(R.string.update_download, (dialog, which) -> startDownload(info));

        if (!info.isForceUpdate()) {
            // 非强制更新，显示"稍后"按钮
            builder.setNegativeButton(R.string.update_later, (dialog, which) -> dialog.dismiss());
        } else {
            // 强制更新，对话框不可取消
            builder.setCancelable(false);
        }

        updateDialog = builder.create();
        updateDialog.show();
    }

    /**
     * 显示"仅 Beta 版本可用"提示对话框。
     * 当只有 Beta 版本有更新但用户未开启 Beta 更新时显示，
     * 引导用户开启 Beta 更新或等待稳定版发布。
     *
     * @param info 更新信息（仅包含 Beta 版本信息）
     */
    private void showBetaOnlyNoticeDialog(final UpdateInfo info) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        String lastStableName = info.getLastStableVersionName().isEmpty()
                ? activity.getString(R.string.update_last_stable_default)
                : info.getLastStableVersionName();
        StringBuilder message = new StringBuilder();
        message.append(activity.getString(R.string.update_beta_only_msg,
                info.getVersionName(), info.getVersionCode()));
        // 若有最新稳定版版本号，额外显示
        if (info.getLastStableVersionCode() > 0) {
            message.append(activity.getString(R.string.update_beta_only_stable_code,
                    info.getLastStableVersionCode()));
        }
        message.append(activity.getString(R.string.update_beta_only_hint));

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update_beta_only_title)
                .setMessage(message.toString())
                .setPositiveButton(R.string.update_beta_only_enable, (dialog, which) -> {
                    // 用户选择开启 Beta 更新，重新检查
                    SettingsManager.getInstance(activity).setAcceptBetaUpdate(true);
                    checkUpdate(true);
                })
                .setNegativeButton(R.string.update_beta_only_wait, null)
                .show();
    }

    /**
     * 显示下载进度对话框并开始下载。
     * 对话框包含进度条、百分比和已下载/总大小文本。
     * 强制更新时，进度对话框不可取消。
     *
     * @param info 更新信息
     */
    private void startDownload(final UpdateInfo info) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        final android.view.View dialogView = LayoutInflater.from(activity)
                .inflate(R.layout.dialog_update_progress, null);
        progressBar = dialogView.findViewById(R.id.progress_bar);
        tvProgressPercent = dialogView.findViewById(R.id.tv_progress_percent);
        tvProgressSize = dialogView.findViewById(R.id.tv_progress_size);

        progressBar.setMax(100);
        progressBar.setProgress(0, true);

        final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update_downloading)
                .setView(dialogView)
                .setCancelable(!info.isForceUpdate());

        progressDialog = builder.create();
        progressDialog.show();

        UpdateManager.getInstance().downloadApk(activity, info, new UpdateManager.DownloadCallback() {
            @Override
            public void onProgress(long downloaded, long total) {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                int percent = total > 0 ? (int) (downloaded * 100 / total) : 0;
                if (progressBar != null) progressBar.setProgress(percent, true);
                if (tvProgressPercent != null) tvProgressPercent.setText(percent + "%");
                if (tvProgressSize != null)
                    tvProgressSize.setText(formatDownloadProgress(downloaded, total));
            }

            @Override
            public void onVerifying() {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (tvProgressPercent != null)
                    tvProgressPercent.setText(activity.getString(R.string.update_verifying));
            }

            @Override
            public void onComplete(final File apkFile) {
                downloadedApk = apkFile;
                if (activity.isFinishing() || activity.isDestroyed()) return;
                dismissProgressDialog();
                showInstallDialog(apkFile);
            }

            @Override
            public void onError(final String message) {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                dismissProgressDialog();
                android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onCancelled() {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                dismissProgressDialog();
            }
        });
    }

    /**
     * 自动下载更新（无需用户确认下载）。
     * 仅显示开始下载和下载完成的 Toast 提示，不显示进度对话框。
     * 下载完成后根据用户设置决定是否弹出安装对话框。
     *
     * @param info      更新信息
     * @param showToast 是否显示 Toast 提示
     */
    private void startAutoDownload(final UpdateInfo info, boolean showToast) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        // 防止重复自动下载
        if (isAutoDownloadingUpdate) {
            if (showToast) {
                android.widget.Toast.makeText(activity,
                        R.string.update_auto_downloading, android.widget.Toast.LENGTH_SHORT).show();
            }
            return;
        }

        isAutoDownloadingUpdate = true;
        android.widget.Toast.makeText(activity,
                R.string.update_auto_download_started, android.widget.Toast.LENGTH_SHORT).show();

        UpdateManager.getInstance().downloadApk(activity, info, new UpdateManager.DownloadCallback() {
            @Override
            public void onProgress(long downloaded, long total) { }

            @Override
            public void onVerifying() { }

            @Override
            public void onComplete(final File apkFile) {
                isAutoDownloadingUpdate = false;
                downloadedApk = apkFile;
                if (activity.isFinishing() || activity.isDestroyed()) return;

                SettingsManager settings = SettingsManager.getInstance(activity);
                if (settings.isPromptInstallAfterAutoDownload()) {
                    // 自动下载后弹出安装确认对话框
                    showInstallDialog(apkFile);
                } else {
                    // 仅显示完成 Toast
                    android.widget.Toast.makeText(activity,
                            R.string.update_auto_download_complete, android.widget.Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onError(final String message) {
                isAutoDownloadingUpdate = false;
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (showToast) {
                    android.widget.Toast.makeText(activity, message, android.widget.Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled() {
                isAutoDownloadingUpdate = false;
            }
        });
    }

    /**
     * 显示安装确认对话框。
     * 提供三个选项：安装、打开下载目录、取消。
     *
     * @param apkFile 下载完成的 APK 文件
     */
    private void showInstallDialog(final File apkFile) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.update_new_version)
                .setMessage(R.string.update_install_prompt)
                .setPositiveButton(R.string.update_install, (dialog, which) -> installApk(apkFile))
                .setNeutralButton(R.string.update_open_directory, (dialog, which) ->
                        UpdateManager.getInstance().openDownloadDirectory(activity))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * 安装 APK 文件。
     * 先检查是否有安装权限，无权限则先请求权限再安装。
     *
     * @param apkFile 要安装的 APK 文件
     */
    private void installApk(File apkFile) {
        if (!UpdateManager.getInstance().canRequestInstall(activity)) {
            // 无安装权限，先请求权限
            UpdateManager.getInstance().requestInstallPermission(activity, REQUEST_INSTALL_PERMISSION);
            return;
        }
        UpdateManager.getInstance().installApk(activity, apkFile);
    }

    /**
     * 处理 Activity 结果回调。
     * 仅处理安装权限请求的结果，其他请求码直接返回 false。
     * <p>
     * 权限授权后自动触发安装；APK 文件丢失时提示用户；
     * 权限被拒绝时提示用户需要授权。
     * </p>
     *
     * @param requestCode  请求码
     * @param resultCode   结果码
     * @return true 表示已处理此结果，false 表示非本 Presenter 处理的请求
     */
    public boolean handleActivityResult(int requestCode, int resultCode) {
        if (requestCode != REQUEST_INSTALL_PERMISSION) return false;
        // 检查 APK 文件是否仍然存在
        if (downloadedApk == null || !downloadedApk.exists()) {
            android.widget.Toast.makeText(activity,
                    R.string.update_apk_lost, android.widget.Toast.LENGTH_SHORT).show();
            downloadedApk = null;
            return true;
        }
        // 用户授权或已有权限，触发安装
        if (resultCode == AppCompatActivity.RESULT_OK || UpdateManager.getInstance().canRequestInstall(activity)) {
            installApk(downloadedApk);
        } else {
            // 用户拒绝授权
            android.widget.Toast.makeText(activity,
                    R.string.update_install_permission_needed, android.widget.Toast.LENGTH_LONG).show();
        }
        return true;
    }

    /**
     * 安全关闭进度对话框。
     * 捕获可能的 IllegalArgumentException（Activity 已销毁时 dialog.dismiss() 可能抛出）。
     */
    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            try { progressDialog.dismiss(); } catch (Exception ignored) { Log.w(TAG, "Dismiss progress dialog: " + ignored.getMessage()); }
        }
        progressDialog = null;
    }

    /**
     * Activity 销毁时调用，关闭所有正在显示的对话框，防止窗口泄漏。
     * 应在 Activity 的 onDestroy() 中调用。
     */
    public void onDestroy() {
        dismissProgressDialog();
        if (updateDialog != null && updateDialog.isShowing()) {
            try { updateDialog.dismiss(); } catch (Exception ignored) { Log.w(TAG, "Dismiss update dialog: " + ignored.getMessage()); }
        }
        updateDialog = null;
    }

    /**
     * 格式化下载进度文本。
     * 显示已下载大小/总大小（百分比）的格式。
     *
     * @param downloaded 已下载字节数
     * @param total      总字节数
     * @return 格式化后的进度文本
     */
    private String formatDownloadProgress(long downloaded, long total) {
        String downloadedStr = formatFileSize(downloaded);
        int percent = total > 0 ? (int) (downloaded * 100 / total) : 0;
        if (total > 0) {
            String totalStr = formatFileSize(total);
            return downloadedStr + " / " + totalStr + " (" + percent + "%)";
        }
        return downloadedStr + " (" + percent + "%)";
    }

    /**
     * 将文件大小（字节）格式化为人类可读的字符串。
     * 小于 1KB 显示字节，小于 1MB 显示 KB，否则显示 MB。
     *
     * @param size 文件大小（字节）
     * @return 格式化后的大小字符串
     */
    private static String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }
}
