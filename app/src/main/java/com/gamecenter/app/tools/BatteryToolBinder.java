package com.gamecenter.app.tools;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.view.View;
import android.widget.TextView;
import com.gamecenter.app.R;
import java.util.concurrent.ExecutorService;

/**
 * 电池信息工具绑定器 — 需要处理 BroadcastReceiver 的注册/注销。
 * 注意：调用方需在 Fragment 的 onDestroyView 时调用 unbind()。
 */
public class BatteryToolBinder implements ToolBinder {
    private BroadcastReceiver batteryReceiver;
    private Context registeredContext;

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;

        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                    int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                    int voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
                    int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    int health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
                    int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);

                    int pct = (int) ((level / (float) scale) * 100);
                    final String fLevel = pct + "%";
                    final String fTemp = (temp / 10.0f) + " °C";
                    final String fVoltage = voltage > 0 ? (voltage / 1000.0f) + " V" : "未知";
                    final String fStatus = formatBatteryStatus(status, plugged);
                    final String fHealth = formatBatteryHealth(health);

                    ToolHelper.safeRunOnUiThread(ctx, () -> {
                        TextView viewLevel = contentView.findViewById(R.id.tv_battery_level);
                        TextView viewTemp = contentView.findViewById(R.id.tv_battery_temp);
                        TextView viewVoltage = contentView.findViewById(R.id.tv_battery_voltage);
                        TextView viewStatus = contentView.findViewById(R.id.tv_battery_status);
                        if (viewLevel != null) viewLevel.setText("电量: " + fLevel);
                        if (viewTemp != null) viewTemp.setText("温度: " + fTemp);
                        if (viewVoltage != null) viewVoltage.setText("电压: " + fVoltage);
                        if (viewStatus != null) viewStatus.setText(fStatus);
                    });
                }
            }
        };

        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        context.registerReceiver(batteryReceiver, filter);
        registeredContext = context;
    }

    public void unbind() {
        if (registeredContext != null && batteryReceiver != null) {
            try {
                registeredContext.unregisterReceiver(batteryReceiver);
            } catch (Exception ignored) {
            }
            registeredContext = null;
        }
    }

    private String formatBatteryStatus(int status, int plugged) {
        String statusStr = "未知";
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: statusStr = "充电中"; break;
            case BatteryManager.BATTERY_STATUS_DISCHARGING: statusStr = "放电中"; break;
            case BatteryManager.BATTERY_STATUS_FULL: statusStr = "已充满"; break;
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: statusStr = "未充电"; break;
        }
        if (plugged > 0) {
            String plugType;
            switch (plugged) {
                case BatteryManager.BATTERY_PLUGGED_AC: plugType = "AC"; break;
                case BatteryManager.BATTERY_PLUGGED_USB: plugType = "USB"; break;
                case BatteryManager.BATTERY_PLUGGED_WIRELESS: plugType = "无线"; break;
                default: plugType = "未知"; break;
            }
            statusStr += " (" + plugType + ")";
        }
        return statusStr;
    }

    private String formatBatteryHealth(int health) {
        String healthStr = "未知";
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD: healthStr = "良好"; break;
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: healthStr = "过热"; break;
            case BatteryManager.BATTERY_HEALTH_DEAD: healthStr = "已损坏"; break;
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: healthStr = "过压"; break;
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: healthStr = "未知故障"; break;
            case BatteryManager.BATTERY_HEALTH_COLD: healthStr = "过冷"; break;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (health == BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE) healthStr = "过压";
            else if (health == 9) healthStr = "电池老化";
        }
        return healthStr;
    }
}
