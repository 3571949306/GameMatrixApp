package com.gamecenter.app.tools;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.view.View;
import android.widget.TextView;
import com.gamecenter.app.R;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * 传感器信息工具绑定器。
 * <p>
 * 负责将传感器信息工具的 UI 视图与设备传感器列表读取逻辑进行绑定。
 * 通过 {@link SensorManager} 获取设备所有传感器的详细信息并展示，包括：
 * 传感器类型名称、厂商、最大范围、分辨率、最小延迟和版本等。
 * 关键设计决策：
 * <ul>
 *   <li>使用 {@link SensorManager#getSensorList(int)} 传入 {@link Sensor#TYPE_ALL} 获取全部传感器</li>
 *   <li>传感器类型名称通过 {@link ToolHelper#sensorTypeName(int)} 转换为中文可读名称</li>
 *   <li>最小延迟从纳秒转换为微秒显示，更符合用户直觉</li>
 * </ul>
 * </p>
 */
public final class SensorToolBinder implements ToolBinder {

    /**
     * 绑定传感器信息工具的视图，读取并显示设备传感器列表。
     * <p>
     * 通过系统服务获取 {@link SensorManager}，查询所有传感器并逐个格式化输出信息。
     * 仅当 SensorManager 和 TextView 均可用时才执行。
     * </p>
     *
     * @param context     上下文环境，用于获取 SensorManager 系统服务
     * @param contentView 工具页面的根视图，包含传感器列表的 TextView
     * @param executor    线程池执行器（本工具未使用，因传感器列表读取为轻量同步操作）
     */
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        TextView tvList = contentView.findViewById(R.id.tv_sensor_list);
        // 获取系统传感器服务
        SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sm != null && tvList != null) {
            // 查询设备上所有可用的传感器
            List<Sensor> sensors = sm.getSensorList(Sensor.TYPE_ALL);
            StringBuilder sb = new StringBuilder();
            sb.append("共检测到 ").append(sensors.size()).append(" 个传感器：\n\n");
            for (Sensor sensor : sensors) {
                // 使用 ToolHelper 将传感器类型 ID 转换为中文可读名称
                sb.append("  ").append(ToolHelper.sensorTypeName(sensor.getType())).append("\n");
                sb.append("  厂商: ").append(sensor.getVendor()).append("\n");
                sb.append("  最大范围: ").append(sensor.getMaximumRange()).append("\n");
                sb.append("  分辨率: ").append(sensor.getResolution()).append("\n");
                // 最小延迟单位从纳秒转换为微秒，更直观
                sb.append("  最小延迟: ").append(sensor.getMinDelay() / 1000).append(" μs\n");
                sb.append("  版本: ").append(sensor.getVersion()).append("\n");
                sb.append("  类型ID: ").append(sensor.getType()).append("\n\n");
            }
            tvList.setText(sb.toString().trim());
        }
    }
}
