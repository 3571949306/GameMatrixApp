package com.gamecenter.app.adb.ui;

import android.app.Activity;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.gamecenter.app.adb.AdbEngine;
import com.gamecenter.app.adb.AdbSessionService;
import com.gamecenter.app.adb.R;

import java.io.File;

/**
 * Fastboot section: device info, command execution, flash/boot/erase operations.
 * High-risk operations require explicit partition name confirmation.
 */
public final class FastbootSection extends BaseSection {

    private static final int REQUEST_PICK_IMAGE = 2001;

    private TextView deviceInfo, resultView;
    private EditText commandInput, partitionInput;
    private String selectedImagePath;

    @Override
    public View createView(Activity activity) {
        activityRef = new java.lang.ref.WeakReference<>(activity);
        View view = LayoutInflater.from(activity).inflate(R.layout.fragment_fastboot, null);

        deviceInfo = view.findViewById(R.id.adb_fb_device_info);
        resultView = view.findViewById(R.id.adb_fb_result);
        commandInput = view.findViewById(R.id.adb_fb_command_input);
        partitionInput = view.findViewById(R.id.adb_fb_flash_partition);

        setupButtons(view);
        return view;
    }

    private void setupButtons(View root) {
        TextView execute = root.findViewById(R.id.adb_fb_execute);
        if (execute != null) execute.setOnClickListener(v -> executeCommand());

        TextView flashImage = root.findViewById(R.id.adb_fb_flash_image);
        if (flashImage != null) flashImage.setOnClickListener(v -> pickImageFile());

        TextView flash = root.findViewById(R.id.adb_fb_flash);
        if (flash != null) flash.setOnClickListener(v -> flashPartition());

        TextView bootTemp = root.findViewById(R.id.adb_fb_boot_temp);
        if (bootTemp != null) bootTemp.setOnClickListener(v -> bootTempImage());

        TextView erase = root.findViewById(R.id.adb_fb_erase);
        if (erase != null) erase.setOnClickListener(v -> erasePartition());

        TextView enter = root.findViewById(R.id.adb_fb_enter_fastboot);
        if (enter != null) enter.setOnClickListener(v -> enterFastboot());

        TextView exit = root.findViewById(R.id.adb_fb_exit_fastboot);
        if (exit != null) exit.setOnClickListener(v -> exitFastboot());
    }

    private void executeCommand() {
        Activity act = activity();
        if (act == null || engine() == null) return;
        String cmd = commandInput.getText().toString().trim();
        if (cmd.isEmpty()) return;

        resultView.setText(act.getString(R.string.adb_fastboot_running));
        new Thread(() -> {
            String output = engine().fastbootCommand(cmd);
            act.runOnUiThread(() -> {
                resultView.setText(output);
                appendResult("\n$ " + cmd);
            });
        }).start();
    }

    private void appendResult(String text) {
        if (resultView == null) return;
        CharSequence current = resultView.getText();
        String newText = (current.length() > 0 ? current.toString() + "\n" : "") + text;
        resultView.setText(newText);
    }

    private void pickImageFile() {
        Activity act = activity();
        if (act == null) return;
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        act.startActivityForResult(Intent.createChooser(intent, act.getString(R.string.adb_fastboot_flash_image)), REQUEST_PICK_IMAGE);
    }

    private void flashPartition() {
        Activity act = activity();
        if (act == null || engine() == null) return;
        String partition = partitionInput.getText().toString().trim();
        if (partition.isEmpty()) {
            showBottomMessage("请输入分区名");
            return;
        }
        if (selectedImagePath == null) {
            showBottomMessage("请先选择镜像文件");
            return;
        }

        new android.app.AlertDialog.Builder(act)
                .setMessage(act.getString(R.string.adb_fastboot_flash_warning))
                .setTitle("刷写分区：" + partition)
                .setPositiveButton(R.string.adb_ok, (d, w) -> {
                    resultView.setText(act.getString(R.string.adb_fastboot_running));
                    new Thread(() -> {
                        engine().flashPartition(partition, selectedImagePath);
                        act.runOnUiThread(() -> resultView.setText(R.string.adb_fastboot_done));
                    }).start();
                })
                .setNegativeButton(R.string.adb_cancel, null)
                .show();
    }

    private void bootTempImage() {
        Activity act = activity();
        if (act == null || engine() == null) return;
        if (selectedImagePath == null) {
            showBottomMessage("请先选择镜像文件");
            return;
        }
        resultView.setText(act.getString(R.string.adb_fastboot_running));
        new Thread(() -> {
            engine().bootTempImage(selectedImagePath);
            act.runOnUiThread(() -> resultView.setText(R.string.adb_fastboot_done));
        }).start();
    }

    private void erasePartition() {
        Activity act = activity();
        if (act == null || engine() == null) return;
        String partition = partitionInput.getText().toString().trim();
        if (partition.isEmpty()) {
            showBottomMessage("请输入分区名");
            return;
        }
        new android.app.AlertDialog.Builder(act)
                .setMessage(act.getString(R.string.adb_fastboot_erase_warning))
                .setTitle("擦除分区：" + partition)
                .setPositiveButton(R.string.adb_ok, (d, w) -> {
                    resultView.setText(act.getString(R.string.adb_fastboot_running));
                    new Thread(() -> {
                        engine().erasePartition(partition);
                        act.runOnUiThread(() -> resultView.setText(R.string.adb_fastboot_done));
                    }).start();
                })
                .setNegativeButton(R.string.adb_cancel, null)
                .show();
    }

    private void enterFastboot() {
        Activity act = activity();
        if (act == null || engine() == null) return;
        AdbEngine.Session selected = engine().selected();
        if (selected == null) {
            showBottomMessage(act.getString(R.string.adb_fastboot_no_device));
            return;
        }
        engine().reboot(selected.id, "bootloader");
        showBottomMessage("设备正在重启到 Fastboot…");
    }

    private void exitFastboot() {
        Activity act = activity();
        if (act == null || engine() == null) return;
        resultView.setText(act.getString(R.string.adb_fastboot_running));
        new Thread(() -> {
            String output = engine().fastbootCommand("reboot");
            act.runOnUiThread(() -> resultView.setText(output));
        }).start();
    }

    @Override
    protected void onEngineBound(AdbEngine engine) {
        Activity act = activity();
        if (act == null) return;
        showBottomMessage(act.getString(R.string.adb_fastboot_info));
        new Thread(() -> {
            String info = engine.getFastbootInfo();
            act.runOnUiThread(() -> deviceInfo.setText(info));
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
