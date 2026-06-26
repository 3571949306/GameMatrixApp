package com.gamecenter.app.games.model.enums;

/**
 * 成就触发条件类型枚举。
 *
 * <p>P0 修复：从原 :module-store:feature:games:games 模块迁回。
 * 当前实现无消费者，仅作类型占位。</p>
 */
public enum ConditionType {
    WIN_COUNT,
    STREAK,
    SCORE_THRESHOLD,
    TIME_UNDER,
    SPECIAL
}