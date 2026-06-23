package com.gamecenter.app.ai.automation;

/**
 * 自动化执行结果 — 封装自动化任务的执行结果。
 * <p>
 * 该类用于返回自动化任务的执行状态和相关信息。
 * <p>
 * 注意：当前为架构预留，阶段6实现具体功能。
 */
public final class AutomationResult {

    /** 执行是否成功 */
    public final boolean success;

    /** 结果描述信息 */
    public final String message;

    /** 执行的步骤数 */
    public final int stepsExecuted;

    /** 执行耗时（毫秒） */
    public final long durationMs;

    /**
     * 私有构造方法。
     */
    private AutomationResult(boolean success, String message, int stepsExecuted, long durationMs) {
        this.success = success;
        this.message = message;
        this.stepsExecuted = stepsExecuted;
        this.durationMs = durationMs;
    }

    /**
     * 创建成功结果。
     *
     * @param stepsExecuted 执行的步骤数
     * @param durationMs 执行耗时
     * @return 成功结果
     */
    public static AutomationResult success(int stepsExecuted, long durationMs) {
        return new AutomationResult(true, "自动化任务执行成功", stepsExecuted, durationMs);
    }

    /**
     * 创建失败结果。
     *
     * @param message 错误描述
     * @return 失败结果
     */
    public static AutomationResult failure(String message) {
        return new AutomationResult(false, message, 0, 0);
    }

    /**
     * 创建"功能未实现"结果。
     * <p>
     * 用于阶段6功能尚未实现时返回。
     *
     * @return 未实现结果
     */
    public static AutomationResult notImplemented() {
        return new AutomationResult(false, "自动化功能将在阶段6实现", 0, 0);
    }

    /**
     * 创建进度更新结果。
     * <p>
     * 用于向用户展示当前执行进度。
     *
     * @param message 进度描述
     * @return 进度结果
     */
    public static AutomationResult progress(String message) {
        return new AutomationResult(false, message, 0, 0);
    }
}
