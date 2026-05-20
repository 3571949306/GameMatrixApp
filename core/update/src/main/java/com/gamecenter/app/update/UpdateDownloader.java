package com.gamecenter.app.update;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.gamecenter.app.update.BuildConfig;
import com.gamecenter.app.utils.NetworkErrorHandler;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.text.MessageFormat;

import javax.net.ssl.HttpsURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * APK 下载器，负责从多个下载源下载更新安装包。
 * <p>
 * 支持多源自动切换：当首选下载源失败或速度过慢时，自动切换到下一个源重试。
 * 下载完成后会进行 MD5 校验，确保安装包完整性。
 * </p>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>使用单线程线程池执行下载任务，避免并发下载冲突</li>
 *   <li>内置速度检测机制：下载开始 3 秒后检测速度，低于 50KB/s 自动切换源</li>
 *   <li>下载前自动清理旧 APK 文件，避免占用过多存储空间</li>
 *   <li>支持断点续传检测：若本地已有 MD5 匹配的 APK，直接返回无需重新下载</li>
 * </ul>
 * </p>
 */
public class UpdateDownloader {

    private static final String TAG = "UpdateDownloader";

    // 速度检测阈值：低于此速度 (bytes/s) 触发换源
    static final long MIN_DOWNLOAD_SPEED_BYTES_PER_SEC = 50 * 1024; // 50 KB/s
    // 速度检测时间：下载开始后等待此时间再检测速度
    static final long SPEED_CHECK_INTERVAL_MS = 3000; // 3 秒

    /** 单线程线程池，确保下载任务串行执行 */
    private final ExecutorService executor;
    /** 取消标志，volatile 保证多线程可见性 */
    private volatile boolean isCancelled = false;
    /** 通知助手，用于显示下载进度和完成通知 */
    private final UpdateNotificationHelper notificationHelper;

    /**
     * 构造函数。
     *
     * @param notificationHelper 通知助手，用于在下载过程中显示系统通知
     */
    UpdateDownloader(UpdateNotificationHelper notificationHelper) {
        this.notificationHelper = notificationHelper;
        executor = Executors.newSingleThreadExecutor();
    }

    /**
     * 异步下载 APK 文件，支持多源自动切换。
     * <p>
     * 首先构建下载 URL 列表（包含主 URL 和备用 URL），
     * 然后依次尝试每个源，直到下载成功或所有源均失败。
     * 下载成功后会显示完成通知。
     * </p>
     *
     * @param context  上下文
     * @param info     更新信息，包含下载 URL、MD5 校验值等
     * @param callback 下载进度和结果回调
     */
    public void downloadApk(final Context context, final UpdateInfo info, final UpdateManager.DownloadCallback callback) {
        isCancelled = false;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                List<String> downloadUrls = buildDownloadUrls(info);
                File apkFile = null;
                String errorMsg = null;
                for (int sourceIndex = 0; sourceIndex < downloadUrls.size(); sourceIndex++) {
                    if (isCancelled) {
                        if (callback != null) callback.onCancelled();
                        return;
                    }
                    String currentUrl = downloadUrls.get(sourceIndex);
                    Log.d(TAG, "Attempting download from source " + (sourceIndex + 1) + "/" + downloadUrls.size() + ": " + currentUrl);
                    try {
                        apkFile = downloadFromUrl(context, info, currentUrl, callback);
                        if (apkFile != null && apkFile.exists()) {
                            Log.d(TAG, "Download succeeded from source " + (sourceIndex + 1));
                            notificationHelper.showDownloadCompleteNotification(context, apkFile, info.getVersionName());
                            if (callback != null) callback.onComplete(apkFile);
                            return;
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Source " + (sourceIndex + 1) + " failed: " + e.getMessage());
                        int errorCode = NetworkErrorHandler.getErrorCodeFromException(e);
                        String userMessage = NetworkErrorHandler.getErrorMessage(context, errorCode);
                        // 仅在最后一个源也失败时才保留错误消息
                        if (sourceIndex >= downloadUrls.size() - 1) {
                            errorMsg = userMessage;
                        } else {
                            Log.d(TAG, "Switching to next download source...");
                        }
                    }
                }
                // 所有源均失败
                if (errorMsg != null) {
                    if (callback != null) callback.onError("下载失败");
                    if (callback != null) callback.onError(errorMsg);
                } else {
                    if (callback != null) callback.onError("所有下载源均不可用");
                }
            }
        });
    }

    /**
     * 从指定 URL 下载 APK 文件。
     * <p>
     * 下载流程：
     * <ol>
     *   <li>清理旧 APK 文件</li>
     *   <li>检查本地是否已有 MD5 匹配的 APK（断点续传检测）</li>
     *   <li>建立 HTTP 连接，读取数据并写入文件</li>
     *   <li>下载过程中进行速度检测，速度过慢时抛异常触发换源</li>
     *   <li>下载完成后进行 MD5 校验</li>
     * </ol>
     * </p>
     *
     * @param context     上下文
     * @param info        更新信息
     * @param downloadUrl 下载 URL
     * @param callback    下载进度回调
     * @return 下载完成的 APK 文件
     * @throws Exception 下载失败、速度过慢或校验失败时抛出
     */
    File downloadFromUrl(Context context, UpdateInfo info, String downloadUrl, UpdateManager.DownloadCallback callback) throws Exception {
        // 下载前清理旧 APK，释放存储空间
        cleanOldApksBeforeDownload(context);
        File downloadDir = getDownloadDir(context);
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }
        File apkFile = new File(downloadDir,
                MessageFormat.format("GameCenter_v{0}_{1}.apk",
                        String.valueOf(info.getVersionCode()), info.getVersionName()));

        // 断点续传检测：若本地已有 MD5 匹配的 APK，直接返回
        if (apkFile.exists() && !info.getMd5().isEmpty()) {
            String existingMd5 = computeMd5(apkFile);
            if (info.getMd5().equalsIgnoreCase(existingMd5)) {
                return apkFile;
            }
            // MD5 不匹配，删除旧文件重新下载
            apkFile.delete();
        }

        URL url = new URL(downloadUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        // 对 HTTPS 连接应用自定义 SSL 配置
        if (conn instanceof HttpsURLConnection) {
            SSLHelper.applySsl((HttpsURLConnection) conn);
        }
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(UpdateChecker.FALLBACK_CONNECT_TIMEOUT);
        // 读取超时设为 5 分钟，适应大文件下载
        conn.setReadTimeout(300000);
        conn.setRequestProperty("User-Agent", "GameCenterApp/" + BuildConfig.VERSION_NAME);

        // 连接建立后再次检查取消状态
        if (isCancelled) {
            conn.disconnect();
            return null;
        }

        // 获取文件总大小，优先使用服务端返回的 Content-Length
        long totalSize = conn.getContentLength();
        // 若服务端未返回 Content-Length，使用 UpdateInfo 中的 fileSize
        if (totalSize <= 0 && info.getFileSize() > 0) {
            totalSize = info.getFileSize();
        }

        BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(apkFile));
        byte[] buf = new byte[65536];
        long downloaded = 0;
        long lastReportTime = 0;
        int read;
        // 速度检测相关变量
        long speedCheckStartTime = 0;
        long speedCheckBytes = 0;
        boolean speedChecked = false;

        while ((read = in.read(buf)) != -1) {
            // 下载过程中检查取消状态
            if (isCancelled) {
                in.close();
                out.close();
                apkFile.delete();
                conn.disconnect();
                return null;
            }
            out.write(buf, 0, read);
            downloaded += read;

            // 速度检测逻辑：仅在首次检测前执行
            if (!speedChecked) {
                long now = System.currentTimeMillis();
                if (speedCheckStartTime == 0) {
                    // 记录速度检测起始时间和已下载字节数
                    speedCheckStartTime = now;
                    speedCheckBytes = downloaded;
                } else if (now - speedCheckStartTime >= SPEED_CHECK_INTERVAL_MS) {
                    // 达到检测间隔，计算下载速度
                    long elapsed = now - speedCheckStartTime;
                    long speed = (downloaded - speedCheckBytes) * 1000 / elapsed;
                    Log.d(TAG, "Download speed: " + (speed / 1024) + " KB/s");
                    if (speed < MIN_DOWNLOAD_SPEED_BYTES_PER_SEC) {
                        // 速度过慢，关闭连接并抛异常触发换源
                        Log.w(TAG, "Speed too low, switching source...");
                        in.close();
                        out.close();
                        apkFile.delete();
                        conn.disconnect();
                        throw new Exception("下载速度过慢，正在切换下载源");
                    }
                    speedChecked = true;
                }
            }

            // 进度上报：间隔至少 80ms 或读取到末尾时上报
            long now = System.currentTimeMillis();
            if (now - lastReportTime >= 80 || read < buf.length) {
                final long fDownloaded = downloaded;
                final long fTotal = totalSize;
                if (callback != null) callback.onProgress(fDownloaded, fTotal);
                int percent = totalSize > 0 ? (int) (downloaded * 100 / totalSize) : 0;
                notificationHelper.showDownloadNotification(context, percent, info.getVersionName());
                lastReportTime = now;
            }
        }
        in.close();
        out.close();
        conn.disconnect();

        // 下载完成后进行 MD5 校验
        if (!info.getMd5().isEmpty()) {
            String actualMd5 = computeMd5(apkFile);
            if (!info.getMd5().equalsIgnoreCase(actualMd5)) {
                apkFile.delete();
                throw new Exception("安装包校验失败");
            }
        }
        return apkFile;
    }

    /**
     * 构建 APK 下载 URL 列表，包含主 URL 和备用 URL。
     * <p>
     * 列表构建逻辑：
     * <ol>
     *   <li>UpdateInfo 中的主下载 URL</li>
     *   <li>GitHub Releases 下载 URL</li>
     *   <li>香港 VPS 下载 URL</li>
     *   <li>美国 VPS 下载 URL</li>
     * </ol>
     * 自动去重，避免同一 URL 出现多次。
     * </p>
     *
     * @param info 更新信息
     * @return 按优先级排列的下载 URL 列表
     */
    List<String> buildDownloadUrls(UpdateInfo info) {
        List<String> urls = new ArrayList<>();
        String primaryUrl = info.getDownloadUrl();
        if (primaryUrl != null && !primaryUrl.isEmpty()) {
            urls.add(primaryUrl);
        }
        String apkName = extractApkName(primaryUrl, info);
        String githubUrl = buildGitHubAssetUrl(info);
        String hkUrl = UpdateManager.trimTrailingSlash(UpdateChecker.HK_BASE_URL) + "/" + apkName;
        String usUrl = UpdateChecker.US_BASE_URL.isEmpty() ? "" : UpdateManager.trimTrailingSlash(UpdateChecker.US_BASE_URL) + "/" + apkName;

        // 添加备用源，自动去重
        if (!urls.contains(githubUrl)) urls.add(githubUrl);
        if (!urls.contains(hkUrl)) urls.add(hkUrl);
        if (!usUrl.isEmpty() && !urls.contains(usUrl)) urls.add(usUrl);
        return urls;
    }

    /**
     * 从下载 URL 中提取 APK 文件名。
     * 取 URL 最后一个路径段作为文件名，去除查询参数。
     * 若无法提取或文件名为 JSON 文件，则使用默认文件名。
     *
     * @param url  下载 URL
     * @param info 更新信息，用于确定默认文件名（Beta/stable）
     * @return APK 文件名
     */
    String extractApkName(String url, UpdateInfo info) {
        String fallback = info != null && info.isBetaRelease() ? "app-beta.apk" : "app-release.apk";
        if (url == null || url.isEmpty()) return fallback;
        int idx = url.lastIndexOf('/');
        String name = idx >= 0 ? url.substring(idx + 1) : url;
        // 去除 URL 查询参数
        int query = name.indexOf('?');
        if (query >= 0) {
            name = name.substring(0, query);
        }
        return name.isEmpty() || name.endsWith(".json") ? fallback : name;
    }

    /**
     * 构建 GitHub Releases 的 APK 下载 URL。
     * 若有版本标签则使用标签路径，否则使用 latest 路径。
     *
     * @param info 更新信息
     * @return GitHub Releases 下载 URL
     */
    String buildGitHubAssetUrl(UpdateInfo info) {
        String tag = info != null ? info.getVersionName() : "";
        String apkName = info != null && info.isBetaRelease() ? "app-beta.apk" : "app-release.apk";
        if (tag == null || tag.isEmpty()) {
            return "https://github.com/3571949306/GameCenterApp/releases/latest/download/" + apkName;
        }
        return "https://github.com/3571949306/GameCenterApp/releases/download/" + tag + "/" + apkName;
    }

    /**
     * 在下载新 APK 前清理所有旧 APK 文件。
     * 与 {@link #cleanOldApks} 不同，此方法会删除所有旧 APK，不保留最新版。
     */
    private void cleanOldApksBeforeDownload(Context context) {
        File downloadDir = getDownloadDir(context);
        if (!downloadDir.exists() || !downloadDir.isDirectory()) {
            return;
        }
        File[] apkFiles = downloadDir.listFiles((dir, name) ->
                name.startsWith("GameCenter_v") && name.endsWith(".apk"));
        if (apkFiles == null || apkFiles.length == 0) {
            return;
        }
        for (File apk : apkFiles) {
            apk.delete();
        }
        Log.d(TAG, "Cleaned all old APKs before downloading new update");
    }

    /**
     * 清理旧 APK 文件，仅保留版本号最高的一个。
     * 通过正则匹配文件名中的版本号，删除所有非最新版本的 APK。
     *
     * @param context 上下文
     * @return 被删除的文件数量
     */
    public int cleanOldApks(Context context) {
        File downloadDir = getDownloadDir(context);
        if (!downloadDir.exists() || !downloadDir.isDirectory()) {
            return 0;
        }
        File[] apkFiles = downloadDir.listFiles((dir, name) ->
                name.startsWith("GameCenter_v") && name.endsWith(".apk"));
        // 仅有一个或没有 APK 文件，无需清理
        if (apkFiles == null || apkFiles.length <= 1) {
            return 0;
        }
        // 从文件名中提取版本号，保留版本号最高的 APK
        Pattern pattern = Pattern.compile("GameCenter_v(\\d+)_");
        File latestApk = null;
        int latestVersionCode = -1;
        int deletedCount = 0;
        for (File apk : apkFiles) {
            Matcher matcher = pattern.matcher(apk.getName());
            if (matcher.find()) {
                int versionCode = Integer.parseInt(matcher.group(1));
                if (versionCode > latestVersionCode) {
                    // 发现更高版本，删除之前的"最新"APK
                    if (latestApk != null && !latestApk.equals(apk)) {
                        latestApk.delete();
                        deletedCount++;
                    }
                    latestApk = apk;
                    latestVersionCode = versionCode;
                } else {
                    // 当前 APK 版本号较低，删除
                    apk.delete();
                    deletedCount++;
                }
            }
        }
        if (deletedCount > 0) {
            Log.d(TAG, "Cleaned " + deletedCount + " old APK(s), keeping: "
                    + (latestApk != null ? latestApk.getName() : "none"));
        }
        return deletedCount;
    }

    /**
     * 计算文件的 MD5 哈希值。
     * 用于校验下载文件的完整性，确保 APK 未被篡改。
     *
     * @param file 要计算 MD5 的文件
     * @return MD5 哈希值的十六进制字符串；计算失败时返回空字符串
     */
    String computeMd5(File file) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            FileInputStream fis = new FileInputStream(file);
            byte[] buf = new byte[8192];
            int read;
            while ((read = fis.read(buf)) != -1) {
                md.update(buf, 0, read);
            }
            fis.close();
            byte[] digest = md.digest();
            // 将字节数组转换为十六进制字符串
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 获取 APK 下载目录。
     * 优先使用外部存储的 Downloads 目录，不可用时回退到内部存储。
     * APK 文件存放在 "update" 子目录下。
     *
     * @param context 上下文
     * @return 下载目录的 File 对象
     */
    public File getDownloadDir(Context context) {
        File baseDir = null;
        // 优先使用外部存储（用户可通过文件管理器访问）
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        }
        // 外部存储不可用时回退到内部存储
        if (baseDir == null) {
            baseDir = context.getFilesDir();
        }
        return new File(baseDir, "update");
    }

    /**
     * 取消正在进行的下载操作。
     * 设置取消标志位，下载循环会在下一次迭代时退出并清理临时文件。
     */
    void cancel() {
        isCancelled = true;
    }
}
