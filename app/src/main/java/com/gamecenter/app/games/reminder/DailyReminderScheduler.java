package com.gamecenter.app.games.reminder;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.gamecenter.app.R;

import java.util.Calendar;

/**
 * 每日定时提醒：每天 20:00 发一条本地通知，提醒用户完成每日挑战/每日关卡。
 *
 * <p>调度方案：宿主 app 模块没有 androidx.work 依赖，故走 {@link AlarmManager#setRepeating}
 * 普通重复闹钟（非精确）。API 19+ 上 setRepeating 本身即不精确，Doze 深度休眠下触发
 * 可能延迟到设备唤醒/解锁后的维护窗口——对"每日 20:00 提醒"这一需求可接受。
 * 注意：闹钟在设备重启后会丢失，ensureScheduled 挂在 App.onCreate（宿主进程），
 * 用户下次冷启动应用时会自动补注册。</p>
 *
 * <p>模块数据边界说明：每日挑战的 completed 状态宿主侧可查（DailyChallengeManager），
 * 但每日关卡（推箱子模块）的完成记录存储在模块自身的 SharedPreferences 中，宿主出于
 * 模块隔离原则不读取模块 SP。因此提醒文案简化为通用文案，不做完成态判定。</p>
 */
public final class DailyReminderScheduler {

    private static final String TAG = "DailyReminder";

    /** 闹钟/通知内部使用的 action（仅用于 PendingIntent 匹配幂等） */
    static final String ACTION_DAILY_REMINDER =
            "com.gamecenter.app.games.reminder.DAILY_REMINDER";

    /** 通知渠道 id：DEFAULT 重要性，用户可在系统设置中调整 */
    static final String CHANNEL_ID = "daily_reminder";

    /** 通知 id 固定：每天覆盖上一条，避免堆积 */
    private static final int NOTIFICATION_ID = 2001;

    /** 每日触发时刻：20:00 */
    private static final int TRIGGER_HOUR_OF_DAY = 20;

    private DailyReminderScheduler() {
    }

    /**
     * 幂等注册每日提醒闹钟。重复调用安全：
     * 先用 {@link PendingIntent#FLAG_NO_CREATE} 探测同 action 的 PendingIntent
     * 是否已注册（此前已设过闹钟），已存在则直接返回，不重复设置。
     */
    public static void ensureScheduled(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            Log.w(TAG, "AlarmManager 不可用，每日提醒未注册");
            return;
        }
        Intent intent = new Intent(context, Receiver.class).setAction(ACTION_DAILY_REMINDER);
        PendingIntent existing = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (existing != null) {
            return; // 已注册过，保持原闹钟不动（KEEP 语义）
        }
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, nextTriggerAtMillis(),
                AlarmManager.INTERVAL_DAY, pendingIntent);
        Log.i(TAG, "每日提醒已注册：每天 " + TRIGGER_HOUR_OF_DAY + ":00 触发");
    }

    /** 计算下一次 20:00 的时刻；当天 20:00 已过则顺延到明天。 */
    private static long nextTriggerAtMillis() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, TRIGGER_HOUR_OF_DAY);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        return calendar.getTimeInMillis();
    }

    /**
     * 发送每日提醒通知。
     * 通用文案，不做完成态判定（模块数据边界见类注释）。
     * Android 13+（targetSdk>=33）POST_NOTIFICATIONS 未授予/通知总开关关闭时静默不发。
     */
    public static void showNotification(Context context) {
        ensureChannel(context);
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context,
                        android.Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "通知权限未授予，跳过每日提醒");
            return;
        }
        if (!notificationManager.areNotificationsEnabled()) {
            Log.w(TAG, "应用通知被系统/用户关闭，跳过每日提醒");
            return;
        }
        // 点击打开应用入口：按包名读取 Manifest launcher activity（SplashActivity → MainActivity）
        Intent launchIntent = context.getPackageManager()
                .getLaunchIntentForPackage(context.getPackageName());
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText("今天的挑战等你来完成 ✦ 每日关卡与挑战都在等你")
                .setAutoCancel(true);
        if (launchIntent != null) {
            PendingIntent contentIntent = PendingIntent.getActivity(context, 0, launchIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            builder.setContentIntent(contentIntent);
        } else {
            Log.w(TAG, "无法解析应用入口 Intent，通知将不可点击");
        }
        try {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        } catch (SecurityException e) {
            // 极端场景：权限检查与发送之间授权被系统撤销
            Log.w(TAG, "每日提醒发送失败：通知权限被撤销", e);
        }
    }

    /** 创建通知渠道（Android O+），重复创建安全。 */
    private static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationManager notificationManager =
                context.getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            Log.w(TAG, "NotificationManager 不可用，渠道未创建");
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "每日提醒",
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("每日 20:00 提醒完成每日挑战与每日关卡");
        notificationManager.createNotificationChannel(channel);
    }

    /** 闹钟触发接收器：转发到 showNotification。 */
    public static class Receiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                showNotification(context.getApplicationContext());
            } catch (RuntimeException e) {
                Log.w(TAG, "每日提醒 onReceive 处理失败", e);
            }
        }
    }
}
