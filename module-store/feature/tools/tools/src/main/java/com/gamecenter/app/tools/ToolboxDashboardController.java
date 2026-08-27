package com.gamecenter.app.tools;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.gamecenter.app.R;

import java.util.Locale;

public final class ToolboxDashboardController {

    private static final long REFRESH_INTERVAL_MS = 2000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            if (!attached) {
                return;
            }
            updateSystemMetrics();
            handler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    private Context appContext;
    private boolean attached;
    private RingView ring;
    private TextView tvTemp;
    private TextView tvRamValue;
    private TextView tvCpuValue;
    private TextView tvStorageValue;
    private ProgressBar pbRam;
    private ProgressBar pbCpu;
    private ProgressBar pbStorage;
    private BroadcastReceiver batteryReceiver;
    private long prevBusy = -1L;
    private long prevTotal = -1L;

    public void attach(View dashboardRoot) {
        if (attached || dashboardRoot == null) return;
        appContext = dashboardRoot.getContext().getApplicationContext();
        FrameLayout ringContainer = dashboardRoot.findViewById(R.id.fl_dash_ring_container);
        ring = new RingView(dashboardRoot.getContext());
        ring.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ringContainer.removeAllViews();
        ringContainer.addView(ring);
        tvTemp = dashboardRoot.findViewById(R.id.tv_dash_battery_temp);
        tvRamValue = dashboardRoot.findViewById(R.id.tv_dash_ram_value);
        tvCpuValue = dashboardRoot.findViewById(R.id.tv_dash_cpu_value);
        tvStorageValue = dashboardRoot.findViewById(R.id.tv_dash_storage_value);
        pbRam = dashboardRoot.findViewById(R.id.pb_dash_ram);
        pbCpu = dashboardRoot.findViewById(R.id.pb_dash_cpu);
        pbStorage = dashboardRoot.findViewById(R.id.pb_dash_storage);
        attached = true;
        registerBatteryReceiver();
        updateSystemMetrics();
        readCpuUsage();
        handler.postDelayed(refreshTask, REFRESH_INTERVAL_MS);
    }

    public void detach() {
        if (!attached) return;
        attached = false;
        handler.removeCallbacks(refreshTask);
        if (batteryReceiver != null && appContext != null) {
            try {
                appContext.unregisterReceiver(batteryReceiver);
            } catch (IllegalArgumentException ignored) {
            }
            batteryReceiver = null;
        }
        ring = null;
        tvTemp = null;
        tvRamValue = null;
        tvCpuValue = null;
        tvStorageValue = null;
        pbRam = null;
        pbCpu = null;
        pbStorage = null;
        appContext = null;
    }

    private void registerBatteryReceiver() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateBattery(intent);
            }
        };
        ContextCompat.registerReceiver(appContext, batteryReceiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void updateBattery(Intent intent) {
        if (intent == null || !attached) return;
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int tempTenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        if (level >= 0 && scale > 0) {
            int percent = Math.min(100, Math.max(0, Math.round(level * 100f / scale)));
            if (ring != null) {
                ring.setLevel(percent);
            }
        }
        if (tvTemp != null && tempTenths != Integer.MIN_VALUE) {
            tvTemp.setText(String.format(Locale.US, "%.1f °C", tempTenths / 10f));
        }
    }

    private void updateSystemMetrics() {
        ActivityManager am = (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        if (mi.totalMem > 0 && pbRam != null) {
            long used = mi.totalMem - mi.availMem;
            int percent = (int) ((used * 100) / mi.totalMem);
            pbRam.setProgress(percent);
            if (tvRamValue != null) {
                tvRamValue.setText(String.format(Locale.US, "%.1f/%.1f GB",
                        used / GB, mi.totalMem / GB));
            }
        }

        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long total = stat.getBlockCountLong() * stat.getBlockSizeLong();
            long avail = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
            long used = total - avail;
            if (total > 0 && pbStorage != null) {
                int percent = (int) ((used * 100) / total);
                pbStorage.setProgress(percent);
                if (tvStorageValue != null) {
                    tvStorageValue.setText(String.format(Locale.US, "%.0f/%.0f GB",
                            used / GB, total / GB));
                }
            }
        } catch (IllegalArgumentException ignored) {
        }

        readCpuUsage();
    }

    private void readCpuUsage() {
        long[] sample = readProcStat();
        if (sample == null || pbCpu == null) return;
        long busy = sample[0];
        long total = sample[1];
        if (prevBusy < 0 || prevTotal <= 0) {
            prevBusy = busy;
            prevTotal = total;
            return;
        }
        long dBusy = busy - prevBusy;
        long dTotal = total - prevTotal;
        prevBusy = busy;
        prevTotal = total;
        if (dTotal > 0) {
            int percent = (int) Math.min(100, Math.max(0, (dBusy * 100) / dTotal));
            pbCpu.setProgress(percent);
            if (tvCpuValue != null) {
                tvCpuValue.setText(percent + "%");
            }
        }
    }

    private static long[] readProcStat() {
        java.io.BufferedReader reader = null;
        try {
            reader = new java.io.BufferedReader(new java.io.FileReader("/proc/stat"));
            String line = reader.readLine();
            if (line == null || !line.startsWith("cpu ")) return null;
            String[] parts = line.trim().split("\\s+");
            if (parts.length < 5) return null;
            long idle = Long.parseLong(parts[4]);
            long iowait = parts.length > 5 ? Long.parseLong(parts[5]) : 0L;
            long total = 0L;
            for (int i = 1; i < parts.length; i++) {
                try {
                    total += Long.parseLong(parts[i]);
                } catch (NumberFormatException ignored) {
                }
            }
            long busy = total - idle - iowait;
            return new long[]{busy, total};
        } catch (Exception e) {
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static final float GB = 1024f * 1024f * 1024f;

    static final class RingView extends View {

        private int levelPercent = -1;
        private final float strokePx;
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        RingView(Context context) {
            super(context);
            float density = context.getResources().getDisplayMetrics().density;
            strokePx = 9f * density;
            int primary = themeColor(context, androidx.appcompat.R.attr.colorPrimary, 0xFF3D7EFF);
            trackPaint.setStyle(Paint.Style.STROKE);
            trackPaint.setStrokeWidth(strokePx);
            trackPaint.setStrokeCap(Paint.Cap.ROUND);
            trackPaint.setColor((primary & 0x00FFFFFF) | 0x2E000000);
            progressPaint.setStyle(Paint.Style.STROKE);
            progressPaint.setStrokeWidth(strokePx);
            progressPaint.setStrokeCap(Paint.Cap.ROUND);
            progressPaint.setColor(primary);
            textPaint.setColor(themeColor(context, android.R.attr.textColorPrimary, 0xFFEDEDED));
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setTextSize(16 * context.getResources().getDisplayMetrics().scaledDensity);
        }

        void setLevel(int percent) {
            this.levelPercent = Math.min(100, Math.max(0, percent));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = (Math.min(getWidth(), getHeight()) - strokePx) / 2f - 2f;
            RectF rect = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
            canvas.drawArc(rect, 0f, 360f, false, trackPaint);
            if (levelPercent >= 0) {
                canvas.drawArc(rect, -90f, levelPercent * 3.6f, false, progressPaint);
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                float baseline = cy - (fm.ascent + fm.descent) / 2f;
                canvas.drawText(levelPercent + "%", cx, baseline, textPaint);
            } else {
                Paint.FontMetrics fm = textPaint.getFontMetrics();
                float baseline = cy - (fm.ascent + fm.descent) / 2f;
                canvas.drawText("–", cx, baseline, textPaint);
            }
        }

        private static int themeColor(Context context, int attr, int fallback) {
            TypedValue value = new TypedValue();
            boolean resolved = context.getTheme().resolveAttribute(attr, value, true);
            if (!resolved) return fallback;
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return value.data;
            }
            try {
                return ContextCompat.getColor(context, value.resourceId);
            } catch (Exception e) {
                return fallback;
            }
        }
    }
}
