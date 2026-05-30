package com.gamecenter.app.games.model.enums;

/**
 * 成就条件类型枚举
 */
public enum ConditionType {
    /** 得分达到指定值 */
    SCORE,
    /** 连胜次数达到指定值 */
    WIN_STREAK,
    /** 游戏局数达到指定值 */
    PLAY_COUNT,
    /** 特定事件触发 */
    EVENT,
    /** 通关指定关数 */
    LEVEL_COMPLETE,
    /** 自定义条件 */
    CUSTOM
}
