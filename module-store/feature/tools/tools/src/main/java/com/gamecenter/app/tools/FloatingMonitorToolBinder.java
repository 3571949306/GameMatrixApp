package com.gamecenter.app.tools;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.BatteryManager;
import android.os.Build;
import android.view.Choreographer;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.gamecenter.app.R;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class FloatingMonitorToolBinder implements ToolBinder {

    private WindowManager wm;
    private View floatView;
    private ScheduledExecutorService scheduler;
    private volatile boolean floating;

    private TextView tvCpuVal;
    private TextView tvRamVal;
    private TextView tvTempVal;
    private TextView tvFpsVal;

    private CheckBox cbCpu;
    private CheckBox cbRam;
    private CheckBox cbTemp;
    private CheckBox cbFps;

    private Choreographer fpsChoreographer;
    private volatile int frameCount;
    private long lastTempCelsius = -1;
    private BroadcastReceiver batteryReceiver;

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;

        Button btnStart = contentView.findViewById(R.id.btn_float_start);
        TextView tvStatus = contentView.findViewById(R.id.tv_float_status);
        cbCpu = contentView.findViewById(R.id.cb_float_cpu);
        cbRam = contentView.findViewById(R.id.cb_float_ram);
        cbTemp = contentView.findViewById(R.id.cb_float_temp);
        cbFps = contentView.findViewById(R.id.cb_float_fps);

        Context appContext = context.getApplicationContext();

        if (btnStart != null) {
            btnStart.setOnClickListener(v -> {
                if (floating) {
                    stopFloating();
                    btnStart.setText(R.string.float_btn_start);
                    if (tvStatus != null) tvStatus.setText("已停止悬浮监控");
                    return;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && !Settings.canDrawOverlays(appContext)) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:" + appContext.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    appContext.startActivity(intent);
                    Toast.makeText(appContext, "请授予悬浮窗权限后再次点击", Toast.LENGTH_LONG).show();
                    return;
                }
                startFloating(appContext, btnStart, tvStatus);
            });
        }

        if (contentView instanceof View) {
            contentView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {}

                @Override
                public void onViewDetachedFromWindow(View v) {
                    stopFloating();
                }
            });
        }
    }

    private void startFloating(Context appContext, Button btnStart, TextView tvStatus) {
        wm = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) {
            Toast.makeText(appContext, "无法访问 WindowManager", Toast.LENGTH_SHORT).show();
            return;
        }

        floatView = buildFloatView(appContext);

        int layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                layoutFlag,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                flags,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 40;
        params.y = 120;

        try {
            wm.addView(floatView, params);
        } catch (Exception e) {
            Toast.makeText(appContext, "悬浮窗启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            wm = null;
            floatView = null;
            return;
        }

        floating = true;
        if (btnStart != null) btnStart.setText("停止悬浮监控");
        if (tvStatus != null) tvStatus.setText("悬浮监控运行中");

        startBatteryMonitor(appContext);
        startFpsMonitor();
        startSampling(appContext);
    }

    private View buildFloatView(Context context) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int dp = (int) (10 * context.getResources().getDisplayMetrics().density);
        root.setPadding(dp, dp, dp, dp);
        root.setBackground(createRoundedBackground(0xCC000000,
                (int) (12 * context.getResources().getDisplayMetrics().density)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);

        TextView labelCpu = new TextView(context);
        labelCpu.setText("CPU");
        labelCpu.setTextColor(0xFFAAAAAA);
        labelCpu.setTextSize(11f);
        root.addView(labelCpu, lp);

        tvCpuVal = new TextView(context);
        tvCpuVal.setTextColor(0xFFFFFFFF);
        tvCpuVal.setTextSize(13f);
        tvCpuVal.setTypeface(android.graphics.Typeface.MONOSPACE);
        root.addView(tvCpuVal, lp);

        TextView labelRam = new TextView(context);
        labelRam.setText("RAM");
        labelRam.setTextColor(0xFFAAAAAA);
        labelRam.setTextSize(11f);
        root.addView(labelRam, lp);

        tvRamVal = new TextView(context);
        tvRamVal.setTextColor(0xFFFFFFFF);
        tvRamVal.setTextSize(13f);
        tvRamVal.setTypeface(android.graphics.Typeface.MONOSPACE);
        root.addView(tvRamVal, lp);

        TextView labelTemp = new TextView(context);
        labelTemp.setText("温度");
        labelTemp.setTextColor(0xFFAAAAAA);
        labelTemp.setTextSize(11f);
        root.addView(labelTemp, lp);

        tvTempVal = new TextView(context);
        tvTempVal.setTextColor(0xFFFFFFFF);
        tvTempVal.setTextSize(13f);
        tvTempVal.setTypeface(android.graphics.Typeface.MONOSPACE);
        root.addView(tvTempVal, lp);

        TextView labelFps = new TextView(context);
        labelFps.setText("FPS");
        labelFps.setTextColor(0xFFAAAAAA);
        labelFps.setTextSize(11f);
        root.addView(labelFps, lp);

        tvFpsVal = new TextView(context);
        tvFpsVal.setTextColor(0xFFFFFFFF);
        tvFpsVal.setTextSize(13f);
        tvFpsVal.setTypeface(android.graphics.Typeface.MONOSPACE);
        root.addView(tvFpsVal, lp);

        return root;
    }

    private android.graphics.drawable.GradientDrawable createRoundedBackground(int color, int cornerDp) {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setColor(color);
        gd.setCornerRadius(cornerDp);
        return gd;
    }

    private void startBatteryMonitor(Context appContext) {
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent i) {
                int tempRaw = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                if (tempRaw >= 0) {
                    lastTempCelsius = tempRaw / 10L;
                }
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        appContext.registerReceiver(batteryReceiver, filter);
    }

    private void startFpsMonitor() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) return;
        frameCount = 0;
        fpsChoreographer = Choreographer.getInstance();
        fpsChoreographer.postFrameCallback(new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                frameCount++;
                if (fpsChoreographer != null) {
                    fpsChoreographer.postFrameCallback(this);
                }
            }
        });
    }

    private void startSampling(Context appContext) {
        if (scheduler != null && !scheduler.isShutdown()) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FloatMonitor");
            t.setDaemon(true);
            return t;
        });
        final Handler mainHandler = new Handler(Looper.getMainLooper());

        scheduler.scheduleAtFixedRate(() -> {
            try {
                StringBuilder sb = new StringBuilder();

                if (cbCpu == null || cbCpu.isChecked()) {
                    double usedMB = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024.0 / 1024.0;
                    double totalMB = Runtime.getRuntime().totalMemory() / 1024.0 / 1024.0;
                    double percent = totalMB > 0 ? (usedMB / totalMB) * 100.0 : 0;
                    sb.append(String.format(Locale.getDefault(), "CPU  --  内存 %.0f%%", percent));
                    if (tvCpuVal != null) {
                        mainHandler.post(() -> tvCpuVal.setText(sb.toString()));
                    }
                } else {
                    if (tvCpuVal != null) mainHandler.post(() -> tvCpuVal.setVisibility(View.GONE));
                }

                if (cbRam == null || cbRam.isChecked()) {
                    ActivityManager am = (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
                    String ramText = "RAM  --  不可用";
                    if (am != null) {
                        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                        am.getMemoryInfo(mi);
                        long totalGB = mi.totalMem / (1024L * 1024L * 1024L);
                        long availMB = mi.availMem / (1024L * 1024L);
                        long usedGB = totalGB - availMB / 1024L;
                        double pct = mi.totalMem > 0 ? (double) (mi.totalMem - mi.availMem) / mi.totalMem * 100.0 : 0;
                        ramText = String.format(Locale.getDefault(), "RAM  %.1fGB/%.1fGB  %.0f%%",
                                (totalGB - availMB / 1024.0), totalGB, pct);
                    }
                    if (tvRamVal != null) {
                        String finalRamText = ramText;
                        mainHandler.post(() -> tvRamVal.setText(finalRamText));
                    }
                }

                if (cbTemp == null || cbTemp.isChecked()) {
                    String tempText = "温度  --  暂无数据";
                    if (lastTempCelsius >= 0) {
                        tempText = String.format(Locale.getDefault(), "温度  %d°C", lastTempCelsius);
                    }
                    if (tvTempVal != null) {
                        String finalTempText = tempText;
                        mainHandler.post(() -> tvTempVal.setText(finalTempText));
                    }
                }

                if (cbFps == null || cbFps.isChecked()) {
                    int fps = frameCount * 1000 / 800;
                    frameCount = 0;
                    String fpsText = String.format(Locale.getDefault(), "FPS  %d", fps);
                    if (tvFpsVal != null) {
                        mainHandler.post(() -> tvFpsVal.setText(fpsText));
                    }
                }

            } catch (Exception ignored) {
            }
        }, 0, 800, TimeUnit.MILLISECONDS);
    }

    private void stopFloating() {
        floating = false;

        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        if (fpsChoreographer != null) {
            try {
                fpsChoreographer.removeFrameCallback(null);
            } catch (Exception ignored) {}
            fpsChoreographer = null;
        }
        if (batteryReceiver != null && floatView != null && floatView.getContext() != null) {
            try {
                floatView.getContext().unregisterReceiver(batteryReceiver);
            } catch (Exception ignored) {}
            batteryReceiver = null;
        }
        if (wm != null && floatView != null) {
            try {
                wm.removeView(floatView);
            } catch (Exception ignored) {}
            floatView = null;
        }
        wm = null;
        tvCpuVal = null;
        tvRamVal = null;
        tvTempVal = null;
        tvFpsVal = null;
    }
}
