package com.gamecenter.app.tools;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.gamecenter.app.R;

import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * 指南针工具，优先使用旋转矢量，缺失时回退到加速度计 + 地磁传感器。
 * 不需要定位权限；传感器监听只在工作区打开时保持活跃。
 */
public final class CompassToolBinder implements ToolBinder {

    private SensorManager sensorManager;
    private SensorEventListener listener;
    private CompassView compassView;
    private TextView headingView;
    private TextView statusView;
    private float[] gravityValues;
    private float[] magneticValues;
    private float offset;
    private float lastHeading = Float.NaN;

    @Override
    public void bind(Context context, View contentView, ExecutorService executor) {
        if (contentView == null) return;
        FrameLayout canvasContainer = contentView.findViewById(R.id.fl_compass_canvas);
        if (canvasContainer != null) {
            compassView = new CompassView(context);
            compassView.setContentDescription(context.getString(R.string.tool_compass_title));
            canvasContainer.removeAllViews();
            canvasContainer.addView(compassView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        }
        headingView = contentView.findViewById(R.id.tv_compass_heading);
        statusView = contentView.findViewById(R.id.tv_compass_status);
        View calibrate = contentView.findViewById(R.id.btn_compass_calibrate);
        if (calibrate != null) calibrate.setOnClickListener(v -> {
            if (!Float.isNaN(lastHeading)) {
                offset = lastHeading;
                setHeading(0f);
            }
        });

        Context appContext = context.getApplicationContext();
        sensorManager = (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager == null) {
            setUnavailable("无法访问传感器服务");
            return;
        }

        Sensor rotation = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        Sensor gravity = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor magnetic = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (rotation == null && (gravity == null || magnetic == null)) {
            setUnavailable("设备无可用指南针传感器");
            return;
        }

        final Sensor rotationSensor = rotation;
        listener = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent event) {
                float azimuth = Float.NaN;
                if (rotationSensor != null && event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                    float[] matrix = new float[9];
                    SensorManager.getRotationMatrixFromVector(matrix, event.values);
                    float[] orientation = new float[3];
                    SensorManager.getOrientation(matrix, orientation);
                    azimuth = (float) Math.toDegrees(orientation[0]);
                } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                    gravityValues = event.values.clone();
                } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                    magneticValues = event.values.clone();
                }
                if (Float.isNaN(azimuth) && gravityValues != null && magneticValues != null) {
                    float[] matrix = new float[9];
                    float[] inclination = new float[9];
                    if (SensorManager.getRotationMatrix(matrix, inclination, gravityValues, magneticValues)) {
                        float[] orientation = new float[3];
                        SensorManager.getOrientation(matrix, orientation);
                        azimuth = (float) Math.toDegrees(orientation[0]);
                    }
                }
                if (!Float.isNaN(azimuth)) updateHeading(azimuth);
            }

            @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {
                if (statusView != null && accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
                    statusView.setText("传感器精度较低，请旋转设备校准");
                }
            }
        };

        boolean registered = false;
        if (rotation != null) registered = sensorManager.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_UI);
        if (!registered && gravity != null && magnetic != null) {
            registered = sensorManager.registerListener(listener, gravity, SensorManager.SENSOR_DELAY_UI);
            registered = sensorManager.registerListener(listener, magnetic, SensorManager.SENSOR_DELAY_UI) && registered;
        }
        if (registered) {
            if (statusView != null) statusView.setText(R.string.tool_compass_status_waiting);
        } else {
            setUnavailable("无法注册指南针传感器");
        }
        if (compassView != null) {
            compassView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View v) { }
                @Override public void onViewDetachedFromWindow(View v) { unbind(); }
            });
        }
    }

    private void updateHeading(float rawHeading) {
        float normalized = (rawHeading - offset + 360f) % 360f;
        if (!Float.isNaN(lastHeading)) {
            float delta = ((normalized - lastHeading + 540f) % 360f) - 180f;
            normalized = lastHeading + delta * 0.25f;
            normalized = (normalized % 360f + 360f) % 360f;
        }
        lastHeading = normalized;
        setHeading(normalized);
    }

    private void setHeading(float heading) {
        if (compassView != null) compassView.setHeading(heading);
        if (headingView != null) {
            headingView.setText(String.format(Locale.getDefault(), "%.0f°  %s", heading, direction(heading)));
        }
        if (statusView != null) statusView.setText("传感器正常 · 仅在本地处理");
    }

    private void setUnavailable(String message) {
        if (compassView != null) compassView.setAvailable(false);
        if (headingView != null) headingView.setText("--°");
        if (statusView != null) statusView.setText(message);
    }

    private static String direction(float heading) {
        final String[] names = {"北", "东北", "东", "东南", "南", "西南", "西", "西北"};
        int index = Math.round(heading / 45f) % 8;
        return names[index];
    }

    public void unbind() {
        if (sensorManager != null && listener != null) {
            sensorManager.unregisterListener(listener);
        }
        listener = null;
        gravityValues = null;
        magneticValues = null;
    }
}
