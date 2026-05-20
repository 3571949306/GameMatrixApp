package com.gamecenter.app.ai.data;

/**
 * AI 提供商配置模型 — 描述一个可用的 AI 模型提供商及其配置参数。
 *
 * <p>该类封装了与 AI 服务商通信所需的全部配置信息，包括认证密钥、接口地址、
 * 模型名称以及运行约束等。所有提供商均采用 OpenAI 兼容接口格式（/chat/completions），
 * 实现了统一的调用协议，便于在运行时动态切换不同的 AI 后端。</p>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>不可变设计（final 类 + final 字段），配置创建后不可修改，需变更时通过 withEnabled() 创建新实例</li>
 *   <li>使用静态工厂方法创建各提供商的默认配置，集中管理模型参数和端点地址</li>
 *   <li>costLevel 分级（0=免费, 1=低, 2=中, 3=高），用于成本控制和智能路由决策</li>
 *   <li>localOnly 标识端侧模型，与云端模型区分，支持离线场景</li>
 * </ul>
 */
public final class AiProviderConfig {

    /** 提供商显示名称，如 "OpenAI"、"DeepSeek"、"阿里云通义" 等 */
    public final String providerName;

    /** 模型标识名称，对应 API 调用时的 model 参数，如 "gpt-4o-mini"、"deepseek-chat" */
    public final String modelName;

    /** API 认证密钥，云端模型必填，本地模型为空字符串 */
    public final String apiKey;

    /** API 基础地址，所有提供商均使用 OpenAI 兼容的 /chat/completions 端点 */
    public final String baseUrl;

    /** 是否启用该提供商配置，用于运行时动态开关 */
    public final boolean enabled;

    /** 是否为纯本地端侧模型（无需网络），true 时表示离线可用 */
    public final boolean localOnly;

    /** 模型最大输入长度（字符数），用于输入截断和任务路由判断 */
    public final int maxInputLength;

    /** 成本等级：0=免费, 1=低, 2=中, 3=高，用于成本感知的智能路由 */
    public final int costLevel;

    /**
     * 全参数构造方法。
     *
     * @param providerName  提供商显示名称
     * @param modelName     模型标识名称
     * @param apiKey        API 认证密钥
     * @param baseUrl       API 基础地址
     * @param enabled       是否启用
     * @param localOnly     是否为纯本地模型
     * @param maxInputLength 最大输入长度
     * @param costLevel     成本等级
     */
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

    /**
     * 创建本地端侧模型的默认配置。
     * 本地模型无需 API 密钥和网络连接，输入长度限制较小（2000字符），成本为免费。
     *
     * @return 本地模型配置实例
     */
    public static AiProviderConfig localConfig() {
        return new AiProviderConfig(
                "本地", "on-device", "",
                "", true, true, 2000, 0
        );
    }

    // ===== 国外 =====

    /**
     * 创建 OpenAI GPT-4o-mini 的默认配置。
     * 适合日常轻量级任务，128K 上下文窗口，成本等级为低。
     *
     * @param apiKey OpenAI API 密钥
     * @return OpenAI 配置实例
     */
    public static AiProviderConfig openAIConfig(String apiKey) {
        return new AiProviderConfig(
                "OpenAI", "gpt-4o-mini", apiKey,
                "https://api.openai.com/v1", true, false, 128000, 1
        );
    }

    // ===== DeepSeek 深度求索 =====

    /**
     * 创建 DeepSeek 通用对话模型的默认配置。
     * 适合通用对话和文本处理，128K 上下文窗口，成本等级为低。
     *
     * @param apiKey DeepSeek API 密钥
     * @return DeepSeek 对话模型配置实例
     */
    public static AiProviderConfig deepseekConfig(String apiKey) {
        return new AiProviderConfig(
                "DeepSeek", "deepseek-chat", apiKey,
                "https://api.deepseek.com/v1", true, false, 128000, 1
        );
    }

    /**
     * 创建 DeepSeek 推理模型的默认配置。
     * 适合需要深度推理的任务，128K 上下文窗口，成本等级为中（推理模型计算量更大）。
     *
     * @param apiKey DeepSeek API 密钥
     * @return DeepSeek 推理模型配置实例
     */
    public static AiProviderConfig deepseekReasonerConfig(String apiKey) {
        return new AiProviderConfig(
                "DeepSeek", "deepseek-reasoner", apiKey,
                "https://api.deepseek.com/v1", true, false, 128000, 2
        );
    }

    // ===== 阿里云通义千问 =====

    /**
     * 创建阿里云通义千问 Plus 版本的默认配置。
     * 平衡性能与成本，32K 上下文窗口，成本等级为中。
     *
     * @param apiKey 阿里云 DashScope API 密钥
     * @return 通义千问 Plus 配置实例
     */
    public static AiProviderConfig aliyunConfig(String apiKey) {
        return new AiProviderConfig(
                "阿里云通义", "qwen-plus", apiKey,
                "https://dashscope.aliyuncs.com/compatible-mode/v1", true, false, 32000, 2
        );
    }

    /**
     * 创建阿里云通义千问 Turbo 版本的默认配置。
     * 追求低延迟和低成本，32K 上下文窗口，成本等级为低。
     *
     * @param apiKey 阿里云 DashScope API 密钥
     * @return 通义千问 Turbo 配置实例
     */
    public static AiProviderConfig aliyunTurboConfig(String apiKey) {
        return new AiProviderConfig(
                "阿里云通义", "qwen-turbo", apiKey,
                "https://dashscope.aliyuncs.com/compatible-mode/v1", true, false, 32000, 1
        );
    }

    /**
     * 创建阿里云通义千问 Max 版本的默认配置。
     * 最强性能，适合复杂任务，32K 上下文窗口，成本等级为高。
     *
     * @param apiKey 阿里云 DashScope API 密钥
     * @return 通义千问 Max 配置实例
     */
    public static AiProviderConfig aliyunMaxConfig(String apiKey) {
        return new AiProviderConfig(
                "阿里云通义", "qwen-max", apiKey,
                "https://dashscope.aliyuncs.com/compatible-mode/v1", true, false, 32000, 3
        );
    }

    // ===== 硅基流动 SiliconFlow（最具性价比，提供多种开源模型） =====

    /**
     * 创建硅基流动平台上 DeepSeek-V3 的默认配置。
     * 通过硅基流动的加速服务调用 DeepSeek-V3，128K 上下文窗口，成本等级为低。
     *
     * @param apiKey 硅基流动 API 密钥
     * @return 硅基流动 DeepSeek-V3 配置实例
     */
    public static AiProviderConfig siliconFlowDeepSeekConfig(String apiKey) {
        return new AiProviderConfig(
                "硅基流动", "Pro/deepseek-ai/DeepSeek-V3", apiKey,
                "https://api.siliconflow.cn/v1", true, false, 128000, 1
        );
    }

    /**
     * 创建硅基流动平台上 Qwen2.5-7B 的默认配置。
     * 轻量级开源模型，32K 上下文窗口，成本等级为低。
     *
     * @param apiKey 硅基流动 API 密钥
     * @return 硅基流动 Qwen2.5-7B 配置实例
     */
    public static AiProviderConfig siliconFlowQwenConfig(String apiKey) {
        return new AiProviderConfig(
                "硅基流动", "Qwen/Qwen2.5-7B-Instruct", apiKey,
                "https://api.siliconflow.cn/v1", true, false, 32000, 1
        );
    }

    // ===== 智谱 AI (GLM) =====

    /**
     * 创建智谱 AI GLM-4-Flash 的默认配置。
     * 快速响应版本，128K 上下文窗口，成本等级为低。
     *
     * @param apiKey 智谱 AI API 密钥
     * @return 智谱 GLM-4-Flash 配置实例
     */
    public static AiProviderConfig zhipuFlashConfig(String apiKey) {
        return new AiProviderConfig(
                "智谱AI", "glm-4-flash", apiKey,
                "https://open.bigmodel.cn/api/paas/v4", true, false, 128000, 1
        );
    }

    /**
     * 创建智谱 AI GLM-4-Plus 的默认配置。
     * 增强能力版本，128K 上下文窗口，成本等级为中。
     *
     * @param apiKey 智谱 AI API 密钥
     * @return 智谱 GLM-4-Plus 配置实例
     */
    public static AiProviderConfig zhipuPlusConfig(String apiKey) {
        return new AiProviderConfig(
                "智谱AI", "glm-4-plus", apiKey,
                "https://open.bigmodel.cn/api/paas/v4", true, false, 128000, 2
        );
    }

    // ===== 零一万物 Yi =====

    /**
     * 创建零一万物 Yi-Lightning 的默认配置。
     * 快速响应版本，32K 上下文窗口，成本等级为低。
     *
     * @param apiKey 零一万物 API 密钥
     * @return Yi-Lightning 配置实例
     */
    public static AiProviderConfig yiLightningConfig(String apiKey) {
        return new AiProviderConfig(
                "零一万物", "yi-lightning", apiKey,
                "https://api.lingyiwanwu.com/v1", true, false, 32000, 1
        );
    }

    /**
     * 创建零一万物 Yi-Large 的默认配置。
     * 大模型版本，64K 上下文窗口，成本等级为中。
     *
     * @param apiKey 零一万物 API 密钥
     * @return Yi-Large 配置实例
     */
    public static AiProviderConfig yiLargeConfig(String apiKey) {
        return new AiProviderConfig(
                "零一万物", "yi-large", apiKey,
                "https://api.lingyiwanwu.com/v1", true, false, 64000, 2
        );
    }

    /**
     * 创建一个仅修改 enabled 状态的新配置实例（不可变对象的"修改"模式）。
     * 保留其他所有字段不变，仅将 enabled 设置为指定值。
     *
     * @param enabled 新的启用状态
     * @return 新的配置实例，enabled 为指定值，其余字段与当前实例相同
     */
    public AiProviderConfig withEnabled(boolean enabled) {
        return new AiProviderConfig(this.providerName, this.modelName, this.apiKey,
                this.baseUrl, enabled, this.localOnly,
                this.maxInputLength, this.costLevel);
    }
}
