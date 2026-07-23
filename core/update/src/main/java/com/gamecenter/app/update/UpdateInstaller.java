package com.gamecenter.app.update;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.gamecenter.app.update.BuildConfig;
import com.gamecenter.app.update.R;

import java.io.File;
import java.text.MessageFormat;

/**
 * APK 安装器，负责触发 APK 安装流程和管理安装权限。
 * <p>
 * 处理 Android 不同版本的安装兼容性问题：
 * <ul>
 *   <li>Android 7.0（API 24）及以上：使用 FileProvider 提供 APK 文件 URI</li>
 *   <li>Android 7.0 以下：直接使用 file:// URI</li>
 *   <li>Android 8.0（API 26）及以上：需要请求未知来源应用安装权限</li>
 * </ul>
 * </p>
 */
public class UpdateInstaller {

    private static final String TAG = "UpdateInstaller";
    private final UpdateDownloader downloader;

    /**
     * 构造函数。
     *
     * @param downloader 下载器实例，用于获取下载目录和清理旧 APK
     */
    UpdateInstaller(UpdateDownloader downloader) {
        this.downloader = downloader;
    }

    /**
     * 预检测 APK 文件是否有效
     * <p>
     * 2026-07-23 修复：在原有 packageName 校验基础上增加降级检测。
     * 若 APK 内部 versionCode < 当前已安装 versionCode，直接拒绝安装，
     * 避免触发系统的 INSTALL_FAILED_VERSION_DOWNGRADE 错误（用户表现为"解析错误"）。
     * 这一防护兜底所有上层漏检场景，例如用户手动点击历史下载的旧 APK 文件。
     * </p>
     *
     * @param context 上下文
     * @param apkFile 要检测的 APK 文件
     * @return true 表示 APK 有效
     */
    public boolean verifyApk(Context context, File apkFile) {
        if (!apkFile.exists()) {
            Log.e(TAG, "APK file not found: " + apkFile.getPath());
            return false;
        }
        if (apkFile.length() == 0) {
            Log.e(TAG, "APK file is empty: " + apkFile.getPath());
            return false;
        }
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo packageInfo = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
            if (packageInfo == null) {
                Log.e(TAG, "Failed to parse APK: " + apkFile.getPath());
                return false;
            }
            if (!context.getPackageName().equals(packageInfo.packageName)) {
                Log.e(TAG, "Downloaded APK package does not match the host app: " + packageInfo.packageName);
                return false;
            }
            // 2026-07-23 修复：降级检测
            // 若 APK versionCode 小于当前已安装 versionCode，系统会拒绝安装并报
            // INSTALL_FAILED_VERSION_DOWNGRADE，MIUI 等定制系统对外显示为"解析错误"。
            // 这里提前拦截，给出准确的错误提示。
            if (BuildConfig.ENABLE_APK_VERSION_CHECK) {
                int apkVersionCode;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    apkVersionCode = (int) packageInfo.getLongVersionCode();
                } else {
                    apkVersionCode = packageInfo.versionCode;
                }
                try {
                    PackageInfo installed = pm.getPackageInfo(context.getPackageName(), 0);
                    int installedVersionCode;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        installedVersionCode = (int) installed.getLongVersionCode();
                    } else {
                        installedVersionCode = installed.versionCode;
                    }
                    if (apkVersionCode < installedVersionCode) {
                        Log.e(TAG, "APK versionCode=" + apkVersionCode
                                + " is lower than installed versionCode=" + installedVersionCode
                                + " (downgrade not allowed)");
                        return false;
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    // 当前应用未安装（例如首次安装场景），跳过降级检测
                    Log.d(TAG, "Host app not installed, skip downgrade check");
                }
            }
            Log.d(TAG, "APK verified successfully: " + packageInfo.packageName);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "APK verification failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * 安装指定的 APK 文件。
     * <p>
     * 通过系统 Intent 触发 APK 安装界面。
     * Android 7.0+ 使用 FileProvider 避免 FileUriExposedException；
     * Android 7.0 以下直接使用 file:// URI。
     * </p>
     *
     * @param context 上下文
     * @param apkFile 要安装的 APK 文件
     * @return true 表示成功启动安装 Intent，false 表示安装包不存在或无法打开安装程序
     */
    public boolean installApk(Context context, File apkFile) {
        if (!apkFile.exists()) {
            Toast.makeText(context, context.getString(R.string.update_apk_lost), Toast.LENGTH_SHORT).show();
            return false;
        }
        // 预检测 APK 有效性（包含降级检测）
        if (!verifyApk(context, apkFile)) {
            Toast.makeText(context, context.getString(R.string.update_apk_invalid_or_downgrade),
                    Toast.LENGTH_LONG).show();
            return false;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Android 7.0+：使用 FileProvider 获取 content:// URI，避免 FileUriExposedException
            Uri uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".update.fileprovider", apkFile);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            // Android 7.0 以下：直接使用 file:// URI
            intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            Toast.makeText(context, "无法打开安装程序: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * 检查当前应用是否有权限请求安装未知来源的 APK。
     * Android 8.0（API 26）及以上需要用户显式授权安装未知来源应用。
     * Android 8.0 以下默认有权限。
     *
     * @param context 上下文
     * @return true 表示已有安装权限
     */
    public boolean canRequestInstall(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return context.getPackageManager().canRequestPackageInstalls();
        }
        return true;
    }

    /**
     * 请求未知来源应用安装权限。
     * 跳转到系统"安装未知应用"设置页面，用户授权后通过 onActivityResult 返回结果。
     * 仅在 Android 8.0（API 26）及以上有效。
     *
     * @param activity    发起请求的 Activity，用于接收权限授权结果
     * @param requestCode 请求码，用于在 onActivityResult 中识别此请求
     */
    public void requestInstallPermission(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri uri = Uri.parse(MessageFormat.format("package:{0}", activity.getPackageName()));
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, uri);
            activity.startActivityForResult(intent, requestCode);
        }
    }

    /**
     * 打开下载目录，让用户可以通过文件管理器查看已下载的 APK 文件。
     * <p>
     * APK 实际保存到公共 Download 目录 (/storage/emulated/0/Download/),
     * 文件名格式为 GameMatrix_v{版本号}.apk。
     * 打开前会先清理旧 APK 文件，仅保留最新版本。
     * </p>
     *
     * @param context 上下文
     * @return true 表示成功打开目录或显示了路径信息
     */
    public boolean openDownloadDirectory(Context context) {
        // 打开目录前清理旧 APK
        downloader.cleanOldApks(context);
        File downloadDir = downloader.getPublicDownloadDir(context);

        // 确保目录存在
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }

        // 检查是否有 APK 文件
        File[] apkFiles = downloadDir.listFiles((dir, name) ->
                name.startsWith("GameMatrix_v") && name.endsWith(".apk"));

        if (apkFiles == null || apkFiles.length == 0) {
            // 没有 APK 文件，提示用户
            String message = "当前没有已下载的更新包\n\nAPK 下载位置：\n" + downloadDir.getAbsolutePath();
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            return false;
        }

        // 尝试打开 Download 目录 - 使用更可靠的方法
        boolean opened = false;

        // 方式一：使用 FileProvider 打开下载目录（Android 7.0+ 避免 FileUriExposedException）
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri uri;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0+：使用 FileProvider 获取 content:// URI，避免 FileUriExposedException
                uri = FileProvider.getUriForFile(context,
                        context.getPackageName() + ".update.fileprovider", downloadDir);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                // Android 7.0 以下：直接使用 file:// URI
                uri = Uri.fromFile(downloadDir);
            }
            intent.setDataAndType(uri, "*/*");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
                opened = true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to open download directory with file URI: " + e.getMessage());
        }

        // 方式二：如果方式一失败，尝试使用 Documents UI（仅在Android 4.4+）
        if (!opened && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                // 尝试使用存储访问框架打开下载目录
                String downloadPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath();
                // 注意：这是一个后备方案，实际打开可能需要用户导航
                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                        Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADownload"));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                if (intent.resolveActivity(context.getPackageManager()) != null) {
                    context.startActivity(intent);
                    opened = true;
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to open download directory with Documents UI: " + e.getMessage());
            }
        }

        // 方式三：显示目录路径（如果以上方法都失败）
        if (!opened) {
            String path = downloadDir.getAbsolutePath();
            Toast.makeText(context, "APK 下载目录: " + path, Toast.LENGTH_LONG).show();

            // 如果是在 Activity 中调用，显示 Snackbar
            try {
                if (context instanceof android.app.Activity) {
                    com.google.android.material.snackbar.Snackbar.make(
                            ((android.app.Activity) context).findViewById(android.R.id.content),
                            "APK 保存在: " + path + "\n请使用文件管理器查看",
                            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).show();
                }
            } catch (Exception ignored) {
                // 如果无法显示 Snackbar，只保留 Toast
            }
        }

        return true;
    }
}
