package com.gamecenter.app.ai.cloud;

import android.content.Context;
import android.util.Log;

import com.gamecenter.app.BuildConfig;
import com.gamecenter.app.ai.data.AiProviderConfig;
import com.gamecenter.app.ai.data.AiResult;
import com.gamecenter.app.network.OkHttpClientProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * AI API 客户端 — 负责与云端 AI 服务通信。
 * 支持 OpenAI 兼容接口（也适用于其他国产 API 如智谱、阿里通义等，只需更换 baseUrl 和 apiKey）。
 * <p>
 * SSE 流式调用功能可后续通过 okhttp-sse 依赖扩展，当前仅支持同步请求。
 */
public final class AiApiClient {

    private static final String TAG = "AiApiClient";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int CONNECT_TIMEOUT = 20;
    private static final int READ_TIMEOUT = 60;

    private final AiProviderConfig config;
    private final OkHttpClient httpClient;

    public AiApiClient(AiProviderConfig config) {
        this.config = config;
        OkHttpClient.Builder builder = OkHttpClientProvider.getInstance(null).getHttpClient().newBuilder();
        builder.connectTimeout(CONNECT_TIMEOUT, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT, java.util.concurrent.TimeUnit.SECONDS);
        this.httpClient = builder.build();
    }

    /**
     * 同步调用：发送消息并返回完整响应文本。
     */
    public AiResult chatSync(String systemPrompt, String userMessage) {
        try {
            String jsonBody = buildChatRequest(systemPrompt, userMessage);
            Request request = new Request.Builder()
                    .url(config.baseUrl + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + config.apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(jsonBody, JSON))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    return AiResult.fail("API请求失败: HTTP " + response.code() + " " + response.message())
                            .errorCode("HTTP_" + response.code()).build();
                }
                String body = response.body().string();
                JSONObject json = new JSONObject(body);
                String content = json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");
                return AiResult.success(content)
                        .source("cloud")
                        .build();
            }
        } catch (Exception e) {
            Log.e(TAG, "chatSync failed", e);
            return AiResult.fail("请求失败: " + e.getMessage()).errorCode("NETWORK_ERROR").build();
        }
    }

    /**
     * 构建 OpenAI 兼容的聊天请求 JSON。
     */
    private String buildChatRequest(String systemPrompt, String userMessage) throws Exception {
        JSONObject json = new JSONObject();
        json.put("model", config.modelName);

        JSONArray messages = new JSONArray();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messages.put(sysMsg);
        }
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.put(userMsg);
        json.put("messages", messages);

        json.put("max_tokens", 1024);
        json.put("temperature", 0.7);

        return json.toString();
    }
}