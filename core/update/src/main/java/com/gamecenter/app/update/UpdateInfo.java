package com.gamecenter.app.update;

import org.json.JSONObject;

/**
 * 更新信息数据模型，封装从服务端获取的版本更新详情。
 * <p>
 * 包含远程版本信息（版本号、版本名、下载地址、更新日志等）和本地版本信息，
 * 以及更新策略相关的标志位（是否强制更新、Beta 是否被阻止等）。
 * </p>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>JSON 解析兼容多种字段名（如 downloadUrl/apkUrl/url），适配不同服务端 API</li>
 *   <li>渠道信息通过多级回退策略确定：channel 字段 → releaseChannel 字段 → beta/isBeta 布尔值 → 版本名推断</li>
 *   <li>Beta 通知版本差距（betaNoticeVersionGap）默认为 3，可由服务端自定义</li>
 * </ul>
 * </p>
 */
public class UpdateInfo {

    /** 是否有可用更新 */
    private boolean hasUpdate;
    /** 远程版本号（整数），用于版本比较 */
    private int versionCode;
    /** 远程版本名称（如 "1.2.0"），用于展示 */
    private String versionName;
    /** APK 下载 URL */
    private String downloadUrl;
    /** 更新日志 */
    private String changelog;
    /** 是否强制更新 */
    private boolean forceUpdate;
    /** APK 文件大小（字节） */
    private long fileSize;
    /** APK 文件 MD5 校验值 */
    private String md5;
    /** 发布渠道（"stable" 或 "beta"） */
    private String channel;
    /** 是否为 Beta 版本 */
    private boolean betaRelease;
    /** 本地版本号 */
    private int localVersionCode;
    /** 本地版本名称 */
    private String localVersionName;
    /** 版本 JSON 的来源 URL */
    private String sourceVersionUrl;
    /** 最新稳定版版本号（服务端返回，用于 Beta 过时判断） */
    private int lastStableVersionCode;
    /** 最新稳定版版本名称 */
    private String lastStableVersionName;
    /** Beta 通知版本差距阈值（默认 3） */
    private int betaNoticeVersionGap;
    /** Beta 更新是否被用户设置阻止 */
    private boolean betaUpdateBlocked;
    /** 本地版本是否已严重落后于最新稳定版（仅 Beta 被阻止时有意义） */
    private boolean betaUpdateOutdated;

    /**
     * 从 JSON 对象解析更新信息。
     * <p>
     * 兼容多种 JSON 字段名格式：
     * <ul>
     *   <li>下载 URL：downloadUrl / apkUrl / url</li>
     *   <li>最新稳定版版本号：lastStableVersionCode / stableVersionCode / releaseVersionCode</li>
     *   <li>最新稳定版版本名：lastStableVersionName / stableVersionName / releaseVersionName</li>
     * </ul>
     * </p>
     *
     * @param json 服务端返回的 JSON 对象
     * @return 解析后的 UpdateInfo 实例
     */
    public static UpdateInfo fromJson(JSONObject json) {
        UpdateInfo info = new UpdateInfo();
        info.hasUpdate = json.optBoolean("hasUpdate", false);
        info.versionCode = json.optInt("versionCode", 0);
        info.versionName = json.optString("versionName", "");
        // 下载 URL 兼容多种字段名
        info.downloadUrl = firstNonEmpty(json.optString("downloadUrl", ""),
                json.optString("apkUrl", ""), json.optString("url", ""));
        info.changelog = json.optString("changelog", "");
        info.forceUpdate = json.optBoolean("forceUpdate", false);
        info.fileSize = json.optLong("fileSize", 0);
        info.md5 = json.optString("md5", "");
        info.channel = resolveChannel(json, info.versionName);
        info.betaRelease = "beta".equals(info.channel);
        // 最新稳定版版本号兼容多种字段名
        info.lastStableVersionCode = firstPositiveInt(json,
                "lastStableVersionCode", "stableVersionCode", "releaseVersionCode");
        info.lastStableVersionName = firstNonEmpty(json.optString("lastStableVersionName", ""),
                json.optString("stableVersionName", ""), json.optString("releaseVersionName", ""));
        info.betaNoticeVersionGap = json.optInt("betaNoticeVersionGap", 3);
        return info;
    }

    /** 是否有可用更新 */
    public boolean hasUpdate() { return hasUpdate; }

    /** 获取远程版本号 */
    public int getVersionCode() { return versionCode; }

    /** 获取远程版本名称 */
    public String getVersionName() { return versionName; }

    /** 获取 APK 下载 URL */
    public String getDownloadUrl() { return downloadUrl; }

    /** 获取更新日志 */
    public String getChangelog() { return changelog; }

    /** 是否强制更新 */
    public boolean isForceUpdate() { return forceUpdate; }

    /** 获取 APK 文件大小（字节） */
    public long getFileSize() { return fileSize; }

    /** 获取 APK 文件 MD5 校验值 */
    public String getMd5() { return md5; }

    /**
     * 获取发布渠道。
     * 默认返回 "stable"，仅在 channel 为空时使用默认值。
     */
    public String getChannel() { return channel == null || channel.isEmpty() ? "stable" : channel; }

    /** 是否为 Beta 版本 */
    public boolean isBetaRelease() { return betaRelease; }

    /** 获取本地版本号 */
    public int getLocalVersionCode() { return localVersionCode; }

    /** 获取本地版本名称，null 安全 */
    public String getLocalVersionName() { return localVersionName == null ? "" : localVersionName; }

    /** 获取版本 JSON 来源 URL，null 安全 */
    public String getSourceVersionUrl() { return sourceVersionUrl == null ? "" : sourceVersionUrl; }

    /** 获取最新稳定版版本号 */
    public int getLastStableVersionCode() { return lastStableVersionCode; }

    /** 获取最新稳定版版本名称，null 安全 */
    public String getLastStableVersionName() {
        return lastStableVersionName == null ? "" : lastStableVersionName;
    }

    /**
     * 获取 Beta 通知版本差距阈值。
     * 保证返回值至少为 1，避免除零或无效比较。
     */
    public int getBetaNoticeVersionGap() {
        return betaNoticeVersionGap <= 0 ? 3 : betaNoticeVersionGap;
    }

    /** Beta 更新是否被用户设置阻止 */
    public boolean isBetaUpdateBlocked() { return betaUpdateBlocked; }

    /** 本地版本是否已严重落后于最新稳定版 */
    public boolean isBetaUpdateOutdated() { return betaUpdateOutdated; }

    /**
     * 获取渠道的中文显示标签。
     * Beta 版本显示"测试版"，稳定版显示"正式版"。
     */
    public String getChannelLabel() {
        return isBetaRelease() ? "测试版" : "正式版";
    }

    /** 设置是否有可用更新 */
    public void setHasUpdate(boolean hasUpdate) { this.hasUpdate = hasUpdate; }

    /** 设置 APK 下载 URL，null 安全 */
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl == null ? "" : downloadUrl; }

    /** 设置 Beta 更新是否被阻止 */
    public void setBetaUpdateBlocked(boolean betaUpdateBlocked) {
        this.betaUpdateBlocked = betaUpdateBlocked;
    }

    /** 设置本地版本是否已严重落后 */
    public void setBetaUpdateOutdated(boolean betaUpdateOutdated) {
        this.betaUpdateOutdated = betaUpdateOutdated;
    }

    /**
     * 设置本地版本信息。
     *
     * @param versionCode 本地版本号
     * @param versionName 本地版本名称
     */
    public void setLocalVersion(int versionCode, String versionName) {
        this.localVersionCode = versionCode;
        this.localVersionName = versionName;
    }

    /** 设置版本 JSON 来源 URL，null 安全 */
    public void setSourceVersionUrl(String sourceVersionUrl) {
        this.sourceVersionUrl = sourceVersionUrl == null ? "" : sourceVersionUrl;
    }

    /**
     * 获取格式化后的文件大小字符串。
     * 自动选择合适的单位（B/KB/MB），未知大小时返回"未知"。
     *
     * @return 格式化后的文件大小字符串
     */
    public String getFileSizeFormatted() {
        if (fileSize <= 0) return "未知";
        if (fileSize < 1024) return fileSize + " B";
        if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
        return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
    }

    /**
     * 解析发布渠道，使用多级回退策略。
     * <p>
     * 回退顺序：
     * <ol>
     *   <li>JSON 中的 "channel" 字段</li>
     *   <li>JSON 中的 "releaseChannel" 字段</li>
     *   <li>JSON 中的 "beta" 或 "isBeta" 布尔值</li>
     *   <li>版本名中是否包含 "beta" 关键字</li>
     * </ol>
     * 最终将包含 "beta" 或 "test" 的渠道归为 "beta"，其余归为 "stable"。
     * </p>
     *
     * @param json        服务端返回的 JSON 对象
     * @param versionName 版本名称，用于回退推断
     * @return 渠道标识（"beta" 或 "stable"）
     */
    private static String resolveChannel(JSONObject json, String versionName) {
        String raw = json.optString("channel", "").trim().toLowerCase();
        if (raw.isEmpty()) {
            raw = json.optString("releaseChannel", "").trim().toLowerCase();
        }
        if (raw.isEmpty() && (json.optBoolean("beta", false) || json.optBoolean("isBeta", false))) {
            raw = "beta";
        }
        if (raw.isEmpty()) {
            raw = versionName != null && versionName.toLowerCase().contains("beta") ? "beta" : "stable";
        }
        return raw.contains("beta") || raw.contains("test") ? "beta" : "stable";
    }

    /**
     * 返回第一个非空字符串值。
     * 用于兼容多种 JSON 字段名格式，按优先级依次尝试。
     *
     * @param values 候选字符串列表
     * @return 第一个非空且非空白的字符串；全部为空时返回空字符串
     */
    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    /**
     * 从 JSON 对象中获取第一个正整数值。
     * 用于兼容多种 JSON 字段名格式，按优先级依次尝试。
     *
     * @param json JSON 对象
     * @param keys 候选键名列表
     * @return 第一个正整数值；全部为零或不存在时返回 0
     */
    private static int firstPositiveInt(JSONObject json, String... keys) {
        for (String key : keys) {
            int value = json.optInt(key, 0);
            if (value > 0) {
                return value;
            }
        }
        return 0;
    }
}
