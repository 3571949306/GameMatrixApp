package com.gamecenter.app.network.p2p

import com.gamecenter.app.network.p2p.transport.quic.QuicKcpChannel
import com.gamecenter.app.network.p2p.utils.P2pCallbackDispatcher
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class QuicKcpChannelTest {

    @Before
    fun setUp() {
        P2pCallbackDispatcher.setDirectExecutionForTesting(true)
    }

    @Test
    fun testQuicActiveHandshakeAndStreamDataTransfer() {
        val serverSocket = DatagramSocket(0)
        val clientSocket = DatagramSocket(0)
        val serverPort = serverSocket.localPort

        val serverConnectedLatch = CountDownLatch(1)
        val clientConnectedLatch = CountDownLatch(1)
        val serverDataReceivedLatch = CountDownLatch(1)
        val clientDataReceivedLatch = CountDownLatch(1)

        var serverReceivedPayload: String? = null
        var clientReceivedPayload: String? = null

        // 1. 房主作为 QUIC 服务端监听
        val serverChannel = QuicKcpChannel(true, serverSocket, object : QuicKcpChannel.QuicListener {
            override fun onQuicConnected(connectionId: Long, remoteAddress: InetAddress, remotePort: Int, rttMs: Long) {
                serverConnectedLatch.countDown()
            }

            override fun onStreamDataReceived(streamId: Int, payload: ByteArray) {
                serverReceivedPayload = String(payload, Charsets.UTF_8)
                serverDataReceivedLatch.countDown()
            }

            override fun onConnectionMigrated(newAddress: InetAddress, newPort: Int) {}
            override fun onQuicClosed(reason: String) {}
        })
        serverChannel.startServer()

        // 2. 访客作为 QUIC 客户端主动出击开门
        val clientChannel = QuicKcpChannel(false, clientSocket, object : QuicKcpChannel.QuicListener {
            override fun onQuicConnected(connectionId: Long, remoteAddress: InetAddress, remotePort: Int, rttMs: Long) {
                clientConnectedLatch.countDown()
            }

            override fun onStreamDataReceived(streamId: Int, payload: ByteArray) {
                clientReceivedPayload = String(payload, Charsets.UTF_8)
                clientDataReceivedLatch.countDown()
            }

            override fun onConnectionMigrated(newAddress: InetAddress, newPort: Int) {}
            override fun onQuicClosed(reason: String) {}
        })
        clientChannel.connectAsClient(InetAddress.getByName("127.0.0.1"), serverPort)

        // 3. 验证 1-RTT 握手成功
        assertTrue("Server should connect via QUIC", serverConnectedLatch.await(3, TimeUnit.SECONDS))
        assertTrue("Client should connect via QUIC", clientConnectedLatch.await(3, TimeUnit.SECONDS))

        // 4. 客户端向服务端发送流数据
        val clientMsg = "QUIC_MOVE:9,9:BLACK"
        clientChannel.sendStreamData(0, clientMsg.toByteArray(Charsets.UTF_8))
        assertTrue("Server should receive stream data", serverDataReceivedLatch.await(2, TimeUnit.SECONDS))
        assertEquals(clientMsg, serverReceivedPayload)

        // 5. 服务端向客户端回信
        val serverMsg = "QUIC_MOVE:9,10:WHITE"
        serverChannel.sendStreamData(0, serverMsg.toByteArray(Charsets.UTF_8))
        assertTrue("Client should receive stream data", clientDataReceivedLatch.await(2, TimeUnit.SECONDS))
        assertEquals(serverMsg, clientReceivedPayload)

        // 6. 关闭
        clientChannel.close()
        serverChannel.close()
    }
}
