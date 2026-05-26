package com.gamecenter.app.ai.data;

/**
 * AI 任务模型 — 描述一次 AI 处理任务的完整生命周期。
 *
 * <p>你可以把 AiTask 想象成一张"工单"：
 * 用户提交一个请求（比如"帮我翻译这段话"），系统就创建一张工单，
 * 工单上记录着任务类型、输入内容、当前状态、处理结果等信息。
 * 随着任务被处理，工单上的状态会从"待处理"变成"运行中"，最终变成"已完成"或"失败"。</p>
 *
 * <p>该类是 AI 任务调度系统的核心数据结构，用于跟踪从任务创建到完成的全过程。
 * 与 AiMessage 不同，AiTask 侧重于任务的状态管理和成本追踪，
 * 而 AiMessage 侧重于对话内容的记录。</p>
 *
 * <p>关键设计决策：</p>
 * <ul>
 *   <li>类声明为 final，但 output、status、costLevel 字段为可变，
 *       以支持任务执行过程中的状态更新（如输出填充、状态流转）</li>
 *   <li>taskId 使用 UUID 保证全局唯一，支持分布式场景下的任务追踪</li>
 *   <li>costLevel 分级机制与 AiProviderConfig 保持一致，便于成本感知路由</li>
 *   <li>状态机模型：pending → running → completed/failed，确保任务状态流转有序</li>
 * </ul>
 */
public final class AiTask {

    // 任务唯一标识，使用 UUID 生成，就像快递单号一样全局唯一
    public final String taskId;

    // 任务类型，如 "ocr"（文字识别）、"summary"（摘要）、"translate"（翻译）、"rewrite"（改写）、"chat"（对话）
    public final String taskType;

    // 任务输入内容，如待识别的文本、待翻译的内容等
    public final String input;

    // 任务输出结果，任务完成后由执行器填充；初始为 null
    public String output;

    // 任务当前状态（PENDING/RUNNING/COMPLETED/FAILED）
    public TaskStatus status;

    // 任务创建时间戳（毫秒级 Unix 时间，如 1700000000000）
    public final long createdAt;

    // 成本等级：0=免费, 1=低, 2=中, 3=高，用于成本控制和路由决策
    public int costLevel;

    /**
     * 全参数构造方法，用于从已知数据（如数据库恢复）创建任务实例。
     *
     * @param taskId    任务唯一标识
     * @param taskType  任务类型
     * @param input     任务输入内容
     * @param status    任务当前状态
     * @param createdAt 任务创建时间戳
     * @param costLevel 成本等级
     */
    public AiTask(String taskId, String taskType, String input, TaskStatus status, long createdAt, int costLevel) {
        this.taskId = taskId;
        this.taskType = taskType;
        this.input = input;
        this.status = status;
        this.createdAt = createdAt;
        this.costLevel = costLevel;
    }

    /**
     * 便捷构造方法，自动生成 UUID、设置初始状态为"待处理"、记录当前时间戳、成本等级默认为 0（免费）。
     * 适用于新建任务场景，不需要手动指定那些可以自动生成的字段。
     *
     * @param taskType 任务类型
     * @param input    任务输入内容
     */
    public AiTask(String taskType, String input) {
        this(java.util.UUID.randomUUID().toString(), taskType, input, TaskStatus.PENDING, System.currentTimeMillis(), 0);
    }

    // 判断任务是否已成功完成
    public boolean isCompleted() {
        return status == TaskStatus.COMPLETED;
    }

    // 判断任务是否已失败
    public boolean isFailed() {
        return status == TaskStatus.FAILED;
    }
}
