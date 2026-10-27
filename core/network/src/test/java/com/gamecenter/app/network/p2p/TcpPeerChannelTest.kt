package com.gamecenter.app.network.p2p

import com.gamecenter.app.network.p2p.model.Candidate
import com.gamecenter.app.network.p2p.model.CandidateType
import com.gamecenter.app.network.p2p.model.NatType
import com.gamecenter.app.network.p2p.transport.tcp.TcpPeerChannel
import com.gamecenter.app.network.p2p.utils.P2pCallbackDispatcher
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TcpPeerChannelTest {

    @Before
    fun setUp() {
        P2pCallbackDispatcher.setDirectExecutionForTesting(true)
    }

    @Test
    fun testTcpServerClientActiveHandshakeAndDataTransfer() {
        // 动态分配一个空闲本地端口
        val freePort = ServerSocket(0).use { it.localPort }

        val serverConnectedLatch = CountDownLatch(1)
        val clientConnectedLatch = CountDownLatch(1)
        val clientReceivedDataLatch = CountDownLatch(1)
        val serverReceivedDataLatch = CountDownLatch(1)

        var clientReceivedMessage: String? = null
        var serverReceivedMessage: String? = null

        // 1. 房主作为服务端启动监听
        val serverChannel = TcpPeerChannel(true, freePort, object : TcpPeerChannel.TcpListener {
            override fun onTcpConnected(socket: Socket, isServerRole: Boolean) {
                assertTrue(isServerRole)
                serverConnectedLatch.countDown()
            }

            override fun onTcpMessageReceived(message: ByteArray) {
                serverReceivedMessage = String(message, Charsets.UTF_8)
                serverReceivedDataLatch.countDown()
            }

            override fun onTcpDisconnected(reason: String) {}
        })
        serverChannel.startServer()

        Thread.sleep(50) // 等待服务端启动

        // 2. 访客作为客户端主动连接服务端 (模拟像看 B 站一样主动出击)
        val clientPort = ServerSocket(0).use { it.localPort }
        val candidates = listOf(
            Candidate(CandidateType.HOST_IPV4, "127.0.0.1", freePort, 100, NatType.OPEN_INTERNET)
        )

        val clientChannel = TcpPeerChannel(false, clientPort, object : TcpPeerChannel.TcpListener {
            override fun onTcpConnected(socket: Socket, isServerRole: Boolean) {
                assertFalse(isServerRole)
                clientConnectedLatch.countDown()
            }

            override fun onTcpMessageReceived(message: ByteArray) {
                clientReceivedMessage = String(message, Charsets.UTF_8)
                clientReceivedDataLatch.countDown()
            }

            override fun onTcpDisconnected(reason: String) {}
        })
        clientChannel.connectAsClient(candidates, 2000)

        // 3. 验证双向 TCP 握手成功
        assertTrue("Server should be connected", serverConnectedLatch.await(3, TimeUnit.SECONDS))
        assertTrue("Client should be connected", clientConnectedLatch.await(3, TimeUnit.SECONDS))

        // 4. 服务端向客户端发送对局消息 (五子棋落子)
        val move1 = "MOVE:7,7:BLACK"
        serverChannel.sendReliable(move1.toByteArray(Charsets.UTF_8))
        assertTrue("Client should receive move1", clientReceivedDataLatch.await(2, TimeUnit.SECONDS))
        assertEquals(move1, clientReceivedMessage)

        // 5. 客户端向服务端回信落子
        val move2 = "MOVE:7,8:WHITE"
        clientChannel.sendReliable(move2.toByteArray(Charsets.UTF_8))
        assertTrue("Server should receive move2", serverReceivedDataLatch.await(2, TimeUnit.SECONDS))
        assertEquals(move2, serverReceivedMessage)

        // 6. 正常关闭
        serverChannel.close()
        clientChannel.close()
    }
}
