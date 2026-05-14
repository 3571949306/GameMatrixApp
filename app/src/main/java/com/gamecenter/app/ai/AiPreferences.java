package com.gamecenter.app.ai;

import android.content.Context;
import android.content.SharedPreferences;

import com.gamecenter.app.ai.data.AiProviderConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 偏好设置 — 存储 AI 功能相关的用户偏好。
 */
public class AiPreferences {

    private static final String PREFS_NAME = "ai_settings";
    private static final String KEY_SELECTED_PROVIDER = "selected_provider";
    private static final String KEY_SELECTED_MODEL = "selected_model";
    private static final String KEY_USE_LOCAL_FIRST = "use_local_first";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_LOCAL_MODEL = "local_model";
    private static final String KEY_HISTORY_MAX = "history_max";
    private static final String KEY_FREE_DAILY_LIMIT = "free_daily_limit";
    private static final String KEY_USED_TODAY = "used_today";
    private static final String KEY_LAST_RESET_DATE = "last_reset_date";
    private static final String KEY_GEMMA_NOTICE_ACCEPTED_VERSION = "gemma_notice_accepted_version";
    private static final String KEY_GEMMA_NOTICE_ACCEPTED_AT = "gemma_notice_accepted_at";

    private final SharedPreferences prefs;

    public AiPreferences(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getSelectedProvider() {
        return prefs.getString(KEY_SELECTED_PROVIDER, "DeepSeek");
    }

    public void setSelectedProvider(String provider) {
        prefs.edit().putString(KEY_SELECTED_PROVIDER, provider).apply();
    }

    public String getSelectedModel() {
        return prefs.getString(KEY_SELECTED_MODEL, "deepseek-chat");
    }

    public void setSelectedModel(String model) {
        prefs.edit().putString(KEY_SELECTED_MODEL, model).apply();
    }

    public boolean isLocalFirst() {
        return prefs.getBoolean(KEY_USE_LOCAL_FIRST, true);
    }

    public void setLocalFirst(boolean localFirst) {
        prefs.edit().putBoolean(KEY_USE_LOCAL_FIRST, localFirst).apply();
    }

    public String getApiKey() {
        return prefs.getString(KEY_API_KEY, "");
    }

    public void setApiKey(String apiKey) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply();
    }

    public String getLocalModel() {
        return prefs.getString(KEY_LOCAL_MODEL, "on-device");
    }

    public void setLocalModel(String model) {
        prefs.edit().putString(KEY_LOCAL_MODEL, model).apply();
    }

    public boolean hasAcceptedGemmaNotice(String noticeVersion) {
        return noticeVersion != null
                && noticeVersion.equals(prefs.getString(KEY_GEMMA_NOTICE_ACCEPTED_VERSION, ""));
    }

    public void acceptGemmaNotice(String noticeVersion) {
        prefs.edit()
                .putString(KEY_GEMMA_NOTICE_ACCEPTED_VERSION, noticeVersion)
                .putLong(KEY_GEMMA_NOTICE_ACCEPTED_AT, System.currentTimeMillis())
                .apply();
    }

    public long getGemmaNoticeAcceptedAt() {
        return prefs.getLong(KEY_GEMMA_NOTICE_ACCEPTED_AT, 0L);
    }

    public int getHistoryMax() {
        return prefs.getInt(KEY_HISTORY_MAX, 50);
    }

    public void setHistoryMax(int max) {
        prefs.edit().putInt(KEY_HISTORY_MAX, max).apply();
    }

    public int getFreeDailyLimit() {
        return prefs.getInt(KEY_FREE_DAILY_LIMIT, 20);
    }

    public void setFreeDailyLimit(int limit) {
        prefs.edit().putInt(KEY_FREE_DAILY_LIMIT, limit).apply();
    }

    public int getUsedInDay() {
        // 检查日期重置
        long today = System.currentTimeMillis() / 86400000L;
        long lastReset = prefs.getLong(KEY_LAST_RESET_DATE, 0);
        if (today != lastReset) {
            prefs.edit().putLong(KEY_LAST_RESET_DATE, today).putInt(KEY_USED_TODAY, 0).apply();
            return 0;
        }
        return prefs.getInt(KEY_USED_TODAY, 0);
    }

    public void incrementUsage() {
        long today = System.currentTimeMillis() / 86400000L;
        prefs.edit()
                .putLong(KEY_LAST_RESET_DATE, today)
                .putInt(KEY_USED_TODAY, getUsedInDay() + 1)
                .apply();
    }

    public boolean hasFreeQuota() {
        return getUsedInDay() < getFreeDailyLimit();
    }

    /**
     * 获取可用的提供商列表（本地 + 云端）。
     * 所有提供商均使用 OpenAI 兼容接口格式。
     */
    public static List<AiProviderConfig> getAvailableProviders(Context context) {
        List<AiProviderConfig> list = new ArrayList<>();
        String apiKey = new AiPreferences(context).getApiKey();
        boolean hasKey = !apiKey.isEmpty();

        // ---- 免费 / 本地 ----
        list.add(new AiProviderConfig(
                "本地", "on-device", "",
                "", true, true, 2000, 0));

        // ---- 国外 ----
        list.add(AiProviderConfig.openAIConfig(apiKey).withEnabled(hasKey));
        // 可加 Claude / Gemini 等

        // ---- DeepSeek（最具性价比的国产 API） ----
        list.add(AiProviderConfig.deepseekConfig(apiKey).withEnabled(hasKey));
        list.add(AiProviderConfig.deepseekReasonerConfig(apiKey).withEnabled(hasKey));

        // ---- 阿里云通义千问 ----
        list.add(AiProviderConfig.aliyunTurboConfig(apiKey).withEnabled(hasKey));
        list.add(AiProviderConfig.aliyunConfig(apiKey).withEnabled(hasKey));
        list.add(AiProviderConfig.aliyunMaxConfig(apiKey).withEnabled(hasKey));

        // ---- 硅基流动（多种开源模型，极低价） ----
        list.add(AiProviderConfig.siliconFlowDeepSeekConfig(apiKey).withEnabled(hasKey));
        list.add(AiProviderConfig.siliconFlowQwenConfig(apiKey).withEnabled(hasKey));

        // ---- 智谱 AI ----
        list.add(AiProviderConfig.zhipuFlashConfig(apiKey).withEnabled(hasKey));
        list.add(AiProviderConfig.zhipuPlusConfig(apiKey).withEnabled(hasKey));

        // ---- 零一万物 ----
        list.add(AiProviderConfig.yiLightningConfig(apiKey).withEnabled(hasKey));
        list.add(AiProviderConfig.yiLargeConfig(apiKey).withEnabled(hasKey));

        return list;
    }
}
