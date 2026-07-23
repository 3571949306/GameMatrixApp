package com.gamecenter.app.games.chinesechess;

/**
 * 中国象棋战术模式枚举。
 *
 * <p>定义常见的战术主题，用于分类课程和练习题。</p>
 */
public enum TacticalPattern {

    /** 将军基础：利用棋子进行直接将军 */
    CHECK_BASICS("将军基础"),

    /** 抽将技巧：移动一个棋子后露出后面的棋子进行将军 */
    DISCOVERED_CHECK("抽将技巧"),

    /** 双将杀法：同时由两个棋子将军，对方无法同时应对 */
    DOUBLE_CHECK("双将杀法"),

    /** 卧槽马：马跳到对方底象前一格的位置，配合其他棋子形成杀招 */
    KNIGHT_CRADLE("卧槽马"),

    /** 铁门栓：炮在中路配合其他棋子封锁对方将帅的移动 */
    IRON_BOLT("铁门栓");

    private final String displayName;

    TacticalPattern(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
