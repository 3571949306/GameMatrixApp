package com.gamecenter.app.browser.data;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.gamecenter.app.R;
import com.gamecenter.app.browser.util.UrlUtils;
import com.gamecenter.app.browser.core.security.DownloadSecurityValidator;
import com.gamecenter.app.browser.core.security.FileNameSanitizer;
import com.gamecenter.app.browser.data.entity.BrowserDownloadEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 浏览器下载管理器，封装系统 DownloadManager + Room 持久化。
 */
public class BrowserDownloadManager {

    private static volatile BrowserDownloadManager instance;
    private final Context context;
    private final DownloadManager downloadManager;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private long lastDownloadId = -1;

    private synchronized ExecutorService getExecutor() {
        if (executor.isShutdown()) {
            executor = Executors.newSingleThreadExecutor();
        }
        return executor;
    }

    /**
     * P0 内存泄漏修复：进度轮询改为按需启动。
     * 原实现 progressRunnable 内 postDelayed(this, 1000) 形成无限循环，
     * 进程存活期间每秒触发 queryProgress()，即使无下载任务也不停止，
     * 导致长会话 PSS 持续增长（Cursor 分配 + lambda 闭包）。
     * 现改为：仅在有下载任务时轮询，全部完成/失败后自动停止。
     */
    private volatile boolean isPolling = false;
    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            queryProgressAndAutoStop();
        }
    };

    /** 注册的广播接收器引用，用于注销。 */
    private BroadcastReceiver downloadReceiver;

    private BrowserDownloadManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        registerReceiver();
        // 不再启动无限轮询；改为 downloadFile 时按需启动
    }

    public static BrowserDownloadManager getInstance(@NonNull Context context) {
        if (instance == null) {
            synchronized (BrowserDownloadManager.class) {
                if (instance == null) instance = new BrowserDownloadManager(context);
            }
        }
        return instance;
    }

    public long downloadFile(String url, String fileName, String mimeType) {
        return downloadFile(url, fileName, mimeType, null);
    }

    /**
     * S1/D3: 下载文件名净化，防止路径穿越（../）。
     * S4: 危险文件检测，APK/EXE 等可执行文件走私有目录而非公共 Downloads。
     */
    public long downloadFile(String url, String fileName, String mimeType, String userAgent) {
        if (!UrlUtils.isValidHttpUrl(url)) {
            Log.w("BrowserDownloadMgr", "Rejecting non-http(s) download URL");
            return -1L;
        }
        if (downloadManager == null) {
            Log.e("BrowserDownloadMgr", "DownloadManager is unavailable");
            return -1L;
        }
        // #8：shutdown 后（配置变更）懒重注册广播接收
        ensureReceiverRegistered();

        // S1: 净化文件名，去除路径穿越字符
        String safeFileName = sanitizeFileName(fileName);

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        String finalMimeType = (mimeType != null && !mimeType.isEmpty()) ? mimeType : getMimeType(url);
        if ("*/*".equals(finalMimeType)) {
            finalMimeType = getMimeType(safeFileName);
        }

        // S4: executable/script-like files must not be placed in the shared public
        // Downloads directory. DownloadManager supports an app-scoped external
        // directory, which keeps the normal DownloadManager/Content URI flow while
        // preventing an accidental download from becoming a shared install artifact.
        boolean dangerous = DownloadSecurityValidator.policyFor(safeFileName, finalMimeType)
                == DownloadSecurityValidator.TargetPolicy.PRIVATE_APP_DIR;
        // B14: 同名去重必须在真实落盘目录做存在性检查，避免静默覆盖已下载文件。
        // 注意：存在并发下载同名文件的窄竞态（检查与 enqueue 之间），属 best-effort。
        safeFileName = dedupeFileName(safeFileName, dangerous);
        if (dangerous) {
            request.setDestinationInExternalFilesDir(
                    context, Environment.DIRECTORY_DOWNLOADS, safeFileName);
        } else {
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, safeFileName);
        }
        request.setTitle(safeFileName);
        request.setDescription(context.getString(R.string.browser_download_description));

        request.setMimeType(finalMimeType);
        if (userAgent != null && !userAgent.isEmpty()) {
            request.addRequestHeader("User-Agent", userAgent);
        }

        final long systemDownloadId;
        try {
            systemDownloadId = downloadManager.enqueue(request);
        } catch (RuntimeException e) {
            // Destination conflicts, storage policy failures and malformed provider
            // state are recoverable UI failures. Do not persist a phantom download
            // record or report that a task has started.
            Log.e("BrowserDownloadMgr", "Failed to enqueue download", e);
            return -1L;
        }
        lastDownloadId = systemDownloadId;

        // P0 内存泄漏修复：按需启动进度轮询（无下载任务时不轮询，避免 PSS 持续增长）
        startProgressPolling();

        final String finalUrl = url;
        final String finalName = safeFileName;  // S1: 使用净化后的文件名
        final String persistedMimeType = finalMimeType;
        // D4: 危险文件标记（供 UI 确认/拦截使用）
        final boolean isDangerous = isDangerousFile(safeFileName, finalMimeType);

        getExecutor().execute(() -> {
            try {
                BrowserDownloadEntity entity = new BrowserDownloadEntity();
                entity.setFileName(finalName);
                entity.setUrl(finalUrl);
                entity.setMimeType(persistedMimeType);
                entity.setFilePath("");
                entity.setStatus(BrowserDownloadEntity.STATUS_DOWNLOADING);
                entity.setSystemDownloadId(systemDownloadId);
                entity.setCreateTime(System.currentTimeMillis());
                entity.setDangerous(isDangerous);  // D4: 记录危险标记
                BrowserDatabase.getInstance(context).downloadDao().insert(entity);
            } catch (Exception e) { Log.e("BrowserDownloadMgr", "Failed to insert download entity", e); }
        });
        return systemDownloadId;
    }

    /**
     * B14: 同名去重。目标目录已存在同名文件时追加 " (n)" 序号（保留扩展名）。
     *
     * <p>目录与最终落盘目录一致：公共 Downloads 或 app 私有 Downloads。
     * 返回原始文件名表示无冲突。目录不可用时保守返回原名，由系统
     * DownloadManager 的默认行为兜底。
     */
    @NonNull
    private String dedupeFileName(@NonNull String fileName, boolean privateDir) {
        try {
            java.io.File dir = privateDir
                    ? context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    : Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (dir == null || !new java.io.File(dir, fileName).exists()) {
                return fileName;
            }
            int dot = fileName.lastIndexOf('.');
            String base = dot > 0 ? fileName.substring(0, dot) : fileName;
            String ext = dot > 0 ? fileName.substring(dot) : "";
            for (int i = 1; i < 1000; i++) {
                String candidate = base + " (" + i + ")" + ext;
                if (!new java.io.File(dir, candidate).exists()) {
                    return candidate;
                }
            }
            return base + " (" + System.currentTimeMillis() + ")" + ext;
        } catch (Throwable t) {
            Log.w("BrowserDownloadMgr", "dedupeFileName failed, keep original", t);
            return fileName;
        }
    }

    /**
     * S1/D3: 文件名净化 - 仅保留基本字符，去除路径穿越风险。
     */
    @NonNull
    private String sanitizeFileName(@Nullable String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return "download";
        }
        String sanitized = FileNameSanitizer.sanitize(fileName);
        // 限制长度
        if (sanitized.length() > 100) {
            // 保留扩展名
            int dotIdx = sanitized.lastIndexOf('.');
            if (dotIdx > 0 && dotIdx < sanitized.length() - 5) {
                sanitized = sanitized.substring(0, 96) + sanitized.substring(dotIdx);
            } else {
                sanitized = sanitized.substring(0, 100);
            }
        }
        // FileNameSanitizer 已经去除了首尾空白和点号；再做一次兜底，保持
        // 该管理器对历史调用方的行为稳定。
        sanitized = sanitized.trim().replaceAll("^[. ]+|[. ]+$", "");
        if (sanitized.isEmpty()) sanitized = "download";
        return sanitized;
    }

    public boolean isDangerousFile(String fileName) {
        return isDangerousFile(fileName, null);
    }

    public boolean isDangerousFile(String fileName, @Nullable String mimeType) {
        return fileName != null
                && DownloadSecurityValidator.policyFor(fileName, mimeType)
                        == DownloadSecurityValidator.TargetPolicy.PRIVATE_APP_DIR;
    }

    public String getMimeType(String url) {
        String extension = "";
        int dotIndex = url.lastIndexOf('.');
        int slashIndex = url.lastIndexOf('/');
        if (dotIndex > slashIndex && dotIndex >= 0) {
            extension = url.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        }
        switch (extension) {
            case "pdf": return "application/pdf";
            case "jpg": case "jpeg": return "image/jpeg";
            case "png": return "image/png";
            case "mp4": return "video/mp4";
            case "mp3": return "audio/mpeg";
            case "zip": return "application/zip";
            case "apk": return "application/vnd.android.package-archive";
            default: return "*/*";
        }
    }

    public void getAllDownloads(DownloadListCallback callback) {
        getExecutor().execute(() -> {
            try {
                List<BrowserDownloadEntity> list = BrowserDatabase.getInstance(context).downloadDao().getAllDownloads();
                callback.onResult(list);
            } catch (Exception e) {
                callback.onResult(new ArrayList<>());
            }
        });
    }

    public void deleteDownload(long id) {
        getExecutor().execute(() -> {
            try {
                BrowserDownloadEntity entity = BrowserDatabase.getInstance(context).downloadDao().getById(id);
                if (entity != null && entity.getSystemDownloadId() != -1) {
                    try {
                        downloadManager.remove(entity.getSystemDownloadId());
                    } catch (Exception e) { Log.e("BrowserDownloadMgr", "Failed to insert download entity", e); }
                }
                BrowserDatabase.getInstance(context).downloadDao().deleteById(id);
            } catch (Exception e) { Log.e("BrowserDownloadMgr", "Failed to insert download entity", e); }
        });
    }

    public void deleteDownloadWithFile(long id) {
        getExecutor().execute(() -> {
            try {
                BrowserDownloadEntity entity = BrowserDatabase.getInstance(context).downloadDao().getById(id);
                if (entity != null) {
                    if (entity.getSystemDownloadId() != -1) {
                        try {
                            downloadManager.remove(entity.getSystemDownloadId());
                        } catch (Exception e) { Log.e("BrowserDownloadMgr", "Failed to insert download entity", e); }
                    }
                    if (entity.getFilePath() != null && !entity.getFilePath().isEmpty()) {
                        String path = entity.getFilePath();
                        if (path.startsWith("file://")) {
                            path = android.net.Uri.parse(path).getPath();
                        }
                        if (path != null && !path.startsWith("content://")) {
                            java.io.File file = new java.io.File(path);
                            if (file.exists()) file.delete();
                        }
                    }
                }
                BrowserDatabase.getInstance(context).downloadDao().deleteById(id);
            } catch (Exception e) { Log.e("BrowserDownloadMgr", "Failed to insert download entity", e); }
        });
    }

    public void clearAllDownloads(boolean deleteFiles) {
        getExecutor().execute(() -> {
            try {
                if (deleteFiles) {
                    List<BrowserDownloadEntity> list = BrowserDatabase.getInstance(context).downloadDao().getAllDownloads();
                    for (BrowserDownloadEntity entity : list) {
                        if (entity.getSystemDownloadId() != -1) {
                            try { downloadManager.remove(entity.getSystemDownloadId()); } catch (Exception e) { Log.e("BrowserDownloadMgr", "Failed to insert download entity", e); }
                        }
                        if (entity.getFilePath() != null && !entity.getFilePath().isEmpty()) {
                            String path = entity.getFilePath();
                            if (path.startsWith("file://")) path = android.net.Uri.parse(path).getPath();
                            if (path != null && !path.startsWith("content://")) {
                                java.io.File file = new java.io.File(path);
                                if (file.exists()) file.delete();
                            }
                        }
                    }
                }
                BrowserDatabase.getInstance(context).downloadDao().deleteAll();
            } catch (Exception e) { Log.e("BrowserDownloadMgr", "Failed to insert download entity", e); }
        });
    }

    /**
     * 启动进度轮询。已在轮询时重复调用安全（幂等）。
     * P0 内存泄漏修复：替代原构造函数中的无限 postDelayed 循环。
     */
    private void startProgressPolling() {
        if (isPolling) return;
        isPolling = true;
        progressHandler.post(progressRunnable);
    }

    /**
     * 停止进度轮询并移除所有待执行的回调。
     */
    private void stopProgressPolling() {
        isPolling = false;
        progressHandler.removeCallbacks(progressRunnable);
    }

    /**
     * 查询下载进度并自动决定是否继续轮询。
     * 无活跃下载任务（WAITING/DOWNLOADING）时自动停止轮询，避免空转。
     */
    private void queryProgressAndAutoStop() {
        getExecutor().execute(() -> {
            boolean hasActive = false;
            try {
                List<BrowserDownloadEntity> list = BrowserDatabase.getInstance(context).downloadDao()
                        .getByStatuses(new int[]{BrowserDownloadEntity.STATUS_WAITING, BrowserDownloadEntity.STATUS_DOWNLOADING});
                hasActive = !list.isEmpty();
                for (BrowserDownloadEntity entity : list) {
                    long systemId = entity.getSystemDownloadId();
                    if (systemId <= 0) continue;
                    DownloadManager.Query q = new DownloadManager.Query().setFilterById(systemId);
                    try (Cursor c = downloadManager.query(q)) {
                        if (c != null && c.moveToFirst()) {
                            int statusIdx = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                            int totalIdx = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                            int downloadedIdx = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                            int status = statusIdx >= 0 ? c.getInt(statusIdx) : DownloadManager.STATUS_FAILED;
                            long total = totalIdx >= 0 ? c.getLong(totalIdx) : 0;
                            long downloaded = downloadedIdx >= 0 ? c.getLong(downloadedIdx) : 0;
                            int newStatus = status == DownloadManager.STATUS_SUCCESSFUL ? BrowserDownloadEntity.STATUS_COMPLETED
                                    : status == DownloadManager.STATUS_FAILED ? BrowserDownloadEntity.STATUS_FAILED
                                    : BrowserDownloadEntity.STATUS_DOWNLOADING;
                            BrowserDatabase.getInstance(context).downloadDao()
                                    .updateProgressBySystemDownloadId(systemId, newStatus, total, downloaded);
                        }
                    } catch (Exception e) { Log.e("BrowserDownloadMgr", "Failed to insert download entity", e); }
                }
            } catch (Exception e) { Log.e("BrowserDownloadMgr", "Failed to insert download entity", e); }
            // 仍有活跃任务则继续轮询，否则停止
            if (hasActive && isPolling) {
                progressHandler.postDelayed(progressRunnable, 1000);
            } else {
                isPolling = false;
            }
        });
    }

    /**
     * 供查询进度使用（保留原方法签名兼容外部调用）。
     */
    private void queryProgress() {
        queryProgressAndAutoStop();
    }

    private void registerReceiver() {
        try {
            if (downloadReceiver != null) {
                try { context.unregisterReceiver(downloadReceiver); } catch (Exception ignored) {}
                downloadReceiver = null;
            }
            downloadReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (id < 0) return;
                    queryAndUpdate(id);
                }

                private void queryAndUpdate(long downloadId) {
                    DownloadManager.Query q = new DownloadManager.Query().setFilterById(downloadId);
                    int status = DownloadManager.STATUS_FAILED;
                    String localUri = null;
                    long total = 0;
                    long downloaded = 0;
                    try (Cursor c = downloadManager.query(q)) {
                        if (c != null && c.moveToFirst()) {
                            int statusIdx = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                            int localUriIdx = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                            int totalIdx = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                            int downloadedIdx = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                            if (statusIdx >= 0) status = c.getInt(statusIdx);
                            if (localUriIdx >= 0) localUri = c.getString(localUriIdx);
                            if (totalIdx >= 0) total = c.getLong(totalIdx);
                            if (downloadedIdx >= 0) downloaded = c.getLong(downloadedIdx);
                        }
                    } catch (Exception e) { Log.e("BrowserDownloadMgr", "Failed to insert download entity", e); }
                    final int newStatus = (status == DownloadManager.STATUS_SUCCESSFUL)
                            ? BrowserDownloadEntity.STATUS_COMPLETED
                            : BrowserDownloadEntity.STATUS_FAILED;
                    final String finalLocalUri = localUri;
                    final long finalTotal = total;
                    final long finalDownloaded = downloaded;
                    getExecutor().execute(() -> {
                        try {
                            BrowserDownloadEntity entity = BrowserDatabase.getInstance(context)
                                    .downloadDao().getBySystemDownloadId(downloadId);
                            if (entity != null) {
                                String path = finalLocalUri != null ? finalLocalUri : entity.getFilePath();
                                if (path != null && path.startsWith("file://")) {
                                    String realPath = Uri.parse(path).getPath();
                                    path = realPath != null ? realPath : "";
                                }
                                BrowserDatabase.getInstance(context).downloadDao()
                                        .updateBySystemDownloadId(downloadId, newStatus, path, System.currentTimeMillis());
                                BrowserDatabase.getInstance(context).downloadDao()
                                        .updateProgressBySystemDownloadId(downloadId, newStatus, finalTotal, finalDownloaded);
                            }
                        } catch (Exception e) { Log.e("BrowserDownloadMgr", "Failed to insert download entity", e); }
                    });
                }
            };
            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            ContextCompat.registerReceiver(context, downloadReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        } catch (Exception e) {
            // 忽略注册失败
        }
    }

    /**
     * #8：shutdown 后（如配置变更重建）懒重注册广播接收，避免完成广播永久失效。
     */
    public void ensureReceiverRegistered() {
        synchronized (BrowserDownloadManager.class) {
            if (downloadReceiver == null) registerReceiver();
        }
    }

    /**
     * #8：配置变更（旋转）重建 Fragment 后调用。恢复广播接收注册，
     * 且若存在 in-flight 下载则重新启动进度轮询，避免状态卡在“下载中”。
     */
    public void refresh() {
        ensureReceiverRegistered();
        getExecutor().execute(() -> {
            try {
                boolean hasActive = !BrowserDatabase.getInstance(context).downloadDao()
                        .getByStatuses(new int[]{BrowserDownloadEntity.STATUS_WAITING,
                                BrowserDownloadEntity.STATUS_DOWNLOADING})
                        .isEmpty();
                if (hasActive) {
                    progressHandler.post(() -> startProgressPolling());
                }
            } catch (Exception e) {
                Log.e("BrowserDownloadMgr", "refresh failed", e);
            }
        });
    }

    /**
     * P0 内存泄漏修复：注销广播接收器、停止轮询、关闭线程池。
     * 供 App.onTerminate 或测试用例调用；单例进程级生命周期下不强制要求调用，
     * 但调用后可彻底释放原生资源，避免长会话 PSS 累积。
     */
    public void shutdown() {
        stopProgressPolling();
        if (downloadReceiver != null) {
            try {
                context.unregisterReceiver(downloadReceiver);
            } catch (Exception e) { Log.e("BrowserDownloadMgr", "Failed to insert download entity", e); }
            downloadReceiver = null;
        }
        if (!getExecutor().isShutdown()) {
            getExecutor().shutdown();
        }
    }

    public interface DownloadListCallback {
        void onResult(List<BrowserDownloadEntity> list);
    }
}
