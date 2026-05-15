package com.gamecenter.app.ai.data;

/**
 * AI 提供商配置模型 — 描述一个可用的 AI 模型。
 * 所有提供商均使用 OpenAI 兼容接口格式（/chat/completions）。
 */
public final class AiProviderConfig {

    public final String providerName;
    public final String modelName;
    public final String apiKey;
    public final String baseUrl;
    public final boolean enabled;
    public final boolean localOnly;
    public final int maxInputLength;
    public final int costLevel;

    public AiProviderConfig(String providerName, String modelName, String apiKey,
                            String baseUrl, boolean enabled, boolean localOnly,
                            int maxInputLength, int costLevel) {
        this.providerName = providerName;
        this.modelName = modelName;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
        this.localOnly = localOnly;
        this.maxInputLength = maxInputLength;
        this.costLevel = costLevel;
    }

    public static AiProviderConfig localConfig() {
        return new AiProviderConfig(
                "本地", "on-device", "",
                "", true, true, 2000, 0
        );
    }

    // ===== 国外 =====

    public static AiProviderConfig openAIConfig(String apiKey) {
        return new AiProviderConfig(
                "OpenAI", "gpt-4o-mini", apiKey,
                "https://api.openai.com/v1", true, false, 128000, 1
        );
    }

    // ===== DeepSeek 深度求索 =====
    public static AiProviderConfig deepseekConfig(String apiKey) {
        return new AiProviderConfig(
                "DeepSeek", "deepseek-chat", apiKey,
                "https://api.deepseek.com/v1", true, false, 128000, 1
        );
    }

    public static AiProviderConfig deepseekReasonerConfig(String apiKey) {
        return new AiProviderConfig(
                "DeepSeek", "deepseek-reasoner", apiKey,
                "https://api.deepseek.com/v1", true, false, 128000, 2
        );
    }

    // ===== 阿里云通义千问 =====
    public static AiProviderConfig aliyunConfig(String apiKey) {
        return new AiProviderConfig(
                "阿里云通义", "qwen-plus", apiKey,
                "https://dashscope.aliyuncs.com/compatible-mode/v1", true, false, 32000, 2
        );
    }

    public static AiProviderConfig aliyunTurboConfig(String apiKey) {
        return new AiProviderConfig(
                "阿里云通义", "qwen-turbo", apiKey,
                "https://dashscope.aliyuncs.com/compatible-mode/v1", true, false, 32000, 1
        );
    }

    public static AiProviderConfig aliyunMaxConfig(String apiKey) {
        return new AiProviderConfig(
                "阿里云通义", "qwen-max", apiKey,
                "https://dashscope.aliyuncs.com/compatible-mode/v1", true, false, 32000, 3
        );
    }

    // ===== 硅基流动 SiliconFlow（最具性价比，提供多种开源模型） =====
    public static AiProviderConfig siliconFlowDeepSeekConfig(String apiKey) {
        return new AiProviderConfig(
                "硅基流动", "Pro/deepseek-ai/DeepSeek-V3", apiKey,
                "https://api.siliconflow.cn/v1", true, false, 128000, 1
        );
    }

    public static AiProviderConfig siliconFlowQwenConfig(String apiKey) {
        return new AiProviderConfig(
                "硅基流动", "Qwen/Qwen2.5-7B-Instruct", apiKey,
                "https://api.siliconflow.cn/v1", true, false, 32000, 1
        );
    }

    // ===== 智谱 AI (GLM) =====
    public static AiProviderConfig zhipuFlashConfig(String apiKey) {
        return new AiProviderConfig(
                "智谱AI", "glm-4-flash", apiKey,
                "https://open.bigmodel.cn/api/paas/v4", true, false, 128000, 1
        );
    }

    public static AiProviderConfig zhipuPlusConfig(String apiKey) {
        return new AiProviderConfig(
                "智谱AI", "glm-4-plus", apiKey,
                "https://open.bigmodel.cn/api/paas/v4", true, false, 128000, 2
        );
    }

    // ===== 零一万物 Yi =====
    public static AiProviderConfig yiLightningConfig(String apiKey) {
        return new AiProviderConfig(
                "零一万物", "yi-lightning", apiKey,
                "https://api.lingyiwanwu.com/v1", true, false, 32000, 1
        );
    }

    public static AiProviderConfig yiLargeConfig(String apiKey) {
        return new AiProviderConfig(
                "零一万物", "yi-large", apiKey,
                "https://api.lingyiwanwu.com/v1", true, false, 64000, 2
        );
    }

    public AiProviderConfig withEnabled(boolean enabled) {
        return new AiProviderConfig(this.providerName, this.modelName, this.apiKey,
                this.baseUrl, enabled, this.localOnly,
                this.maxInputLength, this.costLevel);
    }
}