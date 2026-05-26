package com.gamecenter.app.core.common

import java.io.InputStream
import java.io.OutputStream

/**
 * VPN 服务代理接口。
 */
interface VpnDelegate {

    /** 协议连接返回的流对 */
    data class Tunnel(val input: InputStream, val output: OutputStream)

    /** 根据节点 JSON 建立协议连接，返回隧道流 */
    @Throws(Exception::class)
    fun connect(nodeJson: String): Tunnel

    /** 断开连接 */
    fun disconnect()
}