package com.gamecenter.app.network.p2p

import com.gamecenter.app.network.p2p.stun.StunAttribute
import com.gamecenter.app.network.p2p.stun.StunMessage
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StunProtocolTest {

    @Test
    fun testBindingRequestEncodingAndDecoding() {
        val request = StunMessage.createBindingRequest()
        assertEquals(StunMessage.TYPE_BINDING_REQUEST, request.messageType)
        assertEquals(StunMessage.MAGIC_COOKIE, request.magicCookie)
        assertEquals(12, request.transactionId.size)

        val encoded = request.encode()
        assertTrue(encoded.size >= StunMessage.HEADER_LENGTH)

        val decoded = StunMessage.decode(encoded, encoded.size)
        assertNotNull(decoded)
        assertEquals(StunMessage.TYPE_BINDING_REQUEST, decoded!!.messageType)
        assertEquals(StunMessage.MAGIC_COOKIE, decoded.magicCookie)
        assertArrayEquals(request.transactionId, decoded.transactionId)
    }

    @Test
    fun testXorMappedAddressIpv4() {
        val magicCookie = StunMessage.MAGIC_COOKIE
        val txId = ByteArray(12) { 0x0A.toByte() }
        val msg = StunMessage(StunMessage.TYPE_BINDING_RESPONSE, magicCookie, txId)

        // 目标: IP = 114.114.114.114, Port = 54321
        val targetIp = byteArrayOf(114.toByte(), 114.toByte(), 114.toByte(), 114.toByte())
        val targetPort = 54321

        // XOR 运算
        val xorPort = targetPort xor ((magicCookie ushr 16) and 0xFFFF)
        val cookieBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(magicCookie).array()
        val xorIp = ByteArray(4)
        for (i in 0 until 4) {
            xorIp[i] = (targetIp[i].toInt() xor cookieBytes[i].toInt()).toByte()
        }

        val attrVal = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
            .put(0.toByte())
            .put(StunAttribute.FAMILY_IPV4.toByte())
            .putShort(xorPort.toShort())
            .put(xorIp)
            .array()

        msg.addAttribute(StunAttribute(StunAttribute.TYPE_XOR_MAPPED_ADDRESS, attrVal))

        val resolved = msg.reflexiveAddress
        assertNotNull(resolved)
        assertEquals("114.114.114.114", resolved!!.ip)
        assertEquals(54321, resolved.port)
    }

    @Test
    fun testChangeRequestAttribute() {
        val changeReq = StunAttribute.createChangeRequest(true, false)
        assertEquals(StunAttribute.TYPE_CHANGE_REQUEST, changeReq.type)
        assertEquals(4, changeReq.value.size)

        val buf = ByteBuffer.wrap(changeReq.value).order(ByteOrder.BIG_ENDIAN)
        val flags = buf.int
        assertEquals(4, flags) // Change IP flag = 0x04
    }
}
