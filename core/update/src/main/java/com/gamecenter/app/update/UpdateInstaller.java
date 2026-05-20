package com.gamecenter.app.update;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.FileProvider;

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
            Toast.makeText(context, "安装包不存在", Toast.LENGTH_SHORT).show();
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
     * 打开策略：
     * <ol>
     *   <li>首先尝试使用 FileProvider + ACTION_VIEW 打开目录</li>
     *   <li>若失败，尝试使用 ACTION_OPEN_DOCUMENT_TREE 让用户手动选择目录</li>
     *   <li>若仍失败，显示下载目录的绝对路径供用户手动查找</li>
     * </ol>
     * 打开前会先清理旧 APK 文件，仅保留最新版本。
     * </p>
     *
     * @param context 上下文
     * @return true 表示成功打开目录或显示了路径信息，false 表示所有方式均失败
     */
    public boolean openDownloadDirectory(Context context) {
        // 打开目录前清理旧 APK
        downloader.cleanOldApks(context);
        File downloadDir = downloader.getDownloadDir(context);
        if (!downloadDir.exists() && !downloadDir.mkdirs()) {
            Toast.makeText(context, "无法创建下载目录", Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0+：使用 FileProvider 提供目录 URI
                Uri uri = FileProvider.getUriForFile(context,
                        context.getPackageName() + ".update.fileprovider", downloadDir);
                intent.setDataAndType(uri, "resource/folder");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                intent.setDataAndType(Uri.fromFile(downloadDir), "resource/folder");
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Exception e) {
            // FileProvider 方式失败，尝试使用系统文件选择器
            try {
                Intent fallback = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
                Toast.makeText(context, "请选择应用目录下的 Download/update 文件夹", Toast.LENGTH_LONG).show();
                return true;
            } catch (Exception ignored) {
                // 所有方式均失败，显示路径让用户手动查找
                Toast.makeText(context, "下载目录: " + downloadDir.getAbsolutePath(), Toast.LENGTH_LONG).show();
                return false;
            }
        }
    }
}
