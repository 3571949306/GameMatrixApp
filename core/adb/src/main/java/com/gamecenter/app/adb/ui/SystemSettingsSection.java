package com.gamecenter.app.adb.ui;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.gamecenter.app.adb.AdbEngine;
import com.gamecenter.app.adb.AdbSessionService;
import com.gamecenter.app.adb.R;

/**
 * System settings section: device info, DPI, resolution, animation scale, reboot.
 * All dangerous operations require explicit confirmation dialogs.
 */
public final class SystemSettingsSection extends BaseSection {

    private TextView model, brand, androidVersion, sdk, build, serial, dpiText, resolutionText;
    private EditText dpiInput, resW, resH, animInput;

    @Override
    public View createView(Activity activity) {
        activityRef = new java.lang.ref.WeakReference<>(activity);
        View view = LayoutInflater.from(activity).inflate(R.layout.fragment_system_settings, null);

        model = view.findViewById(R.id.adb_sys_model);
        brand = view.findViewById(R.id.adb_sys_brand);
        androidVersion = view.findViewById(R.id.adb_sys_android_version);
        sdk = view.findViewById(R.id.adb_sys_sdk);
        build = view.findViewById(R.id.adb_sys_build);
        serial = view.findViewById(R.id.adb_sys_serial);
        dpiText = view.findViewById(R.id.adb_sys_dpi);
        resolutionText = view.findViewById(R.id.adb_sys_resolution);

        dpiInput = view.findViewById(R.id.adb_sys_dpi_input);
        resW = view.findViewById(R.id.adb_sys_res_w);
        resH = view.findViewById(R.id.adb_sys_res_h);
        animInput = view.findViewById(R.id.adb_sys_anim_input);

        setupButtons(view);
        return view;
    }

    private void setupButtons(View root) {
        TextView dpiApply = root.findViewById(R.id.adb_sys_dpi_apply);
        if (dpiApply != null) dpiApply.setOnClickListener(v -> applyDpi());

        TextView dpiReset = root.findViewById(R.id.adb_sys_dpi_reset);
        if (dpiReset != null) dpiReset.setOnClickListener(v -> {
            if (dpiInput != null) dpiInput.setText("");
            showBottomMessage("已重置，需重启设备生效");
        });

        TextView resApply = root.findViewById(R.id.adb_sys_res_apply);
        if (resApply != null) resApply.setOnClickListener(v -> applyResolution());

        TextView animApply = root.findViewById(R.id.adb_sys_anim_apply);
        if (animApply != null) animApply.setOnClickListener(v -> applyAnimationScale());

        TextView reboot = root.findViewById(R.id.adb_sys_reboot);
        if (reboot != null) reboot.setOnClickListener(v -> showRebootConfirm("reboot"));

        TextView rebootRecovery = root.findViewById(R.id.adb_sys_reboot_recovery);
        if (rebootRecovery != null) rebootRecovery.setOnClickListener(v -> showRebootConfirm("recovery"));

        TextView rebootBootloader = root.findViewById(R.id.adb_sys_reboot_bootloader);
        if (rebootBootloader != null) rebootBootloader.setOnClickListener(v -> showRebootConfirm("bootloader"));
    }

    private void applyDpi() {
        Activity act = activity();
        if (act == null || engine() == null) return;
        AdbEngine.Session selected = engine().selected();
        if (selected == null) {
            showBottomMessage(act.getString(R.string.adb_system_no_device));
            return;
        }
        String dpiStr = dpiInput.getText().toString().trim();
        if (dpiStr.isEmpty()) {
            showBottomMessage("请输入 DPI 值");
            return;
        }
        try {
            int dpi = Integer.parseInt(dpiStr);
            engine().setDpi(selected.id, dpi);
            showBottomMessage("DPI 已设置为 " + dpi);
        } catch (NumberFormatException e) {
            showBottomMessage("DPI 必须是整数");
        }
    }

    private void applyResolution() {
        Activity act = activity();
        if (act == null || engine() == null) return;
        AdbEngine.Session selected = engine().selected();
        if (selected == null) {
            showBottomMessage(act.getString(R.string.adb_system_no_device));
            return;
        }
        try {
            int width = Integer.parseInt(resW.getText().toString().trim());
            int height = Integer.parseInt(resH.getText().toString().trim());
            engine().setResolution(selected.id, width, height);
            showBottomMessage("分辨率已设置为 " + width + "x" + height);
        } catch (NumberFormatException e) {
            showBottomMessage("宽高必须是整数");
        }
    }

    private void applyAnimationScale() {
        Activity act = activity();
        if (act == null || engine() == null) return;
        AdbEngine.Session selected = engine().selected();
        if (selected == null) {
            showBottomMessage(act.getString(R.string.adb_system_no_device));
            return;
        }
        String scaleStr = animInput.getText().toString().trim();
        if (scaleStr.isEmpty()) {
            showBottomMessage("请输入缩放值");
            return;
        }
        try {
            float scale = Float.parseFloat(scaleStr);
            engine().setAnimationScale(selected.id, scale);
            showBottomMessage("动画缩放已设置为 " + scale);
        } catch (NumberFormatException e) {
            showBottomMessage("缩放值格式不正确");
        }
    }

    private void showRebootConfirm(String mode) {
        Activity act = activity();
        if (act == null || engine() == null) return;
        AdbEngine.Session selected = engine().selected();
        if (selected == null) {
            showBottomMessage(act.getString(R.string.adb_system_no_device));
            return;
        }
        String label;
        switch (mode) {
            case "recovery": label = act.getString(R.string.adb_system_reboot_recovery); break;
            case "bootloader": label = act.getString(R.string.adb_system_reboot_bootloader); break;
            default: label = act.getString(R.string.adb_system_reboot); break;
        }
        new android.app.AlertDialog.Builder(act)
                .setMessage(act.getString(R.string.adb_system_reboot_confirm, label))
                .setPositiveButton(R.string.adb_ok, (d, w) -> {
                    engine().reboot(selected.id, mode);
                    showBottomMessage("设备正在重启…");
                })
                .setNegativeButton(R.string.adb_cancel, null)
                .show();
    }

    @Override
    protected void onEngineBound(AdbEngine engine) {
        Activity act = activity();
        if (act == null) return;
        AdbEngine.Session selected = engine.selected();
        if (selected == null) {
            showBottomMessage(act.getString(R.string.adb_system_no_device));
            return;
        }
        showBottomMessage(act.getString(R.string.adb_loading));
        new Thread(() -> {
            AdbEngine.DeviceInfo info = engine.getDeviceInfo(selected.id);
            if (info == null) return;
            act.runOnUiThread(() -> {
                model.setText(act.getString(R.string.adb_system_model) + " " + info.model);
                brand.setText(act.getString(R.string.adb_system_brand) + " " + info.brand);
                androidVersion.setText(act.getString(R.string.adb_system_android_version) + " " + info.androidVersion);
                sdk.setText(act.getString(R.string.adb_system_sdk) + " " + info.sdk);
                build.setText(act.getString(R.string.adb_system_build) + " " + info.buildId);
                serial.setText(act.getString(R.string.adb_system_serial) + " " + info.serial);
                dpiText.setText(act.getString(R.string.adb_system_current_dpi, info.dpi));
                resolutionText.setText(act.getString(R.string.adb_system_current_resolution,
                        info.resolutionWidth, info.resolutionHeight));
                dpiInput.setText(String.valueOf(info.dpi));
                resW.setText(String.valueOf(info.resolutionWidth));
                resH.setText(String.valueOf(info.resolutionHeight));
                showBottomMessage("");
            });
        }).start();
    }

    @Override
    protected void onEngineUnbound() {
    }

    @Override
    public void onDestroy() {
        activityRef = null;
    }
}
