package com.gamecenter.app.wrongbook.analysis

import android.content.Context
import android.net.Uri
import android.content.SharedPreferences

/**
 * OCR 服务入口。
 *
 * 根据用户设置选择本地或云端 OCR 引擎。
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

    /** 当前 OCR 引擎：local / scnet */
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

    /**
     * 注册云端 OCR 引擎。
     */
    fun registerCloudEngine(engine: OcrEngine) {
        engines[engine.name] = engine
    }

    /**
     * 判断云端 OCR 是否可用。
     */
    fun isCloudAvailable(): Boolean = engines.containsKey("scnet")

    /**
     * 执行 OCR 识别。
     */
    suspend fun recognize(context: Context, imageUri: Uri): OcrResult {
        val engine = engines[currentEngine] ?: engines["local"]!!
        return engine.recognize(context, imageUri)
    }
}
