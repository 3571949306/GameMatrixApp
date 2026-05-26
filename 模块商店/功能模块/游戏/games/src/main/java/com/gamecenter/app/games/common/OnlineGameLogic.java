package com.gamecenter.app.games.common;

/**
 * 联机游戏逻辑接口，扩展 {@link GameLogic} 以支持网络对战。
 *
 * <p>在 {@link GameLogic} 的基础上增加了动作序列化/反序列化和远程动作验证能力，
 * 使游戏逻辑可通过网络传输并安全地处理来自远端玩家的操作。
 *
 * <p>职责：
 * <ul>
 *   <li>将本地动作序列化为字符串（如 JSON），以便通过网络发送</li>
 *   <li>将接收到的字符串反序列化为动作对象，以便在本地执行</li>
 *   <li>提供协议前缀，用于网络消息路由和分发</li>
 *   <li>验证来自远端的动作是否合法，防止作弊或网络错误</li>
 * </ul>
 *
 * <p>关键设计决策：
 * <ul>
 *   <li>序列化格式不强制要求 JSON，但推荐使用 JSON 以便调试和跨平台兼容</li>
 *   <li>协议前缀用于区分不同游戏类型的消息，避免消息混淆</li>
 *   <li>远程动作验证是安全防线，确保仅合法操作被执行</li>
 * </ul>
 *
 * @param <S> 游戏状态类型，继承自 {@link GameLogic}
 */
public interface OnlineGameLogic<S> extends GameLogic<S> {

    /**
     * 将动作对象序列化为字符串，用于网络传输。
     *
     * <p>序列化结果应包含足够信息以在远端完整还原该动作。
     *
     * @param action 要序列化的动作对象
     * @return 序列化后的字符串（通常为 JSON 格式）
     */
    String serializeAction(Object action);

    /**
     * 将字符串反序列化为动作对象，用于处理接收到的远端操作。
     *
     * <p>反序列化后应先通过 {@link #isValidRemoteAction(Object)} 验证再执行。
     *
     * @param json 序列化的动作字符串
     * @return 反序列化后的动作对象
     */
    Object deserializeAction(String json);

    /**
     * 获取协议前缀，用于标识消息所属的游戏类型。
     *
     * <p>在网络通信中，协议前缀用于消息路由，确保不同游戏的消息不会混淆。
     * 例如 "checkers"、"blackjack" 等。
     *
     * @return 协议前缀字符串
     */
    String getProtocolPrefix();

    /**
     * 验证来自远端的动作是否合法。
     *
     * <p>此方法是安全防线，应在执行远端动作前调用。
     * 验证内容包括但不限于：
     * <ul>
     *   <li>动作类型是否正确</li>
     *   <li>动作是否符合当前游戏状态（如轮到远端玩家操作）</li>
     *   <li>动作是否在规则允许范围内</li>
     * </ul>
     *
     * @param action 待验证的远程动作对象
     * @return 如果动作合法返回 true
     */
    boolean isValidRemoteAction(Object action);
}
