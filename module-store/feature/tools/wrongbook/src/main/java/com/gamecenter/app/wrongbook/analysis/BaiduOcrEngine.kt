package com.gamecenter.app.wrongbook.analysis

import android.content.Context
import android.net.Uri
import android.util.Base64
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
 * 百度 OCR 引擎（后端代理模式）。
 *
 * 不直接调用百度 API，而是通过自建后端 /api/wrongbook/ocr 代理调用，
 * 密钥由后端 .env 管理，安卓端只持有后端地址。
 *
 * @param config 后端代理配置
 * @param httpClient 可选的自定义 OkHttp 客户端（测试注入用）
 */
class BaiduOcrEngine(
    private val config: BackendProxyConfig,
    private val httpClient: OkHttpClient = defaultClient
) : OcrEngine {

    companion object {
        private const val TAG = "BaiduOcrEngine"
        private const val OCR_PATH = "/api/wrongbook/ocr"
        private const val DEFAULT_TIMEOUT_SECONDS = 60L
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        }
    }

    override val name: String = "baidu"

    override suspend fun recognize(context: Context, imageUri: Uri, accurate: Boolean): OcrResult =
        withContext(Dispatchers.IO) {
            if (!config.enabled) {
                return@withContext OcrResult(
                    success = false,
                    message = "后端代理模式未启用，请在设置中开启。"
                )
            }
            if (config.apiToken.isBlank()) {
                return@withContext OcrResult(
                    success = false,
                    message = "请先配置后端访问令牌。"
                )
            }

            try {
                val imageBase64 = readUriAsBase64(context, imageUri)
                    ?: return@withContext OcrResult(
                        success = false,
                        message = "图片读取失败，请重新选择"
                    )

                // 高精度模式调用后端 accurate 接口；普通模式使用通用识别
                val requestBody = JSONObject().apply {
                    put("imageBase64", imageBase64)
                    put("ocrMode", if (accurate) "accurate" else "general_with_location")
                }.toString()

                val request = Request.Builder()
                    .url(config.resolve(OCR_PATH))
                    .addHeader("X-API-Key", config.apiToken)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string() ?: ""
                    if (!response.isSuccessful) {
                        val msg = parseErrorMessage(body) ?: "OCR 服务返回 HTTP ${response.code}"
                        return@withContext OcrResult(success = false, message = msg)
                    }
                    val json = JSONObject(body)
                    val success = json.optBoolean("success", false)
                    val text = json.optString("ocrText", "")
                    val message = json.optString("message", "")
                    if (success && text.isNotBlank()) {
                        OcrResult(success = true, text = text)
                    } else {
                        OcrResult(
                            success = false,
                            message = message.ifBlank { "未识别到文字，请重新拍摄或手动输入" }
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "后端 OCR 调用失败: ${e.message}")
                OcrResult(success = false, message = "识别超时，请稍后重试")
            }
        }

    /** 读取 Uri 为 base64 字符串（采样压缩超大图片，避免 OOM） */
    private fun readUriAsBase64(context: Context, uri: Uri): String? {
        return try {
            // 使用采样解码限制最大边长 2048px，避免大图 OOM
            val compressedPath = ImageCompressHelper.compressAndFixOrientation(
                context, uri, maxEdge = 2048, quality = 85
            )
            if (compressedPath.isNotBlank()) {
                val bytes = java.io.File(compressedPath).readBytes()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else {
                // 压缩失败回退：直接读取原始字节
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val bytes = input.readBytes()
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "读取图片 Uri 失败: ${e.message}")
            null
        }
    }

    /** 从错误响应体中提取用户可读消息 */
    private fun parseErrorMessage(body: String): String? {
        return try {
            val json = JSONObject(body)
            json.optString("message").ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }
}
