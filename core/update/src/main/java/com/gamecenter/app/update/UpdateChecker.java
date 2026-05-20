package com.gamecenter.app.update;

import android.content.Context;
import android.util.Log;

import com.gamecenter.app.update.BuildConfig;
import com.gamecenter.app.SettingsManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.MessageFormat;

import javax.net.ssl.HttpsURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 应用更新检查器，负责从多个更新源检查是否有新版本可用。
 * <p>
 * 支持三个更新源（按优先级排列）：香港 VPS、美国 VPS、GitHub Releases。
 * 当首选源不可用时，自动降级到下一个源，确保用户总能获取到更新信息。
 * </p>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>使用单线程线程池执行网络请求，避免并发检查导致的竞态条件</li>
 *   <li>支持 Beta/稳定版双通道检查，用户可选择是否接受 Beta 更新</li>
 *   <li>即使不接受 Beta 更新，也会检查 Beta 版本用于通知用户存在新版</li>
 *   <li>主源使用较短超时（3s/5s），备用源使用较长超时（15s/30s），兼顾速度与可靠性</li>
 * </ul>
 * </p>
 */
public class UpdateChecker {

    private static final String TAG = "UpdateChecker";

    /** 下载源：香港 VPS（主源） */
    static final String HK_BASE_URL = BuildConfig.SERVER_URL;
    /** 下载源：美国 VPS（备用源） */
    static final String US_BASE_URL = BuildConfig.SERVER_URL_FALLBACK;
    /** 下载源：GitHub Releases（最终备用源） */
    static final String GITHUB_RELEASES_BASE_URL = "https://github.com/3571949306/GameCenterApp/releases/latest";

    /** SharedPreferences 文件名，用于存储更新配置 */
    static final String PREF_NAME = "update_config";
    /** 自定义更新源 URL 的存储键 */
    static final String KEY_BASE_URL = "update_base_url";
    /** 上次检查更新时间的存储键 */
    static final String KEY_LAST_CHECK = "last_check_time";

    /** 主源连接超时（毫秒） */
    static final int PRIMARY_CONNECT_TIMEOUT = 3000;
    /** 主源读取超时（毫秒） */
    static final int PRIMARY_READ_TIMEOUT = 5000;
    /** 备用源连接超时（毫秒），比主源长以应对网络不佳的情况 */
    static final int FALLBACK_CONNECT_TIMEOUT = 15000;
    /** 备用源读取超时（毫秒） */
    static final int FALLBACK_READ_TIMEOUT = 30000;
    /** GitHub 源连接超时（毫秒） */
    static final int GITHUB_CONNECT_TIMEOUT = 5000;
    /** GitHub 源读取超时（毫秒） */
    static final int GITHUB_READ_TIMEOUT = 10000;

    /** 单线程线程池，确保更新检查任务串行执行 */
    private final ExecutorService executor;
    /** 取消标志，volatile 保证多线程可见性 */
    private volatile boolean isCancelled = false;

    /**
     * 构造函数，初始化线程池并注册 SSL 信任主机。
     * 将香港和美国 VPS 的域名注册为信任主机，以便自签名证书的服务器也能正常通信。
     */
    UpdateChecker() {
        executor = Executors.newSingleThreadExecutor();
        SSLHelper.trustUpdateServer(HK_BASE_URL);
        if (!US_BASE_URL.isEmpty()) {
            SSLHelper.trustUpdateServer(US_BASE_URL);
        }
    }

    /**
     * 异步检查应用更新，按优先级依次尝试多个更新源。
     * 首先构建更新源 URL 列表，然后在线程池中依次请求每个源，
     * 直到某个源成功返回结果或所有源均失败。
     *
     * @param context  上下文
     * @param callback 检查结果回调（在调用线程执行，非主线程）
     */
    public void checkUpdate(Context context, final UpdateManager.UpdateCheckCallback callback) {
        isCancelled = false;
        List<String> urls = buildUpdateUrls(context);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                UpdateInfo result = null;
                String errorMsg = null;

                for (int i = 0; i < urls.size(); i++) {
                    if (isCancelled) {
                        if (callback != null) callback.onCancelled();
                        return;
                    }
                    String baseUrl = urls.get(i);
                    // 主源使用较短超时，备用源使用较长超时
                    boolean isPrimary = (i == 0);
                    int connectTimeout = isPrimary ? PRIMARY_CONNECT_TIMEOUT : FALLBACK_CONNECT_TIMEOUT;
                    int readTimeout = isPrimary ? PRIMARY_READ_TIMEOUT : FALLBACK_READ_TIMEOUT;
                    try {
                        Log.d(TAG, "Checking update source " + (i + 1) + "/" + urls.size()
                                + ": " + baseUrl + " (primary=" + isPrimary + ")");
                        LocalVersion localVersion = readBundledVersion(context);
                        boolean acceptBeta = SettingsManager.getInstance(context).isAcceptBetaUpdate();

                        result = checkUpdateFromSource(context, baseUrl, localVersion, acceptBeta,
                                connectTimeout, readTimeout);

                        if (result != null) {
                            Log.d(TAG, "Update check succeeded on source " + (i + 1) + ": " + baseUrl);
                            break;
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Source " + (i + 1) + " (" + baseUrl + ") failed: " + e.getMessage());
                        // 仅在最后一个源也失败时才生成错误消息
                        if (i == urls.size() - 1) {
                            errorMsg = MessageFormat.format("检查更新失败：{0}", e.getMessage());
                        }
                    }
                }

                if (callback != null) {
                    if (errorMsg != null) {
                        callback.onError(errorMsg);
                    } else {
                        callback.onResult(result);
                    }
                }
            }
        });
    }

    /**
     * 从指定更新源检查更新，根据源类型和用户设置决定检查策略。
     * <p>
     * 检查策略：
     * <ul>
     *   <li>GitHub 源：仅检查稳定版</li>
     *   <li>接受 Beta：先查 Beta 版，无更新再查稳定版</li>
     *   <li>不接受 Beta：查稳定版，若稳定版无更新则检查 Beta 版用于通知</li>
     * </ul>
     * </p>
     *
     * @param context        上下文
     * @param baseUrl        更新源基础 URL
     * @param localVersion   本地版本信息
     * @param acceptBeta     用户是否接受 Beta 更新
     * @param connectTimeout 连接超时（毫秒）
     * @param readTimeout    读取超时（毫秒）
     * @return 更新信息对象，可能为 null
     */
    public UpdateInfo checkUpdateFromSource(Context context, String baseUrl, LocalVersion localVersion,
                                           boolean acceptBeta, int connectTimeout, int readTimeout) {
        Log.d(TAG, "Checking update from: " + baseUrl + ", acceptBeta=" + acceptBeta);

        // GitHub 源仅支持稳定版检查
        if (baseUrl.equals(GITHUB_RELEASES_BASE_URL)) {
            return checkGitHubRelease(context, baseUrl, localVersion, acceptBeta, connectTimeout, readTimeout);
        }

        if (acceptBeta) {
            // 用户接受 Beta：优先检查 Beta 版本
            Log.d(TAG, "Checking beta version first...");
            UpdateInfo betaInfo = checkSpecificVersion(context, baseUrl, localVersion, true, connectTimeout, readTimeout);
            if (betaInfo != null && betaInfo.hasUpdate()) {
                Log.d(TAG, "Beta update found: " + betaInfo.getVersionName());
                return betaInfo;
            }
            // Beta 无更新，再查稳定版
            Log.d(TAG, "No beta update, checking stable version...");
            UpdateInfo stableInfo = checkSpecificVersion(context, baseUrl, localVersion, false, connectTimeout, readTimeout);
            if (stableInfo != null && stableInfo.hasUpdate()) {
                Log.d(TAG, "Stable update found: " + stableInfo.getVersionName());
                return stableInfo;
            }
            // 两者都无更新，返回任一非 null 的信息
            return betaInfo != null ? betaInfo : stableInfo;
        } else {
            // 用户不接受 Beta：只查稳定版
            Log.d(TAG, "Only checking stable version...");
            UpdateInfo stableInfo = checkSpecificVersion(context, baseUrl, localVersion, false, connectTimeout, readTimeout);
            if (stableInfo != null) {
                if (!stableInfo.hasUpdate()) {
                    // 稳定版无更新时，额外检查 Beta 版本用于通知用户
                    Log.d(TAG, "No stable update, checking if there's beta update to notify...");
                    try {
                        UpdateInfo betaInfo = checkSpecificVersion(context, baseUrl, localVersion, true, connectTimeout, readTimeout);
                        if (betaInfo != null && betaInfo.hasUpdate()) {
                            // 标记 Beta 更新被用户设置阻止，但保留信息用于 UI 提示
                            Log.d(TAG, "Beta update available but blocked by user setting");
                            betaInfo.setBetaUpdateBlocked(true);
                            betaInfo.setBetaUpdateOutdated(isOutdatedAgainstLastStable(betaInfo, localVersion));
                            betaInfo.setHasUpdate(false);
                            return betaInfo;
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to check beta version for notification: " + e.getMessage());
                    }
                }
                return stableInfo;
            }
            return null;
        }
    }

    /**
     * 从指定源检查特定版本（Beta 或稳定版）的更新信息。
     * 构建版本 JSON URL，发起网络请求，解析结果并应用更新策略。
     *
     * @param context        上下文
     * @param baseUrl        更新源基础 URL
     * @param localVersion   本地版本信息
     * @param checkBeta      是否检查 Beta 版本
     * @param connectTimeout 连接超时（毫秒）
     * @param readTimeout    读取超时（毫秒）
     * @return 更新信息对象；检查失败时返回 null
     */
    public UpdateInfo checkSpecificVersion(Context context, String baseUrl, LocalVersion localVersion,
                                           boolean checkBeta, int connectTimeout, int readTimeout) {
        try {
            String versionJsonUrl = buildVersionJsonUrl(baseUrl, checkBeta);
            Log.d(TAG, "Checking " + (checkBeta ? "beta" : "stable") + " version: " + versionJsonUrl);

            JSONObject json = fetchJson(versionJsonUrl, connectTimeout, readTimeout);
            UpdateInfo info = UpdateInfo.fromJson(json);
            info.setSourceVersionUrl(versionJsonUrl);
            info.setLocalVersion(localVersion.versionCode, localVersion.versionName);
            resolveDownloadUrl(info, json, versionJsonUrl, baseUrl);
            applyUpdatePolicy(info, localVersion, checkBeta);
            saveLastCheck(context);
            return info;
        } catch (Exception e) {
            Log.w(TAG, "Failed to check " + (checkBeta ? "beta" : "stable") + " version: " + e.getMessage());
            return null;
        }
    }

    /**
     * 检查 GitHub Releases 的更新（仅稳定版）。
     * 使用旧版 API 接口格式访问 GitHub 更新端点。
     *
     * @param context        上下文
     * @param baseUrl        GitHub Releases URL
     * @param localVersion   本地版本信息
     * @param acceptBeta     是否接受 Beta（GitHub 源忽略此参数）
     * @param connectTimeout 连接超时（毫秒）
     * @param readTimeout    读取超时（毫秒）
     * @return 更新信息对象；检查失败时返回 null
     */
    public UpdateInfo checkGitHubRelease(Context context, String baseUrl, LocalVersion localVersion,
                                          boolean acceptBeta, int connectTimeout, int readTimeout) {
        try {
            Log.d(TAG, "Checking GitHub release (stable only)...");
            UpdateInfo info = checkLegacyApi(context, baseUrl, localVersion, false, connectTimeout, readTimeout);
            if (info != null) {
                return info;
            }
        } catch (Exception e) {
            Log.w(TAG, "GitHub release check failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * 根据用户配置构建更新源 URL 列表。
     * <p>
     * 构建优先级：
     * <ol>
     *   <li>若用户设置了自定义 URL，优先使用自定义源，其余源作为备用</li>
     *   <li>否则根据用户选择的更新源（香港/美国/GitHub）确定首选源，其余源作为备用</li>
     *   <li>默认优先级：香港 VPS → 美国 VPS → GitHub</li>
     * </ol>
     * 会自动去重，避免同一 URL 出现多次。
     * </p>
     *
     * @param context 上下文，用于读取 SharedPreferences 和用户设置
     * @return 按优先级排列的更新源 URL 列表
     */
    public List<String> buildUpdateUrls(Context context) {
        List<String> urls = new ArrayList<>();

        // 1. 首先检查是否有自定义 URL（用户设置的自定义更新源）
        String customUrl = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_BASE_URL, null);
        if (customUrl != null && !customUrl.trim().isEmpty()) {
            // 如果用户设置了自定义 URL，优先使用，并添加备用源
            urls.add(customUrl.trim());
            Log.d(TAG, "Custom update URL configured: " + customUrl);

            // 添加默认源作为备用（避免自定义 URL 不可用时无法更新）
            if (!HK_BASE_URL.equals(customUrl)) {
                urls.add(HK_BASE_URL);
            }
            if (!US_BASE_URL.isEmpty() && !US_BASE_URL.equals(customUrl) && !US_BASE_URL.equals(HK_BASE_URL)) {
                urls.add(US_BASE_URL);
            }
            urls.add(GITHUB_RELEASES_BASE_URL);
        } else {
            // 2. 没有自定义 URL，根据用户选择的更新源构建列表
            int source = SettingsManager.getInstance(context).getUpdateSource();
            switch (source) {
                case SettingsManager.UPDATE_SOURCE_VPS_HK:
                    urls.add(HK_BASE_URL);
                    if (!US_BASE_URL.isEmpty() && !US_BASE_URL.equals(HK_BASE_URL)) {
                        urls.add(US_BASE_URL);
                    }
                    urls.add(GITHUB_RELEASES_BASE_URL);
                    break;
                case SettingsManager.UPDATE_SOURCE_VPS_US:
                    urls.add(US_BASE_URL);
                    if (!HK_BASE_URL.equals(US_BASE_URL)) {
                        urls.add(HK_BASE_URL);
                    }
                    urls.add(GITHUB_RELEASES_BASE_URL);
                    break;
                case SettingsManager.UPDATE_SOURCE_GITHUB:
                    urls.add(GITHUB_RELEASES_BASE_URL);
                    urls.add(HK_BASE_URL);
                    if (!US_BASE_URL.isEmpty() && !US_BASE_URL.equals(HK_BASE_URL)) {
                        urls.add(US_BASE_URL);
                    }
                    break;
                default:
                    // 默认：香港 VPS → 美国 VPS → GitHub
                    urls.add(HK_BASE_URL);
                    if (!US_BASE_URL.isEmpty() && !US_BASE_URL.equals(HK_BASE_URL)) {
                        urls.add(US_BASE_URL);
                    }
                    urls.add(GITHUB_RELEASES_BASE_URL);
                    break;
            }
        }

        Log.d(TAG, "Build update URLs: " + urls);
        return urls;
    }

    /**
     * 使用旧版 API 接口检查更新。
     * 通过查询参数传递版本号和平台信息，适用于 GitHub Releases 等使用 API 端点的源。
     *
     * @param context        上下文
     * @param baseUrl        更新源基础 URL
     * @param localVersion   本地版本信息
     * @param acceptBeta     是否接受 Beta 更新
     * @param connectTimeout 连接超时（毫秒）
     * @param readTimeout    读取超时（毫秒）
     * @return 更新信息对象
     * @throws Exception 网络请求或解析失败时抛出
     */
    public UpdateInfo checkLegacyApi(Context context, String baseUrl, LocalVersion localVersion,
                                      boolean acceptBeta, int connectTimeout, int readTimeout) throws Exception {
        String urlStr = MessageFormat.format("{0}/api/update/check?versionCode={1}&platform=android&acceptBeta={2}",
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

    /**
     * 发起 HTTP GET 请求获取 JSON 响应。
     * 自动处理 HTTPS 连接的 SSL 配置，设置 User-Agent 和 Accept 头。
     *
     * @param urlStr         请求 URL
     * @param connectTimeout 连接超时（毫秒）
     * @param readTimeout    读取超时（毫秒）
     * @return 解析后的 JSON 对象
     * @throws Exception 网络请求失败、非 200 响应码或 JSON 解析失败时抛出
     */
    public JSONObject fetchJson(String urlStr, int connectTimeout, int readTimeout) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            // 对 HTTPS 连接应用自定义 SSL 配置（信任自签名证书等）
            if (conn instanceof HttpsURLConnection) {
                SSLHelper.applySsl((HttpsURLConnection) conn);
            }
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(connectTimeout);
            conn.setReadTimeout(readTimeout);
            conn.setRequestProperty("User-Agent", "GameCenterApp/" + BuildConfig.VERSION_NAME);
            conn.setRequestProperty("Accept", "application/json");
            Log.d(TAG, "Connecting (timeout=" + connectTimeout + "/" + readTimeout + ")...");
            int code = conn.getResponseCode();
            Log.d(TAG, "Response code: " + code);
            if (code != 200) {
                throw new IllegalStateException(MessageFormat.format("服务器返回错误: {0}", String.valueOf(code)));
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

    /**
     * 解析并设置 APK 下载 URL。
     * <p>
     * 下载 URL 的解析优先级：
     * <ol>
     *   <li>JSON 中直接提供的完整 downloadUrl</li>
     *   <li>基于 JSON 中的 apkName/apkFile 字段拼接相对路径</li>
     *   <li>使用默认 APK 文件名（beta 用 app-beta.apk，stable 用 app-release.apk）</li>
     *   <li>对于 GitHub 源，使用 GitHub Releases 的标准下载路径格式</li>
     * </ol>
     * </p>
     *
     * @param info           更新信息对象，其 downloadUrl 会被设置
     * @param json           原始 JSON 数据
     * @param versionJsonUrl 版本 JSON 的完整 URL，用于解析相对路径
     * @param baseUrl        更新源基础 URL
     */
    public void resolveDownloadUrl(UpdateInfo info, JSONObject json, String versionJsonUrl, String baseUrl) {
        String downloadUrl = info.getDownloadUrl();
        // 下载 URL 为空时，尝试从 JSON 中提取 APK 文件名并拼接
        if (downloadUrl == null || downloadUrl.trim().isEmpty()) {
            String apkName = json.optString("apkName", "").trim();
            if (apkName.isEmpty()) apkName = json.optString("apkFile", "").trim();
            // 兜底：使用默认 APK 文件名
            if (apkName.isEmpty()) apkName = info.isBetaRelease() ? "app-beta.apk" : "app-release.apk";
            downloadUrl = resolveRelativeUrl(versionJsonUrl, apkName);
        } else if (!downloadUrl.startsWith("http://") && !downloadUrl.startsWith("https://")) {
            // 下载 URL 为相对路径时，基于版本 JSON URL 解析为完整 URL
            downloadUrl = resolveRelativeUrl(versionJsonUrl, downloadUrl);
        }
        // 所有方式都无法获取下载 URL 时的最终兜底
        if (downloadUrl == null || downloadUrl.trim().isEmpty()) {
            if (baseUrl.equals(GITHUB_RELEASES_BASE_URL)) {
                // GitHub 源：使用标准 Releases 下载路径
                String tag = info.getVersionName();
                String apkName = info.isBetaRelease() ? "app-beta.apk" : "app-release.apk";
                if (tag == null || tag.isEmpty()) {
                    downloadUrl = "https://github.com/3571949306/GameCenterApp/releases/latest/download/" + apkName;
                } else {
                    downloadUrl = "https://github.com/3571949306/GameCenterApp/releases/download/" + tag + "/" + apkName;
                }
            } else {
                // VPS 源：直接拼接基础 URL + APK 文件名
                downloadUrl = UpdateManager.trimTrailingSlash(baseUrl) + "/" + (info.isBetaRelease() ? "app-beta.apk" : "app-release.apk");
            }
        }
        info.setDownloadUrl(downloadUrl);
    }

    /**
     * 将相对路径解析为基于 baseUrl 的完整 URL。
     * 使用 {@link URL} 的构造函数自动处理相对路径解析。
     *
     * @param baseUrl 基础 URL
     * @param path    相对路径
     * @return 完整 URL；解析失败时返回原始 path
     */
    public String resolveRelativeUrl(String baseUrl, String path) {
        try {
            return new URL(new URL(baseUrl), path).toString();
        } catch (Exception e) {
            return path;
        }
    }

    /**
     * 判断是否应该向用户推荐更新。
     * 综合考虑远程版本号和用户是否接受 Beta 版本。
     *
     * @param remote     远程版本信息
     * @param local      本地版本信息
     * @param acceptBeta 用户是否接受 Beta 更新
     * @return true 表示应该推荐更新
     */
    public boolean shouldOfferUpdate(UpdateInfo remote, LocalVersion local, boolean acceptBeta) {
        if (remote == null) return false;

        // 远程版本号不高于本地版本号，无需更新
        if (remote.getVersionCode() <= local.versionCode) {
            logDebug("No update: remote versionCode " + remote.getVersionCode()
                    + " <= local versionCode " + local.versionCode);
            return false;
        }

        // 远程为 Beta 版本但用户不接受 Beta，不推荐更新
        if (remote.isBetaRelease() && !acceptBeta) {
            logDebug("Beta update ignored by user setting: " + remote.getVersionName());
            return false;
        }

        logDebug("Should offer update: local=" + local.versionCode
                + ", remote=" + remote.getVersionCode());
        return true;
    }

    /**
     * 根据版本比较结果和用户设置，应用更新策略到 UpdateInfo 对象。
     * <p>
     * 策略逻辑：
     * <ul>
     *   <li>远程版本号 > 本地版本号 且 非 Beta 阻止：标记 hasUpdate=true</li>
     *   <li>远程版本号 > 本地版本号 但 Beta 被阻止：标记 hasUpdate=false, betaUpdateBlocked=true</li>
     *   <li>远程版本号 <= 本地版本号：标记 hasUpdate=false</li>
     * </ul>
     * </p>
     *
     * @param remote     远程版本信息（会被修改）
     * @param local      本地版本信息
     * @param acceptBeta 用户是否接受 Beta 更新
     */
    public void applyUpdatePolicy(UpdateInfo remote, LocalVersion local, boolean acceptBeta) {
        if (remote == null) return;

        boolean hasVersionUpdate = remote.getVersionCode() > local.versionCode;

        if (hasVersionUpdate) {
            if (remote.isBetaRelease() && !acceptBeta) {
                // Beta 更新被用户设置阻止，但保留信息用于通知
                remote.setHasUpdate(false);
                remote.setBetaUpdateBlocked(true);
                remote.setBetaUpdateOutdated(isOutdatedAgainstLastStable(remote, local));
                logDebug("Beta update blocked by user setting: " + remote.getVersionName());
            } else {
                remote.setHasUpdate(true);
                logDebug("Update available: local=" + local.versionCode + ", remote=" + remote.getVersionCode());
            }
        } else {
            remote.setHasUpdate(false);
            logDebug("No update: local=" + local.versionCode + ", remote=" + remote.getVersionCode());
        }
    }

    /**
     * 判断 Beta 版本是否已严重落后于最新稳定版。
     * <p>
     * 当本地版本与最新稳定版的差距超过 betaNoticeVersionGap 时，
     * 认为用户当前版本已严重过时，应提示用户升级。
     * 优先使用服务端返回的 lastStableVersionCode，若不可用则用 Beta 版本号估算。
     * </p>
     *
     * @param remote 远程版本信息
     * @param local  本地版本信息
     * @return true 表示本地版本已严重落后
     */
    public boolean isOutdatedAgainstLastStable(UpdateInfo remote, LocalVersion local) {
        int gap = Math.max(1, remote.getBetaNoticeVersionGap());
        int lastStableCode = remote.getLastStableVersionCode();
        if (lastStableCode > 0) {
            // 有服务端返回的最新稳定版号，直接比较
            return lastStableCode - local.versionCode >= gap;
        }
        // 无稳定版号时，用 Beta 版本号估算差距
        return remote.getVersionCode() - local.versionCode >= gap;
    }

    /**
     * 读取本地版本信息。
     * <p>
     * 优先从 assets/version.json 读取（可热更新版本号），
     * 若读取失败则回退到 BuildConfig 中的编译时版本号。
     * 渠道信息通过 channel 字段或版本名中是否包含 "beta" 来判断。
     * </p>
     *
     * @param context 上下文
     * @return 本地版本信息对象
     */
    public LocalVersion readBundledVersion(Context context) {
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
            // version.json 中的值覆盖 BuildConfig 的默认值
            version.versionCode = json.optInt("versionCode", version.versionCode);
            version.versionName = json.optString("versionName", version.versionName);
            String channel = json.optString("channel", "").trim().toLowerCase();
            // 若未指定渠道，根据版本名推断
            version.channel = channel.isEmpty()
                    ? (isBeta("", version.versionName) ? "beta" : "stable")
                    : channel;
        } catch (Exception e) {
            Log.d(TAG, "Bundled version.json unavailable, use BuildConfig");
        }
        return version;
    }

    /**
     * 构建版本 JSON 文件的完整 URL。
     * <p>
     * 根据基础 URL 和是否检查 Beta 版本，拼接出对应的版本 JSON 文件路径。
     * 若基础 URL 已以 .json 结尾，则直接返回。
     * </p>
     *
     * @param baseUrl    基础 URL
     * @param acceptBeta 是否检查 Beta 版本
     * @return 版本 JSON 文件的完整 URL
     */
    public String buildVersionJsonUrl(String baseUrl, boolean acceptBeta) {
        String trimmed = baseUrl == null ? "" : baseUrl.trim();
        // 如果 URL 已指向 JSON 文件，直接返回
        if (trimmed.endsWith(".json")) {
            return trimmed;
        }
        String suffix = acceptBeta ? "beta" : "release";
        return UpdateManager.trimTrailingSlash(trimmed) + "/version-" + suffix + ".json";
    }

    /**
     * 判断给定的渠道或版本名是否为 Beta 版本。
     * 通过检查渠道标识和版本名中是否包含 "beta" 或 "test" 关键字来判断。
     *
     * @param channel     渠道标识
     * @param versionName 版本名称
     * @return true 表示为 Beta 版本
     */
    public boolean isBeta(String channel, String versionName) {
        String rawChannel = channel == null ? "" : channel.toLowerCase();
        String rawName = versionName == null ? "" : versionName.toLowerCase();
        return rawChannel.contains("beta") || rawChannel.contains("test") || rawName.contains("beta");
    }

    private static void logDebug(String message) {
        try {
            Log.d(TAG, message);
        } catch (Throwable ignored) {
            // Android Log is unavailable in local JVM unit tests.
        }
    }

    /**
     * 保存本次检查更新的时间戳到 SharedPreferences。
     *
     * @param context 上下文
     */
    public void saveLastCheck(Context context) {
        long now = System.currentTimeMillis();
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putLong(KEY_LAST_CHECK, now).apply();
    }

    /**
     * 获取当前配置的更新源基础 URL。
     * 优先返回用户自定义的 URL，若无则返回默认的香港 VPS URL。
     *
     * @param context 上下文
     * @return 基础 URL
     */
    public String getBaseUrl(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_BASE_URL, HK_BASE_URL);
    }

    /**
     * 设置更新源基础 URL，同时将该 URL 的主机注册为 SSL 信任主机。
     *
     * @param context 上下文
     * @param baseUrl 新的基础 URL
     */
    public void setBaseUrl(Context context, String baseUrl) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_BASE_URL, baseUrl).apply();
        SSLHelper.trustUpdateServer(baseUrl);
    }

    /**
     * 取消正在进行的更新检查。
     * 设置取消标志位，子线程中的检查循环会在下一次迭代时退出。
     */
    void cancel() {
        isCancelled = true;
    }

    /**
     * 本地版本信息内部类。
     * 封装了版本号、版本名和渠道信息，用于与远程版本进行比较。
     */
    static class LocalVersion {
        /** 版本号（整数），用于版本比较 */
        int versionCode;
        /** 版本名称（如 "1.2.0"），用于展示 */
        String versionName;
        /** 发布渠道（"stable" 或 "beta"） */
        String channel;
    }
}
