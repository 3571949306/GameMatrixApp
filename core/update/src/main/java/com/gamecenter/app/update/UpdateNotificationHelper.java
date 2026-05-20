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
     * 使用低优先级（IMPORTANCE_LOW），通知不会发出声音，仅在通知栏静默显示。
     *
     * @param context 上下文
     */
    public void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "应用更新",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("显示应用更新下载进度");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * 显示下载进度通知。
     * 通知为持续型（ongoing），不可被用户滑动消除，避免下载过程中误删通知。
     *
     * @param context     上下文
     * @param progress    下载进度百分比（0-100）
     * @param versionName 正在下载的版本名称
     */
    public void showDownloadNotification(Context context, int progress, String versionName) {
        createNotificationChannel(context);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("正在下载更新")
                .setContentText("版本 " + versionName + " - " + progress + "%")
                .setProgress(100, progress, false)
                .setOngoing(true)
                .setAutoCancel(false);

        manager.notify(NOTIFICATION_ID, builder.build());
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
