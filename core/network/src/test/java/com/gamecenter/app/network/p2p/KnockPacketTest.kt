package com.gamecenter.app.network.p2p

import com.gamecenter.app.network.p2p.punch.KnockPacket
import org.junit.Assert.*
import org.junit.Test

class KnockPacketTest {

    @Test
    fun testKnockPacketEncodingDecoding() {
        val payload = "MOVE_ROW_7_COL_7".toByteArray(Charsets.UTF_8)
        val packet = KnockPacket(
            KnockPacket.TYPE_KNOCK_PING,
            0x12345678,
            42,
            1700000000000L,
            payload
        )

        val encoded = packet.encode()
        assertEquals(KnockPacket.HEADER_SIZE + payload.size, encoded.size)

        val decoded = KnockPacket.decode(encoded, encoded.size)
        assertNotNull(decoded)
        assertEquals(KnockPacket.TYPE_KNOCK_PING, decoded!!.type)
        assertEquals(0x12345678, decoded.sessionHash)
        assertEquals(42, decoded.sequence)
        assertEquals(1700000000000L, decoded.timestamp)
        assertArrayEquals(payload, decoded.payload)
    }

    @Test
    fun testCorruptPacketHandling() {
        val badData = ByteArray(10) { 0xFF.toByte() }
        val decoded = KnockPacket.decode(badData, badData.size)
        assertNull(decoded)
    }
}
