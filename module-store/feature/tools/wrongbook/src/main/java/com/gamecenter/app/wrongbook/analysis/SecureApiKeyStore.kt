package com.gamecenter.app.wrongbook.analysis

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * 错题本敏感凭据加密存储。
 *
 * 第七阶段：使用 EncryptedSharedPreferences 加密保存百度 OCR 与智谱 GLM 的 API Key，
 * 避免在生产环境将密钥硬编码或明文存储。
 *
 * - 文件名：wrongbook_secure_keys.xml
 * - 加密方式：AES-GCM 256 位 + AES-SIV-CMAC256（主密钥由 Android Keystore 托管）
 * - API 版本：security-crypto 1.0.0（使用 MasterKeys.getOrCreate 静态方法）
 * - 失败策略：Keystore 不可用时拒绝持久化，不允许降级为明文。
 *
 * Feature flag：BuildConfig.WRONGBOOK_SECURE_API_CONFIG 控制整个区块是否可见。
 */
class SecureApiKeyStore(context: Context) {

    companion object {
        private const val TAG = "SecureApiKeyStore"
        private const val FILE_NAME = "wrongbook_secure_keys.xml"

        private const val KEY_BAIDU_OCR_API_KEY = "baidu_ocr_api_key"
        private const val KEY_BAIDU_OCR_SECRET_KEY = "baidu_ocr_secret_key"
        private const val KEY_ZHIPU_API_KEY = "zhipu_api_key"
        private const val KEY_ZHIPU_MODEL = "zhipu_model"
        private const val KEY_DIRECT_AI_API_KEY = "direct_ai_api_key"
        private const val KEY_BACKEND_API_TOKEN = "backend_api_token"

        /**
         * 读取宿主 BuildConfig.WRONGBOOK_SECURE_API_CONFIG feature flag。
         * 运行时反射，避免编译期对宿主 BuildConfig 的硬依赖。
         */
        fun isFeatureFlagEnabled(): Boolean {
            return try {
                val clazz = Class.forName("com.gamecenter.app.BuildConfig")
                val field = clazz.getField("WRONGBOOK_SECURE_API_CONFIG")
                field.getBoolean(null)
            } catch (e: Exception) {
                false
            }
        }
    }

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences? by lazy {
        try {
            // security-crypto 1.0.0 API：使用 MasterKeys.getOrCreate 获取主密钥别名
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

            EncryptedSharedPreferences.create(
                FILE_NAME,
                masterKeyAlias,
                appContext,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences 初始化失败，拒绝明文保存敏感凭据", e)
            null
        }
    }

    val isStorageAvailable: Boolean
        get() = prefs != null

    private fun read(key: String, default: String = ""): String =
        prefs?.getString(key, default) ?: default

    private fun write(key: String, value: String) {
        val encryptedPrefs = prefs
        if (encryptedPrefs == null) {
            Log.e(TAG, "安全存储不可用，未保存 $key")
            return
        }
        encryptedPrefs.edit().putString(key, value).apply()
    }

    // ===== 百度 OCR =====

    var baiduOcrApiKey: String
        get() = read(KEY_BAIDU_OCR_API_KEY)
        set(value) = write(KEY_BAIDU_OCR_API_KEY, value)

    var baiduOcrSecretKey: String
        get() = read(KEY_BAIDU_OCR_SECRET_KEY)
        set(value) = write(KEY_BAIDU_OCR_SECRET_KEY, value)

    // ===== 智谱 GLM =====

    var zhipuApiKey: String
        get() = read(KEY_ZHIPU_API_KEY)
        set(value) = write(KEY_ZHIPU_API_KEY, value)

    var zhipuModel: String
        get() = read(KEY_ZHIPU_MODEL, "glm-4-flash")
        set(value) = write(KEY_ZHIPU_MODEL, value)

    var directAiApiKey: String
        get() = read(KEY_DIRECT_AI_API_KEY).ifBlank { zhipuApiKey }
        set(value) = write(KEY_DIRECT_AI_API_KEY, value)

    var backendApiToken: String
        get() = read(KEY_BACKEND_API_TOKEN)
        set(value) = write(KEY_BACKEND_API_TOKEN, value)

    // ===== 通用操作 =====

    /**
     * 清除全部正式版密钥配置。
     */
    fun clearAll() {
        prefs?.edit()?.clear()?.apply()
    }

    /**
     * 是否已配置任何密钥。
     */
    fun hasAnyKey(): Boolean {
        return baiduOcrApiKey.isNotBlank() ||
               baiduOcrSecretKey.isNotBlank() ||
               zhipuApiKey.isNotBlank()
    }

    /**
     * 返回脱敏的密钥摘要（仅显示前 4 位 + ***），用于 UI 展示。
     */
    fun maskedSummary(): String {
        val parts = mutableListOf<String>()
        if (baiduOcrApiKey.isNotBlank()) {
            parts.add("百度OCR: ${mask(baiduOcrApiKey)}")
        }
        if (zhipuApiKey.isNotBlank()) {
            parts.add("智谱AI: ${mask(zhipuApiKey)}")
        }
        return if (parts.isEmpty()) "未配置" else parts.joinToString("  ")
    }

    private fun mask(key: String): String {
        if (key.length <= 4) return "****"
        return key.take(4) + "***"
    }
}
