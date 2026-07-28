package com.gamecenter.app.wrongbook.analysis

import android.content.Context
import android.net.Uri
import android.content.SharedPreferences

/**
 * OCR 服务入口。
 *
 * 根据用户设置选择本地或云端 OCR 引擎。
 * 支持引擎：local（ML Kit）/ scnet（预留）/ baidu（后端代理）。
 */
class OcrService(context: Context) {

    companion object {
        private const val PREFS_NAME = "wrongbook_ocr_prefs"
        private const val KEY_ENGINE = "ocr_engine"
        private const val KEY_SCNET_API_KEY = "scnet_ocr_api_key"
        private const val KEY_SCNET_BASE_URL = "scnet_ocr_base_url"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 后端代理配置（百度 OCR 走后端） */
    val backendConfig: BackendProxyConfig = BackendProxyConfig(context)

    /** 当前 OCR 引擎：local / scnet / baidu */
    var currentEngine: String
        get() = prefs.getString(KEY_ENGINE, "local") ?: "local"
        set(value) = prefs.edit().putString(KEY_ENGINE, value).apply()

    var scnetApiKey: String
        get() = prefs.getString(KEY_SCNET_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SCNET_API_KEY, value).apply()

    var scnetBaseUrl: String
        get() = prefs.getString(KEY_SCNET_BASE_URL, "https://api.scnet.cn/api/llm/v1/ocrdoc") ?: "https://api.scnet.cn/api/llm/v1/ocrdoc"
        set(value) = prefs.edit().putString(KEY_SCNET_BASE_URL, value).apply()

    private val engines = mutableMapOf<String, OcrEngine>(
        "local" to LocalMlKitOcrEngine()
    )

    init {
        // feature flag 开启时，注册百度 OCR 后端代理引擎
        if (BackendProxyConfig.isFeatureFlagEnabled()) {
            engines["baidu"] = BaiduOcrEngine(backendConfig)
        }
    }

    /**
     * 注册云端 OCR 引擎。
     */
    fun registerCloudEngine(engine: OcrEngine) {
        engines[engine.name] = engine
    }

    /**
     * 判断云端 OCR（scnet）是否可用。
     */
    fun isCloudAvailable(): Boolean = engines.containsKey("scnet")

    /**
     * 判断百度 OCR 后端代理是否可用（受 feature flag 控制）。
     */
    fun isBaiduAvailable(): Boolean = engines.containsKey("baidu")

    /**
     * 执行 OCR 识别。
     *
     * @param accurate true 时云端引擎切换高精度模式（本地引擎忽略）
     */
    suspend fun recognize(context: Context, imageUri: Uri, accurate: Boolean = false): OcrResult {
        val engine = engines[currentEngine] ?: engines["local"]!!
        return engine.recognize(context, imageUri, accurate)
    }

    /**
     * 强制使用本地引擎执行 OCR（用于 consent 选择"改用本地"时）。
     */
    suspend fun recognizeLocal(context: Context, imageUri: Uri, accurate: Boolean = false): OcrResult {
        return engines["local"]!!.recognize(context, imageUri, accurate)
    }

    /** 当前是否为云端引擎（baidu / scnet） */
    val isCloudEngine: Boolean
        get() = currentEngine != "local" && engines.containsKey(currentEngine)
}
