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
 * <p>
 * 负责首次启动时的权限说明对话框展示、运行时权限的批量请求、
 * 以及权限授权状态的 SharedPreferences 持久化记录。
 * </p>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>使用 {@link ActivityResultLauncher} 替代已废弃的 {@code onRequestPermissionsResult()}，
 *       适配 AndroidX Activity Result API</li>
 *   <li>存储权限相关标记使用独立的 SharedPreferences 文件（{@link #PREFS_NAME}），
 *       与应用设置隔离，避免键名冲突</li>
 *   <li>针对 Android 13（TIRAMISU）及以上版本使用 {@code READ_MEDIA_IMAGES}，
 *       以下版本使用 {@code READ/WRITE_EXTERNAL_STORAGE}，兼容不同系统版本</li>
 *   <li>"安装未知应用"权限因系统限制无法通过运行时弹窗请求，
 *       需跳转系统设置页面由用户手动开启</li>
 * </ul>
 * </p>
 */
public class PermissionHelper {

    /** 权限偏好文件名，独立于应用设置文件 */
    private static final String PREFS_NAME = "permission_prefs";
    /** 标记权限说明对话框是否已显示过的键名 */
    private static final String KEY_PERMISSION_SHOWN = "permission_dialog_shown";
    /** 标记权限是否已全部授权的键名 */
    private static final String KEY_PERMISSION_GRANTED = "permission_granted";

    /** "安装未知应用"权限的系统设置页面请求码 */
    private static final int REQUEST_CODE_MULTI_PERMISSION = 1002;

    /** 关联的 Activity 实例，用于弹窗、启动设置页面等 UI 操作 */
    private final AppCompatActivity activity;

    /**
     * 构造函数。
     *
     * @param activity 关联的 Activity，必须是 {@link AppCompatActivity} 实例，
     *                 以支持 Activity Result API 和 AlertDialog
     */
    public PermissionHelper(AppCompatActivity activity) {
        this.activity = activity;
    }

    /**
     * 判断是否是首次启动，需要弹出权限说明对话框。
     * <p>
     * 通过检查 SharedPreferences 中是否已记录对话框显示标记来判断。
     * 首次安装后该标记不存在，返回 {@code true}。
     * </p>
     *
     * @return {@code true} 表示首次启动，需要展示权限说明对话框
     */
    public boolean isFirstLaunch() {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return !prefs.getBoolean(KEY_PERMISSION_SHOWN, false);
    }

    /**
     * 标记权限说明对话框已显示过。
     * <p>
     * 无论用户选择"授权"还是"拒绝"，对话框关闭后都应调用此方法，
     * 避免每次启动都重复弹出。
     * </p>
     */
    public void markDialogShown() {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_PERMISSION_SHOWN, true).apply();
    }

    /**
     * 弹出权限说明对话框，引导用户授权或拒绝。
     * <p>
     * 对话框不可取消（{@code setCancelable(false)}），确保用户必须做出选择：
     * <ul>
     *   <li>点击"全部授权"：关闭对话框并触发运行时权限请求</li>
     *   <li>点击"拒绝"：关闭对话框、标记已显示、提示用户已拒绝</li>
     * </ul>
     * 在 Activity 即将销毁或已销毁时跳过弹窗，避免 {@link WindowManager.BadTokenException}。
     * </p>
     *
     * @param permissionLauncher 由 Activity 注册的权限请求启动器，
     *                           用于发起批量运行时权限请求
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
     * <p>
     * 按类别收集尚未授权的权限：
     * <ul>
     *   <li>位置权限：精确位置（{@code ACCESS_FINE_LOCATION}）和粗略位置（{@code ACCESS_COARSE_LOCATION}）</li>
     *   <li>相机权限：{@code CAMERA}</li>
     *   <li>存储权限：Android 13+ 使用 {@code READ_MEDIA_IMAGES}（细粒度媒体权限），
     *       旧版本使用 {@code READ_EXTERNAL_STORAGE} + {@code WRITE_EXTERNAL_STORAGE}</li>
     * </ul>
     * 若所有权限已授权，直接标记完成并提示用户；否则通过 launcher 批量请求。
     * </p>
     *
     * @param permissionLauncher 权限请求启动器
     */
    private void requestRuntimePermissions(ActivityResultLauncher<String[]> permissionLauncher) {
        List<String> permissionsToRequest = new ArrayList<>();

        // 收集未授权的位置权限
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }

        // 收集未授权的相机权限
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA);
        }

        // 存储权限：Android 13+ 使用细粒度媒体权限，旧版本使用传统存储权限
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

        // 所有权限已授权，无需请求，直接标记完成
        if (permissionsToRequest.isEmpty()) {
            markDialogShown();
            SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_PERMISSION_GRANTED, true).apply();
            Toast.makeText(activity, R.string.permission_granted_toast, Toast.LENGTH_SHORT).show();
            return;
        }

        // 将列表转为数组，通过 Activity Result API 批量请求
        String[] permissions = permissionsToRequest.toArray(new String[0]);
        permissionLauncher.launch(permissions);
    }

    /**
     * 处理权限请求结果回调。
     * <p>
     * 遍历授权结果数组，判断是否全部授权，并将结果持久化到 SharedPreferences。
     * 无论结果如何，都标记对话框已显示，避免下次启动重复弹出。
     * </p>
     *
     * @param grantResults 各权限的授权结果数组，{@code true} 表示已授权，
     *                     与请求时的权限顺序一一对应
     */
    public void onPermissionsResult(boolean[] grantResults) {
        markDialogShown();

        // 遍历检查是否所有权限均已授权
        boolean allGranted = true;
        for (boolean granted : grantResults) {
            if (!granted) {
                allGranted = false;
                break;
            }
        }

        // 持久化授权状态，供后续功能模块检查
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_PERMISSION_GRANTED, allGranted).apply();

        if (allGranted) {
            Toast.makeText(activity, R.string.permission_granted_toast, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(activity, R.string.permission_declined_toast, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 检查并请求"安装未知应用"权限（用于 APK 安装）。
     * <p>
     * 此权限从 Android 8.0（Oreo）开始引入，无法通过运行时弹窗请求，
     * 必须跳转到系统设置页面由用户手动开启。
     * 通过 {@code startActivityForResult} 跳转，以便在用户返回后获取结果。
     * </p>
     */
    public void checkAndRequestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                // 构造指向本应用的"安装未知应用"设置页面 Intent
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(intent, REQUEST_CODE_MULTI_PERMISSION);
            }
        }
    }

    /**
     * 判断权限是否已全部授权（用于后续功能检查）。
     * <p>
     * 注意：此方法读取的是 SharedPreferences 中缓存的授权状态，
     * 而非实时检查系统权限。如果用户在系统设置中手动撤销了权限，
     * 此处仍可能返回 {@code true}，需在关键功能入口处做实时校验。
     * </p>
     *
     * @return {@code true} 表示上次请求时所有权限均已授权
     */
    public boolean hasPermissions() {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_PERMISSION_GRANTED, false);
    }
}
