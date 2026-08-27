package com.gamecenter.app.adb;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.*;
import java.util.LinkedHashSet;
import java.util.Set;

/** Private-process session owner. START_NOT_STICKY prevents replay after process death. */
public final class AdbSessionService extends Service {
    interface Listener { void changed(); }
    final class LocalBinder extends Binder { AdbSessionService service() { return AdbSessionService.this; } }
    private final LocalBinder binder = new LocalBinder();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<Listener> listeners = new LinkedHashSet<>();
    private boolean foreground, foregroundUnavailable, exitAfterWork, closing;
    private AdbEngine engine;
    private boolean receiverRegistered;
    private final Runnable deliver = () -> {
        if (closing || engine == null) return;
        updateForeground();
        for (Listener listener : listeners.toArray(new Listener[0])) listener.changed();
        if (exitAfterWork && engine.activeJobs() == 0) shutdown();
    };
    private final BroadcastReceiver detach = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (device != null && engine != null) engine.detached(device.getDeviceName());
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        engine = new AdbEngine(getApplicationContext(), () -> {
            // Coalesce chunk progress; no queued Runnable captures an Activity listener.
            main.removeCallbacks(deliver); main.postDelayed(deliver, 80);
        });
        IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(detach, filter, Context.RECEIVER_NOT_EXPORTED); else registerReceiver(detach, filter);
        receiverRegistered = true;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel("adb_tasks", getString(R.string.adb_notification_channel), NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_NOT_STICKY; }
    @Override public IBinder onBind(Intent intent) { exitAfterWork = false; main.removeCallbacks(orphanCleanup); return binder; }
    public AdbEngine engine() { return engine; }
    public void observe(Listener listener) { listeners.add(listener); listener.changed(); }
    public void remove(Listener listener) { listeners.remove(listener); }
    void leave(boolean finish, boolean backgroundJob) {
        if (!finish) { main.removeCallbacks(orphanCleanup); main.postDelayed(orphanCleanup, 10000); return; }
        if (backgroundJob && engine.activeJobs() > 0) { exitAfterWork = true; updateForeground(); }
        else shutdown();
    }
    private final Runnable orphanCleanup = () -> { if (listeners.isEmpty() && !closing) { if (engine.activeJobs() > 0) exitAfterWork = true; else shutdown(); } };
    private void updateForeground() {
        if (engine.activeJobs() > 0) {
            Intent intent = new Intent(this, AdbWorkbenchActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent open = PendingIntent.getActivity(this, 12, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "adb_tasks") : new Notification.Builder(this);
            Notification notification = builder.setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle(getString(R.string.adb_notification_title))
                    .setContentText(getString(R.string.adb_notification_text)).setContentIntent(open).setOngoing(true).build();
            try {
                if (Build.VERSION.SDK_INT >= 29) startForeground(1301, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
                else startForeground(1301, notification);
                foreground = true;
                foregroundUnavailable = false;
            } catch (RuntimeException error) {
                // engine.message() schedules deliver again. Report once to avoid an updateForeground loop.
                if (!foregroundUnavailable) {
                    foregroundUnavailable = true;
                    engine.message("无法保持后台任务：" + AdbEngine.explain(error) + "。请保持工作台前台。");
                }
            }
        } else if (foreground) { stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; }
    }
    private void shutdown() {
        if (closing) return; closing = true;
        main.removeCallbacksAndMessages(null); listeners.clear();
        if (foreground) { stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; }
        // USB close/codec cleanup must never block the main thread.
        AdbEngine owned = engine;
        new Thread(() -> { owned.close(); stopSelf(); }, "gm-adb-cleanup").start();
    }
    @Override public void onDestroy() {
        closing = true; main.removeCallbacksAndMessages(null); listeners.clear();
        if (receiverRegistered) { unregisterReceiver(detach); receiverRegistered = false; }
        AdbEngine owned = engine; engine = null;
        if (owned != null && !owned.isClosed()) new Thread(owned::close, "gm-adb-final-close").start();
        super.onDestroy();
    }
}
