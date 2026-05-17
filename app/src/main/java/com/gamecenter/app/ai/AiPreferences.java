package com.gamecenter.app.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.gamecenter.app.ai.data.AiProviderConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 偏好设置管理器 — 负责所有 AI 相关配置的持久化存储与读取。
 * <p>
 * 核心设计决策：
 * <ul>
 *   <li>使用 {@link EncryptedSharedPreferences} 加密存储敏感数据（如 API Key），
 *       防止在 root 设备上被直接读取。</li>
 *   <li>支持从旧版明文 SharedPreferences 自动迁移到加密存储，迁移完成后清除旧数据。</li>
 *   <li>若加密存储初始化失败（如设备不支持），自动降级为明文存储，保证功能可用。</li>
 *   <li>每日免费额度通过日期比对实现自动重置，无需外部定时器。</li>
 * </ul>
 */
public class AiPreferences {

    private static final String TAG = "AiPreferences";
    /** 明文 SharedPreferences 文件名（旧版，仅用于迁移） */
    private static final String PREFS_NAME = "ai_settings";
    /** 加密 SharedPreferences 文件名 */
    private static final String ENCRYPTED_PREFS_NAME = "ai_settings_encrypted";
    /** 标记是否已完成从明文到加密存储的迁移 */
    private static final String KEY_MIGRATION_DONE = "migration_to_encrypted_done";
    /** 用户选择的 AI 供应商名称 */
    private static final String KEY_SELECTED_PROVIDER = "selected_provider";
    /** 用户选择的模型名称 */
    private static final String KEY_SELECTED_MODEL = "selected_model";
    /** 是否启用本地优先策略 */
    private static final String KEY_USE_LOCAL_FIRST = "use_local_first";
    /** 云端 API Key（加密存储） */
    private static final String KEY_API_KEY = "api_key";
    /** 本地模型标识 */
    private static final String KEY_LOCAL_MODEL = "local_model";
    /** 历史记录最大条数 */
    private static final String KEY_HISTORY_MAX = "history_max";
    /** 每日免费调用额度上限 */
    private static final String KEY_FREE_DAILY_LIMIT = "free_daily_limit";
    /** 今日已使用的免费额度次数 */
    private static final String KEY_USED_TODAY = "used_today";
    /** 上次额度重置的日期（以天为单位的毫秒时间戳） */
    private static final String KEY_LAST_RESET_DATE = "last_reset_date";
    /** 用户已接受的 Gemma 模型声明版本号 */
    private static final String KEY_GEMMA_NOTICE_ACCEPTED_VERSION = "gemma_notice_accepted_version";
    /** 用户接受 Gemma 声明的时间戳 */
    private static final String KEY_GEMMA_NOTICE_ACCEPTED_AT = "gemma_notice_accepted_at";

    /** 加密 SharedPreferences 实例（降级时为明文实例） */
    private final SharedPreferences prefs;

    /**
     * 构造偏好管理器，初始化加密存储并执行数据迁移。
     *
     * @param context 上下文，内部转为 Application Context
     */
    public AiPreferences(Context context) {
        Context appContext = context.getApplicationContext();
        prefs = getEncryptedPrefs(appContext);
        migrateFromPlainPrefs(appContext);
    }

    /**
     * 获取加密 SharedPreferences 实例。
     * <p>
     * 使用 AES256_GCM 加密值、AES256_SIV 加密键，通过 AndroidX Security 库实现。
     * 若加密存储初始化失败（如设备不支持或 keystore 异常），降级为明文存储。
     *
     * @param appContext 应用级上下文
     * @return 加密或明文的 SharedPreferences 实例
     */
    private static SharedPreferences getEncryptedPrefs(Context appContext) {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            return EncryptedSharedPreferences.create(
                    ENCRYPTED_PREFS_NAME,
                    masterKeyAlias,
                    appContext,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable, falling back to plain prefs", e);
            return appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    /**
     * 从旧版明文 SharedPreferences 迁移数据到加密存储。
     * <p>
     * 迁移流程：
     * <ol>
     *   <li>检查迁移标记，若已完成则跳过</li>
     *   <li>读取旧版明文存储中的所有键值对</li>
     *   <li>逐项写入加密存储</li>
     *   <li>标记迁移完成并清除旧数据</li>
     * </ol>
     * <p>
     * 迁移失败时仅记录日志，不影响应用正常运行。
     *
     * @param appContext 应用级上下文
     */
    private void migrateFromPlainPrefs(Context appContext) {
        if (prefs.getBoolean(KEY_MIGRATION_DONE, false)) {
            return;
        }
        try {
            SharedPreferences plainPrefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            if (plainPrefs.getAll().isEmpty()) {
                // 旧存储为空，直接标记迁移完成
                prefs.edit().putBoolean(KEY_MIGRATION_DONE, true).apply();
                return;
            }
            SharedPreferences.Editor editor = prefs.edit();
            migrateString(plainPrefs, editor, KEY_SELECTED_PROVIDER);
            migrateString(plainPrefs, editor, KEY_SELECTED_MODEL);
            migrateString(plainPrefs, editor, KEY_API_KEY);
            migrateString(plainPrefs, editor, KEY_LOCAL_MODEL);
            migrateBoolean(plainPrefs, editor, KEY_USE_LOCAL_FIRST);
            migrateInt(plainPrefs, editor, KEY_HISTORY_MAX);
            migrateInt(plainPrefs, editor, KEY_FREE_DAILY_LIMIT);
            migrateInt(plainPrefs, editor, KEY_USED_TODAY);
            migrateLong(plainPrefs, editor, KEY_LAST_RESET_DATE);
            migrateString(plainPrefs, editor, KEY_GEMMA_NOTICE_ACCEPTED_VERSION);
            migrateLong(plainPrefs, editor, KEY_GEMMA_NOTICE_ACCEPTED_AT);
            editor.putBoolean(KEY_MIGRATION_DONE, true);
            editor.apply();
            // 迁移完成后清除旧版明文数据，避免敏感信息残留
            plainPrefs.edit().clear().apply();
            Log.d(TAG, "Migrated AI preferences to encrypted storage");
        } catch (Exception e) {
            Log.e(TAG, "Migration failed", e);
        }
    }

    /**
     * 迁移字符串类型的偏好项。
     *
     * @param src 源 SharedPreferences
     * @param dst 目标编辑器
     * @param key 偏好键名
     */
    private static void migrateString(SharedPreferences src, SharedPreferences.Editor dst, String key) {
        if (src.contains(key)) {
            String val = src.getString(key, null);
            if (val != null) dst.putString(key, val);
        }
    }

    /**
     * 迁移布尔类型的偏好项。
     *
     * @param src 源 SharedPreferences
     * @param dst 目标编辑器
     * @param key 偏好键名
     */
    private static void migrateBoolean(SharedPreferences src, SharedPreferences.Editor dst, String key) {
        if (src.contains(key)) dst.putBoolean(key, src.getBoolean(key, false));
    }

    /**
     * 迁移整数类型的偏好项。
     *
     * @param src 源 SharedPreferences
     * @param dst 目标编辑器
     * @param key 偏好键名
     */
    private static void migrateInt(SharedPreferences src, SharedPreferences.Editor dst, String key) {
        if (src.contains(key)) dst.putInt(key, src.getInt(key, 0));
    }

    /**
     * 迁移长整数类型的偏好项。
     *
     * @param src 源 SharedPreferences
     * @param dst 目标编辑器
     * @param key 偏好键名
     */
    private static void migrateLong(SharedPreferences src, SharedPreferences.Editor dst, String key) {
        if (src.contains(key)) dst.putLong(key, src.getLong(key, 0L));
    }

    /**
     * 获取用户选择的 AI 供应商名称。
     *
     * @return 供应商名称，默认为 "DeepSeek"
     */
    public String getSelectedProvider() {
        return prefs.getString(KEY_SELECTED_PROVIDER, "DeepSeek");
    }

    /**
     * 设置用户选择的 AI 供应商。
     *
     * @param provider 供应商名称
     */
    public void setSelectedProvider(String provider) {
        prefs.edit().putString(KEY_SELECTED_PROVIDER, provider).apply();
    }

    /**
     * 获取用户选择的模型名称。
     *
     * @return 模型名称，默认为 "deepseek-chat"
     */
    public String getSelectedModel() {
        return prefs.getString(KEY_SELECTED_MODEL, "deepseek-chat");
    }

    /**
     * 设置用户选择的模型。
     *
     * @param model 模型名称
     */
    public void setSelectedModel(String model) {
        prefs.edit().putString(KEY_SELECTED_MODEL, model).apply();
    }

    /**
     * 获取是否启用本地优先策略。
     * <p>
     * 启用后，AI 任务优先尝试本地规则引擎和本地 LLM 处理，
     * 仅在本地无法胜任时才回退到云端 API。
     *
     * @return 是否本地优先，默认为 true
     */
    public boolean isLocalFirst() {
        return prefs.getBoolean(KEY_USE_LOCAL_FIRST, true);
    }

    /**
     * 设置是否启用本地优先策略。
     *
     * @param localFirst 是否本地优先
     */
    public void setLocalFirst(boolean localFirst) {
        prefs.edit().putBoolean(KEY_USE_LOCAL_FIRST, localFirst).apply();
    }

    /**
     * 获取云端 API Key。
     *
     * @return API Key 字符串；未配置时返回空字符串
     */
    public String getApiKey() {
        return prefs.getString(KEY_API_KEY, "");
    }

    /**
     * 设置云端 API Key。
     * <p>
     * API Key 通过加密 SharedPreferences 安全存储。
     *
     * @param apiKey API Key 字符串
     */
    public void setApiKey(String apiKey) {
        prefs.edit().putString(KEY_API_KEY, apiKey).apply();
    }

    /**
     * 获取本地模型标识。
     *
     * @return 本地模型标识，默认为 "on-device"（规则引擎），
     *         可选 "gemma3-1b-it-q4"（本地 Gemma LLM）
     */
    public String getLocalModel() {
        return prefs.getString(KEY_LOCAL_MODEL, "on-device");
    }

    /**
     * 设置本地模型标识。
     *
     * @param model 本地模型标识
     */
    public void setLocalModel(String model) {
        prefs.edit().putString(KEY_LOCAL_MODEL, model).apply();
    }

    /**
     * 检查用户是否已接受指定版本的 Gemma 模型声明。
     * <p>
     * 当声明内容更新时（版本号变更），需要用户重新确认。
     *
     * @param noticeVersion 当前声明版本号
     * @return 是否已接受该版本的声明
     */
    public boolean hasAcceptedGemmaNotice(String noticeVersion) {
        return noticeVersion != null
                && noticeVersion.equals(prefs.getString(KEY_GEMMA_NOTICE_ACCEPTED_VERSION, ""));
    }

    /**
     * 记录用户已接受 Gemma 模型声明。
     * <p>
     * 同时保存接受时间戳，用于合规审计。
     *
     * @param noticeVersion 当前声明版本号
     */
    public void acceptGemmaNotice(String noticeVersion) {
        prefs.edit()
                .putString(KEY_GEMMA_NOTICE_ACCEPTED_VERSION, noticeVersion)
                .putLong(KEY_GEMMA_NOTICE_ACCEPTED_AT, System.currentTimeMillis())
                .apply();
    }

    /**
     * 获取用户接受 Gemma 声明的时间戳。
     *
     * @return 接受时间的毫秒时间戳；未接受时返回 0
     */
    public long getGemmaNoticeAcceptedAt() {
        return prefs.getLong(KEY_GEMMA_NOTICE_ACCEPTED_AT, 0L);
    }

    /**
     * 获取历史记录最大保存条数。
     *
     * @return 最大条数，默认为 50
     */
    public int getHistoryMax() {
        return prefs.getInt(KEY_HISTORY_MAX, 50);
    }

    /**
     * 设置历史记录最大保存条数。
     *
     * @param max 最大条数
     */
    public void setHistoryMax(int max) {
        prefs.edit().putInt(KEY_HISTORY_MAX, max).apply();
    }

    /**
     * 获取每日免费调用额度上限。
     *
     * @return 每日免费额度上限，默认为 20
     */
    public int getFreeDailyLimit() {
        return prefs.getInt(KEY_FREE_DAILY_LIMIT, 20);
    }

    /**
     * 设置每日免费调用额度上限。
     *
     * @param limit 每日免费额度上限
     */
    public void setFreeDailyLimit(int limit) {
        prefs.edit().putInt(KEY_FREE_DAILY_LIMIT, limit).apply();
    }

    /**
     * 获取今日已使用的免费额度次数。
     * <p>
     * 通过日期比对实现自动重置：若当前日期与上次重置日期不同，
     * 则自动将已用次数归零并更新重置日期。
     * <p>
     * 日期计算方式：{@code System.currentTimeMillis() / 86400000L}，
     * 即以自 Unix 纪元以来的天数作为日期标识（86400000 = 24 * 60 * 60 * 1000）。
     *
     * @return 今日已使用次数；跨天自动归零
     */
    public int getUsedInDay() {
        long today = System.currentTimeMillis() / 86400000L;
        long lastReset = prefs.getLong(KEY_LAST_RESET_DATE, 0);
        if (today != lastReset) {
            // 跨天自动重置已用次数
            prefs.edit().putLong(KEY_LAST_RESET_DATE, today).putInt(KEY_USED_TODAY, 0).apply();
            return 0;
        }
        return prefs.getInt(KEY_USED_TODAY, 0);
    }

    /**
     * 递增今日已使用的免费额度次数。
     * <p>
     * 同时更新日期标识，确保跨天场景下计数正确。
     */
    public void incrementUsage() {
        long today = System.currentTimeMillis() / 86400000L;
        prefs.edit()
                .putLong(KEY_LAST_RESET_DATE, today)
                .putInt(KEY_USED_TODAY, getUsedInDay() + 1)
                .apply();
    }

    /**
     * 检查今日是否还有免费额度可用。
     *
     * @return 已用次数是否小于每日上限
     */
    public boolean hasFreeQuota() {
        return getUsedInDay() < getFreeDailyLimit();
    }

    /**
     * 获取所有可用的 AI 供应商配置列表。
     * <p>
     * 返回的列表包含本地规则引擎和所有云端供应商。
     * 云端供应商的启用状态取决于是否已配置 API Key：
     * <ul>
     *   <li>有 API Key → 所有云端供应商可用</li>
     *   <li>无 API Key → 仅本地规则引擎可用</li>
     * </ul>
     * <p>
     * 当前支持的云端供应商：OpenAI、DeepSeek（含 Reasoner）、阿里通义（Turbo/标准/Max）、
     * SiliconFlow（DeepSeek/Qwen）、智谱（Flash/Plus）、零一万物（Lightning/Large）。
     *
     * @param context 上下文，用于读取 API Key
     * @return 供应商配置列表，始终包含至少一个本地配置
     */
    public static List<AiProviderConfig> getAvailableProviders(Context context) {
        List<AiProviderConfig> list = new ArrayList<>();
        String apiKey = new AiPreferences(context).getApiKey();
        boolean hasKey = !apiKey.isEmpty();

        // 本地规则引擎始终可用，不依赖 API Key
        list.add(new AiProviderConfig(
                "本地", "on-device", "",
                "", true, true, 2000, 0));

        // 云端供应商仅在配置了 API Key 时启用
        list.add(AiProviderConfig.openAIConfig(apiKey).withEnabled(hasKey));

        list.add(AiProviderConfig.deepseekConfig(apiKey).withEnabled(hasKey));
        list.add(AiProviderConfig.deepseekReasonerConfig(apiKey).withEnabled(hasKey));

        list.add(AiProviderConfig.aliyunTurboConfig(apiKey).withEnabled(hasKey));
        list.add(AiProviderConfig.aliyunConfig(apiKey).withEnabled(hasKey));
        list.add(AiProviderConfig.aliyunMaxConfig(apiKey).withEnabled(hasKey));

        list.add(AiProviderConfig.siliconFlowDeepSeekConfig(apiKey).withEnabled(hasKey));
        list.add(AiProviderConfig.siliconFlowQwenConfig(apiKey).withEnabled(hasKey));

        list.add(AiProviderConfig.zhipuFlashConfig(apiKey).withEnabled(hasKey));
        list.add(AiProviderConfig.zhipuPlusConfig(apiKey).withEnabled(hasKey));

        list.add(AiProviderConfig.yiLightningConfig(apiKey).withEnabled(hasKey));
        list.add(AiProviderConfig.yiLargeConfig(apiKey).withEnabled(hasKey));

        return list;
    }
}
