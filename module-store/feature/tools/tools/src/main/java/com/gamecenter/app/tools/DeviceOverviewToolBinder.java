package com.gamecenter.app.tools;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.gamecenter.app.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * X 风格的设备总览卡片。
 *
 * <p>只读取 Android 公共 API 和只读系统文件，不申请查询所有应用、存储管理或定位权限。
 * 这样总览功能可以在普通安装、动态模块和离线环境中稳定工作。</p>
 */
public final class DeviceOverviewToolBinder implements ToolBinder {

    private static final long GB = 1024L * 1024L * 1024L;
    private static final long MB = 1024L * 1024L;

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        Runnable refresh = () -> update(context, contentView);
        View refreshButton = contentView.findViewById(R.id.btn_device_overview_refresh);
        if (refreshButton != null) refreshButton.setOnClickListener(v -> refresh.run());
        View copyButton = contentView.findViewById(R.id.btn_device_overview_copy);
        if (copyButton != null) {
            copyButton.setOnClickListener(v -> {
                String report = buildReport(context);
                ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("设备总览", report));
                    Toast.makeText(context, R.string.tool_device_overview_copied, Toast.LENGTH_SHORT).show();
                }
            });
        }
        refresh.run();
    }

    private void update(Context context, View root) {
        Snapshot snapshot = Snapshot.capture(context);
        set(root, R.id.tv_overview_device, snapshot.device);
        set(root, R.id.tv_overview_android, snapshot.android);
        set(root, R.id.tv_overview_cpu, snapshot.cpu);
        set(root, R.id.tv_overview_memory, snapshot.memory);
        set(root, R.id.tv_overview_storage, snapshot.storage);
        set(root, R.id.tv_overview_battery, snapshot.battery);
        set(root, R.id.tv_overview_display, snapshot.display);
        set(root, R.id.tv_overview_abi, snapshot.abi);
    }

    private String buildReport(Context context) {
        Snapshot s = Snapshot.capture(context);
        return "设备总览\n"
                + "设备: " + s.device + "\n"
                + "系统: " + s.android + "\n"
                + "CPU: " + s.cpu + "\n"
                + "内存: " + s.memory + "\n"
                + "存储: " + s.storage + "\n"
                + "电池: " + s.battery + "\n"
                + "屏幕: " + s.display + "\n"
                + "ABI: " + s.abi;
    }

    private static void set(View root, int id, String text) {
        TextView view = root.findViewById(id);
        if (view != null) view.setText(text);
    }

    private static final class Snapshot {
        String device;
        String android;
        String cpu;
        String memory;
        String storage;
        String battery;
        String display;
        String abi;

        static Snapshot capture(Context context) {
            Snapshot result = new Snapshot();
            result.device = safe(Build.MANUFACTURER) + " " + safe(Build.MODEL);
            result.android = "Android " + safe(Build.VERSION.RELEASE)
                    + " · API " + Build.VERSION.SDK_INT + " · " + safe(Build.ID);
            int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
            String freq = readCpuFrequency();
            result.cpu = cores + " 核 · " + safe(Build.HARDWARE)
                    + (freq.isEmpty() ? "" : " · " + freq);
            result.abi = Build.SUPPORTED_ABIS == null || Build.SUPPORTED_ABIS.length == 0
                    ? "未知" : joinAbis(Build.SUPPORTED_ABIS);

            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            if (am != null) {
                am.getMemoryInfo(memoryInfo);
                long used = Math.max(0L, memoryInfo.totalMem - memoryInfo.availMem);
                result.memory = formatBytes(used) + " / " + formatBytes(memoryInfo.totalMem)
                        + " · 可用 " + formatBytes(memoryInfo.availMem);
            } else {
                result.memory = "不可用";
            }

            try {
                StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());
                long total = stat.getBlockCountLong() * stat.getBlockSizeLong();
                long available = stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
                result.storage = formatBytes(Math.max(0L, total - available)) + " / " + formatBytes(total)
                        + " · 可用 " + formatBytes(available);
            } catch (Exception ignored) {
                result.storage = "不可用";
            }

            Intent battery = getBatteryIntent(context);
            if (battery != null) {
                int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                int temp = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
                int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                String percentage = level >= 0 && scale > 0 ? Math.round(level * 100f / scale) + "%" : "未知";
                String temperature = temp == Integer.MIN_VALUE ? "未知" : String.format(Locale.getDefault(), "%.1f°C", temp / 10f);
                result.battery = percentage + " · " + batteryStatus(status) + " · " + temperature;
            } else {
                result.battery = "不可用";
            }

            result.display = displaySummary(context);
            return result;
        }

        private static Intent getBatteryIntent(Context context) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    return context.registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                            Context.RECEIVER_NOT_EXPORTED);
                }
                return context.registerReceiver(null, new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            } catch (Exception ignored) {
                return null;
            }
        }

        private static String displaySummary(Context context) {
            try {
                DisplayMetrics metrics = new DisplayMetrics();
                Display display = null;
                if (context instanceof Activity) {
                    display = ((Activity) context).getWindowManager().getDefaultDisplay();
                } else {
                    WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
                    if (wm != null) display = wm.getDefaultDisplay();
                }
                if (display == null) return "不可用";
                display.getRealMetrics(metrics);
                float refresh = 60f;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && display.getMode() != null) {
                    refresh = display.getMode().getRefreshRate();
                }
                return metrics.widthPixels + " × " + metrics.heightPixels + " px · "
                        + metrics.densityDpi + " dpi · "
                        + String.format(Locale.getDefault(), "%.0f Hz", refresh);
            } catch (Exception ignored) {
                return "不可用";
            }
        }

        private static String readCpuFrequency() {
            String[] paths = {
                    "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq",
                    "/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq"
            };
            for (String path : paths) {
                try (BufferedReader reader = new BufferedReader(new FileReader(new File(path)))) {
                    String value = reader.readLine();
                    if (value != null) {
                        long khz = Long.parseLong(value.trim());
                        if (khz > 0) return String.format(Locale.getDefault(), "%.2f GHz", khz / 1000000f);
                    }
                } catch (Exception ignored) {
                    // 部分厂商限制读取 cpufreq，继续尝试下一个公开只读路径。
                }
            }
            return "";
        }

        private static String formatBytes(long bytes) {
            if (bytes >= GB) return String.format(Locale.getDefault(), "%.2f GB", bytes / (double) GB);
            if (bytes >= MB) return String.format(Locale.getDefault(), "%.0f MB", bytes / (double) MB);
            return String.format(Locale.getDefault(), "%.0f KB", bytes / 1024.0);
        }

        private static String batteryStatus(int status) {
            switch (status) {
                case BatteryManager.BATTERY_STATUS_CHARGING: return "充电中";
                case BatteryManager.BATTERY_STATUS_FULL: return "已充满";
                case BatteryManager.BATTERY_STATUS_DISCHARGING: return "放电中";
                case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return "未充电";
                default: return "状态未知";
            }
        }

        private static String joinAbis(String[] abis) {
            StringBuilder builder = new StringBuilder();
            for (String abi : abis) {
                if (builder.length() > 0) builder.append(", ");
                builder.append(abi);
            }
            return builder.toString();
        }

        private static String safe(String value) {
            return value == null || value.trim().isEmpty() ? "未知" : value.trim();
        }
    }
}
