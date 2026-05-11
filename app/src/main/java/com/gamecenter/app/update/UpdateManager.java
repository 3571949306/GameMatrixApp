package com.gamecenter.app.update;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import androidx.core.content.FileProvider;

import com.gamecenter.app.BuildConfig;
import com.gamecenter.app.SettingsManager;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateManager {

    private static final String TAG = "UpdateManager";
    private static final String PREF_NAME = "update_config";
    private static final String KEY_BASE_URL = "update_base_url";
    private static final String KEY_LAST_CHECK = "last_check_time";
    private static final String CHANNEL_ID = "update_download";
    private static final int NOTIFICATION_ID = 1001;

    // 下载源：香港 VPS -> 美国 VPS -> GitHub Releases
    private static final String HK_BASE_URL = BuildConfig.SERVER_URL;
    private static final String US_BASE_URL = BuildConfig.SERVER_URL_FALLBACK;
    private static final String GITHUB_RELEASES_BASE_URL = "https://github.com/3571949306/GameCenterApp/releases/latest";
    
    // 速度检测阈值：低于此速度 (bytes/s) 触发换源
    private static final long MIN_DOWNLOAD_SPEED_BYTES_PER_SEC = 50 * 1024; // 50 KB/s
    // 速度检测时间：下载开始后等待此时间再检测速度
    private static final long SPEED_CHECK_INTERVAL_MS = 3000; // 3 秒

    private static final int PRIMARY_CONNECT_TIMEOUT = 3000;
    private static final int PRIMARY_READ_TIMEOUT = 5000;
    private static final int FALLBACK_CONNECT_TIMEOUT = 15000;
    private static final int FALLBACK_READ_TIMEOUT = 30000;
    private static final int GITHUB_CONNECT_TIMEOUT = 5000;
    private static final int GITHUB_READ_TIMEOUT = 10000;

    private static UpdateManager instance;
    private final ExecutorService executor;
    private volatile boolean isCancelled = false;

    private UpdateManager() {
        executor = Executors.newSingleThreadExecutor();
        SSLHelper.trustUpdateServer(HK_BASE_URL);
        if (!US_BASE_URL.isEmpty()) {
            SSLHelper.trustUpdateServer(US_BASE_URL);
        }
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "应用更新",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("显示应用更新下载进度");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private void showDownloadNotification(Context context, int progress, String versionName) {
        createNotificationChannel(context);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("正在下载更新")
                .setContentText("版本 " + versionName + " - " + progress + "%")
                .setProgress(100, progress, false)
                .setOngoing(true)
                .setAutoCancel(false);

        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private void showDownloadCompleteNotification(Context context, File apkFile, String versionName) {
        createNotificationChannel(context);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        Intent installIntent = new Intent(Intent.ACTION_VIEW);
        Uri uri;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            uri = androidx.core.content.FileProvider.getUriForFile(context,
                    context.getPackageName() + ".update.fileprovider", apkFile);
            installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            uri = Uri.fromFile(apkFile);
        }
        installIntent.setDataAndType(uri, "application/vnd.android.package-archive");
        installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, installIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("更新下载完成")
                .setContentText("版本 " + versionName + " - 点击安装")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOngoing(false);

        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private void cancelNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.cancel(NOTIFICATION_ID);
        }
    }

    public static synchronized UpdateManager getInstance() {
        if (instance == null) {
            instance = new UpdateManager();
        }
        return instance;
    }

    public String getBaseUrl(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_BASE_URL, HK_BASE_URL);
    }

    public void setBaseUrl(Context context, String baseUrl) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_BASE_URL, baseUrl).apply();
        SSLHelper.trustUpdateServer(baseUrl);
    }

    public void cancel() {
        isCancelled = true;
    }

    public void checkUpdate(Context context, final UpdateCheckCallback callback) {
        isCancelled = false;
        List<String> urls = buildUpdateUrls(context);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                UpdateInfo result = null;
                String errorMsg = null;
                String usedUrl = null;

                for (int i = 0; i < urls.size(); i++) {
                    if (isCancelled) {
                        safeCallback(callback, null, null, "已取消");
                        return;
                    }
                    String baseUrl = urls.get(i);
                    boolean isPrimary = (i == 0);
                    int connectTimeout = isPrimary ? PRIMARY_CONNECT_TIMEOUT : FALLBACK_CONNECT_TIMEOUT;
                    int readTimeout = isPrimary ? PRIMARY_READ_TIMEOUT : FALLBACK_READ_TIMEOUT;
                    try {
                        Log.d(TAG, "Checking update source " + (i + 1) + "/" + urls.size()
                                + ": " + baseUrl + " (primary=" + isPrimary + ")");
                        LocalVersion localVersion = readBundledVersion(context);
                        boolean acceptBeta = SettingsManager.getInstance(context).isAcceptBetaUpdate();
                        String versionJsonUrl = buildVersionJsonUrl(baseUrl, acceptBeta);
                        result = checkVersionFile(context, versionJsonUrl, baseUrl,
                                localVersion, acceptBeta, connectTimeout, readTimeout);
                        usedUrl = baseUrl;
                        Log.d(TAG, "Update check succeeded on source " + (i + 1) + ": " + baseUrl);
                        break;
                    } catch (Exception e) {
                        Log.w(TAG, "Source " + (i + 1) + " (" + baseUrl + ") failed: " + e.getMessage());
                        if (i == urls.size() - 1) {
                            errorMsg = stringFormat("检查更新失败：{0}", e.getMessage());
                        }
                    }
                }

                if (result == null && errorMsg == null) {
                    try {
                        LocalVersion localVersion = readBundledVersion(context);
                        boolean acceptBeta = SettingsManager.getInstance(context).isAcceptBetaUpdate();
                        for (int i = 0; i < urls.size(); i++) {
                            if (isCancelled) {
                                safeCallback(callback, null, null, "已取消");
                                return;
                            }
                            String baseUrl = urls.get(i);
                            boolean isPrimary = (i == 0);
                            int connectTimeout = isPrimary ? PRIMARY_CONNECT_TIMEOUT : FALLBACK_CONNECT_TIMEOUT;
                            int readTimeout = isPrimary ? PRIMARY_READ_TIMEOUT : FALLBACK_READ_TIMEOUT;
                            try {
                                Log.d(TAG, "Fallback legacy API on source " + (i + 1) + ": " + baseUrl);
                                result = checkLegacyApi(context, baseUrl, localVersion, acceptBeta,
                                        connectTimeout, readTimeout);
                                usedUrl = baseUrl;
                                break;
                            } catch (Exception e) {
                                Log.w(TAG, "Legacy API source " + (i + 1) + " failed: " + e.getMessage());
                                if (i == urls.size() - 1) {
                                    errorMsg = stringFormat("检查更新失败：{0}", e.getMessage());
                                }
                            }
                        }
                    } catch (Exception fallbackError) {
                        Log.e(TAG, "All update sources failed: " + fallbackError.getMessage(), fallbackError);
                        errorMsg = stringFormat("检查更新失败：{0}", fallbackError.getMessage());
                    }
                }

                final UpdateInfo finalResult = result;
                final String finalError = errorMsg;
                safeCallback(callback, finalResult, finalError, null);
            }
        });
    }

    private List<String> buildUpdateUrls(Context context) {
        List<String> urls = new ArrayList<>();
        String customUrl = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_BASE_URL, null);
        if (customUrl != null && !customUrl.equals(HK_BASE_URL)
                && !customUrl.equals(US_BASE_URL)) {
            urls.add(customUrl);
        }

        int source = SettingsManager.getInstance(context).getUpdateSource();
        if (source == SettingsManager.UPDATE_SOURCE_VPS_HK) {
            urls.add(HK_BASE_URL);
            if (!US_BASE_URL.isEmpty() && !US_BASE_URL.equals(HK_BASE_URL)) {
                urls.add(US_BASE_URL);
            }
            urls.add(GITHUB_RELEASES_BASE_URL);
        } else if (source == SettingsManager.UPDATE_SOURCE_VPS_US) {
            urls.add(US_BASE_URL);
            if (!HK_BASE_URL.isEmpty() && !HK_BASE_URL.equals(US_BASE_URL)) {
                urls.add(HK_BASE_URL);
            }
            urls.add(GITHUB_RELEASES_BASE_URL);
        } else if (source == SettingsManager.UPDATE_SOURCE_GITHUB) {
            urls.add(GITHUB_RELEASES_BASE_URL);
            urls.add(HK_BASE_URL);
            if (!US_BASE_URL.isEmpty() && !US_BASE_URL.equals(HK_BASE_URL)) {
                urls.add(US_BASE_URL);
            }
        } else {
            urls.add(HK_BASE_URL);
            if (!US_BASE_URL.isEmpty() && !US_BASE_URL.equals(HK_BASE_URL)) {
                urls.add(US_BASE_URL);
            }
            urls.add(GITHUB_RELEASES_BASE_URL);
        }
        return urls;
    }

    private UpdateInfo checkVersionFile(Context context, String versionJsonUrl, String baseUrl,
                                        LocalVersion localVersion, boolean acceptBeta,
                                        int connectTimeout, int readTimeout) throws Exception {
        JSONObject json;
        boolean fetchedRequestedVersion = true;
        try {
            json = fetchJson(versionJsonUrl, connectTimeout, readTimeout);
        } catch (Exception e) {
            // 请求的版本不存在时：如果本地是beta版本，尝试检查beta版本
            String requestedSuffix = versionJsonUrl.contains("version-release.json") ? "release"
                    : versionJsonUrl.contains("version-beta.json") ? "beta" : "";
            if ("release".equals(requestedSuffix) && isBeta(localVersion.channel, localVersion.versionName)) {
                Log.d(TAG, "Release version not found on " + baseUrl + ", checking beta for local beta user...");
                try {
                    String betaJsonUrl = trimTrailingSlash(baseUrl) + "/version-beta.json";
                    json = fetchJson(betaJsonUrl, connectTimeout, readTimeout);
                    versionJsonUrl = betaJsonUrl;
                    fetchedRequestedVersion = false;
                } catch (Exception betaEx) {
                    Log.w(TAG, "Beta version also not found: " + betaEx.getMessage());
                    throw e;
                }
            } else {
                throw e;
            }
        }

        UpdateInfo info = UpdateInfo.fromJson(json);
        info.setSourceVersionUrl(versionJsonUrl);
        info.setLocalVersion(localVersion.versionCode, localVersion.versionName);
        resolveDownloadUrl(info, json, versionJsonUrl, baseUrl);
        applyUpdatePolicy(info, localVersion, acceptBeta);

        // 如果本地是beta用户但acceptBeta=false，且release没有更新，检查beta版本
        if (!acceptBeta && isBeta(localVersion.channel, localVersion.versionName) && !info.hasUpdate()) {
            Log.d(TAG, "Beta user with acceptBeta=false and no stable update, checking beta version...");
            try {
                String betaJsonUrl = trimTrailingSlash(baseUrl) + "/version-beta.json";
                JSONObject betaJson = fetchJson(betaJsonUrl, connectTimeout, readTimeout);
                UpdateInfo betaInfo = UpdateInfo.fromJson(betaJson);
                betaInfo.setSourceVersionUrl(betaJsonUrl);
                betaInfo.setLocalVersion(localVersion.versionCode, localVersion.versionName);
                resolveDownloadUrl(betaInfo, betaJson, betaJsonUrl, baseUrl);
                applyUpdatePolicy(betaInfo, localVersion, false);

                if (betaInfo.hasUpdate()) {
                    betaInfo.setBetaUpdateBlocked(true);
                    betaInfo.setBetaUpdateOutdated(isOutdatedAgainstLastStable(betaInfo, localVersion));
                    Log.d(TAG, "Beta update available but blocked by user setting");
                    saveLastCheck(context);
                    return betaInfo;
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to check beta version for beta user: " + e.getMessage());
            }
        }

        if (acceptBeta && info.isBetaRelease() && !info.hasUpdate()) {
            Log.d(TAG, "No beta update found for beta user, checking stable release...");
            try {
                String releaseJsonUrl = trimTrailingSlash(baseUrl) + "/version-release.json";
                JSONObject releaseJson = fetchJson(releaseJsonUrl, connectTimeout, readTimeout);
                UpdateInfo releaseInfo = UpdateInfo.fromJson(releaseJson);
                releaseInfo.setSourceVersionUrl(releaseJsonUrl);
                releaseInfo.setLocalVersion(localVersion.versionCode, localVersion.versionName);
                resolveDownloadUrl(releaseInfo, releaseJson, releaseJsonUrl, baseUrl);
                
                if (releaseInfo.getVersionCode() > localVersion.versionCode) {
                    Log.d(TAG, "Stable release available for beta user: " + releaseInfo.getVersionName());
                    releaseInfo.setHasUpdate(true);
                    releaseInfo.setBetaUpdateBlocked(false);
                    releaseInfo.setBetaUpdateOutdated(false);
                    return releaseInfo;
                }
            } catch (Exception e) {
                Log.w(TAG, "Failed to check stable release for beta user: " + e.getMessage());
            }
        }

        saveLastCheck(context);
        return info;
    }

    private UpdateInfo checkLegacyApi(Context context, String baseUrl, LocalVersion localVersion,
                                      boolean acceptBeta, int connectTimeout, int readTimeout) throws Exception {
        String urlStr = stringFormat("{0}/api/update/check?versionCode={1}&platform=android&acceptBeta={2}",
                baseUrl, String.valueOf(localVersion.versionCode), String.valueOf(acceptBeta));
        Log.d(TAG, "Checking legacy update API: " + urlStr);
        JSONObject json = fetchJson(urlStr, connectTimeout, readTimeout);
        UpdateInfo info = UpdateInfo.fromJson(json);
        info.setSourceVersionUrl(urlStr);
        info.setLocalVersion(localVersion.versionCode, localVersion.versionName);
        resolveDownloadUrl(info, json, urlStr, baseUrl);
        applyUpdatePolicy(info, localVersion, acceptBeta);
        saveLastCheck(context);
        return info;
    }

    private JSONObject fetchJson(String urlStr, int connectTimeout, int readTimeout) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setRequestProperty("User-Agent", "GameCenterApp/" + BuildConfig.VERSION_NAME);
            conn.setRequestProperty("Accept", "application/json");
            Log.d(TAG, "Connecting (timeout=" + connectTimeout + "/" + readTimeout + ")...");
            int code = conn.getResponseCode();
            Log.d(TAG, "Response code: " + code);
            if (code != 200) {
                throw new IllegalStateException(stringFormat("服务器返回错误: {0}", String.valueOf(code)));
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            return new JSONObject(sb.toString());
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void resolveDownloadUrl(UpdateInfo info, JSONObject json, String versionJsonUrl, String baseUrl) {
        String downloadUrl = info.getDownloadUrl();
        if (downloadUrl == null || downloadUrl.trim().isEmpty()) {
            String apkName = json.optString("apkName", "").trim();
            if (apkName.isEmpty()) apkName = json.optString("apkFile", "").trim();
            if (apkName.isEmpty()) apkName = "app-debug.apk";
            downloadUrl = resolveRelativeUrl(versionJsonUrl, apkName);
        } else if (!downloadUrl.startsWith("http://") && !downloadUrl.startsWith("https://")) {
            downloadUrl = resolveRelativeUrl(versionJsonUrl, downloadUrl);
        }
        if (downloadUrl == null || downloadUrl.trim().isEmpty()) {
            if (baseUrl.equals(GITHUB_RELEASES_BASE_URL)) {
                downloadUrl = GITHUB_RELEASES_BASE_URL + "/download/" + info.getVersionName() + "/GameCenterApp.apk";
            } else {
                downloadUrl = trimTrailingSlash(baseUrl) + "/app-debug.apk";
            }
        }
        info.setDownloadUrl(downloadUrl);
    }

    private boolean shouldOfferUpdate(UpdateInfo remote, LocalVersion local, boolean acceptBeta) {
        if (remote == null) return false;
        if (remote.isBetaRelease() && !acceptBeta) {
            Log.d(TAG, "Beta update ignored by user setting: " + remote.getVersionName());
            return false;
        }
        if (remote.getVersionCode() > local.versionCode) {
            return true;
        }
        if (remote.getVersionCode() == local.versionCode) {
            boolean localBeta = isBeta(local.channel, local.versionName);
            boolean releaseChanged = !remote.getVersionName().equals(local.versionName);
            if (!remote.isBetaRelease() && localBeta && releaseChanged) {
                return true;
            }
            return remote.isBetaRelease() && acceptBeta && releaseChanged;
        }
        return false;
    }

    private void applyUpdatePolicy(UpdateInfo remote, LocalVersion local, boolean acceptBeta) {
        if (remote == null) return;
        boolean hasVersionUpdate = shouldOfferUpdate(remote, local, true);
        if (remote.isBetaRelease() && !acceptBeta) {
            remote.setHasUpdate(false);
            if (hasVersionUpdate) {
                remote.setBetaUpdateBlocked(true);
                remote.setBetaUpdateOutdated(isOutdatedAgainstLastStable(remote, local));
                Log.d(TAG, "Beta update blocked by user setting: " + remote.getVersionName());
            }
            return;
        }
        remote.setHasUpdate(shouldOfferUpdate(remote, local, acceptBeta));
    }

    private boolean isOutdatedAgainstLastStable(UpdateInfo remote, LocalVersion local) {
        int gap = Math.max(1, remote.getBetaNoticeVersionGap());
        int lastStableCode = remote.getLastStableVersionCode();
        if (lastStableCode > 0) {
            return lastStableCode - local.versionCode >= gap;
        }
        return remote.getVersionCode() - local.versionCode >= gap;
    }

    private LocalVersion readBundledVersion(Context context) {
        LocalVersion version = new LocalVersion();
        version.versionCode = BuildConfig.VERSION_CODE;
        version.versionName = BuildConfig.VERSION_NAME;
        version.channel = BuildConfig.VERSION_CHANNEL;
        try (InputStream input = context.getAssets().open("version.json")) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            JSONObject json = new JSONObject(sb.toString());
            version.versionCode = json.optInt("versionCode", version.versionCode);
            version.versionName = json.optString("versionName", version.versionName);
            String channel = json.optString("channel", "").trim().toLowerCase();
            version.channel = channel.isEmpty()
                    ? (isBeta("", version.versionName) ? "beta" : "stable")
                    : channel;
        } catch (Exception e) {
            Log.d(TAG, "Bundled version.json unavailable, use BuildConfig");
        }
        return version;
    }

    private String buildVersionJsonUrl(String baseUrl, boolean acceptBeta) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        if (trimmed.endsWith(".json")) {
            return trimmed;
        }
        String suffix = acceptBeta ? "beta" : "release";
        return trimTrailingSlash(trimmed) + "/version-" + suffix + ".json";
    }

    private String resolveRelativeUrl(String baseUrl, String path) {
        try {
            return new URL(new URL(baseUrl), path).toString();
        } catch (Exception e) {
            return path;
        }
    }

    private boolean isBeta(String channel, String versionName) {
        String rawChannel = channel == null ? "" : channel.toLowerCase();
        String rawName = versionName == null ? "" : versionName.toLowerCase();
        return rawChannel.contains("beta") || rawChannel.contains("test") || rawName.contains("beta");
    }

    private void saveLastCheck(Context context) {
        long now = System.currentTimeMillis();
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_CHECK, now).apply();
    }

    public void downloadApk(final Context context, final UpdateInfo info, final DownloadCallback callback) {
        isCancelled = false;
        executor.execute(new Runnable() {
            @Override
            public void run() {
                List<String> downloadUrls = buildDownloadUrls(info);
                File apkFile = null;
                String errorMsg = null;
                for (int sourceIndex = 0; sourceIndex < downloadUrls.size(); sourceIndex++) {
                    if (isCancelled) {
                        safeProgress(callback, -3, 0, 0);
                        return;
                    }
                    String currentUrl = downloadUrls.get(sourceIndex);
                    Log.d(TAG, "Attempting download from source " + (sourceIndex + 1) + "/" + downloadUrls.size() + ": " + currentUrl);
                    try {
                        apkFile = downloadFromUrl(context, info, currentUrl, callback);
                        if (apkFile != null && apkFile.exists()) {
                            Log.d(TAG, "Download succeeded from source " + (sourceIndex + 1));
                            // 显示下载完成通知
                            showDownloadCompleteNotification(context, apkFile, info.getVersionName());
                            callbackApkReady(callback, apkFile);
                            return;
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Source " + (sourceIndex + 1) + " failed: " + e.getMessage());
                        if (sourceIndex >= downloadUrls.size() - 1) {
                            errorMsg = stringFormat("下载失败: {0}", e.getMessage());
                        } else {
                            Log.d(TAG, "Switching to next download source...");
                        }
                    }
                }
                if (errorMsg != null) {
                    final String fError = errorMsg;
                    safeProgress(callback, -2, 0, 0);
                    safeCallback2(callback, null, fError);
                } else {
                    safeCallback2(callback, null, "所有下载源均不可用");
                }
            }
        });
    }

    private List<String> buildDownloadUrls(UpdateInfo info) {
        List<String> urls = new ArrayList<>();
        String primaryUrl = info.getDownloadUrl();
        if (primaryUrl != null && !primaryUrl.isEmpty()) {
            urls.add(primaryUrl);
        }
        String githubUrl = GITHUB_RELEASES_BASE_URL + "/download/" + info.getVersionName() + "/GameCenterApp.apk";
        String hkUrl = trimTrailingSlash(HK_BASE_URL) + "/" + extractApkName(primaryUrl);
        String usUrl = US_BASE_URL.isEmpty() ? "" : trimTrailingSlash(US_BASE_URL) + "/" + extractApkName(primaryUrl);
        
        if (!urls.contains(githubUrl)) urls.add(githubUrl);
        if (!urls.contains(hkUrl)) urls.add(hkUrl);
        if (!usUrl.isEmpty() && !urls.contains(usUrl)) urls.add(usUrl);
        return urls;
    }

    private String extractApkName(String url) {
        if (url == null || url.isEmpty()) return "app-debug.apk";
        int idx = url.lastIndexOf('/');
        return idx >= 0 ? url.substring(idx + 1) : "app-debug.apk";
    }

    private File downloadFromUrl(Context context, UpdateInfo info, String downloadUrl, DownloadCallback callback) throws Exception {
        File downloadDir = getDownloadDir(context);
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }
        File apkFile = new File(downloadDir,
                stringFormat("GameCenter_v{0}_{1}.apk",
                        String.valueOf(info.getVersionCode()), info.getVersionName()));
        if (apkFile.exists() && !info.getMd5().isEmpty()) {
            String existingMd5 = computeMd5(apkFile);
            if (info.getMd5().equalsIgnoreCase(existingMd5)) {
                return apkFile;
            }
            apkFile.delete();
        }
        URL url = new URL(downloadUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(FALLBACK_CONNECT_TIMEOUT);
        conn.setReadTimeout(300000);
        conn.setRequestProperty("User-Agent", "GameCenterApp/" + BuildConfig.VERSION_NAME);
        if (isCancelled) {
            conn.disconnect();
            return null;
        }
        long totalSize = conn.getContentLength();
        if (totalSize <= 0 && info.getFileSize() > 0) {
            totalSize = info.getFileSize();
        }
        BufferedInputStream in = new BufferedInputStream(conn.getInputStream());
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(apkFile));
        byte[] buf = new byte[65536];
        long downloaded = 0;
        long lastReportTime = 0;
        int read;
        long speedCheckStartTime = 0;
        long speedCheckBytes = 0;
        boolean speedChecked = false;
        while ((read = in.read(buf)) != -1) {
            if (isCancelled) {
                in.close();
                out.close();
                apkFile.delete();
                conn.disconnect();
                return null;
            }
            out.write(buf, 0, read);
            downloaded += read;
            if (!speedChecked) {
                long now = System.currentTimeMillis();
                if (speedCheckStartTime == 0) {
                    speedCheckStartTime = now;
                    speedCheckBytes = downloaded;
                } else if (now - speedCheckStartTime >= SPEED_CHECK_INTERVAL_MS) {
                    long elapsed = now - speedCheckStartTime;
                    long speed = (downloaded - speedCheckBytes) * 1000 / elapsed;
                    Log.d(TAG, "Download speed: " + (speed / 1024) + " KB/s");
                    if (speed < MIN_DOWNLOAD_SPEED_BYTES_PER_SEC) {
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
            long now = System.currentTimeMillis();
            if (now - lastReportTime >= 80 || read < buf.length) {
                final long fDownloaded = downloaded;
                final long fTotal = totalSize;
                safeProgress(callback, 0, fDownloaded, fTotal);
                // 更新通知进度
                int percent = totalSize > 0 ? (int) (downloaded * 100 / totalSize) : 0;
                showDownloadNotification(context, percent, info.getVersionName());
                lastReportTime = now;
            }
        }
        in.close();
        out.close();
        conn.disconnect();
        if (!info.getMd5().isEmpty()) {
            String actualMd5 = computeMd5(apkFile);
            if (!info.getMd5().equalsIgnoreCase(actualMd5)) {
                apkFile.delete();
                throw new Exception("安装包校验失败");
            }
        }
        return apkFile;
    }

    public File getDownloadDir(Context context) {
        File baseDir = null;
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        }
        if (baseDir == null) {
            baseDir = context.getFilesDir();
        }
        return new File(baseDir, "update");
    }

    public boolean openDownloadDirectory(Context context) {
        File downloadDir = getDownloadDir(context);
        if (!downloadDir.exists() && !downloadDir.mkdirs()) {
            Toast.makeText(context, "无法创建下载目录", Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
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
            try {
                Intent fallback = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
                Toast.makeText(context, "请选择应用目录下的 Download/update 文件夹", Toast.LENGTH_LONG).show();
                return true;
            } catch (Exception ignored) {
                Toast.makeText(context, "下载目录: " + downloadDir.getAbsolutePath(), Toast.LENGTH_LONG).show();
                return false;
            }
        }
    }

    private void callbackApkReady(DownloadCallback callback, File apkFile) {
        if (callback == null) return;
        final File fApk = apkFile;
        sMainHandler.post(new Runnable() {
            @Override
            public void run() {
                callback.onComplete(fApk);
            }
        });
    }

    public boolean installApk(Context context, File apkFile) {
        if (!apkFile.exists()) {
            Toast.makeText(context, "安装包不存在", Toast.LENGTH_SHORT).show();
            return false;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Uri uri = FileProvider.getUriForFile(context,
                    context.getPackageName() + ".update.fileprovider", apkFile);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
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

    public boolean canRequestInstall(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return context.getPackageManager().canRequestPackageInstalls();
        }
        return true;
    }

    public void requestInstallPermission(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Uri uri = Uri.parse(stringFormat("package:{0}", activity.getPackageName()));
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, uri);
            activity.startActivityForResult(intent, requestCode);
        }
    }

    private String computeMd5(File file) {
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
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static final android.os.Handler sMainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private void safeCallback(final UpdateCheckCallback callback, final UpdateInfo info,
                              final String error, final String cancel) {
        if (callback == null) return;
        sMainHandler.post(new Runnable() {
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

    private void safeProgress(final DownloadCallback callback,
                               final int status, final long downloaded, final long total) {
        if (callback == null) return;
        sMainHandler.post(new Runnable() {
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

    private void safeCallback2(final DownloadCallback callback,
                                final File apkFile, final String error) {
        if (callback == null) return;
        sMainHandler.post(new Runnable() {
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

    private static String stringFormat(String template, String... args) {
        String result = template;
        for (int i = 0; i < args.length; i++) {
            result = result.replace(stringConcat("{", String.valueOf(i), "}"),
                    args[i] != null ? args[i] : "");
        }
        return result;
    }

    private static String stringConcat(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) sb.append(p);
        return sb.toString();
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isEmpty()) return "";
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static class LocalVersion {
        int versionCode;
        String versionName;
        String channel;
    }

    public interface UpdateCheckCallback {
        void onResult(UpdateInfo info);
        void onError(String message);
        void onCancelled();
    }

    public interface DownloadCallback {
        void onProgress(long downloaded, long total);
        void onVerifying();
        void onComplete(File apkFile);
        void onError(String message);
        void onCancelled();
    }
}
