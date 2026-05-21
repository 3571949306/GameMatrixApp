package com.gamecenter.app.ai.cloud;

import android.content.Context;
import android.util.Log;

import com.gamecenter.app.BuildConfig;
import com.gamecenter.app.ai.data.AiProviderConfig;
import com.gamecenter.app.ai.data.AiErrorCode;
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
 * <p>
 * 你可以把这个类想象成一个"翻译官"：
 * 它把你（用户）的需求翻译成 AI 服务器能听懂的语言（HTTP 请求），
 * 再把服务器的回答翻译回来给你（AiResult）。
 * <p>
 * 支持 OpenAI 兼容接口（也适用于其他国产 API 如智谱、阿里通义等，只需更换 baseUrl 和 apiKey）。
 * <p>
 * 核心设计决策：
 * <ul>
 *   <li>采用同步阻塞式调用（{@code chatSync}），在后台线程池中执行，避免回调地狱。</li>
 *   <li>HTTP 客户端复用全局 {@link OkHttpClientProvider} 的连接池和 TLS 配置，
 *       并在此基础上设置 AI 请求专用的超时参数。</li>
 *   <li>当前仅支持同步请求；SSE 流式调用功能可后续通过 okhttp-sse 依赖扩展。</li>
 * </ul>
 */
public final class AiApiClient {

    private static final String TAG = "AiApiClient";
    // JSON 请求体的 MediaType（告诉服务器我们发的是 JSON 格式的数据）
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    // 连接超时时间（秒）— 建立网络连接的最大等待时间
    private static final int CONNECT_TIMEOUT = 20;
    // 读取超时时间（秒）— AI 推理响应较慢，设置较长超时，避免还没出结果就超时了
    private static final int READ_TIMEOUT = 60;

    // 当前请求使用的供应商配置（包含 baseUrl、apiKey、modelName 等）
    private final AiProviderConfig config;
    // 定制超时参数的 OkHttpClient 实例
    private final OkHttpClient httpClient;

    /**
     * 构造 API 客户端。
     * <p>
     * 优先复用全局 {@link OkHttpClientProvider} 的连接池配置，
     * 在此基础上覆盖连接和读取超时参数以适配 AI 推理的响应特性。
     * 若全局 Provider 不可用，则创建全新的 OkHttpClient。
     *
     * @param config 云端供应商配置，包含 baseUrl、apiKey、modelName 等
     */
    public AiApiClient(AiProviderConfig config) {
        this.config = config;
        // 尝试复用全局 HTTP 客户端配置（共享连接池，更高效）
        OkHttpClientProvider provider = OkHttpClientProvider.getInstance(null);
        OkHttpClient.Builder builder = provider != null ? provider.getHttpClient().newBuilder() : new OkHttpClient.Builder();
        // AI 推理通常需要更长的响应时间，所以设置较长的超时
        builder.connectTimeout(CONNECT_TIMEOUT, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT, java.util.concurrent.TimeUnit.SECONDS);
        this.httpClient = builder.build();
    }

    /**
     * 同步调用：发送聊天请求并返回完整响应文本。
     * <p>
     * 调用 OpenAI 兼容的 {@code /chat/completions} 接口，
     * 使用 Bearer Token 认证方式传递 API Key。
     * <p>
     * 注意：此方法为阻塞调用，必须在后台线程中执行，不可在主线程调用！
     * 在主线程做网络请求会导致 Android 应用崩溃（NetworkOnMainThreadException）。
     *
     * @param systemPrompt 系统提示词，用于设定 AI 助手的行为角色；可为 null 或空字符串（表示不使用系统提示）
     * @param userMessage  用户消息内容
     * @return AI 处理结果；成功时包含响应文本，失败时包含错误信息和错误码
     */
    public AiResult chatSync(String systemPrompt, String userMessage) {
        try {
            // 构建请求体（JSON 格式）
            String jsonBody = buildChatRequest(systemPrompt, userMessage);
            // 构建 HTTP 请求
            Request request = new Request.Builder()
                    .url(config.baseUrl + "/chat/completions")  // 拼接完整的 API 地址
                    .addHeader("Authorization", "Bearer " + config.apiKey)  // 用 API Key 做身份验证
                    .addHeader("Content-Type", "application/json")  // 告诉服务器发送的是 JSON 数据
                    .post(RequestBody.create(jsonBody, JSON))  // 把 JSON 字符串作为请求体
                    .build();

            // 执行请求并获取响应
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    // HTTP 状态码非 2xx，说明请求出错了（如 401 未授权、429 限流等）
                    return AiResult.fail("API请求失败: HTTP " + response.code() + " " + response.message())
                            .errorCode(AiErrorCode.HTTP_ERROR + "_" + response.code()).build();
                }
                // 读取响应体
                String body = response.body().string();
                JSONObject json = new JSONObject(body);
                // 解析 OpenAI 兼容格式的响应：choices[0].message.content
                // 这是 OpenAI 定义的标准响应格式，大多数国产 AI 也兼容这个格式
                String content = json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");
                return AiResult.success(content)
                        .source("cloud")
                        .build();
            }
        } catch (Exception e) {
            logError("chatSync failed", e);
            return AiResult.fail("请求失败: " + e.getMessage()).errorCode(AiErrorCode.NETWORK_ERROR).build();
        }
    }

    // 安全的日志输出方法，在本地 JVM 测试中 android.util.Log 不可用
    private static void logError(String message, Throwable throwable) {
        try {
            Log.e(TAG, message, throwable);
        } catch (Throwable ignored) {
            // android.util.Log is unavailable in local JVM tests.
        }
    }

    /**
     * 构建 OpenAI 兼容的聊天请求 JSON 体。
     * <p>
     * 请求格式遵循 OpenAI Chat Completions API 规范：
     * <ul>
     *   <li>{@code model}：使用的模型名称</li>
     *   <li>{@code messages}：消息数组，包含可选的 system 消息和必需的 user 消息</li>
     *   <li>{@code max_tokens}：最大生成令牌数，按模型能力在 2048-4096 之间自适应</li>
     *   <li>{@code temperature}：采样温度，设为 0.7（平衡创造性和准确性）</li>
     * </ul>
     * <p>
     * temperature 就像 AI 的"想象力旋钮"：0 表示最保守（每次回答都一样），
     * 1 表示最有创造力（每次回答可能不同），0.7 是一个不错的平衡点。
     *
     * @param systemPrompt 系统提示词；为 null 或空时跳过 system 消息
     * @param userMessage  用户消息内容
     * @return 构建好的 JSON 字符串
     * @throws Exception JSON 构建异常
     */
    private String buildChatRequest(String systemPrompt, String userMessage) throws Exception {
        JSONObject json = new JSONObject();
        json.put("model", config.modelName);

        JSONArray messages = new JSONArray();
        // 仅在系统提示词非空时添加 system 消息
        // system 消息用来给 AI 设定"人设"，比如"你是一个有用的助手"
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messages.put(sysMsg);
        }
        // user 消息是用户实际要问的内容
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        messages.put(userMsg);
        json.put("messages", messages);

        // max_tokens: AI 最多生成多少个 token，按模型能力适当放宽，避免长摘要和长问答被过早截断
        json.put("max_tokens", resolveMaxOutputTokens(config));
        // temperature: 控制 AI 回答的随机性，0.7 是创造性和准确性的平衡点
        json.put("temperature", 0.7);

        return json.toString();
    }

    /**
     * 根据模型成本和上下文能力估算输出长度。
     * 低成本模型默认 2048 token，高能力模型提升到 3072-4096 token，兼顾响应完整度与等待时间。
     */
    private static int resolveMaxOutputTokens(AiProviderConfig config) {
        if (config == null) {
            return 2048;
        }
        if (config.costLevel >= 3 || config.maxInputLength >= 100000) {
            return 4096;
        }
        if (config.costLevel == 2 || config.maxInputLength >= 64000) {
            return 3072;
        }
        return 2048;
    }
}
