package com.gamecenter.app.games.doudizhu.model;

import java.io.Serializable;

/**
 * 扑克牌花色枚举 (Suit Enum)
 * 定义斗地主游戏中所有的花色类型
 * 其中 JOKER_small 和 JOKER_big 用于表示小王和大王
 */
public enum Suit {
    // 黑桃 (Spade) - 花色代号 s
    SPADE("s", "spade"),
    // 红桃 (Heart) - 花色代号 h
    HEART("h", "heart"),
    // 梅花 (Club) - 花色代号 c
    CLUB("c", "club"),
    // 方块 (Diamond) - 花色代号 d
    DIAMOND("d", "diamond"),
    // 小王 (Small Joker) - 花色代号 x
    JOKER_small("x", "joker_small"),
    // 大王 (Big Joker) - 花色代号 d_j
    JOKER_big("d_j", "joker_big");

    // 花色的简写代号，用于拼接资源名称
    private final String code;
    // 花色的完整英文名称
    private final String name;

    Suit(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 获取花色的简写代号
     * @return 花色代码，如 "s", "h", "c", "d", "x", "d_j"
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取花色的完整名称
     * @return 花色名称，如 "spade", "heart"
     */
    public String getName() {
        return name;
    }

    /**
     * 判断当前花色是否为王牌（小王或大王）
     * @return 是否为王牌
     */
    public boolean isJoker() {
        return this == JOKER_small || this == JOKER_big;
    }
}
