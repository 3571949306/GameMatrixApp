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
 * 传感器工具绑定器
 */
public final class SensorToolBinder implements ToolBinder {
    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        TextView tvList = contentView.findViewById(R.id.tv_sensor_list);
        SensorManager sm = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sm != null && tvList != null) {
            List<Sensor> sensors = sm.getSensorList(Sensor.TYPE_ALL);
            StringBuilder sb = new StringBuilder();
            sb.append("共检测到 ").append(sensors.size()).append(" 个传感器：\n\n");
            for (Sensor sensor : sensors) {
                sb.append("  ").append(ToolHelper.sensorTypeName(sensor.getType())).append("\n");
                sb.append("  厂商: ").append(sensor.getVendor()).append("\n");
                sb.append("  最大范围: ").append(sensor.getMaximumRange()).append("\n");
                sb.append("  分辨率: ").append(sensor.getResolution()).append("\n");
                sb.append("  最小延迟: ").append(sensor.getMinDelay() / 1000).append(" μs\n");
                sb.append("  版本: ").append(sensor.getVersion()).append("\n");
                sb.append("  类型ID: ").append(sensor.getType()).append("\n\n");
            }
            tvList.setText(sb.toString().trim());
        }
    }
}
