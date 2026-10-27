package com.gamecenter.app.network.p2p

import com.gamecenter.app.network.p2p.punch.FastFailPortPredictor
import org.junit.Assert.*
import org.junit.Test

class FastFailPortPredictorTest {

    @Test
    fun testPredictableDelta() {
        // 增量步长 delta = 2，处于 [1, 8] 合法范围
        val samples = listOf(10000, 10002)
        val result = FastFailPortPredictor.evaluateAndPredict(samples)

        assertTrue(result.isPredictable)
        assertEquals(2, result.estimatedDelta)
        assertEquals(FastFailPortPredictor.BURST_WINDOW_SIZE, result.targetPorts.size)
        assertEquals(10004, result.targetPorts[0])
    }

    @Test
    fun testPrngRandomDeltaFailsFast() {
        // 增量步长 delta = 500，远大于 8，判定为 5G PRNG 伪随机对称 NAT
        val samples = listOf(10000, 10500)
        val result = FastFailPortPredictor.evaluateAndPredict(samples)

        assertFalse(result.isPredictable)
        assertTrue(result.targetPorts.isEmpty())
    }
}
