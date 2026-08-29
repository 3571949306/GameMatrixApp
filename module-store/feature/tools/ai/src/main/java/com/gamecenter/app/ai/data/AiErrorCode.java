package com.gamecenter.app.ai.data;

/**
 * AI 错误码常量类 — 集中定义 AI 模块中可能出现的各种错误标识。
 *
 * <p>你可以把错误码想象成医院里的科室编号：
 * 不同的编号代表不同类型的问题，方便快速定位和处理。</p>
 *
 * <p>在 AI 模块中的作用：当 AI 任务失败时，AiResult 中会携带一个错误码，
 * 上层代码（如 AiFragment）可以根据错误码决定如何提示用户：
 * 是显示"网络不给力"、还是"额度用完了"、还是"需要设置密钥"等。</p>
 *
 * <p>设计说明：此类使用 private 构造方法，不允许创建实例，
 * 所有错误码都是静态常量，直接通过类名访问，如 {@code AiErrorCode.NETWORK_ERROR}。</p>
 */
public final class AiErrorCode {

    /** 网络错误：设备没有联网，或者网络请求超时、连接被拒绝等 */
    public static final String NETWORK_ERROR = "NETWORK_ERROR";

    /** 额度超限：今日免费调用次数已用完，需要等待明天重置或配置自己的 API Key */
    public static final String QUOTA_EXCEEDED = "QUOTA_EXCEEDED";

    /** 未配置 API Key：用户还没有填写云端 AI 服务的密钥，无法使用云端功能 */
    public static final String NO_API_KEY = "NO_API_KEY";

    /** 本地模型内存不足：设备总内存太小，无法安全加载本地 Gemma 模型（需要至少 3GB） */
    public static final String LOCAL_LLM_LOW_MEMORY = "LOCAL_LLM_LOW_MEMORY";

    /** 本地模型输出退化：模型输出了乱码、重复字符、循环段落等无意义内容 */
    public static final String LOCAL_LLM_DEGENERATED_OUTPUT = "LOCAL_LLM_DEGENERATED_OUTPUT";

    /** 本地模型推理错误：本地 Gemma 模型在推理过程中发生了异常 */
    public static final String LOCAL_LLM_ERROR = "LOCAL_LLM_ERROR";

    /**
     * 本地模式不可用：本次请求没有走网络，调用方可提示用户切换云端并重新确认。
     * 该错误码用于区分“本地失败”与“已获得授权但云端请求失败”。
     */
    public static final String LOCAL_ONLY_UNAVAILABLE = "LOCAL_ONLY_UNAVAILABLE";

    /** HTTP 请求错误：云端 API 返回了非 2xx 的 HTTP 状态码（如 401 未授权、429 限流等） */
    public static final String HTTP_ERROR = "HTTP_ERROR";

    // 私有构造方法，防止外部创建实例（因为所有错误码都是静态常量，不需要实例化）
    private AiErrorCode() {}
}
