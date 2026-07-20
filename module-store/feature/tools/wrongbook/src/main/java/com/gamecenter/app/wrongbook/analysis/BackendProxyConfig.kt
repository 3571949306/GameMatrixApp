package com.gamecenter.app.wrongbook.analysis

import android.content.Context
import android.content.SharedPreferences

/**
 * 错题本后端代理配置。
 *
 * 管理"百度 OCR + 智谱 GLM"后端服务的地址。
 * 安卓端只保存后端地址，不保存百度/智谱密钥（密钥由后端 .env 管理）。
 */
class BackendProxyConfig(context: Context) {

    companion object {
        private const val PREFS_NAME = "wrongbook_backend_prefs"
        private const val KEY_BASE_URL = "backend_base_url"
        private const val KEY_ENABLED = "backend_enabled"

        /**
         * 默认后端地址。
         * 从宿主 BuildConfig 读取 debug 资源 wrongbook_api_base_url；
         * 若读取失败则回退到模拟器回环地址。
         */
        private fun defaultBaseUrl(context: Context): String {
            return try {
                // 通过宿主资源名反射读取，避免直接依赖宿主 R 类
                val resId = context.resources.getIdentifier(
                    "wrongbook_api_base_url", "string", context.packageName
                )
                if (resId != 0) context.getString(resId) else "http://10.0.2.2:8080"
            } catch (e: Exception) {
                "http://10.0.2.2:8080"
            }
        }

        /**
         * 读取宿主 BuildConfig.WRONGBOOK_BACKEND_PROXY feature flag。
         *
         * 用反射避免编译期对宿主 BuildConfig 的硬依赖（动态模块 compileOnly jar
         * 时机不稳定）。运行时通过 DexClassLoader 已加载的宿主类反射读取。
         */
        fun isFeatureFlagEnabled(): Boolean {
            return try {
                val clazz = Class.forName("com.gamecenter.app.BuildConfig")
                val field = clazz.getField("WRONGBOOK_BACKEND_PROXY")
                field.getBoolean(null)
            } catch (e: Exception) {
                false
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secureKeyStore = SecureApiKeyStore(context)

    private var cachedDefaultUrl: String = defaultBaseUrl(context)

    /** 后端服务基础地址，如 http://10.0.2.2:8080 */
    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, cachedDefaultUrl) ?: cachedDefaultUrl
        set(value) {
            val trimmed = value.trim().trimEnd('/')
            prefs.edit().putString(KEY_BASE_URL, trimmed).apply()
        }

    /** 是否启用后端代理模式（独立于 OCR/AI 单项开关，便于一键切换） */
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var apiToken: String
        get() = secureKeyStore.backendApiToken
        set(value) { secureKeyStore.backendApiToken = value }

    /** 拼接完整接口 URL */
    fun resolve(path: String): String {
        val base = baseUrl.trim().trimEnd('/')
        val p = if (path.startsWith("/")) path else "/$path"
        return "$base$p"
    }
}
