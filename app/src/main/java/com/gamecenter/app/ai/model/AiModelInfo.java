package com.gamecenter.app.ai.model;

import org.json.JSONObject;

public final class AiModelInfo {
    public final String id;
    public final String name;
    public final String runtime;
    public final String version;
    public final String fileName;
    public final String downloadUrl;
    public final String upstreamUrl;
    public final String licenseUrl;
    public final String sha256;
    public final long sizeBytes;
    public final long estimatedPeakMemoryBytes;
    public final int minSdk;
    public final int minRamMb;
    public final boolean enabled;
    public final String note;

    private AiModelInfo(JSONObject json) {
        id = json.optString("id", "");
        name = json.optString("name", id);
        runtime = json.optString("runtime", "");
        version = json.optString("version", "");
        fileName = json.optString("fileName", id + ".task");
        downloadUrl = json.optString("downloadUrl", "");
        upstreamUrl = json.optString("upstreamUrl", "");
        licenseUrl = json.optString("licenseUrl", "");
        sha256 = json.optString("sha256", "");
        sizeBytes = json.optLong("sizeBytes", 0);
        estimatedPeakMemoryBytes = json.optLong("estimatedPeakMemoryBytes", 0);
        minSdk = json.optInt("minSdk", 24);
        minRamMb = json.optInt("minRamMb", 2048);
        enabled = json.optBoolean("enabled", false);
        note = json.optString("note", "");
    }

    public static AiModelInfo fromJson(JSONObject json) {
        return new AiModelInfo(json);
    }
}
