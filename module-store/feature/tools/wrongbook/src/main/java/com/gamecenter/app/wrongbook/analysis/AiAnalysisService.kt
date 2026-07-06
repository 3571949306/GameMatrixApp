package com.gamecenter.app.wrongbook.analysis

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /** cloud / local */
    var mode: String
        get() = prefs.getString(KEY_MODE, "cloud") ?: "cloud"
        set(value) = prefs.edit().putString(KEY_MODE, value).apply()

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, "https://api.deepseek.com/v1") ?: "https://api.deepseek.com/v1"
        set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

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
        if (mode != "cloud") {
            // 本地模式预留：未来接入端侧模型
            return@withContext AnalysisResult(
                success = false,
                message = "本地 AI 模式尚未实现，请先使用云端模式。"
            )
        }

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

/**
 * AI 解析结果。
 */
data class AnalysisResult(
    val success: Boolean,
    val subject: String = "",
    val difficulty: Int = 3,
    val knowledgePoints: List<String> = emptyList(),
    val analysis: String = "",
    val message: String = ""
)
