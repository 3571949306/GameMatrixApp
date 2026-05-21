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
 * 【初学者理解】Android 系统为了保护用户隐私，要求应用在使用某些敏感功能前
 * 必须先获得用户的同意（就像进入某些区域需要先刷卡一样）。
 * PermissionHelper 就是帮你处理"刷卡"这件事的助手——
 * 它负责向用户解释为什么需要这些权限、弹出系统授权窗口、记录用户的授权结果。
 * <p>
 * 负责首次启动时的权限说明对话框展示、运行时权限的批量请求、
 * 以及权限授权状态的 SharedPreferences 持久化记录。
 * </p>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>使用 {@link ActivityResultLauncher} 替代已废弃的 {@code onRequestPermissionsResult()}，
 *       适配 AndroidX Activity Result API
 *       【初学者理解】Android 旧版用 onRequestPermissionsResult 回调来接收权限结果，
 *       新版用 ActivityResultLauncher，就像从"写信回复"升级到"在线聊天"，更方便也更安全</li>
 *   <li>存储权限相关标记使用独立的 SharedPreferences 文件（{@link #PREFS_NAME}），
 *       与应用设置隔离，避免键名冲突
 *       【初学者理解】SharedPreferences 就像一个"小本子"，用来记住一些简单的数据。
 *       用独立的小本子记权限相关的事，和设置相关的事分开记，不会搞混</li>
 *   <li>针对 Android 13（TIRAMISU）及以上版本使用 {@code READ_MEDIA_IMAGES}，
 *       以下版本使用 {@code READ/WRITE_EXTERNAL_STORAGE}，兼容不同系统版本
 *       【初学者理解】Android 13 改了存储权限的规则，需要用新的权限名，
 *       旧手机还是用旧的权限名。就像不同年份的法规不同，要按对应的来</li>
 *   <li>"安装未知应用"权限因系统限制无法通过运行时弹窗请求，
 *       需跳转系统设置页面由用户手动开启
 *       【初学者理解】安装未知应用是个危险操作，系统不让你弹窗请求，
 *       必须让用户亲自去设置页面手动打开，就像银行转账要去柜台办一样严格</li>
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
    // 【初学者理解】PermissionHelper 需要借助 Activity 来做 UI 操作（弹窗、跳转页面等），
    // 所以创建时必须传入一个 Activity，就像办事员需要知道在哪个办公室工作
    private final AppCompatActivity activity;

    /**
     * 构造函数。
     * <p>
     * 【初学者理解】创建一个权限助手，需要告诉它"你在哪个页面工作"（传入 Activity），
     * 这样它才能在正确的页面上弹出对话框和跳转设置页面。
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
     * 【初学者理解】检查"小本子"里有没有记录"权限对话框已显示过"。
     * 如果没有记录，说明是第一次安装后启动，需要向用户说明权限用途。
     * 就像新员工入职第一天需要签各种协议，之后就不需要再签了。
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
     * 【初学者理解】在"小本子"上记一笔"权限对话框已经显示过了"，
     * 这样下次启动应用就不会再重复弹出权限说明了。
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
     * 【初学者理解】这个对话框就像"权限使用说明书"——
     * 先告诉用户我们为什么需要这些权限，然后让用户选择同意或拒绝。
     * 对话框不能被点空白处关闭，确保用户必须做出选择。
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
                    // 用户点击"全部授权"：关闭对话框，发起运行时权限请求
                    .setPositiveButton(R.string.permission_grant_all, (d, which) -> {
                        d.dismiss();
                        requestRuntimePermissions(permissionLauncher);
                    })
                    // 用户点击"拒绝"：关闭对话框，标记已显示，提示用户
                    .setNegativeButton(R.string.permission_decline, (d, which) -> {
                        d.dismiss();
                        markDialogShown();
                        Toast.makeText(activity, R.string.permission_declined_toast, Toast.LENGTH_SHORT).show();
                    })
                    // 不允许点击对话框外部关闭，强制用户做出选择
                    .setCancelable(false)
                    .create();
            dialog.show();
        }
    }

    /**
     * 请求所有运行时权限。
     * <p>
     * 【初学者理解】这个方法会检查应用需要哪些权限还没获得，然后一次性向系统请求所有未获得的权限。
     * 就像你去办事大厅，把所有需要盖章的材料一起递上去，而不是一个一个递。
     * <p>
     * 按类别收集尚未授权的权限：
     * <ul>
     *   <li>位置权限：精确位置（{@code ACCESS_FINE_LOCATION}）和粗略位置（{@code ACCESS_COARSE_LOCATION}）
     *       【初学者理解】精确位置用 GPS 定位（精确到米），粗略位置用基站/WiFi 定位（精确到街区）</li>
     *   <li>相机权限：{@code CAMERA}</li>
     *   <li>存储权限：Android 13+ 使用 {@code READ_MEDIA_IMAGES}（细粒度媒体权限），
     *       旧版本使用 {@code READ_EXTERNAL_STORAGE} + {@code WRITE_EXTERNAL_STORAGE}
     *       【初学者理解】Android 13 把存储权限拆细了，只请求"读取图片"这一个，
     *       不再需要"读写整个存储"这么大的权限，更保护隐私</li>
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
            // Android 13 及以上：只需要读取图片的权限
            if (ContextCompat.checkSelfPermission(activity, android.Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(android.Manifest.permission.READ_MEDIA_IMAGES);
            }
        } else {
            // Android 12 及以下：需要读写外部存储的权限
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
        // 【初学者理解】系统权限请求接口只接受数组格式，所以要把 List 转成数组
        String[] permissions = permissionsToRequest.toArray(new String[0]);
        permissionLauncher.launch(permissions);
    }

    /**
     * 处理权限请求结果回调。
     * <p>
     * 【初学者理解】当用户在系统权限弹窗中做出选择后，这个方法会被调用。
     * 它会检查用户是不是把所有权限都同意了，然后把结果记到"小本子"上。
     * 无论用户同意还是拒绝，都会标记对话框已显示过，避免下次启动重复弹出。
     * <p>
     * 遍历授权结果数组，判断是否全部授权，并将结果持久化到 SharedPreferences。
     * 无论结果如何，都标记对话框已显示，避免下次启动重复弹出。
     * </p>
     *
     * @param grantResults 各权限的授权结果数组，{@code true} 表示已授权，
     *                     与请求时的权限顺序一一对应
     */
    public void onPermissionsResult(boolean[] grantResults) {
        // 先标记对话框已显示
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
        // 【初学者理解】把授权结果记到"小本子"上，其他功能可以随时查看
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
     * 【初学者理解】这个权限比较特殊——它不能像其他权限一样弹窗请求，
     * 必须让用户手动跳转到系统设置页面去开启。就像某些特殊权限
     * 需要你亲自去办公室签字，不能在线办理。
     * <p>
     * 此权限从 Android 8.0（Oreo）开始引入，无法通过运行时弹窗请求，
     * 必须跳转到系统设置页面由用户手动开启。
     * 通过 {@code startActivityForResult} 跳转，以便在用户返回后获取结果。
     * </p>
     */
    public void checkAndRequestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 检查是否已有安装未知应用的权限
            if (!activity.getPackageManager().canRequestPackageInstalls()) {
                // 构造指向本应用的"安装未知应用"设置页面 Intent
                // 【初学者理解】Intent 就像一个"导航指令"，告诉系统要跳转到哪个页面
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivityForResult(intent, REQUEST_CODE_MULTI_PERMISSION);
            }
        }
    }

    /**
     * 判断权限是否已全部授权（用于后续功能检查）。
     * <p>
     * 【初学者理解】查看"小本子"上的记录，看上次请求权限时用户是否全部同意了。
     * 但要注意，这个记录可能不是最新的——如果用户在系统设置中手动撤销了权限，
     * "小本子"上可能还记着"已授权"。所以在关键功能入口处，最好实时检查。
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
