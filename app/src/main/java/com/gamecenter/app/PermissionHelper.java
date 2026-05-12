package com.gamecenter.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限请求辅助类。
 * 负责首次启动时的权限说明对话框、运行时权限请求、SharedPreferences 记录。
 */
public class PermissionHelper {

    private static final String PREFS_NAME = "permission_prefs";
    private static final String KEY_PERMISSION_SHOWN = "permission_dialog_shown";
    private static final String KEY_PERMISSION_GRANTED = "permission_granted";

    private static final int REQUEST_CODE_MULTI_PERMISSION = 1002;

    private final AppCompatActivity activity;

    public PermissionHelper(AppCompatActivity activity) {
        this.activity = activity;
    }

    /**
     * 判断是否是首次启动，需要弹出权限说明对话框。
     */
    public boolean isFirstLaunch() {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return !prefs.getBoolean(KEY_PERMISSION_SHOWN, false);
    }

    /**
     * 标记权限说明对话框已显示过。
     */
    public void markDialogShown() {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_PERMISSION_SHOWN, true).apply();
    }

    /**
     * 弹出权限说明对话框。
     */
    public void showPermissionDialog(ActivityResultLauncher<String[]> permissionLauncher) {
        if (!activity.isFinishing() && !activity.isDestroyed()) {
            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle(R.string.permission_dialog_title)
                    .setMessage(R.string.permission_dialog_message)
                    .setPositiveButton(R.string.permission_grant_all, (d, which) -> {
                        d.dismiss();
                        requestRuntimePermissions(permissionLauncher);
                    })
                    .setNegativeButton(R.string.permission_decline, (d, which) -> {
                        d.dismiss();
                        markDialogShown();
                        Toast.makeText(activity, R.string.permission_declined_toast, Toast.LENGTH_SHORT).show();
                    })
                    .setCancelable(false)
                    .create();
            dialog.show();
        }
    }

    /**
     * 请求所有运行时权限。
     * 注意：位置权限需要分别请求精确和粗略，相机和存储可以一起请求。
     */
    private void requestRuntimePermissions(ActivityResultLauncher<String[]> permissionLauncher) {
        List<String> permissionsToRequest = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
        }

        if (permissionsToRequest.isEmpty()) {
            markDialogShown();
            SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_PERMISSION_GRANTED, true).apply();
            Toast.makeText(activity, R.string.permission_granted_toast, Toast.LENGTH_SHORT).show();
            return;
        }

        String[] permissions = permissionsToRequest.toArray(new String[0]);
        permissionLauncher.launch(permissions);
    }

    /**
     * 处理权限请求结果回调。
     */
    public void onPermissionsResult(boolean[] grantResults) {
        markDialogShown();

        boolean allGranted = true;
        for (boolean granted : grantResults) {
            if (!granted) {
                allGranted = false;
                break;
            }
        }

        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_PERMISSION_GRANTED, allGranted).apply();

        if (allGranted) {
            Toast.makeText(activity, R.string.permission_granted_toast, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(activity, R.string.permission_declined_toast, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 检查并请求"安装未知应用"权限（用于APK安装）。
     * 此权限需要跳转到系统设置页面，无法直接弹窗请求。
     */
    public void checkAndRequestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(intent, REQUEST_CODE_MULTI_PERMISSION);
            }
        }
    }

    /**
     * 判断权限是否已全部授权（用于后续功能检查）。
     */
    public boolean hasPermissions() {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_PERMISSION_GRANTED, false);
    }
}
