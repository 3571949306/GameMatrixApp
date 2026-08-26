package com.gamecenter.app.wrongbook.analysis

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.gamecenter.app.core.common.ModuleScopedPreferences
import com.gamecenter.app.wrongbook.ui.ModuleContextHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * AI 解析服务。
 *
 * 支持云端 OpenAI 兼容接口解析错题，未来可扩展本地模型。
 */
class AiAnalysisService(context: Context) {

    companion object {
        private const val TAG = "AiAnalysisService"
        private const val PREFS_NAME = "wrongbook_ai_prefs"
        private const val KEY_BASE_URL = "ai_base_url"
        private const val KEY_API_KEY = "ai_api_key"
        private const val KEY_MODEL = "ai_model"
        private const val KEY_MODE = "ai_mode"

        private const val DEFAULT_TIMEOUT_SECONDS = 60L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** 后端代理解题接口路径 */
        private const val BACKEND_SOLVE_TEXT_PATH = "/api/wrongbook/solve-text"

        private const val SYSTEM_PROMPT = """
            你是一位严谨的学习助手。请根据用户提供的题目文本，完成以下任务：
            1. 简要分析题目考查的核心知识点。
            2. 给出清晰的解题思路或答案要点。
            3. 以 JSON 格式返回，字段包括：
               - "subject": 题目所属科目（如数学、英语、物理等）
               - "difficulty": 难度 1-5
               - "knowledgePoints": 知识点字符串数组
               - "analysis": 解析文本
            只返回 JSON，不要附加说明。
        """
    }

    private val prefs: SharedPreferences = run {
        // Phase 3 数据隔离：迁移旧扁平 SP 并使用作用域 SP（mod_wrongbook__wrongbook_ai_prefs）
        ModuleScopedPreferences.migrateFrom(context, ModuleContextHelper.MODULE_ID, PREFS_NAME)
        ModuleScopedPreferences.get(context, ModuleContextHelper.MODULE_ID, PREFS_NAME)
    }
    private val secureKeyStore = SecureApiKeyStore(context)

    init {
        val legacyPlaintextKey = prefs.getString(KEY_API_KEY, "").orEmpty()
        if (legacyPlaintextKey.isNotBlank() && secureKeyStore.directAiApiKey.isBlank()) {
            secureKeyStore.directAiApiKey = legacyPlaintextKey
        }
        if (legacyPlaintextKey.isNotBlank() && secureKeyStore.isStorageAvailable) {
            prefs.edit().remove(KEY_API_KEY).apply()
        }
    }

    /** 后端代理配置（智谱 GLM 走后端） */
    val backendConfig: BackendProxyConfig = BackendProxyConfig(context)

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /** cloud / local / backend_proxy */
    var mode: String
        get() = prefs.getString(KEY_MODE, "cloud") ?: "cloud"
        set(value) = prefs.edit().putString(KEY_MODE, value).apply()

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, "https://api.deepseek.com/v1") ?: "https://api.deepseek.com/v1"
        set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()

    var apiKey: String
        get() = secureKeyStore.directAiApiKey
        set(value) { secureKeyStore.directAiApiKey = value }

    var model: String
        get() = prefs.getString(KEY_MODEL, "deepseek-chat") ?: "deepseek-chat"
        set(value) = prefs.edit().putString(KEY_MODEL, value).apply()

    /**
     * 解析题目文本。
     *
     * @param questionText 题目文本
     * @return 解析结果，包含 subject、difficulty、knowledgePoints、analysis
     */
    suspend fun analyze(questionText: String): AnalysisResult = withContext(Dispatchers.IO) {
        when (mode) {
            "backend_proxy" -> return@withContext analyzeViaBackend(questionText)
            "local" -> return@withContext AnalysisResult(
                success = false,
                message = "本地 AI 模式尚未实现，请先使用云端模式。"
            )
            else -> {
                // cloud 模式：直连 OpenAI 兼容接口（DeepSeek 等）
                if (baseUrl.isBlank() || apiKey.isBlank()) {
                    return@withContext AnalysisResult(
                        success = false,
                        message = "请先配置 AI 接口地址和 API Key。"
                    )
                }

                try {
                    val requestBody = JSONObject().apply {
                        put("model", model)
                        put("messages", org.json.JSONArray().apply {
                            put(JSONObject().apply {
                                put("role", "system")
                                put("content", SYSTEM_PROMPT)
                            })
                            put(JSONObject().apply {
                                put("role", "user")
                                put("content", questionText)
                            })
                        })
                        put("temperature", 0.3)
                    }.toString()

                    val request = Request.Builder()
                        .url("$baseUrl/chat/completions")
                        .addHeader("Authorization", "Bearer $apiKey")
                        .addHeader("Content-Type", "application/json")
                        .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            return@withContext AnalysisResult(
                                success = false,
                                message = "AI 请求失败: HTTP ${response.code}"
                            )
                        }
                        val body = response.body?.string() ?: ""
                        val content = JSONObject(body)
                            .getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                        return@withContext parseAnalysisJson(content)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "AI 解析失败: ${e.message}")
                    return@withContext AnalysisResult(
                        success = false,
                        message = "AI 解析失败: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 后端代理模式：调用 /api/wrongbook/solve-text，由后端转发到智谱 GLM。
     *
     * 安卓端不持有智谱 API Key，仅发送题目文本和科目。
     */
    private suspend fun analyzeViaBackend(questionText: String): AnalysisResult =
        withContext(Dispatchers.IO) {
            if (!backendConfig.enabled) {
                return@withContext AnalysisResult(
                    success = false,
                    message = "后端代理模式未启用，请在设置中开启。"
                )
            }
            if (backendConfig.apiToken.isBlank()) {
                return@withContext AnalysisResult(
                    success = false,
                    message = "请先配置后端访问令牌。"
                )
            }

            try {
                val requestBody = JSONObject().apply {
                    put("text", questionText)
                    put("subject", "人工智能训练师")
                }.toString()

                val request = Request.Builder()
                    .url(backendConfig.resolve(BACKEND_SOLVE_TEXT_PATH))
                    .addHeader("X-API-Key", backendConfig.apiToken)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        val msg = parseBackendErrorMessage(body)
                            ?: "AI 服务返回 HTTP ${response.code}"
                        return@withContext AnalysisResult(success = false, message = msg)
                    }
                    val json = JSONObject(body)
                    val success = json.optBoolean("success", false)
                    if (!success) {
                        return@withContext AnalysisResult(
                            success = false,
                            message = json.optString("message", "AI 解答失败")
                        )
                    }
                    // 映射后端返回字段到 AnalysisResult
                    return@withContext AnalysisResult(
                        success = true,
                        subject = json.optString("subject", "通用").ifBlank { "通用" },
                        difficulty = json.optInt("difficulty", 3).coerceIn(1, 5),
                        knowledgePoints = parseStringArray(json.optJSONArray("knowledgePoints")),
                        analysis = json.optString("analysis", ""),
                        message = json.optString("message", ""),
                        questionType = json.optString("questionType", "unknown"),
                        question = json.optString("question", ""),
                        options = parseStringArray(json.optJSONArray("options")),
                        answer = json.optString("answer", ""),
                        wrongReason = json.optString("wrongReason", ""),
                        reviewSuggestion = json.optString("reviewSuggestion", ""),
                        confidence = json.optDouble("confidence", 0.0).coerceIn(0.0, 1.0)
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "后端代理 AI 解析失败: ${e.message}")
                return@withContext AnalysisResult(
                    success = false,
                    message = "AI 解答超时，请稍后重试"
                )
            }
        }

    /** 从错误响应体中提取用户可读消息 */
    private fun parseBackendErrorMessage(body: String): String? {
        return try {
            JSONObject(body).optString("message").ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }

    /** 将 JSONArray 转为 List<String> */
    private fun parseStringArray(array: org.json.JSONArray?): List<String> {
        if (array == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until array.length()) {
            list.add(array.optString(i, ""))
        }
        return list.filter { it.isNotBlank() }
    }

    private fun parseAnalysisJson(content: String): AnalysisResult {
        val jsonText = content.trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return try {
            val json = JSONObject(jsonText)
            AnalysisResult(
                success = true,
                subject = json.optString("subject", "通用"),
                difficulty = json.optInt("difficulty", 3).coerceIn(1, 5),
                knowledgePoints = parseKnowledgePoints(json.optJSONArray("knowledgePoints")),
                analysis = json.optString("analysis", "")
            )
        } catch (e: Exception) {
            AnalysisResult(
                success = true,
                subject = "通用",
                difficulty = 3,
                knowledgePoints = emptyList(),
                analysis = content
            )
        }
    }

    private fun parseKnowledgePoints(array: org.json.JSONArray?): List<String> {
        if (array == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until array.length()) {
            list.add(array.optString(i, ""))
        }
        return list.filter { it.isNotBlank() }
    }
}
