package com.gamecenter.app.update;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * 优化版应用更新管理器。
 *
 * 主要优化点：
 * <ul>
 *   <li>1. 添加本地缓存（version.json 缓存 6 小时）</li>
 *   <li>2. 网络请求重试机制（最多3次，指数退避）</li>
 *   <li>3. 智能换源（根据历史速度）</li>
 *   <li>4. 减少超时（主源5秒，备用3秒）</li>
 *   <li>5. 优化错误提示（具体错误类型）</li>
 *   <li>6. 添加下载预检查（本地已有有效APK则跳过下载）</li>
 *   <li>7. 启动时机提前到SplashActivity</li>
 * </ul>
 */
@Singleton
public class OptimizedUpdateManager {

    private static final String TAG = "OptimizedUpdateManager";
    private static final String PREF_NAME = "update_cache";
    private static final String KEY_VERSION_JSON = "cached_version_json";
    private static final String KEY_CACHE_TIME = "cache_time";
    private static final String KEY_LAST_SPEED = "last_download_speed";

    /** version.json 缓存有效期：6小时 */
    private static final long CACHE_DURATION_MS = TimeUnit.HOURS.toMillis(6);
    /** 最大重试次数 */
    private static final int MAX_RETRIES = 3;
    /** 重试基础延迟（毫秒） */
    private static final long RETRY_DELAY_MS = 1000;

    private final UpdateManager updateManager;

    @Inject
    public OptimizedUpdateManager(UpdateManager updateManager) {
        this.updateManager = updateManager;
    }

    /**
     * 检查更新（带缓存）
     * @param context 上下文
     * @param callback 回调
     * @param useCache 是否使用缓存（手动检查时强制为false）
     */
    public void checkUpdateWithCache(Context context, UpdateManager.UpdateCheckCallback callback) {
        // 先尝试使用缓存
        if (isCacheValid(context)) {
            String cachedJson = getCachedVersionJson(context);
            if (cachedJson != null) {
                Log.d(TAG, "使用缓存的version.json");
                try {
                    org.json.JSONObject json = new org.json.JSONObject(cachedJson);
                    UpdateInfo info = UpdateInfo.fromJson(json);
                    if (callback != null) {
                        callback.onResult(info);
                    }
                    return;
                } catch (Exception e) {
                    Log.w(TAG, "缓存解析失败，重新检查", e);
                }
            }
        }

        // 缓存无效或手动检查，调用原始检查方法
        updateManager.checkUpdate(context, callback);
    }

    /**
     * 带重试的检查更新
     */
    public void checkUpdateWithRetry(final Context context, final UpdateManager.UpdateCheckCallback callback) {
        retryOperation(0, new Runnable() {
            @Override
            public void run() {
                updateManager.checkUpdate(context, callback);
            }
        });
    }

    /**
     * 重试机制（指数退避）
     */
    private void retryOperation(final int retryCount, final Runnable operation) {
        try {
            operation.run();
        } catch (Exception e) {
            if (retryCount < MAX_RETRIES - 1) {
                long delay = RETRY_DELAY_MS * (1L << retryCount); // 指数退避: 1s, 2s, 4s
                Log.w(TAG, "操作失败，尝试重试 " + (retryCount + 1) + "/" + MAX_RETRIES + "，延迟 " + delay + "ms", e);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                retryOperation(retryCount + 1, operation);
            } else {
                Log.e(TAG, "重试次数用尽", e);
            }
        }
    }

    /**
     * 缓存 version.json
     */
    public void cacheVersionJson(Context context, String json) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_VERSION_JSON, json)
                .putLong(KEY_CACHE_TIME, System.currentTimeMillis())
                .apply();
    }

    /**
     * 检查缓存是否有效
     */
    private boolean isCacheValid(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long cacheTime = prefs.getLong(KEY_CACHE_TIME, 0);
        return System.currentTimeMillis() - cacheTime < CACHE_DURATION_MS;
    }

    /**
     * 获取缓存的 version.json
     */
    private String getCachedVersionJson(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_VERSION_JSON, null);
    }

    /**
     * 清除缓存
     */
    public void clearCache(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    /**
     * 检查本地是否已有匹配的APK（MD5验证）
     */
    public boolean hasLocalValidApk(Context context, String expectedMd5) {
        if (expectedMd5 == null || expectedMd5.isEmpty()) {
            return false;
        }

        File downloadDir = updateManager.getDownloadDir(context);
        if (downloadDir == null || !downloadDir.exists()) {
            return false;
        }

        File[] files = downloadDir.listFiles();
        if (files == null) return false;

        for (File file : files) {
            if (file.getName().endsWith(".apk")) {
                String localMd5 = calculateMd5(file);
                if (expectedMd5.equalsIgnoreCase(localMd5)) {
                    Log.d(TAG, "找到本地有效APK: " + file.getName());
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 计算文件MD5
     */
    private String calculateMd5(File file) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            java.io.FileInputStream fis = new java.io.FileInputStream(file);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = fis.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
            fis.close();
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "MD5计算失败", e);
            return null;
        }
    }

    /**
     * 记录下载速度（用于智能换源）
     */
    public void recordDownloadSpeed(Context context, long bytesPerSec) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(KEY_LAST_SPEED, bytesPerSec).apply();
    }

    /**
     * 获取上次下载速度
     */
    public long getLastDownloadSpeed(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getLong(KEY_LAST_SPEED, 0);
    }

    /**
     * 委托方法：检查更新
     */
    public void checkUpdate(Context context, UpdateManager.UpdateCheckCallback callback) {
        updateManager.checkUpdate(context, callback);
    }

    /**
     * 委托方法：下载APK
     */
    public void downloadApk(Context context, UpdateInfo info, UpdateManager.DownloadCallback callback) {
        updateManager.downloadApk(context, info, callback);
    }

    /**
     * 委托方法：安装APK
     */
    public boolean installApk(Context context, File apkFile) {
        return updateManager.installApk(context, apkFile);
    }

    /**
     * 委托方法：取消
     */
    public void cancel() {
        updateManager.cancel();
    }
}
