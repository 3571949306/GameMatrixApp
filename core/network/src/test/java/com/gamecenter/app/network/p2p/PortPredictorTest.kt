package com.gamecenter.app.network.p2p

import com.gamecenter.app.network.p2p.punch.PortPredictor
import org.junit.Assert.*
import org.junit.Test

class PortPredictorTest {

    @Test
    fun testLinearDeltaPrediction() {
        // 模拟连续 STUN 探测记录的对称型 NAT 端口样本序列 (步长 +2)
        val samples = listOf(20000, 20002, 20004)
        val predicted = PortPredictor.predictTargetPorts(samples, 16)

        assertFalse(predicted.isEmpty())
        // 增量方向优先命中 20006, 20008, 20010 ...
        assertTrue(predicted.contains(20006))
        assertTrue(predicted.contains(20008))
        assertEquals(20006, predicted[0])
    }

    @Test
    fun testSingleSampleFallback() {
        val samples = listOf(35000)
        val predicted = PortPredictor.predictTargetPorts(samples, 16)

        assertFalse(predicted.isEmpty())
        assertTrue(predicted.contains(35001))
    }

    @Test
    fun testEmptySampleSafe() {
        val predicted = PortPredictor.predictTargetPorts(emptyList(), 16)
        assertTrue(predicted.isEmpty())
    }
}
