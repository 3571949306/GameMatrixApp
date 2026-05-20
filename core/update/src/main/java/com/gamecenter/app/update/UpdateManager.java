package com.gamecenter.app.update;

import android.app.Activity;
import android.content.Context;

import java.io.File;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * 应用更新管理器（门面模式）。
 * <p>
 * 作为整个更新子系统的统一入口，协调 {@link UpdateChecker}（检查更新）、
 * {@link UpdateDownloader}（下载APK）、{@link UpdateInstaller}（安装APK）和
 * {@link UpdateNotificationHelper}（通知管理）四个组件的交互。
 * </p>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>采用单例模式，确保全局只有一个更新管理器实例，避免重复检查或下载冲突</li>
 *   <li>所有回调均通过主线程 Handler 切换到主线程执行，保证 UI 操作安全</li>
 *   <li>对外暴露的回调接口（{@link UpdateCheckCallback}、{@link DownloadCallback}）
 *       已封装了线程切换逻辑，调用方无需关心线程问题</li>
 * </ul>
 * </p>
 */
public class UpdateManager {

    private static final String TAG = "UpdateManager";
    private static UpdateManager instance;

    private final UpdateChecker checker;
    private final UpdateDownloader downloader;
    private final UpdateInstaller installer;
    private final UpdateNotificationHelper notificationHelper;

    /**
     * 构造函数，初始化更新子系统的四个核心组件。
     * 组件之间存在依赖关系：Downloader 依赖 NotificationHelper，Installer 依赖 Downloader。
     *
     * <p>标注 {@code @Inject} 使 Hilt 可直接通过构造函数注入创建实例，
     * 配合 {@code @Singleton} 注解确保全局唯一。</p>
     */
    @Inject
    UpdateManager() {
        notificationHelper = new UpdateNotificationHelper();
        checker = new UpdateChecker();
        downloader = new UpdateDownloader(notificationHelper);
        installer = new UpdateInstaller(downloader);
    }

    /**
     * 获取 UpdateManager 单例实例。
     * 使用 synchronized 保证线程安全，确保多线程环境下只创建一个实例。
     *
     * @return UpdateManager 唯一实例
     * @deprecated 推荐通过 Hilt 依赖注入获取实例，避免手动管理单例
     */
    @Deprecated
    public static synchronized UpdateManager getInstance() {
        if (instance == null) {
            instance = new UpdateManager();
        }
        return instance;
    }

    /**
     * 获取当前配置的更新服务器基础 URL。
     *
     * @param context 上下文，用于读取 SharedPreferences 中存储的 URL 配置
     * @return 当前配置的基础 URL
     */
    public String getBaseUrl(Context context) {
        return checker.getBaseUrl(context);
    }

    /**
     * 设置更新服务器基础 URL，同时将该 URL 注册为 SSL 信任主机。
     *
     * @param context 上下文，用于写入 SharedPreferences
     * @param baseUrl 新的基础 URL
     */
    public void setBaseUrl(Context context, String baseUrl) {
        checker.setBaseUrl(context, baseUrl);
    }

    /**
     * 取消正在进行的更新检查和下载操作。
     * 通过设置取消标志位，让子线程中的检查和下载任务自行终止。
     */
    public void cancel() {
        checker.cancel();
        downloader.cancel();
    }

    /**
     * 检查应用更新。回调方法会在主线程中执行。
     *
     * @param context  上下文
     * @param callback 更新检查结果回调，通过 {@link #wrapCheckCallback} 包装后保证主线程执行
     */
    public void checkUpdate(Context context, final UpdateCheckCallback callback) {
        checker.checkUpdate(context, wrapCheckCallback(callback));
    }

    /**
     * 下载指定版本的 APK 文件。回调方法会在主线程中执行。
     *
     * @param context  上下文
     * @param info     更新信息，包含下载 URL、MD5 校验值等
     * @param callback 下载进度和结果回调，通过 {@link #wrapDownloadCallback} 包装后保证主线程执行
     */
    public void downloadApk(final Context context, final UpdateInfo info, final DownloadCallback callback) {
        downloader.downloadApk(context, info, wrapDownloadCallback(callback));
    }

    /**
     * 安装指定的 APK 文件。
     *
     * @param context 上下文
     * @param apkFile 要安装的 APK 文件
     * @return true 表示成功启动安装 Intent，false 表示安装包不存在或无法打开安装程序
     */
    public boolean installApk(Context context, File apkFile) {
        return installer.installApk(context, apkFile);
    }

    /**
     * 检查当前应用是否有权限请求安装未知来源的 APK。
     * Android 8.0（API 26）及以上需要用户显式授权。
     *
     * @param context 上下文
     * @return true 表示已有安装权限
     */
    public boolean canRequestInstall(Context context) {
        return installer.canRequestInstall(context);
    }

    /**
     * 请求未知来源应用安装权限。
     * 会跳转到系统设置页面，用户授权后通过 onActivityResult 返回结果。
     *
     * @param activity    发起请求的 Activity，用于接收权限授权结果
     * @param requestCode 请求码，用于在 onActivityResult 中识别此请求
     */
    public void requestInstallPermission(Activity activity, int requestCode) {
        installer.requestInstallPermission(activity, requestCode);
    }

    /**
     * 打开下载目录，让用户可以通过文件管理器查看已下载的 APK 文件。
     *
     * @param context 上下文
     * @return true 表示成功打开目录，false 表示无法打开
     */
    public boolean openDownloadDirectory(Context context) {
        return installer.openDownloadDirectory(context);
    }

    /**
     * 清理旧的 APK 文件，仅保留版本号最高的一个。
     *
     * @param context 上下文
     * @return 被删除的文件数量
     */
    public int cleanOldApks(Context context) {
        return downloader.cleanOldApks(context);
    }

    /**
     * 获取 APK 下载目录。
     *
     * @param context 上下文
     * @return 下载目录的 File 对象
     */
    public File getDownloadDir(Context context) {
        return downloader.getDownloadDir(context);
    }

    /**
     * 包装更新检查回调，确保所有回调方法在主线程执行。
     * 更新检查在子线程中进行，但 UI 更新必须在主线程，
     * 因此通过主线程 Handler 将回调投递到主线程。
     *
     * @param callback 原始回调，可能为 null
     * @return 包装后的回调，保证主线程执行；若原始回调为 null 则返回 null
     */
    private UpdateCheckCallback wrapCheckCallback(final UpdateCheckCallback callback) {
        if (callback == null) return null;
        return new UpdateCheckCallback() {
            @Override
            public void onResult(UpdateInfo info) {
                postMain(new Runnable() {
                    @Override
                    public void run() {
                        callback.onResult(info);
                    }
                });
            }

            @Override
            public void onError(String message) {
                postMain(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError(message);
                    }
                });
            }

            @Override
            public void onCancelled() {
                postMain(new Runnable() {
                    @Override
                    public void run() {
                        callback.onCancelled();
                    }
                });
            }
        };
    }

    /**
     * 包装下载回调，确保所有回调方法在主线程执行。
     * 下载过程在子线程中进行，进度更新和完成通知需切换到主线程。
     *
     * @param callback 原始回调，可能为 null
     * @return 包装后的回调，保证主线程执行；若原始回调为 null 则返回 null
     */
    private DownloadCallback wrapDownloadCallback(final DownloadCallback callback) {
        if (callback == null) return null;
        return new DownloadCallback() {
            @Override
            public void onProgress(long downloaded, long total) {
                postMain(new Runnable() {
                    @Override
                    public void run() {
                        callback.onProgress(downloaded, total);
                    }
                });
            }

            @Override
            public void onVerifying() {
                postMain(new Runnable() {
                    @Override
                    public void run() {
                        callback.onVerifying();
                    }
                });
            }

            @Override
            public void onComplete(File apkFile) {
                postMain(new Runnable() {
                    @Override
                    public void run() {
                        callback.onComplete(apkFile);
                    }
                });
            }

            @Override
            public void onError(String message) {
                postMain(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError(message);
                    }
                });
            }

            @Override
            public void onCancelled() {
                postMain(new Runnable() {
                    @Override
                    public void run() {
                        callback.onCancelled();
                    }
                });
            }
        };
    }

    /**
     * 安全地触发更新检查回调，根据参数自动判断调用 onResult/onError/onCancelled。
     * 优先级：取消 > 错误 > 成功。所有回调均通过主线程 Handler 执行。
     *
     * @param callback 回调对象，若为 null 则不执行任何操作
     * @param info     更新信息（成功时使用）
     * @param error    错误消息（非 null 表示发生错误）
     * @param cancel   取消标记（非 null 表示已取消，优先级最高）
     */
    private void safeCallback(final UpdateCheckCallback callback, final UpdateInfo info,
                              final String error, final String cancel) {
        if (callback == null) return;
        postMain(new Runnable() {
            @Override
            public void run() {
                if (cancel != null) {
                    callback.onCancelled();
                } else if (error != null) {
                    callback.onError(error);
                } else {
                    callback.onResult(info);
                }
            }
        });
    }

    /**
     * 安全地触发下载进度回调，根据状态码分发到不同的回调方法。
     * 状态码含义：-3=已取消，-2=下载失败，-1=正在校验，其他=正常进度。
     *
     * @param callback   回调对象，若为 null 则不执行任何操作
     * @param status     状态码（-3=取消, -2=错误, -1=校验中, 其他=进度）
     * @param downloaded 已下载字节数
     * @param total      总字节数
     */
    private void safeProgress(final DownloadCallback callback,
                               final int status, final long downloaded, final long total) {
        if (callback == null) return;
        postMain(new Runnable() {
            @Override
            public void run() {
                switch (status) {
                    case -3:
                        callback.onCancelled();
                        break;
                    case -2:
                        callback.onError("下载失败");
                        break;
                    case -1:
                        callback.onVerifying();
                        break;
                    default:
                        callback.onProgress(downloaded, total);
                        break;
                }
            }
        });
    }

    /**
     * 安全地触发下载完成/失败回调。优先判断错误，其次判断成功。
     *
     * @param callback 回调对象，若为 null 则不执行任何操作
     * @param apkFile  下载完成的 APK 文件（成功时使用）
     * @param error    错误消息（非 null 表示发生错误，优先级高于 apkFile）
     */
    private void safeCallback2(final DownloadCallback callback,
                                final File apkFile, final String error) {
        if (callback == null) return;
        postMain(new Runnable() {
            @Override
            public void run() {
                if (error != null) {
                    callback.onError(error);
                } else if (apkFile != null) {
                    callback.onComplete(apkFile);
                }
            }
        });
    }

    /**
     * 通知回调 APK 已准备就绪，通过主线程投递 onComplete 回调。
     *
     * @param callback 回调对象
     * @param apkFile  已就绪的 APK 文件
     */
    private void callbackApkReady(DownloadCallback callback, File apkFile) {
        if (callback == null) return;
        final File fApk = apkFile;
        postMain(new Runnable() {
            @Override
            public void run() {
                callback.onComplete(fApk);
            }
        });
    }

    /**
     * 去除 URL 末尾的所有斜杠。
     * 用于拼接 URL 路径时避免出现双斜杠问题。
     *
     * @param value 原始 URL 字符串
     * @return 去除末尾斜杠后的字符串；若输入为 null 或空则返回空字符串
     */
    private static void postMain(Runnable runnable) {
        if (runnable == null) return;
        try {
            new android.os.Handler(android.os.Looper.getMainLooper()).post(runnable);
        } catch (RuntimeException e) {
            runnable.run();
        }
    }

    static String trimTrailingSlash(String value) {
        if (value == null || value.isEmpty()) return "";
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * 更新检查回调接口。
     * 所有方法均在主线程中调用，实现类可直接进行 UI 操作。
     */
    public interface UpdateCheckCallback {
        /**
         * 检查完成，返回更新信息。
         * @param info 更新信息，可能表示有更新或无更新
         */
        void onResult(UpdateInfo info);

        /**
         * 检查过程中发生错误。
         * @param message 错误描述
         */
        void onError(String message);

        /** 检查被取消。 */
        void onCancelled();
    }

    /**
     * 下载进度回调接口。
     * 所有方法均在主线程中调用，实现类可直接进行 UI 操作。
     */
    public interface DownloadCallback {
        /**
         * 下载进度更新。
         * @param downloaded 已下载字节数
         * @param total      总字节数（可能为 0 表示未知大小）
         */
        void onProgress(long downloaded, long total);

        /** 正在校验下载文件的完整性（MD5 校验）。 */
        void onVerifying();

        /**
         * 下载完成。
         * @param apkFile 下载完成的 APK 文件
         */
        void onComplete(File apkFile);

        /**
         * 下载过程中发生错误。
         * @param message 错误描述
         */
        void onError(String message);

        /** 下载被取消。 */
        void onCancelled();
    }
}
