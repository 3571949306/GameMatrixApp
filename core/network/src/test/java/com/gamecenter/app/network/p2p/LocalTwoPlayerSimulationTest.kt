package com.gamecenter.app.network.p2p

import com.gamecenter.app.network.p2p.model.Candidate
import com.gamecenter.app.network.p2p.model.CandidateType
import com.gamecenter.app.network.p2p.model.NatType
import com.gamecenter.app.network.p2p.utils.P2pCallbackDispatcher
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LocalTwoPlayerSimulationTest {

    @Before
    fun setUp() {
        P2pCallbackDispatcher.setDirectExecutionForTesting(true)
    }

    @Test
    fun testTwoPlayerSimulationViaTcpHostGuestMode() {
        val hostSession = P2pRoomSession("GAME88", true, P2pRoomSession.TransportMode.TCP_ONLY)
        val guestSession = P2pRoomSession("GAME88", false, P2pRoomSession.TransportMode.TCP_ONLY)

        val hostPort = hostSession.localPort
        assertTrue("Host port should be allocated", hostPort > 0)

        val hostConnectedLatch = CountDownLatch(1)
        val guestConnectedLatch = CountDownLatch(1)
        val hostReceivedMoveLatch = CountDownLatch(1)
        val guestReceivedMoveLatch = CountDownLatch(1)

        var hostReceivedMove: String? = null
        var guestReceivedMove: String? = null

        hostSession.setListener(object : P2pConnectionListener {
            override fun onStateChanged(state: com.gamecenter.app.network.p2p.model.P2pConnectionState) {}
            override fun onConnected(activePath: CandidateType, rttMs: Long) {
                hostConnectedLatch.countDown()
            }
            override fun onDataReceived(payload: ByteArray) {
                hostReceivedMove = String(payload, Charsets.UTF_8)
                hostReceivedMoveLatch.countDown()
            }
            override fun onPathMigrated(newPath: CandidateType, currentRttMs: Long) {}
            override fun onError(errorCode: Int, errorMessage: String) {}
        })

        guestSession.setListener(object : P2pConnectionListener {
            override fun onStateChanged(state: com.gamecenter.app.network.p2p.model.P2pConnectionState) {}
            override fun onConnected(activePath: CandidateType, rttMs: Long) {
                guestConnectedLatch.countDown()
            }
            override fun onDataReceived(payload: ByteArray) {
                guestReceivedMove = String(payload, Charsets.UTF_8)
                guestReceivedMoveLatch.countDown()
            }
            override fun onPathMigrated(newPath: CandidateType, currentRttMs: Long) {}
            override fun onError(errorCode: Int, errorMessage: String) {}
        })

        // 模拟房主收集并发布候选端点
        val hostCandidates = listOf(
            Candidate(CandidateType.HOST_IPV4, "127.0.0.1", hostPort, 100, NatType.OPEN_INTERNET)
        )

        // 房主启动 TCP 服务端流程
        hostSession.startConnectingWithCandidates(emptyList())

        Thread.sleep(60)

        // 访客作为客户端连接房主
        guestSession.startConnectingWithCandidates(hostCandidates)

        // 验证双端联机就绪
        assertTrue("Host should connect", hostConnectedLatch.await(3, TimeUnit.SECONDS))
        assertTrue("Guest should connect", guestConnectedLatch.await(3, TimeUnit.SECONDS))

        // 房主落黑子 (7, 7)
        val blackMove = "GOMOKU:BLACK:7:7"
        hostSession.sendData(blackMove.toByteArray(Charsets.UTF_8))
        assertTrue("Guest should receive black move", guestReceivedMoveLatch.await(2, TimeUnit.SECONDS))
        assertEquals(blackMove, guestReceivedMove)

        // 访客回白子 (7, 8)
        val whiteMove = "GOMOKU:WHITE:7:8"
        guestSession.sendData(whiteMove.toByteArray(Charsets.UTF_8))
        assertTrue("Host should receive white move", hostReceivedMoveLatch.await(2, TimeUnit.SECONDS))
        assertEquals(whiteMove, hostReceivedMove)

        hostSession.close()
        guestSession.close()
    }
}
