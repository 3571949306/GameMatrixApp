package com.gamecenter.app.update;

import org.json.JSONObject;

public class UpdateInfo {

    private boolean hasUpdate;
    private int versionCode;
    private String versionName;
    private String downloadUrl;
    private String changelog;
    private boolean forceUpdate;
    private long fileSize;
    private String md5;
    private String channel;
    private boolean betaRelease;
    private int localVersionCode;
    private String localVersionName;
    private String sourceVersionUrl;
    private int lastStableVersionCode;
    private String lastStableVersionName;
    private int betaNoticeVersionGap;
    private boolean betaUpdateBlocked;
    private boolean betaUpdateOutdated;

    public static UpdateInfo fromJson(JSONObject json) {
        UpdateInfo info = new UpdateInfo();
        info.hasUpdate = json.optBoolean("hasUpdate", false);
        info.versionCode = json.optInt("versionCode", 0);
        info.versionName = json.optString("versionName", "");
        info.downloadUrl = firstNonEmpty(json.optString("downloadUrl", ""),
                json.optString("apkUrl", ""), json.optString("url", ""));
        info.changelog = json.optString("changelog", "");
        info.forceUpdate = json.optBoolean("forceUpdate", false);
        info.fileSize = json.optLong("fileSize", 0);
        info.md5 = json.optString("md5", "");
        info.channel = resolveChannel(json, info.versionName);
        info.betaRelease = "beta".equals(info.channel);
        info.lastStableVersionCode = firstPositiveInt(json,
                "lastStableVersionCode", "stableVersionCode", "releaseVersionCode");
        info.lastStableVersionName = firstNonEmpty(json.optString("lastStableVersionName", ""),
                json.optString("stableVersionName", ""), json.optString("releaseVersionName", ""));
        info.betaNoticeVersionGap = json.optInt("betaNoticeVersionGap", 3);
        return info;
    }

    public boolean hasUpdate() { return hasUpdate; }

    public int getVersionCode() { return versionCode; }

    public String getVersionName() { return versionName; }

    public String getDownloadUrl() { return downloadUrl; }

    public String getChangelog() { return changelog; }

    public boolean isForceUpdate() { return forceUpdate; }

    public long getFileSize() { return fileSize; }

    public String getMd5() { return md5; }

    public String getChannel() { return channel == null || channel.isEmpty() ? "stable" : channel; }

    public boolean isBetaRelease() { return betaRelease; }

    public int getLocalVersionCode() { return localVersionCode; }

    public String getLocalVersionName() { return localVersionName == null ? "" : localVersionName; }

    public String getSourceVersionUrl() { return sourceVersionUrl == null ? "" : sourceVersionUrl; }

    public int getLastStableVersionCode() { return lastStableVersionCode; }

    public String getLastStableVersionName() {
        return lastStableVersionName == null ? "" : lastStableVersionName;
    }

    public int getBetaNoticeVersionGap() {
        return betaNoticeVersionGap <= 0 ? 3 : betaNoticeVersionGap;
    }

    public boolean isBetaUpdateBlocked() { return betaUpdateBlocked; }

    public boolean isBetaUpdateOutdated() { return betaUpdateOutdated; }

    public String getChannelLabel() {
        return isBetaRelease() ? "测试版" : "正式版";
    }

    public void setHasUpdate(boolean hasUpdate) { this.hasUpdate = hasUpdate; }

    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl == null ? "" : downloadUrl; }

    public void setBetaUpdateBlocked(boolean betaUpdateBlocked) {
        this.betaUpdateBlocked = betaUpdateBlocked;
    }

    public void setBetaUpdateOutdated(boolean betaUpdateOutdated) {
        this.betaUpdateOutdated = betaUpdateOutdated;
    }

    public void setLocalVersion(int versionCode, String versionName) {
        this.localVersionCode = versionCode;
        this.localVersionName = versionName;
    }

    public void setSourceVersionUrl(String sourceVersionUrl) {
        this.sourceVersionUrl = sourceVersionUrl == null ? "" : sourceVersionUrl;
    }

    public String getFileSizeFormatted() {
        if (fileSize <= 0) return "未知";
        if (fileSize < 1024) return fileSize + " B";
        if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
        return String.format("%.1f MB", fileSize / (1024.0 * 1024.0));
    }

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

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

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
