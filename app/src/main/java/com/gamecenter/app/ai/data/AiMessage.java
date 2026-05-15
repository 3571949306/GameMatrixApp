package com.gamecenter.app.ai.data;

/**
 * AI 消息模型 — 存储单条对话消息。
 */
public final class AiMessage {

    public final String id;
    public final String role;      // "user" / "assistant" / "system"
    public final String content;
    public final long timestamp;
    public final String taskType;  // 如 "ocr", "summary", "translate", "chat" 等
    public final String source;    // "local" / "cloud"

    public AiMessage(String id, String role, String content, long timestamp, String taskType, String source) {
        this.id = id;
        this.role = role;
        this.content = content;
        this.timestamp = timestamp;
        this.taskType = taskType;
        this.source = source;
    }

    public AiMessage(String role, String content, String taskType, String source) {
        this(java.util.UUID.randomUUID().toString(), role, content, System.currentTimeMillis(), taskType, source);
    }
}