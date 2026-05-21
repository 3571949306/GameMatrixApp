package com.gamecenter.app.network;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 云中转（Relay）客户端辅助工具类，提供客户端侧的通用请求体构建和URL解析功能。
 *
 * <p>打个比方：如果云中转服务器是一个"邮局"，那么这个类就是帮你填写"寄件单"的工具。
 * 每次你要通过邮局寄信（发送消息），都需要在信封上写好房间码、角色（客户端）、
 * 客户端ID和令牌等信息，这个类就是帮你自动填写这些信息的。</p>
 *
 * <p>在网络模块中的角色：这是云中转模式客户端侧的"表单填写员"，
 * 与 {@link RelayHostHelper}（主机侧的表单填写员）互为对偶。
 * 两者分别构建不同角色的请求体，确保客户端和主机端发往中转服务器的请求格式正确。</p>
 * <p>
 * 此类为final类且构造方法私有，仅包含静态工具方法，不可实例化。
 * 主要被 {@link GameSocketClient} 在云中转模式下调用。
 * <p>
 * 关键设计决策：
 * <ul>
 *   <li>将客户端侧的公共逻辑抽取为独立工具类，避免代码重复</li>
 *   <li>与 {@link RelayHostHelper} 中的主机端逻辑对称，两者分别构建不同角色的请求体</li>
 * </ul>
 */
final class RelayClientHelper {

    /** 私有构造方法，防止实例化 */
    private RelayClientHelper() {
    }

    /**
     * 构建云中转客户端请求的基础请求体。
     * <p>
     * 包含房间码、客户端角色标识、客户端ID和认证令牌，
     * 用于客户端向中转服务器发送请求时的身份验证。
     *
     * @param roomCode    房间码，标识要加入的中转房间
     * @param clientId    客户端ID，由中转服务器在加入房间时分配
     * @param clientToken 客户端令牌，用于身份验证
     * @return 包含认证信息的JSONObject
     * @throws JSONException 如果构建JSON失败
     */
    static JSONObject baseBody(String roomCode, int clientId, String clientToken) throws JSONException {
        JSONObject body = new JSONObject();
        body.put("roomCode", roomCode);
        body.put("role", "client");
        body.put("clientId", clientId);
        body.put("token", clientToken);
        return body;
    }

    /**
     * 解析中转服务器的基础URL。
     * <p>
     * 如果传入的baseUrl为null或空白字符串，则返回默认地址 {@link RelayHttpClient#DEFAULT_BASE_URL}。
     * 否则返回去除首尾空白后的baseUrl。
     *
     * @param baseUrl 用户传入的中转服务器URL，可为null
     * @return 有效的中转服务器基础URL
     */
    static String resolveBaseUrl(String baseUrl) {
        return baseUrl != null && !baseUrl.trim().isEmpty()
                ? baseUrl.trim()
                : RelayHttpClient.DEFAULT_BASE_URL;
    }
}
