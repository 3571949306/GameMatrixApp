package com.gamecenter.app.tools;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.util.Log;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.view.View;
import android.widget.TextView;
import com.gamecenter.app.R;
import java.util.concurrent.ExecutorService;

/**
 * 电池信息工具绑定器，实现 {@link ToolBinder} 接口。
 * <p>
 * 通过注册 {@link BroadcastReceiver} 监听系统电池状态变化广播（ACTION_BATTERY_CHANGED），
 * 实时更新 UI 上的电量、温度、电压和充电状态信息。
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>使用动态注册广播而非静态注册，因为 ACTION_BATTERY_CHANGED 是粘性广播，
 *       动态注册可立即获取当前状态且避免不必要的后台唤醒</li>
 *   <li>持有 registeredContext 引用，用于在 unbind() 时注销广播接收器，
 *       防止内存泄漏和重复注册</li>
 * </ul>
 * <p>
 * <b>重要：</b>调用方必须在 Fragment 的 onDestroyView 时调用 {@link #unbind()}，
 * 否则会导致广播接收器泄漏。
 */
public class BatteryToolBinder implements ToolBinder {
    private static final String TAG = "BatteryToolBinder";

    /** 电池状态变化广播接收器，用于实时监听电池信息更新 */
    private BroadcastReceiver batteryReceiver;

    /** 注册广播时使用的上下文，注销时需要使用同一上下文 */
    private Context registeredContext;

    /**
     * 绑定电池信息工具的 UI 交互。
     * <p>
     * 注册电池状态变化广播接收器，每次收到广播时更新 UI 上的电量百分比、
     * 电池温度、电压和充电状态。使用 {@link ToolHelper#safeRunOnUiThread} 确保
     * UI 更新在主线程执行。
     *
     * @param context     上下文，用于注册广播接收器和获取系统服务
     * @param contentView 工具页面的根视图，用于查找电池信息显示的 TextView
     * @param executor    线程池（本工具未使用，因为电池信息通过广播实时获取）
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;

        batteryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
                    int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);
                    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    // 电池温度原始值单位为 0.1°C，需除以 10 转换为摄氏度
                    int temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                    // 电压原始值单位为 mV，需除以 1000 转换为伏特
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

                    // 通过 ToolHelper 安全地在 UI 线程更新视图
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

    /**
     * 注销电池状态广播接收器，释放资源。
     * <p>
     * 必须在 Fragment/Activity 销毁时调用，防止广播接收器泄漏。
     * 注销失败时（如接收器已被注销）仅打印警告日志，不抛出异常。
     */
    public void unbind() {
        if (registeredContext != null && batteryReceiver != null) {
            try {
                registeredContext.unregisterReceiver(batteryReceiver);
            } catch (Exception ignored) {
                Log.w(TAG, "Unregister battery receiver failed: " + ignored.getMessage());
            }
            registeredContext = null;
        }
    }

    /**
     * 格式化电池充电状态和充电方式。
     * <p>
     * 将充电状态（充电中/放电中/已充满/未充电）和充电方式（AC/USB/无线）
     * 组合为可读字符串，如 "充电中 (AC)"。
     *
     * @param status  电池状态常量，来自 BatteryManager.EXTRA_STATUS
     * @param plugged 充电方式常量，来自 BatteryManager.EXTRA_PLUGGED，>0 表示正在充电
     * @return 格式化的充电状态字符串
     */
    private String formatBatteryStatus(int status, int plugged) {
        String statusStr = "未知";
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING: statusStr = "充电中"; break;
            case BatteryManager.BATTERY_STATUS_DISCHARGING: statusStr = "放电中"; break;
            case BatteryManager.BATTERY_STATUS_FULL: statusStr = "已充满"; break;
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: statusStr = "未充电"; break;
        }
        // plugged > 0 表示当前连接了充电器
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

    /**
     * 格式化电池健康状态。
     * <p>
     * 将 BatteryManager 的健康状态常量转换为中文描述。
     * 在 Android Q (API 29) 及以上版本，额外处理了电池老化状态（health == 9）。
     *
     * @param health 电池健康状态常量，来自 BatteryManager.EXTRA_HEALTH
     * @return 格式化的健康状态字符串
     */
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
        // Android Q 及以上版本新增了电池老化状态码（9），BatteryManager 常量中未定义
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (health == BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE) healthStr = "过压";
            else if (health == 9) healthStr = "电池老化";
        }
        return healthStr;
    }
}
