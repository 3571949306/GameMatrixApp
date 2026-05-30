package com.gamecenter.app.vpn.model

import java.util.UUID

enum class ProtocolType { VMess, VLESS, Trojan, Shadowsocks }

/**
 * 节点数据模型。
 *
 * @param id            唯一标识
 * @param name          显示名称
 * @param type          协议类型
 * @param address       服务器地址
 * @param port          端口
 * @param uuid          VMess/VLESS 的 UUID
 * @param alterId       VMess alterId
 * @param security      加密方式（aes-128-gcm、chacha20-ietf-poly1305 等）
 * @param password      Trojan/Shadowsocks 密码
 * @param network       传输协议（tcp、ws、h2、quic 等）
 * @param tls           是否启用 TLS
 * @param sni           TLS SNI
 * @param wsPath        WebSocket 路径
 * @param wsHeaders     WebSocket 请求头
 * @param subscriptionUrl 所属订阅 URL
 */
data class Node(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: ProtocolType,
    val address: String,
    val port: Int,
    val uuid: String? = null,
    val alterId: Int? = null,
    val security: String? = null,
    val password: String? = null,
    val network: String? = null,
    val tls: Boolean = false,
    val sni: String? = null,
    val wsPath: String? = null,
    val wsHeaders: Map<String, String>? = null,
    val subscriptionUrl: String? = null
)