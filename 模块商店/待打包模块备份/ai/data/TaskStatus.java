package com.gamecenter.app.ai.data;

/**
 * 任务状态枚举 — 描述一个 AI 任务当前处于哪个阶段。
 *
 * <p>你可以把 AI 任务想象成快递包裹的物流状态：
 * 下单后是"待处理"，运输中是"运行中"，签收了是"已完成"，出问题了就是"失败"。</p>
 *
 * <p>任务的状态变化遵循一个简单的流程（就像流水线）：</p>
 * <pre>
 *   PENDING（待处理）→ RUNNING（运行中）→ COMPLETED（已完成）
 *                                      ↘ FAILED（失败）
 * </pre>
 *
 * <p>在 AI 模块中的作用：AiTaskRouter 在调度任务时，会根据这个状态来跟踪任务进展，
 * UI 层（AiFragment）也可以通过状态来决定显示"加载中"还是"结果"。</p>
 */
public enum TaskStatus {

    // 每个枚举值都有一个对应的字符串表示，方便存储到数据库或日志中

    /** 待处理：任务刚创建，还没开始执行（就像刚下单的快递） */
    PENDING("pending"),

    /** 运行中：任务正在被 AI 处理（就像快递正在运输中） */
    RUNNING("running"),

    /** 已完成：任务处理成功，可以获取结果了（就像快递已签收） */
    COMPLETED("completed"),

    /** 失败：任务处理出错，无法得到结果（就像快递丢失或退回） */
    FAILED("failed");

    // 保存枚举值对应的字符串，比如 "pending"、"running" 等
    private final String value;

    // 枚举的构造方法，每个枚举值创建时传入自己的字符串表示
    TaskStatus(String value) {
        this.value = value;
    }

    /**
     * 获取状态的字符串表示。
     * 比如用于保存到数据库或打印日志时，需要的是 "pending" 而不是 PENDING。
     *
     * @return 状态的字符串值，如 "pending"、"running" 等
     */
    public String getValue() {
        return value;
    }

    /**
     * 根据字符串值找到对应的枚举对象。
     * 比如从数据库读出 "completed" 字符串，需要转换回 TaskStatus.COMPLETED 枚举。
     *
     * <p>如果传入 null 或无法匹配的字符串，默认返回 PENDING（安全降级），
     * 避免程序因为未知状态而崩溃。</p>
     *
     * @param value 状态的字符串值，如 "pending"、"completed"
     * @return 对应的 TaskStatus 枚举对象；无法匹配时返回 PENDING
     */
    public static TaskStatus fromValue(String value) {
        if (value == null) return PENDING;
        for (TaskStatus s : values()) {
            if (s.value.equals(value)) return s;
        }
        return PENDING;
    }
}
