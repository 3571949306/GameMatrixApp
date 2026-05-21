package com.gamecenter.app.update;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import java.io.File;

/**
 * 更新通知助手，负责管理应用更新相关的系统通知。
 * <p>
 * 提供两种通知类型：
 * <ul>
 *   <li>下载进度通知：显示当前下载进度百分比，不可滑动消除</li>
 *   <li>下载完成通知：显示完成提示，点击可触发 APK 安装</li>
 * </ul>
 * 使用低优先级通知渠道，避免在用户使用时造成干扰。
 * </p>
 */
public class UpdateNotificationHelper {

    private static final String TAG = "UpdateNotificationHelper";
    /** 通知渠道 ID */
    static final String CHANNEL_ID = "update_download";
    /** 通知 ID，所有更新通知共用同一 ID 以实现更新而非堆叠 */
    static final int NOTIFICATION_ID = 1001;

    /**
     * 创建通知渠道（Android 8.0+ 必需）。
     * 使用默认优先级（IMPORTANCE_DEFAULT），确保用户能注意到下载进度。
     *
     * @param context 上下文
     */
    public void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "应用更新",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("显示应用更新下载进度");
            channel.enableVibration(false);
            channel.setSound(null, null);
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 显示下载进度通知。
     * 通知为持续型（ongoing），显示下载速度，并添加取消按钮。
     *
     * @param context     上下文
     * @param progress    下载进度百分比（0-100）
     * @param versionName 正在下载的版本名称
     * @param speed       下载速度（KB/s）
     */
    public void showDownloadNotification(Context context, int progress, String versionName, String speed) {
        createNotificationChannel(context);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        // 创建取消下载的PendingIntent
        Intent cancelIntent = new Intent(UpdateManager.ACTION_CANCEL_DOWNLOAD);
        cancelIntent.setPackage(context.getPackageName());
        PendingIntent cancelPendingIntent = PendingIntent.getBroadcast(
                context, 0, cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String contentText = "版本 " + versionName + " - " + progress + "%";
        if (speed != null && !speed.isEmpty()) {
            contentText += " | " + speed;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("正在下载更新")
                .setContentText(contentText)
                .setProgress(100, progress, false)
                .setOngoing(true)
                .setAutoCancel(false)
                .addAction(android.R.drawable.ic_menu_delete, "取消下载", cancelPendingIntent);

        manager.notify(NOTIFICATION_ID, builder.build());
    }

    /**
     * 兼容旧的调用方式，不传递速度
     */
    public void showDownloadNotification(Context context, int progress, String versionName) {
        showDownloadNotification(context, progress, versionName, "");
    }

    /**
     * 显示下载完成通知。
     * 通知可点击，点击后触发 APK 安装 Intent。
     * 通知为非持续型，可被用户滑动消除。
     *
     * @param context     上下文
     * @param apkFile     下载完成的 APK 文件
     * @param versionName 下载完成的版本名称
     */
    public void showDownloadCompleteNotification(Context context, File apkFile, String versionName) {
        createNotificationChannel(context);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        // 构建点击通知后的安装 Intent
        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Android 7.0+：使用 FileProvider 提供 content:// URI
            uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".update.fileprovider", apkFile);
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            uri = Uri.fromFile(apkFile);
        }
        installIntent.setDataAndType(uri, "application/vnd.android.package-archive");
        installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(context,
                (int) System.currentTimeMillis(), installIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("更新下载完成")
                .setContentText("版本 " + versionName + " - 点击安装")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOngoing(false);

        manager.notify(NOTIFICATION_ID, builder.build());
    }

    /**
     * 取消更新通知。
     * 通常在下载被取消或用户手动安装后调用。
     *
     * @param context 上下文
     */
    public void cancelNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }
    }
}
