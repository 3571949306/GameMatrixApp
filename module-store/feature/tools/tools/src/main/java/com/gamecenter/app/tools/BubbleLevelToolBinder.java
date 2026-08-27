package com.gamecenter.app.tools;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.gamecenter.app.R;

import java.util.Locale;
import java.util.concurrent.ExecutorService;

public final class BubbleLevelToolBinder implements ToolBinder {

    private SensorManager sensorManager;
    private Sensor gravity;
    private SensorEventListener listener;
    private BubbleLevelView bubbleLevelView;
    private TextView tvStatus;
    private TextView tvAngle;
    private float zeroX;
    private float zeroY;
    private float currentRawX;
    private float currentRawY;

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;

        bubbleLevelView = contentView.findViewById(R.id.v_bubble_canvas);
        tvStatus = contentView.findViewById(R.id.tv_level_status);
        tvAngle = contentView.findViewById(R.id.tv_level_angle);
        Button btnCalibrate = contentView.findViewById(R.id.btn_level_calibrate);

        Context appContext = context.getApplicationContext();
        sensorManager = (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager == null) {
            if (tvStatus != null) tvStatus.setText("无法访问传感器服务");
            return;
        }

        gravity = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
        if (gravity == null) {
            if (tvStatus != null) tvStatus.setText("设备无重力传感器");
            return;
        }

        zeroX = 0f;
        zeroY = 0f;
        currentRawX = 0f;
        currentRawY = 0f;

        if (btnCalibrate != null) {
            btnCalibrate.setOnClickListener(v -> {
                zeroX = currentRawX;
                zeroY = currentRawY;
            });
        }

        listener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                float[] values = event.values;
                float rawX = values[0];
                float rawY = values[1];
                float rawZ = values[2];

                float rawAngleX = (float) Math.toDegrees(Math.atan2(rawX, Math.sqrt(rawY * rawY + rawZ * rawZ)));
                float rawAngleY = (float) Math.toDegrees(Math.atan2(-rawY, Math.sqrt(rawX * rawX + rawZ * rawZ)));

                currentRawX = rawAngleX;
                currentRawY = rawAngleY;

                float angleX = rawAngleX - zeroX;
                float angleY = rawAngleY - zeroY;

                if (bubbleLevelView != null) {
                    bubbleLevelView.setAngles(angleX, angleY);
                }
                if (tvAngle != null) {
                    tvAngle.setText(String.format(Locale.getDefault(), "X: %.1f°  Y: %.1f°", angleX, angleY));
                }
                if (tvStatus != null) {
                    float tilt = (float) Math.sqrt(angleX * angleX + angleY * angleY);
                    if (tilt < 1.0f) {
                        tvStatus.setText("几乎水平 ✓");
                    } else if (tilt < 15.0f) {
                        tvStatus.setText(String.format(Locale.getDefault(), "倾斜 %.1f°", tilt));
                    } else {
                        tvStatus.setText("倾斜");
                    }
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };

        sensorManager.registerListener(listener, gravity, SensorManager.SENSOR_DELAY_UI);

        if (bubbleLevelView != null) {
            bubbleLevelView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    if (sensorManager != null && listener != null) {
                        sensorManager.unregisterListener(listener);
                    }
                }
            });
        }
    }
}
