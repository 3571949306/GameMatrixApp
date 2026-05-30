package com.gamecenter.app.vpn.protocol

import android.content.Context
import com.gamecenter.app.vpn.model.Node
import java.io.InputStream
import java.io.OutputStream

/**
 * 协议模块统一接口。
 * 主模块（VpnFragment / ScientificVpnService）只通过此接口与协议实现交互。
 */
interface ProtocolModule {

    /** 初始化模块 */
    fun init(context: Context)

    /** 验证并缓存节点信息，返回是否有效 */
    fun prepare(node: Node): Boolean

    /** 建立与远端服务器的连接，返回输入/输出流供 VpnService 隧道使用 */
    @Throws(Exception::class)
    fun connect(): Pair<InputStream, OutputStream>

    /** 断开连接并释放资源 */
    fun disconnect()

    /** 返回当前状态字符串 */
    fun getStatus(): String
}