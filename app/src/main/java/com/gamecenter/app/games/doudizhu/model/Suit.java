package com.gamecenter.app.games.doudizhu.model;

import java.io.Serializable;

/**
 * 扑克牌花色枚举，定义斗地主游戏中所有可能的花色类型。
 * <p>
 * 职责：
 * <ul>
 *   <li>枚举四种常规花色（黑桃、红桃、梅花、方块）和两种王牌花色</li>
 *   <li>提供花色代号(code)用于拼接 UI 资源名称</li>
 *   <li>提供花色英文名称(name)用于资源路径和标识</li>
 *   <li>提供 isJoker() 方法区分常规花色与王牌花色</li>
 * </ul>
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>将小王和大王也定义为花色(JOKER_small/JOKER_big)，而非仅作为牌值(Rank)，
 *       这是因为每张牌需要同时拥有花色和牌值两个属性，王牌没有传统花色概念，
 *       因此用特殊花色值来表示，使 Card 模型统一</li>
 *   <li>花色代号(code)的设计：s/h/c/d 分别对应 spade/heart/club/diamond 的首字母，
 *       x 代表小王（避免与已有的 s/h/c/d 冲突），d_j 代表大王（diamond_joker 的缩写），
 *       这些代号用于拼接图片资源文件名（如 poker_s_3 表示黑桃3）</li>
 *   <li>枚举实现了 Serializable 接口（通过 Enum 隐式实现），
 *       可以在 Android 的 Intent 和 Bundle 中安全传递</li>
 * </ul>
 */
public enum Suit {
    /**
     * 黑桃(Spade) —— 花色代号为 "s"，英文名 "spade"。
     * 在 UI 资源中，黑桃3的图片名为 poker_s_3。
     */
    SPADE("s", "spade"),

    /**
     * 红桃(Heart) —— 花色代号为 "h"，英文名 "heart"。
     * 在 UI 资源中，红桃K的图片名为 poker_h_K。
     */
    HEART("h", "heart"),

    /**
     * 梅花(Club) —— 花色代号为 "c"，英文名 "club"。
     * 在 UI 资源中，梅花7的图片名为 poker_c_7。
     */
    CLUB("c", "club"),

    /**
     * 方块(Diamond) —— 花色代号为 "d"，英文名 "diamond"。
     * 在 UI 资源中，方块A的图片名为 poker_d_A。
     */
    DIAMOND("d", "diamond"),

    /**
     * 小王(Small Joker) —— 花色代号为 "x"，英文名 "joker_small"。
     * 使用 "x" 作为代号是为了避免与常规花色代号(s/h/c/d)冲突。
     * 小王的牌面图片名为 poker_joker_small（由 Card.getResName() 特殊处理）。
     */
    JOKER_small("x", "joker_small"),

    /**
     * 大王(Big Joker) —— 花色代号为 "d_j"，英文名 "joker_big"。
     * 使用 "d_j" 作为代号（diamond_joker 的缩写），与常规花色区分。
     * 大王的牌面图片名为 poker_joker_big（由 Card.getResName() 特殊处理）。
     */
    JOKER_big("d_j", "joker_big");

    /**
     * 花色的简写代号，用于拼接 UI 资源名称。
     * 例如 "s" 对应黑桃，"h" 对应红桃。
     */
    private final String code;

    /**
     * 花色的完整英文名称，用于资源路径标识和日志输出。
     * 例如 "spade" 对应黑桃，"heart" 对应红桃。
     */
    private final String name;

    /**
     * 枚举构造函数。
     *
     * @param code 花色的简写代号，用于资源文件名拼接
     * @param name 花色的完整英文名称，用于资源路径和标识
     */
    Suit(String code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 获取花色的简写代号。
     * <p>
     * 代号用于拼接扑克牌图片的资源名称，格式为 poker_[code]_[rank]。
     * 例如：黑桃3 → poker_s_3，红桃K → poker_h_K。
     *
     * @return 花色代号字符串，如 "s"(黑桃)、"h"(红桃)、"c"(梅花)、"d"(方块)、"x"(小王)、"d_j"(大王)
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取花色的完整英文名称。
     * <p>
     * 主要用于日志输出和资源路径标识。
     *
     * @return 花色英文名称，如 "spade"、"heart"、"club"、"diamond"、"joker_small"、"joker_big"
     */
    public String getName() {
        return name;
    }

    /**
     * 判断当前花色是否为王牌花色（小王或大王）。
     * <p>
     * 王牌花色在游戏逻辑中有特殊处理：
     * <ul>
     *   <li>王牌没有传统花色概念，显示名称为空</li>
     *   <li>王牌的资源名称使用特殊规则（poker_joker_small/big），不走常规拼接</li>
     *   <li>王牌不能参与顺子、连对等需要花色区分的组合</li>
     * </ul>
     *
     * @return 如果是 JOKER_small 或 JOKER_big 返回 true，否则返回 false
     */
    public boolean isJoker() {
        return this == JOKER_small || this == JOKER_big;
    }
}
