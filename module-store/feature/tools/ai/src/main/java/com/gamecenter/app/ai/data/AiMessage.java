package com.gamecenter.app.ai.data;

/**
 * AI 消息模型 — 存储单条对话消息。
 *
 * <p>你可以把 AiMessage 想象成聊天软件中的一条消息气泡：
 * 每条消息都有发送者（用户/AI/系统）、内容、时间等信息。
 * 用户和 AI 之间的对话就是由一条条 AiMessage 组成的。</p>
 *
 * <p>该类是 AI 对话系统中的核心数据结构，用于表示用户与 AI 之间的单条消息。
 * 采用不可变设计（final 类 + final 字段），确保消息一旦创建就不会被修改，
 * 从而保证对话历史的数据一致性。</p>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>使用 final 修饰类和所有字段，保证线程安全和不可变性</li>
 *   <li>通过 role 字段区分消息角色（用户/助手/系统），兼容 OpenAI 对话协议</li>
 *   <li>通过 source 字段标识消息来源（本地/云端），支持混合推理架构</li>
 * </ul>
 */
public final class AiMessage {

    // 消息唯一标识，用于消息去重和追踪（就像每条微信消息都有唯一 ID）
    public final String id;

    // 消息角色，取值为 "user"（用户）、"assistant"（AI助手）、"system"（系统提示）
    public final String role;

    // 消息文本内容
    public final String content;

    // 消息创建的时间戳（毫秒级 Unix 时间，如 1700000000000）
    public final long timestamp;

    // 任务类型标识，如 "ocr"（文字识别）、"summary"（摘要）、"translate"（翻译）、"chat"（对话）等
    public final String taskType;

    // 消息来源标识，"local" 表示本地端侧模型，"cloud" 表示云端 API
    public final String source;

    /**
     * 全参数构造方法，用于从已知数据（如数据库恢复、反序列化）创建消息实例。
     *
     * @param id        消息唯一标识
     * @param role      消息角色（"user" / "assistant" / "system"）
     * @param content   消息文本内容
     * @param timestamp 消息时间戳（毫秒）
     * @param taskType  任务类型标识
     * @param source    消息来源（"local" / "cloud"）
     */
    public AiMessage(String id, String role, String content, long timestamp, String taskType, String source) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
        this.taskType = taskType;
        this.source = source;
    }

    /**
     * 便捷构造方法，自动生成 UUID 和当前时间戳。
     * 适用于新建消息场景，无需手动指定 id 和 timestamp。
     *
     * @param role     消息角色（"user" / "assistant" / "system"）
     * @param content  消息文本内容
     * @param taskType 任务类型标识
     * @param source   消息来源（"local" / "cloud"）
     */
    public AiMessage(String role, String content, String taskType, String source) {
        this(java.util.UUID.randomUUID().toString(), role, content, System.currentTimeMillis(), taskType, source);
    }
}
