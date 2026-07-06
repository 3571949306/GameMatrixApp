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

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.gamecenter.app.R;
import com.gamecenter.app.browser.data.entity.BrowserDownloadEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 浏览器下载管理器，封装系统 DownloadManager + Room 持久化。
 */
public class BrowserDownloadManager {

    private static volatile BrowserDownloadManager instance;
    private final Context context;
    private final DownloadManager downloadManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private long lastDownloadId = -1;

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            queryProgress();
            progressHandler.postDelayed(this, 1000);
        }
    };

    private BrowserDownloadManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        registerReceiver();
        progressHandler.post(progressRunnable);
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

    public long downloadFile(String url, String fileName, String mimeType, String userAgent) {
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
        request.setTitle(fileName);
        request.setDescription(context.getString(R.string.browser_download_description));

        String finalMimeType = (mimeType != null && !mimeType.isEmpty()) ? mimeType : getMimeType(url);
        if ("*/*".equals(finalMimeType)) {
            finalMimeType = getMimeType(fileName);
        }
        request.setMimeType(finalMimeType);
        if (userAgent != null && !userAgent.isEmpty()) {
            request.addRequestHeader("User-Agent", userAgent);
        }

        lastDownloadId = downloadManager.enqueue(request);

        final long systemDownloadId = lastDownloadId;
        final String finalUrl = url;
        final String finalName = fileName;
        final String persistedMimeType = finalMimeType;
        executor.execute(() -> {
            try {
                BrowserDownloadEntity entity = new BrowserDownloadEntity();
                entity.setFileName(finalName);
                entity.setUrl(finalUrl);
                entity.setMimeType(persistedMimeType);
                entity.setFilePath("");
                entity.setStatus(BrowserDownloadEntity.STATUS_DOWNLOADING);
                entity.setSystemDownloadId(systemDownloadId);
                entity.setCreateTime(System.currentTimeMillis());
                BrowserDatabase.getInstance(context).downloadDao().insert(entity);
            } catch (Exception ignored) {}
        });
        return lastDownloadId;
    }

    public boolean isDangerousFile(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".apk") || lower.endsWith(".exe") || lower.endsWith(".bat")
                || lower.endsWith(".sh") || lower.endsWith(".cmd") || lower.endsWith(".vbs")
                || lower.endsWith(".js") || lower.endsWith(".msi");
    }

    public String getMimeType(String url) {
        String extension = "";
        int dotIndex = url.lastIndexOf('.');
        int slashIndex = url.lastIndexOf('/');
        if (dotIndex > slashIndex && dotIndex >= 0) {
            extension = url.substring(dotIndex + 1).toLowerCase();
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
        executor.execute(() -> {
            try {
                List<BrowserDownloadEntity> list = BrowserDatabase.getInstance(context).downloadDao().getAllDownloads();
                callback.onResult(list);
            } catch (Exception e) {
                callback.onResult(new ArrayList<>());
            }
        });
    }

    public void deleteDownload(long id) {
        executor.execute(() -> {
            try {
                BrowserDownloadEntity entity = BrowserDatabase.getInstance(context).downloadDao().getById(id);
                if (entity != null && entity.getSystemDownloadId() != -1) {
                    try {
                        downloadManager.remove(entity.getSystemDownloadId());
                    } catch (Exception ignored) {}
                }
                BrowserDatabase.getInstance(context).downloadDao().deleteById(id);
            } catch (Exception ignored) {}
        });
    }

    public void deleteDownloadWithFile(long id) {
        executor.execute(() -> {
            try {
                BrowserDownloadEntity entity = BrowserDatabase.getInstance(context).downloadDao().getById(id);
                if (entity != null) {
                    if (entity.getSystemDownloadId() != -1) {
                        try {
                            downloadManager.remove(entity.getSystemDownloadId());
                        } catch (Exception ignored) {}
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
            } catch (Exception ignored) {}
        });
    }

    public void clearAllDownloads(boolean deleteFiles) {
        executor.execute(() -> {
            try {
                if (deleteFiles) {
                    List<BrowserDownloadEntity> list = BrowserDatabase.getInstance(context).downloadDao().getAllDownloads();
                    for (BrowserDownloadEntity entity : list) {
                        if (entity.getSystemDownloadId() != -1) {
                            try { downloadManager.remove(entity.getSystemDownloadId()); } catch (Exception ignored) {}
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
            } catch (Exception ignored) {}
        });
    }

    private void queryProgress() {
        executor.execute(() -> {
            try {
                List<BrowserDownloadEntity> list = BrowserDatabase.getInstance(context).downloadDao()
                        .getByStatuses(new int[]{BrowserDownloadEntity.STATUS_WAITING, BrowserDownloadEntity.STATUS_DOWNLOADING});
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
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        });
    }

    private void registerReceiver() {
        try {
            BroadcastReceiver receiver = new BroadcastReceiver() {
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
                    } catch (Exception ignored) {}
                    final int newStatus = (status == DownloadManager.STATUS_SUCCESSFUL)
                            ? BrowserDownloadEntity.STATUS_COMPLETED
                            : BrowserDownloadEntity.STATUS_FAILED;
                    final String finalLocalUri = localUri;
                    final long finalTotal = total;
                    final long finalDownloaded = downloaded;
                    executor.execute(() -> {
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
                        } catch (Exception ignored) {}
                    });
                }
            };
            IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
        } catch (Exception e) {
            // 忽略注册失败
        }
    }

    public interface DownloadListCallback {
        void onResult(List<BrowserDownloadEntity> list);
    }
}
