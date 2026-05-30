package com.gamecenter.app.vpn.protocol

import android.content.Context
import com.gamecenter.app.vpn.model.Node
import com.gamecenter.app.vpn.model.ProtocolType
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

class VmessModule : ProtocolModule {
    private var currentNode: Node? = null
    private var socket: Socket? = null
    private var connected = false

    override fun init(context: Context) {}
    override fun prepare(node: Node): Boolean {
        if (node.type != ProtocolType.VMess || node.address.isEmpty() || node.port <= 0 || node.uuid.isNullOrEmpty()) return false
        currentNode = node; return true
    }
    override fun connect(): Pair<InputStream, OutputStream> {
        if (connected) throw IllegalStateException("already connected")
        val node = currentNode ?: throw IllegalStateException("not prepared")
        socket = Socket(node.address, node.port); connected = true
        return Pair(socket!!.getInputStream(), socket!!.getOutputStream())
    }
    override fun disconnect() { try { socket?.close() } catch (_: Exception) {}; socket = null; connected = false; currentNode = null }
    override fun getStatus() = if (connected) "connected" else "disconnected"
}