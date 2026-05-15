package com.gamecenter.app.ai.data;

/**
 * AI 任务模型 — 描述一次 AI 处理任务。
 */
public final class AiTask {

    public final String taskId;
    public final String taskType;  // "ocr", "summary", "translate", "rewrite", "chat"
    public final String input;
    public String output;
    public String status;          // "pending", "running", "completed", "failed"
    public final long createdAt;
    public int costLevel;          // 0=免费, 1=低, 2=中, 3=高

    public AiTask(String taskId, String taskType, String input, String status, long createdAt, int costLevel) {
        this.taskId = taskId;
        this.taskType = taskType;
        this.input = input;
        this.status = status;
        this.createdAt = createdAt;
        this.costLevel = costLevel;
    }

    public AiTask(String taskType, String input) {
        this(java.util.UUID.randomUUID().toString(), taskType, input, "pending", System.currentTimeMillis(), 0);
    }

    public boolean isCompleted() {
        return "completed".equals(status);
    }

    public boolean isFailed() {
        return "failed".equals(status);
    }
}